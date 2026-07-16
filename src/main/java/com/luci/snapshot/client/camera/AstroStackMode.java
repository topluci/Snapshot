package com.luci.snapshot.client.camera;

public enum AstroStackMode {
    DENOISE("DENOISE"),
    DEEP_SKY("DEEP SKY"),
    STAR_TRAILS("STAR TRAILS");

    private final String label;

    AstroStackMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public AstroStackMode shift(int direction) {
        AstroStackMode[] modes = values();
        return modes[Math.floorMod(ordinal() + Integer.signum(direction), modes.length)];
    }
}
