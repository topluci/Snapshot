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
import com.luci.snapshot.util.AtomicFiles;
import com.luci.snapshot.network.ApplyEnvironmentPayload;
import com.luci.snapshot.network.CapturePhotoPayload;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
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
    private static int opticalSampleCooldown;
    private static int pipelineRefreshCooldown;
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
    private static HighResolutionSession highResolution;
    private static float lastYaw;
    private static float lastPitch;
    private static float yawVelocity;
    private static float pitchVelocity;
    private static boolean privilegedViewfinderAccess;
    private static boolean rollResetChordHeld;

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
            afLockLatched = focusLocked;
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
        Minecraft client = Minecraft.getInstance();
        return active && gameplayScreenAvailable(client)
            && LiveFilmPipeline.activeFor(client, SETTINGS.filmProfile());
    }

    public static int liveOpticsQualityPercent() {
        return LiveFilmPipeline.adaptiveQualityPercent();
    }

    public static int liveOpticsRenderScalePercent() {
        return LiveFilmPipeline.renderScalePercent();
    }

    public static void prepareLiveOpticsFrame() {
        Minecraft client = Minecraft.getInstance();
        if (active && gameplayScreenAvailable(client)) {
            LiveFilmPipeline.prepareFrame(client, SETTINGS);
        }
    }

    public static boolean viewfinderHudVisible() {
        Minecraft client = Minecraft.getInstance();
        return active && (gameplayScreenAvailable(client) || cameraControlScreenOpen(client));
    }

    public static boolean viewfinderRendering() {
        return active && gameplayScreenAvailable(Minecraft.getInstance());
    }

    static boolean opticalAssistsSuppressed() {
        return pendingCapture != null || longExposure != null || panorama != null || focusStack != null
            || highResolution != null;
    }

    public static boolean captureInProgress() {
        return pendingCapture != null || longExposure != null || panorama != null || focusStack != null
            || highResolution != null
            || intervalShotsRemaining > 0;
    }

    public static void triggerCapture() {
        Minecraft client = Minecraft.getInstance();
        if (active && client.player != null && gameplayScreenAvailable(client)) {
            queueCapture(client);
        }
    }

    public static String environmentPreset() {
        return ENVIRONMENT_PRESETS[environmentIndex];
    }

    public static String[] environmentPresets() {
        return ENVIRONMENT_PRESETS.clone();
    }

    public static boolean applyEnvironmentPreset(String preset) {
        Minecraft client = Minecraft.getInstance();
        if (!environmentControlsAllowed()) {
            if (client.player != null) {
                client.player.sendOverlayMessage(Component.literal(
                    "Snapshot environment controls require Creative mode or cheats/operator permission."
                ));
            }
            return false;
        }
        int selectedIndex = -1;
        for (int index = 0; index < ENVIRONMENT_PRESETS.length; index++) {
            if (ENVIRONMENT_PRESETS[index].equals(preset)) {
                selectedIndex = index;
                break;
            }
        }
        if (selectedIndex < 0) {
            return false;
        }
        environmentIndex = selectedIndex;
        if (ClientPlayNetworking.canSend(ApplyEnvironmentPayload.TYPE)) {
            ClientPlayNetworking.send(new ApplyEnvironmentPayload(preset));
        }
        return true;
    }

    public static boolean environmentControlsAllowed() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && (client.player.hasInfiniteMaterials()
            || client.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
    }

    public static float lensFov(float vanillaFov) {
        if (!active) {
            return vanillaFov;
        }

        long now = System.nanoTime();
        double elapsed = lastFovUpdateNanos == 0L ? 0.0 : Math.min(0.05, (now - lastFovUpdateNanos) / 1_000_000_000.0);
        lastFovUpdateNanos = now;
        double target = highResolution == null
            ? SETTINGS.targetFov() * lensBreathingScale() : highResolution.tileFov();
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
            open(client, false);
        }
    }

    public static boolean openFromCameraItem(InteractionHand hand) {
        Minecraft client = Minecraft.getInstance();
        if (active || client.player == null || !gameplayScreenAvailable(client)
            || client.player.getItemInHand(hand).getItem() != SnapshotItems.CAMERA) {
            return false;
        }
        open(client, false);
        return active;
    }

    public static boolean handleMouseButton(int button, int action) {
        if (!active) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (!gameplayScreenAvailable(client)) {
            return false;
        }
        if (SnapshotKeybinds.matchesFocusMouse(button)) {
            if (action == InputConstants.PRESS || action == InputConstants.RELEASE) {
                setHalfPress(action == InputConstants.PRESS);
            }
            return true;
        }
        if (SnapshotKeybinds.matchesShutterMouse(button)) {
            if (action == InputConstants.PRESS) {
                queueCapture(client);
            }
            return true;
        }
        return false;
    }

    public static boolean setHalfPress(boolean pressed) {
        Minecraft client = Minecraft.getInstance();
        if (!active || client.player == null || pressed && !gameplayScreenAvailable(client)) {
            if (!pressed) {
                halfPressActive = false;
                aeLocked = aeLockLatched;
            }
            return false;
        }
        if (pressed == halfPressActive) {
            return true;
        }
        if (pressed) {
            aeLockLatched = false;
            afLockLatched = false;
            halfPressActive = true;
            aeLocked = true;
            lockedRequiredExposureStops = requiredExposureStops;
            focusLocked = false;
            previousFocusLocked = false;
            HitResult hit = focusHit(client, 256.0);
            if (SETTINGS.autoFocus()) {
                updateAutoFocus(client, hit);
            } else if (hit.getType() != HitResult.Type.MISS) {
                SETTINGS.setFocusDistance(client.player.getEyePosition().distanceTo(hit.getLocation()));
                focusLocked = true;
                previousFocusLocked = true;
                client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.16F, 1.78F);
            }
            playAdjustmentSound(client, 1.52F);
        } else {
            halfPressActive = false;
            if (focusLocked) {
                afLockLatched = SETTINGS.autoFocus();
                client.player.sendOverlayMessage(Component.literal(
                    (SETTINGS.autoFocus() ? "AF LOCK  " : "MF  ") + SETTINGS.focusDistanceLabel()
                ));
            }
            aeLocked = aeLockLatched;
        }
        return true;
    }

    public static boolean handleZoomScroll(double amount) {
        if (!active || amount == 0.0) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (!gameplayScreenAvailable(client)) {
            return false;
        }
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

    private static void open(Minecraft client, boolean privilegedAccess) {
        if (client.player == null || client.level == null || !gameplayScreenAvailable(client)) {
            return;
        }

        if (longExposure != null && longExposure.stabilized()) {
            client.player.setYRot(longExposure.lockedYaw());
            client.player.setXRot(longExposure.lockedPitch());
        }

        if (!privilegedAccess && !canUseCamera(client)) {
            client.player.sendOverlayMessage(Component.translatable("message.snapshot.no_camera"));
            return;
        }

        active = true;
        privilegedViewfinderAccess = privilegedAccess;
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
        opticalSampleCooldown = 0;
        pipelineRefreshCooldown = 0;
        analysisCooldown = 0;
        applyFov(client, true);
        client.player.playSound(SoundEvents.SPYGLASS_USE, 0.35F, 0.82F);
    }

    private static void close(Minecraft client) {
        active = false;
        privilegedViewfinderAccess = false;
        cancelCaptureOperations(client);
        halfPressActive = false;
        aeLocked = false;
        aeLockLatched = false;
        afLockLatched = false;
        focusLocked = false;
        rollResetChordHeld = false;
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

        if (!gameplayScreenAvailable(client)) {
            if (active) {
                if (cameraControlScreenOpen(client)) {
                    cancelCaptureOperations(client);
                } else {
                    close(client);
                }
            }
            SnapshotKeybinds.discardGameplayClicks();
            return;
        }

        while (SnapshotKeybinds.toggle().consumeClick()) {
            toggleFromViewfinderKey(client);
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

        handleRollResetChord(client);

        if (panorama != null) {
            panorama.tick(client);
        }
        if (focusStack != null) {
            focusStack.tick(client);
        }
        if (highResolution != null) {
            highResolution.tick(client);
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
        if (opticalSampleCooldown-- <= 0) {
            HitResult meteringHit = focusHit(client, 256.0);
            updateAutoFocus(client, meteringHit);
            updateSensorAdaptation(client, meteringHit);
            opticalSampleCooldown = 2;
        }
        updateAtmosphere(client);
        if (SETTINGS.exposureAssist() != ExposureAssist.OFF && longExposure == null && analysisCooldown-- <= 0) {
            analysisRequested = true;
            analysisCooldown = 20;
        }

        while (SnapshotKeybinds.capture().consumeClick()) {
            if (captureInProgress()) {
                cancelActiveCapture(client);
            } else {
                queueCapture(client);
            }
        }
        boolean controlsLocked = longExposure != null || panorama != null || focusStack != null || highResolution != null;
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
            String nextEnvironment = ENVIRONMENT_PRESETS[(environmentIndex + 1) % ENVIRONMENT_PRESETS.length];
            applyEnvironmentPreset(nextEnvironment);
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

        if (pipelineRefreshCooldown-- <= 0) {
            LiveFilmPipeline.update(client, SETTINGS.filmProfile());
            pipelineRefreshCooldown = 10;
        }

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
        updateAutoFocus(client, focusHit(client, 256.0));
    }

    private static void updateAutoFocus(Minecraft client, HitResult hit) {
        if (!SETTINGS.autoFocus()) {
            boolean wasLocked = focusLocked;
            if (hit.getType() == HitResult.Type.MISS) {
                focusLocked = false;
            } else {
                double subjectDistance = client.player.getEyePosition().distanceTo(hit.getLocation());
                double tolerance = Math.max(0.12, SETTINGS.focusDistance() * 0.025
                    * (SETTINGS.apertureNumber() / 1.8) * (50.0 / SETTINGS.focalLengthPrecise()));
                focusLocked = Math.abs(subjectDistance - SETTINGS.focusDistance()) <= tolerance;
            }
            if (focusLocked && !wasLocked) {
                client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.12F, 1.72F);
            }
            previousFocusLocked = focusLocked;
            return;
        }
        if ((halfPressActive || afLockLatched) && focusLocked) {
            return;
        }

        focusLocked = hit.getType() != HitResult.Type.MISS;
        if (focusLocked) {
            SETTINGS.setAutoFocusDistance(client.player.getEyePosition().distanceTo(hit.getLocation()));
        }
        if (focusLocked && !previousFocusLocked) {
            client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.16F, 1.85F);
        }
        previousFocusLocked = focusLocked;
    }

    private static void updateSensorAdaptation(Minecraft client, HitResult hit) {
        int localBrightness = client.level.getMaxLocalRawBrightness(client.player.blockPosition());
        int subjectBrightness = localBrightness;
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
        adaptedExposureStops += (target - adaptedExposureStops) * 0.24;
    }

    private static HitResult focusHit(Minecraft client, double maximumDistance) {
        Vec3 origin = client.player.getEyePosition();
        double verticalFov = Math.toRadians(Mth.clamp(currentFov > 0.0 ? currentFov : SETTINGS.targetFov(), 1.0, 170.0));
        double aspect = client.getWindow().getHeight() <= 0
            ? 1.0 : client.getWindow().getWidth() / (double) client.getWindow().getHeight();
        double sensorX = SETTINGS.focusPointX() * 0.5;
        double sensorY = SETTINGS.focusPointY() * 0.5;
        float yawOffset = (float) Math.toDegrees(Math.atan(Math.tan(verticalFov * 0.5) * aspect * sensorX));
        float pitchOffset = (float) Math.toDegrees(Math.atan(Math.tan(verticalFov * 0.5) * sensorY));
        float yaw = client.player.getYRot() - yawOffset;
        float pitch = client.player.getXRot() + pitchOffset;
        Vec3 direction = Vec3.directionFromRotation(pitch, yaw);
        Vec3 end = origin.add(direction.scale(maximumDistance));
        HitResult blockHit = client.level.clip(
            new ClipContext(origin, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player)
        );
        double blockDistance = blockHit.getType() == HitResult.Type.MISS
            ? maximumDistance * maximumDistance : origin.distanceToSqr(blockHit.getLocation());
        AABB searchBounds = client.player.getBoundingBox().expandTowards(direction.scale(maximumDistance)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
            client.level,
            client.player,
            origin,
            end,
            searchBounds,
            entity -> !entity.isSpectator() && entity.isPickable(),
            0.3F
        );
        if (entityHit != null && origin.distanceToSqr(entityHit.getLocation()) < blockDistance) {
            return entityHit;
        }
        return blockHit;
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
        if (!gameplayScreenAvailable(client) || panorama != null || focusStack != null || highResolution != null) {
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
        if (SETTINGS.captureTechnique() == CaptureTechnique.TILED_2X) {
            startHighResolutionCapture(client);
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
        panorama = new PanoramaSession(title, capturedSettings, false,
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
        focusStack = new FocusStackSession(title, capturedSettings, false,
            client.player.getYRot(), client.player.getXRot(), hasItem(client, SnapshotItems.TRIPOD),
            SceneDepthMap.capture(client, capturedSettings));
        focusStack.begin(client);
        shutterTicks = 3;
        client.player.playSound(SoundEvents.CROSSBOW_LOADING_START.value(), 0.20F, 1.38F);
        client.player.sendOverlayMessage(Component.literal("Focus stack NEAR 1 / 3"));
    }

    private static void startHighResolutionCapture(Minecraft client) {
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
            client.player.sendOverlayMessage(Component.literal("Tiled 2X needs a shutter speed faster than one second."));
            return;
        }
        long pixels = (long) client.gameRenderer.mainRenderTarget().width
            * client.gameRenderer.mainRenderTarget().height;
        if (pixels * 32L > 512L * 1024L * 1024L) {
            client.player.sendOverlayMessage(Component.literal(
                "Tiled 2X needs too much memory at this window size. Lower the game resolution first."
            ));
            return;
        }

        CameraSettings capturedSettings = SETTINGS.copy();
        capturedSettings.setExposureBracket(ExposureBracket.OFF);
        String title = "snapshot_" + LocalDateTime.now().format(FILE_STAMP) + "_TILED_2X";
        RenderTarget target = client.gameRenderer.mainRenderTarget();
        highResolution = new HighResolutionSession(
            title,
            capturedSettings,
            client.player.getYRot(),
            client.player.getXRot(),
            target.width / (double) Math.max(1, target.height),
            hasItem(client, SnapshotItems.TRIPOD)
        );
        highResolution.begin(client);
        shutterTicks = 3;
        client.player.playSound(SoundEvents.CROSSBOW_LOADING_START.value(), 0.20F, 1.30F);
        client.player.sendOverlayMessage(Component.literal("Tiled 2X 1 / 4"));
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
        shutterTicks = capturedSettings.longExposure() ? 0 : Math.min(5,
            2 + (int) Math.round(Math.max(0.0, Math.log10(capturedSettings.shutterSeconds() * 125.0 + 1.0))));
        flashTicks = capturedSettings.flash() ? 5 : 0;
        client.player.playSound(SoundEvents.CROSSBOW_SHOOT, 0.26F, 1.72F);
        if (capturedSettings.longExposure()) {
            boolean stabilized = hasItem(client, SnapshotItems.TRIPOD);
            longExposure = new LongExposureAccumulator(title, capturedSettings, depthMap, false,
                stabilized, client.player.getYRot(), client.player.getXRot());
        } else {
            pendingCapture = new CaptureRequest(title, capturedSettings, depthMap, false,
                yawVelocity, pitchVelocity, capturedSettings.shutterSeconds(), 1, false);
        }
    }

    public static void captureRenderedFrame(RenderTarget renderTarget) {
        Minecraft client = Minecraft.getInstance();
        if (!active || !gameplayScreenAvailable(client) || ShaderCompatibility.shadowPassActive()) {
            return;
        }
        SnapshotDevSmokeTest.recordRenderedFrame();
        if (panorama != null) {
            PanoramaSession session = panorama;
            if (session.ready()) {
                session.markInFlight();
                Screenshot.takeScreenshot(renderTarget, image -> {
                    if (session != panorama || !captureCallbackAllowed(client)) {
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
                    if (session != focusStack || !captureCallbackAllowed(client)) {
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
        if (highResolution != null) {
            HighResolutionSession session = highResolution;
            if (session.ready()) {
                session.markInFlight();
                Screenshot.takeScreenshot(renderTarget, image -> {
                    if (session != highResolution || !captureCallbackAllowed(client)) {
                        image.close();
                        return;
                    }
                    if (session.accept(client, image)) {
                        finishHighResolution(client, session);
                    } else if (client.player != null) {
                        client.player.sendOverlayMessage(Component.literal(
                            "Tiled 2X " + (session.frameIndex() + 1) + " / 4"
                        ));
                    }
                });
            }
            return;
        }
        if (pendingCapture != null) {
            CaptureRequest request = pendingCapture;
            pendingCapture = null;
            Screenshot.takeScreenshot(renderTarget, image -> {
                if (!captureCallbackAllowed(client)) {
                    image.close();
                    return;
                }
                handleImage(client, image, request);
            });
            return;
        }

        long now = System.nanoTime();
        if (longExposure != null) {
            LongExposureAccumulator session = longExposure;
            if (session.shouldSample(now)) {
                session.markSampleInFlight();
                Screenshot.takeScreenshot(renderTarget, image -> {
                    if (session != longExposure || !captureCallbackAllowed(client)) {
                        image.close();
                        return;
                    }
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
                    if (captureCallbackAllowed(client)) {
                        exposureAnalysis = ExposureAnalysis.analyze(image);
                    }
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

    private static void finishHighResolution(Minecraft client, HighResolutionSession session) {
        if (session != highResolution) {
            return;
        }
        highResolution = null;
        NativeImage stitched;
        try {
            stitched = session.finishImage();
        } catch (RuntimeException exception) {
            session.releaseFrames();
            SnapshotInit.LOGGER.error("[Snapshot] Tiled high-resolution stitching failed.", exception);
            if (client.player != null) {
                client.player.sendOverlayMessage(Component.literal(
                    "Tiled 2X capture failed safely. Keep the window size fixed and try again."
                ));
            }
            return;
        } finally {
            session.restore(client);
        }
        CaptureRequest request = new CaptureRequest(
            session.title(), session.settings(), null, false, 0.0F, 0.0F,
            session.settings().shutterSeconds(), 4, session.stabilized()
        );
        if (client.player != null) {
            client.player.playSound(SoundEvents.CROSSBOW_LOADING_END.value(), 0.24F, 1.52F);
            client.player.sendOverlayMessage(Component.literal("Tiled 2X capture stitched"));
        }
        handleImage(client, stitched, request);
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
            output = (capturedSettings.captureTechnique() == CaptureTechnique.PANORAMA
                || capturedSettings.captureTechnique() == CaptureTechnique.TILED_2X)
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
        SnapshotCaptureStorage.writePng(image, snapshotImage);

        if (SnapshotConfig.get().rootScreenshotCopy) {
            try {
                Files.createDirectories(screenshotDir);
                Path rootImage = screenshotDir.resolve(title + ".png");
                SnapshotCaptureStorage.copyPng(snapshotImage, rootImage);
                if (!Files.isRegularFile(rootImage) || Files.size(rootImage) != Files.size(snapshotImage)) {
                    throw new IOException("Root screenshot copy did not match the Snapshot PNG");
                }
            } catch (IOException exception) {
                SnapshotInit.LOGGER.warn("[Snapshot] PNG saved to {}, but the root screenshots copy failed.",
                    snapshotImage, exception);
            }
        }

        if (SnapshotConfig.get().image2MapSidecar) {
            try {
                writeImage2MapSidecar(snapshotImage, request.settings().printSize().width(), request.settings().printSize().height());
            } catch (IOException exception) {
                SnapshotInit.LOGGER.warn("[Snapshot] PNG saved, but its Image2Map helper could not be written.", exception);
            }
        }
        try {
            writeMetadataSidecar(client, snapshotImage, image.getWidth(), image.getHeight(), request);
        } catch (IOException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] PNG saved, but its metadata sidecar could not be written.", exception);
        }

        return snapshotImage;
    }

    private static void writeSourceNegative(Minecraft client, NativeImage image, String title) {
        try {
            Path directory = client.gameDirectory.toPath().resolve("screenshots/snapshot");
            Files.createDirectories(directory);
            SnapshotCaptureStorage.writePng(image, directory.resolve(title + ".source.png"));
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
        AtomicFiles.writeString(
            imagePath.resolveSibling(stripExtension(imagePath.getFileName().toString()) + ".image2map.txt"),
            text,
            StandardCharsets.UTF_8
        );
    }

    private static void handOffToImage2Map(Minecraft client, Path imagePath, PrintSize printSize) {
        if (printSize == PrintSize.ONE_BY_ONE || !SnapshotConfig.get().image2MapAuto
            || !FabricLoader.getInstance().isModLoaded("image2map")
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
                AtomicFiles.writeString(
                    imagePath.resolveSibling(stripExtension(imagePath.getFileName().toString()) + ".image2map.txt"),
                    text,
                    StandardCharsets.UTF_8
                );
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
        metadata.addProperty("panorama_exposure_matched",
            capturedSettings.captureTechnique() == CaptureTechnique.PANORAMA);
        metadata.addProperty("tiled_high_resolution",
            capturedSettings.captureTechnique() == CaptureTechnique.TILED_2X);
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
        metadata.addProperty("live_dof_render_scale_percent",
            SnapshotConfig.get().halfResolutionDof && capturedSettings.preset() != OpticsPreset.SCREENSHOT_ULTRA ? 50 : 100);
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
        AtomicFiles.writeString(sidecar, JSON.toJson(metadata), StandardCharsets.UTF_8);
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
        if (privilegedViewfinderAccess || !SnapshotConfig.get().requireCamera
            || client.player == null || client.player.hasInfiniteMaterials()) {
            return true;
        }
        return hasItem(client, SnapshotItems.CAMERA);
    }

    private static void toggleFromViewfinderKey(Minecraft client) {
        if (active) {
            close(client);
            return;
        }
        if (client.player == null || !client.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            if (client.player == null) {
                return;
            }
            client.player.sendOverlayMessage(Component.translatable("message.snapshot.use_camera"));
            return;
        }
        open(client, true);
    }

    private static boolean gameplayScreenAvailable(Minecraft client) {
        return client.gui.screen() == null;
    }

    private static boolean cameraControlScreenOpen(Minecraft client) {
        return client.gui.screen() instanceof CameraQuickMenuScreen
            || client.gui.screen() instanceof FocusPointSelectorScreen;
    }

    private static boolean captureCallbackAllowed(Minecraft client) {
        return active && client.player != null && client.level != null && gameplayScreenAvailable(client);
    }

    private static void cancelCaptureOperations(Minecraft client) {
        pendingBurstShots = 0;
        pendingCapture = null;
        if (longExposure != null && longExposure.stabilized() && client.player != null) {
            client.player.setYRot(longExposure.lockedYaw());
            client.player.setXRot(longExposure.lockedPitch());
        }
        longExposure = null;
        if (panorama != null) {
            panorama.close(client);
            panorama = null;
        }
        if (focusStack != null) {
            focusStack.close(client);
            focusStack = null;
        }
        if (highResolution != null) {
            HighResolutionSession session = highResolution;
            highResolution = null;
            session.close(client);
        }
        stopIntervalometer();
        analysisRequested = false;
    }

    private static void cancelActiveCapture(Minecraft client) {
        if (!captureInProgress()) {
            return;
        }
        cancelCaptureOperations(client);
        if (client.player != null) {
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.18F, 0.72F);
            client.player.sendOverlayMessage(Component.literal("Snapshot capture cancelled"));
        }
    }

    private static void handleRollResetChord(Minecraft client) {
        boolean controlDown = InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_LCONTROL)
            || InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_RCONTROL);
        boolean pressed = controlDown && InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_0);
        if (pressed && !rollResetChordHeld) {
            SETTINGS.resetRoll();
            SETTINGS.select(CameraControl.CAMERA_ROLL);
            playAdjustmentSound(client, 1.0F);
            if (client.player != null) {
                client.player.sendOverlayMessage(Component.literal("Camera roll reset to 0.0deg"));
            }
        }
        rollResetChordHeld = pressed;
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
        double target = highResolution == null
            ? SETTINGS.targetFov() * lensBreathingScale() : highResolution.tileFov();
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
            double exposureGain = overlap > 0 ? overlapExposureGain(output, frame, offsetX, overlap) : 1.0;
            for (int y = 0; y < frame.getHeight(); y++) {
                for (int x = 0; x < frame.getWidth(); x++) {
                    int source = applyExposureGain(frame.getPixel(x, y), exposureGain);
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

        private static double overlapExposureGain(NativeImage output, NativeImage frame, int offsetX, int overlap) {
            double existingLuminance = 0.0;
            double incomingLuminance = 0.0;
            int samples = 0;
            int stepX = Math.max(1, overlap / 96);
            int stepY = Math.max(1, frame.getHeight() / 96);
            for (int y = 0; y < frame.getHeight(); y += stepY) {
                for (int x = 0; x < overlap; x += stepX) {
                    int existing = output.getPixel(offsetX + x, y);
                    int incoming = frame.getPixel(x, y);
                    existingLuminance += pixelLuminance(existing);
                    incomingLuminance += pixelLuminance(incoming);
                    samples++;
                }
            }
            if (samples == 0 || incomingLuminance <= 1.0) {
                return 1.0;
            }
            return Mth.clamp(existingLuminance / incomingLuminance, 0.72, 1.38);
        }

        private static int applyExposureGain(int pixel, double gain) {
            if (Math.abs(gain - 1.0) < 0.002) {
                return pixel;
            }
            int alpha = (pixel >>> 24) & 0xFF;
            int red = Mth.clamp((int) Math.round(((pixel >> 16) & 0xFF) * gain), 0, 255);
            int green = Mth.clamp((int) Math.round(((pixel >> 8) & 0xFF) * gain), 0, 255);
            int blue = Mth.clamp((int) Math.round((pixel & 0xFF) * gain), 0, 255);
            return (alpha << 24) | (red << 16) | (green << 8) | blue;
        }

        private static double pixelLuminance(int pixel) {
            return ((pixel >> 16) & 0xFF) * 0.2126
                + ((pixel >> 8) & 0xFF) * 0.7152
                + (pixel & 0xFF) * 0.0722;
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

    private static final class HighResolutionSession {
        private static final double TILE_COVERAGE = 0.64;
        private static final double TILE_CENTER_OFFSET = 0.36;
        private static final double STITCH_OVERLAP = 0.20;
        private static final int[] YAW_SIGNS = {1, -1, 1, -1};
        private static final int[] PITCH_SIGNS = {-1, -1, 1, 1};

        private final String title;
        private final CameraSettings settings;
        private final float baseYaw;
        private final float basePitch;
        private final boolean stabilized;
        private final double tileFov;
        private final double yawOffset;
        private final double pitchOffset;
        private final double fullHalfVertical;
        private final double fullHalfHorizontal;
        private final double tileHalfVertical;
        private final double tileHalfHorizontal;
        private final double[] rightX = new double[4];
        private final double[] rightY = new double[4];
        private final double[] rightZ = new double[4];
        private final double[] upX = new double[4];
        private final double[] upY = new double[4];
        private final double[] upZ = new double[4];
        private final double[] forwardX = new double[4];
        private final double[] forwardY = new double[4];
        private final double[] forwardZ = new double[4];
        private final NativeImage[] frames = new NativeImage[4];
        private int frameIndex;
        private int settleTicks;
        private boolean inFlight;

        private HighResolutionSession(String title, CameraSettings settings, float baseYaw, float basePitch,
                                      double aspectRatio, boolean stabilized) {
            this.title = title;
            this.settings = settings;
            this.baseYaw = baseYaw;
            this.basePitch = basePitch;
            this.stabilized = stabilized;

            double safeAspect = Math.max(0.1, aspectRatio);
            fullHalfVertical = Math.tan(Math.toRadians(settings.targetFov()) * 0.5);
            fullHalfHorizontal = fullHalfVertical * safeAspect;
            tileHalfVertical = fullHalfVertical * TILE_COVERAGE;
            tileHalfHorizontal = tileHalfVertical * safeAspect;
            tileFov = Math.toDegrees(2.0 * Math.atan(tileHalfVertical));
            pitchOffset = Math.toDegrees(Math.atan(fullHalfVertical * TILE_CENTER_OFFSET));
            yawOffset = Math.toDegrees(Math.atan(fullHalfHorizontal * TILE_CENTER_OFFSET));
            prepareTileBasis();
        }

        private void prepareTileBasis() {
            double[][] base = cameraBasis(baseYaw, basePitch, settings.rollDegrees());
            for (int index = 0; index < frames.length; index++) {
                double[][] tile = cameraBasis(
                    baseYaw + YAW_SIGNS[index] * yawOffset,
                    basePitch + PITCH_SIGNS[index] * pitchOffset,
                    settings.rollDegrees()
                );
                rightX[index] = dot(base[0], tile[0]);
                rightY[index] = dot(base[1], tile[0]);
                rightZ[index] = dot(base[2], tile[0]);
                upX[index] = dot(base[0], tile[1]);
                upY[index] = dot(base[1], tile[1]);
                upZ[index] = dot(base[2], tile[1]);
                forwardX[index] = dot(base[0], tile[2]);
                forwardY[index] = dot(base[1], tile[2]);
                forwardZ[index] = dot(base[2], tile[2]);
            }
        }

        private static double[][] cameraBasis(double yawDegrees, double pitchDegrees, double rollDegrees) {
            double yaw = Math.toRadians(yawDegrees);
            double pitch = Math.toRadians(pitchDegrees);
            double sinYaw = Math.sin(yaw);
            double cosYaw = Math.cos(yaw);
            double sinPitch = Math.sin(pitch);
            double cosPitch = Math.cos(pitch);
            double[] right = {cosYaw, 0.0, sinYaw};
            double[] up = {-sinPitch * sinYaw, cosPitch, sinPitch * cosYaw};
            double[] forward = {-sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch};
            double roll = Math.toRadians(rollDegrees);
            double cosRoll = Math.cos(roll);
            double sinRoll = Math.sin(roll);
            double[] rolledRight = {
                right[0] * cosRoll + up[0] * sinRoll,
                right[1] * cosRoll + up[1] * sinRoll,
                right[2] * cosRoll + up[2] * sinRoll
            };
            double[] rolledUp = {
                up[0] * cosRoll - right[0] * sinRoll,
                up[1] * cosRoll - right[1] * sinRoll,
                up[2] * cosRoll - right[2] * sinRoll
            };
            return new double[][]{rolledRight, rolledUp, forward};
        }

        private static double dot(double[] first, double[] second) {
            return first[0] * second[0] + first[1] * second[1] + first[2] * second[2];
        }

        private void begin(Minecraft client) {
            settleTicks = 5;
            applyPose(client);
            applyFov(client, true);
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
            settleTicks = 5;
            applyPose(client);
            return false;
        }

        private void applyPose(Minecraft client) {
            if (client.player == null || frameIndex >= frames.length) {
                return;
            }
            client.player.setYRot((float) (baseYaw + YAW_SIGNS[frameIndex] * yawOffset));
            client.player.setXRot((float) Mth.clamp(
                basePitch + PITCH_SIGNS[frameIndex] * pitchOffset, -89.9, 89.9
            ));
        }

        private NativeImage finishImage() {
            if (frameIndex != frames.length) {
                throw new IllegalStateException("Tiled capture ended before all frames were captured");
            }
            int frameWidth = frames[0].getWidth();
            int frameHeight = frames[0].getHeight();
            for (NativeImage frame : frames) {
                if (frame.getWidth() != frameWidth || frame.getHeight() != frameHeight) {
                    throw new IllegalStateException("Tiled capture frame dimensions changed during capture");
                }
            }

            int overlapX = Math.max(2, (int) Math.round(frameWidth * STITCH_OVERLAP));
            int overlapY = Math.max(2, (int) Math.round(frameHeight * STITCH_OVERLAP));
            int stepX = frameWidth - overlapX;
            int stepY = frameHeight - overlapY;
            NativeImage stitched = new NativeImage(frameWidth + stepX, frameHeight + stepY, false);
            try {
                double[] gains = exposureGains(frameWidth, frameHeight);
                composite(stitched, frameWidth, frameHeight, gains);
                return stitched;
            } catch (RuntimeException exception) {
                stitched.close();
                throw exception;
            } finally {
                releaseFrames();
            }
        }

        private double[] exposureGains(int frameWidth, int frameHeight) {
            double[] gains = {1.0, 1.0, 1.0, 1.0};
            gains[1] = matchedExposureGain(0, gains[0], 1, frameWidth, frameHeight);
            gains[2] = matchedExposureGain(0, gains[0], 2, frameWidth, frameHeight);
            double fromTop = matchedExposureGain(1, gains[1], 3, frameWidth, frameHeight);
            double fromLeft = matchedExposureGain(2, gains[2], 3, frameWidth, frameHeight);
            gains[3] = Mth.clamp(Math.sqrt(fromTop * fromLeft), 0.72, 1.38);
            return gains;
        }

        private double matchedExposureGain(int referenceIndex, double referenceGain, int incomingIndex,
                                           int frameWidth, int frameHeight) {
            double referenceLuminance = 0.0;
            double incomingLuminance = 0.0;
            int samples = 0;
            double[] referenceProjection = new double[3];
            double[] incomingProjection = new double[3];
            for (int y = 0; y < 72; y++) {
                double rayY = (1.0 - (y + 0.5) / 72.0 * 2.0) * fullHalfVertical;
                for (int x = 0; x < 128; x++) {
                    double rayX = ((x + 0.5) / 128.0 * 2.0 - 1.0) * fullHalfHorizontal;
                    if (!project(referenceIndex, rayX, rayY, referenceProjection)
                        || !project(incomingIndex, rayX, rayY, incomingProjection)
                        || referenceProjection[2] < 0.015 || incomingProjection[2] < 0.015) {
                        continue;
                    }
                    int referencePixel = nearestPixel(frames[referenceIndex], referenceProjection,
                        frameWidth, frameHeight);
                    int incomingPixel = nearestPixel(frames[incomingIndex], incomingProjection,
                        frameWidth, frameHeight);
                    referenceLuminance += PanoramaSession.pixelLuminance(referencePixel) * referenceGain;
                    incomingLuminance += PanoramaSession.pixelLuminance(incomingPixel);
                    samples++;
                }
            }
            if (samples == 0 || incomingLuminance <= 1.0) {
                return 1.0;
            }
            return Mth.clamp(referenceLuminance / incomingLuminance, 0.72, 1.38);
        }

        private void composite(NativeImage output, int frameWidth, int frameHeight, double[] gains) {
            for (int outputY = 0; outputY < output.getHeight(); outputY++) {
                double rayY = (1.0 - (outputY + 0.5) / output.getHeight() * 2.0) * fullHalfVertical;
                for (int outputX = 0; outputX < output.getWidth(); outputX++) {
                    double rayX = ((outputX + 0.5) / output.getWidth() * 2.0 - 1.0) * fullHalfHorizontal;
                    double alpha = 0.0;
                    double red = 0.0;
                    double green = 0.0;
                    double blue = 0.0;
                    double totalWeight = 0.0;
                    for (int index = 0; index < frames.length; index++) {
                        double localX = rayX * rightX[index] + rayY * rightY[index] + rightZ[index];
                        double localY = rayX * upX[index] + rayY * upY[index] + upZ[index];
                        double localZ = rayX * forwardX[index] + rayY * forwardY[index] + forwardZ[index];
                        if (localZ <= 0.0) {
                            continue;
                        }
                        double screenX = localX / (localZ * tileHalfHorizontal);
                        double screenY = localY / (localZ * tileHalfVertical);
                        if (Math.abs(screenX) > 1.0 || Math.abs(screenY) > 1.0) {
                            continue;
                        }
                        double weight = projectionWeight(screenX, screenY);
                        double sourceX = (screenX * 0.5 + 0.5) * (frameWidth - 1);
                        double sourceY = (0.5 - screenY * 0.5) * (frameHeight - 1);
                        int x0 = Mth.clamp((int) Math.floor(sourceX), 0, frameWidth - 1);
                        int y0 = Mth.clamp((int) Math.floor(sourceY), 0, frameHeight - 1);
                        int x1 = Math.min(frameWidth - 1, x0 + 1);
                        int y1 = Math.min(frameHeight - 1, y0 + 1);
                        double fractionX = sourceX - x0;
                        double fractionY = sourceY - y0;
                        int first = frames[index].getPixel(x0, y0);
                        int second = frames[index].getPixel(x1, y0);
                        int third = frames[index].getPixel(x0, y1);
                        int fourth = frames[index].getPixel(x1, y1);
                        alpha += bilinearChannel(first, second, third, fourth, 24, fractionX, fractionY) * weight;
                        red += bilinearChannel(first, second, third, fourth, 16, fractionX, fractionY)
                            * gains[index] * weight;
                        green += bilinearChannel(first, second, third, fourth, 8, fractionX, fractionY)
                            * gains[index] * weight;
                        blue += bilinearChannel(first, second, third, fourth, 0, fractionX, fractionY)
                            * gains[index] * weight;
                        totalWeight += weight;
                    }
                    if (totalWeight <= 0.0) {
                        output.setPixel(outputX, outputY,
                            nearestTilePixel(rayX, rayY, frameWidth, frameHeight, gains));
                        continue;
                    }
                    int outputPixel = (channel(alpha / totalWeight) << 24)
                        | (channel(red / totalWeight) << 16)
                        | (channel(green / totalWeight) << 8)
                        | channel(blue / totalWeight);
                    output.setPixel(outputX, outputY, outputPixel);
                }
            }
        }

        private int nearestTilePixel(double rayX, double rayY, int frameWidth, int frameHeight, double[] gains) {
            int nearestIndex = 0;
            double nearestScreenX = 0.0;
            double nearestScreenY = 0.0;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (int index = 0; index < frames.length; index++) {
                double localX = rayX * rightX[index] + rayY * rightY[index] + rightZ[index];
                double localY = rayX * upX[index] + rayY * upY[index] + upZ[index];
                double localZ = rayX * forwardX[index] + rayY * forwardY[index] + forwardZ[index];
                if (localZ <= 0.0) {
                    continue;
                }
                double screenX = localX / (localZ * tileHalfHorizontal);
                double screenY = localY / (localZ * tileHalfVertical);
                double distance = Math.max(Math.abs(screenX), Math.abs(screenY));
                if (distance < nearestDistance) {
                    nearestIndex = index;
                    nearestScreenX = screenX;
                    nearestScreenY = screenY;
                    nearestDistance = distance;
                }
            }
            int sourceX = Mth.clamp((int) Math.round((Mth.clamp(nearestScreenX, -1.0, 1.0) * 0.5 + 0.5)
                * (frameWidth - 1)), 0, frameWidth - 1);
            int sourceY = Mth.clamp((int) Math.round((0.5 - Mth.clamp(nearestScreenY, -1.0, 1.0) * 0.5)
                * (frameHeight - 1)), 0, frameHeight - 1);
            return PanoramaSession.applyExposureGain(frames[nearestIndex].getPixel(sourceX, sourceY), gains[nearestIndex]);
        }

        private boolean project(int index, double rayX, double rayY, double[] output) {
            double localX = rayX * rightX[index] + rayY * rightY[index] + rightZ[index];
            double localY = rayX * upX[index] + rayY * upY[index] + upZ[index];
            double localZ = rayX * forwardX[index] + rayY * forwardY[index] + forwardZ[index];
            if (localZ <= 0.0) {
                return false;
            }
            double screenX = localX / (localZ * tileHalfHorizontal);
            double screenY = localY / (localZ * tileHalfVertical);
            if (Math.abs(screenX) > 1.0 || Math.abs(screenY) > 1.0) {
                return false;
            }
            output[0] = screenX * 0.5 + 0.5;
            output[1] = 0.5 - screenY * 0.5;
            output[2] = projectionWeight(screenX, screenY);
            return true;
        }

        private static int nearestPixel(NativeImage image, double[] projection, int width, int height) {
            int x = Mth.clamp((int) Math.round(projection[0] * (width - 1)), 0, width - 1);
            int y = Mth.clamp((int) Math.round(projection[1] * (height - 1)), 0, height - 1);
            return image.getPixel(x, y);
        }

        private static double projectionWeight(double screenX, double screenY) {
            double edge = Math.max(0.001, (1.0 - screenX * screenX) * (1.0 - screenY * screenY));
            return edge * edge;
        }

        private static double bilinearChannel(int first, int second, int third, int fourth,
                                              int shift, double fractionX, double fractionY) {
            double top = ((first >> shift) & 0xFF) * (1.0 - fractionX)
                + ((second >> shift) & 0xFF) * fractionX;
            double bottom = ((third >> shift) & 0xFF) * (1.0 - fractionX)
                + ((fourth >> shift) & 0xFF) * fractionX;
            return top * (1.0 - fractionY) + bottom * fractionY;
        }

        private static int channel(double value) {
            return Mth.clamp((int) Math.round(value), 0, 255);
        }

        private void restore(Minecraft client) {
            if (client.player != null) {
                client.player.setYRot(baseYaw);
                client.player.setXRot(basePitch);
            }
            applyFov(client, true);
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

        private boolean stabilized() {
            return stabilized;
        }

        private int frameIndex() {
            return frameIndex;
        }

        private double tileFov() {
            return tileFov;
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
