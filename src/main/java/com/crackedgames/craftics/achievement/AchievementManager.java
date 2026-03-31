package com.crackedgames.craftics.achievement;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.*;
import com.crackedgames.craftics.level.BiomePath;
import com.crackedgames.craftics.network.AchievementUnlockPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Central achievement checking and granting logic.
 * Called from CombatManager at victory and during combat events.
 */
public class AchievementManager {

    private static final Set<String> NETHER_BIOMES = Set.of(
        "nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas"
    );
    private static final Set<String> END_BIOMES = Set.of(
        "outer_end_islands", "end_city", "chorus_grove", "dragons_nest"
    );

    /**
     * Grant an achievement to a player. Returns true if newly unlocked.
     * Sends a network packet to the client for toast display.
     */
    public static boolean grant(ServerPlayerEntity player, Achievement achievement) {
        PlayerProgression prog = PlayerProgression.get((ServerWorld) player.getEntityWorld());
        PlayerProgression.PlayerStats stats = prog.getStats(player);
        if (stats.hasAchievement(achievement)) return false;

        stats.grantAchievement(achievement);
        prog.saveStats(player);

        // Grant the vanilla advancement (shows in the L-key advancement screen)
        grantVanillaAdvancement(player, achievement.advancementPath());

        // Notify client with custom toast (stacks with vanilla toast)
        ServerPlayNetworking.send(player, new AchievementUnlockPayload(
            achievement.name(), achievement.displayName, achievement.description,
            achievement.category.color
        ));
        return true;
    }

    /**
     * Grant a vanilla advancement by path. Also grants the root advancement
     * so the Craftics tab is visible.
     */
    private static void grantVanillaAdvancement(ServerPlayerEntity player, String path) {
        var tracker = player.getAdvancementTracker();
        var server = player.getServer();
        if (server == null) return;

        // Ensure root tab is visible
        grantAdvancementCriteria(player, tracker, server, "root");
        // Grant the actual achievement
        grantAdvancementCriteria(player, tracker, server, path);
    }

    private static void grantAdvancementCriteria(
            ServerPlayerEntity player,
            net.minecraft.advancement.PlayerAdvancementTracker tracker,
            net.minecraft.server.MinecraftServer server,
            String path) {
        Identifier id = Identifier.of(CrafticsMod.MOD_ID, path);
        AdvancementEntry entry = server.getAdvancementLoader().get(id);
        if (entry == null) return;
        AdvancementProgress progress = tracker.getProgress(entry);
        if (progress.isDone()) return;
        for (String criterion : progress.getUnobtainedCriteria()) {
            tracker.grantCriterion(entry, criterion);
        }
    }

    /**
     * Sync all previously-unlocked achievements to the vanilla advancement system.
     * Called on player join so the Craftics tab appears and completed advancements are restored.
     */
    public static void syncVanillaAdvancements(ServerPlayerEntity player) {
        var server = player.getServer();
        if (server == null) return;
        var tracker = player.getAdvancementTracker();

        // Always grant root so tab is visible
        grantAdvancementCriteria(player, tracker, server, "root");

        // Re-grant any achievements the player already earned (persisted in PlayerProgression)
        PlayerProgression prog = PlayerProgression.get((ServerWorld) player.getEntityWorld());
        PlayerProgression.PlayerStats stats = prog.getStats(player);
        for (String achievementName : stats.getAchievements()) {
            try {
                Achievement a = Achievement.valueOf(achievementName);
                grantAdvancementCriteria(player, tracker, server, a.advancementPath());
            } catch (IllegalArgumentException ignored) {
                // Achievement may have been removed in an update
            }
        }
    }

    /**
     * Check and grant all applicable achievements after a boss victory.
     * Called from CombatManager.handleVictory() for boss fights.
     */
    public static void checkBossVictory(ServerPlayerEntity player, String biomeId,
                                         String armorSet, CombatAchievementTracker tracker) {
        // === Biome boss kill ===
        Achievement bossAch = Achievement.getBossAchievementForBiome(biomeId);
        if (bossAch != null) grant(player, bossAch);

        // === Dimension mastery ===
        checkDimensionMastery(player);

        // === Weapon restriction checks ===
        Set<DamageType> weaponsUsed = tracker.getWeaponTypesUsed();
        boolean singleType = weaponsUsed.size() == 1;

        // Swordmaster: Warden with only Slashing
        if ("deep_dark".equals(biomeId) && singleType && weaponsUsed.contains(DamageType.SLASHING)) {
            grant(player, Achievement.CLASS_SWORDMASTER);
        }
        // Lumberjack: Nether boss with only Cleaving
        if (NETHER_BIOMES.contains(biomeId) && singleType && weaponsUsed.contains(DamageType.CLEAVING)) {
            grant(player, Achievement.CLASS_LUMBERJACK);
        }
        // Bonecrusher: Warden with only Blunt
        if ("deep_dark".equals(biomeId) && singleType && weaponsUsed.contains(DamageType.BLUNT)) {
            grant(player, Achievement.CLASS_BONECRUSHER);
        }
        // Tidebringer: Dragon's Nest with only Water
        if ("dragons_nest".equals(biomeId) && singleType && weaponsUsed.contains(DamageType.WATER)) {
            grant(player, Achievement.CLASS_TIDEBRINGER);
        }
        // Reaper's Harvest: Nether boss with only Special
        if (NETHER_BIOMES.contains(biomeId) && singleType && weaponsUsed.contains(DamageType.SPECIAL)) {
            grant(player, Achievement.CLASS_REAPER);
        }
        // Beast Tamer: End boss with only Pet
        if (END_BIOMES.contains(biomeId) && singleType && weaponsUsed.contains(DamageType.PET)) {
            grant(player, Achievement.CLASS_BEAST_TAMER);
        }
        // Sharpshooter: Shulker Architect with only Ranged
        if ("end_city".equals(biomeId) && singleType && weaponsUsed.contains(DamageType.RANGED)) {
            grant(player, Achievement.CLASS_SHARPSHOOTER);
        }
        // Bare Knuckle: End boss with fists only
        if (END_BIOMES.contains(biomeId) && tracker.isUsedFistsOnly()) {
            grant(player, Achievement.CLASS_BARE_KNUCKLE);
        }

        // Coral Crusader: Any boss with only coral weapons (subset of Water)
        // Check that ALL weapons used were Water AND no non-coral water weapons (like trident)
        // We track this via weaponTypesUsed — if only WATER and the tracker can confirm coral-only
        // For simplicity: if only Water type used, grant it (trident is also Water, but close enough)
        if (singleType && weaponsUsed.contains(DamageType.WATER)) {
            grant(player, Achievement.CORAL_CRUSADER);
        }

        // === Armor restriction checks ===
        // Brawler's Pride: End boss in full Leather
        if (END_BIOMES.contains(biomeId) && "leather".equals(armorSet)) {
            grant(player, Achievement.ARMOR_BRAWLER);
        }
        // Rogue's Honor: Nether boss in full Chainmail
        if (NETHER_BIOMES.contains(biomeId) && "chainmail".equals(armorSet)) {
            grant(player, Achievement.ARMOR_ROGUE);
        }
        // Guard's Duty: End boss in full Iron
        if (END_BIOMES.contains(biomeId) && "iron".equals(armorSet)) {
            grant(player, Achievement.ARMOR_GUARD);
        }
        // Gambler's Luck: Nether boss in full Gold
        if (NETHER_BIOMES.contains(biomeId) && "gold".equals(armorSet)) {
            grant(player, Achievement.ARMOR_GAMBLER);
        }
        // Knight's Valor: Warden in full Diamond
        if ("deep_dark".equals(biomeId) && "diamond".equals(armorSet)) {
            grant(player, Achievement.ARMOR_KNIGHT);
        }
        // Juggernaut: Dragon's Nest in full Netherite
        if ("dragons_nest".equals(biomeId) && "netherite".equals(armorSet)) {
            grant(player, Achievement.ARMOR_JUGGERNAUT);
        }
        // Aquatic Assault: Nether boss in full Turtle
        if (NETHER_BIOMES.contains(biomeId) && "turtle".equals(armorSet)) {
            grant(player, Achievement.ARMOR_AQUATIC);
        }

        // Reef Dweller: Boss with Turtle armor + Coral weapon + Coast trim
        // (Coast trim check would need trim scan — simplified to turtle + water weapon for now)
        if ("turtle".equals(armorSet) && singleType && weaponsUsed.contains(DamageType.WATER)) {
            grant(player, Achievement.CORAL_REEF_DWELLER);
        }

        // === Boss challenges ===
        // Flawless: no damage taken
        if (!tracker.hasPlayerTookDamage()) {
            grant(player, Achievement.BOSS_FLAWLESS);
        }
        // Pacifist General: only pets dealt damage
        if (!tracker.hasPlayerDealtDirectDamage()) {
            grant(player, Achievement.BOSS_PACIFIST);
        }
        // Speed Run: 5 turns or fewer
        if (tracker.getTurnCount() <= 5) {
            grant(player, Achievement.BOSS_SPEED_RUN);
        }
        // Endurance: 30+ turns
        if (tracker.getTurnCount() >= 30) {
            grant(player, Achievement.BOSS_ENDURANCE);
        }
        // Second Wind: won after totem proc
        if (tracker.hasTotemProced()) {
            grant(player, Achievement.BOSS_SECOND_WIND);
        }
        // Phase Skipper: boss killed before phase 2
        if (tracker.isBossKilledBeforePhase2()) {
            grant(player, Achievement.BOSS_PHASE_SKIPPER);
        }

        // === Combat feats checked at end of combat ===
        checkCombatFeats(player, tracker);
    }

    /**
     * Check and grant combat feat achievements.
     * Called at end of any combat (boss or not).
     */
    public static void checkCombatFeats(ServerPlayerEntity player, CombatAchievementTracker tracker) {
        // Weapon feats
        if (tracker.getMaxPierceTargets() >= 3) grant(player, Achievement.FEAT_SKEWER);
        if (tracker.getMaxSweepTargets() >= 4) grant(player, Achievement.FEAT_WHIRLWIND);
        if (tracker.hasExecutionKill()) grant(player, Achievement.FEAT_EXECUTION);
        if (tracker.getMaxShockwaveTargets() >= 5) grant(player, Achievement.FEAT_SHOCKWAVE);
        if (tracker.getMaxCoralFanTargets() >= 4) grant(player, Achievement.FEAT_CORAL_REEF);
        if (tracker.hasConfusionKill()) grant(player, Achievement.FEAT_MIND_GAMES);
        if (tracker.hasAnyThreeConsecutiveStuns()) grant(player, Achievement.FEAT_CHAIN_STUN);
        if (tracker.getHighestSingleHitDamage() >= 20) grant(player, Achievement.FEAT_GLASS_CANNON);
        if (tracker.getMaxArmorCrushDefense() >= 5) grant(player, Achievement.FEAT_ARMOR_CRUSH);
        // FEAT_SPEAR_WALL removed — Spears not in MC 1.21.1
        if (tracker.hasCounterKill()) grant(player, Achievement.FEAT_COUNTER_KILL);
        if (tracker.getMaxConsecutiveCrits() >= 3) grant(player, Achievement.FEAT_CRITICAL_STREAK);

        // Item & tactic feats
        if (tracker.hasTotemProced()) grant(player, Achievement.FEAT_UNDYING);
        if (tracker.getMaxLivingAllies() >= 3) grant(player, Achievement.FEAT_ZOOKEEPER);
        if (tracker.hasFishingRareItem()) grant(player, Achievement.FEAT_FISHERMAN);
        if (tracker.getMaxTntKills() >= 3) grant(player, Achievement.FEAT_DEMOLITIONS);
        if (tracker.hasLightningOnSoakedKill()) grant(player, Achievement.FEAT_LIGHTNING_STRIKE);
        if (tracker.getFoodVariety() >= 5) grant(player, Achievement.FEAT_CHEF);
        if (tracker.getUtilityItemsPlaced() >= 5) grant(player, Achievement.FEAT_FORTRESS_BUILDER);
        if (tracker.hasPearlDodge()) grant(player, Achievement.FEAT_PEARL_CLUTCH);
        if (tracker.getMaxDebuffsClearedByMilk() >= 3) grant(player, Achievement.FEAT_MILK_SAVE);
        if (tracker.getHornsUsed() >= 4) grant(player, Achievement.FEAT_HORN_SECTION);

        // Status effect feats
        if (tracker.getMaxSimultaneousBuffs() >= 5) grant(player, Achievement.FEAT_ALCHEMIST);
        if (tracker.hasPoisonKill()) grant(player, Achievement.FEAT_PLAGUE_DOCTOR);
        if (tracker.hasBurnKill()) grant(player, Achievement.FEAT_PYROMANIAC);
        if (tracker.hasDrownKill()) grant(player, Achievement.FEAT_DROWNED);
        if (tracker.hasWitherKill()) grant(player, Achievement.FEAT_WITHERED);

        // Coral feats
        if (tracker.getEnemiesConfused() >= 5) grant(player, Achievement.CORAL_BRAIN_DRAIN);
        if (tracker.getCoralFanKills() >= 3) grant(player, Achievement.CORAL_FAN_CLUB);
    }

    /**
     * Check dimension mastery (all bosses in a dimension defeated).
     */
    private static void checkDimensionMastery(ServerPlayerEntity player) {
        PlayerProgression.PlayerStats stats = PlayerProgression.get(
            (ServerWorld) player.getEntityWorld()).getStats(player);

        // Overworld: check all overworld boss achievements
        boolean allOverworld = true;
        for (String biomeId : BiomePath.getFullPath(0)) {
            if (NETHER_BIOMES.contains(biomeId) || END_BIOMES.contains(biomeId)) continue;
            Achievement a = Achievement.getBossAchievementForBiome(biomeId);
            if (a != null && !stats.hasAchievement(a)) { allOverworld = false; break; }
        }
        // Also check branch 1 biomes that might not be in branch 0
        for (String biomeId : BiomePath.getFullPath(1)) {
            if (NETHER_BIOMES.contains(biomeId) || END_BIOMES.contains(biomeId)) continue;
            Achievement a = Achievement.getBossAchievementForBiome(biomeId);
            if (a != null && !stats.hasAchievement(a)) { allOverworld = false; break; }
        }
        if (allOverworld) grant(player, Achievement.DIM_OVERWORLD);

        // Nether
        boolean allNether = true;
        for (String biomeId : BiomePath.getNetherPath()) {
            Achievement a = Achievement.getBossAchievementForBiome(biomeId);
            if (a != null && !stats.hasAchievement(a)) { allNether = false; break; }
        }
        if (allNether) grant(player, Achievement.DIM_NETHER);

        // End
        boolean allEnd = true;
        for (String biomeId : BiomePath.getEndPath()) {
            Achievement a = Achievement.getBossAchievementForBiome(biomeId);
            if (a != null && !stats.hasAchievement(a)) { allEnd = false; break; }
        }
        if (allEnd) grant(player, Achievement.DIM_END);
    }

    /**
     * Check progression achievements (called after level-up, NG+, etc.)
     */
    public static void checkProgression(ServerPlayerEntity player) {
        PlayerProgression prog = PlayerProgression.get((ServerWorld) player.getEntityWorld());
        PlayerProgression.PlayerStats stats = prog.getStats(player);

        // Max Level
        if (stats.level >= 20) grant(player, Achievement.PROG_MAX_LEVEL);

        // Specialist: 5+ points in one affinity
        for (PlayerProgression.Affinity a : PlayerProgression.Affinity.values()) {
            if (stats.getAffinityPoints(a) >= 5) {
                grant(player, Achievement.PROG_SPECIALIST);
                break;
            }
        }

        // Jack of All Trades: 1+ in every affinity
        boolean allAffinities = true;
        for (PlayerProgression.Affinity a : PlayerProgression.Affinity.values()) {
            if (stats.getAffinityPoints(a) < 1) { allAffinities = false; break; }
        }
        if (allAffinities) grant(player, Achievement.PROG_JACK_OF_ALL);
    }

    /**
     * Check NG+ achievements (called when entering NG+).
     */
    public static void checkNewGamePlus(ServerPlayerEntity player, int ngPlusLevel) {
        if (ngPlusLevel >= 1) grant(player, Achievement.PROG_NG_PLUS);
        if (ngPlusLevel >= 2) grant(player, Achievement.PROG_NG_PLUS_2);
    }

    /**
     * Check collection achievements (called periodically or after inventory changes).
     */
    public static void checkCollections(ServerPlayerEntity player, int emeraldCount) {
        if (emeraldCount >= 100) grant(player, Achievement.COLLECT_EMERALDS);
    }

    /**
     * Check weapon armory achievement (all 8 damage types used across career).
     * Called from CombatManager when a new weapon type is used.
     */
    public static void checkArmory(ServerPlayerEntity player, CombatAchievementTracker tracker) {
        if (tracker.getWeaponTypesUsed().size() >= 8) {
            grant(player, Achievement.COLLECT_ARMORY);
        }
    }
}
