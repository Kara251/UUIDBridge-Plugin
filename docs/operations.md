# Operations Guide

UUIDBridge Plugin is designed for production Minecraft servers where operators
have a console, file manager, restart control, and a maintenance window.

## Standard Workflow

1. Stop the server and copy the entire server directory as an external backup.
2. Install the UUIDBridge plugin jar in `plugins/`.
3. Start the server once and run `/uuidbridge status`.
4. Upload a mapping file to the server root when migrating from offline-mode
   back to online-mode.
5. Run `/uuidbridge scan <online-to-offline|offline-to-online>`.
6. Run `/uuidbridge plan <direction> [--mapping <file>]` and review the plan,
   coverage summary, conflicts, missing mappings, and skipped targets.
7. Run `/uuidbridge apply <planId> --confirm`.
8. Restart the server. UUIDBridge applies the pending plan during plugin
   startup before the world is available for normal play.
9. Run `/uuidbridge status` and inspect the report under `uuidbridge/reports/`.

Do not force-stop the server during startup migration unless the process is
already stuck. If apply fails, UUIDBridge attempts automatic rollback before
failing startup.

## Commands

```sh
/uuidbridge scan <online-to-offline|offline-to-online> [--mapping <file>] [--targets <file>] [--singleplayer-name <name>] [--plugins]
/uuidbridge plan <online-to-offline|offline-to-online> [--mapping <file>] [--targets <file>] [--singleplayer-name <name>] [--plugins]
/uuidbridge apply <planId> --confirm
/uuidbridge status
/uuidbridge rollback <planId> --confirm
/uuidbridge cancel <planId>
```

Permission: `uuidbridge.admin`, default `op`. Commands work from the console
and from administrator players.

## Mapping Files

CSV:

```csv
name,onlineUuid,offlineUuid
Alice,11111111-2222-3333-4444-555555555555,aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee
```

JSON:

```json
[
  {
    "name": "Alice",
    "onlineUuid": "11111111-2222-3333-4444-555555555555",
    "offlineUuid": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
  }
]
```

For online-mode to offline-mode, `offlineUuid` may be omitted because
UUIDBridge derives it from the player name. For offline-mode to online-mode,
provide a mapping file. UUIDBridge does not query Mojang during migration.

## Plugin Data

Default scans cover vanilla server and world data only.

Use `--plugins` only after reading the [plugin support matrix](plugin-support.md).
Writable plugin presets are intentionally narrow. Unknown plugin JSON, NBT,
region, YAML, cache, backup, and database-looking files are reported but not
modified.

Use `--targets <file>` for explicit administrator-owned targets outside the
standard scan. See [extra migration targets](targets.md).

## Rollback Workflow

1. Confirm `uuidbridge/backups/<planId>/manifest.json` exists.
2. Run `/uuidbridge rollback <planId> --confirm`.
3. Restart the server.
4. Inspect `uuidbridge/reports/<planId>-rollback.json`.

Rollback validates backup size and SHA-256 before restore. Before overwriting
current files during manual rollback, UUIDBridge saves current files under
`uuidbridge/backups/<planId>/rollback-current/`.

## Runtime Files

Runtime files are written under `uuidbridge/` in the server root:

- `plans/<planId>.json`
- `pending.json`
- `startup-failure.json`
- `migration.lock`
- `reports/<planId>.json`
- `reports/<planId>-rollback.json`
- `backups/<planId>/manifest.json`
- `backups/<planId>/rollback-current/`

On successful apply or rollback, `pending.json` and `migration.lock` are
removed. If apply fails and automatic rollback succeeds, `pending.json` remains
so the operator can inspect the report before retrying or canceling. If rollback
fails, both `pending.json` and `migration.lock` remain.
