#!/usr/bin/env python3
"""Investigative XHTTP TUN POC. Results are evidence, not full acceptance."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shlex
import subprocess
import time
import uuid

spec=importlib.util.spec_from_file_location('vm',Path(__file__).resolve().parents[1]/'vm/manage.py')
vm=importlib.util.module_from_spec(spec);spec.loader.exec_module(vm)

def main():
    os.umask(0o077)
    p=argparse.ArgumentParser();p.add_argument('--work-dir',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--stop-signal',choices=['SIGINT','SIGTERM','SIGHUP','SIGKILL'],default='SIGTERM');args=p.parse_args()
    w=args.work_dir.resolve();s=json.loads((w/'state.json').read_text());identity=vm.verify(w,s)
    out=args.out.resolve();out.mkdir(parents=True,exist_ok=False,mode=0o700)
    results={'kind':'investigative-poc','guest':identity,'requests':[],'nonce':str(uuid.uuid4())}
    def guest(argv):
        vm.verify(w,s)
        r=subprocess.run([str(a) for a in vm.ssh(w,s)]+[shlex.join(argv)],capture_output=True,text=True,timeout=25)
        return dict(argv=argv,exit=r.returncode,stdout=r.stdout,stderr=r.stderr)
    if guest(['sudo','test','!','-e','/run/xrayfa/session.json'])['exit']!=0:
        raise RuntimeError('Existing journal preserved; resolve it before POC')
    captures=[]
    def capture(name,argv):
        vm.verify(w,s)
        f=open(out/(name+'.txt'),'w')
        p=subprocess.Popen([str(a) for a in vm.ssh(w,s)]+[shlex.join(['sudo','timeout','--signal=INT','30',*argv])],stdin=subprocess.DEVNULL,stdout=f,stderr=f)
        captures.append((p,f))
    capture('underlay',['tcpdump','-l','-tttt','-nn','-i','xfa-underlay','-c','5000'])
    capture('fixture',['ip','netns','exec','xfa-fixture','tcpdump','-l','-tttt','-nn','-i','xfa-peer','-c','5000'])
    results['hashes']=guest(['sha256sum','/opt/xrayfa-runtime/xrayfa','/opt/xrayfa-runtime/xray'])
    env=['env','-u','HTTP_PROXY','-u','HTTPS_PROXY','-u','ALL_PROXY','-u','http_proxy','-u','https_proxy','-u','all_proxy']
    curl=env+['curl','--noproxy','*','--max-time','4','--fail','--silent','--show-error']
    results['ipv6Before']=guest(curl+['http://[2001:db8:203::2]:18081/before-'+results['nonce']])
    results['baseline']=guest(['sudo','python3','-c',"import json,subprocess; print(json.dumps({k:subprocess.check_output(v,text=True) for k,v in {'route':['ip','-j','route','show','table','all'],'address':['ip','-j','address'],'resolver':['resolvectl','status'],'nft':['nft','-j','list','ruleset']}.items()}))"])
    with open(out/'run.log','w') as log:
        vm.verify(w,s)
        proc=subprocess.Popen([str(a) for a in vm.ssh(w,s)]+['sudo /opt/xrayfa-runtime/xrayfa run 1 --core /opt/xrayfa-runtime/xray --log-level debug'],stdout=log,stderr=log)
        deadline=time.monotonic()+18
        while proc.poll() is None and time.monotonic()<deadline:
            if 'stage=Running' in (out/'run.log').read_text():break
            time.sleep(.2)
        results['running']='stage=Running' in (out/'run.log').read_text()
        if results['running']:
            capture('tun',['tcpdump','-l','-tttt','-nn','-i','xrayfa0','-c','5000'])
            time.sleep(.5)
            results['socketEvidence']=guest(['sudo','ss','-tnp'])
            results['routes']=guest(['ip','-j','route','show','table','all'])
            results['requests'].append(guest(curl+['http://203.0.113.10/'+results['nonce']]))
            results['requests'].append(guest(curl+['https://203.0.113.10/'+results['nonce']]))
            results['requests'].append(guest(['dig','@198.18.0.2',results['nonce']+'.xfa.test','A','+time=2','+tries=1']))
            results['requests'].append(guest(['dig','+tcp','@198.18.0.2',results['nonce']+'.xfa.test','A','+time=2','+tries=1']))
            results['requests'].append(guest(['getent','ahostsv4',results['nonce']+'.xfa.test']))
            results['requests'].append(guest(['python3','-c',f"import socket;s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);s.settimeout(3);n={results['nonce']!r}.encode();s.sendto(n,('203.0.113.10',19000));assert s.recv(1024)==n;print('UDP nonce matched')"]))
            results['ipv6During']=guest(curl+['http://[2001:db8:203::2]:18081/during-'+results['nonce']])
            results['stop']=guest(['sudo','python3','-c',f"import json,os,signal;d=json.load(open('/run/xrayfa/session.json'));os.kill(d['controllerPid'],signal.{args.stop_signal})"])
        if proc.poll() is None:
            try:proc.wait(timeout=25)
            except subprocess.TimeoutExpired:
                results['stopDeadlineExceeded']=True
                # Kill only the controller identified by this session journal; recover follows.
                results['killController']=guest(['sudo','python3','-c',"import json,os,signal;d=json.load(open('/run/xrayfa/session.json'));os.kill(d['controllerPid'],signal.SIGKILL)"])
                proc.wait(timeout=10)
        results['sshExit']=proc.returncode
        results['cliExit']=proc.returncode if args.stop_signal!='SIGKILL' else None
        results['cliTerminationSignal']=9 if args.stop_signal=='SIGKILL' else None
    (out/'result-partial.json').write_text(json.dumps(results,indent=2)+'\n')
    results['stopSignal']=args.stop_signal
    if args.stop_signal=='SIGKILL':
        results['orphanState']=guest(['sudo','python3','-c',"import json,os;d=json.load(open('/run/xrayfa/session.json'));print(json.dumps(dict(pid=d['corePid'],exe=os.readlink('/proc/'+str(d['corePid'])+'/exe'))))"])
    for p,f in captures:
        try:p.wait(timeout=35)
        finally:f.close()
    results['recover']=guest(['sudo','/opt/xrayfa-runtime/xrayfa','recover'])
    results['ipv6After']=guest(curl+['http://[2001:db8:203::2]:18081/after-'+results['nonce']])
    results['bypassControl']=guest(curl+['http://203.0.113.10/bypass-'+results['nonce']])
    results['serverLog']=guest(['sudo','tail','-n','100','/var/lib/xrayfa-fixture/server.log'])
    results['servicesLog']=guest(['sudo','cat','/var/lib/xrayfa-fixture/services.log'])
    results['after']=guest(['sudo','python3','-c',"import json,subprocess; print(json.dumps({k:subprocess.check_output(v,text=True) for k,v in {'route':['ip','-j','route','show','table','all'],'address':['ip','-j','address'],'resolver':['resolvectl','status'],'nft':['nft','-j','list','ruleset']}.items()}))"])
    (out/'result.json').write_text(json.dumps(results,indent=2)+'\n')
    print(json.dumps({'running':results['running'],'cliExit':results['cliExit'],'recoverExit':results['recover']['exit'],'requests':[(r['argv'][-1],r['exit']) for r in results['requests']]}))

if __name__=='__main__':main()
