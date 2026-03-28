package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.level.BiomeTemplate;
import com.crackedgames.craftics.level.LevelDefinition;
import com.crackedgames.craftics.network.*;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.combat.ai.DragonAI;
import com.crackedgames.craftics.combat.ai.WardenAI;
import com.crackedgames.craftics.combat.ai.boss.BossAI;
import com.crackedgames.craftics.combat.ai.boss.BroodmotherAI;
import com.crackedgames.craftics.combat.ai.boss.MoltenKingAI;
import com.crackedgames.craftics.combat.ai.boss.ShulkerArchitectAI;
import com.crackedgames.craftics.combat.ai.boss.WailingRevenantAI;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

public class CombatManager {
    // Per-player combat instances
    private static final java.util.Map<java.util.UUID, CombatManager> INSTANCES = new java.util.concurrent.ConcurrentHashMap<>();

    /** Get the combat manager for a specific player. Creates one if it doesn't exist. */
    public static CombatManager get(java.util.UUID playerId) {
        return INSTANCES.computeIfAbsent(playerId, id -> new CombatManager());
    }

    /** Get the combat manager for a player entity. */
    public static CombatManager get(ServerPlayerEntity player) {
        return get(player.getUuid());
    }

    /** Remove a player's combat manager (on disconnect cleanup). */
    public static void remove(java.util.UUID playerId) {
        CombatManager cm = INSTANCES.remove(playerId);
        if (cm != null && cm.active) cm.endCombat();
    }

    /** Tick all active combat instances. */
    public static void tickAll() {
        for (CombatManager cm : INSTANCES.values()) {
            cm.tick();
        }
    }

    /** Check if any player is in active combat (for world-level checks). */
    public static boolean isAnyActive() {
        return INSTANCES.values().stream().anyMatch(cm -> cm.active);
    }

    /** @deprecated Use get(player) instead. Kept for migration — returns first active or a dummy. */
    @Deprecated
    public static CombatManager getInstance() {
        return INSTANCES.values().stream().filter(cm -> cm.active).findFirst()
            .orElseGet(CombatManager::new);
    }

    private static final int DEFAULT_SPEED = 3;
    private static final int MOVE_TICKS = 4; // ticks per tile of movement
    private int getMoveTicks() { return CrafticsMod.CONFIG.skipEnemyAnimations() ? 1 : MOVE_TICKS; }

    private static GridPos adaptSpawnToArena(GridPos originalPos, LevelDefinition levelDef, GridArena arena) {
        int mappedX = scaleGridCoordinate(originalPos.x(), levelDef.getWidth(), arena.getWidth());
        int mappedZ = scaleGridCoordinate(originalPos.z(), levelDef.getHeight(), arena.getHeight());
        return new GridPos(mappedX, mappedZ);
    }

    private static int scaleGridCoordinate(int value, int sourceSize, int targetSize) {
        if (targetSize <= 1) return 0;
        if (sourceSize <= 1) return Math.min(targetSize - 1, targetSize / 2);

        double ratio = (double) value / (double) (sourceSize - 1);
        int mapped = (int) Math.round(ratio * (targetSize - 1));
        return Math.max(0, Math.min(targetSize - 1, mapped));
    }

    private static GridPos findNearestValidSpawn(GridArena arena, GridPos desiredPos, int entitySize) {
        GridPos bestPos = null;
        int bestDistance = Integer.MAX_VALUE;

        for (int x = 0; x <= arena.getWidth() - entitySize; x++) {
            for (int z = 0; z <= arena.getHeight() - entitySize; z++) {
                GridPos candidate = new GridPos(x, z);
                if (!canPlaceSpawnAt(arena, candidate, entitySize)) continue;

                int distance = candidate.manhattanDistance(desiredPos);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestPos = candidate;
                }
            }
        }

        return bestPos;
    }

    private static boolean canPlaceSpawnAt(GridArena arena, GridPos origin, int entitySize) {
        for (GridPos tilePos : GridArena.getOccupiedTiles(origin, entitySize)) {
            if (!arena.isInBounds(tilePos)) return false;

            GridTile tile = arena.getTile(tilePos);
            if (tile == null || !tile.isWalkable()) return false;
            if (arena.isOccupied(tilePos)) return false;
        }

        return true;
    }

    private boolean active = false;
    private GridArena arena;
    private LevelDefinition levelDef;
    private List<CombatEntity> enemies;
    // Mobs shrinking via Pehkui death anim — (mob, ticksRemaining)
    private final List<MobEntity> dyingMobs = new ArrayList<>();
    private final List<Integer> dyingMobTimers = new ArrayList<>();
    private CombatPhase phase;
    private int apRemaining;
    private int movePointsRemaining;
    private int turnNumber;
    private ServerPlayerEntity player;
    private final CombatEffects combatEffects = new CombatEffects();

    /** Shared event manager for this biome run (shared across party members). */
    private EventManager eventManager;

    /** The UUID of the world owner for this combat (determines arena/event positions). */
    private java.util.UUID worldOwnerUuid;

    public EventManager getEventManager() { return eventManager; }
    public void setEventManager(EventManager em) { this.eventManager = em; }

    public java.util.UUID getWorldOwnerUuid() { return worldOwnerUuid; }
    public void setWorldOwnerUuid(java.util.UUID uuid) { this.worldOwnerUuid = uuid; }

    // === Party co-op support ===

    /** All players participating in this combat (leader first, then party members). */
    private final List<ServerPlayerEntity> partyPlayers = new ArrayList<>();

    /** Maps non-leader party member UUID → leader UUID for action routing. */
    private static final java.util.Map<java.util.UUID, java.util.UUID> PARTY_COMBAT_LEADER =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Add a party member to this combat (includes leader — call for every participant). */
    public void addPartyMember(ServerPlayerEntity member) {
        if (partyPlayers.stream().noneMatch(p -> p.getUuid().equals(member.getUuid()))) {
            partyPlayers.add(member);
        }
        if (player != null && !member.getUuid().equals(player.getUuid())) {
            PARTY_COMBAT_LEADER.put(member.getUuid(), player.getUuid());
        }
    }

    /** Remove a party member (disconnect/leave). */
    public void removePartyMember(java.util.UUID memberUuid) {
        partyPlayers.removeIf(p -> p.getUuid().equals(memberUuid));
        PARTY_COMBAT_LEADER.remove(memberUuid);
    }

    /** Get the CombatManager that handles this player's active combat (follows party leader). */
    public static CombatManager getActiveCombat(java.util.UUID playerUuid) {
        java.util.UUID leaderUuid = PARTY_COMBAT_LEADER.get(playerUuid);
        if (leaderUuid != null) {
            CombatManager leaderCm = INSTANCES.get(leaderUuid);
            if (leaderCm != null && leaderCm.active) return leaderCm;
        }
        return get(playerUuid);
    }

    /** Check if a UUID is a party participant in this combat. */
    public boolean isPartyMember(java.util.UUID uuid) {
        return partyPlayers.stream().anyMatch(p -> p.getUuid().equals(uuid));
    }

    /** Get all participating players (leader + party members), or just the leader if solo. */
    private List<ServerPlayerEntity> getAllParticipants() {
        if (!partyPlayers.isEmpty()) return partyPlayers;
        return player != null ? List.of(player) : List.of();
    }

    /** Send a payload to all party participants (or just the player if solo). */
    private void sendToAllParty(net.minecraft.network.packet.CustomPayload payload) {
        for (ServerPlayerEntity p : getAllParticipants()) {
            if (p != null && p.networkHandler != null) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    /** Send a chat message (not action bar) to all party participants. */
    private void sendMessageToAllChat(String msg) {
        for (ServerPlayerEntity p : getAllParticipants()) {
            if (p != null) {
                p.sendMessage(Text.literal(msg), false);
            }
        }
    }

    /** Clean up party tracking on combat end. */
    private void cleanupPartyTracking() {
        for (ServerPlayerEntity member : partyPlayers) {
            PARTY_COMBAT_LEADER.remove(member.getUuid());
        }
        partyPlayers.clear();
    }

    /** Get online party members via EventManager (survives endCombat). Falls back to single player. */
    private List<ServerPlayerEntity> getOnlinePartyMembers(ServerPlayerEntity referencePlayer) {
        if (eventManager != null) {
            return eventManager.getOnlineParticipants((ServerWorld) referencePlayer.getEntityWorld());
        }
        return List.of(referencePlayer);
    }

    /**
     * Transition all party members to a new arena and start combat.
     * Uses EventManager to find participants (survives endCombat clearing partyPlayers).
     */
    private void transitionPartyToArena(ServerPlayerEntity leader, GridArena newArena, LevelDefinition newLevelDef) {
        List<ServerPlayerEntity> members = getOnlinePartyMembers(leader);
        BlockPos startPos = newArena.getPlayerStartBlockPos();
        EnterCombatPayload enterPayload = makeEnterPayload(newArena);
        for (ServerPlayerEntity member : members) {
            member.requestTeleport(startPos.getX() + 0.5, startPos.getY(), startPos.getZ() + 0.5);
            ServerPlayNetworking.send(member, enterPayload);
            if (!member.getUuid().equals(leader.getUuid())) {
                member.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
            }
        }
        startCombat(leader, newArena, newLevelDef);
        // Re-register party members on the new combat instance
        for (ServerPlayerEntity member : members) {
            addPartyMember(member);
        }
    }

    /** Build an arena using this combat's world owner for positioning. */
    private GridArena buildArena(ServerWorld world, LevelDefinition def) {
        if (worldOwnerUuid != null) {
            return com.crackedgames.craftics.level.ArenaBuilder.build(world, def, worldOwnerUuid);
        }
        return com.crackedgames.craftics.level.ArenaBuilder.build(world, def);
    }

    /** Resolve the dynamic hub position for a player (personal world or central lobby). */
    private static void teleportToHub(ServerPlayerEntity p) {
        ServerWorld world = (ServerWorld) p.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(world);
        BlockPos hub = data.getHubTeleportPos(p.getUuid());
        p.requestTeleport(hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5);
    }

    /** Teleport all party members home and send ExitCombat. */
    private void sendPartyHome(ServerPlayerEntity referencePlayer) {
        List<ServerPlayerEntity> members = getOnlinePartyMembers(referencePlayer);
        for (ServerPlayerEntity member : members) {
            teleportToHub(member);
        }
        // Send exit to all via the reference (partyPlayers may already be cleared)
        for (ServerPlayerEntity member : members) {
            if (member.networkHandler != null) {
                ServerPlayNetworking.send(member, new ExitCombatPayload(true));
            }
        }
    }

    // Mounted pet state
    private boolean playerMounted = false;
    private MobEntity mountMob = null;
    private static final int MOUNT_SPEED_BONUS = 3;

    // Tile effects placed by items (lava, campfire, honey, banner, scaffold)
    private final java.util.Map<GridPos, String> tileEffects = new java.util.HashMap<>();
    // Turn counter for hex trap expiry (5 turns max)
    private int hexTrapTurnsRemaining = 0;

    // Chunk positions force-loaded for this combat arena
    private final java.util.List<net.minecraft.util.math.ChunkPos> forcedChunks = new java.util.ArrayList<>();

    // Pending attack — delays damage/effects to sync with client animation impact
    private Runnable pendingAttackAction = null;
    private int pendingAttackDelay = 0;

    // Spell animation cooldown — blocks player input while spell particles are staged
    private int spellAnimCooldown = 0;

    // Boss warning system — telegraphed abilities that resolve next turn
    private record PendingBossWarning(CombatEntity boss, EnemyAction.BossAbility ability) {}
    private final List<PendingBossWarning> pendingBossWarnings = new ArrayList<>();

    // Physical warning blocks — original blocks saved for restoration
    private final java.util.Map<BlockPos, net.minecraft.block.BlockState> warningOriginalBlocks = new java.util.HashMap<>();

    // Pottery Sherd spell state — Prize sherd sets this, consumed on next attack
    private boolean tripleDamageNextAttack = false;

    // Active armor trim bonuses for current combat
    private TrimEffects.TrimScan activeTrimScan = null;
    public TrimEffects.TrimScan getTrimScan() { return activeTrimScan; }

    // Test range mode — infinite AP/movement, no rewards or penalties
    private boolean testRange = false;
    public boolean isTestRange() { return testRange; }

    // Shield brace state — active during enemy turn when player ended turn with shield equipped
    private boolean shieldBraced = false;
    private static final int SHIELD_PASSIVE_DEFENSE = 2;
    private static final int SHIELD_BRACE_DEFENSE = 3; // extra on top of passive when braced

    private int getPlayerHp() {
        if (player == null) return 0;
        int hp = (int) player.getHealth();
        // We clamp real HP to 1 to prevent vanilla death. Report 0 when at clamp threshold.
        return hp <= 1 ? 0 : hp;
    }

    private void damagePlayer(int rawDamage) {
        int defense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus() + PlayerCombatStats.getSetDefenseBonus(player) + PlayerCombatStats.getTotalProtection(player);
        // Shield: passive +2 when in offhand, +3 extra when braced (end turn with shield)
        if (PlayerCombatStats.hasShield(player)) {
            defense += SHIELD_PASSIVE_DEFENSE;
            if (shieldBraced) defense += SHIELD_BRACE_DEFENSE;
        }
        // Percentage-based defense: each point = 5% reduction, capped at 60%
        double reduction = Math.min(0.60, defense * 0.05);
        int actual = Math.max(1, (int)(rawDamage * (1.0 - reduction)));
        player.setHealth(Math.max(1, player.getHealth() - actual));
        onPlayerDamaged(); // reset kill streak

        // Totem of Undying: if player would die (HP <= 1), check for totem in inventory
        if (getPlayerHp() <= 0) {
            for (int i = 0; i < player.getInventory().size(); i++) {
                if (player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                    player.getInventory().getStack(i).decrement(1);
                    player.setHealth(player.getMaxHealth() / 2);
                    player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.REGENERATION, 900, 1));
                    sendMessage("\u00a76\u00a7l\u2726 TOTEM OF UNDYING ACTIVATES! \u2726 \u00a7rResurrected with half health!");
                    break;
                }
            }
        }
    }

    /** Get melee bonus from progression. */
    private int getProgMeleeBonus() {
        if (player == null) return 0;
        return PlayerProgression.get((ServerWorld) (ServerWorld) player.getEntityWorld()).getStats(player).getPoints(PlayerProgression.Stat.MELEE_POWER);
    }

    /** Get ranged bonus from progression. */
    private int getProgRangedBonus() {
        if (player == null) return 0;
        return PlayerProgression.get((ServerWorld) player.getEntityWorld()).getStats(player).getPoints(PlayerProgression.Stat.RANGED_POWER);
    }

    /** Get defense bonus from progression. */
    private int getProgDefenseBonus() {
        if (player == null) return 0;
        return PlayerProgression.get((ServerWorld) player.getEntityWorld()).getStats(player).getPoints(PlayerProgression.Stat.DEFENSE);
    }
    private float playerMoveYaw; // tracked for smooth facing during movement

    // Animation state
    private List<GridPos> movePath;
    private int movePathIndex;
    private int moveTickCounter;
    private double lerpStartX, lerpStartY, lerpStartZ;
    private boolean lerpInitialized;

    // Enemy turn state
    private enum EnemyTurnState { DECIDING, MOVING, ATTACKING, DONE }
    private int enemyTurnIndex;
    private int enemyTurnDelay;
    private EnemyTurnState enemyTurnState;
    private EnemyAction pendingAction;
    private CombatEntity currentEnemy;

    // Enemy movement animation
    private List<GridPos> enemyMovePath;
    private int enemyMovePathIndex;
    private int enemyMoveTickCounter;
    private double enemyLerpStartX, enemyLerpStartY, enemyLerpStartZ;
    private boolean enemyLerpInitialized;

    private net.minecraft.item.Item lastHeldItem = null;
    private int tickCounter; // global tick counter for ambient effects

    public boolean isActive() { return active; }
    public CombatPhase getPhase() { return phase; }
    public int getApRemaining() { return apRemaining; }
    public CombatEffects getCombatEffects() { return combatEffects; }
    public GridArena getArena() { return arena; }
    public List<CombatEntity> getEnemies() { return enemies; }
    public ServerPlayerEntity getPlayer() { return player; }
    public int getTurnNumber() { return turnNumber; }
    public int getMovePointsRemaining() { return movePointsRemaining; }
    public void setApRemaining(int ap) { this.apRemaining = ap; }
    public void setMovePointsRemaining(int mp) { this.movePointsRemaining = mp; }

    /** Admin: kill all enemies and trigger victory. */
    public void adminKillAllEnemies() {
        if (!active || enemies == null) return;
        for (CombatEntity e : enemies) {
            if (e.isAlive()) {
                e.takeDamage(e.getCurrentHp() + e.getDefense() + 9999);
                MobEntity mob = e.getMobEntity();
                if (mob != null && mob.isAlive()) {
                    startDeathShrink(mob);
                    dyingMobs.add(mob);
                    dyingMobTimers.add(20);
                }
            }
        }
        handleVictory();
    }

    /** Admin: force-win current combat (identical to killing all enemies). */
    public void adminWinCombat() {
        adminKillAllEnemies();
    }

    /**
     * Start a test range — a simple arena with a training dummy for testing weapons/spells.
     * Player gets infinite AP and movement. No rewards or penalties.
     */
    public void startTestRange(ServerPlayerEntity player) {
        this.testRange = true;
        ServerWorld world = (ServerWorld) player.getEntityWorld();

        // Use level 999 with player UUID for unique positioning
        BlockPos origin = GridArena.arenaOriginForLevel(999, player.getUuid());
        int size = 9;
        GridTile[][] tiles = new GridTile[size][size];
        for (int x = 0; x < size; x++)
            for (int z = 0; z < size; z++)
                tiles[x][z] = new GridTile(com.crackedgames.craftics.core.TileType.NORMAL, Blocks.STONE_BRICKS);

        GridPos playerStart = new GridPos(1, 1);
        GridArena testArena = new GridArena(size, size, tiles, origin, 999, playerStart);

        // Force-load chunks
        forcedChunks.clear();
        int minCX = (origin.getX() - 2) >> 4;
        int maxCX = (origin.getX() + size + 2) >> 4;
        int minCZ = (origin.getZ() - 2) >> 4;
        int maxCZ = (origin.getZ() + size + 2) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++)
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                world.setChunkForced(cx, cz, true);
                forcedChunks.add(new net.minecraft.util.math.ChunkPos(cx, cz));
            }

        // Build the physical arena floor + clear air above
        int SET_FLAGS = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                world.setBlockState(new BlockPos(origin.getX() + x, origin.getY(), origin.getZ() + z),
                    Blocks.STONE_BRICKS.getDefaultState(), SET_FLAGS);
                for (int y = 1; y <= 4; y++)
                    world.setBlockState(new BlockPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z),
                        Blocks.AIR.getDefaultState(), SET_FLAGS);
            }
        }

        // Clear leftover entities
        net.minecraft.util.math.Box arenaBox = new net.minecraft.util.math.Box(
            origin.getX() - 2, origin.getY() - 1, origin.getZ() - 2,
            origin.getX() + size + 2, origin.getY() + 5, origin.getZ() + size + 2
        );
        for (var entity : world.getEntitiesByClass(MobEntity.class, arenaBox, e -> true)) {
            entity.discard();
        }

        // Spawn Training Dummy (zombie) at center
        GridPos dummyPos = new GridPos(4, 4);
        BlockPos dummyBlockPos = testArena.gridToBlockPos(dummyPos);
        var zombie = EntityType.ZOMBIE.create(world, null, dummyBlockPos, SpawnReason.COMMAND, false, false);
        if (zombie != null) {
            zombie.refreshPositionAndAngles(
                dummyBlockPos.getX() + 0.5, dummyBlockPos.getY(), dummyBlockPos.getZ() + 0.5, 0, 0);
            zombie.setAiDisabled(true);
            zombie.setInvulnerable(true);
            zombie.setNoGravity(true);
            zombie.setPersistent();
            zombie.setBaby(false);
            zombie.setCustomName(Text.literal("§6Training Dummy"));
            zombie.setCustomNameVisible(true);
            zombie.addCommandTag("craftics_arena");
            // Clear equipment so it looks neutral
            for (net.minecraft.entity.EquipmentSlot slot : net.minecraft.entity.EquipmentSlot.values()) {
                zombie.equipStack(slot, ItemStack.EMPTY);
            }
            world.spawnEntity(zombie);

            CombatEntity dummyEntity = new CombatEntity(
                zombie.getId(), "minecraft:zombie", dummyPos,
                9999, 0, 0, 1
            );
            dummyEntity.setMobEntity(zombie);

            this.enemies = new ArrayList<>();
            this.enemies.add(dummyEntity);
            testArena.placeEntity(dummyEntity);
        }

        // Initialize combat state
        this.player = player;
        this.arena = testArena;
        this.levelDef = null;
        this.phase = CombatPhase.PLAYER_TURN;
        this.apRemaining = 999;
        this.movePointsRemaining = 999;
        this.turnNumber = 1;
        this.active = true;
        this.playerMounted = false;
        this.mountMob = null;
        tileEffects.clear();
        combatEffects.clear();

        player.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
        world.getGameRules().get(net.minecraft.world.GameRules.NATURAL_REGENERATION).set(false, world.getServer());
        world.setTimeOfDay(6000);

        // Teleport player
        BlockPos startPos = testArena.getPlayerStartBlockPos();
        player.requestTeleport(startPos.getX() + 0.5, startPos.getY(), startPos.getZ() + 0.5);

        // Send client combat enter payload
        ServerPlayNetworking.send(player, new EnterCombatPayload(
            origin.getX(), origin.getY(), origin.getZ(), size, size, -1f
        ));

        // Give Move feather
        ItemStack moveItem = new ItemStack(Items.FEATHER);
        moveItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§aMove"));
        player.getInventory().setStack(8, moveItem);
        player.getInventory().selectedSlot = 8;

        // Scan trims for combat
        this.activeTrimScan = TrimEffects.scan(player);

        sendMessage("§e§l--- Test Range ---");
        sendMessage("§aInfinite AP & Movement | Training Dummy: 9999 HP");
        sendMessage("§7Use §e/craftics leave §7to exit.");
        sendSync();
        refreshHighlights();

        CrafticsMod.LOGGER.info("Player {} entered test range", player.getName().getString());
    }

    public void startCombat(ServerPlayerEntity player, GridArena arena, LevelDefinition levelDef) {
        this.player = player;
        this.arena = arena;
        this.levelDef = levelDef;
        this.enemies = new ArrayList<>();
        this.phase = CombatPhase.PLAYER_TURN;
        // Read base stats from player progression
        PlayerProgression prog = PlayerProgression.get((ServerWorld) player.getEntityWorld());
        PlayerProgression.PlayerStats pStats = prog.getStats(player);
        this.apRemaining = pStats.getEffective(PlayerProgression.Stat.AP);
        this.movePointsRemaining = pStats.getEffective(PlayerProgression.Stat.SPEED);
        // Armor set bonuses
        this.apRemaining += PlayerCombatStats.getSetApBonus(player);
        this.movePointsRemaining += PlayerCombatStats.getSetSpeedBonus(player);
        // Armor trim bonuses
        this.activeTrimScan = TrimEffects.scan(player);
        this.apRemaining += activeTrimScan.get(TrimEffects.Bonus.AP);
        this.movePointsRemaining += activeTrimScan.get(TrimEffects.Bonus.SPEED);
        this.turnNumber = 1;
        this.active = true;
        this.playerMounted = false;
        this.mountMob = null;
        tileEffects.clear();

        // Adventure mode during combat (no block breaking)
        player.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);

        // Apply Vitality stat + Host trim: max HP bonus via Health Boost
        int vitalityPoints = pStats.getPoints(PlayerProgression.Stat.VITALITY);
        int trimHpBonus = activeTrimScan.get(TrimEffects.Bonus.MAX_HP); // +2 per piece
        int totalHpBonusLevels = vitalityPoints + trimHpBonus;
        if (totalHpBonusLevels > 0) {
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.HEALTH_BOOST, 999999, totalHpBonusLevels - 1));
            player.setHealth(player.getMaxHealth());
        }

        // Unfreeze any hub-applied potion effects now that combat started
        combatEffects.unfreezeAll(5); // default 5 turns for hub potions

        ServerWorld world = (ServerWorld) player.getEntityWorld();

        // Disable natural health regeneration during combat
        world.getGameRules().get(net.minecraft.world.GameRules.NATURAL_REGENERATION).set(false, world.getServer());

        // Force-load arena chunks to prevent entity-tracking gap from removing spawned mobs.
        // Without this, chunks loaded by ArenaBuilder for block editing may not have entity
        // tracking active yet (player teleport hasn't been processed by chunk manager).
        BlockPos origin = arena.getOrigin();
        forcedChunks.clear();
        int minCX = (origin.getX() - 2) >> 4;
        int maxCX = (origin.getX() + arena.getWidth() + 2) >> 4;
        int minCZ = (origin.getZ() - 2) >> 4;
        int maxCZ = (origin.getZ() + arena.getHeight() + 2) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                world.setChunkForced(cx, cz, true);
                forcedChunks.add(new net.minecraft.util.math.ChunkPos(cx, cz));
            }
        }

        // Clear any leftover entities from previous arena attempts
        net.minecraft.util.math.Box arenaBox = new net.minecraft.util.math.Box(
            origin.getX() - 2, origin.getY() - 1, origin.getZ() - 2,
            origin.getX() + arena.getWidth() + 2, origin.getY() + 5, origin.getZ() + arena.getHeight() + 2
        );
        for (var entity : world.getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, arenaBox, e -> true)) {
            entity.discard();
        }

        // NG+ scaling
        CrafticsSavedData ngData = CrafticsSavedData.get(world);
        float ngMult = ngData.getNgPlusMultiplier();

        // Determine if this is a boss level and which entity type is the boss
        String bossEntityTypeId = null;
        String bossBiomeId = null;
        if (levelDef instanceof com.crackedgames.craftics.level.GeneratedLevelDefinition gld) {
            BiomeTemplate biome = gld.getBiomeTemplate();
            if (biome.isBossLevel(gld.getLevelNumber()) && biome.boss != null) {
                bossEntityTypeId = biome.boss.entityTypeId();
                bossBiomeId = biome.biomeId;
            }
        }

        // Set night BEFORE spawning undead to prevent sun damage on first tick
        boolean willHaveUndead = false;
        for (LevelDefinition.EnemySpawn s : levelDef.getEnemySpawns()) {
            if (isUndeadMob(s.entityTypeId())) { willHaveUndead = true; break; }
        }
        if (levelDef.isNightLevel() || willHaveUndead) {
            world.setTimeOfDay(18000);
        }

        // Spawn enemies — remap definition coordinates to the live arena size and
        // resolve to the nearest valid tile if environment bleed or custom arena
        // geometry made the exact spot unusable.
        boolean bossSpawned = false;
        for (LevelDefinition.EnemySpawn spawn : levelDef.getEnemySpawns()) {
            GridPos requestedPos = spawn.position();
            GridPos adaptedPos = adaptSpawnToArena(requestedPos, levelDef, arena);
            int size = CombatEntity.getDefaultSizeStatic(spawn.entityTypeId());
            GridPos resolvedPos = findNearestValidSpawn(arena, adaptedPos, size);

            if (resolvedPos == null) {
                CrafticsMod.LOGGER.warn(
                    "Skipping enemy spawn at {} — no valid tile after adapting to arena {}x{}",
                    requestedPos, arena.getWidth(), arena.getHeight());
                continue;
            }

            if (!resolvedPos.equals(requestedPos)) {
                CrafticsMod.LOGGER.info("Adapted enemy spawn {} -> {} for arena {}x{}",
                    requestedPos, resolvedPos, arena.getWidth(), arena.getHeight());
            }

            EntityType<?> type = Registries.ENTITY_TYPE.get(Identifier.of(spawn.entityTypeId()));
            BlockPos spawnPos = arena.gridToBlockPos(resolvedPos);

            // Use create() + spawnEntity() instead of type.spawn() to bypass Minecraft's
            // placement validation that can reject entities when others are nearby
            var rawEntity = type.create(world, null, spawnPos, SpawnReason.MOB_SUMMONED, false, false);
            if (rawEntity == null) {
                CrafticsMod.LOGGER.warn("Failed to create entity {} at {}", spawn.entityTypeId(), resolvedPos);
                continue;
            }
            rawEntity.refreshPositionAndAngles(
                spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);

            // Set invulnerable BEFORE spawning to prevent instant death from sun/suffocation
            if (rawEntity instanceof MobEntity preMob) {
                preMob.setInvulnerable(true);
                preMob.setAiDisabled(true);
                preMob.setNoGravity(true);
                preMob.setPersistent();
            }

            // Use spawnEntity (not spawnNewEntityAndPassengers) to bypass mob cap
            // and collision validation that can silently reject arena mobs
            boolean spawned = world.spawnEntity(rawEntity);
            if (!spawned || rawEntity.isRemoved()) {
                CrafticsMod.LOGGER.warn("Entity {} failed to spawn at {} — retrying with force",
                    spawn.entityTypeId(), resolvedPos);
                // Force spawn by re-creating
                rawEntity = type.create(world, null, spawnPos, SpawnReason.COMMAND, false, false);
                if (rawEntity != null) {
                    rawEntity.refreshPositionAndAngles(
                        spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
                    world.spawnEntity(rawEntity);
                }
                if (rawEntity == null || rawEntity.isRemoved()) {
                    CrafticsMod.LOGGER.error("Entity {} completely failed to spawn — skipping",
                        spawn.entityTypeId());
                    continue;
                }
            }

            if (rawEntity instanceof MobEntity mob) {
                // Remove any jockey riders (skeleton on spider, baby zombie on chicken, etc.)
                // These riders aren't part of the combat system and would use vanilla AI
                for (net.minecraft.entity.Entity passenger : mob.getPassengerList()) {
                    passenger.stopRiding();
                    passenger.discard();
                }
                // Also dismount this mob if it spawned riding something
                if (mob.hasVehicle()) {
                    net.minecraft.entity.Entity vehicle = mob.getVehicle();
                    mob.stopRiding();
                    if (vehicle != null) vehicle.discard();
                }

                // Position entity at center of occupied area
                // 1x1 mobs: center of tile (+0.5), 2x2 mobs: corner where 4 tiles meet (+1.0)
                double offset = size > 1 ? 1.0 : 0.5;
                mob.refreshPositionAndAngles(
                    spawnPos.getX() + offset, spawnPos.getY(), spawnPos.getZ() + offset,
                    mob.getYaw(), mob.getPitch()
                );
                mob.setAiDisabled(true);
                mob.setInvulnerable(true);
                mob.setNoGravity(true);
                mob.setPersistent();
                mob.setHealth(mob.getMaxHealth()); // Ensure full vanilla health after spawn
                mob.addCommandTag("craftics_arena");

                boolean isBoss = !bossSpawned && bossEntityTypeId != null
                    && spawn.entityTypeId().equals(bossEntityTypeId);
                if (isBoss) bossSpawned = true;

                // Boss visual distinction: per-boss equipment, name, scale + glowing
                if (isBoss && bossBiomeId != null) {
                    equipBossVisuals(mob, bossBiomeId);
                    if (com.crackedgames.craftics.CrafticsMod.CONFIG.bossGlowEffect()) {
                        mob.setGlowing(true);
                    }
                }

                // Equipment bonuses — scan what Minecraft gave the mob
                int equipAtkBonus = 0;
                int equipDefBonus = 0;
                int equipSpeedBonus = 0;
                // Weapon check
                ItemStack mainHand = mob.getMainHandStack();
                if (!mainHand.isEmpty()) {
                    Item weapon = mainHand.getItem();
                    if (weapon == Items.IRON_SWORD || weapon == Items.IRON_AXE) equipAtkBonus += 2;
                    else if (weapon == Items.DIAMOND_SWORD || weapon == Items.DIAMOND_AXE) equipAtkBonus += 3;
                    else if (weapon == Items.NETHERITE_SWORD || weapon == Items.NETHERITE_AXE) equipAtkBonus += 4;
                    else if (weapon == Items.GOLDEN_SWORD || weapon == Items.BOW || weapon == Items.CROSSBOW) equipAtkBonus += 1;
                }
                // Armor check (sum of all armor slots)
                for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[]{
                    net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                    net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET}) {
                    ItemStack armor = mob.getEquippedStack(slot);
                    if (!armor.isEmpty()) equipDefBonus += 1;
                }
                // Baby variant = faster + visually smaller
                if (mob.isBaby()) {
                    equipSpeedBonus += 2;
                    scaleBabyMob(mob);
                }

                // Apply NG+ scaling + config multiplier (biome progression bonus already in spawn.hp() from LevelGenerator)
                int scaledHp = Math.max(1, (int)(spawn.hp() * ngMult * com.crackedgames.craftics.CrafticsMod.CONFIG.enemyHpMultiplier()));
                int scaledAtk = Math.max(1, (int)((spawn.attack() + equipAtkBonus) * ngMult));
                int finalDef = spawn.defense() + equipDefBonus;

                // Determine entity grid size: boss AI defines its own, others use mob defaults
                int sizeOverride = -1;
                if (isBoss && bossBiomeId != null) {
                    var bossAiInstance = AIRegistry.get("boss:" + bossBiomeId);
                    if (bossAiInstance instanceof BossAI bai) {
                        sizeOverride = bai.getGridSize();
                    }
                }
                CombatEntity ce = new CombatEntity(
                    mob.getId(), spawn.entityTypeId(), resolvedPos,
                    scaledHp, scaledAtk, finalDef, spawn.range(),
                    sizeOverride
                );
                // Apply baby speed bonus
                if (equipSpeedBonus > 0) {
                    ce.setSpeedBonus(equipSpeedBonus);
                }
                // Boss setup: flag as boss and assign biome-specific AI
                if (isBoss && bossBiomeId != null) {
                    ce.setBoss(true);
                    ce.setAiOverrideKey("boss:" + bossBiomeId);
                    ce.setBossDisplayName(getBossName(bossBiomeId));
                    CrafticsMod.LOGGER.info("[BOSS DEBUG] Spawned boss entity='{}' aiOverrideKey='boss:{}' entityId={}",
                        spawn.entityTypeId(), bossBiomeId, ce.getEntityId());
                }
                ce.setMobEntity(mob);
                enemies.add(ce);
                arena.placeEntity(ce);
                if (isBoss) {
                    initBossSetup(ce);
                }
            }
        }

        // Force night if any undead are present (prevents burning), otherwise use level setting
        boolean hasUndead = enemies.stream().anyMatch(e -> isUndeadMob(e.getEntityTypeId()));
        world.setTimeOfDay((levelDef.isNightLevel() || hasUndead) ? 18000 : 6000);

        // Give the player a named "Move" feather in slot 8 (hotbar 9)
        ItemStack moveItem = new ItemStack(Items.FEATHER);
        moveItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§aMove"));
        player.getInventory().setStack(8, moveItem);
        player.getInventory().selectedSlot = 8; // start in move mode

        // Sound: combat start
        player.getWorld().playSound(null, player.getBlockPos(),
            net.minecraft.sound.SoundEvents.ENTITY_ENDER_DRAGON_GROWL,
            net.minecraft.sound.SoundCategory.HOSTILE, 0.3f, 1.5f);

        // Level name displayed as title on combat start
        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(5, 40, 15));
        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(Text.literal("§e" + levelDef.getName())));
        // Show enemy intentions if enabled
        if (CrafticsMod.CONFIG.showEnemyIntentions()) {
            showEnemyIntentionPreviews();
        }

        sendSync();
        refreshHighlights();

        int expectedCount = levelDef.getEnemySpawns().length;
        if (enemies.size() < expectedCount) {
            CrafticsMod.LOGGER.warn("Combat started with MISSING ENEMIES: {}/{} spawned for '{}'",
                enemies.size(), expectedCount, levelDef.getName());
        } else {
            CrafticsMod.LOGGER.info("Combat started: {}/{} enemies spawned for '{}'",
                enemies.size(), expectedCount, levelDef.getName());
        }

        // Persist inCombat so a disconnect/rejoin can restart this fight
        CrafticsSavedData startData = CrafticsSavedData.get((ServerWorld) player.getEntityWorld());
        startData.loadPlayerIntoLegacy(player.getUuid());
        startData.inCombat = true;
        startData.markDirty();
    }

    public void handleAction(CombatActionPayload action) {
        if (!active) return;

        if (phase != CombatPhase.PLAYER_TURN) return;

        // Block input during spell cast animations (particles still staging)
        if (spellAnimCooldown > 0) return;

        switch (action.actionType()) {
            case CombatActionPayload.ACTION_MOVE -> handleMove(new GridPos(action.targetX(), action.targetZ()));
            case CombatActionPayload.ACTION_ATTACK -> handleAttack(action.targetEntityId());
            case CombatActionPayload.ACTION_END_TURN -> handleEndTurn();
            case CombatActionPayload.ACTION_USE_ITEM -> handleUseItem(new GridPos(action.targetX(), action.targetZ()));
        }
    }

    private void handleMove(GridPos target) {
        if (!arena.isInBounds(target)) return;
        if (movePointsRemaining <= 0) {
            sendMessage("§cNo movement left this turn!");
            return;
        }

        // Check if player has a boat for water tile access
        boolean hasBoat = playerHasBoat();

        GridPos playerPos = arena.getPlayerGridPos();
        List<GridPos> path = Pathfinding.findPath(arena, playerPos, target, movePointsRemaining, hasBoat);
        if (path.isEmpty()) {
            // Check if the tile is water and they don't have a boat
            GridTile targetTile = arena.getTile(target);
            if (targetTile != null && targetTile.isWater() && !hasBoat) {
                sendMessage("§bYou need a boat to cross water!");
            } else {
                sendMessage("§cCan't reach that tile!");
            }
            return;
        }

        // Check if any tile in the path is water — consume a boat on first water entry
        boolean wasOnWater = isPlayerOnWater();
        boolean pathHasWater = false;
        for (GridPos p : path) {
            GridTile tile = arena.getTile(p);
            if (tile != null && tile.isWater()) {
                pathHasWater = true;
                break;
            }
        }

        if (pathHasWater && !wasOnWater) {
            // Entering water — consume a boat
            if (!consumeBoat()) {
                sendMessage("§bYou need a boat to cross water!");
                return;
            }
        }

        // Check if leaving water to land
        GridTile endTile = arena.getTile(path.get(path.size() - 1));

        int cost = path.size();
        movePointsRemaining -= cost;

        // Start animated movement
        clearHighlights();
        this.movePath = path;
        this.movePathIndex = 0;
        this.moveTickCounter = 0;
        this.phase = CombatPhase.ANIMATING;
        sendSync(); // tell client we're animating so walk animation plays
    }

    private boolean playerHasBoat() {
        if (player == null) return false;
        for (int i = 0; i < player.getInventory().size(); i++) {
            Item item = player.getInventory().getStack(i).getItem();
            if (item == Items.OAK_BOAT || item == Items.SPRUCE_BOAT || item == Items.BIRCH_BOAT
                || item == Items.JUNGLE_BOAT || item == Items.ACACIA_BOAT || item == Items.DARK_OAK_BOAT
                || item == Items.MANGROVE_BOAT || item == Items.CHERRY_BOAT || item == Items.BAMBOO_RAFT) {
                return true;
            }
        }
        return false;
    }

    private boolean consumeBoat() {
        if (player == null) return false;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            Item item = stack.getItem();
            if (item == Items.OAK_BOAT || item == Items.SPRUCE_BOAT || item == Items.BIRCH_BOAT
                || item == Items.JUNGLE_BOAT || item == Items.ACACIA_BOAT || item == Items.DARK_OAK_BOAT
                || item == Items.MANGROVE_BOAT || item == Items.CHERRY_BOAT || item == Items.BAMBOO_RAFT) {
                stack.decrement(1);
                return true;
            }
        }
        return false;
    }

    private boolean isPlayerOnWater() {
        if (arena == null) return false;
        GridTile tile = arena.getTile(arena.getPlayerGridPos());
        return tile != null && tile.isWater();
    }

    /** Returns server-side tick delay for weapon impact (matches client animation). */
    private int getWeaponImpactDelay(Item weapon) {
        String id = Registries.ITEM.getId(weapon).getPath();
        if (id.contains("bow") || id.contains("crossbow")) return 10;
        if (id.contains("axe")) return 9;
        if (id.contains("mace")) return 10;
        if (id.contains("trident")) return 5;
        return 6; // sword/fist default
    }

    private void handleAttack(int targetEntityId) {
        // Block input while a previous attack is still resolving
        if (pendingAttackAction != null) return;

        Item weapon = player.getMainHandStack().getItem();
        int attackCost = WeaponAbility.getAttackCost(weapon);
        if (apRemaining < attackCost) {
            sendMessage("§cNeed " + attackCost + " AP to attack! (have " + apRemaining + ")");
            return;
        }

        CombatEntity target = null;
        for (CombatEntity e : enemies) {
            if (e.getEntityId() == targetEntityId && e.isAlive()) {
                target = e;
                break;
            }
        }

        if (target == null) {
            sendMessage("§cNo valid target!");
            return;
        }

        // Range check
        int range = PlayerCombatStats.getWeaponRange(player);
        GridPos pPos = arena.getPlayerGridPos();
        GridPos tPos = target.getGridPos();

        if (range == PlayerCombatStats.RANGE_CROSSBOW_ROOK) {
            // Crossbow: rook pattern — must be in same row/column with clear line
            if (!PlayerCombatStats.isInCrossbowLine(arena, pPos, tPos)) {
                sendMessage("§cCrossbow requires a clear straight line! (N/S/E/W only)");
                return;
            }
        } else {
            boolean isRanged = PlayerCombatStats.isBow(player) || weapon == Items.TRIDENT;
            // For multi-tile mobs, check distance to nearest occupied tile
            int dist;
            if (target.getSize() > 1) {
                dist = target.minDistanceTo(pPos);
            } else {
                dist = isRanged ? pPos.manhattanDistance(tPos)
                    : Math.max(Math.abs(pPos.x() - tPos.x()), Math.abs(pPos.z() - tPos.z()));
            }
            if (dist > range) {
                sendMessage("§cTarget out of range! (distance: " + dist + ", range: " + range + ")");
                return;
            }
        }

        // === IMMEDIATE: Face target, deduct AP, consume ammo, trigger client animation ===

        // Face the target
        int dx = tPos.x() - pPos.x();
        int dz = tPos.z() - pPos.z();
        float attackYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        player.teleport((ServerWorld) player.getEntityWorld(),
            player.getX(), player.getY(), player.getZ(),
            java.util.Collections.emptySet(), attackYaw, 0f);

        String tippedEffect = null;
        if (PlayerCombatStats.isBow(player) || weapon == Items.CROSSBOW) {
            if (PlayerCombatStats.hasTippedArrows(player)) {
                tippedEffect = PlayerCombatStats.findAndConsumeTippedArrow(player);
            } else if (!PlayerCombatStats.hasInfinity(player)) {
                PlayerCombatStats.consumeArrow(player);
            }
        }

        apRemaining -= attackCost;

        // Apply 10 durability damage per attack (tactical combat is hard on weapons)
        ItemStack weaponStack = player.getMainHandStack();
        if (weaponStack.isDamageable()) {
            weaponStack.damage(10, player, net.minecraft.entity.EquipmentSlot.MAINHAND);
            if (weaponStack.isEmpty() || weaponStack.getDamage() >= weaponStack.getMaxDamage()) {
                sendMessage("§c§lYour weapon broke!");
            }
        }

        // Pre-calculate damage before the delay (snapshot current stats)
        boolean isRangedWeapon = weapon == Items.BOW || weapon == Items.CROSSBOW;
        boolean isTridentWeapon = weapon == Items.TRIDENT;
        int progBonus = isRangedWeapon ? getProgRangedBonus() : getProgMeleeBonus();
        DamageType damageType = DamageType.fromWeapon(weapon);
        int damageTypeBonus = DamageType.getTotalBonus(
            PlayerCombatStats.getArmorSet(player), activeTrimScan, combatEffects, damageType);
        int baseDamage = PlayerCombatStats.getAttackPower(player) + combatEffects.getStrengthBonus()
            + progBonus + PlayerCombatStats.getSetAttackBonus(player)
            + PlayerCombatStats.getWeaponEnchantBonus(player) + damageTypeBonus;
        baseDamage = (int)(baseDamage * com.crackedgames.craftics.CrafticsMod.CONFIG.playerDamageMultiplier());
        if (isRangedWeapon) {
            baseDamage += PlayerCombatStats.getBowPower(player);
        }
        int luckPoints = PlayerProgression.get((ServerWorld) player.getEntityWorld()).getStats(player)
            .getPoints(PlayerProgression.Stat.LUCK);
        int trimLuck = activeTrimScan != null ? activeTrimScan.get(TrimEffects.Bonus.LUCK) : 0;
        boolean hasAnyCritSource = luckPoints > 0 || PlayerCombatStats.hasGoldSet(player) || trimLuck > 0;
        boolean luckCrit = false;
        if (hasAnyCritSource) {
            // Luck stat: 5% per point
            if (luckPoints > 0) luckCrit = Math.random() < (luckPoints * 0.05);
            // Gold set: flat 15% crit
            if (!luckCrit && PlayerCombatStats.hasGoldSet(player)) luckCrit = Math.random() < 0.15;
            // Trim luck bonus: 3% per piece
            if (!luckCrit && trimLuck > 0) luckCrit = Math.random() < (trimLuck * 0.03);
            // Config-based critical hit chance (only if player has a crit source)
            if (!luckCrit && CrafticsMod.CONFIG.criticalHitChance() > 0) {
                luckCrit = Math.random() < CrafticsMod.CONFIG.criticalHitChance();
            }
        }
        if (luckCrit) {
            baseDamage = (int)(baseDamage * 1.5);
        }
        // Kill streak damage multipliers (stack multiplicatively)
        if (killStreak > 0) {
            double streakMult = 1.0;
            // Feral trim set: 1.3x per streak level
            if (activeTrimScan != null && activeTrimScan.setBonus() == TrimEffects.SetBonus.FERAL) {
                streakMult *= Math.pow(1.3, killStreak);
            }
            // Leather (Brawler) armor set: 2x per streak level
            if ("leather".equals(PlayerCombatStats.getArmorSet(player))) {
                streakMult *= Math.pow(2.0, killStreak);
            }
            if (streakMult > 1.0) {
                baseDamage = (int)(baseDamage * streakMult);
            }
        }

        // Apply mob-type vulnerability/resistance multiplier
        double mobResistMult = MobResistances.getDamageMultiplier(target.getEntityTypeId(), damageType);
        if (mobResistMult == 0.0) {
            baseDamage = 0; // immune
        } else if (mobResistMult != 1.0) {
            baseDamage = Math.max(1, (int)(baseDamage * mobResistMult));
        }

        // Prize sherd: Fortune's Favor — triple damage on next attack
        boolean usedTriple = false;
        if (tripleDamageNextAttack) {
            baseDamage *= 3;
            tripleDamageNextAttack = false;
            usedTriple = true;
        }

        int fireAspect = PlayerCombatStats.getFireAspect(player);
        boolean hasBowFlame = isRangedWeapon && PlayerCombatStats.hasBowFlame(player);

        // Snapshot values for the delayed lambda
        final CombatEntity fTarget = target;
        final int fBaseDamage = baseDamage;
        final boolean fUsedTriple = usedTriple;
        final double fMobResistMult = mobResistMult;
        final boolean fIsRangedWeapon = isRangedWeapon;
        final boolean fIsTridentWeapon = isTridentWeapon;
        final boolean fLuckCrit = luckCrit;
        final String fTippedEffect = tippedEffect;
        final int fFireAspect = fireAspect;
        final boolean fHasBowFlame = hasBowFlame;
        final DamageType fDamageType = damageType;
        final int fDamageTypeBonus = damageTypeBonus;
        final Item fWeapon = weapon;
        final GridPos fPPos = pPos;
        final GridPos fTPos = tPos;

        // Trigger client animation immediately (damage visuals are already delayed on client)
        sendToAllParty(new CombatEventPayload(
            CombatEventPayload.EVENT_DAMAGED, target.getEntityId(),
            0, target.getCurrentHp(), tPos.x(), tPos.z()  // 0 damage placeholder — triggers animation
        ));

        // Spawn projectile immediately for ranged (it needs flight time)
        if (isRangedWeapon || isTridentWeapon) {
            player.getWorld().playSound(null, player.getBlockPos(),
                weapon == Items.CROSSBOW ? net.minecraft.sound.SoundEvents.ITEM_CROSSBOW_SHOOT : net.minecraft.sound.SoundEvents.ENTITY_ARROW_SHOOT,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
            BlockPos playerBlock = arena.gridToBlockPos(pPos);
            BlockPos targetBlockForProj = arena.gridToBlockPos(tPos);
            ProjectileSpawner.spawnPlayerProjectile(
                (ServerWorld) player.getEntityWorld(), playerBlock, targetBlockForProj, isTridentWeapon);
        }

        sendSync(); // Update AP display immediately

        // === DELAYED: Damage, effects, particles, death — fires when animation impacts ===
        pendingAttackDelay = getWeaponImpactDelay(weapon);
        pendingAttackAction = () -> {
            if (!active || player == null || !fTarget.isAlive()) return;

            // Ranged accuracy check — miss chance for ranged attacks
            if (fIsRangedWeapon && CrafticsMod.CONFIG.rangedAccuracy() < 1.0f) {
                if (Math.random() > CrafticsMod.CONFIG.rangedAccuracy()) {
                    sendMessage("§7Arrow missed " + fTarget.getDisplayName() + "!");
                    sendSync();
                    return;
                }
            }

            int dealt = fTarget.takeDamage(fBaseDamage);

            // Boss reaction callback
            notifyBossOfDamage(fTarget, dealt);

            // Hit sound
            player.getWorld().playSound(null, player.getBlockPos(),
                net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 0.9f + (float)(Math.random() * 0.2));

            // Fire Aspect
            if (fFireAspect > 0 && !fIsRangedWeapon && fTarget.getMobEntity() != null) {
                fTarget.getMobEntity().setFireTicks(fFireAspect * 80);
                sendMessage("\u00a76Fire Aspect! Enemy burns.");
            }

            // Tipped arrow effects
            if (fTippedEffect != null) {
                switch (fTippedEffect) {
                    case "poison" -> { fTarget.takeDamage(2); sendMessage("\u00a75Poison arrow! +2 poison damage."); }
                    case "slowness" -> { fTarget.setSpeedBonus(fTarget.getSpeedBonus() - 2); sendMessage("\u00a77Slowness arrow! Enemy slowed."); }
                    case "weakness" -> { fTarget.setStunned(true); sendMessage("\u00a77Weakness arrow! Enemy stunned."); }
                    case "harming" -> { fTarget.takeDamage(4); sendMessage("\u00a74Harming arrow! +4 bonus damage."); }
                    case "healing" -> { player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 4)); sendMessage("\u00a7dHealing arrow! Restored 4 HP."); }
                    case "fire_resistance" -> { combatEffects.addEffect(CombatEffects.EffectType.FIRE_RESISTANCE, 3, 0); sendMessage("\u00a76Fire resistance arrow! 3 turns."); }
                }
            }

            // Bow Flame
            if (fHasBowFlame && fTarget.getMobEntity() != null) {
                fTarget.getMobEntity().setFireTicks(100);
                sendMessage("\u00a76Flame arrow! Enemy burns.");
            }

            String tripleMsg = fUsedTriple ? " §d§l\u2726 TRIPLE!" : "";
            String critMsg = fLuckCrit ? " §6§l\u2726 LUCKY CRIT!" : "";
            String typeMsg = fDamageTypeBonus > 0 ? " " + fDamageType.color + "+" + fDamageTypeBonus + " " + fDamageType.displayName : "";
            String resistMsg = fMobResistMult == 0.0 ? " \u00a74IMMUNE!" :
                fMobResistMult < 1.0 ? " \u00a7cResisted (" + fDamageType.displayName + ")" :
                fMobResistMult > 1.0 ? " \u00a7aWeak! (" + fDamageType.displayName + ")" : "";
            sendMessage("§6Hit " + fTarget.getDisplayName() + " for " + dealt + "!" + tripleMsg + critMsg + typeMsg + resistMsg);

            // Weapon ability (cleave, pierce, etc.)
            WeaponAbility.AttackResult abilityResult = WeaponAbility.applyAbility(player, fWeapon, fTarget, arena, fBaseDamage);
            for (String msg : abilityResult.messages()) sendMessage(msg);

            // Send REAL damage event (client will show damage number at this point)
            sendToAllParty(new CombatEventPayload(
                CombatEventPayload.EVENT_DAMAGED, fTarget.getEntityId(),
                dealt, fTarget.getCurrentHp(), fTarget.getGridPos().x(), fTarget.getGridPos().z()
            ));

            // Hit particles
            ServerWorld attackWorld = (ServerWorld) player.getEntityWorld();
            BlockPos targetBlock = arena.gridToBlockPos(fTarget.getGridPos());
            attackWorld.spawnParticles(net.minecraft.particle.ParticleTypes.DAMAGE_INDICATOR,
                targetBlock.getX() + 0.5, targetBlock.getY() + 1.0, targetBlock.getZ() + 0.5,
                3, 0.3, 0.3, 0.3, 0.1);
            attackWorld.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,
                targetBlock.getX() + 0.5, targetBlock.getY() + 1.0, targetBlock.getZ() + 0.5,
                5, 0.4, 0.4, 0.4, 0.2);
            ProjectileSpawner.spawnImpact(attackWorld, targetBlock, fLuckCrit ? "critical" : "melee");

            // Check death
            checkAndHandleDeath(fTarget);
            for (CombatEntity extra : abilityResult.extraTargets()) checkAndHandleDeath(extra);

            sendSync();
            refreshHighlights();

            // Check win
            if (enemies.stream().noneMatch(e -> e.isAlive() && !e.isAlly())) {
                handleVictory();
            }
        };
    }

    private CombatEntity findEnemyById(int entityId) {
        for (CombatEntity e : enemies) {
            if (e.getEntityId() == entityId && e.isAlive()) return e;
        }
        return null;
    }

    public void checkAndHandleDeathPublic(CombatEntity entity) { checkAndHandleDeath(entity); }

    private void checkAndHandleDeath(CombatEntity entity) {
        if (!entity.isAlive()) {
            // Notify bosses of minion death (crystals, turrets, chains, etc.)
            if (!entity.isBoss()) {
                notifyBossOfMinionDeath(entity);
            }
            sendMessage("§a" + entity.getDisplayName() + " defeated!");
            GridPos deathPos = entity.getGridPos();
            arena.removeEntity(entity);

            // Death particles (big poof)
            ServerWorld deathWorld = (ServerWorld) player.getEntityWorld();
            BlockPos deathBlock = arena.gridToBlockPos(deathPos);
            deathWorld.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,
                deathBlock.getX() + 0.5, deathBlock.getY() + 0.5, deathBlock.getZ() + 0.5,
                15, 0.4, 0.4, 0.4, 0.05);
            deathWorld.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
                deathBlock.getX() + 0.5, deathBlock.getY() + 0.5, deathBlock.getZ() + 0.5,
                8, 0.3, 0.3, 0.3, 0.02);

            // Combat sound: enemy death
            if (player != null) {
                player.getWorld().playSound(null, arena.gridToBlockPos(entity.getGridPos()),
                    net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    net.minecraft.sound.SoundCategory.HOSTILE, 1.0f, 0.8f);
            }

            if (entity.getMobEntity() != null) {
                entity.getMobEntity().discard();
            }
            sendToAllParty(new CombatEventPayload(
                CombatEventPayload.EVENT_DIED, entity.getEntityId(), 0, 0,
                deathPos.x(), deathPos.z()
            ));
        }
    }

    private boolean isPassiveMob(CombatEntity entity) {
        return switch (entity.getEntityTypeId()) {
            case "minecraft:cow", "minecraft:pig", "minecraft:sheep", "minecraft:chicken" -> true;
            default -> false;
        };
    }

    private static boolean isUndeadMob(String entityTypeId) {
        return switch (entityTypeId) {
            case "minecraft:zombie", "minecraft:husk", "minecraft:drowned",
                 "minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton",
                 "minecraft:phantom", "minecraft:zombified_piglin",
                 "minecraft:zombie_villager", "minecraft:skeleton_horse",
                 "minecraft:zombie_horse", "minecraft:wither" -> true;
            default -> false;
        };
    }

    /** Returns true if the entity type can visually wear armor (humanoid model). */
    private static boolean isHumanoidMob(String entityTypeId) {
        return switch (entityTypeId) {
            case "minecraft:zombie", "minecraft:husk", "minecraft:drowned",
                 "minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton",
                 "minecraft:zombified_piglin", "minecraft:zombie_villager",
                 "minecraft:piglin", "minecraft:piglin_brute",
                 "minecraft:vindicator", "minecraft:evoker", "minecraft:pillager" -> true;
            default -> false;
        };
    }

    /** Returns the display name for a boss based on biome ID. */
    private static String getBossName(String bossBiomeId) {
        return switch (bossBiomeId) {
            case "plains" -> "The Revenant";
            case "dark_forest" -> "The Hexweaver";
            case "snowy_tundra" -> "The Frostbound Huntsman";
            case "stony_peaks" -> "The Rockbreaker";
            case "river" -> "The Tidecaller";
            case "desert" -> "The Sandstorm Pharaoh";
            case "jungle" -> "The Broodmother";
            case "cave" -> "The Hollow King";
            case "deep_dark" -> "The Warden";
            case "nether_wastes" -> "The Molten King";
            case "soul_sand_valley" -> "The Wailing Revenant";
            case "crimson_forest" -> "The Crimson Ravager";
            case "warped_forest" -> "The Void Walker";
            case "basalt_deltas" -> "The Wither";
            case "outer_end" -> "The Void Herald";
            case "end_city" -> "The Shulker Architect";
            case "chorus_grove" -> "The Chorus Mind";
            case "dragons_nest" -> "The Ender Dragon";
            default -> "Boss";
        };
    }

    /**
     * Equip a boss mob with unique visuals based on biome.
     * Gives each boss custom equipment, name plate, and scale.
     */
    private static void equipBossVisuals(MobEntity mob, String bossBiomeId) {
        switch (bossBiomeId) {
            case "plains" -> { // The Revenant — Zombie
                mob.setCustomName(Text.literal("§4§lThe Revenant"));
                mob.setCustomNameVisible(true);
                ItemStack helmet = new ItemStack(Items.CHAINMAIL_HELMET);
                helmet.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                mob.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, helmet);
                mob.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
                mob.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                ItemStack sword = new ItemStack(Items.STONE_SWORD);
                sword.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                mob.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, sword);
                scaleBoss(mob, 1.6);
            }
            case "dark_forest" -> { // The Hexweaver — Evoker
                mob.setCustomName(Text.literal("§2§lThe Hexweaver"));
                mob.setCustomNameVisible(true);
                // Evoker already has robes; give enchant glint hat for magical look
                ItemStack hat = new ItemStack(Items.LEATHER_HELMET);
                hat.set(DataComponentTypes.DYED_COLOR, new net.minecraft.component.type.DyedColorComponent(0x1B3A1B, false));
                hat.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                mob.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, hat);
                scaleBoss(mob, 1.5);
            }
            case "snowy_tundra" -> { // The Frostbound Huntsman — Stray
                mob.setCustomName(Text.literal("§b§lThe Frostbound Huntsman"));
                mob.setCustomNameVisible(true);
                ItemStack crown = new ItemStack(Items.DIAMOND_HELMET);
                crown.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                mob.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, crown);
                ItemStack frostBow = new ItemStack(Items.BOW);
                frostBow.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                mob.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, frostBow);
                mob.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                scaleBoss(mob, 1.5);
            }
            case "stony_peaks" -> { // The Rockbreaker — Vindicator
                mob.setCustomName(Text.literal("§7§lThe Rockbreaker"));
                mob.setCustomNameVisible(true);
                // Stone-plated armor
                mob.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                mob.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                mob.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                mob.equipStack(net.minecraft.entity.EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                // War hammer (iron axe with glint)
                ItemStack hammer = new ItemStack(Items.IRON_AXE);
                hammer.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                mob.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, hammer);
                scaleBoss(mob, 1.7);
            }
            case "river" -> { // The Tidecaller — Drowned
                mob.setCustomName(Text.literal("§3§lThe Tidecaller"));
                mob.setCustomNameVisible(true);
                // Coral crown = prismarine-tinted helmet
                ItemStack crown = new ItemStack(Items.LEATHER_HELMET);
                crown.set(DataComponentTypes.DYED_COLOR, new net.minecraft.component.type.DyedColorComponent(0x2E8B8B, false));
                crown.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                mob.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, crown);
                // Enchanted trident
                ItemStack trident = new ItemStack(Items.TRIDENT);
                trident.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                mob.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, trident);
                scaleBoss(mob, 1.5);
            }
            case "desert" -> { // The Sandstorm Pharaoh — Husk
                mob.setCustomName(Text.literal("§6§lThe Sandstorm Pharaoh"));
                mob.setCustomNameVisible(true);
                // Gold and lapis headdress
                ItemStack headdress = new ItemStack(Items.GOLDEN_HELMET);
                headdress.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                mob.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, headdress);
                mob.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
                // Golden sword
                ItemStack goldenSword = new ItemStack(Items.GOLDEN_SWORD);
                goldenSword.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                mob.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, goldenSword);
                scaleBoss(mob, 1.6);
            }
            case "jungle" -> { // The Broodmother — Spider
                mob.setCustomName(Text.literal("§a§lThe Broodmother"));
                mob.setCustomNameVisible(true);
                // No equipment — organic spider; just scale it up a lot
                scaleBoss(mob, 2.0);
            }
            case "cave" -> { // The Hollow King — Zombie
                mob.setCustomName(Text.literal("§e§lThe Hollow King"));
                mob.setCustomNameVisible(true);
                // Mining helmet with headlamp (gold helmet + glint)
                ItemStack miningHelmet = new ItemStack(Items.GOLDEN_HELMET);
                miningHelmet.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                mob.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, miningHelmet);
                // Glowing pickaxe
                ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
                pickaxe.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                mob.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, pickaxe);
                // Ore-encrusted body = leather armor dyed gray-green
                ItemStack oreChest = new ItemStack(Items.LEATHER_CHESTPLATE);
                oreChest.set(DataComponentTypes.DYED_COLOR, new net.minecraft.component.type.DyedColorComponent(0x556B2F, false));
                mob.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, oreChest);
                scaleBoss(mob, 1.6);
            }
            case "deep_dark" -> { // The Warden — keep existing
                mob.setCustomName(Text.literal("§8§lThe Warden"));
                mob.setCustomNameVisible(true);
                // No equipment changes — Warden's model is already imposing
                scaleBoss(mob, 1.3); // Warden is already large; slight boost
            }
            case "nether_wastes" -> { // The Molten King — Magma Cube
                mob.setCustomName(Text.literal("§c§lThe Molten King"));
                mob.setCustomNameVisible(true);
                // No equipment — magma cube; big scale
                scaleBoss(mob, 2.2);
            }
            case "soul_sand_valley" -> { // The Wailing Revenant — Ghast
                mob.setCustomName(Text.literal("§9§lThe Wailing Revenant"));
                mob.setCustomNameVisible(true);
                // No equipment — ghast is already ghostly
                scaleBoss(mob, 1.4); // Ghast is already large
            }
            case "crimson_forest" -> { // The Crimson Ravager — Hoglin
                mob.setCustomName(Text.literal("§4§lThe Crimson Ravager"));
                mob.setCustomNameVisible(true);
                // No equipment — organic beast; big scale
                scaleBoss(mob, 1.8);
            }
            case "warped_forest" -> { // The Void Walker — Enderman
                mob.setCustomName(Text.literal("§5§lThe Void Walker"));
                mob.setCustomNameVisible(true);
                // No equipment — entity distorts reality
                scaleBoss(mob, 1.7);
            }
            case "basalt_deltas" -> { // The Wither
                mob.setCustomName(Text.literal("§0§lThe Wither"));
                mob.setCustomNameVisible(true);
                // Wither is naturally large — no equipment needed, just scale
                scaleBoss(mob, 1.5);
            }
            case "outer_end" -> { // The Void Herald — Enderman
                mob.setCustomName(Text.literal("§d§lThe Void Herald"));
                mob.setCustomNameVisible(true);
                // No equipment — channels the void
                scaleBoss(mob, 1.8);
            }
            case "end_city" -> { // The Shulker Architect — Shulker
                mob.setCustomName(Text.literal("§e§lThe Shulker Architect"));
                mob.setCustomNameVisible(true);
                // Shulkers can't equip items; just scale
                scaleBoss(mob, 1.5);
            }
            case "chorus_grove" -> { // The Chorus Mind — Enderman
                mob.setCustomName(Text.literal("§d§lThe Chorus Mind"));
                mob.setCustomNameVisible(true);
                // No equipment — fused with chorus plants
                scaleBoss(mob, 1.7);
            }
            case "dragons_nest" -> { // The Ender Dragon — keep existing
                mob.setCustomName(Text.literal("§5§lThe Ender Dragon"));
                mob.setCustomNameVisible(true);
                // Dragon is already massive; no scale change needed
            }
            default -> {
                // Fallback for unknown biomes — mild visual upgrade
                mob.setCustomName(Text.literal("§c§lBoss"));
                mob.setCustomNameVisible(true);
                if (isHumanoidMob(mob.getType().toString())) {
                    mob.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                }
                scaleBoss(mob, 1.5);
            }
        }
    }

    /** Set boss scale using vanilla GENERIC_SCALE attribute. */
    private static void scaleBoss(MobEntity mob, double scale) {
        var scaleAttr = mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(scale);
        }
    }

    /**
     * Spawn ambient particles around a boss mob each tick based on its biome.
     * Called from the main combat tick loop.
     */
    private void tickBossAmbientParticles() {
        if (player == null) return;
        ServerWorld sw = (ServerWorld) player.getEntityWorld();
        for (CombatEntity e : enemies) {
            if (!e.isAlive() || !e.isBoss()) continue;
            MobEntity mob = e.getMobEntity();
            if (mob == null || !mob.isAlive()) continue;
            double x = mob.getX();
            double y = mob.getY();
            double z = mob.getZ();

            // Only spawn particles every few ticks to avoid spam
            String aiKey = e.getAiKey();
            String biome = aiKey.startsWith("boss:") ? aiKey.substring(5) : "";

            switch (biome) {
                case "plains" -> { // Soul flame wisps
                    if (tickCounter % 10 == 0)
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                            x, y + 1.5, z, 2, 0.3, 0.5, 0.3, 0.01);
                }
                case "dark_forest" -> { // Swirling potion particles
                    if (tickCounter % 6 == 0)
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.WITCH,
                            x, y + 1.0, z, 3, 0.4, 0.6, 0.4, 0.02);
                }
                case "snowy_tundra" -> { // Pale blue snowflakes
                    if (tickCounter % 8 == 0)
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.SNOWFLAKE,
                            x, y + 1.2, z, 3, 0.5, 0.8, 0.5, 0.01);
                }
                case "stony_peaks" -> { // Dust / rock particles
                    if (tickCounter % 12 == 0)
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.ASH,
                            x, y + 0.3, z, 4, 0.5, 0.2, 0.5, 0.01);
                }
                case "river" -> { // Water drips
                    if (tickCounter % 8 == 0)
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.DRIPPING_WATER,
                            x, y + 1.8, z, 2, 0.3, 0.3, 0.3, 0.01);
                }
                case "desert" -> { // Sand swirl
                    if (tickCounter % 8 == 0) {
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.ASH,
                            x, y + 0.5, z, 3, 0.6, 0.4, 0.6, 0.02);
                    }
                }
                case "jungle" -> { // Green drip (venom)
                    if (tickCounter % 10 == 0)
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.ITEM_SLIME,
                            x, y + 0.5, z, 2, 0.5, 0.2, 0.5, 0.01);
                }
                case "cave" -> { // Green aura + ambient dust
                    if (tickCounter % 8 == 0) {
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                            x, y + 1.0, z, 2, 0.4, 0.5, 0.4, 0.01);
                    }
                }
                case "deep_dark" -> { // Sculk particles + heartbeat
                    if (tickCounter % 12 == 0)
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.SCULK_SOUL,
                            x, y + 1.5, z, 2, 0.3, 0.5, 0.3, 0.01);
                }
                case "nether_wastes" -> { // Lava drips + flame
                    if (tickCounter % 6 == 0) {
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.DRIPPING_LAVA,
                            x, y + 1.5, z, 2, 0.4, 0.3, 0.4, 0.01);
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                            x, y + 0.8, z, 1, 0.3, 0.5, 0.3, 0.01);
                    }
                }
                case "soul_sand_valley" -> { // Blue soul fire trail
                    if (tickCounter % 6 == 0)
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                            x, y + 1.0, z, 3, 0.5, 0.8, 0.5, 0.02);
                }
                case "crimson_forest" -> { // Crimson spore + red particles
                    if (tickCounter % 8 == 0)
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.CRIMSON_SPORE,
                            x, y + 1.0, z, 5, 0.6, 0.8, 0.6, 0.02);
                }
                case "warped_forest" -> { // Void / portal particles
                    if (tickCounter % 6 == 0)
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL,
                            x, y + 1.0, z, 4, 0.4, 0.8, 0.4, 0.05);
                }
                case "basalt_deltas" -> { // Wither rose + ash
                    if (tickCounter % 8 == 0) {
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
                            x, y + 1.5, z, 2, 0.3, 0.5, 0.3, 0.01);
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.ASH,
                            x, y + 0.5, z, 3, 0.5, 0.3, 0.5, 0.01);
                    }
                }
                case "outer_end" -> { // Storm / electric
                    if (tickCounter % 6 == 0)
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                            x, y + 2.0, z, 3, 0.6, 1.0, 0.6, 0.03);
                }
                case "end_city" -> { // Purple rune shimmer
                    if (tickCounter % 10 == 0)
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANT,
                            x, y + 0.5, z, 4, 0.3, 0.5, 0.3, 0.5);
                }
                case "chorus_grove" -> { // Magenta bioluminescence
                    if (tickCounter % 8 == 0)
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                            x, y + 1.0, z, 2, 0.4, 0.6, 0.4, 0.02);
                }
                case "dragons_nest" -> { // Dragon breath wisps
                    if (tickCounter % 10 == 0)
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.DRAGON_BREATH,
                            x, y + 2.0, z, 3, 0.8, 0.5, 0.8, 0.02);
                }
                default -> {} // No particles for unknown biomes
            }
        }
    }

    /** Scale baby variant mobs smaller using vanilla SCALE attribute. */
    private static void scaleBabyMob(MobEntity mob) {
        var scaleAttr = mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(0.7);
        }
    }

    /** Shrink a dying mob to near-zero (death animation). */
    private static void startDeathShrink(MobEntity mob) {
        var scaleAttr = mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(0.05); // near-zero, vanishes visually
        }
    }

    private void tickReacting() {
        // Reuses enemy movement lerp logic, returns to PLAYER_TURN when done
        if (enemyMovePathIndex >= enemyMovePath.size()) {
            enemyLerpInitialized = false;
            currentEnemy = null;
            phase = CombatPhase.PLAYER_TURN;
            return;
        }

        MobEntity mob = currentEnemy.getMobEntity();
        if (mob == null || !mob.isAlive() || mob.isRemoved()) {
            enemyLerpInitialized = false;
            currentEnemy = null;
            phase = CombatPhase.PLAYER_TURN;
            return;
        }

        if (!enemyLerpInitialized) {
            enemyLerpInitialized = true;
            enemyMoveTickCounter = 0;
            enemyLerpStartX = mob.getX();
            enemyLerpStartY = mob.getY();
            enemyLerpStartZ = mob.getZ();

            GridPos next = enemyMovePath.get(enemyMovePathIndex);
            GridPos prev = enemyMovePathIndex == 0 ? currentEnemy.getGridPos() : enemyMovePath.get(enemyMovePathIndex - 1);
            int dx = next.x() - prev.x();
            int dz = next.z() - prev.z();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            mob.setYaw(yaw);
            mob.setHeadYaw(yaw);
        }

        enemyMoveTickCounter++;
        GridPos next = enemyMovePath.get(enemyMovePathIndex);
        BlockPos endBlock = arena.gridToBlockPos(next);
        double moveOffset = (currentEnemy != null && currentEnemy.getSize() > 1) ? currentEnemy.getSize() / 2.0 : 0.5;
        double endX = endBlock.getX() + moveOffset;
        double endY = endBlock.getY();
        double endZ = endBlock.getZ() + moveOffset;

        int emTicks = getMoveTicks();
        float progress = Math.min(1.0f, (float) enemyMoveTickCounter / emTicks);
        double x = enemyLerpStartX + (endX - enemyLerpStartX) * progress;
        double y = enemyLerpStartY + (endY - enemyLerpStartY) * progress;
        double z = enemyLerpStartZ + (endZ - enemyLerpStartZ) * progress;
        mob.requestTeleport(x, y, z);

        if (enemyMoveTickCounter >= emTicks) {
            arena.moveEntity(currentEnemy, next);
            enemyLerpInitialized = false;
            enemyMovePathIndex++;
        }
    }

    private void handleUseItem(GridPos targetTile) {
        // Variable AP cost based on item type (stack-aware for goat horns)
        ItemStack heldStack = player.getMainHandStack();
        Item heldItem = heldStack.getItem();
        int apCost = ItemUseHandler.getApCost(heldStack);

        if (apRemaining < apCost) {
            sendMessage("§cNeed " + apCost + " AP to use that! (have " + apRemaining + ")");
            return;
        }

        // Pottery Sherd spells — handled directly for access to enemies/combatEffects
        if (PotterySherdSpells.isPotterySherd(heldItem)) {
            handleSherdSpell(targetTile, heldItem, apCost);
            return;
        }

        String result = ItemUseHandler.useItem(player, arena, targetTile);
        if (result == null) {
            sendMessage("§cCan't use that item!");
            return;
        }

        // Don't deduct AP if the item use failed (range check, invalid target, etc.)
        if (result.startsWith("§c")) {
            sendMessage(result);
            sendSync();
            refreshHighlights();
            return;
        }

        apRemaining -= apCost;

        // Sound: item use
        player.getWorld().playSound(null, player.getBlockPos(),
            net.minecraft.sound.SoundEvents.ENTITY_ITEM_PICKUP,
            net.minecraft.sound.SoundCategory.PLAYERS, 0.5f, 1.2f);

        // Handle mounting — player mounts a saddled horse/donkey/camel for bonus speed
        if (result.startsWith(ItemUseHandler.MOUNT_PREFIX)) {
            int entityId = Integer.parseInt(result.substring(ItemUseHandler.MOUNT_PREFIX.length()));
            for (CombatEntity e : enemies) {
                if (e.getEntityId() == entityId) {
                    e.setAlly(true);
                    e.setMounted(true);
                    playerMounted = true;
                    mountMob = e.getMobEntity();
                    if (mountMob != null) {
                        mountMob.setGlowing(false);
                        mountMob.setAiDisabled(true);
                        // Position mount under the player
                        mountMob.requestTeleport(player.getX(), player.getY(), player.getZ());
                        player.startRiding(mountMob);
                    }
                    // Remove from arena grid so it doesn't block tiles
                    arena.removeEntity(e);
                    movePointsRemaining += MOUNT_SPEED_BONUS;
                    sendMessage("§a§l" + e.getDisplayName() + " saddled and mounted! +" + MOUNT_SPEED_BONUS + " Speed!");
                    break;
                }
            }
        }
        // Handle taming — combat-capable mobs become allies
        else if (result.startsWith(ItemUseHandler.TAME_PREFIX)) {
            int entityId = Integer.parseInt(result.substring(ItemUseHandler.TAME_PREFIX.length()));
            for (CombatEntity e : enemies) {
                if (e.getEntityId() == entityId) {
                    e.setAlly(true);
                    if (e.getMobEntity() != null) {
                        e.getMobEntity().setGlowing(false);
                        // Check for horse/wolf armor on the tamed mob
                        ItemStack petArmor = e.getMobEntity().getEquippedStack(net.minecraft.entity.EquipmentSlot.BODY);
                        if (!petArmor.isEmpty()) {
                            Item armorItem = petArmor.getItem();
                            int defBonus = 0;
                            int atkBonus = 0;
                            if (armorItem == Items.IRON_HORSE_ARMOR) { defBonus = 2; }
                            else if (armorItem == Items.GOLDEN_HORSE_ARMOR) { defBonus = 1; atkBonus = 1; }
                            else if (armorItem == Items.DIAMOND_HORSE_ARMOR) { defBonus = 3; atkBonus = 1; }
                            else if (armorItem == Items.WOLF_ARMOR) { defBonus = 2; atkBonus = 1; }
                            else if (armorItem == Items.LEATHER_HORSE_ARMOR) { defBonus = 1; }
                            if (defBonus > 0 || atkBonus > 0) {
                                sendMessage("\u00a77" + e.getDisplayName() + "'s armor: +" + defBonus + " DEF" + (atkBonus > 0 ? ", +" + atkBonus + " ATK" : ""));
                            }
                        }
                    }
                    sendMessage("§a§l" + e.getDisplayName() + " has been tamed! They fight for you now!");
                    break;
                }
            }
            // Check if taming the last enemy triggers victory
            if (enemies.stream().noneMatch(e -> e.isAlive() && !e.isAlly())) {
                handleVictory();
                return;
            }
        }
        // Handle befriending — passive mobs vanish (sent to hub) with hearts
        else if (result.startsWith(ItemUseHandler.BEFRIEND_PREFIX)) {
            String data = result.substring(ItemUseHandler.BEFRIEND_PREFIX.length());
            String[] parts = data.split(":", 2);
            int entityId = Integer.parseInt(parts[0]);
            String entityType = parts.length > 1 ? parts[1] : "unknown";
            for (CombatEntity e : enemies) {
                if (e.getEntityId() == entityId) {
                    // Show hearts particle burst, then remove
                    if (e.getMobEntity() != null) {
                        net.minecraft.server.world.ServerWorld sw = (net.minecraft.server.world.ServerWorld) player.getEntityWorld();
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.HEART,
                            e.getMobEntity().getX(), e.getMobEntity().getY() + 1.5, e.getMobEntity().getZ(),
                            8, 0.5, 0.3, 0.5, 0.1);
                    }
                    killEnemy(e);
                    String mobName = entityType.contains(":") ? entityType.substring(entityType.indexOf(':') + 1) : entityType;
                    sendMessage("§a§l" + mobName.substring(0,1).toUpperCase() + mobName.substring(1) + " befriended! Sent to your hub.");
                    break;
                }
            }
        }
        else {
            sendMessage(result);
        }
        // Tile effects (lava, campfire, honey, banner, scaffold)
        if (result.startsWith(ItemUseHandler.TILE_EFFECT_PREFIX)) {
            String data = result.substring(ItemUseHandler.TILE_EFFECT_PREFIX.length());
            String[] parts = data.split("\\|", 2);
            String tileData = parts[0]; // e.g. "lava:3:5"
            String[] tileInfo = tileData.split(":");
            String effectType = tileInfo[0];
            int tx = Integer.parseInt(tileInfo[1]);
            int tz = Integer.parseInt(tileInfo[2]);
            tileEffects.put(new GridPos(tx, tz), effectType);
            if (parts.length > 1) sendMessage(parts[1]);
        }
        // Ally buff (jukebox music)
        else if (result.startsWith(ItemUseHandler.ALLY_BUFF_PREFIX)) {
            String data = result.substring(ItemUseHandler.ALLY_BUFF_PREFIX.length());
            String[] parts = data.split("\\|", 2);
            if (parts.length > 1) sendMessage(parts[1]);
        }
        // Fishing result — strip prefix, display the catch message
        if (result.startsWith(ItemUseHandler.FISHING_PREFIX)) {
            sendMessage(result.substring(ItemUseHandler.FISHING_PREFIX.length()));
        }
        // Goat horn effect — apply buff/debuff
        if (result.startsWith(ItemUseHandler.HORN_EFFECT_PREFIX)) {
            String data = result.substring(ItemUseHandler.HORN_EFFECT_PREFIX.length());
            String[] parts = data.split("\\|", 2);
            String hornId = parts[0];
            if (parts.length > 1) sendMessage(parts[1]);

            // Play horn sound
            var hornSounds = net.minecraft.sound.SoundEvents.GOAT_HORN_SOUNDS;
            if (!hornSounds.isEmpty()) {
                player.getWorld().playSound(null, player.getBlockPos(),
                    hornSounds.get((int)(Math.random() * hornSounds.size())).value(),
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.5f, 1.0f);
            }

            // Apply the actual effect
            String effectMsg = GoatHornEffects.useHorn(hornId, combatEffects, enemies);
            if (effectMsg != null && !effectMsg.isEmpty()) {
                sendMessage(effectMsg);
            }
        }

        // Check if any enemy died from the item use (egg damage, etc.)
        // Collect dead enemies first to avoid concurrent modification
        List<CombatEntity> deadEnemies = enemies.stream()
            .filter(e -> !e.isAlive() && e.getMobEntity() != null && e.getMobEntity().isAlive())
            .toList();
        for (CombatEntity dead : deadEnemies) {
            sendMessage("§a" + dead.getDisplayName() + " defeated!");
            sendToAllParty(new CombatEventPayload(
                CombatEventPayload.EVENT_DIED, dead.getEntityId(), 0, 0,
                dead.getGridPos().x(), dead.getGridPos().z()
            ));
            killEnemy(dead);
        }

        sendSync();
        refreshHighlights();
    }

    /** Handle pottery sherd spell usage — called from handleUseItem when held item is a sherd. */
    private void handleSherdSpell(GridPos targetTile, Item sherdItem, int apCost) {
        // Self-cast spells don't need a target tile
        GridPos effectiveTarget = PotterySherdSpells.isSelfCast(sherdItem) ? null : targetTile;

        // Validate range and target
        String error = PotterySherdSpells.validateSherd(sherdItem, arena, effectiveTarget, enemies);
        if (error != null) {
            sendMessage(error);
            sendSync();
            refreshHighlights();
            return;
        }

        apRemaining -= apCost;

        // Cast the spell (consumes the sherd, applies damage/effects, spawns particles)
        String result = PotterySherdSpells.useSherd(player, arena, effectiveTarget, enemies, combatEffects);
        if (result == null) {
            sendMessage("§cFailed to cast spell!");
            sendSync();
            refreshHighlights();
            return;
        }

        // Block input while staged spell particles play out
        int animDelay = PotterySherdSpells.getMaxPendingDelay();
        if (animDelay > 0) spellAnimCooldown = animDelay + 2;

        // Process prefix-based results from spells

        // Tile effects (hex_trap from Danger sherd)
        if (result.contains(PotterySherdSpells.HEX_TRAP_PREFIX)) {
            String afterPrefix = result.substring(result.indexOf(PotterySherdSpells.HEX_TRAP_PREFIX) + PotterySherdSpells.HEX_TRAP_PREFIX.length());
            String[] parts = afterPrefix.split("\\|", 2);
            String tileData = parts[0];
            String[] tileInfo = tileData.split(":");
            String effectType = tileInfo[0];
            int tx = Integer.parseInt(tileInfo[1]);
            int tz = Integer.parseInt(tileInfo[2]);
            tileEffects.put(new GridPos(tx, tz), effectType);
            hexTrapTurnsRemaining = 5;
            if (parts.length > 1) sendMessage(parts[1]);
        }
        // AP restoration (Plenty sherd)
        else if (result.contains(PotterySherdSpells.RESTORE_AP_PREFIX)) {
            String afterPrefix = result.substring(result.indexOf(PotterySherdSpells.RESTORE_AP_PREFIX) + PotterySherdSpells.RESTORE_AP_PREFIX.length());
            String[] parts = afterPrefix.split("\\|", 2);
            int restoreAmount = Integer.parseInt(parts[0]);
            apRemaining += restoreAmount;
            if (parts.length > 1) sendMessage(parts[1]);
        }
        // Triple damage next attack (Prize sherd)
        else if (result.contains(PotterySherdSpells.TRIPLE_NEXT_PREFIX)) {
            String afterPrefix = result.substring(result.indexOf(PotterySherdSpells.TRIPLE_NEXT_PREFIX) + PotterySherdSpells.TRIPLE_NEXT_PREFIX.length());
            String[] parts = afterPrefix.split("\\|", 2);
            tripleDamageNextAttack = true;
            if (parts.length > 1) sendMessage(parts[1]);
        }
        // Plain message
        else {
            sendMessage(result);
        }

        // Check deaths from spell damage
        List<CombatEntity> deadEnemies = enemies.stream()
            .filter(e -> !e.isAlive() && e.getMobEntity() != null && e.getMobEntity().isAlive())
            .toList();
        for (CombatEntity dead : deadEnemies) {
            sendMessage("§a" + dead.getDisplayName() + " defeated!");
            sendToAllParty(new CombatEventPayload(
                CombatEventPayload.EVENT_DIED, dead.getEntityId(), 0, 0,
                dead.getGridPos().x(), dead.getGridPos().z()
            ));
            killEnemy(dead);
        }

        // Check win
        if (enemies.stream().noneMatch(e -> e.isAlive() && !e.isAlly())) {
            handleVictory();
            return;
        }

        sendSync();
        refreshHighlights();
    }

    private void handleEndTurn() {
        // Kill streak: reset if no kills this turn
        if (!killedThisTurn && killStreak > 0) {
            killStreak = 0;
            sendSync();
        }
        killedThisTurn = false; // reset for next turn

        // Auto-brace shield if equipped in offhand
        if (PlayerCombatStats.hasShield(player)) {
            shieldBraced = true;
            sendMessage("§9§lShield braced! §r§7(+" + (SHIELD_PASSIVE_DEFENSE + SHIELD_BRACE_DEFENSE) + " defense this enemy turn)");
        }
        // Tick boss warning telegraphs
        tickBossWarnings();

        startEnemyTurn();
    }

    private void startEnemyTurn() {
        // Sound: enemy turn start
        if (player != null) {
            player.getWorld().playSound(null, player.getBlockPos(),
                net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                net.minecraft.sound.SoundCategory.PLAYERS, 0.6f, 0.5f);
        }
        clearHighlights();
        phase = CombatPhase.ENEMY_TURN;
        enemyTurnIndex = 0;
        enemyTurnDelay = 10;
        enemyTurnState = EnemyTurnState.DECIDING;
        pendingAction = null;
        currentEnemy = null;
        sendSync();
    }

    public void tick() {
        if (!active) return;

        // Safety: if player disconnected or is removed, end combat immediately
        if (player == null || player.isRemoved() || player.isDisconnected()) {
            endCombat();
            return;
        }

        tickCounter++;

        // Boss ambient particles (runs every tick, per-boss throttling inside)
        tickBossAmbientParticles();

        // Tick pending attack delay (damage synced to animation impact)
        if (pendingAttackDelay > 0) {
            pendingAttackDelay--;
            if (pendingAttackDelay == 0 && pendingAttackAction != null) {
                pendingAttackAction.run();
                pendingAttackAction = null;
            }
        }

        // Tick spell animation cooldown
        if (spellAnimCooldown > 0) spellAnimCooldown--;

        // Tick delayed spell visual effects (particles/sounds staged across ticks)
        if (!PotterySherdSpells.PENDING_EFFECTS.isEmpty()) {
            var iter = PotterySherdSpells.PENDING_EFFECTS.iterator();
            while (iter.hasNext()) {
                var effect = iter.next();
                if (--effect.ticksRemaining <= 0) {
                    effect.effect.run();
                    iter.remove();
                }
            }
        }

        // Detect externally killed/removed mobs and suppress any residual fire
        if (enemies != null) {
            for (CombatEntity e : enemies) {
                if (!e.isAlive()) continue;
                MobEntity mob = e.getMobEntity();
                if (mob == null) continue;

                // If the MobEntity was removed by vanilla mechanics, try to re-spawn it
                // instead of killing the CombatEntity (the combat stats are still valid)
                if (!mob.isAlive() || mob.isRemoved()) {
                    CrafticsMod.LOGGER.warn("Enemy '{}' (id={}) removed externally -- reason={}, health={}, pos=({},{},{})",
                        e.getDisplayName(), e.getEntityId(),
                        mob.getRemovalReason(), mob.getHealth(),
                        mob.getX(), mob.getY(), mob.getZ());
                    // Try to re-create the visual mob entity
                    BlockPos respawnPos = arena.gridToBlockPos(e.getGridPos());
                    ServerWorld tickWorld = (ServerWorld) player.getEntityWorld();
                    EntityType<?> reType = Registries.ENTITY_TYPE.get(Identifier.of(e.getEntityTypeId()));
                    var newMob = reType.create(tickWorld, null, respawnPos, SpawnReason.COMMAND, false, false);
                    if (newMob instanceof MobEntity replacementMob) {
                        int size = CombatEntity.getDefaultSizeStatic(e.getEntityTypeId());
                        double offset = size > 1 ? 1.0 : 0.5;
                        replacementMob.refreshPositionAndAngles(
                            respawnPos.getX() + offset, respawnPos.getY(), respawnPos.getZ() + offset, 0, 0);
                        replacementMob.setInvulnerable(true);
                        replacementMob.setAiDisabled(true);
                        replacementMob.setNoGravity(true);
                        replacementMob.setPersistent();
                        replacementMob.setHealth(replacementMob.getMaxHealth());
                        replacementMob.addCommandTag("craftics_arena");
                        tickWorld.spawnEntity(replacementMob);
                        e.setMobEntity(replacementMob);
                        CrafticsMod.LOGGER.info("Re-spawned '{}' successfully", e.getDisplayName());
                    } else {
                        // Complete failure — remove from combat
                        CrafticsMod.LOGGER.error("Failed to re-spawn '{}' — removing from combat", e.getDisplayName());
                        e.takeDamage(9999);
                        arena.removeEntity(e);
                        sendToAllParty(new CombatEventPayload(
                            CombatEventPayload.EVENT_DIED, e.getEntityId(), 0, 0, 0, 0
                        ));
                        if (enemies.stream().noneMatch(en -> en.isAlive() && !en.isAlly())) {
                            handleVictory();
                            return;
                        }
                    }
                    continue;
                }

                // Safety net: clear fire ticks if a mob somehow catches fire
                if (mob.isOnFire()) {
                    mob.setFireTicks(0);
                }

                // Creeper fuse visual: pulse glow + smoke particles (NO ignite — that causes real explosion)
                if (e.getFuseTimer() > 0 && e.getEntityTypeId().equals("minecraft:creeper")) {
                    // Pulse: toggle glowing every 4 ticks for visual warning
                    mob.setGlowing(e.getFuseTimer() % 8 < 4);
                    // Pulse smoke particles every few ticks
                    ServerWorld creeperWorld = (ServerWorld) player.getEntityWorld();
                    creeperWorld.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
                        mob.getX(), mob.getY() + 1.0, mob.getZ(),
                        2, 0.2, 0.3, 0.2, 0.01);
                }

                // Snap idle mobs to their grid position to prevent entity drift
                // Skip the mob currently being animated
                if (e != currentEnemy || enemyTurnState == EnemyTurnState.DONE
                        || enemyTurnState == EnemyTurnState.DECIDING) {
                    BlockPos gridBlock = arena.gridToBlockPos(e.getGridPos());
                    double sizeOffset = e.getSize() > 1 ? e.getSize() / 2.0 : 0.5;
                    double targetX = gridBlock.getX() + sizeOffset;
                    double targetZ = gridBlock.getZ() + sizeOffset;
                    double driftX = Math.abs(mob.getX() - targetX);
                    double driftZ = Math.abs(mob.getZ() - targetZ);
                    if (driftX > 0.1 || driftZ > 0.1) {
                        mob.requestTeleport(targetX, gridBlock.getY(), targetZ);
                    }
                }
            }
        }

        // Refresh highlights when player changes hotbar slot
        if (phase == CombatPhase.PLAYER_TURN && player != null) {
            var currentItem = player.getMainHandStack().getItem();
            if (currentItem != lastHeldItem) {
                lastHeldItem = currentItem;
                refreshHighlights();
            }
        }

        // Tick dying mobs (Pehkui shrink animation then discard)
        for (int i = dyingMobs.size() - 1; i >= 0; i--) {
            int remaining = dyingMobTimers.get(i) - 1;
            if (remaining <= 0) {
                dyingMobs.get(i).discard();
                dyingMobs.remove(i);
                dyingMobTimers.remove(i);
            } else {
                dyingMobTimers.set(i, remaining);
            }
        }

        switch (phase) {
            case ANIMATING -> tickAnimation();
            case REACTING -> tickReacting();
            case ENEMY_TURN -> tickEnemyTurn();
            default -> {}
        }
    }

    private void tickAnimation() {
        if (movePathIndex >= movePath.size()) {
            // Movement complete
            int tilesMoved = movePath.size();
            GridPos finalPos = arena.getPlayerGridPos();
            movePath = null;
            lerpInitialized = false;

            // Notify bosses of player movement (Warden vibration sense)
            notifyBossesPlayerMoved(finalPos, tilesMoved);

            phase = CombatPhase.PLAYER_TURN;
            sendSync();
            refreshHighlights();
            return;
        }

        // Initialize lerp for this tile transition
        if (!lerpInitialized) {
            lerpInitialized = true;
            moveTickCounter = 0;
            lerpStartX = player.getX();
            lerpStartY = player.getY();
            lerpStartZ = player.getZ();

            // Face the direction of movement
            GridPos next = movePath.get(movePathIndex);
            GridPos prev = movePathIndex == 0 ? arena.getPlayerGridPos() : movePath.get(movePathIndex - 1);
            int dx = next.x() - prev.x();
            int dz = next.z() - prev.z();
            playerMoveYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        }

        moveTickCounter++;
        GridPos next = movePath.get(movePathIndex);
        BlockPos endBlock = arena.gridToBlockPos(next);
        double endX = endBlock.getX() + 0.5;
        double endY = endBlock.getY();
        double endZ = endBlock.getZ() + 0.5;

        float progress = Math.min(1.0f, (float) moveTickCounter / MOVE_TICKS);

        // Smooth lerp with walking animation
        double x = lerpStartX + (endX - lerpStartX) * progress;
        double y = lerpStartY + (endY - lerpStartY) * progress;
        double z = lerpStartZ + (endZ - lerpStartZ) * progress;
        player.setYaw(playerMoveYaw);
        player.setHeadYaw(playerMoveYaw);
        player.setOnGround(true);

        // Smooth movement: set position directly and use velocity for client interpolation
        // Save prev position so client-side limb animator sees movement delta
        player.prevX = player.getX();
        player.prevY = player.getY();
        player.prevZ = player.getZ();
        player.setPosition(x, y, z);

        // Send velocity so client sees smooth movement + drives vanilla limb animation
        double dx = endX - lerpStartX;
        double dz = endZ - lerpStartZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0) {
            double speed = 0.12;
            player.setVelocity(dx / len * speed, 0, dz / len * speed);
            player.velocityDirty = true;
        }

        // Force position sync to client every tick during animation
        player.networkHandler.requestTeleport(x, y, z, playerMoveYaw, 0f);

        // Keep mount mob under the player during movement
        if (playerMounted && mountMob != null) {
            mountMob.requestTeleport(x, y, z);
        }

        // Tile reached — advance to next
        if (moveTickCounter >= MOVE_TICKS) {
            arena.setPlayerGridPos(next);
            // Sound: footstep
            if (player != null) {
                player.getWorld().playSound(null, player.getBlockPos(),
                    net.minecraft.sound.SoundEvents.BLOCK_STONE_STEP,
                    net.minecraft.sound.SoundCategory.PLAYERS, 0.3f, 1.0f);
            }
            lerpInitialized = false;
            movePathIndex++;
        }
    }

    private void tickEnemyTurn() {
        if (enemyTurnDelay > 0) { enemyTurnDelay--; return; }

        switch (enemyTurnState) {
            case DECIDING -> tickEnemyDeciding();
            case MOVING -> tickEnemyMoving();
            case ATTACKING -> tickEnemyAttacking();
            case DONE -> tickEnemyDone();
        }
    }

    private void tickEnemyDeciding() {
        // Skip dead enemies
        while (enemyTurnIndex < enemies.size() && !enemies.get(enemyTurnIndex).isAlive()) {
            enemyTurnIndex++;
        }

        if (enemyTurnIndex >= enemies.size()) {
            // All enemies done — new player turn
            for (CombatEntity e : enemies) e.setDamagedSinceLastTurn(false);

            // Tick enemy poison DoT
            for (CombatEntity e : enemies) {
                if (!e.isAlive() || e.getPoisonTurns() <= 0) continue;
                int poisonDmg = e.takeDamage(1 + e.getPoisonAmplifier());
                e.setPoisonTurns(e.getPoisonTurns() - 1);
                sendMessage("§2" + e.getDisplayName() + " took " + poisonDmg + " poison damage.");
                if (e.getMobEntity() != null) {
                    ((ServerWorld) player.getEntityWorld()).spawnParticles(
                        net.minecraft.particle.ParticleTypes.ITEM_SLIME,
                        e.getMobEntity().getX(), e.getMobEntity().getY() + 1.0, e.getMobEntity().getZ(),
                        8, 0.3, 0.3, 0.3, 0.05);
                }
                checkAndHandleDeath(e);
            }

            // Check if poison killed the last enemy
            if (enemies.stream().noneMatch(e -> e.isAlive() && !e.isAlly())) {
                handleVictory();
                return;
            }

            // Tick enemy burning DoT
            for (CombatEntity e : enemies) {
                if (!e.isAlive() || e.getBurningTurns() <= 0) continue;
                int burnDmg = e.takeDamage(e.getBurningDamage());
                e.setBurningTurns(e.getBurningTurns() - 1);
                sendMessage("§6" + e.getDisplayName() + " burned for " + burnDmg + ".");
                if (e.getMobEntity() != null) {
                    ((ServerWorld) player.getEntityWorld()).spawnParticles(
                        net.minecraft.particle.ParticleTypes.FLAME,
                        e.getMobEntity().getX(), e.getMobEntity().getY() + 1.0, e.getMobEntity().getZ(),
                        10, 0.3, 0.5, 0.3, 0.02);
                }
                if (e.getBurningTurns() <= 0) {
                    e.setBurningDamage(0);
                }
                checkAndHandleDeath(e);
            }

            // Check if burning killed the last enemy
            if (enemies.stream().noneMatch(e -> e.isAlive() && !e.isAlly())) {
                handleVictory();
                return;
            }

            // Tick enemy defense penalty duration
            for (CombatEntity e : enemies) {
                if (!e.isAlive() || e.getDefensePenaltyTurns() <= 0) continue;
                e.setDefensePenaltyTurns(e.getDefensePenaltyTurns() - 1);
                if (e.getDefensePenaltyTurns() <= 0) {
                    e.setDefensePenalty(0);
                }
            }

            // Tick hex trap duration
            if (hexTrapTurnsRemaining > 0) {
                hexTrapTurnsRemaining--;
                if (hexTrapTurnsRemaining <= 0) {
                    tileEffects.entrySet().removeIf(e -> "hex_trap".equals(e.getValue()));
                }
            }

            // Tick combat effects (decrement turn counters, remove expired)
            String expired = combatEffects.tickTurn();
            if (expired != null) {
                // Remove vanilla status effects that correspond to expired combat effects
                for (CombatEffects.EffectType expType : combatEffects.getLastExpired()) {
                    var mcEffect = mapCombatToVanillaEffect(expType);
                    if (mcEffect != null && player != null) {
                        player.removeStatusEffect(mcEffect);
                    }
                }
            }

            // Apply per-turn effects (regen, poison, wither, burning)
            int hpChange = combatEffects.applyPerTurnEffects();
            if (hpChange != 0 && player != null) {
                float newHp = Math.max(0, Math.min(player.getMaxHealth(), player.getHealth() + hpChange));
                player.setHealth(newHp);
                if (hpChange > 0) {
                    sendMessage("§a♥ Regeneration healed " + hpChange + " HP");
                } else {
                    StringBuilder dmgSources = new StringBuilder();
                    if (combatEffects.hasEffect(CombatEffects.EffectType.POISON)) dmgSources.append("Poison ");
                    if (combatEffects.hasEffect(CombatEffects.EffectType.WITHER)) dmgSources.append("Wither ");
                    if (combatEffects.hasEffect(CombatEffects.EffectType.BURNING)) dmgSources.append("Fire ");
                    sendMessage("§c☠ " + dmgSources.toString().trim() + " dealt " + (-hpChange) + " damage");
                }
                if (getPlayerHp() <= 0) { handleGameOver(); return; }
            }

            // Environmental hazards — check tile under player
            GridPos playerPos = arena.getPlayerGridPos();
            GridTile playerTile = arena.getTile(playerPos);
            if (playerTile != null) {
                net.minecraft.block.Block tileBlock = playerTile.getBlockType();
                if (tileBlock == Blocks.MAGMA_BLOCK && !combatEffects.hasFireResistance()) {
                    player.setHealth(Math.max(0, player.getHealth() - 1));
                    sendMessage("§c🔥 Magma burns you for 1 damage!");
                    if (getPlayerHp() <= 0) { handleGameOver(); return; }
                } else if (tileBlock == Blocks.SOUL_SAND || tileBlock == Blocks.SOUL_SOIL) {
                    movePointsRemaining = Math.max(1, movePointsRemaining - 1);
                    sendMessage("§7☁ Soul sand slows your movement (-1 Speed)");
                }
            }

            // Process tile effects for enemies
            for (var entry : tileEffects.entrySet()) {
                GridPos tPos = entry.getKey();
                String effect = entry.getValue();
                // Lava: damage enemies standing on it
                if ("lava".equals(effect)) {
                    CombatEntity occupant = arena.getOccupant(tPos);
                    if (occupant != null && occupant.isAlive() && !occupant.isAlly()) {
                        occupant.takeDamage(3);
                        if (occupant.getMobEntity() != null) occupant.getMobEntity().setFireTicks(60);
                        sendMessage("§6" + occupant.getDisplayName() + " burns in lava for 3 damage!");
                    }
                }
                // Campfire: heal player if adjacent
                if ("campfire".equals(effect)) {
                    GridPos pPos = arena.getPlayerGridPos();
                    int d = Math.abs(pPos.x() - tPos.x()) + Math.abs(pPos.z() - tPos.z());
                    if (d <= 1) {
                        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 1));
                        sendMessage("§6Campfire heals 1 HP.");
                    }
                }
                // Poison cloud: damage enemies standing on it + ongoing particles
                if ("poison_cloud".equals(effect)) {
                    // Spawn lingering cloud particles each turn
                    ProjectileSpawner.spawnLingeringCloud(
                        (ServerWorld) player.getEntityWorld(), arena.gridToBlockPos(tPos));
                    // Damage ALL enemies in 3x3 area around cloud center
                    java.util.Set<CombatEntity> cloudHits = new java.util.HashSet<>();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            GridPos checkPos = new GridPos(tPos.x() + dx, tPos.z() + dz);
                            CombatEntity occ = arena.getOccupant(checkPos);
                            if (occ != null && occ.isAlive() && !occ.isAlly()) cloudHits.add(occ);
                        }
                    }
                    for (CombatEntity hit : cloudHits) {
                        hit.takeDamage(2);
                        sendMessage("§5" + hit.getDisplayName() + " chokes in poison cloud for 2 damage!");
                        checkAndHandleDeath(hit);
                    }
                    // Also damage player if standing in the cloud
                    if (arena.getPlayerGridPos().manhattanDistance(tPos) <= 1) {
                        player.setHealth(Math.max(0, player.getHealth() - 1));
                        sendMessage("§5You breathe in the poison cloud! -1 HP");
                        if (getPlayerHp() <= 0) { handleGameOver(); return; }
                    }
                }
                // Cactus: damage enemies standing on or adjacent to it
                if ("cactus".equals(effect)) {
                    for (CombatEntity e : enemies) {
                        if (!e.isAlive() || e.isAlly()) continue;
                        int d = Math.abs(e.getGridPos().x() - tPos.x()) + Math.abs(e.getGridPos().z() - tPos.z());
                        if (d <= 1) {
                            e.takeDamage(1);
                            sendMessage("§2" + e.getDisplayName() + " is pricked by cactus for 1 damage!");
                        }
                    }
                }
            }
            // Lightning rod: strikes this turn then removes itself
            tileEffects.entrySet().removeIf(entry -> {
                if ("lightning".equals(entry.getValue())) {
                    GridPos tPos = entry.getKey();
                    int hit = 0;
                    for (CombatEntity e : enemies) {
                        if (!e.isAlive() || e.isAlly()) continue;
                        int d = Math.abs(e.getGridPos().x() - tPos.x()) + Math.abs(e.getGridPos().z() - tPos.z());
                        if (d <= 1) {
                            e.takeDamage(4);
                            hit++;
                        }
                    }
                    if (hit > 0) sendMessage("§e⚡ Lightning strikes! " + hit + " enemies hit for 4 damage!");
                    else sendMessage("§eLightning rod fizzles — no enemies nearby.");
                    return true; // remove after striking
                }
                return false;
            });

            turnNumber++;
            // Tick temporary terrain tiles (boss fire pools, obstacles, etc.)
            tickTemporaryTerrain();
            // Clear shield brace from previous turn
            shieldBraced = false;
            if (testRange) {
                // Infinite AP and movement in test range
                apRemaining = 999;
                movePointsRemaining = 999;
            } else {
                // Read base stats from progression
                PlayerProgression turnProg = PlayerProgression.get((ServerWorld) player.getEntityWorld());
                PlayerProgression.PlayerStats turnStats = turnProg.getStats(player);
                apRemaining = turnStats.getEffective(PlayerProgression.Stat.AP);
                apRemaining += PlayerCombatStats.getSetApBonus(player);
                movePointsRemaining = turnStats.getEffective(PlayerProgression.Stat.SPEED)
                    + combatEffects.getSpeedBonus() - combatEffects.getSpeedPenalty()
                    + (playerMounted ? MOUNT_SPEED_BONUS : 0);
                movePointsRemaining += PlayerCombatStats.getSetSpeedBonus(player);
            }
            phase = CombatPhase.PLAYER_TURN;
            // Sound: player turn start
            player.getWorld().playSound(null, player.getBlockPos(),
                net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
                net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 1.2f);
            sendMessage("§aAP: " + apRemaining + " | SPD: " + movePointsRemaining);
            sendSync();
            refreshHighlights();
            sendToAllParty(new CombatEventPayload(
                CombatEventPayload.EVENT_PHASE_CHANGED, 0,
                CombatPhase.PLAYER_TURN.ordinal(), apRemaining, 0, 0
            ));
            return;
        }

        currentEnemy = enemies.get(enemyTurnIndex);

        // Stunned entities skip their turn
        if (currentEnemy.isStunned()) {
            sendMessage("§7" + currentEnemy.getDisplayName() + " is stunned!");
            currentEnemy.setStunned(false);
            enemyTurnState = EnemyTurnState.DONE;
            enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            return;
        }

        // === ALLY PET AI ===
        // Allies attack enemies instead of the player, and retreat at low HP
        if (currentEnemy.isAlly()) {
            handleAllyTurn(currentEnemy);
            return;
        }

        // Update player held item context for AI that needs it (e.g., cat)
        arena.setPlayerHeldItemId(net.minecraft.registry.Registries.ITEM
            .getId(player.getMainHandStack().getItem()).toString());

        // Resolve any pending boss warnings from last turn before the boss acts again
        if (currentEnemy.isBoss()) {
            // Clear physical warning blocks first
            clearWarningBlocks();
            resolvePendingBossWarnings(currentEnemy);
        }

        EnemyAI ai = AIRegistry.get(currentEnemy.getAiKey());
        if (currentEnemy.isBoss()) {
            CrafticsMod.LOGGER.info("[BOSS DEBUG] Boss '{}' aiKey='{}' resolved AI class={}",
                currentEnemy.getDisplayName(), currentEnemy.getAiKey(), ai.getClass().getSimpleName());
        }
        pendingAction = ai.decideAction(currentEnemy, arena, arena.getPlayerGridPos());

        switch (pendingAction) {
            case EnemyAction.Move move -> startEnemyMove(move.path());
            case EnemyAction.Flee flee -> startEnemyMove(flee.path());
            case EnemyAction.MoveAndAttack maa -> startEnemyMove(maa.path());
            case EnemyAction.Attack atk -> {
                enemyTurnState = EnemyTurnState.ATTACKING;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }
            case EnemyAction.Teleport tp -> {
                // Instant move — no lerp animation
                sendMessage("§5" + currentEnemy.getDisplayName() + " teleports!");
                MobEntity mob = currentEnemy.getMobEntity();
                if (mob != null) {
                    double wx = arena.getOrigin().getX() + tp.target().x() + 0.5;
                    double wz = arena.getOrigin().getZ() + tp.target().z() + 0.5;
                    mob.requestTeleport(wx, arena.getOrigin().getY() + 1.0, wz);
                }
                arena.moveEntity(currentEnemy, tp.target());
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }
            case EnemyAction.TeleportAndAttack tpa -> {
                // Teleport then attack
                sendMessage("§5" + currentEnemy.getDisplayName() + " teleports behind you!");
                MobEntity mob = currentEnemy.getMobEntity();
                if (mob != null) {
                    double wx = arena.getOrigin().getX() + tpa.target().x() + 0.5;
                    double wz = arena.getOrigin().getZ() + tpa.target().z() + 0.5;
                    mob.requestTeleport(wx, arena.getOrigin().getY() + 1.0, wz);
                }
                arena.moveEntity(currentEnemy, tpa.target());
                // Proceed to attack phase
                pendingAction = new EnemyAction.Attack(tpa.damage());
                enemyTurnState = EnemyTurnState.ATTACKING;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }
            case EnemyAction.Pounce pounce -> {
                // Instant jump to landing position, then attack
                sendMessage("§6" + currentEnemy.getDisplayName() + " pounces!");
                MobEntity mob = currentEnemy.getMobEntity();
                if (mob != null) {
                    double wx = arena.getOrigin().getX() + pounce.landingPos().x() + 0.5;
                    double wz = arena.getOrigin().getZ() + pounce.landingPos().z() + 0.5;
                    mob.requestTeleport(wx, arena.getOrigin().getY() + 1.0, wz);
                }
                arena.moveEntity(currentEnemy, pounce.landingPos());
                pendingAction = new EnemyAction.Attack(pounce.damage());
                enemyTurnState = EnemyTurnState.ATTACKING;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }
            case EnemyAction.Explode explode -> {
                // AoE explosion — damage player AND other mobs within radius
                sendMessage("§c§l" + currentEnemy.getDisplayName() + " EXPLODES!");
                // Explosion particles!
                ServerWorld explodeWorld = (ServerWorld) player.getEntityWorld();
                BlockPos explodeBlock = arena.gridToBlockPos(currentEnemy.getGridPos());
                explodeWorld.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION,
                    explodeBlock.getX() + 0.5, explodeBlock.getY() + 1.0, explodeBlock.getZ() + 0.5,
                    3, 1.0, 0.5, 1.0, 0.0);
                explodeWorld.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                    explodeBlock.getX() + 0.5, explodeBlock.getY() + 0.5, explodeBlock.getZ() + 0.5,
                    20, 1.5, 0.8, 1.5, 0.05);
                explodeWorld.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                    explodeBlock.getX() + 0.5, explodeBlock.getY() + 1.0, explodeBlock.getZ() + 0.5,
                    15, 1.0, 1.0, 1.0, 0.03);
                GridPos selfPos = currentEnemy.getGridPos();
                GridPos playerGridPos = arena.getPlayerGridPos();
                int dist = selfPos.manhattanDistance(playerGridPos);
                if (dist <= explode.radius()) {
                    int playerDefense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus();
                    int actual = Math.max(1, explode.damage() - playerDefense);
                    player.setHealth(Math.max(0, player.getHealth() - actual));
                    sendMessage("§c  Explosion hits you for " + actual + " damage! (HP: " + getPlayerHp() + ")");
                    if (getPlayerHp() <= 0) { handleGameOver(); return; }
                }
                // Damage other mobs caught in the blast (configurable friendly fire)
                List<CombatEntity> blastTargets = new ArrayList<>();
                if (CrafticsMod.CONFIG.friendlyFireEnabled()) for (CombatEntity other : enemies) {
                    if (other == currentEnemy || !other.isAlive()) continue;
                    if (selfPos.manhattanDistance(other.getGridPos()) <= explode.radius()) {
                        blastTargets.add(other);
                    }
                }
                for (CombatEntity target : blastTargets) {
                    int dealt = target.takeDamage(explode.damage());
                    sendMessage("§6  Blast hits " + target.getDisplayName() + " for " + dealt + "!");
                    checkAndHandleDeath(target);
                }
                // Creeper dies from explosion
                currentEnemy.takeDamage(9999);
                killEnemy(currentEnemy);
                sendSync();
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = 8;
            }
            case EnemyAction.RangedAttack ra -> {
                // Ranged attack — different messages by effect type
                String attackVerb = switch (ra.effectName() != null ? ra.effectName() : "") {
                    case "fireball" -> " hurls a fireball!";
                    case "fire" -> " shoots a fire charge!";
                    case "shulker_bullet" -> " fires a shulker bullet!";
                    case "arrow" -> " shoots an arrow!";
                    case "frost_arrow" -> " shoots a frost arrow!";
                    case "crossbow" -> " fires a crossbow bolt!";
                    case "trident" -> " hurls a trident!";
                    case "sonic_boom" -> " unleashes a sonic boom!";
                    case "llama_spit" -> " spits at you!";
                    default -> " throws a potion!";
                };
                sendMessage("§d" + currentEnemy.getDisplayName() + attackVerb);

                // Spawn visible projectile from enemy to player
                BlockPos enemyBlock = arena.gridToBlockPos(currentEnemy.getGridPos());
                BlockPos playerBlock = arena.gridToBlockPos(arena.getPlayerGridPos());
                ProjectileSpawner.spawnProjectile(
                    (ServerWorld) player.getEntityWorld(), enemyBlock, playerBlock,
                    ra.effectName());

                int playerDefense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus();
                int actual = Math.max(1, ra.damage() - playerDefense);
                player.setHealth(Math.max(0, player.getHealth() - actual));
                sendMessage("§c  Hit for " + actual + " damage! (HP: " + getPlayerHp() + ")");
                applyEnemyHitEffect(currentEnemy.getEntityTypeId());
                sendSync();
                if (getPlayerHp() <= 0) { handleGameOver(); return; }
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }
            case EnemyAction.Swoop swoop -> {
                // Phantom swoop — fly along path, damage player if in the way
                sendMessage("§8" + currentEnemy.getDisplayName() + " swoops!");
                boolean hitPlayer = false;
                GridPos playerGridPos = arena.getPlayerGridPos();
                GridPos finalPos = currentEnemy.getGridPos();
                for (GridPos pos : swoop.path()) {
                    if (pos.equals(playerGridPos)) {
                        hitPlayer = true;
                    }
                    finalPos = pos;
                }
                // Move phantom to end of swoop path
                MobEntity mob = currentEnemy.getMobEntity();
                if (mob != null && arena.isInBounds(finalPos) && !arena.isEnemyOccupied(finalPos)) {
                    double wx = arena.getOrigin().getX() + finalPos.x() + 0.5;
                    double wz = arena.getOrigin().getZ() + finalPos.z() + 0.5;
                    mob.requestTeleport(wx, arena.getOrigin().getY() + 1.0, wz);
                    arena.moveEntity(currentEnemy, finalPos);
                }
                if (hitPlayer) {
                    int playerDefense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus();
                    int actual = Math.max(1, swoop.damage() - playerDefense);
                    player.setHealth(Math.max(0, player.getHealth() - actual));
                    sendMessage("§c  Swoops through you for " + actual + " damage! (HP: " + getPlayerHp() + ")");
                    sendSync();
                    if (getPlayerHp() <= 0) { handleGameOver(); return; }
                }
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }
            case EnemyAction.StartFuse fuse -> {
                sendMessage("§e" + currentEnemy.getDisplayName() + " is hissing... §c§lRUN!");
                // Creeper glows white while priming
                if (currentEnemy.getMobEntity() != null) {
                    currentEnemy.getMobEntity().setGlowing(true);
                }
                // Smoke particles around the creeper
                ServerWorld fuseWorld = (ServerWorld) player.getEntityWorld();
                BlockPos fuseBlock = arena.gridToBlockPos(currentEnemy.getGridPos());
                fuseWorld.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                    fuseBlock.getX() + 0.5, fuseBlock.getY() + 1.0, fuseBlock.getZ() + 0.5,
                    10, 0.3, 0.5, 0.3, 0.02);
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }
            case EnemyAction.AttackMob am -> {
                // Mob attacks another mob (predator hunting prey)
                CombatEntity target = findEnemyById(am.targetEntityId());
                if (target != null && target.isAlive()) {
                    int dealt = target.takeDamage(am.damage());
                    sendMessage("§6" + currentEnemy.getDisplayName() + " attacks " + target.getDisplayName() + " for " + dealt + "!");
                    checkAndHandleDeath(target);
                } else {
                    sendMessage("§7" + currentEnemy.getDisplayName() + " looks around...");
                }
                sendSync();
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }
            case EnemyAction.MoveAndAttackMob maam -> startEnemyMove(maam.path());
            case EnemyAction.AttackWithKnockback akb -> {
                enemyTurnState = EnemyTurnState.ATTACKING;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }
            case EnemyAction.MoveAndAttackWithKnockback maakb -> startEnemyMove(maakb.path());
            case EnemyAction.Idle idle -> {
                sendMessage("§7" + currentEnemy.getDisplayName() + " waits...");
                // If boss has a pending warning, render its telegraph for the player's turn.
                if (currentEnemy.isBoss()) {
                    EnemyAI bossAi = AIRegistry.get(currentEnemy.getAiKey());
                    if (bossAi instanceof com.crackedgames.craftics.combat.ai.boss.BossAI ba
                            && ba.getPendingWarning() != null) {
                        renderBossWarning(ba.getPendingWarning());
                    }
                }
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }

            // === Boss action types ===

            case EnemyAction.SummonMinions sm -> {
                sendMessage("§5" + currentEnemy.getDisplayName() + " summons reinforcements!");
                spawnBossMinions(sm);
                sendSync();
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay() + 4;
            }
            case EnemyAction.AreaAttack aa -> {
                sendMessage("§c" + currentEnemy.getDisplayName() + " unleashes " +
                    (aa.effectName() != null ? aa.effectName().replace('_', ' ') : "a powerful attack") + "!");
                resolveAreaAttack(aa);
                sendSync();
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }
            case EnemyAction.CreateTerrain ct -> {
                resolveCreateTerrain(ct);
                sendSync();
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }
            case EnemyAction.LineAttack la -> {
                sendMessage("§c" + currentEnemy.getDisplayName() + " attacks in a line!");
                resolveLineAttack(la);
                sendSync();
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }
            case EnemyAction.ModifySelf ms -> {
                resolveModifySelf(currentEnemy, ms);
                sendSync();
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }
            case EnemyAction.ForcedMovement fm -> {
                resolveForcedMovement(fm);
                sendSync();
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }
            case EnemyAction.BossAbility ba -> {
                // Warning telegraph — store and resolve next turn, or resolve immediately if no warning tiles
                if (ba.warningTiles() != null && !ba.warningTiles().isEmpty()) {
                    sendMessage("§e" + currentEnemy.getDisplayName() + " prepares " +
                        ba.abilityName().replace('_', ' ') + "!");
                    // Store pending warning for resolution next turn
                    pendingBossWarnings.add(new PendingBossWarning(currentEnemy, ba));
                    // Place physical warning blocks on the grid
                    placeWarningBlocks(ba.warningTiles());
                    // Clear BossAI's internal warning to prevent double-fire
                    EnemyAI bossAi = AIRegistry.get(currentEnemy.getAiKey());
                    if (bossAi instanceof com.crackedgames.craftics.combat.ai.boss.BossAI bai) {
                        bai.clearPendingWarning();
                    }
                } else {
                    // Immediate resolve — no telegraph needed
                    sendMessage("§c" + currentEnemy.getDisplayName() + " uses " +
                        ba.abilityName().replace('_', ' ') + "!");
                    dispatchBossSubAction(ba.resolvedAction());
                }
                sendSync();
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }
            case EnemyAction.CompositeAction ca -> {
                for (EnemyAction subAction : ca.actions()) {
                    dispatchBossSubAction(subAction);
                }
                sendSync();
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay() + 2;
            }

            default -> {
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = Math.max(1, CrafticsMod.CONFIG.enemyTurnDelay() / 2);
            }
        }
    }

    /**
     * Handle an ally pet's turn. Allies attack nearby enemies or retreat if low HP.
     */
    private void handleAllyTurn(CombatEntity ally) {
        GridPos allyPos = ally.getGridPos();
        boolean lowHp = ally.getCurrentHp() <= 4; // 2 hearts or less = retreat

        if (lowHp) {
            // RETREAT: move away from nearest enemy
            CombatEntity nearestEnemy = null;
            int nearestDist = Integer.MAX_VALUE;
            for (CombatEntity e : enemies) {
                if (!e.isAlive() || e.isAlly()) continue;
                int d = Math.abs(e.getGridPos().x() - allyPos.x()) + Math.abs(e.getGridPos().z() - allyPos.z());
                if (d < nearestDist) {
                    nearestDist = d;
                    nearestEnemy = e;
                }
            }
            if (nearestEnemy != null) {
                // Move away from nearest enemy
                int dx = Integer.signum(allyPos.x() - nearestEnemy.getGridPos().x());
                int dz = Integer.signum(allyPos.z() - nearestEnemy.getGridPos().z());
                GridPos retreatTarget = new GridPos(allyPos.x() + dx * 2, allyPos.z() + dz * 2);
                List<GridPos> path = Pathfinding.findPath(arena, allyPos, retreatTarget, ally.getMoveSpeed(), false);
                if (path != null && !path.isEmpty()) {
                    sendMessage("§e" + ally.getDisplayName() + " retreats! (low HP)");
                    startEnemyMove(path);
                    return;
                }
            }
            sendMessage("§e" + ally.getDisplayName() + " cowers...");
            enemyTurnState = EnemyTurnState.DONE;
            enemyTurnDelay = Math.max(1, CrafticsMod.CONFIG.enemyTurnDelay() / 2);
            return;
        }

        // ATTACK: find nearest non-ally enemy and move toward / attack it
        CombatEntity target = null;
        int targetDist = Integer.MAX_VALUE;
        for (CombatEntity e : enemies) {
            if (!e.isAlive() || e.isAlly()) continue;
            int d = Math.abs(e.getGridPos().x() - allyPos.x()) + Math.abs(e.getGridPos().z() - allyPos.z());
            if (d < targetDist) {
                targetDist = d;
                target = e;
            }
        }

        if (target == null) {
            // No enemies left to fight
            enemyTurnState = EnemyTurnState.DONE;
            enemyTurnDelay = Math.max(1, CrafticsMod.CONFIG.enemyTurnDelay() / 2);
            return;
        }

        int range = ally.getRange();
        if (targetDist <= range) {
            // In range — attack! Apply pet damage type bonus from player's gear
            int petBonus = DamageType.getTotalBonus(
                PlayerCombatStats.getArmorSet(player), activeTrimScan, combatEffects, DamageType.PET);
            // Rally set bonus: allies get +1 Attack
            if (activeTrimScan != null && activeTrimScan.setBonus() == TrimEffects.SetBonus.RALLY) {
                petBonus += 1;
            }
            int dealt = target.takeDamage(ally.getAttackPower() + petBonus);
            String petMsg = petBonus > 0 ? " \u00a7a+" + petBonus + " Pet" : "";
            sendMessage("§a" + ally.getDisplayName() + " attacks " + target.getDisplayName() + " for " + dealt + " damage!" + petMsg);
            if (!target.isAlive()) {
                killEnemy(target);
            }
            enemyTurnState = EnemyTurnState.DONE;
            enemyTurnDelay = 8;
        } else {
            // Move toward target
            List<GridPos> path = Pathfinding.findPath(arena, allyPos, target.getGridPos(), ally.getMoveSpeed(), false);
            if (path != null && !path.isEmpty()) {
                sendMessage("§a" + ally.getDisplayName() + " advances toward " + target.getDisplayName() + "!");
                startEnemyMove(path);
            } else {
                // Can't path — use seek
                GridPos closest = Pathfinding.findClosestReachableTo(arena, allyPos, target.getGridPos(), ally.getMoveSpeed(), ally);
                if (closest != null && !closest.equals(allyPos)) {
                    List<GridPos> seekPath = Pathfinding.findPath(arena, allyPos, closest, ally.getMoveSpeed(), false);
                    if (seekPath != null && !seekPath.isEmpty()) {
                        startEnemyMove(seekPath);
                        return;
                    }
                }
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = Math.max(1, CrafticsMod.CONFIG.enemyTurnDelay() / 2);
            }
        }
    }

    // ==================== Boss Action Resolution ====================

    /**
     * Resolve pending boss warnings at the start of a boss's turn.
     * Called before the boss chooses its new action.
     */
    private void resolvePendingBossWarnings(CombatEntity boss) {
        var iterator = pendingBossWarnings.iterator();
        while (iterator.hasNext()) {
            PendingBossWarning pw = iterator.next();
            if (pw.boss() == boss && boss.isAlive()) {
                sendMessage("§c" + boss.getDisplayName() + "'s " +
                    pw.ability().abilityName().replace('_', ' ') + " resolves!");
                dispatchBossSubAction(pw.ability().resolvedAction());
                iterator.remove();
            }
        }
    }

    /**
     * Dispatch a single boss sub-action (used by BossAbility resolution and CompositeAction).
     */
    private void dispatchBossSubAction(EnemyAction action) {
        switch (action) {
            case EnemyAction.Attack atk -> {
                int playerDefense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus();
                int actual = Math.max(1, atk.damage() - playerDefense);
                player.setHealth(Math.max(0, player.getHealth() - actual));
                sendMessage("§c  Hit for " + actual + " damage! (HP: " + getPlayerHp() + ")");
                if (getPlayerHp() <= 0) handleGameOver();
            }
            case EnemyAction.SummonMinions sm -> spawnBossMinions(sm);
            case EnemyAction.AreaAttack aa -> resolveAreaAttack(aa);
            case EnemyAction.CreateTerrain ct -> resolveCreateTerrain(ct);
            case EnemyAction.LineAttack la -> resolveLineAttack(la);
            case EnemyAction.ModifySelf ms -> resolveModifySelf(currentEnemy, ms);
            case EnemyAction.ForcedMovement fm -> resolveForcedMovement(fm);
            case EnemyAction.Teleport tp -> {
                if (currentEnemy != null) {
                    MobEntity mob = currentEnemy.getMobEntity();
                    if (mob != null) {
                        double wx = arena.getOrigin().getX() + tp.target().x() + 0.5;
                        double wz = arena.getOrigin().getZ() + tp.target().z() + 0.5;
                        mob.requestTeleport(wx, arena.getOrigin().getY() + 1.0, wz);
                    }
                    arena.moveEntity(currentEnemy, tp.target());
                }
            }
            case EnemyAction.TeleportAndAttack tpa -> {
                if (currentEnemy != null) {
                    MobEntity mob = currentEnemy.getMobEntity();
                    if (mob != null) {
                        double wx = arena.getOrigin().getX() + tpa.target().x() + 0.5;
                        double wz = arena.getOrigin().getZ() + tpa.target().z() + 0.5;
                        mob.requestTeleport(wx, arena.getOrigin().getY() + 1.0, wz);
                    }
                    arena.moveEntity(currentEnemy, tpa.target());
                    int playerDefense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus();
                    int actual = Math.max(1, tpa.damage() - playerDefense);
                    player.setHealth(Math.max(0, player.getHealth() - actual));
                    sendMessage("§c  Teleport strike for " + actual + " damage! (HP: " + getPlayerHp() + ")");
                    if (getPlayerHp() <= 0) handleGameOver();
                }
            }
            case EnemyAction.RangedAttack ra -> {
                int playerDefense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus();
                int actual = Math.max(1, ra.damage() - playerDefense);
                player.setHealth(Math.max(0, player.getHealth() - actual));
                sendMessage("§c  Ranged hit for " + actual + " damage! (HP: " + getPlayerHp() + ")");
                if (getPlayerHp() <= 0) handleGameOver();
            }
            case EnemyAction.CompositeAction ca -> {
                for (EnemyAction sub : ca.actions()) {
                    dispatchBossSubAction(sub);
                }
            }
            default -> {} // Ignore unsupported sub-actions
        }
    }

    /**
     * Spawn minions for a boss SummonMinions action.
     */
    private void spawnBossMinions(EnemyAction.SummonMinions sm) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        EntityType<?> type = Registries.ENTITY_TYPE.get(Identifier.of(sm.entityTypeId()));
        int spawned = 0;
        for (GridPos pos : sm.positions()) {
            if (spawned >= sm.count()) break;
            if (!arena.isInBounds(pos) || arena.isOccupied(pos)) continue;
            GridTile tile = arena.getTile(pos);
            if (tile == null || !tile.isWalkable()) continue;

            BlockPos spawnPos = arena.gridToBlockPos(pos);
            var rawEntity = type.create(world, null, spawnPos, SpawnReason.MOB_SUMMONED, false, false);
            if (rawEntity == null) continue;
            rawEntity.refreshPositionAndAngles(
                spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
            if (rawEntity instanceof MobEntity mob) {
                mob.setAiDisabled(true);
                mob.setInvulnerable(true);
                mob.setNoGravity(true);
                mob.setPersistent();
                mob.addCommandTag("craftics_arena");
            }
            world.spawnEntity(rawEntity);
            if (rawEntity instanceof MobEntity mob) {
                CombatEntity ce = new CombatEntity(
                    mob.getId(), sm.entityTypeId(), pos,
                    sm.hp(), sm.atk(), sm.def(), 1
                );
                ce.setMobEntity(mob);
                enemies.add(ce);
                arena.placeEntity(ce);
                spawned++;
                // Spawn particles
                world.spawnParticles(net.minecraft.particle.ParticleTypes.POOF,
                    spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5,
                    5, 0.3, 0.3, 0.3, 0.02);
            }
        }
        if (spawned > 0) {
            sendMessage("§5  " + spawned + " " + sm.entityTypeId().substring(sm.entityTypeId().indexOf(':') + 1)
                + "(s) appeared!");
        }
    }

    /**
     * Resolve an AoE attack centered on a tile.
     */
    private void resolveAreaAttack(EnemyAction.AreaAttack aa) {
        GridPos center = aa.center();
        int radius = aa.radius();
        int damage = aa.damage();
        ServerWorld world = (ServerWorld) player.getEntityWorld();

        // Particles at center
        BlockPos centerBlock = arena.gridToBlockPos(center);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION,
            centerBlock.getX() + 0.5, centerBlock.getY() + 1.0, centerBlock.getZ() + 0.5,
            3, radius * 0.5, 0.5, radius * 0.5, 0.0);

        // Check player
        GridPos playerGridPos = arena.getPlayerGridPos();
        if (Math.abs(playerGridPos.x() - center.x()) <= radius
            && Math.abs(playerGridPos.z() - center.z()) <= radius) {
            int playerDefense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus();
            int actual = Math.max(1, damage - playerDefense);
            player.setHealth(Math.max(0, player.getHealth() - actual));
            sendMessage("§c  Area hit for " + actual + " damage! (HP: " + getPlayerHp() + ")");
            // Apply named effects
            if (aa.effectName() != null) {
                applyBossAreaEffect(aa.effectName());
            }
            if (getPlayerHp() <= 0) { handleGameOver(); return; }
        }

        // Damage enemy mobs in the area (friendly fire for bosses)
        if (CrafticsMod.CONFIG.friendlyFireEnabled()) {
            for (CombatEntity e : new ArrayList<>(enemies)) {
                if (e == currentEnemy || !e.isAlive()) continue;
                if (Math.abs(e.getGridPos().x() - center.x()) <= radius
                    && Math.abs(e.getGridPos().z() - center.z()) <= radius) {
                    int dealt = e.takeDamage(damage);
                    sendMessage("§6  Blast hits " + e.getDisplayName() + " for " + dealt + "!");
                    checkAndHandleDeath(e);
                }
            }
        }
    }

    /**
     * Apply status effects from boss area attacks.
     */
    private void applyBossAreaEffect(String effectName) {
        switch (effectName) {
            case "slowness", "blizzard", "frost" -> {
                combatEffects.addEffect(CombatEffects.EffectType.SLOWNESS, 2, 0);
                sendMessage("§b  Slowness applied! (-1 movement for 2 turns)");
            }
            case "weakness", "cursed_fog", "wail" -> {
                combatEffects.addEffect(CombatEffects.EffectType.WEAKNESS, 2, 0);
                sendMessage("§7  Weakness applied! (-2 attack for 2 turns)");
            }
            case "burning", "fire", "magma" -> {
                if (!combatEffects.hasFireResistance()) {
                    combatEffects.addEffect(CombatEffects.EffectType.BURNING, 2, 0);
                    sendMessage("§6  You're burning! (-1 HP/turn for 2 turns)");
                }
            }
            case "poison", "venom", "web_poison" -> {
                combatEffects.addEffect(CombatEffects.EffectType.POISON, 3, 0);
                sendMessage("§2  Poison applied! (-1 HP/turn for 3 turns)");
            }
            case "wither", "wither_slash" -> {
                combatEffects.addEffect(CombatEffects.EffectType.WITHER, 3, 0);
                sendMessage("§8  Wither applied! (-2 HP/turn for 3 turns)");
            }
            case "darkness", "lights_out" -> {
                sendMessage("§8  Darkness falls! Vision reduced.");
            }
            default -> {} // No special effect
        }
    }

    /**
     * Resolve terrain creation/transformation.
     */
    private void resolveCreateTerrain(EnemyAction.CreateTerrain ct) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        int changed = 0;
        for (GridPos pos : ct.tiles()) {
            if (!arena.isInBounds(pos)) continue;
            GridTile tile = arena.getTile(pos);
            if (tile == null) continue;

            tile.setType(ct.terrainType());
            if (ct.duration() > 0) {
                tile.setTurnsRemaining(ct.duration());
            }
            // Update the visual block in the world
            BlockPos bp = arena.gridToBlockPos(pos);
            world.setBlockState(bp, tile.getBlockType().getDefaultState());

            // Particles for the transformation
            world.spawnParticles(net.minecraft.particle.ParticleTypes.POOF,
                bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5,
                3, 0.3, 0.3, 0.3, 0.01);
            changed++;
        }
        if (changed > 0) {
            String terrainName = ct.terrainType().name().toLowerCase();
            sendMessage("§e  " + currentEnemy.getDisplayName() + " reshapes " + changed
                + " tiles to " + terrainName + "!");
        }
    }

    /**
     * Resolve a line attack — damage all entities along a line.
     */
    private void resolveLineAttack(EnemyAction.LineAttack la) {
        GridPos playerGridPos = arena.getPlayerGridPos();
        boolean hitPlayer = false;
        for (int i = 0; i < la.length(); i++) {
            GridPos tile = new GridPos(la.start().x() + la.dx() * i, la.start().z() + la.dz() * i);
            if (!arena.isInBounds(tile)) continue;
            if (tile.equals(playerGridPos)) hitPlayer = true;

            // Damage enemies in the line (friendly fire)
            if (CrafticsMod.CONFIG.friendlyFireEnabled()) {
                CombatEntity occupant = arena.getOccupant(tile);
                if (occupant != null && occupant != currentEnemy && occupant.isAlive()) {
                    int dealt = occupant.takeDamage(la.damage());
                    sendMessage("§6  Line hits " + occupant.getDisplayName() + " for " + dealt + "!");
                    checkAndHandleDeath(occupant);
                }
            }
        }
        if (hitPlayer) {
            int playerDefense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus();
            int actual = Math.max(1, la.damage() - playerDefense);
            player.setHealth(Math.max(0, player.getHealth() - actual));
            sendMessage("§c  Line attack hits for " + actual + " damage! (HP: " + getPlayerHp() + ")");
            if (getPlayerHp() <= 0) handleGameOver();
        }
    }

    /**
     * Resolve a boss self-modification (buff/debuff).
     */
    private void resolveModifySelf(CombatEntity boss, EnemyAction.ModifySelf ms) {
        switch (ms.stat()) {
            case "speed" -> {
                boss.setSpeedBonus(boss.getSpeedBonus() + ms.amount());
                sendMessage("§d  " + boss.getDisplayName() + " gains +" + ms.amount() + " speed!");
            }
            case "defense" -> {
                // Temporary defense boost — we'll track via defensePenalty (negative = boost)
                boss.setDefensePenalty(boss.getDefensePenalty() - ms.amount());
                if (ms.duration() > 0) {
                    boss.setDefensePenaltyTurns(ms.duration());
                }
                sendMessage("§d  " + boss.getDisplayName() + " gains +" + ms.amount() + " defense!");
            }
            case "attack" -> {
                // Temporary attack penalty (negative = boost)
                boss.setAttackPenalty(boss.getAttackPenalty() - ms.amount());
                sendMessage("§d  " + boss.getDisplayName() + " gains +" + ms.amount() + " attack!");
            }
            case "heal" -> {
                boss.heal(ms.amount());
                sendMessage("§a  " + boss.getDisplayName() + " heals for " + ms.amount() + " HP!");
            }
            default -> sendMessage("§d  " + boss.getDisplayName() + " modifies " + ms.stat() + "!");
        }
    }

    /**
     * Resolve forced movement — push/pull a target entity.
     */
    private void resolveForcedMovement(EnemyAction.ForcedMovement fm) {
        if (fm.targetEntityId() == -1) {
            // Move the player
            GridPos playerGridPos = arena.getPlayerGridPos();
            GridPos landingPos = playerGridPos;
            for (int i = 1; i <= fm.tiles(); i++) {
                GridPos candidate = new GridPos(playerGridPos.x() + fm.dx() * i, playerGridPos.z() + fm.dz() * i);
                if (!arena.isInBounds(candidate) || arena.isEnemyOccupied(candidate)) break;
                GridTile tile = arena.getTile(candidate);
                if (tile != null && tile.getType() == TileType.VOID) {
                    // Player pushed into void — take fall damage
                    int playerDefense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus();
                    int fallDmg = Math.max(1, 5 - playerDefense);
                    player.setHealth(Math.max(0, player.getHealth() - fallDmg));
                    sendMessage("§c  Pushed into the void for " + fallDmg + " damage! (HP: " + getPlayerHp() + ")");
                    if (getPlayerHp() <= 0) { handleGameOver(); return; }
                    break;
                }
                if (tile == null || !tile.isWalkable()) break;
                landingPos = candidate;
            }
            if (!landingPos.equals(playerGridPos)) {
                arena.setPlayerGridPos(landingPos);
                BlockPos bp = arena.gridToBlockPos(landingPos);
                player.requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                sendMessage("§e  Pushed " + playerGridPos.manhattanDistance(landingPos) + " tiles!");
            }
        } else {
            // Move an enemy entity
            CombatEntity target = findEnemyById(fm.targetEntityId());
            if (target != null && target.isAlive()) {
                GridPos startPos = target.getGridPos();
                GridPos landingPos = startPos;
                for (int i = 1; i <= fm.tiles(); i++) {
                    GridPos candidate = new GridPos(startPos.x() + fm.dx() * i, startPos.z() + fm.dz() * i);
                    if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) break;
                    GridTile tile = arena.getTile(candidate);
                    if (tile == null || !tile.isWalkable()) break;
                    landingPos = candidate;
                }
                if (!landingPos.equals(startPos)) {
                    MobEntity mob = target.getMobEntity();
                    if (mob != null) {
                        BlockPos bp = arena.gridToBlockPos(landingPos);
                        mob.requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                    }
                    arena.moveEntity(target, landingPos);
                }
            }
        }
    }

    /**
     * Highlight a warning tile for the player (red glow effect).
     */
    private void highlightWarningTile(GridPos tile) {
        // Send warning tile highlight to client for rendering
        if (player != null) {
            sendToAllParty(new CombatEventPayload(
                CombatEventPayload.EVENT_TILE_WARNING, 0, 0, 0, tile.x(), tile.z()
            ));
        }
    }

    /**
     * Render a boss warning. Some warnings only use particles/client overlays,
     * while line-style telegraphs also place a temporary floor marker.
     */
    private void renderBossWarning(com.crackedgames.craftics.combat.ai.boss.BossWarning warning) {
        if (warning == null) return;
        for (GridPos tile : warning.getAffectedTiles()) {
            highlightWarningTile(tile);
        }

        if (warning.getType() == com.crackedgames.craftics.combat.ai.boss.BossWarning.WarningType.GROUND_CRACK) {
            spawnWarningParticles(warning.getAffectedTiles(), net.minecraft.particle.ParticleTypes.ANGRY_VILLAGER, 2);
        }

        if (warning.getType() == com.crackedgames.craftics.combat.ai.boss.BossWarning.WarningType.TILE_HIGHLIGHT) {
            placeWarningBlocks(warning.getAffectedTiles());
        }
    }

    private void spawnWarningParticles(java.util.List<GridPos> tiles,
                                       net.minecraft.particle.ParticleEffect particle,
                                       int countPerTile) {
        if (player == null || arena == null) return;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        for (GridPos tile : tiles) {
            if (!arena.isInBounds(tile)) continue;
            BlockPos bp = arena.gridToBlockPos(tile);
            world.spawnParticles(
                particle,
                bp.getX() + 0.5,
                bp.getY() + 0.15,
                bp.getZ() + 0.5,
                countPerTile,
                0.18,
                0.02,
                0.18,
                0.01
            );
        }
    }

    /**
     * Place physical warning blocks on the arena floor and save originals for restoration.
     */
    private void placeWarningBlocks(java.util.List<GridPos> tiles) {
        if (player == null || arena == null) return;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        for (GridPos tile : tiles) {
            if (!arena.isInBounds(tile)) continue;
            BlockPos bp = arena.gridToBlockPos(tile).down();
            net.minecraft.block.BlockState original = world.getBlockState(bp);
            // Don't overwrite if already a warning block
            if (original.getBlock() != Blocks.REDSTONE_BLOCK) {
                warningOriginalBlocks.putIfAbsent(bp, original);
                world.setBlockState(bp, Blocks.REDSTONE_BLOCK.getDefaultState());
            }
        }
    }

    /**
     * Restore all warning blocks to their original state.
     */
    private void clearWarningBlocks() {
        if (player == null || warningOriginalBlocks.isEmpty()) return;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        for (var entry : warningOriginalBlocks.entrySet()) {
            world.setBlockState(entry.getKey(), entry.getValue());
        }
        warningOriginalBlocks.clear();
    }

    /**
     * Tick all temporary terrain tiles (fire, lava, etc.) — called at the start of each turn.
     */
    private void tickTemporaryTerrain() {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridTile tile = arena.getTile(x, z);
                if (tile != null && tile.getTurnsRemaining() > 0) {
                    tile.tickTurn();
                    // If tile reset to NORMAL, update the world block
                    if (tile.getType() == TileType.NORMAL) {
                        BlockPos bp = arena.gridToBlockPos(new GridPos(x, z));
                        world.setBlockState(bp, Blocks.GRASS_BLOCK.getDefaultState());
                    }
                }
            }
        }
    }

    // ===== Boss Callback Hooks =====

    /**
     * Called when the player deals damage to a boss enemy.
     * Dispatches to boss-specific reaction callbacks.
     */
    private void notifyBossOfDamage(CombatEntity target, int damageDealt) {
        if (!target.isBoss()) return;
        EnemyAI ai = AIRegistry.get(target.getAiKey());

        // ShulkerArchitect: notify it was damaged (primes Fortify Shell)
        if (ai instanceof ShulkerArchitectAI sa) {
            sa.notifyDamaged();
        }

        // MoltenKing: reactive split on heavy hit
        if (ai instanceof MoltenKingAI mk && damageDealt >= 8) {
            EnemyAction reaction = mk.reactToHeavyDamage(target, arena);
            if (reaction != null) {
                dispatchBossSubAction(reaction);
            }
        }
    }

    /**
     * Called when the player finishes moving.
     * Notifies Warden boss of player movement for vibration sense.
     */
    private void notifyBossesPlayerMoved(GridPos destination, int tilesMoved) {
        for (CombatEntity e : enemies) {
            if (!e.isAlive() || !e.isBoss()) continue;
            EnemyAI ai = AIRegistry.get(e.getAiKey());
            if (ai instanceof WardenAI warden) {
                warden.onPlayerMove(destination, tilesMoved);
            }
        }
    }

    /**
     * Called at the end of the player's turn.
     * Ticks pending boss warnings (telegraphed attacks).
     */
    private void tickBossWarnings() {
        for (CombatEntity e : enemies) {
            if (!e.isAlive() || !e.isBoss()) continue;
            EnemyAI ai = AIRegistry.get(e.getAiKey());
            if (ai instanceof BossAI boss) {
                boss.tickWarning();
            }
        }
    }

    /**
     * Called when a minion/entity in a boss fight dies.
     * Dispatches boss-specific callbacks for minion death.
     */
    private void notifyBossOfMinionDeath(CombatEntity deadEntity) {
        for (CombatEntity e : enemies) {
            if (!e.isAlive() || !e.isBoss()) continue;
            EnemyAI ai = AIRegistry.get(e.getAiKey());

            // Dragon: crystal destroyed
            if (ai instanceof DragonAI dragon) {
                // Check if the dead entity is an end crystal (e.g. a minion with "crystal" marker)
                if ("minecraft:end_crystal".equals(deadEntity.getEntityTypeId())) {
                    dragon.onCrystalDestroyed();
                    sendMessage("§d✦ End Crystal destroyed! Dragon weakened.");
                }
            }

            // ShulkerArchitect: turret destroyed
            if (ai instanceof ShulkerArchitectAI sa) {
                if (deadEntity.getGridPos() != null) {
                    sa.removeTurret(deadEntity.getGridPos());
                }
            }
        }
    }

    /**
     * Called after a boss entity is spawned and placed on the arena.
     * Initializes boss-specific fight setup.
     */
    private void initBossSetup(CombatEntity bossEntity) {
        EnemyAI ai = AIRegistry.get(bossEntity.getAiKey());

        if (ai instanceof BroodmotherAI broodmother) {
            broodmother.initEggSacs(arena);
        }
    }

    private void startEnemyMove(List<GridPos> path) {
        enemyMovePath = path;
        enemyMovePathIndex = 0;
        enemyMoveTickCounter = 0;
        enemyLerpInitialized = false;
        enemyTurnState = EnemyTurnState.MOVING;
        sendMessage("§7" + currentEnemy.getDisplayName() + " moves...");
    }

    private void tickEnemyMoving() {
        if (enemyMovePathIndex >= enemyMovePath.size()) {
            // Movement done
            enemyLerpInitialized = false;
            // If MoveAndAttack (any variant), proceed to attack
            if (pendingAction instanceof EnemyAction.MoveAndAttack
                    || pendingAction instanceof EnemyAction.MoveAndAttackMob
                    || pendingAction instanceof EnemyAction.MoveAndAttackWithKnockback) {
                enemyTurnState = EnemyTurnState.ATTACKING;
                enemyTurnDelay = Math.max(1, CrafticsMod.CONFIG.enemyTurnDelay() / 2);
            } else {
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = Math.max(1, CrafticsMod.CONFIG.enemyTurnDelay() / 2);
            }
            return;
        }

        MobEntity mob = currentEnemy.getMobEntity();
        if (mob == null || !mob.isAlive() || mob.isRemoved()) {
            enemyLerpInitialized = false;
            enemyTurnState = EnemyTurnState.DONE;
            return;
        }

        // Init lerp for this tile
        if (!enemyLerpInitialized) {
            enemyLerpInitialized = true;
            enemyMoveTickCounter = 0;
            enemyLerpStartX = mob.getX();
            enemyLerpStartY = mob.getY();
            enemyLerpStartZ = mob.getZ();

            // Face movement direction
            GridPos next = enemyMovePath.get(enemyMovePathIndex);
            GridPos prev = enemyMovePathIndex == 0 ? currentEnemy.getGridPos() : enemyMovePath.get(enemyMovePathIndex - 1);
            int dx = next.x() - prev.x();
            int dz = next.z() - prev.z();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            mob.setYaw(yaw);
            mob.setHeadYaw(yaw);
        }

        enemyMoveTickCounter++;
        GridPos next = enemyMovePath.get(enemyMovePathIndex);
        BlockPos endBlock = arena.gridToBlockPos(next);
        double moveOffset = (currentEnemy != null && currentEnemy.getSize() > 1) ? currentEnemy.getSize() / 2.0 : 0.5;
        double endX = endBlock.getX() + moveOffset;
        double endY = endBlock.getY();
        double endZ = endBlock.getZ() + moveOffset;

        int emTicks2 = getMoveTicks();
        float progress = Math.min(1.0f, (float) enemyMoveTickCounter / emTicks2);
        double x = enemyLerpStartX + (endX - enemyLerpStartX) * progress;
        double y = enemyLerpStartY + (endY - enemyLerpStartY) * progress;
        double z = enemyLerpStartZ + (endZ - enemyLerpStartZ) * progress;
        mob.requestTeleport(x, y, z);

        if (enemyMoveTickCounter >= emTicks2) {
            arena.moveEntity(currentEnemy, next);

            // Check for hex trap tile effect
            if (tileEffects.containsKey(next) && "hex_trap".equals(tileEffects.get(next))) {
                tileEffects.remove(next);
                hexTrapTurnsRemaining = 0;
                int trapDmg = currentEnemy.takeDamage(12);
                currentEnemy.setStunned(true);
                sendMessage("§5\u2620 " + currentEnemy.getDisplayName() + " triggers a Hex Trap! " + trapDmg + " damage + stunned!");
                if (currentEnemy.getMobEntity() != null) {
                    ServerWorld sw = (ServerWorld) player.getEntityWorld();
                    sw.spawnParticles(net.minecraft.particle.ParticleTypes.WITCH,
                        currentEnemy.getMobEntity().getX(), currentEnemy.getMobEntity().getY() + 1.0,
                        currentEnemy.getMobEntity().getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                    player.getWorld().playSound(null, currentEnemy.getMobEntity().getBlockPos(),
                        net.minecraft.sound.SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE,
                        net.minecraft.sound.SoundCategory.HOSTILE, 0.8f, 1.2f);
                }
                checkAndHandleDeath(currentEnemy);
            }

            enemyLerpInitialized = false;
            enemyMovePathIndex++;
        }
    }

    private void tickEnemyAttacking() {
        // Handle mob-vs-mob attacks (MoveAndAttackMob)
        if (pendingAction instanceof EnemyAction.MoveAndAttackMob maam) {
            CombatEntity target = findEnemyById(maam.targetEntityId());
            if (target != null && target.isAlive()) {
                int dealt = target.takeDamage(maam.damage());
                sendMessage("§6" + currentEnemy.getDisplayName() + " attacks " + target.getDisplayName() + " for " + dealt + "!");
                checkAndHandleDeath(target);
            }
            sendSync();
            enemyTurnState = EnemyTurnState.DONE;
            enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            return;
        }

        // Validate melee range — enemy must be adjacent to the player to hit
        int distToPlayer = currentEnemy.minDistanceTo(arena.getPlayerGridPos());
        if (distToPlayer > 1) {
            // Not in range — skip attack
            enemyTurnState = EnemyTurnState.DONE;
            enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            return;
        }

        // Determine damage and knockback
        int damage = 0;
        int knockbackTiles = 0;
        if (pendingAction instanceof EnemyAction.Attack atk) damage = atk.damage();
        else if (pendingAction instanceof EnemyAction.MoveAndAttack maa) damage = maa.damage();
        else if (pendingAction instanceof EnemyAction.AttackWithKnockback akb) {
            damage = akb.damage();
            knockbackTiles = akb.knockbackTiles();
        } else if (pendingAction instanceof EnemyAction.MoveAndAttackWithKnockback maakb) {
            damage = maakb.damage();
            knockbackTiles = maakb.knockbackTiles();
        }

        int playerDefense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus();
        damage = (int)(damage * com.crackedgames.craftics.CrafticsMod.CONFIG.enemyDamageMultiplier());
        int actual = Math.max(1, damage - playerDefense);
        player.setHealth(Math.max(0, player.getHealth() - actual));

        // Combat sound: player hit
        player.getWorld().playSound(null, player.getBlockPos(),
            net.minecraft.sound.SoundEvents.ENTITY_PLAYER_HURT,
            net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);

        sendMessage("§c" + currentEnemy.getDisplayName() + " hits you for " + actual + "!");
        applyEnemyHitEffect(currentEnemy.getEntityTypeId());

        // Apply knockback to player
        if (knockbackTiles > 0) {
            applyPlayerKnockback(currentEnemy.getGridPos(), knockbackTiles);
        }

        // Thorns: reflect damage back to attacker
        int thornsLevel = PlayerCombatStats.getThorns(player);
        if (thornsLevel > 0 && Math.random() < (thornsLevel * 0.15)) {
            int thornsDmg = currentEnemy.takeDamage(thornsLevel);
            sendMessage("\u00a72Thorns reflects " + thornsDmg + " damage!");
        }

        sendSync();

        if (getPlayerHp() <= 0) {
            handleGameOver();
            return;
        }

        enemyTurnState = EnemyTurnState.DONE;
        enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
    }

    /**
     * Knock the player back N tiles away from the attacker position.
     */
    private void applyPlayerKnockback(GridPos attackerPos, int tiles) {
        GridPos playerGridPos = arena.getPlayerGridPos();
        int dx = Integer.signum(playerGridPos.x() - attackerPos.x());
        int dz = Integer.signum(playerGridPos.z() - attackerPos.z());
        if (dx == 0 && dz == 0) dx = 1; // default direction

        GridPos landingPos = playerGridPos;
        for (int i = 1; i <= tiles; i++) {
            GridPos candidate = new GridPos(playerGridPos.x() + dx * i, playerGridPos.z() + dz * i);
            if (!arena.isInBounds(candidate) || arena.isEnemyOccupied(candidate)) break;
            var tile = arena.getTile(candidate);
            if (tile == null || !tile.isWalkable()) break;
            landingPos = candidate;
        }

        if (!landingPos.equals(playerGridPos)) {
            arena.setPlayerGridPos(landingPos);
            BlockPos bp = arena.gridToBlockPos(landingPos);
            player.requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
            sendMessage("§e  Knocked back " + playerGridPos.manhattanDistance(landingPos) + " tiles!");
        }
    }

    /**
     * Apply status effects when certain enemy types hit the player.
     */
    private void applyEnemyHitEffect(String entityTypeId) {
        switch (entityTypeId) {
            case "minecraft:wither_skeleton" -> {
                combatEffects.addEffect(CombatEffects.EffectType.WITHER, 3, 0);
                sendMessage("§8  Wither effect applied! (-2 HP/turn for 3 turns)");
            }
            case "minecraft:blaze" -> {
                if (!combatEffects.hasFireResistance()) {
                    combatEffects.addEffect(CombatEffects.EffectType.BURNING, 2, 0);
                    sendMessage("§6  You're burning! (-1 HP/turn for 2 turns)");
                }
            }
            case "minecraft:husk" -> {
                combatEffects.addEffect(CombatEffects.EffectType.WEAKNESS, 2, 0);
                sendMessage("§7  Weakness applied! (-2 attack for 2 turns)");
            }
            case "minecraft:stray" -> {
                combatEffects.addEffect(CombatEffects.EffectType.SLOWNESS, 2, 0);
                sendMessage("§b  Slowness applied! (-1 movement for 2 turns)");
            }
            case "minecraft:witch" -> {
                combatEffects.addEffect(CombatEffects.EffectType.POISON, 3, 0);
                sendMessage("§2  Poison applied! (-1 HP/turn for 3 turns)");
            }
            case "minecraft:shulker" -> {
                combatEffects.addEffect(CombatEffects.EffectType.SLOWNESS, 2, 0);
                sendMessage("§d  Levitation slows you! (-1 movement for 2 turns)");
            }
            case "minecraft:ghast" -> {
                // Ghast fireball splash: also damages nearby enemies? No — just applies burning
                if (!combatEffects.hasFireResistance()) {
                    combatEffects.addEffect(CombatEffects.EffectType.BURNING, 2, 0);
                    sendMessage("§6  Fireball burns you! (-1 HP/turn for 2 turns)");
                }
            }
            case "minecraft:ender_dragon" -> {
                combatEffects.addEffect(CombatEffects.EffectType.WEAKNESS, 2, 0);
                sendMessage("§5  Dragon breath weakens you! (-2 attack for 2 turns)");
            }
            case "minecraft:bee" -> {
                combatEffects.addEffect(CombatEffects.EffectType.POISON, 2, 0);
                sendMessage("§2  Bee sting! Poison applied! (-1 HP/turn for 2 turns)");
            }
            case "minecraft:llama" -> {
                sendMessage("§a  Splat! Llama spit!");
            }
            default -> {} // no special effect
        }
    }

    private void tickEnemyDone() {
        enemyTurnIndex++;
        enemyTurnState = EnemyTurnState.DECIDING;
        enemyTurnDelay = Math.max(1, CrafticsMod.CONFIG.enemyTurnDelay() / 2);
        pendingAction = null;
        currentEnemy = null;
    }

    private void handleGameOver() {
        // Test range: no penalties, just end cleanly
        if (testRange) {
            sendMessage("§c§l*** DEFEATED ***");
            sendMessage("§7Test range — no penalties applied.");
            for (ServerPlayerEntity p : getAllParticipants()) {
                p.setHealth(p.getMaxHealth());
                teleportToHub(p);
            }
            sendToAllParty(new ExitCombatPayload(false));
            endCombat();
            return;
        }
        phase = CombatPhase.GAME_OVER;
        // Sound: defeat
        if (player != null) {
            player.getWorld().playSound(null, player.getBlockPos(),
                net.minecraft.sound.SoundEvents.ENTITY_WITHER_DEATH,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.5f, 0.5f);
        }
        sendMessage("§c§l*** DEFEATED ***");

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(world);

        // Permadeath mode: lose ALL emeralds and reset biome progress
        if (CrafticsMod.CONFIG.permadeathMode()) {
            int allEmeralds = data.emeralds;
            if (allEmeralds > 0) data.spendEmeralds(allEmeralds);
            data.highestBiomeUnlocked = 1;
            sendMessage("§4§lPERMADEATH: All progress lost!");
        }

        // Death penalty: lose 50% emeralds
        int emeraldLoss = CrafticsMod.CONFIG.permadeathMode() ? 0 : data.emeralds / 2;
        if (emeraldLoss > 0) {
            data.spendEmeralds(emeraldLoss);
            sendMessage("§cLost " + emeraldLoss + " emeralds!");
        }

        // If player continued past level 1 in a biome run, they lose ALL items
        // (the "Continue" screen warns about this)
        if (data.isInBiomeRun() && data.activeBiomeLevelIndex > 0) {
            int itemsLost = 0;
            for (int slot = 0; slot < player.getInventory().size(); slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                if (stack.isEmpty()) continue;
                // Keep mod utility items (feather selector, guide book)
                Item item = stack.getItem();
                if (item == Items.FEATHER || item instanceof com.crackedgames.craftics.item.GuideBookItem) continue;
                player.getInventory().setStack(slot, ItemStack.EMPTY);
                itemsLost++;
            }
            // Also clear armor slots
            for (int slot = 0; slot < player.getInventory().armor.size(); slot++) {
                if (!player.getInventory().armor.get(slot).isEmpty()) {
                    player.getInventory().armor.set(slot, ItemStack.EMPTY);
                    itemsLost++;
                }
            }
            // Clear offhand
            if (!player.getOffHandStack().isEmpty()) {
                player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, ItemStack.EMPTY);
                itemsLost++;
            }
            if (itemsLost > 0) {
                sendMessage("§c§lLost all items! (" + itemsLost + " items)");
            }
        } else {
            // First level of a biome run (or no run) — only drop main hand weapon
            var mainHand = player.getMainHandStack();
            if (!mainHand.isEmpty() && mainHand.getItem() != Items.FEATHER) {
                sendMessage("§cDropped: " + mainHand.getName().getString());
                player.getInventory().setStack(player.getInventory().selectedSlot, ItemStack.EMPTY);
            }
        }

        // Heal player between levels (configurable)
        if (CrafticsMod.CONFIG.healBetweenLevels()) {
            player.setHealth(player.getMaxHealth());
        }
        player.clearStatusEffects();

        // Reset biome run
        data.endBiomeRun();
        data.inCombat = false;
        data.markDirty();

        sendMessage("§7Returning to hub... Your biome run has ended.");
        sendMessage("§7Remaining emeralds: " + data.emeralds);

        // Teleport all party members home, restore daytime
        world.setTimeOfDay(6000);
        for (ServerPlayerEntity p : getAllParticipants()) {
            teleportToHub(p);
        }
        sendToAllParty(new ExitCombatPayload(false));
        endCombat();
    }

    private void killEnemy(CombatEntity enemy) {
        enemy.takeDamage(9999);
        arena.removeEntity(enemy); // free the tile for pathfinding
        onEnemyKilled(enemy); // track kill streak
        MobEntity mob = enemy.getMobEntity();
        if (mob != null && mob.isAlive()) {
            // Pehkui death shrink — mob shrinks to 0 then gets discarded after delay
            startDeathShrink(mob);
            dyingMobs.add(mob);
            dyingMobTimers.add(20); // discard after 20 ticks (shrink anim is 15)
        }
        // Check if all enemies are dead or allied
        boolean allDead = enemies.stream().noneMatch(e -> e.isAlive() && !e.isAlly());
        if (allDead) {
            handleVictory();
        }
    }

    private void handleVictory() {
        // Test range: no rewards, just end cleanly
        if (testRange) {
            sendMessage("§a§l*** VICTORY ***");
            sendMessage("§7Test range — no rewards given.");
            for (ServerPlayerEntity p : getAllParticipants()) {
                p.setHealth(p.getMaxHealth());
                teleportToHub(p);
            }
            sendToAllParty(new ExitCombatPayload(true));
            endCombat();
            return;
        }
        // Sound: victory
        if (player != null) {
            player.getWorld().playSound(null, player.getBlockPos(),
                net.minecraft.sound.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
        phase = CombatPhase.LEVEL_COMPLETE;
        sendMessage("§a§l*** VICTORY! ***");

        // Give mob drops to all party participants (Luck stat adds bonus items per mob)
        List<ServerPlayerEntity> rewardRecipients = getAllParticipants();
        int luckBonusItems = PlayerProgression.get((ServerWorld) player.getEntityWorld())
            .getStats(player).getPoints(PlayerProgression.Stat.LUCK);
        for (CombatEntity enemy : enemies) {
            List<ItemStack> drops = getMobDrops(enemy.getEntityTypeId());
            for (ItemStack drop : drops) {
                if (luckBonusItems > 0 && Math.random() < (luckBonusItems * 0.20)) {
                    drop.setCount(drop.getCount() + 1);
                }
                for (ServerPlayerEntity recipient : rewardRecipients) {
                    recipient.getInventory().insertStack(drop.copy());
                }
                sendMessage("§e+ " + drop.getCount() + "x " + drop.getName().getString());
            }
            // Rare goat horn drop (10% chance per goat)
            if ("minecraft:goat".equals(enemy.getEntityTypeId()) && Math.random() < CrafticsMod.CONFIG.goatHornDropChance()) {
                ItemStack horn = GoatHornEffects.createRandomHorn(player.getRegistryManager());
                if (horn != null) {
                    for (ServerPlayerEntity recipient : rewardRecipients) {
                        recipient.getInventory().insertStack(horn.copy());
                    }
                    sendMessage("§6§l+ Goat Horn! " + horn.getName().getString());
                }
            }
        }

        // Give level completion loot to all party participants
        if (levelDef != null) {
            List<ItemStack> loot = levelDef.rollCompletionLoot();
            for (ItemStack item : loot) {
                for (ServerPlayerEntity recipient : rewardRecipients) {
                    recipient.getInventory().insertStack(item.copy());
                }
                sendMessage("§e+ " + item.getCount() + "x " + item.getName().getString());
            }
        }

        // Award emeralds (scales with biome progression)
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(world);
        int biomeIndex = data.activeBiomeLevelIndex;
        com.crackedgames.craftics.level.BiomeTemplate biomeTemplate = null;
        if (levelDef instanceof com.crackedgames.craftics.level.GeneratedLevelDefinition gld) {
            biomeTemplate = gld.getBiomeTemplate();
        }
        // Fallback: look up biome from active biome run (covers event levels like treasure vault)
        if (biomeTemplate == null && data.isInBiomeRun()) {
            for (var b : com.crackedgames.craftics.level.BiomeRegistry.getAllBiomes()) {
                if (b.biomeId.equals(data.activeBiomeId)) { biomeTemplate = b; break; }
            }
        }
        // Use path order for biome progression (not registry order)
        java.util.List<String> fullPath = com.crackedgames.craftics.level.BiomePath
            .getFullPath(Math.max(0, data.branchChoice));
        int biomeOrdinal = biomeTemplate != null
            ? fullPath.indexOf(biomeTemplate.biomeId) : 0;
        if (biomeOrdinal < 0) biomeOrdinal = 0;
        boolean isBoss = biomeTemplate != null && biomeTemplate.isBossLevel(arena.getLevelNumber());
        // Resourceful stat: +1 emerald per point (uses leader's stat)
        PlayerProgression victoryProg = PlayerProgression.get(world);
        int resourcefulBonus = victoryProg.getStats(player).getPoints(PlayerProgression.Stat.RESOURCEFUL);
        int emeraldsEarned = com.crackedgames.craftics.CrafticsMod.CONFIG.emeraldBaseReward() + biomeOrdinal / 3 + (isBoss ? 3 : 0) + resourcefulBonus;
        // Award emeralds to all party members via per-player data
        for (ServerPlayerEntity recipient : rewardRecipients) {
            CrafticsSavedData.PlayerData pd = data.getPlayerData(recipient.getUuid());
            pd.addEmeralds(emeraldsEarned);
        }
        data.addEmeralds(emeraldsEarned); // also update legacy fields for leader
        sendMessage("§a+ " + emeraldsEarned + " Emeralds");

        // Boss trim template drops (semi-rare: ~35% chance)
        if (isBoss && Math.random() < CrafticsMod.CONFIG.trimDropChance()) {
            // Determine dimension from biome position
            int overworldCount = com.crackedgames.craftics.level.BiomePath
                .getPath(Math.max(0, data.branchChoice)).size();
            int netherCount = com.crackedgames.craftics.level.BiomePath.getNetherPath().size();
            String dimension;
            if (biomeOrdinal < overworldCount) dimension = "overworld";
            else if (biomeOrdinal < overworldCount + netherCount) dimension = "nether";
            else dimension = "end";

            net.minecraft.item.Item[] trimPool = TrimEffects.getBossDropTrims(dimension);
            if (trimPool.length > 0) {
                net.minecraft.item.Item trimItem = trimPool[new java.util.Random().nextInt(trimPool.length)];
                ItemStack trimStack = new ItemStack(trimItem);
                for (ServerPlayerEntity recipient : rewardRecipients) {
                    recipient.getInventory().insertStack(trimStack.copy());
                }
                sendMessage("\u00a7b\u00a7l\u2726 RARE DROP: " + trimStack.getName().getString() + "!");
            }
        }

        if (isBoss) {
            // Boss fights end the biome run immediately.
            sendToAllParty(new ExitCombatPayload(true));

            // Boss defeated — biome complete! Unlock next biome, go home
            sendMessage("§6§l*** BIOME COMPLETE! ***");
            int currentBiomeOrder = biomeOrdinal + 1;
            if (data.highestBiomeUnlocked <= currentBiomeOrder) {
                data.highestBiomeUnlocked = currentBiomeOrder + 1;
                data.markDirty();
                com.crackedgames.craftics.CrafticsMod.updateWorldIcon(player.getServer(), data);
                // Unlock biome for all party members
                for (ServerPlayerEntity recipient : rewardRecipients) {
                    CrafticsSavedData.PlayerData pd = data.getPlayerData(recipient.getUuid());
                    if (pd.highestBiomeUnlocked <= currentBiomeOrder) {
                        pd.highestBiomeUnlocked = currentBiomeOrder + 1;
                    }
                }
                // Special messages when unlocking new dimensions
                int overworldBiomeCount = com.crackedgames.craftics.level.BiomePath
                    .getPath(Math.max(0, data.branchChoice)).size();
                int netherBiomeCount = com.crackedgames.craftics.level.BiomePath
                    .getNetherPath().size();
                if (currentBiomeOrder == overworldBiomeCount) {
                    sendMessage("§c§l\u2620 THE NETHER HAS BEEN UNLOCKED! \u2620");
                } else if (currentBiomeOrder == overworldBiomeCount + netherBiomeCount) {
                    sendMessage("§5§l\u2726 THE END HAS BEEN UNLOCKED! \u2726");
                }
                // Check if this was the final boss (Dragon's Nest) — trigger NG+
                int endBiomeCount = com.crackedgames.craftics.level.BiomePath
                    .getEndPath().size();
                int totalBiomes = overworldBiomeCount + netherBiomeCount + endBiomeCount;
                if (currentBiomeOrder == totalBiomes) {
                    data.startNewGamePlus();
                    sendMessage("§6§l\u2605 NEW GAME+ " + data.ngPlusLevel + " UNLOCKED! \u2605");
                    sendMessage("§eAll biomes reset. Enemies are now stronger. Your stats carry over.");
                }
            }
            data.endBiomeRun();
            data.inCombat = false;
            data.markDirty();
            world.setTimeOfDay(6000);
            // Teleport all party members home
            for (ServerPlayerEntity p : rewardRecipients) {
                teleportToHub(p);
            }

            // Save party list before endCombat() clears it
            List<ServerPlayerEntity> savedParty = new ArrayList<>(rewardRecipients);
            ServerPlayerEntity savedPlayer = player;
            endCombat();

            // Grant level-up to all party members
            PlayerProgression progression = PlayerProgression.get(world);
            for (ServerPlayerEntity member : savedParty) {
                progression.grantLevelUp(member);
                PlayerProgression.PlayerStats stats = progression.getStats(member);
                StringBuilder statData = new StringBuilder();
                for (PlayerProgression.Stat s : PlayerProgression.Stat.values()) {
                    if (statData.length() > 0) statData.append(":");
                    statData.append(stats.getPoints(s));
                }
                ServerPlayNetworking.send(member, new com.crackedgames.craftics.network.LevelUpPayload(
                    stats.level, stats.unspentPoints, statData.toString()
                ));
                CrafticsSavedData.PlayerData pd = data.getPlayerData(member.getUuid());
                ServerPlayNetworking.send(member, new com.crackedgames.craftics.network.PlayerStatsSyncPayload(
                    stats.level, stats.unspentPoints, statData.toString(), pd.emeralds
                ));
            }
        } else {
            // Non-boss: show Go Home / Continue choice
            // Don't advance if this was a trial/ambush — those are bonus fights, not real levels
            if (!lastFightWasTrial) {
                data.advanceBiomeRun();
            }
            String biomeName = biomeTemplate != null ? biomeTemplate.displayName : "Unknown";
            int displayIndex = data.activeBiomeLevelIndex;

            // Send choice screen to leader, waiting message to party members
            ServerPlayNetworking.send(player, new VictoryChoicePayload(
                emeraldsEarned, data.emeralds, false, biomeName, displayIndex
            ));
            for (ServerPlayerEntity member : getAllParticipants()) {
                if (!member.getUuid().equals(player.getUuid())) {
                    sendMessageTo(member, "§7Waiting for party leader to decide...");
                }
            }
            // Don't endCombat yet — wait for player choice
            // But do clear the arena
            clearHighlights();
        }
    }

    /**
     * Handle the player's choice after winning a non-boss level.
     * goHome = true: teleport home, reset biome run
     * goHome = false: continue to next level (with possible trader encounter)
     */
    public void handlePostLevelChoice(ServerPlayerEntity choicePlayer, boolean goHome) {
        // Handle trial chamber choice (not in active combat — player ref was nulled by endCombat)
        if (trialChamberPending) {
            trialChamberPending = false;
            lastFightWasTrial = false;
            com.crackedgames.craftics.level.LevelDefinition savedTrialDef = trialChamberLevelDef;
            trialChamberLevelDef = null;
            ServerWorld tcWorld = (ServerWorld) choicePlayer.getEntityWorld();
            if (goHome) {
                // Skip trial — continue to pending next level
                for (ServerPlayerEntity p : getOnlinePartyMembers(choicePlayer)) {
                    sendMessageTo(p, "§7You pass by the trial chamber...");
                }
                if (pendingNextLevelDef != null && pendingBiome != null) {
                    GridArena nextArena = buildArena(tcWorld, pendingNextLevelDef);
                    transitionPartyToArena(choicePlayer, nextArena, pendingNextLevelDef);
                    pendingNextLevelDef = null;
                    pendingBiome = null;
                }
            } else {
                // Accept trial!
                for (ServerPlayerEntity p : getOnlinePartyMembers(choicePlayer)) {
                    sendMessageTo(p, "§6§l\u2694 Entering the Trial Chamber! \u2694");
                    sendMessageTo(p, "§cWarning: Enemies are stronger here!");
                }
                if (savedTrialDef != null) {
                    GridArena trialArena = buildArena(tcWorld, savedTrialDef);
                    transitionPartyToArena(choicePlayer, trialArena, savedTrialDef);
                    lastFightWasTrial = true;
                }
            }
            return;
        }

        // After a trial chamber victory, go directly to the pending next level (no more events)
        if (lastFightWasTrial && pendingNextLevelDef != null && !goHome) {
            lastFightWasTrial = false;
            ServerWorld world2 = (ServerWorld) choicePlayer.getEntityWorld();
            endCombat();
            GridArena nextArena = buildArena(world2, pendingNextLevelDef);
            transitionPartyToArena(choicePlayer, nextArena, pendingNextLevelDef);
            pendingNextLevelDef = null;
            pendingBiome = null;
            return;
        }
        lastFightWasTrial = false;

        // Only accept choice from the combat leader
        if (player == null || !choicePlayer.equals(player)) return;

        // Save references before endCombat() nulls them
        ServerPlayerEntity savedPlayer = player;
        List<ServerPlayerEntity> savedMembers = new ArrayList<>(getAllParticipants());
        ServerWorld world = (ServerWorld) savedPlayer.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(world);

        if (goHome) {
            sendMessage("§7Returning to hub...");
            data.endBiomeRun();
            data.inCombat = false;
            data.markDirty();
            world.setTimeOfDay(6000);
            // Teleport all party members home
            for (ServerPlayerEntity member : savedMembers) {
                teleportToHub(member);
            }
            sendToAllParty(new ExitCombatPayload(true));
            endCombat();
        } else {
            // Continue to next level
            sendMessage("§aOnward!");
            endCombat();

            // Small delay then start next level — use the biome run state
            // The client will re-send a StartLevelPayload which triggers the next level
            // via the biome run tracking in ModNetworking
            // For now, directly start the next level
            String biomeId = data.activeBiomeId;
            int levelIndex = data.activeBiomeLevelIndex;
            com.crackedgames.craftics.level.BiomeTemplate biome = null;
            for (var b : com.crackedgames.craftics.level.BiomeRegistry.getAllBiomes()) {
                if (b.biomeId.equals(biomeId)) { biome = b; break; }
            }
            if (biome != null) {
                int globalLevel = biome.startLevel + levelIndex;
                com.crackedgames.craftics.level.LevelDefinition nextLevelDef =
                    com.crackedgames.craftics.level.LevelRegistry.get(globalLevel, data.branchChoice);
                if (nextLevelDef != null) {
                    java.util.Random eventRng = new java.util.Random();
                    float eventRoll = eventRng.nextFloat();

                    // Calculate biome position for difficulty scaling (use path ordinal, not registry index)
                    java.util.List<String> fullPath = com.crackedgames.craftics.level.BiomePath
                            .getFullPath(Math.max(0, data.branchChoice));
                    int biomeOrdinal = fullPath.indexOf(biome.biomeId);
                    if (biomeOrdinal < 0) biomeOrdinal = 0;

                    // No events on the very first continue (level index 1 = just beat level 1)
                    // Never have events before a boss fight
                    // Reduced event chance on early biomes (first 3 biomes)
                    boolean isBossLevel = biome.isBossLevel(globalLevel);
                    boolean skipEvents = levelIndex <= 1 || isBossLevel;
                    if (!skipEvents && biomeOrdinal < 3) {
                        // Early biomes: only 25% chance of any event (vs 55% normally)
                        skipEvents = eventRoll > 0.25f;
                        if (!skipEvents) eventRoll = eventRng.nextFloat(); // re-roll within event pool
                    }

                    // Check for forced event from /craftics force_event
                    String forced = forcedNextEvent;
                    if (forced != null) {
                        forcedNextEvent = null;
                        skipEvents = false; // override skip
                    }

                    // Helper: broadcast event messages to all party members
                    List<ServerPlayerEntity> partyMsg = getOnlinePartyMembers(savedPlayer);

                    if (skipEvents) {
                        // No event — go straight to next level
                        GridArena nextArena = buildArena(world, nextLevelDef);
                        transitionPartyToArena(savedPlayer, nextArena, nextLevelDef);
                    } else if (forced != null ? forced.equals("ominous_trial") : (eventRoll < 0.05f && biomeOrdinal >= 10)) {
                        // 5% chance (late game only): Ominous Trial Chamber
                        pendingNextLevelDef = nextLevelDef;
                        pendingBiome = biome;
                        trialChamberLevelDef = RandomEvents.generateOminousTrial(biomeOrdinal, data.ngPlusLevel);
                        trialChamberPending = true;
                        for (ServerPlayerEntity p : partyMsg) {
                            sendMessageTo(p, "\u00a74\u00a7l\u2694 OMINOUS TRIAL CHAMBER! \u2694");
                            sendMessageTo(p, "\u00a7cA dark and powerful trial awaits... with a WARDEN.");
                            sendMessageTo(p, "\u00a7eAccept for legendary loot?");
                        }
                        // Only leader gets the choice screen
                        ServerPlayNetworking.send(savedPlayer, new VictoryChoicePayload(
                            0, data.emeralds, false, "Ominous Trial", -1
                        ));
                    } else if (forced != null ? forced.equals("trial") : (eventRoll < 0.15f)) {
                        // 10% chance: Trial Chamber
                        pendingNextLevelDef = nextLevelDef;
                        pendingBiome = biome;
                        trialChamberLevelDef = TrialChamberEvent.generate(biomeOrdinal, data.ngPlusLevel);
                        trialChamberPending = true;
                        for (ServerPlayerEntity p : partyMsg) {
                            sendMessageTo(p, "\u00a76\u00a7l\u2694 TRIAL CHAMBER DISCOVERED! \u2694");
                            sendMessageTo(p, "\u00a77A mysterious trial awaits...");
                            sendMessageTo(p, "\u00a7eAccept the challenge for rare loot?");
                        }
                        ServerPlayNetworking.send(savedPlayer, new VictoryChoicePayload(
                            0, data.emeralds, false, "Trial Chamber", -1
                        ));
                    } else if (forced != null ? forced.equals("ambush") : (eventRoll < 0.25f)) {
                        // 10% chance: Ambush (unavoidable!)
                        pendingNextLevelDef = nextLevelDef;
                        pendingBiome = biome;
                        var ambushDef = RandomEvents.generateAmbush(biomeOrdinal, data.ngPlusLevel);
                        for (ServerPlayerEntity p : partyMsg) {
                            sendMessageTo(p, "\u00a7c\u00a7l\u26a0 AMBUSH! \u26a0");
                            sendMessageTo(p, "\u00a7cEnemies surround you! No escape!");
                        }
                        lastFightWasTrial = true;
                        GridArena ambushArena = buildArena(world, ambushDef);
                        transitionPartyToArena(savedPlayer, ambushArena, ambushDef);
                    } else if (forced != null ? forced.equals("shrine") : (eventRoll < 0.32f)) {
                        // 7% chance: Shrine of Fortune
                        String shrineResult = RandomEvents.handleShrine(savedPlayer, data);
                        for (ServerPlayerEntity p : partyMsg) {
                            sendMessageTo(p, shrineResult);
                        }
                        GridArena nextArena2 = buildArena(world, nextLevelDef);
                        transitionPartyToArena(savedPlayer, nextArena2, nextLevelDef);
                    } else if (forced != null ? forced.equals("traveler") : (eventRoll < 0.38f)) {
                        // 6% chance: Wounded Traveler
                        String travelerResult = RandomEvents.handleWoundedTraveler(savedPlayer);
                        for (ServerPlayerEntity p : partyMsg) {
                            sendMessageTo(p, travelerResult);
                        }
                        GridArena nextArena3 = buildArena(world, nextLevelDef);
                        transitionPartyToArena(savedPlayer, nextArena3, nextLevelDef);
                    } else if (forced != null ? forced.equals("vault") : (eventRoll < 0.42f)) {
                        // 4% chance: Treasure Vault
                        pendingNextLevelDef = nextLevelDef;
                        pendingBiome = biome;
                        trialChamberLevelDef = RandomEvents.generateTreasureVault(biomeOrdinal);
                        trialChamberPending = true;
                        for (ServerPlayerEntity p : partyMsg) {
                            sendMessageTo(p, "\u00a76\u00a7l\u2726 TREASURE VAULT DISCOVERED! \u2726");
                            sendMessageTo(p, "\u00a7eA hidden vault filled with riches!");
                            sendMessageTo(p, "\u00a77Enter to claim the loot? (No enemies!)");
                        }
                        ServerPlayNetworking.send(savedPlayer, new VictoryChoicePayload(
                            0, data.emeralds, false, "Treasure Vault", -1
                        ));
                    } else if (forced != null ? forced.equals("dig_site") : (eventRoll < 0.48f)) {
                        // 6% chance: Dig Site
                        pendingNextLevelDef = nextLevelDef;
                        pendingBiome = biome;
                        offerDigSite(savedPlayer, biome);
                    } else if (forced != null ? forced.equals("trader") : (eventRoll < 0.48f + CrafticsMod.CONFIG.traderSpawnChance())) {
                        // Configurable chance: Wandering Trader
                        pendingNextLevelDef = nextLevelDef;
                        pendingBiome = biome;
                        offerTrader(savedPlayer, biome);
                    } else {
                        // 45% chance: No event — start next level immediately
                        GridArena nextArena = buildArena(world, nextLevelDef);
                        transitionPartyToArena(savedPlayer, nextArena, nextLevelDef);
                    }
                }
            }
        }
    }

    // ---- Forced event (debug command) ----
    private String forcedNextEvent = null;
    public void setForcedNextEvent(String event) { this.forcedNextEvent = event; }
    public String getForcedNextEvent() { return forcedNextEvent; }

    // ---- Trial Chamber event ----
    private boolean trialChamberPending = false;
    private com.crackedgames.craftics.level.LevelDefinition trialChamberLevelDef;
    private boolean lastFightWasTrial = false;

    // ---- Dig site event (suspicious block brushing for pottery sherds) ----
    private boolean digSitePending = false;
    public boolean isDigSitePending() { return digSitePending; }

    // ---- Trader system ----
    private TraderSystem.TraderOffer activeTraderOffer;
    private com.crackedgames.craftics.level.LevelDefinition pendingNextLevelDef;
    private com.crackedgames.craftics.level.BiomeTemplate pendingBiome;
    private net.minecraft.entity.passive.WanderingTraderEntity spawnedTrader;
    private int traderEmeraldsGiven = 0; // how many emerald items we gave the player

    // ---- Dig Site event ----
    /** Get the dig site origin for this combat's world owner. */
    private BlockPos getDigSiteOrigin() {
        if (player != null && worldOwnerUuid != null) {
            ServerWorld w = (ServerWorld) player.getEntityWorld();
            CrafticsSavedData d = CrafticsSavedData.get(w);
            BlockPos origin = d.getDigSiteOrigin(worldOwnerUuid);
            if (origin != null) return origin;
        }
        return new BlockPos(500, 100, 600); // legacy fallback
    }

    private void offerDigSite(ServerPlayerEntity savedPlayer, com.crackedgames.craftics.level.BiomeTemplate biome) {
        // Exit combat mode on client for all party members
        List<ServerPlayerEntity> members = getOnlinePartyMembers(savedPlayer);
        for (ServerPlayerEntity p : members) {
            ServerPlayNetworking.send(p, new ExitCombatPayload(false));
        }

        ServerWorld world = (ServerWorld) savedPlayer.getEntityWorld();
        digSitePending = true;

        buildDigSiteArea(world, getDigSiteOrigin(), biome);
        // Teleport all party members to dig site
        for (ServerPlayerEntity p : members) {
            p.requestTeleport(
                getDigSiteOrigin().getX() + 4.5, getDigSiteOrigin().getY() + 1, getDigSiteOrigin().getZ() + 4.5);
        }

        sendMessageTo(savedPlayer, "§6§l✦ Archaeological Dig Site! ✦");
        sendMessageTo(savedPlayer, "§7You discover a suspicious block buried in the ground...");
        sendMessageTo(savedPlayer, "§eRight-click the suspicious block to brush it!");
        sendMessageTo(savedPlayer, "§7(25%% chance to uncover a pottery sherd)");
    }

    private void buildDigSiteArea(ServerWorld world, BlockPos origin, com.crackedgames.craftics.level.BiomeTemplate biome) {
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        net.minecraft.block.BlockState air = Blocks.AIR.getDefaultState();
        int sf = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE;

        // Determine if sand or gravel themed
        boolean isSandBiome = biome != null && biome.environmentStyle != null
            && biome.environmentStyle.name().contains("DESERT");
        net.minecraft.block.Block groundBlock = isSandBiome ? Blocks.SAND : Blocks.GRAVEL;
        net.minecraft.block.Block suspiciousBlock = isSandBiome ? Blocks.SUSPICIOUS_SAND : Blocks.SUSPICIOUS_GRAVEL;
        net.minecraft.block.Block edgeBlock = isSandBiome ? Blocks.SANDSTONE : Blocks.COBBLESTONE;

        // Clear 9×9, 6 high
        for (int x = 0; x < 9; x++)
            for (int z = 0; z < 9; z++)
                for (int y = 0; y <= 6; y++)
                    world.setBlockState(new BlockPos(ox + x, oy + y, oz + z), air, sf);

        // Base floor - stone
        for (int x = 0; x < 9; x++)
            for (int z = 0; z < 9; z++)
                world.setBlockState(new BlockPos(ox + x, oy, oz + z), edgeBlock.getDefaultState(), sf);

        // Central 5×5 dig pit (lowered floor of sand/gravel)
        for (int x = 2; x <= 6; x++)
            for (int z = 2; z <= 6; z++)
                world.setBlockState(new BlockPos(ox + x, oy, oz + z), groundBlock.getDefaultState(), sf);

        // Suspicious block in the center
        world.setBlockState(new BlockPos(ox + 4, oy + 1, oz + 4), suspiciousBlock.getDefaultState(), sf);

        // Corner fences for decoration
        for (int[] corner : new int[][]{{1,1},{1,7},{7,1},{7,7}}) {
            world.setBlockState(new BlockPos(ox + corner[0], oy + 1, oz + corner[1]),
                Blocks.OAK_FENCE.getDefaultState(), sf);
            world.setBlockState(new BlockPos(ox + corner[0], oy + 2, oz + corner[1]),
                Blocks.TORCH.getDefaultState(), sf);
        }

        // Lanterns for atmosphere
        world.setBlockState(new BlockPos(ox + 4, oy + 1, oz + 2), Blocks.LANTERN.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 4, oy + 1, oz + 6), Blocks.LANTERN.getDefaultState(), sf);
    }

    /**
     * Called from UseBlockCallback when player right-clicks a suspicious block while in dig site event.
     */
    public void handleDigSiteInteraction(ServerPlayerEntity player) {
        if (!digSitePending) return;
        digSitePending = false;

        // Resolve loot with the existing logic
        String result = RandomEvents.handleSuspiciousBlock(player);
        sendMessageTo(player, result);

        // Replace the suspicious block with regular sand/gravel
        BlockPos blockPos = new BlockPos(getDigSiteOrigin().getX() + 4, getDigSiteOrigin().getY() + 1, getDigSiteOrigin().getZ() + 4);
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        net.minecraft.block.Block replacement = world.getBlockState(blockPos).getBlock() == Blocks.SUSPICIOUS_SAND
            ? Blocks.SAND : Blocks.GRAVEL;
        world.setBlockState(blockPos, replacement.getDefaultState(),
            net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE);

        // Particles and sound
        world.spawnParticles(net.minecraft.particle.ParticleTypes.WAX_OFF,
            blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5,
            15, 0.3, 0.3, 0.3, 0.02);
        world.playSound(null, blockPos,
            net.minecraft.sound.SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC,
            net.minecraft.sound.SoundCategory.BLOCKS, 1.0f, 1.0f);

        // Start the pending next level after a short delay — transition whole party
        world.getServer().execute(() -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            if (pendingNextLevelDef != null && pendingBiome != null) {
                GridArena nextArena = buildArena(world, pendingNextLevelDef);
                transitionPartyToArena(player, nextArena, pendingNextLevelDef);
                pendingNextLevelDef = null;
                pendingBiome = null;
            }
        });
    }

    private void offerTrader(ServerPlayerEntity savedPlayer, com.crackedgames.craftics.level.BiomeTemplate biome) {
        // Exit combat mode on client for all party members
        for (ServerPlayerEntity p : getOnlinePartyMembers(savedPlayer)) {
            ServerPlayNetworking.send(p, new ExitCombatPayload(false));
        }

        ServerWorld world = (ServerWorld) savedPlayer.getEntityWorld();
        int biomeTier = com.crackedgames.craftics.level.BiomeRegistry.getAllBiomes().indexOf(biome) + 1;
        activeTraderOffer = TraderSystem.generateOffer(biomeTier, new java.util.Random());

        // Apply Resourceful stat discount (each point = 1 emerald off per trade, min cost 1)
        int resourcefulDiscount = PlayerProgression.get(world)
            .getStats(savedPlayer).getPoints(PlayerProgression.Stat.RESOURCEFUL);
        if (resourcefulDiscount > 0) {
            List<TraderSystem.Trade> discounted = new java.util.ArrayList<>();
            for (TraderSystem.Trade t : activeTraderOffer.trades()) {
                int newCost = Math.max(1, t.emeraldCost() - resourcefulDiscount);
                discounted.add(new TraderSystem.Trade(t.item(), newCost, t.description()));
            }
            activeTraderOffer = new TraderSystem.TraderOffer(activeTraderOffer.type(), discounted);
        }

        // Give player emerald items from their currency
        CrafticsSavedData data = CrafticsSavedData.get(world);
        traderEmeraldsGiven = data.emeralds;
        if (traderEmeraldsGiven > 0) {
            int remaining = traderEmeraldsGiven;
            while (remaining > 0) {
                int stackSize = Math.min(64, remaining);
                savedPlayer.getInventory().insertStack(new ItemStack(Items.EMERALD, stackSize));
                remaining -= stackSize;
            }
            data.emeralds = 0;
            data.markDirty();
        }

        // Build a small themed trader area away from the arena
        // Build a trader area in this combat's world slot
        BlockPos traderAreaOrigin;
        if (worldOwnerUuid != null) {
            CrafticsSavedData traderData = CrafticsSavedData.get(world);
            BlockPos dynamicOrigin = traderData.getTraderOrigin(worldOwnerUuid);
            traderAreaOrigin = dynamicOrigin != null ? dynamicOrigin : new BlockPos(500, 100, 500);
        } else {
            traderAreaOrigin = new BlockPos(500, 100, 500); // legacy fallback
        }
        buildTraderArea(world, traderAreaOrigin, biome);
        savedPlayer.requestTeleport(traderAreaOrigin.getX() + 4.5, traderAreaOrigin.getY() + 1, traderAreaOrigin.getZ() + 4.5);

        // Spawn real wandering trader in the trader area
        spawnedTrader = (net.minecraft.entity.passive.WanderingTraderEntity)
            net.minecraft.entity.EntityType.WANDERING_TRADER.spawn(world, traderAreaOrigin.up(), net.minecraft.entity.SpawnReason.EVENT);
        if (spawnedTrader != null) {
            spawnedTrader.refreshPositionAndAngles(
                traderAreaOrigin.getX() + 6.5, traderAreaOrigin.getY() + 1, traderAreaOrigin.getZ() + 4.5,
                -90f, 0f);
            spawnedTrader.setAiDisabled(true);
            spawnedTrader.setInvulnerable(true);

            // Set custom trades using emerald items as payment
            // Force the entity to initialize its offer list, then replace it
            net.minecraft.village.TradeOfferList tradeOffers = spawnedTrader.getOffers();
            tradeOffers.clear();
            for (TraderSystem.Trade t : activeTraderOffer.trades()) {
                net.minecraft.village.TradedItem cost = new net.minecraft.village.TradedItem(Items.EMERALD, t.emeraldCost());
                net.minecraft.village.TradeOffer offer = new net.minecraft.village.TradeOffer(
                    cost, t.item().copy(), 99, 0, 0f);
                tradeOffers.add(offer);
            }

            world.spawnEntity(spawnedTrader);
        }

        // Signal client that trader is active (for auto-done detection)
        CrafticsSavedData signalData = CrafticsSavedData.get(world);
        ServerPlayNetworking.send(savedPlayer, new com.crackedgames.craftics.network.TraderOfferPayload(
            activeTraderOffer.type().displayName, activeTraderOffer.type().icon, "", signalData.emeralds
        ));

        sendMessageTo(savedPlayer, "§e§lA wandering trader appears!");
        sendMessageTo(savedPlayer, "§7Right-click to trade. Your emeralds are in your inventory.");
        sendMessageTo(savedPlayer, "§7Type §e/craftics done§7 or wait to continue.");

        // Start a timer to auto-proceed after 30 seconds
        // For now, rely on TraderDonePayload from client
    }

    private void buildTraderArea(ServerWorld world, BlockPos origin, com.crackedgames.craftics.level.BiomeTemplate biome) {
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        net.minecraft.block.BlockState air = Blocks.AIR.getDefaultState();
        int sf = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE;

        // Biome-themed blocks
        net.minecraft.block.Block floorBlock = Blocks.GRASS_BLOCK;
        net.minecraft.block.Block accentBlock = Blocks.OAK_PLANKS;
        net.minecraft.block.Block postBlock = Blocks.OAK_LOG;
        net.minecraft.block.Block carpetBlock = Blocks.RED_CARPET;
        net.minecraft.block.Block roofBlock = Blocks.RED_WOOL;

        if (biome != null && biome.environmentStyle != null) {
            switch (biome.environmentStyle) {
                case FOREST -> {
                    floorBlock = Blocks.PODZOL; accentBlock = Blocks.SPRUCE_PLANKS;
                    postBlock = Blocks.SPRUCE_LOG; carpetBlock = Blocks.GREEN_CARPET; roofBlock = Blocks.GREEN_WOOL;
                }
                case SNOWY -> {
                    floorBlock = Blocks.SNOW_BLOCK; accentBlock = Blocks.SPRUCE_PLANKS;
                    postBlock = Blocks.SPRUCE_LOG; carpetBlock = Blocks.LIGHT_BLUE_CARPET; roofBlock = Blocks.WHITE_WOOL;
                }
                case CAVE, DEEP_DARK -> {
                    floorBlock = Blocks.DEEPSLATE_BRICKS; accentBlock = Blocks.POLISHED_DEEPSLATE;
                    postBlock = Blocks.DEEPSLATE_BRICK_WALL; carpetBlock = Blocks.GRAY_CARPET; roofBlock = Blocks.GRAY_WOOL;
                }
                case DESERT -> {
                    floorBlock = Blocks.SMOOTH_SANDSTONE; accentBlock = Blocks.CUT_SANDSTONE;
                    postBlock = Blocks.SANDSTONE_WALL; carpetBlock = Blocks.ORANGE_CARPET; roofBlock = Blocks.ORANGE_WOOL;
                }
                case NETHER -> {
                    floorBlock = Blocks.NETHER_BRICKS; accentBlock = Blocks.CRIMSON_PLANKS;
                    postBlock = Blocks.CRIMSON_STEM; carpetBlock = Blocks.RED_CARPET; roofBlock = Blocks.RED_WOOL;
                }
                case END -> {
                    floorBlock = Blocks.PURPUR_BLOCK; accentBlock = Blocks.END_STONE_BRICKS;
                    postBlock = Blocks.PURPUR_PILLAR; carpetBlock = Blocks.PURPLE_CARPET; roofBlock = Blocks.PURPLE_WOOL;
                }
                default -> {} // plains default
            }
        }

        // Clear area (9x9, 6 high)
        for (int x = 0; x < 9; x++)
            for (int z = 0; z < 9; z++)
                for (int y = 0; y <= 6; y++)
                    world.setBlockState(new BlockPos(ox + x, oy + y, oz + z), air, sf);

        // Floor
        for (int x = 0; x < 9; x++)
            for (int z = 0; z < 9; z++)
                world.setBlockState(new BlockPos(ox + x, oy, oz + z), floorBlock.getDefaultState(), sf);

        // Accent border
        for (int x = 0; x < 9; x++) {
            world.setBlockState(new BlockPos(ox + x, oy, oz), accentBlock.getDefaultState(), sf);
            world.setBlockState(new BlockPos(ox + x, oy, oz + 8), accentBlock.getDefaultState(), sf);
        }
        for (int z = 0; z < 9; z++) {
            world.setBlockState(new BlockPos(ox, oy, oz + z), accentBlock.getDefaultState(), sf);
            world.setBlockState(new BlockPos(ox + 8, oy, oz + z), accentBlock.getDefaultState(), sf);
        }

        // Center carpet rug (3x3)
        for (int x = 3; x <= 5; x++)
            for (int z = 3; z <= 5; z++)
                world.setBlockState(new BlockPos(ox + x, oy + 1, oz + z), carpetBlock.getDefaultState(), sf);

        // Corner posts (4 corners, 3 blocks tall)
        for (int y = 1; y <= 3; y++) {
            world.setBlockState(new BlockPos(ox, oy + y, oz), postBlock.getDefaultState(), sf);
            world.setBlockState(new BlockPos(ox + 8, oy + y, oz), postBlock.getDefaultState(), sf);
            world.setBlockState(new BlockPos(ox, oy + y, oz + 8), postBlock.getDefaultState(), sf);
            world.setBlockState(new BlockPos(ox + 8, oy + y, oz + 8), postBlock.getDefaultState(), sf);
        }

        // Canopy roof (wool at y+4, spanning 5x5 center)
        for (int x = 2; x <= 6; x++)
            for (int z = 2; z <= 6; z++)
                world.setBlockState(new BlockPos(ox + x, oy + 4, oz + z), roofBlock.getDefaultState(), sf);

        // Lanterns hanging from canopy corners
        world.setBlockState(new BlockPos(ox + 2, oy + 3, oz + 2), Blocks.LANTERN.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 6, oy + 3, oz + 2), Blocks.LANTERN.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 2, oy + 3, oz + 6), Blocks.LANTERN.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 6, oy + 3, oz + 6), Blocks.LANTERN.getDefaultState(), sf);

        // Lanterns on top of corner posts
        world.setBlockState(new BlockPos(ox, oy + 4, oz), Blocks.LANTERN.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 8, oy + 4, oz), Blocks.LANTERN.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox, oy + 4, oz + 8), Blocks.LANTERN.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 8, oy + 4, oz + 8), Blocks.LANTERN.getDefaultState(), sf);

        // Flower pots on inner posts
        world.setBlockState(new BlockPos(ox + 2, oy + 1, oz + 1), Blocks.POTTED_POPPY.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 6, oy + 1, oz + 1), Blocks.POTTED_DANDELION.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 2, oy + 1, oz + 7), Blocks.POTTED_BLUE_ORCHID.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 6, oy + 1, oz + 7), Blocks.POTTED_ALLIUM.getDefaultState(), sf);

        // Barrel "display shelves" along back wall
        world.setBlockState(new BlockPos(ox + 3, oy + 1, oz + 7), Blocks.BARREL.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 5, oy + 1, oz + 7), Blocks.BARREL.getDefaultState(), sf);

        // Lectern near entrance
        world.setBlockState(new BlockPos(ox + 4, oy + 1, oz + 1), Blocks.LECTERN.getDefaultState(), sf);
    }

    private void sendMessageTo(ServerPlayerEntity p, String msg) {
        p.sendMessage(net.minecraft.text.Text.literal(msg), false);
    }

    public void handleTraderBuy(ServerPlayerEntity player, int tradeIndex) {
        // No longer used — vanilla trading handles purchases directly
    }

    public void handleTraderDone(ServerPlayerEntity player) {
        // Collect remaining emerald items from inventory back into currency
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(world);
        int collectedEmeralds = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == Items.EMERALD) {
                collectedEmeralds += stack.getCount();
                player.getInventory().removeStack(i);
            }
        }
        data.emeralds = collectedEmeralds;
        data.markDirty();

        // Despawn the trader
        if (spawnedTrader != null && spawnedTrader.isAlive()) {
            spawnedTrader.discard();
            spawnedTrader = null;
        }

        activeTraderOffer = null;

        // Start the pending next level — transition whole party
        if (pendingNextLevelDef != null && pendingBiome != null) {
            GridArena nextArena = buildArena(world, pendingNextLevelDef);
            transitionPartyToArena(player, nextArena, pendingNextLevelDef);
            pendingNextLevelDef = null;
            pendingBiome = null;
        }
    }

    /** Create EnterCombatPayload, consuming any pending camera yaw from ArenaBuilder. */
    private static EnterCombatPayload makeEnterPayload(GridArena arena) {
        BlockPos origin = arena.getOrigin();
        float yaw = com.crackedgames.craftics.level.ArenaBuilder.consumePendingCameraYaw();
        return new EnterCombatPayload(
            origin.getX(), origin.getY(), origin.getZ(),
            arena.getWidth(), arena.getHeight(), yaw
        );
    }

    private String serializeTrades(TraderSystem.TraderOffer offer) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < offer.trades().size(); i++) {
            TraderSystem.Trade t = offer.trades().get(i);
            if (i > 0) sb.append("|");
            String itemId = net.minecraft.registry.Registries.ITEM.getId(t.item().getItem()).toString();
            sb.append(itemId).append("~").append(t.item().getCount())
              .append("~").append(t.emeraldCost()).append("~").append(t.description());
        }
        return sb.toString();
    }

    private static List<ItemStack> getMobDrops(String entityTypeId) {
        LootPool pool = switch (entityTypeId) {
            case "minecraft:cow" -> new LootPool()
                .add(Items.LEATHER, 5).add(Items.BEEF, 5);
            case "minecraft:zombie" -> new LootPool()
                .add(Items.ROTTEN_FLESH, 6).add(Items.IRON_NUGGET, 2);
            case "minecraft:skeleton" -> new LootPool()
                .add(Items.BONE, 5).add(Items.ARROW, 5).add(Items.BOW, 1);
            case "minecraft:creeper" -> new LootPool()
                .add(Items.GUNPOWDER, 8).add(Items.TNT, 1);
            case "minecraft:spider" -> new LootPool()
                .add(Items.STRING, 6).add(Items.SPIDER_EYE, 3);
            case "minecraft:pig" -> new LootPool()
                .add(Items.PORKCHOP, 8).add(Items.LEATHER, 2);
            case "minecraft:sheep" -> new LootPool()
                .add(Items.MUTTON, 5).add(Items.WHITE_WOOL, 5);
            case "minecraft:chicken" -> new LootPool()
                .add(Items.CHICKEN, 5).add(Items.FEATHER, 5);
            case "minecraft:goat" -> new LootPool()
                .add(Items.LEATHER, 5).add(Items.MUTTON, 3);
            default -> null;
        };
        return pool != null ? pool.roll(1, 2, 1, 3) : List.of();
    }

    public void endCombat() {
        if (!active) return;
        clearHighlights();
        clearWarningBlocks();

        // Re-enable natural health regeneration and restore survival mode for all participants
        if (player != null) {
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            world.getGameRules().get(net.minecraft.world.GameRules.NATURAL_REGENERATION).set(true, world.getServer());
        }
        for (ServerPlayerEntity p : getAllParticipants()) {
            if (p != null) {
                p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            }
        }

        // Remove the Move feather from all participants' inventories
        for (ServerPlayerEntity p : getAllParticipants()) {
            if (p != null) {
                for (int i = 0; i < p.getInventory().size(); i++) {
                    ItemStack stack = p.getInventory().getStack(i);
                    if (stack.getItem() == Items.FEATHER) {
                        p.getInventory().removeStack(i);
                    }
                }
            }
        }

        // Dismount and discard mount mob
        if (playerMounted && mountMob != null) {
            if (player != null) player.stopRiding();
            mountMob.discard();
            playerMounted = false;
            mountMob = null;
        }

        // Discard remaining arena mobs
        for (CombatEntity e : enemies) {
            if (e.getMobEntity() != null && e.getMobEntity().isAlive()) {
                e.getMobEntity().discard();
            }
        }

        // Discard any lingering potion clouds tagged as arena entities
        if (player != null) {
            ServerWorld cleanupWorld = (ServerWorld) player.getEntityWorld();
            for (net.minecraft.entity.Entity entity : cleanupWorld.iterateEntities()) {
                if (entity instanceof net.minecraft.entity.AreaEffectCloudEntity
                        && entity.getCommandTags().contains("craftics_arena")) {
                    entity.discard();
                }
            }
        }

        // Release force-loaded arena chunks
        if (!forcedChunks.isEmpty() && player != null) {
            ServerWorld chunkWorld = (ServerWorld) player.getEntityWorld();
            for (net.minecraft.util.math.ChunkPos cp : forcedChunks) {
                chunkWorld.setChunkForced(cp.x, cp.z, false);
            }
            forcedChunks.clear();
        }

        // Clear all vanilla status effects from combat (potion particles) for all participants
        for (ServerPlayerEntity p : getAllParticipants()) {
            if (p != null) {
                p.clearStatusEffects();
            }
        }

        // Clean up party tracking
        cleanupPartyTracking();

        arena = null;
        enemies = null;
        player = null;
        movePath = null;
        active = false;
        testRange = false;
        combatEffects.clear();
        tileEffects.clear();

        CrafticsMod.LOGGER.info("Combat ended.");
    }

    public void refreshHighlights() {
        if (!active || player == null || phase != CombatPhase.PLAYER_TURN) return;

        // --- Build move tiles ---
        var held = player.getMainHandStack().getItem();
        boolean isMoveMode = (held == Items.FEATHER);

        java.util.List<Integer> moveList = new java.util.ArrayList<>();
        java.util.List<Integer> attackList = new java.util.ArrayList<>();

        if (isMoveMode) {
            if (movePointsRemaining > 0) {
                java.util.Set<GridPos> reachable = Pathfinding.getReachableTiles(arena, arena.getPlayerGridPos(), movePointsRemaining);
                for (GridPos gp : reachable) {
                    moveList.add(gp.x());
                    moveList.add(gp.z());
                }
            }
        } else {
            // --- Build attack tiles ---
            int range = PlayerCombatStats.getWeaponRange(player);
            GridPos playerPos = arena.getPlayerGridPos();
            java.util.Set<CombatEntity> highlighted = new java.util.HashSet<>();
            for (var entry : arena.getOccupants().entrySet()) {
                CombatEntity enemy = entry.getValue();
                if (!enemy.isAlive() || highlighted.contains(enemy)) continue;

                boolean inRange;
                if (range == PlayerCombatStats.RANGE_CROSSBOW_ROOK) {
                    inRange = false;
                    for (var tile : GridArena.getOccupiedTiles(enemy.getGridPos(), enemy.getSize())) {
                        if (PlayerCombatStats.isInCrossbowLine(arena, playerPos, tile)) { inRange = true; break; }
                    }
                } else {
                    inRange = enemy.minDistanceTo(playerPos) <= range;
                }

                if (inRange) {
                    highlighted.add(enemy);
                    for (var tile : GridArena.getOccupiedTiles(enemy.getGridPos(), enemy.getSize())) {
                        attackList.add(tile.x());
                        attackList.add(tile.z());
                    }
                }
            }
        }

        // --- Build danger tiles ---
        java.util.List<Integer> dangerList = new java.util.ArrayList<>();
        if (CrafticsMod.CONFIG.showEnemyRangeHints()) {
            GridPos playerPos = arena.getPlayerGridPos();
            java.util.Set<GridPos> dangerTiles = new java.util.HashSet<>();
            for (CombatEntity enemy : enemies) {
                if (!enemy.isAlive() || enemy.isAlly()) continue;
                GridPos ePos = enemy.getGridPos();
                int speed = enemy.getMoveSpeed();
                int eRange = enemy.getRange();
                for (int dx = -(speed + eRange); dx <= speed + eRange; dx++) {
                    for (int dz = -(speed + eRange); dz <= speed + eRange; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) > speed + eRange) continue;
                        GridPos tile = new GridPos(ePos.x() + dx, ePos.z() + dz);
                        if (!arena.isInBounds(tile)) continue;
                        if (tile.equals(playerPos)) continue;
                        dangerTiles.add(tile);
                    }
                }
            }
            for (GridPos gp : dangerTiles) {
                dangerList.add(gp.x());
                dangerList.add(gp.z());
            }
        }

        // --- Build enemy grid map ---
        java.util.List<Integer> enemyMapList = new java.util.ArrayList<>();
        StringBuilder enemyTypesBuilder = new StringBuilder();
        java.util.Set<CombatEntity> seen = new java.util.HashSet<>();
        for (var entry : arena.getOccupants().entrySet()) {
            CombatEntity enemy = entry.getValue();
            if (!enemy.isAlive() || seen.contains(enemy)) continue;
            seen.add(enemy);
            GridPos gp = enemy.getGridPos();
            enemyMapList.add(gp.x());
            enemyMapList.add(gp.z());
            enemyMapList.add(enemy.getEntityId());
            if (enemyTypesBuilder.length() > 0) enemyTypesBuilder.append("|");
            enemyTypesBuilder.append(enemy.getEntityTypeId());
        }

        // Build boss attack warning tiles from pending warnings
        java.util.List<Integer> warningList = new java.util.ArrayList<>();
        for (PendingBossWarning pw : pendingBossWarnings) {
            if (pw.ability().warningTiles() != null) {
                for (GridPos wt : pw.ability().warningTiles()) {
                    warningList.add(wt.x());
                    warningList.add(wt.z());
                }
            }
        }
        // Also include warnings from boss AIs that just set pendingWarning this turn
        for (CombatEntity enemy : enemies) {
            if (!enemy.isBoss() || !enemy.isAlive()) continue;
            var ai = AIRegistry.get(enemy.getAiKey());
            if (ai instanceof com.crackedgames.craftics.combat.ai.boss.BossAI bai && bai.getPendingWarning() != null) {
                for (GridPos wt : bai.getPendingWarning().getAffectedTiles()) {
                    warningList.add(wt.x());
                    warningList.add(wt.z());
                }
            }
        }

        // Convert lists to int arrays
        int[] moveArr = moveList.stream().mapToInt(Integer::intValue).toArray();
        int[] attackArr = attackList.stream().mapToInt(Integer::intValue).toArray();
        int[] dangerArr = dangerList.stream().mapToInt(Integer::intValue).toArray();
        int[] warningArr = warningList.stream().mapToInt(Integer::intValue).toArray();
        int[] enemyMapArr = enemyMapList.stream().mapToInt(Integer::intValue).toArray();

        sendToAllParty(new TileSetPayload(
            moveArr, attackArr, dangerArr, warningArr, enemyMapArr, enemyTypesBuilder.toString()
        ));

        // Auto-end turn when AP is depleted (configurable)
        if (CrafticsMod.CONFIG.autoEndTurn() && apRemaining <= 0 && movePointsRemaining <= 0) {
            handleEndTurn();
        }
    }

    /**
     * Preview what each enemy intends to do (runs AI without executing).
     */
    private void showEnemyIntentionPreviews() {
        for (CombatEntity enemy : enemies) {
            if (!enemy.isAlive() || enemy.isAlly()) continue;
            EnemyAI ai = AIRegistry.get(enemy.getEntityTypeId());
            EnemyAction action = ai.decideAction(enemy, arena, arena.getPlayerGridPos());
            String intent = switch (action) {
                case EnemyAction.Attack a -> "§c\u2694 Attack";
                case EnemyAction.MoveAndAttack ma -> "§c\u2794 Move + Attack";
                case EnemyAction.RangedAttack ra -> "§d\u27B3 Ranged Attack";
                case EnemyAction.Move m -> "§b\u2192 Move";
                case EnemyAction.Flee f -> "§e\u2190 Flee";
                case EnemyAction.AttackWithKnockback k -> "§c\u2694 Ram";
                case EnemyAction.AttackMob am -> "§6\u2694 Hunt prey";
                case EnemyAction.Idle i -> "§7\u23F8 Idle";
                default -> "§7?";
            };
            sendMessage("§8  " + enemy.getDisplayName() + ": " + intent);
        }
    }

    /**
     * Map a combat effect type to its vanilla MC status effect for particle removal.
     */
    private static net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> mapCombatToVanillaEffect(CombatEffects.EffectType type) {
        return switch (type) {
            case SPEED -> net.minecraft.entity.effect.StatusEffects.SPEED;
            case STRENGTH -> net.minecraft.entity.effect.StatusEffects.STRENGTH;
            case RESISTANCE -> net.minecraft.entity.effect.StatusEffects.RESISTANCE;
            case REGENERATION -> net.minecraft.entity.effect.StatusEffects.REGENERATION;
            case FIRE_RESISTANCE -> net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE;
            case INVISIBILITY -> net.minecraft.entity.effect.StatusEffects.INVISIBILITY;
            case ABSORPTION -> net.minecraft.entity.effect.StatusEffects.ABSORPTION;
            case POISON -> net.minecraft.entity.effect.StatusEffects.POISON;
            case SLOWNESS -> net.minecraft.entity.effect.StatusEffects.SLOWNESS;
            case WEAKNESS -> net.minecraft.entity.effect.StatusEffects.WEAKNESS;
            case WITHER -> net.minecraft.entity.effect.StatusEffects.WITHER;
            case BLINDNESS -> net.minecraft.entity.effect.StatusEffects.BLINDNESS;
            case MINING_FATIGUE -> net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE;
            case LEVITATION -> net.minecraft.entity.effect.StatusEffects.LEVITATION;
            case DARKNESS -> net.minecraft.entity.effect.StatusEffects.DARKNESS;
            case LUCK -> net.minecraft.entity.effect.StatusEffects.LUCK;
            case HASTE -> net.minecraft.entity.effect.StatusEffects.HASTE;
            case SLOW_FALLING -> net.minecraft.entity.effect.StatusEffects.SLOW_FALLING;
            case WATER_BREATHING -> net.minecraft.entity.effect.StatusEffects.WATER_BREATHING;
            case BURNING -> null; // no vanilla equivalent
        };
    }

    private void clearHighlights() {
        sendToAllParty(new TileSetPayload(
            new int[0], new int[0], new int[0], new int[0], new int[0], ""
        ));
    }

    private void sendSync() {
        if (player == null || !active) return;

        // Build enemy HP data: [id, hp, maxHp, id, hp, maxHp, ...]
        List<CombatEntity> aliveEnemies = enemies.stream().filter(CombatEntity::isAlive).toList();
        int[] enemyData = new int[aliveEnemies.size() * 3];
        StringBuilder typeIds = new StringBuilder();
        for (int i = 0; i < aliveEnemies.size(); i++) {
            CombatEntity e = aliveEnemies.get(i);
            enemyData[i * 3] = e.getEntityId();
            enemyData[i * 3 + 1] = e.getCurrentHp();
            enemyData[i * 3 + 2] = e.getMaxHp();
            if (i > 0) typeIds.append("|");
            typeIds.append(e.getEntityTypeId());
            // Append boss metadata if this is a boss enemy
            if (e.isBoss()) {
                typeIds.append(";boss=").append(e.getDisplayName());
                typeIds.append(";atk=").append(e.getAttackPower());
                typeIds.append(";def=").append(e.getDefense());
                typeIds.append(";spd=").append(e.getMoveSpeed());
                typeIds.append(";range=").append(e.getRange());
            }
            // Append status effects after type ID with ; separator
            StringBuilder efx = new StringBuilder();
            if (e.isStunned()) efx.append(";Stunned");
            if (e.isEnraged()) efx.append(";Enraged");
            if (e.getSpeedBonus() < 0) efx.append(";Slowed");
            if (e.getPoisonTurns() > 0) efx.append(";Poisoned(" + e.getPoisonTurns() + "t)");
            if (e.getAttackPenalty() > 0) efx.append(";Weakened(-" + e.getAttackPenalty() + "ATK)");
            if (e.getMobEntity() != null && e.getMobEntity().isOnFire()) efx.append(";Burning");
            typeIds.append(efx);
        }

        // Calculate max AP and max Speed from progression
        PlayerProgression syncProg = PlayerProgression.get((ServerWorld) player.getEntityWorld());
        PlayerProgression.PlayerStats syncStats = syncProg.getStats(player);
        int maxAp = syncStats.getEffective(PlayerProgression.Stat.AP)
            + PlayerCombatStats.getSetApBonus(player);
        int maxSpeed = syncStats.getEffective(PlayerProgression.Stat.SPEED)
            + PlayerCombatStats.getSetSpeedBonus(player)
            + (playerMounted ? MOUNT_SPEED_BONUS : 0);

        sendToAllParty(new CombatSyncPayload(
            phase.ordinal(), apRemaining, movePointsRemaining,
            getPlayerHp(), (int) player.getMaxHealth(), turnNumber,
            maxAp, maxSpeed, enemyData, typeIds.toString(),
            combatEffects.getDisplayString(), killStreak
        ));
    }

    private void sendMessage(String msg) {
        // Broadcast action bar message to all party participants
        for (ServerPlayerEntity p : getAllParticipants()) {
            if (p != null) {
                p.sendMessage(Text.literal(msg), true);
            }
        }
    }

    // --- Kill streak tracking ---
    private int killStreak = 0;
    private boolean killedThisTurn = false; // track if player got a kill this turn
    private int totalDamageDealt = 0;
    private int totalEnemiesKilled = 0;

    /** Called when an enemy is killed. */
    private void onEnemyKilled(CombatEntity enemy) {
        killStreak++;
        killedThisTurn = true;
        totalEnemiesKilled++;
        if (killStreak >= 3) {
            int bonusEmeralds = killStreak - 2; // 3-streak = +1, 4-streak = +2, etc.
            sendMessageTo(player, "\u00a76\u00a7l\u2694 " + killStreak + "x KILL STREAK! +" + bonusEmeralds + " bonus emeralds!");
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            com.crackedgames.craftics.world.CrafticsSavedData data =
                com.crackedgames.craftics.world.CrafticsSavedData.get(world);
            data.emeralds += bonusEmeralds;
            data.markDirty();
        }
    }

    /** Called when player takes damage. Kill streaks no longer reset on damage — only on turns with no kills. */
    private void onPlayerDamaged() {
        // Kill streak is preserved through damage — only resets at end of turn with no kills
    }

    /** Calculate flanking bonus: +25% damage if attacking from behind enemy. */
    public static int calculateFlankingBonus(GridPos attackerPos, GridPos targetPos,
                                              GridPos targetFacing, int baseDamage) {
        if (targetFacing == null) return 0;
        // "Behind" = attacker is on the opposite side from where the target is facing
        int dx = attackerPos.x() - targetPos.x();
        int dz = attackerPos.z() - targetPos.z();
        int faceDx = targetFacing.x();
        int faceDz = targetFacing.z();
        // Dot product: negative means attacker is behind
        int dot = dx * faceDx + dz * faceDz;
        if (dot < 0) {
            return Math.max(1, baseDamage / 4); // +25% flanking bonus
        }
        return 0;
    }
}
