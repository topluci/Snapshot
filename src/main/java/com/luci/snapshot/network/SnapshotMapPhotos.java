package com.luci.snapshot.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

final class SnapshotMapPhotos {
    private static final int MAP_SIZE = 128;
    private static final List<PaletteEntry> PALETTE = buildPalette();

    private SnapshotMapPhotos() {
    }

    static ItemStack create(ServerPlayer player, CapturePhotoPayload payload) {
        ServerLevel level = player.level();
        ItemStack stack = MapItem.create(level, (int) player.getX(), (int) player.getZ(), (byte) 0, false, false);
        MapId mapId = stack.get(DataComponents.MAP_ID);
        MapItemSavedData data = MapItem.getSavedData(stack, level);
        if (data != null && mapId != null) {
            writeThumbnail(data, payload.thumbnail());
            level.setMapData(mapId, data.locked());
        }

        stack.set(DataComponents.CUSTOM_NAME, Component.literal(payload.title() + " - Photo Map"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal(payload.width() + "x" + payload.height() + " source"),
            Component.literal(payload.settings()),
            Component.literal(payload.pngExported() ? "PNG exported locally to screenshots/snapshot" : "Snapshot in-game map photo")
        )));
        return stack;
    }

    private static void writeThumbnail(MapItemSavedData data, byte[] thumbnail) {
        int side = thumbnailSide(thumbnail);
        if (side <= 0 || PALETTE.isEmpty()) {
            return;
        }

        for (int y = 0; y < MAP_SIZE; y++) {
            int sourceY = y * side / MAP_SIZE;
            for (int x = 0; x < MAP_SIZE; x++) {
                int sourceX = x * side / MAP_SIZE;
                int offset = (sourceY * side + sourceX) * 4;
                int alpha = thumbnail[offset + 3] & 0xFF;
                if (alpha < 16) {
                    data.setColor(x, y, (byte) 0);
                    continue;
                }

                data.setColor(
                    x,
                    y,
                    closestColor(thumbnail[offset] & 0xFF, thumbnail[offset + 1] & 0xFF, thumbnail[offset + 2] & 0xFF)
                );
            }
        }
    }

    private static int thumbnailSide(byte[] thumbnail) {
        int pixels = thumbnail.length / 4;
        int side = (int) Math.sqrt(pixels);
        return side * side == pixels ? side : 0;
    }

    private static byte closestColor(int red, int green, int blue) {
        PaletteEntry best = PALETTE.getFirst();
        int bestDistance = Integer.MAX_VALUE;
        for (PaletteEntry entry : PALETTE) {
            int redDelta = red - entry.red();
            int greenDelta = green - entry.green();
            int blueDelta = blue - entry.blue();
            int distance = redDelta * redDelta + greenDelta * greenDelta + blueDelta * blueDelta;
            if (distance < bestDistance) {
                best = entry;
                bestDistance = distance;
            }
        }
        return best.packedId();
    }

    private static List<PaletteEntry> buildPalette() {
        List<PaletteEntry> entries = new ArrayList<>();
        for (int id = 1; id < 64; id++) {
            MapColor color = MapColor.byId(id);
            if (color == null || color == MapColor.NONE) {
                continue;
            }

            for (MapColor.Brightness brightness : MapColor.Brightness.values()) {
                int argb = color.calculateARGBColor(brightness);
                entries.add(new PaletteEntry(
                    color.getPackedId(brightness),
                    (argb >> 16) & 0xFF,
                    (argb >> 8) & 0xFF,
                    argb & 0xFF
                ));
            }
        }
        return List.copyOf(entries);
    }

    private record PaletteEntry(byte packedId, int red, int green, int blue) {
    }
}
