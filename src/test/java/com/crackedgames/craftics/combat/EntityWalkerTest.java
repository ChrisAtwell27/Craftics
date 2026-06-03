package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntityWalkerTest {

    /** Records the last applied position so we can assert the lerp progression. */
    static class FakeMover implements EntityWalker.Mover {
        double x, y, z; float yaw; boolean done;
        public void apply(double x, double y, double z, float yaw) { this.x=x; this.y=y; this.z=z; this.yaw=yaw; }
    }

    @Test
    void reachesTargetAfterNTicks() {
        FakeMover m = new FakeMover();
        EntityWalker w = new EntityWalker(m, 0, 0, 0, 4, 0, 0, 4, () -> m.done = true);
        for (int i = 0; i < 4; i++) { assertFalse(w.isComplete()); w.tick(); }
        assertTrue(w.isComplete());
        assertEquals(4.0, m.x, 1e-6);
        assertEquals(0.0, m.z, 1e-6);
        assertTrue(m.done, "onComplete should fire");
    }

    @Test
    void midpointIsHalfway() {
        FakeMover m = new FakeMover();
        EntityWalker w = new EntityWalker(m, 0, 0, 0, 10, 0, 0, 4, () -> {});
        w.tick(); w.tick(); // 2 of 4 ticks
        assertEquals(5.0, m.x, 1e-6);
    }

    @Test
    void callbackFiresExactlyOnce() {
        FakeMover m = new FakeMover();
        int[] calls = {0};
        EntityWalker w = new EntityWalker(m, 0, 0, 0, 1, 0, 0, 2, () -> calls[0]++);
        w.tick(); w.tick(); // completes
        w.tick();           // extra ticks must not re-fire onComplete
        assertEquals(1, calls[0]);
    }
}
