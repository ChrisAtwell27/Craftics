package com.crackedgames.craftics.combat;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryWrapper;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Goat Horn combat effects. Each of the 8 horn variants gives a unique
 * buff to the player or debuff to enemies when used in battle.
 *
 * Since the vanilla Instrument component types aren't accessible from this
 * source set, we identify horn variants via the custom name component.
 * Horns are created with a custom name like "Ponder Horn" and we read it back.
 */
public class GoatHornEffects {

    /** Cap on how high the horn buff/debuff amplifier can stack. Keeps a
     *  player who spams Seek from trivializing a fight. */
    public static final int MAX_HORN_AMPLIFIER = 3;

    private record HornEffectDef(
        String hornId, String displayName, String description,
        int apCost, boolean isPlayerBuff, CombatEffects.EffectType effectType,
        int duration, int amplifier
    ) {}

    private static final List<HornEffectDef> HORN_DEFS = List.of(
        // --- Player Buffs ---
        new HornEffectDef("ponder", "§7Ponder Horn",
            "§7§oA contemplative note... §9+2 Defense for 3 turns",
            1, true, CombatEffects.EffectType.RESISTANCE, 3, 0),
        new HornEffectDef("sing", "§eSing Horn",
            "§e§oAn uplifting melody! §a+2 HP regen for 3 turns",
            1, true, CombatEffects.EffectType.REGENERATION, 3, 0),
        new HornEffectDef("seek", "§6Seek Horn",
            "§6§oA rallying cry! §c+3 Attack for 3 turns",
            2, true, CombatEffects.EffectType.STRENGTH, 3, 0),
        new HornEffectDef("feel", "§dFeel Horn",
            "§d§oA soothing hum... §b+2 Speed for 3 turns",
            1, true, CombatEffects.EffectType.SPEED, 3, 0),

        // --- Enemy Debuffs ---
        new HornEffectDef("admire", "§bAdmire Horn",
            "§b§oA piercing blast! §7All enemies -2 Attack for 2 turns",
            2, false, CombatEffects.EffectType.WEAKNESS, 2, 0),
        new HornEffectDef("call", "§aCall Horn",
            "§a§oA thunderous bellow! §3All enemies -1 Speed for 2 turns",
            2, false, CombatEffects.EffectType.SLOWNESS, 2, 0),
        new HornEffectDef("yearn", "§5Yearn Horn",
            "§5§oA haunting wail! §2All enemies Poisoned for 3 turns",
            3, false, CombatEffects.EffectType.POISON, 3, 0),
        new HornEffectDef("dream", "§3Dream Horn",
            "§3§oAn ethereal whisper... §6Fire Resistance for 4 turns",
            2, true, CombatEffects.EffectType.FIRE_RESISTANCE, 4, 0)
    );

    /**
     * Create a random goat horn with a custom name identifying its variant.
     */
    public static ItemStack createRandomHorn(RegistryWrapper.WrapperLookup registryLookup) {
        HornEffectDef chosen = HORN_DEFS.get(ThreadLocalRandom.current().nextInt(HORN_DEFS.size()));
        ItemStack horn = new ItemStack(Items.GOAT_HORN);
        if (registryLookup instanceof net.minecraft.registry.DynamicRegistryManager drm) {
            HornVariants.writeVariant(horn, chosen.hornId, drm);
        }
        return horn;
    }

    /**
     * Get the horn variant ID from an ItemStack by reading the custom name.
     * Returns null if not a goat horn or has no recognized name.
     */
    public static String getHornId(ItemStack stack) {
        return HornVariants.readVariant(stack);
    }

    /**
     * Get the AP cost for a specific horn variant.
     */
    public static int getApCost(String hornId) {
        for (HornEffectDef def : HORN_DEFS) {
            if (def.hornId.equals(hornId)) return def.apCost;
        }
        return 2;
    }

    /**
     * Two-arg overload for callers without affinity context (e.g. unit tests
     * that don't bootstrap a player).
     */
    public static String useHorn(String hornId, CombatEffects combatEffects, List<CombatEntity> enemies) {
        return useHorn(hornId, combatEffects, enemies, 0, 0);
    }

    /**
     * Apply the horn's effect, scaled by the caller's Special-class affinity.
     * Re-using a buff horn before its previous duration expires stacks the
     * amplifier (capped at {@link #MAX_HORN_AMPLIFIER}) and refreshes duration
     * to the larger of remaining and (base + durationBonus).
     *
     * @param durationBonus added to the base duration (and to existing remaining
     *        duration when re-casting); typically {@link SpecialAffinity#durationBonus}.
     * @param potencyBonus  added to the amplifier on top of the per-cast +1 stack;
     *        typically {@link SpecialAffinity#potencyBonus}.
     */
    public static String useHorn(String hornId, CombatEffects combatEffects,
                                 List<CombatEntity> enemies,
                                 int durationBonus, int potencyBonus) {
        for (HornEffectDef def : HORN_DEFS) {
            if (!def.hornId.equals(hornId)) continue;

            int scaledDuration = def.duration + Math.max(0, durationBonus);
            int scaledAmp = def.amplifier + Math.max(0, potencyBonus);

            if (def.isPlayerBuff) {
                int existingTurns = combatEffects.hasEffect(def.effectType)
                    ? combatEffects.getAll().get(def.effectType).turnsRemaining : 0;
                int existingAmp = combatEffects.hasEffect(def.effectType)
                    ? combatEffects.getAll().get(def.effectType).amplifier : 0;
                int newDur = Math.max(existingTurns, scaledDuration);
                // Re-cast adds +1 to amplifier on top of the scaled base, capped.
                int newAmp = Math.min(MAX_HORN_AMPLIFIER, existingAmp + scaledAmp + 1);
                combatEffects.addEffect(def.effectType, newDur, newAmp);
                return def.description;
            } else {
                applyEnemyDebuff(def.effectType, scaledDuration, scaledAmp, enemies);
                return def.description;
            }
        }
        return "§7The horn makes no sound...";
    }

    /**
     * Apply a debuff to all living enemies. {@code amplifierBoost} stacks on
     * top of the per-effect base amplifier (e.g. weakness's base -2 ATK,
     * slowness's base -1 SPD).
     */
    private static void applyEnemyDebuff(CombatEffects.EffectType type, int duration,
                                         int amplifierBoost, List<CombatEntity> enemies) {
        int boost = Math.max(0, amplifierBoost);
        for (CombatEntity enemy : enemies) {
            if (!enemy.isAlive()) continue;
            switch (type) {
                case WEAKNESS -> {
                    int newPenalty = Math.min(MAX_HORN_AMPLIFIER + 2,
                        enemy.getAttackPenalty() + 2 + boost);
                    enemy.setAttackPenalty(newPenalty);
                    enemy.setAttackPenaltyTurns(Math.max(enemy.getAttackPenaltyTurns(), duration));
                }
                case SLOWNESS -> enemy.stackSlowness(duration, 1 + boost);
                case POISON   -> enemy.stackPoison(duration, boost);
                default       -> {}
            }
        }
    }

    /**
     * Get tooltip for a specific horn variant.
     */
    public static String getTooltip(String hornId) {
        for (HornEffectDef def : HORN_DEFS) {
            if (def.hornId.equals(hornId)) {
                return def.displayName + " — " + def.description + " §8(AP: " + def.apCost + ")";
            }
        }
        return "§7Unknown Horn";
    }

    /**
     * Get all horn tooltips for the guide book.
     */
    public static List<String> getAllHornTooltips() {
        return HORN_DEFS.stream()
            .map(d -> d.displayName + " §8(AP: " + d.apCost + ")§r\n  " + d.description)
            .toList();
    }
}
