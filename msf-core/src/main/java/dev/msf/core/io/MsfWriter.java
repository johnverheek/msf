package dev.msf.core.io;

import dev.msf.core.MsfException;
import dev.msf.core.checksum.XxHash3;
import dev.msf.core.compression.CompressionType;
import dev.msf.core.model.MsfBlockEntity;
import dev.msf.core.model.MsfEntity;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfHeader;
import dev.msf.core.MsfWarning;
import dev.msf.core.util.UuidStripper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Serializes MSF files according to the MSF V1 specification.
 *
 * <h2>Write procedure for checksums</h2>
 * <p>Per Section 3.7, the header checksum is computed over bytes 0–39. Writers MUST
 * write all header fields at offsets 0–39 before computing the header checksum.
 * Per Section 11, the file checksum is appended last, after all other file content
 * including the fully-populated header with the {@code file_size} field set correctly.
 * The correct sequence is: write all content blocks, set the file_size field, compute
 * and write the header checksum, then compute and write the file checksum as the
 * absolute final write operation.
 *
 * <h2>UUID stripping</h2>
 * <p>{@link #writeFile(MsfFile, CompressionType, Consumer)} invokes {@link UuidStripper#strip}
 * on every entity and block entity NBT payload before encoding per Sections 8.2 and 9.2.
 *
 * <h2>Warning mechanism</h2>
 * <p>Write-side warnings have an offset of {@code -1} since no file byte offset is
 * meaningful during writing. The warning mechanism contract is described in Section 3.5.1.
 *
 * <h2>Reserved bit handling</h2>
 * <p>Writers MUST clear reserved feature flag bits 10–31 silently and MUST emit a
 * {@link MsfWarning.Code#RESERVED_FLAG_CLEARED} warning if the caller provided any
 * set bits (Section 3.3).
 *
 * @see MsfSpec Section 3 — header
 * @see MsfSpec Section 3.7 — header checksum write procedure
 * @see MsfSpec Section 11 — file checksum write procedure
 * @see MsfSpec Appendix E — unsigned integer handling in Java
 */
public final class MsfWriter {

    private MsfWriter() {
        // Utility class — all methods are static
    }

    // =========================================================================
    // Full-file write API (Session 3)
    // =========================================================================

    /**
     * Serializes a complete {@link MsfFile} to a byte array.
     *
     * <p>The write sequence (per Sections 3.7 and 11):
     * <ol>
     *   <li>Serialize all content blocks (metadata, palette, layer index, optional blocks)</li>
     *   <li>Compute all block offsets and {@code file_size}</li>
     *   <li>Assemble and write the 48-byte header with correct offsets and {@code file_size}</li>
     *   <li>Compute and write the header checksum (bytes 0–39)</li>
     *   <li>Assemble header + all block bytes</li>
     *   <li>Compute and append the file checksum as the absolute final operation (Section 11)</li>
     * </ol>
     *
     * <p>The compression algorithm for region payloads is {@link CompressionType#ZSTD}
     * (the recommended default, Section 7.2).
     *
     * <p>UUID stripping is applied to all entity and block entity NBT payloads before
     * encoding per Sections 8.2 and 9.2.
     *
     * @param file            the file to serialize
     * @param warningConsumer receives warnings produced during serialization; may be {@code null}
     * @return the complete MSF file bytes including the 8-byte file checksum
     * @throws MsfException             if any block fails validation
     * @throws MsfCompressionException  if region compression fails
     * @throws MsfPaletteException      if the palette has duplicate entries
     * @throws IllegalArgumentException if any NBT payload exceeds 65535 bytes after UUID stripping
     */
    /**
     * Serializes a complete {@link MsfFile} using the default ZSTD compression for
     * region block data. Delegates to {@link #writeFile(MsfFile, CompressionType, Consumer)}.
     *
     * @param file            the file to serialize
     * @param warningConsumer receives warnings produced during serialization; may be {@code null}
     * @return the complete MSF file bytes including the 8-byte file checksum
     * @throws MsfException if any block fails validation
     */
    public static byte[] writeFile(
        MsfFile file,
        Consumer<MsfWarning> warningConsumer
    ) throws MsfException {
        return writeFile(file, CompressionType.ZSTD, warningConsumer);
    }

    /**
     * Serializes a complete {@link MsfFile} using the specified compression algorithm
     * for region block data.
     *
     * @param file            the file to serialize
     * @param compressionType compression algorithm to use for region block data
     * @param warningConsumer receives warnings produced during serialization; may be {@code null}
     * @return the complete MSF file bytes including the 8-byte file checksum
     * @throws MsfException if any block fails validation
     */
    public static byte[] writeFile(
        MsfFile file,
        CompressionType compressionType,
        Consumer<MsfWarning> warningConsumer
    ) throws MsfException {
        MsfHeader hdr            = file.header();
        boolean hasBiomes        = hdr.hasBiomeData();
        boolean hasEntities      = hdr.hasEntities();
        boolean hasBlockEntities = hdr.hasBlockEntities();

        // Sanitize feature flags — clear reserved bits 10–31, warn if any were set (Section 3.3)
        int rawFlags       = hdr.featureFlags();
        int sanitizedFlags = rawFlags & MsfHeader.FeatureFlags.DEFINED_BITS_MASK;
        if (sanitizedFlags != rawFlags) {
            warn(warningConsumer, MsfWarning.writeWarning(
                MsfWarning.Code.RESERVED_FLAG_CLEARED,
                String.format(
                    "Reserved feature flag bits were cleared: caller provided 0x%08X, writing 0x%08X",
                    rawFlags & 0xFFFFFFFFL, sanitizedFlags & 0xFFFFFFFFL
                )
            ));
        }

        // -- Serialize each block body --
        byte[] metaBytes       = file.metadata().toBytes(warningConsumer);
        byte[] paletteBytes    = file.palette().toBytes();
        byte[] layerIndexBytes = file.layerIndex().toBytes(
            file.palette().entries().size(), compressionType, hasBiomes, warningConsumer
        );

        byte[] entityBytes      = null;
        byte[] blockEntityBytes = null;

        if (hasEntities && file.entities().isPresent()) {
            entityBytes = buildEntityBlock(file.entities().get());
        }
        if (hasBlockEntities && file.blockEntities().isPresent()) {
            blockEntityBytes = buildBlockEntityBlock(file.blockEntities().get());
        }

        // -- Compute layout: header(48) + meta + palette + layerIndex [+ entities] [+ blockEntities] --
        long metaOffset       = MsfHeader.HEADER_SIZE;
        long paletteOffset    = metaOffset       + metaBytes.length;
        long layerIndexOffset = paletteOffset    + paletteBytes.length;
        long nextOffset       = layerIndexOffset + layerIndexBytes.length;

        long entityOffset      = 0L;
        long blockEntityOffset = 0L;

        if (entityBytes != null) {
            entityOffset = nextOffset;
            nextOffset  += entityBytes.length;
        }
        if (blockEntityBytes != null) {
            blockEntityOffset = nextOffset;
            nextOffset       += blockEntityBytes.length;
        }

        // file_size includes the 8-byte file checksum appended at the very end (Section 3.6)
        long fileSize = nextOffset + 8L;

        // -- Build the 48-byte header buffer --
        ByteBuffer headerBuf = ByteBuffer.allocate(MsfHeader.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        headerBuf.put(0, MsfHeader.MAGIC[0]);
        headerBuf.put(1, MsfHeader.MAGIC[1]);
        headerBuf.put(2, MsfHeader.MAGIC[2]);
        headerBuf.put(3, MsfHeader.MAGIC[3]);
        headerBuf.putShort(4, (short) (MsfHeader.SUPPORTED_MAJOR_VERSION & 0xFFFF));
        headerBuf.putShort(6, (short) (MsfHeader.SUPPORTED_MINOR_VERSION & 0xFFFF));
        headerBuf.putInt(8, sanitizedFlags);

        validateU32("mcDataVersion",      hdr.mcDataVersion());
        headerBuf.putInt(12, (int) hdr.mcDataVersion());

        validateU32("metadataOffset",     metaOffset);
        validateU32("paletteOffset",      paletteOffset);
        validateU32("layerIndexOffset",   layerIndexOffset);
        validateU32("entityOffset",       entityOffset);
        validateU32("blockEntityOffset",  blockEntityOffset);
        validateU32("fileSize",           fileSize);

        headerBuf.putInt(16, (int) metaOffset);
        headerBuf.putInt(20, (int) paletteOffset);
        headerBuf.putInt(24, (int) layerIndexOffset);
        headerBuf.putInt(28, (int) entityOffset);
        headerBuf.putInt(32, (int) blockEntityOffset);
        headerBuf.putInt(36, (int) fileSize);

        // Compute and write header checksum (Section 3.7): xxHash3-64 of bytes 0–39
        long headerChecksum = XxHash3.hash(headerBuf.array(), 0, 40);
        headerBuf.putLong(40, headerChecksum);

        // -- Assemble header + all block bodies --
        ByteArrayOutputStream baos = new ByteArrayOutputStream((int) fileSize);
        try {
            baos.write(headerBuf.array());
            baos.write(metaBytes);
            baos.write(paletteBytes);
            baos.write(layerIndexBytes);
            if (entityBytes      != null) baos.write(entityBytes);
            if (blockEntityBytes != null) baos.write(blockEntityBytes);
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream.write threw unexpectedly", e);
        }

        // -- Compute file checksum (Section 11): hash all content bytes, append 8-byte digest --
        // Input: bytes 0 through (file_size - 9) inclusive, i.e. everything before the final 8 bytes.
        byte[] content = baos.toByteArray();
        long fileChecksum = XxHash3.hash(content, 0, content.length);
        ByteBuffer checksumBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        checksumBuf.putLong(0, fileChecksum);
        try {
            baos.write(checksumBuf.array());
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream.write threw unexpectedly", e);
        }

        return baos.toByteArray();
    }

    /**
     * Serializes a complete {@link MsfFile} and writes it to a file.
     *
     * @param path            the path to write to
     * @param file            the file to serialize
     * @param warningConsumer receives warnings; may be {@code null}
     * @throws IOException    if an I/O error occurs
     * @throws MsfException   if serialization fails
     */
    public static void writeFile(
        Path path,
        MsfFile file,
        Consumer<MsfWarning> warningConsumer
    ) throws IOException, MsfException {
        Files.write(path, writeFile(file, warningConsumer));
    }

    // =========================================================================
    // Entity block serialization (Section 8)
    // =========================================================================

    /**
     * Builds the entity block bytes: u32 block_length + u32 entity_count + per-entity records.
     *
     * <p>UUIDs are stripped from each entity's NBT payload before encoding (Section 8.2).
     */
    private static byte[] buildEntityBlock(List<MsfEntity> entities) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            writeU32(body, entities.size());    // u32 entity_count

            for (MsfEntity entity : entities) {
                byte[] strippedNbt = UuidStripper.strip(entity.nbtPayload());

                if (strippedNbt.length > MsfEntity.MAX_NBT_PAYLOAD_BYTES) {
                    throw new IllegalArgumentException(String.format(
                        "Entity '%s' NBT payload size %d exceeds maximum permitted value of %d",
                        entity.entityType(), strippedNbt.length, MsfEntity.MAX_NBT_PAYLOAD_BYTES
                    ));
                }

                writeF64(body, entity.positionX());     // f64 position X
                writeF64(body, entity.positionY());     // f64 position Y
                writeF64(body, entity.positionZ());     // f64 position Z
                writeF32(body, entity.yaw());           // f32 yaw
                writeF32(body, entity.pitch());         // f32 pitch
                writeStr(body, entity.entityType());    // str entity type
                writeU16(body, strippedNbt.length);     // u16 NBT payload length
                body.write(strippedNbt);                // u8[] NBT payload
            }
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream.write threw unexpectedly", e);
        }

        byte[] bodyBytes = body.toByteArray();
        ByteBuffer result = ByteBuffer.allocate(4 + bodyBytes.length).order(ByteOrder.LITTLE_ENDIAN);
        result.putInt(bodyBytes.length);                // u32 block_length
        result.put(bodyBytes);
        return result.array();
    }

    // =========================================================================
    // Block entity block serialization (Section 9)
    // =========================================================================

    /**
     * Builds the block entity block bytes: u32 block_length + u32 count + per-block-entity records.
     *
     * <p>UUIDs are stripped from each block entity's NBT payload before encoding (Section 9.2).
     */
    private static byte[] buildBlockEntityBlock(List<MsfBlockEntity> blockEntities) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            writeU32(body, blockEntities.size());   // u32 block_entity_count

            for (MsfBlockEntity be : blockEntities) {
                byte[] strippedNbt = UuidStripper.strip(be.nbtPayload());

                if (strippedNbt.length > MsfBlockEntity.MAX_NBT_PAYLOAD_BYTES) {
                    throw new IllegalArgumentException(String.format(
                        "Block entity '%s' NBT payload size %d exceeds maximum permitted value of %d",
                        be.blockEntityType(), strippedNbt.length, MsfBlockEntity.MAX_NBT_PAYLOAD_BYTES
                    ));
                }

                writeI32(body, be.positionX());             // i32 position X
                writeI32(body, be.positionY());             // i32 position Y
                writeI32(body, be.positionZ());             // i32 position Z
                writeStr(body, be.blockEntityType());       // str block entity type
                writeU16(body, strippedNbt.length);         // u16 NBT payload length
                body.write(strippedNbt);                    // u8[] NBT payload
            }
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream.write threw unexpectedly", e);
        }

        byte[] bodyBytes = body.toByteArray();
        ByteBuffer result = ByteBuffer.allocate(4 + bodyBytes.length).order(ByteOrder.LITTLE_ENDIAN);
        result.putInt(bodyBytes.length);                // u32 block_length
        result.put(bodyBytes);
        return result.array();
    }

    // =========================================================================
    // Legacy header-only API (Session 1 scope — preserved for existing tests)
    // =========================================================================

    /**
     * Writes a minimal MSF file containing only the header to the given path.
     *
     * <p>No warning consumer is provided — warnings are silently discarded.
     *
     * @param path   the path to write the MSF file to
     * @param header the header to serialize
     * @throws IOException  if an I/O error occurs writing the file
     * @throws MsfException if the header values are invalid
     */
    public static void writeHeader(Path path, MsfHeader header) throws IOException, MsfException {
        writeHeader(path, header, null);
    }

    /**
     * Writes a minimal MSF file containing only the header to the given path.
     *
     * @param path            the path to write the MSF file to
     * @param header          the header to serialize
     * @param warningConsumer receives warnings produced during writing; may be {@code null}
     * @throws IOException  if an I/O error occurs writing the file
     * @throws MsfException if the header values are invalid
     */
    public static void writeHeader(
        Path path,
        MsfHeader header,
        Consumer<MsfWarning> warningConsumer
    ) throws IOException, MsfException {
        Files.write(path, buildFileBytes(header, warningConsumer));
    }

    /**
     * Builds the complete MSF file bytes for a header-only file (Session 1 scope).
     *
     * <p>Used by tests that want the raw bytes without writing to disk.
     *
     * @param header          the header to serialize
     * @param warningConsumer receives warnings produced during serialization; may be {@code null}
     * @return the complete MSF file as a byte array, including file checksum
     * @throws MsfException if the header values are invalid
     */
    public static byte[] buildFileBytes(
        MsfHeader header,
        Consumer<MsfWarning> warningConsumer
    ) throws MsfException {
        int rawFlags = header.featureFlags();
        int sanitizedFlags = rawFlags & MsfHeader.FeatureFlags.DEFINED_BITS_MASK;
        if (sanitizedFlags != rawFlags) {
            warn(warningConsumer, MsfWarning.writeWarning(
                MsfWarning.Code.RESERVED_FLAG_CLEARED,
                String.format(
                    "Reserved feature flag bits were cleared: caller provided 0x%08X, writing 0x%08X",
                    rawFlags & 0xFFFFFFFFL, sanitizedFlags & 0xFFFFFFFFL
                )
            ));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(MsfHeader.HEADER_SIZE + 8);
        ByteBuffer headerBuf = ByteBuffer.allocate(MsfHeader.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        headerBuf.put(0, MsfHeader.MAGIC[0]);
        headerBuf.put(1, MsfHeader.MAGIC[1]);
        headerBuf.put(2, MsfHeader.MAGIC[2]);
        headerBuf.put(3, MsfHeader.MAGIC[3]);
        headerBuf.putShort(4, (short) (header.majorVersion() & 0xFFFF));
        headerBuf.putShort(6, (short) (header.minorVersion() & 0xFFFF));
        headerBuf.putInt(8, sanitizedFlags);

        validateU32("mcDataVersion", header.mcDataVersion());
        headerBuf.putInt(12, (int) header.mcDataVersion());

        validateU32("metadataOffset",        header.metadataOffset());
        validateU32("globalPaletteOffset",   header.globalPaletteOffset());
        validateU32("layerIndexOffset",      header.layerIndexOffset());
        validateU32("entityBlockOffset",     header.entityBlockOffset());
        validateU32("blockEntityBlockOffset",header.blockEntityBlockOffset());

        headerBuf.putInt(16, (int) header.metadataOffset());
        headerBuf.putInt(20, (int) header.globalPaletteOffset());
        headerBuf.putInt(24, (int) header.layerIndexOffset());
        headerBuf.putInt(28, (int) header.entityBlockOffset());
        headerBuf.putInt(32, (int) header.blockEntityBlockOffset());

        long fileSize = header.fileSize();
        if (fileSize == 0L) {
            fileSize = MsfHeader.HEADER_SIZE + 8L;
        }
        validateU32("fileSize", fileSize);
        headerBuf.putInt(36, (int) fileSize);

        long headerChecksum = XxHash3.hash(headerBuf.array(), 0, 40);
        headerBuf.putLong(40, headerChecksum);

        try {
            baos.write(headerBuf.array());
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream.write threw unexpectedly", e);
        }

        byte[] fileContent = baos.toByteArray();
        long fileChecksum = XxHash3.hash(fileContent, 0, fileContent.length);
        ByteBuffer checksumBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        checksumBuf.putLong(0, fileChecksum);
        try {
            baos.write(checksumBuf.array());
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream.write threw unexpectedly", e);
        }

        return baos.toByteArray();
    }

    // =========================================================================
    // I/O helpers
    // =========================================================================

    /** Writes an {@code f64} (IEEE 754 double) as 8 little-endian bytes. */
    private static void writeF64(ByteArrayOutputStream out, double v) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        b.putDouble(v);
        out.write(b.array());
    }

    /** Writes an {@code f32} (IEEE 754 float) as 4 little-endian bytes. */
    private static void writeF32(ByteArrayOutputStream out, float v) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        b.putFloat(v);
        out.write(b.array());
    }

    /** Writes an {@code i32} (signed 32-bit integer) as 4 little-endian bytes. */
    private static void writeI32(ByteArrayOutputStream out, int v) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(v);
        out.write(b.array());
    }

    /** Writes a {@code u32} value (passed as {@code long}) as 4 little-endian bytes. */
    private static void writeU32(ByteArrayOutputStream out, long v) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt((int) v);
        out.write(b.array());
    }

    /** Writes a {@code u16} value as 2 little-endian bytes. */
    private static void writeU16(ByteArrayOutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    /**
     * Writes a {@code str} field: u16 byte length (little-endian) followed by UTF-8 bytes.
     * Per Section 2.1 — all multi-byte integers are little-endian.
     */
    private static void writeStr(ByteArrayOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeU16(out, bytes.length);
        out.write(bytes);
    }

    /**
     * Validates that a value fits within a u32 field (0 to 4,294,967,295 inclusive).
     * Per Appendix E: writers MUST validate all values before encoding. Silent truncation
     * is not permitted.
     */
    private static void validateU32(String fieldName, long value) {
        if (value < 0L || value > 0xFFFFFFFFL) {
            throw new IllegalArgumentException(String.format(
                "Field '%s' value %d is outside the u32 range [0, 4294967295]",
                fieldName, value
            ));
        }
    }

    /** Delivers a warning to the consumer if one is present. */
    private static void warn(Consumer<MsfWarning> warningConsumer, MsfWarning warning) {
        if (warningConsumer != null) {
            warningConsumer.accept(warning);
        }
    }
}
