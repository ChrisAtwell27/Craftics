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
     * Use a goat horn in combat. Applies the buff/debuff.
     * Returns a message describing the effect.
     */
    public static String useHorn(String hornId, CombatEffects combatEffects, List<CombatEntity> enemies) {
        for (HornEffectDef def : HORN_DEFS) {
            if (!def.hornId.equals(hornId)) continue;

            if (def.isPlayerBuff) {
                int existing = combatEffects.hasEffect(def.effectType)
                    ? combatEffects.getAll().get(def.effectType).turnsRemaining
                    : 0;
                int newDur = Math.max(existing, def.duration);
                combatEffects.addEffect(def.effectType, newDur, def.amplifier);
                return def.description;
            } else {
                applyEnemyDebuff(def.effectType, def.duration, enemies);
                return def.description;
            }
        }
        return "§7The horn makes no sound...";
    }

    /**
     * Apply a debuff to all living enemies.
     */
    private static void applyEnemyDebuff(CombatEffects.EffectType type, int duration, List<CombatEntity> enemies) {
        for (CombatEntity enemy : enemies) {
            if (!enemy.isAlive()) continue;
            switch (type) {
                case WEAKNESS -> {
                    enemy.setAttackPenalty(2);
                    enemy.setAttackPenaltyTurns(duration);
                }
                case SLOWNESS -> enemy.stackSlowness(duration, 1);
                case POISON   -> enemy.stackPoison(duration, 0);
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
