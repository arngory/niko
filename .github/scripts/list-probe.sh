#!/bin/bash
# The list-screen probe on an emulator (ci.yml, job smoke, after smoke.sh; docs/research/list-screens-probe.md):
# Настройки › Вход made by hand (ListProbeHandActivity, variant А) and drawn from its description
# (ListProbeSpecActivity, variant В). Each is started cold five times, in turn, in a process of its own: the start
# time `am start -W` gives and the window process's memory 5 s after (dumpsys meminfo). Then each, warm, makes its
# screen anew 300 times, a frame each, and logs the median and 90th percentile from composition to drawn. A Markdown
# table of the medians goes to the step summary. Arguments: the output folder.
set -u
pkg=io.niko
out=${1:-list-probe}
here=$(dirname "$0")
mkdir -p "$out"
: > "$out/rows.tsv"
fail=0

measure() { # name activity round
  timeout 30 adb shell am force-stop "$pkg"
  sleep 2
  start=$(timeout 60 adb shell am start -W -n "$pkg/$2" 2>&1 | tr -d '\r')
  echo "$start" > "$out/$1-$3.start"
  total=$(echo "$start" | awk -F': *' '/^TotalTime/ {print $2}')
  case "$start" in *Error*|*Exception*|*"Permission Denial"*) echo "::error::$1 did not start: $start"; fail=1; return ;; esac
  sleep 5
  # the window's process alone: pidof matches the whole name, and the service's is io.niko:bg
  pid=$(timeout 10 adb shell pidof "$pkg" | tr -d '\r' | awk '{print $1}')
  [ -n "$pid" ] || { echo "::error::$1: no process after the start"; fail=1; return; }
  timeout 30 adb shell dumpsys meminfo "$pid" > "$out/$1-$3.meminfo" 2>&1
  python3 -I "$here/list-probe.py" row "$1" "$3" "${total:--}" "$out/$1-$3.meminfo" >> "$out/rows.tsv"
}

frames() { # name activity
  timeout 30 adb shell am force-stop "$pkg"
  sleep 2
  timeout 10 adb logcat -c
  timeout 60 adb shell am start -W -n "$pkg/$2" --ei frames 300 > /dev/null 2>&1
  for _ in $(seq 60); do
    # this variant's own line: logcat -c does not clear every emulator's buffer, and the other's may still be there
    line=$(timeout 10 adb logcat -d -s NikoListProbe:I | tr -d '\r' | grep -o "variant=$1 .*" | tail -1)
    [ -n "$line" ] && break
    sleep 1
  done
  [ -n "$line" ] || { echo "::error::$1: no frame times in the log"; fail=1; return; }
  echo "$line" | tee "$out/$1.frames"
}

for round in 1 2 3 4 5; do
  measure hand .ui.probe.ListProbeHandActivity "$round"
  measure spec .ui.probe.ListProbeSpecActivity "$round"
done
frames hand .ui.probe.ListProbeHandActivity
frames spec .ui.probe.ListProbeSpecActivity
timeout 30 adb shell am force-stop "$pkg"

# the screens as drawn, for a look at both
for name in hand spec; do
  [ "$name" = hand ] && activity=.ui.probe.ListProbeHandActivity || activity=.ui.probe.ListProbeSpecActivity
  timeout 60 adb shell am start -W -n "$pkg/$activity" > /dev/null 2>&1
  sleep 3
  timeout 30 adb exec-out screencap -p > "$out/$name.png" 2>/dev/null || true
  timeout 30 adb shell am force-stop "$pkg"
done

python3 -I "$here/list-probe.py" table "$out/rows.tsv" "$out" | tee "$out/summary.md" |
  tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"
exit "$fail"
