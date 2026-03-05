package dev.msf.core;

/**
 * A non-fatal diagnostic condition produced during MSF read or write operations.
 *
 * <p>Warnings represent conditions that do not prevent parsing from continuing but indicate
 * a non-conforming file or writer, a potential data integrity issue, or an advisory notice.
 * Callers receive warnings via a {@code Consumer<MsfWarning>} provided to reader and writer
 * methods. Callers that do not provide a consumer receive no warnings.
 *
 * <p>Warnings MUST NOT be emitted to stdout, stderr, or any logging framework by default.
 * Routing warnings is the caller's responsibility.
 *
 * @param code    the machine-readable warning code identifying the condition
 * @param message a human-readable description of the warning condition
 * @param offset  the byte offset in the file where the condition was detected,
 *                or {@code -1} for write-side warnings where no file offset is meaningful
 *
 * @see MsfSpec Section 3.5.1 — warning mechanism
 */
public record MsfWarning(
    Code code,
    String message,
    long offset
) {

    /**
     * Machine-readable codes identifying each defined warning condition.
     *
     * <p>All seven codes defined in Section 3.5.1 of the MSF specification are represented here.
     *
     * @see MsfSpec Section 3.5.1 — defined warning codes
     */
    public enum Code {

        /**
         * Reader detected reserved bits that are set in a field that requires them to be zero.
         *
         * <p>Applies to:
         * <ul>
         *   <li>Feature flag bits 10–31 in a V1.0 file (Section 3.3)</li>
         *   <li>Rotation compatibility bits 5–7 (Section 10.3)</li>
         *   <li>Layer flags bits 2–7 (Section 6.3)</li>
         * </ul>
         */
        RESERVED_FLAG_SET,

        /**
         * Writer cleared reserved bits that the caller provided.
         *
         * <p>Applies to:
         * <ul>
         *   <li>Feature flags provided with bits 10–31 set (Section 3.3)</li>
         *   <li>Rotation compatibility value with bits 5–7 set (Section 10.3)</li>
         *   <li>Layer flags with bits 2–7 set (Section 6.3)</li>
         * </ul>
         */
        RESERVED_FLAG_CLEARED,

        /**
         * The actual file length does not match the {@code file_size} field in the header.
         *
         * <p>Indicates the file may be truncated or corrupted. When this warning is emitted,
         * any subsequent {@link #FILE_CHECKSUM_FAILURE} warning MUST note that the checksum
         * result is unreliable due to this size mismatch.
         *
         * @see MsfSpec Section 3.6 — file size
         */
        FILE_SIZE_MISMATCH,

        /**
         * File checksum verification failed.
         *
         * <p>The xxHash3-64 digest of the file content does not match the digest stored
         * in the final 8 bytes of the file. If a {@link #FILE_SIZE_MISMATCH} was emitted
         * during the same operation, this warning message MUST note that the result is
         * unreliable.
         *
         * @see MsfSpec Section 11 — file checksum
         */
        FILE_CHECKSUM_FAILURE,

        /**
         * An offset field's state conflicts with the corresponding feature flag.
         *
         * <p>Examples:
         * <ul>
         *   <li>Entity block offset is non-zero but feature flag bit 0 is not set</li>
         *   <li>Entity block offset is zero but feature flag bit 0 is set</li>
         * </ul>
         *
         * @see MsfSpec Section 3.5 — block offsets
         */
        FEATURE_FLAG_CONFLICT,

        /**
         * A non-zero block offset points to a byte position at or beyond the {@code file_size} value.
         *
         * @see MsfSpec Section 3.5 — block offsets
         */
        OFFSET_BEYOND_FILE_SIZE,

        /**
         * The MC data version in the file header differs from the currently active game version.
         *
         * @see MsfSpec Section 3.4 — MC data version
         */
        DATA_VERSION_MISMATCH,

        /**
         * The thumbnail bytes in the metadata block do not constitute a valid PNG file.
         *
         * <p>The thumbnail size field is non-zero but the bytes do not begin with the PNG
         * magic signature. Parsing continues — exactly {@code thumbnailSize} bytes are consumed.
         * Readers MUST NOT reject the file solely because the thumbnail is malformed.
         *
         * @see MsfSpec Section 5.2 — thumbnail
         */
        MALFORMED_THUMBNAIL,

        /**
         * A mirror transform was applied to a schematic that contains asymmetric mechanical
         * blocks (e.g. pistons, hoppers, comparators). The mirrored placement may not function
         * as the original author intended.
         *
         * <p>This is an advisory placement-side code. It is defined here for tools to use
         * during placement operations and is NOT emitted by {@link dev.msf.core.io.MsfReader}
         * or {@link dev.msf.core.io.MsfWriter}.
         *
         * @see MsfSpec Section 3.5.1 — warning codes
         */
        MIRROR_RISK,

        /**
         * A layer was placed without its declared dependencies having been placed first.
         * The placement may be incomplete or non-functional as a result.
         *
         * <p>This is an advisory placement-side code. It is defined here for tools to use
         * during placement operations and is NOT emitted by {@link dev.msf.core.io.MsfReader}
         * or {@link dev.msf.core.io.MsfWriter}.
         *
         * @see MsfSpec Section 3.5.1 — warning codes
         */
        UNMET_DEPENDENCY,

        /**
         * The {@code mc_edition} field in the metadata block indicates an edition other than
         * Java Edition (0x00), but this reader implements Java Edition only.
         *
         * <p>The schematic was likely produced from a Bedrock Edition world. Block state strings,
         * entity type identifiers, biome identifiers, and NBT structure may differ from
         * Java Edition conventions. Parsing continues, but placement results may be incorrect.
         *
         * @see MsfSpec Section 5.1 — metadata block trailing fields
         * @see MsfSpec Section 3.5.1 — warning codes
         */
        EDITION_MISMATCH
    }

    /**
     * Convenience factory for a warning with no meaningful file offset (write-side warnings).
     *
     * @param code    the warning code
     * @param message the warning message
     * @return a new {@code MsfWarning} with offset {@code -1}
     */
    public static MsfWarning writeWarning(Code code, String message) {
        return new MsfWarning(code, message, -1L);
    }

    /**
     * Convenience factory for a warning at a specific file offset.
     *
     * @param code    the warning code
     * @param message the warning message
     * @param offset  the byte offset in the file where the condition was detected
     * @return a new {@code MsfWarning} with the given offset
     */
    public static MsfWarning atOffset(Code code, String message, long offset) {
        return new MsfWarning(code, message, offset);
    }
}
