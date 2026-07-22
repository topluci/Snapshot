package com.luci.snapshot.network;

import com.luci.snapshot.config.SnapshotConfig;
import com.luci.snapshot.item.PhotographData;
import com.luci.snapshot.item.SnapshotItems;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.saveddata.maps.MapId;

public final class SnapshotNetworking {
    private SnapshotNetworking() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay().register(CapturePhotoPayload.TYPE, CapturePhotoPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ApplyEnvironmentPayload.TYPE, ApplyEnvironmentPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SetPresetPayload.TYPE, SetPresetPayload.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(CapturePhotoPayload.TYPE, (payload, context) ->
            context.server().execute(() -> handleCapture(context.player(), payload))
        );
        ServerPlayNetworking.registerGlobalReceiver(ApplyEnvironmentPayload.TYPE, (payload, context) ->
            context.server().execute(() -> applyEnvironment(context.server(), context.player(), payload.preset(), true))
        );
    }

    private static void handleCapture(ServerPlayer player, CapturePhotoPayload payload) {
        if (!canUseCamera(player)) {
            player.sendOverlayMessage(Component.literal("Snapshot capture requires a camera."));
            return;
        }

        if (!consumePaper(player)) {
            player.sendOverlayMessage(Component.literal("Snapshot capture requires photographic paper."));
            return;
        }

        if (!SnapshotConfig.get().serverPhotos && !SnapshotConfig.get().mapPhotos) {
            player.sendOverlayMessage(Component.literal("Snapshot exported PNG locally."));
            return;
        }

        SnapshotMapPhotos.CreatedMap mapPhoto = SnapshotMapPhotos.create(player, payload);
        if (SnapshotConfig.get().mapPhotos) {
            giveOrDrop(player, mapPhoto.stack());
        }

        if (SnapshotConfig.get().serverPhotos) {
            giveOrDrop(player, createPhotograph(payload, mapPhoto.mapId(), mapPhoto.colors()));
        }
        SnapshotMapPhotos.syncTo(player, mapPhoto);
        player.sendOverlayMessage(Component.literal("Snapshot captured: " + payload.title()));
    }

    private static boolean canUseCamera(ServerPlayer player) {
        if (!SnapshotConfig.get().requireCamera || player.hasInfiniteMaterials()) {
            return true;
        }
        return hasItem(player, SnapshotItems.CAMERA);
    }

    private static boolean consumePaper(ServerPlayer player) {
        if (!SnapshotConfig.get().requirePaper || player.hasInfiniteMaterials()) {
            return true;
        }

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() == SnapshotItems.PHOTOGRAPHIC_PAPER) {
                stack.shrink(1);
                player.getInventory().setChanged();
                return true;
            }
        }
        return false;
    }

    private static boolean hasItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).getItem() == item) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack createPhotograph(CapturePhotoPayload payload, MapId mapId, byte[] colors) {
        ItemStack stack = new ItemStack(SnapshotItems.PHOTOGRAPH);
        if (mapId != null) {
            stack.set(DataComponents.MAP_ID, mapId);
        }
        PhotographData.apply(stack, payload.title(), colors);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(payload.title() + " - Photograph"));
        List<Component> lore = new java.util.ArrayList<>(List.of(
            Component.literal(payload.width() + "x" + payload.height() + " source"),
            Component.literal(payload.settings()),
            Component.literal("Right-click to view")
        ));
        if (payload.pngExported()) {
            lore.add(Component.literal("PNG exported locally to screenshots/snapshot"));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }

    public static int applyEnvironment(MinecraftServer server, ServerPlayer player, String preset, boolean fromClient) {
        if (fromClient && !canApplyEnvironment(server, player)) {
            player.sendOverlayMessage(Component.literal(
                "Snapshot environment changes require Creative mode or cheats/operator permission."
            ));
            return 0;
        }

        ServerLevel level = player.level();
        String normalized = preset.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "clear" -> server.setWeatherParameters(6000, 0, false, false);
            case "rain" -> server.setWeatherParameters(0, 6000, true, false);
            case "storm" -> server.setWeatherParameters(0, 6000, true, true);
            case "sunrise" -> setGameTime(level, 23000L);
            case "noon" -> setGameTime(level, 6000L);
            case "sunset" -> setGameTime(level, 12000L);
            case "night" -> setGameTime(level, 18000L);
            default -> {
                player.sendOverlayMessage(Component.literal("Unknown Snapshot environment preset: " + preset));
                return 0;
            }
        }

        player.sendOverlayMessage(Component.literal("Snapshot environment applied: " + normalized));
        return 1;
    }

    private static void setGameTime(ServerLevel level, long dayTime) {
        long current = level.getOverworldClockTime();
        long startOfDay = current - Math.floorMod(current, 24000L);
        level.registryAccess().get(WorldClocks.OVERWORLD)
            .ifPresent(clock -> level.clockManager().setTotalTicks(clock, startOfDay + dayTime));
    }

    private static boolean canApplyEnvironment(MinecraftServer server, ServerPlayer player) {
        NameAndId nameAndId = new NameAndId(player.getGameProfile());
        int configuredLevel = Math.max(2, SnapshotConfig.get().environmentPermissionLevel);
        return player.hasInfiniteMaterials()
            || server.getProfilePermissions(nameAndId).level().isEqualOrHigherThan(permissionForLevel(configuredLevel));
    }

    static PermissionLevel permissionForLevel(int level) {
        return switch (level) {
            case 0 -> PermissionLevel.ALL;
            case 1 -> PermissionLevel.MODERATORS;
            case 3 -> PermissionLevel.ADMINS;
            case 4 -> PermissionLevel.OWNERS;
            default -> PermissionLevel.GAMEMASTERS;
        };
    }
}
