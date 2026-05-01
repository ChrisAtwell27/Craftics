package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HornVariantsTest {

    @Test
    void variantForInstrumentPath_recognizesAllEightVanillaInstruments() {
        assertEquals("ponder", HornVariants.variantForInstrumentPath("ponder_goat_horn"));
        assertEquals("sing",   HornVariants.variantForInstrumentPath("sing_goat_horn"));
        assertEquals("seek",   HornVariants.variantForInstrumentPath("seek_goat_horn"));
        assertEquals("feel",   HornVariants.variantForInstrumentPath("feel_goat_horn"));
        assertEquals("admire", HornVariants.variantForInstrumentPath("admire_goat_horn"));
        assertEquals("call",   HornVariants.variantForInstrumentPath("call_goat_horn"));
        assertEquals("yearn",  HornVariants.variantForInstrumentPath("yearn_goat_horn"));
        assertEquals("dream",  HornVariants.variantForInstrumentPath("dream_goat_horn"));
    }

    @Test
    void variantForInstrumentPath_unknownReturnsNull() {
        assertNull(HornVariants.variantForInstrumentPath("modded_goat_horn"));
        assertNull(HornVariants.variantForInstrumentPath(""));
    }

    @Test
    void variantForInstrumentPath_nullInputReturnsNull() {
        assertNull(HornVariants.variantForInstrumentPath(null));
    }

    @Test
    void instrumentPathForVariant_isExactInverseOfVariantLookup() {
        for (String variant : new String[]{"ponder","sing","seek","feel","admire","call","yearn","dream"}) {
            String path = HornVariants.instrumentPathForVariant(variant);
            assertNotNull(path, "no instrument path for variant: " + variant);
            assertEquals(variant, HornVariants.variantForInstrumentPath(path),
                "round-trip mismatch for: " + variant);
        }
    }

    @Test
    void instrumentPathForVariant_unknownReturnsNull() {
        assertNull(HornVariants.instrumentPathForVariant("rally"));
    }
}
