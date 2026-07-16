package com.luci.snapshot.client.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.luci.snapshot.SnapshotInit;
import com.luci.snapshot.client.camera.AstroStackMode;
import com.luci.snapshot.client.camera.CameraQuickMenuScreen;
import com.luci.snapshot.client.camera.CameraControl;
import com.luci.snapshot.client.camera.CameraSettings;
import com.luci.snapshot.client.camera.CaptureTechnique;
import com.luci.snapshot.client.camera.ExposureBracket;
import com.luci.snapshot.client.camera.ExposureMode;
import com.luci.snapshot.client.camera.FocusPointSelectorScreen;
import com.luci.snapshot.client.camera.OpticsPreset;
import com.luci.snapshot.client.camera.PrintSize;
import com.luci.snapshot.client.camera.SnapshotCameraController;
import com.luci.snapshot.client.compat.ShaderCompatibility;
import com.luci.snapshot.client.input.SnapshotKeybinds;
import com.luci.snapshot.client.photo.SnapshotJournalScreen;
import com.luci.snapshot.client.photo.SnapshotLighttableScreen;
import com.luci.snapshot.client.photo.SnapshotPhotoViewer;
import com.luci.snapshot.config.SnapshotConfig;
import com.luci.snapshot.item.SnapshotItems;
import com.mojang.blaze3d.platform.InputConstants;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class SnapshotDevSmokeTest {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> REQUIRED_METADATA = Set.of(
        "title", "captured_at", "image", "width", "height", "camera", "iso", "shutter", "aperture",
        "actual_exposure_seconds", "focal_length_mm", "focus_distance_m", "white_balance_k", "film_profile",
        "renderer", "shader_pack_active", "dimension", "biome", "weather", "celestial_condition",
        "objective", "composition_score"
    );
    private static final String TEST_CASE = System.getenv().getOrDefault("SNAPSHOT_AUTOTEST_CASE", "suite");
    private static final String EXPECTED_RENDERER = System.getenv().getOrDefault("SNAPSHOT_AUTOTEST_EXPECT_RENDERER", "");
    private static final String EXPECTED_SHADER = System.getenv().getOrDefault("SNAPSHOT_AUTOTEST_EXPECT_SHADER", "");
    private static final String EXPECTED_ENVIRONMENT = System.getenv().getOrDefault("SNAPSHOT_AUTOTEST_EXPECT_ENVIRONMENT", "");
    private static final OpticsPreset[] PERFORMANCE_PRESETS = {
        OpticsPreset.LOW, OpticsPreset.MEDIUM, OpticsPreset.ULTRA
    };
    private static final JsonArray METRICS = new JsonArray();
    private static int stage;
    private static int ticks;
    private static long startedMillis;
    private static Path gameDirectory;
    private static Path captureDirectory;
    private static Path reportPath;
    private static int capturesAtStart;
    private static int metadataAtStart;
    private static boolean resultWritten;
    private static long benchmarkStartedNanos;
    private static int benchmarkFrames;
    private static int performancePresetIndex;
    private static int multiplayerPhotographsAtStart;
    private static int multiplayerMapsAtStart;
    private static int image2MapBundlesAtStart;
    private static boolean keybindHudExpandedAtStart;
    private static String keybindEnvironmentAtStart;
    private static boolean keybindShaderActiveAtStart;

    private SnapshotDevSmokeTest() {
    }

    public static void registerIfRequested() {
        if (!"true".equalsIgnoreCase(System.getenv("SNAPSHOT_AUTOTEST"))) {
            return;
        }
        initializeReport();
        SnapshotInit.LOGGER.info("[Snapshot] Development capture smoke test armed.");
        ClientTickEvents.END_CLIENT_TICK.register(SnapshotDevSmokeTest::runTick);
    }

    private static void runTick(Minecraft client) {
        try {
            tick(client);
        } catch (Throwable failure) {
            fail(client, "Unhandled test failure: " + failure.getClass().getSimpleName() + ": " + failure.getMessage(), failure);
        }
    }

    private static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }
        ticks++;
        if (stage == 0 && ticks >= 60) {
            client.setScreenAndShow(null);
            if (isMultiplayerCase()) {
                SnapshotCameraController.settings().reset();
                if (!SnapshotCameraController.active()) {
                    SnapshotCameraController.toggle();
                }
                if (!SnapshotCameraController.active()) {
                    fail(client, "Multiplayer viewfinder did not open for a creative player.", null);
                    return;
                }
                multiplayerPhotographsAtStart = countItem(client, SnapshotItems.PHOTOGRAPH);
                multiplayerMapsAtStart = countItem(client, Items.FILLED_MAP);
                SnapshotInit.LOGGER.info("[Snapshot] Multiplayer permission and delivery test prepared.");
                stage = 200;
                ticks = 0;
                return;
            }
            client.player.connection.sendCommand("time set "
                + ("clock".equalsIgnoreCase(TEST_CASE) || "astro2".equalsIgnoreCase(TEST_CASE) ? "midnight" : "noon"));
            client.player.connection.sendCommand("weather clear");
            if ("access".equalsIgnoreCase(TEST_CASE)) {
                client.player.connection.sendCommand("gamemode survival @s");
                client.player.connection.sendCommand("clear @s snapshot:camera");
                client.player.connection.sendCommand("clear @s snapshot:photographic_paper");
                client.player.connection.sendCommand("clear @s snapshot:tripod");
                client.player.connection.sendCommand("clear @s snapshot:photograph");
                client.player.connection.sendCommand("clear @s minecraft:filled_map");
            } else {
                client.player.connection.sendCommand("give @s snapshot:camera");
                client.player.connection.sendCommand("give @s snapshot:photographic_paper 16");
                client.player.connection.sendCommand("give @s snapshot:tripod");
            }
            SnapshotInit.LOGGER.info("[Snapshot] Development smoke test prepared the test world.");
            stage = 1;
            ticks = 0;
        } else if (stage == 1 && ticks >= 80) {
            SnapshotCameraController.settings().reset();
            if ("access".equalsIgnoreCase(TEST_CASE)) {
                if (SnapshotCameraController.active()) {
                    SnapshotCameraController.toggle();
                }
                SnapshotCameraController.toggle();
                if (SnapshotCameraController.active()) {
                    fail(client, "Viewfinder opened in survival without a camera.", null);
                    return;
                }
                client.player.connection.sendCommand("give @s snapshot:camera");
                stage = 80;
                ticks = 0;
                return;
            }
            if (!SnapshotCameraController.active()) {
                SnapshotCameraController.toggle();
            }
            SnapshotCameraController.settings().cycleFilmProfile();
            if ("controls".equalsIgnoreCase(TEST_CASE)) {
                exerciseCameraControls(client);
                beginKeybindingProbe();
                SnapshotInit.LOGGER.info("[Snapshot] Camera math passed; registered keybinding probe started.");
                stage = 84;
                ticks = 0;
                return;
            }
            if ("performance".equalsIgnoreCase(TEST_CASE)) {
                performancePresetIndex = 0;
                beginPerformancePreset(client);
                return;
            }
            if ("items".equalsIgnoreCase(TEST_CASE)) {
                if (SnapshotCameraController.active()) {
                    SnapshotCameraController.toggle();
                }
                client.player.connection.sendCommand("give @s snapshot:photograph");
                client.setScreenAndShow(new InventoryScreen(client.player));
                SnapshotInit.LOGGER.info("[Snapshot] Item artwork visual test opened.");
                stage = 70;
                ticks = 0;
                return;
            }
            if ("astro2".equalsIgnoreCase(TEST_CASE)) {
                SnapshotCameraController.settings().applyAstrophotographyPreset();
                SnapshotCameraController.settings().select(CameraControl.SHUTTER);
                SnapshotCameraController.settings().adjust(-3);
                SnapshotCameraController.settings().setAstroStackMode(AstroStackMode.DENOISE);
                SnapshotCameraController.triggerCapture();
                SnapshotInit.LOGGER.info("[Snapshot] Astro 2.0 denoise stack started.");
                stage = 60;
                ticks = 0;
                return;
            }
            if ("clock".equalsIgnoreCase(TEST_CASE)) {
                SnapshotCameraController.settings().setCaptureTechnique(CaptureTechnique.SINGLE);
                SnapshotCameraController.settings().setExposureBracket(ExposureBracket.OFF);
                SnapshotCameraController.triggerCapture();
                SnapshotInit.LOGGER.info("[Snapshot] Development world-clock metadata test started.");
                stage = 50;
                ticks = 0;
                return;
            }
            if ("ui".equalsIgnoreCase(TEST_CASE)) {
                if (SnapshotCameraController.active()) {
                    SnapshotCameraController.toggle();
                }
                SnapshotLighttableScreen.open(client);
                SnapshotInit.LOGGER.info("[Snapshot] Development lighttable visual test opened.");
                stage = 40;
                ticks = 0;
                return;
            }
            if ("image2map".equalsIgnoreCase(TEST_CASE)) {
                image2MapBundlesAtStart = countItem(client, Items.BUNDLE);
                SnapshotCameraController.settings().setPrintSize(PrintSize.TWO_BY_TWO);
                SnapshotCameraController.settings().setCaptureTechnique(CaptureTechnique.SINGLE);
                SnapshotCameraController.settings().setExposureBracket(ExposureBracket.OFF);
                SnapshotCameraController.triggerCapture();
                SnapshotInit.LOGGER.info("[Snapshot] Development Image2Map 2x2 handoff test started.");
                stage = 30;
                ticks = 0;
                return;
            }
            if ("focus".equalsIgnoreCase(TEST_CASE)) {
                SnapshotCameraController.settings().setExposureBracket(ExposureBracket.OFF);
                SnapshotCameraController.settings().setCaptureTechnique(CaptureTechnique.FOCUS_STACK);
                SnapshotCameraController.triggerCapture();
                SnapshotInit.LOGGER.info("[Snapshot] Development focused visual test started.");
                stage = 20;
                ticks = 0;
                return;
            }
            if ("renderer".equalsIgnoreCase(TEST_CASE)) {
                SnapshotCameraController.settings().setCaptureTechnique(CaptureTechnique.SINGLE);
                SnapshotCameraController.settings().setExposureBracket(ExposureBracket.OFF);
                SnapshotCameraController.triggerCapture();
                SnapshotInit.LOGGER.info("[Snapshot] Renderer compatibility capture started: {}.", ShaderCompatibility.rendererLabel());
                stage = 75;
                ticks = 0;
                return;
            }
            SnapshotCameraController.settings().setCaptureTechnique(CaptureTechnique.SINGLE);
            SnapshotCameraController.settings().setExposureBracket(ExposureBracket.HDR_1_EV);
            SnapshotCameraController.triggerCapture();
            SnapshotInit.LOGGER.info("[Snapshot] Development HDR smoke capture started.");
            stage = 2;
            ticks = 0;
        } else if (stage == 2 && ticks >= 20 && !SnapshotCameraController.captureInProgress()) {
            SnapshotCameraController.settings().setExposureBracket(ExposureBracket.OFF);
            SnapshotCameraController.settings().setCaptureTechnique(CaptureTechnique.FOCUS_STACK);
            SnapshotCameraController.triggerCapture();
            SnapshotInit.LOGGER.info("[Snapshot] Development focus-stack smoke capture started.");
            stage = 3;
            ticks = 0;
        } else if (stage == 3 && ticks >= 20 && !SnapshotCameraController.captureInProgress()) {
            SnapshotCameraController.settings().setCaptureTechnique(CaptureTechnique.PANORAMA);
            SnapshotCameraController.triggerCapture();
            SnapshotInit.LOGGER.info("[Snapshot] Development panorama smoke capture started.");
            stage = 4;
            ticks = 0;
        } else if (stage == 4 && ticks >= 20 && !SnapshotCameraController.captureInProgress()) {
            SnapshotCameraController.settings().setCaptureTechnique(CaptureTechnique.SINGLE);
            SnapshotCameraController.settings().applyAstrophotographyPreset();
            client.player.connection.sendCommand("time set midnight");
            SnapshotInit.LOGGER.info("[Snapshot] Development long-exposure shader smoke test prepared.");
            stage = 5;
            ticks = 0;
        } else if (stage == 5 && ticks >= 30) {
            SnapshotCameraController.triggerCapture();
            SnapshotInit.LOGGER.info("[Snapshot] Development tracked long exposure started.");
            stage = 6;
            ticks = 0;
        } else if (stage == 6 && ticks >= 40 && !SnapshotCameraController.captureInProgress()) {
            pass(client, "Development feature smoke suite completed.");
        } else if (stage == 7 && ticks >= 10) {
            stage = 8;
            client.stop();
        } else if (stage == 20 && ticks >= 20 && !SnapshotCameraController.captureInProgress()) {
            pass(client, "Development focused visual test completed.");
        } else if (stage == 30 && ticks >= 100 && !SnapshotCameraController.captureInProgress()
            && countItem(client, Items.BUNDLE) > image2MapBundlesAtStart) {
            pass(client, "Development Image2Map handoff created a 2x2 map bundle.");
        } else if (stage == 30 && ticks >= 300 && !SnapshotCameraController.captureInProgress()) {
            fail(client, "Image2Map did not create the expected 2x2 map bundle.", null);
        } else if (stage == 40 && ticks >= 30) {
            Screenshot.grab(client.gameDirectory, "snapshot_lighttable_qa.png", client.gameRenderer.mainRenderTarget(), 1,
                message -> SnapshotInit.LOGGER.info("[Snapshot] Lighttable QA screenshot: {}", message.getString()));
            SnapshotJournalScreen.open(client);
            stage = 41;
            ticks = 0;
        } else if (stage == 41 && ticks >= 30) {
            Screenshot.grab(client.gameDirectory, "snapshot_journal_qa.png", client.gameRenderer.mainRenderTarget(), 1,
                message -> SnapshotInit.LOGGER.info("[Snapshot] Journal QA screenshot: {}", message.getString()));
            stage = 42;
            ticks = 0;
        } else if (stage == 42 && ticks >= 20) {
            pass(client, "Lighttable and journal visual tests completed.");
        } else if (stage == 50 && ticks >= 80 && !SnapshotCameraController.captureInProgress()) {
            pass(client, "Development world-clock metadata test completed.");
        } else if (stage == 60 && ticks >= 20 && !SnapshotCameraController.captureInProgress()) {
            SnapshotCameraController.settings().setAstroStackMode(AstroStackMode.DEEP_SKY);
            SnapshotCameraController.triggerCapture();
            SnapshotInit.LOGGER.info("[Snapshot] Astro 2.0 deep-sky stack started.");
            stage = 61;
            ticks = 0;
        } else if (stage == 61 && ticks >= 20 && !SnapshotCameraController.captureInProgress()) {
            SnapshotCameraController.settings().setAstroStackMode(AstroStackMode.STAR_TRAILS);
            SnapshotCameraController.triggerCapture();
            SnapshotInit.LOGGER.info("[Snapshot] Astro 2.0 star-trail stack started.");
            stage = 62;
            ticks = 0;
        } else if (stage == 62 && ticks >= 100 && !SnapshotCameraController.captureInProgress()) {
            pass(client, "Astro 2.0 compositor suite completed.");
        } else if (stage == 70 && ticks >= 40) {
            Screenshot.grab(client.gameDirectory, "snapshot_item_art_qa.png", client.gameRenderer.mainRenderTarget(), 1,
                message -> SnapshotInit.LOGGER.info("[Snapshot] Item artwork QA screenshot: {}", message.getString()));
            stage = 71;
            ticks = 0;
        } else if (stage == 71 && ticks >= 20) {
            pass(client, "Item artwork visual test completed.");
        } else if (stage == 75 && ticks >= 80 && !SnapshotCameraController.captureInProgress()) {
            pass(client, "Renderer compatibility capture completed.");
        } else if (stage == 80 && ticks >= 20) {
            SnapshotCameraController.toggle();
            if (!SnapshotCameraController.active()) {
                fail(client, "Viewfinder did not open after receiving a camera.", null);
                return;
            }
            SnapshotCameraController.triggerCapture();
            stage = 81;
            ticks = 0;
        } else if (stage == 81 && ticks >= 15) {
            if (SnapshotCameraController.captureInProgress()) {
                fail(client, "Capture started in survival without photographic paper.", null);
                return;
            }
            if (!newFiles(captureDirectory, ".png", ".source.png").isEmpty()) {
                fail(client, "A PNG was exported despite the missing-paper rejection.", null);
                return;
            }
            client.player.connection.sendCommand("give @s snapshot:photographic_paper");
            stage = 82;
            ticks = 0;
        } else if (stage == 82 && ticks >= 20) {
            SnapshotCameraController.triggerCapture();
            stage = 83;
            ticks = 0;
        } else if (stage == 83 && ticks >= 100 && !SnapshotCameraController.captureInProgress()) {
            if (countItem(client, SnapshotItems.PHOTOGRAPHIC_PAPER) != 0) {
                fail(client, "Photographic paper was not consumed after capture.", null);
                return;
            }
            if (countItem(client, SnapshotItems.PHOTOGRAPH) < 1 || countItem(client, Items.FILLED_MAP) < 1) {
                fail(client, "Survival capture did not grant both the Photograph and filled map.", null);
                return;
            }
            pass(client, "Survival camera and paper workflow completed.");
        } else if (stage == 84 && ticks >= 1) {
            verifyPrimaryKeybindings();
            clickBinding(SnapshotKeybinds.aeLock());
            clickBinding(SnapshotKeybinds.afLock());
            clickBinding(SnapshotKeybinds.reset());
            clickBinding(SnapshotKeybinds.toggleHudSize());
            stage = 85;
            ticks = 0;
        } else if (stage == 85 && ticks >= 1) {
            CameraSettings settings = SnapshotCameraController.settings();
            require(settings.iso() == 100 && settings.autoFocus() && !settings.flash() && !settings.burst(),
                "Reset key did not restore the default exposure and drive settings.");
            require(!SnapshotCameraController.aeLocked() && !SnapshotCameraController.afLocked(),
                "AE-L or AF-L key did not release its lock.");
            require(SnapshotCameraController.hudExpanded() == keybindHudExpandedAtStart,
                "HUD-size key did not return to its original state.");
            clickBinding(SnapshotKeybinds.nextControl());
            stage = 86;
            ticks = 0;
        } else if (stage == 86 && ticks >= 1) {
            require(SnapshotCameraController.settings().selected() == CameraControl.APERTURE.next(),
                "Next-control key did not advance the selected camera control.");
            clickBinding(SnapshotKeybinds.previousControl());
            stage = 87;
            ticks = 0;
        } else if (stage == 87 && ticks >= 1) {
            CameraSettings settings = SnapshotCameraController.settings();
            require(settings.selected() == CameraControl.APERTURE,
                "Previous-control key did not restore the selected camera control.");
            settings.select(CameraControl.ISO);
            settings.adjust(1);
            clickBinding(SnapshotKeybinds.decrease());
            stage = 88;
            ticks = 0;
        } else if (stage == 88 && ticks >= 1) {
            require(SnapshotCameraController.settings().iso() == 100,
                "Decrease key did not lower the selected ISO control.");
            clickBinding(SnapshotKeybinds.astrophotography());
            stage = 89;
            ticks = 0;
        } else if (stage == 89 && ticks >= 1) {
            require(SnapshotCameraController.settings().astrophotography(),
                "Astrophotography key did not enable the astro setup.");
            clickBinding(SnapshotKeybinds.astrophotography());
            stage = 90;
            ticks = 0;
        } else if (stage == 90 && ticks >= 1) {
            require(!SnapshotCameraController.settings().astrophotography(),
                "Astrophotography key did not disable the astro setup.");
            keybindEnvironmentAtStart = SnapshotCameraController.environmentPreset();
            clickBinding(SnapshotKeybinds.applyEnvironment());
            stage = 91;
            ticks = 0;
        } else if (stage == 91 && ticks >= 20) {
            require(!keybindEnvironmentAtStart.equals(SnapshotCameraController.environmentPreset()),
                "Environment key did not advance the selected atmosphere preset.");
            SnapshotCameraController.applyEnvironmentPreset(keybindEnvironmentAtStart);
            client.player.connection.sendCommand("snapshot preset low");
            stage = 911;
            ticks = 0;
        } else if (stage == 911 && ticks >= 10) {
            require(SnapshotCameraController.settings().preset() == OpticsPreset.LOW,
                "/snapshot preset did not apply the requested client optics preset.");
            SnapshotCameraController.settings().setPreset(OpticsPreset.ULTRA);
            client.player.connection.sendCommand("time set noon");
            client.player.connection.sendCommand("weather clear");
            clickBinding(SnapshotKeybinds.tutorial());
            stage = 92;
            ticks = 0;
        } else if (stage == 92 && ticks >= 1) {
            require(client.gui.screen() != null && client.gui.screen().getClass().getSimpleName().equals("SnapshotTutorialScreen"),
                "Tutorial key did not open the Snapshot tutorial.");
            client.setScreenAndShow(null);
            clickBinding(SnapshotKeybinds.quickMenu());
            stage = 93;
            ticks = 0;
        } else if (stage == 93 && ticks >= 10) {
            require(client.gui.screen() instanceof CameraQuickMenuScreen,
                "Command-dial key did not open the compact camera menu.");
            require(ShaderCompatibility.shaderPackActive() == keybindShaderActiveAtStart,
                "Command-dial key unexpectedly toggled the active Iris shader pack.");
            grabQaScreenshot(client, "snapshot_command_dial_qa.png", "command dial");
            client.setScreenAndShow(null);
            clickBinding(SnapshotKeybinds.focusPointSelector());
            stage = 94;
            ticks = 0;
        } else if (stage == 94 && ticks >= 10) {
            require(client.gui.screen() instanceof FocusPointSelectorScreen,
                "AF-point key did not open the focus-point selector.");
            grabQaScreenshot(client, "snapshot_focus_points_qa.png", "AF-point selector");
            client.setScreenAndShow(null);
            SnapshotCameraController.settings().reset();
            SnapshotCameraController.settings().cycleFilmProfile();
            SnapshotCameraController.settings().setCaptureTechnique(CaptureTechnique.SINGLE);
            SnapshotCameraController.settings().setExposureBracket(ExposureBracket.OFF);
            benchmarkStartedNanos = System.nanoTime();
            clickBinding(SnapshotKeybinds.capture());
            stage = 95;
            ticks = 0;
        } else if (stage == 95 && ticks >= 40 && !SnapshotCameraController.captureInProgress()) {
            addDurationMetric("controls", "capture_and_delivery", benchmarkStartedNanos, 15_000L);
            if (countItem(client, SnapshotItems.PHOTOGRAPH) < 1 || countItem(client, Items.FILLED_MAP) < 1) {
                fail(client, "Control playtest capture did not deliver a Photograph and filled map.", null);
                return;
            }
            clickBinding(SnapshotKeybinds.toggle());
            clickBinding(SnapshotKeybinds.lighttable());
            stage = 96;
            ticks = 0;
        } else if (stage == 96 && ticks >= 20) {
            require(!SnapshotCameraController.active() && client.gui.screen() instanceof SnapshotLighttableScreen,
                "Viewfinder and camera-roll keys did not close the finder and open the lighttable.");
            grabQaScreenshot(client, "snapshot_controls_lighttable_qa.png", "control-playtest lighttable");
            if (!SnapshotPhotoViewer.openLatestReview(client)) {
                fail(client, "Photo review could not open the latest capture.", null);
                return;
            }
            stage = 97;
            ticks = 0;
        } else if (stage == 97 && ticks >= 20) {
            grabQaScreenshot(client, "snapshot_photo_review_qa.png", "photo review");
            stage = 98;
            ticks = 0;
        } else if (stage == 98 && ticks >= 10) {
            require(ShaderCompatibility.shaderPackActive() == keybindShaderActiveAtStart,
                "The control playtest changed the active shader state.");
            pass(client, "Camera controls, registered keybindings, menus, AF points, capture, and review completed.");
        } else if (stage >= 100 && stage <= 104) {
            tickPerformance(client);
        } else if (stage == 200 && ticks >= 40) {
            SnapshotCameraController.applyEnvironmentPreset("night");
            stage = 201;
            ticks = 0;
        } else if (stage == 201 && ticks >= 60) {
            long previewTime = Math.floorMod(client.level.getOverworldClockTime(), 24_000L);
            if (previewTime < 17_000L || previewTime > 19_000L) {
                fail(client, "Client-only night preview did not become visible in the viewfinder.", null);
                return;
            }
            SnapshotCameraController.applyEnvironmentPreset("clear");
            long time = Math.floorMod(client.level.getOverworldClockTime(), 24_000L);
            boolean nightApplied = time >= 17_000L && time <= 19_000L;
            boolean expectedAllowed = "allowed".equalsIgnoreCase(EXPECTED_ENVIRONMENT);
            if (EXPECTED_ENVIRONMENT.isBlank()) {
                fail(client, "Multiplayer test did not declare whether environment changes should be allowed.", null);
                return;
            }
            if (nightApplied != expectedAllowed) {
                fail(client, "Multiplayer environment permission was " + (nightApplied ? "allowed" : "denied")
                    + " but expected " + EXPECTED_ENVIRONMENT + ".", null);
                return;
            }
            JsonObject metric = new JsonObject();
            metric.addProperty("operation", "environment_permission");
            metric.addProperty("expected", EXPECTED_ENVIRONMENT);
            metric.addProperty("preview_time", previewTime);
            metric.addProperty("observed_time", time);
            METRICS.add(metric);
            SnapshotCameraController.settings().setCaptureTechnique(CaptureTechnique.SINGLE);
            SnapshotCameraController.settings().setExposureBracket(ExposureBracket.OFF);
            benchmarkStartedNanos = System.nanoTime();
            SnapshotCameraController.triggerCapture();
            stage = 202;
            ticks = 0;
        } else if (stage == 202 && ticks >= 120 && !SnapshotCameraController.captureInProgress()) {
            addDurationMetric("multiplayer", "capture_and_delivery", benchmarkStartedNanos, 20_000L);
            if (countItem(client, SnapshotItems.PHOTOGRAPH) <= multiplayerPhotographsAtStart
                || countItem(client, Items.FILLED_MAP) <= multiplayerMapsAtStart) {
                fail(client, "Multiplayer capture did not deliver both the Photograph and filled map.", null);
                return;
            }
            pass(client, "Multiplayer permissions and photograph/map delivery completed.");
        } else if (ticks > timeoutForStage()) {
            fail(client, "Development smoke test timed out in stage " + stage + ".", null);
        }
    }

    public static void recordRenderedFrame() {
        if ("performance".equalsIgnoreCase(TEST_CASE) && stage == 100) {
            benchmarkFrames++;
        }
    }

    private static void exerciseCameraControls(Minecraft client) {
        CameraSettings settings = SnapshotCameraController.settings();
        settings.reset();

        double focalLength = settings.focalLengthPrecise();
        require(SnapshotCameraController.handleZoomScroll(0.4), "Viewfinder did not consume smooth zoom input.");
        require(Math.abs(settings.focalLengthPrecise() - focalLength - 0.5) < 0.001,
            "Smooth zoom did not preserve sub-millimeter focal increments.");

        settings.select(CameraControl.ISO);
        settings.adjust(2);
        require(settings.iso() == 400, "ISO control did not move from 100 to 400.");
        settings.select(CameraControl.SHUTTER);
        settings.adjust(2);
        require("1/30".equals(settings.shutter()), "Shutter control did not move to 1/30.");
        settings.select(CameraControl.APERTURE);
        settings.adjust(-1);
        require(Math.abs(settings.apertureNumber() - 1.4) < 0.001, "Aperture control did not move to f/1.4.");
        settings.select(CameraControl.EXPOSURE_COMPENSATION);
        settings.adjust(2);
        require(Math.abs(settings.exposureCompensationStops() - 1.0F) < 0.001F, "Exposure compensation did not reach +1 EV.");
        settings.select(CameraControl.WHITE_BALANCE);
        settings.adjust(-2);
        require(settings.whiteBalance() == 5200, "White balance did not move to 5200K.");

        double focusDistance = settings.focusDistance();
        settings.select(CameraControl.FOCUS_DISTANCE);
        settings.adjust(-2);
        require(!settings.autoFocus() && settings.focusDistance() < focusDistance,
            "Manual focus did not disable AF and move the focal plane.");
        settings.setFocusPoint(1, -1);
        require(settings.focusPointX() == 1 && settings.focusPointY() == -1,
            "Selectable AF point did not move to the top-right position.");
        settings.setAutoFocus(true);
        SnapshotCameraController.requestAutofocus();

        settings.toggleFlash();
        settings.toggleBurst();
        require(settings.flash() && settings.burst(), "Flash or burst toggle did not enable.");
        settings.toggleFlash();
        settings.toggleBurst();

        for (ExposureMode mode : ExposureMode.values()) {
            settings.setExposureMode(mode);
            require(settings.exposureMode() == mode, "Command dial did not select mode " + mode.label() + ".");
        }
        settings.setExposureMode(ExposureMode.MANUAL);
        settings.select(CameraControl.METERING);
        String metering = settings.meteringMode().label();
        settings.adjust(1);
        require(!metering.equals(settings.meteringMode().label()), "Metering control did not advance.");
        boolean autoIso = settings.autoIso();
        settings.toggleAutoIso();
        require(settings.autoIso() != autoIso, "Auto ISO did not toggle.");
        settings.toggleAutoIso();

        settings.cycleFilmProfile();
        settings.cycleAspectRatio();
        settings.cycleMood();
        settings.cycleExposureAssist();
        settings.adjustRoll(7.5);
        require(Math.abs(settings.rollDegrees() - 7.5) < 0.001, "Camera roll did not move to 7.5 degrees.");

        SnapshotCameraController.handleMouseButton(InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.PRESS);
        require(SnapshotCameraController.aeLocked(), "Half-press did not lock exposure.");
        SnapshotCameraController.handleMouseButton(InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.RELEASE);
        require(!SnapshotCameraController.aeLocked(), "Half-press exposure lock did not release.");
    }

    private static void beginKeybindingProbe() {
        CameraSettings settings = SnapshotCameraController.settings();
        settings.reset();
        settings.select(CameraControl.ISO);
        keybindHudExpandedAtStart = SnapshotCameraController.hudExpanded();
        keybindShaderActiveAtStart = ShaderCompatibility.shaderPackActive();
        clickBinding(SnapshotKeybinds.increase());
        clickBinding(SnapshotKeybinds.autofocus());
        clickBinding(SnapshotKeybinds.flash());
        clickBinding(SnapshotKeybinds.burst());
        clickBinding(SnapshotKeybinds.toggleHudSize());
        clickBinding(SnapshotKeybinds.filmProfile());
        clickBinding(SnapshotKeybinds.aspectRatio());
        clickBinding(SnapshotKeybinds.mood());
        clickBinding(SnapshotKeybinds.exposureMode());
        clickBinding(SnapshotKeybinds.exposureAssist());
        clickBinding(SnapshotKeybinds.aeLock());
        clickBinding(SnapshotKeybinds.afLock());
    }

    private static void verifyPrimaryKeybindings() {
        CameraSettings settings = SnapshotCameraController.settings();
        require(settings.iso() == 200, "Increase key did not raise ISO from 100 to 200.");
        require(!settings.autoFocus(), "Autofocus key did not toggle AF off.");
        require(settings.flash() && settings.burst(), "Flash or burst key did not enable its drive option.");
        require(SnapshotCameraController.hudExpanded() != keybindHudExpandedAtStart,
            "HUD-size key did not toggle the viewfinder layout.");
        require(!"NEUTRAL".equals(settings.filmProfile().name()), "Film-profile key did not advance the profile.");
        require(!"NATIVE".equals(settings.aspectRatio().name()), "Aspect-ratio key did not advance the mask.");
        require(!"NATURAL".equals(settings.mood().name()), "Mood key did not advance the atmosphere preset.");
        require(settings.exposureMode() != ExposureMode.MANUAL, "Exposure-mode key did not turn the command dial.");
        require(!"HISTOGRAM".equals(settings.exposureAssist().name()),
            "Exposure-assist key did not advance the assist mode.");
        require(SnapshotCameraController.aeLocked() && SnapshotCameraController.afLocked(),
            "AE-L or AF-L key did not engage its lock.");
        require(ShaderCompatibility.shaderPackActive() == keybindShaderActiveAtStart,
            "A Snapshot keybinding unexpectedly toggled the active shader pack.");
    }

    private static void clickBinding(KeyMapping mapping) {
        KeyMapping.click(KeyMappingHelper.getBoundKeyOf(mapping));
    }

    private static void beginPerformancePreset(Minecraft client) {
        CameraSettings settings = SnapshotCameraController.settings();
        settings.reset();
        setPreset(settings, PERFORMANCE_PRESETS[performancePresetIndex]);
        settings.select(CameraControl.APERTURE);
        settings.adjust(-2);
        settings.setAutoFocus(false);
        settings.setFocusDistance(12.0);
        settings.setCaptureTechnique(CaptureTechnique.SINGLE);
        settings.setExposureBracket(ExposureBracket.OFF);
        client.player.connection.sendCommand("time set noon");
        client.setScreenAndShow(null);
        benchmarkFrames = 0;
        benchmarkStartedNanos = System.nanoTime();
        stage = 100;
        ticks = 0;
        SnapshotInit.LOGGER.info("[Snapshot] {} live depth-of-field benchmark started.",
            PERFORMANCE_PRESETS[performancePresetIndex].label());
    }

    private static void tickPerformance(Minecraft client) {
        OpticsPreset preset = PERFORMANCE_PRESETS[performancePresetIndex];
        if (stage == 100 && ticks >= 60) {
            double elapsedSeconds = Math.max(0.001, (System.nanoTime() - benchmarkStartedNanos) / 1_000_000_000.0);
            double framesPerSecond = benchmarkFrames / elapsedSeconds;
            JsonObject metric = new JsonObject();
            metric.addProperty("preset", preset.label());
            metric.addProperty("operation", "live_depth_of_field");
            metric.addProperty("frames", benchmarkFrames);
            metric.addProperty("seconds", elapsedSeconds);
            metric.addProperty("fps", framesPerSecond);
            metric.addProperty("renderer", ShaderCompatibility.rendererLabel());
            metric.addProperty("shader_pack_active", ShaderCompatibility.shaderPackActive());
            METRICS.add(metric);
            require(framesPerSecond >= 10.0, preset.label() + " live depth of field fell below 10 FPS.");

            benchmarkStartedNanos = System.nanoTime();
            SnapshotCameraController.triggerCapture();
            stage = 101;
            ticks = 0;
        } else if (stage == 101 && ticks >= 20 && !SnapshotCameraController.captureInProgress()) {
            addDurationMetric(preset.label(), "depth_of_field_capture", benchmarkStartedNanos, 15_000L);
            SnapshotCameraController.settings().setCaptureTechnique(CaptureTechnique.FOCUS_STACK);
            benchmarkStartedNanos = System.nanoTime();
            SnapshotCameraController.triggerCapture();
            stage = 102;
            ticks = 0;
        } else if (stage == 102 && ticks >= 20 && !SnapshotCameraController.captureInProgress()) {
            addDurationMetric(preset.label(), "focus_stack", benchmarkStartedNanos, 20_000L);
            CameraSettings settings = SnapshotCameraController.settings();
            settings.setCaptureTechnique(CaptureTechnique.SINGLE);
            settings.applyAstrophotographyPreset();
            setPreset(settings, preset);
            settings.select(CameraControl.SHUTTER);
            settings.adjust(-4);
            require(Math.abs(settings.shutterSeconds() - 1.0) < 0.001,
                "Performance long exposure was not configured to one second.");
            client.player.connection.sendCommand("time set midnight");
            stage = 103;
            ticks = 0;
        } else if (stage == 103 && ticks >= 30) {
            benchmarkStartedNanos = System.nanoTime();
            SnapshotCameraController.triggerCapture();
            stage = 104;
            ticks = 0;
        } else if (stage == 104 && ticks >= 40 && !SnapshotCameraController.captureInProgress()) {
            addDurationMetric(preset.label(), "one_second_long_exposure", benchmarkStartedNanos, 15_000L);
            performancePresetIndex++;
            if (performancePresetIndex >= PERFORMANCE_PRESETS.length) {
                pass(client, "Low, Medium, and Ultra performance benchmarks completed.");
            } else {
                beginPerformancePreset(client);
            }
        } else if (ticks > timeoutForStage()) {
            fail(client, "Performance test timed out in stage " + stage + ".", null);
        }
    }

    private static void setPreset(CameraSettings settings, OpticsPreset target) {
        settings.select(CameraControl.PRESET);
        for (int attempts = 0; attempts < OpticsPreset.values().length && settings.preset() != target; attempts++) {
            settings.adjust(1);
        }
        require(settings.preset() == target, "Could not select quality preset " + target.label() + ".");
    }

    private static void addDurationMetric(String preset, String operation, long startedNanos, long maximumMillis) {
        long durationMillis = Math.max(0L, Math.round((System.nanoTime() - startedNanos) / 1_000_000.0));
        JsonObject metric = new JsonObject();
        metric.addProperty("preset", preset);
        metric.addProperty("operation", operation);
        metric.addProperty("duration_ms", durationMillis);
        metric.addProperty("limit_ms", maximumMillis);
        METRICS.add(metric);
        require(durationMillis <= maximumMillis,
            preset + " " + operation + " exceeded " + maximumMillis + "ms (observed " + durationMillis + "ms).");
    }

    private static void grabQaScreenshot(Minecraft client, String name, String label) {
        Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1,
            message -> SnapshotInit.LOGGER.info("[Snapshot] {} QA screenshot: {}", label, message.getString()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static boolean isMultiplayerCase() {
        return TEST_CASE.toLowerCase(java.util.Locale.ROOT).startsWith("multiplayer");
    }

    private static int timeoutForStage() {
        return stage == 6 ? 700 : stage >= 60 && stage <= 62 ? 500 : stage >= 100 && stage <= 104 ? 800 : 400;
    }

    private static void initializeReport() {
        gameDirectory = FabricLoader.getInstance().getGameDir();
        captureDirectory = gameDirectory.resolve("screenshots/snapshot");
        reportPath = gameDirectory.resolve("snapshot-test-results").resolve(TEST_CASE + ".json");
        startedMillis = System.currentTimeMillis();
        capturesAtStart = countFiles(captureDirectory, ".png", ".source.png");
        metadataAtStart = countFiles(captureDirectory, ".snapshot.json", null);
        writeReport("running", "Smoke test started.", List.of(), 0, 0, 0);
    }

    private static void pass(Minecraft client, String message) {
        if (resultWritten) {
            return;
        }
        Evidence evidence = validateEvidence();
        if (!evidence.errors().isEmpty()) {
            fail(client, "Evidence validation failed: " + String.join("; ", evidence.errors()), null);
            return;
        }
        resultWritten = true;
        writeReport("passed", message, List.of(), evidence.captureCount(), evidence.metadataCount(), evidence.sourceCount());
        SnapshotInit.LOGGER.info("[Snapshot] {} Verified {} capture(s) and {} metadata file(s).",
            message, evidence.captureCount(), evidence.metadataCount());
        cleanup(client);
        stage = 7;
        ticks = 0;
    }

    private static void fail(Minecraft client, String message, Throwable failure) {
        if (resultWritten) {
            return;
        }
        resultWritten = true;
        Evidence evidence = validateEvidence();
        writeReport("failed", message, evidence.errors(), evidence.captureCount(), evidence.metadataCount(), evidence.sourceCount());
        if (failure == null) {
            SnapshotInit.LOGGER.error("[Snapshot] {}", message);
        } else {
            SnapshotInit.LOGGER.error("[Snapshot] {}", message, failure);
        }
        cleanup(client);
        stage = 7;
        ticks = 0;
    }

    private static Evidence validateEvidence() {
        List<String> errors = new ArrayList<>();
        List<Path> images = newFiles(captureDirectory, ".png", ".source.png");
        List<Path> sourceImages = newFiles(captureDirectory, ".source.png", null);
        List<Path> metadataFiles = newFiles(captureDirectory, ".snapshot.json", null);
        int expectedCaptures = expectedCaptureCount();
        if (images.size() < expectedCaptures) {
            errors.add("expected at least " + expectedCaptures + " new capture(s), found " + images.size());
        }
        if (metadataFiles.size() < expectedCaptures) {
            errors.add("expected at least " + expectedCaptures + " new metadata file(s), found " + metadataFiles.size());
        }
        if (SnapshotConfig.get().rawStyleExport && sourceImages.size() < expectedCaptures) {
            errors.add("expected at least " + expectedCaptures + " source negative(s), found " + sourceImages.size());
        }

        List<JsonObject> metadata = new ArrayList<>();
        for (Path image : images) {
            validateImage(image, errors, true);
            String base = stripExtension(image.getFileName().toString());
            Path sidecar = image.resolveSibling(base + ".snapshot.json");
            if (!Files.isRegularFile(sidecar)) {
                errors.add("missing metadata for " + image.getFileName());
            }
            if (SnapshotConfig.get().image2MapSidecar
                && !Files.isRegularFile(image.resolveSibling(base + ".image2map.txt"))) {
                errors.add("missing Image2Map sidecar for " + image.getFileName());
            }
            if (SnapshotConfig.get().rootScreenshotCopy
                && !Files.isRegularFile(gameDirectory.resolve("screenshots").resolve(image.getFileName()))) {
                errors.add("missing root screenshots copy for " + image.getFileName());
            }
        }
        for (Path sidecar : metadataFiles) {
            JsonObject parsed = validateMetadata(sidecar, errors);
            if (parsed != null) {
                metadata.add(parsed);
            }
        }
        for (Path sourceImage : sourceImages) {
            validateImage(sourceImage, errors, false);
        }

        validateCaseSpecificEvidence(images, metadata, errors);
        return new Evidence(images.size(), metadataFiles.size(), sourceImages.size(), List.copyOf(errors));
    }

    private static void validateImage(Path image, List<String> errors, boolean validateExposure) {
        try {
            if (Files.size(image) <= 0) {
                errors.add("empty PNG " + image.getFileName());
                return;
            }
            BufferedImage decoded = ImageIO.read(image.toFile());
            if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0) {
                errors.add("unreadable PNG " + image.getFileName());
                return;
            }
            int stepX = Math.max(1, decoded.getWidth() / 160);
            int stepY = Math.max(1, decoded.getHeight() / 90);
            int minimum = 255;
            int maximum = 0;
            int visible = 0;
            int nearBlack = 0;
            int nearWhite = 0;
            long luminanceTotal = 0L;
            for (int y = 0; y < decoded.getHeight(); y += stepY) {
                for (int x = 0; x < decoded.getWidth(); x += stepX) {
                    int argb = decoded.getRGB(x, y);
                    if ((argb >>> 24) == 0) {
                        continue;
                    }
                    int luminance = (int) Math.round(((argb >> 16) & 0xFF) * 0.2126
                        + ((argb >> 8) & 0xFF) * 0.7152 + (argb & 0xFF) * 0.0722);
                    minimum = Math.min(minimum, luminance);
                    maximum = Math.max(maximum, luminance);
                    luminanceTotal += luminance;
                    if (luminance <= 4) {
                        nearBlack++;
                    }
                    if (luminance >= 250) {
                        nearWhite++;
                    }
                    visible++;
                }
            }
            if (visible == 0) {
                errors.add("fully transparent PNG " + image.getFileName());
            } else if (maximum <= 1) {
                errors.add("effectively black PNG " + image.getFileName());
            } else if (maximum - minimum < 2) {
                errors.add("PNG has no visible tonal variation " + image.getFileName());
            } else if (validateExposure) {
                double mean = luminanceTotal / (double) visible;
                double blackRatio = nearBlack / (double) visible;
                double whiteRatio = nearWhite / (double) visible;
                if (mean < 6.0 || blackRatio > 0.985) {
                    errors.add("PNG is too dark to review " + image.getFileName()
                        + " (mean=" + String.format(java.util.Locale.ROOT, "%.1f", mean) + ")");
                }
                if (whiteRatio > 0.80) {
                    errors.add("PNG is mostly clipped white " + image.getFileName()
                        + " (clipped=" + String.format(java.util.Locale.ROOT, "%.1f%%", whiteRatio * 100.0) + ")");
                }
            }
        } catch (IOException exception) {
            errors.add("could not read PNG " + image.getFileName() + ": " + exception.getMessage());
        }
    }

    private static JsonObject validateMetadata(Path sidecar, List<String> errors) {
        try (Reader reader = Files.newBufferedReader(sidecar, StandardCharsets.UTF_8)) {
            JsonObject metadata = JsonParser.parseReader(reader).getAsJsonObject();
            for (String required : REQUIRED_METADATA) {
                if (!metadata.has(required)) {
                    errors.add(sidecar.getFileName() + " is missing metadata field " + required);
                }
            }
            if (metadata.has("width") && metadata.get("width").getAsInt() <= 0
                || metadata.has("height") && metadata.get("height").getAsInt() <= 0) {
                errors.add(sidecar.getFileName() + " has invalid dimensions");
            }
            if (metadata.has("image") && metadata.has("width") && metadata.has("height")) {
                Path image = Path.of(metadata.get("image").getAsString());
                BufferedImage decoded = ImageIO.read(image.toFile());
                if (decoded == null) {
                    errors.add(sidecar.getFileName() + " references an unreadable image");
                } else if (decoded.getWidth() != metadata.get("width").getAsInt()
                    || decoded.getHeight() != metadata.get("height").getAsInt()) {
                    errors.add(sidecar.getFileName() + " dimensions do not match its PNG");
                }
            }
            return metadata;
        } catch (Exception exception) {
            errors.add("invalid metadata " + sidecar.getFileName() + ": " + exception.getMessage());
            return null;
        }
    }

    private static void validateCaseSpecificEvidence(List<Path> images, List<JsonObject> metadata, List<String> errors) {
        List<String> names = images.stream().map(path -> path.getFileName().toString()).toList();
        if ("suite".equalsIgnoreCase(TEST_CASE)) {
            requireNameContaining(names, "_HDR", errors);
            requireNameContaining(names, "_FOCUS_STACK", errors);
            requireNameContaining(names, "_PANORAMA", errors);
            if (metadata.stream().noneMatch(value -> value.has("astrophotography") && value.get("astrophotography").getAsBoolean())) {
                errors.add("suite did not create an astrophotography capture");
            }
        } else if ("focus".equalsIgnoreCase(TEST_CASE)) {
            requireNameContaining(names, "_FOCUS_STACK", errors);
        } else if ("clock".equalsIgnoreCase(TEST_CASE)) {
            if (metadata.stream().noneMatch(value -> value.has("celestial_condition")
                && "night_sky".equals(value.get("celestial_condition").getAsString()))) {
                errors.add("clock capture did not record the midnight celestial condition");
            }
        } else if ("astro2".equalsIgnoreCase(TEST_CASE)) {
            for (String mode : List.of("DENOISE", "DEEP SKY", "STAR TRAILS")) {
                if (metadata.stream().noneMatch(value -> value.has("astro_stack_mode")
                    && mode.equals(value.get("astro_stack_mode").getAsString()))) {
                    errors.add("astro2 did not create the " + mode + " stack mode");
                }
            }
        } else if ("ui".equalsIgnoreCase(TEST_CASE)) {
            requireFreshScreenshot("snapshot_lighttable_qa.png", errors);
            requireFreshScreenshot("snapshot_journal_qa.png", errors);
        } else if ("items".equalsIgnoreCase(TEST_CASE)) {
            requireFreshScreenshot("snapshot_item_art_qa.png", errors);
        } else if ("controls".equalsIgnoreCase(TEST_CASE)) {
            requireFreshScreenshot("snapshot_focus_points_qa.png", errors);
            requireFreshScreenshot("snapshot_command_dial_qa.png", errors);
            requireFreshScreenshot("snapshot_controls_lighttable_qa.png", errors);
            requireFreshScreenshot("snapshot_photo_review_qa.png", errors);
        } else if ("performance".equalsIgnoreCase(TEST_CASE)) {
            for (OpticsPreset preset : PERFORMANCE_PRESETS) {
                long captures = metadata.stream().filter(value -> value.has("quality_preset")
                    && preset.label().equals(value.get("quality_preset").getAsString())).count();
                if (captures < 3) {
                    errors.add("performance test created only " + captures + " " + preset.label() + " capture(s)");
                }
                boolean focusStack = metadata.stream().anyMatch(value -> value.has("quality_preset")
                    && preset.label().equals(value.get("quality_preset").getAsString())
                    && value.has("capture_technique") && "FOCUS STACK".equals(value.get("capture_technique").getAsString()));
                boolean longExposure = metadata.stream().anyMatch(value -> value.has("quality_preset")
                    && preset.label().equals(value.get("quality_preset").getAsString())
                    && value.has("actual_exposure_seconds") && value.get("actual_exposure_seconds").getAsDouble() >= 0.75);
                if (!focusStack) {
                    errors.add("performance test is missing the " + preset.label() + " focus stack");
                }
                if (!longExposure) {
                    errors.add("performance test is missing the " + preset.label() + " long exposure");
                }
            }
        }
        if (!EXPECTED_RENDERER.isBlank()) {
            for (JsonObject value : metadata) {
                if (!value.has("renderer") || !EXPECTED_RENDERER.equals(value.get("renderer").getAsString())) {
                    errors.add("expected renderer " + EXPECTED_RENDERER + " but metadata recorded "
                        + (value.has("renderer") ? value.get("renderer").getAsString() : "<missing>"));
                }
            }
        }
        if (!EXPECTED_SHADER.isBlank()) {
            boolean expected = Boolean.parseBoolean(EXPECTED_SHADER);
            for (JsonObject value : metadata) {
                if (!value.has("shader_pack_active") || value.get("shader_pack_active").getAsBoolean() != expected) {
                    errors.add("expected shader_pack_active=" + expected + " in "
                        + (value.has("image") ? value.get("image").getAsString() : "metadata"));
                }
            }
        }
    }

    private static void requireNameContaining(List<String> names, String expected, List<String> errors) {
        if (names.stream().noneMatch(name -> name.contains(expected))) {
            errors.add("missing capture variant " + expected);
        }
    }

    private static void requireFreshScreenshot(String name, List<String> errors) {
        Path screenshot = gameDirectory.resolve("screenshots").resolve(name);
        if (!Files.isRegularFile(screenshot) || !isFresh(screenshot)) {
            errors.add("missing fresh QA screenshot " + name);
            return;
        }
        validateImage(screenshot, errors, false);
    }

    private static int expectedCaptureCount() {
        return switch (TEST_CASE.toLowerCase(java.util.Locale.ROOT)) {
            case "suite" -> 4;
            case "astro2" -> 3;
            case "performance" -> 9;
            case "focus", "image2map", "clock", "renderer", "access", "controls",
                "multiplayer_denied", "multiplayer_allowed" -> 1;
            default -> 0;
        };
    }

    private static List<Path> newFiles(Path directory, String suffix, String excludedSuffix) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(suffix))
                .filter(path -> excludedSuffix == null || !path.getFileName().toString().endsWith(excludedSuffix))
                .filter(SnapshotDevSmokeTest::isFresh)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private static int countFiles(Path directory, String suffix, String excludedSuffix) {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return (int) files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(suffix))
                .filter(path -> excludedSuffix == null || !path.getFileName().toString().endsWith(excludedSuffix))
                .count();
        } catch (IOException exception) {
            return 0;
        }
    }

    private static boolean isFresh(Path path) {
        try {
            FileTime modified = Files.getLastModifiedTime(path);
            return modified.toMillis() >= startedMillis - 1_000L;
        } catch (IOException exception) {
            return false;
        }
    }

    private static void writeReport(String status, String message, List<String> errors, int captures, int metadata, int sources) {
        JsonObject report = new JsonObject();
        report.addProperty("test_case", TEST_CASE);
        report.addProperty("status", status);
        report.addProperty("message", message);
        report.addProperty("started_at", Instant.ofEpochMilli(startedMillis).toString());
        report.addProperty("captures_before", capturesAtStart);
        report.addProperty("metadata_before", metadataAtStart);
        report.addProperty("new_captures", captures);
        report.addProperty("new_metadata", metadata);
        report.addProperty("new_source_negatives", sources);
        report.addProperty("expected_renderer", EXPECTED_RENDERER);
        report.addProperty("expected_shader_pack_active", EXPECTED_SHADER);
        report.addProperty("expected_environment_permission", EXPECTED_ENVIRONMENT);
        report.add("metrics", METRICS.deepCopy());
        JsonArray errorArray = new JsonArray();
        errors.forEach(errorArray::add);
        report.add("errors", errorArray);
        try {
            Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, JSON.toJson(report), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            SnapshotInit.LOGGER.error("[Snapshot] Could not write smoke-test result {}.", reportPath, exception);
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static void cleanup(Minecraft client) {
        if (SnapshotCameraController.active()) {
            SnapshotCameraController.toggle();
        }
        if (client.player != null && !isMultiplayerCase()) {
            if ("access".equalsIgnoreCase(TEST_CASE)) {
                client.player.connection.sendCommand("gamemode creative @s");
            }
            client.player.connection.sendCommand("time set day");
            client.player.connection.sendCommand("clear @s snapshot:camera");
            client.player.connection.sendCommand("clear @s snapshot:photographic_paper");
            client.player.connection.sendCommand("clear @s snapshot:tripod");
            client.player.connection.sendCommand("clear @s snapshot:photograph");
            client.player.connection.sendCommand("clear @s minecraft:filled_map");
            client.player.connection.sendCommand("clear @s minecraft:bundle");
        }
    }

    private static int countItem(Minecraft client, Item item) {
        if (client.player == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            if (client.player.getInventory().getItem(slot).getItem() == item) {
                count += client.player.getInventory().getItem(slot).getCount();
            }
        }
        return count;
    }

    private record Evidence(int captureCount, int metadataCount, int sourceCount, List<String> errors) {
    }
}
