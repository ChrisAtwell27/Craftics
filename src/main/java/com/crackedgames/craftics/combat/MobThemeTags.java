package com.crackedgames.craftics.combat;

import java.util.HashSet;
import java.util.Set;

/**
 * Theme tagging for hostile mobs.
 * <p>
 * Any mob registered under a theme automatically applies that theme's status
 * effect to the player when it deals damage. This is the single source of truth
 * for "this creature is water-themed / cold / jungle" so:
 * <ul>
 *   <li>Adding a new vanilla mob to a biome only requires one {@code add*}
 *       call — no AI code changes.</li>
 *   <li>Mod compat modules (Creeper Overhaul, Variants & Ventures, etc.) can
 *       register their themed variants here and have the effect automatically
 *       apply without touching CombatManager.</li>
 *   <li>The themes are data, not hardcoded switches scattered through the
 *       attack resolution path.</li>
 * </ul>
 * <p>
 * Themes:
 * <ul>
 *   <li><b>WATER</b> → applies {@link CombatEffects.EffectType#SOAKED} for 2 turns</li>
 *   <li><b>JUNGLE</b> → applies {@link CombatEffects.EffectType#POISON} for 2 turns</li>
 *   <li><b>COLD</b> → applies {@link CombatEffects.EffectType#WEAKNESS} for 2 turns</li>
 * </ul>
 * <p>
 * Invoked from {@code CombatManager.damagePlayer} — see
 * {@link #applyOnHitEffect} for the entry point.
 */
public final class MobThemeTags {

    /** How many turns each theme's on-hit debuff lasts. */
    public static final int SOAK_TURNS = 2;
    public static final int POISON_TURNS = 2;
    public static final int WEAKNESS_TURNS = 2;

    private static final Set<String> WATER_MOBS = new HashSet<>();
    private static final Set<String> JUNGLE_MOBS = new HashSet<>();
    private static final Set<String> COLD_MOBS = new HashSet<>();

    static {
        // Vanilla water-themed mobs
        addWaterMob("minecraft:drowned");
        addWaterMob("minecraft:guardian");
        addWaterMob("minecraft:elder_guardian");
        addWaterMob("minecraft:pufferfish");

        // Vanilla jungle-themed mobs — the vanilla cave spider already applies
        // its own poison via stackPoison during its attack resolution, so leave
        // it out of this set to avoid double-stacking.
        addJungleMob("minecraft:ocelot");

        // Vanilla cold-themed mobs
        addColdMob("minecraft:stray");     // ice skeleton
        addColdMob("minecraft:polar_bear");
        addColdMob("minecraft:snow_golem");

        // Populated further by compat modules:
        //   CreeperOverhaulCompat.init()      → adds snowy_creeper, ocean_creeper, jungle_creeper, etc.
        //   VariantsAndVenturesCompat.init()  → adds gelid (COLD), thicket (JUNGLE), murk (WATER)
    }

    private MobThemeTags() {}

    public static void addWaterMob(String entityTypeId) {
        if (entityTypeId != null) WATER_MOBS.add(entityTypeId);
    }

    public static void addJungleMob(String entityTypeId) {
        if (entityTypeId != null) JUNGLE_MOBS.add(entityTypeId);
    }

    public static void addColdMob(String entityTypeId) {
        if (entityTypeId != null) COLD_MOBS.add(entityTypeId);
    }

    public static boolean isWater(String entityTypeId) {
        return entityTypeId != null && WATER_MOBS.contains(entityTypeId);
    }

    public static boolean isJungle(String entityTypeId) {
        return entityTypeId != null && JUNGLE_MOBS.contains(entityTypeId);
    }

    public static boolean isCold(String entityTypeId) {
        return entityTypeId != null && COLD_MOBS.contains(entityTypeId);
    }

    /**
     * Resolve the {@link CombatEffects.EffectType} an attacker should apply
     * to the player on a successful hit, or {@code null} if the attacker has
     * no theme tag registered. {@code CombatManager.damagePlayer} calls this
     * and routes the result through {@code addEffectHooked} so addon
     * immunities (Antidote Vessel, Snorkel, Strider Shoes, etc.) still
     * intercept the application.
     */
    public static CombatEffects.EffectType getOnHitEffect(String entityTypeId) {
        if (isWater(entityTypeId)) return CombatEffects.EffectType.SOAKED;
        if (isJungle(entityTypeId)) return CombatEffects.EffectType.POISON;
        if (isCold(entityTypeId)) return CombatEffects.EffectType.WEAKNESS;
        return null;
    }

    /**
     * Duration (in turns) for the given attacker's on-hit debuff.
     * Returns 0 if the attacker has no theme tag.
     */
    public static int getOnHitEffectTurns(String entityTypeId) {
        if (isWater(entityTypeId)) return SOAK_TURNS;
        if (isJungle(entityTypeId)) return POISON_TURNS;
        if (isCold(entityTypeId)) return WEAKNESS_TURNS;
        return 0;
    }

    /**
     * Convenience: returns a human-readable theme name for combat log messages,
     * or {@code null} if the attacker has no theme tag.
     */
    public static String getThemeLabel(String entityTypeId) {
        if (isWater(entityTypeId)) return "§bSoaked";
        if (isJungle(entityTypeId)) return "§aPoisoned";
        if (isCold(entityTypeId)) return "§fWeakened";
        return null;
    }

    /**
     * Helper for compat modules that want to tag a mob by a single string.
     * Accepts {@code "water"}, {@code "jungle"}, {@code "cold"}; ignores any
     * other value.
     */
    public static void addToTheme(String theme, String entityTypeId) {
        if (theme == null || entityTypeId == null) return;
        switch (theme.toLowerCase(java.util.Locale.ROOT)) {
            case "water"  -> addWaterMob(entityTypeId);
            case "jungle" -> addJungleMob(entityTypeId);
            case "cold"   -> addColdMob(entityTypeId);
            default       -> {}
        }
    }

    /**
     * Entry point for {@code CombatManager.damagePlayer}. Looks up the
     * attacker's theme and, if one is registered, applies the debuff via
     * {@code addEffectHooked} so addon immunities still apply. Sends a short
     * combat-log message to the player.
     */
    public static void applyOnHitEffect(com.crackedgames.craftics.combat.CombatManager cm, CombatEntity attacker) {
        if (cm == null || attacker == null) return;
        String typeId = attacker.getEntityTypeId();
        CombatEffects.EffectType effect = getOnHitEffect(typeId);
        if (effect == null) return;
        int turns = getOnHitEffectTurns(typeId);
        if (turns <= 0) return;
        boolean applied = cm.addEffectHooked(effect, turns, 0);
        if (applied) {
            String label = getThemeLabel(typeId);
            if (label != null) {
                cm.sendMessage("§7  " + attacker.getDisplayName() + " inflicts " + label + "§7!");
            }
        }
    }
}
