package dev.msf.core.io;

import dev.msf.core.MsfParseException;
import dev.msf.core.NotNull;
import dev.msf.core.checksum.XxHash3;
import dev.msf.core.model.MsfHeader;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes MSF files and their components.
 *
 * <p>All multi-byte integers are written little-endian, as required by MSF Spec Section 2.1.
 *
 * <p>The writer computes and embeds the header checksum automatically. Callers are responsible
 * for providing correct offset values — the writer does not perform block layout.
 *
 * <p>File checksum computation is deferred to {@link #writeFileChecksum(byte[], OutputStream)}
 * because the checksum covers all preceding file bytes and must be written last.
 *
 * @see MsfReader
 */
public final class MsfWriter {

    private MsfWriter() {
        // Utility class — no instances.
    }

    /**
     * Serialises the given {@link MsfHeader} to a 48-byte array.
     *
     * <p>The header checksum is computed from the first 40 bytes and written into bytes 40–47,
     * overwriting any checksum value previously set on the header. The returned array is the
     * canonical binary representation of the header as it must appear at offset 0 of an MSF file.
     *
     * @param header the header to serialise; all offset fields must be set correctly before calling
     * @return a 48-byte array containing the complete binary header with a valid checksum
     * @throws MsfParseException if any field value is out of its valid range
     */
    @NotNull
    public static byte[] writeHeader(@NotNull MsfHeader header) throws MsfParseException {
        validateHeader(header);

        byte[] buf = new byte[MsfHeader.HEADER_SIZE];

        // Magic bytes: 0x4D 0x53 0x46 0x21 ("MSF!")
        buf[0] = MsfHeader.MAGIC[0];
        buf[1] = MsfHeader.MAGIC[1];
        buf[2] = MsfHeader.MAGIC[2];
        buf[3] = MsfHeader.MAGIC[3];

        // u16 major version at offset 4
        writeLittleEndianU16(buf, 4, header.majorVersion());

        // u16 minor version at offset 6
        writeLittleEndianU16(buf, 6, header.minorVersion());

        // u32 feature flags at offset 8
        writeLittleEndianU32(buf, 8, header.featureFlags());

        // u32 MC data version at offset 12
        writeLittleEndianU32(buf, 12, header.mcDataVersion());

        // u32 metadata block offset at offset 16
        writeLittleEndianU32(buf, 16, header.metadataBlockOffset());

        // u32 global palette offset at offset 20
        writeLittleEndianU32(buf, 20, header.globalPaletteOffset());

        // u32 layer index offset at offset 24
        writeLittleEndianU32(buf, 24, header.layerIndexOffset());

        // u32 entity block offset at offset 28
        writeLittleEndianU32(buf, 28, header.entityBlockOffset());

        // u32 block entity block offset at offset 32
        writeLittleEndianU32(buf, 32, header.blockEntityBlockOffset());

        // u32 file size at offset 36
        writeLittleEndianU32(buf, 36, header.fileSize());

        // u64 header checksum at offset 40 — computed over bytes 0–39.
        long checksum = XxHash3.headerChecksum(buf);
        writeLittleEndianU64(buf, 40, checksum);

        return buf;
    }

    /**
     * Writes the header to an {@link OutputStream}.
     *
     * <p>Convenience wrapper around {@link #writeHeader(MsfHeader)} that writes the resulting
     * bytes directly to the provided stream.
     *
     * @param header the header to write
     * @param out    the output stream positioned at offset 0 of the target file
     * @throws MsfParseException if any header field is invalid
     * @throws IOException       if an IO error occurs while writing
     */
    public static void writeHeader(@NotNull MsfHeader header, @NotNull OutputStream out)
            throws MsfParseException, IOException {
        out.write(writeHeader(header));
    }

    /**
     * Computes and appends the 8-byte file checksum to an {@link OutputStream}.
     *
     * <p>This method MUST be called as the final write operation before closing the file.
     * The file checksum covers all bytes from offset 0 up to and not including the checksum
     * field itself. See MSF Spec Section 11.
     *
     * @param fileContentBeforeChecksum all bytes written to the file so far, NOT including
     *                                  the trailing checksum field
     * @param out                       the output stream to which the 8-byte checksum is appended
     * @throws IOException if an IO error occurs while writing
     */
    public static void writeFileChecksum(@NotNull byte[] fileContentBeforeChecksum, @NotNull OutputStream out)
            throws IOException {
        long checksum = XxHash3.hash(fileContentBeforeChecksum);
        byte[] checksumBytes = new byte[8];
        writeLittleEndianU64(checksumBytes, 0, checksum);
        out.write(checksumBytes);
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    /**
     * Validates that all header fields are within their legal ranges before writing.
     *
     * @param header the header to validate
     * @throws MsfParseException if any field is outside its valid range
     */
    private static void validateHeader(@NotNull MsfHeader header) throws MsfParseException {
        // u16 fields: 0–65535
        requireU16(header.majorVersion(), "majorVersion");
        requireU16(header.minorVersion(), "minorVersion");

        // u32 fields: 0–4294967295 (stored as long, so just check for negative)
        requireU32(header.featureFlags(), "featureFlags");
        requireU32(header.mcDataVersion(), "mcDataVersion");
        requireU32(header.metadataBlockOffset(), "metadataBlockOffset");
        requireU32(header.globalPaletteOffset(), "globalPaletteOffset");
        requireU32(header.layerIndexOffset(), "layerIndexOffset");
        requireU32(header.entityBlockOffset(), "entityBlockOffset");
        requireU32(header.blockEntityBlockOffset(), "blockEntityBlockOffset");
        requireU32(header.fileSize(), "fileSize");

        // Required offsets must not be 0 — per spec Section 3.5.
        if (header.metadataBlockOffset() == 0) {
            throw new MsfParseException("metadataBlockOffset must not be 0 — all MSF files require a metadata block", -1);
        }
        if (header.globalPaletteOffset() == 0) {
            throw new MsfParseException("globalPaletteOffset must not be 0 — all MSF files require a palette block", -1);
        }
        if (header.layerIndexOffset() == 0) {
            throw new MsfParseException("layerIndexOffset must not be 0 — all MSF files require a layer index block", -1);
        }

        // Entity/block-entity offsets must be 0 when the corresponding flags are not set.
        if (!header.hasFlag(MsfHeader.FLAG_HAS_ENTITIES) && header.entityBlockOffset() != 0) {
            throw new MsfParseException(
                "entityBlockOffset must be 0 when FLAG_HAS_ENTITIES is not set", -1
            );
        }
        if (!header.hasFlag(MsfHeader.FLAG_HAS_BLOCK_ENTITIES) && header.blockEntityBlockOffset() != 0) {
            throw new MsfParseException(
                "blockEntityBlockOffset must be 0 when FLAG_HAS_BLOCK_ENTITIES is not set", -1
            );
        }
    }

    private static void requireU16(int value, String field) throws MsfParseException {
        if (value < 0 || value > 0xFFFF) {
            throw new MsfParseException(
                String.format("Field %s value %d is out of u16 range [0, 65535]", field, value), -1
            );
        }
    }

    private static void requireU32(long value, String field) throws MsfParseException {
        if (value < 0 || value > 0xFFFFFFFFL) {
            throw new MsfParseException(
                String.format("Field %s value %d is out of u32 range [0, 4294967295]", field, value), -1
            );
        }
    }

    // -------------------------------------------------------------------------
    // Little-endian write helpers
    // -------------------------------------------------------------------------

    /**
     * Writes a little-endian u16 into {@code buf} at {@code offset}.
     */
    static void writeLittleEndianU16(@NotNull byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    /**
     * Writes a little-endian u32 into {@code buf} at {@code offset}.
     * Accepts a {@code long} to handle the full unsigned range.
     */
    static void writeLittleEndianU32(@NotNull byte[] buf, int offset, long value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    /**
     * Writes a little-endian u64 into {@code buf} at {@code offset}.
     */
    static void writeLittleEndianU64(@NotNull byte[] buf, int offset, long value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
        buf[offset + 4] = (byte) ((value >> 32) & 0xFF);
        buf[offset + 5] = (byte) ((value >> 40) & 0xFF);
        buf[offset + 6] = (byte) ((value >> 48) & 0xFF);
        buf[offset + 7] = (byte) ((value >> 56) & 0xFF);
    }
}
