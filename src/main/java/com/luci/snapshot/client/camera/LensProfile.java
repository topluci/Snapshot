package com.luci.snapshot.client.camera;

public enum LensProfile {
    KIT_ZOOM("18-200mm", 18.0, 200.0, 50.0, 0.45, 0.008),
    WIDE_PRIME("24mm PRIME", 24.0, 24.0, 24.0, 0.25, 0.010),
    STANDARD_PRIME("50mm PRIME", 50.0, 50.0, 50.0, 0.45, 0.004),
    PORTRAIT_PRIME("85mm PRIME", 85.0, 85.0, 85.0, 0.80, 0.003),
    TELEPHOTO_ZOOM("70-200mm", 70.0, 200.0, 105.0, 1.20, 0.006),
    MACRO_PRIME("100mm MACRO", 100.0, 100.0, 100.0, 0.20, 0.002);

    private final String label;
    private final double minimumFocalLength;
    private final double maximumFocalLength;
    private final double defaultFocalLength;
    private final double minimumFocusDistance;
    private final double distortion;

    LensProfile(String label, double minimumFocalLength, double maximumFocalLength, double defaultFocalLength,
                double minimumFocusDistance, double distortion) {
        this.label = label;
        this.minimumFocalLength = minimumFocalLength;
        this.maximumFocalLength = maximumFocalLength;
        this.defaultFocalLength = defaultFocalLength;
        this.minimumFocusDistance = minimumFocusDistance;
        this.distortion = distortion;
    }

    public String label() {
        return label;
    }

    public double minimumFocalLength() {
        return minimumFocalLength;
    }

    public double maximumFocalLength() {
        return maximumFocalLength;
    }

    public double defaultFocalLength() {
        return defaultFocalLength;
    }

    public double minimumFocusDistance() {
        return minimumFocusDistance;
    }

    public double distortion() {
        return distortion;
    }

    public LensProfile shift(int direction) {
        LensProfile[] lenses = values();
        return lenses[Math.floorMod(ordinal() + Integer.signum(direction), lenses.length)];
    }
}
