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

    // ===== Enemy Scaling =====

    @RangeConstraint(min = 0.1, max = 5.0)
    public float enemyDamageMultiplier = 1.0f;

    @RangeConstraint(min = 0.1, max = 5.0)
    public float enemyHpMultiplier = 1.0f;

    @RangeConstraint(min = 0, max = 20)
    public int hpPerBiome = 3;

    public boolean scaleHpPerLevel = false;

    @RangeConstraint(min = 0, max = 5)
    public int hpPerLevel = 1;

    @RangeConstraint(min = 1, max = 10)
    public int atkPerBiome = 2;

    @RangeConstraint(min = 1, max = 10)
    public int defPerBiome = 3;

    // ===== Difficulty & Balance =====

    @RangeConstraint(min = 0.5, max = 5.0)
    public float bossHpMultiplier = 1.0f;

    public boolean friendlyFireEnabled = true;

    public boolean permadeathMode = false;

    public boolean hungerEnabled = false;

    public boolean healBetweenLevels = true;

    // ===== Enemy Counts =====

    @RangeConstraint(min = 1, max = 10)
    public int maxEnemiesPerLevel = 6;

    @RangeConstraint(min = 1, max = 6)
    public int maxBossAdds = 4;

    // ===== Combat Tuning =====

    @RangeConstraint(min = 1, max = 5)
    public int attackApCost = 2;

    @RangeConstraint(min = 1, max = 5)
    public int knockbackDistance = 2;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float criticalHitChance = 0.0f;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float rangedAccuracy = 1.0f;

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
    public float traderSpawnChance = 0.13f;

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

    public boolean showDamageNumbers = true;
    public boolean bossGlowEffect = true;
    public boolean deathShrinkAnimation = true;
    public boolean screenShakeOnHit = true;

    // ===== Accessibility =====

    public boolean colorblindMode = false;
    public boolean largerUI = false;
    public boolean disableCameraShake = false;
}
