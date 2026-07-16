package com.luci.snapshot.client.camera;

import java.util.Locale;

public enum OpticsPreset {
    LOW("Low", 0.25F, 0.0F, 0.12F, 0.04F, 0.05F),
    MEDIUM("Medium", 0.5F, 0.25F, 0.25F, 0.08F, 0.12F),
    ULTRA("Ultra", 0.82F, 0.5F, 0.45F, 0.14F, 0.2F),
    SCREENSHOT_ULTRA("Screenshot Ultra", 1.0F, 0.75F, 0.62F, 0.2F, 0.28F);

    private final String label;
    private final float depthOfField;
    private final float motionBlur;
    private final float bloom;
    private final float chromaticAberration;
    private final float grain;

    OpticsPreset(String label, float depthOfField, float motionBlur, float bloom, float chromaticAberration, float grain) {
        this.label = label;
        this.depthOfField = depthOfField;
        this.motionBlur = motionBlur;
        this.bloom = bloom;
        this.chromaticAberration = chromaticAberration;
        this.grain = grain;
    }

    public String label() {
        return label;
    }

    public float depthOfField() {
        return depthOfField;
    }

    public float motionBlur() {
        return motionBlur;
    }

    public float bloom() {
        return bloom;
    }

    public float chromaticAberration() {
        return chromaticAberration;
    }

    public float grain() {
        return grain;
    }

    public OpticsPreset next() {
        OpticsPreset[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public OpticsPreset previous() {
        OpticsPreset[] values = values();
        return values[(ordinal() + values.length - 1) % values.length];
    }

    public static OpticsPreset fromConfig(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "low" -> LOW;
            case "medium" -> MEDIUM;
            case "screenshot_ultra" -> SCREENSHOT_ULTRA;
            default -> ULTRA;
        };
    }
}
