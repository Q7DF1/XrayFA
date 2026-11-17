#!/usr/bin/env python3
"""Private QEMU/KVM VM lifecycle. Never configures host networking or invokes sudo."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shlex
import socket
import subprocess
import time
import uuid

IMAGE = 'ubuntu-22.04-server-cloudimg-amd64.img'

def run(argv, **kwargs):
    return subprocess.run([str(a) for a in argv], check=True, **kwargs)

def digest(path):
    with open(path, 'rb') as f:
        h=hashlib.sha256()
        for block in iter(lambda:f.read(1024*1024), b''): h.update(block)
        return h.hexdigest()

def save(path, obj):
    temp = path.with_suffix('.tmp')
    with open(temp, 'x') as f:
        json.dump(obj, f, indent=2)
        f.write('\n'); f.flush(); os.fsync(f.fileno())
    os.replace(temp, path)
    fd = os.open(path.parent, os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)

def tools(w):
    root = w/'tools/root'
    env = dict(os.environ)
    env['LD_LIBRARY_PATH'] = str(root/'usr/lib/x86_64-linux-gnu')
    return root, env

def proc_identity(pid):
    try:
        # comm may contain spaces or parentheses.
        stat = Path(f'/proc/{pid}/stat').read_text().rsplit(')', 1)[1].split()
        return dict(pid=pid, start=stat[19], exe=os.readlink(f'/proc/{pid}/exe'),
                    argv=Path(f'/proc/{pid}/cmdline').read_bytes().split(b'\0')[:-1])
    except (FileNotFoundError, ProcessLookupError):
        return None

def owned_process(s):
    cur = proc_identity(s.get('pid', -1))
    if not cur or cur['start'] != s.get('start') or cur['exe'] != s.get('exe'):
        raise RuntimeError('VM process absent or identity mismatch')
    if s['instance'] not in [a.decode() for a in cur['argv']]:
        raise RuntimeError('VM instance argv mismatch')
    return cur

def ssh(w, s):
    return ['ssh', '-i', w/'id_ed25519', '-p', str(s['port']), '-o', 'BatchMode=yes',
            '-o', 'ConnectTimeout=5', '-o', 'StrictHostKeyChecking=yes',
            '-o', f'UserKnownHostsFile={w}/known_hosts', 'xfa@127.0.0.1']

def verify(w, s):
    owned_process(s)
    script = "head -c 36 /etc/xrayfa-vm-id; echo; cat /proc/sys/kernel/random/boot_id; systemd-detect-virt --vm; sudo -n cat /sys/class/dmi/id/product_uuid"
    result = run(ssh(w,s)+[script], capture_output=True, text=True).stdout.splitlines()
    host_boot = Path('/proc/sys/kernel/random/boot_id').read_text().strip()
    if len(result) != 4 or result[0] != s['instance'] or result[1] == host_boot or result[2] not in ['kvm','qemu'] or result[3].lower() != s['instance']:
        raise RuntimeError('Guest instance / boot / virtualization / DMI mismatch')
    return dict(instance=result[0], boot=result[1], virtualization=result[2], dmi=result[3])

def prepare(w, args):
    if (w/'state.json').exists():
        raise RuntimeError('Existing VM state preserved; use info/start')
    image = w/IMAGE
    sums = (w/'SHA256SUMS').read_text()
    run(['gpgv','--keyring','/usr/share/keyrings/ubuntu-cloudimage-keyring.gpg', w/'SHA256SUMS.gpg',w/'SHA256SUMS'])
    expected = next(line.split()[0] for line in sums.splitlines() if line.split()[-1].lstrip('*') == IMAGE)
    if digest(image) != expected:
        raise RuntimeError('Official image hash mismatch')
    root,env = tools(w)
    if not (w/'overlay.qcow2').exists():
        run([root/'usr/bin/qemu-img','create','-f','qcow2','-F','qcow2','-b',image,w/'overlay.qcow2','20G'],env=env)
    instance = str(uuid.uuid4())
    if not (w/'id_ed25519').exists():
        run(['ssh-keygen','-q','-t','ed25519','-N','','-f',w/'id_ed25519'])
    if not (w/'ssh_host_ed25519_key').exists():
        run(['ssh-keygen','-q','-t','ed25519','-N','','-f',w/'ssh_host_ed25519_key'])
    pub = (w/'id_ed25519.pub').read_text().strip()
    hostkey = (w/'ssh_host_ed25519_key').read_text()
    hostpub = (w/'ssh_host_ed25519_key.pub').read_text().strip()
    userdata = f'''#cloud-config
hostname: xrayfa-runtime
users:
  - name: xfa
    shell: /bin/bash
    sudo: ALL=(ALL) NOPASSWD:ALL
    lock_passwd: true
    ssh_authorized_keys:
      - {pub}
ssh_pwauth: false
disable_root: true
ssh_keys:
  ed25519_private: |
'''+''.join('    '+l+'\n' for l in hostkey.splitlines())+f'''  ed25519_public: {hostpub}
write_files:
  - path: /etc/xrayfa-vm-id
    permissions: '0444'
    content: {instance}
package_update: true
packages: [libssl3, python3, curl, dnsutils, iproute2, nftables, tcpdump, openssl, socat]
runcmd:
  - [touch, /var/lib/cloud/xrayfa-ready]
'''
    (w/'user-data').write_text(userdata)
    (w/'meta-data').write_text(f'instance-id: {instance}\nlocal-hostname: xrayfa-runtime\n')
    run(['genisoimage','-quiet','-output',w/'seed.iso','-volid','cidata','-joliet','-rock', w/'user-data',w/'meta-data'])
    (w/'known_hosts').write_text(f'[127.0.0.1]:{args.port} {hostpub}\n')
    save(w/'state.json', dict(schemaVersion=1, instance=instance,port=args.port,
        image=str(image),imageSha256=expected,overlay=str(w/'overlay.qcow2'),
        imageUrl='https://cloud-images.ubuntu.com/releases/jammy/release-20261106/'+IMAGE,
        hostBoot=Path('/proc/sys/kernel/random/boot_id').read_text().strip(),preparedUtc=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())))

def start(w,s):
    if s.get('pid') and proc_identity(s['pid']):
        owned_process(s); print('Already running'); return
    root,env = tools(w)
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',s['port']))
    # Explicit firmware paths support privately extracted Ubuntu packages.
    argv = [root/'usr/bin/qemu-system-x86_64','-enable-kvm','-cpu','host','-smp','2','-m','2048',
            '-name',s['instance'],'-uuid',s['instance'],'-display','none','-vga','none',
            '-global','virtio-net-pci.romfile=',
            '-L',root/'usr/share/qemu','-bios',root/'usr/share/seabios/bios-256k.bin',
            '-drive',f"file={s['overlay']},format=qcow2,if=virtio",
            '-drive',f'file={w}/seed.iso,format=raw,if=virtio,readonly=on',
            '-nic',f"user,model=virtio-net-pci,hostfwd=tcp:127.0.0.1:{s['port']}-:22",
            '-serial',f'file:{w}/serial.log','-qmp',f'unix:{w}/qmp.sock,server=on,wait=off']
    # QEMU keyval paths require comma escaping. Reject ambiguous working directories.
    if ',' in str(w):
        raise RuntimeError('Working directory must not contain commas')
    with open(w/'qemu.log','ab') as log:
        p = subprocess.Popen([str(a) for a in argv],env=env,stdout=log,stderr=log,start_new_session=True)
    time.sleep(.4)
    if p.poll() is not None:
        raise RuntimeError((w/'qemu.log').read_text())
    cur = proc_identity(p.pid)
    s.update({k:cur[k] for k in ['pid','start','exe']}); s['argv']=[str(a) for a in argv]
    save(w/'state.json',s)
    print(json.dumps(s,indent=2))

def qmp(w, s, command):
    owned_process(s)
    with socket.socket(socket.AF_UNIX) as sock:
        sock.settimeout(5); sock.connect(str(w/'qmp.sock'))
        with sock.makefile('rwb',buffering=0) as f:
            json.loads(f.readline())
            for cmd in ['qmp_capabilities',command]:
                f.write((json.dumps({'execute':cmd})+'\n').encode())
                while True:
                    reply=json.loads(f.readline())
                    if 'error' in reply: raise RuntimeError(reply)
                    if 'return' in reply: break

def seal(w,s):
    if s.get('pid') and proc_identity(s['pid']):
        raise RuntimeError('Stop this VM and wait for exit before sealing')
    if (w/'baseline.qcow2').exists():raise RuntimeError('Baseline already exists; preserved')
    # Rename, never silently overwrite or duplicate an unknown disk.
    Path(s['overlay']).rename(w/'baseline.qcow2')
    os.chmod(w/'baseline.qcow2',0o400)
    s['baseline']=str(w/'baseline.qcow2');s['baselineSha256']=digest(w/'baseline.qcow2')
    root,env=tools(w)
    target=w/'suite-001.qcow2'
    if target.exists():raise RuntimeError('Suite overlay already exists; preserved')
    run([root/'usr/bin/qemu-img','create','-f','qcow2','-F','qcow2','-b',w/'baseline.qcow2',target],env=env)
    s['overlay']=str(target);save(w/'state.json',s)
    print(json.dumps(dict(baseline=s['baseline'],sha256=s['baselineSha256'],overlay=s['overlay'])))

def main():
    os.umask(0o077)
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('command',choices=['prepare','start','info','exec','copy','collect','stop','seal'])
    p.add_argument('--work-dir',type=Path,required=True)
    p.add_argument('--port',type=int,default=22240)
    args, trailing=p.parse_known_args(); args.arguments=trailing
    w=args.work_dir.resolve(); w.mkdir(parents=True,exist_ok=True,mode=0o700)
    if w.is_symlink() or w.stat().st_uid != os.getuid() or w.stat().st_mode & 0o077:
        raise RuntimeError('VM work directory must be owned and private (0700)')
    if args.command=='prepare': return prepare(w,args)
    s=json.loads((w/'state.json').read_text())
    if args.command=='start': return start(w,s)
    if args.command=='seal': return seal(w,s)
    if args.command=='info': print(json.dumps(dict(state=s,guest=verify(w,s)),indent=2))
    if args.command=='exec':
        verify(w,s)
        if not args.arguments: raise RuntimeError('Pass guest argv after --')
        argv=args.arguments[1:] if args.arguments[0]=='--' else args.arguments
        return run(ssh(w,s)+[shlex.join(argv)]).returncode
    if args.command=='copy':
        verify(w,s)
        argv=args.arguments[1:] if args.arguments and args.arguments[0]=='--' else args.arguments
        if len(argv)!=2: raise RuntimeError('copy requires local-source guest-destination')
        run(['scp','-i',w/'id_ed25519','-P',str(s['port']),'-o','StrictHostKeyChecking=yes',
             '-o',f'UserKnownHostsFile={w}/known_hosts',argv[0],f'xfa@127.0.0.1:{argv[1]}'])
    if args.command=='collect':
        identity=verify(w,s)
        save(w/'evidence/guest-identity.json',identity)
        with open(w/'evidence/guest-baseline.txt','w') as f:
            run(ssh(w,s)+['uname -a; cat /etc/os-release; ip -j address; ip -j route show table all; ip -j rule; resolvectl status; sudo nft -j list ruleset; dpkg-query -W'],stdout=f)
    if args.command=='stop': qmp(w,s,'system_powerdown')

if __name__=='__main__':
    main()
