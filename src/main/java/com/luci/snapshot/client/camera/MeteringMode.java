package com.luci.snapshot.client.camera;

public enum MeteringMode {
    EVALUATIVE("MATRIX"),
    CENTER_WEIGHTED("CENTER"),
    SPOT("SPOT");

    private final String label;

    MeteringMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public MeteringMode shift(int direction) {
        MeteringMode[] modes = values();
        return modes[Math.floorMod(ordinal() + Integer.signum(direction), modes.length)];
    }
}
