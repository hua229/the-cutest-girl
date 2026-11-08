"""Local-only WebDAV fixture for Android sync regression tests (no real accounts)."""
from email.utils import formatdate
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit
from xml.sax.saxutils import escape
import argparse
import shutil
import time


class DavHandler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def path_on_disk(self):
        path = (self.server.root / unquote(urlsplit(self.path).path).lstrip('/')).resolve()
        if not path.is_relative_to(self.server.root):
            raise ValueError('Path outside fixture directory')
        return path

    def reply(self, status, data=b'', kind='application/octet-stream'):
        self.send_response(status)
        self.send_header('Content-Type', kind)
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_MKCOL(self):
        path = self.path_on_disk()
        exists = path.exists()
        path.mkdir(parents=True, exist_ok=True)
        self.reply(405 if exists else 201)

    def do_PUT(self):
        path = self.path_on_disk()
        path.parent.mkdir(parents=True, exist_ok=True)
        body = self.rfile.read(int(self.headers['Content-Length']))
        if path.name.startswith('slow-'):
            (path.parent.parent.parent / 'upload-started').write_text('ready')
            time.sleep(1)
        # Simulates a record the server always rejects (quota, filename policy).
        # Lets the regression suite prove one bad record cannot stall every upload.
        if path.name.startswith('reject-'):
            self.reply(507)
            return
        path.write_bytes(body)
        self.reply(201)

    def do_GET(self):
        path = self.path_on_disk()
        # Records every record download so the regression suite can prove that
        # incremental sync really stopped re-fetching untouched files (and that
        # an unconfirmed signature is still fetched).
        if path.is_file() and path.suffix == '.json' and path.parent.name == 'records':
            log = path.parent.parent.parent / 'get-log'
            with log.open('a', encoding='utf-8') as handle:
                handle.write(path.name + "\n")
        self.reply(200, path.read_bytes()) if path.is_file() else self.reply(404)

    def do_DELETE(self):
        path = self.path_on_disk()
        # FIX-03：目录删除也要真的生效（模拟用户/服务商删掉 thecutestgirl/records）
        if path.is_dir():
            shutil.rmtree(path, ignore_errors=True)
        elif path.is_file():
            path.unlink()
        self.reply(204)

    def do_PROPFIND(self):
        self.rfile.read(int(self.headers.get('Content-Length', '0')))
        if '/force-failure/' in self.path:
            self.reply(503)
            return
        path = self.path_on_disk()
        if not path.is_dir():
            self.reply(404)
            return
        entries = []
        for child in path.iterdir():
            if not child.is_file():
                continue
            href = escape(self.path.rstrip('/') + '/' + child.name)
            entries.append(
                f'<D:response><D:href>{href}</D:href><D:propstat><D:prop>'
                f'<D:resourcetype/><D:getcontentlength>{child.stat().st_size}</D:getcontentlength>'
                f'<D:getlastmodified>{formatdate(child.stat().st_mtime, usegmt=True)}</D:getlastmodified>'
                '</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>')
        body = ('<?xml version="1.0"?><D:multistatus xmlns:D="DAV:">' +
                ''.join(entries) + '</D:multistatus>').encode()
        self.reply(207, body, 'application/xml')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=18765)
    parser.add_argument('--root', type=Path, required=True)
    args = parser.parse_args()
    server = ThreadingHTTPServer(('127.0.0.1', args.port), DavHandler)
    server.root = args.root.resolve()
    server.root.mkdir(parents=True, exist_ok=True)
    print(f'Test DAV: 127.0.0.1:{args.port}', flush=True)
    server.serve_forever()
