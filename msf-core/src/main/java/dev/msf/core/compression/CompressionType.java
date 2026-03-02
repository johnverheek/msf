package dev.msf.core.compression;

/**
 * Compression type values for MSF region payloads, as defined in Section 7.2.
 *
 * <pre>
 *     0x00  None (raw)
 *     0x01  zstd (default, RECOMMENDED)
 *     0x02  lz4
 *     0x03  brotli
 * </pre>
 *
 * <p>Values 0x04–0xFF are reserved. Readers encountering an unrecognized compression type
 * MUST throw {@link dev.msf.core.MsfParseException}.
 *
 * @see MsfSpec Section 7.2 — compression types
 */
public enum CompressionType {

    /** {@code 0x00} — no compression; payload is stored raw. */
    NONE(0x00),

    /** {@code 0x01} — zstd compression (default, RECOMMENDED). */
    ZSTD(0x01),

    /** {@code 0x02} — lz4 compression. */
    LZ4(0x02),

    /** {@code 0x03} — brotli compression. */
    BROTLI(0x03);

    /** The byte value stored in the MSF file. */
    public final int byteValue;

    CompressionType(int byteValue) {
        this.byteValue = byteValue;
    }

    /**
     * Resolves a raw byte value from the file to a {@link CompressionType}.
     *
     * @param value the raw byte value read from the file (0x00–0xFF)
     * @return the matching {@link CompressionType}
     * @throws dev.msf.core.MsfParseException if the value does not correspond to a
     *         defined compression type
     */
    public static CompressionType fromByte(int value) throws dev.msf.core.MsfParseException {
        return switch (value) {
            case 0x00 -> NONE;
            case 0x01 -> ZSTD;
            case 0x02 -> LZ4;
            case 0x03 -> BROTLI;
            default   -> throw new dev.msf.core.MsfParseException(String.format(
                "Unrecognized compression type 0x%02X — values 0x04–0xFF are reserved "
                + "(Section 7.2); cannot safely decompress region payload",
                value
            ));
        };
    }
}
