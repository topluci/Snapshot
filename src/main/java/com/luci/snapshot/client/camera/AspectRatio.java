package com.luci.snapshot.client.camera;

public enum AspectRatio {
    NATIVE("FULL", 0.0),
    FOUR_THREE("4:3", 4.0 / 3.0),
    SQUARE("1:1", 1.0),
    ANAMORPHIC("2.39:1", 2.39);

    private final String label;
    private final double ratio;

    AspectRatio(String label, double ratio) {
        this.label = label;
        this.ratio = ratio;
    }

    public String label() {
        return label;
    }

    public double ratio() {
        return ratio;
    }

    public boolean nativeFrame() {
        return ratio <= 0.0;
    }

    public AspectRatio shift(int direction) {
        AspectRatio[] ratios = values();
        int index = Math.floorMod(ordinal() + Integer.signum(direction), ratios.length);
        return ratios[index];
    }
}
