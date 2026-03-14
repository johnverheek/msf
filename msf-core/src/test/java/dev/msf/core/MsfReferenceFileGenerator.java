package dev.msf.core;

import dev.msf.core.compression.CompressionType;
import dev.msf.core.io.MsfWriter;
import dev.msf.core.model.MsfBlockEntity;
import dev.msf.core.model.MsfEntity;
import dev.msf.core.model.MsfFile;
import dev.msf.core.model.MsfHeader;
import dev.msf.core.model.MsfLayer;
import dev.msf.core.model.MsfLayerIndex;
import dev.msf.core.model.MsfMetadata;
import dev.msf.core.model.MsfPalette;
import dev.msf.core.model.MsfRegion;
import dev.msf.core.checksum.XxHash3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generates binary test reference files for the test resources directory.
 *
 * <p>These files serve as a regression suite and as reference files for third-party
 * implementers. They are committed to version control in {@code src/test/resources/}.
 *
 * <p>To regenerate the reference files, run this test class explicitly. The generated
 * files are written relative to the project source tree.
 */
class MsfReferenceFileGenerator {

    /** Set to true to actually write files when explicitly running this generator. */
    private static final boolean WRITE_FILES = true;

    private static final MsfHeader MINIMAL_VALID_HEADER = new MsfHeader(
        1, 0, 0, 3953L, 48L, 49L, 50L, 0L, 0L, 0L, 0L
    );

    @Test
    void generateReferenceFiles() throws Exception {
        if (!WRITE_FILES) return;

        Path resourceDir = findTestResourcesDir();
        if (resourceDir == null) {
            System.out.println("Could not locate test resources directory — skipping reference file generation");
            return;
        }

        // 1. Known-good V1.0 file
        byte[] good = MsfWriter.buildFileBytes(MINIMAL_VALID_HEADER, null);
        Files.write(resourceDir.resolve("known_good_v1_0.msf"), good);
        System.out.println("Written known_good_v1_0.msf (" + good.length + " bytes)");

        // 2. Corrupted header checksum
        byte[] corruptHeaderCs = good.clone();
        corruptHeaderCs[40] ^= 0xFF;
        Files.write(resourceDir.resolve("corrupt_header_checksum.msf"), corruptHeaderCs);
        System.out.println("Written corrupt_header_checksum.msf");

        // 3. Corrupted file checksum (flip last byte)
        byte[] corruptFileCs = good.clone();
        corruptFileCs[corruptFileCs.length - 1] ^= 0xFF;
        Files.write(resourceDir.resolve("corrupt_file_checksum.msf"), corruptFileCs);
        System.out.println("Written corrupt_file_checksum.msf");

        // 4. Unsupported major version (major = 2, valid checksum for that content)
        byte[] badMajor = buildRawFile(2, 0);
        Files.write(resourceDir.resolve("unsupported_major_version.msf"), badMajor);
        System.out.println("Written unsupported_major_version.msf");

        // 5. Future minor version (minor = 7, should be accepted by V1 reader)
        byte[] futureMinor = buildRawFile(1, 7);
        Files.write(resourceDir.resolve("future_minor_version.msf"), futureMinor);
        System.out.println("Written future_minor_version.msf");

        // 6. File size mismatch (declare 56 bytes but append 10 extra bytes)
        byte[] extended = new byte[good.length + 10];
        System.arraycopy(good, 0, extended, 0, good.length);
        Files.write(resourceDir.resolve("file_size_mismatch.msf"), extended);
        System.out.println("Written file_size_mismatch.msf");

        // --- Canonical format vectors (Epic 2) ---

        MsfRegion airRegion = MsfRegion.builder().origin(0, 0, 0).size(1, 1, 1).build();

        // 7. minimal.msf — no compression (NONE)
        MsfFile minimalFile = buildCanonicalFile("minimal", airRegion);
        Files.write(resourceDir.resolve("minimal.msf"),
            MsfWriter.writeFile(minimalFile, CompressionType.NONE, null));
        System.out.println("Written minimal.msf");

        // 8. zstd.msf — ZSTD compression
        MsfFile zstdFile = buildCanonicalFile("zstd", airRegion);
        Files.write(resourceDir.resolve("zstd.msf"),
            MsfWriter.writeFile(zstdFile, CompressionType.ZSTD, null));
        System.out.println("Written zstd.msf");

        // 9. lz4.msf — LZ4 compression
        MsfFile lz4File = buildCanonicalFile("lz4", airRegion);
        Files.write(resourceDir.resolve("lz4.msf"),
            MsfWriter.writeFile(lz4File, CompressionType.LZ4, null));
        System.out.println("Written lz4.msf");

        // 10. brotli.msf — Brotli compression
        MsfFile brotliFile = buildCanonicalFile("brotli", airRegion);
        Files.write(resourceDir.resolve("brotli.msf"),
            MsfWriter.writeFile(brotliFile, CompressionType.BROTLI, null));
        System.out.println("Written brotli.msf");

        // 11. entities.msf — ZSTD + one entity (armor stand) + one block entity (chest)
        MsfEntity entity = MsfEntity.builder()
            .position(0.5, 0.0, 0.5)
            .rotation(0.0f, 0.0f)
            .entityType("minecraft:armor_stand")
            .nbtPayload(new byte[0])
            .build();
        MsfBlockEntity blockEntity = MsfBlockEntity.builder()
            .position(0, 0, 0)
            .blockEntityType("minecraft:chest")
            .nbtPayload(new byte[0])
            .build();
        MsfFile entitiesFile = buildCanonicalFile("entities", airRegion);
        entitiesFile = MsfFile.builder()
            .mcDataVersion(entitiesFile.header().mcDataVersion())
            .metadata(entitiesFile.metadata())
            .palette(entitiesFile.palette())
            .layerIndex(entitiesFile.layerIndex())
            .entities(List.of(entity))
            .blockEntities(List.of(blockEntity))
            .build();
        Files.write(resourceDir.resolve("entities.msf"),
            MsfWriter.writeFile(entitiesFile, CompressionType.ZSTD, null));
        System.out.println("Written entities.msf");

        System.out.println("Reference file generation complete.");
    }

    /**
     * Builds a minimal, well-formed {@link MsfFile} with a single 1×1×1 air region
     * suitable for use as a canonical format vector.
     *
     * @param name      schematic name embedded in metadata
     * @param airRegion pre-built 1×1×1 air region
     */
    private static MsfFile buildCanonicalFile(String name, MsfRegion airRegion) throws Exception {
        return MsfFile.builder()
            .mcDataVersion(3953L)           // 1.21.1 data version
            .metadata(MsfMetadata.builder().name(name).build())
            .palette(MsfPalette.of(List.of(MsfPalette.AIR)))
            .layerIndex(MsfLayerIndex.of(List.of(
                MsfLayer.builder().layerId(1).name("l").addRegion(airRegion).build()
            )))
            .build();
    }

    /**
     * Builds a raw 56-byte MSF file with the given major and minor versions,
     * recomputing checksums for the given content.
     */
    private static byte[] buildRawFile(int majorVersion, int minorVersion) {
        ByteBuffer buf = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(0, (byte) 0x4D);
        buf.put(1, (byte) 0x53);
        buf.put(2, (byte) 0x46);
        buf.put(3, (byte) 0x21);
        buf.putShort(4, (short) (majorVersion & 0xFFFF));
        buf.putShort(6, (short) (minorVersion & 0xFFFF));
        buf.putInt(8, 0);       // feature flags
        buf.putInt(12, 3953);   // MC data version
        buf.putInt(16, 48);     // metadataOffset
        buf.putInt(20, 49);     // globalPaletteOffset
        buf.putInt(24, 50);     // layerIndexOffset
        buf.putInt(28, 0);
        buf.putInt(32, 0);
        buf.putInt(36, 56);     // file_size

        byte[] bytes = buf.array();
        long headerChecksum = XxHash3.hash(bytes, 0, 40);
        buf.putLong(40, headerChecksum);

        bytes = buf.array();
        long fileChecksum = XxHash3.hash(bytes, 0, 48);
        ByteBuffer csBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        csBuf.putLong(0, fileChecksum);
        System.arraycopy(csBuf.array(), 0, bytes, 48, 8);

        return bytes;
    }

    /**
     * Locates the test resources directory relative to the build output or source tree.
     */
    private static Path findTestResourcesDir() {
        try {
            // Try to find via the class loader
            URL resource = MsfReferenceFileGenerator.class.getClassLoader().getResource(".");
            if (resource != null) {
                // build/classes/java/test → project root → msf-core/src/test/resources
                Path classesDir = Paths.get(resource.toURI());
                Path projectRoot = classesDir;
                while (projectRoot != null && !Files.exists(projectRoot.resolve("src"))) {
                    projectRoot = projectRoot.getParent();
                }
                if (projectRoot != null) {
                    Path resourceDir = projectRoot.resolve("src/test/resources");
                    if (!Files.exists(resourceDir)) {
                        Files.createDirectories(resourceDir);
                    }
                    return resourceDir;
                }
            }
        } catch (URISyntaxException | IOException e) {
            // fallthrough
        }
        return null;
    }
}
