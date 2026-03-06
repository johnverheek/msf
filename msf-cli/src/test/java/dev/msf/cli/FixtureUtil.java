package dev.msf.cli;

import dev.msf.core.checksum.XxHash3;
import dev.msf.core.io.MsfWriter;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfLayer;
import dev.msf.core.model.MsfLayerIndex;
import dev.msf.core.model.MsfMetadata;
import dev.msf.core.model.MsfPalette;
import dev.msf.core.model.MsfRegion;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Generates synthetic {@code .msf} byte arrays for CLI tests.
 *
 * <p>All helpers return fully self-contained byte arrays (no file I/O required).
 * Corrupt fixtures are derived by mutating valid bytes and, where needed, recomputing
 * the file checksum so that only the intended check fails.
 */
final class FixtureUtil {

    private FixtureUtil() {}

    // -------------------------------------------------------------------------
    // Valid fixtures
    // -------------------------------------------------------------------------

    /**
     * A minimal, fully-valid MSF file with descriptive metadata, a 2×2×2 region,
     * and tool name/version set (used to verify all inspect output fields).
     */
    static byte[] minimalValidBytes() throws Exception {
        MsfMetadata metadata = MsfMetadata.builder()
                .name("Test Schematic")
                .author("Test Author")
                .description("A test description")
                .tags(List.of("test", "cli"))
                .licenseIdentifier("MIT")
                .toolName("msf-test")
                .toolVersion("1.0.0")
                .canonicalFacing(MsfMetadata.FACING_NORTH)
                .rotationCompatibility(MsfMetadata.ROT_90_VALID | MsfMetadata.ROT_180_VALID)
                .build();

        MsfPalette palette = MsfPalette.of(List.of("minecraft:stone"));

        MsfRegion region = MsfRegion.builder()
                .name("main")
                .size(2, 2, 2)
                .blockData(new int[]{0, 1, 1, 0, 1, 0, 0, 1})
                .build();

        MsfLayer layer = MsfLayer.builder()
                .layerId(0)
                .name("Foundation")
                .addRegion(region)
                .build();

        MsfFile file = MsfFile.builder()
                .mcDataVersion(3953L)
                .metadata(metadata)
                .palette(palette)
                .layerIndex(MsfLayerIndex.of(List.of(layer)))
                .build();

        return MsfWriter.writeFile(file, null);
    }

    /**
     * A valid file whose metadata contains a malformed thumbnail, producing a
     * {@link dev.msf.core.MsfWarning.Code#MALFORMED_THUMBNAIL} warning on read.
     */
    static byte[] withMalformedThumbnailBytes() throws Exception {
        MsfMetadata metadata = MsfMetadata.builder()
                .name("Thumbnail Test")
                .author("Author")
                // non-PNG bytes — triggers MALFORMED_THUMBNAIL warning
                .thumbnail(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05})
                .build();

        MsfPalette palette = MsfPalette.of(List.of("minecraft:stone"));

        MsfRegion region = MsfRegion.builder()
                .name("r")
                .size(1, 1, 1)
                .blockData(new int[]{0})
                .build();

        MsfLayer layer = MsfLayer.builder()
                .layerId(0)
                .name("L")
                .addRegion(region)
                .build();

        MsfFile file = MsfFile.builder()
                .mcDataVersion(3953L)
                .metadata(metadata)
                .palette(palette)
                .layerIndex(MsfLayerIndex.of(List.of(layer)))
                .build();

        return MsfWriter.writeFile(file, null);
    }

    // -------------------------------------------------------------------------
    // Corrupt fixtures
    // -------------------------------------------------------------------------

    /**
     * A file whose stored file-checksum is corrupted (last byte flipped).
     * The content is intact, so everything parses correctly; only the
     * {@code FILE_CHECKSUM_FAILURE} warning fires.
     */
    static byte[] badFileChecksumBytes() throws Exception {
        byte[] bytes = minimalValidBytes();
        bytes[bytes.length - 1] ^= 0xFF;
        return bytes;
    }

    /**
     * A file where palette entries 1 and 2 are identical, triggering a
     * {@code MsfParseException} for duplicate palette entries.
     *
     * <p>Both entries are 15 bytes ("minecraft:stone" and "minecraft:grass")
     * so overwriting entry 2's string with entry 1's string does not change
     * the block_length of the palette block, keeping the header intact.
     * The file checksum is recomputed over the modified bytes so that the
     * checksum check passes and parsing reaches the palette duplicate check.
     */
    static byte[] duplicatePaletteBytes() throws Exception {
        // Build a file with palette [air, stone (15 b), grass (15 b)]
        MsfMetadata metadata = MsfMetadata.builder().name("Dup Palette Test").build();
        MsfPalette palette = MsfPalette.of(List.of("minecraft:stone", "minecraft:grass"));

        MsfRegion region = MsfRegion.builder()
                .name("r")
                .size(1, 1, 1)
                .blockData(new int[]{0})
                .build();

        MsfLayer layer = MsfLayer.builder()
                .layerId(0)
                .name("L")
                .addRegion(region)
                .build();

        MsfFile file = MsfFile.builder()
                .mcDataVersion(3953L)
                .metadata(metadata)
                .palette(palette)
                .layerIndex(MsfLayerIndex.of(List.of(layer)))
                .build();

        byte[] bytes = MsfWriter.writeFile(file, null);

        // Find the palette block via the header's globalPaletteOffset (u32 at header offset 20)
        ByteBuffer hb = ByteBuffer.wrap(bytes, 0, 48).order(ByteOrder.LITTLE_ENDIAN);
        int paletteOffset = (int) Integer.toUnsignedLong(hb.getInt(20));

        // Palette layout:
        //   u32 block_length  (4 bytes)  → paletteOffset
        //   u16 entry_count=3 (2 bytes)  → +4
        //   entry 0: u16(13) + "minecraft:air"   = 15 bytes → +6
        //   entry 1: u16(15) + "minecraft:stone" = 17 bytes → +21
        //   entry 2: u16(15) + "minecraft:grass" = 17 bytes → +38
        //     string content at +38+2 = +40
        int entry2StringOffset = paletteOffset + 4 + 2 + 15 + 17 + 2;

        // Overwrite "minecraft:grass" with "minecraft:stone" (same length: 15 bytes)
        byte[] stoneBytes = "minecraft:stone".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(stoneBytes, 0, bytes, entry2StringOffset, stoneBytes.length);

        // Recompute file checksum so only the palette duplicate check fails
        return recomputeFileChecksum(bytes);
    }

    /**
     * A file where the sizeX field of the first region is overwritten to 0,
     * triggering a {@code MsfParseException} for an invalid region dimension.
     *
     * <p>The region and layer names are empty strings so the byte offset of sizeX
     * within the layer index block is predictable. The file checksum is recomputed
     * over the modified bytes.
     */
    static byte[] zeroSizeRegionBytes() throws Exception {
        // Use empty names for predictable offsets
        MsfMetadata metadata = MsfMetadata.builder().name("Zero Size Test").build();
        MsfPalette palette = MsfPalette.of(List.of("minecraft:stone"));

        MsfRegion region = MsfRegion.builder()
                .name("")     // empty name → u16(0) = 2 bytes
                .size(1, 1, 1)
                .blockData(new int[]{0})
                .build();

        MsfLayer layer = MsfLayer.builder()
                .layerId(0)
                .name("")     // empty name → u16(0) = 2 bytes
                .addRegion(region)
                .build();

        MsfFile file = MsfFile.builder()
                .mcDataVersion(3953L)
                .metadata(metadata)
                .palette(palette)
                .layerIndex(MsfLayerIndex.of(List.of(layer)))
                .build();

        byte[] bytes = MsfWriter.writeFile(file, null);

        // Locate layerIndexOffset from header (u32 at header offset 24)
        ByteBuffer hb = ByteBuffer.wrap(bytes, 0, 48).order(ByteOrder.LITTLE_ENDIAN);
        int lio = (int) Integer.toUnsignedLong(hb.getInt(24));

        // Layer index body at lio:
        //   u32 block_length      (4 bytes)  → lio+0
        //   u8  layerCount=1      (1 byte)   → lio+4
        //   u8  layerId=0         (1 byte)   → lio+5
        //   u16 name=""           (2 bytes)  → lio+6
        //   u8  constructionOrder (1 byte)   → lio+8
        //   u8  depCount=0        (1 byte)   → lio+9
        //   u8  flags=0           (1 byte)   → lio+10
        //   u8  regionCount=1     (1 byte)   → lio+11
        //   Region 0 starts at lio+12:
        //     u16 name=""         (2 bytes)  → lio+12
        //     i32 originX         (4 bytes)  → lio+14
        //     i32 originY         (4 bytes)  → lio+18
        //     i32 originZ         (4 bytes)  → lio+22
        //     u32 sizeX           (4 bytes)  → lio+26  ← overwrite this
        int sizeXOffset = lio + 26;
        bytes[sizeXOffset]     = 0;
        bytes[sizeXOffset + 1] = 0;
        bytes[sizeXOffset + 2] = 0;
        bytes[sizeXOffset + 3] = 0;

        return recomputeFileChecksum(bytes);
    }

    /**
     * A file with two independent problems: FILE_SIZE_MISMATCH (header declares N bytes,
     * actual file is N+4) and FILE_CHECKSUM_FAILURE (the extended file's checksum does not
     * match the stored value). Both are reported as separate failed checks by validate.
     */
    static byte[] multiProblemBytes() throws Exception {
        byte[] valid = minimalValidBytes();
        // Append 4 extra bytes — the header still declares the original file size,
        // so FILE_SIZE_MISMATCH fires; the checksum computed over the extended bytes
        // does not match the stored value, so FILE_CHECKSUM_FAILURE fires too.
        byte[] extended = new byte[valid.length + 4];
        System.arraycopy(valid, 0, extended, 0, valid.length);
        return extended;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Recomputes the file checksum (last 8 bytes) over the first {@code len-8} bytes. */
    private static byte[] recomputeFileChecksum(byte[] bytes) {
        int checksumDataLen = bytes.length - 8;
        long newCs = XxHash3.hash(bytes, 0, checksumDataLen);
        ByteBuffer csBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        csBuf.putLong(newCs);
        System.arraycopy(csBuf.array(), 0, bytes, checksumDataLen, 8);
        return bytes;
    }
}
