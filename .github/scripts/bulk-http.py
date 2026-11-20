#!/usr/bin/env python3
"""A local HTTP server for the transport interop jobs: what a tunnel carried, counted
and hashed at its far end.

  GET  /bytes/N   N bytes of a fixed stream (the SHA-256 chain of b"niko"); its
                  digest in the header X-Sha256
  POST /sink      reads the body and answers "<length> <sha256>"

Usage: bulk-http.py PORT (listens on 127.0.0.1). The client side checks a download
with `sha256sum` against X-Sha256 and an upload against the answer of /sink.
"""
import hashlib
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BLOCK = 1 << 16


def stream(n):
    """n bytes of the SHA-256 chain, in blocks."""
    seed = hashlib.sha256(b"niko").digest()
    block = b""
    while len(block) < BLOCK:
        seed = hashlib.sha256(seed).digest()
        block += seed
    block = block[:BLOCK]
    while n > 0:
        chunk = block[: min(n, BLOCK)]
        n -= len(chunk)
        yield chunk


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def do_GET(self):
        parts = self.path.strip("/").split("/")
        if len(parts) != 2 or parts[0] != "bytes" or not parts[1].isdigit():
            self.send_error(404)
            return
        n = int(parts[1])
        digest = hashlib.sha256()
        for chunk in stream(n):
            digest.update(chunk)
        self.send_response(200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(n))
        self.send_header("X-Sha256", digest.hexdigest())
        self.end_headers()
        for chunk in stream(n):
            self.wfile.write(chunk)

    def do_POST(self):
        if self.path != "/sink":
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length", "0"))
        digest = hashlib.sha256()
        left = length
        while left > 0:
            chunk = self.rfile.read(min(left, BLOCK))
            if not chunk:
                break
            digest.update(chunk)
            left -= len(chunk)
        body = f"{length - left} {digest.hexdigest()}".encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), Handler).serve_forever()
