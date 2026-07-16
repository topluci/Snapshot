package com.luci.snapshot.client.camera;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.luci.snapshot.SnapshotInit;
import com.luci.snapshot.client.compat.ShaderCompatibility;
import com.luci.snapshot.client.dev.SnapshotDevSmokeTest;
import com.luci.snapshot.client.compat.Image2MapBridge;
import com.luci.snapshot.client.input.SnapshotKeybinds;
import com.luci.snapshot.client.photo.PhotographyJournal;
import com.luci.snapshot.client.photo.SnapshotLighttableScreen;
import com.luci.snapshot.config.SnapshotConfig;
import com.luci.snapshot.item.SnapshotItems;
import com.luci.snapshot.network.ApplyEnvironmentPayload;
import com.luci.snapshot.network.CapturePhotoPayload;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SnapshotCameraController {
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss.SSS");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final CameraSettings SETTINGS = new CameraSettings();
    private static final String[] ENVIRONMENT_PRESETS = {"clear", "sunrise", "noon", "sunset", "rain", "storm", "night"};

    private static boolean active;
    private static boolean hudExpanded;
    private static boolean focusLocked;
    private static boolean previousFocusLocked;
    private static Integer previousFov;
    private static Double previousSensitivity;
    private static boolean previousSmoothCamera;
    private static double currentFov;
    private static long lastFovUpdateNanos;
    private static double adaptedExposureStops;
    private static int flashTicks;
    private static int shutterTicks;
    private static int pendingBurstShots;
    private static int burstCooldown;
    private static int environmentIndex;
    private static int particleCooldown;
    private static int analysisCooldown;
    private static boolean analysisRequested;
    private static boolean analysisInFlight;
    private static boolean halfPressActive;
    private static boolean aeLocked;
    private static boolean aeLockLatched;
    private static boolean afLockLatched;
    private static int commandDialTicks;
    private static int intervalShotsRemaining;
    private static int intervalShotsTotal;
    private static int intervalDelayTicks;
    private static int intervalPeriodTicks;
    private static boolean constellationGuide;
    private static double requiredExposureStops;
    private static double lockedRequiredExposureStops;
    private static ExposureAnalysis exposureAnalysis;
    private static CaptureRequest pendingCapture;
    private static LongExposureAccumulator longExposure;
    private static PanoramaSession panorama;
    private static FocusStackSession focusStack;
    private static float lastYaw;
    private static float lastPitch;
    private static float yawVelocity;
    private static float pitchVelocity;

    private SnapshotCameraController() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SnapshotCameraController::tick);
    }

    public static boolean active() {
        return active;
    }

    public static boolean hudExpanded() {
        return hudExpanded;
    }

    public static boolean focusLocked() {
        return focusLocked;
    }

    public static CameraSettings settings() {
        return SETTINGS;
    }

    public static int flashTicks() {
        return flashTicks;
    }

    public static int shutterTicks() {
        return shutterTicks;
    }

    public static boolean longExposureActive() {
        return longExposure != null;
    }

    public static double longExposureProgress() {
        return longExposure == null ? 0.0 : longExposure.progress(System.nanoTime());
    }

    public static String longExposureLabel() {
        if (longExposure == null) {
            return "";
        }
        return String.format(java.util.Locale.ROOT, "%s  %.1fs  %d FRAMES%s",
            longExposure.bulb() ? "BULB" : "EXPOSING", longExposure.elapsedSeconds(System.nanoTime()), longExposure.samples(),
            longExposure.stabilized()
                ? "  TRIPOD" + (longExposure.settings().astroTracking() == AstroTracking.OFF
                    ? "" : "  " + longExposure.settings().astroTracking().label())
                : "");
    }

    public static ExposureAnalysis exposureAnalysis() {
        return exposureAnalysis;
    }

    public static boolean aeLocked() {
        return aeLocked;
    }

    public static boolean afLocked() {
        return afLockLatched || (halfPressActive && focusLocked);
    }

    public static int commandDialTicks() {
        return commandDialTicks;
    }

    public static void showCommandDial() {
        commandDialTicks = 30;
    }

    public static void requestAutofocus() {
        Minecraft client = Minecraft.getInstance();
        afLockLatched = false;
        focusLocked = false;
        previousFocusLocked = false;
        if (active && client.player != null && SETTINGS.autoFocus()) {
            updateAutoFocus(client);
        }
    }

    public static boolean constellationGuide() {
        return constellationGuide;
    }

    public static void toggleConstellationGuide() {
        constellationGuide = !constellationGuide;
    }

    public static void focusMoon() {
        SETTINGS.applyMoonPreset();
        focusLocked = true;
        afLockLatched = true;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.18F, 1.82F);
            client.player.sendOverlayMessage(Component.literal("Moon focus locked at infinity  |  200mm lunar tracking"));
        }
    }

    public static void startIntervalometer(int shots, int seconds) {
        intervalShotsRemaining = Math.max(1, Math.min(99, shots));
        intervalShotsTotal = intervalShotsRemaining;
        intervalPeriodTicks = Math.max(1, seconds) * 20;
        intervalDelayTicks = 0;
        SETTINGS.setIntervalSequence(intervalShotsTotal, Math.max(1, seconds));
    }

    public static void stopIntervalometer() {
        intervalShotsRemaining = 0;
        intervalShotsTotal = 0;
        intervalDelayTicks = 0;
        SETTINGS.setIntervalSequence(0, 0);
    }

    public static String intervalometerLabel() {
        return intervalShotsRemaining <= 0 ? "" : "INT "
            + (intervalShotsTotal - intervalShotsRemaining) + "/" + intervalShotsTotal
            + "  NEXT " + Math.max(0, intervalDelayTicks) / 20.0F + "s";
    }

    public static boolean liveFilmActive() {
        return active && LiveFilmPipeline.activeFor(Minecraft.getInstance(), SETTINGS.filmProfile());
    }

    public static void prepareLiveOpticsFrame() {
        if (active) {
            LiveFilmPipeline.prepareFrame(Minecraft.getInstance(), SETTINGS);
        }
    }

    static boolean opticalAssistsSuppressed() {
        return pendingCapture != null || longExposure != null || panorama != null || focusStack != null;
    }

    public static boolean captureInProgress() {
        return pendingCapture != null || longExposure != null || panorama != null || focusStack != null
            || intervalShotsRemaining > 0;
    }

    public static void triggerCapture() {
        Minecraft client = Minecraft.getInstance();
        if (active && client.player != null) {
            queueCapture(client);
        }
    }

    public static String environmentPreset() {
        return ENVIRONMENT_PRESETS[environmentIndex];
    }

    public static String[] environmentPresets() {
        return ENVIRONMENT_PRESETS.clone();
    }

    public static void applyEnvironmentPreset(String preset) {
        for (int index = 0; index < ENVIRONMENT_PRESETS.length; index++) {
            if (ENVIRONMENT_PRESETS[index].equals(preset)) {
                environmentIndex = index;
                break;
            }
        }
        if (ClientPlayNetworking.canSend(ApplyEnvironmentPayload.TYPE)) {
            ClientPlayNetworking.send(new ApplyEnvironmentPayload(preset));
        }
    }

    public static float lensFov(float vanillaFov) {
        if (!active) {
            return vanillaFov;
        }

        long now = System.nanoTime();
        double elapsed = lastFovUpdateNanos == 0L ? 0.0 : Math.min(0.05, (now - lastFovUpdateNanos) / 1_000_000_000.0);
        lastFovUpdateNanos = now;
        double target = SETTINGS.targetFov() * lensBreathingScale();
        double blend = 1.0 - Math.exp(-elapsed * 10.0);
        currentFov += (target - currentFov) * blend;
        return (float) currentFov;
    }

    public static float cameraRollRadians() {
        return active ? (float) Math.toRadians(SETTINGS.rollDegrees()) : 0.0F;
    }

    public static double previewExposureStops() {
        return adaptedExposureStops;
    }

    public static float soundscapeGain(SoundSource source) {
        if (!active || !SnapshotConfig.get().acousticIsolation) {
            return 1.0F;
        }
        return switch (source) {
            case WEATHER, AMBIENT -> 1.16F;
            case UI, MASTER -> 1.0F;
            case MUSIC, RECORDS -> 0.78F;
            case VOICE -> 0.70F;
            case PLAYERS -> 0.64F;
            case BLOCKS, HOSTILE, NEUTRAL -> 0.46F;
        };
    }

    public static void toggle() {
        Minecraft client = Minecraft.getInstance();
        if (active) {
            close(client);
        } else {
            open(client);
        }
    }

    public static boolean handleMouseButton(int button, int action) {
        if (!active) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            if (action == InputConstants.PRESS) {
                halfPressActive = true;
                aeLocked = true;
                lockedRequiredExposureStops = requiredExposureStops;
                focusLocked = false;
                previousFocusLocked = false;
                updateAutoFocus(client);
                playAdjustmentSound(client, 1.52F);
            } else if (action == InputConstants.RELEASE) {
                halfPressActive = false;
                aeLocked = aeLockLatched;
                if (!afLockLatched) {
                    focusLocked = false;
                }
            }
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            if (action == InputConstants.PRESS) {
                queueCapture(client);
            }
            return true;
        }
        return false;
    }

    public static boolean handleZoomScroll(double amount) {
        if (!active || amount == 0.0) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        boolean controlDown = InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_LCONTROL)
            || InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_RCONTROL);
        if (controlDown) {
            SETTINGS.select(CameraControl.CAMERA_ROLL);
            SETTINGS.adjustRoll(amount * 1.5);
            playAdjustmentSound(client, 1.18F);
        } else {
            SETTINGS.select(CameraControl.FOCAL_LENGTH);
            SETTINGS.adjustFocalLength(amount * 1.25);
            playAdjustmentSound(client, 1.05F + (SETTINGS.focalLength() / 200.0F) * 0.35F);
        }
        return true;
    }

    private static void open(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        if (longExposure != null && longExposure.stabilized()) {
            client.player.setYRot(longExposure.lockedYaw());
            client.player.setXRot(longExposure.lockedPitch());
        }

        if (!canUseCamera(client)) {
            client.player.sendOverlayMessage(Component.translatable("message.snapshot.no_camera"));
            return;
        }

        active = true;
        previousFov = client.options.fov().get();
        previousSensitivity = client.options.sensitivity().get();
        previousSmoothCamera = client.options.smoothCamera;
        currentFov = SETTINGS.targetFov() * lensBreathingScale();
        lastFovUpdateNanos = System.nanoTime();
        adaptedExposureStops = SETTINGS.exposureStops();
        client.options.sensitivity().set(Math.max(0.04, previousSensitivity * 0.42));
        client.options.smoothCamera = false;
        LiveFilmPipeline.update(client, SETTINGS.filmProfile());
        refreshSoundscape(client);
        lastYaw = client.player.getYRot();
        lastPitch = client.player.getXRot();
        previousFocusLocked = false;
        applyFov(client, true);
        client.player.playSound(SoundEvents.SPYGLASS_USE, 0.35F, 0.82F);
    }

    private static void close(Minecraft client) {
        active = false;
        pendingBurstShots = 0;
        pendingCapture = null;
        longExposure = null;
        if (panorama != null) {
            panorama.close(client);
            panorama = null;
        }
        if (focusStack != null) {
            focusStack.close(client);
            focusStack = null;
        }
        halfPressActive = false;
        aeLocked = false;
        aeLockLatched = false;
        afLockLatched = false;
        stopIntervalometer();
        focusLocked = false;
        LiveFilmPipeline.clear(client);
        restoreViewOptions(client);
        refreshSoundscape(client);
        if (client.player != null) {
            client.player.playSound(SoundEvents.SPYGLASS_STOP_USING, 0.30F, 0.88F);
        }
    }

    private static void tick(Minecraft client) {
        flashTicks = Math.max(0, flashTicks - 1);
        shutterTicks = Math.max(0, shutterTicks - 1);
        burstCooldown = Math.max(0, burstCooldown - 1);
        commandDialTicks = Math.max(0, commandDialTicks - 1);
        intervalDelayTicks = Math.max(0, intervalDelayTicks - 1);

        while (SnapshotKeybinds.toggle().consumeClick()) {
            toggle();
        }

        while (SnapshotKeybinds.lighttable().consumeClick()) {
            if (active) {
                close(client);
            }
            SnapshotLighttableScreen.open(client);
        }

        if (!active) {
            return;
        }

        if (client.player == null || client.level == null) {
            close(client);
            return;
        }

        if (panorama != null) {
            panorama.tick(client);
        }
        if (focusStack != null) {
            focusStack.tick(client);
        }

        if (longExposure != null && longExposure.stabilized()) {
            double elapsed = longExposure.elapsedSeconds(System.nanoTime());
            double tracking = longExposure.settings().astroTracking().rate();
            client.player.setYRot((float) (longExposure.lockedYaw() - elapsed * 0.30 * tracking));
            client.player.setXRot(longExposure.lockedPitch());
        }

        while (SnapshotKeybinds.quickMenu().consumeClick()) {
            client.setScreenAndShow(new CameraQuickMenuScreen());
        }
        while (SnapshotKeybinds.focusPointSelector().consumeClick()) {
            client.setScreenAndShow(new FocusPointSelectorScreen());
        }
        while (SnapshotKeybinds.aeLock().consumeClick()) {
            aeLockLatched = !aeLockLatched;
            aeLocked = aeLockLatched || halfPressActive;
            if (aeLockLatched) {
                lockedRequiredExposureStops = requiredExposureStops;
            }
            playAdjustmentSound(client, aeLockLatched ? 1.42F : 0.92F);
        }
        while (SnapshotKeybinds.afLock().consumeClick()) {
            if (!afLockLatched) {
                updateAutoFocus(client);
            }
            afLockLatched = !afLockLatched;
            if (!afLockLatched) {
                focusLocked = false;
                previousFocusLocked = false;
            }
            playAdjustmentSound(client, afLockLatched ? 1.48F : 0.90F);
        }

        updateMotion(client);
        updateAutoFocus(client);
        updateSensorAdaptation(client);
        updateAtmosphere(client);
        if (SETTINGS.exposureAssist() != ExposureAssist.OFF && longExposure == null && analysisCooldown-- <= 0) {
            analysisRequested = true;
            analysisCooldown = 10;
        }

        while (SnapshotKeybinds.capture().consumeClick()) {
            queueCapture(client);
        }
        boolean controlsLocked = longExposure != null || panorama != null || focusStack != null;
        while (SnapshotKeybinds.nextControl().consumeClick()) {
            if (!controlsLocked) {
                SETTINGS.selectNext();
            }
            playAdjustmentSound(client, 1.25F);
        }
        while (SnapshotKeybinds.previousControl().consumeClick()) {
            if (!controlsLocked) {
                SETTINGS.selectPrevious();
            }
            playAdjustmentSound(client, 1.15F);
        }
        while (SnapshotKeybinds.increase().consumeClick()) {
            if (!controlsLocked) {
                SETTINGS.adjust(1);
            }
            playAdjustmentSound(client, 1.32F);
        }
        while (SnapshotKeybinds.decrease().consumeClick()) {
            if (!controlsLocked) {
                SETTINGS.adjust(-1);
            }
            playAdjustmentSound(client, 1.08F);
        }
        while (SnapshotKeybinds.autofocus().consumeClick()) {
            SETTINGS.toggleAutoFocus();
            previousFocusLocked = false;
            playAdjustmentSound(client, SETTINGS.autoFocus() ? 1.55F : 0.92F);
        }
        while (SnapshotKeybinds.flash().consumeClick()) {
            SETTINGS.toggleFlash();
            playAdjustmentSound(client, SETTINGS.flash() ? 1.45F : 0.85F);
        }
        while (SnapshotKeybinds.burst().consumeClick()) {
            SETTINGS.toggleBurst();
            playAdjustmentSound(client, SETTINGS.burst() ? 1.38F : 0.95F);
        }
        while (SnapshotKeybinds.reset().consumeClick()) {
            SETTINGS.reset();
            aeLockLatched = false;
            afLockLatched = false;
            aeLocked = halfPressActive;
            playAdjustmentSound(client, 1.0F);
        }
        while (SnapshotKeybinds.toggleHudSize().consumeClick()) {
            hudExpanded = !hudExpanded;
            playAdjustmentSound(client, hudExpanded ? 1.28F : 1.08F);
        }
        while (SnapshotKeybinds.filmProfile().consumeClick()) {
            if (!controlsLocked) {
                SETTINGS.cycleFilmProfile();
                SETTINGS.select(CameraControl.FILM_PROFILE);
                LiveFilmPipeline.update(client, SETTINGS.filmProfile());
            }
            playAdjustmentSound(client, 1.30F);
        }
        while (SnapshotKeybinds.aspectRatio().consumeClick()) {
            if (!controlsLocked) {
                SETTINGS.cycleAspectRatio();
                SETTINGS.select(CameraControl.ASPECT_RATIO);
            }
            playAdjustmentSound(client, 1.22F);
        }
        while (SnapshotKeybinds.mood().consumeClick()) {
            if (!controlsLocked) {
                SETTINGS.cycleMood();
                SETTINGS.select(CameraControl.MOOD);
            }
            playAdjustmentSound(client, 1.12F);
        }
        while (SnapshotKeybinds.exposureMode().consumeClick()) {
            if (!controlsLocked) {
                SETTINGS.cycleExposureMode();
                SETTINGS.select(CameraControl.EXPOSURE_MODE);
                commandDialTicks = 30;
            }
            playAdjustmentSound(client, 1.26F);
        }
        while (SnapshotKeybinds.exposureAssist().consumeClick()) {
            SETTINGS.cycleExposureAssist();
            SETTINGS.select(CameraControl.EXPOSURE_ASSIST);
            analysisRequested = SETTINGS.exposureAssist() != ExposureAssist.OFF;
            playAdjustmentSound(client, 1.18F);
        }
        while (SnapshotKeybinds.astrophotography().consumeClick()) {
            if (!controlsLocked) {
                SETTINGS.toggleAstrophotography();
                SETTINGS.select(CameraControl.SHUTTER);
                LiveFilmPipeline.update(client, SETTINGS.filmProfile());
            }
            playAdjustmentSound(client, SETTINGS.astrophotography() ? 0.88F : 1.08F);
        }
        while (SnapshotKeybinds.applyEnvironment().consumeClick()) {
            environmentIndex = (environmentIndex + 1) % ENVIRONMENT_PRESETS.length;
            applyEnvironmentPreset(environmentPreset());
        }

        if (pendingBurstShots > 0 && burstCooldown == 0) {
            queueCapture(client);
            pendingBurstShots--;
            burstCooldown = 3;
        }

        if (intervalShotsRemaining > 0 && intervalDelayTicks == 0
            && pendingCapture == null && longExposure == null && panorama == null && focusStack == null
            && pendingBurstShots == 0) {
            queueCapture(client);
            intervalShotsRemaining--;
            intervalDelayTicks = intervalShotsRemaining > 0 ? intervalPeriodTicks : 0;
        }

        LiveFilmPipeline.update(client, SETTINGS.filmProfile());

    }

    private static void updateMotion(Minecraft client) {
        float yaw = client.player.getYRot();
        float pitch = client.player.getXRot();
        yawVelocity = Mth.lerp(0.35F, yawVelocity, Mth.wrapDegrees(yaw - lastYaw));
        pitchVelocity = Mth.lerp(0.35F, pitchVelocity, pitch - lastPitch);
        lastYaw = yaw;
        lastPitch = pitch;
    }

    private static void updateAutoFocus(Minecraft client) {
        if (!SETTINGS.autoFocus()) {
            focusLocked = false;
            previousFocusLocked = false;
            return;
        }
        if ((halfPressActive || afLockLatched) && focusLocked) {
            return;
        }

        HitResult hit = focusHit(client, 256.0);
        focusLocked = hit.getType() != HitResult.Type.MISS;
        if (focusLocked) {
            SETTINGS.setAutoFocusDistance(client.player.getEyePosition().distanceTo(hit.getLocation()));
        }
        if (focusLocked && !previousFocusLocked) {
            client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.16F, 1.85F);
        }
        previousFocusLocked = focusLocked;
    }

    private static void updateSensorAdaptation(Minecraft client) {
        int localBrightness = client.level.getMaxLocalRawBrightness(client.player.blockPosition());
        int subjectBrightness = localBrightness;
        HitResult hit = focusHit(client, 128.0);
        if (hit.getType() != HitResult.Type.MISS) {
            BlockPos candidate = BlockPos.containing(hit.getLocation());
            if (client.level.hasChunk(candidate.getX() >> 4, candidate.getZ() >> 4)) {
                subjectBrightness = client.level.getMaxLocalRawBrightness(candidate);
            }
        }
        double meteredBrightness = switch (SETTINGS.meteringMode()) {
            case SPOT -> subjectBrightness;
            case CENTER_WEIGHTED -> subjectBrightness * 0.72 + localBrightness * 0.28;
            case EVALUATIVE -> subjectBrightness * 0.48 + localBrightness * 0.52;
        };
        double measuredRequirement = Mth.clamp((8.0 - meteredBrightness) * 0.22, -2.0, 3.0);
        if (!aeLocked) {
            requiredExposureStops = measuredRequirement;
            lockedRequiredExposureStops = measuredRequirement;
        } else {
            requiredExposureStops = lockedRequiredExposureStops;
        }
        SETTINGS.applyAutoExposure(requiredExposureStops);
        double target = SETTINGS.exposureStops() - requiredExposureStops;
        adaptedExposureStops += (target - adaptedExposureStops) * 0.085;
    }

    private static HitResult focusHit(Minecraft client, double maximumDistance) {
        Vec3 origin = client.player.getEyePosition();
        float yaw = client.player.getYRot() + SETTINGS.focusPointX() * 8.0F;
        float pitch = client.player.getXRot() + SETTINGS.focusPointY() * 7.0F;
        Vec3 direction = Vec3.directionFromRotation(pitch, yaw);
        Vec3 end = origin.add(direction.scale(maximumDistance));
        return client.level.clip(new ClipContext(origin, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
    }

    private static void updateAtmosphere(Minecraft client) {
        if (!SnapshotConfig.get().viewfinderParticles || SETTINGS.astrophotography() || particleCooldown-- > 0) {
            return;
        }

        double pupil = SETTINGS.aperturePupilMillimeters();
        particleCooldown = pupil >= 24.0 ? 3 : pupil >= 12.0 ? 6 : 10;
        Vec3 look = client.player.getLookAngle();
        Vec3 eye = client.player.getEyePosition();
        double distance = 1.6 + RANDOM.nextDouble() * 5.0;
        double spreadX = (RANDOM.nextDouble() - 0.5) * 5.0;
        double spreadY = (RANDOM.nextDouble() - 0.5) * 3.5;
        Vec3 position = eye.add(look.scale(distance)).add(spreadX, spreadY, -spreadX * 0.35);
        ParticleOptions particle = switch (SETTINGS.mood()) {
            case OVERCAST -> ParticleTypes.WHITE_ASH;
            case ALPINE_SUNRISE -> RANDOM.nextBoolean() ? ParticleTypes.FIREFLY : ParticleTypes.GLOW;
            case ETHEREAL_MIST -> ParticleTypes.SPORE_BLOSSOM_AIR;
            case ASTROPHOTOGRAPHY -> ParticleTypes.GLOW;
            case NATURAL -> RANDOM.nextInt(4) == 0 ? ParticleTypes.FIREFLY : ParticleTypes.SPORE_BLOSSOM_AIR;
        };
        client.level.addParticle(particle, position.x, position.y, position.z,
            (RANDOM.nextDouble() - 0.5) * 0.008, 0.002 + RANDOM.nextDouble() * 0.006,
            (RANDOM.nextDouble() - 0.5) * 0.008);
    }

    private static void queueCapture(Minecraft client) {
        if (panorama != null || focusStack != null) {
            return;
        }
        if (longExposure != null) {
            if (longExposure.bulb()) {
                longExposure.requestStop();
                client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.16F, 0.72F);
            }
            return;
        }
        if (SETTINGS.captureTechnique() == CaptureTechnique.PANORAMA) {
            startPanorama(client);
            return;
        }
        if (SETTINGS.captureTechnique() == CaptureTechnique.FOCUS_STACK) {
            startFocusStack(client);
            return;
        }
        if (SETTINGS.exposureBracket().enabled() && !SETTINGS.longExposure()) {
            captureOne(client);
        } else if (SETTINGS.burst() && !SETTINGS.longExposure()) {
            pendingBurstShots = 3;
            burstCooldown = 0;
        } else {
            captureOne(client);
        }
    }

    private static void startPanorama(Minecraft client) {
        if (client.player == null || pendingCapture != null || longExposure != null) {
            return;
        }
        if (!canUseCamera(client)) {
            client.player.sendOverlayMessage(Component.translatable("message.snapshot.no_camera"));
            return;
        }
        if (SnapshotConfig.get().requirePaper && !client.player.hasInfiniteMaterials()
            && countItem(client, SnapshotItems.PHOTOGRAPHIC_PAPER) < 1) {
            client.player.sendOverlayMessage(Component.literal("Snapshot requires photographic paper."));
            return;
        }
        if (SETTINGS.longExposure()) {
            client.player.sendOverlayMessage(Component.literal("Panorama needs a shutter speed faster than one second."));
            return;
        }

        CameraSettings capturedSettings = SETTINGS.copy();
        capturedSettings.setExposureBracket(ExposureBracket.OFF);
        String title = "snapshot_" + LocalDateTime.now().format(FILE_STAMP) + "_PANORAMA";
        boolean liveFilmBaked = LiveFilmPipeline.activeFor(client, capturedSettings.filmProfile());
        panorama = new PanoramaSession(title, capturedSettings, liveFilmBaked,
            client.player.getYRot(), client.player.getXRot(), hasItem(client, SnapshotItems.TRIPOD));
        panorama.begin(client);
        shutterTicks = 3;
        client.player.playSound(SoundEvents.CROSSBOW_LOADING_START.value(), 0.20F, 1.24F);
        client.player.sendOverlayMessage(Component.literal("Panorama 1 / 3"));
    }

    private static void startFocusStack(Minecraft client) {
        if (client.player == null || pendingCapture != null || longExposure != null) {
            return;
        }
        if (!canUseCamera(client)) {
            client.player.sendOverlayMessage(Component.translatable("message.snapshot.no_camera"));
            return;
        }
        if (SnapshotConfig.get().requirePaper && !client.player.hasInfiniteMaterials()
            && countItem(client, SnapshotItems.PHOTOGRAPHIC_PAPER) < 1) {
            client.player.sendOverlayMessage(Component.literal("Snapshot requires photographic paper."));
            return;
        }
        if (SETTINGS.longExposure()) {
            client.player.sendOverlayMessage(Component.literal("Focus stacking needs a shutter speed faster than one second."));
            return;
        }

        CameraSettings capturedSettings = SETTINGS.copy();
        capturedSettings.setExposureBracket(ExposureBracket.OFF);
        String title = "snapshot_" + LocalDateTime.now().format(FILE_STAMP) + "_FOCUS_STACK";
        boolean liveFilmBaked = LiveFilmPipeline.activeFor(client, capturedSettings.filmProfile());
        focusStack = new FocusStackSession(title, capturedSettings, liveFilmBaked,
            client.player.getYRot(), client.player.getXRot(), hasItem(client, SnapshotItems.TRIPOD),
            SceneDepthMap.capture(client, capturedSettings));
        focusStack.begin(client);
        shutterTicks = 3;
        client.player.playSound(SoundEvents.CROSSBOW_LOADING_START.value(), 0.20F, 1.38F);
        client.player.sendOverlayMessage(Component.literal("Focus stack NEAR 1 / 3"));
    }

    private static void captureOne(Minecraft client) {
        if (client.player == null) {
            return;
        }

        if (!canUseCamera(client)) {
            client.player.sendOverlayMessage(Component.translatable("message.snapshot.no_camera"));
            return;
        }

        int requiredPaper = !SETTINGS.longExposure() && SETTINGS.exposureBracket().enabled()
            && !SETTINGS.exposureBracket().hdr() ? 3 : 1;
        if (SnapshotConfig.get().requirePaper && !client.player.hasInfiniteMaterials()
            && countItem(client, SnapshotItems.PHOTOGRAPHIC_PAPER) < requiredPaper) {
            client.player.sendOverlayMessage(Component.literal("Snapshot requires " + requiredPaper + " sheet(s) of photographic paper."));
            pendingBurstShots = 0;
            return;
        }

        if (pendingCapture != null || longExposure != null) {
            return;
        }

        CameraSettings capturedSettings = SETTINGS.copy();
        SceneDepthMap depthMap = SceneDepthMap.capture(client, capturedSettings);
        String title = "snapshot_" + LocalDateTime.now().format(FILE_STAMP);
        boolean liveFilmBaked = LiveFilmPipeline.activeFor(client, capturedSettings.filmProfile());
        shutterTicks = capturedSettings.longExposure() ? 0 : Math.min(5,
            2 + (int) Math.round(Math.max(0.0, Math.log10(capturedSettings.shutterSeconds() * 125.0 + 1.0))));
        flashTicks = capturedSettings.flash() ? 5 : 0;
        client.player.playSound(SoundEvents.CROSSBOW_SHOOT, 0.26F, 1.72F);
        if (capturedSettings.longExposure()) {
            boolean stabilized = hasItem(client, SnapshotItems.TRIPOD);
            longExposure = new LongExposureAccumulator(title, capturedSettings, depthMap, liveFilmBaked,
                stabilized, client.player.getYRot(), client.player.getXRot());
        } else {
            pendingCapture = new CaptureRequest(title, capturedSettings, depthMap, liveFilmBaked,
                yawVelocity, pitchVelocity, capturedSettings.shutterSeconds(), 1, false);
        }
    }

    public static void captureRenderedFrame(RenderTarget renderTarget) {
        if (!active || ShaderCompatibility.shadowPassActive()) {
            return;
        }
        SnapshotDevSmokeTest.recordRenderedFrame();
        Minecraft client = Minecraft.getInstance();
        if (panorama != null) {
            PanoramaSession session = panorama;
            if (session.ready()) {
                session.markInFlight();
                Screenshot.takeScreenshot(renderTarget, image -> {
                    if (session != panorama) {
                        image.close();
                        return;
                    }
                    if (session.accept(client, image)) {
                        finishPanorama(client, session);
                    } else if (client.player != null) {
                        client.player.sendOverlayMessage(Component.literal("Panorama " + (session.frameIndex() + 1) + " / 3"));
                    }
                });
            }
            return;
        }
        if (focusStack != null) {
            FocusStackSession session = focusStack;
            if (session.ready()) {
                session.markInFlight();
                Screenshot.takeScreenshot(renderTarget, image -> {
                    if (session != focusStack) {
                        image.close();
                        return;
                    }
                    if (session.accept(client, image)) {
                        finishFocusStack(client, session);
                    } else if (client.player != null) {
                        client.player.sendOverlayMessage(Component.literal("Focus stack "
                            + session.stageLabel() + " " + (session.frameIndex() + 1) + " / 3"));
                    }
                });
            }
            return;
        }
        if (pendingCapture != null) {
            CaptureRequest request = pendingCapture;
            pendingCapture = null;
            Screenshot.takeScreenshot(renderTarget, image -> handleImage(client, image, request));
            return;
        }

        long now = System.nanoTime();
        if (longExposure != null) {
            LongExposureAccumulator session = longExposure;
            if (session.shouldSample(now)) {
                session.markSampleInFlight();
                Screenshot.takeScreenshot(renderTarget, image -> {
                    session.accept(image);
                    if (session == longExposure && session.readyToFinish(System.nanoTime())) {
                        finishLongExposure(client, session);
                    }
                });
            } else if (session.readyToFinish(now)) {
                finishLongExposure(client, session);
            }
            return;
        }

        if (analysisRequested && !analysisInFlight) {
            analysisRequested = false;
            analysisInFlight = true;
            int factor = renderTarget.width % 4 == 0 && renderTarget.height % 4 == 0 ? 4
                : renderTarget.width % 2 == 0 && renderTarget.height % 2 == 0 ? 2 : 1;
            Screenshot.takeScreenshot(renderTarget, factor, image -> {
                try {
                    exposureAnalysis = ExposureAnalysis.analyze(image);
                } finally {
                    image.close();
                    analysisInFlight = false;
                }
            });
        }
    }

    private static void finishPanorama(Minecraft client, PanoramaSession session) {
        if (session != panorama) {
            return;
        }
        panorama = null;
        NativeImage stitched;
        try {
            stitched = session.finishImage();
        } finally {
            session.restore(client);
        }
        CaptureRequest request = new CaptureRequest(session.title(), session.settings(), null,
            session.liveFilmBaked(), 0.0F, 0.0F, session.settings().shutterSeconds(), 3, session.stabilized());
        if (client.player != null) {
            client.player.playSound(SoundEvents.CROSSBOW_LOADING_END.value(), 0.24F, 1.46F);
            client.player.sendOverlayMessage(Component.literal("Panorama stitched"));
        }
        handleImage(client, stitched, request);
    }

    private static void finishFocusStack(Minecraft client, FocusStackSession session) {
        if (session != focusStack) {
            return;
        }
        focusStack = null;
        NativeImage merged;
        try {
            merged = session.finishImage();
        } finally {
            session.restore(client);
        }
        CaptureRequest request = new CaptureRequest(session.title(), session.settings(), null,
            session.liveFilmBaked(), 0.0F, 0.0F, session.settings().shutterSeconds(), 3, session.stabilized());
        if (client.player != null) {
            client.player.playSound(SoundEvents.CROSSBOW_LOADING_END.value(), 0.24F, 1.58F);
            client.player.sendOverlayMessage(Component.literal("Focus stack merged"));
        }
        handleImage(client, merged, request);
    }

    private static void finishLongExposure(Minecraft client, LongExposureAccumulator session) {
        if (session != longExposure) {
            return;
        }
        longExposure = null;
        NativeImage stacked = session.finishImage();
        CaptureRequest request = new CaptureRequest(session.title(), session.settings(), session.depthMap(),
            session.liveFilmBaked(), 0.0F, 0.0F, session.elapsedSeconds(System.nanoTime()), session.samples(), session.stabilized());
        client.player.playSound(SoundEvents.CROSSBOW_LOADING_END.value(), 0.22F, 1.35F);
        handleImage(client, stacked, request);
    }

    private static void handleImage(Minecraft client, NativeImage image, CaptureRequest request) {
        CameraSettings capturedSettings = request.settings();
        NativeImage output = image;
        try {
            output = capturedSettings.captureTechnique() == CaptureTechnique.PANORAMA
                ? image : PhotoProcessor.cropToAspect(image, capturedSettings.aspectRatio());
            if (SnapshotConfig.get().pngExport && SnapshotConfig.get().rawStyleExport) {
                writeSourceNegative(client, output, request.title());
            }
            if (!capturedSettings.longExposure() && capturedSettings.exposureBracket().enabled()) {
                processExposureSeries(client, output, request);
            } else {
                processImage(output, request, client);
                publishImage(client, output, request);
            }
        } finally {
            if (output != image) {
                image.close();
            }
            output.close();
        }
    }

    private static void processExposureSeries(Minecraft client, NativeImage source, CaptureRequest request) {
        ExposureBracket bracket = request.settings().exposureBracket();
        double[] offsets = {-bracket.stops(), 0.0, bracket.stops()};
        NativeImage[] exposures = new NativeImage[offsets.length];
        CameraSettings[] variants = new CameraSettings[offsets.length];
        try {
            for (int index = 0; index < offsets.length; index++) {
                variants[index] = request.settings().copy();
                variants[index].setCaptureExposureBiasStops(offsets[index]);
                exposures[index] = copyImage(source);
                CaptureRequest variantRequest = requestWith(request,
                    request.title() + "_EV" + signedFileOffset(offsets[index]), variants[index]);
                processImage(exposures[index], variantRequest, client);
            }

            if (bracket.hdr()) {
                NativeImage merged = mergeHdr(exposures[0], exposures[1], exposures[2]);
                try {
                    CameraSettings mergedSettings = request.settings().copy();
                    mergedSettings.setCaptureExposureBiasStops(0.0);
                    CaptureRequest mergedRequest = requestWith(request, request.title() + "_HDR", mergedSettings);
                    publishImage(client, merged, mergedRequest);
                } finally {
                    merged.close();
                }
            } else {
                for (int index = 0; index < exposures.length; index++) {
                    CaptureRequest variantRequest = requestWith(request,
                        request.title() + "_EV" + signedFileOffset(offsets[index]), variants[index]);
                    publishImage(client, exposures[index], variantRequest);
                }
            }
        } finally {
            for (NativeImage exposure : exposures) {
                if (exposure != null) {
                    exposure.close();
                }
            }
        }
    }

    private static void processImage(NativeImage image, CaptureRequest request, Minecraft client) {
        try {
            PhotoProcessor.process(image, request.settings(), request.yawVelocity(), request.pitchVelocity(),
                request.depthMap(), request.liveFilmBaked(), request.actualExposureSeconds());
        } catch (RuntimeException exception) {
            if (client.player != null) {
                client.player.sendOverlayMessage(Component.literal("Snapshot optics fallback used: " + exception.getMessage()));
            }
        }
    }

    private static void publishImage(Minecraft client, NativeImage image, CaptureRequest request) {
        Path exportedImage = null;
        if (SnapshotConfig.get().pngExport) {
            try {
                exportedImage = writeCapture(client, image, request);
                handOffToImage2Map(client, exportedImage, request.settings().printSize());
                if (client.player != null) {
                    client.player.sendOverlayMessage(Component.literal("Saved " + request.title() + ".png"));
                }
            } catch (IOException exception) {
                if (client.player != null) {
                    client.player.sendOverlayMessage(Component.literal("Could not save Snapshot PNG: " + exception.getMessage()));
                }
            }
        }

        if (ClientPlayNetworking.canSend(CapturePhotoPayload.TYPE)) {
            ClientPlayNetworking.send(new CapturePhotoPayload(
                request.title(),
                request.settings().metadata(),
                exportedImage != null,
                image.getWidth(),
                image.getHeight(),
                thumbnail(image, SnapshotConfig.get().thumbnailSize)
            ));
        }
    }

    private static CaptureRequest requestWith(CaptureRequest request, String title, CameraSettings settings) {
        return new CaptureRequest(title, settings, request.depthMap(), request.liveFilmBaked(),
            request.yawVelocity(), request.pitchVelocity(), request.actualExposureSeconds(), request.samples(), request.stabilized());
    }

    private static NativeImage copyImage(NativeImage source) {
        NativeImage copy = new NativeImage(source.getWidth(), source.getHeight(), false);
        int[] pixels = source.getPixels();
        for (int index = 0; index < pixels.length; index++) {
            copy.setPixel(index % source.getWidth(), index / source.getWidth(), pixels[index]);
        }
        return copy;
    }

    private static NativeImage mergeHdr(NativeImage dark, NativeImage normal, NativeImage bright) {
        NativeImage merged = new NativeImage(normal.getWidth(), normal.getHeight(), false);
        int[] darkPixels = dark.getPixels();
        int[] normalPixels = normal.getPixels();
        int[] brightPixels = bright.getPixels();
        for (int index = 0; index < normalPixels.length; index++) {
            int normalPixel = normalPixels[index];
            double luminance = (((normalPixel >> 16) & 0xFF) * 0.2126
                + ((normalPixel >> 8) & 0xFF) * 0.7152 + (normalPixel & 0xFF) * 0.0722) / 255.0;
            double shadowWeight = Math.pow(1.0 - luminance, 2.0);
            double highlightWeight = Math.pow(luminance, 2.0);
            double middleWeight = Math.max(0.18, 1.0 - shadowWeight - highlightWeight);
            double weight = shadowWeight + middleWeight + highlightWeight;
            int red = hdrChannel(darkPixels[index], normalPixel, brightPixels[index], 16,
                highlightWeight, middleWeight, shadowWeight, weight);
            int green = hdrChannel(darkPixels[index], normalPixel, brightPixels[index], 8,
                highlightWeight, middleWeight, shadowWeight, weight);
            int blue = hdrChannel(darkPixels[index], normalPixel, brightPixels[index], 0,
                highlightWeight, middleWeight, shadowWeight, weight);
            int alpha = (normalPixel >>> 24) & 0xFF;
            merged.setPixel(index % merged.getWidth(), index / merged.getWidth(),
                (alpha << 24) | (red << 16) | (green << 8) | blue);
        }
        return merged;
    }

    private static int hdrChannel(int dark, int normal, int bright, int shift,
                                  double darkWeight, double middleWeight, double brightWeight, double totalWeight) {
        double value = (((dark >> shift) & 0xFF) * darkWeight
            + ((normal >> shift) & 0xFF) * middleWeight
            + ((bright >> shift) & 0xFF) * brightWeight) / totalWeight;
        double normalized = value / 255.0;
        normalized = Math.pow(Math.max(0.0, normalized), 0.94);
        return Math.max(0, Math.min(255, (int) Math.round(normalized * 255.0)));
    }

    private static String signedFileOffset(double value) {
        return String.format(java.util.Locale.ROOT, "%+.1f", value).replace('+', 'p').replace('-', 'm');
    }

    private static Path writeCapture(Minecraft client, NativeImage image, CaptureRequest request) throws IOException {
        String title = request.title();
        Path screenshotDir = client.gameDirectory.toPath().resolve("screenshots");
        Path snapshotDir = screenshotDir.resolve("snapshot");
        Files.createDirectories(snapshotDir);
        Path snapshotImage = snapshotDir.resolve(title + ".png");
        image.writeToFile(snapshotImage);

        if (SnapshotConfig.get().rootScreenshotCopy) {
            Files.createDirectories(screenshotDir);
            Path rootImage = screenshotDir.resolve(title + ".png");
            Files.copy(snapshotImage, rootImage, StandardCopyOption.REPLACE_EXISTING);
        }

        if (SnapshotConfig.get().image2MapSidecar) {
            writeImage2MapSidecar(snapshotImage, request.settings().printSize().width(), request.settings().printSize().height());
        }
        writeMetadataSidecar(client, snapshotImage, image.getWidth(), image.getHeight(), request);

        return snapshotImage;
    }

    private static void writeSourceNegative(Minecraft client, NativeImage image, String title) {
        try {
            Path directory = client.gameDirectory.toPath().resolve("screenshots/snapshot");
            Files.createDirectories(directory);
            image.writeToFile(directory.resolve(title + ".source.png"));
        } catch (IOException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not export source negative for {}.", title, exception);
        }
    }

    private static void writeImage2MapSidecar(Path imagePath, int width, int height) throws IOException {
        String text = """
            # Snapshot Image2Map helper commands
            # Image2Map accepts HTTP(S), not local file paths. Upload this PNG for a remote server,
            # then replace IMAGE_URL below. In singleplayer Snapshot performs this handoff automatically.
            # Local PNG: %s
            /image2map preview IMAGE_URL
            /image2map create %d %d dither IMAGE_URL
            """.formatted(imagePath.toAbsolutePath(), width, height);
        Files.writeString(imagePath.resolveSibling(stripExtension(imagePath.getFileName().toString()) + ".image2map.txt"), text, StandardCharsets.UTF_8);
    }

    private static void handOffToImage2Map(Minecraft client, Path imagePath, PrintSize printSize) {
        if (!SnapshotConfig.get().image2MapAuto || !FabricLoader.getInstance().isModLoaded("image2map")
            || !client.hasSingleplayerServer() || client.player == null) {
            return;
        }
        try {
            String url = Image2MapBridge.expose(imagePath);
            String command = "image2map create " + printSize.width() + " " + printSize.height()
                + " dither " + url;
            client.player.connection.sendCommand(command);
            if (SnapshotConfig.get().image2MapSidecar) {
                String text = """
                    # Snapshot temporary Image2Map bridge URL (valid while this game session is running)
                    /image2map preview %s
                    /image2map create %d %d dither %s
                    """.formatted(url, printSize.width(), printSize.height(), url);
                Files.writeString(imagePath.resolveSibling(stripExtension(imagePath.getFileName().toString())
                    + ".image2map.txt"), text, StandardCharsets.UTF_8);
            }
            client.player.sendOverlayMessage(Component.literal("Sent " + printSize.label() + " print directly to Image2Map."));
        } catch (IOException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not expose {} to Image2Map.", imagePath, exception);
            client.player.sendOverlayMessage(Component.literal("Image2Map handoff failed: " + exception.getMessage()));
        }
    }

    private static void writeMetadataSidecar(Minecraft client, Path imagePath, int width, int height, CaptureRequest request) throws IOException {
        CameraSettings capturedSettings = request.settings();
        JsonObject metadata = new JsonObject();
        metadata.addProperty("title", stripExtension(imagePath.getFileName().toString()));
        metadata.addProperty("captured_at", LocalDateTime.now().toString());
        metadata.addProperty("image", imagePath.toAbsolutePath().toString());
        metadata.addProperty("width", width);
        metadata.addProperty("height", height);
        metadata.addProperty("camera", capturedSettings.metadata());
        metadata.addProperty("iso", capturedSettings.iso());
        metadata.addProperty("shutter", capturedSettings.shutter());
        metadata.addProperty("actual_exposure_seconds", request.actualExposureSeconds());
        metadata.addProperty("stacked_frames", request.samples());
        metadata.addProperty("tripod_stabilized", request.stabilized());
        metadata.addProperty("aperture", capturedSettings.apertureNumber());
        metadata.addProperty("exposure_mode", capturedSettings.exposureMode().label());
        metadata.addProperty("metering", capturedSettings.meteringMode().label());
        metadata.addProperty("auto_iso", capturedSettings.autoIso());
        metadata.addProperty("auto_iso_max", capturedSettings.autoIsoMaximum());
        metadata.addProperty("minimum_auto_shutter", capturedSettings.minimumShutter());
        metadata.addProperty("exposure_bracket", capturedSettings.exposureBracket().label());
        metadata.addProperty("capture_technique", capturedSettings.captureTechnique().label());
        metadata.addProperty("capture_exposure_bias_stops", capturedSettings.captureExposureBiasStops());
        metadata.addProperty("focal_length_mm", capturedSettings.focalLengthPrecise());
        metadata.addProperty("lens", capturedSettings.lens().label());
        metadata.addProperty("filter", capturedSettings.filter().label());
        metadata.addProperty("print_size", capturedSettings.printSize().label());
        metadata.addProperty("star_tracking", capturedSettings.astroTracking().label());
        metadata.addProperty("astro_stack_mode", capturedSettings.astroStackMode().label());
        metadata.addProperty("dark_frame_subtraction", capturedSettings.darkFrameSubtraction());
        metadata.addProperty("red_night_vision", capturedSettings.redNightVision());
        metadata.addProperty("interval_shots", capturedSettings.intervalSequenceShots());
        metadata.addProperty("interval_seconds", capturedSettings.intervalSequenceSeconds());
        metadata.addProperty("focus_distance_m", capturedSettings.focusDistance());
        metadata.addProperty("focus_point", capturedSettings.focusPointLabel());
        metadata.addProperty("white_balance_k", capturedSettings.whiteBalance());
        metadata.addProperty("film_profile", capturedSettings.filmProfile().label());
        metadata.addProperty("live_film_baked", request.liveFilmBaked());
        metadata.addProperty("live_depth_optics_baked", request.liveFilmBaked());
        metadata.addProperty("aspect_ratio", capturedSettings.aspectRatio().label());
        metadata.addProperty("camera_roll_degrees", capturedSettings.rollDegrees());
        metadata.addProperty("mood", capturedSettings.mood().label());
        metadata.addProperty("quality_preset", capturedSettings.preset().label());
        metadata.addProperty("astrophotography", capturedSettings.astrophotography());
        metadata.addProperty("renderer", ShaderCompatibility.rendererLabel());
        metadata.addProperty("shader_pack_active", ShaderCompatibility.shaderPackActive());
        if (client.player != null) {
            metadata.addProperty("x", client.player.getX());
            metadata.addProperty("y", client.player.getY());
            metadata.addProperty("z", client.player.getZ());
        }
        if (client.level != null) {
            metadata.addProperty("dimension", client.level.dimension().identifier().toString());
            String biome = client.player == null ? "unknown" : client.level.getBiome(client.player.blockPosition())
                .unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
            metadata.addProperty("biome", biome);
            String weather = client.level.isThundering() ? "thunder" : client.level.isRaining() ? "rain" : "clear";
            metadata.addProperty("weather", weather);
            long dayTime = Math.floorMod(client.level.getOverworldClockTime(), 24000L);
            String celestial = dayTime >= 13000L && dayTime <= 23000L
                ? (capturedSettings.astrophotography() ? "tracked_night_sky" : "night_sky")
                : dayTime < 1500L || dayTime > 22500L ? "sunrise" : dayTime > 11500L ? "sunset" : "daylight";
            metadata.addProperty("celestial_condition", celestial);
        }
        String subject = photographedSubject(client);
        metadata.addProperty("subject", subject);
        String objective = objectiveFor(capturedSettings, subject, metadata);
        metadata.addProperty("objective", objective);
        metadata.addProperty("album", albumFor(objective));
        metadata.addProperty("composition_score", compositionScore(capturedSettings, subject));
        metadata.addProperty("favorite", false);
        metadata.addProperty("rating", 0);
        Path sidecar = imagePath.resolveSibling(stripExtension(imagePath.getFileName().toString()) + ".snapshot.json");
        Files.writeString(sidecar, JSON.toJson(metadata), StandardCharsets.UTF_8);
        PhotographyJournal.record(client, metadata);
    }

    private static String photographedSubject(Minecraft client) {
        if (client.player == null || client.level == null) {
            return "none";
        }
        HitResult result = ProjectileUtil.getHitResultOnViewVector(client.player,
            entity -> entity != client.player && entity.isPickable() && !entity.isSpectator(), 128.0);
        if (result instanceof EntityHitResult entityHit) {
            return BuiltInRegistries.ENTITY_TYPE.getKey(entityHit.getEntity().getType()).toString();
        }
        return "none";
    }

    private static String objectiveFor(CameraSettings settings, String subject, JsonObject metadata) {
        if (!"none".equals(subject)) {
            return "Subject study: " + subject;
        }
        if (settings.astrophotography()) {
            return "Celestial study";
        }
        if (metadata.has("weather") && !"clear".equals(metadata.get("weather").getAsString())) {
            return "Weather study: " + metadata.get("weather").getAsString();
        }
        return "Landscape study";
    }

    private static int compositionScore(CameraSettings settings, String subject) {
        double score = 62.0;
        if (exposureAnalysis != null) {
            score -= exposureAnalysis.clippedHighlights() * 80.0;
            score -= exposureAnalysis.crushedShadows() * 45.0;
        }
        score += focusLocked || !settings.autoFocus() ? 12.0 : 3.0;
        score += settings.focusPointX() != 0 || settings.focusPointY() != 0 ? 8.0 : 4.0;
        score += !"none".equals(subject) ? 9.0 : 0.0;
        score += settings.astrophotography() ? 5.0 : 0.0;
        score += settings.exposureBracket().hdr() ? 4.0 : 0.0;
        return Mth.clamp((int) Math.round(score), 0, 100);
    }

    private static String albumFor(String objective) {
        if (objective.startsWith("Celestial")) {
            return "ASTRO";
        }
        if (objective.startsWith("Subject")) {
            return "WILDLIFE";
        }
        if (objective.startsWith("Weather")) {
            return "WEATHER";
        }
        return "LANDSCAPES";
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    private static byte[] thumbnail(NativeImage image, int size) {
        int clampedSize = Math.max(32, Math.min(128, size));
        byte[] bytes = new byte[clampedSize * clampedSize * 4];
        double scale = Math.min(clampedSize / (double) image.getWidth(), clampedSize / (double) image.getHeight());
        int fittedWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int fittedHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
        int offsetX = (clampedSize - fittedWidth) / 2;
        int offsetY = (clampedSize - fittedHeight) / 2;
        for (int y = 0; y < clampedSize; y++) {
            for (int x = 0; x < clampedSize; x++) {
                int offset = (y * clampedSize + x) * 4;
                if (x < offsetX || x >= offsetX + fittedWidth || y < offsetY || y >= offsetY + fittedHeight) {
                    bytes[offset + 3] = (byte) 0xFF;
                    continue;
                }
                int sourceX = (x - offsetX) * image.getWidth() / fittedWidth;
                int sourceY = (y - offsetY) * image.getHeight() / fittedHeight;
                int pixel = image.getPixel(sourceX, sourceY);
                bytes[offset] = (byte) ((pixel >> 16) & 0xFF);
                bytes[offset + 1] = (byte) ((pixel >> 8) & 0xFF);
                bytes[offset + 2] = (byte) (pixel & 0xFF);
                bytes[offset + 3] = (byte) ((pixel >>> 24) & 0xFF);
            }
        }
        return bytes;
    }

    private static boolean canUseCamera(Minecraft client) {
        if (!SnapshotConfig.get().requireCamera || client.player == null || client.player.hasInfiniteMaterials()) {
            return true;
        }
        return hasItem(client, SnapshotItems.CAMERA);
    }

    private static boolean hasItem(Minecraft client, Item item) {
        return countItem(client, item) > 0;
    }

    private static int countItem(Minecraft client, Item item) {
        if (client.player == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void applyFov(Minecraft client, boolean immediate) {
        if (!active) {
            return;
        }
        double target = SETTINGS.targetFov() * lensBreathingScale();
        currentFov = immediate ? target : Mth.lerp(0.22, currentFov, target);
    }

    private static double lensBreathingScale() {
        double normalizedFocus = Mth.clamp((SETTINGS.focusDistance() - 0.5) / 32.0, 0.0, 1.0);
        return 1.0 - (1.0 - normalizedFocus) * 0.018;
    }

    private static void restoreViewOptions(Minecraft client) {
        if (previousFov != null) {
            previousFov = null;
        }
        if (previousSensitivity != null) {
            client.options.sensitivity().set(previousSensitivity);
            previousSensitivity = null;
        }
        client.options.smoothCamera = previousSmoothCamera;
    }

    private static void playAdjustmentSound(Minecraft client, float pitch) {
        if (client.player != null) {
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.10F, pitch);
        }
    }

    private static void refreshSoundscape(Minecraft client) {
        for (SoundSource source : SoundSource.values()) {
            client.getSoundManager().refreshCategoryVolume(source);
        }
    }

    private record CaptureRequest(
        String title,
        CameraSettings settings,
        SceneDepthMap depthMap,
        boolean liveFilmBaked,
        float yawVelocity,
        float pitchVelocity,
        double actualExposureSeconds,
        int samples,
        boolean stabilized
    ) {
    }

    private static final class PanoramaSession {
        private static final double[] FRAME_OFFSETS = {-0.62, 0.0, 0.62};
        private static final double OVERLAP = 0.36;

        private final String title;
        private final CameraSettings settings;
        private final boolean liveFilmBaked;
        private final float baseYaw;
        private final float basePitch;
        private final boolean stabilized;
        private final NativeImage[] frames = new NativeImage[FRAME_OFFSETS.length];
        private int frameIndex;
        private int settleTicks;
        private boolean inFlight;

        private PanoramaSession(String title, CameraSettings settings, boolean liveFilmBaked,
                                float baseYaw, float basePitch, boolean stabilized) {
            this.title = title;
            this.settings = settings;
            this.liveFilmBaked = liveFilmBaked;
            this.baseYaw = baseYaw;
            this.basePitch = basePitch;
            this.stabilized = stabilized;
        }

        private void begin(Minecraft client) {
            settleTicks = 3;
            applyPose(client);
        }

        private void tick(Minecraft client) {
            applyPose(client);
            if (settleTicks > 0) {
                settleTicks--;
            }
        }

        private boolean ready() {
            return settleTicks == 0 && !inFlight && frameIndex < frames.length;
        }

        private void markInFlight() {
            inFlight = true;
        }

        private boolean accept(Minecraft client, NativeImage image) {
            inFlight = false;
            frames[frameIndex++] = image;
            if (frameIndex >= frames.length) {
                return true;
            }
            settleTicks = 3;
            applyPose(client);
            return false;
        }

        private void applyPose(Minecraft client) {
            if (client.player == null || frameIndex >= FRAME_OFFSETS.length) {
                return;
            }
            float yawOffset = (float) (FRAME_OFFSETS[frameIndex] * settings.targetFov());
            client.player.setYRot(baseYaw + yawOffset);
            client.player.setXRot(basePitch);
        }

        private NativeImage finishImage() {
            if (frameIndex != frames.length) {
                throw new IllegalStateException("Panorama ended before all frames were captured");
            }
            int frameWidth = frames[0].getWidth();
            int frameHeight = frames[0].getHeight();
            int overlap = Math.max(1, (int) Math.round(frameWidth * OVERLAP));
            int step = frameWidth - overlap;
            NativeImage stitched = new NativeImage(frameWidth + step * (frames.length - 1), frameHeight, false);
            try {
                for (int index = 0; index < frames.length; index++) {
                    compositeFrame(stitched, frames[index], index * step, index == 0 ? 0 : overlap);
                }
                return stitched;
            } catch (RuntimeException exception) {
                stitched.close();
                throw exception;
            } finally {
                releaseFrames();
            }
        }

        private static void compositeFrame(NativeImage output, NativeImage frame, int offsetX, int overlap) {
            for (int y = 0; y < frame.getHeight(); y++) {
                for (int x = 0; x < frame.getWidth(); x++) {
                    int source = frame.getPixel(x, y);
                    int outputX = offsetX + x;
                    if (overlap > 0 && x < overlap) {
                        double linear = (x + 1.0) / (overlap + 1.0);
                        double blend = linear * linear * (3.0 - 2.0 * linear);
                        source = blendPixel(output.getPixel(outputX, y), source, blend);
                    }
                    output.setPixel(outputX, y, source);
                }
            }
        }

        private static int blendPixel(int first, int second, double amount) {
            int alpha = blendChannel(first, second, 24, amount);
            int red = blendChannel(first, second, 16, amount);
            int green = blendChannel(first, second, 8, amount);
            int blue = blendChannel(first, second, 0, amount);
            return (alpha << 24) | (red << 16) | (green << 8) | blue;
        }

        private static int blendChannel(int first, int second, int shift, double amount) {
            int a = (first >> shift) & 0xFF;
            int b = (second >> shift) & 0xFF;
            return Mth.clamp((int) Math.round(a + (b - a) * amount), 0, 255);
        }

        private void restore(Minecraft client) {
            if (client.player != null) {
                client.player.setYRot(baseYaw);
                client.player.setXRot(basePitch);
            }
        }

        private void close(Minecraft client) {
            restore(client);
            releaseFrames();
        }

        private void releaseFrames() {
            for (int index = 0; index < frames.length; index++) {
                if (frames[index] != null) {
                    frames[index].close();
                    frames[index] = null;
                }
            }
        }

        private String title() {
            return title;
        }

        private CameraSettings settings() {
            return settings;
        }

        private boolean liveFilmBaked() {
            return liveFilmBaked;
        }

        private boolean stabilized() {
            return stabilized;
        }

        private int frameIndex() {
            return frameIndex;
        }
    }

    private static final class FocusStackSession {
        private static final String[] STAGE_LABELS = {"NEAR", "MID", "FAR"};

        private final String title;
        private final CameraSettings settings;
        private final boolean liveFilmBaked;
        private final float lockedYaw;
        private final float lockedPitch;
        private final boolean stabilized;
        private final boolean originalAutoFocus;
        private final double originalFocusDistance;
        private final CaptureTechnique originalTechnique;
        private final SceneDepthMap sceneDepth;
        private final double[] focusDistances;
        private final NativeImage[] frames = new NativeImage[3];
        private int frameIndex;
        private int settleTicks;
        private boolean inFlight;

        private FocusStackSession(String title, CameraSettings settings, boolean liveFilmBaked,
                                  float lockedYaw, float lockedPitch, boolean stabilized, SceneDepthMap sceneDepth) {
            this.title = title;
            this.settings = settings;
            this.liveFilmBaked = liveFilmBaked;
            this.lockedYaw = lockedYaw;
            this.lockedPitch = lockedPitch;
            this.stabilized = stabilized;
            this.sceneDepth = sceneDepth;
            originalAutoFocus = SETTINGS.autoFocus();
            originalFocusDistance = SETTINGS.focusDistance();
            originalTechnique = SETTINGS.captureTechnique();
            focusDistances = focusSweep(settings);
        }

        private static double[] focusSweep(CameraSettings settings) {
            double minimum = settings.lens().minimumFocusDistance();
            double subject = settings.focusDistance();
            if (subject > 12.0) {
                return new double[]{Math.max(minimum, 2.5), Math.min(subject, 24.0), 256.0};
            }
            double near = Math.max(minimum, subject * 0.34);
            double middle = Math.max(near + 0.1, subject);
            double far = Math.min(256.0, Math.max(middle + 1.0, subject * 4.0));
            return new double[]{near, middle, far};
        }

        private void begin(Minecraft client) {
            SETTINGS.setCaptureTechnique(CaptureTechnique.SINGLE);
            SETTINGS.setAutoFocus(false);
            settleTicks = 3;
            applyStage(client);
        }

        private void tick(Minecraft client) {
            applyPose(client);
            if (settleTicks > 0) {
                settleTicks--;
            }
        }

        private boolean ready() {
            return settleTicks == 0 && !inFlight && frameIndex < frames.length;
        }

        private void markInFlight() {
            inFlight = true;
        }

        private boolean accept(Minecraft client, NativeImage image) {
            inFlight = false;
            frames[frameIndex++] = image;
            if (frameIndex >= frames.length) {
                return true;
            }
            settleTicks = 3;
            applyStage(client);
            return false;
        }

        private void applyStage(Minecraft client) {
            SETTINGS.setFocusDistance(focusDistances[frameIndex]);
            applyPose(client);
        }

        private void applyPose(Minecraft client) {
            if (client.player != null) {
                client.player.setYRot(lockedYaw);
                client.player.setXRot(lockedPitch);
            }
        }

        private NativeImage finishImage() {
            if (frameIndex != frames.length) {
                throw new IllegalStateException("Focus stack ended before all frames were captured");
            }
            int width = frames[0].getWidth();
            int height = frames[0].getHeight();
            NativeImage merged = new NativeImage(width, height, false);
            int[][] pixels = {frames[0].getPixels(), frames[1].getPixels(), frames[2].getPixels()};
            try {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int index = y * width + x;
                        double distance = sceneDepth.sample(x / (double) Math.max(1, width - 1),
                            y / (double) Math.max(1, height - 1));
                        merged.setPixel(x, y, blendFocusedPixel(pixels, index, distance));
                    }
                }
                return merged;
            } catch (RuntimeException exception) {
                merged.close();
                throw exception;
            } finally {
                releaseFrames();
            }
        }

        private int blendFocusedPixel(int[][] pixels, int pixelIndex, double subjectDistance) {
            double subjectDiopter = 1.0 / Math.max(0.05, subjectDistance);
            double firstWeight = focusWeight(subjectDiopter, focusDistances[0]);
            double middleWeight = focusWeight(subjectDiopter, focusDistances[1]);
            double lastWeight = focusWeight(subjectDiopter, focusDistances[2]);
            double totalWeight = firstWeight + middleWeight + lastWeight;
            int alpha = blendFocusChannel(pixels, pixelIndex, firstWeight, middleWeight, lastWeight, totalWeight, 24);
            int red = blendFocusChannel(pixels, pixelIndex, firstWeight, middleWeight, lastWeight, totalWeight, 16);
            int green = blendFocusChannel(pixels, pixelIndex, firstWeight, middleWeight, lastWeight, totalWeight, 8);
            int blue = blendFocusChannel(pixels, pixelIndex, firstWeight, middleWeight, lastWeight, totalWeight, 0);
            return (alpha << 24) | (red << 16) | (green << 8) | blue;
        }

        private static double focusWeight(double subjectDiopter, double focusDistance) {
            double difference = Math.abs(subjectDiopter - 1.0 / Math.max(0.05, focusDistance));
            return 1.0 / (0.00035 + difference * difference * 64.0);
        }

        private static int blendFocusChannel(int[][] pixels, int pixelIndex, double firstWeight,
                                             double middleWeight, double lastWeight, double totalWeight, int shift) {
            double channel = ((pixels[0][pixelIndex] >> shift) & 0xFF) * firstWeight
                + ((pixels[1][pixelIndex] >> shift) & 0xFF) * middleWeight
                + ((pixels[2][pixelIndex] >> shift) & 0xFF) * lastWeight;
            return Mth.clamp((int) Math.round(channel / totalWeight), 0, 255);
        }

        private void restore(Minecraft client) {
            SETTINGS.setCaptureTechnique(originalTechnique);
            SETTINGS.setFocusDistance(originalFocusDistance);
            SETTINGS.setAutoFocus(originalAutoFocus);
            applyPose(client);
        }

        private void close(Minecraft client) {
            restore(client);
            releaseFrames();
        }

        private void releaseFrames() {
            for (int index = 0; index < frames.length; index++) {
                if (frames[index] != null) {
                    frames[index].close();
                    frames[index] = null;
                }
            }
        }

        private String title() {
            return title;
        }

        private CameraSettings settings() {
            return settings;
        }

        private boolean liveFilmBaked() {
            return liveFilmBaked;
        }

        private boolean stabilized() {
            return stabilized;
        }

        private int frameIndex() {
            return frameIndex;
        }

        private String stageLabel() {
            return STAGE_LABELS[Math.min(frameIndex, STAGE_LABELS.length - 1)];
        }
    }
}
