package com.luci.snapshot.client.photo;

import com.google.gson.JsonObject;
import com.luci.snapshot.SnapshotInit;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

final class SnapshotComparisonScreen extends Screen {
    private final Path leftPath;
    private final Path rightPath;
    private final Screen previous;
    private SnapshotTextureLoader.TextureHandle leftTexture;
    private SnapshotTextureLoader.TextureHandle rightTexture;
    private int loadGeneration;

    SnapshotComparisonScreen(Path leftPath, Path rightPath, Screen previous) {
        super(Component.literal("Snapshot Comparison"));
        this.leftPath = leftPath;
        this.rightPath = rightPath;
        this.previous = previous;
    }

    @Override
    protected void init() {
        releaseTextures();
        int generation = loadGeneration;
        loadTexture(leftPath, "compare_left", generation, true);
        loadTexture(rightPath, "compare_right", generation, false);
    }

    private void loadTexture(Path path, String group, int generation, boolean left) {
        SnapshotTextureLoader.decodeAsync(path, 768, true).whenComplete((decoded, throwable) ->
            minecraft.execute(() -> {
                if (generation != loadGeneration || minecraft.gui.screen() != this) {
                    if (decoded != null) {
                        decoded.close();
                    }
                    return;
                }
                if (throwable != null) {
                    SnapshotInit.LOGGER.warn("[Snapshot] Could not load comparison image {}.", path, throwable);
                    return;
                }
                SnapshotTextureLoader.TextureHandle texture = SnapshotTextureLoader.register(decoded, group);
                if (left) {
                    leftTexture = texture;
                } else {
                    rightTexture = texture;
                }
            })
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.fill(0, 0, width, height, 0xFA050607);
        int gap = 8;
        int top = 25;
        int bottom = 31;
        int panelWidth = (width - gap * 3) / 2;
        drawImage(extractor, leftTexture, leftPath, gap, top, panelWidth, height - top - bottom);
        drawImage(extractor, rightTexture, rightPath, gap * 2 + panelWidth, top, panelWidth, height - top - bottom);
        extractor.centeredText(font, SnapshotLighttableScreen.styled("SIDE-BY-SIDE"), width / 2, 7, 0xFFF4F6F7);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void drawImage(GuiGraphicsExtractor extractor, SnapshotTextureLoader.TextureHandle texture,
                           Path path, int x, int y, int areaWidth, int areaHeight) {
        if (texture == null) {
            return;
        }
        double scale = Math.min(areaWidth / (double) texture.width(), areaHeight / (double) texture.height());
        int drawWidth = Math.max(1, (int) Math.round(texture.width() * scale));
        int drawHeight = Math.max(1, (int) Math.round(texture.height() * scale));
        int drawX = x + (areaWidth - drawWidth) / 2;
        int drawY = y + (areaHeight - drawHeight) / 2;
        extractor.blit(RenderPipelines.GUI_TEXTURED, texture.id(), drawX, drawY, 0.0F, 0.0F,
            drawWidth, drawHeight, texture.width(), texture.height(), texture.width(), texture.height());
        extractor.outline(drawX - 1, drawY - 1, drawWidth + 2, drawHeight + 2, 0x5543D6DF);
        JsonObject metadata = PhotoMetadataStore.read(path);
        String score = metadata.has("composition_score") ? "  SCORE " + metadata.get("composition_score").getAsInt() : "";
        extractor.centeredText(font, SnapshotLighttableScreen.styled(stripExtension(path.getFileName().toString()) + score),
            x + areaWidth / 2, height - 19, 0xFFB8C0C4);
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
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        releaseTextures();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void releaseTextures() {
        loadGeneration++;
        if (leftTexture != null) {
            SnapshotTextureLoader.release(minecraft, leftTexture);
            leftTexture = null;
        }
        if (rightTexture != null) {
            SnapshotTextureLoader.release(minecraft, rightTexture);
            rightTexture = null;
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
