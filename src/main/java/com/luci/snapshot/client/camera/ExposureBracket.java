package com.luci.snapshot.client.camera;

public enum ExposureBracket {
    OFF("OFF", 0.0, false),
    THREE_1_EV("3F +/-1", 1.0, false),
    THREE_2_EV("3F +/-2", 2.0, false),
    HDR_1_EV("HDR +/-1", 1.0, true),
    HDR_2_EV("HDR +/-2", 2.0, true);

    private final String label;
    private final double stops;
    private final boolean hdr;

    ExposureBracket(String label, double stops, boolean hdr) {
        this.label = label;
        this.stops = stops;
        this.hdr = hdr;
    }

    public String label() {
        return label;
    }

    public double stops() {
        return stops;
    }

    public boolean enabled() {
        return this != OFF;
    }

    public boolean hdr() {
        return hdr;
    }

    public ExposureBracket shift(int direction) {
        ExposureBracket[] values = values();
        return values[Math.floorMod(ordinal() + Integer.signum(direction), values.length)];
    }
}
