"""Run the frozen manifest through the actual executable and designated Xray -test.
All credentials are synthetic. Artifact directories are private; final JSON contains secrets.
"""
import argparse, base64, collections, hashlib, json, os, platform, subprocess, time
from urllib.parse import urlsplit,unquote
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--manifest',type=Path,default=Path(__file__).with_name('manifest-full.json'))
p.add_argument('--cli',type=Path,default=Path('linux/cli/build/bin/linuxX64/debugExecutable/xrayfa.kexe'))
p.add_argument('--core',type=Path,default=Path('/usr/local/bin/xray'))
p.add_argument('--out',type=Path,required=True)
a=p.parse_args();os.umask(0o077)
a.cli=a.cli.resolve();a.core=a.core.resolve();a.out=a.out.resolve();a.out.mkdir(parents=True,exist_ok=False)
manifest_path=a.manifest
raw=manifest_path.read_bytes();manifest=json.loads(raw)
expected_hash=manifest_path.with_suffix('.sha256').read_text().split()[0]
assert hashlib.sha256(raw).hexdigest()==expected_hash
(a.out/'manifest.json').write_bytes(raw)
cli_hash=hashlib.sha256(a.cli.read_bytes()).hexdigest()
results=[]
def invoke(args,env):
    return subprocess.run([str(a.cli),*args],env=env,capture_output=True,text=True,timeout=45)
def field(data,path):
    for key in path.split('.'): data=data[int(key)] if isinstance(data,list) else data[key]
    return data
for i,case in enumerate(manifest['cases']):
    directory=a.out/f'{i+1:04d}';directory.mkdir()
    r={'id':case['id'],'protocol':case['protocol'],'expected':case['expected'],'caseDirectory':str(directory),'mainCell':case.get('mainCell'),'status':'failed','failures':[],'configPath':None,'configSha256':None,'coreExitCode':None,'logPath':None,'fieldAssertions':[]}
    try:
        if case['expected']=='not-applicable':
            r['status']='not-applicable';r['reason']=case['reason']
        else:
            env={**os.environ,'XRAYFA_HOME':str(directory/'profile')}
            linkfile=directory/'input.txt';linkfile.write_text(case['input']+'\n')
            add=invoke(['add','--link-file',str(linkfile)],env)
            (directory/'add.stdout.txt').write_text(add.stdout);(directory/'add.stderr.txt').write_text(add.stderr)
            r['addExitCode']=add.returncode
            if case['expected']=='reject':
                assert add.returncode==6,f'Expected add rejection 6, got {add.returncode}'
                assert not (directory/'profile/.local/share/xrayfa/nodes.json').exists(),'Rejected input persisted'
                r['status']='rejected'
            else:
                assert add.returncode==0,f'add failed {add.returncode}: {add.stderr}'
                listing=invoke(['ls'],env);(directory/'ls.stdout.txt').write_text(listing.stdout)
                assert listing.returncode==0 and '1:' in listing.stdout
                store=json.loads((directory/'profile/.local/share/xrayfa/nodes.json').read_text())
                assert store['nodes'][0]['url']==case['input'],'Original input not preserved'
                config=directory/'runtime.json'
                check=invoke(['check','1','--output',str(config),'--core',str(a.core),'--log-level','debug'],env)
                (directory/'check.stdout.txt').write_text(check.stdout);(directory/'check.stderr.txt').write_text(check.stderr)
                r['checkExitCode']=check.returncode
                assert config.exists(),'No final JSON saved'
                r['configPath']=str(config);r['configSha256']=hashlib.sha256(config.read_bytes()).hexdigest()
                data=json.loads(config.read_text())
                checks={
                    'inbounds.0.protocol':'tun','inbounds.0.settings.name':'xrayfa0','inbounds.0.settings.mtu':1500,
                    'inbounds.0.settings.gateway':['198.18.0.1/30'],
                    'inbounds.0.settings.autoSystemRoutingTable':['0.0.0.0/1','128.0.0.0/1'],
                    'inbounds.0.settings.autoSystemDnsToGateway':False,'inbounds.0.settings.autoOutboundsInterface':'auto',
                    'outbounds.0.tag':'proxy','outbounds.1.protocol':'dns','outbounds.2.protocol':'blackhole',
                    'log.access':'none','log.loglevel':'debug','dns.queryStrategy':'UseIPv4',
                    **case['assertions']}
                # Independently check protocol and endpoint against stored Node summary.
                n=store['nodes'][0];proto={'ss':'shadowsocks','hysteria2':'hysteria','hy2':'hysteria','socks5':'socks'}.get(n['protocol'],n['protocol'])
                checks['outbounds.0.protocol']=proto
                if proto in ('vless','vmess'): prefix='outbounds.0.settings.vnext.0'
                elif proto=='hysteria': prefix='outbounds.0.settings'
                else: prefix='outbounds.0.settings.servers.0'
                checks[prefix+'.address']=n['address'];checks[prefix+'.port']=n['port']
                # Independent credential decoding; no Kotlin parser or generated output is used as the oracle.
                uri=case['input']; scheme=uri.split('://',1)[0]
                if scheme=='vmess':
                    original=json.loads(base64.b64decode(uri.split('://',1)[1]+'==='))
                    checks['outbounds.0.settings.vnext.0.users.0.id']=original['id']
                elif scheme=='vless':
                    checks['outbounds.0.settings.vnext.0.users.0.id']=unquote(urlsplit(uri).netloc.rsplit('@',1)[0])
                elif scheme=='trojan':checks['outbounds.0.settings.servers.0.password']=unquote(urlsplit(uri).netloc.rsplit('@',1)[0])
                elif scheme in ('hy2','hysteria2'):checks['outbounds.0.streamSettings.hysteriaSettings.auth']=unquote(urlsplit(uri).netloc.rsplit('@',1)[0])
                elif scheme=='ss':
                    main=uri[5:].split('#',1)[0].split('?',1)[0]
                    decoded=base64.b64decode((main.rsplit('@',1)[0] if '@' in main else main)+'===').decode()
                    checks['outbounds.0.settings.servers.0.password']=decoded.split(':',1)[1].rsplit('@',1)[0] if '@' not in main else decoded.split(':',1)[1]
                elif scheme in ('socks','socks5','http') and '@' in urlsplit(uri).netloc:
                    auth=urlsplit(uri).netloc.rsplit('@',1)[0]
                    if ':' not in auth:auth=base64.b64decode(auth+'===').decode()
                    u,pw=auth.split(':',1) if ':' in auth else (auth,'')
                    checks['outbounds.0.settings.servers.0.users.0.user']=unquote(u)
                    checks['outbounds.0.settings.servers.0.users.0.pass']=unquote(pw)
                for key,value in checks.items():
                    try:
                        actual=field(data,key);passed=actual==value
                        r['fieldAssertions'].append({'path':key,'passed':passed})
                        if not passed:r['failures'].append(f'Field mismatch: {key}')
                    except (KeyError,IndexError,TypeError):r['failures'].append(f'Missing field: {key}')
                report=json.loads((directory/'runtime.validation.json').read_text());r['validationReport']=str(directory/'runtime.validation.json')
                r['coreExitCode']=report['coreExitCode'];r['coreSha256']=report['coreSha256'];r['logPath']=report['logPath']
                assert report['configSha256']==r['configSha256'],'Artifact/report hash mismatch'
                assert report['corePath']==str(a.core),'Core changed'
                if case['expected']=='core-reject':
                    assert check.returncode==6 and not report['success'] and report['coreExitCode'] not in (0,None),f'Core rejection not propagated: {check.returncode}'
                else:
                    assert check.returncode==0 and report['success'] and report['coreExitCode']==0,f'check failed: {check.returncode}, core {report["coreExitCode"]}'
                if case['expected']=='accept':assert 'Configuration OK' in check.stderr,'Core success diagnostic not visible'
                log=invoke(['logs','--tail','100'],env)
                (directory/'logs.stdout.txt').write_text(log.stdout)
                assert log.returncode==0 and 'END' in log.stdout,'Persisted log missing'
                assert not r['failures'],'Field assertions failed'
                r['status']='rejected' if case['expected']=='core-reject' else 'passed'
    except Exception as e:r['failures'].append(str(e))
    results.append(r)
    (directory/'case.json').write_text(json.dumps(r,indent=2)+'\n')
    if (i+1)%50==0:print(f'{i+1}/{len(manifest["cases"])} {dict(collections.Counter(x["status"] for x in results))}',flush=True)
assert hashlib.sha256(a.cli.read_bytes()).hexdigest()==cli_hash,'CLI changed during matrix execution'
summary=collections.Counter(r['status'] for r in results)
report={'manifestSha256':expected_hash,'cliPath':str(a.cli),'cliSha256':cli_hash,'corePath':str(a.core),'coreSha256':hashlib.sha256(a.core.read_bytes()).hexdigest(),'coreVersion':subprocess.check_output([str(a.core),'version'],text=True).strip(),'host':platform.platform(),'osRelease':Path('/etc/os-release').read_text(),'total':len(results),'summary':dict(summary),'mainCellsExecuted':len({r['mainCell'] for r in results if r['mainCell']}),'unknown':0,'unexecuted':0,'results':results}
(a.out/'coverage.json').write_text(json.dumps(report,indent=2)+'\n')
lines=['# Linux CLI protocol coverage','',f'Manifest SHA-256: `{expected_hash}`',f'Total: {len(results)}; main cells: {report["mainCellsExecuted"]}/54; unknown/unexecuted: 0/0.','', '| Result | Count |','|---|---:|']+[f'| {k} | {v} |' for k,v in summary.items()]
lines+=['','| Protocol | Passed | Rejected | Failed | N/A |','|---|---:|---:|---:|---:|']
for proto in sorted({x['protocol'] for x in results}):
    c=collections.Counter(x['status'] for x in results if x['protocol']==proto)
    lines.append(f'| {proto} | {c["passed"]} | {c["rejected"]} | {c["failed"]} | {c["not-applicable"]} |')
lines+=['','## Failures','']
for r in results:
    if r['status']=='failed':lines.append(f'- `{r["id"]}`: '+ '; '.join(r['failures'])+f' ([case]({Path(r["caseDirectory"]).name}/case.json))')
lines+=['','Validation is configuration-only. No proxy/TUN startup, remote authentication, traffic or DNS/routing behavior was tested.','']
(a.out/'coverage.md').write_text('\n'.join(lines))
print(json.dumps(report['summary']),flush=True)
raise SystemExit(1 if summary['failed'] else 0)
