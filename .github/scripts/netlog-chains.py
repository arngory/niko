#!/usr/bin/env python3
"""The certificate chains a Chromium net log (chrome --log-net-log) says its verifier accepted, against root lists.

For each verification that ended with a chain: the host, the chain's subjects from the leaf up, and for each key of
the chain whether a list of roots has it (SHA-256 of the SubjectPublicKeyInfo, what Chromium's key pins compare).
The lists are Go files of PEM blocks, as the core carries them (common/certificate/chrome.go, mozilla.go). What it
answers: whether a server that Chrome takes always has a key of a listed root in the chain Chrome verified. A chain
that ends at a certificate the browser trusts by itself, short of a root of the lists, has none.

  netlog-chains.py NETLOG NAME=LIST.go [NAME=LIST.go ...]

The file of a killed browser is cut mid-event: it is read as any sequence of JSON values. Informational: prints,
and always exits 0.
"""
import base64
import hashlib
import json
import re
import subprocess
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


def openssl(arguments, data):
    return subprocess.run(["openssl"] + arguments, input=data, capture_output=True).stdout


def key_hash(pem):
    public = openssl(["x509", "-pubkey", "-noout"], pem.encode())
    spki = openssl(["pkey", "-pubin", "-outform", "der"], public)
    return base64.b64encode(hashlib.sha256(spki).digest()).decode() if spki else ""


def subject(pem):
    out = openssl(["x509", "-noout", "-subject", "-nameopt", "RFC2253"], pem.encode()).decode(errors="replace")
    return out.strip().removeprefix("subject=").strip()[:90] or "?"


PEM = re.compile(r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", re.S)


def main():
    try:
        text = open(sys.argv[1], encoding="utf-8", errors="replace").read()
    except (OSError, IndexError) as e:
        print(f"no net log: {e}")
        return
    lists = {}
    for argument in sys.argv[2:]:
        name, _, path = argument.partition("=")
        try:
            lists[name] = {key_hash(pem) for pem in PEM.findall(open(path).read())} - {""}
        except OSError as e:
            print(f"no list {name}: {e}")
    for name, keys in lists.items():
        print(f"the list {name}: {len(keys)} keys")
    names, events = {}, []
    for value in values(text):
        walk(value, names, events)
    # the host of a verification is in the event that begins it, the chain in the one that ends it: by source
    hosts, seen, without = {}, set(), {name: [] for name in lists}
    for event in events:
        name, params = names.get(event["type"], ""), event["params"]
        if not name.startswith("CERT_VERIF"):
            continue
        source = (event.get("source") or {}).get("id")
        if params.get("host"):
            hosts[source] = str(params["host"])
        verified = params.get("verified_cert")
        if not isinstance(verified, dict) or not isinstance(verified.get("certificates"), list):
            continue
        chain = [c for c in verified["certificates"] if isinstance(c, str)]
        host = hosts.get(source, "?")
        hashes = [key_hash(pem) for pem in chain]
        mark = (host, tuple(hashes))
        if not chain or mark in seen:
            continue
        seen.add(mark)
        print(f"\n{host}: a chain of {len(chain)}, known root: {params.get('is_issued_by_known_root')}")
        for pem, hashed in zip(chain, hashes):
            where = ", ".join(n for n, keys in lists.items() if hashed in keys) or "-"
            print(f"  {subject(pem)}\n    key {hashed}  in: {where}")
        logged = params.get("public_key_hashes")
        if isinstance(logged, list):
            own = {"sha256/" + h for h in hashes}
            extra = [h for h in logged if isinstance(h, str) and h.startswith("sha256/") and h not in own]
            print(f"  the verifier's own key hashes: {len(logged)}" + (f", not of the chain above: {extra}" if extra else ""))
            hashes = hashes + [h.removeprefix("sha256/") for h in extra]
        for name, keys in lists.items():
            if not any(h in keys for h in hashes):
                without[name].append(host)
    if not seen:
        print("the net log has no verified chain (its capture mode leaves certificates out, or no page loaded)")
        return
    print(f"\n{len(seen)} chains")
    for name, hosts_without in without.items():
        if hosts_without:
            print(f"NO key of the list {name} in the chain Chrome verified for: {', '.join(sorted(set(hosts_without)))}")
        else:
            print(f"every chain has a key of the list {name}")


if __name__ == "__main__":
    main()
