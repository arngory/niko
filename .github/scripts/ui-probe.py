"""The UI probe's numbers (ui-probe.sh; docs/research/ui-probe.md).

  row NAME ROUND TOTAL MEMINFO   one TSV row: the start time (ms) and dumpsys meminfo's totals for one cold start
  table ROWS APK...              Markdown: the medians per screen, Compose and MainActivity against Views, then
                                 each APK's size and its dex

Columns, in KB, from dumpsys meminfo's App Summary: pss its TOTAL PSS ("TOTAL" before Android 11), rss its TOTAL RSS
(Android 11 and later only: "-" on API 29); java, native, code, graphics and private_other its rows (Pss).
"""
import os
import re
import statistics
import sys
import zipfile

COLUMNS = ["name", "round", "start_ms", "pss", "rss", "java", "native", "code", "graphics", "private_other"]
SUMMARY = {"Java Heap": "java", "Native Heap": "native", "Code": "code", "Graphics": "graphics",
           "Private Other": "private_other"}
ORDER = ["views", "compose", "main"]
NAMES = {"views": "Views (probe)", "compose": "Compose (probe)", "main": "MainActivity (now)"}


def meminfo(path):
    row = {}
    try:
        text = open(path, errors="replace").read()
    except OSError:
        return row
    # the totals of the App Summary alone (the table above it has a TOTAL row of its own): "TOTAL PSS: n ... TOTAL
    # RSS: n" since Android 11, "TOTAL: n" with no RSS before (API 29 has PSS only)
    summary = text.split("App Summary", 1)[1] if "App Summary" in text else ""
    m = re.search(r"TOTAL(?: PSS)?:\s+(\d+)", summary)
    if m:
        row["pss"] = m.group(1)
    m = re.search(r"TOTAL RSS:\s+(\d+)", summary)
    if m:
        row["rss"] = m.group(1)
    for line in summary.splitlines():
        m = re.match(r"\s*([A-Za-z ]+):\s+(\d+)", line)
        if m and m.group(1).strip() in SUMMARY:
            row[SUMMARY[m.group(1).strip()]] = m.group(2)
    return row


def median(rows, name, column):
    values = [int(r[column]) for r in rows if r["name"] == name and r.get(column, "-").isdigit()]
    return statistics.median(values) if values else None


def table(rows_path, apks):
    rows = []
    for line in open(rows_path):
        values = line.rstrip("\n").split("\t")
        rows.append(dict(zip(COLUMNS, values)))
    shown = ["start_ms", "pss", "rss", "java", "native", "code", "graphics", "private_other"]
    print("### UI probe: Главная · Простой, cold start, median of 3")
    print()
    print("| screen | " + " | ".join(shown) + " |")
    print("|---|" + "---|" * len(shown))
    base = {c: median(rows, "views", c) for c in shown}
    for name in ORDER:
        cells = []
        for c in shown:
            v = median(rows, name, c)
            if v is None:
                cells.append("-")
            elif name == "views" or base[c] is None:
                cells.append(f"{v:g}")
            else:
                cells.append(f"{v:g} ({v - base[c]:+g})")
        print(f"| {NAMES[name]} | " + " | ".join(cells) + " |")
    print()
    print("KB but start_ms; in brackets, against Views. Each run: " + ", ".join(
        f"{r['name']} {r['round']}: pss {r.get('pss', '-')}, {r.get('start_ms', '-')} ms" for r in rows))
    print()
    print("| APK | bytes | dex bytes |")
    print("|---|---|---|")
    for apk in apks:
        try:
            with zipfile.ZipFile(apk) as z:
                dex = sum(i.compress_size for i in z.infolist() if re.fullmatch(r"classes\d*\.dex", i.filename))
            print(f"| {os.path.basename(apk)} | {os.path.getsize(apk)} | {dex} |")
        except (OSError, zipfile.BadZipFile) as e:
            print(f"| {apk} | {e} | - |")


def main():
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "row":
        name, round_, total, path = sys.argv[2:6]
        row = {"name": name, "round": round_, "start_ms": total}
        row.update(meminfo(path))
        print("\t".join(row.get(c, "-") for c in COLUMNS))
    elif command == "table":
        table(sys.argv[2], sys.argv[3:])
    else:
        sys.exit(__doc__)


main()
