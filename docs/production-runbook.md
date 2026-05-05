# UUIDBridge Production Runbook

UUIDBridge is a startup-time migration tool. Treat every apply or rollback as a maintenance operation, not as a live command.

## Before Running

- Stop the server and take a full filesystem backup of the server root.
- Verify the plugin jar was built with `./gradlew --no-daemon build`.
- Verify the Paper smoke task passes for the release jar: `./gradlew --no-daemon smokePaperServer`.
- Keep database-backed plugin data out of scope. UUIDBridge reports SQLite, H2, MySQL, LevelDB, cache, log, jar, and backup files but does not write them.

## Standard Migration

1. Start the server with UUIDBridge installed and no players online.
2. Run `/uuidbridge scan online-to-offline` or `/uuidbridge scan offline-to-online --mapping <file>`.
3. Run `/uuidbridge plan <direction>` after reviewing coverage and skipped targets.
4. Run `/uuidbridge apply <planId> --confirm`.
5. Restart the server. Pending apply runs during plugin startup before the world is available for play.
6. Run `/uuidbridge status` and inspect `uuidbridge/reports/<planId>.json`.

Use `--plugins` only when the plugin support matrix says the target path is writable. Unknown plugin files are reported, not migrated. Use `--targets <file>` for explicit administrator-owned targets.

## Rollback

1. Confirm `uuidbridge/backups/<planId>/manifest.json` exists.
2. Run `/uuidbridge rollback <planId> --confirm`.
3. Restart the server.
4. Run `/uuidbridge status` and inspect `uuidbridge/reports/<planId>-rollback.json`.

Rollback validates backup size and SHA-256 before restore. If rollback fails, UUIDBridge keeps `uuidbridge/pending.json` and `uuidbridge/migration.lock` so the server owner can inspect the failure state.

## Startup Failure Recovery

If pending apply or rollback fails during startup, UUIDBridge writes failure state, requests server shutdown, and then forces JVM exit if Paper continues booting. This prevents a failed migration from reaching a playable `Done` state.

UUIDBridge writes:

- `uuidbridge/startup-failure.json`
- `uuidbridge/reports/<planId>.json` or `uuidbridge/reports/<planId>-rollback.json` when execution reached the migration executor
- `uuidbridge/migration.lock` when rollback could not safely complete

Do not delete lock or pending files until the report has been inspected. Restore from the external full backup if the report says `ROLLBACK_FAILED`.
