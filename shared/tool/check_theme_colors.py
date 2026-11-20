#!/usr/bin/env python3
"""The Niko theme's styles and the app's layouts take their colours from the design system only.

    check_theme_colors.py [res]

Allowed: a colour of the tokens (@color/niko_<token>, generated into niko_tokens.xml by gen_tokens.py), an attribute
of the theme (?attr/…, ?android:attr/…), @android:color/transparent and @null, and a colour state list in res/color
whose items are allowed in turn. Not allowed: a colour of the app's own (@color/material_red_500, niko_bar), a
platform colour, a literal #rrggbb. Where: every style whose name has "Niko" in values*/ (themes and styles), with
what it takes from the app's own styles above it and does not set itself (its parent, or the style its name's dots
make it a child of: Theme.SagerNet.Niko of Theme.SagerNet), and every layout. NekoBox's 21 colour themes and Black
are gone since 1.62.0 (reskin step 3).

EXCEPTIONS counts what is there today and goes only down: a new colour anywhere fails, and so does an exception that
is no longer there (take it out of the list).
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
COLOUR = re.compile(r"^(@color/[A-Za-z0-9_]+|@android:color/[A-Za-z0-9_]+|#[0-9a-fA-F]{3,8})$")

# (file under res, colour): how many there are today. Step 1 of the reskin takes them away
EXCEPTIONS = {
}


def token_colours(res):
    tree = ET.parse(os.path.join(res, "values", "niko_tokens.xml"))
    return {"@color/" + c.get("name") for c in tree.getroot().findall("color")}


def state_list_problems(res, name, allowed, seen):
    """The colours a state list in res/color holds that are not allowed; [] for none, None for no such list."""
    path = os.path.join(res, "color", name + ".xml")
    if not os.path.exists(path):
        return None
    if name in seen:
        return []
    seen.add(name)
    problems = []
    for item in ET.parse(path).getroot().iter("item"):
        for key, value in item.attrib.items():
            if key.endswith("}color") or key == "color":
                problems += value_problems(res, value, allowed, seen)
    return problems


def value_problems(res, value, allowed, seen):
    value = value.strip()
    if not COLOUR.match(value) or value in allowed or value in ("@android:color/transparent",):
        return []
    if value.startswith("@color/"):
        inner = state_list_problems(res, value[len("@color/"):], allowed, seen)
        if inner is not None:
            return inner
    return [value]


def styles(res):
    """Every style of the app: name -> (file under res, {item: value}, parent name or None)."""
    out = {}
    for folder in sorted(os.listdir(res)):
        if not (folder == "values" or folder.startswith("values-")):
            continue
        for name in sorted(os.listdir(os.path.join(res, folder))):
            if not name.endswith(".xml"):
                continue
            for style in ET.parse(os.path.join(res, folder, name)).getroot().findall("style"):
                parent = style.get("parent")
                if parent is not None:
                    parent = parent.split("/", 1)[-1]
                elif "." in style.get("name"):
                    parent = style.get("name").rsplit(".", 1)[0]
                items = {item.get("name"): item.text or "" for item in style.findall("item")}
                # the default configuration's wins: an inheritance read for values/
                out.setdefault(style.get("name"), (f"{folder}/{name}", items, parent))
    return out


def inherited(all_styles, name):
    """(file under res, value) of what the style takes from the app's own styles above it and does not set itself."""
    _, items, parent = all_styles[name]
    seen = set(items)
    out = []
    while parent in all_styles:
        where, above, parent = all_styles[parent]
        for item, value in above.items():
            if item not in seen:
                seen.add(item)
                out.append((where, value))
    return out


def found(res):
    """(file under res, colour) for every colour not allowed."""
    allowed = token_colours(res)
    all_styles = styles(res)
    out = []
    for folder in sorted(os.listdir(res)):
        path = os.path.join(res, folder)
        if folder == "layout" or folder.startswith("layout-"):
            for name in sorted(os.listdir(path)):
                if not name.endswith(".xml"):
                    continue
                for element in ET.parse(os.path.join(path, name)).getroot().iter():
                    for value in element.attrib.values():
                        out += [(f"{folder}/{name}", v) for v in value_problems(res, value, allowed, set())]
        elif folder == "values" or folder.startswith("values-"):
            for name in sorted(os.listdir(path)):
                if not name.endswith(".xml"):
                    continue
                root = ET.parse(os.path.join(path, name)).getroot()
                for style in root.findall("style"):
                    if "Niko" not in style.get("name", ""):
                        continue
                    for item in style.findall("item"):
                        out += [(f"{folder}/{name}", v)
                                for v in value_problems(res, item.text or "", allowed, set())]
                    if folder == "values":
                        for where, value in inherited(all_styles, style.get("name")):
                            out += [(where, v) for v in value_problems(res, value, allowed, set())]
    return out


def main():
    res = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "app", "src", "main", "res")
    counts = {}
    for key in found(res):
        counts[key] = counts.get(key, 0) + 1
    problems = []
    for (where, value), n in sorted(counts.items()):
        allowed = EXCEPTIONS.get((where, value), 0)
        if n > allowed:
            problems.append(f"res/{where}: {value} ({n}), not a colour of the design system: a token or an "
                            "attribute of the theme")
    for (where, value), n in sorted(EXCEPTIONS.items()):
        if counts.get((where, value), 0) < n:
            problems.append(f"res/{where}: {value} is down to {counts.get((where, value), 0)} from {n}: lower "
                            "EXCEPTIONS in shared/tool/check_theme_colors.py")
    if problems:
        print("\n".join(problems))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
