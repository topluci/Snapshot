package com.luci.snapshot.client.camera;

public enum CameraControl {
    ISO("ISO"),
    SHUTTER("Shutter"),
    APERTURE("Aperture"),
    EXPOSURE_MODE("Mode"),
    METERING("Metering"),
    AUTO_ISO("Auto ISO"),
    AUTO_ISO_MAX("ISO Limit"),
    MIN_SHUTTER("Min Shutter"),
    BRACKETING("Bracket/HDR"),
    CAPTURE_TECHNIQUE("Drive"),
    FOCAL_LENGTH("Focal"),
    LENS("Lens"),
    FOCUS_DISTANCE("Focus"),
    FOCUS_POINT("AF Point"),
    EXPOSURE_COMPENSATION("EV"),
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
