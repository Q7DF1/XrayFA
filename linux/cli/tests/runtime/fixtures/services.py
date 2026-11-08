#!/usr/bin/env python3
"""Synthetic destination services. Run only in the isolated fixture namespace."""
import argparse
import http.server
import json
import socket
import socketserver
import ssl
import struct
import threading
import time

class HTTP(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        body=json.dumps({'path':self.path,'service':'xrayfa-fixture'}).encode()
        print(json.dumps({'kind':'http','path':self.path,'peer':self.client_address[0],'time':time.time()}),flush=True)
        self.send_response(200); self.send_header('Content-Length',str(len(body))); self.end_headers(); self.wfile.write(body)
    def log_message(self,*args): pass

def dns(data):
    if len(data)<12:return b''
    end=12
    while end<len(data) and data[end]: end+=data[end]+1
    end+=5
    if end>len(data):return b''
    question=data[12:end]
    print(json.dumps({'kind':'dns','questionHex':question.hex(),'time':time.time()}),flush=True)
    qtype=struct.unpack('!H',data[end-4:end-2])[0]
    answer=(b'\xc0\x0c'+struct.pack('!HHIH',1,1,0,4)+socket.inet_aton('203.0.113.10')) if qtype==1 else b''
    return data[:2]+b'\x81\x80'+b'\x00\x01'+struct.pack('!H',bool(answer))+b'\x00\x00\x00\x00'+question+answer

class DNSUDP(socketserver.BaseRequestHandler):
    def handle(self):
        data,sock=self.request; sock.sendto(dns(data),self.client_address)

class DNSTCP(socketserver.StreamRequestHandler):
    def handle(self):
        size=self.rfile.read(2)
        if len(size)!=2:return
        data=self.rfile.read(struct.unpack('!H',size)[0]);result=dns(data)
        self.wfile.write(struct.pack('!H',len(result))+result)

class Echo(socketserver.BaseRequestHandler):
    def handle(self):
        data,sock=self.request
        print(json.dumps({'kind':'udp','nonce':data.decode(errors='replace'),'time':time.time()}),flush=True)
        sock.sendto(data,self.client_address)

class V6HTTP(http.server.ThreadingHTTPServer):address_family=socket.AF_INET6

def main():
    p=argparse.ArgumentParser();p.add_argument('--cert',required=True);p.add_argument('--key',required=True);args=p.parse_args()
    services=[http.server.ThreadingHTTPServer(('127.0.0.1',18080),HTTP),
              http.server.ThreadingHTTPServer(('127.0.0.1',18443),HTTP),
              socketserver.ThreadingUDPServer(('127.0.0.1',1053),DNSUDP),
              socketserver.ThreadingTCPServer(('127.0.0.1',1053),DNSTCP),
              socketserver.ThreadingUDPServer(('127.0.0.1',19000),Echo),
              V6HTTP(('2001:db8:203::2',18081),HTTP)]
    ctx=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);ctx.load_cert_chain(args.cert,args.key)
    services[1].socket=ctx.wrap_socket(services[1].socket,server_side=True)
    for server in services:threading.Thread(target=server.serve_forever,daemon=True).start()
    threading.Event().wait()

if __name__=='__main__':main()
