#!/usr/bin/env python3
"""Real-core writer-lock and unread-terminal pressure, without arbitrary product hooks."""
import argparse
import fcntl
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import time
import uuid
import probe
import setup

LOGDIR=Path('/var/log/xrayfa/1000')


def main():
    os.umask(0o077)
    p=argparse.ArgumentParser();p.add_argument('--instance',required=True);p.add_argument('--out',type=Path,required=True);args=p.parse_args();setup.guard(args.instance)
    out=args.out.resolve()
    if not out.is_relative_to(setup.STATE/'scenarios'):raise RuntimeError('Private output path required')
    out.mkdir(parents=True,exist_ok=False,mode=0o700)
    if Path('/run/xrayfa/session.json').exists():raise RuntimeError('Retained journal preserved')
    nodes=json.loads(Path('/home/xfa/.local/share/xrayfa/nodes.json').read_text())['nodes'];node=next(n['id'] for n in nodes if '10.204.0.200:24402?' in n['url'])
    r={'schemaVersion':1,'instance':args.instance,'boot':Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
       'cliSha256':probe.sha(probe.CLI),'coreSha256':probe.sha(probe.CORE),'fixtureSha256':probe.sha(__file__),'cases':[]}
    env={'PATH':'/usr/sbin:/usr/bin:/sbin:/bin','LANG':'C','LC_ALL':'C','SUDO_UID':'1000'}
    argv=[probe.CLI,'run',str(node),'--core',probe.CORE,'--log-level','debug']
    def save():(out/'result.json').write_text(json.dumps(r,indent=2)+'\n')
    def ready(proc):
        end=time.monotonic()+16
        while time.monotonic()<end and proc.poll() is None:
            try:
                j=json.loads(Path('/run/xrayfa/session.json').read_text())
                if j['controllerPid']==proc.pid and j['phase']=='Running':return j
            except (FileNotFoundError,KeyError):pass
            time.sleep(.025)
        raise RuntimeError('Real core did not reach Running')
    def finish(c):
        c['recover']=probe.cmd([probe.CLI,'recover'],timeout=24);c['after']=probe.snapshot()
        c['baselineEqual']=probe.config_snapshot(c['before'])==probe.config_snapshot(c['after'])
        if c['recover']['exit']!=0 or not c['baselineEqual'] or Path('/run/xrayfa/session.json').exists():raise RuntimeError('Baseline recovery failed')
    # The startup barrier must reject before even creating a network lease.
    c={'id':'LOG-02/blocked-writer','status':'failed','before':probe.snapshot()};proc=None
    folder=out/'blocked-writer';folder.mkdir(mode=0o700)
    try:
        with open(LOGDIR/'writer.lock','r+') as lock:
            fcntl.flock(lock,fcntl.LOCK_EX)
            c['startup']=probe.cmd(argv,timeout=8)
            c['startupNetworkUnchanged']=probe.config_snapshot(c['before'])==probe.config_snapshot(probe.snapshot())
            c['startupNoJournal']=not Path('/run/xrayfa/session.json').exists()
        if c['startup']['exit']!=11 or not c['startupNetworkUnchanged'] or not c['startupNoJournal']:raise RuntimeError('Startup persistence failure crossed the network boundary')
        with open(folder/'cli.log','wb') as log:
            proc=subprocess.Popen(argv,stdout=log,stderr=log,env=env);c['journal']=ready(proc)
            nonce=uuid.uuid4().hex
            with open(LOGDIR/'writer.lock','r+') as lock:
                fcntl.flock(lock,fcntl.LOCK_EX)
                c['duringLockRequest']=probe.cmd(['curl','--noproxy','*','--max-time','2','--fail','--silent','http://203.0.113.10/'+nonce],user=True)
                c['cliExit']=proc.wait(timeout=15)
            if c['duringLockRequest']['exit']!=0 or nonce not in c['duringLockRequest']['stdout'] or c['cliExit']!=11:raise RuntimeError('Writer lock blocked traffic/drain or did not trigger controlled stop')
        finish(c);c['status']='passed'
    except Exception as e:c['error']=str(e)
    finally:
        if proc and proc.poll() is None:
            proc.send_signal(signal.SIGTERM)
            try:proc.wait(timeout=22)
            except subprocess.TimeoutExpired:proc.kill();proc.wait(timeout=3)
        if Path('/run/xrayfa/session.json').exists():c['finalRecover']=probe.cmd([probe.CLI,'recover'],timeout=24)
        r['cases'].append(c);save();print(json.dumps({'id':c['id'],'status':c['status'],'error':c.get('error')}),flush=True)
    if Path('/run/xrayfa/session.json').exists():raise RuntimeError('Recovery failure stops isolation')
    c={'id':'LOG-02/flood','status':'failed','before':probe.snapshot()};proc=None
    folder=out/'unread-terminal';folder.mkdir(mode=0o700)
    try:
        proc=subprocess.Popen(argv,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,env=env)
        c['pipeBytes']=fcntl.fcntl(proc.stderr.fileno(),fcntl.F_SETPIPE_SZ,4096)
        c['journal']=ready(proc);session=Path(c['journal']['configPath']).parent.name;nonce=uuid.uuid4().hex
        c['requests']=[probe.cmd(['curl','--noproxy','*','--max-time','2','--fail','--silent',f'http://203.0.113.10/{nonce}/{i}'],user=True) for i in range(12)]
        proc.send_signal(signal.SIGTERM);c['cliExit']=proc.wait(timeout=22)
        (folder/'terminal.txt').write_bytes(proc.stderr.read());proc.stderr.close()
        lines=[]
        for name in ['xrayfa.log','xrayfa.log.1','xrayfa.log.2','xrayfa.log.3']:
            path=LOGDIR/name
            if path.exists():lines.extend(x for x in path.read_text().splitlines() if 'session='+session+' ' in x)
        (folder/'system-session.log').write_text('\n'.join(lines)+'\n')
        end=next((x for x in lines if 'LOG_QUEUE_END' in x),'')
        match=re.search(r'terminal_dropped_total=(\d+)',end);c['terminalDropped']=int(match[1]) if match else None
        if c['cliExit']!=0 or not all(q['exit']==0 and nonce in q['stdout'] for q in c['requests']) or not c['terminalDropped']:raise RuntimeError('Unread terminal stalled real traffic/cleanup or lacked persistent drop accounting')
        finish(c);c['status']='passed'
    except Exception as e:c['error']=str(e)
    finally:
        if proc and proc.poll() is None:
            proc.send_signal(signal.SIGTERM)
            try:proc.wait(timeout=22)
            except subprocess.TimeoutExpired:proc.kill();proc.wait(timeout=3)
        if Path('/run/xrayfa/session.json').exists():c['finalRecover']=probe.cmd([probe.CLI,'recover'],timeout=24)
        r['cases'].append(c);save();print(json.dumps({'id':c['id'],'status':c['status'],'error':c.get('error')}),flush=True)
    if Path('/run/xrayfa/session.json').exists():raise RuntimeError('Retained journal stops rotation case')
    c={'id':'LOG-01/two-rotations','status':'failed','before':probe.snapshot()};proc=None
    folder=out/'two-rotations';folder.mkdir(mode=0o700)
    try:
        proc=subprocess.Popen(argv,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,env=env)
        c['journal']=ready(proc);nonce=uuid.uuid4().hex
        current=LOGDIR/'xrayfa.log';seen=[current.stat().st_ino];started=time.monotonic()
        # A separate ordinary-user application sends real requests. stdout progress
        # is bounded; no proxy environment can satisfy the data path.
        script="""import http.client,json,time,os
nonce=%r
start=time.monotonic()
for i in range(20000):
 c=http.client.HTTPConnection('203.0.113.10',80,timeout=3)
 try:
  c.request('GET','/'+nonce+'/'+str(i));r=c.getresponse();b=r.read()
  assert r.status==200 and nonce.encode() in b
 finally:c.close()
 if i%%25==0:print(json.dumps({'completed':i+1,'seconds':time.monotonic()-start}),flush=True)
 if time.monotonic()-start>150:break
 time.sleep(.002)
""" % nonce
        data=subprocess.Popen(['python3','-c',script],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,user=1000,group=1000,extra_groups=[],env=env)
        progress=[]
        try:
            for line in data.stdout:
                item=json.loads(line);progress.append(item)
                inode=current.stat().st_ino
                if inode!=seen[-1]:seen.append(inode)
                if len(seen)>=3 or proc.poll() is not None or time.monotonic()-started>155:break
        finally:
            if data.poll() is None:data.terminate()
            data.wait(timeout=5)
            c['dataError']=data.stderr.read();data.stdout.close();data.stderr.close()
        c['progress']=progress;c['observedCurrentInodes']=seen;c['seconds']=time.monotonic()-started
        proc.send_signal(signal.SIGTERM);c['cliExit']=proc.wait(timeout=22)
        files=[p for p in LOGDIR.glob('xrayfa.log*') if p.is_file()]
        c['files']=[dict(name=p.name,size=p.stat().st_size,inode=p.stat().st_ino) for p in files]
        for p in files:(folder/p.name).write_bytes(p.read_bytes())
        c['serverNonce']=probe.cmd(['tail','-n','80',str(setup.STATE/'services.log')])
        if len(seen)<3 or c['cliExit']!=0 or c['dataError'] or not progress or nonce not in c['serverNonce']['stdout']:raise RuntimeError('Independent real-core flood did not achieve two observed rotations')
        if len(files)>4 or any(p.stat().st_size>5*1024*1024 for p in files) or any(p.name not in ['xrayfa.log','xrayfa.log.1','xrayfa.log.2','xrayfa.log.3'] for p in files):raise RuntimeError('Rotation retention/size limit violated')
        finish(c);c['status']='passed'
    except Exception as e:c['error']=str(e)
    finally:
        if proc and proc.poll() is None:
            proc.send_signal(signal.SIGTERM)
            try:proc.wait(timeout=22)
            except subprocess.TimeoutExpired:proc.kill();proc.wait(timeout=3)
        if Path('/run/xrayfa/session.json').exists():c['finalRecover']=probe.cmd([probe.CLI,'recover'],timeout=24)
        r['cases'].append(c);save();print(json.dumps({'id':c['id'],'status':c['status'],'error':c.get('error'),'seconds':c.get('seconds')}),flush=True)
    r['status']='passed' if all(c['status']=='passed' for c in r['cases']) else 'failed';save()
    raise SystemExit(r['status']!='passed')

if __name__=='__main__':main()
