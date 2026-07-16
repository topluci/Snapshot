package com.luci.snapshot.client.camera;

public enum CaptureTechnique {
    SINGLE("SINGLE"),
    FOCUS_STACK("FOCUS STACK"),
    PANORAMA("PANORAMA");

    private final String label;

    CaptureTechnique(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public CaptureTechnique shift(int direction) {
        CaptureTechnique[] values = values();
        return values[Math.floorMod(ordinal() + Integer.signum(direction), values.length)];
    }
}
