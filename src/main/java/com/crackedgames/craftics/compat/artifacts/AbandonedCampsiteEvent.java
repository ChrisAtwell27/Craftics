package com.crackedgames.craftics.compat.artifacts;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.CrafticsAPI;
import com.crackedgames.craftics.api.EventHandler;
import com.crackedgames.craftics.api.registry.EventEntry;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.combat.EventManager;
import com.crackedgames.craftics.combat.LootDelivery;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.level.BiomeTemplate;
import com.crackedgames.craftics.level.LevelDefinition;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * "Abandoned Campsite" between-level event from the Artifacts mod compat layer.
 * <p>
 * Registered only when the Artifacts mod is loaded. Walks the player through:
 * <ol>
 *   <li>Choice prompt: "you find an abandoned campsite, something feels off"</li>
 *   <li>If declined, normal next-level transition.</li>
 *   <li>If accepted, 50/50 split:
 *     <ul>
 *       <li><b>Lucky</b>: a random artifact is added to every party member's inventory.</li>
 *       <li><b>Mimic ambush</b>: launch a fight with one Mimic in a small biome-themed
 *           arena. Defeating it drops another random artifact via the level loot.</li>
 *     </ul>
 *   </li>
 * </ol>
 */
public final class AbandonedCampsiteEvent {

    public static final String EVENT_ID = "artifacts:abandoned_campsite";
    private static final String MIMIC_ENTITY = "artifacts:mimic";
    private static final Random RNG = new Random();

    private AbandonedCampsiteEvent() {}

    /** Register both the AI mapping and the event entry. Idempotent. */
    public static void register() {
        AIRegistry.register(MIMIC_ENTITY, new MimicAI());
        CrafticsAPI.registerEvent(new EventEntry(
            EVENT_ID,
            "Abandoned Campsite",
            0.06f,         // 6% — modest, since it has its own dedicated payoff
            0,             // available from the very first biome
            true,          // choice event — show accept/decline prompt
            HANDLER
        ));
    }

    /**
     * Pick a single random floor block from the biome's floor block array,
     * or fall back to dirt if the biome has no floor blocks defined.
     */
    private static Block pickBiomeFloor(BiomeTemplate biome) {
        if (biome != null && biome.floorBlocks != null && biome.floorBlocks.length > 0) {
            return biome.floorBlocks[RNG.nextInt(biome.floorBlocks.length)];
        }
        return Blocks.DIRT;
    }

    /**
     * Compute the mimic's HP as 0.75x the current biome's boss base HP, with a sane
     * floor for early biomes that have very weak bosses.
     */
    private static int computeMimicHp(BiomeTemplate biome) {
        if (biome == null || biome.boss == null) return 18;
        int bossHp = (int) (biome.boss.baseHp() * CrafticsMod.CONFIG.bossHpMultiplier());
        int hp = (int) Math.round(bossHp * 0.75f);
        return Math.max(15, hp);
    }

    /** The actual event handler — runs after the player accepts the prompt. */
    private static final EventHandler HANDLER = (participants, world, eventManager) -> {
        if (participants == null || participants.isEmpty()) return;
        ServerPlayerEntity leader = participants.get(0);
        CombatManager cm = CombatManager.get(leader);
        if (cm == null) {
            CrafticsMod.LOGGER.warn("[Craftics × Artifacts] Campsite event: no CombatManager for leader {}", leader.getName().getString());
            return;
        }
        BiomeTemplate biome = cm.getPendingBiome();
        CrafticsMod.LOGGER.info("[Craftics × Artifacts] Campsite event fired — leader={}, biome={}",
            leader.getName().getString(), biome != null ? biome.biomeId : "<null>");

        boolean lucky = RNG.nextBoolean();
        if (lucky) {
            // Lucky path — give every participant a random artifact, then auto-continue.
            CrafticsMod.LOGGER.info("[Craftics × Artifacts] Campsite event: LUCKY path");
            for (ServerPlayerEntity p : participants) {
                ItemStack reward = ArtifactRoller.rollOne();
                if (reward.isEmpty()) {
                    p.sendMessage(Text.literal("§7The campsite is empty after all..."), false);
                    continue;
                }
                String name = reward.getName().getString();
                LootDelivery.deliver(p, reward);
                if (!reward.isEmpty()) p.dropItem(reward, false);
                p.sendMessage(Text.literal("§a✦ You loot the campsite — found §e" + name + "§a!"), false);
            }
            // Returning here lets handlePostLevelChoice's auto-continue kick in.
            return;
        }

        // Trap path — spawn the mimic in a biome-themed arena and start combat.
        CrafticsMod.LOGGER.info("[Craftics × Artifacts] Campsite event: MIMIC TRAP path");
        for (ServerPlayerEntity p : participants) {
            p.sendMessage(Text.literal("§c§l⚠ It's a trap! §r§cThe loot pile lunges at you — MIMIC!"), false);
        }
        LevelDefinition mimicDef = buildMimicLevel(biome, computeMimicHp(biome));
        // startCustomCombat will buildArena + transitionPartyToArena, using `leader` as the
        // active player (this.player may have been nulled by a prior endCombat by this point).
        cm.startCustomCombat(leader, mimicDef);
    };

    /** Build a small arena themed to the current biome with one mimic in the middle. */
    private static LevelDefinition buildMimicLevel(BiomeTemplate biome, int mimicHp) {
        final Block floor = pickBiomeFloor(biome);
        final String biomeId = biome != null ? biome.biomeId : null;
        final int width = 9;
        final int height = 9;
        // Synthetic level number that's clearly outside the normal 1..100ish range.
        // This is NOT used for world placement (see getOverrideOrigin below) — it's
        // just a stable id for CombatManager logging and metadata lookups.
        final int levelNumber = 9500 + RNG.nextInt(400);
        final List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        spawns.add(new LevelDefinition.EnemySpawn(
            MIMIC_ENTITY,
            new GridPos(width / 2, height / 2),
            mimicHp,
            6,    // attack power (only used by Idle/Attack fallback paths)
            2,    // defense
            1));  // melee range

        return new LevelDefinition() {
            @Override public int getLevelNumber() { return levelNumber; }
            @Override public String getName() { return "Abandoned Campsite"; }
            @Override public int getWidth() { return width; }
            @Override public int getHeight() { return height; }
            @Override public GridPos getPlayerStart() { return new GridPos(1, 1); }
            @Override public Block getFloorBlock() { return floor; }
            @Override public LevelDefinition.EnemySpawn[] getEnemySpawns() {
                return spawns.toArray(new LevelDefinition.EnemySpawn[0]);
            }
            @Override public boolean isNightLevel() { return false; }
            @Override public String getArenaBiomeId() { return biomeId; }

            /**
             * Use the player's dedicated event-arena scratch position instead of
             * letting CrafticsSavedData.getArenaOrigin multiply our synthetic
             * level number by 300 — that would send us to a million-block-away
             * unloaded chunk and make the whole encounter invisible.
             */
            @Override
            public net.minecraft.util.math.BlockPos getOverrideOrigin(
                    java.util.UUID worldOwner,
                    com.crackedgames.craftics.world.CrafticsSavedData data) {
                if (worldOwner == null || data == null) return null;
                return data.getEventArenaOrigin(worldOwner);
            }

            @Override public List<ItemStack> rollCompletionLoot() {
                List<ItemStack> loot = new ArrayList<>();
                ItemStack drop = ArtifactRoller.rollOne();
                if (!drop.isEmpty()) loot.add(drop);
                return loot;
            }
        };
    }
}
