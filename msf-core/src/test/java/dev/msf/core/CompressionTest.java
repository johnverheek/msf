package dev.msf.core;

import dev.msf.core.compression.CompressionType;
import dev.msf.core.compression.RegionCompressor;
import dev.msf.core.compression.RegionDecompressor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RegionCompressor}, {@link RegionDecompressor}, and {@link CompressionType}.
 */
class CompressionTest {

    // =========================================================================
    // Round trips for all four compression types
    // =========================================================================

    @ParameterizedTest
    @EnumSource(CompressionType.class)
    void roundTrip_emptyData(CompressionType type) throws Exception {
        byte[] data = new byte[0];
        byte[] compressed = RegionCompressor.compress(data, type);
        byte[] decompressed = RegionDecompressor.decompress(compressed, type, 0);
        assertArrayEquals(data, decompressed);
    }

    @ParameterizedTest
    @EnumSource(CompressionType.class)
    void roundTrip_smallPayload(CompressionType type) throws Exception {
        byte[] data = "Hello, MSF!".getBytes(StandardCharsets.UTF_8);
        byte[] compressed = RegionCompressor.compress(data, type);
        byte[] decompressed = RegionDecompressor.decompress(compressed, type, data.length);
        assertArrayEquals(data, decompressed);
    }

    @ParameterizedTest
    @EnumSource(CompressionType.class)
    void roundTrip_repeatingData(CompressionType type) throws Exception {
        // Highly compressible: 4096 bytes all the same value
        byte[] data = new byte[4096];
        Arrays.fill(data, (byte) 0x42);
        byte[] compressed = RegionCompressor.compress(data, type);
        byte[] decompressed = RegionDecompressor.decompress(compressed, type, data.length);
        assertArrayEquals(data, decompressed);
    }

    @ParameterizedTest
    @EnumSource(CompressionType.class)
    void roundTrip_randomishData(CompressionType type) throws Exception {
        // Pseudo-random: not easily compressible but tests round trip
        byte[] data = new byte[1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((i * 31 + 17) & 0xFF);
        }
        byte[] compressed = RegionCompressor.compress(data, type);
        byte[] decompressed = RegionDecompressor.decompress(compressed, type, data.length);
        assertArrayEquals(data, decompressed);
    }

    @ParameterizedTest
    @EnumSource(CompressionType.class)
    void roundTrip_ninetyBytes(CompressionType type) throws Exception {
        byte[] data = new byte[90];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        byte[] compressed = RegionCompressor.compress(data, type);
        byte[] decompressed = RegionDecompressor.decompress(compressed, type, data.length);
        assertArrayEquals(data, decompressed);
    }

    // =========================================================================
    // NONE compression — passthrough
    // =========================================================================

    @Test
    void none_compressIsIdentity() throws Exception {
        byte[] data = {1, 2, 3, 4, 5};
        byte[] compressed = RegionCompressor.compress(data, CompressionType.NONE);
        assertArrayEquals(data, compressed);
    }

    @Test
    void none_decompress_wrongLength_throwsMsfCompressionException() {
        byte[] data = {1, 2, 3};
        assertThrows(MsfCompressionException.class, () ->
            RegionDecompressor.decompress(data, CompressionType.NONE, 5) // wrong expected length
        );
    }

    // =========================================================================
    // CompressionType.fromByte
    // =========================================================================

    @Test
    void fromByte_0x00_returnsNone() throws Exception {
        assertEquals(CompressionType.NONE, CompressionType.fromByte(0x00));
    }

    @Test
    void fromByte_0x01_returnsZstd() throws Exception {
        assertEquals(CompressionType.ZSTD, CompressionType.fromByte(0x01));
    }

    @Test
    void fromByte_0x02_returnsLz4() throws Exception {
        assertEquals(CompressionType.LZ4, CompressionType.fromByte(0x02));
    }

    @Test
    void fromByte_0x03_returnsBrotli() throws Exception {
        assertEquals(CompressionType.BROTLI, CompressionType.fromByte(0x03));
    }

    @Test
    void fromByte_unrecognized_throwsMsfParseException() {
        assertThrows(MsfParseException.class, () -> CompressionType.fromByte(0x04));
        assertThrows(MsfParseException.class, () -> CompressionType.fromByte(0xFF));
    }
}
