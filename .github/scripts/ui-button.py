"""The center of the positive button of the dialog on the screen, when the screen shows the text given: "x y", or
nothing. An alert dialog's button by its id; the Niko dialog's (ui/compose/NikoDialog, drawn by Compose: no ids) by
its text, "Yes" unless a third argument names it. Arguments: uiautomator's dump, the text, the button's text (ci.yml,
job e2e; smoke.sh)."""
import re
import sys
import xml.etree.ElementTree as ET

try:
    nodes = list(ET.parse(sys.argv[1]).getroot().iter("node"))
except (ET.ParseError, OSError):
    sys.exit(0)
button = sys.argv[3] if len(sys.argv) > 3 else "Yes"
if any(sys.argv[2] in (n.get("text") or "") for n in nodes):
    found = [n for n in nodes if n.get("resource-id") == "android:id/button1"] or \
        [n for n in nodes if button in (n.get("text"), n.get("content-desc"))]
    if found:
        x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", found[0].get("bounds", ""))[:4])
        print((x1 + x2) // 2, (y1 + y2) // 2)
