package com.luci.snapshot.client.camera;

public enum ExposureMode {
    MANUAL("M"),
    APERTURE_PRIORITY("A"),
    SHUTTER_PRIORITY("S"),
    PROGRAM("P"),
    AUTO("AUTO");

    private final String label;

    ExposureMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public ExposureMode next() {
        ExposureMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    public ExposureMode shift(int direction) {
        ExposureMode[] modes = values();
        return modes[Math.floorMod(ordinal() + Integer.signum(direction), modes.length)];
    }
}
