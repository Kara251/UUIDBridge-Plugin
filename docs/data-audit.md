# Player Data Migration Audit

UUIDBridge Plugin migrates identity references, not player names. Online-mode
and offline-mode usually keep the same visible username, so name-only data is
reported or left alone unless a future dedicated username migration tool exists.

## Covered Now

- Player file names: `playerdata`, `advancements`, and `stats`.
- Server identity lists: `whitelist.json`, `ops.json`, `banned-players.json`,
  and `usercache.json`.
- World NBT and region data: `level.dat`, `data/*.dat`, `region/*.mca`,
  `entities/*.mca`, and `poi/*.mca`.
- Common UUID forms: dashed strings, undashed strings, int arrays, long arrays,
  and `UUIDMost` / `UUIDLeast` pairs.
- Common ownership references in entities, block entities, player data, and
  plugin-like NBT: owners, trusted players, anger/love causes, conversion
  players, projectile owner/thrower, leash data, villager gossip targets,
  custom boss event players, raid saved data, scoreboard UUID values, player
  head profiles, 1.21 item component profiles, Brain memories, and
  `owner_uuid`-style fields.
- Singleplayer transfer from `level.dat` `Data.Player`.
- Writable plugin presets documented in [plugin support](plugin-support.md).
- Extra administrator targets declared by `uuidbridge/targets.json`.

## Important Gaps

- Residence, WorldGuard, economy, quest, grave, shop, home, warp, backpack, and
  other plugin formats need real fixtures before they become writable presets.
- Database-backed data such as SQLite, H2, MySQL, LevelDB, and plugin-private
  transaction stores is not rewritten.
- Unknown plugin JSON, NBT, region, and YAML files are report-only under
  `--plugins`.
- Scoreboard entries keyed by player name are not rewritten.
- Region migration reads a whole `.mca` file before writing changed chunks.
  This is acceptable for the current safety profile but remains a performance
  area for very large worlds.

## Safety Principles

- No Mojang network lookup during migration. Offline-to-online migrations
  require an explicit mapping file.
- No fuzzy text replacement. UUIDBridge replaces mapped UUID shapes only.
- No database writes without a format-specific adapter and real fixtures.
- No broad plugin writes without a documented writable preset.
