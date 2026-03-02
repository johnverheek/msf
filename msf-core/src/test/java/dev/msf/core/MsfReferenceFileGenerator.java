package dev.msf.core;

import dev.msf.core.io.MsfWriter;
import dev.msf.core.model.MsfHeader;
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

        System.out.println("Reference file generation complete.");
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
