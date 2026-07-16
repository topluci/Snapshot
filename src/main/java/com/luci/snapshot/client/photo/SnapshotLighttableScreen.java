package com.luci.snapshot.client.photo;

import com.luci.snapshot.SnapshotInit;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public final class SnapshotLighttableScreen extends Screen {
    private static final int THUMBNAIL_LIMIT = 512;
    private static final FontDescription.Resource VIEWFINDER_FONT = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, "viewfinder")
    );

    private final List<Path> allPhotos = new ArrayList<>();
    private final List<Path> photos = new ArrayList<>();
    private final List<TextureEntry> textures = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private final Map<Path, PhotoState> states = new HashMap<>();
    private int page;
    private Tile hoveredTile;
    private Path comparisonAnchor;
    private PhotoAlbum albumFilter = PhotoAlbum.ALL;

    private SnapshotLighttableScreen() {
        super(Component.literal("Snapshot Camera Roll"));
    }

    public static void open(Minecraft client) {
        client.setScreenAndShow(new SnapshotLighttableScreen());
    }

    @Override
    protected void init() {
        scanPhotos();
        page = Math.min(page, maxPage());
        loadPage();
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
        if (textures.isEmpty()) {
            extractor.centeredText(font, styled("NO CAPTURES"), width / 2, height / 2 - 5, 0xFF9CA7AD);
        } else {
            drawGrid(extractor, mouseX, mouseY);
        }

        String pageText = (page + 1) + " / " + (maxPage() + 1);
        extractor.centeredText(font, styled(pageText), width / 2, height - 17, 0xFF9CA7AD);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void drawGrid(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        int columns = columns();
        int rows = (int) Math.ceil(textures.size() / (double) columns);
        int gap = 8;
        int margin = 18;
        int top = 42;
        int bottom = 28;
        int tileWidth = Math.max(80, (width - margin * 2 - gap * (columns - 1)) / columns);
        int tileHeight = Math.max(62, (height - top - bottom - gap * Math.max(0, rows - 1)) / Math.max(1, rows));

        hoveredTile = null;
        for (int index = 0; index < textures.size(); index++) {
            TextureEntry entry = textures.get(index);
            int column = index % columns;
            int row = index / columns;
            int x = margin + column * (tileWidth + gap);
            int y = top + row * (tileHeight + gap);
            int imageHeight = Math.max(32, tileHeight - 15);
            Tile tile = new Tile(x, y, tileWidth, tileHeight, entry.path());
            boolean hovered = tile.contains(mouseX, mouseY);
            if (hovered) {
                hoveredTile = tile;
            }
            extractor.fill(x, y, x + tileWidth, y + tileHeight, hovered ? 0xFF182126 : 0xFF111416);
            extractor.outline(x, y, tileWidth, tileHeight, hovered ? 0xAA43D6DF : 0x553F4A50);
            drawFitted(extractor, entry, x + 2, y + 2, tileWidth - 4, imageHeight - 3);
            String label = displayLabel(entry.path(), tileWidth - 10);
            extractor.centeredText(font, styled(label), x + tileWidth / 2, y + tileHeight - 11, 0xFFC6CDD1);
            PhotoState state = states.getOrDefault(entry.path(), PhotoState.EMPTY);
            if (state.favorite()) {
                extractor.text(font, styled("*"), x + 5, y + 5, 0xFFFFD166, true);
            }
            if (state.rating() > 0) {
                extractor.text(font, styled(state.rating() + "/5"), x + tileWidth - 24, y + 5, 0xFFF4F6F7, true);
            }
            extractor.centeredText(font, styled(state.album().label()), x + tileWidth / 2, y + 5, 0xAAE5E9EB);
            if (entry.path().equals(comparisonAnchor)) {
                extractor.horizontalLine(x + 4, x + tileWidth - 4, y + 4, 0xFFFFD166);
            }
            tiles.add(tile);
        }
    }

    private static void drawFitted(GuiGraphicsExtractor extractor, TextureEntry entry, int x, int y, int width, int height) {
        double scale = Math.min(width / (double) entry.width(), height / (double) entry.height());
        int drawWidth = Math.max(1, (int) Math.round(entry.width() * scale));
        int drawHeight = Math.max(1, (int) Math.round(entry.height() * scale));
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;
        extractor.blit(RenderPipelines.GUI_TEXTURED, entry.id(), drawX, drawY, 0.0F, 0.0F,
            drawWidth, drawHeight, entry.width(), entry.height());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (Tile tile : tiles) {
                if (tile.contains(event.x(), event.y())) {
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
            PhotoMetadataStore.delete(hoveredTile.path());
            scanPhotos();
            page = Math.min(page, maxPage());
            loadPage();
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
            allPhotos.addAll(paths
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    return name.endsWith(".png") && !name.endsWith(".source.png");
                })
                .sorted(Comparator.<Path, Boolean>comparing(PhotoMetadataStore::favorite).reversed()
                    .thenComparing(Comparator.comparingLong(SnapshotLighttableScreen::modifiedTime).reversed()))
                .toList());
            for (Path photo : allPhotos) {
                refreshState(photo);
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
        for (int index = start; index < end; index++) {
            Path path = photos.get(index);
            try {
                textures.add(loadTexture(path, "lighttable"));
            } catch (IOException exception) {
                SnapshotInit.LOGGER.warn("[Snapshot] Could not load camera roll image {}.", path, exception);
            }
        }
    }

    private void refreshState(Path path) {
        states.put(path, new PhotoState(PhotoMetadataStore.favorite(path), PhotoMetadataStore.rating(path),
            PhotoMetadataStore.album(path)));
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

    static TextureEntry loadTexture(Path path, String group) throws IOException {
        NativeImage source;
        try (InputStream stream = Files.newInputStream(path)) {
            source = NativeImage.read(stream);
        }

        NativeImage pixels = source;
        int largest = Math.max(source.getWidth(), source.getHeight());
        int limit = "review".equals(group) ? 2048 : THUMBNAIL_LIMIT;
        if (largest > limit) {
            double scale = limit / (double) largest;
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            pixels = new NativeImage(width, height, false);
            source.resizeSubRectTo(0, 0, source.getWidth(), source.getHeight(), pixels);
            source.close();
        }

        String hash = Integer.toUnsignedString(path.toAbsolutePath().toString().hashCode());
        Identifier id = Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, group + "/" + hash);
        DynamicTexture texture = new DynamicTexture(() -> "Snapshot " + group + " " + path.getFileName(), pixels);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        texture.upload();
        return new TextureEntry(path, id, pixels.getWidth(), pixels.getHeight());
    }

    private void releaseTextures() {
        for (TextureEntry entry : textures) {
            minecraft.getTextureManager().release(entry.id());
        }
        textures.clear();
        tiles.clear();
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

    record TextureEntry(Path path, Identifier id, int width, int height) {
    }

    private record Tile(int x, int y, int width, int height, Path path) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record PhotoState(boolean favorite, int rating, PhotoAlbum album) {
        private static final PhotoState EMPTY = new PhotoState(false, 0, PhotoAlbum.UNSORTED);
    }
}
