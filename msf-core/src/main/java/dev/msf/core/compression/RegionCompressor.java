package dev.msf.core.compression;

import com.github.luben.zstd.Zstd;
import dev.msf.core.MsfCompressionException;
import net.jpountz.lz4.LZ4Factory;

import java.util.Arrays;

/**
 * Compresses region payloads using the compression type specified for each region.
 *
 * <p>Writers SHOULD use zstd compression ({@link CompressionType#ZSTD}), which is the
 * default and recommended type. All four compression types are supported.
 *
 * <p>Brotli compression is implemented as a pure-Java literal (uncompressed) brotli stream,
 * which produces valid brotli output decompressable by {@code org.brotli:dec}.
 *
 * @see MsfSpec Section 7.2 — compression types
 */
public final class RegionCompressor {

    private RegionCompressor() {}

    /**
     * Compresses a region payload using the given compression type.
     *
     * @param data            the uncompressed payload bytes
     * @param compressionType the compression algorithm to apply
     * @return the compressed bytes
     * @throws MsfCompressionException if compression fails
     */
    public static byte[] compress(byte[] data, CompressionType compressionType)
            throws MsfCompressionException {
        return switch (compressionType) {
            case NONE   -> Arrays.copyOf(data, data.length);
            case ZSTD   -> compressZstd(data);
            case LZ4    -> compressLz4(data);
            case BROTLI -> compressBrotli(data);
        };
    }

    private static byte[] compressZstd(byte[] data) throws MsfCompressionException {
        try {
            byte[] compressed = Zstd.compress(data);
            if (Zstd.isError(compressed.length)) {
                throw new MsfCompressionException(
                    "zstd compression failed: " + Zstd.getErrorName(compressed.length));
            }
            return compressed;
        } catch (MsfCompressionException e) {
            throw e;
        } catch (Exception e) {
            throw new MsfCompressionException("zstd compression failed: " + e.getMessage(), e);
        }
    }

    private static byte[] compressLz4(byte[] data) throws MsfCompressionException {
        try {
            LZ4Factory factory = LZ4Factory.fastestInstance();
            return factory.fastCompressor().compress(data);
        } catch (Exception e) {
            throw new MsfCompressionException("lz4 compression failed: " + e.getMessage(), e);
        }
    }

    private static byte[] compressBrotli(byte[] data) throws MsfCompressionException {
        try {
            return BrotliLiteralStream.encode(data);
        } catch (Exception e) {
            throw new MsfCompressionException("brotli encoding failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Pure-Java brotli literal stream encoder (RFC 7932)
    // -------------------------------------------------------------------------

    /**
     * Writes data as a valid brotli literal (ISUNCOMPRESSED=1) stream.
     * Split into metablocks of at most 16 MiB each.
     */
    static final class BrotliLiteralStream {

        private static final int MAX_MLEN = 1 << 24; // 16 MiB per metablock

        static byte[] encode(byte[] data) {
            BitWriter bw = new BitWriter();
            // Stream header: 1 bit = 0 → window_bits = 16 (RFC 7932 §9.1)
            bw.writeBits(0, 1);

            // Write each chunk as a non-last ISUNCOMPRESSED=1 metablock.
            // ISUNCOMPRESSED is only parsed by the decoder when ISLAST=0,
            // so we must never set ISLAST=1 on a data block.
            int offset = 0;
            while (offset < data.length) {
                int chunkLen = Math.min(MAX_MLEN, data.length - offset);
                writeUncompressedMetablock(bw, data, offset, chunkLen);
                offset += chunkLen;
            }

            // Empty last metablock: ISLAST=1, ISLASTEMPTY=1 (RFC 7932 §9.2)
            bw.writeBits(1, 1);
            bw.writeBits(1, 1);

            return bw.toByteArray();
        }

        /**
         * Writes a non-last metablock with ISUNCOMPRESSED=1 (RFC 7932 §9.2).
         * ISLAST is always 0: ISUNCOMPRESSED is only decoded when ISLAST=0.
         */
        private static void writeUncompressedMetablock(
                BitWriter bw, byte[] data, int offset, int mlen) {
            bw.writeBits(0, 1); // ISLAST = 0 (ISUNCOMPRESSED only parsed when ISLAST=0)

            // MLEN: 2-bit MNIBBLES field (raw value N → N+4 nibbles), then (mlen-1) in nibbles.
            // Decoder rejects the stream with "Exuberant nibble" if the top nibble is zero
            // and sizeNibbles > 4, so we must use the minimum sizeNibbles:
            //   sizeNibbles=4 (raw=0): mlenMinus1 in [0, 2^16-1]  — no exuberant check
            //   sizeNibbles=5 (raw=1): mlenMinus1 in [2^16, 2^20-1] — top nibble guaranteed ≠ 0
            //   sizeNibbles=6 (raw=2): mlenMinus1 in [2^20, 2^24-1] — top nibble guaranteed ≠ 0
            int mlenMinus1 = mlen - 1;
            int rawNibbles;
            int mlenBits;
            if (mlenMinus1 < (1 << 16)) {
                rawNibbles = 0; mlenBits = 16; // sizeNibbles=4, no exuberant check
            } else if (mlenMinus1 < (1 << 20)) {
                rawNibbles = 1; mlenBits = 20; // sizeNibbles=5
            } else {
                rawNibbles = 2; mlenBits = 24; // sizeNibbles=6
            }
            bw.writeBits(rawNibbles, 2);
            bw.writeBits(mlenMinus1, mlenBits);

            bw.writeBits(1, 1); // ISUNCOMPRESSED = 1

            bw.byteAlign(); // Required before literal data

            bw.writeBytes(data, offset, mlen); // raw bytes
        }
    }

    /** Bit-level writer; bits are written LSB first within each byte. */
    static final class BitWriter {
        private final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        private int currentByte = 0;
        private int bitsInCurrent = 0;

        void writeBits(int value, int count) {
            for (int i = 0; i < count; i++) {
                currentByte |= ((value >> i) & 1) << bitsInCurrent;
                if (++bitsInCurrent == 8) {
                    buf.write(currentByte);
                    currentByte = 0;
                    bitsInCurrent = 0;
                }
            }
        }

        void byteAlign() {
            if (bitsInCurrent > 0) {
                buf.write(currentByte);
                currentByte = 0;
                bitsInCurrent = 0;
            }
        }

        void writeBytes(byte[] data, int offset, int length) {
            buf.write(data, offset, length);
        }

        byte[] toByteArray() {
            byteAlign();
            return buf.toByteArray();
        }
    }
}
