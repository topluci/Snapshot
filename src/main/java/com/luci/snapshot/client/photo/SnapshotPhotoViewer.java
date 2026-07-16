package com.luci.snapshot.client.photo;

import com.luci.snapshot.client.camera.SnapshotCameraController;
import com.luci.snapshot.item.SnapshotItems;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public final class SnapshotPhotoViewer {
    private SnapshotPhotoViewer() {
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide() && stack.getItem() == SnapshotItems.CAMERA) {
                SnapshotCameraController.toggle();
                return InteractionResult.SUCCESS;
            }
            if (!level.isClientSide() || stack.getItem() != SnapshotItems.PHOTOGRAPH) {
                return InteractionResult.PASS;
            }
            Minecraft.getInstance().setScreenAndShow(new SnapshotPhotographScreen(stack.copy()));
            return InteractionResult.SUCCESS;
        });
    }

    public static boolean openLatestReview(Minecraft client) {
        Path directory = client.gameDirectory.toPath().resolve("screenshots/snapshot");
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (var paths = Files.list(directory)) {
            Path latest = paths
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    return name.endsWith(".png") && !name.endsWith(".source.png");
                })
                .max(Comparator.comparingLong(SnapshotPhotoViewer::modifiedTime))
                .orElse(null);
            if (latest == null) {
                return false;
            }
            client.setScreenAndShow(new SnapshotImageReviewScreen(latest, client.gui.screen()));
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static long modifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return Long.MIN_VALUE;
        }
    }
}
