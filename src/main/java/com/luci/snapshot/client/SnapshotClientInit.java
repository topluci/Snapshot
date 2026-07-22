package com.luci.snapshot.client;

import com.luci.snapshot.client.camera.SnapshotCameraController;
import com.luci.snapshot.client.compat.ShaderCompatibility;
import com.luci.snapshot.client.compat.Image2MapBridge;
import com.luci.snapshot.client.dev.SnapshotDevSmokeTest;
import com.luci.snapshot.client.hud.SnapshotHud;
import com.luci.snapshot.client.input.SnapshotKeybinds;
import com.luci.snapshot.client.input.SnapshotGamepadInput;
import com.luci.snapshot.client.photo.SnapshotPhotoViewer;
import com.luci.snapshot.client.tutorial.SnapshotTutorial;
import com.luci.snapshot.config.SnapshotConfig;
import com.luci.snapshot.item.SnapshotItems;
import com.luci.snapshot.network.SetPresetPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;

public class SnapshotClientInit implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SnapshotConfig.load();
        ShaderCompatibility.initialize();
        SnapshotKeybinds.register();
        SnapshotGamepadInput.register();
        SnapshotCameraController.register();
        SnapshotHud.register();
        SnapshotPhotoViewer.register();
        SnapshotTutorial.register();
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide() || player.getItemInHand(hand).getItem() != SnapshotItems.CAMERA) {
                return InteractionResult.PASS;
            }
            return SnapshotCameraController.openFromCameraItem(hand)
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        ClientPlayNetworking.registerGlobalReceiver(SetPresetPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                SnapshotCameraController.settings().setPreset(
                    com.luci.snapshot.client.camera.OpticsPreset.fromConfig(payload.preset())
                );
                context.player().sendOverlayMessage(Component.literal(
                    "Snapshot quality preset: " + SnapshotCameraController.settings().preset().label()
                ));
            })
        );
        SnapshotDevSmokeTest.registerIfRequested();
        ClientLifecycleEvents.CLIENT_STARTED.register(SnapshotKeybinds::migrateLegacyBindings);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Image2MapBridge.stop());
    }
}
