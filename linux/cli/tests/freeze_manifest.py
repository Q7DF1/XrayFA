# Synthetic test credentials only; never use production node inputs here.
"""Freeze finite support candidates BEFORE CLI implementation. Synthetic credentials only.
Do not alter expectations in response to failures; record a compatibility defect instead.
"""
import base64, hashlib, itertools, json
from pathlib import Path
from urllib.parse import urlencode, quote
ROOT = Path(__file__).resolve().parent
B64 = lambda x: base64.b64encode(x if isinstance(x, bytes) else x.encode()).decode()
UUID = '00000000-0000-4000-8000-000000000001'
KEY = base64.urlsafe_b64encode(bytes(range(32))).decode().rstrip('=')
# RFC 9460 ECHConfigList: valid X25519/HKDF-SHA256/AES-128-GCM test config.
ech_body = bytes([1])+bytes.fromhex('0020')+bytes.fromhex('0020')+bytes(range(32))+bytes.fromhex('000400010001')+bytes([0,11])+b'example.com'+bytes.fromhex('0000')
ech = B64((len(ech_body)+4).to_bytes(2,'big')+bytes.fromhex('fe0d')+len(ech_body).to_bytes(2,'big')+ech_body)
PIN = bytes(range(32)).hex()
CASES=[]
# Fixture correction v2 before first execution: fixed Hysteria2 defaults and VMess header encoding.
def link(p, t='raw', s='none', extra=None, host='example.com', port=443, credential=None):
    q={} if p in ('hysteria2','hy2') else {'type':t,'security':s}
    if s=='reality': q.update(pbk=KEY,sni='example.com',sid='0123456789abcdef')
    q.update(extra or {})
    # NodeFormValidation / core prohibit unencrypted VLESS/Trojan to public endpoints.
    if p in ('vless','trojan') and s=='none' and host=='example.com': host='example.test'
    if t in ('kcp','mkcp') and q.get('mtu','1350').isdigit() and int(q.get('mtu','1350'))>1350 and 'maxSendingWindow' not in q: q['maxSendingWindow']=q['mtu']
    if p=='vmess':
        j={'v':'2','ps':'Tést Nöde','id':credential or UUID,'add':host.strip('[]'),'port':port,'net':q.pop('type'),'tls':q.pop('security'),'scy':q.pop('scy','auto')}
        if 'headerType' in q: j['type']=q.pop('headerType')
        j.update(q)
        return 'vmess://'+B64(json.dumps(j,ensure_ascii=False))
    cred=credential or (UUID if p=='vless' else 'test-password')
    return f'{p}://{quote(cred,safe="")}@{host}:{port}?{urlencode(q)}#'+quote('Tést Nöde')
def add(id,p,uri,expected='accept',reason='Existing parser/model support',**kw):
    CASES.append(dict(id=id,protocol=p,input=uri,inputFormat='base64-json' if p=='vmess' else 'uri',expected=expected,stage='add' if expected=='reject' else 'check',reason=reason,assertions=kw.pop('assertions',{}),**kw))
def stream_assert(t,s): return {'outbounds.0.streamSettings.network':t,'outbounds.0.streamSettings.security':s}
# 54 mandatory cells, expanded across all compatible finite flow/cipher/mode values.
for p,t,s in itertools.product(['vless','vmess','trojan'],['raw','ws','grpc','xhttp','httpupgrade','kcp'],['none','tls','reality']):
    base=f'matrix-{p}-{t}-{s}'
    if s=='reality' and t not in ('raw','grpc','xhttp'):
        add(base,p,link(p,t,s),'reject','ShareLinkStreamSettings restricts REALITY to raw/grpc/xhttp',transport=t,security=s,mainCell=base)
        continue
    modes=['auto','packet-up','stream-up','stream-one'] if t=='xhttp' else (['default','gun','multi'] if t=='grpc' else ['default'])
    # Vision only works on VLESS RAW TLS/REALITY. Other flow/transport combos get explicit negatives below.
    flows=['absent','empty']+(['xtls-rprx-vision','xtls-rprx-vision-udp443'] if t=='raw' and s!='none' else []) if p=='vless' else ['absent']
    ciphers=['auto','aes-128-gcm','chacha20-poly1305','none','zero'] if p=='vmess' else ['absent']
    for mode,flow,cipher in itertools.product(modes,flows,ciphers):
        q={}
        a=stream_assert(t,s)
        if mode!='default': q['mode']=mode
        if t=='grpc': a['outbounds.0.streamSettings.grpcSettings.multiMode']=mode=='multi'
        if t=='xhttp': a['outbounds.0.streamSettings.xhttpSettings.mode']=mode
        if flow!='absent': q['flow']='' if flow=='empty' else flow
        if p=='vless':
            a['outbounds.0.settings.vnext.0.users.0.flow']='' if flow in ('absent','empty') else flow
            a['outbounds.0.settings.vnext.0.users.0.encryption']='none'
        if cipher!='absent': q['scy']=cipher; a['outbounds.0.settings.vnext.0.users.0.security']=cipher
        add(f'{base}-{mode}-{flow}-{cipher}',p,link(p,t,s,q),transport=t,security=s,mode=mode,flow=flow,cipher=cipher,mainCell=base,assertions=a)
for p in ('vless','vmess','trojan'):
    for alias,normal in [('tcp','raw'),('websocket','ws'),('splithttp','xhttp'),('mkcp','kcp')]:
        add(f'alias-{p}-{alias}',p,link(p,alias),assertions=stream_assert(normal,'none'))
    for q in [{'type':'unknown'},{'security':'unknown'},{'type':'grpc','mode':'wrong'},{'type':'raw','headerType':'wrong'},{'type':'xhttp','mode':'wrong'},{'type':'xhttp','extra':'[]'},{'type':'xhttp','extra':'{'},{'type':'kcp','seed':'legacy'},{'type':'kcp','path':'legacy'},{'type':'kcp','headerType':'srtp'}]:
        add(f'negative-{p}-params-{len(CASES)}',p,link(p,extra=q),'reject','Unsupported/invalid transport parameter')
    for t in ('raw','ws','httpupgrade','grpc','xhttp'):
        path='/páth/a?x=1&y=%25'
        q={'host':'cdn.example.com','path':path}
        if t=='grpc': q.update(serviceName='service/tést',authority='grpc.example.com')
        settings={'raw':'rawSettings.header.request','ws':'wsSettings','httpupgrade':'httpUpgradeSettings','grpc':'grpcSettings','xhttp':'xhttpSettings'}[t]
        if t=='raw': q['headerType']='http'
        a={}
        if t=='raw': a['outbounds.0.streamSettings.'+settings+'.path']=[path]; a['outbounds.0.streamSettings.'+settings+'.headers.Host']=['cdn.example.com']
        elif t=='grpc': a['outbounds.0.streamSettings.grpcSettings.serviceName']=path if p=='vmess' else 'service/tést'; a['outbounds.0.streamSettings.grpcSettings.authority']='grpc.example.com'
        else: a['outbounds.0.streamSettings.'+settings+'.path']=path; a['outbounds.0.streamSettings.'+settings+'.host']='cdn.example.com'
        add(f'path-{p}-{t}',p,link(p,t,extra=q),assertions=a)
    extra={'headers':{'X-Test':'value'},'xmux':{'maxConcurrency':'16-32'},'downloadSettings':{'address':'example.com','port':443,'network':'xhttp','security':'tls','tlsSettings':{'serverName':'example.com'},'xhttpSettings':{'path':'/down','mode':'stream-one'}}}
    add(f'extra-{p}',p,link(p,'xhttp','tls',{'extra':json.dumps(extra)}),assertions={'outbounds.0.streamSettings.xhttpSettings.extra':extra})
    for fp in ('','chrome','firefox','safari','edge','android','ios','random','randomized'):
        add(f'tls-fp-{p}-{fp or "default"}',p,link(p,'raw','tls',{'fp':fp}),assertions={'outbounds.0.streamSettings.tlsSettings.serverName':'example.com',**({'outbounds.0.streamSettings.tlsSettings.fingerprint':fp} if fp else {})})
    # All finite TLS verification switch combinations: pcs/vcn/ech, on/off.
    for pcs,vcn,e in itertools.product((False,True),repeat=3):
        q={'sni':'tls.example.com','peer':'peer.example.com','host':'host.example.com','alpn':'h2,http/1.1'}
        a={'outbounds.0.streamSettings.tlsSettings.serverName':'tls.example.com','outbounds.0.streamSettings.tlsSettings.alpn':['h2','http/1.1']}
        for enabled,k,v,dest in [(pcs,'pcs',PIN,'pinnedPeerCertSha256'),(vcn,'vcn','example.com','verifyPeerCertByName'),(e,'ech',ech,'echConfigList')]:
            if enabled: q[k]=v; a['outbounds.0.streamSettings.tlsSettings.'+dest]=v
        add(f'tls-options-{p}-{int(pcs)}{int(vcn)}{int(e)}',p,link(p,'raw','tls',q),assertions=a)
    for q,expected in [({'peer':'peer.example.com','host':'host.example.com'},'peer.example.com'),({'host':'host.example.com'},'host.example.com'),({},'example.com')]:
        add(f'tls-fallback-{p}-{len(CASES)}',p,link(p,'raw','tls',q),assertions={'outbounds.0.streamSettings.tlsSettings.serverName':expected})
    for key in ('allowInsecure','insecure','skip-cert-verify'):
        for val in ('true','1'):
            add(f'insecure-{p}-{key}-{val}',p,link(p,'raw','tls',{key:val}),'reject','Removed insecure TLS behavior')
        add(f'secure-{p}-{key}',p,link(p,'raw','tls',{key:'false'}))
    for alias in ('pbk','password','publicKey'):
        q={'pbk':'',alias:KEY,'spx':'/spider?x=1'}
        add(f'reality-key-{p}-{alias}',p,link(p,'raw','reality',q),assertions={'outbounds.0.streamSettings.realitySettings.password':KEY,'outbounds.0.streamSettings.realitySettings.spiderX':'/spider?x=1'})
    for key,bad in [('pbk','bad'),('sid','xyz'),('sid','0'),('sid','00'*9),('pqv','bad'),('mldsa65Verify','bad')]:
        add(f'reality-invalid-{p}-{key}-{len(CASES)}',p,link(p,'raw','reality',{key:bad}),'reject','Invalid REALITY length/encoding')
    for k,lo,hi in [('mtu',21,4294967295),('tti',10,1000),('uplinkCapacity',0,4294967295),('downlinkCapacity',0,4294967295),('cwndMultiplier',1,4294967295),('maxSendingWindow',1350,4294967295)]:
        for v in (lo,hi):
            add(f'kcp-{p}-{k}-{v}',p,link(p,'kcp',extra={k:str(v)}),assertions={f'outbounds.0.streamSettings.kcpSettings.{k}':v})
        for v in (lo-1,hi+1,'NaN'):
            add(f'kcp-invalid-{p}-{k}-{v}',p,link(p,'kcp',extra={k:str(v)}),'reject','mKCP numeric bounds')
    add(f'kcp-all-{p}',p,link(p,'kcp',extra={'mtu':'1400','tti':'20','uplinkCapacity':'5','downlinkCapacity':'20','cwndMultiplier':'2','maxSendingWindow':'2800'}))
# Shadowsocks: all UI methods and existing aliases, both SIP002 and legacy formats.
methods=['aes-128-gcm','aes-256-gcm','chacha20-ietf-poly1305','xchacha20-ietf-poly1305','2022-blake3-aes-128-gcm','2022-blake3-aes-256-gcm','2022-blake3-chacha20-poly1305']
for method,fmt,host in itertools.product(methods,['sip002','legacy'],['example.com','192.0.2.1','[2001:db8::1]']):
    password=B64(bytes(range(16 if method=='2022-blake3-aes-128-gcm' else 32))) if method.startswith('2022') else 'test@:/+%&'
    auth=f'{method}:{password}'
    uri='ss://'+(B64(auth)+'@'+host+':8388' if fmt=='sip002' else B64(auth+'@'+host+':8388'))+'#'+quote('Tést Nöde')
    add(f'ss-{method}-{fmt}-{host}', 'ss',uri,inputVariant=fmt,assertions={'outbounds.0.settings.servers.0.method':method,'outbounds.0.settings.servers.0.password':password,'outbounds.0.settings.servers.0.address':host.strip('[]')})
for alias,canonical in [('chacha20-poly1305','chacha20-ietf-poly1305'),('aead_chacha20_poly1305','chacha20-ietf-poly1305'),('xchacha20-poly1305','xchacha20-ietf-poly1305'),('aead_xchacha20_poly1305','xchacha20-ietf-poly1305')]:
    add('ss-alias-'+alias,'ss','ss://'+B64(alias+':test')+'@example.com:8388',assertions={'outbounds.0.settings.servers.0.method':canonical})
for method in methods[4:]:
    for password in ('bad!',B64(b'short'),B64(bytes(16)) if method!='2022-blake3-aes-128-gcm' else B64(bytes(32))):
        add('ss-invalid-'+str(len(CASES)),'ss','ss://'+B64(method+':'+password)+'@example.com:8388','reject','2022 key length/encoding')
add('ss-plugin','ss','ss://'+B64('aes-128-gcm:test')+'@example.com:8388?plugin=obfs-local','reject','No plugin support')
for scheme,obfs in itertools.product(['hysteria2','hy2'],['none','salamander']):
    q={'sni':'example.com','alpn':'h3'}
    if obfs=='salamander': q.update(obfs=obfs,**{'obfs-password':'mask@:/+%&'})
    add(f'hy2-{scheme}-{obfs}','hysteria2',link(scheme,extra=q,credential='auth@:/+%&'),assertions={'outbounds.0.protocol':'hysteria','outbounds.0.streamSettings.network':'hysteria','outbounds.0.streamSettings.security':'tls','outbounds.0.streamSettings.hysteriaSettings.auth':'auth@:/+%&',**({'outbounds.0.streamSettings.finalMask.udp.0.settings.password':'mask@:/+%&'} if obfs=='salamander' else {})})
for q in ({'obfs':'unknown'},{'obfs':'salamander'},{'obfs':'salamander','obfs-password':''},{'obfs-password':'orphan'},{'security':'none'},{'type':'ws'}):
    add('hy2-invalid-'+str(len(CASES)),'hysteria2',link('hy2',extra=q),'reject','Unsupported Hysteria2 semantics')
for p in ('socks','socks5','http'):
    for host,port,auth in itertools.product(['example.com','192.0.2.1','[2001:db8::1]'],['',':1',':65535'],['','user:'+quote('p@:/+%&',safe='')+'@',B64('user:password')+'@']):
        a={'outbounds.0.settings.servers.0.address':host.strip('[]'),'outbounds.0.settings.servers.0.port':int(port[1:]) if port else (8080 if p=='http' else 1080)}
        add(f'proxy-{p}-{len(CASES)}','socks' if p=='socks5' else p,f'{p}://{auth}{host}{port}#'+quote('Tést Nöde'),assertions=a)
    for query in ('security=tls','type=ws','plugin=obfs'):
        add(f'proxy-invalid-{p}-{query}',p,f'{p}://example.com:1080?{query}','reject','No proxy TLS/transport/plugin input support')
for p in ('vless','vmess','trojan','hysteria2'):
    for host,port in itertools.product(['example.com','192.0.2.1','[2001:db8::1]'],[1,65535]):
        add(f'endpoint-{p}-{host}-{port}',p,link(p,s='tls',host=host,port=port))
    for port in (0,65536,-2): add(f'port-invalid-{p}-{port}',p,link(p,port=port),'reject','Invalid port')
    add(f'host-missing-{p}',p,link(p,host=''),'reject','Missing server')
for p in ('socks','http'):
    for port in ('0','65536','bad','-2'): add(f'port-invalid-{p}-{port}',p,f'{p}://example.com:{port}','reject','Invalid explicit port')
for id,uri in [('https-entry','https://example.com:443'),('unknown-scheme','unknown://a@b:443'),('truncated-vmess','vmess://e30'),('malformed-vmess','vmess://@@'),('malformed-ss','ss://@@'),('missing-vless-auth','vless://example.com:443'),('bad-percent','trojan://p%ZZ@example.com:443')]: add(id,'invalid',uri,'reject','Invalid input/no factory entry')
# Explicit duplicate policy: last query value wins (associate in ShareLinkUri).
add('duplicate-query','vless',link('vless')+'&type=ws' if False else f'vless://{UUID}@example.test:443?type=ws&type=raw',assertions=stream_assert('raw','none'))
add('vmess-string-port','vmess',link('vmess',port='443'))
add('trojan-default-port','trojan','trojan://password@example.com')
add('hy2-default-port','hysteria2','hy2://auth@example.com')
# Inapplicable values stay visible, never silently skipped.
for p in ('ss','socks','http','hysteria2'):
    add('na-arbitrary-transports-'+p,p,'',expected='not-applicable',reason='Factory offers fixed stream; unsupported query has explicit rejection cases',transport='arbitrary',security='arbitrary')
result={'schemaVersion':1,'source':'ParserFactory + ShareLinkStreamSettings + protocol parsers + NodeFormOptions; frozen before Linux CLI implementation','mainCells':54,'cases':CASES}
path=ROOT/'manifest.json'
if path.exists(): raise SystemExit('Manifest already frozen; review changes explicitly')
path.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
(ROOT/'manifest.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()+'  manifest.json\n')
print('Frozen',len(CASES),'cases')
