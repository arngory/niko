"""The list-screen probe's numbers (list-probe.sh; docs/research/list-screens-probe.md).

  row NAME ROUND TOTAL MEMINFO   one TSV row: the start time (ms) and dumpsys meminfo's totals for one cold start
  table ROWS DIR                 Markdown: the medians of the cold starts per variant, the spec's against the hand's,
                                 then the frame times DIR/<variant>.frames hold

Columns, in KB, from dumpsys meminfo's App Summary: pss its TOTAL PSS ("TOTAL" before Android 11), rss its TOTAL RSS
(Android 11 and later only: "-" on API 29); java, native, code and graphics its rows (Pss).
"""
import os
import re
import statistics
import sys

COLUMNS = ["name", "round", "start_ms", "pss", "rss", "java", "native", "code", "graphics"]
SUMMARY = {"Java Heap": "java", "Native Heap": "native", "Code": "code", "Graphics": "graphics"}
NAMES = {"hand": "А, by hand", "spec": "В, from the description"}


def meminfo(path):
    row = {}
    try:
        text = open(path, errors="replace").read()
    except OSError:
        return row
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


def table(rows_path, folder):
    rows = [dict(zip(COLUMNS, line.rstrip("\n").split("\t"))) for line in open(rows_path)]
    shown = COLUMNS[2:]
    print("### List-screen probe: Настройки › Вход, cold start, median of 5")
    print()
    print("| variant | " + " | ".join(shown) + " | frame median µs | frame p90 µs |")
    print("|---|" + "---|" * (len(shown) + 2))
    for name in NAMES:
        cells = []
        for c in shown:
            v = median(rows, name, c)
            base = median(rows, "hand", c)
            if v is None:
                cells.append("-")
            elif name == "hand" or base is None:
                cells.append(f"{v:g}")
            else:
                cells.append(f"{v:g} ({v - base:+g})")
        frames = {}
        try:
            frames = dict(kv.split("=", 1) for kv in open(os.path.join(folder, f"{name}.frames")).read().split())
        except (OSError, ValueError):
            pass
        cells += [frames.get("median_us", "-"), frames.get("p90_us", "-")]
        print(f"| {NAMES[name]} | " + " | ".join(cells) + " |")
    print()
    print("KB but start_ms; in brackets, against А. Frames: the screen made anew 300 times, warm, from its composition"
          " to its drawing. Each start: " + ", ".join(
              f"{r['name']} {r['round']}: pss {r.get('pss', '-')}, {r.get('start_ms', '-')} ms" for r in rows))


def main():
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "row":
        name, round_, total, path = sys.argv[2:6]
        row = {"name": name, "round": round_, "start_ms": total}
        row.update(meminfo(path))
        print("\t".join(row.get(c, "-") for c in COLUMNS))
    elif command == "table":
        table(sys.argv[2], sys.argv[3])
    else:
        sys.exit(__doc__)


main()
