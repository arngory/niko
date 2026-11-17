"""The centre of the first node on the screen whose text or description is the one given: "x y", or nothing.
Arguments: uiautomator's dump, the text (smoke.sh)."""
import re
import sys
import xml.etree.ElementTree as ET

try:
    nodes = list(ET.parse(sys.argv[1]).getroot().iter("node"))
except (ET.ParseError, OSError):
    sys.exit(0)
for node in nodes:
    if sys.argv[2] in (node.get("text"), node.get("content-desc")):
        x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", node.get("bounds", ""))[:4])
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
