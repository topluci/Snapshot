package com.luci.snapshot.client.photo;

import com.luci.snapshot.SnapshotInit;
import com.luci.snapshot.item.PhotographData;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

final class SnapshotPhotographScreen extends Screen {
    private static final FontDescription.Resource VIEWFINDER_FONT = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "viewfinder")
    );

    private final ItemStack photograph;
    private SnapshotTextureLoader.TextureHandle fallbackTexture;
    private int loadGeneration;
    private boolean loading;

    SnapshotPhotographScreen(ItemStack photograph) {
        super(photograph.getHoverName());
        this.photograph = photograph;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
            .bounds(width / 2 - 50, height - 28, 100, 20)
            .build());
        loadFallback();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.fill(0, 0, width, height, 0xE6000000);
        int size = Math.min(width - 52, height - 72);
        int x = (width - size) / 2;
        int y = 22;
        extractor.fill(x - 5, y - 5, x + size + 5, y + size + 14, 0xFFF1F0EC);
        extractor.fill(x, y, x + size, y + size, 0xFF17191B);

        MapId mapId = photograph.get(DataComponents.MAP_ID);
        MapItemSavedData data = minecraft.level == null ? null : MapItem.getSavedData(mapId, minecraft.level);
        if (mapId != null && data != null) {
            MapRenderState state = new MapRenderState();
            minecraft.getMapRenderer().extractRenderState(mapId, data, state);
            float mapScale = size / 128.0F;
            extractor.pose().pushMatrix();
            extractor.pose().translate(x, y);
            extractor.pose().scale(mapScale, mapScale);
            extractor.map(state);
            extractor.pose().popMatrix();
        } else if (fallbackTexture != null) {
            SnapshotTextureLoader.drawFitted(extractor, fallbackTexture, x, y, size, size);
        } else {
            extractor.centeredText(font, styled(loading ? "Loading photograph..." : "Photograph unavailable"),
                width / 2, y + size / 2 - 4, loading ? 0xFFE7EAEC : 0xFFFFD166);
        }

        extractor.centeredText(font, styled(photograph.getHoverName().getString()), width / 2, 7, 0xFFF4F6F7);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        releaseFallback();
        super.removed();
    }

    private void loadFallback() {
        releaseFallback();
        NativeImage embedded = embeddedPreview();
        if (embedded != null) {
            String identity = PhotographData.title(photograph);
            if (identity.isBlank()) {
                identity = photograph.getHoverName().getString();
            }
            fallbackTexture = SnapshotTextureLoader.register(
                Path.of("embedded", Integer.toUnsignedString(identity.hashCode()) + ".png"), embedded, "photograph"
            );
            return;
        }

        Path localImage = localImage();
        if (localImage == null) {
            return;
        }
        loading = true;
        int generation = loadGeneration;
        SnapshotTextureLoader.decodeAsync(localImage, 1024, true).whenComplete((decoded, throwable) ->
            minecraft.execute(() -> {
                if (generation != loadGeneration || minecraft.gui.screen() != this) {
                    if (decoded != null) {
                        decoded.close();
                    }
                    return;
                }
                loading = false;
                if (throwable != null) {
                    SnapshotInit.LOGGER.warn("[Snapshot] Could not load photograph preview {}.", localImage, throwable);
                    return;
                }
                fallbackTexture = SnapshotTextureLoader.register(decoded, "photograph");
            })
        );
    }

    private NativeImage embeddedPreview() {
        byte[] colors = PhotographData.colors(photograph).orElse(null);
        if (colors == null) {
            return null;
        }
        NativeImage image = new NativeImage(128, 128, false);
        for (int index = 0; index < colors.length; index++) {
            image.setPixel(index % 128, index / 128, MapColor.getColorFromPackedId(colors[index] & 0xFF));
        }
        return image;
    }

    private Path localImage() {
        String title = PhotographData.title(photograph);
        if (title.isBlank()) {
            title = photograph.getHoverName().getString();
            String suffix = " - Photograph";
            if (title.endsWith(suffix)) {
                title = title.substring(0, title.length() - suffix.length());
            }
        }
        if (title.isBlank()) {
            return null;
        }
        Path image = minecraft.gameDirectory.toPath().resolve("screenshots/snapshot").resolve(title + ".png");
        return Files.isRegularFile(image) ? image : null;
    }

    private void releaseFallback() {
        loadGeneration++;
        loading = false;
        if (fallbackTexture != null) {
            SnapshotTextureLoader.release(minecraft, fallbackTexture);
            fallbackTexture = null;
        }
    }

    private static Component styled(String value) {
        return Component.literal(value).withStyle(style -> style.withFont(VIEWFINDER_FONT));
    }
}
