# Niko documentation

English | [Русский](README.ru.md)

Most documents are in Russian; the routing guide is in both languages. A document says how things are now; what
changed and when is in the release notes and in git.

## Using Niko

- [Routing rules](guide/routing.md)
- [Port knocking](guide/port-knocking.md)
- [Checking Niko on a real node (VPS)](guide/vps-check.md)

## How it works

- [TLS fingerprint: keeping it Chrome's](design/fingerprint.md)
- [Trusted roots: which root store each part takes](design/trust.md)
- [Niko for the computer](design/desktop.md) and [its stage 3: `nikod` and `NikoService`](design/desktop-stage3.md)

## Contracts

- [The computer's requirements, from the screen to the core](spec/desktop-requirements.md)
- [Statistics for the last hour, the client's side](spec/stats-client.md)
- The node's protocols (subscription, API, probe, relays, updates and the rest) are specified in raycontrol:
  [docs/spec](https://github.com/arngory/raycontrol/tree/main/docs/spec)

## Visual design

- [Every function against the design](ui/functions.md)
- [One design on every platform](ui/platforms.md)

## Maintaining

- [Releasing and signing](guide/release.md)
- [CONTRIBUTING.md](../CONTRIBUTING.md): branches, pull requests, messages, pins
- [Decisions](decisions.md) and [what is ahead](roadmap.md)

## Research (dated, kept as written)

- [Anti-DPI: TCP and UDP](research/anti-dpi.md), 2026-11-01
- [Review: security, blocking, performance, quality](research/review-2026-11.md), 2026-11
- [The client's answers to raycontrol's handoff notes](research/raycontrol-handoff.md), 2026-11
- [Keepalive cost at rest](research/keepalive.md), 2026-11-17
- [Memory on the emulator](research/memory.md), 2026-11
- [Routing by app: lists, the node, updates](research/routing-apps.md), 2026-11-16
