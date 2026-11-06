"""The center of the positive button of the dialog on the screen, when the screen shows the text given: "x y", or
nothing. Arguments: uiautomator's dump, the text (ci.yml, job e2e)."""
import re
import sys
import xml.etree.ElementTree as ET

try:
    nodes = list(ET.parse(sys.argv[1]).getroot().iter("node"))
except (ET.ParseError, OSError):
    sys.exit(0)
if any(sys.argv[2] in (n.get("text") or "") for n in nodes):
    for n in nodes:
        if n.get("resource-id") == "android:id/button1":
            x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", n.get("bounds", ""))[:4])
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
