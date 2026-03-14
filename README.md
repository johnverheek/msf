# MSF — Minecraft Structured Format

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://minecraft.net)

A tool-agnostic binary schematic format for Minecraft. No single tool or mod owns the format — any tool can implement it.

MSF solves schematic fragmentation. Vanilla `.nbt` structures are size-limited. Litematica `.litematic` is tied to one client mod. Sponge `.schem` is aging. MSF is modern, compressed, layered, and permanently forward-compatible within V1 — code you write today will never be broken by a future V1 spec update.

## Why MSF?

| Feature | MSF | Litematica | Sponge Schematic |
|---|---|---|---|
| Tool ownership | None — open spec | Litematica mod | Sponge / WorldEdit |
| Forward compatibility | Guaranteed within V1 | No promise | Limited |
| Compression | ZSTD, LZ4, Brotli, none | GZIP (NBT) | GZIP (NBT) |
| Semantic layers | Yes — construction phases | No | No |
| Bit-packed palettes | Yes — compact storage | No | No |
| Entity support | Yes — with UUID stripping | Yes | Yes |
| Pure Java library | Yes — zero MC dependencies | No | No |

## Project Structure

- **msf-core** — Pure Java 21 library. Zero Minecraft dependencies. All MSF parsing, encoding, checksums, and compression. Publishable to Maven Central.
- **msf-fabric** — Fabric 1.21.1 bridge. Resolves MSF strings against Minecraft registries. In-game `/msf` commands.
- **msf-cli** — Standalone CLI tool. Inspect, validate, and convert between `.nbt`, `.litematic`, and `.msf` files.

## Quick Start

### Use the CLI

Convert a Litematica schematic to MSF:
```bash
java -jar msf-cli.jar convert my_build.litematic my_build.msf
```

Inspect an MSF file:
```bash
java -jar msf-cli.jar inspect my_build.msf
```

Validate an MSF file:
```bash
java -jar msf-cli.jar validate my_build.msf
```

### Use the Fabric mod

Extract a region in-game:
```
/msf extract 0 64 0 15 80 15 my_build
```

Place a schematic with rotation and mirror:
```
/msf place my_build 100 64 200 rotation 90 mirror x
```

List available schematics:
```
/msf list
```

Inspect a schematic in chat:
```
/msf info my_build
```

## Installation

### msf-core (Java library)

**Gradle (Kotlin DSL):**
```kotlin
dependencies {
    implementation("dev.msf:msf-core:1.1.0")
}
```

**Maven:**
```xml
<dependency>
    <groupId>dev.msf</groupId>
    <artifactId>msf-core</artifactId>
    <version>1.1.0</version>
</dependency>
```

### msf-fabric (Minecraft mod)

Download from [Modrinth](https://modrinth.com/mod/msf) or [CurseForge](https://curseforge.com/minecraft/mc-mods/msf). Requires Fabric Loader 0.16.5+ and Fabric API for Minecraft 1.21.1.

### msf-cli (command-line tool)

Download `msf-cli.jar` from [GitHub Releases](https://github.com/johnverheek/msf/releases). Requires Java 21.

## CLI Usage

### Convert

Convert between `.nbt`, `.litematic`, and `.msf` with control over compression and metadata:

```bash
# Convert with specific compressor and level
java -jar msf-cli.jar convert input.litematic output.msf --compressor zstd --compression-level 6

# Convert without entities
java -jar msf-cli.jar convert input.nbt output.msf --entities false

# Override metadata during conversion
java -jar msf-cli.jar convert input.litematic output.msf --name "My Build" --author "Builder"

# Re-encode an existing MSF file with different compression
java -jar msf-cli.jar convert input.msf output.msf --compressor lz4
```

Supported conversions: `.nbt` ↔ `.msf`, `.litematic` ↔ `.msf`, `.litematic` ↔ `.nbt`, `.msf` → `.msf`.

### Inspect

Print a detailed summary of an MSF file. Includes palette statistics (top 10 blocks by frequency), layer breakdown, entity counts, and checksum status.

```bash
# Human-readable output
java -jar msf-cli.jar inspect my_build.msf

# JSON output for CI pipelines
java -jar msf-cli.jar inspect --format json my_build.msf

# Show parser warnings on stderr
java -jar msf-cli.jar inspect --warnings my_build.msf
```

### Validate

Run structural validation checks against the MSF specification:

```bash
java -jar msf-cli.jar validate my_build.msf

# Show warning details
java -jar msf-cli.jar validate --warnings my_build.msf
```

## Fabric Commands

| Command | Description |
|---|---|
| `/msf extract <x1> <y1> <z1> <x2> <y2> <z2> <name>` | Extract a region to an MSF file |
| `/msf extract ... entities 1 name "My Build"` | Extract with entities and custom name |
| `/msf place <name>` | Place at player position |
| `/msf place <name> <x> <y> <z>` | Place at explicit coordinates |
| `/msf place <name> rotation 90 mirror x layer foundation` | Place with rotation, mirror, and layer filter |
| `/msf list` | List available schematics (paginated) |
| `/msf info <name>` | Inspect a schematic in chat |

Schematics are stored in the `msf-schematics/` directory relative to the server working directory.

## Key Features

### Semantic Layers

MSF schematics support named construction layers (foundation, frame, wiring, decoration, etc.) with dependency ordering. Build tools can display, toggle, and place layers independently.

### Entity and Block Entity Support

Entities (armor stands, item frames, etc.) and block entities (chests, signs, etc.) are stored with typed fields. UUIDs are automatically stripped on save and regenerated on paste — no UUID collisions when pasting the same schematic multiple times.

### Forward Compatibility

The MSF V1 header is permanently frozen at 48 bytes. All blocks beyond the header are discoverable and skippable via length prefixes. A V1.0 reader can safely read any future V1.x file — it reads what it understands and skips what it doesn't.

### Version Disambiguation

All output surfaces (CLI and Fabric) clearly distinguish the MSF format version (from the file header, e.g., "Format: V1.0") from the implementation version (e.g., "msf-cli: 1.1.0"). These are independent version tracks.

## File Format

The MSF binary format is defined by a normative specification: [`docs/MSF_Specification_V1.md`](docs/MSF_Specification_V1.md).

```
Header layout (48 bytes, permanently frozen):

Offset  Size  Field
------  ----  -----
0       4     Magic bytes (MSF!)
4       2     Major version
6       2     Minor version
8       4     Feature flags
12      4     MC data version
16      4     Metadata block offset
20      4     Global palette offset
24      4     Layer index offset
28      4     Entity block offset
32      4     Block entity block offset
36      4     File size
40      8     Header checksum (xxHash3-64)
```

## Documentation

| Audience | Document |
|---|---|
| Spec implementors | [MSF Specification V1](docs/MSF_Specification_V1.md) |
| Contributors | [Architecture](docs/ARCHITECTURE.md) · [Coding Standards](docs/CODING_STANDARDS.md) · [Contributing](CONTRIBUTING.md) |
| Module details | [msf-core](docs/modules/) · [msf-fabric](docs/modules/) · [msf-cli](docs/modules/) |
| Changelog | [CHANGELOG.md](CHANGELOG.md) |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, test running, and commit conventions.

## License

[MIT](LICENSE)
