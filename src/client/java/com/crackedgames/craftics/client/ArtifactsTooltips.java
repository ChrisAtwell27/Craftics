package com.crackedgames.craftics.client;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Craftics-flavored tooltips for items from the Artifacts mod.
 * <p>
 * Used by {@link CombatTooltips} to replace the original Artifacts mod tooltips
 * with text describing what each artifact actually does inside Craftics combat.
 * Keep this in sync with {@code compat/artifacts/ArtifactEffects.java} and
 * {@code docs/artifact-abilities.md}.
 */
public final class ArtifactsTooltips {

    private ArtifactsTooltips() {}

    /** Returns true if the given registry id namespace is the Artifacts mod. */
    public static boolean isArtifactsItem(net.minecraft.util.Identifier id) {
        return id != null && "artifacts".equals(id.getNamespace());
    }

    /**
     * Append Craftics tooltip lines for the given artifact path (e.g. "cowboy_hat").
     * The header is added once if any lines are produced; if the artifact isn't recognised
     * a single "(no Craftics effect)" line is added so the player still gets feedback.
     */
    public static void appendLines(String artifactPath, List<Text> lines) {
        List<String> body = describe(artifactPath);
        lines.add(Text.empty());
        lines.add(Text.literal("\u00a76\u00a7lCraftics Combat:"));
        if (body.isEmpty()) {
            lines.add(Text.literal("\u00a78  (no Craftics effect)"));
            return;
        }
        for (String line : body) {
            lines.add(Text.literal(line));
        }
    }

    /**
     * Multi-line description for the given artifact id path. Each line includes its
     * own colour codes and is prefixed with two spaces for visual indentation under
     * the "Craftics Combat:" header.
     */
    private static List<String> describe(String path) {
        List<String> out = new ArrayList<>();
        switch (path) {
            // ===== Head slot =====
            case "night_vision_goggles" -> {
                out.add(stat("+1 Attack Range"));
            }
            case "superstitious_hat" -> {
                out.add(active("30% chance for a bonus loot duplicate on level clear"));
            }
            case "villager_hat" -> {
                out.add(active("+50% emeralds (rounds up to at least +1)"));
            }
            case "cowboy_hat" -> {
                out.add(active("On hit: pull the target 1 tile closer to you"));
            }
            case "anglers_hat" -> {
                out.add(active("+1 bonus loot item every level clear"));
            }
            case "snorkel" -> {
                out.add(stat("+1 Water Power"));
                out.add(active("Immune to SOAKED"));
            }
            case "plastic_drinking_hat" -> {
                out.add(active("Heal 1 HP at the start of each turn"));
            }
            case "novelty_drinking_hat" -> {
                out.add(active("Combat start: random buff"));
                out.add(sub("Strength / Speed / Resistance / Luck"));
            }

            // ===== Necklace slot =====
            case "cross_necklace" -> {
                out.add(active("After being hit, the next hit takes 50% less damage"));
                out.add(sub("Shield resets at the start of each turn"));
            }
            case "flame_pendant" -> {
                out.add(active("Turn start: 2 fire damage to all adjacent enemies"));
                out.add(sub("Applies 1 stack of BURNING for 2 turns"));
            }
            case "thorn_pendant" -> {
                out.add(active("Reflects 25% (min 1) of damage taken back to attacker"));
            }
            case "panic_necklace" -> {
                out.add(active("Take damage: gain +2 Speed for the rest of the turn"));
            }
            case "shock_pendant" -> {
                out.add(active("30% chance on hit: chain 3 damage to an adjacent enemy"));
            }
            case "charm_of_sinking" -> {
                out.add(stat("+1 Defense"));
                out.add(active("Immune to knockback"));
            }
            case "charm_of_shrinking" -> {
                out.add(active("20% chance to dodge any incoming attack"));
            }
            case "scarf_of_invisibility" -> {
                out.add(stat("+1 Stealth Range"));
                out.add(active("First attack of combat deals 2x damage"));
            }
            case "lucky_scarf" -> {
                out.add(active("15% chance on hit to crit for double damage"));
                out.add(active("+1 bonus loot item"));
            }

            // ===== Ring slot =====
            case "onion_ring" -> {
                out.add(stat("+1 HP Regen"));
                out.add(stat("+1 Max HP"));
            }
            case "golden_hook" -> {
                out.add(active("+2 emeralds on every emerald gain"));
            }
            case "pickaxe_heater" -> {
                out.add(active("On hit: 1 stack of BURNING for 2 turns"));
            }
            case "withered_bracelet" -> {
                out.add(stat("+1 Armor Penetration"));
                out.add(active("On hit: reduce target max HP by 1 (Wither)"));
            }

            // ===== Hand slot =====
            case "digging_claws" -> {
                out.add(active("On hit: ignore 50% of target effective defense"));
                out.add(sub("Adds the ignored value as bonus damage"));
            }
            case "feral_claws" -> {
                out.add(active("Refund 1 AP on kill"));
            }
            case "power_glove" -> {
                out.add(stat("+3 Melee Power"));
            }
            case "fire_gauntlet" -> {
                out.add(active("On hit: 2 stacks of BURNING for 3 turns"));
                out.add(active("On kill: 3 fire damage to enemies adjacent to the kill"));
                out.add(sub("Plus 1 stack of BURNING for 2 turns"));
            }
            case "pocket_piston" -> {
                out.add(active("On hit: knock target back 2 tiles"));
                out.add(sub("Slam into wall/entity = +3 collision damage"));
            }
            case "vampiric_glove" -> {
                out.add(active("Heal for 25% of damage dealt (min 1)"));
            }

            // ===== Belt slot =====
            case "crystal_heart" -> {
                out.add(stat("+6 Max HP"));
            }
            case "helium_flamingo" -> {
                out.add(active("Immune to knockback"));
                out.add(active("Move through enemies on intermediate tiles"));
                out.add(sub("(can't end your move on top of one)"));
            }
            case "chorus_totem" -> {
                out.add(active("Once per combat: survive lethal damage"));
                out.add(sub("Heals to 25% of max HP"));
            }
            case "obsidian_skull" -> {
                out.add(active("Immune to BURNING"));
            }
            case "cloud_in_a_bottle" -> {
                out.add(stat("+2 Speed"));
                out.add(active("15% chance to dodge incoming attacks"));
            }
            case "warp_drive" -> {
                out.add(active("Once per combat: type \u00a7e/craftics warp\u00a77"));
                out.add(sub("Next attack ignores range and warps you to the target"));
            }
            case "antidote_vessel" -> {
                out.add(active("Immune to POISON and CONFUSION"));
            }
            case "universal_attractor" -> {
                out.add(active("Turn start: pull all enemies 1 tile toward you"));
                out.add(active("+1 bonus loot item"));
            }
            case "everlasting_beef", "eternal_steak" -> {
                out.add(active("Non-consuming food item"));
                out.add(sub("Costs 2 AP, heals 3 HP"));
            }

            // ===== Misc slot =====
            case "umbrella" -> {
                out.add(active("Blocks the first hit of combat completely"));
                out.add(active("10% chance to block ranged attacks"));
            }

            // ===== Feet slot =====
            case "bunny_hoppers" -> {
                out.add(stat("+1 Speed"));
                out.add(active("10% chance to dodge incoming attacks"));
            }
            case "kitty_slippers" -> {
                out.add(stat("+2 Stealth Range"));
            }
            case "running_shoes" -> {
                out.add(stat("+3 Speed"));
            }
            case "aqua_dashers" -> {
                out.add(active("On hit: +2 bonus Water damage"));
                out.add(active("On move: 1 SLOWNESS to enemies adjacent to destination"));
            }
            case "rooted_boots" -> {
                out.add(active("Turn end: heal 3 HP if you didn't move, else 1 HP"));
            }
            case "snowshoes" -> {
                out.add(active("Turn start: 1 SLOWNESS to all adjacent enemies"));
                out.add(active("On move: 1 SLOWNESS to enemies adjacent to destination"));
            }
            case "steadfast_spikes" -> {
                out.add(active("Immune to knockback"));
                out.add(active("Reflect 2 damage to melee attackers"));
            }
            case "flippers" -> {
                out.add(stat("+2 Water Power"));
                out.add(stat("+1 Speed"));
                out.add(active("On hit: +3 bonus damage to SOAKED targets"));
            }
            case "strider_shoes" -> {
                out.add(stat("+1 Speed"));
                out.add(active("Immune to BURNING and SLOWNESS"));
            }
            case "whoopee_cushion" -> {
                out.add(active("25% chance at turn start: 1 CONFUSION to a random enemy"));
                out.add(active("15% chance when hit: stun the attacker"));
            }

            default -> {} // returns empty list
        }
        return out;
    }

    private static String stat(String text)   { return "\u00a7a  + " + text; }
    private static String active(String text) { return "\u00a7e  \u2022 " + text; }
    private static String sub(String text)    { return "\u00a78    " + text; }
}
