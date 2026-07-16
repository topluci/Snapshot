package com.luci.snapshot.client.camera;

public enum OpticalFilter {
    NONE("NO FILTER", 0.0),
    ND8("ND8", -3.0),
    GRADUATED_ND("GND", -1.0),
    POLARIZER("CPL", -1.0),
    DIFFUSION("1/4 MIST", 0.0),
    INFRARED("IR", -1.0);

    private final String label;
    private final double exposureStops;

    OpticalFilter(String label, double exposureStops) {
        this.label = label;
        this.exposureStops = exposureStops;
    }

    public String label() {
        return label;
    }

    public double exposureStops() {
        return exposureStops;
    }

    public OpticalFilter shift(int direction) {
        OpticalFilter[] filters = values();
        return filters[Math.floorMod(ordinal() + Integer.signum(direction), filters.length)];
    }
}
