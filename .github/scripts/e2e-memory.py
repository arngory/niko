"""Memory of the app's processes at the phases of the e2e (ci.yml, job e2e; e2e.sh, memory()), in KB.

  row PHASE PROC PREFIX GOMEM   one TSV row: PREFIX.meminfo (dumpsys meminfo PID), PREFIX.smaps (/proc/PID/smaps),
                                GOMEM the last go-mem line of the log for PROC (libcore MemoryStats), "-" for none
  header                        the TSV's header
  table SUMMARY                 the rows as a Markdown table, the columns that tell most
  tap UI_XML TEXT               "x y": the center of the node whose text or content-desc is TEXT, or whose
                                resource-id ends in TEXT; nothing when there is none

What each column is:
  pss, rss, swap_pss         dumpsys meminfo's TOTAL PSS, TOTAL RSS, TOTAL SWAP PSS
  java, native, code, stack, graphics, private_other, system
                             its App Summary (Pss); unknown_rss its Unknown, which has an Rss only
  m_native, m_so, m_other_mmap, m_unknown
                             rows of its table (Pss): Native Heap is bionic's malloc only (scudo); the core's Go heap
                             and Chromium's PartitionAlloc are "Unknown" (named anonymous mappings)
  go_rss, go_pss             smaps of "[anon: Go: ...]": the Go runtime's mappings (heap, stacks, metadata; Go 1.25+)
  pa_rss, pa_pss             smaps of "[anon:partition_alloc]": Chromium's allocator (naive; malloc of the library)
  scudo_rss                  smaps of bionic's malloc
  gojni_rss, gojni_pss, gojni_dirty
                             libgojni.so's mappings and its .bss: code and data, the relocated part private dirty
  dalvik_rss                 ART's heaps and its other mappings
  go_*                       the go-mem line (the Go runtime's own view, before FreeOSMemory)
  go_live                    its heap_inuse after FreeOSMemory: what the collection that comes with it found live
"""
import re
import sys
import xml.etree.ElementTree as ET

COLUMNS = [
    "phase", "proc", "pid",
    "pss", "rss", "swap_pss",
    "java", "native", "code", "stack", "graphics", "private_other", "system", "unknown_rss",
    "m_native", "m_so", "m_other_mmap", "m_unknown",
    "go_rss", "go_pss", "pa_rss", "pa_pss", "scudo_rss", "gojni_rss", "gojni_pss", "gojni_dirty", "dalvik_rss",
    "go_sys", "go_heap_inuse", "go_heap_idle", "go_heap_released", "go_stack", "go_next_gc", "go_gc", "go_goroutines",
    "go_live",
]
SUMMARY = {"Java Heap": "java", "Native Heap": "native", "Code": "code", "Stack": "stack", "Graphics": "graphics",
           "Private Other": "private_other", "System": "system"}
TABLE = {"Native Heap": "m_native", ".so mmap": "m_so", "Other mmap": "m_other_mmap", "Unknown": "m_unknown"}


def meminfo(path, row):
    try:
        text = open(path, errors="replace").read()
    except OSError:
        return
    m = re.search(r"\*\* MEMINFO in pid (\d+)", text)
    if m:
        row["pid"] = m.group(1)
    summary = False
    pss_end = rss_end = None
    for line in text.splitlines():
        if "App Summary" in line:
            summary = True
            continue
        if summary and "Pss(KB)" in line and "Rss(KB)" in line:
            pss_end, rss_end = line.index("Pss(KB)") + 7, line.index("Rss(KB)") + 7
            continue
        m = re.search(r"TOTAL PSS:\s+(\d+)", line)
        if m:
            row["pss"] = m.group(1)
            for key, label in (("rss", "TOTAL RSS"), ("swap_pss", "TOTAL SWAP PSS")):
                n = re.search(label + r":\s+(\d+)", line)
                if n:
                    row[key] = n.group(1)
            continue
        if summary:
            m = re.match(r"\s*([A-Za-z ]+):", line)
            if not m:
                continue
            label = m.group(1).strip()
            for number in re.finditer(r"\d+", line[m.end():]):
                end = m.end() + number.end()
                # the column a number stands under: Unknown has an Rss only
                is_rss = pss_end is not None and abs(end - rss_end) < abs(end - pss_end)
                if label == "Unknown" and is_rss:
                    row["unknown_rss"] = number.group()
                elif label in SUMMARY and not is_rss:
                    row[SUMMARY[label]] = number.group()
        else:
            m = re.match(r"\s*([A-Za-z. ]+?)\s+(\d+)(?:\s|$)", line)
            if m and m.group(1).strip() in TABLE:
                row[TABLE[m.group(1).strip()]] = m.group(2)


def smaps(path, row):
    sums = {}
    kind = None
    previous = ""
    try:
        lines = open(path, errors="replace").read().splitlines()
    except OSError:
        return
    for line in lines:
        head = re.match(r"^[0-9a-f]+-[0-9a-f]+\s+\S+\s+\S+\s+\S+\s+\d+\s*(.*)$", line)
        if head:
            name = head.group(1).strip()
            if name.startswith("[anon: Go:") or name.startswith("[anon:Go:"):
                kind = "go"
            elif name.startswith("[anon:partition_alloc"):
                kind = "pa"
            elif name.startswith("[anon:scudo:") or name.startswith("[anon:libc_malloc") or name == "[heap]":
                kind = "scudo"
            elif name.endswith("libgojni.so") or (name == "[anon:.bss]" and previous.endswith("libgojni.so")):
                kind = "gojni"
            elif name.startswith("[anon:dalvik-") or "dalvik" in name or name.endswith(".art"):
                kind = "dalvik"
            else:
                kind = None
            if name:
                previous = name if name != "[anon:.bss]" else previous
            continue
        if kind is None:
            continue
        m = re.match(r"^(Rss|Pss|Private_Dirty):\s+(\d+) kB", line)
        if m:
            key = (kind, m.group(1))
            sums[key] = sums.get(key, 0) + int(m.group(2))
    for kind, columns in (("go", ("go_rss", "go_pss")), ("pa", ("pa_rss", "pa_pss")), ("scudo", ("scudo_rss",)),
                          ("gojni", ("gojni_rss", "gojni_pss", "gojni_dirty")), ("dalvik", ("dalvik_rss",))):
        for column in columns:
            field = {"rss": "Rss", "pss": "Pss", "dirty": "Private_Dirty"}[column.rsplit("_", 1)[1]]
            row[column] = str(sums.get((kind, field), 0))


def gomem(line, row):
    before, _, after = line.partition("| after")
    for key, value in re.findall(r"(\w+)=(\S+)", before):
        column = "go_" + key
        if column in COLUMNS:
            row[column] = value
    m = re.search(r"\bheap_inuse=(\d+)", after)
    if m:
        row["go_live"] = m.group(1)


def main():
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "header":
        print("\t".join(COLUMNS))
    elif command == "row":
        phase, proc, prefix, go = sys.argv[2:6]
        row = {"phase": phase, "proc": proc}
        meminfo(prefix + ".meminfo", row)
        smaps(prefix + ".smaps", row)
        if go and go != "-":
            gomem(go, row)
        print("\t".join(row.get(c, "-") for c in COLUMNS))
    elif command == "table":
        shown = ["phase", "proc", "pss", "rss", "java", "native", "code", "m_unknown", "go_rss", "pa_rss", "gojni_dirty",
                 "go_heap_inuse", "go_live", "go_heap_idle", "go_heap_released", "go_goroutines"]
        try:
            rows = [line.rstrip("\n").split("\t") for line in open(sys.argv[2])]
        except OSError:
            return
        if not rows:
            return
        index = {name: i for i, name in enumerate(rows[0])}
        print("| " + " | ".join(shown) + " |")
        print("|" + "---|" * len(shown))
        for r in rows[1:]:
            print("| " + " | ".join(r[index[c]] if c in index and index[c] < len(r) else "-" for c in shown) + " |")
    elif command == "tap":
        try:
            nodes = list(ET.parse(sys.argv[2]).getroot().iter("node"))
        except (ET.ParseError, OSError):
            return
        want = sys.argv[3]
        for n in nodes:
            if want in ((n.get("text") or ""), (n.get("content-desc") or "")) or \
                    (n.get("resource-id") or "").endswith(want):
                x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", n.get("bounds", ""))[:4])
                print((x1 + x2) // 2, (y1 + y2) // 2)
                return


main()
