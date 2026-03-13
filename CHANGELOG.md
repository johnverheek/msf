# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] — 2026-03-12

### Summary

Initial release of MSF (Minecraft Structured Format) — a binary schematic file format for Minecraft structures, with a Java reference implementation across three modules.

### Specification

- MSF V1.0 — [docs/MSF_Specification_V1.md](docs/MSF_Specification_V1.md)
- Covers: 48-byte frozen header, global palette (palette ID 0 = air invariant), metadata block, layer index, compressed region data, entity block, block entity block, file checksum (xxHash3-64), and the V1 versioning contract
- Appendices: implementer compatibility guarantee, block type summary, canonical Minecraft property ordering, xxHash3 reference, unsigned integer handling in Java, deprecation policy, binary reference file examples
- Minecraft target: Java Edition 1.13 and later; reference implementation targets 1.21.1

### msf-core

Pure Java 21 library; zero Fabric or Minecraft dependencies.

**Format I/O**
- `MsfReader` — reads MSF files from any `InputStream`; validates magic, header checksum, major version, and file checksum; delivers warnings via `Consumer<MsfWarning>`
- `MsfWriter` — writes complete MSF files; computes header and file checksums; validates all field ranges before writing
- `MsfReaderConfig` — caller-controlled strictness (e.g. `allowChecksumFailure`)

**Data model** (immutable records with builders)
- `MsfFile`, `MsfHeader`, `MsfPalette`, `MsfLayer`, `MsfRegion`, `MsfLayerIndex`, `MsfMetadata`, `MsfEntity`, `MsfBlockEntity`
- Full placement metadata: anchor point, canonical facing (N/S/E/W), rotation compatibility flags, optional functional volume, tool name/version, recommended placement mode, MC edition

**Codec**
- Bit-packed block data (YZX ordering, global palette IDs, word-boundary-safe packing)
- Biome data at 4×4×4 section resolution (YZX-quarter ordering)
- Compression: zstd, lz4, brotli, none

**Checksum**
- xxHash3-64 via `org.lz4:lz4-java`; header checksum (bytes 0–39) and file checksum (all bytes except final 8)

**NBT**
- In-house `NbtReader` / `NbtWriter` — no external NBT library
- Supports all 12 tag types; gzip and raw modes

**Conversion**
- `.nbt` (vanilla structure format) ↔ `.msf` round-trip via `VanillaStructureFormat`
- `.litematic` (Litematica) ↔ `.msf` round-trip; pending tick data is silently dropped
- Cross-format routing (e.g. `.litematic` → `.nbt` via MSF intermediate)

**Utilities**
- `UuidStripper` — strips entity and block entity UUIDs from raw NBT payloads
- Exception hierarchy: `MsfException` → `MsfParseException`, `MsfVersionException`, `MsfChecksumException`, `MsfPaletteException`, `MsfCompressionException`
- `MsfWarning` with 11 warning codes

### msf-fabric

Fabric 1.21.1 bridge; bundles msf-core.

- `BlockStateBridge` — canonical blockstate string ↔ `BlockState` resolution against Minecraft registries
- `EntityBridge` — `Entity` ↔ `MsfEntity` conversion; UUID stripping on write, UUID assignment on read
- `BlockEntityBridge` — `BlockEntity` ↔ `MsfBlockEntity` conversion; excludes position and id tags
- `BiomeBridge` — world biome queries at 4×4×4 resolution
- `RegionExtractor` — extracts a world subvolume to `MsfFile`; assigns canonical facing and MC edition metadata
- `RegionPlacer` — places `MsfFile` layers into the world with facing rotation (CW 90/180/270)
- `CanonicalFacing` — canonical facing enum with clockwise rotation delta computation
- `/msf extract` — server command; extracts selected region, writes to `msf-schematics/<filename>.msf`
- `/msf place` — server command; reads and places schematic at player position with facing alignment

### msf-cli

Standalone CLI tool; depends on msf-core only.

- `inspect` — prints version, file size, feature flags, palette size, and layer summary
- `validate` — checks structure, checksums, and palette; reports warnings and errors; exit codes 0/1/2
- `convert` — routes by input/output extension; supports `.nbt`, `.litematic`, `.msf`
- Distributed as a self-contained fat jar (`msf-cli-1.0.0.jar`); requires Java 21

### Infrastructure

- GitHub Actions CI/CD: build and test on push and PR to `develop` and `main`; Java 21; Gradle dependency cache
- Version sourced from a single location (`allprojects.version` in root `build.gradle.kts`)
- CLI fat jar built with Shadow plugin (`./gradlew :msf-cli:shadowJar`)

[1.0.0]: https://github.com/johnverheek/msf/releases/tag/v1.0.0
