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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
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
    private static long lastFrameNanos;
    private static double averageFrameSeconds = 1.0 / 60.0;
    private static float adaptiveScale = 1.0F;
    private static float stableFocusDistance = Float.NaN;
    private static float stableMaximumBlur = Float.NaN;
    private static int measuredFrames;
    private static PostChain scaledTargetChain;
    private static int scaledTargetWidth;
    private static int scaledTargetHeight;

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
        lastFrameNanos = 0L;
        averageFrameSeconds = 1.0 / 60.0;
        adaptiveScale = 1.0F;
        stableFocusDistance = Float.NaN;
        stableMaximumBlur = Float.NaN;
        measuredFrames = 0;
        scaledTargetChain = null;
        scaledTargetWidth = 0;
        scaledTargetHeight = 0;
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

        configureDofTarget(client, chain, settings);
        updatePerformanceScale();
        float requestedFocusDistance = (float) settings.focusDistance();
        float focalLength = (float) settings.focalLengthPrecise();
        float aperture = (float) settings.apertureNumber();
        float requestedMaximumBlur = calculateMaximumBlur(settings, ShaderCompatibility.shaderPackActive());
        float stability = SnapshotConfig.get().depthTemporalStability / 100.0F;
        float blend = Mth.lerp(stability, 1.0F, 0.08F);
        stableFocusDistance = Float.isNaN(stableFocusDistance)
            ? requestedFocusDistance : Mth.lerp(blend, stableFocusDistance, requestedFocusDistance);
        stableMaximumBlur = Float.isNaN(stableMaximumBlur)
            ? requestedMaximumBlur : Mth.lerp(blend, stableMaximumBlur, requestedMaximumBlur);
        float focusDistance = stableFocusDistance;
        float maximumBlur = stableMaximumBlur;
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
        float manualFocus = settings.autoFocus() || SnapshotCameraController.opticalAssistsSuppressed()
            ? 0.0F : SnapshotConfig.get().focusPeakingStrength / 100.0F;
        float[] peaking = peakingColor(SnapshotConfig.get().focusPeakingPalette);
        float sampleQuality = quantizeQuality(presetQuality(settings.preset()) * adaptiveScale);
        float rain = client.level.getRainLevel(1.0F);
        float condensation = rain * (settings.mood() == MoodPreset.ETHEREAL_MIST ? 0.75F : 0.28F);
        float dust = settings.astrophotography() ? 0.03F : 0.12F;
        int hash = uniformHash(chain, focusDistance, focalLength, aperture, maximumBlur, nearPlane, farPlane,
            blades, catEye, profile, temperature, contrast, saturation, manualFocus, rain, condensation, dust,
            sampleQuality, peaking[0], peaking[1], peaking[2]);
        if (chain == preparedChain && hash == preparedUniformHash) {
            return;
        }

        for (PostPass pass : ((PostChainAccessor) chain).snapshot$passes()) {
            var uniforms = ((PostPassAccessor) pass).snapshot$customUniforms();
            if (!uniforms.containsKey(OPTICS_UNIFORM)) {
                continue;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Std140Builder builder = Std140Builder.onStack(stack, 80)
                    .putVec4(focusDistance, focalLength, aperture, maximumBlur)
                    .putVec4(nearPlane, farPlane, blades, catEye)
                    .putVec4(profile, temperature, contrast, saturation)
                    .putVec4(manualFocus, rain, condensation, dust)
                    .putVec4(sampleQuality, peaking[0], peaking[1], peaking[2]);
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
            // Iris still exposes the main depth attachment here. Retain a modest guard against
            // shader-specific depth transforms while keeping focus changes clearly visible.
            return Math.min(7.5F, maximumBlur * 0.72F);
        }
        return maximumBlur;
    }

    static int adaptiveQualityPercent() {
        return Math.round(adaptiveScale * 100.0F);
    }

    static int renderScalePercent() {
        Minecraft client = Minecraft.getInstance();
        int mainWidth = client.gameRenderer.mainRenderTarget().width;
        return mainWidth <= 0 || scaledTargetWidth <= 0
            ? 100 : Mth.clamp(Math.round(scaledTargetWidth * 100.0F / mainWidth), 1, 100);
    }

    private static void configureDofTarget(Minecraft client, PostChain chain, CameraSettings settings) {
        int divisor = SnapshotConfig.get().halfResolutionDof
            && settings.preset() != OpticsPreset.SCREENSHOT_ULTRA ? 2 : 1;
        int width = Math.max(1, client.gameRenderer.mainRenderTarget().width / divisor);
        int height = Math.max(1, client.gameRenderer.mainRenderTarget().height / divisor);
        if (chain == scaledTargetChain && width == scaledTargetWidth && height == scaledTargetHeight) {
            return;
        }

        PostChainAccessor accessor = (PostChainAccessor) chain;
        Map<Identifier, PostChainConfig.InternalTarget> targets = accessor.snapshot$internalTargets();
        Identifier optics = targets.keySet().stream()
            .filter(id -> "optics".equals(id.getPath()))
            .findFirst()
            .orElse(null);
        if (optics == null) {
            return;
        }
        PostChainConfig.InternalTarget previous = targets.get(optics);
        Map<Identifier, PostChainConfig.InternalTarget> resized = new HashMap<>(targets);
        resized.put(optics, new PostChainConfig.InternalTarget(
            Optional.of(width), Optional.of(height), previous.persistent(), previous.clearColor()
        ));
        accessor.snapshot$setInternalTargets(Map.copyOf(resized));
        scaledTargetChain = chain;
        scaledTargetWidth = width;
        scaledTargetHeight = height;
    }

    private static void updatePerformanceScale() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return;
        }
        double elapsed = (now - lastFrameNanos) / 1_000_000_000.0;
        lastFrameNanos = now;
        if (elapsed <= 0.0 || elapsed > 0.10) {
            return;
        }
        averageFrameSeconds += (elapsed - averageFrameSeconds) * 0.08;
        if (++measuredFrames < 90 || !SnapshotConfig.get().adaptiveDof) {
            adaptiveScale = 1.0F;
            return;
        }
        double framesPerSecond = 1.0 / Math.max(0.001, averageFrameSeconds);
        int target = SnapshotConfig.get().targetFps;
        if (framesPerSecond < target * 0.82) {
            adaptiveScale = Math.max(0.35F, adaptiveScale - 0.035F);
        } else if (framesPerSecond > target * 0.96) {
            adaptiveScale = Math.min(1.0F, adaptiveScale + 0.012F);
        }
    }

    private static float presetQuality(OpticsPreset preset) {
        return switch (preset) {
            case LOW -> 0.42F;
            case MEDIUM -> 0.62F;
            case ULTRA -> 0.82F;
            case SCREENSHOT_ULTRA -> 1.0F;
        };
    }

    private static float quantizeQuality(float quality) {
        return Math.round(Mth.clamp(quality, 0.20F, 1.0F) * 20.0F) / 20.0F;
    }

    private static float[] peakingColor(int palette) {
        return switch (palette) {
            case 1 -> new float[] {1.0F, 0.82F, 0.12F};
            case 2 -> new float[] {0.25F, 0.72F, 1.0F};
            case 3 -> new float[] {1.0F, 0.30F, 0.70F};
            default -> new float[] {0.18F, 0.95F, 0.86F};
        };
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
