#!/usr/bin/env python3
"""Guest-only synthetic network fixture. Refuses host execution by instance ID."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import time

STATE=Path('/var/lib/xrayfa-fixture')
NS='xfa-fixture'

def run(argv,**kw):return subprocess.run(argv,check=True,**kw)
def ip(*argv):return run(['/usr/sbin/ip',*argv])
def get(*argv):return run(list(argv),capture_output=True,text=True).stdout

def guard(instance):
    if os.geteuid()!=0:raise SystemExit('Fixture requires guest root')
    if Path('/etc/xrayfa-vm-id').read_text().strip()!=instance:raise SystemExit('Instance marker mismatch')
    if get('/usr/bin/systemd-detect-virt','--vm').strip() not in ['kvm','qemu']:raise SystemExit('Not a VM')
    if Path('/sys/class/dmi/id/product_uuid').read_text().strip().lower()!=instance:raise SystemExit('DMI mismatch')

def snapshot():
    return {key:json.loads(get('/usr/sbin/ip','-j',*args)) for key,args in {
        'addresses':['address'],'routes':['route','show','table','all'],'rules':['rule']}.items()}

def spawn(args,log):
    with open(STATE/log,'ab') as f:
        p=subprocess.Popen(['/usr/sbin/ip','netns','exec',NS,*args],stdout=f,stderr=f,start_new_session=True)
    time.sleep(.2)
    if p.poll() is not None:raise RuntimeError(f'{log} process exited')
    stat=Path(f'/proc/{p.pid}/stat').read_text().rsplit(')',1)[1].split()
    return {'pid':p.pid,'start':stat[19],'exe':os.readlink(f'/proc/{p.pid}/exe')}

def save(s):(STATE/'state.json').write_text(json.dumps(s,indent=2)+'\n')

def setup(instance):
    if (STATE/'state.json').exists():raise SystemExit('Fixture state already exists; preserve it and teardown explicitly')
    if 'xfa-underlay' in get('/usr/sbin/ip','-j','link') or NS in get('/usr/sbin/ip','netns','list'):
        raise SystemExit('Existing fixture resource conflict')
    STATE.mkdir(mode=0o700,exist_ok=True)
    s={'instance':instance,'boot':Path('/proc/sys/kernel/random/boot_id').read_text().strip(),'baseline':snapshot(),'processes':[]}
    save(s)
    # CA and credentials are synthetic, private, and limited to this test VM.
    run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(STATE/'ca.key'),'-out',str(STATE/'ca.crt'),'-days','3','-subj','/CN=XrayFA Runtime Fixture CA'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    (STATE/'cert.ext').write_text('subjectAltName=DNS:proxy.xfa.test,DNS:app.xfa.test,IP:203.0.113.10\nextendedKeyUsage=serverAuth\n')
    run(['openssl','req','-newkey','rsa:2048','-nodes','-keyout',str(STATE/'server.key'),'-out',str(STATE/'server.csr'),'-subj','/CN=proxy.xfa.test'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    run(['openssl','x509','-req','-in',str(STATE/'server.csr'),'-CA',str(STATE/'ca.crt'),'-CAkey',str(STATE/'ca.key'),'-CAcreateserial','-out',str(STATE/'server.crt'),'-days','3','-extfile',str(STATE/'cert.ext')],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    run(['install','-m','644',str(STATE/'ca.crt'),'/usr/local/share/ca-certificates/xrayfa-runtime-fixture.crt'])
    run(['update-ca-certificates'],stdout=subprocess.DEVNULL)
    ip('netns','add',NS);ip('link','add','xfa-underlay','type','veth','peer','name','xfa-peer')
    ip('link','set','xfa-peer','netns',NS)
    ip('addr','add','10.203.0.1/30','dev','xfa-underlay');ip('-6','addr','add','2001:db8:203::1/64','dev','xfa-underlay','nodad');ip('link','set','xfa-underlay','up')
    ip('-n',NS,'link','set','lo','up');ip('-n',NS,'addr','add','10.203.0.2/30','dev','xfa-peer');ip('-n',NS,'addr','add','10.204.0.200/32','dev','lo')
    ip('-n',NS,'-6','addr','add','2001:db8:203::2/64','dev','xfa-peer','nodad');ip('-n',NS,'link','set','xfa-peer','up')
    # Retain the management subnet's connected route, remove prior IPv4 defaults.
    for route in s['baseline']['routes']:
        if route.get('dst')=='default' and route.get('table','main')=='main' and ':' not in route.get('gateway',''):
            argv=['route','del','default']
            if route.get('gateway'):argv+=['via',route['gateway']]
            argv+=['dev',route['dev']]
            if 'metric' in route:argv+=['metric',str(route['metric'])]
            ip(*argv)
    ip('route','add','default','via','10.203.0.2','dev','xfa-underlay','metric','1')
    s['processes'].append(spawn(['/usr/bin/python3',str(Path(__file__).with_name('services.py')),'--cert',str(STATE/'server.crt'),'--key',str(STATE/'server.key')],'services.log'));save(s)
    user='11111111-2222-4333-8444-555555555555'
    config={'log':{'loglevel':'debug'},'inbounds':[{'listen':'10.204.0.200','port':443,'protocol':'vless','settings':{'clients':[{'id':user}],'decryption':'none'},'streamSettings':{'network':'xhttp','security':'tls','tlsSettings':{'alpn':['h2','http/1.1'],'certificates':[{'certificateFile':str(STATE/'server.crt'),'keyFile':str(STATE/'server.key')}]},'xhttpSettings':{'path':'/runtime','host':'proxy.xfa.test','mode':'auto'}}}],
        'outbounds':[{'tag':'deny','protocol':'blackhole'}], 'routing':{'rules':[]}}
    for tag,port,target,network in [('http',80,18080,'tcp'),('https',443,18443,'tcp'),('dns',53,1053,'tcp,udp'),('udp',19000,19000,'udp')]:
        config['outbounds'].append({'tag':tag,'protocol':'freedom','settings':{'redirect':f'127.0.0.1:{target}','finalRules':[{'action':'allow','ip':['127.0.0.1'],'port':target}]}})
        config['routing']['rules'].append({'type':'field','port':str(port),'network':network,'outboundTag':tag})
    (STATE/'server.json').write_text(json.dumps(config,indent=2)+'\n')
    run(['/opt/xrayfa-runtime/xray','run','-test','-c',str(STATE/'server.json')],stdout=subprocess.DEVNULL)
    s['processes'].append(spawn(['/opt/xrayfa-runtime/xray','run','-c',str(STATE/'server.json')],'server.log'));save(s)
    (STATE/'link.txt').write_text(f'vless://{user}@10.204.0.200:443?type=xhttp&security=tls&sni=proxy.xfa.test&host=proxy.xfa.test&path=%2Fruntime&mode=auto&alpn=h2%2Chttp%2F1.1#runtime-xhttp\n')
    run(['install','-o','xfa','-g','xfa','-m','600',str(STATE/'link.txt'),'/home/xfa/fixture-link.txt'])
    print(json.dumps({'fixture':str(STATE),'status':'started','boot':s['boot']}))

def teardown(instance):
    s=json.loads((STATE/'state.json').read_text())
    if s['instance']!=instance or s['boot']!=Path('/proc/sys/kernel/random/boot_id').read_text().strip():raise SystemExit('Fixture state identity mismatch')
    for p in s['processes']:
        try:
            stat=Path(f"/proc/{p['pid']}/stat").read_text().rsplit(')',1)[1].split()
            if stat[19]!=p['start'] or os.readlink(f"/proc/{p['pid']}/exe")!=p['exe']:raise RuntimeError('Fixture PID reuse; refusing kill')
            os.kill(p['pid'],signal.SIGTERM)
        except FileNotFoundError:pass
    ip('link','del','xfa-underlay');ip('netns','del',NS)
    for route in s['baseline']['routes']:
        if route.get('dst')=='default' and route.get('table','main')=='main' and ':' not in route.get('gateway',''):
            argv=['route','add','default']
            if route.get('gateway'):argv+=['via',route['gateway']]
            argv+=['dev',route['dev']]
            for key in ['metric','protocol','scope','prefsrc']:
                if key in route:argv+=['src' if key=='prefsrc' else key,str(route[key])]
            ip(*argv)
    Path('/usr/local/share/ca-certificates/xrayfa-runtime-fixture.crt').unlink()
    run(['update-ca-certificates','--fresh'],stdout=subprocess.DEVNULL)
    s['teardownSnapshot']=snapshot();save(s)
    (STATE/'state.json').rename(STATE/f"completed-{int(time.time())}.json")

def main():
    os.umask(0o077)
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('action',choices=['setup','teardown']);p.add_argument('--instance',required=True);args=p.parse_args()
    guard(args.instance)
    (setup if args.action=='setup' else teardown)(args.instance)

if __name__=='__main__':main()
