package com.luci.snapshot.client.photo;

import com.luci.snapshot.SnapshotInit;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class SnapshotTrashScreen extends Screen {
    private static final int THUMBNAIL_LIMIT = 320;

    private final Screen previous;
    private final List<Path> photos = new ArrayList<>();
    private final List<Path> pagePhotos = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private final Map<Path, SnapshotTextureLoader.TextureHandle> textures = new HashMap<>();
    private Path selected;
    private int page;
    private int loadGeneration;
    private Button restoreButton;
    private Button deleteButton;
    private Button emptyButton;

    SnapshotTrashScreen(Screen previous) {
        super(Component.literal("Snapshot Trash"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        scan();
        page = Math.min(page, maxPage());
        loadPage();
        restoreButton = addRenderableWidget(Button.builder(Component.literal("Restore"), button -> restoreSelected())
            .bounds(18, height - 26, 82, 20).build());
        deleteButton = addRenderableWidget(Button.builder(Component.literal("Delete Forever..."), button -> confirmDeleteSelected())
            .bounds(108, height - 26, 126, 20).build());
        emptyButton = addRenderableWidget(Button.builder(Component.literal("Empty Trash..."), button -> confirmEmpty())
            .bounds(width - 122, height - 26, 104, 20).build());
        updateButtons();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.fill(0, 0, width, height, 0xF5080A0C);
        extractor.fillGradient(0, 0, width, 42, 0xFF000000, 0x00000000);
        extractor.centeredText(font, SnapshotLighttableScreen.styled("CAMERA ROLL  /  TRASH"), width / 2, 12, 0xFFF4F6F7);
        extractor.horizontalLine(18, width - 18, 30, 0x5543D6DF);
        tiles.clear();
        if (photos.isEmpty()) {
            extractor.centeredText(font, SnapshotLighttableScreen.styled("TRASH IS EMPTY"),
                width / 2, height / 2, 0xFF9CA7AD);
        } else {
            drawGrid(extractor, mouseX, mouseY);
        }
        extractor.centeredText(font, SnapshotLighttableScreen.styled((page + 1) + " / " + (maxPage() + 1)),
            width / 2, height - 19, 0xFF9CA7AD);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void drawGrid(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        int columns = width < 560 ? 2 : 3;
        int rows = (int) Math.ceil(pagePhotos.size() / (double) columns);
        int gap = 8;
        int margin = 18;
        int top = 42;
        int bottom = 54;
        int tileWidth = Math.max(80, (width - margin * 2 - gap * (columns - 1)) / columns);
        int tileHeight = Math.max(62, (height - top - bottom - gap * Math.max(0, rows - 1)) / Math.max(1, rows));
        for (int index = 0; index < pagePhotos.size(); index++) {
            Path path = pagePhotos.get(index);
            int column = index % columns;
            int row = index / columns;
            int x = margin + column * (tileWidth + gap);
            int y = top + row * (tileHeight + gap);
            Tile tile = new Tile(x, y, tileWidth, tileHeight, path);
            boolean hovered = tile.contains(mouseX, mouseY);
            boolean selectedTile = path.equals(selected);
            extractor.fill(x, y, x + tileWidth, y + tileHeight, hovered ? 0xFF182126 : 0xFF111416);
            extractor.outline(x, y, tileWidth, tileHeight,
                hovered ? 0xDD43D6DF : selectedTile ? 0xAAFFD166 : 0x553F4A50);
            SnapshotTextureLoader.TextureHandle texture = textures.get(path);
            int imageHeight = Math.max(32, tileHeight - 15);
            if (texture != null) {
                SnapshotTextureLoader.drawFitted(extractor, texture, x + 2, y + 2, tileWidth - 4, imageHeight - 3);
            } else {
                extractor.centeredText(font, SnapshotLighttableScreen.styled("LOADING"),
                    x + tileWidth / 2, y + imageHeight / 2 - 4, 0xFF657177);
            }
            extractor.centeredText(font, SnapshotLighttableScreen.styled(displayName(path)),
                x + tileWidth / 2, y + tileHeight - 11, 0xFFC6CDD1);
            tiles.add(tile);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (Tile tile : tiles) {
                if (tile.contains(event.x(), event.y())) {
                    selected = tile.path();
                    updateButtons();
                    if (doubleClick) {
                        restoreSelected();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0.0) {
            setPage(page + (vertical > 0.0 ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_M) {
            minecraft.setScreenAndShow(null);
            return true;
        }
        if (event.key() == InputConstants.KEY_LEFT || event.key() == InputConstants.KEY_PAGEUP) {
            setPage(page - 1);
            return true;
        }
        if (event.key() == InputConstants.KEY_RIGHT || event.key() == InputConstants.KEY_PAGEDOWN) {
            setPage(page + 1);
            return true;
        }
        if (event.key() == InputConstants.KEY_DELETE || event.key() == InputConstants.KEY_BACKSPACE) {
            confirmDeleteSelected();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(previous);
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

    private void restoreSelected() {
        if (selected != null && SnapshotTrashStore.restore(selected)) {
            selected = null;
            minecraft.setScreenAndShow(this);
        }
    }

    private void confirmDeleteSelected() {
        if (selected == null || !Files.isRegularFile(selected)) {
            return;
        }
        Path pending = selected;
        minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                SnapshotTrashStore.deleteForever(pending);
                selected = null;
            }
            minecraft.setScreenAndShow(this);
        }, Component.literal("Delete photograph permanently?"),
            Component.literal(pending.getFileName() + " cannot be restored after this.")));
    }

    private void confirmEmpty() {
        if (photos.isEmpty()) {
            return;
        }
        int count = photos.size();
        minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                SnapshotTrashStore.empty(minecraft.gameDirectory.toPath());
                selected = null;
                page = 0;
            }
            minecraft.setScreenAndShow(this);
        }, Component.literal("Empty Snapshot trash?"),
            Component.literal(count + " photographs will be permanently deleted.")));
    }

    private void scan() {
        photos.clear();
        photos.addAll(SnapshotTrashStore.images(minecraft.gameDirectory.toPath()));
        if (selected != null && !photos.contains(selected)) {
            selected = null;
        }
    }

    private void loadPage() {
        releaseTextures();
        int start = page * pageSize();
        int end = Math.min(photos.size(), start + pageSize());
        pagePhotos.clear();
        pagePhotos.addAll(photos.subList(start, end));
        selected = pagePhotos.isEmpty() ? null : pagePhotos.getFirst();
        updateButtons();
        int generation = loadGeneration;
        for (Path path : pagePhotos) {
            SnapshotTextureLoader.decodeAsync(path, THUMBNAIL_LIMIT, true).whenComplete((decoded, throwable) ->
                minecraft.execute(() -> finishLoad(path, generation, decoded, throwable))
            );
        }
    }

    private void finishLoad(Path path, int generation, SnapshotTextureLoader.DecodedImage decoded, Throwable throwable) {
        if (generation != loadGeneration || minecraft.gui.screen() != this) {
            if (decoded != null) {
                decoded.close();
            }
            return;
        }
        if (throwable != null) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not load trashed photograph {}.", path, throwable);
            return;
        }
        textures.put(path, SnapshotTextureLoader.register(decoded, "trash"));
    }

    private void setPage(int nextPage) {
        int clamped = Math.max(0, Math.min(maxPage(), nextPage));
        if (clamped != page) {
            page = clamped;
            loadPage();
        }
    }

    private int maxPage() {
        return Math.max(0, (photos.size() - 1) / pageSize());
    }

    private int pageSize() {
        int columns = width < 560 ? 2 : 3;
        int rows = height < 360 ? 2 : 3;
        return columns * rows;
    }

    private void updateButtons() {
        boolean hasSelection = selected != null && Files.isRegularFile(selected);
        if (restoreButton != null) {
            restoreButton.active = hasSelection;
        }
        if (deleteButton != null) {
            deleteButton.active = hasSelection;
        }
        if (emptyButton != null) {
            emptyButton.active = !photos.isEmpty();
        }
    }

    private void releaseTextures() {
        loadGeneration++;
        for (SnapshotTextureLoader.TextureHandle texture : textures.values()) {
            SnapshotTextureLoader.release(minecraft, texture);
        }
        textures.clear();
        tiles.clear();
    }

    private static String displayName(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base.replace("snapshot_", "");
    }

    private record Tile(int x, int y, int width, int height, Path path) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
