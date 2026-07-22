package com.luci.snapshot.client.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CameraPresetStoreTest {
    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void resetStore() {
        CameraPresetStore.usePathForTests(null);
    }

    @Test
    void savesEveryCameraSettingAndReloadsItAcrossSessions() {
        CameraPresetStore.usePathForTests(temporaryDirectory.resolve("snapshot-presets.json"));
        CameraSettings original = new CameraSettings();
        original.applyMoonPreset();
        original.setFocusPoint(1, -1);
        original.setFilter(OpticalFilter.POLARIZER);
        original.setPrintSize(PrintSize.TWO_BY_TWO);
        original.setCaptureTechnique(CaptureTechnique.PANORAMA);
        original.adjustRoll(11.5);

        CameraPresetStore.save(CameraPresetSlot.LANDSCAPE, original);
        String expected = original.metadata();
        CameraPresetStore.resetForTests();
        CameraSettings restored = new CameraSettings();
        CameraPresetStore.load(CameraPresetSlot.LANDSCAPE, restored);

        assertEquals(expected, restored.metadata());
        assertTrue(CameraPresetStore.active(CameraPresetSlot.LANDSCAPE));
        assertFalse(restored.autoFocus());
        assertEquals("TOP-RIGHT", restored.focusPointLabel());
    }

    @Test
    void builtInSlotsProvideDistinctStartingPoints() {
        CameraPresetStore.usePathForTests(temporaryDirectory.resolve("defaults.json"));

        CameraSettings landscape = CameraPresetStore.settings(CameraPresetSlot.LANDSCAPE);
        CameraSettings portrait = CameraPresetStore.settings(CameraPresetSlot.PORTRAIT);
        CameraSettings astro = CameraPresetStore.settings(CameraPresetSlot.ASTRO);

        assertEquals(LensProfile.WIDE_PRIME, landscape.lens());
        assertEquals(LensProfile.PORTRAIT_PRIME, portrait.lens());
        assertTrue(astro.astrophotography());
    }
}
