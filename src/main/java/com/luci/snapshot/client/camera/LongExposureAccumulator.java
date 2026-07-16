package com.luci.snapshot.client.camera;

import com.luci.snapshot.config.SnapshotConfig;
import com.mojang.blaze3d.platform.NativeImage;

final class LongExposureAccumulator {
    private final String title;
    private final CameraSettings settings;
    private final SceneDepthMap depthMap;
    private final boolean liveFilmBaked;
    private final boolean stabilized;
    private final float lockedYaw;
    private final float lockedPitch;
    private final long startedNanos;
    private final long maximumEndNanos;
    private final long sampleIntervalNanos;
    private final int maximumSamples;
    private boolean stopRequested;
    private boolean sampleInFlight;
    private int samples;
    private long nextSampleNanos;
    private int width;
    private int height;
    private float[] redSum;
    private float[] greenSum;
    private float[] blueSum;
    private short[] redMaximum;
    private short[] greenMaximum;
    private short[] blueMaximum;

    LongExposureAccumulator(String title, CameraSettings settings, SceneDepthMap depthMap, boolean liveFilmBaked,
                            boolean stabilized, float lockedYaw, float lockedPitch) {
        this.title = title;
        this.settings = settings;
        this.depthMap = depthMap;
        this.liveFilmBaked = liveFilmBaked;
        this.stabilized = stabilized;
        this.lockedYaw = lockedYaw;
        this.lockedPitch = lockedPitch;
        startedNanos = System.nanoTime();
        double maximumSeconds = settings.bulb() ? SnapshotConfig.get().maxBulbSeconds : settings.shutterSeconds();
        long durationNanos = (long) (maximumSeconds * 1_000_000_000L);
        maximumEndNanos = startedNanos + durationNanos;
        int timedSamples = Math.max(2, (int) Math.ceil(maximumSeconds * SnapshotConfig.get().longExposureSamplesPerSecond));
        maximumSamples = Math.min(SnapshotConfig.get().maxLongExposureSamples, timedSamples);
        sampleIntervalNanos = settings.bulb()
            ? 1_000_000_000L / SnapshotConfig.get().longExposureSamplesPerSecond
            : Math.max(1L, durationNanos / Math.max(1, maximumSamples - 1));
        nextSampleNanos = startedNanos;
    }

    String title() {
        return title;
    }

    CameraSettings settings() {
        return settings;
    }

    SceneDepthMap depthMap() {
        return depthMap;
    }

    boolean liveFilmBaked() {
        return liveFilmBaked;
    }

    boolean stabilized() {
        return stabilized;
    }

    float lockedYaw() {
        return lockedYaw;
    }

    float lockedPitch() {
        return lockedPitch;
    }

    boolean bulb() {
        return settings.bulb();
    }

    void requestStop() {
        stopRequested = true;
    }

    boolean shouldSample(long now) {
        return !sampleInFlight && samples < maximumSamples && now >= nextSampleNanos
            && (!expired(now) || samples == 0);
    }

    void markSampleInFlight() {
        sampleInFlight = true;
    }

    void accept(NativeImage frame) {
        NativeImage cropped = frame;
        try {
            cropped = PhotoProcessor.cropToAspect(frame, settings.aspectRatio());
            initialize(cropped.getWidth(), cropped.getHeight());
            if (cropped.getWidth() != width || cropped.getHeight() != height) {
                return;
            }
            int[] pixels = cropped.getPixels();
            for (int index = 0; index < pixels.length; index++) {
                int pixel = pixels[index];
                int red = (pixel >> 16) & 0xFF;
                int green = (pixel >> 8) & 0xFF;
                int blue = pixel & 0xFF;
                redSum[index] += red;
                greenSum[index] += green;
                blueSum[index] += blue;
                redMaximum[index] = (short) Math.max(redMaximum[index] & 0xFFFF, red);
                greenMaximum[index] = (short) Math.max(greenMaximum[index] & 0xFFFF, green);
                blueMaximum[index] = (short) Math.max(blueMaximum[index] & 0xFFFF, blue);
            }
            samples++;
            nextSampleNanos = startedNanos + (long) samples * sampleIntervalNanos;
        } finally {
            sampleInFlight = false;
            if (cropped != frame) {
                cropped.close();
            }
            frame.close();
        }
    }

    boolean readyToFinish(long now) {
        return !sampleInFlight && samples > 0 && expired(now);
    }

    NativeImage finishImage() {
        NativeImage result = new NativeImage(width, height, false);
        AstroStackMode mode = settings.astrophotography() ? settings.astroStackMode() : AstroStackMode.DEEP_SKY;
        double trailStrength = settings.astrophotography()
            ? switch (mode) {
                case DENOISE -> 0.0;
                case DEEP_SKY -> 0.42;
                case STAR_TRAILS -> 0.96;
            }
            : 0.46;
        for (int index = 0; index < width * height; index++) {
            double redAverage = redSum[index] / samples;
            double greenAverage = greenSum[index] / samples;
            double blueAverage = blueSum[index] / samples;
            double red = redAverage + Math.max(0.0, (redMaximum[index] & 0xFFFF) - redAverage) * trailStrength;
            double green = greenAverage + Math.max(0.0, (greenMaximum[index] & 0xFFFF) - greenAverage) * trailStrength;
            double blue = blueAverage + Math.max(0.0, (blueMaximum[index] & 0xFFFF) - blueAverage) * trailStrength;
            if (settings.astrophotography() && mode == AstroStackMode.DENOISE) {
                double noise = ((redMaximum[index] & 0xFFFF) - redAverage
                    + (greenMaximum[index] & 0xFFFF) - greenAverage
                    + (blueMaximum[index] & 0xFFFF) - blueAverage) / 3.0;
                double luminance = red * 0.2126 + green * 0.7152 + blue * 0.0722;
                double shadow = 1.0 - clamp(luminance / 128.0, 0.0, 1.0);
                double chromaRetention = clamp(1.0 - noise * shadow / 72.0, 0.58, 1.0);
                red = mix(luminance, red, chromaRetention);
                green = mix(luminance, green, chromaRetention);
                blue = mix(luminance, blue, chromaRetention);
            }
            if (settings.astrophotography() && settings.darkFrameSubtraction()) {
                double darkSignal = modeledDarkSignal(index);
                red -= darkSignal * 1.08;
                green -= darkSignal * 0.92;
                blue -= darkSignal * 1.18;
            }
            int x = index % width;
            int y = index / width;
            result.setPixel(x, y, 0xFF000000
                | (clamp((int) Math.round(red), 0, 255) << 16)
                | (clamp((int) Math.round(green), 0, 255) << 8)
                | clamp((int) Math.round(blue), 0, 255));
        }
        return result;
    }

    double progress(long now) {
        if (settings.bulb()) {
            return Math.max(0.0, Math.min(1.0, (now - startedNanos) / (double) (maximumEndNanos - startedNanos)));
        }
        return Math.max(0.0, Math.min(1.0, (now - startedNanos) / (double) (maximumEndNanos - startedNanos)));
    }

    double elapsedSeconds(long now) {
        return Math.max(0.0, (now - startedNanos) / 1_000_000_000.0);
    }

    int samples() {
        return samples;
    }

    private boolean expired(long now) {
        return stopRequested || now >= maximumEndNanos;
    }

    private void initialize(int imageWidth, int imageHeight) {
        if (redSum != null) {
            return;
        }
        width = imageWidth;
        height = imageHeight;
        int size = width * height;
        redSum = new float[size];
        greenSum = new float[size];
        blueSum = new float[size];
        redMaximum = new short[size];
        greenMaximum = new short[size];
        blueMaximum = new short[size];
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private double modeledDarkSignal(int pixelIndex) {
        long hash = (pixelIndex * 0x9E3779B97F4A7C15L) ^ 0xD1B54A32D192ED03L;
        double fixedPattern = ((hash >>> 40) & 0xFFFF) / 65535.0;
        double exposureScale = Math.sqrt(Math.max(1.0, settings.shutterSeconds()));
        double isoScale = Math.sqrt(settings.iso() / 100.0);
        return Math.min(7.0, (0.35 + fixedPattern * 1.15) * exposureScale * isoScale * 0.24);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double mix(double start, double end, double amount) {
        return start + (end - start) * amount;
    }
}
