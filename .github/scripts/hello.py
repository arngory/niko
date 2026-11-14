#!/usr/bin/env python3
"""A TLS ClientHello as a client really sends it, against the one captured from Chrome.

  hello.py capture PORT OUT          accept one connection on 127.0.0.1:PORT, write its first TLS record to OUT (hex)
  hello.py compare REFERENCE GOT     compare two such records, part by part; --strict: exit 1 when they differ

The naive outbound is Chromium's network stack, so its ClientHello should be Chrome's: this shows where it is not
(an extension the library build leaves out, a list in another order). GREASE values count as one; the server name,
the padding, key shares' keys, the ECH payload and session tickets differ by connection and are not compared.
Extension order is not compared: Chrome shuffles it.
"""
import socket
import struct
import sys

NAMES = {
    0x0000: "server_name", 0x0005: "status_request", 0x000a: "supported_groups", 0x000b: "ec_point_formats",
    0x000d: "signature_algorithms", 0x0010: "alpn", 0x0012: "signed_certificate_timestamp", 0x0015: "padding",
    0x0017: "extended_master_secret", 0x001b: "compress_certificate", 0x0023: "session_ticket",
    0x0029: "pre_shared_key", 0x002b: "supported_versions", 0x002d: "psk_key_exchange_modes", 0x0033: "key_share",
    0x44cd: "application_settings", 0xca34: "trust_anchors", 0xfe0d: "encrypted_client_hello",
    0xff01: "renegotiation_info",
}
# what differs by connection or by the server asked
PER_CONNECTION = {0x0000, 0x0015, 0x0033, 0xfe0d, 0x0023, 0x0029}


def grease(value):
    return value & 0x0f0f == 0x0a0a and value >> 8 == value & 0xff


def name(ext):
    return "GREASE" if grease(ext) else f"{NAMES.get(ext, 'unknown')} ({ext:#06x})"


def capture(port, out):
    listener = socket.socket()
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    listener.bind(("127.0.0.1", port))
    listener.listen(1)
    listener.settimeout(120)
    conn, _ = listener.accept()
    conn.settimeout(20)
    data = b""
    while len(data) < 5 or len(data) < 5 + struct.unpack(">H", data[3:5])[0]:
        chunk = conn.recv(65536)
        if not chunk:
            break
        data += chunk
    # the file before the connection closes: who waits for the client's failure finds the record written. A record
    # cut short is not one: nothing is written
    if len(data) >= 5 and len(data) >= 5 + struct.unpack(">H", data[3:5])[0]:
        with open(out, "w") as f:
            f.write(data[:5 + struct.unpack(">H", data[3:5])[0]].hex() + "\n")
    conn.close()


def parse(path):
    raw = bytes.fromhex(open(path).read().strip())
    if raw[0] == 0x16:
        raw = raw[5:]
    if raw[0] != 0x01:
        raise ValueError("not a ClientHello")
    body, i = raw[4:], 0
    i += 2 + 32
    i += 1 + body[i]
    n = struct.unpack(">H", body[i:i + 2])[0]
    ciphers = [struct.unpack(">H", body[k:k + 2])[0] for k in range(i + 2, i + 2 + n, 2)]
    i += 2 + n
    i += 1 + body[i]
    end = i + 2 + struct.unpack(">H", body[i:i + 2])[0]
    i += 2
    extensions = []
    while i + 4 <= end:
        ext, size = struct.unpack(">HH", body[i:i + 4])
        extensions.append((ext, body[i + 4:i + 4 + size]))
        i += 4 + size
    return ciphers, extensions


def shape(path):
    ciphers, extensions = parse(path)
    parts = {"ciphers": ",".join("GREASE" if grease(c) else f"{c:04x}" for c in ciphers)}
    parts["extensions"] = ",".join(sorted({name(ext) for ext, _ in extensions if ext not in (0x0000, 0x0015)}))
    for ext, data in extensions:
        if grease(ext) or ext in PER_CONNECTION:
            continue
        if ext in (0x000a, 0x000d):
            values = [struct.unpack(">H", data[k:k + 2])[0] for k in range(2, len(data), 2)]
            parts[name(ext)] = ",".join("GREASE" if grease(v) else f"{v:04x}" for v in values)
        elif ext == 0x002b:
            values = [struct.unpack(">H", data[k:k + 2])[0] for k in range(1, len(data), 2)]
            parts[name(ext)] = ",".join("GREASE" if grease(v) else f"{v:04x}" for v in values)
        elif ext == 0xca34:
            # the identifiers follow the browser's root store, which moves with its version: their presence and size
            parts[name(ext)] = f"present, {len(data)} bytes"
        else:
            parts[name(ext)] = data.hex()
    for ext, data in extensions:
        if ext == 0x0033:
            groups, k = [], 2
            while k + 4 <= len(data):
                group, size = struct.unpack(">HH", data[k:k + 4])
                groups.append(("GREASE" if grease(group) else f"{group:04x}") + f"/{size}")
                k += 4 + size
            parts[name(ext)] = ",".join(groups)
    return parts


def compare(reference, got, strict):
    want, have = shape(reference), shape(got)
    differ = [key for key in sorted(set(want) | set(have)) if want.get(key) != have.get(key)]
    if not differ:
        print(f"the ClientHello has Chrome's shape: {len(have)} parts, extensions {have['extensions']}")
        return 0
    for key in differ:
        if key == "extensions":
            theirs, ours = set(want[key].split(",")), set(have[key].split(","))
            print(f"::warning::ClientHello differs from Chrome's in its extensions: only Chrome sends "
                  f"{', '.join(sorted(theirs - ours)) or 'none'}; only this client sends "
                  f"{', '.join(sorted(ours - theirs)) or 'none'}")
            continue
        print(f"::warning::ClientHello differs from Chrome's in {key}: Chrome {want.get(key, 'absent')!s:.300}, "
              f"here {have.get(key, 'absent')!s:.300}")
    return 1 if strict else 0


if __name__ == "__main__":
    if len(sys.argv) >= 4 and sys.argv[1] == "capture":
        capture(int(sys.argv[2]), sys.argv[3])
    elif len(sys.argv) >= 4 and sys.argv[1] == "compare":
        sys.exit(compare(sys.argv[2], sys.argv[3], "--strict" in sys.argv[4:]))
    else:
        sys.exit(__doc__)
