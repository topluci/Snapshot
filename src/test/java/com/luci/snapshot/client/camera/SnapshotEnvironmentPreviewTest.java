package com.luci.snapshot.client.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SnapshotEnvironmentPreviewTest {
    @Test
    void timePresetsKeepTheCurrentMinecraftDay() {
        long serverTime = 48_050L;

        assertEquals(71_000L, SnapshotEnvironmentPreview.clockTime("sunrise", serverTime));
        assertEquals(54_000L, SnapshotEnvironmentPreview.clockTime("noon", serverTime));
        assertEquals(60_000L, SnapshotEnvironmentPreview.clockTime("sunset", serverTime));
        assertEquals(66_000L, SnapshotEnvironmentPreview.clockTime("night", serverTime));
        assertEquals(serverTime, SnapshotEnvironmentPreview.clockTime("rain", serverTime));
    }

    @Test
    void weatherPresetsDoNotAlterUnrelatedWeatherState() {
        assertEquals(0.0F, SnapshotEnvironmentPreview.rainLevel("clear", 0.7F));
        assertEquals(1.0F, SnapshotEnvironmentPreview.rainLevel("rain", 0.0F));
        assertEquals(1.0F, SnapshotEnvironmentPreview.rainLevel("storm", 0.0F));
        assertEquals(0.35F, SnapshotEnvironmentPreview.rainLevel("night", 0.35F));

        assertEquals(0.0F, SnapshotEnvironmentPreview.thunderLevel("rain", 0.8F));
        assertEquals(1.0F, SnapshotEnvironmentPreview.thunderLevel("storm", 0.0F));
        assertEquals(0.4F, SnapshotEnvironmentPreview.thunderLevel("sunset", 0.4F));
    }
}
