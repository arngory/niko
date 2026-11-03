#!/usr/bin/env python3
"""Prints the network errors of a Chromium net log (NaiveProxy --log-net-log).

The writer of a running process leaves the file unclosed and may cut it
mid-event, and the format differs between versions (one JSON object with an
"events" array, NDJSON records, objects back to back). So the file is read as
any sequence of JSON values, skipping what does not parse: the event type
names come from any "logEventTypes" object, the events from any object with a
"type" and "params". It says what it found, or that it could not read the file.

With --connect STATUS it checks instead that a CONNECT through the proxy got
STATUS: within one net log source the tunnel requests and their answers are
paired in order, and only a source with an answer to each request counts, so a
cut tail or a reset stream proves nothing. naive logs its own GET / before the
CONNECT with the same tunnel line ("CONNECT host:port"), and that GET always
gets the site's answer, so the method comes from the HTTP/2 request headers.
Those are logged on the HTTP/2 session's source, right after the tunnel's own
headers: each tunnel request takes the method of the next HTTP/2 request
headers in the log, in order. Without HTTP/2 events (an HTTP/1.1 proxy) the
tunnel line decides. Exit 0 when some CONNECT got STATUS, 1 otherwise.
"""
import json
import re
import sys
from collections import OrderedDict


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


def walk(value, names, events):
    if isinstance(value, dict):
        if isinstance(value.get("logEventTypes"), dict):
            names.update({v: k for k, v in value["logEventTypes"].items()})
        if isinstance(value.get("type"), int) and isinstance(value.get("params"), dict):
            events.append(value)
        for v in value.values():
            if isinstance(v, (dict, list)):
                walk(v, names, events)
    elif isinstance(value, list):
        for v in value:
            walk(v, names, events)


def read(path):
    try:
        text = open(path, encoding="utf-8", errors="replace").read()
    except OSError as e:
        print(f"net log {path}: not readable: {e}")
        return None, {}, []
    names, events = {}, []
    for value in values(text):
        try:
            walk(value, names, events)
        except RecursionError:
            continue
    return text, names, events


def connect(path, want):
    _, names, events = read(path)
    # per source: its tunnel requests ([line method, HTTP/2 method, has credentials]) and their answers
    sources = OrderedDict()
    pending = []
    h2seen = False
    for e in events:
        name = names.get(e["type"])
        source = e.get("source")
        key = source.get("id") if isinstance(source, dict) else None
        params = e["params"]
        if name == "HTTP_TRANSACTION_SEND_TUNNEL_HEADERS":
            line = params.get("line")
            headers = [h for h in params.get("headers") or [] if isinstance(h, str)]
            auth = any(h.lower().startswith("proxy-authorization:") for h in headers)
            request = [line.split(" ", 1)[0] if isinstance(line, str) and line else "?", None, auth]
            sources.setdefault(key, ([], []))[0].append(request)
            pending.append(request)
        elif name == "HTTP_TRANSACTION_HTTP2_SEND_REQUEST_HEADERS":
            h2seen = True
            method = next((h.split(": ", 1)[1] for h in params.get("headers") or []
                           if isinstance(h, str) and h.startswith(":method: ")), "?")
            if pending:
                pending.pop(0)[1] = method
        elif name == "HTTP_TRANSACTION_READ_TUNNEL_RESPONSE_HEADERS":
            headers = params.get("headers") or ["?"]
            status = str(headers[0]).split(" ")
            sources.setdefault(key, ([], []))[1].append(status[1] if len(status) > 1 else "?")
    got = []
    for key, (requests, answers) in sources.items():
        if not requests:
            continue
        methods = [(r[1] or "?") if h2seen else r[0] for r in requests]
        shown = [m + (" +auth" if r[2] else "") for m, r in zip(methods, requests)]
        print(f"source {key}: sent {shown}, answers {answers}")
        if len(answers) == len(requests):
            got += [a for m, a in zip(methods, answers) if m == "CONNECT"]
    ok = want in got
    print(f"net log {path}: CONNECT answered {got or 'nothing complete'}, want {want}: {'ok' if ok else 'FAIL'}")
    return ok


def main(path, extra=None):
    text, names, events = read(path)
    if text is None:
        return
    found = 0
    try:
        pattern = re.compile(extra) if extra else None
    except re.error as e:
        print(f"bad pattern {extra!r}: {e}")
        pattern = None
    for e in events:
        params = e["params"]
        name = names.get(e["type"], str(e["type"]))
        if "net_error" in params or "verifier_errors" in params or (pattern and pattern.search(name)):
            found += 1
            print(name, json.dumps(params)[:600])
    print(f"net log {path}: {len(text)} bytes, {len(events)} events, {found} with errors, {len(names)} event types")


if __name__ == "__main__":
    if sys.argv[1] == "--connect":
        sys.exit(0 if connect(sys.argv[3], sys.argv[2]) else 1)
    # optional: a regular expression of event type names to print as well (e.g. TUNNEL|RST_STREAM)
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
