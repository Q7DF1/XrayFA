#!/usr/bin/env python3
"""Real-core VM lifecycle, conflict, external-change and recovery obligations."""
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import time
import uuid

import probe
import setup

CLI=probe.CLI
CORE=probe.CORE


def main():
    os.umask(0o077)
    p=argparse.ArgumentParser();p.add_argument('--instance',required=True);p.add_argument('--out',type=Path,required=True);args=p.parse_args();setup.guard(args.instance)
    out=args.out.resolve()
    if not out.is_relative_to(setup.STATE/'scenarios'):raise RuntimeError('Private fixture scenario path required')
    out.mkdir(parents=True,exist_ok=False,mode=0o700)
    if Path('/run/xrayfa/session.json').exists():raise RuntimeError('Existing journal must be resolved first')
    nodes=json.loads(Path('/home/xfa/.local/share/xrayfa/nodes.json').read_text())['nodes']
    node=next(n['id'] for n in nodes if '10.204.0.200:24402?' in n['url'])
    report={'schemaVersion':1,'instance':args.instance,'boot':Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
        'cliSha256':probe.sha(CLI),'coreSha256':probe.sha(CORE),'fixtureSha256':probe.sha(__file__),'cases':[]}
    env={'PATH':'/usr/sbin:/usr/bin:/sbin:/bin','LANG':'C','LC_ALL':'C','SUDO_UID':'1000'}
    def save():(out/'result.json').write_text(json.dumps(report,indent=2)+'\n')
    def cli(*argv,timeout=24):return probe.cmd([CLI,*argv],timeout=timeout)
    def recover():return cli('recover')
    def run_case(case_id,action):
        folder=out/case_id.replace('/','-');folder.mkdir(mode=0o700)
        r={'id':case_id,'status':'failed','before':probe.snapshot(),'evidenceDirectory':folder.name};proc=None;log=None;foreign_route=False
        def start(prefix=None):
            nonlocal proc,log
            log=open(folder/'cli.log','wb')
            proc=subprocess.Popen((prefix or [])+[CLI,'run',str(node),'--core',CORE,'--log-level','debug'],stdout=log,stderr=log,env=env)
            r['launcher']=probe.pid_record(proc.pid)
            end=time.monotonic()+16
            while time.monotonic()<end and proc.poll() is None:
                if 'stage=Running' in (folder/'cli.log').read_text():break
                time.sleep(.025)
            r['running']='stage=Running' in (folder/'cli.log').read_text()
            if not r['running']:raise RuntimeError('Real core did not reach Running')
            r['journal']=json.loads(Path('/run/xrayfa/session.json').read_text());r['controller']=probe.pid_record(r['journal']['controllerPid'])
            r['core']=probe.pid_record(r['journal']['corePid'])
            if proc.pid!=r['journal']['controllerPid']:raise RuntimeError('Unexpected launcher/controller relationship')
            return r['journal']
        def wait(expected):
            try:code=proc.wait(timeout=22)
            except subprocess.TimeoutExpired:raise RuntimeError('Session exit exceeded deadline')
            r['terminationSignal']=-code if code<0 else None;r['cliExit']=code if code>=0 else None
            if code!=expected:raise RuntimeError(f'Expected process result {expected}, observed {code}')
        try:
            if action in ['SIGINT','SIGTERM','SIGHUP']:
                start();proc.send_signal(getattr(signal,action));wait(0);r['expectedExit']=0
            elif action=='active-recover':
                start();during=probe.snapshot();r['command']=recover()
                r['assertion']=r['command']['exit']==8 and probe.config_snapshot(during)==probe.config_snapshot(probe.snapshot()) and probe.pid_record(proc.pid)==r['controller']
                proc.send_signal(signal.SIGTERM);wait(0);r['expectedExit']=8
                if not r['assertion']:raise RuntimeError('Active recovery was not safely refused')
            elif action=='duplicate':
                start();old=Path('/run/xrayfa/session.json').read_bytes();during=probe.snapshot()
                r['command']=cli('run',str(node),'--core',CORE)
                r['assertion']=r['command']['exit']==8 and Path('/run/xrayfa/session.json').read_bytes()==old and probe.config_snapshot(during)==probe.config_snapshot(probe.snapshot())
                proc.send_signal(signal.SIGTERM);wait(0);r['expectedExit']=8
                if not r['assertion']:raise RuntimeError('Duplicate start altered active state')
            elif action in ['nft-deleted','interface-deleted','route-changed']:
                j=start()
                if action=='nft-deleted':r['fault']=probe.cmd(['nft','delete','table','inet',j['nftTable']])
                elif action=='interface-deleted':r['fault']=probe.cmd(['ip','link','delete','xrayfa0'])
                else:
                    r['fault']=probe.cmd(['ip','route','add','203.0.113.77/32','via','10.203.0.2','dev','xfa-underlay','metric','456']);foreign_route=r['fault']['exit']==0
                if r['fault']['exit']!=0:raise RuntimeError('Fault injection command failed')
                wait(9 if action=='route-changed' else 7);r['expectedExit']=9 if action=='route-changed' else 7
                if action=='route-changed':
                    r['retainedJournal']=Path('/run/xrayfa/session.json').exists()
                    r['foreignRoute']=probe.cmd(['ip','-j','route','show','203.0.113.77/32'])
                    routes=json.loads(r['foreignRoute']['stdout'])
                    if not r['retainedJournal'] or not any(x.get('metric')==456 for x in routes):raise RuntimeError('Foreign route or journal not preserved')
                    r['removeFault']=probe.cmd(['ip','route','del','203.0.113.77/32','via','10.203.0.2','dev','xfa-underlay','metric','456']);foreign_route=False
                    if r['removeFault']['exit']!=0:raise RuntimeError('Cannot remove fixture route fault')
            elif action in ['core-kill','both-kill','live-orphan']:
                j=start()
                if action!='core-kill':proc.send_signal(signal.SIGKILL)
                if action!='live-orphan':
                    if probe.pid_record(j['corePid'])!=r['core']:raise RuntimeError('Core identity changed before injection')
                    os.kill(j['corePid'],signal.SIGKILL)
                wait(7 if action=='core-kill' else -9);r['expectedExit']=7 if action=='core-kill' else None
                if action=='live-orphan':
                    r['orphan']=probe.pid_record(j['corePid'])
                    if r['orphan']!=r['core']:raise RuntimeError('Expected live orphan not observed')
            elif action=='exec-denied':
                tracer='/opt/xrayfa-runtime/test-tools/trace_fault'
                r['traceToolSha256']=probe.sha(tracer)
                log=open(folder/'cli.log','wb')
                proc=subprocess.Popen([tracer,'exec-deny',args.instance,str(node),'unused',str(folder/'trace-result.json')],stdout=log,stderr=log,env=env)
                wait(7);r['expectedExit']=7
                trace=json.loads((folder/'trace-result.json').read_text());r['trace']=trace
                if not trace['injectedCoreExec'] or trace['normalExit']!=7 or trace['timedOut']:raise RuntimeError('Core-specific exec failure injection not observed')
            r['recover']=recover();r['recoverAgain']=recover();r['after']=probe.snapshot()
            r['baselineEqual']=probe.config_snapshot(r['before'])==probe.config_snapshot(r['after'])
            r['noJournal']=not Path('/run/xrayfa/session.json').exists()
            if r['recover']['exit']!=0 or r['recoverAgain']['exit']!=0 or not r['baselineEqual'] or not r['noJournal']:raise RuntimeError('Recovery did not restore exact baseline')
            if r.get('core') and probe.pid_record(r['core']['pid'])==r['core']:raise RuntimeError('Owned core remains after recovery')
            r['status']='passed'
        except Exception as e:r['error']=str(e)
        finally:
            if proc is not None and proc.poll() is None:
                proc.send_signal(signal.SIGTERM)
                try:proc.wait(timeout=22)
                except subprocess.TimeoutExpired:proc.kill();proc.wait(timeout=3)
            if foreign_route:r['fixtureEmergencyRouteRemoval']=probe.cmd(['ip','route','del','203.0.113.77/32','via','10.203.0.2','dev','xfa-underlay','metric','456'])
            if log is not None:log.close()
            if Path('/run/xrayfa/session.json').exists():r['finalRecover']=recover()
            report['cases'].append(r);save();print(json.dumps({'id':case_id,'status':r['status'],'error':r.get('error')}),flush=True)
            if Path('/run/xrayfa/session.json').exists():raise RuntimeError('Retained journal blocks further isolated cases; preserve evidence')
    for case,action in [('EXIT-01/sigint','SIGINT'),('EXIT-01/sigterm','SIGTERM'),('EXIT-01/sighup','SIGHUP'),
                        ('REC-01/active','active-recover'),('LEASE-01/duplicate','duplicate'),
                        ('EXT-01/nft-deleted','nft-deleted'),('EXT-01/interface-deleted','interface-deleted'),('EXT-01/route-changed','route-changed'),
                        ('CRASH-02/core-kill','core-kill'),('CRASH-02/both-kill','both-kill'),('CRASH-02/live-orphan','live-orphan'),('PROC-01/exec','exec-denied')]:
        run_case(case,action)
    report['status']='passed' if all(c['status']=='passed' for c in report['cases']) else 'failed';save()
    print(json.dumps({'status':report['status'],'cases':len(report['cases'])}))
    raise SystemExit(report['status']!='passed')

if __name__=='__main__':main()
