package dev.msf.core.io;

import dev.msf.core.MsfChecksumException;
import dev.msf.core.MsfException;
import dev.msf.core.MsfParseException;
import dev.msf.core.MsfVersionException;
import dev.msf.core.model.MsfHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MsfReader} and {@link MsfWriter} header serialisation.
 *
 * <p>Covers the four required test categories:
 * <ul>
 *   <li>Happy path round trips</li>
 *   <li>Boundary conditions</li>
 *   <li>Failure modes</li>
 *   <li>Forward compatibility</li>
 * </ul>
 */
@DisplayName("MSF Header Round Trip")
class MsfHeaderRoundTripTest {

    // -------------------------------------------------------------------------
    // Test fixture helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal but valid header. All required offsets are non-zero.
     * No optional blocks are referenced.
     */
    private static MsfHeader minimalHeader() {
        return MsfHeader.builder()
            .majorVersion(1)
            .minorVersion(0)
            .featureFlags(0L)
            .mcDataVersion(3953L)           // MC 1.21 data version
            .metadataBlockOffset(48L)       // immediately after header
            .globalPaletteOffset(256L)
            .layerIndexOffset(512L)
            .entityBlockOffset(0L)          // absent — flag not set
            .blockEntityBlockOffset(0L)     // absent — flag not set
            .fileSize(1024L)
            .build();
    }

    /**
     * Builds a header with all feature flags set and all block offsets populated.
     */
    private static MsfHeader fullHeader() {
        long flags = MsfHeader.FLAG_HAS_ENTITIES
            | MsfHeader.FLAG_HAS_BLOCK_ENTITIES
            | MsfHeader.FLAG_HAS_BIOME_DATA
            | MsfHeader.FLAG_HAS_LIGHTING_HINTS
            | MsfHeader.FLAG_MULTI_REGION
            | MsfHeader.FLAG_HAS_SIGNAL_PORTS
            | MsfHeader.FLAG_HAS_CONSTRUCTION_LAYERS
            | MsfHeader.FLAG_HAS_VARIANT_SYSTEM
            | MsfHeader.FLAG_HAS_PALETTE_SUBSTITUTION;

        return MsfHeader.builder()
            .majorVersion(1)
            .minorVersion(5)
            .featureFlags(flags)
            .mcDataVersion(3953L)
            .metadataBlockOffset(48L)
            .globalPaletteOffset(256L)
            .layerIndexOffset(512L)
            .entityBlockOffset(1024L)
            .blockEntityBlockOffset(2048L)
            .fileSize(8192L)
            .build();
    }

    /**
     * Serialises a header and produces a synthetic "full file" by appending a stub body
     * and computing the file checksum.
     */
    private static byte[] buildSyntheticFile(MsfHeader header) throws Exception {
        byte[] headerBytes = MsfWriter.writeHeader(header);

        // Stub body: just zeros to pad out to the declared fileSize.
        // For test purposes we keep the file tiny — header + 8-byte file checksum.
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(headerBytes);

        // Compute and append the file checksum.
        byte[] bodyBytes = body.toByteArray();
        long fileChecksum = dev.msf.core.checksum.XxHash3.hash(bodyBytes);
        byte[] checksumBuf = new byte[8];
        MsfWriter.writeLittleEndianU64(checksumBuf, 0, fileChecksum);
        body.write(checksumBuf);

        return body.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Happy path round trips
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Happy path round trips")
    class HappyPath {

        @Test
        @DisplayName("Minimal header survives write-then-read")
        void minimalHeaderRoundTrip() throws Exception {
            MsfHeader original = minimalHeader();
            byte[] fileBytes = buildSyntheticFile(original);

            // Update the fileSize field to match the actual synthetic file we produced.
            MsfHeader headerWithCorrectSize = original.toBuilder()
                .fileSize(fileBytes.length)
                .build();
            fileBytes = buildSyntheticFile(headerWithCorrectSize);

            MsfHeader parsed = MsfReader.readHeader(fileBytes);

            assertEquals(1, parsed.majorVersion(), "majorVersion");
            assertEquals(0, parsed.minorVersion(), "minorVersion");
            assertEquals(0L, parsed.featureFlags(), "featureFlags");
            assertEquals(3953L, parsed.mcDataVersion(), "mcDataVersion");
            assertEquals(48L, parsed.metadataBlockOffset(), "metadataBlockOffset");
            assertEquals(256L, parsed.globalPaletteOffset(), "globalPaletteOffset");
            assertEquals(512L, parsed.layerIndexOffset(), "layerIndexOffset");
            assertEquals(0L, parsed.entityBlockOffset(), "entityBlockOffset");
            assertEquals(0L, parsed.blockEntityBlockOffset(), "blockEntityBlockOffset");
        }

        @Test
        @DisplayName("Full-featured header survives write-then-read")
        void fullHeaderRoundTrip() throws Exception {
            MsfHeader original = fullHeader();
            byte[] headerBytes = MsfWriter.writeHeader(original);

            // Build a minimal synthetic file to test reading from byte array.
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(headerBytes);
            byte[] fileChecksum = new byte[8];
            MsfWriter.writeLittleEndianU64(fileChecksum, 0,
                dev.msf.core.checksum.XxHash3.hash(bos.toByteArray()));
            bos.write(fileChecksum);

            // Update fileSize to actual.
            byte[] fileBytes = bos.toByteArray();
            MsfHeader withSize = original.toBuilder().fileSize(fileBytes.length).build();
            byte[] finalHeader = MsfWriter.writeHeader(withSize);
            System.arraycopy(finalHeader, 0, fileBytes, 0, MsfHeader.HEADER_SIZE);
            // Recompute file checksum with updated header.
            MsfWriter.writeLittleEndianU64(fileBytes, fileBytes.length - 8,
                dev.msf.core.checksum.XxHash3.hash(fileBytes, 0, fileBytes.length - 8));

            MsfHeader parsed = MsfReader.readHeader(fileBytes);

            assertEquals(1, parsed.majorVersion());
            assertEquals(5, parsed.minorVersion());
            assertTrue(parsed.hasFlag(MsfHeader.FLAG_HAS_ENTITIES));
            assertTrue(parsed.hasFlag(MsfHeader.FLAG_HAS_BLOCK_ENTITIES));
            assertTrue(parsed.hasFlag(MsfHeader.FLAG_HAS_BIOME_DATA));
            assertTrue(parsed.hasFlag(MsfHeader.FLAG_MULTI_REGION));
            assertEquals(1024L, parsed.entityBlockOffset());
            assertEquals(2048L, parsed.blockEntityBlockOffset());
        }

        @Test
        @DisplayName("InputStream-based reader produces identical header")
        void streamReaderMatchesByteArrayReader() throws Exception {
            MsfHeader original = minimalHeader();
            byte[] headerBytes = MsfWriter.writeHeader(original);

            MsfHeader fromStream = MsfReader.readHeader(new ByteArrayInputStream(headerBytes));

            assertEquals(original.majorVersion(), fromStream.majorVersion());
            assertEquals(original.minorVersion(), fromStream.minorVersion());
            assertEquals(original.featureFlags(), fromStream.featureFlags());
            assertEquals(original.mcDataVersion(), fromStream.mcDataVersion());
            assertEquals(original.metadataBlockOffset(), fromStream.metadataBlockOffset());
            assertEquals(original.globalPaletteOffset(), fromStream.globalPaletteOffset());
            assertEquals(original.layerIndexOffset(), fromStream.layerIndexOffset());
        }

        @Test
        @DisplayName("Header checksum is computed automatically on write")
        void checksumComputedOnWrite() throws Exception {
            MsfHeader original = minimalHeader();
            // Header built with headerChecksum = 0 (default) — writer must recompute it.
            byte[] headerBytes = MsfWriter.writeHeader(original);

            long storedChecksum = MsfReader.readLittleEndianU64(headerBytes, 40);
            long computedChecksum = dev.msf.core.checksum.XxHash3.headerChecksum(headerBytes);

            assertEquals(computedChecksum, storedChecksum,
                "Writer must embed a valid header checksum");
        }

        @Test
        @DisplayName("Feature flag round trips correctly for all defined bits")
        void featureFlagRoundTrip() throws Exception {
            // Test each individual flag bit in isolation.
            long[] flags = {
                MsfHeader.FLAG_HAS_ENTITIES,
                MsfHeader.FLAG_HAS_BLOCK_ENTITIES,
                MsfHeader.FLAG_HAS_BIOME_DATA,
                MsfHeader.FLAG_HAS_LIGHTING_HINTS,
                MsfHeader.FLAG_MULTI_REGION,
                MsfHeader.FLAG_DELTA_FORMAT,
                MsfHeader.FLAG_HAS_SIGNAL_PORTS,
                MsfHeader.FLAG_HAS_CONSTRUCTION_LAYERS,
                MsfHeader.FLAG_HAS_VARIANT_SYSTEM,
                MsfHeader.FLAG_HAS_PALETTE_SUBSTITUTION,
            };

            for (long flag : flags) {
                // Build entity/block-entity offsets only when their flags are set.
                long entityOffset = (flag & MsfHeader.FLAG_HAS_ENTITIES) != 0 ? 1024L : 0L;
                long blockEntityOffset = (flag & MsfHeader.FLAG_HAS_BLOCK_ENTITIES) != 0 ? 2048L : 0L;

                MsfHeader header = MsfHeader.builder()
                    .featureFlags(flag)
                    .metadataBlockOffset(48L)
                    .globalPaletteOffset(256L)
                    .layerIndexOffset(512L)
                    .entityBlockOffset(entityOffset)
                    .blockEntityBlockOffset(blockEntityOffset)
                    .fileSize(64L)
                    .build();

                byte[] bytes = MsfWriter.writeHeader(header);
                MsfHeader parsed = MsfReader.readHeader(new ByteArrayInputStream(bytes));
                assertTrue(parsed.hasFlag(flag),
                    "Flag 0x" + Long.toHexString(flag) + " must survive round trip");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Boundary conditions
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Boundary conditions")
    class BoundaryConditions {

        @Test
        @DisplayName("Minimum u32 values (0) round trip correctly")
        void minimumU32Values() throws Exception {
            // featureFlags = 0, mcDataVersion = 0
            MsfHeader header = MsfHeader.builder()
                .featureFlags(0L)
                .mcDataVersion(0L)
                .metadataBlockOffset(48L)
                .globalPaletteOffset(256L)
                .layerIndexOffset(512L)
                .fileSize(64L)
                .build();

            byte[] bytes = MsfWriter.writeHeader(header);
            MsfHeader parsed = MsfReader.readHeader(new ByteArrayInputStream(bytes));

            assertEquals(0L, parsed.featureFlags());
            assertEquals(0L, parsed.mcDataVersion());
        }

        @Test
        @DisplayName("Maximum u32 value (0xFFFFFFFF) round trips correctly")
        void maximumU32Value() throws Exception {
            long maxU32 = 0xFFFFFFFFL;

            MsfHeader header = MsfHeader.builder()
                .mcDataVersion(maxU32)
                .metadataBlockOffset(48L)
                .globalPaletteOffset(256L)
                .layerIndexOffset(512L)
                .fileSize(maxU32)
                .build();

            byte[] bytes = MsfWriter.writeHeader(header);
            MsfHeader parsed = MsfReader.readHeader(new ByteArrayInputStream(bytes));

            assertEquals(maxU32, parsed.mcDataVersion(), "max mcDataVersion must round trip");
            assertEquals(maxU32, parsed.fileSize(), "max fileSize must round trip");
        }

        @Test
        @DisplayName("Maximum u16 minor version (65535) round trips correctly")
        void maximumMinorVersion() throws Exception {
            MsfHeader header = MsfHeader.builder()
                .minorVersion(65535)
                .metadataBlockOffset(48L)
                .globalPaletteOffset(256L)
                .layerIndexOffset(512L)
                .fileSize(64L)
                .build();

            byte[] bytes = MsfWriter.writeHeader(header);
            MsfHeader parsed = MsfReader.readHeader(new ByteArrayInputStream(bytes));

            assertEquals(65535, parsed.minorVersion());
        }

        @Test
        @DisplayName("Zero minor version is valid")
        void zeroMinorVersion() throws Exception {
            MsfHeader header = MsfHeader.builder()
                .minorVersion(0)
                .metadataBlockOffset(48L)
                .globalPaletteOffset(256L)
                .layerIndexOffset(512L)
                .fileSize(64L)
                .build();

            byte[] bytes = MsfWriter.writeHeader(header);
            MsfHeader parsed = MsfReader.readHeader(new ByteArrayInputStream(bytes));

            assertEquals(0, parsed.minorVersion());
        }

        @Test
        @DisplayName("Header is exactly 48 bytes")
        void headerIsExactly48Bytes() throws Exception {
            byte[] bytes = MsfWriter.writeHeader(minimalHeader());
            assertEquals(48, bytes.length, "Header must be exactly 48 bytes — frozen by spec");
        }

        @Test
        @DisplayName("Magic bytes are correct at offsets 0-3")
        void magicBytesAtCorrectOffsets() throws Exception {
            byte[] bytes = MsfWriter.writeHeader(minimalHeader());
            assertArrayEquals(MsfHeader.MAGIC, new byte[]{bytes[0], bytes[1], bytes[2], bytes[3]});
        }
    }

    // -------------------------------------------------------------------------
    // Failure modes
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Failure modes")
    class FailureModes {

        @Test
        @DisplayName("Wrong magic bytes → MsfParseException immediately")
        void wrongMagicBytes() throws Exception {
            byte[] bytes = MsfWriter.writeHeader(minimalHeader());
            bytes[0] = 0x00; // corrupt first magic byte

            assertThrows(MsfParseException.class,
                () -> MsfReader.readHeader(new ByteArrayInputStream(bytes)),
                "Wrong magic must throw MsfParseException"
            );
        }

        @Test
        @DisplayName("Corrupted header checksum → MsfChecksumException immediately")
        void corruptedHeaderChecksum() throws Exception {
            byte[] bytes = MsfWriter.writeHeader(minimalHeader());
            // Corrupt the checksum field at bytes 40-47.
            bytes[40] ^= 0xFF;

            MsfChecksumException ex = assertThrows(MsfChecksumException.class,
                () -> MsfReader.readHeader(new ByteArrayInputStream(bytes))
            );
            assertTrue(ex.isHeaderChecksum(), "Must identify this as a header checksum failure");
        }

        @Test
        @DisplayName("Unsupported major version → MsfVersionException immediately")
        void unsupportedMajorVersion() throws Exception {
            // Write a valid header but with major version 2.
            byte[] bytes = MsfWriter.writeHeader(minimalHeader());
            // Manually write major version 2 at offset 4 (u16 little-endian).
            bytes[4] = 0x02;
            bytes[5] = 0x00;
            // Recompute checksum so we get past checksum check and hit version check.
            long newChecksum = dev.msf.core.checksum.XxHash3.headerChecksum(bytes);
            MsfWriter.writeLittleEndianU64(bytes, 40, newChecksum);

            MsfVersionException ex = assertThrows(MsfVersionException.class,
                () -> MsfReader.readHeader(new ByteArrayInputStream(bytes))
            );
            assertEquals(2, ex.getFileMajorVersion());
            assertEquals(1, ex.getSupportedMajorVersion());
        }

        @Test
        @DisplayName("File too short → MsfParseException")
        void fileTooShort() {
            byte[] tooShort = new byte[16];

            assertThrows(MsfParseException.class,
                () -> MsfReader.readHeader(tooShort)
            );
        }

        @Test
        @DisplayName("Stream shorter than header → MsfParseException")
        void streamTooShort() {
            byte[] tooShort = new byte[10];

            assertThrows(MsfParseException.class,
                () -> MsfReader.readHeader(new ByteArrayInputStream(tooShort))
            );
        }

        @Test
        @DisplayName("metadataBlockOffset = 0 → MsfParseException on write")
        void nullMetadataOffset() {
            MsfHeader bad = MsfHeader.builder()
                .metadataBlockOffset(0L)   // invalid — must not be 0
                .globalPaletteOffset(256L)
                .layerIndexOffset(512L)
                .fileSize(64L)
                .build();

            assertThrows(MsfParseException.class,
                () -> MsfWriter.writeHeader(bad),
                "Null metadataBlockOffset must be rejected"
            );
        }

        @Test
        @DisplayName("globalPaletteOffset = 0 → MsfParseException on write")
        void nullPaletteOffset() {
            MsfHeader bad = MsfHeader.builder()
                .metadataBlockOffset(48L)
                .globalPaletteOffset(0L)   // invalid
                .layerIndexOffset(512L)
                .fileSize(64L)
                .build();

            assertThrows(MsfParseException.class,
                () -> MsfWriter.writeHeader(bad)
            );
        }

        @Test
        @DisplayName("layerIndexOffset = 0 → MsfParseException on write")
        void nullLayerIndexOffset() {
            MsfHeader bad = MsfHeader.builder()
                .metadataBlockOffset(48L)
                .globalPaletteOffset(256L)
                .layerIndexOffset(0L)      // invalid
                .fileSize(64L)
                .build();

            assertThrows(MsfParseException.class,
                () -> MsfWriter.writeHeader(bad)
            );
        }

        @Test
        @DisplayName("entityBlockOffset != 0 without FLAG_HAS_ENTITIES → MsfParseException")
        void entityOffsetWithoutFlag() {
            MsfHeader bad = MsfHeader.builder()
                .featureFlags(0L)          // FLAG_HAS_ENTITIES not set
                .metadataBlockOffset(48L)
                .globalPaletteOffset(256L)
                .layerIndexOffset(512L)
                .entityBlockOffset(1024L)  // must be 0 when flag not set
                .fileSize(64L)
                .build();

            assertThrows(MsfParseException.class,
                () -> MsfWriter.writeHeader(bad)
            );
        }

        @Test
        @DisplayName("blockEntityBlockOffset != 0 without FLAG_HAS_BLOCK_ENTITIES → MsfParseException")
        void blockEntityOffsetWithoutFlag() {
            MsfHeader bad = MsfHeader.builder()
                .featureFlags(0L)
                .metadataBlockOffset(48L)
                .globalPaletteOffset(256L)
                .layerIndexOffset(512L)
                .blockEntityBlockOffset(2048L) // must be 0 when flag not set
                .fileSize(64L)
                .build();

            assertThrows(MsfParseException.class,
                () -> MsfWriter.writeHeader(bad)
            );
        }

        @Test
        @DisplayName("Major version 0 → rejected as unsupported")
        void majorVersionZero() throws Exception {
            byte[] bytes = MsfWriter.writeHeader(minimalHeader());
            // Write major version 0.
            bytes[4] = 0x00;
            bytes[5] = 0x00;
            long newChecksum = dev.msf.core.checksum.XxHash3.headerChecksum(bytes);
            MsfWriter.writeLittleEndianU64(bytes, 40, newChecksum);

            assertThrows(MsfVersionException.class,
                () -> MsfReader.readHeader(new ByteArrayInputStream(bytes))
            );
        }
    }

    // -------------------------------------------------------------------------
    // Forward compatibility
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Forward compatibility")
    class ForwardCompatibility {

        @Test
        @DisplayName("Higher minor version is accepted without rejection")
        void higherMinorVersionAccepted() throws Exception {
            // A V1.99 file must be accepted by this V1.0 reader.
            MsfHeader v1_99 = MsfHeader.builder()
                .majorVersion(1)
                .minorVersion(99)
                .featureFlags(0L)
                .mcDataVersion(3953L)
                .metadataBlockOffset(48L)
                .globalPaletteOffset(256L)
                .layerIndexOffset(512L)
                .fileSize(64L)
                .build();

            byte[] bytes = MsfWriter.writeHeader(v1_99);

            // Must not throw — minor version alone MUST NOT reject the file.
            MsfHeader parsed = assertDoesNotThrow(
                () -> MsfReader.readHeader(new ByteArrayInputStream(bytes))
            );

            assertEquals(99, parsed.minorVersion(),
                "Reader must preserve the minor version it read, not clamp it");
        }

        @Test
        @DisplayName("Unknown (reserved) feature flag bits are preserved, not rejected")
        void unknownFeatureFlagsPreserved() throws Exception {
            // Set a reserved bit (bit 20) which no V1.0 reader should know about.
            long flagsWithReservedBit = 1L << 20;

            MsfHeader header = MsfHeader.builder()
                .featureFlags(flagsWithReservedBit)
                .metadataBlockOffset(48L)
                .globalPaletteOffset(256L)
                .layerIndexOffset(512L)
                .fileSize(64L)
                .build();

            byte[] bytes = MsfWriter.writeHeader(header);
            MsfHeader parsed = MsfReader.readHeader(new ByteArrayInputStream(bytes));

            // Reader must not reject — unknown flags are ignored, not rejected.
            assertEquals(flagsWithReservedBit, parsed.featureFlags(),
                "Unknown feature flags must be preserved through round trip");
        }

        @Test
        @DisplayName("V1.0 reader can read a V1.n file with higher minor version and unknown flags")
        void futureV1FilePartiallyReadable() throws Exception {
            long futureFlags = MsfHeader.FLAG_HAS_ENTITIES | (1L << 15) | (1L << 20);

            MsfHeader futureHeader = MsfHeader.builder()
                .majorVersion(1)
                .minorVersion(7)           // future minor version
                .featureFlags(futureFlags)
                .mcDataVersion(9999L)      // future MC data version
                .metadataBlockOffset(48L)
                .globalPaletteOffset(256L)
                .layerIndexOffset(512L)
                .entityBlockOffset(1024L)
                .fileSize(64L)
                .build();

            byte[] bytes = MsfWriter.writeHeader(futureHeader);
            MsfHeader parsed = assertDoesNotThrow(
                () -> MsfReader.readHeader(new ByteArrayInputStream(bytes)),
                "A V1.0 reader MUST NOT reject a V1.7 file"
            );

            assertEquals(1, parsed.majorVersion());
            assertEquals(7, parsed.minorVersion());
            assertEquals(9999L, parsed.mcDataVersion());
        }
    }
}
