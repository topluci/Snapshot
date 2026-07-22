package com.luci.snapshot.client.photo;

import com.luci.snapshot.SnapshotInit;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

final class SnapshotTextureLoader {
    private SnapshotTextureLoader() {
    }

    static CompletableFuture<DecodedImage> decodeAsync(Path path, int limit, boolean cachePreview) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return decode(path, limit, cachePreview);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    static DecodedImage decode(Path path, int limit, boolean cachePreview) throws IOException {
        Path sourcePath = cachePreview ? validCache(path, limit) : null;
        boolean fromCache = sourcePath != null;
        if (sourcePath == null) {
            sourcePath = path;
        }

        NativeImage source;
        try (InputStream stream = Files.newInputStream(sourcePath)) {
            source = NativeImage.read(stream);
        }

        NativeImage pixels = source;
        int boundedLimit = Math.max(64, limit);
        int largest = Math.max(source.getWidth(), source.getHeight());
        if (largest > boundedLimit) {
            double scale = boundedLimit / (double) largest;
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            pixels = new NativeImage(width, height, false);
            source.resizeSubRectTo(0, 0, source.getWidth(), source.getHeight(), pixels);
            source.close();
        }

        if (cachePreview && !fromCache && pixels != source) {
            Path cache = cachePath(path, boundedLimit);
            try {
                Files.createDirectories(cache.getParent());
                pixels.writeToFile(cache);
                Files.setLastModifiedTime(cache, Files.getLastModifiedTime(path));
            } catch (IOException exception) {
                SnapshotInit.LOGGER.debug("[Snapshot] Could not cache preview {}.", cache, exception);
            }
        }
        return new DecodedImage(path, pixels);
    }

    static TextureHandle register(DecodedImage decoded, String group) {
        NativeImage pixels = decoded.takePixels();
        String hash = Integer.toUnsignedString(decoded.path().toAbsolutePath().toString().hashCode());
        String safeGroup = group.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        Identifier id = Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, safeGroup + "/" + hash);
        DynamicTexture texture = new DynamicTexture(
            () -> "Snapshot " + safeGroup + " " + decoded.path().getFileName(), pixels
        );
        Minecraft.getInstance().getTextureManager().register(id, texture);
        texture.upload();
        return new TextureHandle(decoded.path(), id, pixels.getWidth(), pixels.getHeight());
    }

    static TextureHandle register(Path identity, NativeImage pixels, String group) {
        return register(new DecodedImage(identity, pixels), group);
    }

    static void drawFitted(GuiGraphicsExtractor extractor, TextureHandle texture,
                           int x, int y, int width, int height) {
        double scale = Math.min(width / (double) texture.width(), height / (double) texture.height());
        int drawWidth = Math.max(1, (int) Math.round(texture.width() * scale));
        int drawHeight = Math.max(1, (int) Math.round(texture.height() * scale));
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;
        extractor.blit(RenderPipelines.GUI_TEXTURED, texture.id(), drawX, drawY, 0.0F, 0.0F,
            drawWidth, drawHeight, texture.width(), texture.height(), texture.width(), texture.height());
    }

    static void release(Minecraft minecraft, TextureHandle texture) {
        if (texture != null) {
            minecraft.getTextureManager().release(texture.id());
        }
    }

    static void deleteCached(Path image) {
        Path directory = image.getParent() == null ? null : image.getParent().resolve(".thumbnails");
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        String prefix = stripExtension(image.getFileName().toString()) + "_";
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(candidate -> candidate.getFileName().toString().startsWith(prefix)).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            SnapshotInit.LOGGER.debug("[Snapshot] Could not remove cached previews for {}.", image, exception);
        }
    }

    private static Path validCache(Path image, int limit) {
        Path cache = cachePath(image, limit);
        try {
            if (Files.isRegularFile(cache)
                && Files.getLastModifiedTime(cache).toMillis() >= Files.getLastModifiedTime(image).toMillis()) {
                return cache;
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static Path cachePath(Path image, int limit) {
        String name = stripExtension(image.getFileName().toString()) + "_" + limit + ".png";
        return image.resolveSibling(".thumbnails").resolve(name);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    static final class DecodedImage implements AutoCloseable {
        private final Path path;
        private NativeImage pixels;

        private DecodedImage(Path path, NativeImage pixels) {
            this.path = path;
            this.pixels = pixels;
        }

        Path path() {
            return path;
        }

        NativeImage takePixels() {
            NativeImage result = pixels;
            pixels = null;
            return result;
        }

        @Override
        public void close() {
            if (pixels != null) {
                pixels.close();
                pixels = null;
            }
        }
    }

    record TextureHandle(Path path, Identifier id, int width, int height) {
    }
}
