# Plugin Support Matrix

`--plugins` is conservative by design. It does not mean "rewrite every plugin file."

## Writable Presets

These paths are migrated when `--plugins` is enabled:

| Plugin family | Path | Formats | UUID filename rename |
| --- | --- | --- | --- |
| EssentialsX | `plugins/Essentials/userdata/*.yml` | YAML text | Yes |
| LuckPerms file storage | `plugins/LuckPerms/yaml-storage/users/*.yml` | YAML text | Yes |
| LuckPerms file storage | `plugins/LuckPerms/json-storage/users/*.json` | JSON | Yes |

YAML text migration is boundary-aware text replacement. It preserves file formatting and comments, and it only replaces mapped dashed UUIDs outside `[0-9A-Fa-f-]` tokens and mapped undashed UUIDs outside `[0-9A-Fa-f]` tokens.

## Report-Only Candidates

These are discovered for visibility but are not written in the conservative production profile:

- Residence-style YAML under `plugins/Residence/Save/`
- WorldGuard-style YAML under `plugins/WorldGuard/worlds/`
- Generic economy YAML under `plugins/Economy/`
- Unknown plugin JSON, NBT, region, YAML, and database-looking files

Use `uuidbridge/targets.json` or `--targets <file>` only after manually verifying a specific file should be migrated.

## Always Unsupported

UUIDBridge does not write plugin databases or operational files:

- SQLite, H2, MySQL, LevelDB
- jars, logs, temp files
- cache, backup, library, updater, and UUIDBridge control directories
