# Module: msf-core

## Purpose

Pure Java 21 library containing all MSF format logic. Zero Minecraft or Fabric dependencies. Publishable to Maven Central as `dev.msf:msf-core:1.0.0`.

## Package Structure
```
dev.msf.core
  io/           MsfReader, MsfWriter
  model/        MsfFile, MsfHeader, MsfLayer, MsfRegion, MsfPalette
  codec/        BitPackedArray, BlockDataCodec
  checksum/     XxHash3
  compression/  CompressionType, RegionCompressor, RegionDecompressor
  util/         YzxOrder, UuidStripper
  exception/    MsfException, MsfParseException, MsfVersionException,
                MsfChecksumException, MsfPaletteException, MsfCompressionException
```

## Dependencies

| Artifact | Purpose |
|----------|---------|
| org.lz4:lz4-java | xxHash3 + LZ4 compression |
| com.github.luben:zstd-jni | Zstd compression |
| org.brotli:dec | Brotli decompression |
| org.junit.jupiter:junit-jupiter | Testing only |

## Key Invariants

- Blockstate strings, entity type strings, biome identifiers are opaque UTF-8 — never interpreted
- NBT payloads are raw bytes — never deserialized
- All parsing, encoding, checksum, and compression logic lives here
- Warning mechanism uses `Consumer<MsfWarning>` — no stdout/stderr by default

## Test Coverage

Tests cover header round-trip, corruption detection, version rejection, palette encoding/decoding, bit packing, all compression types, region encoding/decoding, entity/block entity blocks, full MsfFile round-trip, biome data, and forward compatibility.
