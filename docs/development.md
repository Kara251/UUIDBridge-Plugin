# Development Guide

UUIDBridge Plugin targets Minecraft/Paper API 1.21.1 and requires JDK 21.

## Common Commands

```sh
./gradlew --no-daemon test
./gradlew --no-daemon build
./gradlew --no-daemon smokePaperServer
```

The Gradle Wrapper is used so no global Gradle install is required.

## Build Outputs

The installable plugin jar is:

```text
build/libs/uuidbridge-plugin-0.1.0-SNAPSHOT.jar
```

Do not install the sources jar on a server.

## Smoke Test

`smokePaperServer` downloads Paper 1.21.1, starts temporary servers, installs
the current shadow jar, and verifies:

- the plugin loads and `/uuidbridge status` works;
- a pending apply runs during startup and succeeds;
- a failed pending apply writes `startup-failure.json` and does not reach a
  playable `Done` state.

The task needs network access the first time it downloads Paper or Mojang
server dependencies. It keeps build-local caches under `build/`.

## CI

GitHub Actions runs:

```sh
./gradlew --no-daemon test build
```

Run `smokePaperServer` before a production release because it starts real Paper
processes and may take longer than normal CI.

## Project Boundaries

- Keep documentation under `docs/`, except the root `README.md`.
- Keep runtime dependencies shaded or provided by the server; do not add
  unshaded runtime libraries.
- Preserve Bukkit-compatible `plugin.yml` entrypoints.
- Prefer explicit plugin presets and fixtures over broad automatic plugin
  migration.
