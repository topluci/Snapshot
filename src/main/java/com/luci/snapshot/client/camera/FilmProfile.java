package com.luci.snapshot.client.camera;

public enum FilmProfile {
    NEUTRAL("Neutral"),
    WARM_400("Warm 400"),
    MUTED_CHROME("Muted Chrome"),
    MONOCHROME("Monochrome");

    private final String label;

    FilmProfile(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public FilmProfile shift(int direction) {
        FilmProfile[] profiles = values();
        int index = Math.floorMod(ordinal() + Integer.signum(direction), profiles.length);
        return profiles[index];
    }
}
