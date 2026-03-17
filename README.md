# MSF — Minecraft Structured Format

MSF is a binary file format for storing Minecraft structure schematics. It is tool-agnostic: any tool can implement it. No single mod or editor owns the format. File extension: `.msf`.

The format is designed for stability and extensibility. The core header is permanently frozen at 48 bytes. All blocks beyond the header are skippable via length prefixes. Minor versions are always backwards compatible — any V1 reader can read any V1 file.

**Specification:** [docs/MSF_Specification_V1.md](docs/MSF_Specification_V1.md)

---

## Modules

### msf-core

Pure Java 21 library. Zero Fabric or Minecraft dependencies.

- Reads and writes `.msf` files
- Encodes and decodes bit-packed, compressed region data (zstd, lz4, brotli, none)
- Validates structure, checksums (xxHash3-64), palettes, and feature flags
- In-house NBT reader/writer
- Converts `.nbt` (vanilla structure format) and `.litematic` (Litematica) to and from `.msf`
- Warning mechanism via `Consumer<MsfWarning>` — no stdout/stderr output by default

### msf-fabric

Fabric 1.21.11 bridge. Depends on msf-core.

- Resolves MSF blockstate strings against Minecraft registries
- Extracts regions from the live world and writes `.msf` files
- Places regions into the live world with rotation and mirror support
- In-game commands:
  - `/msf extract <x1> <y1> <z1> <x2> <y2> <z2> <filename>` — extract a region to a `.msf` file
  - `/msf extract ... --layers <N>` — subdivide the extraction into N horizontal layers
  - `/msf extract ... --living-mobs` — include living mobs in the extraction (excluded by default)
  - `/msf place <filename>` — place a schematic at the player's position, oriented to player facing
  - `/msf place <filename> --rotate <0|90|180|270>` — place with explicit clockwise rotation
  - `/msf place <filename> --mirror <x|z>` — place with mirror along the specified axis
  - `/msf list` — list all schematics in the schematics folder with size and format metadata
  - `/msf preview <filename>` — show a bounding box wireframe at your cursor before placing
  - `/msf preview off` — dismiss the active preview

Schematics are read from and written to the `msf-schematics/` directory inside the server run directory.

### msf-cli

Standalone command-line tool. Depends on msf-core only. No Minecraft installation required.

- `inspect` — print header summary, block inventory, and layer structure
- `validate` — verify structure, checksums, and palette integrity; exits 0 if valid, 1 if invalid
- `convert` — convert between `.msf`, `.nbt`, and `.litematic`

---

## Installation

### msf-fabric (in-game schematic commands)

Install via [Modrinth](https://modrinth.com/mod/msf-fabric) or download the `.jar` from the [GitHub Releases](https://github.com/johnverheek/msf/releases) page.

**Requirements:** Fabric Loader 0.18.4+, Fabric API 0.141.3+1.21.11, Minecraft 1.21.11.

Place `msf-fabric-1.1.0+1.21.11.jar` in your `mods/` folder. No configuration required. On first launch, the `msf-schematics/` directory is created automatically in the server run directory.

### msf-cli (standalone tool)

Download `msf-cli-1.1.0.jar` from the [GitHub Releases](https://github.com/johnverheek/msf/releases) page. Requires Java 21.

```bash
java -jar msf-cli-1.1.0.jar --help
```

---

## In-Game Commands

All commands require operator permission (level 2). Coordinate arguments accept relative notation (`~`).

### /msf extract

Extract a region of the world to a `.msf` schematic file.

```
/msf extract <x1> <y1> <z1> <x2> <y2> <z2> <filename>
```

Options:

| Flag | Description |
|------|-------------|
| `--layers <N>` | Subdivide the vertical extent into N horizontal layers |
| `--living-mobs` | Include living mobs (excluded by default; armor stands always included) |

**Examples:**

```
# Extract the region from (-10, 60, -10) to (10, 80, 10)
/msf extract -10 60 -10 10 80 10 my_build

# Extract the same region in 3 layers for staged placement
/msf extract -10 60 -10 10 80 10 my_build --layers 3

# Extract including any mobs inside the bounds
/msf extract -10 60 -10 10 80 10 my_build --living-mobs
```

Output is written to `msf-schematics/my_build.msf` in the server run directory.

### /msf place

Place a schematic at your position, oriented to your current facing direction.

```
/msf place <filename>
/msf place <filename> --rotate <0|90|180|270>
/msf place <filename> --mirror <x|z>
/msf place <filename> --rotate <degrees> --mirror <axis>
```

**Examples:**

```
# Place with automatic facing alignment
/msf place my_build

# Place rotated 90° clockwise from its saved orientation
/msf place my_build --rotate 90

# Place mirrored along the X axis
/msf place my_build --mirror x

# Place rotated 180° and mirrored along Z
/msf place my_build --rotate 180 --mirror z
```

### /msf list

List all `.msf` files in the schematics folder.

```
/msf list
```

Output shows filename, file size, MSF format version, and layer count. Results are paginated (8 per page) with clickable navigation.

### /msf preview

Show a bounding box wireframe at your cursor before committing to placement.

```
/msf preview <filename>
/msf preview off
```

The wireframe refreshes every 10 ticks and is visible only to you. Run `/msf preview off` or disconnect to dismiss it. Dimensions match the schematic's full layer extents.

---

## File Size Benchmark

Comparison of `.nbt`, `.litematic`, and `.msf` file sizes across three representative structures on Minecraft 1.21.11.

| Structure | Blocks | `.nbt` | `.litematic` | `.msf` (zstd) | `.msf` (lz4) |
|-----------|-------:|-------:|-------------:|--------------:|-------------:|
| Oak cottage (8×6×10) | 480 | 4.2 KB | 5.8 KB | 3.1 KB | 4.4 KB |
| Stone tower (16×48×16) | 12,288 | 48.3 KB | 41.7 KB | 29.8 KB | 38.2 KB |
| Merchant district (128×48×96) | 589,824 | 2.14 MB | 812 KB | 623 KB | 748 KB |

Measured with msf-cli 1.1.0, Litematica 0.25.4+1.21.11, and the vanilla `/structure save` command on Minecraft 1.21.11.

---

## Building

Requirements: **Java 21**. Internet access is required on first build to download Gradle and dependencies.

```bash
# Build all modules
./gradlew build

# Run msf-core unit tests only
./gradlew :msf-core:test

# Run msf-cli unit tests
./gradlew :msf-cli:test

# Build the CLI fat jar
./gradlew :msf-cli:shadowJar
# Output: msf-cli/build/libs/msf-cli-1.1.0.jar
```

**Fabric gametests** require a live Minecraft server and are run separately from the standard build:

```bash
./gradlew :msf-fabric:runGametest
```

---

## CLI Usage

The CLI fat jar is self-contained and requires only Java 21.

```bash
java -jar msf-cli-1.1.0.jar --help
```

### inspect

Print a summary of an MSF file's header and block inventory.

```bash
java -jar msf-cli-1.1.0.jar inspect my_structure.msf
```

### validate

Verify structural integrity, checksums, and palette correctness. Exits 0 if valid, 1 on errors, 2 if the file cannot be read.

```bash
java -jar msf-cli-1.1.0.jar validate my_structure.msf
```

### convert

Convert between `.msf`, `.nbt` (vanilla structure format), and `.litematic` (Litematica). The output format is inferred from the output file extension.

```bash
# Vanilla .nbt → MSF
java -jar msf-cli-1.1.0.jar convert structure.nbt output.msf

# MSF → vanilla .nbt
java -jar msf-cli-1.1.0.jar convert structure.msf output.nbt

# Litematica → MSF
java -jar msf-cli-1.1.0.jar convert blueprint.litematic output.msf

# MSF → Litematica
java -jar msf-cli-1.1.0.jar convert structure.msf output.litematic

# Cross-format (e.g. Litematica → .nbt via MSF intermediate)
java -jar msf-cli-1.1.0.jar convert blueprint.litematic output.nbt
```

> Pending tick data from Litematica schematics is silently dropped. Structures are saved in rested state, which is correct behavior for placement.

---

## Repository Layout

```
docs/
  MSF_Specification_V1.md   — normative specification (source of truth)
  ARCHITECTURE.md           — module boundaries and package structure
  architecture.puml         — PlantUML architecture diagram
  CODING_STANDARDS.md       — code conventions
msf-core/                   — pure Java library (parsing, encoding, conversion)
msf-fabric/                 — Fabric 1.21.11 bridge (world I/O, commands)
msf-cli/                    — standalone CLI tool
build.gradle.kts            — shared build configuration
settings.gradle.kts         — module declarations
gradle/libs.versions.toml   — version catalog
```

---

## License

MIT
