# MSF Architecture

## System Overview

MSF is a two-module Java 21 library. The dependency direction is one-way:

```
msf-fabric  →  msf-core
```

msf-fabric depends on msf-core. msf-core has zero external dependencies beyond its compression and checksum libraries.

## Module Responsibilities

### msf-core

**Purpose:** All MSF format logic. This module is the complete, self-contained implementation of the MSF specification.

**What lives here:**
- File parsing and serialization (`MsfReader`, `MsfWriter`)
- All data model classes (`MsfHeader`, `MsfPalette`, `MsfMetadata`, `MsfLayer`, `MsfRegion`, `MsfFile`)
- Bit packing and block data encoding (`BitPackedArray`, `BlockDataCodec`)
- Checksum computation (`XxHash3`)
- Compression and decompression for all four types (`RegionCompressor`, `RegionDecompressor`)
- YZX block ordering index computation (`YzxOrder`)
- UUID stripping from binary NBT payloads (`UuidStripper`)
- The complete exception hierarchy (`MsfException` and subclasses)
- The warning mechanism (`MsfWarning`, `MsfWarning.Code`)

**What must never appear here:**
- Any Fabric or Minecraft import
- Registry lookups of any kind
- Blockstate string interpretation
- NBT deserialization
- Entity type validation

Blockstate strings, entity type strings, and biome identifiers are opaque UTF-8 in this module. They are stored, packed, and retrieved — never interpreted.

**Publishing target:** Maven Central as `dev.msf:msf-core:1.0.0`

### msf-fabric

**Purpose:** Bridge between msf-core and a live Minecraft/Fabric environment.

**What lives here:**
- Resolving blockstate strings against `Registries.BLOCK`
- Converting `MsfRegion` block data to/from `BlockState` objects
- Resolving entity and biome identifier strings against Minecraft registries
- Converting `MsfEntity` and `MsfBlockEntity` NBT payloads to/from `NbtCompound`
- Reading block data from a live world into `MsfRegion` (`RegionExtractor`)
- Writing `MsfRegion` block data to a live world (`RegionPlacer`)
- Validating blockstate strings against the active MC data version

**What must never appear here:**
- Any MSF parsing or encoding logic
- Copies of bit packing, checksum, or compression code
- Any class that duplicates msf-core functionality

**Publishing target:** Modrinth and CurseForge as `msf-fabric:1.0.0+1.21.1`

## Package Structure

```
dev.msf.core
  io/           MsfReader, MsfWriter
  model/        MsfFile, MsfHeader, MsfLayer, MsfRegion, MsfPalette, MsfMetadata,
                MsfEntity, MsfBlockEntity
  codec/        BitPackedArray, BlockDataCodec
  checksum/     XxHash3
  compression/  CompressionType, RegionCompressor, RegionDecompressor
  util/         YzxOrder, UuidStripper
  exception/    MsfException, MsfParseException, MsfVersionException,
                MsfChecksumException, MsfPaletteException, MsfCompressionException

dev.msf.fabric
  bridge/       BlockStateBridge, EntityBridge, BiomeBridge
  world/        RegionExtractor, RegionPlacer
  validation/   BlockStateValidator, DataVersionChecker
```

## Key Design Decisions

**Global palette, shared across all regions.** Palette ID 0 is always `minecraft:air` — this is a format invariant, not a convention. The palette is read once and referenced by all region block data.

**YZX block ordering.** Y is the outermost index, X is the innermost. Chosen for cache-friendly vertical operations (lighting propagation, column-based processing).

**Per-region compression.** Each region declares its compression type independently. zstd is the default and recommended compression. Readers must support all four types: none, zstd, lz4, brotli.

**Bit packing with no-span rule.** Palette IDs are packed into u64 words from the LSB. Entries never span word boundaries — when an entry would cross a boundary it begins in the next word.

**Semantic layers, not viewport layers.** Layers carry meaning about construction sequence and logical grouping. Viewport slicing is a tool concern. The format defines what layers exist; tools define how they are displayed.

**Append-only versioning.** The format grows by addition only. Features are never removed. Flag bits are never reassigned. Block types are never retired. Any V1 reader can read any V1 file via the block length prefix skip mechanism.

**Frozen 48-byte header.** The header layout is permanently fixed. It will never change within major version 1.

## Exception Hierarchy

```
MsfException (checked)
  MsfParseException       malformed file structure
  MsfVersionException     unsupported major version
  MsfChecksumException    checksum verification failure
  MsfPaletteException     palette encoding or lookup error
  MsfCompressionException compression or decompression failure
```

## Reader Failure Precedence

1. Magic byte mismatch → `MsfParseException`, stop immediately
2. Header checksum failure → `MsfChecksumException`, stop immediately
3. Unsupported major version → `MsfVersionException`, stop immediately
4. File size mismatch → warn, attempt to continue
5. File checksum failure → warn, stop at caller's discretion
6. Unknown feature flag → ignore, continue
7. Unknown block type → skip via length prefix, continue

## Warning Mechanism

Non-fatal diagnostic conditions are reported via `Consumer<MsfWarning>` passed to all read and write methods. Warnings are never written to stdout, stderr, or any logging framework — routing is the caller's responsibility.

See `docs/CODING_STANDARDS.md` for the full warning code table.
