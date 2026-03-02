package dev.msf.core.compression;

import com.github.luben.zstd.Zstd;
import dev.msf.core.MsfCompressionException;
import dev.msf.core.MsfParseException;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;
import org.brotli.dec.BrotliInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Decompresses region payloads according to the compression type stored in each region header.
 *
 * <p>All four compression types defined in Section 7.2 are fully supported:
 * <ul>
 *   <li>{@code 0x00} None — passthrough</li>
 *   <li>{@code 0x01} zstd — via {@code com.github.luben:zstd-jni}</li>
 *   <li>{@code 0x02} lz4  — via {@code org.lz4:lz4-java}</li>
 *   <li>{@code 0x03} brotli — via {@code org.brotli:dec}</li>
 * </ul>
 *
 * <p>Readers encountering an unrecognized compression type byte MUST throw
 * {@link MsfParseException} — this is handled by {@link CompressionType#fromByte(int)}.
 *
 * @see MsfSpec Section 7.2 — compression types
 */
public final class RegionDecompressor {

    private RegionDecompressor() {
        // Utility class — not instantiable
    }

    /**
     * Decompresses the given data using the specified compression algorithm.
     *
     * @param compressed          the compressed bytes to decompress
     * @param compressionType     the algorithm used to compress the data
     * @param uncompressedLength  the expected byte length of the decompressed output;
     *                            used to allocate buffers and validate output length
     * @return the decompressed bytes
     * @throws MsfCompressionException if decompression fails for any reason
     */
    public static byte[] decompress(byte[] compressed, CompressionType compressionType,
                                    int uncompressedLength) throws MsfCompressionException {
        return switch (compressionType) {
            case NONE   -> decompressNone(compressed, uncompressedLength);
            case ZSTD   -> decompressZstd(compressed, uncompressedLength);
            case LZ4    -> decompressLz4(compressed, uncompressedLength);
            case BROTLI -> decompressBrotli(compressed, uncompressedLength);
        };
    }

    // -------------------------------------------------------------------------
    // Private implementations
    // -------------------------------------------------------------------------

    private static byte[] decompressNone(byte[] data, int expectedLength)
            throws MsfCompressionException {
        if (data.length != expectedLength) {
            throw new MsfCompressionException(String.format(
                "NONE-compressed region has %d bytes but uncompressed_data_length declares %d",
                data.length, expectedLength
            ));
        }
        return Arrays.copyOf(data, data.length);
    }

    private static byte[] decompressZstd(byte[] compressed, int uncompressedLength)
            throws MsfCompressionException {
        try {
            byte[] result = Zstd.decompress(compressed, uncompressedLength);
            if (Zstd.isError(result.length)) {
                throw new MsfCompressionException(
                    "zstd decompression failed: " + Zstd.getErrorName(result.length)
                );
            }
            return result;
        } catch (MsfCompressionException e) {
            throw e;
        } catch (Exception e) {
            throw new MsfCompressionException("zstd decompression failed: " + e.getMessage(), e);
        }
    }

    private static byte[] decompressLz4(byte[] compressed, int uncompressedLength)
            throws MsfCompressionException {
        try {
            LZ4Factory factory = LZ4Factory.fastestInstance();
            LZ4SafeDecompressor decompressor = factory.safeDecompressor();
            byte[] result = new byte[uncompressedLength];
            int decompressed = decompressor.decompress(
                compressed, 0, compressed.length, result, 0, uncompressedLength
            );
            if (decompressed != uncompressedLength) {
                throw new MsfCompressionException(String.format(
                    "lz4 decompression produced %d bytes but uncompressed_data_length declares %d",
                    decompressed, uncompressedLength
                ));
            }
            return result;
        } catch (MsfCompressionException e) {
            throw e;
        } catch (Exception e) {
            throw new MsfCompressionException("lz4 decompression failed: " + e.getMessage(), e);
        }
    }

    private static byte[] decompressBrotli(byte[] compressed, int uncompressedLength)
            throws MsfCompressionException {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
            BrotliInputStream brotliStream = new BrotliInputStream(bais);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(uncompressedLength);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = brotliStream.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            brotliStream.close();
            byte[] result = baos.toByteArray();
            if (result.length != uncompressedLength) {
                throw new MsfCompressionException(String.format(
                    "brotli decompression produced %d bytes but uncompressed_data_length declares %d",
                    result.length, uncompressedLength
                ));
            }
            return result;
        } catch (MsfCompressionException e) {
            throw e;
        } catch (IOException e) {
            throw new MsfCompressionException("brotli decompression failed: " + e.getMessage(), e);
        }
    }
}
