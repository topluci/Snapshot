package com.luci.snapshot.client.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LiveFilmPipelineTest {
    private static final float EPSILON = 0.0001F;

    @Test
    void activeShaderPacksUseAConservativeLiveDepthRadius() {
        CameraSettings settings = new CameraSettings();
        settings.setPreset(OpticsPreset.SCREENSHOT_ULTRA);
        settings.select(CameraControl.APERTURE);
        settings.adjust(-10);

        float nativeBlur = LiveFilmPipeline.calculateMaximumBlur(settings, false);
        float shaderBlur = LiveFilmPipeline.calculateMaximumBlur(settings, true);

        assertTrue(nativeBlur > shaderBlur);
        assertTrue(shaderBlur <= 7.5F);

        settings.adjust(10);
        assertTrue(LiveFilmPipeline.calculateMaximumBlur(settings, true) < shaderBlur);
    }

    @Test
    void focusStackingDisablesLiveDepthBlurForEveryRenderer() {
        CameraSettings settings = new CameraSettings();
        settings.setCaptureTechnique(CaptureTechnique.FOCUS_STACK);

        assertEquals(0.0F, LiveFilmPipeline.calculateMaximumBlur(settings, false), EPSILON);
        assertEquals(0.0F, LiveFilmPipeline.calculateMaximumBlur(settings, true), EPSILON);
    }
}
