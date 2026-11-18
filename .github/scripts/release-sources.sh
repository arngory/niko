#!/bin/bash
# The release's Corresponding Source (GPL-3.0, section 6) in one archive, niko-<tag>-sources.tar.zst:
#   niko/        the app's tree at the release's commit (git archive), with libcore/vendor/: the Go modules the
#                library is linked from (go mod vendor), the prebuilt archives some of them carry taken out
#   sing-box/    the core, Niko's fork, at COMMIT_SING_BOX (buildScript/lib/core/get_source_env.sh)
#   gomobile/    the generator of the library's binding, at COMMIT_GOMOBILE (the same file)
#   slipstream/  the bundled slipstream client with its submodules, at COMMIT_SLIPSTREAM (buildScript/lib/slipstream)
#   SOURCES.md   what each part is, at which commit, and where the prebuilt parts are built from
# Run at the repository's root, at the release's commit, with git, Go (libcore/go.mod's toolchain), python3 and zstd.
# Nothing of what it fetches runs: git and go mod vendor only copy. release.yml (job sources) attaches the archive to
# the release; ci.yml makes it when this script or a pin changes.
#   release-sources.sh TAG OUT_DIR
set -euo pipefail
tag=$1
out=$2
name="niko-$tag-sources"
work=$(mktemp -d)
# the module cache's copies are read-only
trap 'chmod -R u+w "$work" 2> /dev/null; rm -rf "$work"' EXIT
dst="$work/$name"
mkdir -p "$dst" "$out"
# shellcheck source=buildScript/lib/core/get_source_env.sh
. buildScript/lib/core/get_source_env.sh
# shellcheck source=buildScript/lib/slipstream/env.sh
. buildScript/lib/slipstream/env.sh
commit=$(git rev-parse HEAD)

# fetch URL COMMIT DIR: a repository at a commit, with its submodules, without history
fetch() {
  git init -q "$3"
  git -C "$3" remote add origin "$1"
  git -C "$3" fetch -q --depth 1 origin "$2"
  git -C "$3" checkout -q FETCH_HEAD
  git -C "$3" submodule update -q --init --recursive
}

echo "== the parts"
git archive --format=tar --prefix=niko/ "$commit" | tar -x -C "$dst"
fetch "$REPOSITORY_SING_BOX" "$COMMIT_SING_BOX" "$dst/sing-box"
fetch https://github.com/MatsuriDayo/gomobile.git "$COMMIT_GOMOBILE" "$dst/gomobile"
fetch "$REPOSITORY_SLIPSTREAM" "$COMMIT_SLIPSTREAM" "$dst/slipstream"
find "$dst" -name .git -prune -exec rm -rf {} +

echo "== the Go modules"
# libcore's go.mod points at ../../sing-box: sing-box/ here
(cd "$dst/niko/libcore" && go mod vendor)
# the prebuilt code some modules carry (libcronet's archives, one per platform; sing-tun's wintun.dll): not a
# source; SOURCES.md names each and, for libcronet, what it is built from
vendor="$dst/niko/libcore/vendor"
prebuilt=$(cd "$vendor" && find . -type f \( -name '*.a' -o -name '*.so' -o -name '*.dll' -o -name '*.dylib' -o -name '*.lib' \) |
  sed 's#^\./##' | sort)
(cd "$vendor" && printf '%s\n' "$prebuilt" | { grep . || true; } | while read -r f; do rm -f "$f"; done)

# libcronet's source: the cronet-go module libcore's go.mod takes (its replacement), at its commit; its submodule
# naiveproxy holds Chromium's source
# (the module's own graph, not vendor/, which the go command would read now that it is there)
cronet_mod=$(cd "$dst/niko/libcore" && GOFLAGS=-mod=mod go list -m \
  -f '{{with .Replace}}{{.Path}}@{{.Version}}{{else}}{{.Path}}@{{.Version}}{{end}}' github.com/sagernet/cronet-go)
cronet_url=?
cronet_hash=?
read -r cronet_url cronet_hash < <(cd "$dst/niko/libcore" && GOFLAGS=-mod=mod go mod download -json "$cronet_mod" |
  python3 -I -c 'import json, sys; o = json.load(sys.stdin).get("Origin") or {}; print(o.get("URL", "?"), o.get("Hash", "?"))') ||
  true
# read empties both when the module's origin did not come
cronet_url=${cronet_url:-?}
cronet_hash=${cronet_hash:-?}
naive_url=?
naive_hash=?
if [ "$cronet_hash" != "?" ] && [ "$cronet_url" != "?" ]; then
  git init -q "$work/cronet"
  git -C "$work/cronet" remote add origin "$cronet_url"
  # the tree only: the gitlink of the submodule, and .gitmodules fetched on demand
  if git -C "$work/cronet" fetch -q --depth 1 --filter=blob:none origin "$cronet_hash"; then
    naive_hash=$(git -C "$work/cronet" ls-tree FETCH_HEAD naiveproxy | awk '$2 == "commit" { print $3 }')
    naive_url=$(git -C "$work/cronet" config --blob FETCH_HEAD:.gitmodules --get submodule.naiveproxy.url || true)
  fi
  naive_hash=${naive_hash:-?}
  naive_url=${naive_url:-?}
fi
[ "$naive_hash" != "?" ] || echo "::warning::no commit of naiveproxy for libcronet ($cronet_mod): SOURCES.md says so"
# whose each archive is: go mod vendor copies the module of every platform, and only those libcore's go.mod replaces
# (the APKs' android ones, the desktop's) come from that build; the others, which the APKs do not link, are upstream's
# at the version modules.txt names
cronet_ours=
cronet_theirs=
while read -r f; do
  [ -n "$f" ] || continue
  mod=$(cut -d/ -f1-5 <<< "$f")
  line=$(grep -m1 "^# $mod " "$vendor/modules.txt" || true)
  case $line in
    *" => "*) cronet_ours+="$f"$'\n' ;;
    "") cronet_theirs+="\`$f\` (not in \`modules.txt\`)"$'\n' ;;
    *) cronet_theirs+="\`$f\` (\`$(awk '{ print $3 }' <<< "$line")\`)"$'\n' ;;
  esac
done < <(printf '%s\n' "$prebuilt" | grep '^github.com/sagernet/cronet-go/' || true)
toolchain=$(sed -n 's/^toolchain //p' "$dst/niko/libcore/go.mod")

echo "== SOURCES.md"
{
  echo "# Niko $tag: the sources"
  echo
  echo "The Corresponding Source of the APKs of Niko $tag (GPL-3.0, section 6), at the commits the release was built"
  echo "from. How to build: \`niko/README.md\` and \`niko/.github/workflows/build.yml\` (Go ${toolchain:-of libcore/go.mod},"
  echo "the Android NDK the workflow names)."
  echo
  echo "| Folder | What | From |"
  echo "|---|---|---|"
  echo "| \`niko/\` | the app, its library libcore, the build's scripts | https://github.com/arngory/niko at \`$commit\` |"
  echo "| \`niko/libcore/vendor/\` | the Go modules libcore is linked from (\`go mod vendor\`; \`modules.txt\` lists them, \`libcore/go.sum\` holds their hashes) | their repositories |"
  echo "| \`sing-box/\` | the core, Niko's fork; libcore's \`go.mod\` points at it (\`../../sing-box\`) | ${REPOSITORY_SING_BOX%.git} at \`$COMMIT_SING_BOX\` |"
  echo "| \`gomobile/\` | the generator of the library's Java binding | https://github.com/MatsuriDayo/gomobile at \`$COMMIT_GOMOBILE\` |"
  echo "| \`slipstream/\` | the slipstream client the APK carries, with its submodules | ${REPOSITORY_SLIPSTREAM%.git} at \`$COMMIT_SLIPSTREAM\` |"
  echo
  echo "## Prebuilt, not here"
  echo
  echo "- **libcronet**, the network stack of naive (Chromium's): the modules of \`github.com/sagernet/cronet-go/lib/\`"
  echo "  carry it built, and \`vendor/\` has their archives taken out. The APKs link the android ones."
  echo "  - The modules libcore's \`go.mod\` replaces, built by $cronet_url at \`$cronet_hash\` (\`$cronet_mod\`), whose"
  echo "    submodule \`naiveproxy\` holds Chromium's source: ${naive_url%.git} at \`$naive_hash\`:"
  printf '%s' "$cronet_ours" | grep . | sed 's/^/    - `/; s/$/`/' || echo "    - (none)"
  if [ -n "$cronet_theirs" ]; then
    echo "  - The other platforms' modules, which the APKs do not link (\`go mod vendor\` copies every platform's):"
    echo "    upstream's, github.com/sagernet/cronet-go at the version each names:"
    printf '%s' "$cronet_theirs" | sed 's/^/    - /'
  fi
  others=$(printf '%s\n' "$prebuilt" | grep . | grep -v '^github.com/sagernet/cronet-go/' || true)
  if [ -n "$others" ]; then
    echo "- **Other prebuilt files** the modules carry, taken out of \`vendor/\` too (their modules' own repositories"
    echo "  say where they come from):"
    printf '%s\n' "$others" | sed 's/^/  - `/; s/$/`/'
  fi
  echo "- **OpenSSL** for the slipstream client: \`slipstream/.github/scripts/android-openssl.sh\` downloads its release"
  echo "  and checks its hash."
  echo "- **The rule databases** (geoip.db, geosite.db) and the panel (yacd): data, downloaded by"
  echo "  \`niko/buildScript/lib/assets.sh\` from their releases."
  echo
  echo "Their licenses: \`niko/NOTICE\`."
} > "$dst/SOURCES.md"
cat "$dst/SOURCES.md"

echo "== the archive"
# the same bytes from the same commit: names sorted, one owner, the commit's time
tar --sort=name --owner=0 --group=0 --numeric-owner --mtime="@$(git log -1 --format=%ct "$commit")" \
  -C "$work" -cf - "$name" | zstd -q -T0 -19 -o "$out/$name.tar.zst"
ls -la "$out/$name.tar.zst"
sha256sum "$out/$name.tar.zst"
