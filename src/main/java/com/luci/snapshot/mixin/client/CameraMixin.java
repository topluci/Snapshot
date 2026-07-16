package com.luci.snapshot.mixin.client;

import com.luci.snapshot.client.camera.SnapshotCameraController;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void snapshot$applyLensFov(float partialTick, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(SnapshotCameraController.lensFov(cir.getReturnValue()));
    }

    @Inject(method = "getViewRotationMatrix", at = @At("RETURN"))
    private void snapshot$applyCameraRoll(Matrix4f destination, CallbackInfoReturnable<Matrix4f> cir) {
        float roll = SnapshotCameraController.cameraRollRadians();
        if (roll != 0.0F) {
            cir.getReturnValue().rotateZ(roll);
        }
    }
}
