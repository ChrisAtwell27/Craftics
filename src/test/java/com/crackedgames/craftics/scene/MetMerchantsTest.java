package com.crackedgames.craftics.scene;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MetMerchantsTest {
    @Test
    void filterKeepsOnlyMetIdsInOrder() {
        List<String> candidates = List.of("craftics:warmonger", "craftics:hoarder", "craftics:trickster");
        List<String> out = MetMerchants.filterMet(candidates, Set.of("craftics:trickster", "craftics:warmonger"), s -> s);
        assertEquals(List.of("craftics:warmonger", "craftics:trickster"), out);
    }

    @Test
    void emptyMetSetYieldsEmptyList_noFallback() {
        List<String> out = MetMerchants.filterMet(List.of("WEAPONSMITH", "ARMORER"), Set.of(), s -> s);
        assertTrue(out.isEmpty());
    }
}
