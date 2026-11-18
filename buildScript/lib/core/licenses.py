#!/usr/bin/env python3
"""The licenses of the Go modules a built library holds, from its own build info.

    licenses.py [--aar AAR] --out JSON BINARY...

`go version -m` reads the modules each BINARY (a Go executable or shared library: libgojni.so of every ABI) was linked
from; the license files of each are read where the build took it: the module cache, or the directory a local replace
names (the core's checkout). The Go toolchain's own (the runtime and standard library) is the GOROOT of the `go` on
PATH, as GOTOOLCHAIN selects it. A module with no license file, or a license file this script does not know, stops
it: what the app shows and NOTICE says are to be the build's whole truth, so a new dependency is looked at once,
here. --aar writes the JSON into the AAR as assets/licenses/go.json, where the app's build takes its assets from.

The JSON: {"v": 1, "go": "<version>", "modules": [{"path", "version", "source", "licenses": [SPDX...],
"files": [{"name", "text": <index into texts>}]}], "texts": [...]}; "source" is where a module of a local replace
comes from (its repository and commit), else absent. Only the standard library of Python: run it with `python3 -I`.
"""

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import zipfile

ASSET = "assets/licenses/go.json"

# the files of a module that carry its license or notices
LICENSE_FILE = re.compile(r"^(UN)?LICEN[CS]E|^COPYING|^COPYRIGHT|^NOTICE|^PATENTS", re.IGNORECASE)
# of those, the ones that are no license by themselves: notices that go along with one
NOTICE_FILE = re.compile(r"^NOTICE|^PATENTS|^COPYRIGHT", re.IGNORECASE)
# a file larger is not a license file read by mistake: Chromium's NOTICE, which the cronet-go modules carry
# (NOTICE.chromium.txt, the credits of every library Chromium builds in), is some 260 KB
TOO_LARGE = 512 * 1024

# what this repository builds of its own: under its LICENSE at the root
OWN = {"gobind", "libcore"}


def normalized(text):
    text = re.sub(r"^[ \t]*(//|#|\*|/\*|\*/)", " ", text, flags=re.MULTILINE)
    return re.sub(r"\s+", " ", text).strip().lower()


# What marks a license in a text, as normalized() leaves it. The GNU licenses by their title or the notice that applies
# them, never by a mention: the GPL's text names the LGPL, the MPL's the LGPL and the AGPL. An id with ! is a license
# this build is not to link in unlooked-at: under GPL-3.0 the app could not carry it, or it asks something the app
# does not do.
MARKERS = [
    ("MPL-2.0", r"mozilla public license,? version 2\.0"),
    ("LGPL-3.0", r"gnu lesser general public license version 3, 29 june 2007"
                 r"|gnu lesser general public license as published by the free software foundation,? either version 3"),
    ("GPL-3.0", r"gnu general public license version 3, 29 june 2007"
                r"|gnu general public license as published by the free software foundation,? either version 3"),
    ("!AGPL-3.0", r"gnu affero general public license version 3, 19 november 2007"),
    ("!GPL-2.0", r"gnu general public license version 2, june 1991"),
    ("!LGPL-2.1", r"gnu lesser general public license version 2\.1, february 1999"),
    ("Apache-2.0", r"apache license,? version 2\.0"),
    ("MIT", r"permission is hereby granted, free of charge"),
    ("BSD", r"redistribution and use in source and binary forms"),
    ("ISC", r"permission to use, copy, modify, and(/or)? distribute this software for any purpose"),
    ("Unlicense", r"this is free and unencumbered software released into the public domain"),
    ("CC0-1.0", r"cc0 1\.0 universal"),
    ("Zlib", r"provided 'as-is', without any express or implied warranty"),
]


def classify(text):
    """The SPDX identifiers of the licenses in a text, in the order they come; empty for none known here."""
    t = normalized(text)
    found = []
    for kind, marker in MARKERS:
        for m in re.finditer(marker, t):
            spdx = kind
            if kind == "BSD":
                # the clauses that follow say which: a third on the names, a fourth on advertising
                clauses = t[m.end():m.end() + 1500]
                if "all advertising materials" in clauses:
                    spdx = "!BSD-4-Clause"
                elif re.search(r"neither the name|names of (its|the) contributors|name of the copyright holder",
                               clauses):
                    spdx = "BSD-3-Clause"
                else:
                    spdx = "BSD-2-Clause"
            elif kind == "LGPL-3.0" and re.search(r"as a special exception to the gnu lesser general public "
                                                  r"license version 3", t):
                spdx = "LGPL-3.0 WITH LGPL-3.0-linking-exception"
            elif kind == "Zlib" and "altered source versions must be plainly marked" not in t:
                continue
            found.append((m.start(), spdx))
            if kind != "BSD":
                break  # one is enough; a file may hold BSD texts of both kinds
    ordered = []
    for _, spdx in sorted(found):
        if spdx not in ordered:
            ordered.append(spdx)
    return ordered


def go(*args, cwd=None):
    return subprocess.run(["go", *args], check=True, capture_output=True, text=True, cwd=cwd).stdout


def built_modules(binary):
    """(go version, [(path, version, dir path or None, replacement path or None, replacement version)])"""
    version = None
    modules = []
    for line in go("version", "-m", binary).splitlines():
        fields = line.split("\t")
        if version is None:
            version = line.rsplit(" ", 1)[-1].strip()
            continue
        fields = [f for f in fields if f]
        if not fields:
            continue
        if fields[0] in ("mod", "dep"):
            modules.append([fields[1], fields[2] if len(fields) > 2 else "", None, None])
        elif fields[0] == "=>" and modules:
            modules[-1][2] = fields[1]
            modules[-1][3] = fields[2] if len(fields) > 2 else ""
    return version, modules


def escaped(path):
    # the module cache's case encoding: an upper-case letter as ! and its lower case
    return re.sub(r"[A-Z]", lambda m: "!" + m.group(0).lower(), path)


def module_dir(cache, path, version):
    d = os.path.join(cache, escaped(path) + "@" + version)
    if os.path.isdir(d):
        return d
    info = json.loads(go("mod", "download", "-json", path + "@" + version))
    return info["Dir"]


def repository_name(url):
    """host/owner/name of a clone URL: no scheme, and no user or token the URL may carry."""
    if re.match(r"^[\w.-]+@[\w.-]+:", url):
        url = url.split("@", 1)[1].replace(":", "/", 1)
    return re.sub(r"\.git$", "", re.sub(r"^[a-z][a-z0-9+.-]*://([^@/]*@)?", "", url))


def git_source(directory):
    """'<repository>@<commit>[/<subdirectory>]' of a local checkout, None outside one."""
    try:
        top = subprocess.run(["git", "-C", directory, "rev-parse", "--show-toplevel"], check=True,
                             capture_output=True, text=True).stdout.strip()
        commit = subprocess.run(["git", "-C", directory, "rev-parse", "HEAD"], check=True,
                                capture_output=True, text=True).stdout.strip()
        url = subprocess.run(["git", "-C", directory, "config", "--get", "remote.origin.url"],
                             capture_output=True, text=True).stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None
    repository = repository_name(url) or os.path.basename(top)
    sub = os.path.relpath(os.path.realpath(directory), os.path.realpath(top))
    # a checkout with changes of its own (a local build) is not that commit's source: it says so
    changed = subprocess.run(["git", "-C", directory, "status", "--porcelain", "--untracked-files=no"],
                             capture_output=True, text=True).stdout.strip()
    return f"{repository}@{commit}" + ("+dirty" if changed else "") + ("" if sub == "." else "/" + sub)


def license_files(directory):
    names = sorted(f for f in os.listdir(directory)
                   if LICENSE_FILE.match(f) and os.path.isfile(os.path.join(directory, f)))
    files = []
    for name in names:
        p = os.path.join(directory, name)
        if os.path.getsize(p) > TOO_LARGE:
            raise SystemExit(f"licenses: {p} is over {TOO_LARGE} bytes")
        with open(p, encoding="utf-8", errors="replace") as f:
            files.append((name, f.read().replace("\r\n", "\n")))
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--out", required=True)
    parser.add_argument("--aar")
    parser.add_argument("binaries", nargs="+")
    args = parser.parse_args()

    cache = go("env", "GOMODCACHE").strip()
    go_version = None
    seen = {}
    for binary in args.binaries:
        version, modules = built_modules(binary)
        if go_version not in (None, version):
            raise SystemExit(f"licenses: {binary} was built with {version}, another with {go_version}")
        go_version = version
        for path, mod_version, replacement, replacement_version in modules:
            if path in OWN:
                continue
            seen[(path, mod_version, replacement, replacement_version)] = None

    texts, index = [], {}

    def text_id(text):
        key = hashlib.sha256(text.encode()).hexdigest()
        if key not in index:
            index[key] = len(texts)
            texts.append(text)
        return index[key]

    failures = []
    out = []

    def add(path, version, directory, source=None):
        files = license_files(directory)
        licenses = []
        for name, text in files:
            if NOTICE_FILE.match(name):
                continue
            found = classify(text)
            if not found:
                failures.append(f"{path}@{version}: {name} is no license this script knows ({directory})")
            for spdx in found:
                if spdx.startswith("!"):
                    failures.append(f"{path}@{version}: {name} is {spdx[1:]}, to be looked at before it is linked in")
                elif spdx not in licenses:
                    licenses.append(spdx)
        if not licenses and not any(f"{path}@{version}:" in f for f in failures):
            failures.append(f"{path}@{version}: no license file in {directory}")
        entry = {"path": path, "version": version}
        if source:
            entry["source"] = source
        entry["licenses"] = licenses
        entry["files"] = [{"name": name, "text": text_id(text)} for name, text in files]
        out.append(entry)

    goroot = go("env", "GOROOT").strip()
    toolchain = go("env", "GOVERSION").strip()
    if toolchain != go_version:
        raise SystemExit(f"licenses: the binaries are of {go_version}, the go on PATH is {toolchain}: "
                         "GOTOOLCHAIN as the build had it")
    add("go", go_version, goroot)

    for path, version, replacement, replacement_version in sorted(seen):
        if replacement is None:
            add(path, version, module_dir(cache, path, version))
        elif replacement_version in ("", "(devel)"):
            if not os.path.isdir(replacement):
                failures.append(f"{path}: the local replace {replacement} is not here")
                continue
            add(path, version, replacement, git_source(replacement))
        else:
            # a module replaced by another: its license is the replacement's
            add(path, version, module_dir(cache, replacement, replacement_version),
                f"{replacement}@{replacement_version}")

    if failures:
        print("licenses:\n  " + "\n  ".join(failures), file=sys.stderr)
        raise SystemExit(1)

    data = {"v": 1, "go": go_version, "modules": out, "texts": texts}
    encoded = json.dumps(data, ensure_ascii=False, indent=None, separators=(",", ":")).encode()
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "wb") as f:
        f.write(encoded)
    counts = {}
    for m in out:
        for spdx in m["licenses"]:
            counts[spdx] = counts.get(spdx, 0) + 1
    print(f">> licenses: {len(out)} modules, {len(texts)} texts, {len(encoded)} bytes: "
          + ", ".join(f"{k} {v}" for k, v in sorted(counts.items(), key=lambda kv: -kv[1])))

    if args.aar:
        with zipfile.ZipFile(args.aar) as z:
            if ASSET in z.namelist():
                raise SystemExit(f"licenses: {args.aar} already holds {ASSET}")
        with zipfile.ZipFile(args.aar, "a", compression=zipfile.ZIP_DEFLATED) as z:
            z.writestr(ASSET, encoded)
        print(f">> licenses: {ASSET} in {args.aar}")


if __name__ == "__main__":
    main()
