package com.luci.snapshot.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SnapshotConfigTest {
    @Test
    void malformedAndOutOfRangeValuesFallBackOrClampSafely() throws ReflectiveOperationException {
        Properties properties = new Properties();
        properties.setProperty("survival.require_camera", "not-a-boolean");
        properties.setProperty("survival.require_paper", "false");
        properties.setProperty("capture.thumbnail_size", "2");
        properties.setProperty("capture.long_exposure_samples_per_second", "99");
        properties.setProperty("capture.max_long_exposure_samples", "broken");
        properties.setProperty("capture.max_bulb_seconds", "999");
        properties.setProperty("optics.default_preset", "cinematic-but-invalid");
        properties.setProperty("environment.permission_level", "-4");

        SnapshotConfig config = create(properties);

        assertTrue(config.requireCamera);
        assertFalse(config.requirePaper);
        assertEquals(32, config.thumbnailSize);
        assertEquals(8, config.longExposureSamplesPerSecond);
        assertEquals(64, config.maxLongExposureSamples);
        assertEquals(300, config.maxBulbSeconds);
        assertEquals("ultra", config.defaultPreset);
        assertEquals(0, config.environmentPermissionLevel);
    }

    @Test
    void everyEditableSettingHasAUniqueKeyAndValidRange() {
        Set<String> keys = new HashSet<>();
        for (SnapshotConfig.ConfigSpec spec : SnapshotConfig.SPECS) {
            assertTrue(keys.add(spec.key()), () -> "Duplicate config key: " + spec.key());
            assertFalse(spec.label().isBlank());
            assertFalse(spec.section().isBlank());
            assertTrue(spec.max() >= spec.min());
        }
        assertEquals(20, keys.size());
    }

    private static SnapshotConfig create(Properties properties) throws ReflectiveOperationException {
        Constructor<SnapshotConfig> constructor = SnapshotConfig.class.getDeclaredConstructor(Properties.class);
        constructor.setAccessible(true);
        return constructor.newInstance(properties);
    }
}
