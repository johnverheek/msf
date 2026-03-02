# MSF — Minecraft Structured Format

MSF is a binary file format for storing Minecraft structure schematics. It is designed to be tool-agnostic, version-stable, and extensible without breaking backwards compatibility. Any tool may implement MSF. No single tool or mod owns the format.

## Format Overview

MSF files use the `.msf` extension. The format stores:
- Block data in palette-compressed, bit-packed regions
- Semantic construction layers with dependency ordering
- Placement metadata including anchor point, canonical facing, and rotation compatibility
- Optional entities, block entities, and biome data

The core header is permanently frozen at 48 bytes. All blocks beyond the header are discoverable and skippable via length prefixes, guaranteeing that any V1 reader can read any V1 file regardless of minor version.

The full specification is at [docs/MSF_Specification_V1.md](docs/MSF_Specification_V1.md).

## Modules

### msf-core
Pure Java 21 library with zero Minecraft or Fabric dependencies. Contains all MSF parsing, encoding, checksum, compression, and data model logic. Published to Maven Central as `dev.msf:msf-core:1.0.0`.

### msf-fabric
Fabric mod bridge that resolves MSF blockstate strings, entity types, and biome identifiers against Minecraft registries. Converts between MSF model types and Minecraft types. Contains zero MSF parsing logic — delegates entirely to msf-core. Published to Modrinth and CurseForge as `msf-fabric:1.0.0+1.21.1`.

## Building

```bash
./gradlew build
```

## Testing

```bash
./gradlew test
```

## Target Versions

| Component | Version |
|-----------|---------|
| Java | 21 |
| Minecraft | 1.21.1 |
| Fabric Loader | 0.16.5+ |
| Fabric API | 0.102.0+1.21.1 |

## License

MIT License — see [LICENSE](LICENSE).
