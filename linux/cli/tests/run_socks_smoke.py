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
with socket.socket() as preflight:
 preflight.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
 try:
  preflight.bind(('127.0.0.1',10808));preflight.listen()
 except OSError:
  raise SystemExit('SOCKS5 smoke test requires a free 127.0.0.1:10808; an existing listener must be stopped first')
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
def wait_for_proxy(process):
 for _ in range(300):
  if process.poll() is not None:raise AssertionError('runtime exited before listening')
  try:
   with socket.create_connection(('127.0.0.1',10808),.1):return
  except OSError:time.sleep(.05)
 raise AssertionError('listen timeout')
def verify_local_forwarding():
 local=subprocess.run(['curl','--proxy','socks5h://127.0.0.1:10808','--noproxy','','--max-time','10','--fail','--silent',f'http://127.0.0.1:{destination.server_port}/'],capture_output=True,text=True)
 assert local.returncode==0 and local.stdout==nonce, 'Local real VLESS data path failed'
try:
 link=d/'link';link.write_text(f'vless://{u}@127.0.0.1:{port}?security=none&type=tcp#synthetic')
 for args in [['add','--link-file',str(link)],['ls'],['check','1','--output',str(d/'check.json'),'--core',core]]:
  r=subprocess.run([cli,*args],env=env,capture_output=True);assert r.returncode==0,(args[0],r.returncode)
 log=(d/'runtime.log').open('wb');proxy=subprocess.Popen([cli,'run','1','--core',core],env=env,stdout=log,stderr=log)
 wait_for_proxy(proxy)
 conflict=subprocess.run([cli,'run','1','--core',core],env=env,capture_output=True,timeout=15)
 assert conflict.returncode!=0, 'occupied port must fail'
 verify_local_forwarding()
 print('local VLESS HTTP nonce matched',flush=True)
 for url in ([] if a.local_only else ['https://example.com','https://www.google.com','https://www.cloudflare.com']):
  r=subprocess.run(['curl','--proxy','socks5h://127.0.0.1:10808','--noproxy','','--max-time','20','-s','-o','/dev/null','-w','%{http_code}',url],capture_output=True,text=True)
  print(url,'curl_exit',r.returncode,'http',r.stdout,flush=True)
  assert r.returncode==0 and 200 <= int(r.stdout) < 400, 'HTTPS proxy acceptance failed'
 proxy.send_signal(signal.SIGINT);assert proxy.wait(timeout=10)==0
 with socket.socket() as released:
  released.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
  released.bind(('127.0.0.1',10808))
  released.listen()
 assert not list((d/'profile/.local/share/xrayfa').glob('socks-*.json'))
 proxy=subprocess.Popen([cli,'run','1','--core',core],env=env,stdout=log,stderr=log)
 wait_for_proxy(proxy)
 verify_local_forwarding()
 proxy.send_signal(signal.SIGTERM);assert proxy.wait(timeout=10)==0
 assert not list((d/'profile/.local/share/xrayfa').glob('socks-*.json'))
 print('shutdown and private config cleanup passed',flush=True)
finally:
 if proxy and proxy.poll() is None:proxy.terminate();proxy.wait(timeout=10)
 server.terminate();server.wait(timeout=10)
 destination.shutdown();destination.server_close()
 server_log.close()
