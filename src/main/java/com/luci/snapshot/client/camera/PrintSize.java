package com.luci.snapshot.client.camera;

public enum PrintSize {
    ONE_BY_ONE("1x1", 128, 128),
    TWO_BY_ONE("2x1", 256, 128),
    TWO_BY_TWO("2x2", 256, 256),
    THREE_BY_TWO("3x2", 384, 256);

    private final String label;
    private final int width;
    private final int height;

    PrintSize(String label, int width, int height) {
        this.label = label;
        this.width = width;
        this.height = height;
    }

    public String label() {
        return label;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public PrintSize shift(int direction) {
        PrintSize[] values = values();
        return values[Math.floorMod(ordinal() + Integer.signum(direction), values.length)];
    }
}
