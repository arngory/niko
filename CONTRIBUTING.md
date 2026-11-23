# Contributing

English | [Русский](CONTRIBUTING.ru.md)

In short: a short branch from `main` → a pull request → a green "CI result" check → a squash merge. The owner makes a
release with their tag. These rules are shared by the client and the server: the node's repositories (raycontrol,
niko-server and the forks) keep the same text, except for the section "In this repository".

## Branches and pull requests

- `main` is protected: changes reach it only through a pull request with green "CI result" (`ci.yml`), "Commit
  messages" and "Secrets" (`checks.yml`); no force-push, no deletion. Only the owner can bypass the rule.
- A task gets its own short branch from a fresh `main`: `<area>/<short>`, for example `app/keepalive-obfs`,
  `ci/commit-checks`, `docs/structure`. A branch lives for days, not weeks, and is deleted after the merge.
- The pull request is opened right away, as a draft if needed: the checks run on it, not on a push to the branch.
- The merge is a **squash**: the pull request's title and description become the commit message in `main`, so they
  follow the same rules as a commit message (below). GitHub appends " (#N)" to the title: in the merge dialog,
  replace it with " (arngory/niko#N)". Several independent changes are several pull requests; rebase instead of
  squash only when the commits inside make sense one by one.
- The author merges once the checks are green. The owner sees every pull request and can stop or revert it.
- The description template is `.github/pull_request_template.md`: what and why, how it was checked, what changes for
  the other side.

## Commit messages

```
<what changed, in English, in the present tense, up to 72 characters>

<why and how, lines up to ~110 characters; specifications by name: lan-v1 §3, api-v1.md §2>
```

- **No bare `#N`.** In a fork GitHub links `#N` to an issue of the parent repository: that is how Niko's commits
  ended up in other people's issues in MatsuriDayo/NekoBoxForAndroid. Write the full form `arngory/<repository>#N` or
  "task N". "Commit messages" (`.github/scripts/bare-refs.py`) checks the commits, the pull request's title and its
  description, again on every edit of them.
- Closing words (`fixes`, `closes`) only with the full form, and only where Issues are on.
- No `Co-Authored-By` and `Claude-Session` trailers (the owner's decision of 2026-11-18): who wrote a commit is its
  author. Commits made before that day keep them.

## Secrets and data

- Keys, passwords, tokens and the addresses of real nodes never go into the repository. "Secrets" runs gitleaks over
  the pull request's commits. A false finding (a test's one-off key) goes into the allowlist in `.gitleaks.toml`
  (there is an example there) with a comment on where the key comes from, in a pull request of its own: the check
  takes `.gitleaks.toml` and `.gitleaksignore` from `main`, so the exception works from the next pull request on.
  `gitleaks:allow` comments are not taken.
- The "Commit messages" script and the rules and exceptions of "Secrets" come from `main`; the Go version for gitleaks
  is pinned in `checks.yml`. The workflows themselves run as the pull request has them: a pull request that changes
  `.github/workflows/` changes its own checks too. The owner merges such a pull request from another contributor.
- Release keys live only in the GitHub environment `release` (`docs/guide/release.md`). `*.keystore`, `*.jks`,
  `*.b64`, `local.properties` and `docs/local/` are in `.gitignore`.
- Addresses in tests and examples are documentation ones only: `192.0.2.0/24`, `198.51.100.0/24`, `203.0.113.0/24`,
  `2001:db8::/32`, the domains `*.example` and `*.test`. Data captured from nodes comes only from CI's one-off nodes.
- Private links (sessions, canvases, workspaces) are not put into the repository.

## Pins

Everything taken from another repository is pinned to an exact version: a commit, a tag, a Go pseudo-version or a
file's SHA-256.

- A pin changes in a pull request of its own, titled like `Pin raycontrol v0.31.2 (…)`: what, to what and why.
- Pin only a tag or a commit of a lasting branch (`main`, `niko` in the forks), never a temporary branch: it can be
  deleted.
- Before a server release is pinned, interop CI runs against it (a manual run with the new value, or a pull request
  with it).

## Dependency licenses

Everything that goes into the APK is listed in `NOTICE` with its license, and the license texts are in the APK itself
(`assets/licenses/`).

- The core's Go modules are gathered by the libcore build (`buildScript/lib/core/licenses.py`). A module with no
  license file, or with a license the script does not know (GPL-2.0, AGPL, BSD-4 and the like), stops the build: such
  a dependency is discussed first.
- The Android libraries are listed in `app/src/main/assets/licenses/components.json`. `app:checkLicenses` fails in CI
  on a module no component covers, on a pattern with no module and on a `META-INF/NOTICE` nobody quoted. A new
  library goes there too, with its license text in `texts/` and a line in `NOTICE`.
- The native parts, data, fonts and icons are described there by hand: with a new version of slipstream, OpenSSL or
  Chromium the texts are checked again.
- Release sources: `release.yml` attaches `niko-<tag>-sources.tar.zst` (`.github/scripts/release-sources.sh`). A new
  outside repository the APK is built from goes into that script too; CI makes the archive again when a pull request
  changes the script or a pin.

## Releasing

1. `niko.properties`: `VERSION_NAME` (semver) and `VERSION_CODE`, which only grows.
2. `.github/release-notes/vX.Y.Z.md`: in English, then in Russian, with no bare `#N`. A required update also needs
   `vX.Y.Z.update.json` (`docs/guide/release.md` §6).
3. The owner tags a commit of `main` with green CI (or runs Release by hand and publishes the draft) and approves the
   signing. In detail: `docs/guide/release.md`.

## In this repository

| What | Where |
|---|---|
| the Android app | `app/` |
| snapshots of the Niko theme's parts (Paparazzi) | goldens in `app/src/test/snapshots/`, CI compares them with `app/src/test/java/io/niko/ui/NikoSnapshotTest.kt`. New goldens: delete that folder in the PR, CI records them into the artifact `paparazzi-goldens`, commit its contents |
| the app's Go library | `libcore/`; tests as in `.github/workflows/build.yml` (libcore's test step: `-ldflags=-checklinkname=0` and the `TAGS` tags) |
| the desktop window (Flutter) | the repository [arngory/niko-desktop](https://github.com/arngory/niko-desktop), which pins this one by commit; here: the daemon `nikod` and its Go, with its own check `desktop.yml` |
| the shared protocols of the window and the daemon | `shared/proto/` (Go: `shared/tool/gen_proto_go.sh`, Dart: `tool/gen_proto.sh` in arngory/niko-desktop); the window's tokens and icons are written by `shared/tool/gen_tokens.py` and `gen_icons.py` with `--desktop <the window's checkout>` |
| the core | the fork `arngory/sing-box`, pinned in `buildScript/lib/core/get_source_env.sh` |
| building and signing | `.github/workflows/build.yml`, `docs/guide/release.md` |
| documents | [`docs/`](docs/README.md): guides, how it works, contracts, decisions, plans |

The node (raycontrol, niko-server) lives in its own repositories. Client–node compatibility is held by raycontrol's
specifications (`docs/spec/`) and by interop CI here (`raycontrol-interop.yml`, `niko-server-interop.yml`,
`xray-interop.yml`).
