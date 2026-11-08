#!/usr/bin/env python3
"""Execute frozen runtime obligations in the identity-verified private VM."""
import argparse
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shlex
import subprocess
import tarfile
import time
import uuid

spec=importlib.util.spec_from_file_location('vm',Path(__file__).resolve().parents[1]/'vm/manage.py')
vm=importlib.util.module_from_spec(spec);spec.loader.exec_module(vm)


def main():
    os.umask(0o077)
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--vm-state',type=Path,required=True);p.add_argument('--manifest',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--seeds',nargs='+');p.add_argument('--idle-seconds',type=int,default=0)
    p.add_argument('--scenario-group',choices=['preflight','preflight-extra','process-extra','recovery-extra','log-extra','idle-extra','path-extra','external-dns-extra','base-extra','log-failure-extra','log-write-extra','log-full-extra','boot-extra','data-extra','lease-extra','lifecycle','crash','dns-faults','recovery-refusals','log-faults']);p.add_argument('--only-scenarios',action='store_true');p.add_argument('--bootstrap-hostname')
    args=p.parse_args();w=args.vm_state.resolve().parent;s=json.loads(args.vm_state.read_text());identity=vm.verify(w,s)
    payload=args.manifest.read_bytes();digest=hashlib.sha256(payload).hexdigest()
    if args.manifest.with_suffix('.sha256').read_text().split()[0]!=digest:raise RuntimeError('Frozen manifest digest mismatch')
    manifest=json.loads(payload);allseeds=list(dict.fromkeys(c['id'].split('/')[0] for c in manifest['cases'] if c['category']=='runtime'))
    seeds=[] if args.only_scenarios else (args.seeds or allseeds)
    if (args.only_scenarios and not args.scenario_group) or len(set(seeds))!=len(seeds) or any(x not in allseeds for x in seeds):raise RuntimeError('Unknown or duplicate seed selection')
    out=args.out.resolve();out.mkdir(parents=True,exist_ok=False,mode=0o700)
    runid=time.strftime('%Y%m%dT%H%M%S',time.gmtime())+'-'+uuid.uuid4().hex[:8]
    results={'schemaVersion':1,'runId':runid,'manifestSha256':digest,'guest':identity,'status':'running','cases':[
        {'id':c['id'],'mandatory':c['mandatory'],'status':'not-executed','category':c['category']} for c in manifest['cases']], 'probes':[]}
    def save():
        counts={}
        for c in results['cases']:counts[c['status']]=counts.get(c['status'],0)+1
        results['counts']=counts
        dispositions={}
        for c in results['cases']:
            if c['status']=='passed':
                d=c.get('disposition','positive');dispositions[d]=dispositions.get(d,0)+1
        results['passedByDisposition']=dispositions
        temp=out/'report.tmp';temp.write_text(json.dumps(results,indent=2)+'\n');temp.replace(out/'report.json')
        (out/'report.md').write_text('# Runtime acceptance\n\n'+f'Run `{runid}`, manifest `{digest}`.\n\nStatus: {results["status"]}.\n\n'+
            '\n'.join(f'- {k}: {v}' for k,v in counts.items())+'\n\n'+
            '\n'.join(f'- {c["id"]}: {c["status"]}' for c in results['cases'])+'\n')
    def guest(argv,timeout=15,**kw):
        vm.verify(w,s)
        return subprocess.run([str(x) for x in vm.ssh(w,s)]+[shlex.join(argv)],capture_output=True,timeout=timeout,**kw)
    def unpack(collected,label):
        folder=out/label;folder.mkdir(mode=0o700)
        with tarfile.open(fileobj=io.BytesIO(collected.stdout)) as archive:
            for item in archive.getmembers():
                relative=Path(item.name)
                if relative.is_absolute() or '..' in relative.parts or not (item.isdir() or item.isfile()):raise RuntimeError('Unsafe evidence archive member')
                dest=folder/relative
                if item.isdir():dest.mkdir(parents=True,exist_ok=True,mode=0o700)
                else:
                    dest.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
                    with dest.open('xb') as f:f.write(archive.extractfile(item).read())
        return folder
    save()
    try:
        for seed in seeds:
            remote=f'/var/lib/xrayfa-fixture/probes/{runid}/{seed}'
            vm.verify(w,s)
            with open(out/(seed+'.console.log'),'wb') as log:
                proc=subprocess.Popen([str(x) for x in vm.ssh(w,s)]+[shlex.join(['sudo','python3','/opt/xrayfa-runtime/fixtures/probe.py','--instance',s['instance'],'--seed',seed,'--out',remote,'--idle-seconds',str(args.idle_seconds),*(['--bootstrap-hostname',args.bootstrap_hostname] if args.bootstrap_hostname else [])])],stdout=log,stderr=log)
                # The process remains observable across tool yields; timeout never restarts a probe.
                while proc.poll() is None:time.sleep(.1)
            collected=guest(['sudo','tar','-C',remote,'-cf','-','.'],timeout=25)
            if collected.returncode!=0:raise RuntimeError('Probe evidence collection failed for '+seed)
            folder=unpack(collected,seed)
            r=json.loads((folder/'result.json').read_text())
            r['manifestSha256']=digest
            if r['instance']!=s['instance'] or r['coreSha256']!=manifest['coreSha256']:raise RuntimeError('Probe identity/core digest mismatch')
            results['probes'].append({'seed':seed,'status':r['status'],'cliSha256':r['cliSha256'],'coreSha256':r['coreSha256'],'fixtureSha256':r['fixtureSha256'],'serverConfigSha256':r['serverConfigSha256'],'boot':r['boot'],'sshExit':proc.returncode,'evidence':str(folder.relative_to(out))})
            for case in results['cases']:
                if case['id'].startswith(seed+'/'):
                    action=case['id'].split('/')[1]
                    common=all(r['assertions'].get(k,False) for k in ['three-sides','dns-no-bypass','immutable-config','core-survived-request-failure'])
                    case['status']='passed' if r['assertions'].get(action,False) and common else 'failed'
                    case['evidence']=str((folder/'result.json').relative_to(out));case['assertion']=action
                    case['disposition']='expected-block' if action=='udp' and next(c for c in manifest['cases'] if c['id']==case['id'])['udpPolicy']=='block' else 'positive'
            if args.idle_seconds:
                case=next(c for c in results['cases'] if c['id']=='LOG-01/idle-70s')
                case.update(status='passed' if r['assertions'].get('long-idle',False) else 'failed',evidence=str((folder/'result.json').relative_to(out)),disposition='positive')
            save();print(json.dumps(results['probes'][-1]),flush=True)
        if args.scenario_group:
            group=args.scenario_group
            remote=f'/var/lib/xrayfa-fixture/scenarios/{runid}-{group}'
            script={'preflight':'scenarios.py','preflight-extra':'preflight_extra.py','process-extra':'process_extra.py','recovery-extra':'recovery_extra.py','log-extra':'log_extra.py','idle-extra':'idle_extra.py','path-extra':'path_extra.py','external-dns-extra':'external_dns_extra.py','base-extra':'base_extra.py','log-failure-extra':'log_failure_extra.py','log-write-extra':'log_failure_extra.py','log-full-extra':'log_failure_extra.py','boot-extra':'boot_extra.py','data-extra':'data_extra.py','lease-extra':'lease_extra.py','lifecycle':'lifecycle.py','crash':'crash.py','dns-faults':'dns_faults.py','recovery-refusals':'recovery_refusals.py','log-faults':'log_faults.py'}[group]
            scenarioArgs=['sudo','python3','/opt/xrayfa-runtime/fixtures/'+script,'--instance',s['instance'],'--out',remote]
            if group=='log-write-extra': scenarioArgs.append('--only-write')
            if group=='log-full-extra': scenarioArgs.append('--only-full')
            if group=='boot-extra':
                scenarioArgs += ['--phase','prepare']
                probeResult=guest(scenarioArgs,timeout=25)
                old_boot=identity['boot'];reboot_deadline=time.monotonic()+100
                while time.monotonic()<reboot_deadline:
                    try:
                        if vm.verify(w,s)['boot']!=old_boot:break
                    except Exception:pass
                    time.sleep(1)
                else:raise RuntimeError('VM did not return with a new boot identity')
                probeResult=guest(['sudo','python3','/opt/xrayfa-runtime/fixtures/boot_extra.py','--instance',s['instance'],'--out',remote,'--phase','collect'],timeout=120)
            else:
                probeResult=guest(scenarioArgs,timeout=300)
            (out/(group+'.console.log')).write_bytes(probeResult.stdout+probeResult.stderr)
            collected=guest(['sudo','tar','-C',remote,'-cf','-','.'],timeout=25)
            if collected.returncode!=0:raise RuntimeError('Scenario evidence collection failed')
            folder=unpack(collected,group)
            r=json.loads((folder/'result.json').read_text())
            if r['instance']!=s['instance'] or r['coreSha256']!=manifest['coreSha256']:raise RuntimeError('Scenario identity/core mismatch')
            if len({c['id'] for c in r['cases']})!=len(r['cases']):raise RuntimeError('Unaggregated duplicate scenario IDs; evidence preserved')
            for actual in r['cases']:
                case=next((c for c in results['cases'] if c['id']==actual['id']),None)
                if case is None:raise RuntimeError('Scenario absent from frozen manifest: '+actual['id'])
                obligation=next(c for c in manifest['cases'] if c['id']==actual['id'])
                expected=obligation['expected']
                observed=actual.get('command',{}).get('exit') if 'command' in actual else actual.get('cliExit')
                contract=observed==expected['cliExit'] and actual.get('terminationSignal')==expected.get('terminationSignal')
                disposition='positive' if observed==0 else 'expected-signal' if observed is None else 'expected-incomplete-recovery' if observed==9 else 'expected-runtime-failure' if observed==7 else 'expected-rejection'
                case.update(status='passed' if actual['status']=='passed' and contract else 'failed',
                    evidence=group+'/result.json',observedExit=observed,observedSignal=actual.get('terminationSignal'),manifestContractMatches=contract,
                    cliSha256=r['cliSha256'],coreSha256=r['coreSha256'],fixtureSha256=r['fixtureSha256'],disposition=disposition)
            save()
        mandatory=[c for c in results['cases'] if c['mandatory']]
        results['status']='passed' if all(c['status']=='passed' for c in mandatory) else 'incomplete'
    except Exception as e:
        results['status']='failed';results['runnerError']=str(e)
    finally:save()
    print(json.dumps({'runId':runid,'status':results['status'],'counts':results['counts']}),flush=True)
    raise SystemExit(results['status']!='passed')

if __name__=='__main__':main()
