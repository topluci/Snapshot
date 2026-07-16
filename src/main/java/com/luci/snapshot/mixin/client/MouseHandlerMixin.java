package com.luci.snapshot.mixin.client;

import com.luci.snapshot.client.camera.SnapshotCameraController;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void snapshot$cameraMouseControls(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (SnapshotCameraController.handleMouseButton(button.button(), action)) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void snapshot$zoomLens(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (SnapshotCameraController.handleZoomScroll(vertical)) {
            ci.cancel();
        }
    }
}
