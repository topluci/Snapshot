package com.luci.snapshot.client.camera;

import com.luci.snapshot.config.SnapshotConfig;

public final class SnapshotEnvironmentPreview {
    private SnapshotEnvironmentPreview() {
    }

    public static long clockTime(long serverTime) {
        return enabled() ? clockTime(SnapshotCameraController.environmentPreset(), serverTime) : serverTime;
    }

    public static float rainLevel(float serverLevel) {
        return enabled() ? rainLevel(SnapshotCameraController.environmentPreset(), serverLevel) : serverLevel;
    }

    public static float thunderLevel(float serverLevel) {
        return enabled() ? thunderLevel(SnapshotCameraController.environmentPreset(), serverLevel) : serverLevel;
    }

    static long clockTime(String preset, long serverTime) {
        long fixedTime = switch (preset) {
            case "sunrise" -> 23_000L;
            case "noon" -> 6_000L;
            case "sunset" -> 12_000L;
            case "night" -> 18_000L;
            default -> -1L;
        };
        if (fixedTime < 0L) {
            return serverTime;
        }
        return serverTime - Math.floorMod(serverTime, 24_000L) + fixedTime;
    }

    static float rainLevel(String preset, float serverLevel) {
        return switch (preset) {
            case "clear" -> 0.0F;
            case "rain", "storm" -> 1.0F;
            default -> serverLevel;
        };
    }

    static float thunderLevel(String preset, float serverLevel) {
        return switch (preset) {
            case "clear", "rain" -> 0.0F;
            case "storm" -> 1.0F;
            default -> serverLevel;
        };
    }

    private static boolean enabled() {
        return SnapshotCameraController.active() && SnapshotConfig.get().environmentPreview
            && SnapshotCameraController.environmentControlsAllowed();
    }
}
