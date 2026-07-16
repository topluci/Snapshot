package com.luci.snapshot.client.camera;

public enum ExposureAssist {
    OFF("ASSIST OFF"),
    HISTOGRAM("RGB HIST"),
    ZEBRAS("ZEBRA"),
    WAVEFORM("WAVEFORM"),
    FALSE_COLOR("FALSE COLOR");

    private final String label;

    ExposureAssist(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public ExposureAssist shift(int direction) {
        ExposureAssist[] assists = values();
        return assists[Math.floorMod(ordinal() + Integer.signum(direction), assists.length)];
    }
}
