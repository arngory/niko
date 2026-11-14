#!/usr/bin/env python3
"""What a Chromium net log (chrome --log-net-log) says of its QUIC sessions' settings.

The transport parameters the client sent (max_udp_payload_size, max_datagram_frame_size, google_connection_options),
the connection options of its sessions, the largest QUIC packet it sent, and the field trials whose names speak of
QUIC or MTU: whether a released Chrome runs MTU discovery by a field trial shows here even where no probe went out.
The file of a killed browser is cut mid-event: it is read as any sequence of JSON values. Informational: prints, and
always exits 0.
"""
import json
import re
import sys


def values(text):
    decoder = json.JSONDecoder()
    i, n = 0, len(text)
    while i < n:
        while i < n and text[i] in " \t\r\n,[]":
            i += 1
        if i >= n:
            return
        try:
            value, i = decoder.raw_decode(text, i)
            yield value
        except (ValueError, RecursionError):
            # unclosed (the outer object, a cut tail): go on inside it
            k = text.find("{", i + 1)
            if k < 0:
                return
            i = k


def walk(value, names, events, trials):
    if isinstance(value, dict):
        if isinstance(value.get("logEventTypes"), dict):
            names.update({v: k for k, v in value["logEventTypes"].items()})
        if isinstance(value.get("activeFieldTrialGroups"), list):
            trials.update(str(t) for t in value["activeFieldTrialGroups"])
        if isinstance(value.get("type"), int) and isinstance(value.get("params"), dict):
            events.append(value)
        for v in value.values():
            if isinstance(v, (dict, list)):
                walk(v, names, events, trials)
    elif isinstance(value, list):
        for v in value:
            walk(v, names, events, trials)


def main():
    try:
        text = open(sys.argv[1], encoding="utf-8", errors="replace").read()
    except (OSError, IndexError) as e:
        print(f"no net log: {e}")
        return
    names, events, trials = {}, [], set()
    for value in values(text):
        walk(value, names, events, trials)
    sent, options, largest, over, packets = set(), set(), 0, 0, 0
    for event in events:
        name, params = names.get(event["type"], ""), event["params"]
        if name == "QUIC_SESSION_TRANSPORT_PARAMETERS_SENT":
            sent.add(str(params.get("quic_transport_parameters", params))[:700])
        elif name == "QUIC_SESSION_PACKET_SENT":
            size = params.get("size")
            if isinstance(size, int):
                packets += 1
                largest = max(largest, size)
                over += size > 1250
        if name.startswith("QUIC_SESSION"):
            for key, value in params.items():
                if "connection_options" in key:
                    options.add(f"{key}: {value}")
    if not events:
        print("the net log has no events that read")
        return
    print(f"QUIC packets sent by the browser's own count: {packets}, the largest {largest}, over 1250 bytes: {over}")
    for line in sorted(sent) or ["none logged"]:
        print(f"transport parameters sent: {line}")
    for line in sorted(options) or ["none logged"]:
        print(f"session connection options: {line}")
    about = sorted(t for t in trials if re.search(r"quic|mtu", t, re.I))
    print(f"field trials about QUIC or MTU ({len(trials)} active in all): {', '.join(about) if about else 'none'}")


if __name__ == "__main__":
    main()
