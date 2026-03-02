# MSF Coding Standards

## Java Version

Java 21 minimum. Use these features where appropriate:

- **Records** — for all immutable model classes (`MsfHeader`, `MsfPalette`, `MsfLayer`, etc.)
- **Sealed classes** — for closed type hierarchies (`CompressionType`, signal port types)
- **Pattern matching** — for type-safe dispatch
- **Switch expressions** — preferred over switch statements
- **Text blocks** — for multiline string literals in tests

Do not use preview features. Stability across build environments is required.

## Formatting

- 4 space indentation, no tabs
- 120 character line limit
- Opening braces on the same line
- One blank line between methods

These are enforced by `.editorconfig`.

## Naming Conventions

| Kind | Convention | Example |
|------|-----------|---------|
| Classes | `UpperCamelCase` | `MsfReader`, `BitPackedArray` |
| Methods and fields | `lowerCamelCase` | `readHeader`, `paletteOffset` |
| Constants | `UPPER_SNAKE_CASE` | `MAGIC_BYTES`, `MAX_PALETTE_ENTRIES` |
| Packages | `lowercase.dot.separated` | `dev.msf.core.codec` |

## Package Structure

```
dev.msf.core
  io/           MsfReader, MsfWriter
  model/        MsfFile, MsfHeader, MsfLayer, MsfRegion, MsfPalette, MsfMetadata
  codec/        BitPackedArray, BlockDataCodec
  checksum/     XxHash3
  compression/  CompressionType, RegionCompressor, RegionDecompressor
  util/         YzxOrder, UuidStripper
  exception/    MsfException and subclasses

dev.msf.fabric
  bridge/       BlockStateBridge, EntityBridge, BiomeBridge
  world/        RegionExtractor, RegionPlacer
  validation/   BlockStateValidator, DataVersionChecker
```

## Null Handling

- Return `Optional<T>` from public API methods that may not have a value
- Never return null from public API methods
- Use `@NotNull` and `@Nullable` (from `org.jetbrains.annotations` or equivalent) on all public method parameters and return types
- Validate parameters at public API boundaries using `Objects.requireNonNull()`

## Immutability

All model classes MUST be immutable. Use records where possible:

```java
// Preferred — record for simple immutable data
public record MsfHeader(
    int majorVersion,
    int minorVersion,
    int featureFlags,
    long mcDataVersion,
    long metadataOffset,
    long paletteOffset,
    long layerIndexOffset,
    long entityBlockOffset,
    long blockEntityBlockOffset,
    long fileSize,
    long headerChecksum
) {}
```

Use builders for complex object construction:

```java
MsfFile file = MsfFile.builder()
    .name("My Build")
    .author("Author")
    .addLayer(foundationLayer)
    .build();
```

Collections returned from public API methods MUST be unmodifiable.

## Unsigned Integer Handling

Java has no native unsigned integer types. Handle these cases explicitly:

- **u16** — read as `short`, mask with `0xFFFF` when used as a numeric value
- **u32** — read as `int`, mask with `0xFFFFFFFFL` or use `Integer.toUnsignedLong()`
- **u64** — read as `long`, use `Long.compareUnsigned()` for values exceeding `Long.MAX_VALUE`

**Write-side range validation:** throw `IllegalArgumentException` identifying the field name, the value provided, and the maximum permitted value. Silent truncation and value clamping are never permitted:

```java
if (entryCount > 65535) {
    throw new IllegalArgumentException(
        "entryCount exceeds maximum: provided " + entryCount + ", maximum 65535");
}
```

## Exception Hierarchy

```
MsfException (checked)
  MsfParseException       malformed file structure
  MsfVersionException     unsupported major version
  MsfChecksumException    checksum verification failure
  MsfPaletteException     palette encoding or lookup error
  MsfCompressionException compression or decompression failure
```

All IO operations MUST declare or handle `IOException`. MSF-specific errors MUST throw the appropriate `MsfException` subclass. Exception messages MUST include relevant context — file offset, expected value, actual value.

## Warning Mechanism

`Consumer<MsfWarning>` is accepted as an optional parameter on all `MsfReader` and `MsfWriter` read/write methods. Invoke once per warning. Never emit warnings to stdout, stderr, or any logging framework by default.

`MsfWarning` carries: `MsfWarning.Code` enum, human-readable message, file byte offset (or -1 for write-side warnings).

Warning codes: `RESERVED_FLAG_SET`, `RESERVED_FLAG_CLEARED`, `FILE_SIZE_MISMATCH`, `FILE_CHECKSUM_FAILURE`, `FEATURE_FLAG_CONFLICT`, `OFFSET_BEYOND_FILE_SIZE`, `DATA_VERSION_MISMATCH`.

## Spec References in Code

When implementing a normative requirement, cite the spec section inline. This makes the code self-documenting and helps reviewers verify correctness:

```java
// Palette ID 0 is always AIR per spec Section 4.3 — write unconditionally
// regardless of whether air appears in the block data
palette.add(0, "minecraft:air");
```

```java
// Entries are packed from the least significant bit of each u64 word.
// When an entry would span a word boundary it begins in the next word
// per spec Section 7.5.
if (bitOffset + bitsPerEntry > 64) {
    wordIndex++;
    bitOffset = 0;
}
```

## Javadoc

All public classes and interfaces MUST have Javadoc. All public methods MUST have Javadoc including `@param`, `@return`, and `@throws`. Reference the spec section where relevant:

```java
/**
 * Reads and validates the 48-byte MSF file header.
 *
 * <p>Validation sequence per spec Section 3.7:
 * <ol>
 *   <li>Magic bytes at offsets 0–3</li>
 *   <li>Header checksum at offsets 40–47</li>
 *   <li>Major version at offsets 4–5</li>
 * </ol>
 *
 * @param in       the input stream positioned at offset 0
 * @param warnings optional consumer for non-fatal diagnostic warnings
 * @return the parsed and validated header
 * @throws MsfParseException     if magic bytes do not match or file is too short
 * @throws MsfChecksumException  if header checksum verification fails
 * @throws MsfVersionException   if major version is not supported
 * @throws IOException           if an IO error occurs
 */
```

## Module Boundary Rules

**msf-core:**
- Zero Fabric or Minecraft dependencies — this is non-negotiable
- Blockstate strings are opaque UTF-8 — stored and packed, never interpreted
- Entity type strings are opaque UTF-8 — never validated against a registry
- Biome identifier strings are opaque UTF-8 — never validated against a registry
- NBT payloads are raw bytes — never deserialized

**msf-fabric:**
- Contains zero MSF parsing logic — delegates entirely to msf-core
- Resolves MSF strings against Minecraft registries
- Converts between MSF model types and Minecraft types
- Any Minecraft version change touches only this module
