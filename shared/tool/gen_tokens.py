#!/usr/bin/env python3
"""The design system's tokens (shared/tokens.json) as code for each platform.

  gen_tokens.py [--desktop DIR]            write the generated files
  gen_tokens.py --check [--desktop DIR]    exit 1 if one of them is not what the tokens give (CI)

DIR is a checkout of the window, arngory/niko-desktop: its files (below) are written or checked there, and left alone
without --desktop (its CI checks them against the Niko it pins).

One source for the phone and the computer (docs/ui/platforms.md, variant В): a colour, a size or a text style is
changed in tokens.json and regenerated, never edited in a generated file.

  DIR/lib/theme/tokens.g.dart                      the window, Flutter: colours of both themes, text styles, sizes
  DIR/fonts/*.ttf                                  the window: the fonts, copies of the app's (FONT_COPIES)
  app/src/main/res/values/niko_tokens.xml          Android: the colours, light (niko_<token>)
  app/src/main/res/values-night/niko_tokens.xml    Android: the colours, dark
  app/src/main/res/values/niko_type.xml            Android: the text styles, each by name (TextAppearance.Niko.Body)
                                                   and on Material's slots (TextAppearance.Niko.Slot.*)
  app/src/main/res/values/niko_dimens.xml          Android: spacing, radii, sizes and strokes (niko_space_4)
  app/src/main/res/font/*.ttf                      Android: the fonts, the source of the window's
  app/src/main/java/io/niko/ui/compose/NikoTokens.kt
                                                   Android, Compose: colours of both themes, text styles, sizes

Both modes also hold the colours to the contrast their usage promises (CONTRAST): a token changed so that ink-2 no
longer reads on surface-3 fails here, for both platforms at once.
"""
import argparse
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
# in the window's checkout (--desktop)
DART = "lib/theme/tokens.g.dart"
ANDROID = {"light": "app/src/main/res/values/niko_tokens.xml", "dark": "app/src/main/res/values-night/niko_tokens.xml"}
ANDROID_TYPE = "app/src/main/res/values/niko_type.xml"
ANDROID_DIMENS = "app/src/main/res/values/niko_dimens.xml"
COMPOSE = "app/src/main/java/io/niko/ui/compose/NikoTokens.kt"

# The window's fonts (in its checkout) are byte copies of the app's, which are here under the names Android's
# resources allow. Until the window had a repository of its own the window's were the source; the bytes are the same
FONT_COPIES = {
    "fonts/Geist-Regular.ttf": "app/src/main/res/font/geist_regular.ttf",
    "fonts/Geist-Medium.ttf": "app/src/main/res/font/geist_medium.ttf",
    "fonts/Geist-SemiBold.ttf": "app/src/main/res/font/geist_semibold.ttf",
    "fonts/GeistMono-Regular.ttf": "app/src/main/res/font/geist_mono.ttf",
    "fonts/GeistMono-Medium.ttf": "app/src/main/res/font/geist_mono_medium.ttf",
}

# The contrast the colours' usage in tokens.json promises, in both themes: (text or line, on which, at least), WCAG
# 2's ratio. danger is promised on surface-0…2 and danger-soft, not on surface-3 (the snackbar's): 4.43:1 there, light
CONTRAST = (
    [(fg, bg, 4.5) for fg in ("ink", "ink-2") for bg in ("surface-0", "surface-1", "surface-2", "surface-3")]
    + [("danger", bg, 4.5) for bg in ("surface-0", "surface-1", "surface-2", "danger-soft")]
    + [("line-strong", bg, 3.0) for bg in ("surface-0", "surface-1", "surface-2")]
    + [("on-ink", bg, 10.0) for bg in ("ink", "ink-pressed")]
)

# Material's text slots the app's views and AppCompat's read, each as one style of the design system: (slot, style,
# weight if not the style's own). The button is the canvas's m-btn (600 16/24), not in capitals.
TYPE_SLOTS = [
    ("Headline4", "display", None),
    ("Headline5", "title", None),
    ("Headline6", "headline", None),
    ("Subtitle1", "body", None),
    ("Subtitle2", "body-sm", 600),
    ("Body1", "body", None),
    ("Body2", "body-sm", None),
    ("Button", "body", 600),
    ("Caption", "caption", None),
    ("Overline", "label", None),
]

# The fonts in res/font for a family and a weight: Geist's family file picks 400, 500 or 600 itself (bold asks for 700
# and gets 600); Geist Mono is there in the one weight a slot takes.
ANDROID_FONTS = {("sans", 400): "@font/geist", ("sans", 500): "@font/geist_medium", ("sans", 600): "@font/geist",
                 ("mono", 400): "@font/geist_mono", ("mono", 500): "@font/geist_mono_medium"}


def camel(name):
    head, *rest = re.split(r"[-_ ]+", name)
    return head + "".join(part.capitalize() for part in rest)


def colour(value, themes):
    """#rrggbb or #rrggbbaa as Dart's 0xAARRGGBB; {name} is another token of the same theme."""
    match = re.fullmatch(r"\{([a-z0-9-]+)\}", value)
    if match:
        return colour(themes[match.group(1)], themes)
    match = re.fullmatch(r"#([0-9a-fA-F]{6})([0-9a-fA-F]{2})?", value)
    if not match:
        raise ValueError(f"not a colour: {value}")
    return "0x" + (match.group(2) or "ff").upper() + match.group(1).upper()


def android_colour(value, themes):
    """#rrggbb or #rrggbbaa as Android's #RRGGBB or #AARRGGBB."""
    argb = colour(value, themes)[2:]
    return "#" + (argb[2:] if argb.startswith("FF") else argb)


def android(tokens, theme):
    values = {token["name"]: token["value"][theme] for token in tokens["color"]["tokens"]}
    out = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- GENERATED by shared/tool/gen_tokens.py from shared/tokens.json. Do not edit: change the tokens and run it.",
        f"     The design system's colours, {theme}. -->",
        "<resources>",
    ]
    for token in tokens["color"]["tokens"]:
        name = "niko_" + token["name"].replace("-", "_")
        out.append(f'    <color name="{name}">{android_colour(values[token["name"]], values)}</color>')
    out += ["</resources>", ""]
    return "\n".join(out)


def em(value):
    match = re.fullmatch(r"(-?[0-9.]+)em", value)
    if not match:
        raise ValueError(f"not a length in em: {value}")
    return match.group(1)


def text_style(name, parent, family, style, weight):
    font = ANDROID_FONTS[(family, weight)]
    return [
        f'    <style name="{name}" parent="{parent}">',
        f'        <item name="fontFamily">{font}</item>',
        f'        <item name="android:fontFamily">{font}</item>',
        f'        <item name="android:textStyle">{"bold" if weight >= 600 else "normal"}</item>',
        f'        <item name="android:textSize">{px(style["fontSize"]).removesuffix(".0")}sp</item>',
        f'        <item name="lineHeight">{px(style["lineHeight"]).removesuffix(".0")}sp</item>',
        f'        <item name="android:letterSpacing">{em(style.get("letterSpacing", "0em"))}</item>',
        # label is always in capitals, as the Dart side has it (its usage says so)
        f'        <item name="android:textAllCaps">{"true" if style["name"] == "label" else "false"}</item>',
        "    </style>",
    ]


def android_type(tokens):
    styles = {}
    for group in tokens["type"]["groups"]:
        for style in group["styles"]:
            styles[style["name"]] = (group["family"], style)
    out = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- GENERATED by shared/tool/gen_tokens.py from shared/tokens.json. Do not edit: change the tokens and run it.",
        "     The design system's text styles: each by its name (TextAppearance.Niko.BodySm, .Code), then on Material's",
        "     slots (TextAppearance.Niko.Slot.Body1; TYPE_SLOTS there), which the theme sets. The size, the line (MaterialTextView takes it from the appearance), the tracking",
        "     and the weight; the colour is the view's. -->",
        "<resources>",
    ]
    for name, (family, style) in styles.items():
        out.append(f'    <!-- {name}: {style["usage"]} -->')
        out += text_style("TextAppearance.Niko." + camel(name)[0].upper() + camel(name)[1:],
                          "TextAppearance.MaterialComponents.Body1", family, style, style["fontWeight"])
    for slot, name, weight in TYPE_SLOTS:
        family, style = styles[name]
        out.append(f'    <!-- {slot}: {name} -->')
        out += text_style(f"TextAppearance.Niko.Slot.{slot}", f"TextAppearance.MaterialComponents.{slot}", family, style,
                          weight or style["fontWeight"])
    out += ["</resources>", ""]
    return "\n".join(out)


def android_dimens(tokens):
    out = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- GENERATED by shared/tool/gen_tokens.py from shared/tokens.json. Do not edit: change the tokens and run it.",
        "     The design system's spacing, radii, sizes and strokes in dp (a px of the tokens is a dp). radius-full is a",
        "     capsule: a shape takes it as 50% (cornerSize), this dimension only where a length is needed. -->",
        "<resources>",
    ]
    for section in ("spacing", "radius", "size", "stroke"):
        for token in tokens[section]["tokens"]:
            name = "niko_" + token["name"].replace("-", "_")
            out.append(f'    <!-- {token["usage"]} -->')
            out.append(f'    <dimen name="{name}">{px(token["value"]).removesuffix(".0")}dp</dimen>')
    out += ["</resources>", ""]
    return "\n".join(out)


def luminance(argb):
    def channel(c):
        c /= 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (int(argb[i:i + 2], 16) for i in (4, 6, 8))
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)


def contrast_problems(tokens):
    problems = []
    for theme in tokens["color"]["themes"]:
        values = {token["name"]: token["value"][theme["id"]] for token in tokens["color"]["tokens"]}
        for fg, bg, least in CONTRAST:
            a, b = (luminance(colour(values[n], values)) for n in (fg, bg))
            ratio = (max(a, b) + 0.05) / (min(a, b) + 0.05)
            if ratio < least:
                problems.append(f"{theme['id']}: {fg} on {bg} is {ratio:.2f}:1, its usage promises {least}:1")
    return problems


def px(value):
    match = re.fullmatch(r"(-?[0-9.]+)px", value)
    if not match:
        raise ValueError(f"not a length in px: {value}")
    number = float(match.group(1))
    return repr(number) if number != int(number) else f"{int(number)}.0"


def dart(tokens):
    out = [
        "// GENERATED by shared/tool/gen_tokens.py from shared/tokens.json. Do not edit: change the tokens and run it.",
        "// ignore_for_file: constant_identifier_names",
        "",
        "import 'dart:ui' show Color, FontWeight;",
        "",
        "/// The colours of one theme of the design system.",
        "class NikoColors {",
    ]
    names = [token["name"] for token in tokens["color"]["tokens"]]
    out.append("  const NikoColors({")
    out += [f"    required this.{camel(name)}," for name in names]
    out.append("  });")
    out.append("")
    for token in tokens["color"]["tokens"]:
        out.append(f"  /// {token['usage']}")
        out.append(f"  final Color {camel(token['name'])};")
    for theme in tokens["color"]["themes"]:
        values = {token["name"]: token["value"][theme["id"]] for token in tokens["color"]["tokens"]}
        out.append("")
        out.append(f"  static const {theme['id']} = NikoColors(")
        out += [f"    {camel(name)}: Color({colour(values[name], values)})," for name in names]
        out.append("  );")
    out += ["}", ""]

    out += ["/// A text style of the design system: sizes in logical pixels, letter spacing in em.", "class NikoTextToken {",
            "  const NikoTextToken(this.mono, this.size, this.height, this.weight, this.spacingEm, {this.upper = false});",
            "", "  final bool mono;", "  final double size;", "  final double height;", "  final FontWeight weight;",
            "  final double spacingEm;", "  final bool upper;", "}", "", "abstract final class NikoType {"]
    families = tokens["type"]["families"]
    for key in ("sans", "mono"):
        first = families[key].split(",")[0].strip().strip('"')
        out.append(f"  static const {key}Family = '{first}';")
    for group in tokens["type"]["groups"]:
        mono = "true" if group["family"] == "mono" else "false"
        for style in group["styles"]:
            spacing = style.get("letterSpacing", "0em")
            match = re.fullmatch(r"(-?[0-9.]+)em", spacing)
            if not match:
                raise ValueError(f"letter spacing not in em: {spacing}")
            # "always capitals" is the label style's rule (its usage says so): the one style the tokens mark this way
            upper = ", upper: true" if style["name"] == "label" else ""
            out.append(f"  /// {style['usage']}")
            out.append(f"  static const {camel(style['name'])} = NikoTextToken({mono}, {px(style['fontSize'])}, "
                       f"{px(style['lineHeight'])}, FontWeight.w{style['fontWeight']}, {float(match.group(1))!r}{upper});")
    out += ["}", ""]

    for section, cls in (("spacing", "NikoSpace"), ("radius", "NikoRadius"), ("size", "NikoSize"), ("stroke", "NikoStroke")):
        out.append(f"abstract final class {cls} {{")
        for token in tokens[section]["tokens"]:
            short = token["name"].split("-", 1)[1]
            name = camel(short if not short[0].isdigit() else "s" + short)
            out.append(f"  /// {token['usage']}")
            out.append(f"  static const double {name} = {px(token['value'])};")
        out += ["}", ""]
    opacity = {token["name"]: token["value"] for token in tokens["opacity"]["tokens"]}
    out += ["abstract final class NikoOpacity {", f"  static const double disabled = {float(opacity['opacity-disabled'])!r};", "}", ""]
    return "\n".join(out)


# The res/font file for a family and a weight in Compose: each weight its own Font (Compose picks among them)
COMPOSE_FONTS = {"sans": {400: "geist_regular", 500: "geist_medium", 600: "geist_semibold"},
                 "mono": {400: "geist_mono", 500: "geist_mono_medium"}}


def kotlin_number(value):
    """A px or em number as Kotlin writes it: 16, -0.02."""
    number = float(value)
    return str(int(number)) if number == int(number) else repr(number)


def kotlin_colour(value, themes):
    return "Color(" + colour(value, themes) + ")"


def compose(tokens):
    out = [
        "// GENERATED by shared/tool/gen_tokens.py from shared/tokens.json. Do not edit: change the tokens and run it.",
        "",
        "package io.niko.ui.compose",
        "",
        "import androidx.compose.runtime.Immutable",
        "import androidx.compose.ui.graphics.Color",
        "import androidx.compose.ui.text.TextStyle",
        "import androidx.compose.ui.text.font.Font",
        "import androidx.compose.ui.text.font.FontFamily",
        "import androidx.compose.ui.text.font.FontWeight",
        "import androidx.compose.ui.text.style.LineHeightStyle",
        "import androidx.compose.ui.unit.dp",
        "import androidx.compose.ui.unit.em",
        "import androidx.compose.ui.unit.sp",
        "import io.niko.R",
        "",
        "/** The colours of one theme of the design system; [NikoTheme] gives the one night mode asks for. */",
        "@Immutable",
        "class NikoColors(",
    ]
    names = [token["name"] for token in tokens["color"]["tokens"]]
    for token in tokens["color"]["tokens"]:
        out.append(f"    /** {token['usage']} */")
        out.append(f"    val {camel(token['name'])}: Color,")
    out.append(") {")
    out.append("    companion object {")
    for theme in tokens["color"]["themes"]:
        values = {token["name"]: token["value"][theme["id"]] for token in tokens["color"]["tokens"]}
        out.append(f"        val {theme['id'].capitalize()} = NikoColors(")
        out += [f"            {camel(name)} = {kotlin_colour(values[name], values)}," for name in names]
        out.append("        )")
    out += ["    }", "}", ""]

    out.append("/** The design system's two families, a Font for each weight its text styles take. */")
    out.append("object NikoFonts {")
    for key, weights in COMPOSE_FONTS.items():
        fonts = ", ".join(f"Font(R.font.{f}, FontWeight.W{w})" for w, f in weights.items())
        out.append(f"    val {key.capitalize()} = FontFamily({fonts})")
    out += ["}", ""]

    out.append("/** The design system's text styles, without a colour (the caller's). label is always written in capitals. */")
    out.append("object NikoType {")
    out.append("    // a line as CSS lays it out: the whole line height, the text in its middle (Compose trims a text's first")
    out.append("    // and last line to the font by default, a single line 2-4 dp shorter than on the canvas)")
    out.append("    private val CssLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)")
    out.append("")
    for group in tokens["type"]["groups"]:
        family = group["family"].capitalize()
        for style in group["styles"]:
            if style["fontWeight"] not in COMPOSE_FONTS[group["family"]]:
                raise ValueError(f"no font for {group['family']} {style['fontWeight']}")
            spacing = em(style.get("letterSpacing", "0em"))
            out.append(f"    /** {style['usage']} */")
            out.append(f"    val {camel(style['name'])} = TextStyle(fontFamily = NikoFonts.{family}, "
                       f"fontWeight = FontWeight.W{style['fontWeight']}, "
                       f"fontSize = {kotlin_number(px(style['fontSize']))}.sp, "
                       f"lineHeight = {kotlin_number(px(style['lineHeight']))}.sp, "
                       f"letterSpacing = ({kotlin_number(spacing)}).em, lineHeightStyle = CssLine)")
    out += ["}", ""]

    for section, cls in (("spacing", "NikoSpace"), ("radius", "NikoRadius"), ("size", "NikoSize"), ("stroke", "NikoStroke")):
        out.append(f"object {cls} {{")
        for token in tokens[section]["tokens"]:
            short = token["name"].split("-", 1)[1]
            name = camel(short if not short[0].isdigit() else "s" + short)
            out.append(f"    /** {token['usage']} */")
            out.append(f"    val {name} = {kotlin_number(px(token['value']))}.dp")
        out += ["}", ""]
    opacity = {token["name"]: token["value"] for token in tokens["opacity"]["tokens"]}
    out += ["object NikoOpacity {", f"    const val DISABLED = {float(opacity['opacity-disabled'])!r}f", "}", ""]
    return "\n".join(out)


def main():
    parser = argparse.ArgumentParser(description="The design system's tokens as code for each platform.")
    parser.add_argument("--check", action="store_true", help="exit 1 if a generated file is not what the tokens give")
    parser.add_argument("--desktop", metavar="DIR", help="a checkout of the window, arngory/niko-desktop")
    args = parser.parse_args()
    desktop = os.path.abspath(args.desktop) if args.desktop else None
    if desktop and not os.path.isfile(os.path.join(desktop, "pubspec.yaml")):
        print(f"{args.desktop} is not a checkout of the window (no pubspec.yaml)")
        return 2
    tokens = json.load(open(os.path.join(ROOT, "shared", "tokens.json"), encoding="utf-8"))
    files = {os.path.join(ROOT, COMPOSE): compose(tokens)}
    for theme, path in ANDROID.items():
        files[os.path.join(ROOT, path)] = android(tokens, theme)
    files[os.path.join(ROOT, ANDROID_TYPE)] = android_type(tokens)
    files[os.path.join(ROOT, ANDROID_DIMENS)] = android_dimens(tokens)
    if desktop:
        files[os.path.join(desktop, DART)] = dart(tokens)
    problems = contrast_problems(tokens)
    if problems:
        print("shared/tokens.json:\n  " + "\n  ".join(problems))
        return 1
    stale = 0
    check = args.check
    for copy, source in FONT_COPIES.items() if desktop else ():
        data = open(os.path.join(ROOT, source), "rb").read()
        full = os.path.join(desktop, copy)
        if check:
            if not os.path.exists(full) or open(full, "rb").read() != data:
                print(f"{os.path.relpath(full)} is not {source}: run shared/tool/gen_tokens.py --desktop")
                stale = 1
            continue
        open(full, "wb").write(data)
    for full, text in files.items():
        if check:
            have = open(full, encoding="utf-8").read() if os.path.exists(full) else ""
            if have != text:
                print(f"{os.path.relpath(full)} is not what shared/tokens.json gives: run shared/tool/gen_tokens.py")
                stale = 1
            continue
        os.makedirs(os.path.dirname(full), exist_ok=True)
        open(full, "w", encoding="utf-8").write(text)
    return stale

if __name__ == "__main__":
    sys.exit(main())
