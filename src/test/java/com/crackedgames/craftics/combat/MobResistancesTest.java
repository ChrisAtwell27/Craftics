package com.crackedgames.craftics.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for PHYSICAL (fist / unarmed) resistance assignments. */
class MobResistancesTest {

    private static double phys(String mobId) {
        return MobResistances.getDamageMultiplier(mobId, DamageType.PHYSICAL);
    }

    @Test
    void physicalResist_appliedToNetherMobs() {
        // All Nether hostiles except soft-body magma_cube.
        for (String mob : new String[]{
                "minecraft:blaze", "minecraft:ghast", "minecraft:hoglin",
                "minecraft:piglin", "minecraft:piglin_brute",
                "minecraft:zombified_piglin", "minecraft:wither_skeleton",
                "minecraft:strider", "minecraft:zoglin"}) {
            assertEquals(0.5, phys(mob), mob + " should resist physical");
        }
    }

    @Test
    void physicalResist_appliedToEndMobs() {
        for (String mob : new String[]{
                "minecraft:enderman", "minecraft:endermite",
                "minecraft:shulker", "minecraft:ender_dragon"}) {
            assertEquals(0.5, phys(mob), mob + " should resist physical");
        }
    }

    @Test
    void physicalResist_appliedToOverworldMobs() {
        for (String mob : new String[]{
                "minecraft:skeleton", "minecraft:stray", "minecraft:bogged",
                "minecraft:creeper", "minecraft:spider", "minecraft:cave_spider",
                "minecraft:witch", "minecraft:pillager", "minecraft:vindicator",
                "minecraft:evoker", "minecraft:ravager", "minecraft:warden",
                "minecraft:guardian", "minecraft:elder_guardian", "minecraft:vex",
                "minecraft:iron_golem", "minecraft:illusioner"}) {
            assertEquals(0.5, phys(mob), mob + " should resist physical");
        }
    }

    @Test
    void physicalResist_appliedToWitherBoss() {
        assertEquals(0.5, phys("minecraft:wither"), "wither should resist physical");
    }

    @Test
    void physicalResist_notAppliedToPunchableMobs() {
        // Zombie family + soft / low-tier mobs stay fist-vulnerable (neutral 1.0).
        for (String mob : new String[]{
                "minecraft:zombie", "minecraft:zombie_villager",
                "minecraft:husk", "minecraft:drowned",
                "minecraft:silverfish", "minecraft:slime",
                "minecraft:magma_cube", "minecraft:phantom",
                "minecraft:breeze"}) {
            assertEquals(1.0, phys(mob), mob + " should stay fist-vulnerable");
        }
    }

    @Test
    void physicalResist_notAppliedToPassiveAnimals() {
        for (String mob : new String[]{
                "minecraft:cow", "minecraft:pig", "minecraft:sheep",
                "minecraft:wolf", "minecraft:goat", "minecraft:ocelot"}) {
            assertEquals(1.0, phys(mob), mob + " should stay fist-vulnerable");
        }
    }

    @Test
    void newEntries_keepThematicWeakAndResist() {
        // zoglin mirrors hoglin: vuln SPECIAL (1.5), resist BLUNT (0.5).
        assertEquals(1.5, MobResistances.getDamageMultiplier("minecraft:zoglin", DamageType.SPECIAL));
        assertEquals(0.5, MobResistances.getDamageMultiplier("minecraft:zoglin", DamageType.BLUNT));
        // guardian: vuln BLUNT (1.5), resist WATER (0.5).
        assertEquals(1.5, MobResistances.getDamageMultiplier("minecraft:guardian", DamageType.BLUNT));
        assertEquals(0.5, MobResistances.getDamageMultiplier("minecraft:guardian", DamageType.WATER));
        // iron_golem: vuln WATER (1.5), resist BLUNT (0.5), resist SLASHING (0.5).
        assertEquals(1.5, MobResistances.getDamageMultiplier("minecraft:iron_golem", DamageType.WATER));
        assertEquals(0.5, MobResistances.getDamageMultiplier("minecraft:iron_golem", DamageType.BLUNT));
        assertEquals(0.5, MobResistances.getDamageMultiplier("minecraft:iron_golem", DamageType.SLASHING));
        // illusioner mirrors evoker: vuln SLASHING/CLEAVING (1.5), resist SPECIAL (0.5).
        assertEquals(1.5, MobResistances.getDamageMultiplier("minecraft:illusioner", DamageType.SLASHING));
        assertEquals(1.5, MobResistances.getDamageMultiplier("minecraft:illusioner", DamageType.CLEAVING));
        assertEquals(0.5, MobResistances.getDamageMultiplier("minecraft:illusioner", DamageType.SPECIAL));
        // vex: vuln SLASHING (1.5), resist RANGED (0.5).
        assertEquals(1.5, MobResistances.getDamageMultiplier("minecraft:vex", DamageType.SLASHING));
        assertEquals(0.5, MobResistances.getDamageMultiplier("minecraft:vex", DamageType.RANGED));
        // wither boss: vuln WATER (1.5), resist BLUNT/SLASHING/RANGED (0.5).
        assertEquals(1.5, MobResistances.getDamageMultiplier("minecraft:wither", DamageType.WATER));
        assertEquals(0.5, MobResistances.getDamageMultiplier("minecraft:wither", DamageType.BLUNT));
        assertEquals(0.5, MobResistances.getDamageMultiplier("minecraft:wither", DamageType.SLASHING));
        assertEquals(0.5, MobResistances.getDamageMultiplier("minecraft:wither", DamageType.RANGED));
    }
}
