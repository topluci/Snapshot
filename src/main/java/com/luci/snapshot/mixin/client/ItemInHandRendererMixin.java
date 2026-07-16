package com.luci.snapshot.mixin.client;

import com.luci.snapshot.client.camera.SnapshotCameraController;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void snapshot$hideSubmittedHands(float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
                                             LocalPlayer player, int packedLight, CallbackInfo ci) {
        if (SnapshotCameraController.active()) {
            ci.cancel();
        }
    }
}
