package com.luci.snapshot.client.photo;

import com.luci.snapshot.SnapshotInit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

final class SnapshotTrashStore {
    private static final String DIRECTORY = ".trash";

    private SnapshotTrashStore() {
    }

    static List<Path> images(Path gameDirectory) {
        Path trash = trashDirectory(gameDirectory);
        if (!Files.isDirectory(trash)) {
            return List.of();
        }
        try (var paths = Files.list(trash)) {
            return paths.filter(path -> {
                    String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    return name.endsWith(".png") && !name.endsWith(".source.png");
                })
                .sorted(Comparator.comparingLong(SnapshotTrashStore::modifiedTime).reversed())
                .toList();
        } catch (IOException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not scan photo trash.", exception);
            return List.of();
        }
    }

    static boolean moveToTrash(Path image) {
        Path cameraRoll = image.getParent();
        if (cameraRoll == null) {
            return false;
        }
        Path trash = cameraRoll.resolve(DIRECTORY);
        Path trashRoot = trash.resolve("root");
        Path screenshots = cameraRoll.getParent();
        List<Move> completed = new ArrayList<>();
        try {
            Files.createDirectories(trash);
            Files.createDirectories(trashRoot);
            for (Path source : associatedFiles(image)) {
                if (Files.exists(source)) {
                    move(source, trash.resolve(source.getFileName()), completed);
                }
            }
            if (screenshots != null) {
                Path rootCopy = screenshots.resolve(image.getFileName());
                if (Files.exists(rootCopy)) {
                    move(rootCopy, trashRoot.resolve(rootCopy.getFileName()), completed);
                }
            }
            SnapshotTextureLoader.deleteCached(image);
            return true;
        } catch (IOException exception) {
            rollback(completed);
            SnapshotInit.LOGGER.warn("[Snapshot] Could not move photo {} to trash.", image, exception);
            return false;
        }
    }

    static void moveAllToTrash(Collection<Path> images) {
        for (Path image : List.copyOf(images)) {
            moveToTrash(image);
        }
    }

    static boolean restore(Path trashedImage) {
        Path trash = trashedImage.getParent();
        if (trash == null || !DIRECTORY.equals(trash.getFileName().toString())) {
            return false;
        }
        Path cameraRoll = trash.getParent();
        Path screenshots = cameraRoll == null ? null : cameraRoll.getParent();
        List<Move> completed = new ArrayList<>();
        try {
            Files.createDirectories(cameraRoll);
            for (Path source : associatedFiles(trashedImage)) {
                if (Files.exists(source)) {
                    move(source, cameraRoll.resolve(source.getFileName()), completed);
                }
            }
            Path rootTrash = trash.resolve("root").resolve(trashedImage.getFileName());
            if (screenshots != null && Files.exists(rootTrash)) {
                move(rootTrash, screenshots.resolve(rootTrash.getFileName()), completed);
            }
            return true;
        } catch (IOException exception) {
            rollback(completed);
            SnapshotInit.LOGGER.warn("[Snapshot] Could not restore photo {}.", trashedImage, exception);
            return false;
        }
    }

    static void deleteForever(Path trashedImage) {
        Path trash = trashedImage.getParent();
        try {
            for (Path path : associatedFiles(trashedImage)) {
                Files.deleteIfExists(path);
            }
            if (trash != null) {
                Files.deleteIfExists(trash.resolve("root").resolve(trashedImage.getFileName()));
            }
        } catch (IOException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not permanently delete trashed photo {}.", trashedImage, exception);
        }
    }

    static void empty(Path gameDirectory) {
        Path trash = trashDirectory(gameDirectory);
        if (!Files.isDirectory(trash)) {
            return;
        }
        try (var paths = Files.walk(trash)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not empty photo trash.", exception);
        }
    }

    static Path trashDirectory(Path gameDirectory) {
        return gameDirectory.resolve("screenshots/snapshot").resolve(DIRECTORY);
    }

    private static List<Path> associatedFiles(Path image) {
        String base = stripExtension(image.getFileName().toString());
        List<Path> paths = new ArrayList<>();
        paths.add(image);
        paths.add(image.resolveSibling(base + ".source.png"));
        if (base.endsWith("_HDR")) {
            paths.add(image.resolveSibling(base.substring(0, base.length() - 4) + ".source.png"));
        }
        paths.add(image.resolveSibling(base + ".snapshot.json"));
        paths.add(image.resolveSibling(base + ".image2map.txt"));
        return paths;
    }

    private static void move(Path source, Path destination, List<Move> completed) throws IOException {
        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        completed.add(new Move(destination, source));
    }

    private static void rollback(List<Move> completed) {
        for (int index = completed.size() - 1; index >= 0; index--) {
            Move move = completed.get(index);
            try {
                if (Files.exists(move.source())) {
                    Files.move(move.source(), move.destination(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static long modifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private record Move(Path source, Path destination) {
    }
}
