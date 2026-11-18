#!/usr/bin/env python3
"""Checks a release's UPDATE.json before release.yml signs it into SHA256SUMS.

UPDATE.json (raycontrol docs/spec/client-min-v1.md §6) says whether the update is mandatory and the lowest version
the release still supports, with a message by language. The owner writes it as .github/release-notes/<tag>.update.json;
the app reads it after the signature of SHA256SUMS holds (io.niko.utils.UpdateNotice).

    update-json.py <file> <tag>

Exit 0: the file holds. Otherwise the reason on stderr (as a GitHub error line) and exit 1.
"""
import json
import re
import sys

MAX_BYTES = 16 * 1024
MAX_MESSAGE = 1000
SEMVER = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$")
LANGUAGE = re.compile(r"^[a-z]{2}$")
KEYS = {"v", "mandatory", "min_supported", "min_android", "message"}


def semver_key(version):
    """A sort key by semver 2.0: a pre-release below its release, numeric identifiers by number, below the others."""
    core, _, pre = version.partition("-")
    numbers = tuple(int(n) for n in core.split("."))
    if not pre:
        return numbers, (1,)
    ids = tuple((0, int(p), "") if p.isdigit() else (1, 0, p) for p in pre.split("."))
    return numbers, (0, ids)


def check(raw, tag):
    if len(raw) > MAX_BYTES:
        return f"{len(raw)} bytes, {MAX_BYTES} at most"
    try:
        notice = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, ValueError) as e:
        return f"not JSON in UTF-8: {e}"
    if not isinstance(notice, dict):
        return "not a JSON object"
    unknown = sorted(set(notice) - KEYS)
    if unknown:
        return f"unknown keys {unknown}: the app reads only {sorted(KEYS)}"
    v = notice.get("v")
    if type(v) is not int or v != 1:
        return f"v is {v!r}, want 1"
    mandatory = notice.get("mandatory")
    if type(mandatory) is not bool:
        return f"mandatory is {mandatory!r}, want true or false"
    version = tag.removeprefix("v")
    if not SEMVER.match(version):
        return f"the tag {tag} is not a version"
    min_supported = notice.get("min_supported")
    if min_supported is not None:
        if not isinstance(min_supported, str) or not SEMVER.match(min_supported):
            return f"min_supported is {min_supported!r}, want a version such as 1.59.0"
        if semver_key(min_supported) > semver_key(version):
            return f"min_supported {min_supported} is above the release {version}: the release itself would be unsupported"
    min_android = notice.get("min_android")
    if min_android is not None and (type(min_android) is not int or not 21 <= min_android <= 1000):
        return f"min_android is {min_android!r}, want an API level such as 29"
    message = notice.get("message", {})
    if not isinstance(message, dict):
        return "message is not an object of languages"
    for language, text in message.items():
        if not LANGUAGE.match(language):
            return f"message: {language!r} is not a two-letter language"
        if not isinstance(text, str) or not text.strip():
            return f"message.{language} is empty"
        if len(text) > MAX_MESSAGE:
            return f"message.{language}: {len(text)} characters, {MAX_MESSAGE} at most"
    # an update without "later" says why, in the app's two languages
    if (mandatory or min_supported is not None) and not {"en", "ru"} <= set(message):
        return "a mandatory update or a min_supported needs message.en and message.ru: the user is told why"
    return None


def main():
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    path, tag = sys.argv[1], sys.argv[2]
    with open(path, "rb") as f:
        raw = f.read()
    reason = check(raw, tag)
    if reason:
        print(f"::error file={path}::UPDATE.json for {tag}: {reason}", file=sys.stderr)
        return 1
    print(f"{path}: UPDATE.json for {tag} holds")
    return 0


if __name__ == "__main__":
    sys.exit(main())
