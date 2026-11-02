- niko-server front: DNS multiplexer on UDP 53 (zones → backends, REFUSED for other names)
- niko-server backend: dnstt and MasterDnsVPN (AES-GCM only) into the inner stage; DNS queries through the tunnel are refused, the node resolves nothing itself
- niko-server check: exit codes 0/1/2 and `--json`
- Config schema 1, IPC version 1 (docs/server/CONFIG.md, IPC.md)

---

- niko-server front: DNS-мультиплексор на UDP 53 (зоны → бэкенды, REFUSED для чужих имён)
- niko-server backend: dnstt и MasterDnsVPN (только AES-GCM) во внутреннюю ступень; DNS-запросы через туннель отклоняются, нода сама ничего не резолвит
- niko-server check: коды выхода 0/1/2 и `--json`
- Схема конфигов 1, IPC версии 1 (docs/server/CONFIG.md, IPC.md)

`SHA256SUMS` / SHA-256 next to the binaries.
