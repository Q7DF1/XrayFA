"""Live synthetic VLESS -> SOCKS5 acceptance; prints status codes only.
An optional existing loopback transport can provide Internet egress, without
reading its configuration or logging credentials. Never use production share links.
"""
import argparse,os,subprocess,socket,time,signal,json,tempfile,threading,http.server,secrets
p=argparse.ArgumentParser()
p.add_argument('--cli',type=str,required=True)
p.add_argument('--core',required=True)
p.add_argument('--upstream-socks-port',type=int)
p.add_argument('--local-only',action='store_true',help='Verify real VLESS forwarding against a local HTTP destination only')
a=p.parse_args()
from pathlib import Path
os.umask(0o077)
d=Path(tempfile.mkdtemp(prefix='xrayfa-socks-smoke-'));env={**os.environ,'XRAYFA_HOME':str(d/'profile')}
cli=str(Path(a.cli).resolve());core=str(Path(a.core).resolve())
s=socket.socket();s.bind(('127.0.0.1',0));port=s.getsockname()[1];s.close()
u='00000000-0000-4000-8000-000000000001'
# The pinned core blocks private destinations behind VLESS by default. Permit
# only the local HTTP fixture in this synthetic server's outbound configuration.
fixture_freedom={'protocol':'freedom','settings':{'finalRules':[{'action':'allow','network':'tcp','ip':['127.0.0.1/32']}]}}
config={'log':{'loglevel':'info','access':'none'},'inbounds':[{'listen':'127.0.0.1','port':port,'protocol':'vless','settings':{'clients':[{'id':u}],'decryption':'none'}}],'outbounds':[fixture_freedom]}
if a.upstream_socks_port:
 config['outbounds']=[{'protocol':'socks','settings':{'servers':[{'address':'127.0.0.1','port':a.upstream_socks_port}]}}, {**fixture_freedom,'tag':'local-fixture'}]
 config['routing']={'rules':[{'type':'field','ip':['127.0.0.0/8'],'outboundTag':'local-fixture'}]}
f=d/'server.json';f.write_text(json.dumps(config))
server_log=(d/'server.log').open('wb')
server=subprocess.Popen([core,'run','-c',str(f)],stdout=server_log,stderr=server_log)
proxy=None
nonce=secrets.token_hex(16)
class Handler(http.server.BaseHTTPRequestHandler):
 def do_GET(self):
  body=nonce.encode();self.send_response(200);self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def log_message(self,*args):pass
destination=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler)
threading.Thread(target=destination.serve_forever,daemon=True).start()
try:
 link=d/'link';link.write_text(f'vless://{u}@127.0.0.1:{port}?security=none&type=tcp#synthetic')
 for args in [['add','--link-file',str(link)],['ls'],['check','1','--output',str(d/'check.json'),'--core',core]]:
  r=subprocess.run([cli,*args],env=env,capture_output=True);assert r.returncode==0,(args[0],r.returncode)
 log=(d/'runtime.log').open('wb');proxy=subprocess.Popen([cli,'run','1','--core',core],env=env,stdout=log,stderr=log)
 for _ in range(300):
  if proxy.poll() is not None:raise AssertionError('runtime exited')
  try:
   with socket.create_connection(('127.0.0.1',10808),.1):break
  except OSError:time.sleep(.05)
 else:raise AssertionError('listen timeout')
 conflict=subprocess.run([cli,'run','1','--core',core],env=env,capture_output=True,timeout=15)
 assert conflict.returncode!=0, 'occupied port must fail'
 local=subprocess.run(['curl','--proxy','socks5h://127.0.0.1:10808','--noproxy','','--max-time','10','--fail','--silent',f'http://127.0.0.1:{destination.server_port}/'],capture_output=True,text=True)
 assert local.returncode==0 and local.stdout==nonce, 'Local real VLESS data path failed'
 print('local VLESS HTTP nonce matched',flush=True)
 for url in ([] if a.local_only else ['https://example.com','https://www.google.com','https://www.cloudflare.com']):
  r=subprocess.run(['curl','--proxy','socks5h://127.0.0.1:10808','--noproxy','','--max-time','20','-s','-o','/dev/null','-w','%{http_code}',url],capture_output=True,text=True)
  print(url,'curl_exit',r.returncode,'http',r.stdout,flush=True)
  assert r.returncode==0 and 200 <= int(r.stdout) < 400, 'HTTPS proxy acceptance failed'
 proxy.send_signal(signal.SIGTERM);assert proxy.wait(timeout=10)==0
 with socket.socket() as released:
  released.bind(('127.0.0.1',10808))
 assert not list((d/'profile/.local/share/xrayfa').glob('socks-*.json'))
 print('shutdown and private config cleanup passed',flush=True)
finally:
 if proxy and proxy.poll() is None:proxy.terminate();proxy.wait(timeout=10)
 server.terminate();server.wait(timeout=10)
 destination.shutdown();destination.server_close()
 server_log.close()
