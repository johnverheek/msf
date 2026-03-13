# Module: msf-cli

## Purpose

Standalone CLI tool for inspecting, validating, and converting MSF files. Depends on msf-core only — no Minecraft or Fabric dependencies. Lives in the same repository as a third Gradle module.

## Subcommands

| Command | Description |
|---------|-------------|
| `inspect` | Display file structure, header fields, palette, layer index |
| `validate` | Run validation checks and report issues |
| `convert` | Convert between .msf, .nbt, and .litematic formats |

## Format Conversion

Supported conversion paths:

- `.nbt` → `.msf`
- `.msf` → `.nbt`
- `.litematic` → `.msf`
- `.msf` → `.litematic`
- `.nbt` ↔ `.litematic` (via intermediate .msf routing)

## Key Design Decisions

- In-house NBT reader/writer — no external NBT library dependency
- `.nbt` before `.litematic` in implementation priority because vanilla .nbt has a simpler data model
- Pending ticks from Litematica are silently dropped — rested-state placement is correct behavior
- Property ordering uses accept-and-warn rather than bundling a blocks.json snapshot
