package com.luci.snapshot.client.tutorial;

import com.luci.snapshot.SnapshotInit;
import com.luci.snapshot.client.input.SnapshotKeybinds;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

final class SnapshotTutorialScreen extends Screen {
    private static final FontDescription.Resource VIEWFINDER_FONT = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "viewfinder")
    );

    private int page;
    private Button back;
    private Button next;

    SnapshotTutorialScreen() {
        super(Component.literal("Snapshot Tutorial"));
    }

    @Override
    protected void init() {
        back = addRenderableWidget(Button.builder(Component.literal("Back"), button -> setPage(page - 1))
            .bounds(width / 2 - 154, height - 34, 96, 20)
            .build());
        addRenderableWidget(Button.builder(Component.literal("Skip"), button -> SnapshotTutorial.complete(minecraft))
            .bounds(width / 2 - 48, height - 34, 96, 20)
            .build());
        next = addRenderableWidget(Button.builder(Component.literal("Next"), button -> {
            if (page == pages().size() - 1) {
                SnapshotTutorial.complete(minecraft);
            } else {
                setPage(page + 1);
            }
        }).bounds(width / 2 + 58, height - 34, 96, 20).build());
        updateButtons();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.fill(0, 0, width, height, 0xF0080A0C);
        extractor.fillGradient(0, 0, width, 54, 0xD0000000, 0x00000000);
        extractor.horizontalLine(18, width - 18, 18, 0x8843D6DF);

        int frameLeft = 24;
        int frameRight = width - 24;
        int frameTop = 48;
        int frameBottom = height - 50;
        int guide = 0x18FFFFFF;
        extractor.verticalLine(frameLeft + (frameRight - frameLeft) / 3, frameTop, frameBottom, guide);
        extractor.verticalLine(frameLeft + (frameRight - frameLeft) * 2 / 3, frameTop, frameBottom, guide);
        extractor.horizontalLine(frameLeft, frameRight, frameTop + (frameBottom - frameTop) / 3, guide);
        extractor.horizontalLine(frameLeft, frameRight, frameTop + (frameBottom - frameTop) * 2 / 3, guide);

        Page current = pages().get(page);
        extractor.centeredText(font, styled("SNAPSHOT"), width / 2, 10, 0xFFF4F6F7);
        extractor.centeredText(font, styled(current.title()), width / 2, 63, 0xFFFFD166);
        int y = 88;
        for (String line : current.lines()) {
            extractor.centeredText(font, styled(line), width / 2, y, 0xFFE5E9EB);
            y += line.isBlank() ? 15 : 13;
        }
        extractor.centeredText(font, styled((page + 1) + " / " + pages().size()), width / 2, height - 49, 0xFF9CA7AD);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private void setPage(int nextPage) {
        page = Math.max(0, Math.min(pages().size() - 1, nextPage));
        updateButtons();
    }

    private void updateButtons() {
        if (back != null) {
            back.active = page > 0;
        }
        if (next != null) {
            next.setMessage(Component.literal(page == pages().size() - 1 ? "Start shooting" : "Next"));
        }
    }

    private List<Page> pages() {
        String viewfinder = key(SnapshotKeybinds.toggle());
        String capture = key(SnapshotKeybinds.capture());
        String nextControl = key(SnapshotKeybinds.nextControl());
        String previousControl = key(SnapshotKeybinds.previousControl());
        String increase = key(SnapshotKeybinds.increase());
        String decrease = key(SnapshotKeybinds.decrease());
        String autoFocus = key(SnapshotKeybinds.autofocus());
        String hud = key(SnapshotKeybinds.toggleHudSize());
        String film = key(SnapshotKeybinds.filmProfile());
        String aspect = key(SnapshotKeybinds.aspectRatio());
        String mood = key(SnapshotKeybinds.mood());
        String lighttable = key(SnapshotKeybinds.lighttable());
        String exposureMode = key(SnapshotKeybinds.exposureMode());
        String exposureAssist = key(SnapshotKeybinds.exposureAssist());
        String astro = key(SnapshotKeybinds.astrophotography());
        String quickMenu = key(SnapshotKeybinds.quickMenu());
        String focusPoint = key(SnapshotKeybinds.focusPointSelector());
        String aeLock = key(SnapshotKeybinds.aeLock());
        String afLock = key(SnapshotKeybinds.afLock());
        return List.of(
            new Page("READY THE CAMERA", List.of(
                "Carry a Snapshot camera and photographic paper.",
                "Press " + viewfinder + " to raise or lower the viewfinder.",
                "The hotbar, hand, and crosshair disappear while composing."
            )),
            new Page("EXPOSURE TRIANGLE", List.of(
                nextControl + " / " + previousControl + " selects a setting.",
                increase + " / " + decrease + " adjusts it.",
                "Shutter controls light and motion blur.",
                "Aperture controls light and depth of field.",
                "ISO controls sensitivity and sensor grain."
            )),
            new Page("PRO CAMERA MODES", List.of(
                exposureMode + " cycles M, A, S, P, and Auto exposure.",
                quickMenu + " opens the camera command dial; scroll between pages.",
                "Auto ISO includes a maximum ISO and minimum shutter limit.",
                "Hold right mouse for momentary AF and AE lock.",
                aeLock + " latches AE-L; " + afLock + " latches AF-L.",
                exposureAssist + " cycles histogram, zebras, waveform, and false color."
            )),
            new Page("FOCUS AND LENS", List.of(
                "Scroll to zoom smoothly from 18mm to 200mm.",
                "Hold CTRL and scroll for camera roll from -30 to +30 degrees.",
                "Press " + autoFocus + " to switch between AF and manual focus.",
                focusPoint + " opens the nine-point AF selector; click your subject.",
                "Manual focus enables live focus peaking, never baked into the photo."
            )),
            new Page("VIEWFINDER", List.of(
                "The thirds grid is evenly spaced for precise composition.",
                "White balance and focus sit at the upper-left edge.",
                "Resolution, optics, and drive mode sit upper-right.",
                "Press " + hud + " to toggle compact and expanded readouts."
            )),
            new Page("FILM AND FORMAT", List.of(
                film + " cycles Neutral, Warm 400, Muted Chrome, and Monochrome.",
                aspect + " cycles Full, 4:3, 1:1, and 2.39:1 sensor formats.",
                "The visible mask is the exact crop written to the PNG.",
                mood + " cycles curated color and contrast moods."
            )),
            new Page("LONG EXPOSURE", List.of(
                "Shutters from 1s to 30s accumulate real rendered frames.",
                "BULB opens on the first shutter press and closes on the second.",
                "Carry a Camera Tripod to lock orientation during the exposure.",
                astro + " loads a 24mm, f/1.8, ISO 1600, 15s night-sky setup.",
                "The ASTRO dial selects denoise, deep-sky, or star-trail stacking.",
                "It also provides modeled dark-signal correction, red night vision, tracking, and interval programs."
            )),
            new Page("DRIVE AND PRINT", List.of(
                "Open " + quickMenu + " and scroll to the DRIVE page.",
                "3F brackets save three exposures; HDR merges them into one image.",
                "Focus Stack sweeps near, middle, and far focus into one sharp frame.",
                "Panorama rotates through three overlapping views and stitches them.",
                "The PRINT page selects 1x1, 2x1, 2x2, or 3x2 map artwork."
            )),
            new Page("TAKE AND VIEW PHOTOS", List.of(
                "Press " + capture + " for the shutter.",
                "With default export settings, captures save to screenshots/snapshot.",
                "Optional pre-processing PNG and metadata sidecars are saved there too.",
                "The locked Photo Map displays the image in normal item frames.",
                "In singleplayer, Image2Map can automatically create the selected multi-map print.",
                "Right-click the Photograph item for an enlarged map-resolution viewer.",
                lighttable + " opens the roll: F favorite, 0-5 rate, C compare, Delete remove."
            ))
        );
    }

    private static String key(net.minecraft.client.KeyMapping mapping) {
        return "[" + mapping.getTranslatedKeyMessage().getString().toUpperCase() + "]";
    }

    private static Component styled(String value) {
        return Component.literal(value).withStyle(style -> style.withFont(VIEWFINDER_FONT));
    }

    private record Page(String title, List<String> lines) {
    }
}
