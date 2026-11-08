# Synthetic test credentials only; never use production node inputs here.
"""Executable acceptance: storage, exit mapping, logging, subprocess isolation and failure behavior."""
import argparse,concurrent.futures,fcntl,hashlib,json,os,signal,stat,subprocess,time
from urllib.parse import urlencode
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--cli',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
a=p.parse_args();os.umask(0o077);a.cli=a.cli.resolve();a.out=a.out.resolve();a.out.mkdir(parents=True,exist_ok=False)
results=[]
UUID='00000000-0000-4000-8000-000000000001';PASSWORD='synthetic-secret-@:/+%&'
LINK=f'vless://{UUID}@example.com:443?security=tls#name'
def env(d):return {**os.environ,'XRAYFA_HOME':str(d/'profile')}
def run(d,*args):return subprocess.run([str(a.cli),*map(str,args)],env=env(d),capture_output=True,text=True,timeout=40)
def test(name,fn):
 d=a.out/name;d.mkdir();r={'id':name,'status':'failed'}
 try:fn(d);r['status']='passed'
 except Exception as e:r['error']=str(e)
 results.append(r);(d/'result.json').write_text(json.dumps(r,indent=2)+'\n');print(name,r['status'],r.get('error',''),flush=True)
def expect(proc,code):
 assert proc.returncode==code,f'exit expected {code}, got {proc.returncode}: {proc.stderr}'
 return proc
def add(d):expect(run(d,'add','--link',LINK),0)
def nodes(d):return d/'profile/.local/share/xrayfa/nodes.json'
def logs(d):return d/'profile/.local/state/xrayfa/logs/xrayfa.log'
def fake(d,body):
 path=d/'core-fixture';path.write_text('#!/usr/bin/python3\nimport os,sys,time,signal\nif sys.argv[1:]==["version"]:\n print("Xray fixture 1.0");sys.exit(0)\nassert sys.argv[1:4]==["run","-test","-c"],sys.argv\n'+body+'\n');path.chmod(0o700);return path

test('help-version',lambda d:(expect(run(d,'--help'),0),expect(run(d,'--version'),0),expect(run(d,'run','1'),4)))
test('usage',lambda d:[expect(run(d,*args),2) for args in [('add',),('add','--link',LINK,'--link-file','missing'),('add','--link',LINK,'--link',LINK),('ls','extra'),('check','0','--output','x'),('check','1'),('logs','--tail','-1'),('logs','--tail','abc'),('logs','--tail','10001'),('check','1','--output','x','--log-level','trace')]])
def file_input(d):
 f=d/'links';f.write_text(LINK+'\n'+LINK);expect(run(d,'add','--link-file',f),2);assert not nodes(d).exists()
 f.write_text('\n'+LINK+'\n');expect(run(d,'add','--link-file',f),0)
 expect(run(d,'add','--link-file',d/'missing'),10)
test('link-file',file_input)
def storage(d):
 expect(run(d,'ls'),0)
 with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
  for proc in pool.map(lambda _:run(d,'add','--link',LINK),range(20)):expect(proc,0)
 s=json.loads(nodes(d).read_text());assert s['nextId']==21 and sorted(n['id'] for n in s['nodes'])==list(range(1,21))
 assert len(run(d,'ls').stdout.splitlines())==20
 assert stat.S_IMODE(nodes(d).stat().st_mode)==0o600 and stat.S_IMODE(nodes(d).parent.stat().st_mode)==0o700
 before=nodes(d).read_bytes();expect(run(d,'add','--link','bad://secret'),6);assert nodes(d).read_bytes()==before
 nodes(d).write_text('{broken');expect(run(d,'ls'),10);expect(run(d,'add','--link',LINK),10);assert nodes(d).read_text()=='{broken'
test('atomic-ids-corruption',storage)
def symlinks(d):
 add(d);target=d/'target';target.write_text('unchanged');nodes(d).unlink();nodes(d).symlink_to(target)
 expect(run(d,'ls'),10);expect(run(d,'add','--link',LINK),10);assert target.read_text()=='unchanged'
test('node-symlink',symlinks)
def perms(d):
 add(d);nodes(d).chmod(0o644);expect(run(d,'ls'),10)
test('node-permissions',perms)
def missing(d):
 expect(run(d,'check','999','--output',d/'out.json'),3)
 assert 'START' in logs(d).read_text() and 'END' in logs(d).read_text()
 expect(run(d,'logs','--tail','2'),0)
test('missing-node-logged',missing)
def conflict(d):
 add(d);f=d/'runtime.json';f.write_text('preserve');expect(run(d,'check','1','--output',f),8);assert f.read_text()=='preserve'
test('output-conflict',conflict)
def report_conflict(d):
 add(d);f=d/'runtime.validation.json';f.write_text('preserve');expect(run(d,'check','1','--output',d/'runtime.json'),8);assert f.read_text()=='preserve'
test('report-conflict',report_conflict)
def no_core(d):
 add(d);expect(run(d,'check','1','--output',d/'runtime.json','--core',d/'missing'),5)
 assert json.loads((d/'runtime.validation.json').read_text())['success'] is False
 assert (d/'runtime.json').exists()
test('core-missing',no_core)
def nonzero(d):
 add(d);c=fake(d,'print("Failed to start: TLS configuration error",file=sys.stderr)\nsys.exit(23)')
 r=expect(run(d,'check','1','--output',d/'runtime.json','--core',c),6)
 report=json.loads((d/'runtime.validation.json').read_text());assert report['coreExitCode']==23 and 'TLS configuration error' in r.stderr
 assert 'TLS configuration error' in run(d,'logs').stdout
test('core-nonzero',nonzero)
def signal_exit(d):
 add(d);c=fake(d,'os.kill(os.getpid(),signal.SIGTERM)');expect(run(d,'check','1','--output',d/'runtime.json','--core',c),6)
 assert json.loads((d/'runtime.validation.json').read_text())['coreExitCode']==143
test('core-signal',signal_exit)
def version_fail(d):
 add(d);c=d/'badcore';c.write_text('#!/bin/sh\nexit 23\n');c.chmod(0o700)
 expect(run(d,'check','1','--output',d/'runtime.json','--core',c),5)
test('version-failed',version_fail)
def version_timeout(d):
 add(d);c=d/'slowcore';c.write_text('#!/usr/bin/python3\nimport time\ntime.sleep(60)\n');c.chmod(0o700)
 start=time.monotonic();expect(run(d,'check','1','--output',d/'runtime.json','--core',c),7)
 assert 5<=time.monotonic()-start<8
 assert json.loads((d/'runtime.validation.json').read_text())['coreExitCode'] is None
test('version-timeout',version_timeout)

def hash_change(d):
 add(d);c=fake(d,'open(sys.argv[4],"a").write(" ")\nprint("Configuration OK.")')
 expect(run(d,'check','1','--output',d/'runtime.json','--core',c),6)
 assert 'changed during validation' in logs(d).read_text()
test('artifact-tamper',hash_change)
def redaction(d):
 link='trojan://synthetic-secret-%40%3A%2F%2B%25%26@example.com:443?security=tls#'+UUID
 expect(run(d,'add','--link',link),0)
 body=f'print("UUID {UUID} password={PASSWORD} invalid byte: diagnostic retained")\nprint("authorization=Bearer-secret \\x1b[31mERROR\\x07",file=sys.stderr)\nprint("Configuration OK.")'
 c=fake(d,body);r=expect(run(d,'check','1','--output',d/'runtime.json','--core',c,'--log-level','debug'),0)
 text=r.stderr+run(d,'logs').stdout+run(d,'ls').stdout
 assert UUID not in text and PASSWORD not in text and 'Bearer-secret' not in text
 assert '\x1b' not in text and '\x07' not in text and 'invalid byte: diagnostic retained' in text
 report=json.loads((d/'runtime.validation.json').read_text());assert PASSWORD not in json.dumps(report)
 assert stat.S_IMODE(logs(d).stat().st_mode)==0o600 and stat.S_IMODE(logs(d).parent.stat().st_mode)==0o700
test('redaction-control-debug',redaction)
def levels(d):
 add(d);c=fake(d,'print("[Debug] low-detail")\nprint("[Info] info-detail")\nprint("[Warning] warn-detail")\nprint("[Error] error-detail",file=sys.stderr)\nprint("Configuration OK.")')
 for level in ('debug','info','warning','error'):
  f=d/(level+'.json');r=expect(run(d,'check','1','--output',f,'--core',c,'--log-level',level),0)
  assert json.loads(f.read_text())['log']['loglevel']==level
  assert 'error-detail' in r.stderr
  assert ('low-detail' in r.stderr)==(level=='debug')
  assert ('info-detail' in r.stderr)==(level in ('debug','info'))
  assert ('warn-detail' in r.stderr)==(level!='error')
  assert 'Core version=Xray fixture' in r.stderr
test('log-levels',levels)
def tail_rotation(d):
 add(d);c=fake(d,'print("Configuration OK.")')
 expect(run(d,'check','1','--output',d/'first.json','--core',c),0)
 log=logs(d)
 for i in range(5):
  with log.open('a') as f:f.write(('seed-%d\n'%i)*((5*1024*1024)//7))
  expect(run(d,'check','1','--output',d/f'round{i}.json','--core',c),0)
 assert all(log.with_name(log.name+f'.{i}').exists() for i in (1,2,3))
 assert not log.with_name(log.name+'.4').exists()
 r=expect(run(d,'logs','--tail','17'),0);assert len(r.stdout.splitlines())==17 and 'END' in r.stdout
 assert not run(d,'logs','--tail','0').stdout
 # Actual writer rotations while a follow process is active.
 follower=subprocess.Popen([str(a.cli),'logs','--tail','0','--follow'],env=env(d),stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
 try:
  time.sleep(.2)
  with log.open('a') as f:f.write('follow-old\n')
  time.sleep(.3)
  log.rename(log.with_name(log.name+'.1'));log.write_text('follow-new\n');log.chmod(0o600)
  time.sleep(.3);follower.send_signal(signal.SIGINT);out,err=follower.communicate(timeout=3)
  assert follower.returncode==0 and 'follow-old' in out and 'follow-new' in out,(out,err)
 finally:
  if follower.poll() is None:follower.kill();follower.wait()
test('rotation-tail-follow',tail_rotation)
def chronological_tail(d):
 add(d);c=fake(d,'print("Configuration OK.")')
 expect(run(d,'check','1','--output',d/'runtime.json','--core',c),0)
 log=logs(d)
 for i in range(4):
  f=log if i==0 else log.with_name(log.name+f'.{i}')
  f.write_text(f'{i}-a\n{i}-b\n{i}-c\n');f.chmod(0o600)
 r=expect(run(d,'logs','--tail','7'),0)
 assert r.stdout.splitlines()==['2-c','1-a','1-b','1-c','0-a','0-b','0-c'],r.stdout
test('tail-history-order',chronological_tail)
def node_mapping(d):
 add(d);s=json.loads(nodes(d).read_text());s['nodes'][0]['address']='other.example.com';nodes(d).write_text(json.dumps(s))
 expect(run(d,'check','1','--output',d/'runtime.json'),6);assert not (d/'runtime.json').exists()
test('stored-endpoint-mapping',node_mapping)

def no_logs(d):expect(run(d,'logs'),11)
test('missing-logs-error',no_logs)
def log_init(d):
 add(d);directory=d/'profile/.local/state/xrayfa/logs';directory.mkdir(parents=True);directory.chmod(0o700);(directory/'xrayfa.log').symlink_to('/dev/null')
 expect(run(d,'check','1','--output',d/'out.json'),11);assert not (d/'out.json').exists()
test('log-init-error',log_init)
def log_failure(d):
 add(d)
 body='p=os.environ.get("SHOULD_NOT_EXIST")\nassert p is None\nconfig=sys.argv[4]\nfrom pathlib import Path\nprofile=Path(config).parent/"profile"\nlog=profile/".local/state/xrayfa/logs/xrayfa.log"\nlog.unlink();log.symlink_to("/dev/full")\nprint("Configuration OK.")'
 c=fake(d,body);r=expect(run(d,'check','1','--output',d/'runtime.json','--core',c),11)
 assert 'Log persistence failed' in r.stderr and json.loads((d/'runtime.validation.json').read_text())['success'] is False
test('log-write-failure-drains',log_failure)
def first_failure(d):
 add(d)
 body='from pathlib import Path\nlog=Path(sys.argv[4]).parent/"profile/.local/state/xrayfa/logs/xrayfa.log"\nlog.unlink();log.symlink_to("/dev/full")\nprint("[Info] triggers-log-failure",flush=True)\ntime.sleep(.3)\nprint("Failed to start: core failure",flush=True)\nsys.exit(23)'
 c=fake(d,body);expect(run(d,'check','1','--output',d/'runtime.json','--core',c),11)
 assert json.loads((d/'runtime.validation.json').read_text())['coreExitCode']==23
test('log-fault-before-core-failure',first_failure)
def core_first_failure(d):
 add(d);expect(run(d,'check','999','--output',d/'missing.json'),3)
 c=fake(d,'print("Failed to start: core failure",flush=True)\nsys.exit(23)')
 with logs(d).with_name('writer.lock').open('r+') as held,(d/'terminal.txt').open('w') as terminal:
  fcntl.flock(held,fcntl.LOCK_EX)
  proc=subprocess.Popen([str(a.cli),'check','1','--output',str(d/'runtime.json'),'--core',str(c)],env=env(d),stdout=terminal,stderr=terminal)
  try:
   start=time.monotonic()
   while 'core_exit=23' not in (d/'terminal.txt').read_text() and time.monotonic()-start<5:time.sleep(.02)
   assert 'core_exit=23' in (d/'terminal.txt').read_text()
   time.sleep(.1);logs(d).unlink();logs(d).symlink_to('/dev/full')
  finally:fcntl.flock(held,fcntl.LOCK_UN)
  assert proc.wait(timeout=5)==6
 assert json.loads((d/'runtime.validation.json').read_text())['coreExitCode']==23
 assert 'Log persistence failed' in (d/'terminal.txt').read_text()
test('core-first-cause-retained',core_first_failure)

def env_argv(d):
 add(d)
 c=fake(d,'assert not any(k.startswith("XRAY_") and k!="XRAY_LOCATION_ASSET" for k in os.environ)\nassert "HTTP_PROXY" not in os.environ\nprint("Configuration OK.")')
 old=os.environ.get('XRAY_LOCATION_CONFIG');os.environ['XRAY_LOCATION_CONFIG']='malicious'
 try:expect(run(d,'check','1','--output',d/'runtime name;$(no-shell).json','--core',c),0)
 finally:
  if old is None:os.environ.pop('XRAY_LOCATION_CONFIG')
  else:os.environ['XRAY_LOCATION_CONFIG']=old
test('clean-env-fixed-argv',env_argv)
def pipes(d):
 add(d);c=fake(d,'os.write(1,b"stdout-line\\n"*1000)\nos.write(2,b"stderr-line\\n"*1000)\nos.write(1,b"x"*1000000+b"\\n")\nprint("[Error] important-after-flood",file=sys.stderr)\nprint("Configuration OK.")')
 r=expect(run(d,'check','1','--output',d/'runtime.json','--core',c),0)
 assert 'stdout-line' in r.stderr and 'stderr-line' in r.stderr and 'important-after-flood' in r.stderr and 'overlong line truncated' in r.stderr
 assert max(map(len,r.stderr.splitlines()))<17000
 assert logs(d).stat().st_size<5*1024*1024
test('parallel-pipes-bounded-lines',pipes)
def queue_overflow(d):
 add(d);c=fake(d,'print("Configuration OK.")')
 expect(run(d,'check','1','--output',d/'first.json','--core',c),0)
 c=fake(d,'print("queued-normal\\n"*5000,end="")\nprint("[Error] preserved-priority-error",file=sys.stderr)\nopen(sys.argv[4]+".drained","w").write("done")\nprint("Configuration OK.")')
 lock=logs(d).with_name('writer.lock')
 with lock.open('r+') as held,(d/'terminal.txt').open('w') as terminal:
  fcntl.flock(held,fcntl.LOCK_EX)
  proc=subprocess.Popen([str(a.cli),'check','1','--output',str(d/'flood.json'),'--core',str(c)],env=env(d),stdout=terminal,stderr=terminal)
  try:
   start=time.monotonic()
   while not (d/'flood.json.drained').exists() and time.monotonic()-start<5:time.sleep(.02)
   assert (d/'flood.json.drained').exists(),'File log lock blocked core pipe reader'
  finally:fcntl.flock(held,fcntl.LOCK_UN)
  assert proc.wait(timeout=5)==0
 text=logs(d).read_text();assert 'preserved-priority-error' in text and 'dropped_events=' in text and 'END' in text
test('bounded-queue-error-priority',queue_overflow)
def header_redaction(d):
 q=urlencode({'security':'tls','type':'xhttp','extra':json.dumps({'headers':{'Authorization':'Bearer synthetic-token-value'}})})
 expect(run(d,'add','--link',f'vless://{UUID}@example.com:443?{q}'),0)
 c=fake(d,'print("header raw synthetic-token-value")\nprint("Authorization: Bearer fallback-token-value")\nprint("Configuration OK.")')
 r=expect(run(d,'check','1','--output',d/'runtime.json','--core',c,'--log-level','debug'),0)
 text=r.stderr+logs(d).read_text();assert 'synthetic-token-value' not in text and 'fallback-token-value' not in text
test('nested-authorization-redaction',header_redaction)

def interrupt(d):
 add(d);c=fake(d,'time.sleep(60)')
 proc=subprocess.Popen([str(a.cli),'check','1','--output',str(d/'runtime.json'),'--core',str(c)],env=env(d),stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
 time.sleep(.4);proc.send_signal(signal.SIGINT);out,err=proc.communicate(timeout=4)
 assert proc.returncode==7 and json.loads((d/'runtime.validation.json').read_text())['interrupted']
test('interrupt-reaps-core',interrupt)
def timeout(d):
 add(d);c=fake(d,'signal.signal(signal.SIGTERM,signal.SIG_IGN)\ntime.sleep(60)')
 start=time.monotonic();expect(run(d,'check','1','--output',d/'runtime.json','--core',c),7)
 assert 30<=time.monotonic()-start<35
 assert json.loads((d/'runtime.validation.json').read_text())['timedOut']
test('timeout-kill-reap',timeout)
report={'cliSha256':hashlib.sha256(a.cli.read_bytes()).hexdigest(),'total':len(results),'passed':sum(r['status']=='passed' for r in results),'failed':sum(r['status']=='failed' for r in results),'results':results}
(a.out/'acceptance.json').write_text(json.dumps(report,indent=2)+'\n')
(a.out/'acceptance.md').write_text('# CLI acceptance\n\n'+f'{report["passed"]}/{len(results)} passed.\n\n'+'\n'.join(f'- {r["id"]}: {r["status"]}'+(' — '+r['error'] if 'error' in r else '') for r in results)+'\n')
raise SystemExit(bool(report['failed']))
