# MSF Module Guide

## msf-core

### Adding a new model class

Model classes are immutable records. Use a builder for complex construction:

```java
package dev.msf.core.model;

/**
 * Represents the global palette block.
 *
 * @see <a href="../docs/MSF_Specification_V1.md">MSF Specification Section 4</a>
 */
public record MsfPalette(List<String> entries) {

    public MsfPalette {
        // Defensive copy and immutability
        entries = List.copyOf(entries);
        // Validate per spec Section 4.3
        if (entries.isEmpty() || !entries.get(0).equals("minecraft:air")) {
            throw new IllegalArgumentException("Palette entry 0 must be minecraft:air");
        }
    }

    /** Returns the number of entries including the mandatory air entry at ID 0. */
    public int size() {
        return entries.size();
    }
}
```

### Adding a read/write method to MsfReader/MsfWriter

Every read and write method accepts `@Nullable Consumer<MsfWarning>` as the final parameter:

```java
/**
 * Reads the global palette block from the given input stream.
 *
 * @param in       input stream positioned at the start of the palette block
 * @param warnings optional consumer for non-fatal warnings; may be null
 * @return the parsed palette
 * @throws MsfParseException if the palette is malformed
 * @throws IOException       if an IO error occurs
 */
public @NotNull MsfPalette readPalette(
        @NotNull DataInputStream in,
        @Nullable Consumer<MsfWarning> warnings) throws MsfParseException, IOException {
    // ...
}
```

### Emitting a warning

```java
if (warnings != null) {
    warnings.accept(new MsfWarning(
        MsfWarning.Code.RESERVED_FLAG_SET,
        "Reserved feature flag bits set: 0x" + Integer.toHexString(reservedBits),
        currentOffset
    ));
}
```

### Throwing with context

Always include the file offset and the expected vs actual value in exception messages:

```java
throw new MsfParseException(
    "Palette entry count exceeds maximum at offset " + offset +
    ": found " + entryCount + ", maximum 65535");
```

### Write-side range validation

```java
if (value > 0xFFFFL) {
    throw new IllegalArgumentException(
        "fieldName exceeds u16 maximum: provided " + value + ", maximum 65535");
}
```

---

## msf-fabric

### Adding a bridge class

Bridge classes resolve MSF opaque strings against Minecraft registries. They must not contain any parsing logic:

```java
package dev.msf.fabric.bridge;

/**
 * Resolves blockstate strings from MSF palette entries to Minecraft BlockState objects.
 */
public class BlockStateBridge {

    /**
     * Resolves a blockstate string to a Minecraft BlockState.
     *
     * @param blockstateString the opaque blockstate string from an MSF palette entry
     * @param warnings         optional consumer for non-fatal warnings
     * @return the resolved BlockState
     * @throws IllegalArgumentException if the string cannot be resolved
     */
    public @NotNull BlockState resolve(
            @NotNull String blockstateString,
            @Nullable Consumer<MsfWarning> warnings) {
        // Resolve against Registries.BLOCK
    }
}
```

### Adding world read/write

RegionExtractor reads block data from a live world into MsfRegion. RegionPlacer writes MsfRegion block data to a live world. Both classes delegate all encoding and decoding to msf-core — they only handle the Minecraft API surface:

```java
// In RegionExtractor: read from world, produce MsfRegion via msf-core
// In RegionPlacer: consume MsfRegion via msf-core, write to world
// Neither class touches bit packing, compression, or checksum logic
```
