#!/usr/bin/env python3
"""The interface's icons, for Android and for the window, from the design system's set.

  gen_icons.py [--desktop DIR]          write app/src/main/res/drawable/ic_l_<name>.xml for every
                                        shared/icons/lucide/<name>.svg (but the window's own, WINDOW_ONLY), and with
                                        --desktop DIR/lib/ui/icons.g.dart with the window's
  gen_icons.py --check [--desktop DIR]  exit 1 if a file is not what its SVGs give, is missing, or a drawable has no
                                        SVG (CI)

DIR is a checkout of the window, arngory/niko-desktop; without --desktop its file is left alone (its CI checks it
against the Niko it pins).

The set is Lucide (shared/icons/LICENSE), the one the canvas and the window for the computer draw: outlines on a 24
grid, stroked with the icon stroke of shared/tokens.json. An icon is added by putting its SVG, unchanged, into
shared/icons/lucide and running this; a drawable is never edited by hand.
"""
import argparse
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SOURCE = os.path.join(ROOT, "shared", "icons", "lucide")
TARGET = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
SVG = "{http://www.w3.org/2000/svg}"
# drawn towards the reading direction: mirrored where the text runs right to left
MIRRORED = {"arrow-left", "arrow-right-to-line", "send", "undo-2", "redo-2", "log-in", "log-out", "align-left"}
# the icons the window draws: its name for each → the SVG's (lib/ui/icons.g.dart of the window's checkout)
WINDOW = {
    "power": "power", "alert": "circle-alert", "close": "x", "search": "search", "trash": "trash",
    "menu": "menu", "copy": "copy", "export": "share", "plus": "plus", "rotateCw": "rotate-cw",
    "chevronRight": "chevron-right",
}
# drawn by the window alone: no drawable for them (lint fails the APK on a resource nothing uses)
WINDOW_ONLY = {"circle-alert", "trash", "copy", "share"}
# in the window's checkout (--desktop)
DART = os.path.join("lib", "ui", "icons.g.dart")
# icons outside a themed view (a launcher shortcut): a colour of their own in place of the theme's tint
FIXED = {"ic_qu_camera_foreground": ("camera", "#FF1C1C1C")}
# lint's VectorPath
LONGEST_PATH = 800


def number(value):
    value = float(value)
    return str(int(value)) if value == int(value) else ("%.4f" % value).rstrip("0").rstrip(".")


# how many numbers each path command takes
ARGUMENTS = {"m": 2, "l": 2, "h": 1, "v": 1, "c": 6, "s": 4, "q": 4, "t": 2, "a": 7, "z": 0}
NUMBER = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")


def spaced(data):
    """Path data with every number apart. SVG lets numbers run together ("1.5.5", "2-3"), and an arc's two flags
    stand with no space before what follows ("a2 2 0 0022 17" is flags 0 and 0, then x 22). Android reads a path's
    numbers as floats one after another: "0022" is 22 to it, the arc is short of numbers, and the drawable does not
    load (Resources.NotFoundException when it is inflated, nothing at build time or in lint)."""
    out, i, command, taken = [], 0, None, 0
    while i < len(data):
        c = data[i]
        if c in " \t\r\n,":
            i += 1
        elif c.lower() in ARGUMENTS:
            if command and ARGUMENTS[command.lower()] and taken % ARGUMENTS[command.lower()]:
                raise ValueError(f"path data: {command} is short of numbers in {data!r}")
            command, taken = c, 0
            out.append(c)
            i += 1
        elif command is None or not ARGUMENTS[command.lower()]:
            raise ValueError(f"path data: a number with no command at {i} in {data!r}")
        elif command.lower() == "a" and taken % 7 in (3, 4):
            if c not in "01":
                raise ValueError(f"path data: an arc flag is {c!r} at {i} in {data!r}")
            out.append(c)
            taken += 1
            i += 1
        else:
            match = NUMBER.match(data, i)
            if not match:
                raise ValueError(f"path data: not a number at {i} in {data!r}")
            out.append(match.group())
            taken += 1
            i = match.end()
    if command and ARGUMENTS[command.lower()] and taken % ARGUMENTS[command.lower()]:
        raise ValueError(f"path data: {command} is short of numbers in {data!r}")
    text = ""
    for token in out:
        text += token if token.lower() in ARGUMENTS or text[-1:].lower() in ARGUMENTS else " " + token
    return text


def shape_path(element):
    """A basic shape as path data (SVG 2, 10: equivalent paths)."""
    tag = element.tag.replace(SVG, "")
    get = lambda name, default="0": float(element.get(name, default))
    if tag == "path":
        return spaced(element.get("d"))
    if tag == "line":
        return f"M{number(get('x1'))} {number(get('y1'))}L{number(get('x2'))} {number(get('y2'))}"
    if tag in ("circle", "ellipse"):
        rx = get("r") if tag == "circle" else get("rx")
        ry = get("r") if tag == "circle" else get("ry")
        cx, cy = get("cx"), get("cy")
        return (f"M{number(cx - rx)} {number(cy)}a{number(rx)} {number(ry)} 0 1 0 {number(2 * rx)} 0"
                f"a{number(rx)} {number(ry)} 0 1 0 {number(-2 * rx)} 0Z")
    if tag == "rect":
        x, y, w, h = get("x"), get("y"), get("width"), get("height")
        rx = element.get("rx", element.get("ry", "0"))
        ry = element.get("ry", rx)
        rx, ry = min(float(rx), w / 2), min(float(ry), h / 2)
        if rx == 0 or ry == 0:
            return f"M{number(x)} {number(y)}h{number(w)}v{number(h)}h{number(-w)}Z"
        arc = lambda dx, dy: f"a{number(rx)} {number(ry)} 0 0 1 {number(dx)} {number(dy)}"
        return (f"M{number(x + rx)} {number(y)}h{number(w - 2 * rx)}{arc(rx, ry)}v{number(h - 2 * ry)}{arc(-rx, ry)}"
                f"h{number(-(w - 2 * rx))}{arc(-rx, -ry)}v{number(-(h - 2 * ry))}{arc(rx, -ry)}Z")
    if tag in ("polyline", "polygon"):
        points = [number(value) for value in re.split(r"[\s,]+", element.get("points").strip())]
        pairs = [f"{points[i]} {points[i + 1]}" for i in range(0, len(points) - 1, 2)]
        return "M" + "L".join(pairs) + ("Z" if tag == "polygon" else "")
    raise ValueError(f"an SVG element this does not draw: <{tag}>")


def drawable(name, stroke_width, colour=None):
    root = ET.parse(os.path.join(SOURCE, name + ".svg")).getroot()
    if root.get("viewBox") != "0 0 24 24":
        raise ValueError(f"{name}: not on the 24 grid")
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           f"<!-- GENERATED by shared/tool/gen_icons.py from shared/icons/lucide/{name}.svg (Lucide). Do not edit. -->",
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="24dp"', '    android:height="24dp"',
           '    android:viewportWidth="24"', '    android:viewportHeight="24"']
    if name in MIRRORED and colour is None:
        out.append('    android:autoMirrored="true"')
    if colour is None:
        out.append('    android:tint="?attr/colorControlNormal"')
    out[-1] += ">"
    ink = colour or "#FF000000"
    for element in root:
        data = shape_path(element)
        if len(data) > LONGEST_PATH:
            raise ValueError(f"{name}: a path of {len(data)} characters, lint takes {LONGEST_PATH}")
        fill = element.get("fill", "none")
        out += ["    <path", f'        android:pathData="{data}"',
                f'        android:fillColor="{ink if fill != "none" else "#00000000"}"',
                f'        android:strokeColor="{ink}"', f'        android:strokeWidth="{stroke_width}"',
                '        android:strokeLineCap="round"', '        android:strokeLineJoin="round" />']
    out += ["</vector>", ""]
    return "\n".join(out)


def absolute_start(data):
    """An element's path data that stays itself after another's in one string. A path's first moveto is absolute
    even when written "m" (SVG 2, 9.3.3), and the pairs after it are relative linetos: joined after another path, an
    "m" would be taken from that path's end. So the first pair is written "M" and the rest of it "l"."""
    match = re.match(r"m\s*(%s)\s+(%s)(.*)$" % (NUMBER.pattern, NUMBER.pattern), data, re.S)
    if not match:
        return data
    x, y, rest = match.group(1), match.group(2), match.group(3)
    rest = rest.strip()
    if rest and rest[0] not in ARGUMENTS and rest[0].lower() not in ARGUMENTS:
        rest = "l" + rest
    return f"M{x} {y}" + (rest if not rest or rest[0].isalpha() else " " + rest)


def dart():
    """The window's icons as SVG path data, every shape of an icon in one string, as its parser takes them."""
    out = ["// GENERATED by shared/tool/gen_icons.py from shared/icons/lucide (Lucide, ISC: shared/icons/LICENSE).",
           "// Do not edit: put the SVG there, name it in WINDOW and run the tool.", "",
           "/// The icons of the design system the window draws: Lucide's outlines on a 24 grid, as SVG path data.",
           "abstract final class NikoIcons {"]
    for name, svg in sorted(WINDOW.items()):
        root = ET.parse(os.path.join(SOURCE, svg + ".svg")).getroot()
        if root.get("viewBox") != "0 0 24 24":
            raise ValueError(f"{svg}: not on the 24 grid")
        data = " ".join(absolute_start(shape_path(element)) for element in root)
        out += [f"  /// {svg}.svg", f"  static const {name} = '{data}';", ""]
    out += [f"  static const all = [{', '.join(sorted(WINDOW))}];", "}", ""]
    return "\n".join(out)


def main():
    parser = argparse.ArgumentParser(description="The interface's icons, for Android and for the window.")
    parser.add_argument("--check", action="store_true", help="exit 1 if a generated file is not what its SVGs give")
    parser.add_argument("--desktop", metavar="DIR", help="a checkout of the window, arngory/niko-desktop")
    args = parser.parse_args()
    if args.desktop and not os.path.isfile(os.path.join(args.desktop, "pubspec.yaml")):
        print(f"{args.desktop} is not a checkout of the window (no pubspec.yaml)")
        return 2
    tokens = json.load(open(os.path.join(ROOT, "shared", "tokens.json"), encoding="utf-8"))
    stroke = next(token["value"] for token in tokens["stroke"]["tokens"] if token["name"] == "stroke-icon")
    stroke = number(stroke.replace("px", ""))
    names = sorted(name[:-4] for name in os.listdir(SOURCE) if name.endswith(".svg") and name[:-4] not in WINDOW_ONLY)
    files = {f"ic_l_{name.replace('-', '_')}.xml": drawable(name, stroke) for name in names}
    for resource, (name, colour) in FIXED.items():
        files[resource + ".xml"] = drawable(name, stroke, colour)
    check = args.check
    stale = 0
    for file, text in files.items():
        path = os.path.join(TARGET, file)
        if check:
            if not os.path.exists(path) or open(path, encoding="utf-8").read() != text:
                print(f"res/drawable/{file} is not what its SVG gives: run shared/tool/gen_icons.py")
                stale = 1
        else:
            open(path, "w", encoding="utf-8").write(text)
    if args.desktop:
        window, path = dart(), os.path.join(args.desktop, DART)
        if check:
            if not os.path.exists(path) or open(path, encoding="utf-8").read() != window:
                print(f"{path} is not what the SVGs give: run shared/tool/gen_icons.py --desktop")
                stale = 1
        else:
            open(path, "w", encoding="utf-8").write(window)
    for file in sorted(os.listdir(TARGET)):
        if file.startswith("ic_l_") and file not in files:
            if check:
                print(f"res/drawable/{file} has no SVG in shared/icons/lucide")
                stale = 1
            else:
                os.remove(os.path.join(TARGET, file))
    return stale


if __name__ == "__main__":
    sys.exit(main())
