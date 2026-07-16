package com.luci.snapshot.client.camera;

import com.luci.snapshot.SnapshotInit;
import com.luci.snapshot.client.compat.ShaderCompatibility;
import com.luci.snapshot.config.SnapshotConfig;
import com.luci.snapshot.mixin.client.GameRendererAccessor;
import com.luci.snapshot.mixin.client.PostChainAccessor;
import com.luci.snapshot.mixin.client.PostPassAccessor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.lwjgl.system.MemoryStack;

final class LiveFilmPipeline {
    private static final Identifier LIVE_OPTICS = id("live_optics");
    private static final Set<Identifier> SNAPSHOT_EFFECTS = Set.of(LIVE_OPTICS);
    private static final String OPTICS_UNIFORM = "SnapshotOptics";
    private static PostChain preparedChain;
    private static int preparedUniformHash;

    private LiveFilmPipeline() {
    }

    static void update(Minecraft client, FilmProfile profile) {
        if (!SnapshotConfig.get().liveFilmPipeline) {
            clear(client);
            return;
        }
        GameRenderer renderer = client.gameRenderer;
        Identifier current = renderer.currentPostEffect();
        if (!LIVE_OPTICS.equals(current) && (current == null || SNAPSHOT_EFFECTS.contains(current))) {
            ((GameRendererAccessor) renderer).snapshot$setPostEffect(LIVE_OPTICS);
            preparedChain = null;
        }
    }

    static void clear(Minecraft client) {
        Identifier current = client.gameRenderer.currentPostEffect();
        if (current != null && SNAPSHOT_EFFECTS.contains(current)) {
            client.gameRenderer.clearPostEffect();
        }
        preparedChain = null;
    }

    static boolean activeFor(Minecraft client, FilmProfile profile) {
        return LIVE_OPTICS.equals(client.gameRenderer.currentPostEffect());
    }

    static void prepareFrame(Minecraft client, CameraSettings settings) {
        if (!LIVE_OPTICS.equals(client.gameRenderer.currentPostEffect()) || client.level == null) {
            return;
        }
        PostChain chain = client.getShaderManager().getPostChain(LIVE_OPTICS, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) {
            return;
        }

        float focusDistance = (float) settings.focusDistance();
        float focalLength = (float) settings.focalLengthPrecise();
        float aperture = (float) settings.apertureNumber();
        float maximumBlur = calculateMaximumBlur(settings, ShaderCompatibility.shaderPackActive());
        float nearPlane = 0.05F;
        float farPlane = Math.max(128.0F, client.options.renderDistance().get() * 16.0F * 4.0F);
        float blades = switch (settings.lens()) {
            case PORTRAIT_PRIME, TELEPHOTO_ZOOM, MACRO_PRIME -> 9.0F;
            default -> 7.0F;
        };
        float catEye = Mth.clamp((3.2F - aperture) / 2.2F, 0.0F, 0.72F);
        float profile = settings.filmProfile().ordinal();
        float temperature = (settings.whiteBalance() + settings.mood().temperatureOffset() - 6500.0F) / 7500.0F;
        float contrast = (float) ((1.0F + settings.contrast() * 0.08F) * settings.mood().contrast());
        float saturation = (float) ((1.0F + settings.saturation() * 0.10F) * settings.mood().saturation());
        float manualFocus = settings.autoFocus() || SnapshotCameraController.opticalAssistsSuppressed() ? 0.0F : 1.0F;
        float rain = client.level.getRainLevel(1.0F);
        float condensation = rain * (settings.mood() == MoodPreset.ETHEREAL_MIST ? 0.75F : 0.28F);
        float dust = settings.astrophotography() ? 0.03F : 0.12F;
        int hash = uniformHash(chain, focusDistance, focalLength, aperture, maximumBlur, nearPlane, farPlane,
            blades, catEye, profile, temperature, contrast, saturation, manualFocus, rain, condensation, dust);
        if (chain == preparedChain && hash == preparedUniformHash) {
            return;
        }

        for (PostPass pass : ((PostChainAccessor) chain).snapshot$passes()) {
            var uniforms = ((PostPassAccessor) pass).snapshot$customUniforms();
            if (!uniforms.containsKey(OPTICS_UNIFORM)) {
                continue;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Std140Builder builder = Std140Builder.onStack(stack, 64)
                    .putVec4(focusDistance, focalLength, aperture, maximumBlur)
                    .putVec4(nearPlane, farPlane, blades, catEye)
                    .putVec4(profile, temperature, contrast, saturation)
                    .putVec4(manualFocus, rain, condensation, dust);
                GpuBuffer replacement = RenderSystem.getDevice().createBuffer(
                    () -> "Snapshot live optics uniforms",
                    GpuBuffer.USAGE_UNIFORM,
                    builder.get()
                );
                GpuBuffer previous = uniforms.put(OPTICS_UNIFORM, replacement);
                if (previous != null) {
                    previous.close();
                }
            }
        }
        preparedChain = chain;
        preparedUniformHash = hash;
    }

    static float calculateMaximumBlur(CameraSettings settings, boolean shaderPackActive) {
        if (settings.captureTechnique() == CaptureTechnique.FOCUS_STACK) {
            return 0.0F;
        }
        float aperture = (float) settings.apertureNumber();
        float apertureFactor = Mth.clamp((4.5F - aperture) / 3.3F, 0.04F, 1.0F);
        float maximumBlur = (1.5F + settings.preset().depthOfField() * 10.0F) * apertureFactor;
        if (shaderPackActive) {
            // Iris packs can remap the main depth texture. Keep live focus responsive without
            // allowing transformed depth values to soften the entire frame.
            return Math.min(3.6F, maximumBlur * 0.38F);
        }
        return maximumBlur;
    }

    private static int uniformHash(PostChain chain, float... values) {
        int hash = System.identityHashCode(chain);
        for (float value : values) {
            hash = 31 * hash + Float.floatToIntBits(value);
        }
        return hash;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(SnapshotInit.MOD_ID, path);
    }
}
