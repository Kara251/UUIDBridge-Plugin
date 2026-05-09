# Extra Migration Targets

Use `uuidbridge/targets.json` for files that live outside the standard vanilla
scan and outside documented plugin presets.

Paths are relative to the server root by default. Prefix with `world:` to make
a path relative to the active world directory, or `game:` to make that root
explicit.

```json
{
  "include": [
    {
      "path": "config/claims/*.json",
      "format": "json"
    },
    {
      "path": "world:data/custom_saved_data.dat",
      "format": "nbt-gzip"
    }
  ],
  "exclude": [
    "config/claims/archive/**"
  ]
}
```

Use a different file with:

```sh
/uuidbridge scan online-to-offline --targets uuidbridge/test-targets.json
/uuidbridge plan online-to-offline --targets uuidbridge/test-targets.json
```

## Supported Formats

- `auto`: choose from the file extension.
- `json`: rewrite UUID string values while preserving unknown fields.
- `nbt-gzip`: rewrite compressed NBT.
- `nbt-plain`: rewrite uncompressed NBT.
- `region`: rewrite `.mca` chunks.
- `yaml`, `yml`, `yaml-text`: boundary-aware text replacement for mapped
  UUID strings.

`binary`, SQLite, H2, MySQL, LevelDB, and other database formats are not
accepted as writable targets in this release.

## Path Safety

Target paths may not escape the server or world directory. Paths containing
`..` are rejected.

Broad scans exclude UUIDBridge runtime files, logs, crash reports, `.git`,
build outputs, and plugin operational directories such as cache, backup,
library, updater, and temp folders.

## YAML Text Rules

YAML is not parsed or reformatted. UUIDBridge only performs text replacement:

- Dashed UUIDs are replaced only when not embedded in `[0-9A-Fa-f-]` tokens.
- Undashed UUIDs are replaced only when not embedded in `[0-9A-Fa-f]` tokens.
- Comments, quoting, ordering, and indentation are preserved.

Use YAML targets only for files you have inspected and backed up.
