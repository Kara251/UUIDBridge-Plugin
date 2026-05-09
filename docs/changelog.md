# Changelog

## Unreleased

- Added production-readiness documentation for operations, coverage, targets,
  development, release checks, and plugin support.
- Added Paper smoke verification for plugin load, status, pending apply, and
  fail-closed startup behavior.
- Hardened `--plugins` so only documented writable presets are migrated.
- Changed YAML text migration to boundary-aware UUID replacement.
- Added fail-closed startup failure marker and server termination behavior for
  failed pending migration.
