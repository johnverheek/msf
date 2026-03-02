package dev.msf.core.io;

import dev.msf.core.MsfException;
import dev.msf.core.checksum.XxHash3;
import dev.msf.core.model.MsfHeader;
import dev.msf.core.MsfWarning;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Serializes MSF file headers according to the MSF V1 specification.
 *
 * <p>Session 1 scope: header serialization, file size computation, header checksum,
 * and file checksum. Palette, layer index, region data, and remaining blocks are
 * implemented in Session 2.
 *
 * <h2>Write procedure for checksums</h2>
 * <p>Per Section 3.7, the header checksum is computed over bytes 0–39. Writers MUST
 * write all header fields at offsets 0–39 before computing the header checksum.
 * Per Section 11, the file checksum is appended last, after all other file content
 * including the fully-populated header with the {@code file_size} field set correctly.
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

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Writes a minimal MSF file containing only the header and required block stubs to the
     * given path.
     *
     * <p>In Session 1, this method writes a complete header with valid checksums. The three
     * required blocks (metadata, palette, layer index) are represented by their offsets in
     * the header and stub content in the file body. This allows round-trip header testing
     * without the full block implementations.
     *
     * <p>No warning consumer is provided — warnings are silently discarded.
     *
     * @param path            the path to write the MSF file to
     * @param header          the header to serialize
     * @throws IOException    if an I/O error occurs writing the file
     * @throws MsfException   if the header values are invalid
     */
    public static void writeHeader(Path path, MsfHeader header) throws IOException, MsfException {
        writeHeader(path, header, null);
    }

    /**
     * Writes a minimal MSF file containing only the header and required block stubs to the
     * given path.
     *
     * @param path            the path to write the MSF file to
     * @param header          the header to serialize
     * @param warningConsumer receives warnings produced during writing; may be {@code null}
     * @throws IOException    if an I/O error occurs writing the file
     * @throws MsfException   if the header values are invalid
     */
    public static void writeHeader(
        Path path,
        MsfHeader header,
        Consumer<MsfWarning> warningConsumer
    ) throws IOException, MsfException {
        byte[] fileBytes = buildFileBytes(header, warningConsumer);
        Files.write(path, fileBytes);
    }

    /**
     * Builds the complete MSF file bytes for a header-only file (Session 1 scope).
     *
     * <p>This method is also used internally by tests that want the raw bytes without
     * writing to disk.
     *
     * @param header          the header to serialize
     * @param warningConsumer receives warnings produced during serialization; may be {@code null}
     * @return the complete MSF file as a byte array, including file checksum
     * @throws MsfException   if the header values are invalid
     */
    public static byte[] buildFileBytes(
        MsfHeader header,
        Consumer<MsfWarning> warningConsumer
    ) throws MsfException {
        // Sanitize feature flags — clear reserved bits 10–31, warn if any were set.
        // Per Section 3.3: writers MUST mask to bits 0–9, MUST emit warning if caller set reserved bits.
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

        // Build a ByteArrayOutputStream so we can compute the exact file size.
        // In Session 1, the file is just the 48-byte header + 8-byte file checksum = 56 bytes.
        // Session 2 will extend this to write the actual block bodies.
        // Note: ByteArrayOutputStream.write(byte[]) is declared to throw IOException but
        // never actually does so for in-memory writes. The try-catch is required by the compiler.
        ByteArrayOutputStream baos = new ByteArrayOutputStream(MsfHeader.HEADER_SIZE + 8);

        // Allocate the 48-byte header buffer.
        ByteBuffer headerBuf = ByteBuffer.allocate(MsfHeader.HEADER_SIZE)
                                         .order(ByteOrder.LITTLE_ENDIAN);

        // --- Write magic bytes at offsets 0–3 ---
        // Magic bytes are individual bytes, NOT a multi-byte integer. Do not apply LE convention.
        headerBuf.put(0, MsfHeader.MAGIC[0]);
        headerBuf.put(1, MsfHeader.MAGIC[1]);
        headerBuf.put(2, MsfHeader.MAGIC[2]);
        headerBuf.put(3, MsfHeader.MAGIC[3]);

        // --- Write version fields ---
        headerBuf.putShort(4, (short) (header.majorVersion() & 0xFFFF));
        headerBuf.putShort(6, (short) (header.minorVersion() & 0xFFFF));

        // --- Write feature flags (sanitized) ---
        headerBuf.putInt(8, sanitizedFlags);

        // --- Write MC data version ---
        // u32 field: validate that the caller's value fits in a u32 before encoding (Appendix E).
        validateU32("mcDataVersion", header.mcDataVersion());
        headerBuf.putInt(12, (int) header.mcDataVersion());

        // --- Write block offsets ---
        // For Session 1, the file size is header (48) + file checksum (8) = 56 bytes.
        // The blocks are not written in Session 1, so offsets are whatever the caller provides.
        // In a full implementation, the writer would compute these after laying out all blocks.
        validateU32("metadataOffset", header.metadataOffset());
        validateU32("globalPaletteOffset", header.globalPaletteOffset());
        validateU32("layerIndexOffset", header.layerIndexOffset());
        validateU32("entityBlockOffset", header.entityBlockOffset());
        validateU32("blockEntityBlockOffset", header.blockEntityBlockOffset());

        headerBuf.putInt(16, (int) header.metadataOffset());
        headerBuf.putInt(20, (int) header.globalPaletteOffset());
        headerBuf.putInt(24, (int) header.layerIndexOffset());
        headerBuf.putInt(28, (int) header.entityBlockOffset());
        headerBuf.putInt(32, (int) header.blockEntityBlockOffset());

        // --- Write file size ---
        // For Session 1, file size = header (48) + file checksum (8) = 56.
        // Per Section 3.6: file_size includes the 8-byte checksum.
        // The caller's fileSize is used if non-zero; otherwise we compute it.
        // In Session 1 tests we pass 56L as fileSize.
        long fileSize = header.fileSize();
        if (fileSize == 0L) {
            // Compute: header only + file checksum
            fileSize = MsfHeader.HEADER_SIZE + 8L;
        }
        validateU32("fileSize", fileSize);
        headerBuf.putInt(36, (int) fileSize);

        // --- Compute and write header checksum (Section 3.7) ---
        // The checksum is over exactly bytes 0–39 of the header. The 8 bytes at 40–47 are
        // not included. We compute it now before placing it at offset 40.
        byte[] headerBytes = headerBuf.array();
        long headerChecksum = XxHash3.hash(headerBytes, 0, 40);
        headerBuf.putLong(40, headerChecksum);

        // Flush the complete header to the output stream.
        // ByteArrayOutputStream.write(byte[]) is declared to throw IOException but never does
        // for in-memory writes; the AssertionError guard is for clarity.
        try {
            baos.write(headerBuf.array());
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream.write threw unexpectedly", e);
        }

        // Retrieve everything written so far.
        byte[] fileContent = baos.toByteArray();

        // --- Compute and append file checksum (Section 11) ---
        // Input: all bytes from 0 to (file_size - 9) inclusive, i.e. all bytes except the
        // final 8. At this point fileContent is 48 bytes (just the header). The file size
        // is 56 bytes (48 header + 8 checksum). So we hash bytes 0–47 (the header).
        // In Session 2 this will hash header + all block body bytes.
        int checksumInputLength = fileContent.length; // everything before the final 8 bytes
        long fileChecksum = XxHash3.hash(fileContent, 0, checksumInputLength);

        // Append the file checksum as a u64 little-endian value.
        ByteBuffer checksumBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        checksumBuf.putLong(0, fileChecksum);
        try {
            baos.write(checksumBuf.array());
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream.write threw unexpectedly", e);
        }

        return baos.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Validates that a value fits within a u32 field (0 to 4,294,967,295 inclusive).
     *
     * <p>Per Appendix E: writers MUST validate all values before encoding them into unsigned
     * fields. Silent truncation is not permitted.
     *
     * @param fieldName the field name for the error message
     * @param value     the value to validate
     * @throws IllegalArgumentException if the value is outside the u32 range
     */
    private static void validateU32(String fieldName, long value) {
        if (value < 0L || value > 0xFFFFFFFFL) {
            throw new IllegalArgumentException(String.format(
                "Field '%s' value %d is outside the u32 range [0, 4294967295]",
                fieldName, value
            ));
        }
    }

    /**
     * Delivers a warning to the consumer if one is present.
     *
     * @param warningConsumer the consumer to deliver to; no-op if {@code null}
     * @param warning         the warning to deliver
     */
    private static void warn(Consumer<MsfWarning> warningConsumer, MsfWarning warning) {
        if (warningConsumer != null) {
            warningConsumer.accept(warning);
        }
    }
}
