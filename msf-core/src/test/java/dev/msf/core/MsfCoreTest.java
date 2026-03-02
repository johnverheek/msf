package dev.msf.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic smoke tests for MSF core module.
 */
@DisplayName("MSF Core Tests")
class MsfCoreTest {

    @Test
    @DisplayName("should support MsfException creation")
    void testMsfExceptionCreation() {
        MsfException ex = new MsfException("Test message");
        assertNotNull(ex);
        assertEquals("Test message", ex.getMessage());
    }

    @Test
    @DisplayName("should support MsfParseException creation")
    void testMsfParseException() {
        MsfParseException ex = new MsfParseException("Parse failed");
        assertNotNull(ex);
        assertEquals("Parse failed", ex.getMessage());
    }

    @Test
    @DisplayName("should support MsfVersionException creation")
    void testMsfVersionException() {
        MsfVersionException ex = new MsfVersionException(2, 1);
        assertNotNull(ex);
        assertTrue(ex.getMessage().contains("version"));
    }

    @Test
    @DisplayName("should support MsfCompressionException creation")
    void testMsfCompressionException() {
        MsfCompressionException ex = new MsfCompressionException("Compression failed");
        assertNotNull(ex);
        assertEquals("Compression failed", ex.getMessage());
    }

    @Test
    @DisplayName("should support MsfPaletteException creation")
    void testMsfPaletteException() {
        MsfPaletteException ex = new MsfPaletteException("Palette error");
        assertNotNull(ex);
        assertEquals("Palette error", ex.getMessage());
    }
}
