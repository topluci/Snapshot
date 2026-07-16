package com.luci.snapshot.mixin.client;

import com.luci.snapshot.client.camera.SnapshotEnvironmentPreview;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelEnvironmentPreviewMixin {
    @Inject(method = "getOverworldClockTime", at = @At("RETURN"), cancellable = true)
    private void snapshot$previewClockTime(CallbackInfoReturnable<Long> cir) {
        if (((Level) (Object) this).isClientSide()) {
            cir.setReturnValue(SnapshotEnvironmentPreview.clockTime(cir.getReturnValue()));
        }
    }

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void snapshot$previewRainLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (((Level) (Object) this).isClientSide()) {
            cir.setReturnValue(SnapshotEnvironmentPreview.rainLevel(cir.getReturnValue()));
        }
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void snapshot$previewThunderLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (((Level) (Object) this).isClientSide()) {
            cir.setReturnValue(SnapshotEnvironmentPreview.thunderLevel(cir.getReturnValue()));
        }
    }
}
