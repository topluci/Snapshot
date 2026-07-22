package com.luci.snapshot.client.camera;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.imageio.ImageIO;

final class SnapshotCaptureStorage {
    private static final long FREE_SPACE_RESERVE = 64L * 1024L * 1024L;
    private static final long MINIMUM_WRITE_BUDGET = 8L * 1024L * 1024L;
    private static final String TEMP_PREFIX = ".snapshot-write-";

    private SnapshotCaptureStorage() {
    }

    static void writePng(NativeImage image, Path destination) throws IOException {
        Path directory = destination.toAbsolutePath().getParent();
        if (directory == null) {
            throw new IOException("Capture destination has no parent directory: " + destination);
        }
        Files.createDirectories(directory);
        removeStaleTemporaryFiles(directory);
        long uncompressedBytes = (long) image.getWidth() * image.getHeight() * 4L;
        requireFreeSpace(directory, Math.max(MINIMUM_WRITE_BUDGET, uncompressedBytes * 2L));

        Path temporary = temporaryPath(destination);
        try {
            image.writeToFile(temporary);
            validatePng(temporary, image.getWidth(), image.getHeight());
            moveIntoPlace(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void copyPng(Path source, Path destination) throws IOException {
        Path directory = destination.toAbsolutePath().getParent();
        if (directory == null) {
            throw new IOException("Screenshot destination has no parent directory: " + destination);
        }
        Files.createDirectories(directory);
        requireFreeSpace(directory, Math.max(MINIMUM_WRITE_BUDGET, Files.size(source) * 2L));
        Path temporary = temporaryPath(destination);
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            validatePng(temporary, -1, -1);
            moveIntoPlace(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void validatePng(Path image, int expectedWidth, int expectedHeight) throws IOException {
        if (!Files.isRegularFile(image) || Files.size(image) < 33L) {
            throw new IOException("PNG is missing or incomplete: " + image);
        }
        BufferedImage decoded = ImageIO.read(image.toFile());
        if (decoded == null) {
            throw new IOException("PNG could not be decoded: " + image);
        }
        if (expectedWidth > 0 && expectedHeight > 0
            && (decoded.getWidth() != expectedWidth || decoded.getHeight() != expectedHeight)) {
            throw new IOException("PNG dimensions changed while saving: " + image);
        }
    }

    private static void requireFreeSpace(Path directory, long writeBudget) throws IOException {
        FileStore store = Files.getFileStore(directory);
        long usable = store.getUsableSpace();
        long required = writeBudget + FREE_SPACE_RESERVE;
        if (usable > 0L && usable < required) {
            throw new IOException("Not enough disk space for Snapshot capture (requires approximately "
                + Math.max(1L, required / (1024L * 1024L)) + " MiB free)");
        }
    }

    private static Path temporaryPath(Path destination) {
        return destination.resolveSibling(TEMP_PREFIX + UUID.randomUUID() + ".tmp.png");
    }

    private static void moveIntoPlace(Path temporary, Path destination) throws IOException {
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void removeStaleTemporaryFiles(Path directory) {
        FileTime cutoff = FileTime.from(Instant.now().minus(1, ChronoUnit.DAYS));
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(candidate -> candidate.getFileName().toString().startsWith(TEMP_PREFIX)).toList()) {
                try {
                    if (Files.getLastModifiedTime(path).compareTo(cutoff) < 0) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }
}
