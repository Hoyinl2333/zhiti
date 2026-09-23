#!/usr/bin/env python3
"""Serve this demo locally. No external dependencies."""
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from functools import partial
from pathlib import Path
import argparse
p=argparse.ArgumentParser(description='启动知题本地 Demo');p.add_argument('--port',type=int,default=8765);args=p.parse_args()
root=Path(__file__).resolve().parent
try:
 server=ThreadingHTTPServer(('127.0.0.1',args.port),partial(SimpleHTTPRequestHandler,directory=str(root)))
except OSError as e:
 raise SystemExit(f'无法启动本地服务：{e}\n如果预览已在运行，请直接打开 http://127.0.0.1:{args.port} 。')
print(f'知题已启动：http://127.0.0.1:{args.port}\n只在本机开放。按 Ctrl+C 停止。',flush=True)
try:server.serve_forever()
except KeyboardInterrupt:print('\n知题已停止。')
finally:server.server_close()
