package com.luci.snapshot.client.photo;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhotoMetadataStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void deleteRemovesTheScreenshotAndEverySnapshotSidecar() throws IOException {
        Path screenshots = Files.createDirectories(temporaryDirectory.resolve("screenshots"));
        Path cameraRoll = Files.createDirectories(screenshots.resolve("snapshot"));
        Path image = createCapture(cameraRoll, screenshots, "snapshot_test");

        PhotoMetadataStore.delete(image);

        assertCaptureMissing(cameraRoll, screenshots, "snapshot_test");
    }

    @Test
    void deleteAllRemovesEveryCapture() throws IOException {
        Path screenshots = Files.createDirectories(temporaryDirectory.resolve("screenshots"));
        Path cameraRoll = Files.createDirectories(screenshots.resolve("snapshot"));
        Path first = createCapture(cameraRoll, screenshots, "snapshot_first");
        Path second = createCapture(cameraRoll, screenshots, "snapshot_second");

        PhotoMetadataStore.deleteAll(List.of(first, second));

        assertCaptureMissing(cameraRoll, screenshots, "snapshot_first");
        assertCaptureMissing(cameraRoll, screenshots, "snapshot_second");
    }

    private static Path createCapture(Path cameraRoll, Path screenshots, String base) throws IOException {
        Path image = Files.write(cameraRoll.resolve(base + ".png"), new byte[] {1});
        Files.write(cameraRoll.resolve(base + ".source.png"), new byte[] {2});
        Files.writeString(cameraRoll.resolve(base + ".snapshot.json"), "{}");
        Files.writeString(cameraRoll.resolve(base + ".image2map.txt"), "image2map");
        Files.write(screenshots.resolve(base + ".png"), new byte[] {1});
        return image;
    }

    private static void assertCaptureMissing(Path cameraRoll, Path screenshots, String base) {
        assertFalse(Files.exists(cameraRoll.resolve(base + ".png")));
        assertFalse(Files.exists(cameraRoll.resolve(base + ".source.png")));
        assertFalse(Files.exists(cameraRoll.resolve(base + ".snapshot.json")));
        assertFalse(Files.exists(cameraRoll.resolve(base + ".image2map.txt")));
        assertFalse(Files.exists(screenshots.resolve(base + ".png")));
    }
}
