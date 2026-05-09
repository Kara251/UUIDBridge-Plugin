# UUIDBridge Plugin

UUIDBridge Plugin is a server-side UUID migration plugin for Paper, Purpur,
Spigot, and Bukkit-compatible Minecraft servers.

Target version: Minecraft and Paper API 1.21.1. The plugin requires Java 21.

UUIDBridge helps server administrators migrate between online-mode and
offline-mode UUIDs without losing player-bound vanilla data. Plugin data is
handled conservatively: only documented writable presets are migrated with
`--plugins`; unknown plugin data is reported but not modified.

## Supported Servers

- Paper and Purpur 1.21.1
- Spigot/Bukkit-compatible 1.21.1 servers where the Bukkit plugin lifecycle
  matches Paper's startup behavior

Folia-specific scheduling and proxy-side migrations are out of scope for this
release.

## Quick Start

1. Build the plugin:

   ```sh
   ./gradlew --no-daemon build
   ```

2. Install `build/libs/uuidbridge-plugin-0.1.0-SNAPSHOT.jar` into the server
   `plugins/` directory.
3. Start the server once and run:

   ```sh
   uuidbridge status
   ```

4. Create and review a plan:

   ```sh
   uuidbridge scan online-to-offline
   uuidbridge plan online-to-offline
   ```

5. Mark the plan pending and restart:

   ```sh
   uuidbridge apply <planId> --confirm
   ```

UUIDBridge applies pending migrations during plugin startup before normal play.
If startup migration fails, it writes a report and fails closed rather than
letting the server continue in a partially migrated state.

## Documentation

- [Documentation index](docs/index.md)
- [Operations guide](docs/operations.md)
- [Vanilla coverage](docs/vanilla-coverage.md)
- [Plugin support matrix](docs/plugin-support.md)
- [Extra migration targets](docs/targets.md)
- [Production runbook](docs/production-runbook.md)
- [Development guide](docs/development.md)
- [Release checklist](docs/release-checklist.md)

## License

MIT
