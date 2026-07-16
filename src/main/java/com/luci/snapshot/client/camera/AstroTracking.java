package com.luci.snapshot.client.camera;

public enum AstroTracking {
    OFF("TRACK OFF", 0.0),
    SIDEREAL("SIDEREAL", 1.0),
    LUNAR("LUNAR", 0.966);

    private final String label;
    private final double rate;

    AstroTracking(String label, double rate) {
        this.label = label;
        this.rate = rate;
    }

    public String label() {
        return label;
    }

    public double rate() {
        return rate;
    }

    public AstroTracking shift(int direction) {
        AstroTracking[] values = values();
        return values[Math.floorMod(ordinal() + Integer.signum(direction), values.length)];
    }
}
