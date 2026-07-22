package com.luci.snapshot.client.photo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.luci.snapshot.SnapshotInit;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

final class SnapshotImageReviewScreen extends Screen {
    private final Path imagePath;
    private final Screen previous;
    private final List<String> metadata = new ArrayList<>();
    private SnapshotTextureLoader.TextureHandle texture;
    private int loadGeneration;
    private boolean loading;

    SnapshotImageReviewScreen(Path imagePath, Screen previous) {
        super(Component.literal("Snapshot Review"));
        this.imagePath = imagePath;
        this.previous = previous;
    }

    @Override
    protected void init() {
        releaseTexture();
        metadata.clear();
        addRenderableWidget(Button.builder(Component.literal("Trash..."), button -> confirmDelete())
            .bounds(width - 92, 6, 80, 20)
            .build());
        loading = true;
        int generation = loadGeneration;
        SnapshotTextureLoader.decodeAsync(imagePath, 1024, true).whenComplete((decoded, throwable) ->
            minecraft.execute(() -> finishLoad(generation, decoded, throwable))
        );
    }

    private void finishLoad(int generation, SnapshotTextureLoader.DecodedImage decoded, Throwable throwable) {
        if (generation != loadGeneration || minecraft.gui.screen() != this) {
            if (decoded != null) {
                decoded.close();
            }
            return;
        }
        loading = false;
        if (throwable != null) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not open photograph {}.", imagePath, throwable);
            return;
        }
        texture = SnapshotTextureLoader.register(decoded, "review");
        readMetadata();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.fill(0, 0, width, height, 0xFA050607);
        if (texture != null) {
            int margin = 24;
            int top = 32;
            int metadataHeight = metadata.isEmpty() ? 24 : metadata.size() * 11 + 12;
            int availableWidth = width - margin * 2;
            int availableHeight = height - top - metadataHeight;
            double scale = Math.min(availableWidth / (double) texture.width(), availableHeight / (double) texture.height());
            int drawWidth = Math.max(1, (int) Math.round(texture.width() * scale));
            int drawHeight = Math.max(1, (int) Math.round(texture.height() * scale));
            int x = (width - drawWidth) / 2;
            int y = top + (availableHeight - drawHeight) / 2;
            extractor.blit(RenderPipelines.GUI_TEXTURED, texture.id(), x, y, 0.0F, 0.0F,
                drawWidth, drawHeight, texture.width(), texture.height(), texture.width(), texture.height());
            extractor.outline(x - 1, y - 1, drawWidth + 2, drawHeight + 2, 0x5543D6DF);
        } else if (loading) {
            extractor.centeredText(font, SnapshotLighttableScreen.styled("LOADING PREVIEW"),
                width / 2, height / 2, 0xFF9CA7AD);
        } else {
            extractor.centeredText(font, SnapshotLighttableScreen.styled("IMAGE UNAVAILABLE"), width / 2, height / 2, 0xFFFFD166);
        }

        extractor.centeredText(font, SnapshotLighttableScreen.styled(stripExtension(imagePath.getFileName().toString())),
            width / 2, 7, 0xFFF4F6F7);
        int metadataY = height - metadata.size() * 11 - 7;
        for (String line : metadata) {
            extractor.centeredText(font, SnapshotLighttableScreen.styled(line), width / 2, metadataY, 0xFFB8C0C4);
            metadataY += 11;
        }
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void readMetadata() {
        metadata.clear();
        Path sidecar = imagePath.resolveSibling(stripExtension(imagePath.getFileName().toString()) + ".snapshot.json");
        if (!Files.isRegularFile(sidecar)) {
            metadata.add(texture.width() + "x" + texture.height());
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(sidecar, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.has("camera")) {
                addWrappedMetadata(json.get("camera").getAsString());
            }
            if (json.has("x") && json.has("y") && json.has("z")) {
                String dimension = json.has("dimension") ? json.get("dimension").getAsString() + "  " : "";
                metadata.add(dimension + String.format(java.util.Locale.ROOT, "%.1f  %.1f  %.1f",
                    json.get("x").getAsDouble(), json.get("y").getAsDouble(), json.get("z").getAsDouble()));
            }
            if (json.has("objective") || json.has("composition_score")) {
                String objective = json.has("objective") ? json.get("objective").getAsString() : "Photograph";
                String score = json.has("composition_score") ? "  SCORE " + json.get("composition_score").getAsInt() : "";
                metadata.add(objective + score);
            }
            int rating = json.has("rating") ? json.get("rating").getAsInt() : 0;
            boolean favorite = json.has("favorite") && json.get("favorite").getAsBoolean();
            metadata.add((favorite ? "* FAVORITE  " : "") + "RATING " + rating + "/5  ALBUM "
                + PhotoAlbum.fromMetadata(json).label());
        } catch (RuntimeException | IOException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not read metadata for {}.", imagePath, exception);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(previous);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_M) {
            minecraft.setScreenAndShow(null);
            return true;
        }
        if (event.key() == InputConstants.KEY_F) {
            PhotoMetadataStore.toggleFavorite(imagePath);
            readMetadata();
            return true;
        }
        if (event.key() >= InputConstants.KEY_0 && event.key() <= InputConstants.KEY_5) {
            PhotoMetadataStore.setRating(imagePath, event.key() - InputConstants.KEY_0);
            readMetadata();
            return true;
        }
        if (event.key() == InputConstants.KEY_A) {
            PhotoMetadataStore.cycleAlbum(imagePath);
            readMetadata();
            return true;
        }
        if (event.key() == InputConstants.KEY_DELETE || event.key() == InputConstants.KEY_BACKSPACE) {
            confirmDelete();
            return true;
        }
        return super.keyPressed(event);
    }

    private void confirmDelete() {
        minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                SnapshotTrashStore.moveToTrash(imagePath);
                minecraft.setScreenAndShow(previous);
            } else {
                minecraft.setScreenAndShow(this);
            }
        }, Component.literal("Move photograph to trash?"),
            Component.literal(imagePath.getFileName() + " can be restored from Camera Roll / Trash.")));
    }

    @Override
    public void removed() {
        releaseTexture();
        super.removed();
    }

    private void releaseTexture() {
        loadGeneration++;
        loading = false;
        if (texture != null) {
            SnapshotTextureLoader.release(minecraft, texture);
            texture = null;
        }
    }

    private void addWrappedMetadata(String value) {
        StringBuilder line = new StringBuilder();
        for (String part : value.split(" \\| ")) {
            String candidate = line.isEmpty() ? part : line + "  |  " + part;
            if (!line.isEmpty() && font.width(SnapshotLighttableScreen.styled(candidate)) > width - 32) {
                metadata.add(line.toString());
                line.setLength(0);
                line.append(part);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (!line.isEmpty()) {
            metadata.add(line.toString());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
