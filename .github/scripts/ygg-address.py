#!/usr/bin/env python3
"""Prints the Yggdrasil address (200::/7) of an ed25519 public key given in hex,
as yggdrasil-go's address.AddrForKey derives it: the key inverted, the count of
its leading ones after the prefix 0x02, then the bits after the first zero."""
import ipaddress
import sys


def addr_for_key(key: bytes) -> str:
    if len(key) != 32:
        raise ValueError("an ed25519 public key is 32 bytes")
    inverted = bytes(~b & 0xFF for b in key)
    ones, done, bits, n, tail = 0, False, 0, 0, []
    for i in range(8 * len(inverted)):
        bit = (inverted[i // 8] >> (7 - i % 8)) & 1
        if not done:
            if bit:
                ones += 1
                continue
            done = True
            continue
        bits = ((bits << 1) | bit) & 0xFF
        n += 1
        if n == 8:
            tail.append(bits)
            n = 0
    addr = bytes([0x02, ones]) + bytes(tail)[:14]
    return str(ipaddress.IPv6Address(addr))


if __name__ == "__main__":
    print(addr_for_key(bytes.fromhex(sys.argv[1].strip())))
