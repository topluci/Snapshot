package com.luci.snapshot.config;

import com.luci.snapshot.SnapshotInit;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class SnapshotConfig {
    private static final String FILE_NAME = "snapshot.properties";
    public static final List<ConfigSpec> SPECS = List.of(
        new ConfigSpec("survival.require_camera", "Require camera", "Survival", "true", 0.0D, 1.0D, false),
        new ConfigSpec("survival.require_paper", "Require paper", "Survival", "true", 0.0D, 1.0D, false),
        new ConfigSpec("capture.png_export", "Export PNG", "Capture", "true", 0.0D, 1.0D, false),
        new ConfigSpec("capture.root_screenshot_copy", "Root screenshot copy", "Capture", "true", 0.0D, 1.0D, false),
        new ConfigSpec("capture.server_photos", "Create photo item", "Capture", "true", 0.0D, 1.0D, false),
        new ConfigSpec("capture.map_photos", "Create map photo", "Capture", "true", 0.0D, 1.0D, false),
        new ConfigSpec("capture.image2map_sidecar", "Image2Map sidecar", "Capture", "true", 0.0D, 1.0D, false),
        new ConfigSpec("capture.image2map_auto", "Automatic Image2Map handoff", "Capture", "true", 0.0D, 1.0D, false),
        new ConfigSpec("capture.raw_style_export", "Export source negative", "Capture", "true", 0.0D, 1.0D, false),
        new ConfigSpec("capture.thumbnail_size", "Thumbnail size", "Capture", "128", 32.0D, 128.0D, false),
        new ConfigSpec("capture.long_exposure_samples_per_second", "Exposure samples/second", "Capture", "4", 1.0D, 8.0D, false),
        new ConfigSpec("capture.max_long_exposure_samples", "Maximum exposure samples", "Capture", "64", 8.0D, 128.0D, false),
        new ConfigSpec("capture.max_bulb_seconds", "Maximum bulb seconds", "Capture", "120", 10.0D, 300.0D, false),
        new ConfigSpec("optics.default_preset", "Default preset", "Optics", "ultra", 0.0D, 0.0D, false),
        new ConfigSpec("optics.shader_compat", "Shader compatibility", "Optics", "true", 0.0D, 1.0D, false),
        new ConfigSpec("optics.live_film_pipeline", "Live film processing", "Optics", "true", 0.0D, 1.0D, false),
        new ConfigSpec("atmosphere.viewfinder_particles", "Viewfinder particles", "Atmosphere", "true", 0.0D, 1.0D, false),
        new ConfigSpec("atmosphere.acoustic_isolation", "Acoustic isolation", "Atmosphere", "true", 0.0D, 1.0D, false),
        new ConfigSpec("environment.client_preview", "Environment preview", "Environment", "true", 0.0D, 1.0D, false),
        new ConfigSpec("environment.permission_level", "Apply permission", "Environment", "2", 0.0D, 4.0D, false)
    );

    private static SnapshotConfig instance = defaults();

    public final boolean requireCamera;
    public final boolean requirePaper;
    public final boolean pngExport;
    public final boolean rootScreenshotCopy;
    public final boolean serverPhotos;
    public final boolean mapPhotos;
    public final boolean image2MapSidecar;
    public final boolean image2MapAuto;
    public final boolean rawStyleExport;
    public final int thumbnailSize;
    public final int longExposureSamplesPerSecond;
    public final int maxLongExposureSamples;
    public final int maxBulbSeconds;
    public final String defaultPreset;
    public final boolean shaderCompat;
    public final boolean liveFilmPipeline;
    public final boolean viewfinderParticles;
    public final boolean acousticIsolation;
    public final boolean environmentPreview;
    public final int environmentPermissionLevel;

    private SnapshotConfig(Properties properties) {
        requireCamera = readBoolean(properties, "survival.require_camera", true);
        requirePaper = readBoolean(properties, "survival.require_paper", true);
        pngExport = readBoolean(properties, "capture.png_export", true);
        rootScreenshotCopy = readBoolean(properties, "capture.root_screenshot_copy", true);
        serverPhotos = readBoolean(properties, "capture.server_photos", true);
        mapPhotos = readBoolean(properties, "capture.map_photos", true);
        image2MapSidecar = readBoolean(properties, "capture.image2map_sidecar", true);
        image2MapAuto = readBoolean(properties, "capture.image2map_auto", true);
        rawStyleExport = readBoolean(properties, "capture.raw_style_export", true);
        thumbnailSize = readInt(properties, "capture.thumbnail_size", 128, 32, 128);
        longExposureSamplesPerSecond = readInt(properties, "capture.long_exposure_samples_per_second", 4, 1, 8);
        maxLongExposureSamples = readInt(properties, "capture.max_long_exposure_samples", 64, 8, 128);
        maxBulbSeconds = readInt(properties, "capture.max_bulb_seconds", 120, 10, 300);
        defaultPreset = readPreset(properties, "optics.default_preset", "ultra");
        shaderCompat = readBoolean(properties, "optics.shader_compat", true);
        liveFilmPipeline = readBoolean(properties, "optics.live_film_pipeline", true);
        viewfinderParticles = readBoolean(properties, "atmosphere.viewfinder_particles", true);
        acousticIsolation = readBoolean(properties, "atmosphere.acoustic_isolation", true);
        environmentPreview = readBoolean(properties, "environment.client_preview", true);
        environmentPermissionLevel = readInt(properties, "environment.permission_level", 2, 0, 4);
    }

    public static SnapshotConfig get() {
        return instance;
    }

    public static void load() {
        Path path = path();
        Properties properties = loadEditableProperties();
        instance = new SnapshotConfig(properties);
        write(path, properties);
    }

    public static void save(Properties properties) {
        Properties merged = defaultProperties();
        merged.putAll(properties);
        instance = new SnapshotConfig(merged);
        write(path(), toProperties(instance));
    }

    public static Properties loadEditableProperties() {
        Properties properties = defaultProperties();
        Path path = path();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            } catch (IOException exception) {
                SnapshotInit.LOGGER.warn("[Snapshot] Could not read config file, using defaults.", exception);
            }
        }
        return properties;
    }

    public static Properties defaultEditableProperties() {
        return defaultProperties();
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static SnapshotConfig defaults() {
        return new SnapshotConfig(defaultProperties());
    }

    private static Properties defaultProperties() {
        Properties properties = new Properties();
        for (ConfigSpec spec : SPECS) {
            properties.setProperty(spec.key(), spec.defaultValue());
        }
        return properties;
    }

    private static Properties toProperties(SnapshotConfig config) {
        Properties properties = new Properties();
        properties.setProperty("survival.require_camera", Boolean.toString(config.requireCamera));
        properties.setProperty("survival.require_paper", Boolean.toString(config.requirePaper));
        properties.setProperty("capture.png_export", Boolean.toString(config.pngExport));
        properties.setProperty("capture.root_screenshot_copy", Boolean.toString(config.rootScreenshotCopy));
        properties.setProperty("capture.server_photos", Boolean.toString(config.serverPhotos));
        properties.setProperty("capture.map_photos", Boolean.toString(config.mapPhotos));
        properties.setProperty("capture.image2map_sidecar", Boolean.toString(config.image2MapSidecar));
        properties.setProperty("capture.image2map_auto", Boolean.toString(config.image2MapAuto));
        properties.setProperty("capture.raw_style_export", Boolean.toString(config.rawStyleExport));
        properties.setProperty("capture.thumbnail_size", Integer.toString(config.thumbnailSize));
        properties.setProperty("capture.long_exposure_samples_per_second", Integer.toString(config.longExposureSamplesPerSecond));
        properties.setProperty("capture.max_long_exposure_samples", Integer.toString(config.maxLongExposureSamples));
        properties.setProperty("capture.max_bulb_seconds", Integer.toString(config.maxBulbSeconds));
        properties.setProperty("optics.default_preset", config.defaultPreset);
        properties.setProperty("optics.shader_compat", Boolean.toString(config.shaderCompat));
        properties.setProperty("optics.live_film_pipeline", Boolean.toString(config.liveFilmPipeline));
        properties.setProperty("atmosphere.viewfinder_particles", Boolean.toString(config.viewfinderParticles));
        properties.setProperty("atmosphere.acoustic_isolation", Boolean.toString(config.acousticIsolation));
        properties.setProperty("environment.client_preview", Boolean.toString(config.environmentPreview));
        properties.setProperty("environment.permission_level", Integer.toString(config.environmentPermissionLevel));
        return properties;
    }

    private static void write(Path path, Properties properties) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "Snapshot configuration");
            }
        } catch (IOException exception) {
            SnapshotInit.LOGGER.warn("[Snapshot] Could not write config file.", exception);
        }
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key, Boolean.toString(fallback)).trim();
        return "true".equalsIgnoreCase(value) || (!"false".equalsIgnoreCase(value) && fallback);
    }

    private static int readInt(Properties properties, String key, int fallback, int min, int max) {
        try {
            int parsed = Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String readPreset(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key, fallback).trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "low", "medium", "ultra", "screenshot_ultra" -> value;
            default -> fallback;
        };
    }

    public record ConfigSpec(String key, String label, String section, String defaultValue, double min, double max, boolean decimal) {
        public boolean booleanValue() {
            return "true".equals(defaultValue) || "false".equals(defaultValue);
        }

        public boolean textValue() {
            return min == 0.0D && max == 0.0D && !booleanValue();
        }

        public String rangeText() {
            if (booleanValue()) {
                return "true/false";
            }
            if (textValue()) {
                return "low, medium, ultra, screenshot_ultra";
            }
            return (int) min + "-" + (int) max;
        }
    }
}
