# Release Checklist

Run this checklist before publishing a production jar.

## Required Checks

- `./gradlew --no-daemon test`
- `./gradlew --no-daemon build`
- `./gradlew --no-daemon smokePaperServer`
- Confirm GitHub Actions is green for the release commit.

## Manual Review

- Read the latest changed files for path traversal, accidental broad plugin writes, and unsafe startup behavior.
- Confirm `plugin.yml` still uses `load: STARTUP` and `api-version: '1.21.1'`.
- Confirm `--plugins` only writes documented writable presets.
- Confirm docs mention new supported plugin paths or new limitations.

## Release Artifact

Publish `build/libs/uuidbridge-plugin-<version>.jar`. Do not publish the sources jar as the server plugin.
