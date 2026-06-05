package com.crackedgames.craftics.compat.instruments;

import com.crackedgames.craftics.compat.instruments.InstrumentDef.Role;
import com.crackedgames.craftics.compat.instruments.InstrumentDef.Signature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentRosterTest {

    @Test
    void rosterHas15Instruments() {
        assertEquals(15, InstrumentRoster.ALL.size());
    }

    @Test
    void rosterHas7Attack8Support() {
        long attack = InstrumentRoster.ALL.stream().filter(d -> d.role() == Role.ATTACK).count();
        long support = InstrumentRoster.ALL.stream().filter(d -> d.role() == Role.SUPPORT).count();
        assertEquals(7, attack);
        assertEquals(8, support);
    }

    @Test
    void apCostsInRange1to3() {
        for (InstrumentDef d : InstrumentRoster.ALL) {
            assertTrue(d.apCost() >= 1 && d.apCost() <= 3, d.itemPath() + " AP in [1,3]");
        }
    }

    @Test
    void attackHaveDamageSupportDoNot() {
        for (InstrumentDef d : InstrumentRoster.ALL) {
            if (d.role() == Role.ATTACK) assertTrue(d.baseDamage() > 0, d.itemPath() + " attack has damage");
            else assertEquals(0, d.baseDamage(), d.itemPath() + " support has no damage");
        }
    }

    @Test
    void exactlyFourSignatures() {
        long sigs = InstrumentRoster.ALL.stream().filter(d -> d.signature() != Signature.NONE).count();
        assertEquals(4, sigs);
    }

    @Test
    void allShapesUsed() {
        var used = new java.util.HashSet<InstrumentDef.Shape>();
        InstrumentRoster.ALL.forEach(d -> used.add(d.shape()));
        assertEquals(InstrumentDef.Shape.values().length, used.size(), "every shape is assigned at least once");
    }
}
