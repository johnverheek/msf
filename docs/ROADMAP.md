# MSF Roadmap

## Implementation Sessions

| Session | Scope | Status |
|---------|-------|--------|
| 1 | Gradle scaffold, exception hierarchy, MsfWarning, MsfHeader, MsfReader/MsfWriter (header only), JUnit 5 tests | Complete |
| 2 | Global palette, metadata block, layer index, region data (bit packing, all compression types, biome data) | Complete |
| 3 | Entity block, block entity block, full MsfFile round trip | Complete |
| 13f | BlockPosArgument relative coordinates, gametest API migration to Fabric 0.141.x, debug cleanup | Complete |
| 14 | /msf list (paginated, clickable prev/next, per-file metadata, error annotation), Mod Menu integration (ModMenuIntegration, modmenu entrypoint), msf-cli output header (stderr, all subcommands) | Complete — uncommitted |

## Specification Review Rounds

| Round | Key Changes | Status |
|-------|-------------|--------|
| V1_A | Header checksum, file checksum, version rejection, warning mechanism | Closed |
| V1_B | Null offset validation, write-side range validation, magic bytes endianness, buffer-first reading | Closed |
| V1_C | Warning code table, write-side warning offset, placement metadata layout, bit packing zero-entry palette, biome all-or-nothing flag, region count zero | Closed |
| V1_D | Section 7.7 cross-reference, canonical facing writer validation, rotation compatibility reserved bits, thumbnail semantics, layer/region count overflow, region size zero | Closed |
| V1_E | Unrecognized compression type behavior, packed array length consistency check, biome packed array length formula, entity/block entity count of zero, anchor offset range | Closed |
| V1_F | Integer overflow in packed array length formula, out-of-range palette ID validation, layer flags reserved bits, entity count u32 ceiling, author field empty string policy | Closed |
| V1_G | Biome absence detection mechanism, compressed/uncompressed data length validation, duplicate palette entry reader behavior | Closed |
| V1_H | Entity NBT payload u16 ceiling, UUID stripping scope, block entity NBT payload ceiling, id tag naming, writer flag/offset sync obligation, file checksum input range, header-before-file checksum write ordering | Closed |
| V1_I | EE/FF/GG deferred from V1_G — pending resolution before Session 3 | Pending |

## Publishing Targets

- `dev.msf:msf-core:1.0.0` → Maven Central
- `msf-fabric:1.0.0+1.21.11` → Modrinth + CurseForge

## Open Questions (Post-V1)

These are tracked but explicitly deferred to a future minor version:

- Compact binary encoding for blockstate strings in palette
- Formal delta/diff block specification (feature flag bit 5)
- Fluid state handling — flowing vs source fluid storage
- Migration hint system for MC data version incompatibilities
- Full signal port type taxonomy (feature flag bit 6)
- Variant selection ruleset — how biome tags map to variants (feature flag bit 8)
- Palette substitution rule encoding (feature flag bit 9)
