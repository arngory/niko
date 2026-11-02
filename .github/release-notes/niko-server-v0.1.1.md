- Fix: MasterDnsVPN DNS queries through the tunnel are refused; v0.1.0 resolved them at 1.1.1.1 from the node, past the inner stage — do not use v0.1.0
- Fix: the front could crash on answers that arrived before it finished starting
- Includes everything of v0.1.0: DNS front on UDP 53, dnstt and MasterDnsVPN (AES-GCM only) backends into the inner stage, check with exit codes and --json

---

- Исправлено: DNS-запросы MasterDnsVPN через туннель отклоняются; v0.1.0 резолвила их через 1.1.1.1 прямо с ноды, мимо внутренней ступени — v0.1.0 не использовать
- Исправлено: фронт мог упасть на ответах, пришедших до конца его запуска
- Всё из v0.1.0: DNS-фронт на UDP 53, бэкенды dnstt и MasterDnsVPN (только AES-GCM) во внутреннюю ступень, check с кодами выхода и --json

`SHA256SUMS` / SHA-256 next to the binaries.
