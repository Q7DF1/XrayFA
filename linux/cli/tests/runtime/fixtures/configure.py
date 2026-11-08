#!/usr/bin/env python3
"""Configure all eighteen synthetic protocol endpoints inside the verified VM."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import time
from urllib.parse import quote, urlencode

import setup

STATE=setup.STATE
CORE='/opt/xrayfa-runtime/xray'
ADDR='10.204.0.200'
USER='11111111-2222-4333-8444-555555555555'
CIPHERS=['aes-128-gcm','aes-256-gcm','chacha20-ietf-poly1305','xchacha20-ietf-poly1305',
         '2022-blake3-aes-128-gcm','2022-blake3-aes-256-gcm','2022-blake3-chacha20-poly1305']

def b64(s):return base64.urlsafe_b64encode(s.encode()).decode().rstrip('=')

def main():
    os.umask(0o077)
    p=argparse.ArgumentParser();p.add_argument('--instance',required=True);args=p.parse_args();setup.guard(args.instance)
    s=json.loads((STATE/'state.json').read_text())
    if s['boot']!=Path('/proc/sys/kernel/random/boot_id').read_text().strip():raise RuntimeError('Fixture boot mismatch')
    if Path('/run/xrayfa/session.json').exists():raise RuntimeError('Product session journal exists; preserve it')
    if (STATE/'server-all.json').exists():raise RuntimeError('Configured all-seed artifact already exists; preserved')
    tls={'alpn':['h2','http/1.1'],'certificates':[{'certificateFile':str(STATE/'server.crt'),'keyFile':str(STATE/'server.key')}]}
    streams={'network':'raw','security':'tls','tlsSettings':tls}
    # Private materials never leave guest private fixture directory or public logs.
    raw=setup.get(CORE,'x25519')
    keys={l.split(':',1)[0]:l.split(':',1)[1].strip() for l in raw.splitlines() if ':' in l}
    private=keys['PrivateKey'];public=keys.get('Password (PublicKey)',keys.get('PublicKey'))
    if not public:raise RuntimeError('Unknown pinned core x25519 output')
    (STATE/'reality-key.json').write_text(json.dumps({'private':private,'public':public})+'\n')
    config=json.loads((STATE/'server.json').read_text());config['inbounds']=[]
    seeds=[]
    def add(name,protocol,port,settings,stream,link,udp='proxy'):
        inbound={'tag':name,'listen':ADDR,'port':port,'protocol':protocol,'settings':settings}
        if stream:inbound['streamSettings']=stream
        config['inbounds'].append(inbound)
        seeds.append(dict(id=name,protocol=protocol,port=port,link=link,udpPolicy=udp,serverTag=name))
    q={'type':'raw','security':'tls','sni':'proxy.xfa.test','alpn':'h2,http/1.1'}
    def vlink(port,params):return f'vless://{USER}@{ADDR}:{port}?{urlencode(params)}'
    add('RT-VLESS-TLS','vless',24401,{'clients':[{'id':USER,'flow':'xtls-rprx-vision'}],'decryption':'none'},streams,vlink(24401,{**q,'flow':'xtls-rprx-vision'}))
    add('RT-VLESS-XHTTP','vless',24402,{'clients':[{'id':USER}],'decryption':'none'},
        {**streams,'network':'xhttp','xhttpSettings':{'path':'/runtime','host':'proxy.xfa.test','mode':'auto'}},
        vlink(24402,{**q,'type':'xhttp','path':'/runtime','host':'proxy.xfa.test','mode':'auto'}))
    add('RT-VLESS-REALITY','vless',24403,{'clients':[{'id':USER}],'decryption':'none'},
        {'network':'raw','security':'reality','realitySettings':{'show':False,'target':'127.0.0.1:19443','serverNames':['proxy.xfa.test'],'privateKey':private,'shortIds':['12ab']}},
        vlink(24403,{'type':'raw','security':'reality','sni':'proxy.xfa.test','fp':'chrome','pbk':public,'sid':'12ab'}))
    vmess={'v':'2','ps':'runtime-vmess','add':ADDR,'port':'24404','id':USER,'aid':'0','scy':'auto','net':'tcp','type':'none','host':'','path':'','tls':''}
    add('RT-VMESS','vmess',24404,{'clients':[{'id':USER}]},{'network':'raw'},'vmess://'+base64.b64encode(json.dumps(vmess).encode()).decode())
    add('RT-TROJAN','trojan',24405,{'clients':[{'password':'fixture-trojan-password'}]},streams,
        f'trojan://fixture-trojan-password@{ADDR}:24405?'+urlencode(q))
    for i,cipher in enumerate(CIPHERS,1):
        port=24500+i
        key=base64.b64encode(os.urandom(16 if cipher=='2022-blake3-aes-128-gcm' else 32)).decode() if cipher.startswith('2022') else 'fixture-shadowsocks-password'
        add(f'RT-SS-{i:02}','shadowsocks',port,{'method':cipher,'password':key,'network':'tcp,udp'},None,
            f'ss://{b64(cipher+":"+key)}@{ADDR}:{port}')
    for i,obfs in enumerate(['none','salamander'],1):
        port=24600+i;auth='fixture-hysteria-password'
        stream={'network':'hysteria','security':'tls','tlsSettings':{**tls,'alpn':['h3']},'hysteriaSettings':{'version':2}}
        params={'sni':'proxy.xfa.test','alpn':'h3'}
        if obfs=='salamander':
            stream['finalmask']={'udp':[{'type':'salamander','settings':{'password':'fixture-salamander-password'}}]}
            params.update(obfs='salamander',**{'obfs-password':'fixture-salamander-password'})
        add(f'RT-HY2-{i:02}','hysteria',port,{'version':2,'clients':[{'auth':auth}]},stream,
            f'hysteria2://{auth}@{ADDR}:{port}?'+urlencode(params))
    for proto,base in [('socks',24700),('http',24800)]:
        for i,auth in enumerate([False,True],1):
            port=base+i;credentials='fixture-user:fixture-password@' if auth else ''
            settings={'auth':'password' if auth else 'noauth','udp':False} if proto=='socks' else {}
            if auth:settings['accounts']=[{'user':'fixture-user','pass':'fixture-password'}]
            add(f'RT-{proto.upper()}-{i:02}',proto,port,settings,None,f'{proto}://{credentials}{ADDR}:{port}',udp='block')
    assert len(seeds)==18
    (STATE/'server-all.json').write_text(json.dumps(config,indent=2)+'\n')
    (STATE/'seeds.json').write_text(json.dumps(seeds,indent=2)+'\n')
    setup.run([CORE,'run','-test','-c',str(STATE/'server-all.json')],stdout=subprocess.DEVNULL)
    # Stop only the root-owned server tracked by the fixture PID/start/executable.
    old=s['processes'][-1];pid=old['pid']
    actual=Path(f'/proc/{pid}/stat').read_text().rsplit(')',1)[1].split()
    if actual[19]!=old['start'] or os.readlink(f'/proc/{pid}/exe')!=old['exe']:raise RuntimeError('Server PID identity mismatch')
    os.kill(pid,signal.SIGTERM)
    for _ in range(100):
        if not Path(f'/proc/{pid}/exe').exists():break
        time.sleep(.02)
    s['previousServer']=old
    s['processes'][-1]=setup.spawn([CORE,'run','-c',str(STATE/'server-all.json')],'server-all.log')
    s['processes'].append(setup.spawn(['/usr/bin/openssl','s_server','-accept','19443','-cert',str(STATE/'server.crt'),'-key',str(STATE/'server.key'),'-alpn','h2','-www','-tls1_3'],'reality-target.log'))
    s['serverConfigSha256']=hashlib.sha256((STATE/'server-all.json').read_bytes()).hexdigest();setup.save(s)
    setup.run(['install','-o','xfa','-g','xfa','-m','600',str(STATE/'seeds.json'),'/home/xfa/fixture-seeds.json'])
    print(json.dumps({'count':len(seeds),'serverConfigSha256':s['serverConfigSha256'],'status':'configured-not-yet-tested'}))

if __name__=='__main__':main()
