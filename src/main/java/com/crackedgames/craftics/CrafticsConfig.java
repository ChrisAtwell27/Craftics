package com.crackedgames.craftics;

import io.wispforest.owo.config.annotation.Config;
import io.wispforest.owo.config.annotation.Modmenu;
import io.wispforest.owo.config.annotation.RangeConstraint;

@Modmenu(modId = "craftics")
@Config(name = "craftics-config", wrapperName = "CrafticsConfigWrapper")
public class CrafticsConfig {

    // ===== Player Stats =====

    @RangeConstraint(min = 1, max = 10)
    public int baseAp = 3;

    @RangeConstraint(min = 1, max = 10)
    public int baseSpeed = 3;

    @RangeConstraint(min = 0.1, max = 5.0)
    public float playerDamageMultiplier = 1.0f;

    // ===== Enemy Scaling (centralized — tweak these to adjust all mob difficulty) =====

    /** Global multiplier for all enemy damage dealt to the player. 1.0 = normal. */
    @RangeConstraint(min = 0.1, max = 5.0)
    public float enemyDamageMultiplier = 1.35f;

    /** Global multiplier for all enemy HP (regular mobs). 1.0 = normal. */
    @RangeConstraint(min = 0.1, max = 5.0)
    public float enemyHpMultiplier = 1.45f;

    /** HP bonus added per biome in the progression path. Higher = steeper health curve. */
    @RangeConstraint(min = 0, max = 20)
    public int hpPerBiome = 8;

    /** Whether enemies gain +hpPerLevel HP for each level within a biome. */
    public boolean scaleHpPerLevel = true;

    /** HP bonus per level within a biome (if scaleHpPerLevel is true). */
    @RangeConstraint(min = 0, max = 5)
    public int hpPerLevel = 2;

    /** Attack bonus divisor per biome. Lower = faster attack scaling. atkBonus = biomeOrdinal / atkPerBiome. */
    @RangeConstraint(min = 1, max = 10)
    public int atkPerBiome = 1;

    /** Defense bonus divisor per biome. Lower = faster defense scaling. defBonus = biomeOrdinal / defPerBiome. */
    @RangeConstraint(min = 1, max = 10)
    public int defPerBiome = 3;

    // ===== Boss Scaling =====

    /** HP multiplier applied only to bosses (does not stack with enemyHpMultiplier). */
    @RangeConstraint(min = 0.5, max = 5.0)
    public float bossHpMultiplier = 3.0f;

    public boolean friendlyFireEnabled = true;

    public boolean permadeathMode = false;

    public boolean hungerEnabled = false;

    public boolean healBetweenLevels = true;

    // ===== Enemy Counts =====

    @RangeConstraint(min = 1, max = 15)
    public int maxEnemiesPerLevel = 7;

    @RangeConstraint(min = 1, max = 6)
    public int maxBossAdds = 4;

    // ===== Combat Tuning =====

    @RangeConstraint(min = 1, max = 5)
    public int attackApCost = 2;

    @RangeConstraint(min = 1, max = 5)
    public int knockbackDistance = 2;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float criticalHitChance = 0.05f;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float rangedAccuracy = 0.95f;

    @RangeConstraint(min = 1, max = 5)
    public int poisonDamagePerTurn = 1;

    @RangeConstraint(min = 1, max = 20)
    public int maxCombatEffectDuration = 10;

    // ===== Enemy Behavior =====

    @RangeConstraint(min = 0.5, max = 3.0)
    public float enemyMoveSpeedMultiplier = 1.0f;

    public boolean beeSwarmReplacesPassives = true;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float passiveMobWanderChance = 0.5f;

    public boolean predatorHuntingEnabled = true;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float passiveHostileRatioEarly = 0.4f;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float passiveHostileRatioLate = 1.0f;

    // ===== Weapon Base Damage =====

    @RangeConstraint(min = 0, max = 20)
    public int dmgFist = 1;
    @RangeConstraint(min = 0, max = 30)
    public int dmgWoodenSword = 5;
    @RangeConstraint(min = 0, max = 30)
    public int dmgStoneSword = 6;
    @RangeConstraint(min = 0, max = 30)
    public int dmgIronSword = 9;
    @RangeConstraint(min = 0, max = 30)
    public int dmgDiamondSword = 12;
    @RangeConstraint(min = 0, max = 30)
    public int dmgNetheriteSword = 15;
    @RangeConstraint(min = 0, max = 30)
    public int dmgGoldenSword = 14;
    @RangeConstraint(min = 0, max = 45)
    public int dmgWoodenAxe = 8;
    @RangeConstraint(min = 0, max = 45)
    public int dmgStoneAxe = 11;
    @RangeConstraint(min = 0, max = 45)
    public int dmgIronAxe = 15;
    @RangeConstraint(min = 0, max = 45)
    public int dmgDiamondAxe = 21;
    @RangeConstraint(min = 0, max = 45)
    public int dmgNetheriteAxe = 27;
    @RangeConstraint(min = 0, max = 45)
    public int dmgGoldenAxe = 24;
    @RangeConstraint(min = 0, max = 20)
    public int dmgMace = 15;
    @RangeConstraint(min = 0, max = 30)
    public int dmgTrident = 10;
    @RangeConstraint(min = 0, max = 30)
    public int dmgBow = 11;
    @RangeConstraint(min = 0, max = 20)
    public int dmgCrossbow = 14;
    @RangeConstraint(min = 0, max = 20)
    public int dmgStick = 2;
    @RangeConstraint(min = 0, max = 20)
    public int dmgBamboo = 6;
    @RangeConstraint(min = 0, max = 20)
    public int dmgBlazeRod = 12;
    @RangeConstraint(min = 0, max = 20)
    public int dmgBreezeRod = 12;

    // Coral weapons (Water-type melee)
    @RangeConstraint(min = 0, max = 20)
    public int dmgCoralTube = 5;
    @RangeConstraint(min = 0, max = 20)
    public int dmgCoralBrain = 8;
    @RangeConstraint(min = 0, max = 20)
    public int dmgCoralBubble = 5;
    @RangeConstraint(min = 0, max = 20)
    public int dmgCoralFire = 11;
    @RangeConstraint(min = 0, max = 20)
    public int dmgCoralHorn = 9;
    @RangeConstraint(min = 0, max = 20)
    public int dmgCoralDead = 3;
    @RangeConstraint(min = 0, max = 20)
    public int dmgCoralFan = 2;

    // ===== Arena & Progression =====

    @RangeConstraint(min = 0, max = 10)
    public int arenaGridPadding = 0;

    @RangeConstraint(min = 1, max = 10)
    public int levelsPerBiome = 5;

    @RangeConstraint(min = 0.1, max = 5.0)
    public float lootQuantityMultiplier = 1.0f;

    // ===== Rewards =====

    @RangeConstraint(min = 0, max = 20)
    public int emeraldBaseReward = 1;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float goatHornDropChance = 0.10f;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float trimDropChance = 0.35f;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float potterySherdDropChance = 0.02f;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float traderSpawnChance = 0.25f;

    // ===== Event Chances (cumulative thresholds — each is the total chance up to that event) =====

    /** Chance of any event in early biomes (0-2). Set to 0 to disable events early. */
    @RangeConstraint(min = 0.0, max = 1.0)
    public float earlyBiomeEventChance = 0.75f;

    /** Ominous Trial Chamber (late game only, biome 10+). */
    @RangeConstraint(min = 0.0, max = 1.0)
    public float ominousTrialChance = 0.05f;

    /** Trial Chamber. */
    @RangeConstraint(min = 0.0, max = 1.0)
    public float trialChamberChance = 0.10f;

    /** Ambush (unavoidable fight). */
    @RangeConstraint(min = 0.0, max = 1.0)
    public float ambushChance = 0.10f;

    /** Shrine of Fortune. */
    @RangeConstraint(min = 0.0, max = 1.0)
    public float shrineChance = 0.07f;

    /** Wounded Traveler. */
    @RangeConstraint(min = 0.0, max = 1.0)
    public float travelerChance = 0.06f;

    /** Treasure Vault. */
    @RangeConstraint(min = 0.0, max = 1.0)
    public float vaultChance = 0.04f;

    /** Dig Site. */
    @RangeConstraint(min = 0.0, max = 1.0)
    public float digSiteChance = 0.06f;

    // ===== Party =====

    @RangeConstraint(min = 2, max = 8)
    public int maxPartySize = 4;

    // ===== NG+ =====

    @RangeConstraint(min = 0.0, max = 1.0)
    public float ngPlusScalingPerCycle = 0.25f;

    // ===== Quality of Life =====

    public boolean skipEnemyAnimations = false;

    @RangeConstraint(min = 1, max = 20)
    public int enemyTurnDelay = 5;

    public boolean autoEndTurn = false;

    public boolean showEnemyRangeHints = false;

    public boolean showEnemyIntentions = false;

    @RangeConstraint(min = 5, max = 50)
    public int combatLogMaxLines = 20;

    // ===== Visual =====

    public boolean compactEnemyList = true;
    public boolean showEmeraldsInCombat = false;
    public boolean fadeTurnBanner = true;

    public boolean showDamageNumbers = true;
    public boolean bossGlowEffect = true;
    public boolean deathShrinkAnimation = true;
    public boolean screenShakeOnHit = true;
    public boolean vfxBlockEntitiesEnabled = true;
    public boolean hitPauseEnabled = true;
    public float vfxIntensity = 1.0f;

    // ===== Accessibility =====

    public boolean colorblindMode = false;
    public boolean largerUI = false;
    public boolean disableCameraShake = false;
}
