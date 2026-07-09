package com.crackedgames.craftics.compat.artifacts;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Rare "artifact carrier" enemies for the Artifacts mod compat layer.
 * <p>
 * A normal (non-boss) arena mob has a small chance to spawn carrying a random
 * Artifacts curio. The curio is shown in the mob's offhand, buffs the mob with an
 * enemy-side reading of what the item does for a player (see {@link #buffsFor}),
 * and drops on kill far more generously than regular mob gear
 * ({@link #DROP_CHANCE} vs the 6%/12% gear rolls).
 * <p>
 * Wear roll happens in the CombatManager enemy-spawn path; the drop roll lives in
 * {@code CombatManager.rollMobEquipmentDrops}. Everything here no-ops when the
 * Artifacts mod isn't loaded.
 */
public final class MobArtifacts {

    /**
     * Per-mob chance to carry an artifact. Tuned to feel like armor-trim rarity:
     * a trimmed mob is roughly 10% gear gate x 55% slot fill x 5% trim per piece
     * (~1% of arena mobs), so the carrier gate matches that order of magnitude.
     */
    public static final double WEAR_CHANCE = 0.01;

    /** Chance per killer that the carried artifact actually drops. */
    public static final double DROP_CHANCE = 0.25;

    /** Enemy-side stat package granted by a carried artifact. */
    public record Buffs(int attack, int defense, int speed, int hp, int range) {}

    private MobArtifacts() {}

    /**
     * Roll the carrier gate and pick a random artifact. Returns EMPTY when the
     * Artifacts mod isn't loaded, the gate roll fails, or no artifact item resolves.
     */
    public static ItemStack maybeRollWorn() {
        if (!ArtifactsCompat.isLoaded()) return ItemStack.EMPTY;
        if (Math.random() >= WEAR_CHANCE) return ItemStack.EMPTY;
        return ArtifactRoller.rollOne();
    }

    /**
     * The vanilla equipment slot an artifact should occupy on a mob so it renders
     * as WORN, not held. Feet artifacts (boots/shoes) go on the feet, head artifacts
     * (hats/goggles) on the head; everything else - necklaces, rings, gloves, belts,
     * which have no vanilla-armor analogue - stays in the offhand (held) as before.
     * A worn slot keeps the vanilla renderer drawing the item in the right place.
     */
    public static EquipmentSlot slotFor(ItemStack stack) {
        String path = "";
        if (stack != null && !stack.isEmpty()) {
            Identifier id = Registries.ITEM.getId(stack.getItem());
            if (id != null && ArtifactsCompat.MOD_ID.equals(id.getNamespace())) {
                path = id.getPath();
            }
        }
        return switch (path) {
            case "bunny_hoppers", "kitty_slippers", "running_shoes", "aqua_dashers",
                 "rooted_boots", "snowshoes", "steadfast_spikes", "flippers",
                 "strider_shoes" -> EquipmentSlot.FEET;
            case "night_vision_goggles", "superstitious_hat", "villager_hat",
                 "cowboy_hat", "anglers_hat", "snorkel", "plastic_drinking_hat",
                 "novelty_drinking_hat" -> EquipmentSlot.HEAD;
            default -> EquipmentSlot.OFFHAND;
        };
    }

    /**
     * Map an artifact to the stat buffs its enemy carrier gets. Mirrors the
     * player-side numbers in {@link ArtifactsScanner} where the artifact grants
     * plain stats (Running Shoes +3 Speed, Power Glove +3 Melee, Crystal Heart
     * +6 HP, ...). Effect-style artifacts that have no enemy-side equivalent are
     * folded into a small themed bump instead, so carrying is never a no-op.
     */
    public static Buffs buffsFor(ItemStack stack) {
        String path = "";
        if (stack != null && !stack.isEmpty()) {
            Identifier id = Registries.ITEM.getId(stack.getItem());
            if (id != null && ArtifactsCompat.MOD_ID.equals(id.getNamespace())) {
                path = id.getPath();
            }
        }
        return switch (path) {
            // Speed - matches the player-side SPEED numbers where they exist
            case "running_shoes" -> new Buffs(0, 0, 3, 0, 0);
            case "cloud_in_a_bottle", "warp_drive" -> new Buffs(0, 0, 2, 0, 0);
            case "bunny_hoppers", "flippers", "strider_shoes", "kitty_slippers",
                 "cowboy_hat", "panic_necklace", "digging_claws", "aqua_dashers",
                 "snowshoes", "helium_flamingo", "charm_of_shrinking" -> new Buffs(0, 0, 1, 0, 0);
            // Attack
            case "power_glove" -> new Buffs(3, 0, 0, 0, 0);
            case "feral_claws", "fire_gauntlet", "flame_pendant", "withered_bracelet",
                 "superstitious_hat", "vampiric_glove" -> new Buffs(2, 0, 0, 0, 0);
            case "shock_pendant", "golden_hook", "pickaxe_heater", "pocket_piston",
                 "snorkel", "universal_attractor", "lucky_scarf" -> new Buffs(1, 0, 0, 0, 0);
            // Defense
            case "obsidian_skull" -> new Buffs(0, 2, 0, 0, 0);
            case "villager_hat", "anglers_hat", "charm_of_sinking", "umbrella",
                 "thorn_pendant", "rooted_boots", "steadfast_spikes",
                 "scarf_of_invisibility" -> new Buffs(0, 1, 0, 0, 0);
            // Max HP
            case "crystal_heart" -> new Buffs(0, 0, 0, 6, 0);
            case "chorus_totem" -> new Buffs(0, 0, 0, 4, 0);
            case "cross_necklace", "everlasting_beef", "eternal_steak" -> new Buffs(0, 0, 0, 3, 0);
            case "onion_ring", "antidote_vessel" -> new Buffs(0, 0, 0, 2, 0);
            // Attack range
            case "night_vision_goggles" -> new Buffs(0, 0, 0, 0, 1);
            // Mixed
            case "plastic_drinking_hat", "novelty_drinking_hat" -> new Buffs(1, 1, 0, 0, 0);
            // Anything unmapped (incl. whoopee_cushion) still gets a token bump
            default -> new Buffs(1, 0, 0, 0, 0);
        };
    }
}
