package com.crackedgames.craftics.achievement;

import com.crackedgames.craftics.combat.DamageType;
import net.minecraft.item.Item;

import java.util.*;

/**
 * Tracks in-combat statistics for achievement evaluation.
 * One instance lives per CombatManager session, reset each fight.
 */
public class CombatAchievementTracker {

    // Weapon types used by the player during this combat
    private final Set<DamageType> weaponTypesUsed = EnumSet.noneOf(DamageType.class);
    // Whether the player attacked without any weapon (fists)
    private boolean usedFistsOnly = true;
    // Whether any weapon was ever used
    private boolean usedAnyWeapon = false;

    // Damage tracking
    private boolean playerTookDamage = false;
    private boolean playerDealtDirectDamage = false; // player attacked (not just pets)
    private int highestSingleHitDamage = 0;
    private int turnCount = 0;

    // Totem tracking
    private boolean totemProced = false;

    // Stun tracking: entityId -> consecutive stun turns
    private final Map<Integer, Integer> consecutiveStuns = new HashMap<>();

    // Crit tracking
    private int consecutiveCrits = 0;
    private int maxConsecutiveCrits = 0;

    // Sweep/AoE hit counts (max per single attack)
    private int maxSweepTargets = 0;
    private int maxShockwaveTargets = 0;
    private int maxPierceTargets = 0;
    private int maxCoralFanTargets = 0;
    private int maxSpearPierceTargets = 0;

    // Execution tracking
    private boolean executionKill = false;

    // Armor crush tracking (max defense ignored in single hit)
    private int maxArmorCrushDefense = 0;

    // Counter kill tracking
    private boolean counterKill = false;

    // Confusion kill tracking (enemy killed by confused ally)
    private boolean confusionKill = false;

    // Effect kills
    private boolean poisonKill = false;
    private boolean burnKill = false;
    private boolean witherKill = false;

    // Soaked + water damage kill
    private boolean drownKill = false;

    // TNT kills
    private int maxTntKills = 0;

    // Lightning rod kill on soaked target
    private boolean lightningOnSoakedKill = false;

    // Food variety tracking
    private final Set<Item> foodsEaten = new HashSet<>();

    // Utility items placed
    private int utilityItemsPlaced = 0;

    // Goat horns used
    private final Set<String> hornsUsed = new HashSet<>();

    // Max simultaneous buffs
    private int maxSimultaneousBuffs = 0;

    // Pearl dodge (moved out of boss warning zone)
    private boolean pearlDodge = false;

    // Milk debuffs cleared
    private int maxDebuffsClearedByMilk = 0;

    // Max living allies at once
    private int maxLivingAllies = 0;

    // Brain coral confusion tracking (unique enemies confused)
    private final Set<Integer> enemiesConfused = new HashSet<>();

    // Coral fan kills
    private int coralFanKills = 0;

    // Boss-specific
    private boolean bossKilledBeforePhase2 = false;

    // Fishing rod rare item
    private boolean fishingRareItem = false;

    // === Recording Methods ===

    public void recordWeaponUsed(Item weapon) {
        if (weapon == null || weapon == net.minecraft.item.Items.AIR) {
            // Fist attack — don't add to weaponTypesUsed
            return;
        }
        usedAnyWeapon = true;
        usedFistsOnly = false;
        weaponTypesUsed.add(DamageType.fromWeapon(weapon));
    }

    public void recordPlayerDealtDamage() {
        playerDealtDirectDamage = true;
    }

    public void recordDamageDealt(int amount) {
        if (amount > highestSingleHitDamage) highestSingleHitDamage = amount;
    }

    public void recordPlayerTookDamage() {
        playerTookDamage = true;
    }

    public void recordTurnCompleted() {
        turnCount++;
    }

    public void recordTotemProc() {
        totemProced = true;
    }

    public void recordStun(int entityId) {
        int streak = consecutiveStuns.getOrDefault(entityId, 0) + 1;
        consecutiveStuns.put(entityId, streak);
    }

    /** Call at start of each turn to reset stun streaks for entities NOT stunned this turn. */
    public void clearStunStreaksExcept(Set<Integer> stunnedThisTurn) {
        consecutiveStuns.entrySet().removeIf(e -> !stunnedThisTurn.contains(e.getKey()));
    }

    public void recordCrit() {
        consecutiveCrits++;
        if (consecutiveCrits > maxConsecutiveCrits) maxConsecutiveCrits = consecutiveCrits;
    }

    public void resetCritStreak() {
        consecutiveCrits = 0;
    }

    public void recordSweepTargets(int count) {
        if (count > maxSweepTargets) maxSweepTargets = count;
    }

    public void recordShockwaveTargets(int count) {
        if (count > maxShockwaveTargets) maxShockwaveTargets = count;
    }

    public void recordPierceTargets(int count) {
        // count includes original target
        if (count > maxPierceTargets) maxPierceTargets = count;
    }

    public void recordCoralFanTargets(int count) {
        if (count > maxCoralFanTargets) maxCoralFanTargets = count;
    }

    public void recordSpearPierceTargets(int count) {
        if (count > maxSpearPierceTargets) maxSpearPierceTargets = count;
    }

    public void recordExecutionKill() {
        executionKill = true;
    }

    public void recordArmorCrush(int defenseIgnored) {
        if (defenseIgnored > maxArmorCrushDefense) maxArmorCrushDefense = defenseIgnored;
    }

    public void recordCounterKill() {
        counterKill = true;
    }

    public void recordConfusionKill() {
        confusionKill = true;
    }

    public void recordPoisonKill() {
        poisonKill = true;
    }

    public void recordBurnKill() {
        burnKill = true;
    }

    public void recordWitherKill() {
        witherKill = true;
    }

    public void recordDrownKill() {
        drownKill = true;
    }

    public void recordTntKills(int count) {
        if (count > maxTntKills) maxTntKills = count;
    }

    public void recordLightningOnSoakedKill() {
        lightningOnSoakedKill = true;
    }

    public void recordFoodEaten(Item food) {
        foodsEaten.add(food);
    }

    public void recordUtilityPlaced() {
        utilityItemsPlaced++;
    }

    public void recordHornUsed(String hornVariant) {
        hornsUsed.add(hornVariant);
    }

    public void recordSimultaneousBuffs(int count) {
        if (count > maxSimultaneousBuffs) maxSimultaneousBuffs = count;
    }

    public void recordPearlDodge() {
        pearlDodge = true;
    }

    public void recordMilkDebuffsCleared(int count) {
        if (count > maxDebuffsClearedByMilk) maxDebuffsClearedByMilk = count;
    }

    public void recordLivingAllies(int count) {
        if (count > maxLivingAllies) maxLivingAllies = count;
    }

    public void recordEnemyConfused(int entityId) {
        enemiesConfused.add(entityId);
    }

    public void recordCoralFanKill() {
        coralFanKills++;
    }

    public void recordBossKilledBeforePhase2() {
        bossKilledBeforePhase2 = true;
    }

    public void recordFishingRareItem() {
        fishingRareItem = true;
    }

    // === Query Methods ===

    public Set<DamageType> getWeaponTypesUsed() { return Collections.unmodifiableSet(weaponTypesUsed); }
    public boolean isUsedFistsOnly() { return usedFistsOnly && !usedAnyWeapon; }
    public boolean hasPlayerTookDamage() { return playerTookDamage; }
    public boolean hasPlayerDealtDirectDamage() { return playerDealtDirectDamage; }
    public int getHighestSingleHitDamage() { return highestSingleHitDamage; }
    public int getTurnCount() { return turnCount; }
    public boolean hasTotemProced() { return totemProced; }
    public int getMaxConsecutiveStuns(int entityId) { return consecutiveStuns.getOrDefault(entityId, 0); }
    public boolean hasAnyThreeConsecutiveStuns() {
        return consecutiveStuns.values().stream().anyMatch(v -> v >= 3);
    }
    public int getMaxConsecutiveCrits() { return maxConsecutiveCrits; }
    public int getMaxSweepTargets() { return maxSweepTargets; }
    public int getMaxShockwaveTargets() { return maxShockwaveTargets; }
    public int getMaxPierceTargets() { return maxPierceTargets; }
    public int getMaxCoralFanTargets() { return maxCoralFanTargets; }
    public int getMaxSpearPierceTargets() { return maxSpearPierceTargets; }
    public boolean hasExecutionKill() { return executionKill; }
    public int getMaxArmorCrushDefense() { return maxArmorCrushDefense; }
    public boolean hasCounterKill() { return counterKill; }
    public boolean hasConfusionKill() { return confusionKill; }
    public boolean hasPoisonKill() { return poisonKill; }
    public boolean hasBurnKill() { return burnKill; }
    public boolean hasWitherKill() { return witherKill; }
    public boolean hasDrownKill() { return drownKill; }
    public int getMaxTntKills() { return maxTntKills; }
    public boolean hasLightningOnSoakedKill() { return lightningOnSoakedKill; }
    public int getFoodVariety() { return foodsEaten.size(); }
    public int getUtilityItemsPlaced() { return utilityItemsPlaced; }
    public int getHornsUsed() { return hornsUsed.size(); }
    public int getMaxSimultaneousBuffs() { return maxSimultaneousBuffs; }
    public boolean hasPearlDodge() { return pearlDodge; }
    public int getMaxDebuffsClearedByMilk() { return maxDebuffsClearedByMilk; }
    public int getMaxLivingAllies() { return maxLivingAllies; }
    public int getEnemiesConfused() { return enemiesConfused.size(); }
    public int getCoralFanKills() { return coralFanKills; }
    public boolean isBossKilledBeforePhase2() { return bossKilledBeforePhase2; }
    public boolean hasFishingRareItem() { return fishingRareItem; }
}
