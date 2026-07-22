package com.luci.snapshot.client.camera;

public enum CameraControl {
    EXPOSURE_MODE("Mode"),
    SHUTTER("Shutter"),
    ISO("ISO"),
    APERTURE("Aperture"),
    EXPOSURE_COMPENSATION("EV"),
    FOCAL_LENGTH("Focal"),
    FOCUS_MODE("Focus Mode"),
    FOCUS_DISTANCE("Focus"),
    FOCUS_POINT("AF Point"),
    LENS("Lens"),
    METERING("Metering"),
    AUTO_ISO("Auto ISO"),
    AUTO_ISO_MAX("ISO Limit"),
    MIN_SHUTTER("Min Shutter"),
    BRACKETING("Bracket/HDR"),
    CAPTURE_TECHNIQUE("Drive"),
    WHITE_BALANCE("WB"),
    CONTRAST("Contrast"),
    SATURATION("Saturation"),
    FILM_PROFILE("Film"),
    ASPECT_RATIO("Format"),
    CAMERA_ROLL("Roll"),
    FILTER("Filter"),
    PRINT_SIZE("Print Size"),
    STAR_TRACKING("Star Tracking"),
    ASTRO_STACK("Astro Stack"),
    EXPOSURE_ASSIST("Assist"),
    MOOD("Mood"),
    PRESET("Quality");

    private final String label;

    CameraControl(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public CameraControl next() {
        CameraControl[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public CameraControl previous() {
        CameraControl[] values = values();
        return values[(ordinal() + values.length - 1) % values.length];
    }
}
