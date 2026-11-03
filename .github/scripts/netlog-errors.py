#!/usr/bin/env python3
"""Prints the network errors of a Chromium net log (NaiveProxy --log-net-log).

The writer of a running process leaves the file unclosed and may cut it
mid-event, and the format differs between versions (one JSON object with an
"events" array, NDJSON records, objects back to back). So the file is read as
any sequence of JSON values, skipping what does not parse: the event type
names come from any "logEventTypes" object, the events from any object with a
"type" and "params". It says what it found, or that it could not read the file.
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


def main(path, extra=None):
    try:
        text = open(path, encoding="utf-8", errors="replace").read()
    except OSError as e:
        print(f"net log {path}: not readable: {e}")
        return
    names, events = {}, []
    for value in values(text):
        try:
            walk(value, names, events)
        except RecursionError:
            continue
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
    # optional: a regular expression of event type names to print as well (e.g. TUNNEL|RST_STREAM)
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
