#!/usr/bin/env python3
"""What passes between the phone and the node while the phone is idle (e2e, the VPN through Hysteria2 at rest; #148).

  e2e-keepalive.py CAPTURE SECONDS NODE

CAPTURE is tcpdump -nn -tt -q of the UDP packets between the phone's side of the runner's bridge and the node, both
ways; SECONDS is how long it ran; NODE is the node's address, so a packet from it is one to the phone. Each packet
wakes the phone's radio on a mobile network unless another went just before (the radio stays up a few seconds after
its last packet: its tail). The gaps between packets are the wakes; who sent the first packet of a wake is who woke
it: the phone (its own traffic, its keepalive) or the node (its own keepalive, which pages the phone). A wake of small
packets only (64 bytes or less: a QUIC PING and its ACK) is a keepalive alone.
"""
import re
import statistics
import sys

LINE = re.compile(r"^(\d+\.\d+) IP (\S+) > (\S+): UDP, length (\d+)")
# a radio woken stays up a few seconds (its tail): packets closer than this ride the same wake
SAME_WAKE = 2.0
SMALL = 64


def host(endpoint):
    return endpoint.rsplit(".", 1)[0]


def gaps_line(label, times):
    gaps = sorted(b - a for a, b in zip(times, times[1:]))
    if gaps:
        print("keepalive: %s (s): median %.1f, shortest %.1f, longest %.1f"
              % (label, statistics.median(gaps), gaps[0], gaps[-1]))


def main():
    path, seconds, node = sys.argv[1], float(sys.argv[2]), sys.argv[3]
    packets = []  # (time, from the node, length)
    for line in open(path, encoding="utf-8", errors="replace"):
        m = LINE.match(line)
        if m:
            packets.append((float(m.group(1)), host(m.group(2)) == node, int(m.group(4))))
    if not packets:
        print(f"keepalive: no UDP packet between the phone and the node in {seconds:.0f} s of quiet (no capture?)")
        return 0
    up = [p for p in packets if not p[1]]
    down = [p for p in packets if p[1]]
    print(f"keepalive: {len(packets)} UDP packets in {seconds:.0f} s of quiet: {len(up)} to the node "
          f"({sum(1 for p in up if p[2] <= SMALL)} of {SMALL} bytes or less), {len(down)} from it")
    wakes = [[packets[0]]]
    for a, b in zip(packets, packets[1:]):
        if b[0] - a[0] > SAME_WAKE:
            wakes.append([])
        wakes[-1].append(b)
    by_node = sum(1 for w in wakes if w[0][1])
    print(f"keepalive: {len(wakes)} radio wakes (packets {SAME_WAKE:.0f} s apart or more), "
          f"{len(wakes) * 3600 / seconds:.0f} an hour at this rate; {len(wakes) - by_node} begun by the phone, "
          f"{by_node} by the node")
    gaps_line("gaps between wakes", [w[0][0] for w in wakes])
    alone = [w for w in wakes if all(p[2] <= SMALL for p in w)]
    alone_by_node = sum(1 for w in alone if w[0][1])
    print(f"keepalive: {len(alone)} of the wakes a keepalive alone ({SMALL} bytes or less each way), "
          f"{alone_by_node} of them begun by the node")
    gaps_line("gaps between keepalives alone", [w[0][0] for w in alone])
    return 0


if __name__ == "__main__":
    sys.exit(main())
