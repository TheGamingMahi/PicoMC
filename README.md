# PicoMC

Low footprint Minecraft server optimization for **LeafMC 1.21.11**.
Designed for 1–5 players targeting 512mb–1gb RAM.

Other versions may load but won't achieve full performance gains (such as PaperMC, Purpur, or older and newer versions).

---

## What it does

- Backs up and replaces all server configs with optimized settings on first startup only
- Self-tunes view distance to highest value your hardware sustains above 19.0 TPS
- Two state mob AI — mobs beyond 16 blocks stop ticking entirely
- Blocks natural spawning of decorative and useless mobs
- `/pico pregenerate <radius>` — async chunk pregeneration

## Requirements

- LeafMC 1.21.11
- Java 21

## Installation

Drop `PicoMC.jar` into `/plugins` and start the server.
Original configs backed up to `picomc-backup-<timestamp>/` in server root on first run only.
**Restart the server after first start** — configs are applied on first launch but a restart is needed for all changes to fully take effect.

## Commands

| Command | Description |
|---------|-------------|
| `/pico status` | TPS, RAM, entities, view distance — color coded |
| `/pico pregenerate <radius>` | Async chunk pregeneration around spawn |
| `/pico reload` | Reloads the plugin |

Permission: `picomc.admin` (default: op)

## Roadmap

- [x] Config optimization on first startup
- [x] Self-tuning view distance
- [x] Two state isAware mob AI system
- [x] Mob follow range reduction
- [x] Selective spawn blocking
- [x] `/pico status` with dynamic color coding
- [x] `/pico pregenerate <radius>`
- [x] `/pico reload`
- [ ] Idle wandering for unaware mobs — currently freeze completely
- [ ] Advanced mob AI — goal selector manipulation via NMS
- [ ] PeekSync — client-side chunk caching
- [ ] Vertical chunk slicing — load only sections near player Y level
- [ ] Full config system — presets + CUSTOM preset for full control
- [ ] More features to be added

*Some planned features may change or be replaced by better approaches.*

## License

Source code is publicly viewable for transparency.
You may not redistribute, modify, or use this code in your own projects without permission.
Copyright (c) 2026 TheGamingMahi — All Rights Reserved.
