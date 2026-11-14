#!/bin/bash
# The app end to end on an emulator (ci.yml, job e2e), against the CI node: the invite opened as a link registers the
# device, the subscription brings the node's Hysteria2 profile, the VPN goes up through it, and a request of the
# emulator's reaches the runner's HTTP server from the node's address, where without the VPN, before and after, it
# comes from one of the runner's own (the emulator's network is the runner's sockets). Then a slipstream row: the VPN
# through the bundled client to the runner's slipstream server, whose VLESS stage (203.0.113.3) the request comes from.
# Last the root store: a Trojan profile to a TLS server whose certificate comes from a root installed on the emulator
# as its user's; refused under the Chrome list (the default), carried under the system's store.
# Then naive, which is Chromium of the library the APK carries: against rayd naive under the same root, a profile that
# names no root (refused: the root is the phone's user's, and naive follows no setting of the app's), then profiles
# that carry the root, over HTTP/2 and over QUIC.
# Arguments: the APK folder, the invite file, the server's log, the token it serves, the folder for what the emulator
# showed (a failure's artifact), the slipstream link, the root to install, the link of the profile under it, the
# folder of the naive links (h2.link, quic.link, h2-cert.link, quic-cert.link, public.link, hello.link) and of Chrome's ClientHello
# (chrome-hello.hex, chrome-hello.source).
set -u
pkg=io.niko
apk=$(ls "$1"/*-x86_64.apk 2>/dev/null | head -1)
[ -n "$apk" ] || { echo "::error::no signed x86_64 APK in $1"; ls "$1"; exit 1; }
invite=$(cat "$2")
weblog=$3
secret=$(cat "$4")
out=$5
slip_link=$(cat "$6")
user_root=$7
roots_link=$(cat "$8")
naive_links=$9
here=$(dirname "$0")
mkdir -p "$out"
# raycontrol-interop's addresses: the runner 203.0.113.1 (its server), the node .2
runner=203.0.113.1
node=203.0.113.2
# slipstream's VLESS stage (ci.yml)
slip_stage=203.0.113.3
# rayd naive behind its front (ci.yml)
naive_stage=203.0.113.5
port=8080
# ProxyEntity.TYPE_HYSTERIA, TYPE_VMESS (VLESS rows too), TYPE_TROJAN, TYPE_NAIVE
hysteria=15
vless=4
trojan=6
naive=9

finish() {
  timeout 30 adb exec-out screencap -p > "$out/screen.png" 2>/dev/null
  timeout 60 adb logcat -d > "$out/logcat.txt" 2>/dev/null
  timeout 30 adb pull "/data/data/$pkg/cache/neko.log" "$out/neko.log" > /dev/null 2>&1
  { timeout 20 adb shell ip addr; timeout 20 adb shell ip rule; timeout 30 adb shell dumpsys connectivity;
    timeout 20 adb shell ps -A -o PID,USER,ARGS; } > "$out/network.txt" 2>&1
  status=$1
  if grep -E "FATAL EXCEPTION|ANR in $pkg" -A 20 "$out/logcat.txt" | grep -B2 -A20 "$pkg"; then
    echo "::error::the app crashed or did not answer"; status=1
  fi
  echo "--- the app's warnings and errors"
  grep -E '^[0-9-]+ [0-9:.]+ +[0-9]+ +[0-9]+ [WEF] ' "$out/logcat.txt" | grep -iE "$pkg|niko|libcore|sing-box" | tail -n 60
  if [ "$status" != 0 ] && [ -s "$out/neko.log" ]; then
    echo "--- the core's log (its end)"
    tail -n 80 "$out/neko.log"
  fi
  exit "$status"
}
fail() { echo "::error::$*"; finish 1; }

# fetch TAG [WAIT]: the emulator asks the runner's server for its token, the request marked with TAG, its side open WAIT
# seconds (3 by default). from: the address the server saw it from (empty when it did not come); carried: whether the
# answer carried the token; last: what nc said
fetch() {
  local body wait=${2:-3}
  # the request's side stays open until the answer has come: a Hysteria2 stream closes whole on a half-close (sing-
  # quic's clientConn has no CloseWrite), and nc would half-close at the end of its input
  body=$(timeout 60 adb shell "(printf 'GET /e2e?$1 HTTP/1.0\r\n\r\n'; sleep $wait) | nc -w 20 -q 2 $runner $port" 2>&1 | tr -d '\r')
  sleep 1
  from=$(grep -F "\"GET /e2e?$1 " "$weblog" | head -1 | cut -d' ' -f1)
  carried=no
  if grep -qF "$secret" <<< "$body"; then carried=yes; fi
  last=$(head -c 300 <<< "$body")
}

# the ID of the first profile of type $1 in the app's database (with "last" as $2, of the newest), empty while there
# is none
profile() {
  rm -rf "$out/db" && mkdir -p "$out/db"
  for f in sager_net.db sager_net.db-wal sager_net.db-shm; do
    timeout 30 adb pull "/data/data/$pkg/databases/$f" "$out/db/$f" > /dev/null 2>&1
  done
  # shellcheck disable=SC2086 # the optional word
  python3 -I "$here/e2e-profile.py" "$out/db/sager_net.db" "$1" ${2:-}
}

# tap TEXT: taps the positive button of the dialog that shows TEXT, waiting a minute for it
tap() {
  for i in $(seq 30); do
    timeout 30 adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    timeout 30 adb pull /sdcard/ui.xml "$out/ui.xml" > /dev/null 2>&1
    xy=$(python3 -I "$here/ui-button.py" "$out/ui.xml" "$1")
    if [ -n "$xy" ]; then
      # shellcheck disable=SC2086 # x and y
      timeout 20 adb shell input tap $xy && return 0
    fi
    sleep 2
  done
  return 1
}

echo "== root (the app's database, its components that are not exported)"
timeout 30 adb root || echo "adb root: $?"
rooted=0
for i in $(seq 30); do
  [ "$(timeout 5 adb shell id -u 2>/dev/null | tr -d '\r')" = 0 ] && { rooted=1; break; }
  sleep 1
done
timeout 60 adb wait-for-device || fail "the emulator did not come back after adb root"
[ "$rooted" = 1 ] || fail "no root on the emulator"

echo "== install $apk"
timeout 180 adb install -r -g "$apk" || fail "the APK did not install"
# the VPN's consent, as the user's tap on the system's dialog gives it
timeout 20 adb shell appops set "$pkg" ACTIVATE_VPN allow || fail "appops ACTIVATE_VPN"
adb logcat -c

echo "== the runner's server, directly"
# the emulator's network comes up a little after the boot
for i in $(seq 20); do
  fetch "direct-$i-$RANDOM"
  [ -n "$from" ] && [ "$carried" = yes ] && break
  sleep 3
done
[ -n "$from" ] && [ "$from" != "$node" ] && [ "$carried" = yes ] ||
  fail "the emulator's own request: from '${from:-nowhere}', token $carried, want the runner's own (nc said: $last)"
echo "directly: the runner's server saw the request from $from"

echo "== the invite, opened as a link"
timeout 60 adb shell "am start -W -n $pkg/$pkg.ui.MainActivity -a android.intent.action.VIEW -d '$invite'" > /dev/null ||
  fail "the invite did not open the app"
tap "raycontrol node" || fail "no question to import the invite on the screen"

echo "== registration and the subscription"
id=
for i in $(seq 45); do
  id=$(profile "$hysteria")
  [ -n "$id" ] && break
  sleep 2
done
[ -n "$id" ] || fail "no Hysteria2 profile from the node after 90 s"
echo "the node's Hysteria2 profile: $id"

echo "== the core's log at debug (a failure's artifact)"
# the app stopped first: its settings are Room's KeyValuePair in configuration.db, the log level a string
timeout 20 adb shell am force-stop "$pkg"
if timeout 20 adb shell "command -v sqlite3" > /dev/null 2>&1; then
  timeout 20 adb shell "sqlite3 /data/data/$pkg/databases/configuration.db \"INSERT OR REPLACE INTO KeyValuePair (key, valueType, value) VALUES ('logLevel', 5, CAST('3' AS BLOB));\"" ||
    echo "the log level not set: the core logs at its default"
else
  echo "no sqlite3 on the emulator: the core logs at its default level"
fi

echo "== the VPN through it"
timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" --el profile "$id" > /dev/null ||
  fail "the quick toggle did not start"
for i in $(seq 20); do
  fetch "vpn-$i-$RANDOM"
  [ "$from" = "$node" ] && [ "$carried" = yes ] && break
  sleep 3
done
[ "$from" = "$node" ] && [ "$carried" = yes ] ||
  fail "through the VPN: from '${from:-nowhere}', token $carried, want the node's $node (nc said: $last)"
echo "through the tunnel: the runner's server saw the request from $from"

echo "== Private DNS with the VPN on: each mode, then strict across a restart of the core"
# resolved NAME: the address ping got for NAME through the system's resolver (what every app gets), empty: none
resolved() {
  timeout 15 adb shell "ping -c 1 -W 3 $1" 2>&1 | tr -d '\r' | sed -n 's/^PING [^ ]* (\([^)]*\)).*/\1/p' | head -1
}
# the VPN's network in ConnectivityService (network{N}): a restart of the core may keep it, a handover in place;
# and its interface's index, new with each tun the service makes
vpn_net() {
  echo "$(timeout 30 adb shell dumpsys connectivity 2>/dev/null | tr -d '\r' | grep -m1 'ni{VPN' | grep -oE 'network\{[0-9]+\}' | head -1)" \
    "$(timeout 20 adb shell ip -o link 2>/dev/null | tr -d '\r' | grep -oE '^[0-9]+: tun[0-9]+' | head -1)"
}
# wait_resolved NAME TRIES: resolved, tried every 5 s; took: the seconds it took
wait_resolved() {
  got=
  took=0
  for i in $(seq "$2"); do
    sleep 5
    took=$((i * 5))
    got=$(resolved "$1")
    [ -n "$got" ] && break
  done
}
# pdns MODE NAME [HOST]: the mode set (HOST for hostname), then a real name not asked before (the resolver's cache)
pdns() {
  timeout 20 adb shell settings put global private_dns_mode "$1"
  [ -n "${3:-}" ] && timeout 20 adb shell settings put global private_dns_specifier "$3"
  wait_resolved "$2" 6
  echo "private DNS $1${3:+ $3}: $2 resolves to '${got:-nothing}'"
  { echo "=== $1 ${3:-} $2"; timeout 30 adb shell dumpsys dnsresolver; } >> "$out/dnsresolver.txt" 2>&1
  [ -n "$got" ] || fail "private DNS $1${3:+ $3} with the VPN on: $2 not resolved (the system's resolver, every app's)"
}
pdns off example.com
pdns opportunistic example.org
# dns.google is the remote DNS's own name: the core resolves it for real. Any other host gets FakeDNS's address
pdns hostname example.net dns.google
pdns hostname www.iana.org one.one.one.one
# the core restarted (a profile's change does it), now with the strict host in its config: the host resolved for
# real (above FakeDNS), and where the VPN's network stays (a handover in place) the system keeps the address it had
net_before=$(vpn_net)
timeout 20 adb shell am broadcast -a "$pkg.RELOAD" -p "$pkg" --ez "$pkg.extra.FORCE" true > /dev/null
sleep 3
for i in $(seq 20); do
  fetch "reload-$i-$RANDOM"
  [ "$from" = "$node" ] && [ "$carried" = yes ] && break
  sleep 3
done
[ "$from" = "$node" ] && [ "$carried" = yes ] ||
  fail "after a restart of the core: from '${from:-nowhere}', token $carried, want the node's $node (nc said: $last)"
net_after=$(vpn_net)
echo "the core restarted: the tunnel from $from, the VPN's network ${net_before:-?} then $net_after"
wait_resolved github.com 12
echo "private DNS hostname one.one.one.one after the restart: github.com resolves to '${got:-nothing}' (${took} s)"
{ echo "=== after the restart"; timeout 30 adb shell dumpsys dnsresolver; } >> "$out/dnsresolver.txt" 2>&1
[ -n "$got" ] || fail "private DNS hostname one.one.one.one after a restart of the core: github.com not resolved"
# the addresses the system has for the host on the VPN's network: on a new network resolved again, through the
# restarted core, whose config has the host: real ones, not FakeDNS's (198.18.0.0/15). Where the network stayed (a
# handover in place) the system keeps what the core before gave it, and store_fakeip is what keeps it working
netid=$(grep -oE 'network\{[0-9]+\}' <<< "$net_after" | grep -oE '[0-9]+')
id_before=$(grep -oE 'network\{[0-9]+\}' <<< "$net_before" | grep -oE '[0-9]+')
servers=$(timeout 30 adb shell dumpsys dnsresolver 2>/dev/null | tr -d '\r' |
  awk -v id="${netid:-none}" '/NetId: /{on = ($0 ~ ("NetId: " id "$"))} on && /name\{one\.one\.one\.one\}/')
echo "the VPN's network $netid, its Private DNS servers:"
echo "${servers:-  (none listed)}"
if [ -n "$netid" ] && [ "$netid" != "$id_before" ] && grep -qE '(^|[^0-9.])198\.1[89]\.' <<< "$servers"; then
  fail "the strict Private DNS host has FakeDNS's address on the VPN's new network after a restart of the core"
fi
[ -n "$servers" ] || echo "::warning::no Private DNS servers of the VPN's network in dumpsys dnsresolver (its format?)"
timeout 20 adb shell settings put global private_dns_mode off
timeout 30 adb pull "/data/data/$pkg/cache/neko.log" "$out/neko-pdns.log" > /dev/null 2>&1
grep -E ":853|missing fakeip|one\.one\.one\.one" "$out/neko-pdns.log" | tail -n 30

echo "== FakeDNS's names in the core's cache file: there while the VPN runs, gone once it stops"
canary="niko-e2e-$RANDOM$RANDOM.example.com"
# tried for a while: Private DNS was just turned off, and the system's resolver takes a moment to follow
wait_resolved "$canary" 6
echo "a name of its own: $canary resolves to '${got:-nothing}' (${took} s)"
# in_cache: yes or no, whether the cache file pulled holds the name; nofile when none came
in_cache() {
  rm -f "$out/cache.db"
  timeout 30 adb pull "/data/data/$pkg/no_backup/cache.db" "$out/cache.db" > /dev/null 2>&1
  if [ ! -s "$out/cache.db" ]; then echo nofile; elif grep -qaF "$canary" "$out/cache.db"; then echo yes; else echo no; fi
}
cached=no
for i in $(seq 5); do
  cached=$(in_cache)
  [ "$cached" = yes ] && break
  sleep 2
done
[ "$cached" = yes ] || fail "FakeDNS's name $canary not in the core's cache file while the VPN runs ($cached): store_fakeip"

echo "== the VPN down again"
timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" > /dev/null || fail "the quick toggle did not stop"
for i in $(seq 10); do
  fetch "after-$i-$RANDOM"
  [ -n "$from" ] && [ "$from" != "$node" ] && [ "$carried" = yes ] && break
  sleep 3
done
[ -n "$from" ] && [ "$from" != "$node" ] && [ "$carried" = yes ] ||
  fail "after the VPN: from '${from:-nowhere}', token $carried, want the runner's own (nc said: $last)"
for i in $(seq 10); do
  cached=$(in_cache)
  [ "$cached" = no ] && break
  sleep 2
done
[ "$cached" = no ] || fail "after the VPN stopped, FakeDNS's name $canary in the core's cache file ($cached)"
echo "the VPN stopped: $canary is out of the core's cache file"

echo "== slipstream: the link, the VPN through the bundled client"
timeout 60 adb shell "am start -W -n $pkg/$pkg.ui.MainActivity -a android.intent.action.VIEW -d '$slip_link'" > /dev/null ||
  fail "the slipstream link did not open the app"
tap "Import profile" || fail "no question to import the slipstream link on the screen"
id=
for i in $(seq 20); do
  id=$(profile "$vless")
  [ -n "$id" ] && break
  sleep 2
done
[ -n "$id" ] || fail "no VLESS profile from the slipstream link"
echo "the slipstream profile: $id"
timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" --el profile "$id" > /dev/null ||
  fail "the quick toggle did not start (slipstream)"
# the client's handshake goes through DNS, and the tunnel is shared with the emulator's own traffic (Play services
# and the like): a longer wait for each answer
for i in $(seq 20); do
  fetch "slip-$i-$RANDOM" 15
  [ "$from" = "$slip_stage" ] && [ "$carried" = yes ] && break
  sleep 3
done
timeout 20 adb shell "ps -A -o PID,USER,ARGS | grep [l]ibslipstream" || echo "no slipstream client process"
[ "$from" = "$slip_stage" ] && [ "$carried" = yes ] ||
  fail "through slipstream: from '${from:-nowhere}', token $carried, want its stage $slip_stage (nc said: $last)"
echo "through slipstream: the runner's server saw the request from $from"
timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" > /dev/null || fail "the quick toggle did not stop (slipstream)"
for i in $(seq 10); do
  fetch "after-slip-$i-$RANDOM"
  [ -n "$from" ] && [ "$from" != "$slip_stage" ] && break
  sleep 3
done

echo "== the root store: a root the phone's user installed, under the Chrome list and under the system's store"
# the user's store as Android keeps it (Settings, Install a certificate, CA certificate): a file named by the
# subject's old hash. The app is stopped: its next process reads the store anew
timeout 20 adb shell am force-stop "$pkg"
# the core's log from here on is this profile's: what it said before is the profiles' before
timeout 20 adb shell "rm -f /data/data/$pkg/cache/neko.log"
hash=$(openssl x509 -in "$user_root" -noout -subject_hash_old) || fail "the root's subject hash"
added=/data/misc/user/0/cacerts-added
timeout 20 adb shell "mkdir -p $added" || fail "the user's root store: no folder"
timeout 30 adb push "$user_root" "$added/$hash.0" > /dev/null || fail "the user's root store: the root not written"
timeout 20 adb shell "chown -R system:system $added && chmod 755 $added && chmod 644 $added/$hash.0 && restorecon -R /data/misc/user/0" ||
  fail "the user's root store: its owner and mode"
timeout 60 adb shell "am start -W -n $pkg/$pkg.ui.MainActivity -a android.intent.action.VIEW -d '$roots_link'" > /dev/null ||
  fail "the link of the profile under the root did not open the app"
tap "Import profile" || fail "no question to import the profile under the root on the screen"
id=
for i in $(seq 20); do
  id=$(profile "$trojan")
  [ -n "$id" ] && break
  sleep 2
done
[ -n "$id" ] || fail "no Trojan profile from the link"
echo "the profile under the root: $id"
# the Chrome list, the app's default: the root on the phone opens nothing, and the core says whose fault it is
timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" --el profile "$id" > /dev/null ||
  fail "the quick toggle did not start (the Chrome list)"
# a request that went nowhere with the VPN up (one before it is up comes from the runner's own address), and the
# core's word for why, about this server
refused=no
for i in $(seq 10); do
  fetch "roots-chrome-$i-$RANDOM"
  [ "$from" = "$slip_stage" ] &&
    fail "under the Chrome list a certificate from a root the phone's user installed was taken: the request came from $from"
  timeout 30 adb pull "/data/data/$pkg/cache/neko.log" "$out/neko-roots.log" > /dev/null 2>&1
  if [ -z "$from" ] && grep -q "unknown authority" "$out/neko-roots.log" 2> /dev/null; then refused=yes; break; fi
  sleep 3
done
[ "$refused" = yes ] ||
  fail "under the Chrome list: no request lost to an unknown authority (the last from '${from:-nowhere}', nc said: $last)"
echo "the Chrome list: the request went nowhere, and the core's log says, $(grep -c 'unknown authority' "$out/neko-roots.log") times:"
grep -m1 "unknown authority" "$out/neko-roots.log" | cut -c1-400
timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" > /dev/null || fail "the quick toggle did not stop (the Chrome list)"
sleep 3
# the system's store: the setting as the app keeps it (configuration.db, a string), the app stopped first
timeout 20 adb shell am force-stop "$pkg"
timeout 20 adb shell "sqlite3 /data/data/$pkg/databases/configuration.db \"INSERT OR REPLACE INTO KeyValuePair (key, valueType, value) VALUES ('rootStore', 5, CAST('system' AS BLOB));\"" ||
  fail "the root store setting not written (sqlite3 on the emulator)"
timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" --el profile "$id" > /dev/null ||
  fail "the quick toggle did not start (the system's store)"
for i in $(seq 20); do
  fetch "roots-system-$i-$RANDOM"
  [ "$from" = "$slip_stage" ] && [ "$carried" = yes ] && break
  sleep 3
done
[ "$from" = "$slip_stage" ] && [ "$carried" = yes ] ||
  fail "under the system's store, the root installed: from '${from:-nowhere}', token $carried, want the stage $slip_stage (nc said: $last)"
echo "the system's store: the runner's server saw the request from $from"
timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" > /dev/null || fail "the quick toggle did not stop (the system's store)"

echo "== naive, the Chromium of the app's library: the roots it takes, then HTTP/2 and QUIC with the profile's own root"
# imported LINK AFTER: the naive link opened and its import confirmed; id: the profile it made, newer than AFTER
imported() {
  timeout 60 adb shell "am start -W -n $pkg/$pkg.ui.MainActivity -a android.intent.action.VIEW -d '$1'" > /dev/null || return 1
  tap "Import profile" || return 1
  id=
  for i in $(seq 20); do
    id=$(profile "$naive" last)
    [ -n "$id" ] && [ "$id" -gt "$2" ] && return 0
    sleep 2
  done
  return 1
}
# down: the VPN stopped, and a request of the emulator's comes from the runner's own address again
down() {
  timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" > /dev/null || fail "the quick toggle did not stop ($1)"
  for i in $(seq 10); do
    fetch "after-$1-$i-$RANDOM"
    [ -n "$from" ] && [ "$from" != "$naive_stage" ] && break
    sleep 3
  done
}
for i in $(seq 10); do
  fetch "before-naive-$i-$RANDOM"
  [ -n "$from" ] && [ "$from" != "$slip_stage" ] && break
  sleep 3
done
# roots VALUE: the app stopped, its trusted-roots setting written (configuration.db, a string) and the core's log
# begun anew: what the core says from here on is said under that setting
roots() {
  timeout 20 adb shell am force-stop "$pkg"
  timeout 20 adb shell "sqlite3 /data/data/$pkg/databases/configuration.db \"INSERT OR REPLACE INTO KeyValuePair (key, valueType, value) VALUES ('rootStore', 5, CAST('$1' AS BLOB));\"" ||
    fail "the root store setting not written (sqlite3 on the emulator)"
  timeout 20 adb shell "rm -f /data/data/$pkg/cache/neko.log"
}
# refused ID WHAT: the profile started, and a request that goes nowhere with Chromium's "cert authority invalid" in
# the core's log; then the VPN down
refused() {
  timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" --el profile "$1" > /dev/null ||
    fail "the quick toggle did not start (naive, $2)"
  local no=no
  for i in $(seq 10); do
    fetch "naive-refused-$i-$RANDOM"
    [ "$from" = "$naive_stage" ] && fail "naive, $2: the server's certificate was taken, the request came from $from"
    timeout 30 adb pull "/data/data/$pkg/cache/neko.log" "$out/neko-naive.log" > /dev/null 2>&1
    if [ -z "$from" ] && grep -q "cert authority invalid" "$out/neko-naive.log" 2> /dev/null; then no=yes; break; fi
    sleep 3
  done
  if [ "$no" != yes ]; then
    echo "--- what naive said"
    grep -a "naive" "$out/neko-naive.log" 2> /dev/null | tail -n 20 | cut -c1-400
    fail "naive, $2: no request lost to an unknown authority (the last from '${from:-nowhere}', nc said: $last)"
  fi
  echo "naive, $2: the request went nowhere, and the core's log says:"
  grep -m1 "cert authority invalid" "$out/neko-naive.log" | cut -c1-400
  down naive-refused
}
# The stage's root is on the phone as its user's. A profile that names no root has what the app's setting gives
# Chromium besides Chrome's list (libcore/naive_roots.go): nothing under Chrome's list, the phone's roots under the
# system's store, and those over HTTP/2 only: over QUIC Chromium wants a root of Chrome's list
roots chrome
imported "$(cat "$naive_links/h2.link")" 0 || fail "no naive profile from the link without a root"
bare=$id
echo "the naive profile without a root: $bare"
refused "$bare" "Chrome's list, no root in the profile"
roots system
timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" --el profile "$bare" > /dev/null ||
  fail "the quick toggle did not start (naive, the system's store)"
for i in $(seq 20); do
  fetch "naive-system-$i-$RANDOM"
  [ "$from" = "$naive_stage" ] && [ "$carried" = yes ] && break
  sleep 3
done
if [ "$from" != "$naive_stage" ] || [ "$carried" != yes ]; then
  timeout 30 adb pull "/data/data/$pkg/cache/neko.log" "$out/neko-naive.log" > /dev/null 2>&1
  echo "--- what naive said"
  grep -a "naive" "$out/neko-naive.log" 2> /dev/null | tail -n 20 | cut -c1-400
  fail "naive under the system's store, the root installed on the phone: from '${from:-nowhere}', token $carried, want the stage $naive_stage (nc said: $last)"
fi
echo "naive under the system's store, HTTP/2: the runner's server saw the request from $from"
down naive-system
# the same over QUIC: a root of the phone's is not one Chrome knows, and the request goes nowhere
roots system
imported "$(cat "$naive_links/quic.link")" "$bare" || fail "no naive profile from the QUIC link without a root"
bare_quic=$id
timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" --el profile "$bare_quic" > /dev/null ||
  fail "the quick toggle did not start (naive over QUIC, the system's store)"
lost=0
for i in $(seq 6); do
  fetch "naive-system-quic-$i-$RANDOM"
  [ "$from" = "$naive_stage" ] &&
    fail "naive over QUIC took a root of the phone's (the system's store): the request came from $from"
  [ -z "$from" ] && lost=$((lost + 1))
  sleep 3
done
timeout 30 adb pull "/data/data/$pkg/cache/neko.log" "$out/neko-naive.log" > /dev/null 2>&1
[ "$lost" -gt 0 ] || fail "naive over QUIC under the system's store: no request went through the VPN at all"
# lost in the QUIC handshake, not on the way: Chromium ends a session whose root it does not know as a protocol
# error (ERR_QUIC_PROTOCOL_ERROR; the verifier's own code stays in the connection's details), on some platforms as
# a failed handshake. That alone would also be the word for a stand whose QUIC is broken: the same server over
# QUIC with the root in the profile, further down, is what tells the two apart. A port nobody answers would time
# out and say none of these
quic_said=$(grep -a "outbound/naive" "$out/neko-naive.log" 2> /dev/null |
  grep -aiE "quic protocol error|quic handshake failed|quic cert root not known|cert authority invalid" | head -1 | cut -c1-400)
if [ -z "$quic_said" ]; then
  echo "--- what naive said"
  grep -a "naive" "$out/neko-naive.log" 2> /dev/null | tail -n 20 | cut -c1-400
  fail "naive over QUIC under the system's store: $lost requests lost, but not in the QUIC handshake"
fi
echo "naive under the system's store, QUIC: $lost requests of 6 went nowhere, none through, and the core's log says:"
echo "$quic_said"
down naive-system-quic
# and back: the list again, the file of the phone's roots emptied
roots chrome
refused "$bare" "back to Chrome's list"
id=$bare_quic
# the same server with its root in the profile: Chromium of the APK's library carries, over HTTP/2 and over QUIC
for carriage in h2 quic; do
  imported "$(cat "$naive_links/$carriage-cert.link")" "$id" || fail "no naive profile from the $carriage link"
  echo "the naive profile with the root, $carriage: $id"
  timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" --el profile "$id" > /dev/null ||
    fail "the quick toggle did not start (naive, $carriage)"
  for i in $(seq 20); do
    fetch "naive-$carriage-$i-$RANDOM"
    [ "$from" = "$naive_stage" ] && [ "$carried" = yes ] && break
    sleep 3
  done
  if [ "$from" != "$naive_stage" ] || [ "$carried" != yes ]; then
    timeout 30 adb pull "/data/data/$pkg/cache/neko.log" "$out/neko-naive.log" > /dev/null 2>&1
    echo "--- what naive said"
    grep -a "naive" "$out/neko-naive.log" 2> /dev/null | tail -n 20 | cut -c1-400
    fail "naive over $carriage with the profile's root: from '${from:-nowhere}', token $carried, want the stage $naive_stage (nc said: $last)"
  fi
  echo "naive over $carriage: the runner's server saw the request from $from"
  down "naive-$carriage"
done

echo "== naive against a public site: Chrome's list as the APK's Chromium carries it"
# a site that is no naive server: its chain is to pass (a certificate of a public CA, the roots the phone gave left
# out: the setting is Chrome's list), and the CONNECT to fail on something else than the certificate
roots chrome
imported "$(cat "$naive_links/public.link")" "$id" || fail "no naive profile from the link to a public site"
timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" --el profile "$id" > /dev/null ||
  fail "the quick toggle did not start (naive, a public site)"
said=
for i in $(seq 8); do
  fetch "naive-public-$i-$RANDOM"
  timeout 30 adb pull "/data/data/$pkg/cache/neko.log" "$out/neko-naive.log" > /dev/null 2>&1
  said=$(grep -a "outbound/naive" "$out/neko-naive.log" 2> /dev/null | grep -aiE "fail|error|status" | head -1 | cut -c1-400)
  [ -n "$said" ] && break
  sleep 3
done
grep -q "cert authority invalid" "$out/neko-naive.log" 2> /dev/null &&
  fail "naive does not take a public site's certificate under Chrome's list: $said"
if [ -n "$said" ]; then
  echo "naive against a public site: the certificate passed, and then: $said"
else
  echo "::warning::naive against a public site: the core's log has no word of naive's (nc said: $last)"
fi
down naive-public
# Chromium's own complaint about a file without roots: the library says nothing of it on Android
if timeout 60 adb logcat -d 2> /dev/null | grep -q "No CA certificates were found"; then
  fail "Chromium complains of the empty file of roots (No CA certificates were found)"
fi

echo "== naive's ClientHello as this phone sends it, against the one captured from Chrome"
# a profile to a port of the runner's that reads the first TLS record and closes: what Chromium of the APK's library
# sends first, on Android. No capture is a failure (naive did not connect at all); a difference from Chrome's is a
# warning, as in raycontrol-interop: the captured Chrome and the library's Chromium are of different versions
rm -f "$out/naive-hello.hex"
python3 -I "$here/hello.py" capture "$runner:18447" "$out/naive-hello.hex" &
hello_pid=$!
imported "$(cat "$naive_links/hello.link")" "$id" || fail "no naive profile from the ClientHello link"
timeout 60 adb shell am start -n "$pkg/$pkg.QuickToggleShortcut" --el profile "$id" > /dev/null ||
  fail "the quick toggle did not start (naive, the ClientHello)"
for i in $(seq 10); do
  fetch "naive-hello-$i-$RANDOM"
  [ -s "$out/naive-hello.hex" ] && break
  sleep 3
done
# the capture ends once it has its record (or after its two minutes without a connection)
wait "$hello_pid" || true
[ -s "$out/naive-hello.hex" ] || fail "no ClientHello captured from naive on the emulator"
echo "Chrome's ClientHello: $(cat "$naive_links/chrome-hello.source" 2> /dev/null)"
python3 -I "$here/hello.py" compare "$naive_links/chrome-hello.hex" "$out/naive-hello.hex" ||
  echo "::warning::the ClientHello of naive on the emulator could not be compared with Chrome's"
down naive-hello

finish 0
