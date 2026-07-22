package com.luci.snapshot.client.photo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotTrashStoreTest {
    @TempDir
    Path gameDirectory;

    @Test
    void movesCompleteCaptureToTrashAndRestoresIt() throws IOException {
        Path image = createCapture("snapshot_restore");

        assertTrue(SnapshotTrashStore.moveToTrash(image));
        Path trashed = SnapshotTrashStore.trashDirectory(gameDirectory).resolve(image.getFileName());
        assertFalse(Files.exists(image));
        assertTrue(Files.isRegularFile(trashed));
        assertEquals(1, SnapshotTrashStore.images(gameDirectory).size());

        assertTrue(SnapshotTrashStore.restore(trashed));
        assertTrue(Files.isRegularFile(image));
        assertTrue(Files.isRegularFile(image.resolveSibling("snapshot_restore.source.png")));
        assertTrue(Files.isRegularFile(gameDirectory.resolve("screenshots/snapshot_restore.png")));
        assertTrue(SnapshotTrashStore.images(gameDirectory).isEmpty());
    }

    @Test
    void emptyTrashPermanentlyRemovesEveryCapture() throws IOException {
        SnapshotTrashStore.moveToTrash(createCapture("snapshot_first"));
        SnapshotTrashStore.moveToTrash(createCapture("snapshot_second"));
        assertEquals(2, SnapshotTrashStore.images(gameDirectory).size());

        SnapshotTrashStore.empty(gameDirectory);

        assertTrue(SnapshotTrashStore.images(gameDirectory).isEmpty());
        assertFalse(Files.exists(SnapshotTrashStore.trashDirectory(gameDirectory)));
    }

    private Path createCapture(String base) throws IOException {
        Path screenshots = Files.createDirectories(gameDirectory.resolve("screenshots"));
        Path cameraRoll = Files.createDirectories(screenshots.resolve("snapshot"));
        Path image = Files.write(cameraRoll.resolve(base + ".png"), new byte[] {1, 2, 3});
        Files.write(cameraRoll.resolve(base + ".source.png"), new byte[] {4});
        Files.writeString(cameraRoll.resolve(base + ".snapshot.json"), "{}");
        Files.writeString(cameraRoll.resolve(base + ".image2map.txt"), "helper");
        Files.write(screenshots.resolve(base + ".png"), new byte[] {1, 2, 3});
        return image;
    }
}
