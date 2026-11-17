# Synthetic test credentials only; never use production node inputs here.
"""Add source-audit cases without deleting or reclassifying the frozen base cases."""
import base64, hashlib, itertools, json
from pathlib import Path
from urllib.parse import urlencode,quote,parse_qsl,urlsplit
root=Path(__file__).parent
raw=(root/'manifest.json').read_bytes();base=json.loads(raw);cases=base['cases'].copy()
UUID='00000000-0000-4000-8000-000000000001'
KEY=base64.urlsafe_b64encode(bytes(range(32))).decode().rstrip('=')
MLDSA=base64.urlsafe_b64encode(bytes(1952)).decode().rstrip('=')
FP=['','chrome','firefox','safari','edge','android','ios','random','randomized']
def link(p,t='raw',s='tls',q=None,credential=None,host='example.com'):
    d={'type':t,'security':s};d.update(q or {})
    if s=='reality':d={'pbk':KEY,'sid':'','sni':'example.com',**d}
    if p=='vmess':
        j={'id':credential or UUID,'add':host,'port':443,'net':d.pop('type'),'tls':d.pop('security'),'scy':'auto'}
        j.update(d)
        return 'vmess://'+base64.b64encode(json.dumps(j).encode()).decode()
    if p in ('hy2','hysteria2'):d.pop('type');d.pop('security')
    return f'{p}://{quote(credential or (UUID if p=="vless" else "test-password"),safe="")}@{host}:443?{urlencode(d)}'
def add(id,p,uri,expected='accept',assertions=None,reason='Existing NodeFormValidation / parser schema support',stage=None):
    cases.append({'id':'audit-'+id,'protocol':p,'input':uri,'inputFormat':'base64-json' if p=='vmess' else 'uri','transport':None,'security':None,'expected':expected,'stage':stage or ('add' if expected=='reject' else 'check'),'reason':reason,'assertions':assertions or {}})
# All fingerprint values on all allowed TLS/REALITY transports, all protocols with transport choices.
for p,t,s,fp in itertools.product(['vless','vmess','trojan'],['raw','ws','grpc','xhttp','httpupgrade','kcp'],['tls','reality'],FP):
    if s=='reality' and t not in ('raw','grpc','xhttp'):continue
    path='realitySettings' if s=='reality' else 'tlsSettings'
    a={f'outbounds.0.streamSettings.{path}.fingerprint':fp or 'chrome'} if fp or s=='reality' else {}
    add(f'fp-{p}-{t}-{s}-{fp or "default"}',p,link(p,t,s,{'fp':fp}),assertions=a)
# Every finite encryption mode x handshake x applicable transport/security/flow combination.
for mode,rtt,t,s in itertools.product(['native','xorpub','random'],['0rtt','1rtt'],['raw','ws','grpc','xhttp','httpupgrade','kcp'],['none','tls','reality']):
    if s=='reality' and t not in ('raw','grpc','xhttp'):continue
    enc=f'mlkem768x25519plus.{mode}.{rtt}.{KEY}'
    for flow in ['absent','empty']+(['xtls-rprx-vision','xtls-rprx-vision-udp443'] if t=='raw' and s!='none' else []):
        q={'encryption':enc}
        if flow!='absent':q['flow']='' if flow=='empty' else flow
        add(f'enc-{mode}-{rtt}-{t}-{s}-{flow}','vless',link('vless',t,s,q),assertions={'outbounds.0.settings.vnext.0.users.0.encryption':enc,'outbounds.0.settings.vnext.0.users.0.flow':'' if flow in ('absent','empty') else flow})
for p,alias in itertools.product(['vless','vmess','trojan'],['pqv','mldsa65Verify']):
    add(f'mldsa-{p}-{alias}',p,link(p,s='reality',q={alias:MLDSA,'spx':'/all?x=1','fp':'firefox'}),assertions={'outbounds.0.streamSettings.realitySettings.mldsa65Verify':MLDSA,'outbounds.0.streamSettings.realitySettings.spiderX':'/all?x=1'})
# Hysteria TLS boolean options all combinations, both scheme aliases and all fingerprints.
for scheme,fp,pcs,vcn,ech_on in itertools.product(['hy2','hysteria2'],FP,[False,True],[False,True],[False,True]):
    q={'fp':fp,'alpn':'h3','sni':'example.com'}
    a={'outbounds.0.streamSettings.tlsSettings.alpn':['h3']}
    if fp:a['outbounds.0.streamSettings.tlsSettings.fingerprint']=fp
    pin=bytes(range(32)).hex()
    if pcs:q['pcs']=pin;a['outbounds.0.streamSettings.tlsSettings.pinnedPeerCertSha256']=pin
    if vcn:q['vcn']='example.com';a['outbounds.0.streamSettings.tlsSettings.verifyPeerCertByName']='example.com'
    if ech_on:
        source=next(c for c in cases if c['id']=='tls-options-vless-001')
        ech=source['assertions']['outbounds.0.streamSettings.tlsSettings.echConfigList']
        q['ech']=ech;a['outbounds.0.streamSettings.tlsSettings.echConfigList']=ech
    add(f'hy-tls-{scheme}-{fp or "default"}-{pcs}-{vcn}-{ech_on}','hysteria2',link(scheme,q=q),assertions=a)
for p in ('vless','vmess','trojan','hysteria2'):
    for key,val in [('pcs','not-hex'),('fp','unknown-fingerprint'),('ech','not-base64!')]:
        add(f'tls-invalid-{p}-{key}',p,link(p,q={key:val}),'reject')
    add(f'unknown-param-{p}',p,link(p,q={'ignored-semantic':'on'}),'reject')
    for credential in ('',):
        if p=='vmess':
            uri='vmess://'+base64.b64encode(json.dumps({'id':'','add':'example.com','port':443}).encode()).decode()
        else:uri=f'{p}://@example.com:443'
        add(f'missing-auth-{p}',p,uri,'reject')
    for host in ('192.0.2.3','[2001:db8::3]'):
        add(f'encoded-auth-{p}-{host}',p,link(p,credential='a@:/+%&',host=host))
for p in ('vless','trojan'):
    add('public-no-security-'+p,p,link(p,s='none'),'reject',reason='NodeFormValidation SECURITY_REQUIRED; core validates private-only unencrypted transport')
for t,s in itertools.product(['raw','ws','grpc','xhttp','httpupgrade','kcp'],['none','tls','reality']):
    if t=='raw' and s!='none':continue
    add('invalid-vision-'+t+'-'+s,'vless',link('vless',t,s,{'flow':'xtls-rprx-vision'},host='example.test'),'reject')
for p in ('socks','http'):
    add('base64-invalid-'+p,p,f'{p}://@@@example.com:443','reject')
    add('missing-host-'+p,p,f'{p}://user:pass@:443','reject')
for method in ('aead_aes_128_gcm','aead_aes_256_gcm'):
    canonical={'aead_aes_128_gcm':'aes-128-gcm','aead_aes_256_gcm':'aes-256-gcm'}[method]
    uri='ss://'+base64.b64encode((method+':password').encode()).decode()+'@example.com:8388'
    add(method,'ss',uri,assertions={'outbounds.0.settings.servers.0.method':canonical})
for uri in ('ss://'+base64.b64encode(b'aes-128-gcm:').decode()+'@example.com:8388','ss://'+base64.b64encode(b'unknown:password').decode()+'@example.com:8388','ss://'+base64.b64encode(b'aes-128-gcm:password').decode()+'@:8388'):
    add('ss-invalid-'+str(len(cases)),'ss',uri,'reject')
for p in ('vless','trojan'):
    add('core-private-range-'+p,p,link(p,s='none',host='192.0.2.1'),expected='core-reject',stage='check',reason='Designated core excludes this documentation range from its private-only no-TLS policy; preserve failure artifact')
for p,key in itertools.product(['vless','vmess','trojan'],['allowInsecure','insecure','skip-cert-verify']):
    add('reality-insecure-'+p+'-'+key,p,link(p,s='reality',q={key:'true'}),'reject')
# Field-level preservation for all supported authentication representations is independently checked by the runner.
# Explicit metadata on every case, including malformed inputs and N/A entries.
for c in cases:
    raw_input=c['input'];scheme=raw_input.split('://',1)[0] if raw_input else c['protocol']
    params={};transport='not-applicable';security='not-applicable'
    try:
        if scheme=='vmess':
            params=json.loads(base64.b64decode(raw_input.split('://',1)[1]+'==='))
            transport=params.get('net','raw');security=params.get('tls','none') or 'none'
        elif raw_input:
            params=dict(parse_qsl(raw_input.split('#',1)[0].split('?',1)[1] if '?' in raw_input.split('#',1)[0] else '',keep_blank_values=True))
            if scheme in ('vless','trojan'):
                transport=params.get('type','raw') or 'raw';security=params.get('security','tls' if scheme=='trojan' else 'none') or ('tls' if scheme=='trojan' else 'none')
            elif scheme in ('hy2','hysteria2'):transport='hysteria';security='tls'
            elif scheme in ('ss','socks','socks5','http'):transport='raw';security='none'
        if scheme=='ss':c['inputFormat']='sip002' if '@' in raw_input.split('#',1)[0] else 'legacy-base64'
        if scheme in ('socks','socks5','http') and '@' in raw_input:
            c['inputFormat']='base64-auth-uri' if ':' not in urlsplit(raw_input).netloc.rsplit('@',1)[0] else 'userinfo-uri'
    except Exception:
        transport='malformed';security='malformed'
    transport={'tcp':'raw','websocket':'ws','splithttp':'xhttp','mkcp':'kcp'}.get(transport,transport)
    c.update(transport=transport,security=security,parameters=params)
    c.setdefault('flow',params.get('flow','absent'))
    c.setdefault('cipher',params.get('scy','auto') if scheme=='vmess' else 'not-applicable')
    c.setdefault('mode',params.get('mode','default'))
    c['expectedCliExitCode']=6 if c['expected'] in ('reject','core-reject') else (0 if c['expected']=='accept' else None)
    c['expectedCoreExitCode']=0 if c['expected']=='accept' else ('nonzero' if c['expected']=='core-reject' else None)
result={**base,'baseManifestSha256':hashlib.sha256(raw).hexdigest(),'source':base['source']+'; supplemental source audit from NodeFormValidation and advanced model fields','cases':cases}
p=root/'manifest-full.json'
if p.exists():raise SystemExit('Already frozen')
p.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
p.with_suffix('.sha256').write_text(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n')
print('Frozen full manifest:',len(cases),'cases')
