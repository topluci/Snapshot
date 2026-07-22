package com.luci.snapshot.mixin.client;

import com.luci.snapshot.client.camera.SnapshotCameraController;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void snapshot$hideHandsInViewfinder(CameraRenderState cameraRenderState, float tickProgress, Matrix4fc projectionMatrix, CallbackInfo ci) {
        if (SnapshotCameraController.viewfinderRendering()) {
            ci.cancel();
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void snapshot$steadyViewfinder(CameraRenderState cameraRenderState, PoseStack poseStack, CallbackInfo ci) {
        if (SnapshotCameraController.viewfinderRendering()) {
            ci.cancel();
        }
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V",
            shift = At.Shift.BEFORE
        )
    )
    private void snapshot$captureCleanWorldFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        GameRenderer renderer = (GameRenderer) (Object) this;
        SnapshotCameraController.captureRenderedFrame(renderer.mainRenderTarget());
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/PostChain;process(Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;)V",
            shift = At.Shift.BEFORE
        ),
        require = 0
    )
    private void snapshot$prepareLiveOptics(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        SnapshotCameraController.prepareLiveOpticsFrame();
    }
}
