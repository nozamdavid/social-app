"""Serve Android App Bundles with explicit attachment filenames."""

import argparse
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote, urlsplit

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--file', type=Path, required=True)
parser.add_argument('--port', type=int, default=18788)
args = parser.parse_args()
bundle = args.file.resolve(strict=True)
if bundle.suffix != '.aab':
    parser.error('--file must be an .aab file')


class BundleHandler(SimpleHTTPRequestHandler):
    def send_head(self):
        path = urlsplit(self.path).path
        name = path.removeprefix('/')
        target = bundle if path == '/' else bundle.parent / name
        if (
            (path != '/' and ('/' in name or not name.endswith('.aab')))
            or target.resolve().parent != bundle.parent
            or not target.is_file()
        ):
            self.send_error(404)
            return None
        stream = target.open('rb')
        self.send_response(200)
        self.send_header('Content-Type', 'application/octet-stream')
        self.send_header('Content-Length', str(target.stat().st_size))
        self.send_header(
            'Content-Disposition',
            f'attachment; filename="{target.name}"; filename*=UTF-8\'\'{quote(target.name)}',
        )
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        return stream


ThreadingHTTPServer(('127.0.0.1', args.port), BundleHandler).serve_forever()
