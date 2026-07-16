package com.luci.snapshot.mixin.client;

import com.luci.snapshot.client.camera.SnapshotCameraController;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void snapshot$shapeViewfinderSoundscape(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        float gain = SnapshotCameraController.soundscapeGain(sound.getSource());
        if (gain != 1.0F) {
            cir.setReturnValue(Math.min(1.0F, cir.getReturnValue() * gain));
        }
    }
}
