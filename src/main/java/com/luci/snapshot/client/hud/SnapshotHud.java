package com.luci.snapshot.client.hud;

import com.luci.snapshot.SnapshotInit;
import com.luci.snapshot.client.camera.CameraControl;
import com.luci.snapshot.client.camera.CameraSettings;
import com.luci.snapshot.client.camera.CaptureTechnique;
import com.luci.snapshot.client.camera.ExposureAnalysis;
import com.luci.snapshot.client.camera.ExposureAssist;
import com.luci.snapshot.client.camera.SnapshotCameraController;
import com.luci.snapshot.client.compat.ShaderCompatibility;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public final class SnapshotHud {
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "viewfinder");
    private static final FontDescription.Resource VIEWFINDER_FONT = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "viewfinder")
    );
    private static final int TEXT = 0xFFF4F6F7;
    private static final int MUTED = 0xFFB3BBC0;
    private static final int ACCENT = 0xFF43D6DF;
    private static final int WARNING = 0xFFFFD166;
    private static final int LOCKED = 0xFF74E3A5;

    private SnapshotHud() {
    }

    public static void register() {
        hideWhileViewfinder(VanillaHudElements.CROSSHAIR);
        hideWhileViewfinder(VanillaHudElements.HOTBAR);
        hideWhileViewfinder(VanillaHudElements.ARMOR_BAR);
        hideWhileViewfinder(VanillaHudElements.HEALTH_BAR);
        hideWhileViewfinder(VanillaHudElements.FOOD_BAR);
        hideWhileViewfinder(VanillaHudElements.AIR_BAR);
        hideWhileViewfinder(VanillaHudElements.MOUNT_HEALTH);
        hideWhileViewfinder(VanillaHudElements.INFO_BAR);
        hideWhileViewfinder(VanillaHudElements.EXPERIENCE_LEVEL);
        hideWhileViewfinder(VanillaHudElements.HELD_ITEM_TOOLTIP);
        hideWhileViewfinder(VanillaHudElements.MOB_EFFECTS);
        hideWhileViewfinder(VanillaHudElements.BOSS_BAR);
        hideWhileViewfinder(VanillaHudElements.OVERLAY_MESSAGE);
        hideWhileViewfinder(VanillaHudElements.SCOREBOARD);
        HudElementRegistry.addLast(HUD_ID, SnapshotHud::extract);
    }

    private static void hideWhileViewfinder(Identifier id) {
        HudElementRegistry.replaceElement(id, original -> (extractor, deltaTracker) -> {
            if (!SnapshotCameraController.active()) {
                original.extractRenderState(extractor, deltaTracker);
            }
        });
    }

    private static void extract(GuiGraphicsExtractor extractor, net.minecraft.client.DeltaTracker deltaTracker) {
        if (!SnapshotCameraController.active()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        CameraSettings settings = SnapshotCameraController.settings();
        float scale = SnapshotCameraController.hudExpanded() ? 0.62F : 0.55F;
        int width = Math.round(extractor.guiWidth() / scale);
        int height = Math.round(extractor.guiHeight() / scale);

        extractor.pose().pushMatrix();
        extractor.pose().scale(scale, scale);
        drawExposurePreview(extractor, width, height, settings);
        if (settings.astrophotography() && settings.redNightVision()) {
            extractor.fill(0, 0, width, height, 0x24180000);
            extractor.fillGradient(0, 0, width, Math.max(28, height / 9), 0x481C0000, 0x001C0000);
            extractor.fillGradient(0, height - Math.max(34, height / 8), width, height, 0x001C0000, 0x521C0000);
        }
        drawExposureAssist(extractor, width, height, settings);
        drawViewfinder(extractor, width, height, settings);
        drawAstroGuide(extractor, client.font, width, height, settings);
        drawCommandDial(extractor, client.font, width, settings);
        drawCornerReadouts(extractor, client.font, client, width, settings);
        drawBottomBar(extractor, client.font, width, height, settings);
        if (SnapshotCameraController.hudExpanded()) {
            drawExpandedPanel(extractor, client.font, client, width, height, settings);
        }
        drawLongExposure(extractor, client.font, width, height);
        drawShutter(extractor, width, height);
        extractor.pose().popMatrix();
    }

    private static void drawExposurePreview(GuiGraphicsExtractor extractor, int width, int height, CameraSettings settings) {
        double stops = SnapshotCameraController.previewExposureStops();
        if (stops < -0.05) {
            int alpha = Math.min(76, (int) Math.round(-stops * 15.0));
            extractor.fill(0, 0, width, height, alpha << 24);
        } else if (stops > 0.05) {
            int alpha = Math.min(54, (int) Math.round(stops * 9.0));
            extractor.fill(0, 0, width, height, (alpha << 24) | 0xFFF8EC);
        }

        int temperatureDelta = settings.whiteBalance() + settings.mood().temperatureOffset() - 6500;
        int temperatureAlpha = Math.min(24, Math.abs(temperatureDelta) / 180);
        if (temperatureAlpha > 0) {
            int tint = temperatureDelta > 0 ? 0xFFB46A : 0x72A9FF;
            extractor.fill(0, 0, width, height, (temperatureAlpha << 24) | tint);
        }

        if (!SnapshotCameraController.liveFilmActive()) {
            switch (settings.filmProfile()) {
                case WARM_400 -> extractor.fill(0, 0, width, height, 0x06FFB67A);
                case MUTED_CHROME -> extractor.fill(0, 0, width, height, 0x071C5E55);
                case MONOCHROME -> extractor.fill(0, 0, width, height, 0x06000000);
                case NEUTRAL -> {
                }
            }
        }

        switch (settings.mood()) {
            case OVERCAST -> extractor.fill(0, 0, width, height, 0x0C94A7B6);
            case ALPINE_SUNRISE -> extractor.fill(0, 0, width, height, 0x0AFFB060);
            case ETHEREAL_MIST -> extractor.fill(0, 0, width, height, 0x10DCE7F2);
            case ASTROPHOTOGRAPHY -> extractor.fill(0, 0, width, height, 0x08182E5A);
            case NATURAL -> {
            }
        }
    }

    private static void drawExposureAssist(GuiGraphicsExtractor extractor, int width, int height, CameraSettings settings) {
        ExposureAnalysis analysis = SnapshotCameraController.exposureAnalysis();
        ExposureAssist assist = settings.exposureAssist();
        if (analysis == null || assist == ExposureAssist.OFF) {
            return;
        }
        if (assist == ExposureAssist.ZEBRAS || assist == ExposureAssist.FALSE_COLOR) {
            int cellWidth = Math.max(1, (int) Math.ceil(width / (double) analysis.gridWidth()));
            int cellHeight = Math.max(1, (int) Math.ceil(height / (double) analysis.gridHeight()));
            for (int gridY = 0; gridY < analysis.gridHeight(); gridY++) {
                for (int gridX = 0; gridX < analysis.gridWidth(); gridX++) {
                    int luminance = analysis.luminanceAt(gridX, gridY);
                    int left = gridX * width / analysis.gridWidth();
                    int top = gridY * height / analysis.gridHeight();
                    if (assist == ExposureAssist.ZEBRAS && luminance >= 246 && ((gridX + gridY) & 1) == 0) {
                        extractor.fill(left, top, left + cellWidth, top + cellHeight, 0x2CFFFFFF);
                    } else if (assist == ExposureAssist.FALSE_COLOR) {
                        extractor.fill(left, top, left + cellWidth, top + cellHeight, falseColor(luminance));
                    }
                }
            }
        }

        if (assist == ExposureAssist.HISTOGRAM) {
            drawHistogram(extractor, analysis, 15, 78, 112, 38);
        } else if (assist == ExposureAssist.WAVEFORM) {
            drawWaveform(extractor, analysis, 15, 78, 112, 38);
        }

        Font font = Minecraft.getInstance().font;
        String clipping = String.format(Locale.ROOT, "H %.1f%%  S %.1f%%",
            analysis.clippedHighlights() * 100.0, analysis.crushedShadows() * 100.0);
        drawText(extractor, font, clipping, 15, 119, analysis.clippedHighlights() > 0.02 ? WARNING : MUTED);
    }

    private static void drawHistogram(GuiGraphicsExtractor extractor, ExposureAnalysis analysis, int x, int y, int width, int height) {
        extractor.fill(x, y, x + width, y + height, 0x4A000000);
        int maximum = 1;
        for (int index = 0; index < analysis.redHistogram().length; index++) {
            maximum = Math.max(maximum, Math.max(analysis.redHistogram()[index],
                Math.max(analysis.greenHistogram()[index], analysis.blueHistogram()[index])));
        }
        for (int index = 0; index < analysis.redHistogram().length; index++) {
            int columnX = x + index * width / analysis.redHistogram().length;
            int redHeight = analysis.redHistogram()[index] * (height - 2) / maximum;
            int greenHeight = analysis.greenHistogram()[index] * (height - 2) / maximum;
            int blueHeight = analysis.blueHistogram()[index] * (height - 2) / maximum;
            extractor.verticalLine(columnX, y + height - redHeight, y + height, 0x99FF5A5A);
            extractor.verticalLine(columnX + 1, y + height - greenHeight, y + height, 0x995DDF86);
            extractor.verticalLine(columnX + 2, y + height - blueHeight, y + height, 0x996DA7FF);
        }
        extractor.outline(x, y, width, height, 0x55FFFFFF);
    }

    private static void drawWaveform(GuiGraphicsExtractor extractor, ExposureAnalysis analysis, int x, int y, int width, int height) {
        extractor.fill(x, y, x + width, y + height, 0x4A000000);
        for (int index = 0; index < analysis.waveform().length; index++) {
            int pointX = x + index * (width - 1) / Math.max(1, analysis.waveform().length - 1);
            int pointY = y + height - 2 - Math.round(analysis.waveform()[index] * (height - 4));
            extractor.fill(pointX, pointY, pointX + 2, pointY + 2, 0xBB74E3A5);
        }
        extractor.horizontalLine(x, x + width, y + height / 2, 0x35FFFFFF);
        extractor.outline(x, y, width, height, 0x55FFFFFF);
    }

    private static int falseColor(int luminance) {
        if (luminance < 18) {
            return 0x44334CFF;
        }
        if (luminance < 55) {
            return 0x444A23A8;
        }
        if (luminance < 115) {
            return 0x4436C978;
        }
        if (luminance < 180) {
            return 0x44E4B54B;
        }
        if (luminance < 235) {
            return 0x44FF7A3D;
        }
        return 0x55FF3348;
    }

    private static void drawViewfinder(GuiGraphicsExtractor extractor, int width, int height, CameraSettings settings) {
        Frame frame = frameFor(width, height, settings);
        drawAspectMask(extractor, width, height, frame);
        int horizontalInset = Math.max(8, Math.round(frame.width() * 0.045F));
        int verticalInset = Math.max(8, Math.round(frame.height() * 0.055F));
        int left = frame.left() + horizontalInset;
        int right = frame.right() - horizontalInset;
        int top = frame.top() + verticalInset;
        int bottom = frame.bottom() - verticalInset;

        extractor.fillGradient(0, 0, width, top + 18, 0x72000000, 0x00000000);
        extractor.fillGradient(0, bottom - 24, width, height, 0x00000000, 0x78000000);
        extractor.fill(0, 0, 7, height, 0x4C000000);
        extractor.fill(width - 7, 0, width, height, 0x4C000000);

        int guide = 0x36FFFFFF;
        int innerWidth = right - left;
        int innerHeight = bottom - top;
        int thirdX = innerWidth / 3;
        int thirdY = innerHeight / 3;
        extractor.verticalLine(left + thirdX, top, bottom, guide);
        extractor.verticalLine(left + thirdX * 2, top, bottom, guide);
        extractor.horizontalLine(left, right, top + thirdY, guide);
        extractor.horizontalLine(left, right, top + thirdY * 2, guide);

        int centerX = width / 2;
        int centerY = top + innerHeight / 2;
        drawFocusPointArray(extractor, centerX, centerY, innerWidth, innerHeight, settings);
        int focusX = centerX + settings.focusPointX() * innerWidth / 4;
        int focusY = centerY + settings.focusPointY() * innerHeight / 4;
        int focusColor = settings.autoFocus()
            ? (SnapshotCameraController.focusLocked() ? LOCKED : MUTED)
            : WARNING;
        drawBracket(extractor, focusX, focusY, 30, 20, focusColor);
        extractor.horizontalLine(focusX - 4, focusX + 4, focusY, focusColor);
        extractor.verticalLine(focusX, focusY - 4, focusY + 4, focusColor);

        if (!settings.autoFocus()) {
            drawFocusPeaking(extractor, focusX, focusY);
        }
    }

    private static void drawFocusPointArray(GuiGraphicsExtractor extractor, int centerX, int centerY,
                                            int innerWidth, int innerHeight, CameraSettings settings) {
        int passive = 0x28FFFFFF;
        for (int row = -1; row <= 1; row++) {
            for (int column = -1; column <= 1; column++) {
                if (column == settings.focusPointX() && row == settings.focusPointY()) {
                    continue;
                }
                int x = centerX + column * innerWidth / 4;
                int y = centerY + row * innerHeight / 4;
                extractor.horizontalLine(x - 2, x + 2, y, passive);
                extractor.verticalLine(x, y - 2, y + 2, passive);
            }
        }
    }

    private static Frame frameFor(int width, int height, CameraSettings settings) {
        if (settings.aspectRatio().nativeFrame()) {
            return new Frame(0, 0, width, height);
        }
        double current = width / (double) height;
        double target = settings.aspectRatio().ratio();
        if (current > target) {
            int frameWidth = Math.max(1, (int) Math.round(height * target));
            int left = (width - frameWidth) / 2;
            return new Frame(left, 0, left + frameWidth, height);
        }
        int frameHeight = Math.max(1, (int) Math.round(width / target));
        int top = (height - frameHeight) / 2;
        return new Frame(0, top, width, top + frameHeight);
    }

    private static void drawAspectMask(GuiGraphicsExtractor extractor, int width, int height, Frame frame) {
        int mask = 0xD8000000;
        if (frame.left() > 0) {
            extractor.fill(0, 0, frame.left(), height, mask);
            extractor.fill(frame.right(), 0, width, height, mask);
            extractor.verticalLine(frame.left(), 0, height, 0x50FFFFFF);
            extractor.verticalLine(frame.right(), 0, height, 0x50FFFFFF);
        }
        if (frame.top() > 0) {
            extractor.fill(0, 0, width, frame.top(), mask);
            extractor.fill(0, frame.bottom(), width, height, mask);
            extractor.horizontalLine(0, width, frame.top(), 0x50FFFFFF);
            extractor.horizontalLine(0, width, frame.bottom(), 0x50FFFFFF);
        }
    }

    private static void drawBracket(GuiGraphicsExtractor extractor, int centerX, int centerY, int width, int height, int color) {
        int left = centerX - width / 2;
        int right = centerX + width / 2;
        int top = centerY - height / 2;
        int bottom = centerY + height / 2;
        int arm = 6;
        extractor.horizontalLine(left, left + arm, top, color);
        extractor.verticalLine(left, top, top + arm, color);
        extractor.horizontalLine(right - arm, right, top, color);
        extractor.verticalLine(right, top, top + arm, color);
        extractor.horizontalLine(left, left + arm, bottom, color);
        extractor.verticalLine(left, bottom - arm, bottom, color);
        extractor.horizontalLine(right - arm, right, bottom, color);
        extractor.verticalLine(right, bottom - arm, bottom, color);
    }

    private static void drawFocusPeaking(GuiGraphicsExtractor extractor, int centerX, int centerY) {
        int peak = 0xAAFF4D57;
        extractor.horizontalLine(centerX - 42, centerX - 34, centerY - 15, peak);
        extractor.horizontalLine(centerX + 34, centerX + 42, centerY - 15, peak);
        extractor.horizontalLine(centerX - 36, centerX - 28, centerY + 17, peak);
        extractor.horizontalLine(centerX + 28, centerX + 36, centerY + 17, peak);
    }

    private static void drawCommandDial(GuiGraphicsExtractor extractor, Font font, int width, CameraSettings settings) {
        if (SnapshotCameraController.commandDialTicks() <= 0) {
            return;
        }
        String[] modes = {"M", "A", "S", "P", "AUTO"};
        int itemWidth = 34;
        int totalWidth = itemWidth * modes.length;
        int left = width / 2 - totalWidth / 2;
        int top = 13;
        extractor.fillGradient(left - 8, top - 6, left + totalWidth + 8, top + 18, 0x8A000000, 0x36000000);
        for (int index = 0; index < modes.length; index++) {
            boolean selected = modes[index].equals(settings.exposureMode().label());
            int center = left + index * itemWidth + itemWidth / 2;
            extractor.centeredText(font, styled(modes[index]), center, top, selected ? WARNING : MUTED);
            if (selected) {
                extractor.horizontalLine(center - 9, center + 9, top + 11, ACCENT);
            }
        }
    }

    private static void drawCornerReadouts(GuiGraphicsExtractor extractor, Font font, Minecraft client, int width, CameraSettings settings) {
        int x = 15;
        int y = 12;
        drawSun(extractor, x + 4, y + 4, MUTED);
        drawText(extractor, font, settings.whiteBalance() + " K", x + 14, y, selectedColor(settings, CameraControl.WHITE_BALANCE));
        String focusLock = SnapshotCameraController.afLocked() ? "  AF-L" : "";
        drawText(extractor, font, (settings.autoFocus() ? "AF-S  " : "MF  ") + settings.focusDistanceLabel() + focusLock, x, y + 12,
            selectedColor(settings, CameraControl.FOCUS_DISTANCE));
        String lock = SnapshotCameraController.aeLocked() ? "  AE-L" : "";
        drawText(extractor, font, settings.meteringMode().label() + lock, x, y + 24,
            selectedColor(settings, CameraControl.METERING));
        drawText(extractor, font, settings.mood().label().toUpperCase(Locale.ROOT), x, y + 36,
            selectedColor(settings, CameraControl.MOOD));
        drawText(extractor, font, SnapshotCameraController.environmentPreset().toUpperCase(Locale.ROOT), x, y + 48, MUTED);

        String resolution = client.getWindow().getWidth() + "x" + client.getWindow().getHeight();
        drawRightText(extractor, font, resolution, width - 15, y, TEXT);
        drawRightText(extractor, font, settings.filmProfile().label().toUpperCase(Locale.ROOT), width - 15, y + 12,
            selectedColor(settings, CameraControl.FILM_PROFILE));
        String format = settings.aspectRatio().label() + "  R " + String.format(Locale.ROOT, "%+.1f", settings.rollDegrees()) + "deg";
        drawRightText(extractor, font, format, width - 15, y + 24,
            settings.selected() == CameraControl.ASPECT_RATIO || settings.selected() == CameraControl.CAMERA_ROLL ? WARNING : TEXT);
        drawRightText(extractor, font, settings.preset().label().toUpperCase(Locale.ROOT), width - 15, y + 36,
            selectedColor(settings, CameraControl.PRESET));
        String drive = settings.captureTechnique() != CaptureTechnique.SINGLE
            ? settings.captureTechnique().label()
            : settings.exposureBracket().enabled() ? settings.exposureBracket().label()
            : settings.burst() ? "BURST" : "ONE SHOT";
        drawRightText(extractor, font, drive, width - 15, y + 48, MUTED);
        drawRightText(extractor, font, ShaderCompatibility.rendererLabel().toUpperCase(Locale.ROOT), width - 15, y + 60, MUTED);
        if (SnapshotCameraController.liveFilmActive()) {
            drawRightText(extractor, font, "LIVE DEPTH OPTICS", width - 15, y + 72, LOCKED);
        }
        if (settings.astrophotography()) {
            String astro = settings.astroStackMode().label()
                + (settings.darkFrameSubtraction() ? "  DARK" : "");
            drawRightText(extractor, font, astro, width - 15, y + 84, settings.redNightVision() ? 0xFFE86A6A : WARNING);
        }
        if (!SnapshotCameraController.intervalometerLabel().isEmpty()) {
            drawRightText(extractor, font, SnapshotCameraController.intervalometerLabel(), width - 15,
                y + (settings.astrophotography() ? 96 : 84), WARNING);
        }
    }

    private static void drawAstroGuide(GuiGraphicsExtractor extractor, Font font, int width, int height, CameraSettings settings) {
        if (!settings.astrophotography() || !SnapshotCameraController.constellationGuide()) {
            return;
        }
        boolean red = settings.redNightVision();
        int color = red ? 0x55E86A6A : 0x5574C7FF;
        int[][] points = {
            {18, 26}, {26, 20}, {34, 29}, {43, 18}, {51, 31},
            {58, 22}, {67, 28}, {74, 17}, {82, 25}, {69, 42},
            {56, 48}, {44, 41}, {31, 47}, {22, 39}
        };
        int previousX = -1;
        int previousY = -1;
        for (int[] point : points) {
            int x = point[0] * width / 100;
            int y = point[1] * height / 100;
            extractor.fill(x - 1, y - 1, x + 2, y + 2, red ? 0xAAFFB0A8 : 0xAAE4F3FF);
            if (previousX >= 0) {
                drawGuideLine(extractor, previousX, previousY, x, y, color);
            }
            previousX = x;
            previousY = y;
        }
        int guideColor = red ? 0x44E86A6A : 0x4474C7FF;
        extractor.horizontalLine(width / 2 - 20, width / 2 + 20, height / 2, guideColor);
        extractor.verticalLine(width / 2, height / 2 - 20, height / 2 + 20, guideColor);
        drawText(extractor, font, settings.astroTracking().label(), width / 2 + 24, height / 2 - 5,
            red ? 0x99FF8A80 : 0x9974C7FF);
    }

    private static void drawGuideLine(GuiGraphicsExtractor extractor, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int step = 0; step <= steps; step += 3) {
            int x = x0 + (x1 - x0) * step / Math.max(1, steps);
            int y = y0 + (y1 - y0) * step / Math.max(1, steps);
            extractor.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void drawBottomBar(GuiGraphicsExtractor extractor, Font font, int width, int height, CameraSettings settings) {
        int baseline = height - 14;
        extractor.fillGradient(0, height - 34, width, height, 0x00000000, 0x8A000000);

        drawReadout(extractor, font, settings.exposureMode().label(), 15, baseline, 22,
            settings.selected() == CameraControl.EXPOSURE_MODE);
        drawReadout(extractor, font, settings.shutter(), 43, baseline, 52, settings.selected() == CameraControl.SHUTTER);
        drawReadout(extractor, font, "f/" + settings.aperture(), 103, baseline, 50, settings.selected() == CameraControl.APERTURE);
        drawReadout(extractor, font, (settings.autoIso() ? "A-ISO " : "ISO ") + settings.iso(), 161, baseline, 72,
            settings.selected() == CameraControl.ISO || settings.selected() == CameraControl.AUTO_ISO);

        int meterWidth = Math.min(150, Math.max(96, width / 5));
        int meterX = width / 2 - meterWidth / 2;
        drawMeter(extractor, font, meterX, baseline - 1, meterWidth, settings);

        String af = settings.autoFocus() ? (SnapshotCameraController.focusLocked() ? "AF LOCK" : "AF") : "MF";
        drawReadout(extractor, font, settings.focalLength() + "mm", width - 148, baseline, 64,
            settings.selected() == CameraControl.FOCAL_LENGTH);
        drawReadout(extractor, font, af, width - 76, baseline, 61,
            settings.selected() == CameraControl.FOCUS_DISTANCE);
    }

    private static void drawReadout(GuiGraphicsExtractor extractor, Font font, String value, int x, int y, int width, boolean selected) {
        drawText(extractor, font, value, x, y, selected ? WARNING : TEXT);
        if (selected) {
            extractor.horizontalLine(x, x + Math.min(width, Math.max(12, font.width(styled(value)))), y + 10, ACCENT);
        }
    }

    private static void drawMeter(GuiGraphicsExtractor extractor, Font font, int x, int y, int width, CameraSettings settings) {
        int center = x + width / 2;
        int lineY = y + 6;
        extractor.horizontalLine(x, x + width, lineY, 0x99FFFFFF);
        for (int i = -3; i <= 3; i++) {
            int tickX = center + Math.round(i * width / 6.0F);
            extractor.verticalLine(tickX, lineY - (i == 0 ? 4 : 2), lineY + 3, i == 0 ? TEXT : 0x88FFFFFF);
        }
        double meterStops = Math.max(-3.0, Math.min(3.0, SnapshotCameraController.previewExposureStops()));
        int indicator = center + (int) Math.round(meterStops * width / 6.0);
        extractor.fill(indicator - 2, lineY + 4, indicator + 3, lineY + 7, WARNING);
        drawRightText(extractor, font, signed(settings.exposureCompensationStops()), x + width, y - 7,
            selectedColor(settings, CameraControl.EXPOSURE_COMPENSATION));
    }

    private static void drawExpandedPanel(GuiGraphicsExtractor extractor, Font font, Minecraft client, int width, int height, CameraSettings settings) {
        int panelWidth = 276;
        int panelHeight = 112;
        int x = width - panelWidth - 14;
        int y = height - panelHeight - 34;
        extractor.fill(x, y, x + panelWidth, y + panelHeight, 0xB006080A);
        extractor.fillGradient(x, y, x + panelWidth, y + 14, 0xA0000000, 0x36000000);

        drawText(extractor, font, settings.exposureMode().label(), x + 8, y + 7,
            settings.selected() == CameraControl.EXPOSURE_MODE || settings.selected() == CameraControl.METERING ? WARNING : MUTED);
        drawText(extractor, font, settings.shutter(), x + 58, y + 7, selectedColor(settings, CameraControl.SHUTTER));
        drawText(extractor, font, "f/" + settings.aperture(), x + 106, y + 7, selectedColor(settings, CameraControl.APERTURE));
        drawText(extractor, font, "ISO " + settings.iso(), x + 157, y + 7, selectedColor(settings, CameraControl.ISO));

        drawMeter(extractor, font, x + 8, y + 29, panelWidth - 16, settings);

        drawText(extractor, font, "WB " + settings.whiteBalance() + "K", x + 8, y + 50, selectedColor(settings, CameraControl.WHITE_BALANCE));
        drawText(extractor, font, settings.focalLength() + "mm", x + 76, y + 50, selectedColor(settings, CameraControl.FOCAL_LENGTH));
        drawText(extractor, font, settings.autoFocus() ? "AF " + settings.focusDistanceLabel() : "MF " + settings.focusDistanceLabel(), x + 123, y + 50,
            selectedColor(settings, CameraControl.FOCUS_DISTANCE));
        drawRightText(extractor, font, settings.filter().label(), x + panelWidth - 8, y + 50, selectedColor(settings, CameraControl.FILTER));

        String line = settings.filmProfile().label() + "   " + settings.aspectRatio().label()
            + "   R " + String.format(Locale.ROOT, "%+.1f", settings.rollDegrees());
        drawText(extractor, font, line, x + 8, y + 67, MUTED);
        drawRightText(extractor, font, client.getWindow().getWidth() + "x" + client.getWindow().getHeight(), x + panelWidth - 8, y + 67, MUTED);
        String finalLine = (settings.burst() ? "BURST" : "ONE SHOT") + "   C " + signed(settings.contrast()) + "   S " + signed(settings.saturation());
        drawText(extractor, font, finalLine, x + 8, y + 81, MUTED);
        drawText(extractor, font, settings.lens().label() + "   AF " + settings.focusPointLabel(), x + 8, y + 96,
            settings.selected() == CameraControl.LENS || settings.selected() == CameraControl.FOCUS_POINT ? WARNING : MUTED);
        drawRightText(extractor, font, settings.exposureAssist().label(), x + panelWidth - 8, y + 96,
            selectedColor(settings, CameraControl.EXPOSURE_ASSIST));
    }

    private static void drawLongExposure(GuiGraphicsExtractor extractor, Font font, int width, int height) {
        if (!SnapshotCameraController.longExposureActive()) {
            return;
        }
        int barWidth = Math.min(240, Math.max(140, width / 4));
        int left = width / 2 - barWidth / 2;
        int top = height - 56;
        extractor.fill(left, top, left + barWidth, top + 4, 0x88000000);
        int progress = (int) Math.round(barWidth * SnapshotCameraController.longExposureProgress());
        extractor.fill(left, top, left + progress, top + 4, 0xFFFF5B5B);
        drawText(extractor, font, SnapshotCameraController.longExposureLabel(), left, top - 12, WARNING);
    }

    private static void drawShutter(GuiGraphicsExtractor extractor, int width, int height) {
        int shutter = SnapshotCameraController.shutterTicks();
        if (shutter > 0) {
            int alpha = Math.min(230, 60 + shutter * 38);
            extractor.fill(0, 0, width, height, alpha << 24);
        }
        int flash = SnapshotCameraController.flashTicks();
        if (flash > 0) {
            int alpha = Math.min(190, 35 + flash * 30);
            extractor.fill(0, 0, width, height, (alpha << 24) | 0xFFFFFF);
        }
    }

    private static void drawSun(GuiGraphicsExtractor extractor, int x, int y, int color) {
        extractor.outline(x - 2, y - 2, 5, 5, color);
        extractor.horizontalLine(x - 5, x - 4, y, color);
        extractor.horizontalLine(x + 4, x + 5, y, color);
        extractor.verticalLine(x, y - 5, y - 4, color);
        extractor.verticalLine(x, y + 4, y + 5, color);
    }

    private static void drawText(GuiGraphicsExtractor extractor, Font font, String value, int x, int y, int color) {
        extractor.text(font, styled(value), x, y, color, true);
    }

    private static void drawRightText(GuiGraphicsExtractor extractor, Font font, String value, int right, int y, int color) {
        Component text = styled(value);
        extractor.text(font, text, right - font.width(text), y, color, true);
    }

    private static Component styled(String value) {
        return Component.literal(value).withStyle(style -> style.withFont(VIEWFINDER_FONT));
    }

    private static int selectedColor(CameraSettings settings, CameraControl control) {
        return settings.selected() == control ? WARNING : TEXT;
    }

    private static String signed(float value) {
        return String.format(Locale.ROOT, "%+.1f", value);
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    private record Frame(int left, int top, int right, int bottom) {
        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }
    }
}
