package com.luci.snapshot.client.photo;

import com.luci.snapshot.client.photo.PhotographyJournal.JournalEntry;
import com.luci.snapshot.client.photo.PhotographyJournal.JournalView;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class SnapshotJournalScreen extends Screen {
    private final Screen previous;
    private JournalView journal;

    SnapshotJournalScreen(Screen previous) {
        super(Component.literal("Snapshot Photography Journal"));
        this.previous = previous;
    }

    public static void open(net.minecraft.client.Minecraft client) {
        client.setScreenAndShow(new SnapshotJournalScreen(null));
    }

    @Override
    protected void init() {
        journal = PhotographyJournal.load(minecraft);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.fill(0, 0, width, height, 0xF5080A0C);
        extractor.fillGradient(0, 0, width, 48, 0xFF000000, 0x00000000);
        extractor.centeredText(font, SnapshotLighttableScreen.styled("PHOTOGRAPHY JOURNAL"), width / 2, 12, 0xFFF4F6F7);
        extractor.horizontalLine(18, width - 18, 30, 0x5543D6DF);

        int left = Math.max(18, width / 12);
        int contentWidth = width - left * 2;
        extractor.text(font, SnapshotLighttableScreen.styled("CAPTURES  " + journal.totalCaptures()), left, 47, 0xFFE5E9EB, false);
        extractor.text(font, SnapshotLighttableScreen.styled("BEST SCORE  " + journal.bestScore()), left, 61, 0xFFFFD166, false);
        drawDiscovery(extractor, "BIOMES", journal.biomes(), left, 79, contentWidth);
        drawDiscovery(extractor, "WEATHER", journal.weather(), left, 93, contentWidth);
        drawDiscovery(extractor, "SUBJECTS", journal.subjects(), left, 107, contentWidth);
        drawDiscovery(extractor, "CELESTIAL", journal.celestial(), left, 121, contentWidth);

        int y = 149;
        extractor.text(font, SnapshotLighttableScreen.styled("RECENT ASSIGNMENTS"), left, y, 0xFF43D6DF, false);
        y += 16;
        int maximumEntries = Math.max(1, (height - y - 28) / 15);
        if (journal.recent().isEmpty()) {
            extractor.centeredText(font, SnapshotLighttableScreen.styled("NO JOURNAL ENTRIES"), width / 2, y + 18, 0xFF9CA7AD);
        } else {
            for (int index = 0; index < Math.min(maximumEntries, journal.recent().size()); index++) {
                JournalEntry entry = journal.recent().get(index);
                String objective = entry.objective().isBlank() ? "Photograph" : entry.objective();
                String location = simplify(entry.biome());
                String line = objective + "  /  " + location + "  /  SCORE " + entry.score();
                extractor.text(font, SnapshotLighttableScreen.styled(fit(line, contentWidth)), left, y, 0xFFC6CDD1, false);
                y += 15;
            }
        }

        extractor.centeredText(font, SnapshotLighttableScreen.styled("M CLOSE  |  J / ESC BACK"),
            width / 2, height - 17, 0xFF7F8B91);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void drawDiscovery(GuiGraphicsExtractor extractor, String label, List<String> values,
                               int x, int y, int maximumWidth) {
        String joined = values.isEmpty() ? "NONE" : values.stream().map(SnapshotJournalScreen::simplify)
            .limit(8).reduce((first, next) -> first + "  " + next).orElse("NONE");
        extractor.text(font, SnapshotLighttableScreen.styled(fit(label + "  " + joined, maximumWidth)),
            x, y, 0xFF9CA7AD, false);
    }

    private String fit(String value, int maximumWidth) {
        String fitted = value;
        while (fitted.length() > 4
            && font.width(SnapshotLighttableScreen.styled(fitted + "...")) > maximumWidth) {
            fitted = fitted.substring(0, fitted.length() - 1);
        }
        return fitted.equals(value) ? value : fitted.stripTrailing() + "...";
    }

    private static String simplify(String value) {
        int namespace = value.indexOf(':');
        return (namespace >= 0 ? value.substring(namespace + 1) : value).replace('_', ' ').toUpperCase(java.util.Locale.ROOT);
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
        if (event.key() == InputConstants.KEY_J) {
            minecraft.setScreenAndShow(previous);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
