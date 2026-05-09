# UUIDBridge Plugin Documentation

UUIDBridge Plugin helps Minecraft server administrators migrate between
online-mode and offline-mode UUIDs on Bukkit-compatible servers.

This repository is the plugin edition. It targets Paper/Purpur/Spigot/Bukkit
1.21.1 with Java 21 and uses `plugin.yml` with `load: STARTUP` so pending
migrations run before normal play.

## Guides

- [Operations guide](operations.md)
- [Production runbook](production-runbook.md)
- [Vanilla coverage](vanilla-coverage.md)
- [Plugin support matrix](plugin-support.md)
- [Extra migration targets](targets.md)
- [Player data migration audit](data-audit.md)
- [Development guide](development.md)
- [Release checklist](release-checklist.md)
- [Changelog](changelog.md)

## Important Defaults

- Plugin data is not migrated unless `--plugins` or explicit `--targets` is
  used.
- `--plugins` only writes documented writable presets. Unknown plugin files are
  report-only.
- Databases are never rewritten in this release.
- Failed startup migration writes reports and fails closed.
