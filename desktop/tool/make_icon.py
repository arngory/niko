#!/usr/bin/env python3
"""The .ico of the PNGs tool/make_icon.js made: make_icon.py <dir with icon-<size>.png> [app_icon.ico]."""
import os
import sys

from PIL import Image

SIZES = [16, 20, 24, 32, 40, 48, 64, 128, 256]
source = sys.argv[1]
target = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "windows", "runner", "resources", "app_icon.ico")
images = [Image.open(os.path.join(source, f"icon-{size}.png")).convert("RGBA") for size in SIZES]
images[-1].save(target, format="ICO", sizes=[(size, size) for size in SIZES], append_images=images[:-1])
print(target, sorted(Image.open(target).ico.sizes()))
