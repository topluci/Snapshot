package com.luci.snapshot.client.camera;

enum CameraPresetSlot {
    LANDSCAPE("landscape", "LANDSCAPE"),
    PORTRAIT("portrait", "PORTRAIT"),
    WILDLIFE("wildlife", "WILDLIFE"),
    ASTRO("astro", "ASTRO"),
    MACRO("macro", "MACRO");

    private final String id;
    private final String label;

    CameraPresetSlot(String id, String label) {
        this.id = id;
        this.label = label;
    }

    String id() {
        return id;
    }

    String label() {
        return label;
    }

    CameraSettings defaults() {
        CameraSettings settings = new CameraSettings();
        settings.reset();
        switch (this) {
            case LANDSCAPE -> {
                settings.setExposureMode(ExposureMode.APERTURE_PRIORITY);
                settings.setLens(LensProfile.WIDE_PRIME);
                settings.select(CameraControl.APERTURE);
                settings.adjust(5);
                settings.select(CameraControl.WHITE_BALANCE);
                settings.adjust(-1);
                settings.setFocusDistance(32.0);
            }
            case PORTRAIT -> {
                settings.setExposureMode(ExposureMode.APERTURE_PRIORITY);
                settings.setLens(LensProfile.PORTRAIT_PRIME);
                settings.setFocusDistance(4.0);
                settings.setAutoFocus(true);
                settings.select(CameraControl.FILM_PROFILE);
                settings.adjust(1);
            }
            case WILDLIFE -> {
                settings.setExposureMode(ExposureMode.SHUTTER_PRIORITY);
                settings.setLens(LensProfile.TELEPHOTO_ZOOM);
                settings.adjustFocalLength(500.0);
                settings.select(CameraControl.SHUTTER);
                settings.adjust(-3);
                settings.setAutoFocus(true);
            }
            case ASTRO -> settings.applyAstrophotographyPreset();
            case MACRO -> {
                settings.setExposureMode(ExposureMode.APERTURE_PRIORITY);
                settings.setLens(LensProfile.MACRO_PRIME);
                settings.setAutoFocus(false);
                settings.setFocusDistance(0.35);
                settings.select(CameraControl.APERTURE);
                settings.adjust(3);
            }
        }
        settings.select(CameraControl.EXPOSURE_MODE);
        return settings;
    }
}
