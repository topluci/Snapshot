package com.luci.snapshot.client.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

class PhotoPipelineTest {
    @Test
    void aspectRatioCroppingIsCenteredAndUsesExpectedDimensions() {
        try (NativeImage source = gradient(160, 90)) {
            assertSame(source, PhotoProcessor.cropToAspect(source, AspectRatio.NATIVE));

            try (NativeImage square = PhotoProcessor.cropToAspect(source, AspectRatio.SQUARE);
                 NativeImage fourThree = PhotoProcessor.cropToAspect(source, AspectRatio.FOUR_THREE);
                 NativeImage anamorphic = PhotoProcessor.cropToAspect(source, AspectRatio.ANAMORPHIC)) {
                assertNotSame(source, square);
                assertEquals(90, square.getWidth());
                assertEquals(90, square.getHeight());
                assertEquals(source.getPixel(35, 0), square.getPixel(0, 0));

                assertEquals(120, fourThree.getWidth());
                assertEquals(90, fourThree.getHeight());
                assertEquals(source.getPixel(20, 0), fourThree.getPixel(0, 0));

                assertEquals(160, anamorphic.getWidth());
                assertEquals(66, anamorphic.getHeight());
                assertEquals(source.getPixel(0, 12), anamorphic.getPixel(0, 0));
            }
        }
    }

    @Test
    void exposureAnalysisFindsClippedHighlightsAndCrushedShadows() {
        try (NativeImage image = new NativeImage(64, 36, false)) {
            for (int y = 0; y < 36; y++) {
                for (int x = 0; x < 64; x++) {
                    image.setPixel(x, y, x < 32 ? 0xFF000000 : 0xFFFFFFFF);
                }
            }

            ExposureAnalysis analysis = ExposureAnalysis.analyze(image);
            assertEquals(0.5, analysis.clippedHighlights(), 0.0001);
            assertEquals(0.5, analysis.crushedShadows(), 0.0001);
            assertEquals(32, analysis.gridWidth());
            assertEquals(18, analysis.gridHeight());
            assertEquals(0, analysis.luminanceAt(-50, -50));
            assertEquals(255, analysis.luminanceAt(50, -50));
            assertEquals(1152, analysis.redHistogram()[0]);
            assertEquals(1152, analysis.redHistogram()[63]);
        }
    }

    @Test
    void processingPreservesDimensionsAndOpaqueAlpha() {
        CameraSettings settings = new CameraSettings();
        try (NativeImage image = gradient(32, 18)) {
            PhotoProcessor.process(image, settings, 0.0F, 0.0F);

            assertEquals(32, image.getWidth());
            assertEquals(18, image.getHeight());
            for (int pixel : image.getPixels()) {
                assertEquals(0xFF, (pixel >>> 24) & 0xFF);
            }
        }
    }

    @Test
    void wideApertureRetainsDaylightHighlightDetail() {
        CameraSettings settings = new CameraSettings();
        settings.select(CameraControl.APERTURE);
        settings.adjust(-2);
        try (NativeImage image = solid(16, 16, 0xFFC0D8F0)) {
            PhotoProcessor.process(image, settings, 0.0F, 0.0F);

            int pixel = image.getPixel(8, 8);
            assertTrue(((pixel >> 16) & 0xFF) < 255);
            assertTrue(((pixel >> 8) & 0xFF) < 255);
            assertTrue((pixel & 0xFF) < 255);
        }
    }

    @Test
    void twoSecondAstroExposureKeepsNightSceneReadable() {
        CameraSettings settings = new CameraSettings();
        settings.applyAstrophotographyPreset();
        settings.select(CameraControl.SHUTTER);
        settings.adjust(-3);
        try (NativeImage image = solid(16, 16, 0xFF101820)) {
            PhotoProcessor.process(image, settings, 0.0F, 0.0F, null, false, 2.0);

            int pixel = image.getPixel(8, 8);
            int luminance = (int) Math.round(((pixel >> 16) & 0xFF) * 0.2126
                + ((pixel >> 8) & 0xFF) * 0.7152 + (pixel & 0xFF) * 0.0722);
            assertTrue(luminance >= 24, () -> "Astro luminance was only " + luminance);
        }
    }

    @Test
    void longExposureAveragesFramesAndCanFinishAfterAStopRequest() {
        CameraSettings settings = new CameraSettings();
        settings.select(CameraControl.SHUTTER);
        for (int index = 0; index < 7; index++) {
            settings.adjust(1);
        }
        LongExposureAccumulator accumulator = new LongExposureAccumulator(
            "test", settings, null, false, true, 12.0F, -3.0F
        );

        accumulator.markSampleInFlight();
        accumulator.accept(solid(4, 4, 0xFF204060));
        accumulator.requestStop();

        assertEquals(1, accumulator.samples());
        assertTrue(accumulator.readyToFinish(System.nanoTime()));
        try (NativeImage result = accumulator.finishImage()) {
            assertEquals(4, result.getWidth());
            assertEquals(4, result.getHeight());
            assertEquals(0xFF204060, result.getPixel(0, 0));
        }
    }

    @Test
    void everyFilmMoodAndFilterCombinationProducesReadablePixels() {
        for (FilmProfile film : FilmProfile.values()) {
            for (MoodPreset mood : MoodPreset.values()) {
                for (OpticalFilter filter : OpticalFilter.values()) {
                    CameraSettings settings = new CameraSettings();
                    for (int index = 0; index < film.ordinal(); index++) {
                        settings.cycleFilmProfile();
                    }
                    for (int index = 0; index < mood.ordinal(); index++) {
                        settings.cycleMood();
                    }
                    settings.setFilter(filter);
                    try (NativeImage image = gradient(12, 8)) {
                        PhotoProcessor.process(image, settings, 2.5F, -1.5F);
                        for (int pixel : image.getPixels()) {
                            assertEquals(0xFF, (pixel >>> 24) & 0xFF,
                                () -> film + " / " + mood + " / " + filter + " produced transparent pixels");
                        }
                    }
                }
            }
        }
    }

    @Test
    void everyAspectRatioHandlesTheSmallestPossibleImage() {
        for (AspectRatio ratio : AspectRatio.values()) {
            try (NativeImage source = gradient(1, 1)) {
                NativeImage result = PhotoProcessor.cropToAspect(source, ratio);
                try {
                    assertTrue(result.getWidth() >= 1);
                    assertTrue(result.getHeight() >= 1);
                } finally {
                    if (result != source) {
                        result.close();
                    }
                }
            }
        }
    }

    @Test
    void astroStackModesIncreaseHighlightRetentionInOrder() {
        int denoise = stackedHighlight(AstroStackMode.DENOISE);
        int deepSky = stackedHighlight(AstroStackMode.DEEP_SKY);
        int starTrails = stackedHighlight(AstroStackMode.STAR_TRAILS);

        assertTrue(denoise < deepSky, () -> denoise + " should be below " + deepSky);
        assertTrue(deepSky < starTrails, () -> deepSky + " should be below " + starTrails);
    }

    private static int stackedHighlight(AstroStackMode mode) {
        CameraSettings settings = new CameraSettings();
        settings.applyAstrophotographyPreset();
        settings.setAstroStackMode(mode);
        settings.toggleDarkFrameSubtraction();
        LongExposureAccumulator accumulator = new LongExposureAccumulator(
            "stack-test", settings, null, false, true, 0.0F, 0.0F
        );
        accumulator.accept(solid(1, 1, 0xFF000000));
        accumulator.accept(solid(1, 1, 0xFFFFFFFF));
        accumulator.requestStop();
        try (NativeImage result = accumulator.finishImage()) {
            return (result.getPixel(0, 0) >> 16) & 0xFF;
        }
    }

    private static NativeImage gradient(int width, int height) {
        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = x * 255 / Math.max(1, width - 1);
                int green = y * 255 / Math.max(1, height - 1);
                int blue = (x + y) * 255 / Math.max(1, width + height - 2);
                image.setPixel(x, y, 0xFF000000 | (red << 16) | (green << 8) | blue);
            }
        }
        return image;
    }

    private static NativeImage solid(int width, int height, int color) {
        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixel(x, y, color);
            }
        }
        return image;
    }
}
