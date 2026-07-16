package com.luci.snapshot.client.camera;

public enum MoodPreset {
    NATURAL("Natural", 0, 1.0, 1.0, 1.0),
    OVERCAST("Overcast Melancholy", -450, 0.82, 0.94, 1.10),
    ALPINE_SUNRISE("Alpine Sunrise", 850, 1.06, 1.05, 1.12),
    ETHEREAL_MIST("Ethereal Mist", 250, 0.92, 0.90, 1.38),
    ASTROPHOTOGRAPHY("Astrophotography", -1200, 0.90, 1.02, 1.30);

    private final String label;
    private final int temperatureOffset;
    private final double saturation;
    private final double contrast;
    private final double bloom;

    MoodPreset(String label, int temperatureOffset, double saturation, double contrast, double bloom) {
        this.label = label;
        this.temperatureOffset = temperatureOffset;
        this.saturation = saturation;
        this.contrast = contrast;
        this.bloom = bloom;
    }

    public String label() {
        return label;
    }

    public int temperatureOffset() {
        return temperatureOffset;
    }

    public double saturation() {
        return saturation;
    }

    public double contrast() {
        return contrast;
    }

    public double bloom() {
        return bloom;
    }

    public MoodPreset shift(int direction) {
        MoodPreset[] presets = values();
        int index = Math.floorMod(ordinal() + Integer.signum(direction), presets.length);
        return presets[index];
    }
}
