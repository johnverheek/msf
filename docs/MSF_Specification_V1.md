# MSF (Minecraft Structured Format) Specification

## Version 1.0 — DRAFT

-----

## Status of This Document

This is the normative specification for the MSF file format version 1.0. Implementations claiming MSF V1 compliance MUST conform to all normative requirements in this document. Normative requirements are indicated by the keywords MUST, MUST NOT, SHALL, SHALL NOT, SHOULD, SHOULD NOT, MAY, and OPTIONAL as defined in RFC 2119.

-----

## 1. Introduction

MSF is a binary file format for storing Minecraft structure schematics. It is designed to be tool-agnostic, version-stable, and extensible without breaking backwards compatibility. Any tool MAY implement MSF. No single tool or mod owns the format.

### 1.1 Design Goals

- **Structured extensibility** — the core header is permanently frozen. All blocks beyond the core are discoverable and skippable via length prefixes.
- **Tool agnosticism** — the format owns data and structure. Tools own visualization and interaction. Rendering, viewport slicing, and UI concerns are explicitly out of scope for this specification.
- **Complexity is additive** — simple use cases require only the core header, palette, and a single region. Advanced features are opt-in via feature flags.
- **Append-only evolution** — the format grows by addition only. Features are never removed. Flag bits are never reassigned. Block types are never retired. This is a binding commitment to every implementer — code written against this spec will not be broken by future versions of this spec.
- **Universal backwards compatibility within a major version** — any V1 reader can read any V1 file regardless of minor version. The minor version is a capability advertisement not a compatibility gate. Readers MUST NOT reject files based on minor version alone.

### 1.2 Scope

This specification defines:

- The binary layout of MSF files
- The encoding of all data blocks
- Normative requirements for readers and writers
- The versioning contract

This specification does not define:

- How tools render or visualize MSF data
- How tools implement user interaction
- How tools implement viewport slicing or layer toggling
- Any mod, plugin, or application

MSF V1.0 targets Java Edition 1.13 and later. Files authored against pre-1.13 data versions are outside the scope of this specification.

> **Edition scope (non-normative):** This specification is authored with Java Edition as the primary target. Blockstate strings, entity type identifiers, biome identifiers, and NBT structure follow Java Edition conventions as of the MC data version declared in the header. Bedrock Edition uses different identifiers, different NBT schemas, and different data versions. A conforming MSF V1.0 file produced from a Bedrock world is outside the scope of this specification. Cross-edition support, if pursued, would require a future minor version with explicit identifier mapping provisions.

### 1.3 Terminology

- **Block** — a discrete section of an MSF file located via an absolute offset. Not to be confused with a Minecraft block.
- **Region** — a three-dimensional subvolume of block data within a layer.
- **Layer** — a semantically meaningful construction phase containing one or more regions.
- **Palette** — the global registry of blockstate strings referenced by block data arrays.
- **Reader** — any implementation that parses MSF files.
- **Writer** — any implementation that produces MSF files.
- **MC data version** — the integer data version Minecraft assigns to each release, accessible via the game's version.json.

-----

## 2. File Structure

An MSF file consists of the following components in order:

```
[HEADER]              48 bytes, fixed, always at offset 0
[METADATA BLOCK]      located via header offset
[PALETTE BLOCK]       located via header offset
[LAYER INDEX BLOCK]   located via header offset
[REGION DATA]         one or more compressed region payloads
[ENTITY BLOCK]        located via header offset, optional
[BLOCK ENTITY BLOCK]  located via header offset, optional
[FILE CHECKSUM]       8 bytes, always at end of file
```

Components beyond the header MAY appear in any order in the file. Readers MUST use the absolute offsets in the header to locate each component and MUST NOT assume any ordering beyond the header appearing at offset 0 and the file checksum appearing at the final 8 bytes.

### 2.1 Data Types

All multi-byte integers are **little-endian** unless otherwise specified.

|Type|Size    |Description                                           |
|----|--------|------------------------------------------------------|
|u8  |1 byte  |Unsigned 8-bit integer                                |
|u16 |2 bytes |Unsigned 16-bit integer, little-endian                |
|u32 |4 bytes |Unsigned 32-bit integer, little-endian                |
|u64 |8 bytes |Unsigned 64-bit integer, little-endian                |
|i32 |4 bytes |Signed 32-bit integer, little-endian, two's complement|
|f32 |4 bytes |IEEE 754 single-precision float, little-endian        |
|f64 |8 bytes |IEEE 754 double-precision float, little-endian        |
|str |variable|u16 length prefix followed by length bytes of UTF-8   |

String values MUST be valid UTF-8. String length prefix counts bytes not characters. Empty strings are encoded as a u16 value of 0 with no following bytes.

Readers encountering a string value that is not valid UTF-8 MUST throw MsfParseException if the string is a blockstate string, biome identifier string, or any string whose value is required for correct parsing of subsequent data. Readers encountering invalid UTF-8 in metadata strings (name, author, description, tags, license identifier, source URL) SHOULD emit a warning and MAY substitute a replacement character or empty string rather than throwing, as metadata fields do not affect structural parsing.

### 2.2 Null Offsets

An offset field value of 0x00000000 indicates that the referenced block is not present in the file. Readers MUST treat a null offset as absence of that block. Writers MUST set offset fields to 0 when the corresponding feature flag bit is not set.

-----

## 3. Header

The header is exactly **48 bytes**, begins at file offset 0, and is **permanently frozen**. No future version of this specification will alter the size or meaning of any header field.

```
Offset  Size  Type    Field
------  ----  ------  -----
0       4     u8[4]   Magic bytes
4       2     u16     Major version
6       2     u16     Minor version
8       4     u32     Feature flags
12      4     u32     MC data version
16      4     u32     Metadata block offset
20      4     u32     Global palette offset
24      4     u32     Layer index offset
28      4     u32     Entity block offset
32      4     u32     Block entity block offset
36      4     u32     File size
40      8     u64     Header checksum
```

### 3.1 Magic Bytes

Bytes 0–3 MUST be `0x4D 0x53 0x46 0x21` (ASCII: `MSF!`).

Magic bytes are stored as an ordered sequence of 4 individual bytes at offsets 0, 1, 2, and 3. They are NOT interpreted as a multi-byte integer and the little-endian convention stated in Section 2.1 does NOT apply to them. Readers MUST compare the byte at offset 0 to 0x4D, offset 1 to 0x53, offset 2 to 0x46, and offset 3 to 0x21 individually. A mismatch at any position constitutes a magic byte mismatch regardless of the values at other positions.

If the file is shorter than 48 bytes, the reader MUST throw MsfParseException before performing any other validation, as the file cannot contain a valid header.

Readers MUST reject any file where the magic bytes do not match this value.

### 3.2 Version Fields

**Major version** (offset 4) is a u16 indicating the major format version. This specification defines major version **1**. A reader implementing this specification supports major version 1 only. A reader encounters an unsupported major version when the value of this field is anything other than 1, including 0. Upon encountering an unsupported major version, the reader MUST throw MsfVersionException immediately. The exception message MUST include the major version found in the file and the major version the reader supports. No partial read result is valid — callers MUST treat the file as unreadable. Major version 0 is not a pre-release designation and receives no special treatment.

**Minor version** (offset 6) is a u16 indicating the minor format version within the current major version. The minor version is a capability advertisement — it tells the reader what features the writer supports. It is not a compatibility gate. A V1 reader MUST attempt to read any V1 file regardless of minor version, skipping blocks and fields it does not recognize using block length prefixes. A reader MUST NOT reject a file solely because the minor version is higher than the version the reader implements. A reader MAY inform the user that the file contains features the reader does not support, but MUST still read all blocks it does understand.

Writers producing V1.0 files MUST write major version 1 and minor version 0.

### 3.3 Feature Flags

Feature flags (offset 8) is a u32 bitmask advertising optional capabilities present in this file.

```
Bit 0:   Has entities
Bit 1:   Has block entities
Bit 2:   Has biome data
Bit 3:   Has lighting hints
Bit 4:   Reserved in V1.0 — multi-region (not yet defined)
Bit 5:   Reserved in V1.0 — delta/diff format (not yet defined)
Bit 6:   Reserved in V1.0 — signal ports (not yet defined)
Bit 7:   Reserved in V1.0 — extended construction layers (not yet defined)
Bit 8:   Reserved in V1.0 — variant system (not yet defined)
Bit 9:   Reserved in V1.0 — palette substitution rules (not yet defined)
Bit 10–31: Reserved, MUST be 0 in V1.0
```

Readers MUST NOT reject a file solely because a feature flag bit is set that the reader does not support. Readers MUST skip blocks associated with unsupported feature flags using the block length field at the start of each block. Feature flag bit 2 is an exception to this skip mechanism — biome data is embedded within region payloads rather than stored as a standalone block with a header offset field; readers that do not support biome data simply do not parse the trailing bytes of each region payload. Section 7.3 defines the writer obligations and reader expectations specific to bit 2.

Readers encountering a V1.0 file — where the file's minor version equals 0 — with any of bits 4–31 set MUST emit a RESERVED_FLAG_SET warning identifying which reserved bits are set, as this indicates a non-conforming writer. Readers encountering a file whose minor version exceeds the reader's implemented minor version MUST NOT warn on reserved bits, as those bits may carry defined meaning in a later minor version the reader does not implement.

Writers producing V1.0 files MUST NOT set bits 4–9 or bits 10–31. If a caller provides a feature flags value with any of bits 4–9 or bits 10–31 set, the writer MUST clear those bits and MUST emit a RESERVED_FLAG_CLEARED warning via the warning mechanism.

Writers MUST set feature flag bits 0 and 1 if and only if the corresponding optional block (entity block and block entity block respectively) is present in the file. Feature flag bit 2 governs biome data embedded within region payloads rather than a standalone block; see Section 7.3 for the writer obligations specific to bit 2.

Feature flag bits 4, 5, 6, 7, 8, and 9 are all reserved in V1.0 — no block layouts are defined for these bits in this version of the specification. Writers MUST NOT set any of these bits in V1.0 files. Readers encountering any of bits 4–9 set in a V1.0 file MUST emit a RESERVED_FLAG_SET warning. Feature flag bit 7 additionally notes that the layer index block defined in Section 6 is required in all MSF files unconditionally and is not gated by bit 7.

### 3.4 MC Data Version

The MC data version (offset 12) is the integer data version of the Minecraft release the schematic was authored in. This value is sourced from Minecraft's version.json. For example, Minecraft 1.21 has data version 3953.

Readers MUST read this field before attempting to interpret any blockstate strings. Readers SHOULD emit a DATA_VERSION_MISMATCH warning if the file's MC data version differs from the currently loaded game version.

This field is in the header rather than the metadata block because readers may need it to determine whether to attempt reading the rest of the file.

### 3.5 Block Offsets

Offsets at positions 16–35 are absolute byte offsets from the beginning of the file to the start of each named block. A value of 0 indicates the block is not present. A reader encountering any non-zero offset that points to a byte position at or beyond the value of the file_size field MUST throw MsfParseException regardless of which block it references.

- **Metadata block offset** (16) — MUST NOT be 0. If a reader encounters 0 here it MUST throw MsfParseException immediately. All MSF files MUST contain a metadata block.
- **Global palette offset** (20) — MUST NOT be 0. If a reader encounters 0 here it MUST throw MsfParseException immediately. All MSF files MUST contain a global palette block.
- **Layer index offset** (24) — MUST NOT be 0. If a reader encounters 0 here it MUST throw MsfParseException immediately. All MSF files MUST contain a layer index block.
- **Entity block offset** (28) — MUST be 0 if feature flag bit 0 is not set. If a reader encounters a non-zero value here while feature flag bit 0 is not set, it MUST emit a FEATURE_FLAG_CONFLICT warning and MUST ignore the offset, treating the entity block as absent. The feature flag is authoritative for optional blocks. If a reader encounters a zero value here while feature flag bit 0 is set, it MUST emit a FEATURE_FLAG_CONFLICT warning. The reader MAY continue without the entity block or MAY throw MsfParseException — the reader's chosen behavior MUST be documented by the reader implementation. Writers MUST ensure the entity block offset is non-zero if and only if feature flag bit 0 is set. Writers MUST set the entity block offset to the correct absolute byte offset of the entity block before writing the header. Writing a non-zero feature flag bit 0 with a zero entity block offset, or a zero feature flag bit 0 with a non-zero entity block offset, constitutes a non-conforming write.
- **Block entity block offset** (32) — MUST be 0 if feature flag bit 1 is not set. The same rules that apply to the entity block offset apply to this field with respect to feature flag bit 1, including the writer obligations for flag/offset consistency.

### 3.5.1 Warning Mechanism

Warnings are non-fatal diagnostic conditions detected during parsing, writing, or placement. A conforming reader and writer implementation MUST expose a mechanism for callers to receive warnings produced during an operation. Warnings MUST NOT be emitted to stdout, stderr, or any logging framework by default — routing warnings is the caller's responsibility. Multiple warnings may be produced in a single operation and each MUST be delivered individually, not aggregated. The warning mechanism defined here for readers and writers MAY also be used by tools during placement operations; warning codes designated as advisory in the table below apply in that context.

In the Java reference implementation, MsfReader and MsfWriter MUST accept an optional `Consumer<MsfWarning>` parameter on all read and write methods respectively, and MUST invoke it once per warning produced. MsfWarning MUST carry at minimum: a warning code (MsfWarning.Code enum), a human-readable message, and the file byte offset where the condition was detected. For warnings produced during write operations or tool-side placement operations where no file byte offset is meaningful, the offset field MUST be set to -1. Callers that do not provide a consumer receive no warnings.

Defined warning codes and the conditions that trigger them:

|Code                     |Condition                                                                                                                                    |
|-------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
|`RESERVED_FLAG_SET`      |Reader detected reserved bits set — feature flag bits 4–31 in a V1.0 file (Section 3.3), or rotation compatibility bits 5–7 (Section 10.3)  |
|`RESERVED_FLAG_CLEARED`  |Writer cleared reserved bits provided by caller — applies to feature flags (Section 3.3) and rotation compatibility (Section 10.3)           |
|`FILE_SIZE_MISMATCH`     |Actual file length does not match the file_size field (Section 3.6)                                                                          |
|`FILE_CHECKSUM_FAILURE`  |File checksum verification failed (Section 11)                                                                                               |
|`FEATURE_FLAG_CONFLICT`  |Offset field state conflicts with the corresponding feature flag (Section 3.5)                                                               |
|`OFFSET_BEYOND_FILE_SIZE`|A non-zero block offset is at or beyond the file_size value (Section 3.5)                                                                    |
|`DATA_VERSION_MISMATCH`  |File MC data version differs from the currently active game version (Section 3.4)                                                            |
|`THUMBNAIL_INVALID`      |Thumbnail size is non-zero but the thumbnail bytes are not a valid PNG file (Section 5.2)                                                    |
|`EDITION_MISMATCH`       |The MC edition field in the metadata block indicates an edition different from the reader's implementation (Section 5.2). Advisory.          |
|`MIRROR_RISK`            |Tool is applying a mirror transformation to a schematic whose rotation compatibility flags do not declare the mirror valid (Section 10.3). Advisory — does not prevent placement.|
|`UNMET_DEPENDENCY`       |A layer is being placed whose dependency list includes layers not yet placed (Section 6.4). Advisory — does not prevent placement after user confirmation.|

A file size mismatch warning and a subsequent file checksum failure warning MUST be linked — the FILE_CHECKSUM_FAILURE warning message MUST note that the result is unreliable due to the prior FILE_SIZE_MISMATCH.

### 3.6 File Size

The file size field (offset 36) is a u32 containing the total byte length of the complete file, including the 8-byte file checksum appended at the end. Writers MUST set this field to the byte length of all content plus 8 before computing either checksum.

The u32 field imposes a maximum MSF file size of 4,294,967,295 bytes (approximately 4 GiB). Writers producing files that would exceed this size MUST throw MsfException before beginning file output. This is a V1 format constraint.

Readers SHOULD verify that the actual file size matches this field and SHOULD emit a FILE_SIZE_MISMATCH warning on mismatch. A mismatch indicates a truncated or corrupted file. When a file size mismatch warning has been emitted, any subsequent file checksum failure warning MUST note that the checksum result is unreliable due to the size mismatch, since the byte range input to the hash computation is defined by the file_size field which is known to be incorrect.

### 3.7 Header Checksum

The header checksum (offset 40) is a u64 containing the xxHash3-64 digest, with seed value 0, of the 40 bytes occupying header offsets 0 through 39 inclusive.

**Write procedure:** Writers MUST write all header fields at offsets 0–39 before computing the header checksum. The checksum is computed over exactly these 40 bytes. The 8 bytes at offsets 40–47 are not included in the input and their value at the time of computation is irrelevant. Writers MUST write the computed digest to offsets 40–47 as a u64 little-endian value as the final header write operation.

**Read procedure:** Readers MUST read all 48 header bytes into a buffer before acting on any header field. The validation sequence MUST be applied to the buffered bytes in the following strict order:

1. Compare magic bytes at offsets 0–3 individually against the expected values
2. Compute the xxHash3-64 digest with seed 0 of buffered bytes 0–39 and compare to the u64 at offsets 40–47
3. Inspect the major version field at offsets 4–5

No header field value other than the magic bytes and checksum MAY be used to make any parsing decision until all three validation steps have passed. A reader that acts on the major version, feature flags, or any offset field before the header checksum is verified is non-conforming.

A header checksum mismatch MUST cause the reader to throw MsfChecksumException and parsing MUST stop immediately. The header checksum covers only the header. A separate file checksum covers the full file contents and is stored at the end of the file.

-----

## 4. Global Palette Block

The global palette is a registry of all blockstate strings referenced by block data in the file. It is shared across all layers and regions.

### 4.1 Layout

```
Offset  Size    Type    Field
------  ------  ------  -----
0       4       u32     Block length (bytes following this field)
4       2       u16     Entry count
6       variable        Palette entries
```

### 4.2 Palette Entry Format

Each palette entry is:

```
u16     Blockstate string byte length
u8[]    Blockstate string (UTF-8, not null terminated)
```

### 4.3 Normative Requirements

**Palette ID 0 MUST be AIR.** The string value of palette entry 0 MUST be `minecraft:air`. This is a format invariant. Writers MUST always write `minecraft:air` as the first palette entry regardless of whether air appears in the block data. Readers MUST treat palette ID 0 as air without reading the string value.

**Palette entries MUST be deduplicated.** All palette entries MUST represent distinct blockstate strings. Writers MUST check for duplicates before writing and MUST throw MsfPaletteException if the input contains duplicate blockstate strings, identifying the duplicated string. Readers encountering duplicate palette entries MUST throw MsfParseException. A duplicate palette entry indicates a non-conforming writer and the palette cannot be trusted to correctly map block IDs to blockstate strings.

**Writers MUST follow canonical Minecraft property ordering within blockstate strings.** Writers MUST NOT reorder properties alphabetically or by any other scheme. Properties within a blockstate string MUST appear in the order they are registered in Minecraft's BlockStateDefinition for that block as of the MC data version declared in the header.

Example of canonical ordering for oak stairs:

```
minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]
```

**Entry count** is a u16. The maximum number of palette entries is 65535. Writers MUST NOT produce a palette with more than 65535 entries. If a writer is given more than 65535 entries it MUST throw IllegalArgumentException identifying the field name, the count provided, and the maximum permitted value. Silent truncation is not permitted. Palette IDs are zero-indexed u16 values.

**Block length** allows readers that do not wish to read the palette to skip it entirely by seeking forward block length + 4 bytes from the start of the palette block.

### 4.4 Blockstate String Format

Blockstate strings MUST use the namespaced identifier format introduced in Java Edition 1.13. Numeric block IDs and pre-1.13 blockstate formats are not valid in MSF V1.0.

Blockstate strings follow Minecraft's standard notation:

```
namespace:block_name[property=value,property=value,...]
```

- Namespace and block name are separated by a colon
- Properties are enclosed in square brackets
- Property key-value pairs are separated by commas
- No whitespace is permitted within the string
- Blocks with no properties are written without brackets: `minecraft:stone`

Writers MUST include all properties explicitly. Writers MUST NOT omit properties that have default values. A blockstate string with missing properties is invalid.

### 4.5 Palette ID Reference

Throughout this specification, a **palette ID** refers to a zero-indexed u16 value identifying an entry in the global palette. Palette ID 0 always refers to `minecraft:air`.

-----

## 5. Metadata Block

The metadata block contains descriptive information about the schematic. It is required in all MSF files.

### 5.1 Layout

```
u32     Block length (bytes following this field)
str     Name
str     Author
u64     Created timestamp (Unix epoch, seconds)
u64     Modified timestamp (Unix epoch, seconds)
str     Description
u16     Tag count
  [per tag]
  str   Tag value
u16     Contributor count
  [per contributor]
  str   Contributor name
str     License identifier (SPDX format, empty if none)
str     Source URL (empty if none)
u32     Thumbnail size (0 = no thumbnail)
  u8[]  Thumbnail bytes (PNG format)

[Placement metadata — see Section 10 for field semantics]
str     Anchor name
i32     Anchor offset X from minimum corner
i32     Anchor offset Y from minimum corner
i32     Anchor offset Z from minimum corner
u8      Canonical facing (0x00 North, 0x01 South, 0x02 East, 0x03 West)
u8      Rotation compatibility flags
u8      Has functional volume (0x00 = absent, 0x01 = present)
  [if has functional volume]
  i32   Functional volume min X (relative to anchor)
  i32   Functional volume min Y (relative to anchor)
  i32   Functional volume min Z (relative to anchor)
  i32   Functional volume max X (relative to anchor)
  i32   Functional volume max Y (relative to anchor)
  i32   Functional volume max Z (relative to anchor)

[Optional trailing fields — present only if block length covers them]
str     Tool name (empty string if unknown)
str     Tool version (empty string if unknown)
u8      Recommended placement mode
          0x00 = strict (place only within exact block footprint)
          0x01 = functional (clear functional volume before placement)
          0x02 = loose (place with best-effort, no clearance requirement)
          0xFF = unspecified (author made no recommendation)
u8      MC edition
          0x00 = Java Edition
          0x01 = Bedrock Edition
          0x02 = unknown
```

The placement metadata fields follow immediately after the thumbnail bytes. Section 10 defines the semantics and normative requirements for each placement field. The physical layout is authoritative here — Section 10 describes meaning, Section 5.1 describes position within the block.

### 5.2 Normative Requirements

**Name** MUST NOT be empty. Writers MUST provide a non-empty name.

**Author** MAY be empty. An empty author string indicates the author is unknown or anonymous — this is valid for schematics extracted from a world by a tool where no author can be determined. Writers that know the author SHOULD provide a non-empty value.

**Timestamps** are Unix epoch seconds as u64. The created timestamp SHOULD reflect when the schematic was first authored and SHOULD NOT be updated on subsequent saves. The modified timestamp MUST reflect the time of the most recent write. Writers that accept caller-provided timestamps MUST write the provided values without modification. Tools that manage schematic lifecycle SHOULD set created once at initial authorship and update modified on every save.

**License identifier** SHOULD use SPDX license identifiers where applicable (e.g. `CC-BY-4.0`, `MIT`). An empty string indicates no license is declared.

**Thumbnail** — the thumbnail size field is a u32 containing the byte length of the PNG data that follows. A value of 0 indicates no thumbnail is present and no bytes follow. A non-zero value indicates that exactly that many bytes of PNG data follow immediately. Readers MAY use this field to skip the thumbnail entirely by seeking forward thumbnail size bytes from the start of the thumbnail data. When thumbnail size is non-zero but the bytes do not constitute a valid PNG file, readers MUST emit a THUMBNAIL_INVALID warning and MUST skip exactly thumbnail size bytes, continuing parsing from the field immediately following the thumbnail data. Readers MUST NOT reject the file solely because the thumbnail is malformed.

**Tags** are freeform UTF-8 strings. No controlled vocabulary is defined in V1. Tags are case-sensitive.

**Optional trailing fields** — writers MAY omit the optional trailing fields (tool name, tool version, recommended placement mode, MC edition) by setting the block length to cover only the fields through the has functional volume indicator and any present functional volume coordinates. Readers MUST stop parsing the metadata block at the block length boundary. Readers MUST treat any absent optional trailing fields as their default values: tool name and tool version default to empty strings, recommended placement mode defaults to 0xFF (unspecified), and MC edition defaults to 0x02 (unknown).

**Tool name** and **tool version** are informational only. Readers MUST NOT make any parsing decision based on these fields.

**Recommended placement mode** is advisory. Tools SHOULD use the value as the default placement mode but MAY allow the user to override it. A value of 0xFF indicates the author made no recommendation. Values other than 0x00, 0x01, 0x02, and 0xFF are reserved; readers encountering a reserved value MUST treat it as 0xFF.

**MC edition** is advisory. Readers SHOULD emit an EDITION_MISMATCH warning if the MC edition field indicates an edition different from the one the reader implements, as blockstate strings, entity identifiers, and NBT structure may not be valid in the reader's edition.

-----

## 6. Layer Index Block

The layer index defines the semantic construction layers of the schematic. Layers carry meaning about construction sequence and logical grouping. This block is required in all MSF files.

### 6.1 Purpose

Layers represent distinct construction phases such as terrain clearing, foundation, frame, exterior, interior, detailing, and landscaping. The format defines what layers exist and what blocks belong to each. Tools define how layers are visualized and interacted with.

Viewport slicing is a tool feature. Semantic layers are a format feature. These are distinct concerns and MUST NOT be conflated.

Feature flag bit 7 ("Has construction layers") is reserved in V1.0. The layer index block is required unconditionally in all MSF files and is not gated by bit 7. Bit 7 is reserved for a future extended construction layer block not yet defined in this version of the specification.

> **Note (non-normative):** The u8 layer count and region count fields impose a maximum of 255 layers and 255 regions per layer in V1.0. For the vast majority of schematics this is sufficient. A future minor version may introduce a large-structure mode using u16 or u32 counts via a reserved feature flag. Writers encountering these limits in V1.0 MUST throw rather than truncate — see Section 6.4.

### 6.2 Layout

```
u32     Block length
u8      Layer count (1–255)
  [per layer]
  u8    Layer ID
  str   Layer name
  u8    Construction order index
  u8    Dependency count
    u8[]  Dependent layer IDs
  u8    Flags
  u8    Region count
    [per region]
    ... (see Section 7)
```

### 6.3 Layer Flags

```
Bit 0:  Optional layer (may be skipped during placement)
Bit 1:  Draft (incomplete, not ready for placement)
Bit 2–7: Reserved, MUST be 0
```

Readers encountering bits 2–7 set in any layer flags field MUST NOT reject the file. Readers SHOULD emit a RESERVED_FLAG_SET warning identifying the layer ID and the set bits, as this may indicate a non-conforming writer. Readers MUST otherwise treat reserved bits as if they were 0. Writers MUST mask layer flags values to bits 0–1 before writing. If a caller provides layer flags with bits 2–7 set, writers MUST clear those bits and MUST emit a RESERVED_FLAG_CLEARED warning.

### 6.4 Normative Requirements

**Layer IDs** MUST be unique within a file. Layer ID values are author-assigned u8 values.

**Construction order index** defines the intended placement sequence. Lower values are placed first. Multiple layers MAY share the same construction order index indicating they can be placed in parallel.

**Dependencies** list layer IDs that MUST be placed before this layer. Tools SHOULD emit an UNMET_DEPENDENCY warning when a layer is placed without its declared dependencies. Tools MUST NOT prevent placement of a layer solely because its declared dependencies have not been placed — tools MUST allow placement to proceed once the user confirms after receiving the warning. A layer MUST NOT list itself as a dependency. Circular dependencies are invalid and writers MUST NOT produce them.

**Layer count** is a u8. The minimum layer count is 1. The maximum layer count is 255. Every MSF file MUST contain at least one layer. Writers given more than 255 layers MUST throw IllegalArgumentException identifying the field name, the count provided, and the maximum permitted value of 255.

**Region count** per layer is a u8. The minimum region count per layer is 1. The maximum region count per layer is 255. A layer with zero regions is invalid. Writers MUST NOT produce a layer with a region count of 0. Writers given more than 255 regions for a single layer MUST throw IllegalArgumentException identifying the field name, the count provided, and the maximum permitted value of 255. Readers encountering a layer with a region count of 0 MUST throw MsfParseException.

-----

## 7. Region Data

Each region is a three-dimensional subvolume of block data associated with a layer. A layer contains one or more regions.

### 7.1 Region Header

```
str     Region name
i32     Origin X (relative to schematic anchor)
i32     Origin Y (relative to schematic anchor)
i32     Origin Z (relative to schematic anchor)
u32     Size X
u32     Size Y
u32     Size Z
u8      Compression type
u32     Compressed data length
u32     Uncompressed data length
u8[]    Compressed region payload
```

Size X, Size Y, and Size Z MUST each be at least 1. A region with a size of 0 on any axis is invalid and produces a block count of zero which is meaningless. Writers MUST NOT produce a region where any size field is 0. Readers encountering a region where any size field is 0 MUST throw MsfParseException before attempting to decompress or parse the region payload.

The compressed data length field contains the byte length of the compressed region payload that follows immediately in the file. Writers MUST set this field to the exact byte length of the compressed payload. Readers MUST use this field to locate the end of the compressed payload and to bound how many bytes are consumed from the file stream. Readers MUST throw MsfParseException if the number of bytes consumed from the stream for the compressed payload does not match this field — an incorrect compressed data length will misalign all subsequent file parsing. This field has the same structural significance as the packed array length field in Section 7.5.

The uncompressed data length field contains the expected byte length of the payload after decompression. Writers MUST set this field to the exact byte length of the uncompressed payload. Readers SHOULD verify that the decompressed output byte length matches this field and MUST throw MsfParseException if a mismatch is detected.

> **Note (non-normative):** Writers producing regions from large builds SHOULD consider splitting volumes exceeding approximately 128–256 blocks per axis into multiple regions within a layer. Very large single regions increase peak memory requirements during decompression and reduce the granularity available to tools for visibility toggling and incremental loading. This is guidance only — the format imposes no maximum region size beyond the u32 field constraints.

### 7.2 Compression Types

```
0x00    None (raw)
0x01    zstd (default, RECOMMENDED)
0x02    lz4
0x03    brotli
```

Writers SHOULD use zstd compression. Readers MUST support all four compression types.

Compression type values 0x04 through 0xFF are reserved. Readers encountering an unrecognized compression type value MUST throw MsfParseException. Readers MUST NOT attempt to interpret a region payload whose compression type is unrecognized — a payload with an unknown compression type cannot be decoded safely, and there is no fallback. Future minor versions MAY define additional compression type values. A reader that does not implement a compression type defined in a later minor version MUST still throw MsfParseException on encountering it, as the payload cannot be safely decoded regardless of minor version.

### 7.3 Region Payload

After decompression the region payload contains:

```
u8      Bits per entry
u32     Packed array length (count of u64 words)
u64[]   Packed block data
```

Writers MUST set feature flag bit 2 if biome data is present in any region. Writers MUST NOT set feature flag bit 2 unless biome data is present in all regions. Biome data is all-or-nothing across the file — either all regions carry biome data (bit 2 set) or no regions carry biome data (bit 2 clear). Readers MUST expect biome data in every region payload when feature flag bit 2 is set and MUST treat its absence in any region as an MsfParseException. When feature flag bit 2 is not set, no region payload contains biome data and readers MUST NOT attempt to parse it.

Readers verify biome data presence by checking whether bytes remain in the decompressed payload after consuming the packed block data. If no bytes remain in the decompressed payload at the point where the biome palette entry count u16 should be read, the biome section is absent. Readers MUST throw MsfParseException in this case. Readers MUST NOT silently stop parsing a region payload after the packed block data when biome data is expected — a reader that returns without attempting to read the biome section is non-conforming when feature flag bit 2 is set.

Biome data, when present, immediately follows the packed block data:

```
u16     Biome palette entry count
  [per biome entry]
  str   Biome identifier string (e.g. "minecraft:forest")
u8      Biome bits per entry
u32     Biome packed array length
u64[]   Packed biome data
```

### 7.4 Block Ordering

Blocks are stored in **YZX order** — Y is the outermost index, Z is the middle index, X is the innermost index. For a region of size X × Y × Z the block at position (x, y, z) is at array index:

```
index = y * (size_z * size_x) + z * size_x + x
```

Y-major ordering is chosen for cache-friendly vertical operations such as lighting propagation and column-based processing.

### 7.5 Bit Packing

Palette IDs are packed into u64 words. The number of bits used per entry is:

```
bits_per_entry = max(1, ceil(log2(palette_entry_count)))
```

In this formula, `palette_entry_count` is the total number of entries in the global palette, including the mandatory `minecraft:air` entry at palette ID 0. Writers MUST use the global palette entry count when computing bits_per_entry so that every valid global palette ID can be represented in the packed array. Writers MUST NOT use a region-specific or deduplicated entry count.

This formula is defined only for palette_entry_count ≥ 1. Since palette entry 0 (`minecraft:air`) is always present per Section 4.3, the minimum global palette entry count is 1. A region payload with a bits_per_entry value of 0 is invalid and MUST NOT be produced by writers. Readers encountering bits_per_entry = 0 MUST throw MsfParseException.

Writers MUST store the actual bits_per_entry value explicitly. Readers MUST use the stored bits_per_entry value and MUST NOT derive it independently.

The packed array length field MUST equal:

```
packed_array_length = ceil((size_x * size_y * size_z * bits_per_entry) / 64)
```

Writers MUST perform this computation using integer arithmetic of sufficient width to avoid overflow. In Java, all dimension and bits-per-entry values MUST be widened to `long` before multiplication — multiplying these values in `int` arithmetic will silently overflow for large regions. The result MUST fit in a u32 field. Writers MUST throw MsfException if the computed packed array length exceeds 4,294,967,295.

Writers MUST compute and store this value correctly. Readers SHOULD verify that the stored packed array length matches the value derived from the region dimensions and bits_per_entry. If a mismatch is detected, readers MUST throw MsfParseException — a wrong packed array length would cause the reader to consume the wrong number of bytes, misaligning all subsequent parsing. Readers MUST also perform this verification using arithmetic of sufficient width to avoid overflow. In Java, all dimension values MUST be widened to `long` before multiplication when computing the expected packed array length for verification.

Entries are packed from the least significant bit of each u64 word. When an entry would span a word boundary it begins in the next word. The remaining bits of the previous word are set to 0 and MUST be ignored by readers.

Packed block data values MUST be valid palette IDs — each value MUST be less than the global palette entry count. Writers MUST NOT produce packed block data containing out-of-range palette IDs. Readers MUST throw MsfParseException if any unpacked value in the block data references a palette ID that does not exist in the global palette. Silent substitution with air or any other fallback value is not permitted.

> **Note (non-normative) — Bit-packing worked example**
>
> This example shows the complete packing computation for a concrete region. All values here are illustrative — the canonical binary test vector is the reference file committed to `docs/examples/` in the repository.
>
> **Setup:** A 3×3×3 region (27 blocks) with a global palette of 5 entries:
>
> | ID | Blockstate              |
> |----|-------------------------|
> | 0  | `minecraft:air`         |
> | 1  | `minecraft:stone`       |
> | 2  | `minecraft:oak_log`     |
> | 3  | `minecraft:dirt`        |
> | 4  | `minecraft:gravel`      |
>
> **Step 1 — bits_per_entry:**
>
> ```
> bits_per_entry = max(1, ceil(log2(5))) = max(1, 3) = 3
> ```
>
> Three bits are required to represent palette IDs 0–4.
>
> **Step 2 — packed_array_length:**
>
> ```
> packed_array_length = ceil(3 × 3 × 3 × 3 / 64) = ceil(81 / 64) = 2
> ```
>
> Two u64 words are required to hold 81 bits of packed data.
>
> **Step 3 — Block ordering**
>
> Blocks are indexed in YZX order. For this region the 27 array indices map as follows (y outermost, x innermost):
>
> | Index range | y | z | x values |
> |-------------|---|---|----------|
> | 0–2         | 0 | 0 | 0, 1, 2  |
> | 3–5         | 0 | 1 | 0, 1, 2  |
> | 6–8         | 0 | 2 | 0, 1, 2  |
> | 9–17        | 1 | 0–2 | 0–2   |
> | 18–26       | 2 | 0–2 | 0–2   |
>
> Suppose the region contains: all 9 blocks at y=0 are stone (ID 1), all 9 at y=1 are oak_log (ID 2), all 9 at y=2 are air (ID 0). The 27 IDs in array order are:
>
> ```
> [1,1,1,1,1,1,1,1,1, 2,2,2,2,2,2,2,2,2, 0,0,0,0,0,0,0,0,0]
>  ^---- y=0 ----^    ^---- y=1 ----^    ^---- y=2 ----^
> ```
>
> **Step 4 — Packing into u64 words**
>
> Each palette ID occupies exactly 3 bits. Entry N occupies bits [N×3, N×3+2] in the packed array, with the ID's least significant bit at the lower bit position. Packing proceeds from bit 0 of word 0 upward.
>
> - **Entries 0–8 (ID 1 = binary `001`):** Occupy bits 0–26 of word 0. Because ID 1 has only its bit 0 set, bits 0, 3, 6, 9, 12, 15, 18, 21, and 24 are set in word 0.
> - **Entries 9–17 (ID 2 = binary `010`):** Occupy bits 27–53 of word 0. Because ID 2 has only its bit 1 set, bits 28, 31, 34, 37, 40, 43, 46, 49, and 52 are set in word 0.
> - **Entries 18–20 (ID 0 = binary `000`):** Occupy bits 54–62 of word 0. No bits set.
> - **Entry 21 (ID 0 = binary `000`) — word boundary span:** Entry 21 would occupy bits 63–65. Bit 63 falls in word 0; bits 64–65 fall in word 1. The low bit of entry 21's value (0) goes to bit 63 of word 0; the remaining two bits of the value (both 0) go to bits 0–1 of word 1. Bit 63 of word 0 is not set. This is the word boundary case: the entry starts in word 0 and continues in word 1.
> - **Entries 22–26 (ID 0):** Occupy bits 2–16 of word 1. No bits set.
> - **Bits 17–63 of word 1:** Padding zeros, ignored by readers.
>
> Word 0 has set bits at positions: 0, 3, 6, 9, 12, 15, 18, 21, 24, 28, 31, 34, 37, 40, 43, 46, 49, 52.
> Word 1 is all zeros.
>
> The packed array stored in the file is these two u64 words, each in little-endian byte order. A reader unpacks by extracting 3 bits at a time starting from bit 0 of word 0, skipping to the next word when a 3-bit window would span the word boundary (not consuming the straddle — the remainder of the current word is discarded and the entry begins at bit 0 of the next word).

### 7.6 Biome Data

Biome data when present is stored at 4×4×4 section resolution matching Minecraft's internal biome storage. The biome palette is local to the region and independent of the global block palette. Biome palette IDs are the index into the region's local biome palette array.

The biome palette entry count MUST be at least 1. A biome palette entry count of 0 is invalid. Writers MUST NOT produce biome data with a biome palette entry count of 0. Readers encountering a biome palette entry count of 0 MUST throw MsfParseException.

For a region of size X × Y × Z, the number of biome entries is:

```
biome_entry_count = ceil(X/4) * ceil(Y/4) * ceil(Z/4)
```

The biome packed array length MUST equal:

```
biome_packed_array_length = ceil((biome_entry_count * biome_bits_per_entry) / 64)
```

Writers MUST perform this computation using arithmetic of sufficient width to avoid overflow. In Java, intermediate values MUST be widened to `long` before multiplication. Writers MUST compute and store both the biome entry count and biome packed array length correctly. Readers SHOULD verify the biome packed array length against the value derived from the region dimensions and biome_bits_per_entry, using overflow-safe arithmetic — in Java, all intermediate values MUST be widened to `long` before multiplication — and MUST throw MsfParseException on mismatch.

Packed biome data values MUST be valid biome palette IDs — each value MUST be less than the biome palette entry count for that region. Writers MUST NOT produce packed biome data containing out-of-range biome palette IDs. Readers MUST throw MsfParseException if any unpacked value in the biome data references a biome palette ID that does not exist in the region's local biome palette.

### 7.7 Coordinate System

All region coordinates are relative to the schematic anchor point declared in the placement metadata (see Section 10). Coordinates use Minecraft's standard axis orientation: X increases east, Y increases up, Z increases south.

-----

## 8. Entity Block

The entity block is present when feature flag bit 0 is set.

### 8.1 Layout

```
u32     Block length
u32     Entity count
  [per entity]
  f64   Position X
  f64   Position Y
  f64   Position Z
  f32   Yaw
  f32   Pitch
  str   Entity type (e.g. "minecraft:armor_stand")
  u16   NBT payload length
  u8[]  NBT payload (binary NBT, excludes position and type)
```

### 8.2 Normative Requirements

**Position and type MUST be stored in typed fields** and MUST NOT be duplicated in the NBT payload. The NBT payload contains only entity-specific data that is not captured by typed fields. The entity type in Minecraft NBT is stored as the `id` tag. Writers MUST NOT include the `id` tag in the NBT payload, as the entity type is already captured in the typed entity type field. Readers encountering an `id` tag in an entity NBT payload MUST ignore it. Its presence indicates a non-conforming writer but does not affect parsing. Readers MUST NOT throw MsfParseException solely because excluded tags are present in a payload.

**UUIDs MUST be stripped from all entities** before writing. Writers MUST NOT include UUID data in the NBT payload. Writers MUST strip the entity's own UUID from the NBT payload before writing. UUIDs are stored as an `int[4]` tag named `UUID` in Minecraft 1.16 and later, and as `long` tags named `UUIDMost` and `UUIDLeast` in earlier versions. Writers MUST handle both representations. Owner UUIDs and other relational UUID references stored in entity NBT — such as tame owner UUIDs, leash holder UUIDs, and anger target UUIDs — SHOULD also be stripped, as these references will be invalid in a new world context. Readers generating entities on paste MUST assign a new UUID to the entity itself. Restoring owner UUIDs and relational references is a tool concern beyond the scope of this specification. This requirement prevents UUID collisions when the same schematic is pasted multiple times.

**Entity count MUST be at least 1.** Writers MUST NOT write an entity block with an entity count of 0. If a writer has no entities to write, it MUST NOT set feature flag bit 0 and MUST NOT write an entity block. Readers encountering an entity block with an entity count of 0 MUST emit a FEATURE_FLAG_CONFLICT warning, MUST treat the entity block as absent, and MUST continue parsing the remainder of the file. This indicates a non-conforming writer.

**Entity count is a u32.** No maximum below the u32 ceiling is imposed by this field. The effective maximum number of entities is bounded by the u32 block length field, which limits the entity block to 4,294,967,295 bytes total. Writers MUST NOT produce an entity block whose total byte length exceeds this limit, consistent with Section 3.6.

**NBT payload size limit.** The NBT payload for a single entity MUST NOT exceed 65535 bytes after UUID stripping and exclusion of typed fields. Writers MUST throw IllegalArgumentException identifying the entity type, the payload size computed, and the maximum permitted value of 65535, if the stripped NBT payload for any entity exceeds this limit. Silent truncation is not permitted.

-----

## 9. Block Entity Block

The block entity block is present when feature flag bit 1 is set.

### 9.1 Layout

```
u32     Block length
u32     Block entity count
  [per block entity]
  i32   Position X (relative to anchor)
  i32   Position Y (relative to anchor)
  i32   Position Z (relative to anchor)
  str   Block entity type (e.g. "minecraft:chest")
  u16   NBT payload length
  u8[]  NBT payload (binary NBT, excludes position tags and id tag)
```

### 9.2 Normative Requirements

**Position and type MUST be stored in typed fields** and MUST NOT be duplicated in the NBT payload. The block entity type in Minecraft NBT is stored as the `id` tag. Writers MUST NOT include the `id` tag in the NBT payload, as the block entity type is already captured in the typed block entity type field. Position coordinates are similarly excluded — writers MUST NOT include `x`, `y`, or `z` position tags in the NBT payload. Readers encountering `id`, `x`, `y`, or `z` tags in a block entity NBT payload MUST ignore them. Their presence indicates a non-conforming writer but does not affect parsing. Readers MUST NOT throw MsfParseException solely because excluded tags are present in a payload.

**UUIDs MUST be stripped** from block entity NBT payloads. Writers MUST strip UUIDs from block entity NBT payloads before writing, handling both the `int[4]` tag named `UUID` used in Minecraft 1.16 and later and the `long` tags named `UUIDMost` and `UUIDLeast` used in earlier versions. Readers generating block entities on paste MUST NOT preserve any UUID data from the payload. The rationale and full scoping of this requirement are stated in Section 8.2.

**Block entity count MUST be at least 1.** Writers MUST NOT write a block entity block with a block entity count of 0. If a writer has no block entities to write, it MUST NOT set feature flag bit 1 and MUST NOT write a block entity block. Readers encountering a block entity block with a block entity count of 0 MUST emit a FEATURE_FLAG_CONFLICT warning, MUST treat the block entity block as absent, and MUST continue parsing the remainder of the file. This indicates a non-conforming writer.

**Block entity count is a u32.** No maximum below the u32 ceiling is imposed by this field. The effective maximum number of block entities is bounded by the u32 block length field, which limits the block entity block to 4,294,967,295 bytes total. Writers MUST NOT produce a block entity block whose total byte length exceeds this limit, consistent with Section 3.6.

**NBT payload size limit.** The NBT payload for a single block entity MUST NOT exceed 65535 bytes after UUID stripping and exclusion of typed fields. Writers MUST throw IllegalArgumentException identifying the block entity type, the payload size computed, and the maximum permitted value of 65535, if the stripped NBT payload for any block entity exceeds this limit. Silent truncation is not permitted.

-----

## 10. Placement Metadata

Placement metadata defines how the schematic is oriented and anchored in world space. The physical layout of placement metadata fields within the metadata block is defined in Section 5.1. This section defines the semantics and normative requirements for each field.

### 10.1 Anchor Point

The anchor point is an author-declared named point that defines where the schematic attaches to the world on placement. All region coordinates are relative to the anchor point.

Writers MUST declare an explicit anchor point. Writers MUST NOT default to the minimum corner without author intent. The anchor point is stored as an i32 offset from the minimum corner of the bounding box on each axis. The anchor name is a human-readable label such as "entrance", "redstone input", or "center" that tools MAY display to the user during placement.

Anchor offset values MAY be negative, indicating a point outside the bounding box in the negative direction on that axis. Negative anchor offsets are valid and MUST NOT be rejected by readers. Writers MUST throw IllegalArgumentException if given anchor offset values outside the i32 range, consistent with Appendix E. The constraint that writers MUST NOT default to the minimum corner without author intent is a writer obligation and is not detectable by readers — an offset of (0, 0, 0) is a valid anchor position when declared with author intent.

### 10.2 Canonical Facing

Canonical facing declares the cardinal direction the schematic was authored to face, stored as a u8 in the metadata block layout. Valid values:

```
0x00    North
0x01    South
0x02    East
0x03    West
```

Any value other than 0x00–0x03 is invalid. Readers encountering an invalid canonical facing value MUST throw MsfParseException. Writers MUST validate the canonical facing value before writing. If given a value other than 0x00–0x03, writers MUST throw IllegalArgumentException identifying the field name and the value provided.

Rotation is computed as the angular delta between the canonical facing declared in the schematic and the target facing requested at placement time. Tools MUST read the canonical facing from the schematic before computing any rotation or mirror transformation. The canonical facing is the reference direction from which all rotation deltas are measured. Tools MUST transform all direction-dependent blockstate properties — including but not limited to facing, shape, and half properties on stairs, trapdoors, pistons, observers, and repeaters — when applying rotation or mirror.

### 10.3 Rotation Compatibility

The rotation compatibility field is a u8 bitmask stored in the metadata block layout declaring which transformations the author has declared valid for this schematic:

```
Bit 0:  Rotate 90° valid
Bit 1:  Rotate 180° valid
Bit 2:  Rotate 270° valid
Bit 3:  Mirror X valid
Bit 4:  Mirror Z valid
Bit 5–7: Reserved, MUST be 0
```

Readers encountering bits 5–7 set in the rotation compatibility field MUST NOT reject the file. Readers SHOULD emit a RESERVED_FLAG_SET warning, as set reserved bits may indicate a non-conforming writer. Readers MUST otherwise treat reserved bits as if they were 0. Writers MUST mask the rotation compatibility value to bits 0–4 before writing. If a caller provides a value with bits 5–7 set, writers MUST clear those bits and MUST emit a RESERVED_FLAG_CLEARED warning.

When a transformation bit is set, the author has declared that transformation valid; tools MAY apply it. When a transformation bit is not set, the transformation has not been author-validated; tools MUST warn the user before applying it. Tools MUST NOT silently apply an undeclared transformation — explicit user confirmation is required. Tools MUST NOT prevent the user from applying any transformation; restriction of transformations is never required by this specification.

When a tool applies a mirror transformation to a schematic whose rotation compatibility flags do not declare the mirror valid, the tool MUST emit a MIRROR_RISK warning. Redstone contraptions with axis-dependent behavior SHOULD declare restricted rotation compatibility.

### 10.4 Bounding Box and Functional Volume

**Bounding box** is the minimum axis-aligned box enclosing all non-air blocks. It is derived by readers and MUST NOT be stored explicitly.

**Functional volume** is an optional author-declared region that must be unobstructed for the schematic to function correctly. It may extend beyond the bounding box. Its presence is indicated by the has functional volume u8 field in the metadata block layout — a value of 0x01 indicates the six i32 coordinate fields follow; a value of 0x00 indicates they are absent. Any value other than 0x00 or 0x01 is invalid and readers MUST throw MsfParseException.

Functional volume coordinates are relative to the anchor point. The min values MUST be less than or equal to the corresponding max values on each axis. Writers MUST NOT produce a functional volume where any min coordinate exceeds the corresponding max coordinate.

When a tool applies rotation or mirroring to a schematic during placement, it MUST apply the same rotation or mirror transformation to the functional volume coordinates using the same transformation formulas applied to block positions. The functional volume MUST remain axis-aligned after transformation — tools MUST recompute the min and max coordinates of the functional volume by transforming all six corner coordinate values and taking the resulting axis-aligned bounding box.

Tools SHOULD check the functional volume for obstructions before placement and SHOULD warn the user if obstructions are found.

-----

## 11. File Checksum

The final 8 bytes of every MSF file are a u64 xxHash3-64 digest, with seed value 0, of all file bytes from offset 0 to offset (file_size - 9) inclusive — equivalently, all bytes except the final 8.

**Write procedure:** Writers MUST compute this digest after all other file content has been written, including the fully populated header with the file_size field set to total content length plus 8. The file checksum covers all 48 header bytes including the header checksum at offsets 40–47. Writers MUST therefore compute and write the header checksum before computing the file checksum. The correct write sequence is: write all content blocks, set the file_size field, compute and write the header checksum, then compute and write the file checksum as the absolute final write operation before closing the file.

**Read procedure:** Readers MUST verify the file checksum after verifying the header checksum. Readers MUST use the value of the file_size header field, not the actual file length, as the upper bound when computing the verification checksum — the input range is bytes 0 through (file_size - 9) inclusive. A file checksum failure is a mandatory warning and a permitted stop — it is NOT a mandatory stop. Readers MUST emit a FILE_CHECKSUM_FAILURE warning via the warning mechanism when file checksum verification fails. Readers MUST NOT silently ignore a file checksum failure. The reference Java implementation MUST throw MsfChecksumException by default on file checksum failure, while providing a reader configuration option that allows callers to continue past file checksum failure at their explicit request.

If a FILE_SIZE_MISMATCH warning was emitted during the same read operation, the FILE_CHECKSUM_FAILURE warning message MUST note that the checksum result is unreliable due to the size mismatch, since the byte range input to the hash computation is defined by the file_size field which is known to be incorrect.

-----

## 12. Versioning Contract

This section is normative and constitutes a binding commitment of this specification to every implementer. The rules in this section are not conventions — they are guarantees. Mod developers, tool authors, and library maintainers can depend on these guarantees without reservation.

### 12.1 The Append-Only Rule

**The MSF format evolves by addition only. Features are never removed.**

This is the foundational rule from which all other versioning rules derive. Its purpose is to ensure that code written against any version of this specification is never broken by a future version of this specification. An implementer who supports MSF today will not be required to update their codebase due to feature removal tomorrow.

Specifically:

**Feature flag bits are never reassigned.** A bit assigned meaning in any minor version retains that meaning permanently. Reserved bits MAY be assigned meaning in future minor versions but MUST NOT be given meaning that conflicts with their previously reserved status of being ignored.

**Block types are never removed.** A block type defined in any minor version of the V1 specification MUST remain parseable in all future V1 minor versions. The block length prefix guarantees that readers can always skip unknown blocks mechanically — this mechanism exists precisely to support forward compatibility.

**Field meanings within defined blocks are never changed.** Existing fields at existing offsets within a defined block retain their meaning permanently. New optional fields MAY be appended to existing blocks in future minor versions. Readers encountering a block longer than they expect MUST skip the additional bytes using the block length field rather than rejecting the file.

**Deprecation is advisory only.** Future minor versions MAY mark features as deprecated in the spec text, indicating that writers SHOULD NOT emit them and new implementations SHOULD NOT implement them. Deprecated features are never removed from the binary contract. Readers MUST continue to handle deprecated features correctly regardless of deprecation status.

### 12.2 The Frozen Header

**The V1 header is permanently frozen at 48 bytes.**

No future version of this specification under major version 1 will alter the size, position, or meaning of any field in the 48-byte header. This includes reserved fields — they remain reserved and zero-valued in all V1 minor versions unless explicitly assigned meaning by a future minor version, in which case they are assigned new meaning without altering any existing field.

### 12.3 Universal V1 Compatibility

**Any V1 reader can read any V1 file.**

The minor version field indicates what features the writer used. It does not gate what the reader can attempt. A reader implementing V1.0 encountering a V1.7 file MUST attempt to read it. It reads what it understands, skips what it does not, and MAY inform the user which features are present but unsupported. It MUST NOT reject the file.

Rejection criteria are divided into two tiers:

**Mandatory rejection — reader MUST stop and MUST NOT return partial data:**

- Magic bytes do not match `MSF!`
- Header checksum fails
- Major version is not supported

**Permitted rejection — reader MUST warn, MAY stop:**

- File checksum fails

A reader MUST NOT add additional mandatory rejection criteria. A reader that stops on file checksum failure MUST do so only after emitting the appropriate warning and only if not configured by the caller to continue.

### 12.4 When a Major Version Increment Is Justified

A major version increment is the emergency exit. It exists for situations where the append-only rule genuinely cannot solve a problem — where a flaw in the core format is so fundamental that it cannot be corrected without a structural change that breaks existing readers.

The bar for a major version increment is extraordinarily high. The following do NOT justify a major version increment:

- A feature turned out to be poorly designed — deprecate it and add a better replacement
- A Minecraft update made a feature less useful — the feature remains in the format, its data becomes advisory
- A better encoding scheme was discovered — add it as a new optional block alongside the old one

A major version increment MAY be justified only when:

- The header layout itself must change in a way that makes V1 files structurally unreadable by a V2 reader or vice versa
- A format invariant — such as the meaning of palette ID 0 or the block ordering scheme — must change in a way that causes silent data corruption rather than clean failure

A major version increment fractures the ecosystem. Every tool must update. Every file format must either migrate or become a second-class citizen. This cost is real and must be weighed against the severity of the problem being solved. Major version increments should be treated as architectural failures of the original design, not as routine upgrade mechanisms.

### 12.5 Block Length Prefix Requirement

**Every block in an MSF file MUST begin with a u32 block length field.**

The block length field counts the number of bytes following that field to the end of the block. This field is mandatory for all blocks including blocks defined in future minor versions. It is the mechanical guarantee that enables universal V1 compatibility — without it the append-only rule cannot be enforced in practice.

Writers producing new block types in future minor versions MUST include a block length prefix. A block type without a length prefix is a breaking change and violates this specification regardless of minor version.

-----

## Appendix A — Implementer Compatibility Guarantee

This appendix summarizes the practical commitments this specification makes to implementers in plain language.

**If you implement MSF today:**

- Your reader will never encounter a V1 file it cannot at least partially read
- Your writer will never produce a file that becomes invalid due to spec changes
- Fields you write today will mean the same thing in every future V1 reader
- Features you implement will never be removed from the format

**If a future minor version adds a feature you haven't implemented:**

- Your reader skips the new block via its length prefix
- Your reader continues reading everything it understands
- Your reader does not need to be updated to remain correct
- You may optionally update to support the new feature when it suits your timeline

**The only things that will ever require a reader update:**

- A major version increment — which represents an architectural emergency not a routine upgrade
- A new Minecraft data version — which is a Minecraft concern not an MSF concern

-----

## Appendix B — Summary of All Block Types

|Block             |Required|Feature Flag|Offset Field             |
|------------------|--------|------------|-------------------------|
|Metadata          |Yes     |—           |Metadata block offset    |
|Global Palette    |Yes     |—           |Global palette offset    |
|Layer Index       |Yes     |—           |Layer index offset       |
|Entity Block      |No      |Bit 0       |Entity block offset      |
|Block Entity Block|No      |Bit 1       |Block entity block offset|

-----

## Appendix C — Canonical Minecraft Ordering Reference

Blockstate property ordering follows the registration order in Minecraft's BlockStateDefinition as of the MC data version declared in the header. The canonical ordering is determined by the order in which properties are passed to `BlockStateDefinition.Builder.add()` in Minecraft's source, which corresponds to the order properties appear in the game's generated block reports.

Implementations SHOULD derive property ordering from the game's data generator output rather than hardcoding property lists. Minecraft's data generator (invoked with `--reports`) produces a `blocks.json` file in the generated reports directory; the property order within each block's `properties` object in that file reflects canonical ordering for that data version. Property sets and ordering may change between Minecraft releases, so derivation from game data is preferred over hardcoding to remain correct across data versions.

-----

## Appendix D — xxHash3 Reference

xxHash3 is used for both the header checksum and the file checksum. The canonical reference implementation is available at https://github.com/Cyan4973/xxHash. Java implementations may use the `org.lz4:lz4-java` library, which bundles xxHash3 support.

The seed value for all xxHash3 computations in MSF is **0** (default seed).

-----

## Appendix E — Unsigned Integer Handling in Java

Java does not have native unsigned integer types. Implementations MUST handle the following cases:

- **u16** — read as Java `short`, mask with `0xFFFF` when used as a numeric value
- **u32** — read as Java `int`, mask with `0xFFFFFFFFL` when used as a numeric value or use `Integer.toUnsignedLong()`
- **u64** — read as Java `long`. For values exceeding `Long.MAX_VALUE` use `Long.compareUnsigned()` and related unsigned comparison methods

**Write-side range validation:** Writers MUST validate all values before encoding them into unsigned fields. If a value exceeds the maximum representable value for the target field's unsigned type, the writer MUST throw `IllegalArgumentException` with a message identifying the field name, the value provided, and the maximum permitted value. Silent truncation and value clamping are not permitted under any circumstance.

-----

## Appendix F — Deprecation Policy

When a feature is deprecated in a future minor version the following applies:

**In the spec:** The feature is marked deprecated with a rationale and the minor version in which deprecation was declared. A preferred alternative is identified where one exists.

**For writers:** Writers SHOULD NOT emit deprecated features in new files. Writers MAY continue emitting deprecated features for compatibility with older readers that only understand the deprecated form.

**For readers:** Readers MUST continue handling deprecated features correctly regardless of deprecation status. Deprecation does not grant readers permission to stop handling a feature.

**For the binary format:** Deprecated features remain in the format permanently. The flag bit remains assigned. The block type remains valid. The append-only rule has no deprecation exception.

-----

## Appendix G — Reference File Examples

This appendix describes the structure of the canonical reference `.msf` files committed to the repository at `docs/examples/`. Implementers can use these files as binary validation targets.

### G.1 Minimal Single-Block File

The minimal reference file (`docs/examples/minimal_stone.msf`) encodes a single `minecraft:stone` block. It has no entities, no block entities, and no biome data. The global palette contains two entries: `minecraft:air` (palette ID 0) and `minecraft:stone` (palette ID 1).

**Header (48 bytes at offset 0):**

The header is annotated below. All multi-byte values are little-endian. The header checksum is the xxHash3-64 digest with seed 0 of bytes 0–39.

```
Offset  Bytes               Field
------  ------------------  ------------------------------------------
00      4D 53 46 21         Magic: "MSF!"
04      01 00               Major version: 1
06      00 00               Minor version: 0
08      00 00 00 00         Feature flags: 0x00000000 (no optional blocks)
0C      71 0F 00 00         MC data version: 3953 (Minecraft 1.21.1)
10      30 00 00 00         Metadata block offset: 48 (0x30)
14      74 00 00 00         Global palette offset: 116 (0x74)
18      9A 00 00 00         Layer index offset: 154 (0x9A)
1C      00 00 00 00         Entity block offset: 0 (absent)
20      00 00 00 00         Block entity block offset: 0 (absent)
24      E6 00 00 00         File size: 230 bytes
28      xx xx xx xx         Header checksum: xxHash3-64(bytes 0–39),
        xx xx xx xx         8 bytes little-endian — see repository file
                            for the exact value
```

**Metadata block (68 bytes at offset 48):**

The block length is 64 (the 4-byte length field itself is not counted). The block contains: name `"example"`, empty author, zero timestamps, empty description, zero tags, zero contributors, empty license identifier, empty source URL, thumbnail size 0, anchor name `"origin"`, anchor offsets (0, 0, 0), canonical facing 0x00 (North), rotation compatibility 0x00 (none declared), has functional volume 0x00. The optional trailing fields (tool name, tool version, placement mode, MC edition) are omitted — the block length ends before them.

**Global palette block (38 bytes at offset 116):**

Block length 34. Entry count 2. Entry 0: `minecraft:air` (13 bytes). Entry 1: `minecraft:stone` (15 bytes).

**Layer index block (68 bytes at offset 154):**

Block length 64. Layer count 1. Single layer: ID 0, name `"main"`, construction order 0, no dependencies, flags 0x00, region count 1. Single region: name `"main"`, origin (0, 0, 0), size (1, 1, 1), compression 0x00 (none), compressed data length 13, uncompressed data length 13.

Region payload (13 bytes, uncompressed): bits per entry 1 (because `max(1, ceil(log2(2))) = 1`), packed array length 1 (`ceil(1×1×1×1 / 64) = 1`), packed data `01 00 00 00 00 00 00 00` (palette ID 1 = stone stored in the least significant bit of the single u64 word).

**File checksum (8 bytes at offset 222):**

The xxHash3-64 digest with seed 0 of bytes 0–221. See the repository file for the exact value.

> **Note (non-normative):** The reference files in `docs/examples/` are the authoritative binary targets. The values described in this appendix are correct for the file as committed. Implementers building test suites are encouraged to verify byte-for-byte against the committed files rather than recomputing from this prose description.

### G.2 Two-Layer Redstone Contraption

The two-layer reference file (`docs/examples/two_layer_redstone.msf`) demonstrates layer serialization, construction order, and dependency encoding. It represents a minimal redstone contraption: a base of stone blocks that must be placed first, and a redstone layer containing observers placed on top, which depends on the base layer.

**Layer index layout:**

The layer index block contains layer count = 2, followed by two layer records in sequence.

**Layer 0 — structural base:**

- Layer ID: `0x00`
- Name: `"base"` (4 bytes as UTF-8, with u16 length prefix)
- Construction order index: `0` (placed first)
- Dependency count: `0` (no dependencies)
- Flags: `0x00`
- Region count: `1`
- One region header follows, describing the stone blocks.

**Layer 1 — redstone:**

- Layer ID: `0x01`
- Name: `"redstone"` (8 bytes as UTF-8, with u16 length prefix)
- Construction order index: `1` (placed after base)
- Dependency count: `1`
- Dependent layer IDs: `[0x00]` (one byte, referencing layer ID 0)
- Flags: `0x00`
- Region count: `1`
- One region header follows, describing the observer blocks.

**Construction order reconstruction:**

A reader reconstructing placement order groups layers by construction order index and resolves dependencies within groups. In this file, layer 0 (index 0) is placed first. Layer 1 (index 1) declares layer 0 as a dependency — a tool encountering a placement request for layer 1 before layer 0 has been placed SHOULD emit an UNMET_DEPENDENCY warning per Section 6.4.

**Dependency field encoding on the wire:**

The dependency count byte for layer 1 is `0x01`. The single dependent layer ID byte immediately following is `0x00`. A reader parsing the layer record reads dependency count, then reads exactly that many u8 bytes as the dependency list.

The full binary layout of this file, with annotated hex, is committed to `docs/examples/two_layer_redstone.msf` in the repository.