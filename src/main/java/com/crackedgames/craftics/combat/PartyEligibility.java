package com.crackedgames.craftics.combat;

import java.util.Set;

/**
 * Which mobs may never join a battle party, by registry id.
 *
 * <p>Split out of {@link PartyMobs} because it is pure data with no Minecraft in it, and being
 * trapped inside a class whose static initialiser needs a live registry made it untestable. That
 * mattered: the list is the ONLY thing keeping some mobs out of a party, and the one time it was
 * wrong - the Pale Garden backport's Creaking named under the vanilla id alone - nothing could
 * have caught it.
 */
public final class PartyEligibility {

    private PartyEligibility() {}

    /**
     * Mobs that attack on sight, unprovoked - never party-eligible.
     *
     * <p>Every other mob (passive animals, and neutral mobs like endermen and spiders that only
     * fight when provoked) can be added. Matched by registry id so version-only mobs simply never
     * match on shards that lack them.
     *
     * <p>Ids, not classes, and the Creaking is why that matters. The Pale Garden backport
     * implements it as an ANIMAL - reasonable for a mob that stands still until you look away,
     * and enough to slip it past any {@code instanceof HostileEntity} test in the mod.
     */
    public static final Set<String> ALWAYS_HOSTILE = Set.of(
        "minecraft:zombie", "minecraft:zombie_villager", "minecraft:husk", "minecraft:drowned",
        "minecraft:skeleton", "minecraft:stray", "minecraft:bogged", "minecraft:wither_skeleton",
        "minecraft:creeper", "minecraft:witch", "minecraft:slime", "minecraft:magma_cube",
        "minecraft:blaze", "minecraft:ghast", "minecraft:silverfish", "minecraft:endermite",
        "minecraft:guardian", "minecraft:elder_guardian", "minecraft:shulker", "minecraft:phantom",
        "minecraft:vex", "minecraft:vindicator", "minecraft:pillager", "minecraft:evoker",
        "minecraft:illusioner", "minecraft:ravager", "minecraft:zoglin", "minecraft:piglin_brute",
        "minecraft:warden", "minecraft:breeze", "minecraft:creaking",
        // The backport's Creaking is a separate id AND a separate class family. Without naming
        // it here it reads as an ordinary passive animal and could be walked into a party.
        "palegardenbackport:creaking",
        "minecraft:ender_dragon", "minecraft:wither", "minecraft:giant"
    );
}
