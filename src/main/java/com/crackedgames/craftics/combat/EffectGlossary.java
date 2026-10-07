package com.crackedgames.craftics.combat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What each status effect does, in one sentence, for the tooltip shown when an effect pill is
 * hovered.
 *
 * <p>Companion to {@link EffectIcons}, and keyed the same way ({@link EffectIcons#baseName}),
 * so {@code "Poisoned(3t)"} and {@code "Poison II (2t)"} both resolve. Two tables rather than
 * one because the same word can mean different things on either side of the grid: Airtime
 * sharpens a player's next shot, while a mob in Airtime has simply been thrown into the air.
 *
 * <p>The player table is not written here at all - it is read off
 * {@link CombatEffects.EffectType#description}, which every effect already carries. A new
 * player effect is therefore described the moment it is added to the enum.
 *
 * <p>Free of Minecraft imports so the coverage can be unit-tested without a bootstrap.
 */
public final class EffectGlossary {

    private EffectGlossary() {}

    /** Effects as they appear on a mob or ally: the labels {@code CombatManager.sendSync} emits. */
    private static final Map<String, String> ON_MOB = new HashMap<>();
    /** Effects as they appear on a player. */
    private static final Map<String, String> ON_PLAYER = new HashMap<>();

    private static void mob(String label, String description) {
        ON_MOB.put(label.toLowerCase(Locale.ROOT), description);
    }

    static {
        // One entry per label in the efx block of CombatManager.sendSync. Numbers the label
        // already shows ("-2ATK", "3 stacks") are left out of the sentence on purpose.
        mob("Stunned", "Loses its next turn.");
        mob("Enraged", "Provoked. Fights more aggressively.");
        mob("Marked", "Takes double damage from every attack (1.5x for bosses).");
        mob("Slowed", "Moves fewer tiles each turn.");
        mob("Poisoned", "Takes poison damage each turn.");
        mob("Weakened", "Attack is reduced.");
        mob("Soul Burning", "Takes soul fire damage each turn. Fire immunity only softens it.");
        mob("Burning", "Takes fire damage each turn.");
        mob("Soaked", "Moves 1 fewer tile and takes double damage from lightning.");
        mob("Confused", "May turn on its own allies.");
        mob("Blinded", "Cannot see to attack. Deals no damage on its turn.");
        mob("Exposed", "Defense is reduced.");
        mob("Bleeding", "Loses HP each turn. More stacks bleed harder.");
        mob("Withered", "Takes wither damage each turn, ramping up over time.");
        mob("Frozen", "Cannot move or attack.");
        mob("Taunting", "Forces enemies to target it.");
        mob("Regenerating", "Heals each turn.");
        mob("Absorption", "Extra HP that is lost before real health.");
        mob("Resistant", "Takes less damage.");
        mob("Strengthened", "Attack is increased.");
        mob("Hastened", "Moves more tiles each turn.");
        mob("SlowFalling", "Resists knockback.");
        mob("Airtime", "Thrown into the air.");
        mob("Levitation", "Floating. Moves fewer tiles each turn.");

        for (CombatEffects.EffectType type : CombatEffects.EffectType.values()) {
            ON_PLAYER.put(type.displayName.toLowerCase(Locale.ROOT), sentence(type.description));
        }
        // Not an EffectType: the server injects this marker while you stand in a stealth tile.
        ON_PLAYER.put("hidden", "Enemies cannot see you while you stay in cover.");
    }

    /** "+2 movement/level" -> "+2 movement/level." so both tables read as sentences. */
    private static String sentence(String fragment) {
        if (fragment == null || fragment.isEmpty()) return "";
        String s = Character.toUpperCase(fragment.charAt(0)) + fragment.substring(1);
        char last = s.charAt(s.length() - 1);
        return last == '.' || last == '!' || last == '?' ? s : s + ".";
    }

    /**
     * What an effect does to a mob or ally, or null when the label is not one Craftics emits
     * (an addon's custom effect, which carries its own description elsewhere).
     */
    public static String onMob(String rawLabel) {
        return ON_MOB.get(EffectIcons.baseName(rawLabel));
    }

    /** What an effect does to a player, or null for a name with no entry. */
    public static String onPlayer(String rawLabel) {
        return ON_PLAYER.get(EffectIcons.baseName(rawLabel));
    }
}
