package com.luci.snapshot.client.photo;

import com.luci.snapshot.SnapshotInit;
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
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

final class SnapshotPhotographScreen extends Screen {
    private static final FontDescription.Resource VIEWFINDER_FONT = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "viewfinder")
    );

    private final ItemStack photograph;

    SnapshotPhotographScreen(ItemStack photograph) {
        super(photograph.getHoverName());
        this.photograph = photograph;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
            .bounds(width / 2 - 50, height - 28, 100, 20)
            .build());
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
        } else {
            extractor.centeredText(font, styled("Loading photograph..."), width / 2, y + size / 2 - 4, 0xFFE7EAEC);
        }

        extractor.centeredText(font, styled(photograph.getHoverName().getString()), width / 2, 7, 0xFFF4F6F7);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component styled(String value) {
        return Component.literal(value).withStyle(style -> style.withFont(VIEWFINDER_FONT));
    }
}
