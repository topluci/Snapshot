package com.luci.snapshot.client.camera;

import com.luci.snapshot.SnapshotInit;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public final class CameraQuickMenuScreen extends Screen {
    private static final FontDescription.Resource VIEWFINDER_FONT = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "viewfinder")
    );
    private static final int TEXT = 0xFFF4F6F7;
    private static final int MUTED = 0xFF9DA8AE;
    private static final int ACCENT = 0xFF43D6DF;
    private static final int SELECTED = 0xFFFFD166;

    private Page page = Page.MODE;
    private int hovered = -1;

    public CameraQuickMenuScreen() {
        super(Component.literal("Snapshot Camera Command Dial"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int centerX = width / 2;
        int centerY = height / 2;
        int radius = Math.max(68, Math.min(108, Math.min(width, height) / 4));
        List<RadialOption> options = options();
        hovered = hoveredOption(mouseX, mouseY, centerX, centerY, radius, options.size());

        extractor.fill(0, 0, width, height, 0x3A000000);
        drawDial(extractor, centerX, centerY, radius, options);
        extractor.centeredText(font, styled(page.label), centerX, centerY - 6, TEXT);
        String hint = page == Page.PRESET ? "CLICK LOAD / SHIFT+CLICK SAVE" : "SCROLL: CATEGORY";
        extractor.centeredText(font, styled(hint), centerX, centerY + 8, MUTED);
        extractor.centeredText(font, styled("CAMERA COMMAND DIAL"), centerX, Math.max(12, centerY - radius - 34), TEXT);
        extractor.centeredText(font, styled(pageIndex()), centerX, Math.min(height - 18, centerY + radius + 25), MUTED);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void drawDial(GuiGraphicsExtractor extractor, int centerX, int centerY, int radius, List<RadialOption> options) {
        int segments = Math.max(90, options.size() * 24);
        for (int segment = 0; segment < segments; segment++) {
            double angle = -Math.PI / 2.0 + segment * Math.PI * 2.0 / segments;
            int option = Math.floorMod((int) Math.round(segment * options.size() / (double) segments), options.size());
            int color = option == hovered ? 0xDD43D6DF : 0x663A4449;
            for (int thickness = -3; thickness <= 3; thickness++) {
                int x = centerX + (int) Math.round(Math.cos(angle) * (radius + thickness));
                int y = centerY + (int) Math.round(Math.sin(angle) * (radius + thickness));
                extractor.fill(x - 1, y - 1, x + 2, y + 2, color);
            }
        }

        for (int index = 0; index < options.size(); index++) {
            double angle = -Math.PI / 2.0 + index * Math.PI * 2.0 / options.size();
            int x = centerX + (int) Math.round(Math.cos(angle) * radius * 0.72);
            int y = centerY + (int) Math.round(Math.sin(angle) * radius * 0.72) - 4;
            RadialOption option = options.get(index);
            int color = index == hovered ? SELECTED : option.selected() ? ACCENT : TEXT;
            extractor.centeredText(font, styled(option.label()), x, y, color);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && hovered >= 0) {
            List<RadialOption> options = options();
            if (hovered < options.size()) {
                RadialOption option = options.get(hovered);
                boolean shiftDown = InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_LSHIFT)
                    || InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_RSHIFT);
                if (shiftDown && option.saveAction() != null) {
                    option.saveAction().run();
                } else {
                    option.action().run();
                }
                SnapshotCameraController.showCommandDial();
                closeMenu();
                return true;
            }
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            shiftPage(1);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0.0) {
            shiftPage(vertical > 0.0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_GRAVE || event.key() == InputConstants.KEY_ESCAPE) {
            closeMenu();
            return true;
        }
        if (event.key() == InputConstants.KEY_LEFT) {
            shiftPage(-1);
            return true;
        }
        if (event.key() == InputConstants.KEY_RIGHT) {
            shiftPage(1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<RadialOption> options() {
        CameraSettings settings = SnapshotCameraController.settings();
        List<RadialOption> options = new ArrayList<>();
        switch (page) {
            case MODE -> {
                for (ExposureMode mode : ExposureMode.values()) {
                    options.add(new RadialOption(mode.label(), settings.exposureMode() == mode,
                        () -> settings.setExposureMode(mode)));
                }
            }
            case LENS -> {
                for (LensProfile lens : LensProfile.values()) {
                    options.add(new RadialOption(lens.label(), settings.lens() == lens,
                        () -> settings.setLens(lens)));
                }
            }
            case FILTER -> {
                for (OpticalFilter filter : OpticalFilter.values()) {
                    options.add(new RadialOption(filter.label(), settings.filter() == filter,
                        () -> settings.setFilter(filter)));
                }
            }
            case ENVIRONMENT -> {
                for (String preset : SnapshotCameraController.environmentPresets()) {
                    options.add(new RadialOption(preset.toUpperCase(java.util.Locale.ROOT),
                        SnapshotCameraController.environmentPreset().equals(preset),
                        () -> SnapshotCameraController.applyEnvironmentPreset(preset)));
                }
            }
            case ASTRO -> {
                options.add(new RadialOption("WIDE SKY", settings.astrophotography() && settings.lens() == LensProfile.WIDE_PRIME,
                    settings::applyAstrophotographyPreset));
                options.add(new RadialOption("MOON", settings.astrophotography() && settings.lens() == LensProfile.TELEPHOTO_ZOOM,
                    SnapshotCameraController::focusMoon));
                options.add(new RadialOption("TRACK OFF", settings.astroTracking() == AstroTracking.OFF,
                    () -> settings.setAstroTracking(AstroTracking.OFF)));
                options.add(new RadialOption("SIDEREAL", settings.astroTracking() == AstroTracking.SIDEREAL,
                    () -> settings.setAstroTracking(AstroTracking.SIDEREAL)));
                options.add(new RadialOption("LUNAR", settings.astroTracking() == AstroTracking.LUNAR,
                    () -> settings.setAstroTracking(AstroTracking.LUNAR)));
                for (AstroStackMode mode : AstroStackMode.values()) {
                    options.add(new RadialOption(mode.label(), settings.astroStackMode() == mode,
                        () -> settings.setAstroStackMode(mode)));
                }
                options.add(new RadialOption("AUTO DARK", settings.darkFrameSubtraction(), settings::toggleDarkFrameSubtraction));
                options.add(new RadialOption("RED HUD", settings.redNightVision(), settings::toggleRedNightVision));
                options.add(new RadialOption("SKY GUIDE", SnapshotCameraController.constellationGuide(),
                    SnapshotCameraController::toggleConstellationGuide));
            }
            case SEQUENCE -> {
                options.add(new RadialOption("3 SHOTS / 2s", false,
                    () -> SnapshotCameraController.startIntervalometer(3, 2)));
                options.add(new RadialOption("5 SHOTS / 5s", false,
                    () -> SnapshotCameraController.startIntervalometer(5, 5)));
                options.add(new RadialOption("10 SHOTS / 10s", false,
                    () -> SnapshotCameraController.startIntervalometer(10, 10)));
                options.add(new RadialOption("STOP", false, SnapshotCameraController::stopIntervalometer));
            }
            case PRINT -> {
                for (PrintSize printSize : PrintSize.values()) {
                    options.add(new RadialOption(printSize.label(), settings.printSize() == printSize,
                        () -> settings.setPrintSize(printSize)));
                }
            }
            case DRIVE -> {
                for (CaptureTechnique technique : CaptureTechnique.values()) {
                    options.add(new RadialOption(technique.label(), settings.captureTechnique() == technique,
                        () -> settings.setCaptureTechnique(technique)));
                }
                for (ExposureBracket bracket : ExposureBracket.values()) {
                    options.add(new RadialOption(bracket.label(), settings.exposureBracket() == bracket,
                        () -> settings.setExposureBracket(bracket)));
                }
            }
            case PRESET -> {
                for (CameraPresetSlot slot : CameraPresetSlot.values()) {
                    options.add(new RadialOption(slot.label(), CameraPresetStore.active(slot),
                        () -> {
                            CameraPresetStore.load(slot, settings);
                            if (minecraft.player != null) {
                                minecraft.player.sendOverlayMessage(Component.literal("Loaded " + slot.label() + " camera preset"));
                            }
                        },
                        () -> {
                            CameraPresetStore.save(slot, settings);
                            if (minecraft.player != null) {
                                minecraft.player.sendOverlayMessage(Component.literal("Saved " + slot.label() + " camera preset"));
                            }
                        }));
                }
            }
        }
        return options;
    }

    private int hoveredOption(double mouseX, double mouseY, int centerX, int centerY, int radius, int count) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < radius * 0.43 || distance > radius * 1.28) {
            return -1;
        }
        double angle = Math.atan2(dy, dx) + Math.PI / 2.0;
        if (angle < 0.0) {
            angle += Math.PI * 2.0;
        }
        return Math.floorMod((int) Math.round(angle / (Math.PI * 2.0) * count), count);
    }

    private void shiftPage(int direction) {
        Page[] pages = Page.values();
        page = pages[Math.floorMod(page.ordinal() + direction, pages.length)];
    }

    private String pageIndex() {
        return (page.ordinal() + 1) + " / " + Page.values().length;
    }

    private void closeMenu() {
        minecraft.setScreenAndShow(null);
    }

    private static Component styled(String value) {
        return Component.literal(value).withStyle(style -> style.withFont(VIEWFINDER_FONT));
    }

    private enum Page {
        MODE("MODE"),
        LENS("LENS"),
        FILTER("FILTER"),
        ENVIRONMENT("ENV"),
        ASTRO("ASTRO"),
        SEQUENCE("SEQUENCE"),
        PRINT("PRINT"),
        DRIVE("DRIVE"),
        PRESET("PRESETS");

        private final String label;

        Page(String label) {
            this.label = label;
        }
    }

    private record RadialOption(String label, boolean selected, Runnable action, Runnable saveAction) {
        private RadialOption(String label, boolean selected, Runnable action) {
            this(label, selected, action, null);
        }
    }
}
