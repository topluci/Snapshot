package com.luci.snapshot.client.tutorial;

import com.luci.snapshot.SnapshotInit;
import com.luci.snapshot.client.input.SnapshotKeybinds;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class SnapshotTutorial {
    private static final Path COMPLETION_MARKER = FabricLoader.getInstance().getConfigDir().resolve("snapshot-tutorial-v1.done");
    private static int countdown = -1;

    private SnapshotTutorial() {
    }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!Files.exists(COMPLETION_MARKER)) {
                countdown = 35;
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> countdown = -1);
        ClientTickEvents.END_CLIENT_TICK.register(SnapshotTutorial::tick);
    }

    public static void complete(Minecraft client) {
        countdown = -1;
        try {
            Files.createDirectories(COMPLETION_MARKER.getParent());
            Files.writeString(COMPLETION_MARKER, "Snapshot tutorial completed.\n");
        } catch (IOException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not save tutorial completion.", exception);
        }
        client.setScreenAndShow(null);
    }

    private static void tick(Minecraft client) {
        while (SnapshotKeybinds.tutorial().consumeClick()) {
            if (client.player != null && client.level != null) {
                client.setScreenAndShow(new SnapshotTutorialScreen());
            }
        }
        if (countdown < 0 || client.player == null || client.level == null) {
            return;
        }
        if (client.gui.screen() != null) {
            return;
        }
        if (--countdown <= 0) {
            countdown = -1;
            client.setScreenAndShow(new SnapshotTutorialScreen());
        }
    }
}
