# Vanilla Coverage

This page defines the acceptance boundary for vanilla migration in
UUIDBridge Plugin. The plugin migrates player UUID identity references persisted
by Minecraft 1.21.1 vanilla data. It does not migrate player names.

## Covered Files

- Server root: `whitelist.json`, `ops.json`, `banned-players.json`,
  `usercache.json`.
- Player files: `world/playerdata/*.dat`, `world/advancements/*.json`,
  `world/stats/*.json`, including UUID-based file renames.
- World root: `world/level.dat`, `world/level.dat_old`.
- Saved data: `world/data/*.dat`, including scoreboard, raid, map, and command
  storage data.
- Region files: `region/*.mca`, `entities/*.mca`, `poi/*.mca`, and matching
  files in dimension directories such as `DIM-1/region/*.mca` and
  `DIM1/entities/*.mca`.

The active world directory is read from `server.properties` `level-name`; it
defaults to `world`.

## Covered UUID Shapes

- Dashed UUID strings.
- Undashed UUID strings.
- NBT `int[4]` UUIDs.
- NBT `long[2]` UUIDs.
- `UUIDMost` / `UUIDLeast` and similar `*Most` / `*Least` long pairs.

## Covered Semantics

- Entity, block entity, and player NBT owner, trusted player, anger/love cause,
  conversion player, projectile owner/thrower, and leash UUID references.
- Ownership data for tameable or trusted entities such as wolves, cats,
  parrots, horses, and foxes.
- Villager gossip targets.
- BossBar player lists.
- Raid heroes and raid saved data.
- UUID-shaped values in scoreboard data.
- Player head owner profiles and 1.21 item component profile UUIDs.
- Player references stored in Brain memories.
- Singleplayer `level.dat` `Data.Player`: copied to
  `playerdata/<targetUuid>.dat` when exactly one mapping exists; use
  `--singleplayer-name <name>` when multiple mappings exist.

## Explicitly Out Of Scope

- Scoreboard team member names, score owner names, and other player-name fields.
- `banned-ips.json`.
- Logs, crash reports, backups, plugins caches, build output, and `.git`.
- SQLite, H2, MySQL, LevelDB, and other databases.
- Unknown plugin data unless it is a documented writable preset or an explicit
  administrator target.
