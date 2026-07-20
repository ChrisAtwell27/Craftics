package com.crackedgames.craftics;

import io.wispforest.owo.config.annotation.Config;
import io.wispforest.owo.config.annotation.Modmenu;
import io.wispforest.owo.config.annotation.RangeConstraint;
import io.wispforest.owo.config.annotation.SectionHeader;

@Modmenu(modId = "craftics")
@Config(name = "craftics-config", wrapperName = "CrafticsConfigWrapper")
public class CrafticsConfig {

    // ===== Player Stats =====

    @SectionHeader("playerStats")
    @RangeConstraint(min = 1, max = 10)
    public int baseAp = 3;

    @RangeConstraint(min = 1, max = 10)
    public int baseSpeed = 3;

    @RangeConstraint(min = 0.1, max = 5.0)
    public float playerDamageMultiplier = 1.0f;

    // ===== Enemy Scaling (centralized - tweak these to adjust all mob difficulty) =====

    /** Global multiplier for all enemy damage dealt to the player. 1.0 = normal. */
    @SectionHeader("enemyScaling")
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

    /**
     * Attack bonus divisor per biome. Lower = faster attack scaling.
     * atkBonus = biomeOrdinal / atkPerBiome + (biomeIndex + 1) / 2.
     * <p>Default 4: under the Armor Class system a landed hit deals flat damage
     * (no %-reduction), so steep biome attack scaling would make late-game hits
     * lethal. A gentle divisor keeps effective enemy attack in a survivable band.
     */
    @RangeConstraint(min = 1, max = 10)
    public int atkPerBiome = 4;

    /** Defense bonus divisor per biome. Lower = faster defense scaling. defBonus = biomeOrdinal / defPerBiome. */
    @RangeConstraint(min = 1, max = 10)
    public int defPerBiome = 3;

    /**
     * Extra enemy HP per additional party member, as a fraction of base HP.
     * partyHpMult = 1 + (partySize - 1) * partyHpPerPlayer. Default 0.75 →
     * 2p = 1.75x, 3p = 2.5x, 4p = 3.25x. Applies to bosses too.
     */
    @RangeConstraint(min = 0.0, max = 3.0)
    public float partyHpPerPlayer = 0.95f;

    // ===== Boss Scaling =====

    /** HP multiplier applied only to bosses (does not stack with enemyHpMultiplier). */
    @SectionHeader("bossScaling")
    @RangeConstraint(min = 0.5, max = 5.0)
    public float bossHpMultiplier = 3.25f;

    /**
     * Linear boss HP bonus per prior defeat of that boss on the same island.
     * bossKillMult = 1 + bossKillHpScale * killCount. Default 0.5 → 2nd kill
     * 1.5x, 3rd 2.0x, 4th 2.5x. Kill count is per-island (per world owner) per
     * boss biome; see CrafticsSavedData.PlayerData.bossKills.
     */
    @RangeConstraint(min = 0.0, max = 3.0)
    public float bossKillHpScale = 0.5f;

    /**
     * Hard ceiling on a biome boss's scaled attack stat. Boss attack scales
     * additively with biome progress (baseAttack + atkBonus) and, unlike regular
     * enemies, bypasses the per-biome damage cap. Without a ceiling a late-campaign
     * boss can reach an attack that near one-shots a 20 HP player, so the scaled
     * value is clamped here. 12 matches the strongest hand-authored boss base
     * (Chorus Grove), so scaling can never push a boss past the hardest boss the
     * designer built by hand. Authored bosses at or below this value are unchanged.
     */
    @RangeConstraint(min = 1, max = 30)
    public int maxBossAttack = 12;

    // ===== Infinite Scaling =====

    /** Infinite mode: boss HP at biome 1 (ordinal 0). Replaces the campaign HP path. */
    @SectionHeader("infiniteScaling")
    @RangeConstraint(min = 1, max = 2000)
    public int infiniteBossBaseHp = 50;

    /** Infinite mode: boss HP added per cleared biome. */
    @RangeConstraint(min = 0, max = 500)
    public int infiniteBossHpPerBiome = 35;

    /** Infinite mode: regular enemy HP at biome 1, level 1. */
    @RangeConstraint(min = 1, max = 500)
    public int infiniteEnemyBaseHp = 6;

    /** Infinite mode: enemy HP added per level within a biome. */
    @RangeConstraint(min = 0, max = 100)
    public int infiniteEnemyHpPerLevel = 1;

    /** Infinite mode: enemy HP added per cleared biome. */
    @RangeConstraint(min = 0, max = 200)
    public int infiniteEnemyHpPerBiome = 5;

    // ===== Gameplay Flags =====

    @SectionHeader("gameplayFlags")
    public boolean friendlyFireEnabled = true;

    public boolean permadeathMode = false;

    public boolean healBetweenLevels = true;

    /**
     * When true, {@code /craftics rebuild_arenas} requires op/permission level 2.
     * When false (default), any player may run it on their own personal world.
     */
    public boolean rebuildArenasAdminOnly = false;

    // ===== Enemy Counts =====

    @SectionHeader("enemyCounts")
    @RangeConstraint(min = 1, max = 15)
    public int maxEnemiesPerLevel = 7;

    @RangeConstraint(min = 1, max = 6)
    public int maxBossAdds = 5;

    // ===== Combat Tuning =====

    @SectionHeader("combatTuning")
    @RangeConstraint(min = 1, max = 5)
    public int attackApCost = 2;

    @RangeConstraint(min = 1, max = 5)
    public int knockbackDistance = 2;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float criticalHitChance = 0.05f;

    /** Chance for a normally-guaranteed confusion source (saxophone, Nautilus/Heart items,
     *  flower field, the Abilities CONFUSION factory) to actually confuse. Nerf: no source
     *  guarantees confusion anymore. */
    @RangeConstraint(min = 0.0, max = 1.0)
    public double confusionApplyChance = 0.35;

    /** Chance each turn that a confused enemy is disoriented and redirects its attack at a
     *  random enemy (vs acting normally against the player). */
    @RangeConstraint(min = 0.0, max = 1.0)
    public double confusedRetargetChance = 0.50;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float rangedAccuracy = 0.95f;

    @RangeConstraint(min = 1, max = 20)
    public int maxCombatEffectDuration = 10;

    // ===== Enemy Behavior =====

    @SectionHeader("enemyBehavior")
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

    @SectionHeader("weaponDamage")
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

    @SectionHeader("arenaProgression")
    @RangeConstraint(min = 0.1, max = 5.0)
    public float lootQuantityMultiplier = 1.0f;

    // ===== Rewards =====

    @SectionHeader("rewards")
    @RangeConstraint(min = 0, max = 20)
    public int emeraldBaseReward = 1;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float goatHornDropChance = 0.10f;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float trimDropChance = 0.35f;

    /** Boss-kill chance to drop a MoreTotems totem (Luck adds a little on top). Kept low so
     *  totems stay a rare prize instead of a stack you accumulate across a biome run. */
    @RangeConstraint(min = 0.0, max = 1.0)
    public float totemDropChance = 0.04f;

    /** Boss-kill chance to drop a Simply Swords unique weapon (Luck adds a little on top).
     *  Higher than the totem chance on purpose - there are ~45 uniques to collect, and each
     *  recipient rolls independently, preferring weapons they don't already carry. */
    @RangeConstraint(min = 0.0, max = 1.0)
    public float uniqueWeaponDropChance = 0.02f;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float potterySherdDropChance = 0.1f;

    @RangeConstraint(min = 0.0, max = 1.0)
    public float traderSpawnChance = 0.25f;

    // ===== Death Penalty =====

    /** On defeat past level 1, the chance each backpack (main inventory) item is lost. */
    @SectionHeader("deathPenalty")
    @RangeConstraint(min = 0.0, max = 1.0)
    public float deathMainInventoryLossChance = 0.80f;

    /** On defeat past level 1, the chance each hotbar, armor, offhand, or accessory item is lost. */
    @RangeConstraint(min = 0.0, max = 1.0)
    public float deathGearLossChance = 0.25f;

    // ===== Event Chances (cumulative thresholds - each is the total chance up to that event) =====

    /** Chance of any event in early biomes (0-2). Set to 0 to disable events early. */
    @SectionHeader("eventChances")
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

    @SectionHeader("party")
    @RangeConstraint(min = 2, max = 8)
    public int maxPartySize = 4;

    // ===== NG+ =====

    @SectionHeader("ngPlus")
    @RangeConstraint(min = 0.0, max = 1.0)
    public float ngPlusScalingPerCycle = 0.25f;

    // ===== Quality of Life =====

    @SectionHeader("qualityOfLife")
    public boolean skipEnemyAnimations = false;

    @RangeConstraint(min = 1, max = 20)
    public int enemyTurnDelay = 5;

    /** Stretch each enemy/ally action delay (~1.8x) so their turns play out at a
     *  slower, more readable pace. Off (default) keeps enemy turns at the base
     *  enemyTurnDelay speed. Independent of cameraFollowEnemies. */
    public boolean cinematicEnemyTurns = false;

    /** When on, the camera pans to follow enemies/allies as they move and attack
     *  during their turn. Off (default) keeps the camera on your framing for the
     *  whole enemy phase. Boss spectacle moments still play regardless. */
    public boolean cameraFollowEnemies = false;

    public boolean autoEndTurn = false;

    /** When on, a player's turn auto-ends after {@link #turnTimerSeconds} of inactivity
     *  (no action taken), so an AFK player can't stall a multiplayer fight indefinitely.
     *  Off by default for solo play; DEDICATED SERVERS SHOULD TURN THIS ON - one AFK
     *  party member otherwise blocks the whole fight. Reloadable live via
     *  {@code /craftics config reload}. */
    public boolean turnTimerEnabled = false;

    /** Inactivity timeout (seconds) before {@link #turnTimerEnabled} ends a player's turn. */
    @RangeConstraint(min = 15, max = 600)
    public int turnTimerSeconds = 90;

    public boolean showEnemyRangeHints = false;

    public boolean showEnemyIntentions = false;

    @RangeConstraint(min = 5, max = 50)
    public int combatLogMaxLines = 20;

    // ===== Visual =====

    @SectionHeader("visual")
    public boolean showEmeraldsInCombat = false;
    public boolean fadeTurnBanner = true;

    public boolean showDamageNumbers = true;
    public boolean bossGlowEffect = true;
    public boolean screenShakeOnHit = true;
    public boolean vfxBlockEntitiesEnabled = true;
    public boolean hitPauseEnabled = true;
    public float vfxIntensity = 1.0f;

    // ===== Accessibility =====

    @SectionHeader("accessibility")
    public boolean colorblindMode = false;
    public boolean largerUI = false;
    public boolean disableCameraShake = false;

    // ===== Hints =====

    /** Master toggle for all in-game UX hints. */
    @SectionHeader("hints")
    public boolean hintsEnabled = true;

    /** Multiplier on every hint's idle threshold (0.5 = hints fire twice as fast, 2.0 = half as fast). */
    @RangeConstraint(min = 0.5, max = 2.0)
    public float hintIdleMultiplier = 1.0f;

    // ===== Bug Reports =====

    /**
     * Where /bugreport uploads to. The endpoint forwards the report to the
     * Craftics Discord bug-report forum. Blank disables in-game reporting
     * (the screen will say so); reports that fail to send are always saved
     * to the craftics-bugreports/ folder instead.
     */
    @SectionHeader("bugReports")
    public String bugReportEndpoint = "https://crackedgames.co/api/bugreport";
}
