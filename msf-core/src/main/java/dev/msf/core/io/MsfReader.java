package dev.msf.core.io;

import dev.msf.core.MsfChecksumException;
import dev.msf.core.MsfParseException;
import dev.msf.core.MsfVersionException;
import dev.msf.core.NotNull;
import dev.msf.core.checksum.XxHash3;
import dev.msf.core.model.MsfHeader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads and validates MSF files.
 *
 * <p>The reader enforces the failure precedence defined in MSF Spec Section 12.3 and the
 * MSF Technical Constraints document:
 * <ol>
 *   <li>Magic byte mismatch → {@link MsfParseException}, stop immediately</li>
 *   <li>Header checksum failure → {@link MsfChecksumException}, stop immediately</li>
 *   <li>Unsupported major version → {@link MsfVersionException}, stop immediately</li>
 *   <li>File size mismatch → warn, attempt to continue</li>
 *   <li>File checksum failure → warn, continue at caller's discretion</li>
 *   <li>Unknown feature flag → ignore, continue</li>
 *   <li>Unknown block type → skip via length prefix, continue</li>
 * </ol>
 *
 * <p>This class reads only the header. Higher-level parsers are responsible for reading
 * the blocks located via the header offsets.
 */
public final class MsfReader {

    private static final Logger LOG = Logger.getLogger(MsfReader.class.getName());

    private MsfReader() {
        // Utility class — no instances.
    }

    /**
     * Reads and validates the 48-byte MSF header from the beginning of the given byte array.
     *
     * <p>The full file bytes must be supplied so that the file size field can be verified against
     * the actual length. The file checksum is also verified here because the entire file is
     * available.
     *
     * <p>Failure precedence is strictly enforced:
     * <ol>
     *   <li>Magic bytes are checked first — {@link MsfParseException} on mismatch</li>
     *   <li>Header checksum is verified second — {@link MsfChecksumException} on mismatch</li>
     *   <li>Major version is checked third — {@link MsfVersionException} if unsupported</li>
     *   <li>File size is verified — warning logged on mismatch, parsing continues</li>
     *   <li>File checksum is verified — warning logged on mismatch, caller must decide</li>
     * </ol>
     *
     * @param fileBytes the complete contents of an MSF file
     * @return the parsed and validated {@link MsfHeader}
     * @throws MsfParseException     if the magic bytes do not match
     * @throws MsfChecksumException  if the header checksum fails
     * @throws MsfVersionException   if the major version is not supported
     * @throws IOException           if an IO error occurs (not applicable here but declared for
     *                               consistency with the stream-based overload)
     */
    @NotNull
    public static MsfHeader readHeader(@NotNull byte[] fileBytes)
            throws MsfParseException, MsfChecksumException, MsfVersionException, IOException {

        if (fileBytes.length < MsfHeader.HEADER_SIZE) {
            throw new MsfParseException(
                "File is too short to contain a valid MSF header — expected at least "
                    + MsfHeader.HEADER_SIZE + " bytes, got " + fileBytes.length,
                0L
            );
        }

        // --- Step 1: Magic bytes — fail immediately on mismatch. ---
        verifyMagic(fileBytes);

        // --- Step 2: Header checksum — fail immediately on mismatch. ---
        // The checksum covers bytes 0–39; the checksum field itself is at bytes 40–47.
        long storedHeaderChecksum = readLittleEndianU64(fileBytes, 40);
        long computedHeaderChecksum = XxHash3.headerChecksum(fileBytes);
        if (storedHeaderChecksum != computedHeaderChecksum) {
            throw new MsfChecksumException(true, computedHeaderChecksum, storedHeaderChecksum);
        }

        // --- Step 3: Major version — fail immediately if unsupported. ---
        int majorVersion = readLittleEndianU16(fileBytes, 4);
        if (majorVersion != MsfHeader.SUPPORTED_MAJOR_VERSION) {
            throw new MsfVersionException(majorVersion, MsfHeader.SUPPORTED_MAJOR_VERSION);
        }

        // All remaining fields are now safe to read.
        int minorVersion = readLittleEndianU16(fileBytes, 6);
        long featureFlags = readLittleEndianU32(fileBytes, 8);
        long mcDataVersion = readLittleEndianU32(fileBytes, 12);
        long metadataBlockOffset = readLittleEndianU32(fileBytes, 16);
        long globalPaletteOffset = readLittleEndianU32(fileBytes, 20);
        long layerIndexOffset = readLittleEndianU32(fileBytes, 24);
        long entityBlockOffset = readLittleEndianU32(fileBytes, 28);
        long blockEntityBlockOffset = readLittleEndianU32(fileBytes, 32);
        long storedFileSize = readLittleEndianU32(fileBytes, 36);

        // --- Step 4: File size mismatch — warn, continue. ---
        if (storedFileSize != fileBytes.length) {
            LOG.warning(String.format(
                "MSF file size mismatch — header declares %d bytes, actual file is %d bytes. "
                    + "The file may be truncated or corrupted.",
                storedFileSize, fileBytes.length
            ));
        }

        // --- Step 5: File checksum — warn, caller decides. ---
        if (fileBytes.length >= 9) {
            long computedFileChecksum = XxHash3.fileChecksum(fileBytes);
            long storedFileChecksum = readLittleEndianU64(fileBytes, fileBytes.length - 8);
            if (computedFileChecksum != storedFileChecksum) {
                LOG.warning(String.format(
                    "MSF file checksum mismatch — computed 0x%016X, stored 0x%016X. "
                        + "The file may be corrupted.",
                    computedFileChecksum, storedFileChecksum
                ));
            }
        }

        return new MsfHeader(
            majorVersion,
            minorVersion,
            featureFlags,
            mcDataVersion,
            metadataBlockOffset,
            globalPaletteOffset,
            layerIndexOffset,
            entityBlockOffset,
            blockEntityBlockOffset,
            storedFileSize,
            storedHeaderChecksum
        );
    }

    /**
     * Reads and validates the MSF header from an {@link InputStream}.
     *
     * <p>Reads exactly {@value MsfHeader#HEADER_SIZE} bytes. Because the full file is not
     * available, file size and file checksum cannot be verified — only magic, header checksum,
     * and major version are checked.
     *
     * <p>The failure precedence for magic, header checksum, and major version is the same as
     * {@link #readHeader(byte[])}.
     *
     * @param stream an {@code InputStream} positioned at the beginning of an MSF file
     * @return the parsed and validated {@link MsfHeader}
     * @throws MsfParseException    if the magic bytes do not match or the stream is too short
     * @throws MsfChecksumException if the header checksum fails
     * @throws MsfVersionException  if the major version is not supported
     * @throws IOException          if an IO error occurs while reading the stream
     */
    @NotNull
    public static MsfHeader readHeader(@NotNull InputStream stream)
            throws MsfParseException, MsfChecksumException, MsfVersionException, IOException {

        byte[] headerBytes = stream.readNBytes(MsfHeader.HEADER_SIZE);
        if (headerBytes.length < MsfHeader.HEADER_SIZE) {
            throw new MsfParseException(
                "Stream ended before a complete MSF header could be read — expected "
                    + MsfHeader.HEADER_SIZE + " bytes, got " + headerBytes.length,
                headerBytes.length
            );
        }

        // Step 1: Magic bytes.
        verifyMagic(headerBytes);

        // Step 2: Header checksum.
        long storedChecksum = readLittleEndianU64(headerBytes, 40);
        long computedChecksum = XxHash3.headerChecksum(headerBytes);
        if (storedChecksum != computedChecksum) {
            throw new MsfChecksumException(true, computedChecksum, storedChecksum);
        }

        // Step 3: Major version.
        int majorVersion = readLittleEndianU16(headerBytes, 0 + 4);
        if (majorVersion != MsfHeader.SUPPORTED_MAJOR_VERSION) {
            throw new MsfVersionException(majorVersion, MsfHeader.SUPPORTED_MAJOR_VERSION);
        }

        int minorVersion = readLittleEndianU16(headerBytes, 6);
        long featureFlags = readLittleEndianU32(headerBytes, 8);
        long mcDataVersion = readLittleEndianU32(headerBytes, 12);
        long metadataBlockOffset = readLittleEndianU32(headerBytes, 16);
        long globalPaletteOffset = readLittleEndianU32(headerBytes, 20);
        long layerIndexOffset = readLittleEndianU32(headerBytes, 24);
        long entityBlockOffset = readLittleEndianU32(headerBytes, 28);
        long blockEntityBlockOffset = readLittleEndianU32(headerBytes, 32);
        long fileSize = readLittleEndianU32(headerBytes, 36);

        // File size and file checksum cannot be verified without the full file.
        LOG.fine("Header read from stream — file size and file checksum not verified.");

        return new MsfHeader(
            majorVersion,
            minorVersion,
            featureFlags,
            mcDataVersion,
            metadataBlockOffset,
            globalPaletteOffset,
            layerIndexOffset,
            entityBlockOffset,
            blockEntityBlockOffset,
            fileSize,
            storedChecksum
        );
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Verifies that the first 4 bytes of {@code data} match the MSF magic bytes.
     *
     * @param data the byte array (must be at least 4 bytes long)
     * @throws MsfParseException if the magic bytes do not match
     */
    private static void verifyMagic(@NotNull byte[] data) throws MsfParseException {
        for (int i = 0; i < MsfHeader.MAGIC.length; i++) {
            if (data[i] != MsfHeader.MAGIC[i]) {
                throw new MsfParseException(
                    String.format(
                        "Magic byte mismatch at offset %d — expected 0x%02X, got 0x%02X. "
                            + "This is not an MSF file.",
                        i, MsfHeader.MAGIC[i] & 0xFF, data[i] & 0xFF
                    ),
                    i
                );
            }
        }
    }

    /**
     * Reads a little-endian unsigned 16-bit integer from {@code data} at {@code offset}.
     * Returns a non-negative Java {@code int}.
     */
    static int readLittleEndianU16(@NotNull byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    /**
     * Reads a little-endian unsigned 32-bit integer from {@code data} at {@code offset}.
     * Returns a non-negative Java {@code long} to preserve the full u32 range.
     * Per MSF Spec Appendix E.
     */
    static long readLittleEndianU32(@NotNull byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF))
            | ((long) (data[offset + 1] & 0xFF) << 8)
            | ((long) (data[offset + 2] & 0xFF) << 16)
            | ((long) (data[offset + 3] & 0xFF) << 24);
    }

    /**
     * Reads a little-endian 64-bit value from {@code data} at {@code offset}.
     * Returns a Java {@code long}; callers needing unsigned comparisons must use
     * {@link Long#compareUnsigned}. Per MSF Spec Appendix E.
     */
    static long readLittleEndianU64(@NotNull byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF))
            | ((long) (data[offset + 1] & 0xFF) << 8)
            | ((long) (data[offset + 2] & 0xFF) << 16)
            | ((long) (data[offset + 3] & 0xFF) << 24)
            | ((long) (data[offset + 4] & 0xFF) << 32)
            | ((long) (data[offset + 5] & 0xFF) << 40)
            | ((long) (data[offset + 6] & 0xFF) << 48)
            | ((long) (data[offset + 7] & 0xFF) << 56);
    }
}
