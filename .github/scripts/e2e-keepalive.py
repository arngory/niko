#!/usr/bin/env python3
"""What the phone sends the node while it is idle (e2e, the VPN through Hysteria2 at rest; #148).

  e2e-keepalive.py CAPTURE SECONDS

CAPTURE is tcpdump -nn -tt -q of the UDP packets to the node on the runner's bridge, SECONDS how long it ran. Each
packet the phone sends wakes its radio on a mobile network unless another went just before: the gaps between them
are the wakes. QUIC's keepalive (Hysteria2: a PING every 10 s an idle connection) shows as small packets at that
period; the system's and the apps' own traffic as the rest.
"""
import re
import statistics
import sys

LINE = re.compile(r"^(\d+\.\d+) IP \S+ > \S+: UDP, length (\d+)")
# a radio woken stays up a few seconds (its tail): packets closer than this ride the same wake
SAME_WAKE = 2.0


def main():
    path, seconds = sys.argv[1], float(sys.argv[2])
    times, lengths = [], []
    for line in open(path, encoding="utf-8", errors="replace"):
        m = LINE.match(line)
        if m:
            times.append(float(m.group(1)))
            lengths.append(int(m.group(2)))
    if not times:
        print(f"keepalive: no UDP packet to the node in {seconds:.0f} s of quiet (no capture?)")
        return 0
    wakes = 1 + sum(1 for a, b in zip(times, times[1:]) if b - a > SAME_WAKE)
    gaps = [b - a for a, b in zip(times, times[1:]) if b - a > SAME_WAKE]
    small = sum(1 for n in lengths if n <= 64)
    print(f"keepalive: {len(times)} UDP packets to the node in {seconds:.0f} s of quiet, {small} of them 64 bytes or less")
    print(f"keepalive: {wakes} radio wakes (packets {SAME_WAKE:.0f} s apart or more), {wakes * 3600 / seconds:.0f} an hour at this rate")
    if gaps:
        gaps.sort()
        print("keepalive: gaps between wakes (s): median %.1f, shortest %.1f, longest %.1f"
              % (statistics.median(gaps), gaps[0], gaps[-1]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
