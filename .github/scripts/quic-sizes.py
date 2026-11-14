#!/usr/bin/env python3
"""The sizes of the QUIC packets in tcpdump's text (tcpdump -n -r capture 'udp port 443'), by direction.

What a real Chrome sends to a site over HTTP/3: the largest UDP payload from the client and from the server, the
payloads over 1250 bytes from the client (Chromium's packet size to a site over IPv4), and the commonest sizes.
The naive outbound is Chromium too, so a client option that changes these sizes (MTU discovery) would show against
this. Informational: prints, and always exits 0.
"""
import collections
import re
import sys

LINE = re.compile(r" IP6? (\S+)\.(\d+) > (\S+)\.(\d+): UDP, length (\d+)")


def main():
    up, down = collections.Counter(), collections.Counter()
    for line in sys.stdin:
        m = LINE.search(line)
        if not m:
            continue
        size = int(m.group(5))
        if m.group(4) == "443":
            up[size] += 1
        elif m.group(2) == "443":
            down[size] += 1
    if not up:
        print("no QUIC packets from the client in the capture (UDP 443 closed on this runner, or no HTTP/3)")
        return
    for name, sizes in (("client to server", up), ("server to client", down)):
        if not sizes:
            print(f"{name}: none")
            continue
        total = sum(sizes.values())
        common = ", ".join(f"{size} x{count}" for size, count in sizes.most_common(6))
        print(f"{name}: {total} packets, the largest UDP payload {max(sizes)}; the commonest: {common}")
    over = {size: count for size, count in up.items() if size > 1250}
    if over:
        listed = ", ".join(f"{size} x{count}" for size, count in sorted(over.items()))
        print(f"client packets over 1250 bytes: {sum(over.values())} ({listed})")
    else:
        print("client packets over 1250 bytes: none")


if __name__ == "__main__":
    main()
