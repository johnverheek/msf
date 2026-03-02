package dev.msf.core.io;

import dev.msf.core.MsfChecksumException;
import dev.msf.core.MsfException;
import dev.msf.core.MsfParseException;
import dev.msf.core.MsfVersionException;
import dev.msf.core.checksum.XxHash3;
import dev.msf.core.model.MsfHeader;
import dev.msf.core.MsfWarning;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Parses MSF file headers according to the MSF V1 specification.
 *
 * <p>Session 1 scope: header parsing, validation sequence, file size check, and file
 * checksum verification. Palette, layer index, region data, and remaining blocks are
 * implemented in Session 2.
 *
 * <h2>Validation sequence</h2>
 * <p>The header validation sequence is strictly ordered per Section 3.7:
 * <ol>
 *   <li>Read all 48 header bytes into a buffer</li>
 *   <li>Compare magic bytes individually at offsets 0–3</li>
 *   <li>Compute xxHash3-64 of bytes 0–39 and compare to the u64 at offsets 40–47</li>
 *   <li>Inspect the major version field</li>
 * </ol>
 * No header field other than magic bytes and checksum is acted upon until all three
 * steps pass. A reader that acts on any other field before passing all three steps
 * is non-conforming.
 *
 * <h2>Warning mechanism</h2>
 * <p>Warnings are delivered via an optional {@code Consumer<MsfWarning>}. Callers that
 * do not provide a consumer receive no warnings. Warnings are never emitted to stdout,
 * stderr, or any logging framework. See Section 3.5.1 for the full warning contract.
 *
 * <h2>File checksum behaviour</h2>
 * <p>By default, a file checksum failure throws {@link MsfChecksumException} after
 * emitting a {@link MsfWarning.Code#FILE_CHECKSUM_FAILURE} warning. To continue past
 * a file checksum failure, supply {@link MsfReaderConfig#allowChecksumFailure()}.
 *
 * @see MsfSpec Section 3 — header
 * @see MsfSpec Section 3.7 — header checksum and validation sequence
 * @see MsfSpec Section 11 — file checksum
 * @see MsfSpec Appendix E — unsigned integer handling in Java
 */
public final class MsfReader {

    private MsfReader() {
        // Utility class — all methods are static
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Reads and validates the header of an MSF file from the given path.
     *
     * <p>Uses {@link MsfReaderConfig#DEFAULT}: throws on file checksum failure.
     * No warning consumer is provided — warnings are silently discarded.
     *
     * @param path the path to the MSF file
     * @return the parsed and validated {@link MsfHeader}
     * @throws IOException      if an I/O error occurs reading the file
     * @throws MsfException     if the file is not a valid MSF V1 file
     */
    public static MsfHeader readHeader(Path path) throws IOException, MsfException {
        return readHeader(path, MsfReaderConfig.DEFAULT, null);
    }

    /**
     * Reads and validates the header of an MSF file from the given path.
     *
     * @param path            the path to the MSF file
     * @param warningConsumer receives warnings produced during parsing; may be {@code null}
     * @return the parsed and validated {@link MsfHeader}
     * @throws IOException      if an I/O error occurs reading the file
     * @throws MsfException     if the file is not a valid MSF V1 file
     */
    public static MsfHeader readHeader(
        Path path,
        Consumer<MsfWarning> warningConsumer
    ) throws IOException, MsfException {
        return readHeader(path, MsfReaderConfig.DEFAULT, warningConsumer);
    }

    /**
     * Reads and validates the header of an MSF file from the given path.
     *
     * @param path            the path to the MSF file
     * @param config          reader configuration
     * @param warningConsumer receives warnings produced during parsing; may be {@code null}
     * @return the parsed and validated {@link MsfHeader}
     * @throws IOException      if an I/O error occurs reading the file
     * @throws MsfException     if the file is not a valid MSF V1 file
     */
    public static MsfHeader readHeader(
        Path path,
        MsfReaderConfig config,
        Consumer<MsfWarning> warningConsumer
    ) throws IOException, MsfException {
        byte[] fileBytes = Files.readAllBytes(path);
        return readHeaderFromBytes(fileBytes, config, warningConsumer);
    }

    /**
     * Reads and validates the header of an MSF file from a byte array.
     *
     * <p>This overload is provided for testing and for callers that already have the file
     * content in memory.
     *
     * @param fileBytes       the complete content of the MSF file
     * @param config          reader configuration
     * @param warningConsumer receives warnings produced during parsing; may be {@code null}
     * @return the parsed and validated {@link MsfHeader}
     * @throws MsfException if the file is not a valid MSF V1 file
     */
    public static MsfHeader readHeaderFromBytes(
        byte[] fileBytes,
        MsfReaderConfig config,
        Consumer<MsfWarning> warningConsumer
    ) throws MsfException {
        // Per Section 3.1: if the file is shorter than 48 bytes we must throw MsfParseException
        // before performing any other validation.
        if (fileBytes.length < MsfHeader.HEADER_SIZE) {
            throw new MsfParseException(
                "File is too short to contain a valid MSF header: "
                + fileBytes.length + " bytes (minimum " + MsfHeader.HEADER_SIZE + " required)"
            );
        }

        // --- Step 1: Validate magic bytes (Section 3.1) ---
        // Magic bytes are NOT interpreted as a multi-byte integer; each is compared individually.
        for (int i = 0; i < MsfHeader.MAGIC.length; i++) {
            if (fileBytes[i] != MsfHeader.MAGIC[i]) {
                throw new MsfParseException(String.format(
                    "Magic byte mismatch at offset %d: expected 0x%02X, found 0x%02X",
                    i, MsfHeader.MAGIC[i] & 0xFF, fileBytes[i] & 0xFF
                ));
            }
        }

        // --- Step 2: Verify header checksum (Section 3.7) ---
        // Compute xxHash3-64 of bytes 0–39 and compare to the u64 at offsets 40–47.
        // We must not act on any other field until this passes.
        long computedHeaderChecksum = XxHash3.hash(fileBytes, 0, 40);
        ByteBuffer headerBuf = ByteBuffer.wrap(fileBytes, 0, MsfHeader.HEADER_SIZE)
                                         .order(ByteOrder.LITTLE_ENDIAN);
        long storedHeaderChecksum = headerBuf.getLong(40);

        if (computedHeaderChecksum != storedHeaderChecksum) {
            throw new MsfChecksumException(
                MsfChecksumException.ChecksumType.HEADER,
                storedHeaderChecksum,
                computedHeaderChecksum
            );
        }

        // --- Step 3: Inspect major version (Section 3.2) ---
        // After magic and checksum pass, we may now read other header fields.
        int majorVersion = Short.toUnsignedInt(headerBuf.getShort(4));
        if (majorVersion != MsfHeader.SUPPORTED_MAJOR_VERSION) {
            throw new MsfVersionException(majorVersion, MsfHeader.SUPPORTED_MAJOR_VERSION);
        }

        // --- Parse remaining header fields ---
        // All multi-byte integers are little-endian (Section 2.1).
        // u16 fields: mask with 0xFFFF (or use Short.toUnsignedInt)
        // u32 fields: mask with 0xFFFFFFFFL to get unsigned value (Appendix E)
        int minorVersion          = Short.toUnsignedInt(headerBuf.getShort(6));
        int featureFlags          = headerBuf.getInt(8);
        long mcDataVersion        = Integer.toUnsignedLong(headerBuf.getInt(12));
        long metadataOffset       = Integer.toUnsignedLong(headerBuf.getInt(16));
        long globalPaletteOffset  = Integer.toUnsignedLong(headerBuf.getInt(20));
        long layerIndexOffset     = Integer.toUnsignedLong(headerBuf.getInt(24));
        long entityBlockOffset    = Integer.toUnsignedLong(headerBuf.getInt(28));
        long blockEntityBlockOffset = Integer.toUnsignedLong(headerBuf.getInt(32));
        long fileSize             = Integer.toUnsignedLong(headerBuf.getInt(36));
        long headerChecksum       = headerBuf.getLong(40);

        // --- Validate required offsets (Section 3.5) ---
        if (metadataOffset == 0L) {
            throw new MsfParseException(
                "Metadata block offset at header offset 16 is 0 — all MSF files must contain a metadata block"
            );
        }
        if (globalPaletteOffset == 0L) {
            throw new MsfParseException(
                "Global palette offset at header offset 20 is 0 — all MSF files must contain a global palette block"
            );
        }
        if (layerIndexOffset == 0L) {
            throw new MsfParseException(
                "Layer index offset at header offset 24 is 0 — all MSF files must contain a layer index block"
            );
        }

        // Track whether a file size mismatch was found — needed to annotate a subsequent
        // file checksum failure warning (Section 3.6 and Section 11).
        boolean fileSizeMismatch = false;

        // --- File size check (Section 3.6) ---
        long actualFileSize = fileBytes.length;
        if (actualFileSize != fileSize) {
            fileSizeMismatch = true;
            warn(warningConsumer, MsfWarning.atOffset(
                MsfWarning.Code.FILE_SIZE_MISMATCH,
                String.format(
                    "File size mismatch: header declares %d bytes, actual file length is %d bytes — "
                    + "file may be truncated or corrupted",
                    fileSize, actualFileSize
                ),
                36L
            ));
        }

        // --- Reserved feature flag bits check (Section 3.3) ---
        // For V1.0 files (minor version == 0), reserved bits 10–31 must be 0.
        // For files with minor version > our implemented version, we must NOT warn,
        // as those bits may carry meaning in a later minor version.
        if (minorVersion == 0) {
            int reservedBits = featureFlags & MsfHeader.FeatureFlags.RESERVED_BITS_MASK;
            if (reservedBits != 0) {
                warn(warningConsumer, MsfWarning.atOffset(
                    MsfWarning.Code.RESERVED_FLAG_SET,
                    String.format(
                        "Reserved feature flag bits are set in a V1.0 file — "
                        + "reserved bits mask 0x%08X — non-conforming writer",
                        reservedBits & 0xFFFFFFFFL
                    ),
                    8L
                ));
            }
        }

        // --- Validate block offsets against file size (Section 3.5) ---
        checkOffsetBounds("metadata",          metadataOffset,          fileSize, warningConsumer);
        checkOffsetBounds("global palette",    globalPaletteOffset,     fileSize, warningConsumer);
        checkOffsetBounds("layer index",       layerIndexOffset,        fileSize, warningConsumer);
        checkOptionalOffsetBounds("entity block",       entityBlockOffset,       fileSize, warningConsumer);
        checkOptionalOffsetBounds("block entity block", blockEntityBlockOffset,  fileSize, warningConsumer);

        // --- Validate optional block offset vs feature flag consistency (Section 3.5) ---
        boolean hasEntitiesFlag = (featureFlags & MsfHeader.FeatureFlags.HAS_ENTITIES) != 0;
        boolean hasBlockEntitiesFlag = (featureFlags & MsfHeader.FeatureFlags.HAS_BLOCK_ENTITIES) != 0;

        if (hasEntitiesFlag && entityBlockOffset == 0L) {
            warn(warningConsumer, MsfWarning.atOffset(
                MsfWarning.Code.FEATURE_FLAG_CONFLICT,
                "Feature flag bit 0 (HAS_ENTITIES) is set but entity block offset is 0",
                28L
            ));
        } else if (!hasEntitiesFlag && entityBlockOffset != 0L) {
            warn(warningConsumer, MsfWarning.atOffset(
                MsfWarning.Code.FEATURE_FLAG_CONFLICT,
                "Entity block offset is non-zero but feature flag bit 0 (HAS_ENTITIES) is not set — "
                + "ignoring offset; feature flag is authoritative",
                28L
            ));
            // Per spec: the feature flag is authoritative; we treat the entity block as absent.
            // We do not modify the parsed header — the caller sees the raw values and the warning.
        }

        if (hasBlockEntitiesFlag && blockEntityBlockOffset == 0L) {
            warn(warningConsumer, MsfWarning.atOffset(
                MsfWarning.Code.FEATURE_FLAG_CONFLICT,
                "Feature flag bit 1 (HAS_BLOCK_ENTITIES) is set but block entity block offset is 0",
                32L
            ));
        } else if (!hasBlockEntitiesFlag && blockEntityBlockOffset != 0L) {
            warn(warningConsumer, MsfWarning.atOffset(
                MsfWarning.Code.FEATURE_FLAG_CONFLICT,
                "Block entity block offset is non-zero but feature flag bit 1 (HAS_BLOCK_ENTITIES) is not set — "
                + "ignoring offset; feature flag is authoritative",
                32L
            ));
        }

        MsfHeader header = new MsfHeader(
            majorVersion,
            minorVersion,
            featureFlags,
            mcDataVersion,
            metadataOffset,
            globalPaletteOffset,
            layerIndexOffset,
            entityBlockOffset,
            blockEntityBlockOffset,
            fileSize,
            headerChecksum
        );

        // --- File checksum verification (Section 11) ---
        // Verify after header checksum. Failure is a mandatory warning and a permitted stop.
        // Input: all bytes from 0 to (file_size - 9) inclusive — i.e. all bytes except the
        // final 8. We use the actual file bytes, not the declared file size, as the range.
        if (fileBytes.length >= 8) {
            int checksumDataLength = fileBytes.length - 8;
            long computedFileChecksum = XxHash3.hash(fileBytes, 0, checksumDataLength);
            // The stored file checksum is the final 8 bytes of the file.
            ByteBuffer tailBuf = ByteBuffer.wrap(fileBytes, checksumDataLength, 8)
                                           .order(ByteOrder.LITTLE_ENDIAN);
            long storedFileChecksum = tailBuf.getLong();

            if (computedFileChecksum != storedFileChecksum) {
                String checksumMsg;
                if (fileSizeMismatch) {
                    checksumMsg = String.format(
                        "File checksum mismatch: stored 0x%016X, computed 0x%016X — "
                        + "NOTE: this result is unreliable because a file size mismatch was "
                        + "also detected; the byte range input to the hash is undefined",
                        storedFileChecksum, computedFileChecksum
                    );
                } else {
                    checksumMsg = String.format(
                        "File checksum mismatch: stored 0x%016X, computed 0x%016X — "
                        + "file may be corrupted",
                        storedFileChecksum, computedFileChecksum
                    );
                }

                warn(warningConsumer, MsfWarning.atOffset(
                    MsfWarning.Code.FILE_CHECKSUM_FAILURE,
                    checksumMsg,
                    (long) (fileBytes.length - 8)
                ));

                if (!config.continueOnFileChecksumFailure()) {
                    throw new MsfChecksumException(
                        MsfChecksumException.ChecksumType.FILE,
                        storedFileChecksum,
                        computedFileChecksum
                    );
                }
            }
        } else {
            // File is too short to contain a file checksum — this was already caught above
            // if the file is shorter than 48 bytes. If it's between 48 and 7 bytes, we simply
            // cannot verify the file checksum. This case is extremely unlikely in practice.
            warn(warningConsumer, MsfWarning.atOffset(
                MsfWarning.Code.FILE_SIZE_MISMATCH,
                "File is too short (" + fileBytes.length + " bytes) to contain a valid file checksum",
                (long) fileBytes.length
            ));
        }

        return header;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Checks that a required block offset is within the bounds declared by {@code fileSize}.
     * Per Section 3.5, a non-zero offset at or beyond {@code fileSize} is an
     * {@link MsfParseException} regardless of which block it references.
     *
     * @param blockName       human-readable name of the block for error messages
     * @param offset          the block offset to validate
     * @param fileSize        the declared file size from the header
     * @param warningConsumer warning consumer; may be null
     * @throws MsfParseException if the offset is at or beyond fileSize
     */
    private static void checkOffsetBounds(
        String blockName,
        long offset,
        long fileSize,
        Consumer<MsfWarning> warningConsumer
    ) throws MsfParseException {
        if (offset != 0L && offset >= fileSize) {
            // For required blocks, this is always a hard error regardless.
            // We also emit a warning first to populate the warning consumer.
            warn(warningConsumer, MsfWarning.atOffset(
                MsfWarning.Code.OFFSET_BEYOND_FILE_SIZE,
                String.format(
                    "%s offset 0x%X is at or beyond file size %d — file is corrupt",
                    blockName, offset, fileSize
                ),
                offset
            ));
            throw new MsfParseException(String.format(
                "%s offset 0x%X is at or beyond declared file size %d",
                blockName, offset, fileSize
            ));
        }
    }

    /**
     * Checks that an optional block offset, if non-zero, is within the bounds declared by
     * {@code fileSize}. Emits {@link MsfWarning.Code#OFFSET_BEYOND_FILE_SIZE} if violated.
     * Does not throw — per Section 3.5, this is advisory for optional blocks.
     *
     * @param blockName       human-readable name of the block for warning messages
     * @param offset          the block offset to validate (0 is always valid for optional blocks)
     * @param fileSize        the declared file size from the header
     * @param warningConsumer warning consumer; may be null
     */
    private static void checkOptionalOffsetBounds(
        String blockName,
        long offset,
        long fileSize,
        Consumer<MsfWarning> warningConsumer
    ) {
        if (offset != 0L && offset >= fileSize) {
            warn(warningConsumer, MsfWarning.atOffset(
                MsfWarning.Code.OFFSET_BEYOND_FILE_SIZE,
                String.format(
                    "%s offset 0x%X is at or beyond file size %d — treating block as absent",
                    blockName, offset, fileSize
                ),
                offset
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
