package com.luci.snapshot.command;

import com.luci.snapshot.config.SnapshotConfig;
import com.luci.snapshot.item.SnapshotItems;
import com.luci.snapshot.network.SetPresetPayload;
import com.luci.snapshot.network.SnapshotNetworking;
import java.util.Locale;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class SnapshotCommands {
    private static final String[] PRESETS = {"low", "medium", "ultra", "screenshot_ultra"};
    private static final String[] ENVIRONMENT_PRESETS = {"clear", "rain", "storm", "sunrise", "noon", "sunset", "night"};

    private SnapshotCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            Commands.literal("snapshot")
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("reload")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(context -> reload(context.getSource())))
                .then(Commands.literal("give")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("camera")
                        .executes(context -> giveCamera(context.getSource().getPlayerOrException()))))
                .then(Commands.literal("preset")
                    .then(Commands.argument("preset", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(PRESETS, builder))
                        .executes(context -> setPreset(
                            context.getSource().getPlayerOrException(),
                            StringArgumentType.getString(context, "preset")
                        ))))
                .then(Commands.literal("env")
                    .then(Commands.literal("apply")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("preset", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(ENVIRONMENT_PRESETS, builder))
                            .executes(context -> SnapshotNetworking.applyEnvironment(
                                context.getSource().getServer(),
                                context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "preset"),
                                false
                            )))))
        ));
    }

    private static int status(net.minecraft.commands.CommandSourceStack source) {
        SnapshotConfig config = SnapshotConfig.get();
        source.sendSuccess(() -> Component.literal(
            "Snapshot: camera_required=" + config.requireCamera
                + ", paper_required=" + config.requirePaper
                + ", png_export=" + config.pngExport
                + ", server_photos=" + config.serverPhotos
                + ", default_preset=" + config.defaultPreset + "."
        ), false);
        return 1;
    }

    private static int reload(net.minecraft.commands.CommandSourceStack source) {
        SnapshotConfig.load();
        source.sendSuccess(() -> Component.literal("Snapshot config reloaded."), true);
        return 1;
    }

    private static int giveCamera(ServerPlayer player) {
        ItemStack camera = new ItemStack(SnapshotItems.CAMERA);
        if (!player.addItem(camera)) {
            player.drop(camera, false);
        }
        player.sendOverlayMessage(Component.literal("Snapshot camera added."));
        return 1;
    }

    private static int setPreset(ServerPlayer player, String preset) {
        String normalized = preset.toLowerCase(Locale.ROOT);
        if (!switch (normalized) {
            case "low", "medium", "ultra", "screenshot_ultra" -> true;
            default -> false;
        }) {
            player.sendOverlayMessage(Component.literal("Unknown Snapshot quality preset: " + preset));
            return 0;
        }
        if (!ServerPlayNetworking.canSend(player, SetPresetPayload.TYPE)) {
            player.sendOverlayMessage(Component.literal("The Snapshot client mod is required to change viewfinder quality."));
            return 0;
        }
        ServerPlayNetworking.send(player, new SetPresetPayload(normalized));
        return 1;
    }
}
