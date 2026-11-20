#!/bin/bash
# The UI probe on an emulator (ci.yml, job smoke, after smoke.sh; docs/research/ui-probe.md): the canvas's
# Главная · Простой drawn with Views (ProbeViewsActivity) and with Compose (ProbeComposeActivity), and the app's
# MainActivity beside them for scale. Each is started cold three times, in turn, in a process of its own: the start
# time `am start -W` gives, and the window process's memory 5 s after (dumpsys meminfo). A Markdown table of the
# medians goes to the step summary, with each APK's size and its dex. Arguments: the APK folder, the output folder.
set -u
pkg=io.niko
apks=$1
out=${2:-ui-probe}
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
  python3 -I "$here/ui-probe.py" row "$1" "$3" "${total:--}" "$out/$1-$3.meminfo" >> "$out/rows.tsv"
}

for round in 1 2 3; do
  measure views .ui.probe.ProbeViewsActivity "$round"
  measure compose .ui.probe.ProbeComposeActivity "$round"
  measure main .ui.MainActivity "$round"
done
timeout 30 adb shell am force-stop "$pkg"

# the screens as drawn, for a look at both halves side by side
for name in views compose; do
  [ "$name" = views ] && activity=.ui.probe.ProbeViewsActivity || activity=.ui.probe.ProbeComposeActivity
  timeout 60 adb shell am start -W -n "$pkg/$activity" > /dev/null 2>&1
  sleep 3
  timeout 30 adb exec-out screencap -p > "$out/$name.png" 2>/dev/null || true
  timeout 30 adb shell am force-stop "$pkg"
done

python3 -I "$here/ui-probe.py" table "$out/rows.tsv" "$apks"/*.apk | tee "$out/summary.md" |
  tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"
exit "$fail"
