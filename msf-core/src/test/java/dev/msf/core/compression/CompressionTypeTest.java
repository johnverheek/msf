package dev.msf.core.compression;

import dev.msf.core.MsfCompressionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CompressionType}.
 */
@DisplayName("CompressionType")
class CompressionTypeTest {

    @Test
    @DisplayName("All four canonical instances have distinct wire IDs")
    void distinctWireIds() {
        assertNotEquals(CompressionType.NONE.wireId(), CompressionType.ZSTD.wireId());
        assertNotEquals(CompressionType.NONE.wireId(), CompressionType.LZ4.wireId());
        assertNotEquals(CompressionType.NONE.wireId(), CompressionType.BROTLI.wireId());
        assertNotEquals(CompressionType.ZSTD.wireId(), CompressionType.LZ4.wireId());
        assertNotEquals(CompressionType.ZSTD.wireId(), CompressionType.BROTLI.wireId());
        assertNotEquals(CompressionType.LZ4.wireId(), CompressionType.BROTLI.wireId());
    }

    @Test
    @DisplayName("fromWireId round trips for all four types")
    void fromWireIdRoundTrips() throws Exception {
        assertInstanceOf(CompressionType.None.class, CompressionType.fromWireId((byte) 0x00));
        assertInstanceOf(CompressionType.Zstd.class, CompressionType.fromWireId((byte) 0x01));
        assertInstanceOf(CompressionType.Lz4.class, CompressionType.fromWireId((byte) 0x02));
        assertInstanceOf(CompressionType.Brotli.class, CompressionType.fromWireId((byte) 0x03));
    }

    @Test
    @DisplayName("fromWireId with unknown byte → MsfCompressionException")
    void unknownWireId() {
        assertThrows(MsfCompressionException.class,
            () -> CompressionType.fromWireId((byte) 0x7F)
        );
    }

    @Test
    @DisplayName("ZSTD is the recommended default — wire ID 0x01")
    void zstdIsDefault() {
        assertEquals((byte) 0x01, CompressionType.ZSTD.wireId());
    }

    @Test
    @DisplayName("NONE wire ID is 0x00")
    void noneWireId() {
        assertEquals((byte) 0x00, CompressionType.NONE.wireId());
    }

    @Test
    @DisplayName("All types have non-empty display names")
    void displayNamesNonEmpty() {
        for (CompressionType type : new CompressionType[]{
            CompressionType.NONE, CompressionType.ZSTD,
            CompressionType.LZ4, CompressionType.BROTLI
        }) {
            assertNotNull(type.displayName());
            assertFalse(type.displayName().isBlank(), "Display name must not be blank");
        }
    }
}
