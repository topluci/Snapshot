package com.luci.snapshot.client.camera;

import com.luci.snapshot.SnapshotInit;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public final class FocusPointSelectorScreen extends Screen {
    private static final FontDescription.Resource VIEWFINDER_FONT = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "viewfinder")
    );
    private int hoveredColumn;
    private int hoveredRow;

    public FocusPointSelectorScreen() {
        super(Component.literal("Snapshot AF Point Selection"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int left = width / 6;
        int right = width - left;
        int top = height / 6;
        int bottom = height - top;
        int cellWidth = (right - left) / 3;
        int cellHeight = (bottom - top) / 3;
        if (mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom) {
            hoveredColumn = Math.max(-1, Math.min(1, (mouseX - left) / Math.max(1, cellWidth) - 1));
            hoveredRow = Math.max(-1, Math.min(1, (mouseY - top) / Math.max(1, cellHeight) - 1));
        } else {
            hoveredColumn = 2;
            hoveredRow = 2;
        }

        extractor.fillGradient(0, 0, width, 42, 0x75000000, 0x00000000);
        extractor.centeredText(font, styled("SELECT AF POINT"), width / 2, 12, 0xFFF4F6F7);
        for (int row = -1; row <= 1; row++) {
            for (int column = -1; column <= 1; column++) {
                int x = left + (column + 1) * cellWidth + cellWidth / 2;
                int y = top + (row + 1) * cellHeight + cellHeight / 2;
                boolean hovered = column == hoveredColumn && row == hoveredRow;
                boolean selected = column == SnapshotCameraController.settings().focusPointX()
                    && row == SnapshotCameraController.settings().focusPointY();
                drawBracket(extractor, x, y, hovered ? 42 : 30, hovered ? 28 : 20,
                    hovered ? 0xFFFFD166 : selected ? 0xFF43D6DF : 0x88FFFFFF);
            }
        }
        extractor.centeredText(font, styled("CLICK TO LOCK SELECTION"), width / 2, height - 20, 0xFF9DA8AE);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
            && hoveredColumn >= -1 && hoveredColumn <= 1 && hoveredRow >= -1 && hoveredRow <= 1) {
            CameraSettings settings = SnapshotCameraController.settings();
            settings.setFocusPoint(hoveredColumn, hoveredRow);
            settings.setAutoFocus(true);
            settings.select(CameraControl.FOCUS_POINT);
            SnapshotCameraController.requestAutofocus();
            minecraft.setScreenAndShow(null);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_X || event.key() == InputConstants.KEY_ESCAPE) {
            minecraft.setScreenAndShow(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void drawBracket(GuiGraphicsExtractor extractor, int x, int y, int width, int height, int color) {
        int arm = Math.max(4, width / 4);
        extractor.horizontalLine(x - width / 2, x - width / 2 + arm, y - height / 2, color);
        extractor.verticalLine(x - width / 2, y - height / 2, y - height / 2 + arm, color);
        extractor.horizontalLine(x + width / 2 - arm, x + width / 2, y - height / 2, color);
        extractor.verticalLine(x + width / 2, y - height / 2, y - height / 2 + arm, color);
        extractor.horizontalLine(x - width / 2, x - width / 2 + arm, y + height / 2, color);
        extractor.verticalLine(x - width / 2, y + height / 2 - arm, y + height / 2, color);
        extractor.horizontalLine(x + width / 2 - arm, x + width / 2, y + height / 2, color);
        extractor.verticalLine(x + width / 2, y + height / 2 - arm, y + height / 2, color);
    }

    private static Component styled(String value) {
        return Component.literal(value).withStyle(style -> style.withFont(VIEWFINDER_FONT));
    }
}
