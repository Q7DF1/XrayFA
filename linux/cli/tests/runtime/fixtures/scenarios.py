#!/usr/bin/env python3
"""Guest-only permission, diagnostic, storage and capability fault obligations."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

import setup
import probe

CLI=probe.CLI
CORE=probe.CORE
STATE=setup.STATE


def main():
    os.umask(0o077)
    p=argparse.ArgumentParser();p.add_argument('--instance',required=True);p.add_argument('--out',type=Path,required=True);args=p.parse_args();setup.guard(args.instance)
    if Path('/run/xrayfa/session.json').exists():raise RuntimeError('Active/retained journal must be resolved first')
    out=args.out.resolve()
    if not out.is_relative_to(STATE/'scenarios'):raise RuntimeError('Private fixture scenario path required')
    out.mkdir(parents=True,exist_ok=False,mode=0o700)
    state=json.loads((STATE/'state.json').read_text())
    result={'schemaVersion':1,'instance':args.instance,'boot':state['boot'],'cliSha256':probe.sha(CLI),'coreSha256':probe.sha(CORE),'fixtureSha256':probe.sha(__file__),'cases':[]}
    store=Path('/home/xfa/.local/share/xrayfa/nodes.json')
    original=store.read_bytes();st=store.stat();seed_id=json.loads(original)['nodes'][0]['id']
    def run_case(case_id,expected,action,assertion=None):
        record={'id':case_id,'expectedExit':expected,'status':'failed'}
        before=probe.snapshot()
        try:
            r=action();record['command']=r
            after=probe.snapshot();record['before']=before;record['after']=after
            record['networkUnchanged']=probe.config_snapshot(before)==probe.config_snapshot(after)
            record['noJournal']=not Path('/run/xrayfa/session.json').exists()
            record['assertion']=assertion(r) if assertion else True
            record['status']='passed' if r['exit']==expected and record['networkUnchanged'] and record['noJournal'] and record['assertion'] else 'failed'
        except Exception as e:record['error']=str(e)
        result['cases'].append(record)
        (out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
        print(json.dumps({k:record[k] for k in ['id','status']}),flush=True)
    def cli(*argv,user=False):return probe.cmd([CLI,*argv],user=user)
    for root in [False,True]:
        for fmt in ['text','json']:
            label=('root' if root else 'user')+'-'+fmt
            run_case('DIAG-01/'+label,0,lambda root=root,fmt=fmt:cli('doctor','--core',CORE,*(['--json'] if fmt=='json' else []),user=not root),
                lambda r,root=root,fmt=fmt: json.loads(r['stdout'])['uid']==1000 and json.loads(r['stdout'])['effectiveUid']==(0 if root else 1000) if fmt=='json' else 'profile=/home/xfa/.local/share/xrayfa' in r['stdout'])
    run_case('AUTH-01/unprivileged',4,lambda:cli('run',str(seed_id),'--core',CORE,user=True))
    run_case('STORE-01/missing',3,lambda:cli('run','2147483646','--core',CORE))
    def mutate_store(case,mutation):
        try:
            mutation()
            run_case('STORE-01/'+case,10,lambda:cli('run',str(seed_id),'--core',CORE))
        finally:
            if store.is_symlink():store.unlink()
            if not store.exists():store.touch(mode=0o600)
            store.write_bytes(original);os.chown(store,st.st_uid,st.st_gid);store.chmod(st.st_mode&0o777)
    mutate_store('corrupt',lambda:store.write_text('{corrupt'))
    mutate_store('owner',lambda:os.chown(store,0,0))
    mutate_store('permissions',lambda:store.chmod(0o644))
    sentinel=out/'sentinel';sentinel.write_text('unmodified')
    def symlink():store.unlink();store.symlink_to(sentinel)
    mutate_store('symlink',symlink)
    if sentinel.read_text()!='unmodified':raise RuntimeError('Foreign symlink target modified')
    run_case('PRE-01/missing-core',5,lambda:cli('run',str(seed_id),'--core','/opt/xrayfa-runtime/missing-core'))
    nonexec=out/'nonexec-core';shutil.copyfile(CORE,nonexec);nonexec.chmod(0o600)
    run_case('PRE-01/nonexec-core',4,lambda:cli('run',str(seed_id),'--core',str(nonexec)))
    nonexec.unlink()
    # Dependencies are hidden only for the duration of a VM-local case and restored
    # by the fixture finally block. The product must never repair these test faults.
    # Snapshots while dependencies are hidden cannot invoke nft: take before/after outside
    # the injection, record the command result inside, then restore before comparison.
    def dependency(case,path):
        path=Path(path);saved=path.with_name('.xrayfa-saved-'+path.name+'-'+str(os.getpid()))
        if saved.exists() or saved.is_symlink():raise RuntimeError('Dependency backup already exists')
        def action():
            path.rename(saved)
            try:return cli('run',str(seed_id),'--core',CORE)
            finally:saved.rename(path)
        run_case(case,5,action)
    dependency('PRE-02/tun','/dev/net/tun')
    dependency('PRE-02/tool','/usr/sbin/nft')
    resolver=Path('/etc/resolv.conf')
    def unsupported_resolver():
        saved=out/'saved-resolv.conf';resolver.rename(saved)
        try:
            resolver.symlink_to('/run/systemd/resolve/resolv.conf')
            return cli('run',str(seed_id),'--core',CORE)
        finally:
            resolver.unlink();saved.rename(resolver)
    run_case('PRE-02/resolver',5,unsupported_resolver)
    if Path('/usr/sbin/capsh').exists():
        command=f'exec {CLI} run {seed_id} --core {CORE}'
        run_case('PRE-02/capability',5,lambda:probe.cmd(['/usr/sbin/capsh','--drop=cap_net_admin','--','-c',command]))
    if store.read_bytes()!=original or store.stat().st_uid!=st.st_uid or store.stat().st_mode!=st.st_mode:raise RuntimeError('Store not restored after fixture mutations')
    result['status']='passed' if all(c['status']=='passed' for c in result['cases']) else 'failed'
    (out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({'status':result['status'],'cases':len(result['cases'])}))
    raise SystemExit(result['status']!='passed')

if __name__=='__main__':main()
