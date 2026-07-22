package com.luci.snapshot.client.photo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.luci.snapshot.SnapshotInit;
import com.luci.snapshot.util.AtomicFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

final class PhotoMetadataStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    private PhotoMetadataStore() {
    }

    static JsonObject read(Path image) {
        Path sidecar = sidecar(image);
        if (!Files.isRegularFile(sidecar)) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(Files.readString(sidecar, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException | IOException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not read photo metadata for {}.", image, exception);
            return new JsonObject();
        }
    }

    static boolean favorite(Path image) {
        JsonObject metadata = read(image);
        return metadata.has("favorite") && metadata.get("favorite").getAsBoolean();
    }

    static int rating(Path image) {
        JsonObject metadata = read(image);
        return metadata.has("rating") ? Math.max(0, Math.min(5, metadata.get("rating").getAsInt())) : 0;
    }

    static PhotoAlbum album(Path image) {
        return PhotoAlbum.fromMetadata(read(image));
    }

    static void toggleFavorite(Path image) {
        JsonObject metadata = read(image);
        metadata.addProperty("favorite", !(metadata.has("favorite") && metadata.get("favorite").getAsBoolean()));
        write(image, metadata);
    }

    static void setRating(Path image, int rating) {
        JsonObject metadata = read(image);
        metadata.addProperty("rating", Math.max(0, Math.min(5, rating)));
        write(image, metadata);
    }

    static PhotoAlbum cycleAlbum(Path image) {
        JsonObject metadata = read(image);
        PhotoAlbum album = PhotoAlbum.fromMetadata(metadata).nextAssignable();
        metadata.addProperty("album", album.name());
        write(image, metadata);
        return album;
    }

    static void delete(Path image) {
        String base = stripExtension(image.getFileName().toString());
        try {
            Files.deleteIfExists(image);
            Files.deleteIfExists(image.resolveSibling(base + ".source.png"));
            if (base.endsWith("_HDR")) {
                Files.deleteIfExists(image.resolveSibling(base.substring(0, base.length() - 4) + ".source.png"));
            }
            Files.deleteIfExists(image.resolveSibling(base + ".snapshot.json"));
            Files.deleteIfExists(image.resolveSibling(base + ".image2map.txt"));
            SnapshotTextureLoader.deleteCached(image);
            Path screenshots = image.getParent() == null ? null : image.getParent().getParent();
            if (screenshots != null) {
                Files.deleteIfExists(screenshots.resolve(image.getFileName()));
            }
        } catch (IOException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not delete photo {}.", image, exception);
        }
    }

    static void deleteAll(Collection<Path> images) {
        for (Path image : List.copyOf(images)) {
            delete(image);
        }
    }

    private static void write(Path image, JsonObject metadata) {
        try {
            AtomicFiles.writeString(sidecar(image), JSON.toJson(metadata), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not update photo metadata for {}.", image, exception);
        }
    }

    private static Path sidecar(Path image) {
        return image.resolveSibling(stripExtension(image.getFileName().toString()) + ".snapshot.json");
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
