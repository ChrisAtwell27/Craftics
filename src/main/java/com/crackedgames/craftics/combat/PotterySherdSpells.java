package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Pottery Sherd Spells — ancient magic sealed within pottery sherds.
 * Single-use spell scrolls found through archaeology. All consumed on use.
 */
public class PotterySherdSpells {

    // ============================
    // Delayed Spell Effect System
    // ============================

    /** Queued visual effects (particles/sounds) staged across ticks for dramatic flair. */
    public static final List<DelayedSpellEffect> PENDING_EFFECTS = new ArrayList<>();

    /** A particle/sound effect scheduled to fire after a tick delay. */
    public static class DelayedSpellEffect {
        public int ticksRemaining;
        public final Runnable effect;
        public DelayedSpellEffect(int ticks, Runnable effect) {
            this.ticksRemaining = ticks;
            this.effect = effect;
        }
    }

    /** Queue a visual effect to fire after the given number of server ticks (20 ticks = 1 second). */
    private static void queueEffect(int delayTicks, Runnable effect) {
        PENDING_EFFECTS.add(new DelayedSpellEffect(delayTicks, effect));
    }

    /** Returns the max delay across all pending effects (for input-blocking duration). */
    public static int getMaxPendingDelay() {
        int max = 0;
        for (DelayedSpellEffect e : PENDING_EFFECTS) max = Math.max(max, e.ticksRemaining);
        return max;
    }

    /** Prefix for AP restoration (Plenty sherd). CombatManager parses this. */
    public static final String RESTORE_AP_PREFIX = "§aRESTORE_AP:";
    /** Prefix for triple damage next attack (Prize sherd). CombatManager parses this. */
    public static final String TRIPLE_NEXT_PREFIX = "§6TRIPLE_NEXT:";
    /** Prefix for hex trap tile effect. */
    public static final String HEX_TRAP_PREFIX = "§eTILE:";

    /** All pottery sherd items that function as spells. */
    public static final Set<Item> POTTERY_SHERDS = Set.of(
        // 2 AP
        Items.EXPLORER_POTTERY_SHERD,
        Items.FRIEND_POTTERY_SHERD,
        // 3 AP
        Items.HEART_POTTERY_SHERD,
        Items.SCRAPE_POTTERY_SHERD,
        Items.ANGLER_POTTERY_SHERD,
        Items.HEARTBREAK_POTTERY_SHERD,
        Items.SHEAF_POTTERY_SHERD,
        Items.MINER_POTTERY_SHERD,
        Items.DANGER_POTTERY_SHERD,
        // 4 AP
        Items.BLADE_POTTERY_SHERD,
        Items.BURN_POTTERY_SHERD,
        Items.SNORT_POTTERY_SHERD,
        Items.SHELTER_POTTERY_SHERD,
        Items.FLOW_POTTERY_SHERD,
        Items.MOURNER_POTTERY_SHERD,
        Items.BREWER_POTTERY_SHERD,
        Items.PLENTY_POTTERY_SHERD,
        Items.GUSTER_POTTERY_SHERD,
        // 5 AP
        Items.ARCHER_POTTERY_SHERD,
        Items.HOWL_POTTERY_SHERD,
        Items.ARMS_UP_POTTERY_SHERD,
        Items.PRIZE_POTTERY_SHERD,
        // 6 AP
        Items.SKULL_POTTERY_SHERD
    );

    public static boolean isPotterySherd(Item item) {
        return POTTERY_SHERDS.contains(item);
    }

    public static int getSherdApCost(Item item) {
        // 2 AP — Quick casts
        if (item == Items.EXPLORER_POTTERY_SHERD || item == Items.FRIEND_POTTERY_SHERD) return 2;
        // 3 AP — Standard spells
        if (item == Items.HEART_POTTERY_SHERD || item == Items.SCRAPE_POTTERY_SHERD
            || item == Items.ANGLER_POTTERY_SHERD || item == Items.HEARTBREAK_POTTERY_SHERD
            || item == Items.SHEAF_POTTERY_SHERD || item == Items.MINER_POTTERY_SHERD
            || item == Items.DANGER_POTTERY_SHERD) return 3;
        // 4 AP — Strong spells
        if (item == Items.BLADE_POTTERY_SHERD || item == Items.BURN_POTTERY_SHERD
            || item == Items.SNORT_POTTERY_SHERD || item == Items.SHELTER_POTTERY_SHERD
            || item == Items.FLOW_POTTERY_SHERD || item == Items.MOURNER_POTTERY_SHERD
            || item == Items.BREWER_POTTERY_SHERD || item == Items.PLENTY_POTTERY_SHERD
            || item == Items.GUSTER_POTTERY_SHERD) return 4;
        // 5 AP — Powerful spells
        if (item == Items.ARCHER_POTTERY_SHERD || item == Items.HOWL_POTTERY_SHERD
            || item == Items.ARMS_UP_POTTERY_SHERD || item == Items.PRIZE_POTTERY_SHERD) return 5;
        // 6 AP — Ultimate
        if (item == Items.SKULL_POTTERY_SHERD) return 6;
        return 3; // fallback
    }

    /**
     * Get the maximum targeting range for a sherd spell.
     * Returns 0 for self-cast spells (no target tile needed),
     * or the manhattan distance range for targeted spells.
     */
    public static int getSherdRange(Item item) {
        if (item == Items.FRIEND_POTTERY_SHERD || item == Items.HEART_POTTERY_SHERD
            || item == Items.SHELTER_POTTERY_SHERD || item == Items.FLOW_POTTERY_SHERD
            || item == Items.BREWER_POTTERY_SHERD || item == Items.PLENTY_POTTERY_SHERD
            || item == Items.HOWL_POTTERY_SHERD || item == Items.ARMS_UP_POTTERY_SHERD
            || item == Items.PRIZE_POTTERY_SHERD) return 0; // self-cast
        if (item == Items.EXPLORER_POTTERY_SHERD || item == Items.ARCHER_POTTERY_SHERD) return 4;
        if (item == Items.SCRAPE_POTTERY_SHERD || item == Items.ANGLER_POTTERY_SHERD
            || item == Items.HEARTBREAK_POTTERY_SHERD || item == Items.SHEAF_POTTERY_SHERD
            || item == Items.DANGER_POTTERY_SHERD || item == Items.BURN_POTTERY_SHERD
            || item == Items.MOURNER_POTTERY_SHERD || item == Items.SKULL_POTTERY_SHERD
            || item == Items.GUSTER_POTTERY_SHERD) return 3;
        if (item == Items.MINER_POTTERY_SHERD || item == Items.SNORT_POTTERY_SHERD) return 2;
        if (item == Items.BLADE_POTTERY_SHERD) return 1;
        return 3;
    }

    /** Returns true if this sherd spell targets self (no target tile required). */
    public static boolean isSelfCast(Item item) {
        return getSherdRange(item) == 0;
    }

    /**
     * Use a pottery sherd spell. Returns a message string, or null if the sherd isn't a spell.
     * Handles consuming the sherd, dealing damage, applying effects, and spawning particles/sounds.
     */
    public static String useSherd(ServerPlayerEntity player, GridArena arena, GridPos targetTile,
                                   List<CombatEntity> enemies, CombatEffects combatEffects) {
        Item item = player.getMainHandStack().getItem();
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        GridPos playerPos = arena.getPlayerGridPos();
        BlockPos playerBlock = arena.gridToBlockPos(playerPos);

        // Consume the sherd
        player.getMainHandStack().decrement(1);

        // Dispatch to specific spell
        if (item == Items.EXPLORER_POTTERY_SHERD) return useExplorerSherd(player, arena, world, targetTile, playerPos, playerBlock, enemies);
        if (item == Items.FRIEND_POTTERY_SHERD) return useFriendSherd(player, world, playerBlock, enemies);
        if (item == Items.HEART_POTTERY_SHERD) return useHeartSherd(player, world, playerBlock, combatEffects);
        if (item == Items.SCRAPE_POTTERY_SHERD) return useScrapeSherd(player, arena, world, targetTile, playerPos, playerBlock);
        if (item == Items.ANGLER_POTTERY_SHERD) return useAnglerSherd(player, arena, world, targetTile, playerPos, playerBlock);
        if (item == Items.HEARTBREAK_POTTERY_SHERD) return useHeartbreakSherd(player, arena, world, targetTile, playerPos, playerBlock);
        if (item == Items.SHEAF_POTTERY_SHERD) return useSheafSherd(player, arena, world, targetTile, playerPos, playerBlock, enemies);
        if (item == Items.MINER_POTTERY_SHERD) return useMinerSherd(player, arena, world, targetTile, playerPos, playerBlock);
        if (item == Items.DANGER_POTTERY_SHERD) return useDangerSherd(player, arena, world, targetTile, playerPos, playerBlock);
        if (item == Items.BLADE_POTTERY_SHERD) return useBladeSherd(player, arena, world, targetTile, playerPos, playerBlock, enemies);
        if (item == Items.BURN_POTTERY_SHERD) return useBurnSherd(player, arena, world, targetTile, playerPos, playerBlock, enemies);
        if (item == Items.SNORT_POTTERY_SHERD) return useSnortSherd(player, arena, world, targetTile, playerPos, playerBlock);
        if (item == Items.SHELTER_POTTERY_SHERD) return useShelterSherd(player, world, playerBlock, combatEffects);
        if (item == Items.FLOW_POTTERY_SHERD) return useFlowSherd(player, arena, world, playerPos, playerBlock, enemies);
        if (item == Items.MOURNER_POTTERY_SHERD) return useMournerSherd(player, arena, world, targetTile, playerPos, playerBlock);
        if (item == Items.BREWER_POTTERY_SHERD) return useBrewerSherd(player, world, playerBlock, combatEffects);
        if (item == Items.PLENTY_POTTERY_SHERD) return usePlentySherd(player, world, playerBlock);
        if (item == Items.ARCHER_POTTERY_SHERD) return useArcherSherd(player, arena, world, targetTile, playerPos, playerBlock, enemies);
        if (item == Items.HOWL_POTTERY_SHERD) return useHowlSherd(player, arena, world, playerPos, playerBlock, enemies);
        if (item == Items.ARMS_UP_POTTERY_SHERD) return useArmsUpSherd(player, world, playerBlock, combatEffects);
        if (item == Items.PRIZE_POTTERY_SHERD) return usePrizeSherd(player, world, playerBlock, combatEffects);
        if (item == Items.SKULL_POTTERY_SHERD) return useSkullSherd(player, arena, world, targetTile, playerPos, playerBlock);
        if (item == Items.GUSTER_POTTERY_SHERD) return useGusterSherd(player, arena, world, targetTile, playerPos, playerBlock, enemies);

        return null;
    }

    /**
     * Validate a sherd spell can be cast. Returns an error message, or null if valid.
     */
    public static String validateSherd(Item item, GridArena arena, GridPos targetTile, List<CombatEntity> enemies) {
        GridPos playerPos = arena.getPlayerGridPos();
        int range = getSherdRange(item);

        // Self-cast spells don't need a target tile
        if (isSelfCast(item)) return null;

        if (targetTile == null) return "§cNeed to target a tile!";

        // Explorer targets walkable tiles, Danger targets empty tiles
        if (item == Items.EXPLORER_POTTERY_SHERD) {
            if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
            if (arena.isOccupied(targetTile)) return "§cTile is occupied!";
            var tile = arena.getTile(targetTile);
            if (tile == null || !tile.isWalkable()) return "§cCan't teleport there!";
            if (playerPos.manhattanDistance(targetTile) > range) return "§cOut of range! (max " + range + " tiles)";
            return null;
        }

        if (item == Items.DANGER_POTTERY_SHERD) {
            if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
            if (arena.isOccupied(targetTile)) return "§cTile must be empty!";
            var tile = arena.getTile(targetTile);
            if (tile == null || !tile.isWalkable()) return "§cInvalid tile!";
            if (playerPos.manhattanDistance(targetTile) > range) return "§cOut of range! (max " + range + " tiles)";
            return null;
        }

        // All other targeted spells need an enemy at the target
        CombatEntity target = arena.getOccupant(targetTile);
        if (target == null || !target.isAlive() || target.isAlly()) return "§cNo enemy at target!";
        if (playerPos.manhattanDistance(targetTile) > range) return "§cOut of range! (max " + range + " tiles)";
        return null;
    }

    // ================================
    // 2 AP — Quick Casts
    // ================================

    /** Explorer Sherd — "Phase Step": Teleport to target tile, reveal enemy stats. */
    private static String useExplorerSherd(ServerPlayerEntity player, GridArena arena, ServerWorld world,
                                            GridPos targetTile, GridPos playerPos, BlockPos playerBlock,
                                            List<CombatEntity> enemies) {
        // Phase 0 — Cast: power gathers at origin
        world.playSound(null, playerBlock, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        ProjectileSpawner.spawnConverging(world, playerBlock, 1.2, ParticleTypes.REVERSE_PORTAL, 12);
        world.spawnParticles(ParticleTypes.END_ROD, playerBlock.getX() + 0.5, playerBlock.getY() + 1.0,
            playerBlock.getZ() + 0.5, 15, 0.3, 0.8, 0.3, 0.05);

        // Teleport immediately (game state)
        arena.setPlayerGridPos(targetTile);
        BlockPos destBlock = arena.gridToBlockPos(targetTile);
        player.requestTeleport(destBlock.getX() + 0.5, destBlock.getY(), destBlock.getZ() + 0.5);

        // Phase 1 (3 ticks) — Portal trail streaks from origin to destination
        final BlockPos fromBlock = playerBlock;
        queueEffect(3, () -> {
            ProjectileSpawner.spawnSpellTrail(world, fromBlock, destBlock, ParticleTypes.PORTAL, ParticleTypes.END_ROD, 14, 0.5);
            world.playSound(null, destBlock, SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.3f, 1.5f);
        });

        // Phase 2 (6 ticks) — Arrival burst at destination
        queueEffect(6, () -> {
            world.spawnParticles(ParticleTypes.END_ROD, destBlock.getX() + 0.5, destBlock.getY() + 1.0,
                destBlock.getZ() + 0.5, 20, 0.5, 0.8, 0.5, 0.1);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, destBlock.getX() + 0.5, destBlock.getY() + 0.5,
                destBlock.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.05);
            ProjectileSpawner.spawnExpandingRing(world, destBlock, 0.8, ParticleTypes.END_ROD, 10);
            world.playSound(null, destBlock, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.3f);
        });

        // Reveal enemy stats (build info string)
        StringBuilder reveal = new StringBuilder("§d§lPhase Step! §7Enemies revealed: ");
        for (CombatEntity e : enemies) {
            if (!e.isAlive() || e.isAlly()) continue;
            reveal.append("§f").append(e.getDisplayName())
                .append(" §7[HP:").append(e.getCurrentHp()).append("/").append(e.getMaxHp())
                .append(" ATK:").append(e.getAttackPower())
                .append(" DEF:").append(e.getDefense())
                .append(" SPD:").append(e.getMoveSpeed()).append("] ");
        }

        return reveal.toString();
    }

    /** Friend Sherd — "Guardian Spirit": Heal 8 HP, buff ally pet. */
    private static String useFriendSherd(ServerPlayerEntity player, ServerWorld world, BlockPos playerBlock,
                                          List<CombatEntity> enemies) {
        // Phase 0 — Cast: warm gathering glow
        world.playSound(null, playerBlock, SoundEvents.ENTITY_CAT_PURR, SoundCategory.PLAYERS, 1.0f, 1.0f);
        ProjectileSpawner.spawnConverging(world, playerBlock, 1.0, ParticleTypes.HAPPY_VILLAGER, 8);
        world.spawnParticles(ParticleTypes.ENCHANT, playerBlock.getX() + 0.5, playerBlock.getY() + 1.0,
            playerBlock.getZ() + 0.5, 8, 0.3, 0.5, 0.3, 0.02);

        // Heal player 8 HP (buffed from 5)
        float maxHp = player.getMaxHealth();
        player.setHealth(Math.min(maxHp, player.getHealth() + 8));

        // Find ally pet
        CombatEntity allyPet = null;
        for (CombatEntity e : enemies) {
            if (e.isAlive() && e.isAlly()) { allyPet = e; break; }
        }

        // Phase 1 (3 ticks) — Healing burst on player + pet link
        final CombatEntity pet = allyPet;
        queueEffect(3, () -> {
            world.playSound(null, playerBlock, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
            world.spawnParticles(ParticleTypes.HEART, playerBlock.getX() + 0.5, playerBlock.getY() + 1.5,
                playerBlock.getZ() + 0.5, 8, 0.4, 0.5, 0.4, 0.05);
            if (pet != null && pet.getMobEntity() != null) {
                BlockPos petBlock = pet.getMobEntity().getBlockPos();
                ProjectileSpawner.spawnSpellTrail(world, playerBlock, petBlock,
                    ParticleTypes.HAPPY_VILLAGER, ParticleTypes.ENCHANT, 12, 0.4);
            }
        });

        if (allyPet != null) {
            // Buff pet immediately (game state): heal 8 HP, +5 ATK
            allyPet.heal(8);
            allyPet.setAttackPenalty(allyPet.getAttackPenalty() - 5);

            // Phase 2 (6 ticks) — Pet empowerment burst
            if (allyPet.getMobEntity() != null) {
                final BlockPos petBlock = allyPet.getMobEntity().getBlockPos();
                queueEffect(6, () -> {
                    world.spawnParticles(ParticleTypes.HEART, petBlock.getX() + 0.5, petBlock.getY() + 1.5,
                        petBlock.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
                    world.spawnParticles(ParticleTypes.ENCHANTED_HIT, petBlock.getX() + 0.5, petBlock.getY() + 1.0,
                        petBlock.getZ() + 0.5, 10, 0.4, 0.4, 0.4, 0.12);
                    ProjectileSpawner.spawnExpandingRing(world, petBlock, 0.6, ParticleTypes.HAPPY_VILLAGER, 8);
                    world.playSound(null, petBlock, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.5f);
                });
            }

            return "§a§lGuardian Spirit! §fHealed 8 HP. " + allyPet.getDisplayName() + " healed & empowered (+5 ATK for 2 turns)!";
        }

        // Phase 2 (6 ticks) — Solo healing finisher
        queueEffect(6, () -> {
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, playerBlock.getX() + 0.5, playerBlock.getY() + 1.0,
                playerBlock.getZ() + 0.5, 8, 0.5, 0.5, 0.5, 0.05);
            ProjectileSpawner.spawnExpandingRing(world, playerBlock, 0.6, ParticleTypes.HEART, 6);
        });

        return "§a§lGuardian Spirit! §fHealed 8 HP.";
    }

    // ================================
    // 3 AP — Standard Spells
    // ================================

    /** Heart Sherd — "Mending Light": Heal 15 HP + Regeneration II for 4 turns. */
    private static String useHeartSherd(ServerPlayerEntity player, ServerWorld world, BlockPos playerBlock,
                                         CombatEffects combatEffects) {
        // Phase 0 — Cast: warm light gathers
        world.playSound(null, playerBlock, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
        ProjectileSpawner.spawnConverging(world, playerBlock, 1.5, ParticleTypes.ENCHANT, 10);

        // Heal 15 HP (buffed from 10) + Regen II for 4 turns (buffed from 3)
        float maxHp = player.getMaxHealth();
        player.setHealth(Math.min(maxHp, player.getHealth() + 15));
        combatEffects.addEffect(CombatEffects.EffectType.REGENERATION, 4, 1);

        // Phase 1 (3 ticks) — Rising helix
        double cx = playerBlock.getX() + 0.5, cy = playerBlock.getY() + 0.5, cz = playerBlock.getZ() + 0.5;
        queueEffect(3, () -> {
            for (int i = 0; i < 16; i++) {
                double angle = (2 * Math.PI * i / 16) * 2;
                double y = cy + (2.5 * i / 16);
                double x = cx + Math.cos(angle) * 0.6;
                double z = cz + Math.sin(angle) * 0.6;
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            world.playSound(null, playerBlock, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.4f, 1.5f);
        });

        // Phase 2 (7 ticks) — Heart burst + ring
        queueEffect(7, () -> {
            world.spawnParticles(ParticleTypes.HEART, cx, cy + 1.5, cz, 12, 0.6, 0.3, 0.6, 0.08);
            ProjectileSpawner.spawnExpandingRing(world, playerBlock, 1.0, ParticleTypes.ENCHANTED_HIT, 12);
            world.spawnParticles(ParticleTypes.FIREWORK, cx, cy + 2.0, cz, 2, 0.1, 0.1, 0.1, 0.02);
        });

        return "§a§lMending Light! §fHealed 15 HP + Regeneration II (4 turns).";
    }

    /** Scrape Sherd — "Corrode": 5 damage + reduce target defense by 7 for 3 turns. */
    private static String useScrapeSherd(ServerPlayerEntity player, GridArena arena, ServerWorld world,
                                          GridPos targetTile, GridPos playerPos, BlockPos playerBlock) {
        CombatEntity target = arena.getOccupant(targetTile);
        BlockPos targetBlock = arena.gridToBlockPos(targetTile);

        // Phase 0 — Cast: acid charging
        world.playSound(null, playerBlock, SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.ITEM_SLIME, playerBlock.getX() + 0.5, playerBlock.getY() + 1.0,
            playerBlock.getZ() + 0.5, 6, 0.2, 0.3, 0.2, 0.05);

        // Damage + debuff immediately (game state): 5 dmg (buffed from 3), -7 DEF (buffed from -5)
        int dealt = target.takeDamage(5);
        target.setDefensePenalty(target.getDefensePenalty() + 7);
        target.setDefensePenaltyTurns(Math.max(target.getDefensePenaltyTurns(), 3));

        // Phase 1 (4 ticks) — Acid trail
        queueEffect(4, () -> {
            ProjectileSpawner.spawnSpellTrail(world, playerBlock, targetBlock,
                ParticleTypes.ITEM_SLIME, ParticleTypes.FALLING_OBSIDIAN_TEAR, 12, 0.8);
            world.playSound(null, targetBlock, SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 0.5f, 0.8f);
        });

        // Phase 2 (8 ticks) — Corroding impact
        queueEffect(8, () -> {
            world.spawnParticles(ParticleTypes.ITEM_SLIME, targetBlock.getX() + 0.5, targetBlock.getY() + 1.0,
                targetBlock.getZ() + 0.5, 20, 0.5, 0.7, 0.5, 0.1);
            world.spawnParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, targetBlock.getX() + 0.5, targetBlock.getY() + 2.0,
                targetBlock.getZ() + 0.5, 10, 0.3, 0.1, 0.3, 0.01);
            ProjectileSpawner.spawnExpandingRing(world, targetBlock, 0.5, ParticleTypes.ITEM_SLIME, 8);
            world.playSound(null, targetBlock, SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS, 0.6f, 0.6f);
        });

        return "§d§lCorrode! §f" + target.getDisplayName() + " takes " + dealt + " damage! Defense reduced by 7 for 3 turns. ("
            + target.getCurrentHp() + "/" + target.getMaxHp() + " HP)";
    }

    /** Angler Sherd — "Riptide Hook": Pull target 2 tiles toward player + 6 damage, +5 bonus if adjacent. */
    private static String useAnglerSherd(ServerPlayerEntity player, GridArena arena, ServerWorld world,
                                          GridPos targetTile, GridPos playerPos, BlockPos playerBlock) {
        CombatEntity target = arena.getOccupant(targetTile);
        BlockPos targetBlock = arena.gridToBlockPos(targetTile);

        // Phase 0 — Cast: hook launch
        world.playSound(null, playerBlock, SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.SPLASH, playerBlock.getX() + 0.5, playerBlock.getY() + 1.0,
            playerBlock.getZ() + 0.5, 6, 0.2, 0.3, 0.2, 0.05);

        // Pull target toward player (up to 2 tiles) — game state
        int dx = Integer.signum(playerPos.x() - target.getGridPos().x());
        int dz = Integer.signum(playerPos.z() - target.getGridPos().z());
        GridPos pullPos = target.getGridPos();
        for (int i = 1; i <= 2; i++) {
            GridPos candidate = new GridPos(target.getGridPos().x() + dx * i, target.getGridPos().z() + dz * i);
            if (candidate.equals(playerPos)) break;
            if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) break;
            var tile = arena.getTile(candidate);
            if (tile == null || !tile.isWalkable()) break;
            pullPos = candidate;
        }

        // Move entity
        final BlockPos landingBlock;
        if (!pullPos.equals(target.getGridPos())) {
            BlockPos oldBlock = arena.gridToBlockPos(target.getGridPos());
            BlockPos newBlock = arena.gridToBlockPos(pullPos);
            landingBlock = newBlock;
            arena.moveEntity(target, pullPos);
            if (target.getMobEntity() != null) {
                target.getMobEntity().requestTeleport(newBlock.getX() + 0.5, newBlock.getY(), newBlock.getZ() + 0.5);
            }
            // Phase 1 (3 ticks) — Hook trail + bubble pull trail
            queueEffect(3, () -> {
                ProjectileSpawner.spawnSpellTrail(world, playerBlock, targetBlock, ParticleTypes.CRIT, null, 10, 0.5);
                ProjectileSpawner.spawnReversedTrail(world, oldBlock, newBlock, ParticleTypes.BUBBLE, 8);
                world.playSound(null, playerBlock, SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.PLAYERS, 1.0f, 1.0f);
            });
        } else {
            landingBlock = targetBlock;
            queueEffect(3, () -> {
                ProjectileSpawner.spawnSpellTrail(world, playerBlock, targetBlock, ParticleTypes.CRIT, null, 10, 0.5);
                world.playSound(null, playerBlock, SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.PLAYERS, 1.0f, 1.0f);
            });
        }

        // 6 base damage (buffed from 4), +5 adjacent bonus (buffed from +3)
        int baseDmg = 6;
        boolean adjacent = pullPos.manhattanDistance(playerPos) <= 1;
        if (adjacent) baseDmg += 5;
        int dealt = target.takeDamage(baseDmg);

        // Phase 2 (7 ticks) — Impact splash
        queueEffect(7, () -> {
            world.spawnParticles(ParticleTypes.SPLASH, landingBlock.getX() + 0.5, landingBlock.getY() + 1.0,
                landingBlock.getZ() + 0.5, 15, 0.4, 0.4, 0.4, 0.12);
            world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, landingBlock.getX() + 0.5, landingBlock.getY() + 1.0,
                landingBlock.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.1);
            if (adjacent) {
                world.spawnParticles(ParticleTypes.CRIT, landingBlock.getX() + 0.5, landingBlock.getY() + 1.0,
                    landingBlock.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.15);
            }
        });

        String bonus = adjacent ? " §b(Reeled in! +5 bonus)" : "";
        return "§3§lRiptide Hook! §f" + target.getDisplayName() + " takes " + dealt + " WATER damage!" + bonus
            + " (" + target.getCurrentHp() + "/" + target.getMaxHp() + " HP)";
    }

    /** Heartbreak Sherd — "Shatter Will": 5 damage + -5 attack, -4 speed for 2 turns. */
    private static String useHeartbreakSherd(ServerPlayerEntity player, GridArena arena, ServerWorld world,
                                              GridPos targetTile, GridPos playerPos, BlockPos playerBlock) {
        CombatEntity target = arena.getOccupant(targetTile);
        BlockPos targetBlock = arena.gridToBlockPos(targetTile);

        // Phase 0 — Cast: dark energy gathering
        world.playSound(null, playerBlock, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.spawnParticles(ParticleTypes.ENCHANTED_HIT, playerBlock.getX() + 0.5, playerBlock.getY() + 1.0,
            playerBlock.getZ() + 0.5, 8, 0.3, 0.4, 0.3, 0.08);

        // Damage + debuffs immediately: 5 dmg (buffed from 3), -5 ATK (from -4), -4 SPD (from -3)
        int dealt = target.takeDamage(5);
        target.setAttackPenalty(target.getAttackPenalty() + 5);
        target.setSpeedBonus(target.getSpeedBonus() - 4);

        // Phase 1 (4 ticks) — Shattering trail
        queueEffect(4, () -> {
            ProjectileSpawner.spawnSpellTrail(world, playerBlock, targetBlock,
                ParticleTypes.ENCHANTED_HIT, ParticleTypes.LARGE_SMOKE, 10, 1.2);
            world.playSound(null, targetBlock, SoundEvents.BLOCK_ANVIL_DESTROY, SoundCategory.PLAYERS, 0.5f, 1.2f);
        });

        // Phase 2 (8 ticks) — Shattered heart impact
        queueEffect(8, () -> {
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT, targetBlock.getX() + 0.5, targetBlock.getY() + 1.0,
                targetBlock.getZ() + 0.5, 15, 0.6, 0.6, 0.6, 0.18);
            world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, targetBlock.getX() + 0.5, targetBlock.getY() + 2.0,
                targetBlock.getZ() + 0.5, 5, 0.3, 0.1, 0.3, 0.0);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, targetBlock.getX() + 0.5, targetBlock.getY() + 1.0,
                targetBlock.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.03);
            ProjectileSpawner.spawnExpandingRing(world, targetBlock, 0.6, ParticleTypes.ENCHANTED_HIT, 8);
        });

        return "§d§lShatter Will! §f" + target.getDisplayName() + " takes " + dealt + " damage! -5 ATK, -4 SPD for 2 turns. ("
            + target.getCurrentHp() + "/" + target.getMaxHp() + " HP)";
    }

    /** Sheaf Sherd — "Entangle": Target stunned 1 turn, nearby enemies -5 speed for 2 turns. */
    private static String useSheafSherd(ServerPlayerEntity player, GridArena arena, ServerWorld world,
                                         GridPos targetTile, GridPos playerPos, BlockPos playerBlock,
                                         List<CombatEntity> enemies) {
        CombatEntity target = arena.getOccupant(targetTile);
        BlockPos targetBlock = arena.gridToBlockPos(targetTile);

        // Phase 0 — Cast: nature gathering
        world.playSound(null, playerBlock, SoundEvents.BLOCK_GRASS_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.COMPOSTER, playerBlock.getX() + 0.5, playerBlock.getY() + 0.5,
            playerBlock.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.05);

        // Stun + slow immediately (game state)
        target.setStunned(true);

        // Collect slowed enemy positions for delayed particles
        List<BlockPos> slowedPositions = new ArrayList<>();
        int slowed = 0;
        for (CombatEntity e : enemies) {
            if (!e.isAlive() || e.isAlly() || e == target) continue;
            if (e.getGridPos().manhattanDistance(targetTile) <= 1) {
                e.setSpeedBonus(e.getSpeedBonus() - 5); // buffed from -3
                slowed++;
                if (e.getMobEntity() != null) {
                    slowedPositions.add(arena.gridToBlockPos(e.getGridPos()));
                }
            }
        }

        // Phase 1 (4 ticks) — Vine trail to target
        queueEffect(4, () -> {
            ProjectileSpawner.spawnSpellTrail(world, playerBlock, targetBlock,
                ParticleTypes.COMPOSTER, ParticleTypes.HAPPY_VILLAGER, 12, 0.8);
            world.playSound(null, targetBlock, SoundEvents.BLOCK_VINE_PLACE, SoundCategory.PLAYERS, 0.8f, 0.8f);
        });

        // Phase 2 (8 ticks) — Vines erupt at target + AoE
        final int slowedCount = slowed;
        queueEffect(8, () -> {
            world.spawnParticles(ParticleTypes.COMPOSTER, targetBlock.getX() + 0.5, targetBlock.getY() + 0.2,
                targetBlock.getZ() + 0.5, 25, 0.3, 1.0, 0.3, 0.12);
            world.spawnParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, targetBlock.getX() + 0.5, targetBlock.getY() + 2.5,
                targetBlock.getZ() + 0.5, 10, 0.5, 0.1, 0.5, 0.0);
            ProjectileSpawner.spawnExpandingRing(world, targetBlock, 0.8, ParticleTypes.COMPOSTER, 10);
            for (BlockPos eBlock : slowedPositions) {
                world.spawnParticles(ParticleTypes.COMPOSTER, eBlock.getX() + 0.5, eBlock.getY() + 0.2,
                    eBlock.getZ() + 0.5, 10, 0.2, 0.5, 0.2, 0.08);
                world.spawnParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, eBlock.getX() + 0.5, eBlock.getY() + 2.0,
                    eBlock.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.0);
            }
        });

        String aoeMsg = slowed > 0 ? " " + slowed + " nearby enemies slowed!" : "";
        return "§2§lEntangle! §f" + target.getDisplayName() + " stunned for 1 turn!" + aoeMsg;
    }

    /** Miner Sherd — "Earthen Spike": 10 damage, +6 if adjacent to obstacle. */
    private static String useMinerSherd(ServerPlayerEntity player, GridArena arena, ServerWorld world,
                                         GridPos targetTile, GridPos playerPos, BlockPos playerBlock) {
        CombatEntity target = arena.getOccupant(targetTile);
        BlockPos targetBlock = arena.gridToBlockPos(targetTile);

        // Phase 0 — Cast: earth rumble
        world.playSound(null, playerBlock, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
        world.spawnParticles(ParticleTypes.DUST_PLUME, playerBlock.getX() + 0.5, playerBlock.getY() + 0.2,
            playerBlock.getZ() + 0.5, 8, 0.3, 0.1, 0.3, 0.08);

        // Check for adjacent obstacles (game state)
        boolean nearObstacle = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                GridPos adj = new GridPos(targetTile.x() + dx, targetTile.z() + dz);
                if (!arena.isInBounds(adj)) { nearObstacle = true; break; }
                GridTile tile = arena.getTile(adj);
                if (tile != null && !tile.isWalkable()) { nearObstacle = true; break; }
            }
            if (nearObstacle) break;
        }

        int baseDmg = nearObstacle ? 16 : 10; // buffed from 11/7
        int dealt = target.takeDamage(baseDmg);

        // Phase 1 (4 ticks) — Ground trail + rumble
        final boolean hasBonus = nearObstacle;
        queueEffect(4, () -> {
            ProjectileSpawner.spawnGroundTrail(world, playerBlock, targetBlock, ParticleTypes.DUST_PLUME, 10);
            world.playSound(null, targetBlock, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.5f, 1.0f);
        });

        // Phase 2 (8 ticks) — Spike eruption impact
        queueEffect(8, () -> {
            int particleCount = hasBonus ? 50 : 30;
            world.spawnParticles(ParticleTypes.DUST_PLUME, targetBlock.getX() + 0.5, targetBlock.getY() + 0.5,
                targetBlock.getZ() + 0.5, particleCount, 0.3, 0.0, 0.3, 0.35);
            world.spawnParticles(ParticleTypes.EXPLOSION, targetBlock.getX() + 0.5, targetBlock.getY() + 1.0,
                targetBlock.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0);
            world.spawnParticles(ParticleTypes.CLOUD, targetBlock.getX() + 0.5, targetBlock.getY() + 0.5,
                targetBlock.getZ() + 0.5, 8, 0.4, 0.3, 0.4, 0.04);
            ProjectileSpawner.spawnExpandingRing(world, targetBlock, 0.6, ParticleTypes.DUST_PLUME, 8);
            world.playSound(null, targetBlock, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1.2f, 0.6f);
        });

        String bonus = nearObstacle ? " §8(Near obstacle! +6 bonus)" : "";
        return "§8§lEarthen Spike! §f" + target.getDisplayName() + " takes " + dealt + " BLUNT damage!" + bonus
            + " (" + target.getCurrentHp() + "/" + target.getMaxHp() + " HP)";
    }

    /** Danger Sherd — "Hex Trap": Place invisible trap on empty tile. 12 dmg + stun on trigger. */
    private static String useDangerSherd(ServerPlayerEntity player, GridArena arena, ServerWorld world,
                                          GridPos targetTile, GridPos playerPos, BlockPos playerBlock) {
        BlockPos targetBlock = arena.gridToBlockPos(targetTile);

        // Phase 0 — Cast: hex conjuring
        world.playSound(null, playerBlock, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.5f, 1.0f);
        world.spawnParticles(ParticleTypes.WITCH, playerBlock.getX() + 0.5, playerBlock.getY() + 1.2,
            playerBlock.getZ() + 0.5, 6, 0.2, 0.3, 0.2, 0.05);
        ProjectileSpawner.spawnConverging(world, playerBlock, 0.8, ParticleTypes.ENCHANT, 6);

        // Phase 1 (4 ticks) — Dark trail to tile
        queueEffect(4, () -> {
            ProjectileSpawner.spawnSpellTrail(world, playerBlock, targetBlock, ParticleTypes.WITCH, ParticleTypes.ENCHANT, 8, 0.5);
            world.playSound(null, targetBlock, SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 0.4f, 0.8f);
        });

        // Phase 2 (8 ticks) — Trap sinks into ground
        queueEffect(8, () -> {
            world.spawnParticles(ParticleTypes.ENCHANT, targetBlock.getX() + 0.5, targetBlock.getY() + 1.5,
                targetBlock.getZ() + 0.5, 15, 0.3, 0.8, 0.3, 0.0);
            world.spawnParticles(ParticleTypes.WITCH, targetBlock.getX() + 0.5, targetBlock.getY() + 0.5,
                targetBlock.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.03);
            ProjectileSpawner.spawnExpandingRing(world, targetBlock, 0.5, ParticleTypes.WITCH, 6);
            world.playSound(null, targetBlock, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 0.3f, 1.5f);
        });

        // Return tile effect — CombatManager will register it (buffed from 8 to 12 dmg)
        return HEX_TRAP_PREFIX + "hex_trap:" + targetTile.x() + ":" + targetTile.z()
            + "|§d§lHex Trap placed! §7Invisible trap set at (" + targetTile.x() + "," + targetTile.z() + "). 12 damage + stun on trigger.";
    }

    // ================================
    // 4 AP — Strong Spells
    // ================================

    /** Blade Sherd — "Phantom Slash": 12 damage to adjacent target + 8 to random nearby enemy. */
    private static String useBladeSherd(ServerPlayerEntity player, GridArena arena, ServerWorld world,
                                         GridPos targetTile, GridPos playerPos, BlockPos playerBlock,
                                         List<CombatEntity> enemies) {
        CombatEntity target = arena.getOccupant(targetTile);
        BlockPos targetBlock = arena.gridToBlockPos(targetTile);

        // Phase 0 — Cast: blade conjuring
        world.playSound(null, playerBlock, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.ENCHANTED_HIT, playerBlock.getX() + 0.5, playerBlock.getY() + 1.2,
            playerBlock.getZ() + 0.5, 8, 0.2, 0.3, 0.2, 0.12);
        ProjectileSpawner.spawnConverging(world, playerBlock, 0.6, ParticleTypes.ENCHANTED_HIT, 6);

        // Damage immediately (game state)
        int dealt = target.takeDamage(12); // buffed from 8
        StringBuilder msg = new StringBuilder("§d§lPhantom Slash! §f" + target.getDisplayName() + " takes " + dealt + " damage!");

        // Cleave — find a random adjacent enemy (not the primary target)
        List<CombatEntity> cleaveTargets = new ArrayList<>();
        for (CombatEntity e : enemies) {
            if (!e.isAlive() || e.isAlly() || e == target) continue;
            if (e.getGridPos().manhattanDistance(targetTile) <= 1) {
                cleaveTargets.add(e);
            }
        }
        CombatEntity cleaveTarget = null;
        int cleaveDmg = 0;
        if (!cleaveTargets.isEmpty()) {
            cleaveTarget = cleaveTargets.get((int)(Math.random() * cleaveTargets.size()));
            cleaveDmg = cleaveTarget.takeDamage(8); // buffed from 5
            msg.append(" §dCleave! §f").append(cleaveTarget.getDisplayName()).append(" takes ").append(cleaveDmg).append(" damage!");
        }

        // Phase 1 (3 ticks) — Slash arc
        queueEffect(3, () -> {
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, targetBlock.getX() + 0.5, targetBlock.getY() + 1.0,
                targetBlock.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0);
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT, targetBlock.getX() + 0.5, targetBlock.getY() + 1.0,
                targetBlock.getZ() + 0.5, 15, 0.5, 0.3, 0.5, 0.18);
            world.playSound(null, targetBlock, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 0.9f, 1.2f);
        });

        // Phase 2 (7 ticks) — Impact explosion + cleave hit
        final CombatEntity finalCleave = cleaveTarget;
        queueEffect(7, () -> {
            world.spawnParticles(ParticleTypes.CRIT, targetBlock.getX() + 0.5, targetBlock.getY() + 1.0,
                targetBlock.getZ() + 0.5, 15, 0.4, 0.4, 0.4, 0.2);
            ProjectileSpawner.spawnExpandingRing(world, targetBlock, 0.7, ParticleTypes.ENCHANTED_HIT, 8);
            if (finalCleave != null && finalCleave.getMobEntity() != null) {
                BlockPos cleaveBlock = arena.gridToBlockPos(finalCleave.getGridPos());
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, cleaveBlock.getX() + 0.5, cleaveBlock.getY() + 1.0,
                    cleaveBlock.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0);
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT, cleaveBlock.getX() + 0.5, cleaveBlock.getY() + 1.0,
                    cleaveBlock.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.12);
                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, cleaveBlock.getX() + 0.5, cleaveBlock.getY() + 1.0,
                    cleaveBlock.getZ() + 0.5, 5, 0.2, 0.2, 0.2, 0.12);
            }
        });

        return msg.toString();
    }

    /** Burn Sherd — "Immolation": 9 fire damage + burning 3 turns (3/t) to target, 5 fire + burning 1 turn to AoE. */
    private static String useBurnSherd(ServerPlayerEntity player, GridArena arena, ServerWorld world,
                                        GridPos targetTile, GridPos playerPos, BlockPos playerBlock,
                                        List<CombatEntity> enemies) {
        CombatEntity target = arena.getOccupant(targetTile);
        BlockPos targetBlock = arena.gridToBlockPos(targetTile);

        // Phase 0 — Cast: fireball forming
        world.playSound(null, playerBlock, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.FLAME, playerBlock.getX() + 0.5, playerBlock.getY() + 1.2,
            playerBlock.getZ() + 0.5, 10, 0.2, 0.3, 0.2, 0.08);
        ProjectileSpawner.spawnConverging(world, playerBlock, 0.7, ParticleTypes.FLAME, 6);

        // 9 fire damage to target + burning for 3 turns (3 dmg/turn, buffed from 6/2)
        int dealt = target.takeDamage(9);
        target.setBurningTurns(3);
        target.setBurningDamage(3); // buffed from 2
        if (target.getMobEntity() != null) target.getMobEntity().setFireTicks(200);

        // Collect AoE hit info for delayed particles
        List<BlockPos> aoeBlocks = new ArrayList<>();
        StringBuilder msg = new StringBuilder("§6§lImmolation! §f" + target.getDisplayName() + " takes " + dealt + " fire damage + Burning (3 turns, 3/t)!");
        for (CombatEntity e : enemies) {
            if (!e.isAlive() || e.isAlly() || e == target) continue;
            if (e.getGridPos().manhattanDistance(targetTile) <= 1) {
                int aoeDmg = e.takeDamage(5); // buffed from 3
                e.setBurningTurns(Math.max(e.getBurningTurns(), 1));
                e.setBurningDamage(Math.max(e.getBurningDamage(), 3)); // buffed from 2
                if (e.getMobEntity() != null) e.getMobEntity().setFireTicks(60);
                aoeBlocks.add(arena.gridToBlockPos(e.getGridPos()));
                msg.append(" §6").append(e.getDisplayName()).append(" caught in blast for ").append(aoeDmg).append(" fire damage!");
            }
        }

        // Phase 1 (4 ticks) — Fireball trail
        queueEffect(4, () -> {
            ProjectileSpawner.spawnSpellTrail(world, playerBlock, targetBlock,
                ParticleTypes.FLAME, ParticleTypes.LARGE_SMOKE, 14, 1.8);
            world.playSound(null, targetBlock, SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.PLAYERS, 0.6f, 1.2f);
        });

        // Phase 2 (8 ticks) — Massive detonation
        queueEffect(8, () -> {
            world.spawnParticles(ParticleTypes.LAVA, targetBlock.getX() + 0.5, targetBlock.getY() + 1.0,
                targetBlock.getZ() + 0.5, 15, 0.4, 0.5, 0.4, 0.15);
            world.spawnParticles(ParticleTypes.FLAME, targetBlock.getX() + 0.5, targetBlock.getY() + 1.0,
                targetBlock.getZ() + 0.5, 30, 0.7, 1.2, 0.7, 0.15);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, targetBlock.getX() + 0.5, targetBlock.getY() + 1.5,
                targetBlock.getZ() + 0.5, 15, 0.5, 0.6, 0.5, 0.06);
            ProjectileSpawner.spawnExpandingRing(world, targetBlock, 1.0, ParticleTypes.FLAME, 12);
            world.playSound(null, targetBlock, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.8f, 1.0f);
            for (BlockPos eBlock : aoeBlocks) {
                world.spawnParticles(ParticleTypes.FLAME, eBlock.getX() + 0.5, eBlock.getY() + 1.0,
                    eBlock.getZ() + 0.5, 10, 0.3, 0.4, 0.3, 0.12);
            }
        });

        return msg.toString();
    }

    /** Snort Sherd — "Tectonic Charge": Knockback 3 tiles, 4 damage per tile, +9 + stun on wall collision. */
    private static String useSnortSherd(ServerPlayerEntity player, GridArena arena, ServerWorld world,
                                         GridPos targetTile, GridPos playerPos, BlockPos playerBlock) {
        CombatEntity target = arena.getOccupant(targetTile);
        BlockPos targetBlock = arena.gridToBlockPos(targetTile);

        // Phase 0 — Cast: earth tremor
        world.playSound(null, playerBlock, SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.DUST_PLUME, playerBlock.getX() + 0.5, playerBlock.getY() + 0.5,
            playerBlock.getZ() + 0.5, 10, 0.3, 0.2, 0.3, 0.12);
        world.spawnParticles(ParticleTypes.CLOUD, playerBlock.getX() + 0.5, playerBlock.getY() + 1.0,
            playerBlock.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.06);

        // Calculate knockback direction (away from player)
        int dx = Integer.signum(target.getGridPos().x() - playerPos.x());
        int dz = Integer.signum(target.getGridPos().z() - playerPos.z());
        if (dx == 0 && dz == 0) dx = 1;

        GridPos landingPos = target.getGridPos();
        boolean hitWall = false;
        int tilesPushed = 0;
        List<BlockPos> trailBlocks = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            GridPos candidate = new GridPos(target.getGridPos().x() + dx * i, target.getGridPos().z() + dz * i);
            if (!arena.isInBounds(candidate)) { hitWall = true; break; }
            var tile = arena.getTile(candidate);
            if (tile == null || !tile.isWalkable()) { hitWall = true; break; }
            if (arena.isOccupied(candidate)) { hitWall = true; break; }
            landingPos = candidate;
            tilesPushed++;
            trailBlocks.add(arena.gridToBlockPos(candidate));
        }

        // Move the entity (game state)
        if (!landingPos.equals(target.getGridPos())) {
            arena.moveEntity(target, landingPos);
            if (target.getMobEntity() != null) {
                BlockPos bp = arena.gridToBlockPos(landingPos);
                target.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
            }
        }

        // Damage: 4 per tile pushed (buffed from 3)
        int damage = tilesPushed * 4;
        if (hitWall) {
            damage += 9; // buffed from 6
            target.setStunned(true);
        }
        int dealt = damage > 0 ? target.takeDamage(damage) : 0;

        // Phase 1 (3 ticks) — Dust trail along push path
        final boolean wallSlam = hitWall;
        final GridPos finalLanding = landingPos;
        queueEffect(3, () -> {
            for (BlockPos tileBlock : trailBlocks) {
                world.spawnParticles(ParticleTypes.DUST_PLUME, tileBlock.getX() + 0.5, tileBlock.getY() + 0.5,
                    tileBlock.getZ() + 0.5, 6, 0.2, 0.15, 0.2, 0.06);
            }
            world.playSound(null, arena.gridToBlockPos(finalLanding), SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.PLAYERS, 0.8f, 0.9f);
        });

        // Phase 2 (7 ticks) — Landing/wall slam impact
        queueEffect(7, () -> {
            BlockPos landBlock = arena.gridToBlockPos(finalLanding);
            if (wallSlam) {
                world.playSound(null, landBlock, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.2f, 0.7f);
                world.spawnParticles(ParticleTypes.EXPLOSION, landBlock.getX() + 0.5, landBlock.getY() + 1.0,
                    landBlock.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0);
                world.spawnParticles(ParticleTypes.DUST_PLUME, landBlock.getX() + 0.5, landBlock.getY() + 0.5,
                    landBlock.getZ() + 0.5, 25, 0.5, 0.5, 0.5, 0.18);
                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, landBlock.getX() + 0.5, landBlock.getY() + 1.0,
                    landBlock.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.12);
                ProjectileSpawner.spawnExpandingRing(world, landBlock, 0.8, ParticleTypes.DUST_PLUME, 10);
            } else {
                world.spawnParticles(ParticleTypes.DUST_PLUME, landBlock.getX() + 0.5, landBlock.getY() + 0.5,
                    landBlock.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.06);
                world.spawnParticles(ParticleTypes.CLOUD, landBlock.getX() + 0.5, landBlock.getY() + 1.0,
                    landBlock.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.03);
            }
        });

        String wallMsg = hitWall ? " §c§lWALL SLAM! +9 bonus + Stunned!" : "";
        return "§8§lTectonic Charge! §f" + target.getDisplayName() + " pushed " + tilesPushed + " tiles for " + dealt + " BLUNT damage!" + wallMsg
            + " (" + target.getCurrentHp() + "/" + target.getMaxHp() + " HP)";
    }

    /** Shelter Sherd — "Stone Aegis": Resistance III for 5 turns + Absorption III for 4 turns. */
    private static String useShelterSherd(ServerPlayerEntity player, ServerWorld world, BlockPos playerBlock,
                                           CombatEffects combatEffects) {
        // Phase 0 — Cast: stone gathering
        world.playSound(null, playerBlock, SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.DUST_PLUME, playerBlock.getX() + 0.5, playerBlock.getY() + 0.2,
            playerBlock.getZ() + 0.5, 8, 0.4, 0.1, 0.4, 0.06);
        ProjectileSpawner.spawnConverging(world, playerBlock, 1.2, ParticleTypes.DUST_PLUME, 10);

        // Resistance III for 5 turns, Absorption III for 4 turns (buffed from II/4t + II/3t)
        combatEffects.addEffect(CombatEffects.EffectType.RESISTANCE, 5, 2);
        combatEffects.addEffect(CombatEffects.EffectType.ABSORPTION, 4, 2);

        double cx = playerBlock.getX() + 0.5, cy = playerBlock.getY() + 0.5, cz = playerBlock.getZ() + 0.5;

        // Phase 1 (4 ticks) — Stone dome forming
        queueEffect(4, () -> {
            world.playSound(null, playerBlock, SoundEvents.ENTITY_IRON_GOLEM_REPAIR, SoundCategory.PLAYERS, 0.5f, 1.0f);
            ProjectileSpawner.spawnExpandingRing(world, playerBlock, 1.0, ParticleTypes.DUST_PLUME, 18);
            for (int i = 0; i < 10; i++) {
                double angle = 2 * Math.PI * i / 10;
                double x = cx + Math.cos(angle) * 0.8;
                double y = cy + 1.5 + Math.sin(Math.PI * i / 10) * 0.5;
                double z = cz + Math.sin(angle) * 0.8;
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 2, 0.02, 0.02, 0.02, 0.0);
            }
        });

        // Phase 2 (8 ticks) — Shield flash + solidify
        queueEffect(8, () -> {
            ProjectileSpawner.spawnExpandingRing(world, playerBlock, 0.6, ParticleTypes.ENCHANTED_HIT, 10);
            world.spawnParticles(ParticleTypes.FIREWORK, cx, cy + 1.0, cz, 3, 0.1, 0.1, 0.1, 0.05);
            world.spawnParticles(ParticleTypes.DUST_PLUME, cx, cy + 0.5, cz, 12, 0.3, 0.8, 0.3, 0.08);
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT, cx, cy + 1.5, cz, 8, 0.4, 0.3, 0.4, 0.05);
            world.playSound(null, playerBlock, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.4f, 1.5f);
        });

        return "§7§lStone Aegis! §fResistance III (5 turns) + Absorption III (4 turns). Nearly unkillable!";
    }

    /** Flow Sherd — "Tidal Surge": 8 WATER damage + knockback 2 to all enemies within 2 tiles. */
    private static String useFlowSherd(ServerPlayerEntity player, GridArena arena, ServerWorld world,
                                        GridPos playerPos, BlockPos playerBlock, List<CombatEntity> enemies) {
        // Phase 0 — Cast: water rising
        world.playSound(null, playerBlock, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.BUBBLE, playerBlock.getX() + 0.5, playerBlock.getY() + 0.5,
            playerBlock.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.06);
        ProjectileSpawner.spawnConverging(world, playerBlock, 1.5, ParticleTypes.SPLASH, 8);

        // Collect hit info for delayed particles
        List<BlockPos> hitBlocks = new ArrayList<>();
        List<BlockPos[]> pushTrails = new ArrayList<>(); // [oldBlock, newBlock]
        int hit = 0;
        StringBuilder msg = new StringBuilder("§3§lTidal Surge! §f");

        for (CombatEntity e : enemies) {
            if (!e.isAlive() || e.isAlly()) continue;
            if (e.getGridPos().manhattanDistance(playerPos) > 2) continue;

            // 8 water damage (buffed from 5)
            int dealt = e.takeDamage(8);
            hit++;
            if (e.getMobEntity() != null) {
                hitBlocks.add(arena.gridToBlockPos(e.getGridPos()));
            }

            // Knockback 2 tiles away from player
            int edx = Integer.signum(e.getGridPos().x() - playerPos.x());
            int edz = Integer.signum(e.getGridPos().z() - playerPos.z());
            if (edx == 0 && edz == 0) edx = 1;

            GridPos pushTo = e.getGridPos();
            for (int i = 1; i <= 2; i++) {
                GridPos candidate = new GridPos(e.getGridPos().x() + edx * i, e.getGridPos().z() + edz * i);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) break;
                var tile = arena.getTile(candidate);
                if (tile == null || !tile.isWalkable()) break;
                pushTo = candidate;
            }
            if (!pushTo.equals(e.getGridPos())) {
                BlockPos oldEBlock = arena.gridToBlockPos(e.getGridPos());
                BlockPos newEBlock = arena.gridToBlockPos(pushTo);
                pushTrails.add(new BlockPos[]{oldEBlock, newEBlock});
                arena.moveEntity(e, pushTo);
                if (e.getMobEntity() != null) {
                    BlockPos bp = arena.gridToBlockPos(pushTo);
                    e.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                }
            }

            msg.append(e.getDisplayName()).append(" ").append(dealt).append(" dmg. ");
        }

        // Phase 1 (4 ticks) — Expanding water rings
        queueEffect(4, () -> {
            ProjectileSpawner.spawnExpandingRing(world, playerBlock, 1.0, ParticleTypes.SPLASH, 12);
            ProjectileSpawner.spawnExpandingRing(world, playerBlock, 2.0, ParticleTypes.SPLASH, 20);
            world.playSound(null, playerBlock, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 0.7f, 0.8f);
            for (BlockPos[] trail : pushTrails) {
                ProjectileSpawner.spawnSpellTrail(world, trail[0], trail[1], ParticleTypes.DRIPPING_WATER, null, 4, 0.0);
            }
        });

        // Phase 2 (8 ticks) — Impact splashes + settle
        queueEffect(8, () -> {
            for (BlockPos eBlock : hitBlocks) {
                world.spawnParticles(ParticleTypes.SPLASH, eBlock.getX() + 0.5, eBlock.getY() + 1.0,
                    eBlock.getZ() + 0.5, 15, 0.3, 0.4, 0.3, 0.12);
                world.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, eBlock.getX() + 0.5, eBlock.getY() + 0.5,
                    eBlock.getZ() + 0.5, 8, 0.2, 0.6, 0.2, 0.06);
            }
            world.spawnParticles(ParticleTypes.FALLING_WATER, playerBlock.getX() + 0.5, playerBlock.getY() + 0.5,
                playerBlock.getZ() + 0.5, 8, 0.5, 0.1, 0.5, 0.0);
        });

        if (hit == 0) return "§3§lTidal Surge! §7No enemies in range.";
        return msg.toString().trim();
    }

    /** Mourner Sherd — "Soul Drain": 10 damage, heal player for amount dealt. */
    private static String useMournerSherd(ServerPlayerEntity player, GridArena arena, ServerWorld world,
                                           GridPos targetTile, GridPos playerPos, BlockPos playerBlock) {
        CombatEntity target = arena.getOccupant(targetTile);
        BlockPos targetBlock = arena.gridToBlockPos(targetTile);

        // Phase 0 — Cast: soul gathering
        world.playSound(null, playerBlock, SoundEvents.ENTITY_VEX_AMBIENT, SoundCategory.PLAYERS, 0.8f, 0.6f);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, playerBlock.getX() + 0.5, playerBlock.getY() + 1.2,
            playerBlock.getZ() + 0.5, 10, 0.2, 0.3, 0.2, 0.06);
        world.spawnParticles(ParticleTypes.SOUL, playerBlock.getX() + 0.5, playerBlock.getY() + 1.2,
            playerBlock.getZ() + 0.5, 8, 0.2, 0.3, 0.2, 0.05);

        // 10 damage (buffed from 7) + heal
        int dealt = target.takeDamage(10);
        float maxHp = player.getMaxHealth();
        player.setHealth(Math.min(maxHp, player.getHealth() + dealt));

        // Phase 1 (4 ticks) — Soul fire trail to target
        queueEffect(4, () -> {
            ProjectileSpawner.spawnSpellTrail(world, playerBlock, targetBlock,
                ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.SOUL, 12, 1.0);
            world.playSound(null, targetBlock, SoundEvents.ENTITY_VEX_CHARGE, SoundCategory.PLAYERS, 0.6f, 0.7f);
        });

        // Phase 2 (8 ticks) — Drain explosion + life stream back
        queueEffect(8, () -> {
            // Drain impact
            world.spawnParticles(ParticleTypes.SOUL, targetBlock.getX() + 0.5, targetBlock.getY() + 1.0,
                targetBlock.getZ() + 0.5, 18, 0.5, 0.7, 0.5, 0.1);
            world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, targetBlock.getX() + 0.5, targetBlock.getY() + 1.0,
                targetBlock.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.12);
            ProjectileSpawner.spawnExpandingRing(world, targetBlock, 0.6, ParticleTypes.SOUL_FIRE_FLAME, 8);

            // Life stream back to player
            ProjectileSpawner.spawnReversedTrail(world, targetBlock, playerBlock, ParticleTypes.SOUL_FIRE_FLAME, 14);
            world.playSound(null, playerBlock, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.2f);
            world.spawnParticles(ParticleTypes.HEART, playerBlock.getX() + 0.5, playerBlock.getY() + 1.5,
                playerBlock.getZ() + 0.5, 8, 0.3, 0.4, 0.3, 0.06);
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, playerBlock.getX() + 0.5, playerBlock.getY() + 1.0,
                playerBlock.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
        });

        return "§5§lSoul Drain! §f" + target.getDisplayName() + " takes " + dealt + " damage! You heal " + dealt + " HP. ("
            + target.getCurrentHp() + "/" + target.getMaxHp() + " HP)";
    }

    /** Brewer Sherd — "Alchemist's Surge": Apply 4 random positive effects II for 4 turns. */
    private static String useBrewerSherd(ServerPlayerEntity player, ServerWorld world, BlockPos playerBlock,
                                          CombatEffects combatEffects) {
        // Phase 0 — Cast: brew bubbling
        world.playSound(null, playerBlock, SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.PLAYERS, 1.0f, 1.0f);
        double cx = playerBlock.getX() + 0.5, cy = playerBlock.getY() + 1.0, cz = playerBlock.getZ() + 0.5;
        world.spawnParticles(ParticleTypes.WITCH, cx, cy, cz, 8, 0.2, 0.3, 0.2, 0.04);
        ProjectileSpawner.spawnConverging(world, playerBlock, 0.8, ParticleTypes.EFFECT, 6);

        // Pool of possible effects
        List<CombatEffects.EffectType> pool = new ArrayList<>(List.of(
            CombatEffects.EffectType.SPEED, CombatEffects.EffectType.STRENGTH,
            CombatEffects.EffectType.RESISTANCE, CombatEffects.EffectType.REGENERATION,
            CombatEffects.EffectType.FIRE_RESISTANCE, CombatEffects.EffectType.HASTE
        ));
        Collections.shuffle(pool);

        // Pick 4, amplifier 1 (buffed from 0), duration 4 turns (buffed from 3)
        List<CombatEffects.EffectType> applied = new ArrayList<>();
        for (int i = 0; i < 4 && i < pool.size(); i++) {
            CombatEffects.EffectType effect = pool.get(i);
            combatEffects.addEffect(effect, 4, 1); // buffed from (3, 0)
            applied.add(effect);
        }

        // Phase 1 (4 ticks) — Potion splash
        queueEffect(4, () -> {
            world.spawnParticles(ParticleTypes.SPLASH, cx, cy, cz, 18, 0.5, 0.4, 0.5, 0.12);
            world.spawnParticles(ParticleTypes.WITCH, cx, cy + 0.5, cz, 10, 0.4, 0.5, 0.4, 0.06);
            world.playSound(null, playerBlock, SoundEvents.ENTITY_SPLASH_POTION_BREAK, SoundCategory.PLAYERS, 0.8f, 1.0f);
        });

        // Phase 2 (8 ticks) — Alchemical burst
        queueEffect(8, () -> {
            world.spawnParticles(ParticleTypes.EFFECT, cx, cy, cz, 12, 0.4, 0.5, 0.4, 0.04);
            ProjectileSpawner.spawnExpandingRing(world, playerBlock, 0.8, ParticleTypes.WITCH, 8);
            world.spawnParticles(ParticleTypes.FIREWORK, cx, cy + 0.5, cz, 2, 0.1, 0.1, 0.1, 0.05);
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, cx, cy + 1.0, cz, 6, 0.3, 0.3, 0.3, 0.05);
        });

        StringBuilder effectNames = new StringBuilder();
        for (CombatEffects.EffectType e : applied) {
            if (effectNames.length() > 0) effectNames.append(", ");
            effectNames.append(e.displayName).append(" II");
        }

        return "§d§lAlchemist's Surge! §fGained: " + effectNames + " (4 turns each)!";
    }

    /** Plenty Sherd — "Bountiful Harvest": Restore +7 AP + heal 15 HP. */
    private static String usePlentySherd(ServerPlayerEntity player, ServerWorld world, BlockPos playerBlock) {
        // Phase 0 — Cast: nature gathering
        world.playSound(null, playerBlock, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
        double cx = playerBlock.getX() + 0.5, cy = playerBlock.getY() + 1.0, cz = playerBlock.getZ() + 0.5;
        world.spawnParticles(ParticleTypes.COMPOSTER, cx, cy, cz, 8, 0.4, 0.2, 0.4, 0.06);
        ProjectileSpawner.spawnConverging(world, playerBlock, 1.0, ParticleTypes.HAPPY_VILLAGER, 8);

        // Heal 15 HP (buffed from 10)
        float maxHp = player.getMaxHealth();
        player.setHealth(Math.min(maxHp, player.getHealth() + 15));

        // Phase 1 (4 ticks) — Golden bloom
        queueEffect(4, () -> {
            world.playSound(null, playerBlock, SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 0.5f, 1.2f);
            world.spawnParticles(ParticleTypes.ENCHANT, cx, cy + 0.5, cz, 15, 0.3, 1.2, 0.3, 0.0);
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, cx, cy, cz, 10, 0.4, 0.4, 0.4, 0.06);
        });

        // Phase 2 (8 ticks) — Harvest burst
        queueEffect(8, () -> {
            world.spawnParticles(ParticleTypes.HEART, cx, cy + 0.5, cz, 8, 0.4, 0.4, 0.4, 0.06);
            world.spawnParticles(ParticleTypes.COMPOSTER, cx, cy, cz, 12, 0.6, 0.3, 0.6, 0.08);
            ProjectileSpawner.spawnExpandingRing(world, playerBlock, 0.8, ParticleTypes.HAPPY_VILLAGER, 8);
            world.spawnParticles(ParticleTypes.FIREWORK, cx, cy + 1.0, cz, 2, 0.1, 0.1, 0.1, 0.05);
        });

        // Return RESTORE_AP prefix — CombatManager adds the AP back (buffed from 5 to 7)
        return RESTORE_AP_PREFIX + "7|§a§lBountiful Harvest! §fHealed 15 HP + restored 7 AP!";
    }

    // ================================
    // 5 AP — Powerful Spells
    // ================================

    /** Archer Sherd — "Spectral Volley": 11 damage to target + 7 to enemies within 1 tile. */
    private static String useArcherSherd(ServerPlayerEntity player, GridArena arena, ServerWorld world,
                                          GridPos targetTile, GridPos playerPos, BlockPos playerBlock,
                                          List<CombatEntity> enemies) {
        CombatEntity target = arena.getOccupant(targetTile);
        BlockPos targetBlock = arena.gridToBlockPos(targetTile);

        // Phase 0 — Cast: bow draw
        world.playSound(null, playerBlock, SoundEvents.ENTITY_SKELETON_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.ENCHANTED_HIT, playerBlock.getX() + 0.5, playerBlock.getY() + 1.2,
            playerBlock.getZ() + 0.5, 6, 0.2, 0.3, 0.2, 0.12);
        ProjectileSpawner.spawnConverging(world, playerBlock, 0.5, ParticleTypes.ENCHANTED_HIT, 4);

        // Damage immediately (game state) — buffed from 7/4
        int dealt = target.takeDamage(11);
        StringBuilder msg = new StringBuilder("§b§lSpectral Volley! §f" + target.getDisplayName() + " takes "
            + dealt + " RANGED damage!");

        List<BlockPos> aoeBlocks = new ArrayList<>();
        for (CombatEntity e : enemies) {
            if (!e.isAlive() || e.isAlly() || e == target) continue;
            if (e.getGridPos().manhattanDistance(targetTile) <= 1) {
                int aoeDmg = e.takeDamage(7); // buffed from 4
                aoeBlocks.add(arena.gridToBlockPos(e.getGridPos()));
                msg.append(" §b").append(e.getDisplayName()).append(" hit for ").append(aoeDmg).append("!");
            }
        }

        // Phase 1 (3 ticks) — Descending volley
        queueEffect(3, () -> {
            ProjectileSpawner.spawnDescendingVolley(world, targetBlock, 5, 1.2);
            world.playSound(null, targetBlock, SoundEvents.ENTITY_ARROW_HIT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        });

        // Phase 2 (7 ticks) — Explosion impact + AoE
        queueEffect(7, () -> {
            world.spawnParticles(ParticleTypes.CRIT, targetBlock.getX() + 0.5, targetBlock.getY() + 1.0,
                targetBlock.getZ() + 0.5, 18, 0.4, 0.4, 0.4, 0.18);
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT, targetBlock.getX() + 0.5, targetBlock.getY() + 1.0,
                targetBlock.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.12);
            ProjectileSpawner.spawnExpandingRing(world, targetBlock, 1.0, ParticleTypes.CRIT, 10);
            for (BlockPos eBlock : aoeBlocks) {
                world.spawnParticles(ParticleTypes.CRIT, eBlock.getX() + 0.5, eBlock.getY() + 1.0,
                    eBlock.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.12);
                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, eBlock.getX() + 0.5, eBlock.getY() + 1.0,
                    eBlock.getZ() + 0.5, 5, 0.2, 0.2, 0.2, 0.12);
            }
            // Ground scatter
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT, targetBlock.getX() + 0.5, targetBlock.getY() + 0.5,
                targetBlock.getZ() + 0.5, 8, 1.2, 0.1, 1.2, 0.06);
        });

        return msg.toString();
    }

    /** Howl Sherd — "Dread Howl": 7 damage + stun to all enemies within 3 tiles. */
    private static String useHowlSherd(ServerPlayerEntity player, GridArena arena, ServerWorld world,
                                        GridPos playerPos, BlockPos playerBlock, List<CombatEntity> enemies) {
        // Phase 0 — Cast: breath intake
        world.playSound(null, playerBlock, SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.PLAYERS, 2.0f, 0.8f);
        double cx = playerBlock.getX() + 0.5, cy = playerBlock.getY() + 1.0, cz = playerBlock.getZ() + 0.5;
        world.spawnParticles(ParticleTypes.SOUL, cx, cy, cz, 10, 0.2, 0.3, 0.2, 0.03);
        ProjectileSpawner.spawnConverging(world, playerBlock, 1.5, ParticleTypes.CLOUD, 8);

        // Damage + stun immediately (game state) — buffed from 4
        List<BlockPos> hitBlocks = new ArrayList<>();
        int hit = 0;
        StringBuilder msg = new StringBuilder("§7§lDread Howl! §f");
        for (CombatEntity e : enemies) {
            if (!e.isAlive() || e.isAlly()) continue;
            if (e.getGridPos().manhattanDistance(playerPos) > 3) continue;

            int dealt = e.takeDamage(7); // buffed from 4
            e.setStunned(true);
            hit++;
            if (e.getMobEntity() != null) {
                hitBlocks.add(arena.gridToBlockPos(e.getGridPos()));
            }
            msg.append(e.getDisplayName()).append(" ").append(dealt).append(" dmg + stunned! ");
        }

        // Phase 1 (3 ticks) — Expanding shockwave rings
        queueEffect(3, () -> {
            ProjectileSpawner.spawnExpandingRing(world, playerBlock, 1.0, ParticleTypes.CLOUD, 10);
            ProjectileSpawner.spawnExpandingRing(world, playerBlock, 2.0, ParticleTypes.CLOUD, 16);
            world.playSound(null, playerBlock, SoundEvents.ENTITY_WOLF_HOWL, SoundCategory.PLAYERS, 1.0f, 1.2f);
        });

        // Phase 2 (7 ticks) — Final ring + enemy impacts
        queueEffect(7, () -> {
            ProjectileSpawner.spawnExpandingRing(world, playerBlock, 3.0, ParticleTypes.CLOUD, 22);
            for (BlockPos eBlock : hitBlocks) {
                world.spawnParticles(ParticleTypes.CLOUD, eBlock.getX() + 0.5, eBlock.getY() + 1.0,
                    eBlock.getZ() + 0.5, 12, 0.3, 0.6, 0.3, 0.06);
                world.spawnParticles(ParticleTypes.SOUL, eBlock.getX() + 0.5, eBlock.getY() + 1.0,
                    eBlock.getZ() + 0.5, 6, 0.2, 0.3, 0.2, 0.06);
                world.spawnParticles(ParticleTypes.CRIT, eBlock.getX() + 0.5, eBlock.getY() + 1.0,
                    eBlock.getZ() + 0.5, 5, 0.2, 0.2, 0.2, 0.12);
            }
            // Echo
            world.spawnParticles(ParticleTypes.SOUL, cx, cy, cz, 6, 0.5, 0.3, 0.5, 0.03);
        });

        if (hit == 0) return "§7§lDread Howl! §7No enemies in range.";
        return msg.toString().trim();
    }

    /** Arms Up Sherd — "War Cry": Strength IV for 4 turns + Speed III for 4 turns. */
    private static String useArmsUpSherd(ServerPlayerEntity player, ServerWorld world, BlockPos playerBlock,
                                          CombatEffects combatEffects) {
        // Phase 0 — Cast: power summoning
        world.playSound(null, playerBlock, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 0.5f, 1.2f);
        double cx = playerBlock.getX() + 0.5, cy = playerBlock.getY() + 0.5, cz = playerBlock.getZ() + 0.5;
        world.spawnParticles(ParticleTypes.FLAME, cx, cy, cz, 10, 0.3, 0.1, 0.3, 0.06);
        ProjectileSpawner.spawnConverging(world, playerBlock, 1.0, ParticleTypes.FLAME, 8);

        // Strength IV (amplifier 3) for 4 turns, Speed III (amplifier 2) for 4 turns (buffed from III/3t + II/3t)
        combatEffects.addEffect(CombatEffects.EffectType.STRENGTH, 4, 3);
        combatEffects.addEffect(CombatEffects.EffectType.SPEED, 4, 2);

        // Phase 1 (4 ticks) — Fire eruption
        queueEffect(4, () -> {
            world.playSound(null, playerBlock, SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.PLAYERS, 1.0f, 1.0f);
            ProjectileSpawner.spawnExpandingRing(world, playerBlock, 0.6, ParticleTypes.FLAME, 6);
            ProjectileSpawner.spawnExpandingRing(world, playerBlock, 0.6, ParticleTypes.SOUL_FIRE_FLAME, 6);
            world.spawnParticles(ParticleTypes.FLAME, cx, cy, cz, 20, 0.3, 0.0, 0.3, 0.45);
            world.spawnParticles(ParticleTypes.LAVA, cx, cy + 1.0, cz, 8, 0.5, 0.3, 0.5, 0.12);
        });

        // Phase 2 (8 ticks) — Power ring + crit burst
        queueEffect(8, () -> {
            for (int i = 0; i < 12; i++) {
                double angle = 2 * Math.PI * i / 12;
                double x = cx + Math.cos(angle) * 0.7;
                double z = cz + Math.sin(angle) * 0.7;
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT, x, cy + 1.5, z, 2, 0.02, 0.02, 0.02, 0.0);
            }
            world.spawnParticles(ParticleTypes.CRIT, cx, cy + 2.0, cz, 12, 0.3, 0.1, 0.3, 0.18);
            world.spawnParticles(ParticleTypes.FLAME, cx, cy, cz, 6, 0.2, 0.1, 0.2, 0.06);
            world.spawnParticles(ParticleTypes.FIREWORK, cx, cy + 1.5, cz, 2, 0.1, 0.1, 0.1, 0.05);
        });

        return "§6§lWar Cry! §fStrength IV (+12 ATK) + Speed III (+6 SPD) for 4 turns!";
    }

    /** Prize Sherd — "Fortune's Favor": Next attack deals triple damage + Luck II for 4 turns. */
    private static String usePrizeSherd(ServerPlayerEntity player, ServerWorld world, BlockPos playerBlock,
                                         CombatEffects combatEffects) {
        // Phase 0 — Cast: fortune gathering
        world.playSound(null, playerBlock, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.0f, 1.0f);
        double cx = playerBlock.getX() + 0.5, cy = playerBlock.getY() + 1.0, cz = playerBlock.getZ() + 0.5;
        world.spawnParticles(ParticleTypes.WAX_ON, cx, cy + 1.5, cz, 8, 0.3, 0.3, 0.3, 0.04);
        ProjectileSpawner.spawnConverging(world, playerBlock, 1.0, ParticleTypes.ENCHANT, 8);

        // Luck II for 4 turns (buffed from 3)
        combatEffects.addEffect(CombatEffects.EffectType.LUCK, 4, 1);

        // Phase 1 (4 ticks) — Enchant glyphs orbiting
        queueEffect(4, () -> {
            world.playSound(null, playerBlock, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.5f, 1.2f);
            for (int i = 0; i < 10; i++) {
                double angle = 2 * Math.PI * i / 10;
                double x = cx + Math.cos(angle) * 0.8;
                double z = cz + Math.sin(angle) * 0.8;
                world.spawnParticles(ParticleTypes.ENCHANT, x, cy, z, 2, 0.02, 0.02, 0.02, 0.0);
            }
            ProjectileSpawner.spawnConverging(world, playerBlock, 0.5, ParticleTypes.WAX_ON, 6);
        });

        // Phase 2 (8 ticks) — Weapon charge flash
        queueEffect(8, () -> {
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT, cx, cy, cz, 15, 0.2, 0.3, 0.2, 0.12);
            world.spawnParticles(ParticleTypes.FIREWORK, cx, cy + 0.5, cz, 3, 0.1, 0.1, 0.1, 0.05);
            world.spawnParticles(ParticleTypes.WAX_ON, cx, cy + 1.5, cz, 10, 0.5, 0.4, 0.5, 0.06);
            ProjectileSpawner.spawnExpandingRing(world, playerBlock, 0.6, ParticleTypes.ENCHANTED_HIT, 8);
        });

        // Return TRIPLE_NEXT prefix — CombatManager sets the flag
        return TRIPLE_NEXT_PREFIX + "1|§6§lFortune's Favor! §fNext attack deals §6§lTRIPLE DAMAGE! §f+ Luck II (4 turns).";
    }

    // ================================
    // 6 AP — Ultimate Spell
    // ================================

    /** Skull Sherd — "Death Mark": Execute below 40% HP, otherwise 5 damage + Wither III 3 turns. */
    private static String useSkullSherd(ServerPlayerEntity player, GridArena arena, ServerWorld world,
                                         GridPos targetTile, GridPos playerPos, BlockPos playerBlock) {
        CombatEntity target = arena.getOccupant(targetTile);

        BlockPos targetBlock = arena.gridToBlockPos(targetTile);
        double tx = targetBlock.getX() + 0.5, ty = targetBlock.getY() + 1.0, tz = targetBlock.getZ() + 0.5;
        double cx = playerBlock.getX() + 0.5, cy = playerBlock.getY() + 1.2, cz = playerBlock.getZ() + 0.5;

        // Phase 0 — Cast: dark energy gathering
        world.playSound(null, playerBlock, SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.PLAYERS, 0.6f, 0.6f);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, cx, cy, cz, 10, 0.2, 0.3, 0.2, 0.05);
        world.spawnParticles(ParticleTypes.SOUL, cx, cy, cz, 8, 0.2, 0.3, 0.2, 0.05);
        ProjectileSpawner.spawnConverging(world, playerBlock, 1.5, ParticleTypes.SOUL, 8);

        boolean execute = target.getCurrentHp() <= (target.getMaxHp() * 0.5); // buffed 40% → 50%

        // Phase 1 (4 ticks) — Menacing trail to target
        queueEffect(4, () -> {
            ProjectileSpawner.spawnSpellTrail(world, playerBlock, targetBlock,
                ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.SOUL, 14, 2.0);
            world.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, tx, ty + 1.5, tz, 6, 0.2, 0.1, 0.2, 0.02);
        });

        if (execute) {
            // Game state immediately — instant kill
            target.takeDamage(9999);

            // Phase 2 (8 ticks) — Massive execution explosion
            queueEffect(8, () -> {
                world.playSound(null, targetBlock, SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 1.5f, 0.5f);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, tx, ty, tz, 1, 0, 0, 0, 0);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, tx, ty, tz, 25, 0.8, 1.2, 0.8, 0.18);
                world.spawnParticles(ParticleTypes.SOUL, tx, ty, tz, 20, 0.5, 1.0, 0.5, 0.12);
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, tx, ty + 0.5, tz, 12, 0.6, 0.8, 0.6, 0.05);
                ProjectileSpawner.spawnExpandingRing(world, targetBlock, 1.0, ParticleTypes.SOUL_FIRE_FLAME, 12);
            });

            return "§4§l☠ DEATH MARK — EXECUTE! §f" + target.getDisplayName() + " obliterated!";
        } else {
            // Game state immediately — 10 damage (buffed from 5) + Wither IV for 4 turns (buffed from III/3)
            int dealt = target.takeDamage(10);
            target.setPoisonTurns(4);
            target.setPoisonAmplifier(3); // Wither IV: 2 * (3+1) = 8 dmg/turn

            // Phase 2 (8 ticks) — Wither curse impact
            queueEffect(8, () -> {
                world.playSound(null, targetBlock, SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.8f);
                world.spawnParticles(ParticleTypes.SOUL, tx, ty, tz, 15, 0.4, 0.6, 0.4, 0.1);
                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, tx, ty, tz, 6, 0.3, 0.3, 0.3, 0.12);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, tx, ty, tz, 10, 0.3, 0.4, 0.3, 0.06);
                world.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, tx, ty + 1.0, tz, 6, 0.2, 0.2, 0.2, 0.03);
                ProjectileSpawner.spawnExpandingRing(world, targetBlock, 0.6, ParticleTypes.SOUL, 8);
            });

            return "§4§lDeath Mark! §f" + target.getDisplayName() + " takes " + dealt + " damage + Wither IV (8 dmg/turn, 4 turns). ("
                + target.getCurrentHp() + "/" + target.getMaxHp() + " HP)";
        }
    }

    /** Guster Sherd — "Chain Lightning": Bolt hits target, then arcs to nearby mobs within 2 tiles.
     *  Soaked enemies take 2x damage. Each arc deals slightly less damage. */
    private static String useGusterSherd(ServerPlayerEntity player, GridArena arena, ServerWorld world,
                                          GridPos targetTile, GridPos playerPos, BlockPos playerBlock,
                                          List<CombatEntity> enemies) {
        CombatEntity target = arena.getOccupant(targetTile);

        // Phase 0 — Cast: storm charging
        world.playSound(null, playerBlock, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.8f, 1.4f);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, playerBlock.getX() + 0.5, playerBlock.getY() + 1.5,
            playerBlock.getZ() + 0.5, 15, 0.3, 0.4, 0.3, 0.15);

        // Chain lightning: BFS from the target, chaining to enemies within 2 tiles of each hit
        int baseDamage = 8;
        int chainDecay = 1; // damage reduces per chain
        Set<CombatEntity> hit = new LinkedHashSet<>();
        java.util.Deque<CombatEntity> queue = new java.util.ArrayDeque<>();
        Map<CombatEntity, Integer> chainDepth = new HashMap<>();

        hit.add(target);
        queue.add(target);
        chainDepth.put(target, 0);

        // BFS: find all chained enemies
        while (!queue.isEmpty()) {
            CombatEntity current = queue.poll();
            int depth = chainDepth.get(current);
            for (CombatEntity e : enemies) {
                if (!e.isAlive() || e.isAlly() || hit.contains(e)) continue;
                if (e.getGridPos().manhattanDistance(current.getGridPos()) <= 2) {
                    hit.add(e);
                    queue.add(e);
                    chainDepth.put(e, depth + 1);
                }
            }
        }

        // Apply damage in chain order
        StringBuilder msg = new StringBuilder("§e§lChain Lightning!");
        List<BlockPos> chainBlocks = new ArrayList<>();
        List<BlockPos> prevBlocks = new ArrayList<>();
        prevBlocks.add(playerBlock);
        int chainIndex = 0;
        for (CombatEntity e : hit) {
            int depth = chainDepth.get(e);
            int dmg = Math.max(3, baseDamage - (depth * chainDecay));
            // Soaked enemies take 2x lightning damage
            if (e.getSoakedTurns() > 0) dmg *= 2;
            int dealt = e.takeDamage(dmg);
            BlockPos eBlock = arena.gridToBlockPos(e.getGridPos());
            chainBlocks.add(eBlock);

            String soakedTag = e.getSoakedTurns() > 0 ? " §3(2x Soaked!)" : "";
            msg.append(" §e⚡ ").append(e.getDisplayName()).append(" -").append(dealt).append("HP").append(soakedTag);

            // Delayed arc particles per chain step
            final int delay = 3 + (chainIndex * 4);
            final BlockPos fromBlock = chainIndex == 0 ? playerBlock : chainBlocks.get(Math.max(0, chainIndex - 1));
            final BlockPos toBlock = eBlock;
            final boolean isSoaked = e.getSoakedTurns() > 0;
            queueEffect(delay, () -> {
                ProjectileSpawner.spawnSpellTrail(world, fromBlock, toBlock,
                    ParticleTypes.ELECTRIC_SPARK, ParticleTypes.SOUL_FIRE_FLAME, 10, 1.5);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, toBlock.getX() + 0.5, toBlock.getY() + 1.0,
                    toBlock.getZ() + 0.5, 20, 0.3, 0.5, 0.3, 0.15);
                if (isSoaked) {
                    world.spawnParticles(ParticleTypes.SPLASH, toBlock.getX() + 0.5, toBlock.getY() + 1.0,
                        toBlock.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.1);
                }
                world.playSound(null, toBlock, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.6f, 1.2f + depth * 0.1f);
            });
            chainIndex++;
        }

        msg.append(" §7(").append(hit.size()).append(" enemies chained)");
        return msg.toString();
    }

    // ================================
    // Tooltip Descriptions
    // ================================

    /** Get tooltip description for a pottery sherd spell. */
    public static String getSherdTooltip(Item item) {
        if (item == Items.EXPLORER_POTTERY_SHERD) return "§d[2 AP] Phase Step §7— Teleport 4 tiles + reveal enemy stats";
        if (item == Items.FRIEND_POTTERY_SHERD) return "§d[2 AP] Guardian Spirit §7— Heal 8 HP, buff ally pet (+5 ATK)";
        if (item == Items.HEART_POTTERY_SHERD) return "§d[3 AP] Mending Light §7— Heal 15 HP + Regen II (4 turns)";
        if (item == Items.SCRAPE_POTTERY_SHERD) return "§d[3 AP] Corrode §7— 5 dmg + reduce DEF by 7 (3 turns)";
        if (item == Items.ANGLER_POTTERY_SHERD) return "§3[3 AP] Riptide Hook §7— Pull 2 tiles + 6 dmg (+5 if adjacent)";
        if (item == Items.HEARTBREAK_POTTERY_SHERD) return "§d[3 AP] Shatter Will §7— 5 dmg + -5 ATK, -4 SPD (2 turns)";
        if (item == Items.SHEAF_POTTERY_SHERD) return "§2[3 AP] Entangle §7— Stun target + slow nearby enemies (-5 SPD)";
        if (item == Items.MINER_POTTERY_SHERD) return "§8[3 AP] Earthen Spike §7— 10 BLUNT dmg (+6 near obstacle)";
        if (item == Items.DANGER_POTTERY_SHERD) return "§d[3 AP] Hex Trap §7— Invisible trap: 12 dmg + stun on trigger";
        if (item == Items.BLADE_POTTERY_SHERD) return "§d[4 AP] Phantom Slash §7— 12 dmg + 8 cleave to adjacent enemy";
        if (item == Items.BURN_POTTERY_SHERD) return "§6[4 AP] Immolation §7— 9 fire + burn 3/t (3t), splash 5 dmg + burn";
        if (item == Items.SNORT_POTTERY_SHERD) return "§8[4 AP] Tectonic Charge §7— KB 3 tiles, 4 dmg/tile, wall slam +9";
        if (item == Items.SHELTER_POTTERY_SHERD) return "§7[4 AP] Stone Aegis §7— Resistance III (5t) + Absorption III (4t)";
        if (item == Items.FLOW_POTTERY_SHERD) return "§3[4 AP] Tidal Surge §7— 8 WATER dmg + KB 2 to all within 2 tiles";
        if (item == Items.MOURNER_POTTERY_SHERD) return "§5[4 AP] Soul Drain §7— 10 dmg, heal for damage dealt";
        if (item == Items.BREWER_POTTERY_SHERD) return "§d[4 AP] Alchemist's Surge §7— 4 random buffs II (4 turns each)";
        if (item == Items.PLENTY_POTTERY_SHERD) return "§a[4 AP] Bountiful Harvest §7— Heal 15 HP + restore 7 AP";
        if (item == Items.ARCHER_POTTERY_SHERD) return "§b[5 AP] Spectral Volley §7— 11 RANGED dmg + 7 AoE splash";
        if (item == Items.HOWL_POTTERY_SHERD) return "§7[5 AP] Dread Howl §7— 7 dmg + stun all within 3 tiles";
        if (item == Items.ARMS_UP_POTTERY_SHERD) return "§6[5 AP] War Cry §7— STR IV (+12 ATK) + SPD III (+6 SPD) (4 turns)";
        if (item == Items.PRIZE_POTTERY_SHERD) return "§6[5 AP] Fortune's Favor §7— Next attack = TRIPLE damage + Luck II (4t)";
        if (item == Items.SKULL_POTTERY_SHERD) return "§4[6 AP] Death Mark §7— Execute <50% HP or 10 dmg + Wither IV (4t)";
        if (item == Items.GUSTER_POTTERY_SHERD) return "§e[4 AP] Chain Lightning §7— 8 dmg, chains to enemies within 2 tiles (2x on Soaked)";
        return null;
    }
}
