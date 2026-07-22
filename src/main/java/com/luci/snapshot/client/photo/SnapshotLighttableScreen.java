package com.luci.snapshot.client.photo;

import com.google.gson.JsonObject;
import com.luci.snapshot.SnapshotInit;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public final class SnapshotLighttableScreen extends Screen {
    private static final int THUMBNAIL_LIMIT = 320;
    private static final FontDescription.Resource VIEWFINDER_FONT = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "viewfinder")
    );

    private final List<Path> allPhotos = new ArrayList<>();
    private final List<Path> photos = new ArrayList<>();
    private final List<Path> pagePhotos = new ArrayList<>();
    private final Map<Path, SnapshotTextureLoader.TextureHandle> textures = new HashMap<>();
    private final List<Tile> tiles = new ArrayList<>();
    private final Map<Path, PhotoState> states = new HashMap<>();
    private int page;
    private int loadGeneration;
    private int pendingTextures;
    private Tile hoveredTile;
    private Path selectedPhoto;
    private Path comparisonAnchor;
    private PhotoAlbum albumFilter = PhotoAlbum.ALL;
    private Button deleteButton;
    private Button deleteAllButton;
    private Button trashButton;
    private boolean trashAvailable;

    private SnapshotLighttableScreen() {
        super(Component.literal("Snapshot Camera Roll"));
    }

    public static void open(Minecraft client) {
        client.setScreenAndShow(new SnapshotLighttableScreen());
    }

    @Override
    protected void init() {
        scanPhotos();
        trashAvailable = !SnapshotTrashStore.images(minecraft.gameDirectory.toPath()).isEmpty();
        page = Math.min(page, maxPage());
        loadPage();
        deleteButton = addRenderableWidget(Button.builder(Component.literal("Trash Photo"), button -> confirmDelete(selectedPhoto))
            .bounds(18, height - 26, 92, 20)
            .build());
        trashButton = addRenderableWidget(Button.builder(Component.literal("View Trash"),
                button -> minecraft.setScreenAndShow(new SnapshotTrashScreen(this)))
            .bounds(118, height - 26, 88, 20)
            .build());
        deleteAllButton = addRenderableWidget(Button.builder(Component.literal("Trash All..."), button -> confirmDeleteAll())
            .bounds(width - 112, height - 26, 94, 20)
            .build());
        updateDeleteButtons();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.fill(0, 0, width, height, 0xF5080A0C);
        extractor.fillGradient(0, 0, width, 42, 0xFF000000, 0x00000000);
        extractor.centeredText(font, styled("CAMERA ROLL  /  " + albumFilter.label()), width / 2, 12, 0xFFF4F6F7);
        if (width >= 720) {
            extractor.text(font, styled("TAB FILTER  A FILE  J JOURNAL"), 18, 12, 0xFF7F8B91, false);
        }
        extractor.horizontalLine(18, width - 18, 30, 0x5543D6DF);

        tiles.clear();
        if (photos.isEmpty()) {
            extractor.centeredText(font, styled("NO CAPTURES"), width / 2, height / 2 - 5, 0xFF9CA7AD);
        } else {
            drawGrid(extractor, mouseX, mouseY);
        }

        String pageText = (page + 1) + " / " + (maxPage() + 1);
        extractor.centeredText(font, styled(pageText), width / 2, height - 19, 0xFF9CA7AD);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void drawGrid(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        int columns = columns();
        int rows = (int) Math.ceil(pagePhotos.size() / (double) columns);
        int gap = 8;
        int margin = 18;
        int top = 42;
        int bottom = 54;
        int tileWidth = Math.max(80, (width - margin * 2 - gap * (columns - 1)) / columns);
        int tileHeight = Math.max(62, (height - top - bottom - gap * Math.max(0, rows - 1)) / Math.max(1, rows));

        hoveredTile = null;
        for (int index = 0; index < pagePhotos.size(); index++) {
            Path path = pagePhotos.get(index);
            SnapshotTextureLoader.TextureHandle entry = textures.get(path);
            int column = index % columns;
            int row = index / columns;
            int x = margin + column * (tileWidth + gap);
            int y = top + row * (tileHeight + gap);
            int imageHeight = Math.max(32, tileHeight - 15);
            Tile tile = new Tile(x, y, tileWidth, tileHeight, path);
            boolean hovered = tile.contains(mouseX, mouseY);
            if (hovered) {
                hoveredTile = tile;
                selectedPhoto = path;
            }
            boolean selected = path.equals(selectedPhoto);
            extractor.fill(x, y, x + tileWidth, y + tileHeight, hovered ? 0xFF182126 : 0xFF111416);
            extractor.outline(x, y, tileWidth, tileHeight,
                hovered ? 0xDD43D6DF : selected ? 0xAAFFD166 : 0x553F4A50);
            if (entry != null) {
                SnapshotTextureLoader.drawFitted(extractor, entry, x + 2, y + 2, tileWidth - 4, imageHeight - 3);
            } else {
                extractor.centeredText(font, styled("LOADING"), x + tileWidth / 2,
                    y + Math.max(8, imageHeight / 2 - 4), 0xFF657177);
            }
            String label = displayLabel(path, tileWidth - 10);
            extractor.centeredText(font, styled(label), x + tileWidth / 2, y + tileHeight - 11, 0xFFC6CDD1);
            PhotoState state = states.getOrDefault(path, PhotoState.EMPTY);
            if (state.favorite()) {
                extractor.text(font, styled("*"), x + 5, y + 5, 0xFFFFD166, true);
            }
            if (state.rating() > 0) {
                extractor.text(font, styled(state.rating() + "/5"), x + tileWidth - 24, y + 5, 0xFFF4F6F7, true);
            }
            extractor.centeredText(font, styled(state.album().label()), x + tileWidth / 2, y + 5, 0xAAE5E9EB);
            if (path.equals(comparisonAnchor)) {
                extractor.horizontalLine(x + 4, x + tileWidth - 4, y + 4, 0xFFFFD166);
            }
            tiles.add(tile);
        }
        updateDeleteButtons();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (Tile tile : tiles) {
                if (tile.contains(event.x(), event.y())) {
                    selectedPhoto = tile.path();
                    minecraft.setScreenAndShow(new SnapshotImageReviewScreen(tile.path(), this));
                    return true;
                }
            }
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && hoveredTile != null) {
            PhotoMetadataStore.toggleFavorite(hoveredTile.path());
            refreshState(hoveredTile.path());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical > 0.0) {
            setPage(page - 1);
            return true;
        }
        if (vertical < 0.0) {
            setPage(page + 1);
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
        if (hoveredTile != null && event.key() >= InputConstants.KEY_0 && event.key() <= InputConstants.KEY_5) {
            PhotoMetadataStore.setRating(hoveredTile.path(), event.key() - InputConstants.KEY_0);
            refreshState(hoveredTile.path());
            return true;
        }
        if (hoveredTile != null && event.key() == InputConstants.KEY_F) {
            PhotoMetadataStore.toggleFavorite(hoveredTile.path());
            refreshState(hoveredTile.path());
            return true;
        }
        if (event.key() == InputConstants.KEY_TAB) {
            albumFilter = albumFilter.nextFilter();
            page = 0;
            applyAlbumFilter(true);
            return true;
        }
        if (hoveredTile != null && event.key() == InputConstants.KEY_A) {
            PhotoMetadataStore.cycleAlbum(hoveredTile.path());
            refreshState(hoveredTile.path());
            applyAlbumFilter(true);
            return true;
        }
        if (event.key() == InputConstants.KEY_J) {
            minecraft.setScreenAndShow(new SnapshotJournalScreen(this));
            return true;
        }
        if (hoveredTile != null && event.key() == InputConstants.KEY_C) {
            if (comparisonAnchor == null || comparisonAnchor.equals(hoveredTile.path())) {
                comparisonAnchor = comparisonAnchor == null ? hoveredTile.path() : null;
            } else {
                minecraft.setScreenAndShow(new SnapshotComparisonScreen(comparisonAnchor, hoveredTile.path(), this));
            }
            return true;
        }
        if (hoveredTile != null && (event.key() == InputConstants.KEY_DELETE || event.key() == InputConstants.KEY_BACKSPACE)) {
            confirmDelete(hoveredTile.path());
            return true;
        }
        return super.keyPressed(event);
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

    private void scanPhotos() {
        allPhotos.clear();
        photos.clear();
        states.clear();
        Path directory = minecraft.gameDirectory.toPath().resolve("screenshots/snapshot");
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var paths = Files.list(directory)) {
            List<Path> discovered = new ArrayList<>(paths
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    return name.endsWith(".png") && !name.endsWith(".source.png");
                })
                .toList());
            for (Path photo : discovered) {
                refreshState(photo);
            }
            discovered.sort(Comparator.<Path, Boolean>comparing(
                    path -> states.getOrDefault(path, PhotoState.EMPTY).favorite()).reversed()
                .thenComparing(Comparator.comparingLong(SnapshotLighttableScreen::modifiedTime).reversed()));
            allPhotos.addAll(discovered);
            if (selectedPhoto != null && !allPhotos.contains(selectedPhoto)) {
                selectedPhoto = null;
            }
            applyAlbumFilter(false);
        } catch (IOException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not scan the camera roll.", exception);
        }
    }

    private void loadPage() {
        releaseTextures();
        int start = page * pageSize();
        int end = Math.min(photos.size(), start + pageSize());
        pagePhotos.clear();
        pagePhotos.addAll(photos.subList(start, end));
        if (!pagePhotos.isEmpty() && (selectedPhoto == null || !pagePhotos.contains(selectedPhoto))) {
            selectedPhoto = pagePhotos.getFirst();
        } else if (pagePhotos.isEmpty()) {
            selectedPhoto = null;
        }
        updateDeleteButtons();
        int generation = loadGeneration;
        pendingTextures = pagePhotos.size();
        for (int index = start; index < end; index++) {
            Path path = photos.get(index);
            SnapshotTextureLoader.decodeAsync(path, THUMBNAIL_LIMIT, true).whenComplete((decoded, throwable) ->
                minecraft.execute(() -> finishTextureLoad(path, generation, decoded, throwable))
            );
        }
    }

    private void finishTextureLoad(Path path, int generation, SnapshotTextureLoader.DecodedImage decoded,
                                   Throwable throwable) {
        if (generation != loadGeneration || minecraft.gui.screen() != this) {
            if (decoded != null) {
                decoded.close();
            }
            return;
        }
        pendingTextures = Math.max(0, pendingTextures - 1);
        if (throwable != null) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not load camera roll image {}.", path, throwable);
            return;
        }
        textures.put(path, SnapshotTextureLoader.register(decoded, "lighttable"));
    }

    private void refreshState(Path path) {
        states.put(path, PhotoState.fromMetadata(PhotoMetadataStore.read(path)));
    }

    private void applyAlbumFilter(boolean reload) {
        photos.clear();
        for (Path photo : allPhotos) {
            PhotoState state = states.getOrDefault(photo, PhotoState.EMPTY);
            if (albumFilter == PhotoAlbum.ALL || state.album() == albumFilter) {
                photos.add(photo);
            }
        }
        page = Math.min(page, maxPage());
        if (reload) {
            loadPage();
        }
    }

    private void confirmDelete(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        String name = path.getFileName().toString();
        minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                SnapshotTrashStore.moveToTrash(path);
                selectedPhoto = null;
            }
            minecraft.setScreenAndShow(this);
        }, Component.literal("Move photograph to trash?"),
            Component.literal(name + " can be restored from Camera Roll / Trash.")));
    }

    private void confirmDeleteAll() {
        if (allPhotos.isEmpty()) {
            return;
        }
        List<Path> pendingDeletion = List.copyOf(allPhotos);
        minecraft.setScreenAndShow(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                SnapshotTrashStore.moveAllToTrash(pendingDeletion);
                selectedPhoto = null;
                page = 0;
            }
            minecraft.setScreenAndShow(this);
        }, Component.literal("Move every photograph to trash?"),
            Component.literal(pendingDeletion.size() + " photographs can be restored until trash is emptied.")));
    }

    private void updateDeleteButtons() {
        if (deleteButton != null) {
            deleteButton.active = selectedPhoto != null && Files.isRegularFile(selectedPhoto);
        }
        if (deleteAllButton != null) {
            deleteAllButton.active = !allPhotos.isEmpty();
        }
        if (trashButton != null) {
            trashButton.active = trashAvailable;
        }
    }

    private void releaseTextures() {
        loadGeneration++;
        for (SnapshotTextureLoader.TextureHandle entry : textures.values()) {
            SnapshotTextureLoader.release(minecraft, entry);
        }
        textures.clear();
        tiles.clear();
        pendingTextures = 0;
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

    private static long modifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private int columns() {
        return width < 560 ? 2 : 3;
    }

    private int pageSize() {
        int rows = height < 360 ? 2 : 3;
        return columns() * rows;
    }

    private String displayLabel(Path path, int maximumWidth) {
        String base = stripExtension(path.getFileName().toString());
        String value = base.replace("snapshot_", "");
        String type = "";
        if (value.endsWith("_FOCUS_STACK")) {
            type = "  FOCUS STACK";
        } else if (value.endsWith("_PANORAMA")) {
            type = "  PANORAMA";
        } else if (value.endsWith("_HDR")) {
            type = "  HDR";
        }
        if (value.length() >= 16 && Character.isDigit(value.charAt(0))) {
            value = value.substring(5, 10) + " " + value.substring(11, 16).replace('.', ':') + type;
        }
        String fitted = value;
        while (fitted.length() > 4 && font.width(styled(fitted + "...")) > maximumWidth) {
            fitted = fitted.substring(0, fitted.length() - 1);
        }
        return fitted.equals(value) ? value : fitted.stripTrailing() + "...";
    }

    static Component styled(String value) {
        return Component.literal(value).withStyle(style -> style.withFont(VIEWFINDER_FONT));
    }

    private record Tile(int x, int y, int width, int height, Path path) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record PhotoState(boolean favorite, int rating, PhotoAlbum album) {
        private static final PhotoState EMPTY = new PhotoState(false, 0, PhotoAlbum.UNSORTED);

        private static PhotoState fromMetadata(JsonObject metadata) {
            boolean favorite = metadata.has("favorite") && metadata.get("favorite").getAsBoolean();
            int rating = metadata.has("rating") ? Math.max(0, Math.min(5, metadata.get("rating").getAsInt())) : 0;
            return new PhotoState(favorite, rating, PhotoAlbum.fromMetadata(metadata));
        }
    }
}
