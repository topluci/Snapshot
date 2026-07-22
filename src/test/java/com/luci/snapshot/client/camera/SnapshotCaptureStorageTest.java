package com.luci.snapshot.client.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotCaptureStorageTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesAndCopiesValidatedPngsWithoutLeavingTemporaryFiles() throws IOException {
        Path capture = temporaryDirectory.resolve("snapshot.png");
        Path copy = temporaryDirectory.resolve("root").resolve("snapshot.png");
        try (NativeImage image = new NativeImage(16, 8, false)) {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    image.setPixel(x, y, 0xFF000000 | x * 12 << 16 | y * 24 << 8 | 0x55);
                }
            }
            SnapshotCaptureStorage.writePng(image, capture);
        }

        SnapshotCaptureStorage.copyPng(capture, copy);

        assertTrue(Files.isRegularFile(capture));
        assertTrue(Files.isRegularFile(copy));
        assertEquals(16, ImageIO.read(capture.toFile()).getWidth());
        assertEquals(Files.size(capture), Files.size(copy));
        try (var files = Files.walk(temporaryDirectory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".snapshot-write-")));
        }
    }

    @Test
    void rejectsCorruptPngCopies() throws IOException {
        Path corrupt = Files.writeString(temporaryDirectory.resolve("broken.png"), "not a png");
        assertThrows(IOException.class,
            () -> SnapshotCaptureStorage.copyPng(corrupt, temporaryDirectory.resolve("copy.png")));
    }
}
