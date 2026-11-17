#!/usr/bin/env python3
"""Unsafe-journal and foreign-interface recovery refusals in the dedicated VM."""
import argparse
import copy
import json
import os
from pathlib import Path
import signal
import subprocess
import probe
import setup


def main():
    os.umask(0o077)
    p=argparse.ArgumentParser();p.add_argument('--instance',required=True);p.add_argument('--out',type=Path,required=True);args=p.parse_args();setup.guard(args.instance)
    out=args.out.resolve()
    if not out.is_relative_to(setup.STATE/'scenarios'):raise RuntimeError('Private fixture output required')
    out.mkdir(parents=True,exist_ok=False,mode=0o700)
    journal=Path('/run/xrayfa/session.json')
    if journal.exists():raise RuntimeError('Existing journal preserved')
    sources=sorted(Path('/var/log/xrayfa/1000').glob('recovery-*.json'),key=lambda p:p.stat().st_mtime,reverse=True)
    source=next(p for p in sources if json.loads(p.read_text()).get('tunAfter'))
    base=json.loads(source.read_text());base['phase']='Running'
    # The source is an actually completed session with immutable config and identities.
    # No production resource is created by forging these negative-test input claims.
    r={'schemaVersion':1,'instance':args.instance,'boot':Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
       'cliSha256':probe.sha(probe.CLI),'coreSha256':probe.sha(probe.CORE),'fixtureSha256':probe.sha(__file__),'sourceJournal':str(source),'cases':[]}
    def save():(out/'result.json').write_text(json.dumps(r,indent=2)+'\n')
    for name in ['wrong-exe','pid-reuse','corrupt-journal','cross-boot','foreign-interface']:
        folder=out/name;folder.mkdir(mode=0o700);candidate=copy.deepcopy(base);foreign=None;interface=None
        c={'id':'REC-01/'+name,'status':'failed','expectedExit':9}
        try:
            if name=='wrong-exe':
                foreign=subprocess.Popen(['/usr/bin/sleep','120'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
                identity=probe.pid_record(foreign.pid)
                if not identity:raise RuntimeError('Foreign process not alive')
                candidate['corePid']=foreign.pid;candidate['coreStart']=identity['start'];c['foreignBefore']=identity
            if name=='pid-reuse':
                foreign=subprocess.Popen(['/usr/bin/sleep','120'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
                identity=probe.pid_record(foreign.pid)
                if not identity:raise RuntimeError('PID-reuse fixture process not alive')
                candidate['corePid']=foreign.pid
                candidate['coreStart']=base['coreStart']
                c['foreignBefore']=identity
                c['reusedPid']=foreign.pid
            if name=='cross-boot':candidate['bootId']='00000000-0000-0000-0000-000000000000'
            if name=='foreign-interface':
                created=probe.cmd(['ip','link','add','xrayfa0','type','dummy'])
                if created['exit']!=0:raise RuntimeError('Foreign interface fixture failed')
                interface=json.loads(probe.cmd(['ip','-j','link','show','dev','xrayfa0'])['stdout'])[0]
                c['foreignInterface']=interface
            data=b'{corrupt' if name=='corrupt-journal' else (json.dumps(candidate,indent=2)+'\n').encode()
            journal.write_bytes(data);journal.chmod(0o600);(folder/'injected-journal.json').write_bytes(data)
            c['before']=probe.snapshot();c['command']=probe.cmd([probe.CLI,'recover'],timeout=24);c['after']=probe.snapshot()
            c['networkUnchanged']=probe.config_snapshot(c['before'])==probe.config_snapshot(c['after'])
            c['journalRetained']=journal.exists()
            if foreign:
                c['foreignAfter']=probe.pid_record(foreign.pid)
                if c['foreignAfter']!=c['foreignBefore']:raise RuntimeError('Foreign process was changed/stopped')
            if c['command']['exit']!=9 or not c['networkUnchanged'] or not c['journalRetained']:raise RuntimeError('Unsafe recovery did not preserve foreign state and journal')
            # Removing only the fixture fault restores the original completed-session
            # record, then the product must complete recovery itself.
            journal.write_text(json.dumps(base,indent=2)+'\n');journal.chmod(0o600)
            if interface:
                now=json.loads(probe.cmd(['ip','-j','link','show','dev','xrayfa0'])['stdout'])[0]
                if now['ifindex']!=interface['ifindex']:raise RuntimeError('Foreign fixture interface identity changed')
                probe.cmd(['ip','link','delete','xrayfa0']);interface=None
            if name=='pid-reuse' and foreign:
                foreign.send_signal(signal.SIGTERM);foreign.wait(timeout=3);foreign=None
            c['afterFaultRemoval']=probe.cmd([probe.CLI,'recover'],timeout=24)
            if c['afterFaultRemoval']['exit']!=0 or journal.exists():raise RuntimeError('Recovery did not complete after removing fixture fault')
            c['status']='passed'
        except Exception as e:c['error']=str(e)
        finally:
            if foreign:
                foreign.send_signal(signal.SIGTERM);foreign.wait(timeout=3)
            if interface:
                now=probe.cmd(['ip','-j','link','show','dev','xrayfa0'])
                if now['exit']==0 and json.loads(now['stdout'])[0]['ifindex']==interface['ifindex']:probe.cmd(['ip','link','delete','xrayfa0'])
            if journal.exists():
                # Preserve failure bytes separately; never discard an actual product lease.
                (folder/'retained-failure-journal.json').write_bytes(journal.read_bytes())
                journal.write_text(json.dumps(base,indent=2)+'\n');journal.chmod(0o600)
                c['finalRecover']=probe.cmd([probe.CLI,'recover'],timeout=24)
            r['cases'].append(c);save();print(json.dumps({'id':c['id'],'status':c['status'],'error':c.get('error')}),flush=True)
        if journal.exists():raise RuntimeError('Retained recovery failure stops further isolation')
    r['status']='passed' if all(c['status']=='passed' for c in r['cases']) else 'failed';save()
    raise SystemExit(r['status']!='passed')

if __name__=='__main__':main()
