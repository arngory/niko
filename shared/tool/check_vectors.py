#!/usr/bin/env python3
"""Every vector drawable's path data, read the way Android reads it.

Android takes the numbers after a path command as floats, one after another. SVG's shorter forms it does not know:
an arc's two flags with nothing after them ("a2 2 0 0022 17") are one number, 22, to it. Such a drawable builds, passes
lint, and fails when it is inflated: the screen that shows it does not open. This reads each android:pathData so and
fails on a command with the wrong count of numbers or an arc flag that is not 0 or 1.

  check_vectors.py DIR...    the res directories to look through (drawable*/ under each)
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
ARGUMENTS = {"m": 2, "l": 2, "h": 1, "v": 1, "c": 6, "s": 4, "q": 4, "t": 2, "a": 7, "z": 0}
COMMAND = re.compile(r"([MmLlHhVvCcSsQqTtAaZz])([^MmLlHhVvCcSsQqTtAaZz]*)")
# what Float.parseFloat takes of the forms a path can hold
FLOAT = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")


def numbers_of(rest):
    """The text after a command cut into numbers as android.util.PathParser.extract cuts it: at a space or a comma,
    before a minus that does not follow an exponent's e, before a second dot. Nothing else ends a number: a plus
    does not ("1+2" is one piece, which is no number), nor does a dot after an exponent when the number had none
    before it ("1e2.5")."""
    pieces, current, dot, previous = [], "", False, ""
    for c in rest:
        if c in " ,\t\r\n":
            if current:
                pieces.append(current)
            current, dot = "", False
        elif c == "-" and current and previous not in "eE":
            pieces.append(current)
            current, dot = c, False
        elif c == "." and dot:
            pieces.append(current)
            current, dot = c, True
        else:
            dot = dot or c == "."
            current += c
        previous = c
    if current:
        pieces.append(current)
    return pieces


def faults(data):
    if not data.strip() or data.strip()[0] not in "Mm":
        return ["does not start with a move"]
    found = []
    for command, rest in COMMAND.findall(data):
        numbers = numbers_of(rest)
        bad = [n for n in numbers if not FLOAT.fullmatch(n)]
        if bad:
            found.append(f"{command}: not a number to Android: {bad[0][:40]!r} in {rest.strip()[:60]!r}")
            continue
        want = ARGUMENTS[command.lower()]
        if (want == 0 and numbers) or (want and (not numbers or len(numbers) % want)):
            found.append(f"{command} takes {want} numbers a time, has {len(numbers)}: {rest.strip()[:60]!r}")
        elif command.lower() == "a":
            flags = [n for k, n in enumerate(numbers) if k % 7 in (3, 4)]
            if any(flag not in ("0", "1") for flag in flags):
                found.append(f"{command}: an arc flag that is not 0 or 1: {rest.strip()[:60]!r}")
    return found


def main():
    bad = count = 0
    for res in sys.argv[1:]:
        for path in sorted(glob.glob(os.path.join(res, "drawable*", "*.xml"))):
            try:
                root = ET.parse(path).getroot()
            except ET.ParseError as error:
                sys.exit(f"{path}: {error}")
            for element in root.iter():
                data = element.get(ANDROID + "pathData")
                if data is None or data.startswith("@"):
                    continue
                count += 1
                for fault in faults(data):
                    bad += 1
                    print(f"{path}: {fault}")
    print(f"{count} paths read as Android reads them, {bad} it cannot")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
