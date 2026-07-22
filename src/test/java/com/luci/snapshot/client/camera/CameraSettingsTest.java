package com.luci.snapshot.client.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CameraSettingsTest {
    private static final double EPSILON = 0.0001;

    @Test
    void defaultsMatchTheDocumentedCamera() {
        CameraSettings settings = new CameraSettings();

        assertEquals(CameraControl.EXPOSURE_MODE, settings.selected());
        assertEquals(100, settings.iso());
        assertEquals("1/125", settings.shutter());
        assertEquals(1.8, settings.apertureNumber(), EPSILON);
        assertEquals(50, settings.focalLength());
        assertEquals(18.0, settings.focusDistance(), EPSILON);
        assertEquals(6500, settings.whiteBalance());
        assertEquals(ExposureMode.MANUAL, settings.exposureMode());
        assertEquals(MeteringMode.EVALUATIVE, settings.meteringMode());
        assertEquals(LensProfile.KIT_ZOOM, settings.lens());
        assertEquals(FilmProfile.NEUTRAL, settings.filmProfile());
        assertEquals(AspectRatio.NATIVE, settings.aspectRatio());
        assertTrue(settings.autoFocus());
        assertTrue(settings.autoIso());
        assertFalse(settings.astrophotography());
    }

    @Test
    void commandDialFollowsThePhysicalCameraControlOrder() {
        CameraSettings settings = new CameraSettings();
        CameraControl[] expected = {
            CameraControl.EXPOSURE_MODE,
            CameraControl.SHUTTER,
            CameraControl.ISO,
            CameraControl.APERTURE,
            CameraControl.EXPOSURE_COMPENSATION,
            CameraControl.FOCAL_LENGTH,
            CameraControl.FOCUS_MODE,
            CameraControl.FOCUS_DISTANCE,
            CameraControl.FOCUS_POINT
        };

        for (CameraControl control : expected) {
            assertEquals(control, settings.selected());
            settings.selectNext();
        }

        settings.select(CameraControl.FOCUS_MODE);
        settings.adjust(1);
        assertFalse(settings.autoFocus());
        settings.adjust(-1);
        assertTrue(settings.autoFocus());

        settings.adjustRoll(14.0);
        settings.resetRoll();
        assertEquals(0.0, settings.rollDegrees(), EPSILON);
    }

    @Test
    void physicalControlsClampAtTheirSupportedLimits() {
        CameraSettings settings = new CameraSettings();

        adjust(settings, CameraControl.ISO, 100);
        adjust(settings, CameraControl.SHUTTER, 100);
        adjust(settings, CameraControl.APERTURE, 100);
        adjust(settings, CameraControl.EXPOSURE_COMPENSATION, 100);
        adjust(settings, CameraControl.WHITE_BALANCE, 100);
        settings.adjustFocalLength(10_000.0);
        settings.adjustRoll(10_000.0);

        assertEquals(12800, settings.iso());
        assertEquals("BULB", settings.shutter());
        assertEquals(22.0, settings.apertureNumber(), EPSILON);
        assertEquals(3.0F, settings.exposureCompensationStops(), EPSILON);
        assertEquals(10000, settings.whiteBalance());
        assertEquals(200.0, settings.focalLengthPrecise(), EPSILON);
        assertEquals(30.0, settings.rollDegrees(), EPSILON);

        adjust(settings, CameraControl.ISO, -100);
        adjust(settings, CameraControl.SHUTTER, -100);
        adjust(settings, CameraControl.APERTURE, -100);
        adjust(settings, CameraControl.EXPOSURE_COMPENSATION, -100);
        adjust(settings, CameraControl.WHITE_BALANCE, -100);
        settings.adjustFocalLength(-10_000.0);
        settings.adjustRoll(-10_000.0);

        assertEquals(50, settings.iso());
        assertEquals("1/8000", settings.shutter());
        assertEquals(1.2, settings.apertureNumber(), EPSILON);
        assertEquals(-3.0F, settings.exposureCompensationStops(), EPSILON);
        assertEquals(2500, settings.whiteBalance());
        assertEquals(18.0, settings.focalLengthPrecise(), EPSILON);
        assertEquals(-30.0, settings.rollDegrees(), EPSILON);
    }

    @Test
    void lensesEnforceFocalAndFocusLimits() {
        CameraSettings settings = new CameraSettings();

        settings.setLens(LensProfile.WIDE_PRIME);
        assertEquals(24.0, settings.focalLengthPrecise(), EPSILON);
        settings.adjustFocalLength(500.0);
        assertEquals(24.0, settings.focalLengthPrecise(), EPSILON);
        settings.setFocusDistance(0.01);
        assertEquals(0.25, settings.focusDistance(), EPSILON);

        settings.setLens(LensProfile.TELEPHOTO_ZOOM);
        assertEquals(105.0, settings.focalLengthPrecise(), EPSILON);
        settings.adjustFocalLength(500.0);
        assertEquals(200.0, settings.focalLengthPrecise(), EPSILON);
        settings.adjustFocalLength(-500.0);
        assertEquals(70.0, settings.focalLengthPrecise(), EPSILON);
        settings.setFocusDistance(0.01);
        assertEquals(1.20, settings.focusDistance(), EPSILON);
    }

    @Test
    void autofocusPointsFormAClampedThreeByThreeGrid() {
        CameraSettings settings = new CameraSettings();

        settings.setFocusPoint(-99, -99);
        assertEquals(-1, settings.focusPointX());
        assertEquals(-1, settings.focusPointY());
        assertEquals("TOP-LEFT", settings.focusPointLabel());

        settings.setFocusPoint(0, 0);
        assertEquals("CENTER", settings.focusPointLabel());

        settings.setFocusPoint(99, 99);
        assertEquals(1, settings.focusPointX());
        assertEquals(1, settings.focusPointY());
        assertEquals("BOTTOM-RIGHT", settings.focusPointLabel());
    }

    @Test
    void astrophotographyAndMoonPresetsUseExpectedPhysicalSettings() {
        CameraSettings settings = new CameraSettings();

        settings.applyAstrophotographyPreset();
        assertTrue(settings.astrophotography());
        assertEquals(ExposureMode.MANUAL, settings.exposureMode());
        assertEquals(1600, settings.iso());
        assertEquals("15s", settings.shutter());
        assertEquals(1.8, settings.apertureNumber(), EPSILON);
        assertEquals(24, settings.focalLength());
        assertEquals(3800, settings.whiteBalance());
        assertEquals(256.0, settings.focusDistance(), EPSILON);
        assertEquals(AstroTracking.SIDEREAL, settings.astroTracking());
        assertFalse(settings.autoFocus());
        assertFalse(settings.flash());

        settings.applyMoonPreset();
        assertEquals(100, settings.iso());
        assertEquals("1/125", settings.shutter());
        assertEquals(8.0, settings.apertureNumber(), EPSILON);
        assertEquals(200, settings.focalLength());
        assertEquals(AstroTracking.LUNAR, settings.astroTracking());
        assertEquals(LensProfile.TELEPHOTO_ZOOM, settings.lens());
    }

    @Test
    void automaticModesKeepThePhotographerControlledAxisLocked() {
        CameraSettings aperturePriority = new CameraSettings();
        aperturePriority.setExposureMode(ExposureMode.APERTURE_PRIORITY);
        adjust(aperturePriority, CameraControl.APERTURE, 3);
        double selectedAperture = aperturePriority.apertureNumber();
        aperturePriority.applyAutoExposure(4.0);
        assertEquals(selectedAperture, aperturePriority.apertureNumber(), EPSILON);

        CameraSettings shutterPriority = new CameraSettings();
        shutterPriority.setExposureMode(ExposureMode.SHUTTER_PRIORITY);
        adjust(shutterPriority, CameraControl.SHUTTER, 4);
        String selectedShutter = shutterPriority.shutter();
        shutterPriority.applyAutoExposure(-2.0);
        assertEquals(selectedShutter, shutterPriority.shutter());

        CameraSettings manual = new CameraSettings();
        String manualMetadata = manual.metadata();
        manual.applyAutoExposure(8.0);
        assertEquals(manualMetadata, manual.metadata());
    }

    @Test
    void copiesCanBeChangedWithoutMutatingTheCapturedSettings() {
        CameraSettings original = new CameraSettings();
        CameraSettings copy = original.copy();

        copy.setLens(LensProfile.MACRO_PRIME);
        copy.toggleAutoFocus();
        copy.setExposureBracket(ExposureBracket.HDR_2_EV);
        copy.setCaptureTechnique(CaptureTechnique.FOCUS_STACK);

        assertNotEquals(original.lens(), copy.lens());
        assertNotEquals(original.autoFocus(), copy.autoFocus());
        assertNotEquals(original.exposureBracket(), copy.exposureBracket());
        assertNotEquals(original.captureTechnique(), copy.captureTechnique());
        assertEquals(LensProfile.KIT_ZOOM, original.lens());
        assertTrue(original.autoFocus());
    }

    private static void adjust(CameraSettings settings, CameraControl control, int steps) {
        settings.select(control);
        int direction = Integer.signum(steps);
        for (int index = 0; index < Math.abs(steps); index++) {
            settings.adjust(direction);
        }
    }
}
