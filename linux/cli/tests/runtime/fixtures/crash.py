#!/usr/bin/env python3
"""SIGKILL the actual controller at durable journal syscall boundaries."""
import argparse
import json
import os
from pathlib import Path

import probe
import setup


def main():
    os.umask(0o077)
    p=argparse.ArgumentParser();p.add_argument('--instance',required=True);p.add_argument('--out',type=Path,required=True);args=p.parse_args();setup.guard(args.instance)
    out=args.out.resolve()
    if not out.is_relative_to(setup.STATE/'scenarios'):raise RuntimeError('Private fixture output required')
    out.mkdir(parents=True,exist_ok=False,mode=0o700)
    if Path('/run/xrayfa/session.json').exists():raise RuntimeError('Retained journal preserved; resolve it before isolated cases')
    nodes=json.loads(Path('/home/xfa/.local/share/xrayfa/nodes.json').read_text())['nodes']
    node=next(n['id'] for n in nodes if '10.204.0.200:24402?' in n['url'])
    tracer='/opt/xrayfa-runtime/test-tools/trace_fault'
    r={'schemaVersion':1,'instance':args.instance,'boot':Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
       'cliSha256':probe.sha(probe.CLI),'coreSha256':probe.sha(probe.CORE),'fixtureSha256':probe.sha(__file__),'traceToolSha256':probe.sha(tracer),'cases':[]}
    def save():(out/'result.json').write_text(json.dumps(r,indent=2)+'\n')
    for label,phase in [('journal','leased'),('tool-gated','tool-gated'),('nft-committed','nft-committed'),('gate','core-gated'),('tun-created','tun-ready'),('dns-set','Running'),('cleanup','cleanup')]:
        folder=out/label;folder.mkdir(mode=0o700)
        c={'id':'CRASH-01/'+('journal' if label in ['tool-gated','nft-committed'] else label),'injectionPoint':label,'status':'failed','before':probe.snapshot(),'evidenceDirectory':label}
        try:
            command=probe.cmd([tracer,'kill-phase',args.instance,str(node),phase,str(folder/'trace-result.json')],timeout=42)
            (folder/'cli.log').write_text(command['stdout']+command['stderr']);c['traceCommand']=command
            trace=json.loads((folder/'trace-result.json').read_text());c['trace']=trace
            if command['exit']!=137 or not trace['phaseKilled'] or trace['terminationSignal']!=9 or trace['timedOut']:raise RuntimeError('Requested crash point was not reached and killed')
            c['cliExit']=None;c['terminationSignal']=9
            c['journal']=json.loads(Path('/run/xrayfa/session.json').read_text())
            if c['journal']['controllerPid']!=trace['controllerPid']:raise RuntimeError('Killed controller does not match journal')
            c['afterKill']=probe.snapshot()
            c['recover']=probe.cmd([probe.CLI,'recover'],timeout=24)
            c['recoverAgain']=probe.cmd([probe.CLI,'recover'],timeout=24)
            c['after']=probe.snapshot();c['baselineEqual']=probe.config_snapshot(c['before'])==probe.config_snapshot(c['after'])
            c['noJournal']=not Path('/run/xrayfa/session.json').exists()
            if c['recover']['exit']!=0 or c['recoverAgain']['exit']!=0 or not c['baselineEqual'] or not c['noJournal']:raise RuntimeError('First recovery or exact baseline check failed')
            c['status']='passed'
        except Exception as e:c['error']=str(e)
        finally:
            if Path('/run/xrayfa/session.json').exists():c['finalRecover']=probe.cmd([probe.CLI,'recover'],timeout=24)
            r['cases'].append(c);save();print(json.dumps({'id':c['id'],'status':c['status'],'error':c.get('error')}),flush=True)
        if Path('/run/xrayfa/session.json').exists():raise RuntimeError('Recovery incomplete; preserve failed journal and stop further cases')
    # Three distinct journal/WAL windows are independent assertions of one
    # frozen obligation. A later successful window cannot overwrite a failed one.
    windows=[c for c in r['cases'] if c['id']=='CRASH-01/journal']
    combined=dict(windows[0]);combined['injections']=windows
    combined['assertions']={c['injectionPoint']:c['status']=='passed' for c in windows}
    combined['status']='passed' if all(combined['assertions'].values()) else 'failed'
    r['cases']=[combined]+[c for c in r['cases'] if c['id']!='CRASH-01/journal']
    r['status']='passed' if all(c['status']=='passed' for c in r['cases']) else 'failed';save()
    raise SystemExit(r['status']!='passed')

if __name__=='__main__':main()
