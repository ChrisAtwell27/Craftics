package com.crackedgames.craftics.level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BiomeEffectGateTest {
    @Test void beforeStartInactive()  { assertFalse(BiomeTemplate.effectActiveAt(4, 2)); }
    @Test void atStartActive()        { assertTrue(BiomeTemplate.effectActiveAt(4, 3)); }
    @Test void bossLevelInRange()     { assertTrue(BiomeTemplate.effectActiveAt(4, 6)); }
    @Test void earlyStart()           { assertTrue(BiomeTemplate.effectActiveAt(2, 1)); }
    @Test void zeroMeansNoEffect()    { assertFalse(BiomeTemplate.effectActiveAt(0, 5)); }
}
