# QUIC through stream profiles: results

Run [42514430233](https://github.com/arngory/niko/actions/runs/42514430233) of `quic-experiment.yml`, 2026-11-08.

Setup: one runner, the Niko core (sing-box fork `957c8cf`) as both server and client. netem on the leg between
client and node: 40 ms each way (80 ms round trip), 50 Mbit/s, the loss shown. The congestion control is the
kernel's, on both ends; raycontrol nodes run bbr.

Columns:
- **first**: a fresh connection's first answer.
- **bulk**: 4 MiB on that connection.
- **page**: a fresh connection with 24 × 64 KiB requests at once.

Every cell is the median of 3 runs.

## bbr (raycontrol nodes)

| path | loss | first, ms | bulk, Mbit/s | page, ms |
|---|---|---|---|---|
| TCP through REALITY | 0 % | 327 | 30.7 | 915 |
| QUIC through REALITY (XUDP) | 0 % | 329 | 11.6 | 1569 |
| TCP through naive | 0 % | 183 | 41.9 | 433 |
| QUIC through naive (UoT) | 0 % | 169 | 25.5 | 752 |
| TCP through REALITY | 1 % | 327 | 26.9 | 1016 |
| QUIC through REALITY (XUDP) | 1 % | 411 | 6.8 | 2431 |
| TCP through naive | 1 % | 190 | 39.7 | 438 |
| QUIC through naive (UoT) | 1 % | 169 | 7.9 | 1802 |
| TCP through REALITY | 3 % | 328 | 25.1 | 1195 |
| QUIC through REALITY (XUDP) | 3 % | 432 | 5.2 | 2861 |
| TCP through naive | 3 % | 247 | 39.0 | 507 |
| QUIC through naive (UoT) | 3 % | 170 | 4.4 | 4991 |

## cubic

| path | loss | first, ms | bulk, Mbit/s | page, ms |
|---|---|---|---|---|
| TCP through REALITY | 0 % | 327 | 24.8 | 998 |
| QUIC through REALITY (XUDP) | 0 % | 327 | 27.0 | 980 |
| TCP through naive | 0 % | 167 | 33.9 | 474 |
| QUIC through naive (UoT) | 0 % | 169 | 25.4 | 746 |
| TCP through REALITY | 1 % | 327 | 5.7 | 1114 |
| QUIC through REALITY (XUDP) | 1 % | 329 | 2.7 | 5640 |
| TCP through naive | 1 % | 166 | 2.5 | 6229 |
| QUIC through naive (UoT) | 1 % | 210 | 1.8 | 6463 |
| TCP through REALITY | 3 % | 328 | 1.6 | 10272 |
| QUIC through REALITY (XUDP) | 3 % | 330 | 1.0 | 8860 |
| TCP through naive | 3 % | 330 | 1.2 | 10809 |
| QUIC through naive (UoT) | 3 % | 247 | 1.0 | 16177 |

Straight paths, without a tunnel, are in the run's summary. Their QUIC uses quic-go's own loss-based congestion
control, so it is no stand-in for Hysteria2.

## What it says

- **bbr (the nodes' setting).** On a stream profile QUIC loses to TCP everywhere.
  - Even without loss, bulk is 1.6–2.6× slower and the page 1.7× slower.
  - With loss TCP holds, because the tunnel's bbr does not back off for it. QUIC drops to 4–9× slower in bulk and 2–10× slower on the page.
- **cubic.** Without loss the two are about even. With loss everything collapses, and QUIC is no better; one exception is REALITY at 3 % on the page.
- **Why.** QUIC's congestion control sits on top of the tunnel's TCP:
  - every TCP retransmission arrives as a delay spike;
  - QUIC reads those spikes as congestion;
  - one stalled tunnel connection stalls every QUIC stream in it.
- **Prior art.** Xray's VLESS client refuses UDP/443 under Vision by default for the same reason (`XTLS rejected UDP/443 traffic`; the `xtls-rprx-vision-udp443` flow lets it through).
- **Consequence.** "QUIC to TCP on stream profiles" (`refuseQuicOverStreams`) makes browsers use TCP at once and gains this difference. Its cost is one refused QUIC attempt per site, which Chrome remembers.
