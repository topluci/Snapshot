package com.luci.snapshot.client.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SceneDepthMapTest {
    @Test
    void interpolatesCoherentDepthWithoutCreatingSilhouetteHalos() {
        SceneDepthMap smooth = SceneDepthMap.synthetic(2, 2, 10.0, 10.5, 10.0, 10.5);
        assertEquals(10.25, smooth.sample(0.5, 0.5), 0.001);

        SceneDepthMap edge = SceneDepthMap.synthetic(2, 2, 4.0, 40.0, 4.0, 40.0);
        assertEquals(4.0, edge.sample(0.49, 0.5), 0.001);
        assertEquals(40.0, edge.sample(0.51, 0.5), 0.001);
    }

    @Test
    void rejectsMalformedSyntheticFixtures() {
        assertThrows(IllegalArgumentException.class, () -> SceneDepthMap.synthetic(2, 2, 1.0, 2.0));
    }
}
