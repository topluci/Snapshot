package com.luci.snapshot.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class AtomicFiles {
    private AtomicFiles() {
    }

    public static void writeString(Path destination, String contents, Charset charset) throws IOException {
        Path directory = destination.toAbsolutePath().getParent();
        if (directory == null) {
            throw new IOException("Destination has no parent directory: " + destination);
        }
        Files.createDirectories(directory);
        Path temporary = destination.resolveSibling(
            ".snapshot-write-" + UUID.randomUUID() + ".tmp"
        );
        try {
            Files.writeString(temporary, contents, charset);
            moveIntoPlace(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void moveIntoPlace(Path temporary, Path destination) throws IOException {
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
