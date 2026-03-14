# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] — 2026-03-14

### Added

#### msf-core
- `OVERLAPPING_REGIONS` warning code per spec V1_O §6.4
- Compression level support through the writer API: `RegionCompressor.compress(data, type, level)`, with level-aware `toBytes` overloads on `MsfRegion`, `MsfLayer`, `MsfLayerIndex`
- `MsfWriter.writeFile(file, compressionType, compressionLevel, warnings)` entry point; existing `writeFile(file, warnings)` delegates to ZSTD at default level 3

#### msf-cli
- `convert --compressor zstd|lz4|brotli|none` — choose the compression algorithm for MSF output
- `convert --compression-level <int>` — set the compression level (ZSTD 1–22, 0 = default)
- `convert --entities true|false` — include or exclude entities from MSF output
- `convert --name <str>` and `--author <str>` — override metadata fields during conversion
- `inspect` palette statistics: total block count and top 10 entries by frequency
- `inspect --format json` — machine-readable JSON output for CI pipelines
- `inspect --warnings` and `validate --warnings` — print parser warnings to stderr
- Version disambiguation: all output shows "Format: V1.0" (from file) and "msf-cli: 1.1.0" separately

#### msf-fabric
- `/msf place` accepts explicit facing (0–3), mirror (none/x/z), and layer name filter
- `/msf extract` gains `entities` flag and `name` override
- `/msf list` — paginated schematic browser (8 per page, clickable navigation)
- `/msf info <filename>` — in-chat schematic inspector with format version and mod version
- Mirror support in `RegionPlacer`: `BlockMirror` applied after rotation per spec §10.3
- `CanonicalFacing.fromCwOrdinal(int)` for reverse lookup from Brigadier arguments
- Version disambiguation: all output shows format version and "msf-fabric: 1.1.0+1.21.1" separately

#### Spec
- V1_O revision: region overlap rule (§6.4), 4 GiB forward-compat note (§3.6), lighting hints stub (§3.3), test vector appendix (Appendix G), per-region palette rationale (§4.1), zstd level recommendation (§7.2), thumbnail dimension guidance (§5.2), data version validation depth (§4.3), circular dependency reader obligation (§6.4)

## [1.0.0] — 2026-03-12

### Added

#### msf-core
- MSF Specification V1 — normative binary format definition with frozen 48-byte header, append-only versioning contract, and universal V1 forward compatibility
- Complete MSF reader and writer with header validation sequence (magic → checksum → version), xxHash3-64 checksums, and configurable checksum failure handling
- Global palette with ID 0 = AIR invariant, deduplication enforcement, and canonical blockstate property ordering
- Layer index with semantic construction layers, dependency ordering, and per-layer region data
- Region data: YZX block ordering, bit-packed palette IDs with no-span rule, per-region compression (ZSTD, LZ4, Brotli, none)
- Biome data support at 4×4×4 section resolution with per-region local biome palettes
- Entity and block entity blocks with typed position/rotation fields and automatic UUID stripping
- Placement metadata: named anchor points, canonical facing, rotation compatibility flags, optional functional volume
- Warning mechanism via `Consumer<MsfWarning>` — callers control routing, no default stdout/stderr output
- In-house NBT reader and writer (no external NBT library dependency)

#### msf-cli
- `inspect` — human-readable MSF file summary
- `validate` — structural validation against the MSF specification
- `convert` — bidirectional conversion: `.nbt` ↔ `.msf`, `.litematic` ↔ `.msf`, `.litematic` ↔ `.nbt`
- Litematica pending tick data silently dropped (rested-state placement is correct behavior)

#### msf-fabric
- `/msf extract` — extract a world region to an MSF file with canonical facing from player direction
- `/msf place` — place an MSF file in-world with rotation derived from canonical-to-target facing delta
- Fabric 1.21.1 bridge: `BlockStateBridge`, `EntityBridge`, `BlockEntityBridge`, `BiomeBridge`
- Block state validation against Minecraft registries via `BlockStateValidator`
- Data version checking via `DataVersionChecker`
