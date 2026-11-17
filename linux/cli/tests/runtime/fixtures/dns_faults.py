#!/usr/bin/env python3
"""Fail actual resolver mutations after partial apply and during restoration."""
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
    if not out.is_relative_to(setup.STATE/'scenarios'):raise RuntimeError('Private scenario path required')
    out.mkdir(parents=True,exist_ok=False,mode=0o700)
    if Path('/run/xrayfa/session.json').exists():raise RuntimeError('Retained journal preserved')
    nodes=json.loads(Path('/home/xfa/.local/share/xrayfa/nodes.json').read_text())['nodes']
    node=next(n['id'] for n in nodes if '10.204.0.200:24402?' in n['url'])
    tracer='/opt/xrayfa-runtime/test-tools/trace_fault'
    r={'schemaVersion':1,'instance':args.instance,'boot':Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
       'cliSha256':probe.sha(probe.CLI),'coreSha256':probe.sha(probe.CORE),'fixtureSha256':probe.sha(__file__),'traceToolSha256':probe.sha(tracer),'cases':[]}
    def save():(out/'result.json').write_text(json.dumps(r,indent=2)+'\n')
    for label,mode,expected in [('partial-apply','dns-partial',7),('restore-failure','dns-restore',9)]:
        folder=out/label;folder.mkdir(mode=0o700)
        c={'id':'DNS-02/'+label,'status':'failed','expectedExit':expected,'before':probe.snapshot(),'evidenceDirectory':label}
        try:
            command=probe.cmd([tracer,mode,args.instance,str(node),'unused',str(folder/'trace-result.json')],timeout=42)
            (folder/'cli.log').write_text(command['stdout']+command['stderr']);c['traceCommand']=command
            trace=json.loads((folder/'trace-result.json').read_text());c['trace']=trace
            c['cliExit']=trace['normalExit'];c['terminationSignal']=trace['terminationSignal'] or None
            if command['exit']!=expected or trace['normalExit']!=expected or not trace['injectedResolverStep'] or trace['timedOut']:raise RuntimeError('Expected resolver failure was not observed')
            injection=json.loads((folder/'inject-journal.json').read_text());c['injectionJournal']=injection
            index=str(injection['tunAfter']['ifindex'])
            if label=='partial-apply':
                link=injection['resolverAfter']['structured']['links'][index]
                if link['dns']!=['198.18.0.2'] or link['domains']!=[]:raise RuntimeError('DNS partial apply did not occur before injected domain failure')
                if injection['pendingTool']['argv'][1]!='domain':raise RuntimeError('Wrong resolver step failed')
            else:
                if injection['phase']!='Running' or injection['pendingTool']['argv'][1]!='revert':raise RuntimeError('Restore failure did not occur after Running')
                if not Path('/run/xrayfa/session.json').exists():raise RuntimeError('Incomplete recovery journal was removed')
                c['retainedJournal']=json.loads(Path('/run/xrayfa/session.json').read_text())
                if not c['retainedJournal'].get('recoveryErrors'):raise RuntimeError('Recovery failure reason not retained')
            c['afterClient']=probe.snapshot()
            c['recover']=probe.cmd([probe.CLI,'recover'],timeout=24)
            c['recoverAgain']=probe.cmd([probe.CLI,'recover'],timeout=24)
            c['after']=probe.snapshot();c['baselineEqual']=probe.config_snapshot(c['before'])==probe.config_snapshot(c['after'])
            c['noJournal']=not Path('/run/xrayfa/session.json').exists()
            if c['recover']['exit']!=0 or c['recoverAgain']['exit']!=0 or not c['baselineEqual'] or not c['noJournal']:raise RuntimeError('Recovery retry or exact baseline restoration failed')
            c['status']='passed'
        except Exception as e:c['error']=str(e)
        finally:
            if Path('/run/xrayfa/session.json').exists():c['finalRecover']=probe.cmd([probe.CLI,'recover'],timeout=24)
            r['cases'].append(c);save();print(json.dumps({'id':c['id'],'status':c['status'],'error':c.get('error')}),flush=True)
        if Path('/run/xrayfa/session.json').exists():raise RuntimeError('Retained failure blocks further isolated cases')
    r['status']='passed' if all(c['status']=='passed' for c in r['cases']) else 'failed';save()
    raise SystemExit(r['status']!='passed')

if __name__=='__main__':main()
