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

Fabric 1.21.1 bridge. Depends on msf-core.

- Resolves MSF blockstate strings against Minecraft registries
- Extracts regions from the live world and writes `.msf` files
- Places regions into the live world with player-facing rotation support
- In-game commands:
  - `/msf extract <x1> <y1> <z1> <x2> <y2> <z2> <filename>` — extract a region to a `.msf` file
  - `/msf place <filename>` — place a schematic at the player's position

Schematics are read from and written to the `msf-schematics/` directory inside the server run directory.

### msf-cli

Standalone command-line tool. Depends on msf-core only. No Minecraft installation required.

- `inspect` — print header summary, block inventory, and layer structure
- `validate` — verify structure, checksums, and palette integrity; exits 0 if valid, 1 if invalid
- `convert` — convert between `.msf`, `.nbt`, and `.litematic`

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
# Output: msf-cli/build/libs/msf-cli-1.0.0.jar
```

**Fabric gametests** require a live Minecraft server and are run separately from the standard build:

```bash
./gradlew :msf-fabric:runGametest
```

---

## CLI Usage

The CLI fat jar is self-contained and requires only Java 21.

```bash
java -jar msf-cli-1.0.0.jar --help
```

### inspect

Print a summary of an MSF file's header and block inventory.

```bash
java -jar msf-cli-1.0.0.jar inspect my_structure.msf
```

### validate

Verify structural integrity, checksums, and palette correctness. Exits 0 if valid, 1 on errors, 2 if the file cannot be read.

```bash
java -jar msf-cli-1.0.0.jar validate my_structure.msf
```

### convert

Convert between `.msf`, `.nbt` (vanilla structure format), and `.litematic` (Litematica). The output format is inferred from the output file extension.

```bash
# Vanilla .nbt → MSF
java -jar msf-cli-1.0.0.jar convert structure.nbt output.msf

# MSF → vanilla .nbt
java -jar msf-cli-1.0.0.jar convert structure.msf output.nbt

# Litematica → MSF
java -jar msf-cli-1.0.0.jar convert blueprint.litematic output.msf

# MSF → Litematica
java -jar msf-cli-1.0.0.jar convert structure.msf output.litematic

# Cross-format (e.g. Litematica → .nbt via MSF intermediate)
java -jar msf-cli-1.0.0.jar convert blueprint.litematic output.nbt
```

> Pending tick data from Litematica schematics is silently dropped. Structures are saved in rested state, which is correct behavior for placement.

---

## Repository Layout

```
docs/
  MSF_Specification_V1.md   — normative specification (source of truth)
  ARCHITECTURE.md           — module boundaries and package structure
  CODING_STANDARDS.md       — code conventions
msf-core/                   — pure Java library (parsing, encoding, conversion)
msf-fabric/                 — Fabric 1.21.1 bridge (world I/O, commands)
msf-cli/                    — standalone CLI tool
build.gradle.kts            — shared build configuration
settings.gradle.kts         — module declarations
gradle/libs.versions.toml   — version catalog
```

---

## License

MIT
