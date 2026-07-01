package com.crackedgames.craftics.scene;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneOfferStoreTest {
    @Test
    void refreshFiresExactlyOncePerPeriod() {
        // The pure cadence: given a per-tick countdown starting at REFRESH_TICKS, decrementing each
        // tick, shouldRefresh(counterBeforeDecrement) is true exactly when the counter hits 1→0.
        int period = SceneOfferStore.REFRESH_TICKS;
        int fires = 0;
        int counter = period;
        for (int t = 0; t < period * 3; t++) {
            if (SceneOfferStore.shouldRefresh(counter)) fires++;
            counter = SceneOfferStore.nextCounter(counter);
        }
        assertEquals(3, fires, "should refresh once per 10-min period over 3 periods");
    }

    @Test
    void counterWrapsAtZero() {
        assertEquals(SceneOfferStore.REFRESH_TICKS, SceneOfferStore.nextCounter(1));
        assertEquals(5, SceneOfferStore.nextCounter(6));
    }
}
