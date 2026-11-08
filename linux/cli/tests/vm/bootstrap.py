#!/usr/bin/env python3
"""Download authenticated Ubuntu packages into a private prefix; no host install."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.request

PACKAGES = ['qemu-system-x86','qemu-system-common','qemu-system-data','qemu-utils',
            'seabios','ipxe-qemu','libslirp0','liburing2','libaio1','libfdt1',
            'libpmem1','librdmacm1','libibverbs1','libndctl6','libdaxctl1']
BASE = 'https://cloud-images.ubuntu.com/releases/jammy/release-20261106/'
IMAGE = 'ubuntu-22.04-server-cloudimg-amd64.img'

def stream_hash(f):
    h=hashlib.sha256()
    for block in iter(lambda:f.read(1024*1024), b''): h.update(block)
    return h.hexdigest()

def download(url, path):
    if path.exists(): return
    partial=path.with_suffix(path.suffix+'.partial')
    subprocess.run(['curl','-fL','--retry','3','-C','-','-o',str(partial),url],check=True)
    partial.rename(path)

def main():
    os.umask(0o077)
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--work-dir',type=Path,required=True)
    w=p.parse_args().work_dir.resolve();w.mkdir(parents=True,exist_ok=True,mode=0o700)
    if w.stat().st_mode & 0o077: raise SystemExit('work-dir must be private (0700)')
    packages=w/'tools/packages';packages.mkdir(parents=True,exist_ok=True)
    subprocess.run(['apt-get','download',*PACKAGES],cwd=packages,check=True)
    records=[]
    for deb in sorted(packages.glob('*.deb')):
        subprocess.run(['dpkg-deb','-x',str(deb),str(w/'tools/root')],check=True)
        with deb.open('rb') as f: sha=stream_hash(f)
        records.append(dict(file=deb.name,sha256=sha))
    (w/'tools/packages.json').write_text(json.dumps(records,indent=2)+'\n')
    for name in ['SHA256SUMS','SHA256SUMS.gpg']:
        download(BASE+name,w/name)
    subprocess.run(['gpgv','--keyring','/usr/share/keyrings/ubuntu-cloudimage-keyring.gpg',
                    str(w/'SHA256SUMS.gpg'),str(w/'SHA256SUMS')],check=True)
    download(BASE+IMAGE,w/IMAGE)
    expected=next(line.split()[0] for line in (w/'SHA256SUMS').read_text().splitlines()
                  if line.split()[-1].lstrip('*')==IMAGE)
    with (w/IMAGE).open('rb') as f: sha=stream_hash(f)
    if sha!=expected: raise SystemExit('Image hash mismatch; preserved for investigation')
    print(json.dumps(dict(image=IMAGE,sha256=sha,source=BASE)))

if __name__=='__main__': main()
