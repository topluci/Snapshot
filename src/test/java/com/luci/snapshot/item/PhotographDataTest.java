package com.luci.snapshot.item;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;

class PhotographDataTest {
    @Test
    void embeddedPreviewAndCaptureTitleRoundTripThroughItemData() {
        byte[] colors = new byte[PhotographData.MAP_PIXEL_COUNT];
        Arrays.fill(colors, (byte) 42);

        CustomData data = PhotographData.create("snapshot_test", colors);

        assertEquals("snapshot_test", PhotographData.title(data));
        assertArrayEquals(colors, PhotographData.colors(data).orElseThrow());
    }
}
