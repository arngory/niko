#!/usr/bin/env python3
"""Sends a DNS query (A www.gstatic.com) over UDP to 127.0.0.1:PORT and prints
what came back: "answer" (a reply with records), "empty" (a reply without), or
"timeout" (no reply to three tries, 3 s each). Used by the interop workflows
to send UDP through a proxy: a direct inbound on PORT forwards it to a resolver.
"""
import os
import socket
import struct
import sys


def query(name):
    qid = os.urandom(2)
    labels = b"".join(bytes([len(p)]) + p.encode() for p in name.split("."))
    return qid + struct.pack(">HHHHH", 0x0100, 1, 0, 0, 0) + labels + b"\x00" + struct.pack(">HH", 1, 1)


def main(port):
    q = query("www.gstatic.com")
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.settimeout(3)
    for _ in range(3):
        s.sendto(q, ("127.0.0.1", port))
        try:
            while True:
                r, _ = s.recvfrom(4096)
                # a late reply to an earlier try has the same id: any of them counts
                if len(r) >= 12 and r[:2] == q[:2]:
                    print("answer" if struct.unpack(">H", r[6:8])[0] > 0 else "empty")
                    return
        except OSError:
            # a timeout, or an ICMP error reported on the socket
            continue
    print("timeout")


if __name__ == "__main__":
    main(int(sys.argv[1]))
