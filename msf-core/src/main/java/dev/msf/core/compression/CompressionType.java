package dev.msf.core.compression;

/**
 * Declares the compression types supported by MSF region payloads.
 *
 * <p>Per MSF Spec Section 7.2, each region declares its compression type as a single byte.
 * Writers SHOULD use zstd. Readers MUST support all four types.
 *
 * <p>Uses a sealed interface with record implementations so that switch expressions over
 * compression types are exhaustive without a default branch.
 */
public sealed interface CompressionType permits
        CompressionType.None,
        CompressionType.Zstd,
        CompressionType.Lz4,
        CompressionType.Brotli {

    /** Wire byte identifying this compression type in the region header. */
    byte wireId();

    /** Human-readable name for this compression type. */
    String displayName();

    /**
     * No compression — region payload is stored as raw bytes.
     * Wire ID: {@code 0x00}.
     */
    record None() implements CompressionType {
        @Override
        public byte wireId() { return 0x00; }

        @Override
        public String displayName() { return "none"; }
    }

    /**
     * Zstd compression — the default and recommended compression type.
     * Wire ID: {@code 0x01}.
     *
     * @see <a href="https://facebook.github.io/zstd/">zstd specification</a>
     */
    record Zstd() implements CompressionType {
        @Override
        public byte wireId() { return 0x01; }

        @Override
        public String displayName() { return "zstd"; }
    }

    /**
     * LZ4 compression — faster decompression than zstd at lower compression ratio.
     * Wire ID: {@code 0x02}.
     */
    record Lz4() implements CompressionType {
        @Override
        public byte wireId() { return 0x02; }

        @Override
        public String displayName() { return "lz4"; }
    }

    /**
     * Brotli compression — higher compression ratio at the cost of decompression speed.
     * Wire ID: {@code 0x03}.
     */
    record Brotli() implements CompressionType {
        @Override
        public byte wireId() { return 0x03; }

        @Override
        public String displayName() { return "brotli"; }
    }

    /** Canonical instance for no compression. */
    CompressionType NONE = new None();

    /** Canonical instance for zstd — the recommended default. */
    CompressionType ZSTD = new Zstd();

    /** Canonical instance for lz4. */
    CompressionType LZ4 = new Lz4();

    /** Canonical instance for brotli. */
    CompressionType BROTLI = new Brotli();

    /**
     * Resolves a wire ID byte to a {@link CompressionType}.
     *
     * @param wireId the byte read from the region header compression type field
     * @return the matching {@code CompressionType}
     * @throws dev.msf.core.MsfCompressionException if the wire ID is not a recognised compression type
     */
    static CompressionType fromWireId(byte wireId) throws dev.msf.core.MsfCompressionException {
        return switch (wireId) {
            case 0x00 -> NONE;
            case 0x01 -> ZSTD;
            case 0x02 -> LZ4;
            case 0x03 -> BROTLI;
            default -> throw new dev.msf.core.MsfCompressionException(
                String.format("Unknown compression type wire ID 0x%02X", wireId), null
            );
        };
    }
}
