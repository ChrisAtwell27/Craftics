package com.crackedgames.craftics.combat;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Per-mob-type damage vulnerabilities and resistances.
 *
 * Vulnerable = 1.5x damage from that type
 * Resistant  = 0.5x damage from that type
 * Immune     = 0x damage from that type
 *
 * Thematic mappings based on Minecraft mob lore.
 */
public class MobResistances {

    private static final Map<String, Set<DamageType>> VULNERABILITIES = new HashMap<>();
    private static final Map<String, Set<DamageType>> RESISTANCES = new HashMap<>();
    private static final Map<String, Set<DamageType>> IMMUNITIES = new HashMap<>();

    static {
        // ── Undead: weak to BLUNT (crushing bones), resist SLASHING ──
        vuln("minecraft:zombie",            DamageType.BLUNT, DamageType.CLEAVING);

        vuln("minecraft:husk",              DamageType.BLUNT, DamageType.WATER);

        vuln("minecraft:drowned",           DamageType.BLUNT, DamageType.CLEAVING);
        resist("minecraft:drowned",         DamageType.WATER);

        vuln("minecraft:skeleton",          DamageType.BLUNT);
        resist("minecraft:skeleton",        DamageType.RANGED);

        vuln("minecraft:stray",            DamageType.BLUNT);
        resist("minecraft:stray",          DamageType.RANGED, DamageType.WATER);

        vuln("minecraft:wither_skeleton",   DamageType.BLUNT, DamageType.WATER);
        resist("minecraft:wither_skeleton", DamageType.SPECIAL);

        vuln("minecraft:phantom",           DamageType.RANGED);
        resist("minecraft:phantom",         DamageType.SLASHING);

        // ── Arthropods: weak to CLEAVING (chopping legs), resist BLUNT ──
        vuln("minecraft:spider",            DamageType.CLEAVING, DamageType.SPECIAL);
        resist("minecraft:spider",          DamageType.BLUNT);

        // ── Nether mobs: weak to WATER, resist SPECIAL ──
        vuln("minecraft:blaze",             DamageType.WATER);
        resist("minecraft:blaze",           DamageType.SPECIAL, DamageType.RANGED);

        vuln("minecraft:ghast",             DamageType.RANGED, DamageType.SLASHING);
        resist("minecraft:ghast",           DamageType.SPECIAL);

        vuln("minecraft:magma_cube",        DamageType.WATER);
        resist("minecraft:magma_cube",      DamageType.BLUNT);

        vuln("minecraft:hoglin",            DamageType.SPECIAL);
        resist("minecraft:hoglin",          DamageType.BLUNT);

        vuln("minecraft:piglin",            DamageType.SPECIAL, DamageType.WATER);
        resist("minecraft:piglin",          DamageType.RANGED);

        // ── Creeper: weak to RANGED (keep your distance), resist SLASHING ──
        vuln("minecraft:creeper",           DamageType.RANGED, DamageType.CLEAVING);
        resist("minecraft:creeper",         DamageType.SLASHING);

        // ── Illagers: weak to SLASHING (dueling), resist SPECIAL ──
        vuln("minecraft:vindicator",        DamageType.RANGED);
        resist("minecraft:vindicator",      DamageType.CLEAVING);

        vuln("minecraft:pillager",          DamageType.SLASHING, DamageType.CLEAVING);
        resist("minecraft:pillager",        DamageType.RANGED);

        vuln("minecraft:witch",             DamageType.SLASHING, DamageType.CLEAVING);
        resist("minecraft:witch",           DamageType.SPECIAL);
        immune("minecraft:witch",           DamageType.WATER);

        // ── Endermen: weak to WATER (lore-accurate), resist RANGED ──
        vuln("minecraft:enderman",          DamageType.WATER, DamageType.SPECIAL);
        resist("minecraft:enderman",        DamageType.RANGED);

        // ── Shulker: weak to BLUNT (cracking the shell), resist RANGED ──
        vuln("minecraft:shulker",           DamageType.BLUNT);
        resist("minecraft:shulker",         DamageType.RANGED, DamageType.SLASHING);

        // ── Animals / passives: no special resistances ──
        // (cows, pigs, sheep, chickens — neutral to everything)

        // ── Ocelot: weak to BLUNT ──
        vuln("minecraft:ocelot",            DamageType.BLUNT);

        // ── Wolf: weak to SPECIAL ──
        vuln("minecraft:wolf",              DamageType.SPECIAL);

        // ── Goat: weak to SLASHING, resist BLUNT ──
        vuln("minecraft:goat",              DamageType.SLASHING);
        resist("minecraft:goat",            DamageType.BLUNT);

        // ── Bosses ──
        vuln("minecraft:warden",            DamageType.RANGED);
        resist("minecraft:warden",          DamageType.BLUNT, DamageType.SLASHING);

        vuln("minecraft:ender_dragon",      DamageType.RANGED, DamageType.SPECIAL);
        resist("minecraft:ender_dragon",    DamageType.WATER);
    }

    // ── Helpers to populate the maps ──

    private static void vuln(String mobId, DamageType... types) {
        VULNERABILITIES.put(mobId, EnumSet.copyOf(Set.of(types)));
    }

    private static void resist(String mobId, DamageType... types) {
        RESISTANCES.put(mobId, EnumSet.copyOf(Set.of(types)));
    }

    private static void immune(String mobId, DamageType... types) {
        IMMUNITIES.put(mobId, EnumSet.copyOf(Set.of(types)));
    }

    // ── Public API ──

    /** Check if a mob type is vulnerable to a damage type. */
    public static boolean isVulnerable(String entityTypeId, DamageType type) {
        Set<DamageType> set = VULNERABILITIES.get(entityTypeId);
        return set != null && set.contains(type);
    }

    /** Check if a mob type is resistant to a damage type. */
    public static boolean isResistant(String entityTypeId, DamageType type) {
        Set<DamageType> set = RESISTANCES.get(entityTypeId);
        return set != null && set.contains(type);
    }

    /** Check if a mob type is immune to a damage type. */
    public static boolean isImmune(String entityTypeId, DamageType type) {
        Set<DamageType> set = IMMUNITIES.get(entityTypeId);
        return set != null && set.contains(type);
    }

    /**
     * Get the damage multiplier for a mob type vs a damage type.
     * Returns: 0.0 (immune), 0.5 (resistant), 1.0 (neutral), 1.5 (vulnerable)
     */
    public static double getDamageMultiplier(String entityTypeId, DamageType type) {
        if (isImmune(entityTypeId, type)) return 0.0;
        if (isResistant(entityTypeId, type)) return 0.5;
        if (isVulnerable(entityTypeId, type)) return 1.5;
        return 1.0;
    }

    /**
     * Apply mob resistance/vulnerability to a damage value.
     * Returns the modified damage (minimum 1 unless immune, then 0).
     */
    public static int applyResistance(String entityTypeId, DamageType type, int baseDamage) {
        double mult = getDamageMultiplier(entityTypeId, type);
        if (mult == 0.0) return 0;
        return Math.max(1, (int)(baseDamage * mult));
    }

    // ── Display helpers ──

    /** Get vulnerability list for display. Returns empty string if none. */
    public static String getVulnerabilityDisplay(String entityTypeId) {
        Set<DamageType> set = VULNERABILITIES.get(entityTypeId);
        if (set == null || set.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (DamageType t : set) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.color).append(t.displayName);
        }
        return sb.toString();
    }

    /** Get resistance list for display. Returns empty string if none. */
    public static String getResistanceDisplay(String entityTypeId) {
        Set<DamageType> set = RESISTANCES.get(entityTypeId);
        if (set == null || set.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (DamageType t : set) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.color).append(t.displayName);
        }
        return sb.toString();
    }

    /** Get immunity list for display. Returns empty string if none. */
    public static String getImmunityDisplay(String entityTypeId) {
        Set<DamageType> set = IMMUNITIES.get(entityTypeId);
        if (set == null || set.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (DamageType t : set) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.color).append(t.displayName);
        }
        return sb.toString();
    }

    /** Full summary line for a mob. E.g. "§aWeak: Water, Blunt §cResist: Magic" */
    public static String getSummaryLine(String entityTypeId) {
        StringBuilder sb = new StringBuilder();
        String vulns = getVulnerabilityDisplay(entityTypeId);
        String resists = getResistanceDisplay(entityTypeId);
        String immunes = getImmunityDisplay(entityTypeId);
        if (!vulns.isEmpty()) sb.append("\u00a7aWeak: ").append(vulns).append(" ");
        if (!resists.isEmpty()) sb.append("\u00a7cResist: ").append(resists).append(" ");
        if (!immunes.isEmpty()) sb.append("\u00a74Immune: ").append(immunes);
        return sb.toString().trim();
    }
}
