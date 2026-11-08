#!/usr/bin/env python3
"""Guest-side real protocol probe; all host interaction is managed outside this script."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import socket
import subprocess
import time
import uuid

import setup

CLI='/opt/xrayfa-runtime/xrayfa'
CORE='/opt/xrayfa-runtime/xray'
STATE=setup.STATE

def sha(path):
    h=hashlib.sha256()
    with open(path,'rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()

def normalize(v):
    if isinstance(v,dict):return {k:normalize(x) for k,x in v.items() if k not in ['valid_life_time','preferred_life_time','expires','packets','bytes','metainfo']}
    if isinstance(v,list):return [normalize(x) for x in v if not isinstance(x,dict) or 'metainfo' not in x]
    return v

def cmd(argv,timeout=8,user=False):
    env={k:v for k,v in os.environ.items() if k.lower() not in ['http_proxy','https_proxy','all_proxy','no_proxy'] and not k.startswith('XRAY')}
    env.update(PATH='/usr/sbin:/usr/bin:/sbin:/bin',LANG='C',LC_ALL='C',SUDO_UID='1000')
    start=time.monotonic()
    try:
        p=subprocess.run(argv,capture_output=True,text=True,timeout=timeout,env=env,**(dict(user=1000,group=1000,extra_groups=[]) if user else {}))
        return {'argv':argv,'exit':p.returncode,'stdout':p.stdout[-262144:],'stderr':p.stderr[-65536:],'seconds':time.monotonic()-start}
    except subprocess.TimeoutExpired as e:
        return {'argv':argv,'exit':None,'timeout':True,'stdout':(e.stdout or b'').decode(errors='replace')[-262144:],'stderr':(e.stderr or b'').decode(errors='replace')[-65536:],'seconds':time.monotonic()-start}

def snapshot():
    return {key:cmd(argv) for key,argv in {
        'addresses':['ip','-j','address'],'links':['ip','-j','link'],'routes':['ip','-j','route','show','table','all'],
        'rules':['ip','-j','rule'],'dns':['resolvectl','dns'],'domains':['resolvectl','domain'],
        'defaultRoute':['resolvectl','default-route'],'nft':['nft','-j','list','ruleset']}.items()}

def config_snapshot(s):
    result={}
    for key,entry in s.items():
        if entry['exit']!=0:raise RuntimeError('Snapshot failed: '+key)
        text=entry['stdout'].strip()
        result[key]=normalize(json.loads(text)) if key in ['addresses','links','routes','rules','nft'] else text
    return result

def pid_record(pid):
    try:return {'pid':pid,'start':Path(f'/proc/{pid}/stat').read_text().rsplit(')',1)[1].split()[19],'exe':os.readlink(f'/proc/{pid}/exe')}
    except FileNotFoundError:return None

def verify_pid(expected):
    if pid_record(expected['pid'])!=expected:raise RuntimeError('Tracked fixture process identity mismatch')

def main():
    os.umask(0o077)
    p=argparse.ArgumentParser();p.add_argument('--instance',required=True);p.add_argument('--seed',required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--idle-seconds',type=int,default=0);p.add_argument('--bootstrap-hostname');args=p.parse_args();setup.guard(args.instance)
    seeds=json.loads((STATE/'seeds.json').read_text());seed=next(s for s in seeds if s['id']==args.seed)
    state=json.loads((STATE/'state.json').read_text())
    if state['boot']!=Path('/proc/sys/kernel/random/boot_id').read_text().strip():raise RuntimeError('Fixture boot changed')
    if Path('/run/xrayfa/session.json').exists():raise RuntimeError('Existing runtime journal preserved')
    out=args.out.resolve()
    if not out.is_relative_to(STATE/'probes'):raise RuntimeError('Probe output must be private under fixture/probes')
    out.mkdir(parents=True,exist_ok=False,mode=0o700)
    nonce=uuid.uuid4().hex
    report={'schemaVersion':1,'kind':'real-protocol-probe','seedId':args.seed,'nonce':nonce,'instance':args.instance,
        'boot':state['boot'],'namespace':os.readlink('/proc/self/ns/net'),'uid':1000,'cliSha256':sha(CLI),'coreSha256':sha(CORE),
        'fixtureSha256':sha(__file__),'serverConfigSha256':sha(STATE/'server-all.json'),'startedUtc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'requests':{},'assertions':{}}
    captures=[];client=None;paused=False;hostsOriginal=None
    # Locate tracked Xray server; services/REALITY target are separate identities.
    server=next(x for x in state['processes'] if x['exe']==CORE)
    def save():
        target=out/'result.json';temp=out/'result.tmp';temp.write_text(json.dumps(report,indent=2)+'\n');temp.replace(target)
    def capture(name,argv):
        f=open(out/(name+'.stderr'),'wb')
        proc=subprocess.Popen(argv,stdout=f,stderr=f)
        captures.append((name,proc,f))
    curl=['curl','--noproxy','*','--max-time','3','--fail','--silent','--show-error']
    def http(path='',https=False):return cmd(curl+[('https' if https else 'http')+'://203.0.113.10/'+nonce+path],user=True)
    def v6(stage):return cmd(curl+['http://[2001:db8:203::2]:18081/'+nonce+'/'+stage],user=True)
    def dns(label,tcp=False,system=False):
        domain=f'{nonce}-{label}.xfa.test'
        if system:return cmd(['getent','ahostsv4',domain],user=True)
        return cmd(['dig',*(['+tcp'] if tcp else []),'@198.18.0.2',domain,'A','+tries=1','+time=2'],user=True)
    try:
        before=snapshot();report['before']=before
        report['ipv6Before']=v6('before')
        report['udpServiceHealth']=cmd(['ip','netns','exec',setup.NS,'python3','-c',f"import socket;s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);s.settimeout(2);n=b'health-{nonce}';s.sendto(n,('127.0.0.1',19000));assert s.recv(1024)==n;print('healthy')"])
        # Add through the original user, using a private synthetic share-link file.
        share=seed['link']
        if args.bootstrap_hostname:
            if not re.fullmatch(r'[a-zA-Z0-9.-]+',args.bootstrap_hostname) or seed['protocol'] not in ['vless','trojan','socks','http']:raise RuntimeError('Unsupported bootstrap fixture hostname/format')
            hostsOriginal=Path('/etc/hosts').read_bytes()
            with open('/etc/hosts','ab') as f:f.write(('\n10.204.0.200 '+args.bootstrap_hostname+'\n').encode())
            share=share.replace('10.204.0.200:',args.bootstrap_hostname+':')
            report['bootstrapHostname']=args.bootstrap_hostname
        link=Path('/home/xfa/.fixture-probe-link');link.write_text(share+'\n');os.chown(link,1000,1000);link.chmod(0o600)
        added=cmd([CLI,'add','--link-file',str(link)],user=True);link.unlink()
        if added['exit']!=0:raise RuntimeError('Share link rejected: '+added['stderr'])
        report['nodeId']=int(re.search(r'Added node (\d+)',added['stdout'])[1])
        capture('underlay',['tcpdump','-U','-nn','-i','xfa-underlay','-c','12000','-w',str(out/'underlay.pcap')])
        capture('fixture',['ip','netns','exec',setup.NS,'tcpdump','-U','-nn','-i','xfa-peer','-c','12000','-w',str(out/'fixture.pcap')])
        management=json.loads(cmd(['ip','-j','route','get','10.0.2.2'])['stdout'])[0]['dev']
        capture('management-dns',['tcpdump','-U','-nn','-i',management,'-c','12000','-w',str(out/'management-dns.pcap'),'port','53'])
        with open(out/'cli.log','wb') as log:
            env={'PATH':'/usr/sbin:/usr/bin:/sbin:/bin','LANG':'C','LC_ALL':'C','SUDO_UID':'1000'}
            client=subprocess.Popen([CLI,'run',str(report['nodeId']),'--core',CORE,'--log-level','debug'],stdout=log,stderr=log,env=env)
            report['controller']=pid_record(client.pid);save()
            deadline=time.monotonic()+17
            while time.monotonic()<deadline and client.poll() is None:
                if 'stage=Running' in (out/'cli.log').read_text():break
                time.sleep(.05)
            report['running']='stage=Running' in (out/'cli.log').read_text()
            if not report['running']:raise RuntimeError('Client did not reach Running')
            journal=json.loads(Path('/run/xrayfa/session.json').read_text())
            report['journal']=journal
            config=Path(journal['configPath']);report['configSha256']=sha(config)
            (out/'final.json').write_bytes(config.read_bytes())
            capture('tun',['tcpdump','-U','-nn','-i','xrayfa0','-c','12000','-w',str(out/'tun.pcap')]);time.sleep(.15)
            report['during']=snapshot();report['sockets']=cmd(['ss','-ntup'])
            report['requests']['http']=http()
            report['requests']['https']=http(https=True)
            report['requests']['dns-udp']=dns('udp')
            report['requests']['dns-tcp']=dns('tcp',tcp=True)
            report['requests']['system-dns']=dns('system',system=True)
            report['requests']['udp']=cmd(['python3','-c',f"import socket;s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);s.settimeout(2);n=b'{nonce}';s.sendto(n,('203.0.113.10',19000));assert s.recv(1024)==n;print('UDP nonce matched')"],user=True)
            report['ipv6During']=v6('during');report['nftDuring']=cmd(['nft','-j','list','ruleset'])
            if args.idle_seconds:
                report['idleSeconds']=args.idle_seconds
                time.sleep(args.idle_seconds)
                report['afterIdle']=http('/after-idle')
            verify_pid(server);os.kill(server['pid'],signal.SIGSTOP);paused=True
            report['serverPaused']=pid_record(server['pid']);report['serverOffRequest']=http('/server-unavailable')
            os.kill(server['pid'],signal.SIGCONT);paused=False
            report['serverResumedRequest']=http('/server-resumed')
            report['dnsAfterFault']=dns('after-fault',tcp=True)
            report['configUnchanged']=sha(config)==journal['configSha256']
            report['coreAfterFault']=pid_record(journal['corePid'])
            client.send_signal(signal.SIGTERM)
            try:client.wait(timeout=22)
            except subprocess.TimeoutExpired:report['cleanupDeadlineExceeded']=True;client.kill();client.wait(timeout=3)
            report['cliExit']=client.returncode;report['exitSeconds']=time.monotonic()
        report['recover']=cmd([CLI,'recover'],timeout=23)
        report['recoverAgain']=cmd([CLI,'recover'],timeout=23)
        report['ipv6After']=v6('after');report['bypassRequest']=http('/bypass')
        report['after']=snapshot()
        report['baselineEqual']=config_snapshot(before)==config_snapshot(report['after'])
        report['serverLog']=cmd(['tail','-n','300',str(STATE/'server-all.log')])
        report['servicesLog']=cmd(['tail','-n','250',str(STATE/'services.log')])
    except Exception as e:
        report['error']=str(e)
    finally:
        if paused:
            verify_pid(server);os.kill(server['pid'],signal.SIGCONT)
        if client and client.poll() is None:
            client.send_signal(signal.SIGTERM)
            try:client.wait(timeout=22)
            except subprocess.TimeoutExpired:client.kill();client.wait(timeout=3)
            report['cliExit']=client.returncode
        if Path('/run/xrayfa/session.json').exists():report['finalRecover']=cmd([CLI,'recover'],timeout=23)
        for name,proc,f in captures:
            if proc.poll() is None:proc.send_signal(signal.SIGINT)
            try:proc.wait(timeout=3)
            except subprocess.TimeoutExpired:proc.kill();proc.wait(timeout=2)
            f.close()
            text=cmd(['tcpdump','-tttt','-nn','-A','-r',str(out/(name+'.pcap'))],timeout=5)
            (out/(name+'.txt')).write_text(text['stdout']+'\n'+text['stderr'])
        if hostsOriginal is not None:
            Path('/etc/hosts').write_bytes(hostsOriginal)
            report['hostsRestored']=Path('/etc/hosts').read_bytes()==hostsOriginal
        save()
    # Assertions are evaluated independently; omitted evidence fails, never passes.
    a=report['assertions'];requests=report.get('requests',{})
    for kind in ['http','https']:
        a[kind]=requests.get(kind,{}).get('exit')==0 and nonce in requests.get(kind,{}).get('stdout','')
    for kind in ['dns-udp','dns-tcp','system-dns']:
        a[kind]=requests.get(kind,{}).get('exit')==0 and '203.0.113.10' in requests.get(kind,{}).get('stdout','')
    udp=requests.get('udp',{})
    tun=(out/'tun.txt').read_text() if (out/'tun.txt').exists() else ''
    underlay=(out/'underlay.txt').read_text() if (out/'underlay.txt').exists() else ''
    fixture=(out/'fixture.txt').read_text() if (out/'fixture.txt').exists() else ''
    management=(out/'management-dns.txt').read_text() if (out/'management-dns.txt').exists() else ''
    if seed['udpPolicy']=='block':
        a['udp']=udp.get('exit')!=0 and report.get('udpServiceHealth',{}).get('exit')==0 and f'203.0.113.10.19000' in tun and '203.0.113.10.19000' not in underlay and a['http'] and a['dns-tcp']
    else:a['udp']=udp.get('exit')==0 and 'UDP nonce matched' in udp.get('stdout','')
    a['ipv6-three-states']=report.get('ipv6Before',{}).get('exit')==0 and report.get('ipv6During',{}).get('exit')==28 and report.get('ipv6After',{}).get('exit')==0
    a['stop-restore']=report.get('cliExit')==0 and report.get('baselineEqual') is True and report.get('recover',{}).get('exit')==0 and report.get('recoverAgain',{}).get('exit')==0 and not report.get('cleanupDeadlineExceeded')
    a['bypass-control']=report.get('bypassRequest',{}).get('exit') not in [0,None]
    a['server-off-control']=report.get('serverOffRequest',{}).get('exit')==28 and report.get('serverResumedRequest',{}).get('exit')==0 and nonce in report.get('serverResumedRequest',{}).get('stdout','')
    a['three-sides']=nonce in tun and f'10.204.0.200.{seed["port"]}' in underlay and f'10.204.0.200.{seed["port"]}' in fixture and nonce in report.get('servicesLog',{}).get('stdout','')
    a['dns-no-bypass']=nonce not in management and '1.1.1.1.53' not in underlay and all(a[k] for k in ['dns-udp','dns-tcp','system-dns'])
    if args.idle_seconds:
        a['long-idle']=args.idle_seconds>=70 and report.get('afterIdle',{}).get('exit')==0 and nonce in report.get('afterIdle',{}).get('stdout','')
    if args.bootstrap_hostname:
        config=json.loads((out/'final.json').read_text()) if (out/'final.json').exists() else {}
        outbounds=config.get('outbounds',[{}]);o=outbounds[0];settings=o.get('settings',{})
        endpoint=(settings.get('vnext') or settings.get('servers') or [settings])[0]
        a['node-bootstrap']=endpoint.get('address')=='10.204.0.200' and report.get('hostsRestored') is True
    a['immutable-config']=report.get('configUnchanged') is True
    a['core-survived-request-failure']=report.get('coreAfterFault') is not None
    report['status']='passed' if a and all(a.values()) else 'failed'
    save();print(json.dumps({'seed':args.seed,'status':report['status'],'assertions':a,'error':report.get('error')}))
    raise SystemExit(report['status']!='passed')

if __name__=='__main__':main()
