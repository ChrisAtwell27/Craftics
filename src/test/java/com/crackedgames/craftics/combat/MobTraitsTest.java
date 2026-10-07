package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A trait pill is a promise about how a mob fights, so these pin the pills to the rules.
 *
 * <p>The derived traits are held to the same call the fight makes - {@code isStunImmune},
 * the hazard flag, the undead list - rather than to a list of mob names restated here, which
 * would only prove the test agrees with itself. The declared ones have no such call to lean
 * on, so they are spot-checked against the mobs the feature was specified with.
 */
class MobTraitsTest {

    private static CombatEntity mob(String type) {
        return new CombatEntity(1, type, new GridPos(0, 0), 10, 3, 0, 1);
    }

    private static List<MobTrait> ofType(String type) {
        return MobTraits.forType(type, type, false, false, false);
    }

    // ── Derived traits follow the flag the fight reads ──

    @Test
    @DisplayName("Thick-skulled is exactly stun immunity")
    void thickSkulledTracksStunImmunity() {
        for (String type : List.of("minecraft:zombie", "minecraft:spider", "minecraft:goat",
                "minecraft:iron_golem", "minecraft:ravager", "minecraft:creeper")) {
            CombatEntity e = mob(type);
            assertEquals(e.isStunImmune(), MobTraits.forEntity(e).contains(MobTraits.THICK_SKULLED),
                type + ": the pill and CombatEntity.isStunImmune disagree");
        }
    }

    @Test
    @DisplayName("a boss resists half of stuns unless it is immune outright")
    void indomitableIsTheBossFallback() {
        CombatEntity revenant = mob("minecraft:zombie");
        revenant.setBoss(true);
        revenant.setAiOverrideKey("boss:plains");
        List<MobTrait> traits = MobTraits.forEntity(revenant);
        assertTrue(traits.contains(MobTraits.INDOMITABLE));
        assertFalse(traits.contains(MobTraits.THICK_SKULLED));

        // The Rockbreaker resists blunt by its boss key, which makes it fully stun immune -
        // describing it as resisting "half" would undersell what the player is up against.
        CombatEntity rockbreaker = mob("minecraft:vindicator");
        rockbreaker.setBoss(true);
        rockbreaker.setAiOverrideKey("boss:mountain");
        traits = MobTraits.forEntity(rockbreaker);
        assertTrue(traits.contains(MobTraits.THICK_SKULLED));
        assertFalse(traits.contains(MobTraits.INDOMITABLE));
    }

    @Test
    @DisplayName("an ordinary mob is neither")
    void nonBossHasNoStunTrait() {
        List<MobTrait> traits = MobTraits.forEntity(mob("minecraft:zombie"));
        assertFalse(traits.contains(MobTraits.INDOMITABLE));
        assertFalse(traits.contains(MobTraits.THICK_SKULLED));
    }

    @Test
    @DisplayName("flag-backed traits appear with the flag and not without it")
    void flagsDriveTheirTraits() {
        CombatEntity e = mob("minecraft:pig");
        assertTrue(MobTraits.forEntity(e).isEmpty(), "a plain pig has nothing to say");

        e.setHazardImmune(true);
        assertTrue(MobTraits.forEntity(e).contains(MobTraits.INFALLIBLE));
        e.setImmovable(true);
        assertTrue(MobTraits.forEntity(e).contains(MobTraits.IMMOVABLE));
        e.setInertObject(true);
        assertTrue(MobTraits.forEntity(e).contains(MobTraits.INANIMATE));
    }

    @Test
    @DisplayName("a background boss counts as immovable")
    void backgroundBossIsImmovable() {
        CombatEntity dragon = mob("minecraft:ender_dragon");
        dragon.setBackgroundBoss(true);
        assertTrue(MobTraits.forEntity(dragon).contains(MobTraits.IMMOVABLE));
    }

    @Test
    @DisplayName("Large follows the footprint")
    void largeFollowsFootprint() {
        assertTrue(MobTraits.forEntity(mob("minecraft:spider")).contains(MobTraits.LARGE));
        assertFalse(MobTraits.forEntity(mob("minecraft:zombie")).contains(MobTraits.LARGE));
    }

    @Test
    @DisplayName("Undead and Arthropod match the lists Smite and Bane use")
    void creatureTypesMatchTheEnchantLists() {
        for (String type : List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider",
                "minecraft:bee", "minecraft:creeper", "minecraft:wither")) {
            List<MobTrait> traits = ofType(type);
            assertEquals(PlayerCombatStats.isUndead(type), traits.contains(MobTraits.UNDEAD), type);
            assertEquals(PlayerCombatStats.isArthropod(type), traits.contains(MobTraits.ARTHROPOD), type);
        }
    }

    @Test
    @DisplayName("a Creaking is invulnerable only while its heart is linked")
    void creakingNeedsItsHeart() {
        CombatEntity creaking = mob("minecraft:creaking");
        assertFalse(MobTraits.forEntity(creaking).contains(MobTraits.INVULNERABLE),
            "an unlinked Creaking can be hit, so the pill would be a lie");
        creaking.setLinkedHeartId(7);
        assertTrue(MobTraits.forEntity(creaking).contains(MobTraits.INVULNERABLE));

        CombatEntity zombie = mob("minecraft:zombie");
        zombie.setLinkedHeartId(7);
        assertFalse(MobTraits.forEntity(zombie).contains(MobTraits.INVULNERABLE));
    }

    @Test
    @DisplayName("the bestiary view infers what a spawn would set")
    void forTypeMirrorsTheSpawn() {
        List<MobTrait> boss = MobTraits.forType("minecraft:zombie", "boss:plains", true, false, false);
        assertTrue(boss.contains(MobTraits.INFALLIBLE), "every boss spawns hazard-immune");
        assertTrue(boss.contains(MobTraits.INDOMITABLE));
        assertTrue(boss.contains(MobTraits.UNDEAD));

        assertTrue(MobTraits.forType("palegardenbackport:creaking", null, false, false, false)
            .contains(MobTraits.INVULNERABLE));
        assertTrue(MobTraits.forType("minecraft:blaze", null, false, true, false)
            .contains(MobTraits.FLAMEBORNE));
    }

    // ── Declared traits ──

    @Test
    @DisplayName("the mobs the feature was specified with carry their traits")
    void specifiedExamples() {
        assertTrue(ofType("minecraft:husk").contains(MobTraits.FORCEFUL));
        assertTrue(ofType("minecraft:enderman").contains(MobTraits.ETHEREAL));
        assertTrue(ofType("minecraft:shulker").contains(MobTraits.ETHEREAL));
        assertTrue(ofType("minecraft:spider").contains(MobTraits.ACROBATIC));
        assertTrue(ofType("minecraft:creeper").contains(MobTraits.MARTYR));
        assertTrue(ofType("minecraft:wither_skeleton").contains(MobTraits.DECAYED));
        assertTrue(ofType("minecraft:stray").contains(MobTraits.SUPPRESSOR));
        assertTrue(ofType("minecraft:ravager").contains(MobTraits.BERZERKER));
        assertTrue(ofType("craftics:war_banner").contains(MobTraits.INANIMATE));
        assertTrue(ofType("craftics:grave").contains(MobTraits.INANIMATE));
    }

    @Test
    @DisplayName("cave spider and bogged are Toxic, now that they poison")
    void thePoisonFixIsReflected() {
        assertTrue(ofType("minecraft:cave_spider").contains(MobTraits.TOXIC));
        assertTrue(ofType("minecraft:bogged").contains(MobTraits.TOXIC));
    }

    @Test
    @DisplayName("a jungle-themed mob is Toxic without being declared")
    void jungleThemeImpliesToxic() {
        // The ocelot is in MobThemeTags' jungle set and nowhere in the declared table.
        assertTrue(MobThemeTags.isJungle("minecraft:ocelot"));
        assertTrue(ofType("minecraft:ocelot").contains(MobTraits.TOXIC));
    }

    @Test
    @DisplayName("a boss collects the traits declared for its AI key")
    void bossKeyDeclarations() {
        List<MobTrait> broodmother =
            MobTraits.forType("minecraft:spider", "boss:jungle", true, false, true);
        assertTrue(broodmother.contains(MobTraits.ACROBATIC));
        assertTrue(broodmother.contains(MobTraits.TOXIC));
        assertTrue(broodmother.contains(MobTraits.THICK_SKULLED));
        assertFalse(MobTraits.forType("minecraft:zombie", "boss:plains", true, false, false)
            .contains(MobTraits.TOXIC), "another boss key must not leak in");
    }

    @Test
    @DisplayName("an ally keeps what it is and drops what a hostile does")
    void alliesSkipHostileBehaviour() {
        CombatEntity husk = mob("minecraft:husk");
        assertTrue(MobTraits.forEntity(husk).contains(MobTraits.FORCEFUL));
        husk.setAlly(true);
        List<MobTrait> asAlly = MobTraits.forEntity(husk);
        assertFalse(asAlly.contains(MobTraits.FORCEFUL),
            "the hostile on-hit switch never runs for an ally");
        assertTrue(asAlly.contains(MobTraits.UNDEAD), "it is still undead");
    }

    // ── Registry and wire format ──

    @Test
    @DisplayName("ids are unique and safe to put on the wire")
    void registryIntegrity() {
        Set<String> ids = new HashSet<>();
        for (MobTrait t : MobTraits.all()) {
            assertTrue(ids.add(t.id()), "duplicate id " + t.id());
            assertFalse(t.name().isBlank());
            assertFalse(t.description().isBlank(), t.id() + " has no tooltip text");
        }
        assertEquals(18, ids.size(), "the launch set");
    }

    @Test
    @DisplayName("traits come back in registry order, whatever order they were found in")
    void displayOrderIsStable() {
        List<MobTrait> order = new ArrayList<>(MobTraits.all());
        List<MobTrait> traits = MobTraits.forType("minecraft:spider", "boss:jungle", true, true, true);
        int last = -1;
        for (MobTrait t : traits) {
            int at = order.indexOf(t);
            assertTrue(at > last, t.id() + " is out of display order");
            last = at;
        }
    }

    @Test
    @DisplayName("encode and decode round-trip")
    void wireRoundTrip() {
        List<MobTrait> traits = List.of(MobTraits.INFALLIBLE, MobTraits.UNDEAD, MobTraits.MARTYR);
        assertEquals("infallible,undead,martyr", MobTraits.encode(traits));
        assertEquals(traits, MobTraits.decode(MobTraits.encode(traits)));
        assertEquals("", MobTraits.encode(List.of()));
        assertTrue(MobTraits.decode("").isEmpty());
        assertTrue(MobTraits.decode(null).isEmpty());
    }

    @Test
    @DisplayName("an id this side does not know is dropped, not shown")
    void unknownIdsAreDropped() {
        assertEquals(List.of(MobTraits.UNDEAD), MobTraits.decode("some_addon_trait,undead"));
    }

    @Test
    @DisplayName("the trait segment is not mistaken for a status effect")
    void traitSegmentIsNotAnEffect() {
        List<String> effects = EffectIcons.parseEnemyEffects(
            "minecraft:husk;atk=3;def=1;spd=1;range=1;mv=walk;tr=forceful,undead;Poisoned(2t)");
        assertEquals(List.of("Poisoned(2t)"), effects);
    }

    // ── Bestiary catalog ──

    @Test
    @DisplayName("the bestiary table round-trips and omits traitless mobs")
    void catalogRoundTrip() {
        Map<String, List<MobTrait>> table = new LinkedHashMap<>();
        table.put("Zombie", List.of(MobTraits.UNDEAD));
        table.put("Cow", List.of());
        table.put("The Revenant", List.of(MobTraits.INFALLIBLE, MobTraits.INDOMITABLE, MobTraits.UNDEAD));

        String encoded = MobTraitCatalog.encode(table);
        assertEquals("Zombie=undead|The Revenant=infallible,indomitable,undead", encoded);

        Map<String, List<MobTrait>> back = MobTraitCatalog.decode(encoded);
        assertEquals(2, back.size());
        assertEquals(table.get("The Revenant"), back.get("The Revenant"));
        assertNotNull(back.get("Zombie"));
        assertFalse(back.containsKey("Cow"));
        assertTrue(MobTraitCatalog.decode("").isEmpty());
    }

    @Test
    @DisplayName("merging two mobs under one name keeps display order")
    void unionKeepsOrder() {
        List<MobTrait> merged = MobTraitCatalog.union(
            List.of(MobTraits.UNDEAD), List.of(MobTraits.INFALLIBLE, MobTraits.UNDEAD));
        assertEquals(List.of(MobTraits.INFALLIBLE, MobTraits.UNDEAD), merged);
    }
}
