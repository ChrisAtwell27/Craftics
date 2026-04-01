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
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
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
import com.crackedgames.craftics.component.DeathProtectionComponent;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import com.crackedgames.craftics.achievement.AchievementManager;
import com.crackedgames.craftics.achievement.CombatAchievementTracker;

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

    /** Clear all static state between world loads (prevents leaking across saves in singleplayer). */
    public static void clearAll() {
        INSTANCES.clear();
        PARTY_COMBAT_LEADER.clear();
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
    private final List<Float> dyingMobStartScales = new ArrayList<>(); // original scale for gradual shrink
    private CombatPhase phase;
    private int playerDeathAnimTick = 0;
    private static final int PLAYER_DEATH_ANIM_TICKS = 60; // ~3 seconds at 20 TPS
    private int apRemaining;
    private int movePointsRemaining;
    private int turnNumber;
    private int peacefulTurnCount = 0; // consecutive turns with no hostile enemies
    private boolean endTurnHintSent = false; // avoid spamming the "press R" hint
    private ServerPlayerEntity player;

    // === Per-player turn rotation for party combat ===
    /** Ordered list of party member UUIDs who take turns this round. */
    private final List<java.util.UUID> turnQueue = new ArrayList<>();
    /** Index into turnQueue for whose sub-turn it currently is. */
    private int currentTurnIndex = 0;
    private final CombatEffects combatEffects = new CombatEffects();
    private CombatAchievementTracker achievementTracker = new CombatAchievementTracker();

    /** Shared event manager for this biome run (shared across party members). */
    private EventManager eventManager;

    /** The UUID of the world owner for this combat (determines arena/event positions). */
    private java.util.UUID worldOwnerUuid;

    /** The UUID of the player who owns the biome run data (leader who started the level). */
    private java.util.UUID leaderUuid;

    /** Persisted pet data across level transitions within a biome run. */
    private record PetData(String entityType, int hp, int maxHp, int atk, int def, int speed, int range) {}
    private final List<PetData> savedPets = new ArrayList<>();

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

    /** Tracks party members who have died during this combat level (spectating). */
    private final java.util.Set<java.util.UUID> deadPartyMembers = new java.util.HashSet<>();

    /** Add a party member to this combat (includes leader — call for every participant). */
    public void addPartyMember(ServerPlayerEntity member) {
        if (partyPlayers.stream().noneMatch(p -> p.getUuid().equals(member.getUuid()))) {
            partyPlayers.add(member);
        }
        if (player != null && !member.getUuid().equals(player.getUuid())) {
            PARTY_COMBAT_LEADER.put(member.getUuid(), player.getUuid());
        }
    }

    /** Remove a party member (disconnect/leave). Also cleans up turn queue. */
    public void removePartyMember(java.util.UUID memberUuid) {
        partyPlayers.removeIf(p -> p.getUuid().equals(memberUuid));
        PARTY_COMBAT_LEADER.remove(memberUuid);
        turnQueue.remove(memberUuid);
        if (currentTurnIndex >= turnQueue.size() && !turnQueue.isEmpty()) {
            currentTurnIndex = 0;
        }
    }

    /**
     * Gracefully remove a single player from party combat (e.g. /home while in party).
     * Cleans up their state without ending combat for everyone else.
     * If the leaving player is the active fighter, transfers control.
     * If they're the last one, ends combat entirely.
     */
    public void leavePartyCombat(ServerPlayerEntity leaver) {
        if (!active) return;
        java.util.UUID leaverUuid = leaver.getUuid();

        // Remove feather from leaver's inventory
        for (int i = 0; i < leaver.getInventory().size(); i++) {
            if (leaver.getInventory().getStack(i).getItem() == Items.FEATHER) {
                leaver.getInventory().removeStack(i);
            }
        }
        leaver.clearStatusEffects();
        leaver.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);

        // Send exit combat to just this player
        if (leaver.networkHandler != null) {
            ServerPlayNetworking.send(leaver, new com.crackedgames.craftics.network.ExitCombatPayload(false));
        }

        // Remove from tracking
        deadPartyMembers.remove(leaverUuid);
        removePartyMember(leaverUuid);

        // Notify remaining players
        sendMessage("§e" + leaver.getName().getString() + " left the battle.");

        // If leaver was the active fighter, transfer control
        if (player != null && player.getUuid().equals(leaverUuid)) {
            ServerPlayerEntity nextAlive = null;
            for (ServerPlayerEntity member : partyPlayers) {
                if (!deadPartyMembers.contains(member.getUuid())
                        && member != null && !member.isRemoved() && !member.isDisconnected()) {
                    nextAlive = member;
                    break;
                }
            }
            if (nextAlive != null) {
                sendMessage("§e" + nextAlive.getName().getString() + " takes over the fight!");
                nextAlive.requestTeleport(player.getX(), player.getY(), player.getZ());
                this.player = nextAlive;
                PlayerProgression prog = PlayerProgression.get((ServerWorld) player.getEntityWorld());
                PlayerProgression.PlayerStats pStats = prog.getStats(player);
                this.apRemaining = pStats.getEffective(PlayerProgression.Stat.AP)
                    + PlayerCombatStats.getSetApBonus(player);
                this.movePointsRemaining = pStats.getEffective(PlayerProgression.Stat.SPEED)
                    + PlayerCombatStats.getSetSpeedBonus(player);
                this.activeTrimScan = TrimEffects.scan(player);
                sendSync();
                refreshHighlights();
            } else {
                // Nobody left — end combat
                endCombat();
            }
        }

        // If only one player remains, combat continues normally for them
        if (partyPlayers.size() == 0) {
            endCombat();
        }
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
        deadPartyMembers.clear();
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

        // Revive any dead party members with 2 hearts for the next level
        for (ServerPlayerEntity member : members) {
            if (deadPartyMembers.remove(member.getUuid())) {
                member.setHealth(4); // 2 hearts
                member.clearStatusEffects();
                member.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
                sendMessageTo(member, "§a§l\u2726 REVIVED! §rYou're back with 2 hearts!");
            }
        }

        for (ServerPlayerEntity member : members) {
            member.requestTeleport(startPos.getX() + 0.5, startPos.getY(), startPos.getZ() + 0.5);
            ServerPlayNetworking.send(member, enterPayload);
            if (!member.getUuid().equals(leader.getUuid())) {
                member.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
            }
        }

        // Register party members BEFORE startCombat so that sendSync/highlights
        // inside startCombat reach all participants, not just the leader
        for (ServerPlayerEntity member : members) {
            addPartyMember(member);
        }

        startCombat(leader, newArena, newLevelDef);

        // Re-register PARTY_COMBAT_LEADER mappings now that this.player is set.
        // addPartyMember above ran while player was null (cleared by prior endCombat),
        // so the leader routing for non-leader members wasn't established.
        for (ServerPlayerEntity member : members) {
            if (!member.getUuid().equals(leader.getUuid())) {
                PARTY_COMBAT_LEADER.put(member.getUuid(), leader.getUuid());
            }
        }

        // Respawn tamed pets from previous level
        spawnSavedPets();

        // startCombat only sends chunks/feather/title to the leader — replicate for party members
        if (members.size() > 1) {
            ServerWorld world = (ServerWorld) leader.getEntityWorld();
            BlockPos origin = newArena.getOrigin();
            int chunkMargin = 48;
            int minCX = (origin.getX() - chunkMargin) >> 4;
            int maxCX = (origin.getX() + newArena.getWidth() + chunkMargin) >> 4;
            int minCZ = (origin.getZ() - chunkMargin) >> 4;
            int maxCZ = (origin.getZ() + newArena.getHeight() + chunkMargin) >> 4;

            for (ServerPlayerEntity member : members) {
                if (member.getUuid().equals(leader.getUuid())) continue;
                // Resend arena chunks so member can see the arena
                for (int cx = minCX; cx <= maxCX; cx++) {
                    for (int cz = minCZ; cz <= maxCZ; cz++) {
                        var chunk = world.getChunk(cx, cz);
                        if (chunk != null) {
                            member.networkHandler.chunkDataSender.add(chunk);
                        }
                    }
                }
                // Give Move feather
                ItemStack displaced = member.getInventory().getStack(8);
                if (!displaced.isEmpty()) {
                    int emptySlot = member.getInventory().getEmptySlot();
                    if (emptySlot != -1) {
                        member.getInventory().setStack(emptySlot, displaced);
                    }
                }
                ItemStack moveItem = new ItemStack(Items.FEATHER);
                moveItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal("\u00a7aMove"));
                member.getInventory().setStack(8, moveItem);
                member.getInventory().selectedSlot = 8;
                // Show level title
                member.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(5, 40, 15));
                member.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                    Text.literal("\u00a7e" + newLevelDef.getName())));
            }
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

    // Pending TNT explosions — placed this turn, detonate at the start of next round
    private record PendingTnt(GridPos tile, BlockPos blockPos) {}
    private final List<PendingTnt> pendingTnts = new ArrayList<>();

    // Pottery Sherd spell state — Prize sherd sets this, consumed on next attack
    private boolean tripleDamageNextAttack = false;

    // Active armor trim bonuses for current combat
    private TrimEffects.TrimScan activeTrimScan = null;
    public TrimEffects.TrimScan getTrimScan() { return activeTrimScan; }

    // Track last dropped trim to avoid back-to-back duplicates
    private static net.minecraft.item.Item lastDroppedTrim = null;

    // Test range mode — infinite AP/movement, no rewards or penalties
    private boolean testRange = false;
    public boolean isTestRange() { return testRange; }

    // Dropped trident tracking — when thrown without Loyalty, the trident lands on the arena
    private GridPos droppedTridentPos = null;
    private ItemStack droppedTridentStack = null;
    private net.minecraft.entity.ItemEntity droppedTridentEntity = null; // visual item entity on the ground
    public GridPos getDroppedTridentPos() { return droppedTridentPos; }

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
        achievementTracker.recordPlayerTookDamage();
        // Totem check is handled in handlePlayerDeathOrGameOver()
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
    private enum EnemyTurnState { DECIDING, MOVING, ANIMATING, ATTACKING, DONE }
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

    // Enemy attack animation (lunge toward target + arm swing)
    private static final int ATTACK_ANIM_LUNGE_TICKS = 5;
    private static final int ATTACK_ANIM_RETURN_TICKS = 4;
    private static final int ATTACK_ANIM_TOTAL_TICKS = ATTACK_ANIM_LUNGE_TICKS + ATTACK_ANIM_RETURN_TICKS;
    private static final double ATTACK_LUNGE_DISTANCE = 0.55;
    // Ranged attack animation (lean back to aim, then snap forward on release)
    private static final int RANGED_ANIM_DRAW_TICKS = 6;
    private static final int RANGED_ANIM_RELEASE_TICKS = 3;
    private static final int RANGED_ANIM_TOTAL_TICKS = RANGED_ANIM_DRAW_TICKS + RANGED_ANIM_RELEASE_TICKS;
    private static final double RANGED_LEAN_DISTANCE = 0.35;
    private int attackAnimTick;
    private double attackAnimOriginX, attackAnimOriginY, attackAnimOriginZ;
    private double attackAnimLungeX, attackAnimLungeZ; // direction unit vector
    private boolean attackAnimSwung; // whether arm swing has been triggered

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
            zombie.noClip = true;
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

        // Give Move feather — move existing slot 8 item elsewhere first
        ItemStack displaced = player.getInventory().getStack(8);
        if (!displaced.isEmpty()) {
            int emptySlot = player.getInventory().getEmptySlot();
            if (emptySlot != -1) {
                player.getInventory().setStack(emptySlot, displaced);
            }
            // If inventory is full, the item stays — feather overwrites it
        }
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
        this.achievementTracker = new CombatAchievementTracker();
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
        // Preserve HP ratio so continuing between battles doesn't full-heal
        int vitalityPoints = pStats.getPoints(PlayerProgression.Stat.VITALITY);
        int trimHpBonus = activeTrimScan.get(TrimEffects.Bonus.MAX_HP); // +2 per piece
        int totalHpBonusLevels = vitalityPoints + trimHpBonus;
        if (totalHpBonusLevels > 0) {
            float hpRatio = player.getMaxHealth() > 0 ? player.getHealth() / player.getMaxHealth() : 1.0f;
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.HEALTH_BOOST, 999999, totalHpBonusLevels - 1));
            player.setHealth(Math.max(1, hpRatio * player.getMaxHealth()));
        }

        // Unfreeze any hub-applied potion effects now that combat started
        combatEffects.unfreezeAll(5); // default 5 turns for hub potions

        ServerWorld world = (ServerWorld) player.getEntityWorld();

        // Disable natural health regeneration during combat
        world.getGameRules().get(net.minecraft.world.GameRules.NATURAL_REGENERATION).set(false, world.getServer());

        // Force-load arena chunks + surrounding chunks to prevent visual artifacts.
        // Extra margin (48 blocks / 3 chunks) ensures the void around the arena renders
        // as clean air rather than showing chunk-loading glitches at the edges.
        BlockPos origin = arena.getOrigin();
        forcedChunks.clear();
        int chunkMargin = 48;
        int minCX = (origin.getX() - chunkMargin) >> 4;
        int maxCX = (origin.getX() + arena.getWidth() + chunkMargin) >> 4;
        int minCZ = (origin.getZ() - chunkMargin) >> 4;
        int maxCZ = (origin.getZ() + arena.getHeight() + chunkMargin) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                world.setChunkForced(cx, cz, true);
                forcedChunks.add(new net.minecraft.util.math.ChunkPos(cx, cz));
            }
        }

        // Resend all arena chunks to the player so they see the full schematic.
        // This is critical for level 2+ where the arena is built before the player
        // teleports — the ArenaBuilder's resend may not reach a player still at the
        // old position, causing only game-tile overlays (water/gravel) to appear.
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                var chunk = world.getChunk(cx, cz);
                if (chunk != null) {
                    player.networkHandler.chunkDataSender.add(chunk);
                }
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
        float ngMult = ngData.getPlayerData(player.getUuid()).getNgPlusMultiplier();

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
        // Check if this is a ghast boss fight — if so, skip regular ghast spawns
        boolean isGhastBossFight = "minecraft:ghast".equals(bossEntityTypeId);

        boolean bossSpawned = false;
        for (LevelDefinition.EnemySpawn spawn : levelDef.getEnemySpawns()) {
            // In ghast boss fights, skip all regular ghasts (only the boss ghast spawns)
            boolean isBossSpawn = !bossSpawned && bossEntityTypeId != null
                && spawn.entityTypeId().equals(bossEntityTypeId);
            if (isGhastBossFight && "minecraft:ghast".equals(spawn.entityTypeId()) && !isBossSpawn) {
                continue;
            }

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
                preMob.noClip = true;
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
                // Ghasts float 1 block higher so they don't phase into the ground
                double spawnY = spawnPos.getY() + ("minecraft:ghast".equals(spawn.entityTypeId()) ? 1.0 : 0.0);
                mob.refreshPositionAndAngles(
                    spawnPos.getX() + offset, spawnY, spawnPos.getZ() + offset,
                    mob.getYaw(), mob.getPitch()
                );
                mob.setAiDisabled(true);
                mob.setInvulnerable(true);
                mob.setNoGravity(true);
                mob.noClip = true;
                mob.setPersistent();
                mob.setHealth(mob.getMaxHealth()); // Ensure full vanilla health after spawn
                mob.addCommandTag("craftics_arena");

                // Scale down naturally oversized mobs to fit their grid size
                if ("minecraft:ghast".equals(spawn.entityTypeId())) {
                    scaleBoss(mob, 0.5); // Vanilla ghast is 4x4x4; 0.5 → ~2x2 blocks
                    // Ghasts must face a cardinal direction — snap to nearest 90°
                    float ghastYaw = snapToCardinalYaw(mob.getYaw());
                    mob.setYaw(ghastYaw);
                    mob.setHeadYaw(ghastYaw);
                }

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

        // Give the player a named "Move" feather in slot 8 — move existing item elsewhere first
        ItemStack displaced2 = player.getInventory().getStack(8);
        if (!displaced2.isEmpty()) {
            int emptySlot2 = player.getInventory().getEmptySlot();
            if (emptySlot2 != -1) {
                player.getInventory().setStack(emptySlot2, displaced2);
            }
        }
        ItemStack moveItem = new ItemStack(Items.FEATHER);
        moveItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§aMove"));
        player.getInventory().setStack(8, moveItem);
        player.getInventory().selectedSlot = 8; // start in move mode
        lastHeldItem = Items.FEATHER; // Reset weapon tracking so next slot change triggers refresh

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

        // Create a no-collision team for all arena entities to prevent player pushing
        setupNoCollisionTeam();

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
        CrafticsSavedData.PlayerData startPd = startData.getPlayerData(player.getUuid());
        startPd.inCombat = true;
        startData.markDirty();

        // Track which player owns the biome run data
        this.leaderUuid = player.getUuid();

        // Build turn queue now so player 2 gets their turn in the first cycle
        if (partyPlayers.size() > 1) {
            rebuildTurnQueue();
        }
    }

    public void handleAction(CombatActionPayload action, java.util.UUID senderUuid) {
        if (!active) return;

        if (phase != CombatPhase.PLAYER_TURN) return;

        // In party combat, only the active turn player can act
        if (!turnQueue.isEmpty() && player != null && !player.getUuid().equals(senderUuid)) {
            // Not your turn — silently ignore
            return;
        }

        // Block input during spell cast animations (particles still staging)
        if (spellAnimCooldown > 0) return;

        switch (action.actionType()) {
            case CombatActionPayload.ACTION_MOVE -> handleMove(new GridPos(action.targetX(), action.targetZ()));
            case CombatActionPayload.ACTION_ATTACK -> handleAttack(action.targetEntityId());
            case CombatActionPayload.ACTION_END_TURN -> handleEndTurn();
            case CombatActionPayload.ACTION_USE_ITEM -> handleUseItem(new GridPos(action.targetX(), action.targetZ()));
        }
    }

    /** Legacy overload for solo combat / internal calls. */
    public void handleAction(CombatActionPayload action) {
        handleAction(action, player != null ? player.getUuid() : null);
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
        // Turtle helmet set bonus: water tiles are walkable
        if (player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).getItem() == Items.TURTLE_HELMET) {
            return true;
        }
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

        // Find target first (needed for trident mode detection)
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

        GridPos pPos = arena.getPlayerGridPos();
        GridPos tPos = target.getGridPos();

        // === TRIDENT MODE DETECTION ===
        boolean isTridentMelee = false;
        boolean isTridentThrow = false;
        int tridentRiptideLevel = 0;
        int tridentChannelingLevel = 0;
        boolean tridentHasLoyalty = false;
        // For multi-tile mobs, use the nearest occupied tile for line/distance checks
        GridPos tridentAimTile = tPos;

        if (weapon == Items.TRIDENT) {
            tridentAimTile = target.nearestTileTo(pPos);
            int chebyDist = Math.max(Math.abs(pPos.x() - tridentAimTile.x()), Math.abs(pPos.z() - tridentAimTile.z()));
            tridentRiptideLevel = PlayerCombatStats.getRiptide(player);
            tridentChannelingLevel = PlayerCombatStats.getChanneling(player);
            tridentHasLoyalty = PlayerCombatStats.getLoyalty(player) > 0;

            if (chebyDist <= 1) {
                isTridentMelee = true;
            } else if (tridentRiptideLevel > 0) {
                // Riptide: validate direction is straight/diagonal, then dash
                if (!PlayerCombatStats.isInTridentLine(pPos, tridentAimTile)) {
                    sendMessage("§cRiptide requires a straight or diagonal line!");
                    return;
                }
                if (apRemaining < 2) {
                    sendMessage("§cNeed 2 AP for Riptide dash! (have " + apRemaining + ")");
                    return;
                }
                handleRiptideDash(tridentRiptideLevel, pPos, tridentAimTile);
                return;
            } else {
                isTridentThrow = true;
                if (!PlayerCombatStats.isInTridentLine(pPos, tridentAimTile)) {
                    sendMessage("§cTrident can only be thrown in straight or diagonal lines!");
                    return;
                }
                int throwDist = Math.max(Math.abs(tridentAimTile.x() - pPos.x()), Math.abs(tridentAimTile.z() - pPos.z()));
                if (throwDist > PlayerCombatStats.TRIDENT_THROW_RANGE) {
                    sendMessage("§cOut of throw range! (max " + PlayerCombatStats.TRIDENT_THROW_RANGE + " tiles)");
                    return;
                }
            }
        }

        // AP cost: trident melee = 1, trident throw = 2, others from WeaponAbility
        int attackCost;
        if (isTridentMelee) {
            attackCost = 1;
        } else if (isTridentThrow) {
            attackCost = 2;
        } else {
            attackCost = WeaponAbility.getAttackCost(weapon);
        }
        if (apRemaining < attackCost) {
            sendMessage("§cNeed " + attackCost + " AP to attack! (have " + apRemaining + ")");
            return;
        }

        // Range check (scaffold tile grants +1 range for ranged) — trident range already validated above
        if (weapon != Items.TRIDENT) {
            int range = PlayerCombatStats.getWeaponRange(player);
            if (range > 1) range += getScaffoldRangeBonus(pPos);

            if (range == PlayerCombatStats.RANGE_CROSSBOW_ROOK) {
                if (!PlayerCombatStats.isInCrossbowLine(arena, pPos, tPos)) {
                    sendMessage("§cCrossbow requires a clear straight line! (N/S/E/W only)");
                    return;
                }
            } else {
                boolean isRanged = PlayerCombatStats.isBow(player);
                int dist;
                if (target.isBackgroundBoss()) {
                    // Background boss: find min distance to any tile it occupies
                    dist = Integer.MAX_VALUE;
                    for (var occEntry : arena.getOccupants().entrySet()) {
                        if (occEntry.getValue() == target) {
                            int d = isRanged ? pPos.manhattanDistance(occEntry.getKey())
                                : Math.max(Math.abs(pPos.x() - occEntry.getKey().x()),
                                           Math.abs(pPos.z() - occEntry.getKey().z()));
                            if (d < dist) dist = d;
                        }
                    }
                } else if (target.getSize() > 1) {
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

        // Special affinity: chance to not consume AP (hoes / Special weapons)
        boolean freeApProc = false;
        if (DamageType.fromWeapon(weapon) == DamageType.SPECIAL) {
            PlayerProgression.PlayerStats specialStats = PlayerProgression.get(
                (ServerWorld) player.getEntityWorld()).getStats(player);
            int specialPts = specialStats.getAffinityPoints(PlayerProgression.Affinity.SPECIAL);
            double freeApChance = specialPts * 0.03; // 0% base + 3% per point
            if (freeApChance > 0 && Math.random() < freeApChance) {
                freeApProc = true;
                sendMessage("\u00a7d\u2728 Magic Surge! Attack costs no AP!");
            }
        }
        if (!freeApProc) {
            apRemaining -= attackCost;
        }

        // Apply 10 durability damage per attack (tactical combat is hard on weapons)
        ItemStack weaponStack = player.getMainHandStack();
        if (weaponStack.isDamageable()) {
            weaponStack.damage(10, player, net.minecraft.entity.EquipmentSlot.MAINHAND);
            if (weaponStack.isEmpty() || weaponStack.getDamage() >= weaponStack.getMaxDamage()) {
                sendMessage("§c§lYour weapon broke!");
            }
        } else {
            // Non-durable weapons have a chance to break (consumed on use)
            double breakChance = WeaponAbility.getBreakChance(weapon);
            if (breakChance > 0 && Math.random() < breakChance) {
                weaponStack.decrement(1);
                int pct = (int) (breakChance * 100);
                sendMessage("§c§lYour " + weaponStack.getName().getString() + " broke! §7(" + pct + "% chance)");
            }
        }

        // Trident throw: remove from hand and drop on arena (unless Loyalty)
        if (isTridentThrow && !tridentHasLoyalty) {
            droppedTridentStack = player.getMainHandStack().copy();
            // Calculate landing tile: one tile before the nearest target tile along throw direction
            int ddx = Integer.signum(tridentAimTile.x() - pPos.x());
            int ddz = Integer.signum(tridentAimTile.z() - pPos.z());
            GridPos landPos = new GridPos(tridentAimTile.x() - ddx, tridentAimTile.z() - ddz);
            // If landing tile is the player's tile (distance 2 throw), step forward instead
            if (landPos.equals(pPos)) landPos = new GridPos(pPos.x() + ddx, pPos.z() + ddz);
            // If landing tile is occupied or not walkable, search backward along throw line
            if (arena.isOccupied(landPos) || !arena.isInBounds(landPos)
                    || arena.getTile(landPos) == null || !arena.getTile(landPos).isWalkable()) {
                // Walk backward from the aim tile to find the first free tile
                GridPos fallback = null;
                for (int step = 2; step <= 5; step++) {
                    GridPos candidate = new GridPos(tridentAimTile.x() - ddx * step, tridentAimTile.z() - ddz * step);
                    if (!arena.isInBounds(candidate)) break;
                    if (candidate.equals(pPos)) continue;
                    if (!arena.isOccupied(candidate)) {
                        var tile = arena.getTile(candidate);
                        if (tile != null && tile.isWalkable()) { fallback = candidate; break; }
                    }
                }
                landPos = fallback != null ? fallback : tridentAimTile;
            }
            droppedTridentPos = landPos;
            player.getMainHandStack().decrement(1);
            // Spawn a visual item entity on the ground
            ServerWorld dropWorld = (ServerWorld) player.getEntityWorld();
            BlockPos dropBlock = arena.gridToBlockPos(landPos);
            droppedTridentEntity = new net.minecraft.entity.ItemEntity(dropWorld,
                dropBlock.getX() + 0.5, dropBlock.getY() + 0.2, dropBlock.getZ() + 0.5,
                droppedTridentStack.copy());
            droppedTridentEntity.setPickupDelay(Integer.MAX_VALUE); // prevent vanilla pickup
            droppedTridentEntity.setNeverDespawn();
            dropWorld.spawnEntity(droppedTridentEntity);
            sendMessage("§3Trident lodges in the ground at (" + landPos.x() + ", " + landPos.z() + ")! Walk there to retrieve it.");
        }

        // Track weapon usage for achievements
        achievementTracker.recordWeaponUsed(weapon);
        achievementTracker.recordPlayerDealtDamage();

        // Pre-calculate damage before the delay (snapshot current stats)
        boolean isRangedWeapon = weapon == Items.BOW || weapon == Items.CROSSBOW;
        int progBonus = isRangedWeapon ? getProgRangedBonus() : getProgMeleeBonus();
        DamageType damageType = DamageType.fromWeapon(weapon);
        PlayerProgression.PlayerStats attackerStats = PlayerProgression.get(
            (ServerWorld) player.getEntityWorld()).getStats(player);
        int damageTypeBonus = DamageType.getTotalBonus(
            PlayerCombatStats.getArmorSet(player), activeTrimScan, combatEffects, damageType, attackerStats);
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
        final boolean fIsTridentThrow = isTridentThrow;
        final boolean fIsTridentMelee = isTridentMelee;
        final int fTridentChanneling = tridentChannelingLevel;
        final boolean fTridentHasLoyalty = tridentHasLoyalty;
        final boolean fLuckCrit = luckCrit;
        final String fTippedEffect = tippedEffect;
        final int fFireAspect = fireAspect;
        final boolean fHasBowFlame = hasBowFlame;
        final DamageType fDamageType = damageType;
        final int fDamageTypeBonus = damageTypeBonus;
        final Item fWeapon = weapon;
        final GridPos fPPos = pPos;
        final GridPos fTPos = tPos;
        final PlayerProgression.PlayerStats fAttackerStats = attackerStats;

        // Trigger client animation immediately (damage visuals are already delayed on client)
        // valueB = attacker entity ID so clients animate the correct player, not themselves
        sendToAllParty(new CombatEventPayload(
            CombatEventPayload.EVENT_DAMAGED, target.getEntityId(),
            0, player.getId(), tPos.x(), tPos.z()  // 0 damage = animation trigger, valueB = attacker entity ID
        ));

        // Spawn projectile immediately for ranged (it needs flight time)
        if (isRangedWeapon || isTridentThrow) {
            player.getWorld().playSound(null, player.getBlockPos(),
                weapon == Items.CROSSBOW ? net.minecraft.sound.SoundEvents.ITEM_CROSSBOW_SHOOT : net.minecraft.sound.SoundEvents.ENTITY_ARROW_SHOOT,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
            BlockPos playerBlock = arena.gridToBlockPos(pPos);
            BlockPos targetBlockForProj = arena.gridToBlockPos(tPos);
            ProjectileSpawner.spawnPlayerProjectile(
                (ServerWorld) player.getEntityWorld(), playerBlock, targetBlockForProj, isTridentThrow);
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

            // Projectile redirect: hitting a ghast fireball reverses its direction
            if (fTarget.isProjectile() && "ghast_fireball".equals(fTarget.getProjectileType())) {
                GridPos fireballPos = fTarget.getGridPos();
                GridPos deflectPlayerPos = arena.getPlayerGridPos();
                int rdx = fireballPos.x() - deflectPlayerPos.x();
                int rdz = fireballPos.z() - deflectPlayerPos.z();
                // Normalize to cardinal direction (away from the player)
                if (Math.abs(rdx) >= Math.abs(rdz)) {
                    rdx = Integer.signum(rdx); rdz = 0;
                } else {
                    rdx = 0; rdz = Integer.signum(rdz);
                }
                if (rdx == 0 && rdz == 0) { rdx = 0; rdz = -1; } // fallback
                fTarget.setProjectileDirX(rdx);
                fTarget.setProjectileDirZ(rdz);
                fTarget.setProjectileRedirected(true);
                sendMessage("§a\u2604 You deflect the fireball!");
                player.getWorld().playSound(null, player.getBlockPos(),
                    net.minecraft.sound.SoundEvents.ENTITY_GHAST_SHOOT,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.2f);
                sendSync();
                return;
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
            WeaponAbility.AttackResult abilityResult = WeaponAbility.applyAbility(player, fWeapon, fTarget, arena, fBaseDamage, fAttackerStats);
            for (String msg : abilityResult.messages()) sendMessage(msg);

            // Track achievement stats from weapon ability results
            achievementTracker.recordDamageDealt(dealt);
            int extraHits = abilityResult.extraTargets().size();
            if (fWeapon == Items.CROSSBOW && extraHits > 0) {
                achievementTracker.recordPierceTargets(1 + extraHits); // original + pierced
            }
            if (fWeapon == Items.MACE && extraHits > 0) {
                achievementTracker.recordShockwaveTargets(extraHits);
            }
            // Check ability messages for specific achievement events
            for (String msg : abilityResult.messages()) {
                if (msg.contains("Sweep!")) achievementTracker.recordSweepTargets(extraHits);
                if (msg.contains("CRITICAL HIT")) achievementTracker.recordCrit();
                if (msg.contains("EXECUTE!") && !fTarget.isAlive()) achievementTracker.recordExecutionKill();
                if (msg.contains("ARMOR CRUSH")) {
                    achievementTracker.recordArmorCrush(fTarget.getDefense());
                }
                if (msg.contains("STUNNED")) achievementTracker.recordStun(fTarget.getEntityId());
                if (msg.contains("Splash!")) achievementTracker.recordCoralFanTargets(extraHits);
                if (msg.contains("Confused!")) achievementTracker.recordEnemyConfused(fTarget.getEntityId());
            }
            // Reset crit streak on non-crit
            boolean wasCrit = abilityResult.messages().stream().anyMatch(m -> m.contains("CRITICAL HIT"));
            if (!wasCrit) achievementTracker.resetCritStreak();

            // === TRIDENT CHANNELING: Lightning strike on throw hit ===
            if (fIsTridentThrow && fTridentChanneling > 0 && fTarget.isAlive()) {
                applyChannelingLightning(fTarget, fTridentChanneling, fBaseDamage);
            }

            // === TRIDENT LOYALTY: Return after throw ===
            if (fIsTridentThrow && fTridentHasLoyalty) {
                returnDroppedTrident();
                sendMessage("§b↺ Loyalty! Trident returns to your hand.");
            }

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

    // === TRIDENT: Riptide Dash ===
    // Dashes the player in a straight line toward the target direction until hitting a wall.
    // Damages and pushes all mobs out of the way. Strength scales with enchant level.
    private void handleRiptideDash(int riptideLevel, GridPos pPos, GridPos tPos) {
        apRemaining -= 2;

        int ddx = Integer.signum(tPos.x() - pPos.x());
        int ddz = Integer.signum(tPos.z() - pPos.z());

        // Calculate dash path — move until hitting arena edge or obstacle
        List<GridPos> dashPath = new ArrayList<>();
        GridPos check = new GridPos(pPos.x() + ddx, pPos.z() + ddz);
        while (arena.isInBounds(check)) {
            var tile = arena.getTile(check);
            if (tile == null || !tile.isWalkable()) break;
            dashPath.add(check);
            check = new GridPos(check.x() + ddx, check.z() + ddz);
        }

        if (dashPath.isEmpty()) {
            sendMessage("§cNo room to dash!");
            sendSync();
            return;
        }

        // Apply durability
        ItemStack weaponStack = player.getMainHandStack();
        if (weaponStack.isDamageable()) {
            weaponStack.damage(10, player, net.minecraft.entity.EquipmentSlot.MAINHAND);
        }

        // Calculate damage: base weapon damage + riptide level * 3
        int riptideDamage = PlayerCombatStats.getAttackPower(player) + riptideLevel * 3;
        riptideDamage = (int)(riptideDamage * CrafticsMod.CONFIG.playerDamageMultiplier());
        int knockbackStrength = 1 + riptideLevel;

        // Sound effect
        player.getWorld().playSound(null, player.getBlockPos(),
            net.minecraft.sound.SoundEvents.ITEM_TRIDENT_THROW.value(),
            net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 0.8f);

        // Process each tile in the dash path
        List<CombatEntity> hitEnemies = new ArrayList<>();
        GridPos finalPos = pPos;
        for (GridPos dashTile : dashPath) {
            // Check for enemy at this tile
            CombatEntity enemy = arena.getOccupant(dashTile);
            if (enemy != null && enemy.isAlive() && !enemy.isAlly()) {
                int dealt = enemy.takeDamage(riptideDamage);
                hitEnemies.add(enemy);
                sendMessage("§3⚡ Riptide hits " + enemy.getDisplayName() + " for " + dealt + "!");

                // Knockback: push enemy perpendicular or forward from dash direction
                int kbDx = Integer.signum(enemy.getGridPos().x() - pPos.x());
                int kbDz = Integer.signum(enemy.getGridPos().z() - pPos.z());
                // Push in dash direction
                if (kbDx == 0 && kbDz == 0) { kbDx = ddx; kbDz = ddz; }
                GridPos kbPos = enemy.getGridPos();
                for (int k = 0; k < knockbackStrength; k++) {
                    GridPos nextKb = new GridPos(kbPos.x() + kbDx, kbPos.z() + kbDz);
                    if (!arena.isInBounds(nextKb) || arena.isOccupied(nextKb)) break;
                    var kbTile = arena.getTile(nextKb);
                    if (kbTile == null || !kbTile.isWalkable()) break;
                    kbPos = nextKb;
                }
                if (!kbPos.equals(enemy.getGridPos())) {
                    arena.moveEntity(enemy, kbPos);
                    if (enemy.getMobEntity() != null) {
                        var bp = arena.gridToBlockPos(kbPos);
                        enemy.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                    }
                    sendMessage("§b💨 " + enemy.getDisplayName() + " knocked back " + knockbackStrength + " tiles!");
                }

                // Particles on hit
                ServerWorld dashWorld = (ServerWorld) player.getEntityWorld();
                BlockPos hitBlock = arena.gridToBlockPos(enemy.getGridPos());
                dashWorld.spawnParticles(net.minecraft.particle.ParticleTypes.SPLASH,
                    hitBlock.getX() + 0.5, hitBlock.getY() + 1.0, hitBlock.getZ() + 0.5,
                    10, 0.3, 0.3, 0.3, 0.1);
            }

            // Player can pass through (enemy was knocked away or tile is free)
            if (!arena.isEnemyOccupied(dashTile)) {
                finalPos = dashTile;
            } else {
                break; // can't pass through if enemy is still there (e.g. couldn't be knocked back)
            }
        }

        // Move player to final dash position
        arena.setPlayerGridPos(finalPos);
        BlockPos endBlock = arena.gridToBlockPos(finalPos);
        player.teleport((ServerWorld) player.getEntityWorld(),
            endBlock.getX() + 0.5, endBlock.getY(), endBlock.getZ() + 0.5,
            java.util.Collections.emptySet(), player.getYaw(), 0f);

        // Dash trail particles
        ServerWorld dashWorld = (ServerWorld) player.getEntityWorld();
        for (GridPos tile : dashPath) {
            if (tile.equals(finalPos) || dashPath.indexOf(tile) > dashPath.indexOf(finalPos)) break;
            BlockPos trailBlock = arena.gridToBlockPos(tile);
            dashWorld.spawnParticles(net.minecraft.particle.ParticleTypes.BUBBLE,
                trailBlock.getX() + 0.5, trailBlock.getY() + 0.5, trailBlock.getZ() + 0.5,
                5, 0.3, 0.2, 0.3, 0.05);
        }

        // Check deaths
        for (CombatEntity hit : hitEnemies) checkAndHandleDeath(hit);

        if (hitEnemies.isEmpty()) {
            sendMessage("§3Riptide dash! No enemies in path.");
        }

        sendSync();
        refreshHighlights();

        // Check win
        if (enemies.stream().noneMatch(e -> e.isAlive() && !e.isAlly())) {
            handleVictory();
        }
    }

    // === TRIDENT: Channeling Lightning ===
    // On throw hit: lightning strike on target. Higher levels chain to adjacent enemies.
    // Deals burning damage, extra damage if target is Soaked.
    private void applyChannelingLightning(CombatEntity target, int channelingLevel, int baseDamage) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();

        // Base lightning damage: 4 + channeling level * 2
        int lightningDmg = 4 + channelingLevel * 2;

        // Soaked bonus: double lightning damage
        boolean targetSoaked = target.getSoakedTurns() > 0;
        if (targetSoaked) lightningDmg *= 2;

        int dealt = target.takeDamage(lightningDmg);

        // Apply burning (2 turns)
        target.setBurningTurns(Math.max(target.getBurningTurns(), 2));
        target.setBurningDamage(Math.max(target.getBurningDamage(), 2));

        // Lightning visual + sound
        BlockPos targetBlock = arena.gridToBlockPos(target.getGridPos());
        world.playSound(null, targetBlock, net.minecraft.sound.SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
            net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 1.4f);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK,
            targetBlock.getX() + 0.5, targetBlock.getY() + 1.5, targetBlock.getZ() + 0.5,
            20, 0.3, 0.8, 0.3, 0.1);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.FLASH,
            targetBlock.getX() + 0.5, targetBlock.getY() + 1.0, targetBlock.getZ() + 0.5,
            1, 0, 0, 0, 0);

        String soakedMsg = targetSoaked ? " §b(2x Soaked!)" : "";
        sendMessage("§e⚡ Channeling! Lightning strikes " + target.getDisplayName() + " for " + dealt + "!" + soakedMsg);

        // Chain to adjacent enemies (channelingLevel - 1 additional targets)
        int chainCount = channelingLevel - 1;
        if (chainCount > 0) {
            List<CombatEntity> chainTargets = new ArrayList<>();
            for (int cdx = -1; cdx <= 1; cdx++) {
                for (int cdz = -1; cdz <= 1; cdz++) {
                    if (cdx == 0 && cdz == 0) continue;
                    GridPos adj = new GridPos(target.getGridPos().x() + cdx, target.getGridPos().z() + cdz);
                    CombatEntity chainTarget = arena.getOccupant(adj);
                    if (chainTarget != null && chainTarget.isAlive() && chainTarget != target && !chainTarget.isAlly()) {
                        chainTargets.add(chainTarget);
                        if (chainTargets.size() >= chainCount) break;
                    }
                }
                if (chainTargets.size() >= chainCount) break;
            }

            for (CombatEntity chain : chainTargets) {
                int chainDmg = lightningDmg / 2; // chain does half damage
                boolean chainSoaked = chain.getSoakedTurns() > 0;
                if (chainSoaked) chainDmg *= 2;
                int chainDealt = chain.takeDamage(chainDmg);
                chain.setBurningTurns(Math.max(chain.getBurningTurns(), 1));
                chain.setBurningDamage(Math.max(chain.getBurningDamage(), 1));

                // Chain lightning visual
                BlockPos chainBlock = arena.gridToBlockPos(chain.getGridPos());
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK,
                    chainBlock.getX() + 0.5, chainBlock.getY() + 1.0, chainBlock.getZ() + 0.5,
                    15, 0.3, 0.5, 0.3, 0.1);

                String chainSoakedMsg = chainSoaked ? " §b(2x Soaked!)" : "";
                sendMessage("§e⚡ Chain lightning hits " + chain.getDisplayName() + " for " + chainDealt + "!" + chainSoakedMsg);
                checkAndHandleDeath(chain);
            }
        }
    }

    // === TRIDENT: Return dropped trident to player ===
    private void returnDroppedTrident() {
        if (droppedTridentStack != null && player != null) {
            player.getInventory().insertStack(droppedTridentStack.copy());
            droppedTridentStack = null;
            droppedTridentPos = null;
            if (droppedTridentEntity != null) {
                droppedTridentEntity.discard();
                droppedTridentEntity = null;
            }
        }
    }

    private CombatEntity findEnemyById(int entityId) {
        for (CombatEntity e : enemies) {
            if (e.getEntityId() == entityId && e.isAlive()) return e;
        }
        return null;
    }

    public void checkAndHandleDeathPublic(CombatEntity entity) { checkAndHandleDeath(entity); }

    /**
     * Check if an enemy has landed on a void/death tile and kill it instantly if so.
     * Returns true if the enemy fell to its death.
     */
    private boolean checkEnemyFallDeath(CombatEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        GridPos pos = entity.getGridPos();
        if (pos == null || !arena.isInBounds(pos)) return false;
        GridTile tile = arena.getTile(pos);
        if (tile != null && tile.getType() == TileType.VOID) {
            sendMessage("§c" + entity.getDisplayName() + " fell to its death!");
            // Death particles at the edge
            if (entity.getMobEntity() != null) {
                ServerWorld world = (ServerWorld) player.getEntityWorld();
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,
                    entity.getMobEntity().getX(), entity.getMobEntity().getY(), entity.getMobEntity().getZ(),
                    10, 0.3, 0.3, 0.3, 0.05);
            }
            killEnemy(entity);
            return true;
        }
        return false;
    }

    private void checkAndHandleDeath(CombatEntity entity) {
        if (!entity.isAlive()) {
            // Notify bosses of minion death (crystals, turrets, chains, etc.)
            if (!entity.isBoss()) {
                notifyBossOfMinionDeath(entity);
            }
            sendMessage("§a" + entity.getDisplayName() + " defeated!");
            GridPos deathPos = entity.getGridPos();
            int deathSize = entity.getSize();
            String deathType = entity.getEntityTypeId();
            int deathMaxHp = entity.getMaxHp();
            int deathAtk = entity.getAttackPower();
            int deathDef = entity.getDefense();
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

            // Shrink animation then discard (instead of instant removal)
            MobEntity mob = entity.getMobEntity();
            if (mob != null) {
                startDeathShrink(mob);
                dyingMobs.add(mob);
                dyingMobTimers.add(20);
            }
            sendToAllParty(new CombatEventPayload(
                CombatEventPayload.EVENT_DIED, entity.getEntityId(), 0, 0,
                deathPos.x(), deathPos.z()
            ));

            // Slime/Magma Cube split: 2x2 splits into 2 mini 1x1 versions
            if (deathSize >= 2 && isSplittableMob(deathType)) {
                trySplitOnDeath(deathType, deathPos, deathSize, deathMaxHp, deathAtk, deathDef);
            }
        }
    }

    /** Returns true if this mob type splits into smaller versions on death. */
    private static boolean isSplittableMob(String entityTypeId) {
        return switch (entityTypeId) {
            case "minecraft:slime", "minecraft:magma_cube" -> true;
            default -> false;
        };
    }

    /**
     * Spawn 2 mini (1x1) versions of a slime/magma cube after the 2x2 parent dies.
     * Mini versions have 1/3 HP and 1/2 ATK of the parent. They do NOT split again.
     */
    private void trySplitOnDeath(String entityTypeId, GridPos deathPos, int parentSize,
                                  int parentMaxHp, int parentAtk, int parentDef) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        EntityType<?> type = Registries.ENTITY_TYPE.get(Identifier.of(entityTypeId));

        // Find spawn positions from the tiles the parent occupied
        List<GridPos> candidates = new ArrayList<>();
        for (int dx = 0; dx < parentSize; dx++) {
            for (int dz = 0; dz < parentSize; dz++) {
                GridPos tile = new GridPos(deathPos.x() + dx, deathPos.z() + dz);
                if (arena.isInBounds(tile) && !arena.isOccupied(tile)) {
                    GridTile gt = arena.getTile(tile);
                    if (gt != null && gt.isWalkable()) candidates.add(tile);
                }
            }
        }
        if (candidates.size() < 2) return; // not enough room to split
        java.util.Collections.shuffle(candidates);

        int miniHp = Math.max(2, parentMaxHp / 3);
        int miniAtk = Math.max(1, parentAtk / 2);
        int spawned = 0;

        for (GridPos pos : candidates) {
            if (spawned >= 2) break;
            if (arena.isOccupied(pos)) continue; // re-check in case first spawn took it

            BlockPos spawnPos = arena.gridToBlockPos(pos);
            var rawEntity = type.create(world, null, spawnPos, SpawnReason.MOB_SUMMONED, false, false);
            if (rawEntity == null) continue;
            rawEntity.refreshPositionAndAngles(
                spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
            if (rawEntity instanceof MobEntity mob) {
                mob.setAiDisabled(true);
                mob.setInvulnerable(true);
                mob.setNoGravity(true);
                mob.noClip = true;
                mob.setPersistent();
                mob.addCommandTag("craftics_arena");
            }
            world.spawnEntity(rawEntity);
            if (rawEntity instanceof MobEntity mob) {
                // Force size 1 override so minis are 1x1 and don't split again
                CombatEntity ce = new CombatEntity(
                    mob.getId(), entityTypeId, pos,
                    miniHp, miniAtk, parentDef, 1, 1
                );
                ce.setMobEntity(mob);
                enemies.add(ce);
                arena.placeEntity(ce);
                spawned++;
                // Spawn particles (slimey poof)
                world.spawnParticles(
                    entityTypeId.equals("minecraft:magma_cube")
                        ? net.minecraft.particle.ParticleTypes.FLAME
                        : net.minecraft.particle.ParticleTypes.ITEM_SLIME,
                    spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5,
                    8, 0.3, 0.3, 0.3, 0.05);
            }
        }
        if (spawned > 0) {
            String name = entityTypeId.substring(entityTypeId.indexOf(':') + 1).replace('_', ' ');
            sendMessage("§d  Split into " + spawned + " mini " + name + "s!");
            // Slime squish sound
            if (player != null) {
                player.getWorld().playSound(null, arena.gridToBlockPos(deathPos),
                    net.minecraft.sound.SoundEvents.ENTITY_SLIME_SQUISH,
                    net.minecraft.sound.SoundCategory.HOSTILE, 1.0f, 1.5f);
            }
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
            case "desert" -> { // The Sandstorm Pharaoh — Husk (no helmet so husk head is visible)
                mob.setCustomName(Text.literal("§6§lThe Sandstorm Pharaoh"));
                mob.setCustomNameVisible(true);
                mob.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
                mob.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
                mob.equipStack(net.minecraft.entity.EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
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
                // No equipment changes — Warden's model is already imposing and large
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
                scaleBoss(mob, 2.0); // Huge ghast looming over the edge of the arena
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
                // Wither is naturally large — no scaling needed
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

    /** Snap a yaw angle to the nearest cardinal direction (0, 90, 180, -90). */
    private static float snapToCardinalYaw(float yaw) {
        float normalized = ((yaw % 360) + 360) % 360; // 0..360
        if (normalized < 45 || normalized >= 315) return 0f;
        if (normalized < 135) return 90f;
        if (normalized < 225) return 180f;
        return -90f; // 225..315
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

    /** Start a dying mob's gradual shrink + death particles. */
    private void startDeathShrink(MobEntity mob) {
        var scaleAttr = mob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
        float startScale = scaleAttr != null ? (float) scaleAttr.getBaseValue() : 1.0f;
        dyingMobStartScales.add(startScale);

        // Hurt flash + death sound
        mob.setHealth(0);

        // Initial death poof particles
        if (mob.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.POOF,
                mob.getX(), mob.getY() + mob.getHeight() / 2, mob.getZ(),
                10, 0.3, 0.3, 0.3, 0.02);
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
            if ("minecraft:ghast".equals(currentEnemy.getEntityTypeId())) {
                yaw = snapToCardinalYaw(yaw);
            }
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
                    // Apply per-species minimum stats for combat pets
                    PetStats.Stats petMin = PetStats.get(e.getEntityTypeId());
                    if (e.getAttackPower() < petMin.atk()) {
                        e.setAttackBoost(petMin.atk() - e.getAttackPower());
                    }
                    if (e.getDefense() < petMin.def()) {
                        e.setDefenseBoost(petMin.def() - e.getDefense());
                    }
                    if (e.getRange() < petMin.range()) {
                        e.setRangeOverride(petMin.range());
                    }
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
                    // Pet affinity: bonus HP and ATK based on Pet affinity points (3% per point)
                    PlayerProgression.PlayerStats petOwnerStats = PlayerProgression.get(
                        (ServerWorld) player.getEntityWorld()).getStats(player);
                    int petPts = petOwnerStats.getAffinityPoints(PlayerProgression.Affinity.PET);
                    if (petPts > 0) {
                        double petBoostPct = petPts * 0.03;
                        int hpBoost = Math.max(1, (int)(e.getMaxHp() * petBoostPct));
                        int atkBoost = Math.max(0, (int)(e.getAttackPower() * petBoostPct));
                        // Increase effective max HP via negative reduction, then heal
                        e.addMaxHpReduction(-hpBoost);
                        e.heal(hpBoost);
                        if (atkBoost > 0) e.setAttackBoost(e.getAttackBoost() + atkBoost);
                        sendMessage("\u00a7a\uD83D\uDC3E Pet Affinity: +" + hpBoost + " HP, +" + atkBoost + " ATK!");
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
        // Handle TNT placement — track for detonation next round
        else if (result.startsWith(ItemUseHandler.TNT_PREFIX)) {
            String coords = result.substring(ItemUseHandler.TNT_PREFIX.length());
            String[] parts = coords.split(",");
            int tx = Integer.parseInt(parts[0]);
            int tz = Integer.parseInt(parts[1]);
            GridPos tntPos = new GridPos(tx, tz);
            BlockPos tntBp = arena.gridToBlockPos(tntPos);
            pendingTnts.add(new PendingTnt(tntPos, tntBp));
            // Mark the tile as a warning so the red overlay shows
            highlightWarningTile(tntPos);
            sendMessage("§e§lTNT placed! §r§7It will explode at the start of next round!");
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
            if ("clear".equals(effectType)) {
                // Empty bucket: revert tile to NORMAL and remove any existing effect
                GridPos clearPos = new GridPos(tx, tz);
                GridTile clearTile = arena.getTile(clearPos);
                if (clearTile != null) {
                    clearTile.setType(TileType.NORMAL);
                }
                tileEffects.remove(clearPos);
                // Place the actual block
                BlockPos bp = arena.gridToBlockPos(clearPos);
                ServerWorld sw = (ServerWorld) player.getEntityWorld();
                sw.setBlockState(bp, net.minecraft.block.Blocks.GRASS_BLOCK.getDefaultState());
            } else if ("break".equals(effectType)) {
                // Pickaxe: convert obstacle to walkable NORMAL tile
                GridPos breakPos = new GridPos(tx, tz);
                GridTile breakTile = arena.getTile(breakPos);
                if (breakTile != null) {
                    breakTile.setType(TileType.NORMAL);
                }
                // gridToBlockPos returns oy+1 (where the obstacle block sits)
                BlockPos aboveBp = arena.gridToBlockPos(breakPos);
                // Floor is one Y below
                BlockPos floorBp = aboveBp.down();
                ServerWorld sw = (ServerWorld) player.getEntityWorld();
                // Clear the obstacle block at oy+1
                sw.setBlockState(aboveBp, net.minecraft.block.Blocks.AIR.getDefaultState());
                // Restore floor block at oy
                sw.setBlockState(floorBp, breakTile != null ? breakTile.getBlockType().getDefaultState()
                    : net.minecraft.block.Blocks.GRASS_BLOCK.getDefaultState());
                // Break particles
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,
                    aboveBp.getX() + 0.5, aboveBp.getY() + 0.5, aboveBp.getZ() + 0.5,
                    8, 0.3, 0.3, 0.3, 0.05);
                player.getWorld().playSound(null, aboveBp,
                    net.minecraft.sound.SoundEvents.BLOCK_STONE_BREAK,
                    net.minecraft.sound.SoundCategory.BLOCKS, 1.0f, 1.0f);
            } else {
                GridPos effectPos = new GridPos(tx, tz);
                tileEffects.put(effectPos, effectType);
                // Update the GridTile type so fishing/boat checks work
                GridTile effectTile = arena.getTile(effectPos);
                if (effectTile != null) {
                    if ("water".equals(effectType)) effectTile.setType(TileType.WATER);
                    else if ("lava".equals(effectType)) effectTile.setType(TileType.LAVA);
                    else if ("fire".equals(effectType)) effectTile.setType(TileType.FIRE);
                }
            }
            // Process message part - handle GIVE: prefix for item rewards
            if (parts.length > 1) {
                String msg = parts[1];
                // Check for chained GIVE:item|message format
                if (msg.startsWith("GIVE:")) {
                    String[] giveParts = msg.split("\\|", 2);
                    String itemId = giveParts[0].substring(5); // after "GIVE:"
                    net.minecraft.item.Item giveItem = switch (itemId) {
                        case "water_bucket" -> Items.WATER_BUCKET;
                        case "lava_bucket" -> Items.LAVA_BUCKET;
                        default -> null;
                    };
                    if (giveItem != null) {
                        player.getInventory().insertStack(new ItemStack(giveItem));
                    }
                    if (giveParts.length > 1) sendMessage(giveParts[1]);
                } else {
                    sendMessage(msg);
                }
            }
        }
        // Ally buff (jukebox music, hay bale heal, echo shard)
        else if (result.startsWith(ItemUseHandler.ALLY_BUFF_PREFIX)) {
            String data = result.substring(ItemUseHandler.ALLY_BUFF_PREFIX.length());
            String[] parts = data.split("\\|", 2);
            // Parse buff type: "heal:entityId:amount" or "music" or "echo"
            String buffData = parts[0];
            if (buffData.startsWith("heal:")) {
                String[] healParts = buffData.split(":");
                if (healParts.length >= 3) {
                    try {
                        int entityId = Integer.parseInt(healParts[1]);
                        int healAmount = Integer.parseInt(healParts[2]);
                        for (CombatEntity e : enemies) {
                            if (e.getEntityId() == entityId && e.isAlive() && e.isAlly()) {
                                e.heal(healAmount);
                                break;
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
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

        // Party turn rotation: advance to next alive player before enemy turn
        // Lazy init: on the initial level start, party members are registered AFTER startCombat,
        // so the queue built inside startCombat was empty. Build it now on first use.
        if (partyPlayers.size() > 1 && turnQueue.size() <= 1) {
            rebuildTurnQueue();
        }
        if (turnQueue.size() > 1) {
            currentTurnIndex++;
            if (currentTurnIndex < turnQueue.size()) {
                // Next player's sub-turn — switch active player
                switchToTurnPlayer();
                return; // Don't start enemy turn yet
            }
            // All players have acted — fall through to enemy turn
            currentTurnIndex = 0;
        }

        // Tick boss warning telegraphs
        tickBossWarnings();

        startEnemyTurn();
    }

    /**
     * Build the turn queue from alive, online party members. Called at the start of each
     * player turn round. Skips dead/disconnected members.
     */
    private void rebuildTurnQueue() {
        turnQueue.clear();
        for (ServerPlayerEntity member : partyPlayers) {
            if (member != null && !member.isRemoved() && !member.isDisconnected()
                    && !deadPartyMembers.contains(member.getUuid())) {
                turnQueue.add(member.getUuid());
            }
        }
        // Solo play — queue stays empty, no rotation needed
        currentTurnIndex = 0;
    }

    /**
     * Switch the active player to whoever is at currentTurnIndex in the turn queue.
     * Recalculates AP/Move from that player's stats and notifies all clients.
     */
    private void switchToTurnPlayer() {
        if (turnQueue.isEmpty()) return;
        java.util.UUID nextUuid = turnQueue.get(currentTurnIndex);
        ServerPlayerEntity nextPlayer = null;
        for (ServerPlayerEntity member : partyPlayers) {
            if (member.getUuid().equals(nextUuid)) { nextPlayer = member; break; }
        }
        if (nextPlayer == null || nextPlayer.isRemoved() || nextPlayer.isDisconnected()) {
            // Skip disconnected/dead player, try next
            turnQueue.remove(currentTurnIndex);
            if (currentTurnIndex >= turnQueue.size()) {
                currentTurnIndex = 0;
                tickBossWarnings();
                startEnemyTurn();
                return;
            }
            switchToTurnPlayer(); // recurse to next
            return;
        }

        this.player = nextPlayer;

        // Sync the arena's tracked player grid position to this player's actual position
        if (arena != null) {
            net.minecraft.util.math.BlockPos origin = arena.getOrigin();
            net.minecraft.util.math.BlockPos pBlock = player.getBlockPos();
            arena.setPlayerGridPos(new GridPos(
                pBlock.getX() - origin.getX(),
                pBlock.getZ() - origin.getZ()
            ));
        }

        // Recalculate AP and Move from this player's stats
        if (testRange) {
            apRemaining = 999;
            movePointsRemaining = 999;
        } else {
            PlayerProgression turnProg = PlayerProgression.get((ServerWorld) player.getEntityWorld());
            PlayerProgression.PlayerStats turnStats = turnProg.getStats(player);
            apRemaining = turnStats.getEffective(PlayerProgression.Stat.AP)
                + PlayerCombatStats.getSetApBonus(player);
            movePointsRemaining = turnStats.getEffective(PlayerProgression.Stat.SPEED)
                + combatEffects.getSpeedBonus() - combatEffects.getSpeedPenalty()
                + (playerMounted ? MOUNT_SPEED_BONUS : 0)
                + PlayerCombatStats.getSetSpeedBonus(player);
            if (combatEffects.hasEffect(CombatEffects.EffectType.SOAKED)) {
                movePointsRemaining = Math.max(1, movePointsRemaining - 1);
            }
        }

        this.activeTrimScan = TrimEffects.scan(player);

        // Sound + announce
        player.getWorld().playSound(null, player.getBlockPos(),
            net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
            net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 1.2f);
        sendMessage("§e" + player.getName().getString() + "'s turn! §aAP: " + apRemaining + " | SPD: " + movePointsRemaining);
        sendSync();
        refreshHighlights();
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

        // Fall death: if player drops more than 2 blocks below arena floor, instant death.
        // This bypasses totem — falling off the arena is environmental, not combat.
        // Teleport back first to prevent vanilla lava/void damage loops.
        if (arena != null) {
            int arenaFloorY = arena.getOrigin().getY();
            if (player.getY() < arenaFloorY - 2) {
                // Immediately teleport back to arena to stop vanilla damage
                BlockPos safePos = arena.getPlayerStartBlockPos();
                if (safePos != null) {
                    player.requestTeleport(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
                }
                player.setFireTicks(0); // extinguish
                player.setFrozenTicks(0);
                player.setHealth(1);
                sendMessage("§c§l☠ You fell to your death!");
                // Skip totem — go straight to death/game over
                if (partyPlayers.size() <= 1) {
                    startPlayerDeathAnimation();
                } else {
                    deadPartyMembers.add(player.getUuid());
                    player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
                    sendMessage("§c§l☠ " + player.getName().getString() + " has fallen!");
                    ServerPlayerEntity nextAlive = null;
                    for (ServerPlayerEntity member : partyPlayers) {
                        if (!deadPartyMembers.contains(member.getUuid())
                                && member != null && !member.isRemoved() && !member.isDisconnected()) {
                            nextAlive = member;
                            break;
                        }
                    }
                    if (nextAlive == null) {
                        startPlayerDeathAnimation();
                    } else {
                        sendMessage("§e" + nextAlive.getName().getString() + " takes over the fight!");
                        nextAlive.requestTeleport(
                            arena.getPlayerStartBlockPos().getX() + 0.5,
                            arena.getPlayerStartBlockPos().getY(),
                            arena.getPlayerStartBlockPos().getZ() + 0.5);
                        this.player = nextAlive;
                    }
                }
                return;
            }
        }
        // Void safety: if player falls below Y=0, teleport them back
        if (player.getY() < 0) {
            BlockPos safePos = arena != null ? arena.getPlayerStartBlockPos() : null;
            if (safePos != null) {
                player.requestTeleport(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
            } else {
                player.requestTeleport(player.getX(), 100, player.getZ());
            }
            player.setVelocity(0, 0, 0);
            player.velocityModified = true;
            player.setFireTicks(0);
            player.fallDistance = 0;
        }

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
                        replacementMob.noClip = true;
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
                // Skip the mob currently being animated and background bosses (positioned off-grid)
                if (!e.isBackgroundBoss()
                        && (e != currentEnemy || enemyTurnState == EnemyTurnState.DONE
                        || enemyTurnState == EnemyTurnState.DECIDING)) {
                    BlockPos gridBlock = arena.gridToBlockPos(e.getGridPos());
                    double sizeOffset = e.getSize() > 1 ? e.getSize() / 2.0 : 0.5;
                    double targetX = gridBlock.getX() + sizeOffset;
                    double targetZ = gridBlock.getZ() + sizeOffset;
                    double driftX = Math.abs(mob.getX() - targetX);
                    double driftZ = Math.abs(mob.getZ() - targetZ);
                    if (driftX > 0.1 || driftZ > 0.1) {
                        mob.requestTeleport(targetX, gridBlock.getY(), targetZ);
                    }
                    // Keep visual projectile entity synced with tracking mob
                    if (e.isProjectile() && e.getVisualProjectileEntityId() != -1) {
                        syncVisualProjectile(e, targetX, gridBlock.getY() + 0.5, targetZ);
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

        // Tick dying mobs (gradual shrink animation + particles then discard)
        for (int i = dyingMobs.size() - 1; i >= 0; i--) {
            int remaining = dyingMobTimers.get(i) - 1;
            MobEntity dyingMob = dyingMobs.get(i);
            if (remaining <= 0) {
                dyingMob.discard();
                dyingMobs.remove(i);
                dyingMobTimers.remove(i);
                dyingMobStartScales.remove(i);
            } else {
                dyingMobTimers.set(i, remaining);
                // Gradual shrink: interpolate from original scale to near-zero over 15 ticks
                float startScale = i < dyingMobStartScales.size() ? dyingMobStartScales.get(i) : 1.0f;
                int totalShrinkTicks = 15;
                int elapsed = 20 - remaining; // total timer is 20
                float progress = Math.min(1.0f, (float) elapsed / totalShrinkTicks);
                float eased = progress * progress; // accelerating shrink
                float currentScale = startScale * (1.0f - eased) + 0.01f * eased;
                var scaleAttr = dyingMob.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
                if (scaleAttr != null) scaleAttr.setBaseValue(currentScale);

                // Smoke particles during shrink
                if (elapsed % 3 == 0 && dyingMob.getWorld() instanceof ServerWorld sw) {
                    sw.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
                        dyingMob.getX(), dyingMob.getY() + 0.3, dyingMob.getZ(),
                        3, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }

        switch (phase) {
            case ANIMATING -> tickAnimation();
            case REACTING -> tickReacting();
            case ENEMY_TURN -> tickEnemyTurn();
            case PLAYER_DYING -> tickPlayerDying();
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

            // Check for dropped trident — pick it up
            if (droppedTridentPos != null && next.equals(droppedTridentPos)) {
                returnDroppedTrident();
                sendMessage("§3You retrieve your trident!");
            }

            // Check for cake tile — heal player on step
            if (tileEffects.containsKey(next) && tileEffects.get(next).startsWith("cake")) {
                String cakeData = tileEffects.get(next);
                int uses = 3;
                if (cakeData.contains(":")) {
                    try { uses = Integer.parseInt(cakeData.split(":")[1]); } catch (Exception ignored) {}
                }
                if (player.getHealth() < player.getMaxHealth()) {
                    player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 2));
                    sendMessage("§dYou eat cake! +2 HP");
                }
                uses--;
                if (uses <= 0) {
                    tileEffects.remove(next);
                    sendMessage("§7The cake is all gone!");
                } else {
                    tileEffects.put(next, "cake:" + uses);
                }
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
            case ANIMATING -> tickEnemyAnimating();
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
                boolean wasAlive = e.isAlive();
                int poisonDmg = e.takeDamage(1 + e.getPoisonAmplifier());
                e.setPoisonTurns(e.getPoisonTurns() - 1);
                sendMessage("§2" + e.getDisplayName() + " took " + poisonDmg + " poison damage.");
                if (e.getMobEntity() != null) {
                    ((ServerWorld) player.getEntityWorld()).spawnParticles(
                        net.minecraft.particle.ParticleTypes.ITEM_SLIME,
                        e.getMobEntity().getX(), e.getMobEntity().getY() + 1.0, e.getMobEntity().getZ(),
                        8, 0.3, 0.3, 0.3, 0.05);
                }
                if (wasAlive && !e.isAlive()) achievementTracker.recordPoisonKill();
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
                boolean wasAlive = e.isAlive();
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
                if (wasAlive && !e.isAlive()) achievementTracker.recordBurnKill();
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

            // Tick enemy soaked duration
            for (CombatEntity e : enemies) {
                if (!e.isAlive() || e.getSoakedTurns() <= 0) continue;
                e.setSoakedTurns(e.getSoakedTurns() - 1);
                if (e.getSoakedTurns() <= 0) {
                    e.setSoakedAmplifier(0);
                    sendMessage("§3" + e.getDisplayName() + " dried off.");
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
                float newHp = Math.max(1, Math.min(player.getMaxHealth(), player.getHealth() + hpChange));
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
                if (getPlayerHp() <= 0) { handlePlayerDeathOrGameOver(); return; }
            }

            // Environmental hazards — check tile under player
            GridPos playerPos = arena.getPlayerGridPos();
            GridTile playerTile = arena.getTile(playerPos);
            if (playerTile != null) {
                net.minecraft.block.Block tileBlock = playerTile.getBlockType();
                if (tileBlock == Blocks.MAGMA_BLOCK && !combatEffects.hasFireResistance()) {
                    player.setHealth(Math.max(1, player.getHealth() - 1));
                    sendMessage("§c🔥 Magma burns you for 1 damage!");
                    if (getPlayerHp() <= 0) { handlePlayerDeathOrGameOver(); return; }
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
                        player.setHealth(Math.max(1, player.getHealth() - 1));
                        sendMessage("§5You breathe in the poison cloud! -1 HP");
                        if (getPlayerHp() <= 0) { handlePlayerDeathOrGameOver(); return; }
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
                            int lightningDmg = 4;
                            // Soaked doubles lightning damage
                            if (e.getSoakedTurns() > 0) lightningDmg *= 2;
                            e.takeDamage(lightningDmg);
                            hit++;
                        }
                    }
                    if (hit > 0) sendMessage("§e⚡ Lightning strikes! " + hit + " enemies hit!");
                    else sendMessage("§eLightning rod fizzles — no enemies nearby.");
                    return true; // remove after striking
                }
                return false;
            });

            turnNumber++;
            achievementTracker.recordTurnCompleted();

            // Reset ghast scream face at start of player's turn
            for (CombatEntity e : enemies) {
                if (e.isAlive() && e.isBackgroundBoss()) {
                    triggerGhastScream(e, false);
                }
            }

            // Track living allies for Zookeeper achievement
            long allyCount = enemies.stream().filter(e -> e.isAlive() && e.isAlly()).count();
            achievementTracker.recordLivingAllies((int) allyCount);

            // Check if only passives/neutrals remain (no hostile enemies)
            boolean hasHostile = enemies.stream().anyMatch(e -> e.isAlive() && !e.isAlly());
            if (!hasHostile) {
                peacefulTurnCount++;
                int remaining = 3 - peacefulTurnCount;
                if (peacefulTurnCount >= 3) {
                    sendMessage("§a§lNo enemies remain! Combat complete.");
                    handleVictory();
                    return;
                } else {
                    sendMessage("§a☮ No hostile enemies on the field. Auto-ending in §e" + remaining + "§a turn" + (remaining != 1 ? "s" : "") + "...");
                }
            } else {
                peacefulTurnCount = 0;
            }

            endTurnHintSent = false; // reset per turn

            // Tick temporary terrain tiles (boss fire pools, obstacles, etc.)
            tickTemporaryTerrain();
            // Detonate any TNT placed last round
            detonatePendingTnts();
            // Clear shield brace from previous turn
            shieldBraced = false;

            // Rebuild party turn queue for this round (skips dead/offline members)
            if (partyPlayers.size() > 1) {
                rebuildTurnQueue();
                if (!turnQueue.isEmpty()) {
                    // Set active player to first in queue
                    currentTurnIndex = 0;
                    java.util.UUID firstUuid = turnQueue.get(0);
                    for (ServerPlayerEntity member : partyPlayers) {
                        if (member.getUuid().equals(firstUuid)) { this.player = member; break; }
                    }
                    // Sync arena grid position to this player's actual position
                    if (arena != null) {
                        net.minecraft.util.math.BlockPos qOrigin = arena.getOrigin();
                        net.minecraft.util.math.BlockPos qBlock = player.getBlockPos();
                        arena.setPlayerGridPos(new GridPos(
                            qBlock.getX() - qOrigin.getX(),
                            qBlock.getZ() - qOrigin.getZ()
                        ));
                    }
                    this.activeTrimScan = TrimEffects.scan(player);
                }
            }

            if (testRange) {
                // Infinite AP and movement in test range
                apRemaining = 999;
                movePointsRemaining = 999;
            } else {
                // Read base stats from progression (uses current `player` which may have been set by turn queue)
                PlayerProgression turnProg = PlayerProgression.get((ServerWorld) player.getEntityWorld());
                PlayerProgression.PlayerStats turnStats = turnProg.getStats(player);
                apRemaining = turnStats.getEffective(PlayerProgression.Stat.AP);
                apRemaining += PlayerCombatStats.getSetApBonus(player);
                movePointsRemaining = turnStats.getEffective(PlayerProgression.Stat.SPEED)
                    + combatEffects.getSpeedBonus() - combatEffects.getSpeedPenalty()
                    + (playerMounted ? MOUNT_SPEED_BONUS : 0);
                movePointsRemaining += PlayerCombatStats.getSetSpeedBonus(player);
                // Soaked reduces player movement by 1
                if (combatEffects.hasEffect(CombatEffects.EffectType.SOAKED)) {
                    movePointsRemaining = Math.max(1, movePointsRemaining - 1);
                }
            }
            phase = CombatPhase.PLAYER_TURN;
            // Sound: player turn start
            player.getWorld().playSound(null, player.getBlockPos(),
                net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
                net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 1.2f);
            // Announce whose turn it is in party combat
            if (turnQueue.size() > 1) {
                sendMessage("§e" + player.getName().getString() + "'s turn! §aAP: " + apRemaining + " | SPD: " + movePointsRemaining);
            } else {
                sendMessage("§aAP: " + apRemaining + " | SPD: " + movePointsRemaining);
            }
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

        // Confused entities attack a random teammate instead
        if (currentEnemy.getConfusionTurns() > 0 && !currentEnemy.isAlly()) {
            // Find a random alive non-ally teammate to attack
            java.util.List<CombatEntity> teammates = new java.util.ArrayList<>();
            for (CombatEntity e : enemies) {
                if (e != currentEnemy && e.isAlive() && !e.isAlly()) teammates.add(e);
            }
            if (!teammates.isEmpty()) {
                CombatEntity victim = teammates.get((int)(Math.random() * teammates.size()));
                sendMessage("§d" + currentEnemy.getDisplayName() + " is confused and attacks " + victim.getDisplayName() + "!");
                // Pathfind toward the victim and attack
                java.util.List<GridPos> path = Pathfinding.findPath(arena, currentEnemy.getGridPos(),
                    victim.getGridPos(), currentEnemy.getMoveSpeed(), false);
                pendingAction = new com.crackedgames.craftics.combat.ai.EnemyAction.MoveAndAttackMob(
                    path, victim.getEntityId(), currentEnemy.getAttackPower());
                currentEnemy.setConfusionTurns(currentEnemy.getConfusionTurns() - 1);
                if (path != null && !path.isEmpty()) {
                    startEnemyMove(path);
                } else {
                    // Adjacent already — attack directly
                    startAttackAnimation(CrafticsMod.CONFIG.enemyTurnDelay());
                }
                return;
            }
            // No teammates to hit — confusion wasted, tick it down
            currentEnemy.setConfusionTurns(currentEnemy.getConfusionTurns() - 1);
            sendMessage("§d" + currentEnemy.getDisplayName() + " is confused but has no allies to hit!");
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
                startAttackAnimation(CrafticsMod.CONFIG.enemyTurnDelay());
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
                startAttackAnimation(CrafticsMod.CONFIG.enemyTurnDelay());
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
                startAttackAnimation(2);
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
                    player.setHealth(Math.max(1, player.getHealth() - actual));
                    sendMessage("§c  Explosion hits you for " + actual + " damage! (HP: " + getPlayerHp() + ")");
                    if (getPlayerHp() <= 0) { handlePlayerDeathOrGameOver(); return; }
                }
                // Damage other mobs caught in the blast (explosions always hit nearby mobs)
                List<CombatEntity> blastTargets = new ArrayList<>();
                for (CombatEntity other : enemies) {
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
                // Creeper dies from explosion — mark as self-exploded (no drops)
                currentEnemy.setSelfExploded(true);
                currentEnemy.takeDamage(9999);
                killEnemy(currentEnemy);
                sendSync();
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = 8;
            }
            case EnemyAction.RangedAttack ra -> {
                // Ranged attack — play aim animation first, then resolve in ATTACKING phase
                startAttackAnimation(CrafticsMod.CONFIG.enemyTurnDelay());
            }
            case EnemyAction.Swoop swoop -> {
                // Phantom swoop — fly along path, damage player if in the way
                sendMessage("§8" + currentEnemy.getDisplayName() + " swoops!");
                ServerWorld swoopWorld = (ServerWorld) player.getEntityWorld();
                boolean hitPlayer = false;
                GridPos playerGridPos = arena.getPlayerGridPos();
                GridPos finalPos = currentEnemy.getGridPos();

                // Determine swoop particle based on the boss
                net.minecraft.particle.ParticleEffect swoopTrail = net.minecraft.particle.ParticleTypes.CLOUD;
                net.minecraft.particle.ParticleEffect swoopAccent = net.minecraft.particle.ParticleTypes.SMOKE;
                if (currentEnemy.getAiKey() != null) {
                    String aiKey = currentEnemy.getAiKey();
                    if (aiKey.contains("wither") || aiKey.contains("Wither")) {
                        swoopTrail = net.minecraft.particle.ParticleTypes.SOUL;
                        swoopAccent = net.minecraft.particle.ParticleTypes.LARGE_SMOKE;
                    } else if (aiKey.contains("crimson") || aiKey.contains("Crimson")) {
                        swoopTrail = net.minecraft.particle.ParticleTypes.CRIMSON_SPORE;
                        swoopAccent = net.minecraft.particle.ParticleTypes.FLAME;
                    } else if (aiKey.contains("revenant") || aiKey.contains("Revenant")) {
                        swoopTrail = net.minecraft.particle.ParticleTypes.SMOKE;
                        swoopAccent = net.minecraft.particle.ParticleTypes.SOUL;
                    }
                }

                for (GridPos pos : swoop.path()) {
                    if (pos.equals(playerGridPos)) {
                        hitPlayer = true;
                    }
                    // Trail particles along each swoop tile
                    BlockPos swoopBp = arena.gridToBlockPos(pos);
                    swoopWorld.spawnParticles(swoopTrail,
                        swoopBp.getX() + 0.5, swoopBp.getY() + 1.0, swoopBp.getZ() + 0.5,
                        4, 0.2, 0.3, 0.2, 0.02);
                    swoopWorld.spawnParticles(swoopAccent,
                        swoopBp.getX() + 0.5, swoopBp.getY() + 0.5, swoopBp.getZ() + 0.5,
                        2, 0.15, 0.2, 0.15, 0.01);
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
                    player.setHealth(Math.max(1, player.getHealth() - actual));
                    sendMessage("§c  Swoops through you for " + actual + " damage! (HP: " + getPlayerHp() + ")");
                    // Impact burst on player
                    swoopWorld.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,
                        player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.3, 0.5, 0.3, 0.03);
                    swoopWorld.spawnParticles(net.minecraft.particle.ParticleTypes.DAMAGE_INDICATOR,
                        player.getX(), player.getY() + 0.8, player.getZ(), 5, 0.2, 0.3, 0.2, 0.01);
                    sendSync();
                    if (getPlayerHp() <= 0) { handlePlayerDeathOrGameOver(); return; }
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
                startAttackAnimation(CrafticsMod.CONFIG.enemyTurnDelay());
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
                    // Ghast scream animation when telegraphing an attack
                    triggerGhastScream(currentEnemy, true);
                }
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }

            // === Spider ceiling mechanic ===

            case EnemyAction.CeilingAscend ascend -> {
                sendMessage("§7" + currentEnemy.getDisplayName() + " shoots a web and ascends to the ceiling!");
                // Hide the mob entity (move it way up so it's invisible)
                MobEntity ascendMob = currentEnemy.getMobEntity();
                if (ascendMob != null) {
                    ascendMob.requestTeleport(ascendMob.getX(), ascendMob.getY() + 100, ascendMob.getZ());
                    ascendMob.setInvisible(true);
                }
                // Remove from grid so it doesn't block tiles, but keep in enemies list
                arena.removeEntity(currentEnemy);
                // Mark as "on ceiling" via a tag the AI can check
                currentEnemy.setOnCeiling(true);
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
            }

            case EnemyAction.CeilingDrop drop -> {
                sendMessage("§c" + currentEnemy.getDisplayName() + " drops from the ceiling!");
                // Place the mob back on the grid at the landing position
                currentEnemy.setOnCeiling(false);
                currentEnemy.setGridPos(drop.landingPos());
                arena.placeEntity(currentEnemy);
                // Show the mob entity again at the landing position
                MobEntity dropMob = currentEnemy.getMobEntity();
                if (dropMob != null) {
                    dropMob.setInvisible(false);
                    BlockPos dropBlock = arena.gridToBlockPos(drop.landingPos());
                    dropMob.requestTeleport(dropBlock.getX() + 0.5, dropBlock.getY(), dropBlock.getZ() + 0.5);
                }
                // Landing only — no attack this turn (drop is the action)
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
                    // Render warning particles on the telegraphed tiles
                    spawnBossAbilityTelegraphParticles(ba);
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

            // === Projectile action types ===

            case EnemyAction.SpawnProjectile sp -> {
                sendMessage("§5" + currentEnemy.getDisplayName() + " launches projectiles!");
                spawnProjectiles(sp);
                sendSync();
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay() + 4;
            }
            case EnemyAction.ProjectileMove pm -> {
                if (pm.path().isEmpty()) {
                    if (pm.impacts()) {
                        resolveProjectileImpact(currentEnemy, pm.impactPos());
                    }
                    enemyTurnState = EnemyTurnState.DONE;
                    enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
                } else {
                    startEnemyMove(pm.path());
                }
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
        boolean lowHp = ally.getMaxHp() > 0 && (float) ally.getCurrentHp() / ally.getMaxHp() <= 0.25f;

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

        // ATTACK: find best target using priority scoring
        GridPos playerPos = arena.getPlayerGridPos();
        CombatEntity target = null;
        int bestScore = Integer.MIN_VALUE;
        int targetDist = Integer.MAX_VALUE;
        for (CombatEntity e : enemies) {
            if (!e.isAlive() || e.isAlly()) continue;
            int d = e.minDistanceTo(allyPos);
            int dToPlayer = e.minDistanceTo(playerPos);
            // Score: prefer close targets, targets near player, and wounded targets
            int score = -d;
            if (dToPlayer <= 2) score += 3; // protect the player
            if (e.getCurrentHp() <= e.getMaxHp() / 2) score += 2; // finish wounded
            if (score > bestScore || (score == bestScore && d < targetDist)) {
                bestScore = score;
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
            // In range — attack! Apply pet damage type bonus from player's gear + affinity
            PlayerProgression.PlayerStats allyOwnerStats = PlayerProgression.get(
                (ServerWorld) player.getEntityWorld()).getStats(player);
            int petBonus = DamageType.getTotalBonus(
                PlayerCombatStats.getArmorSet(player), activeTrimScan, combatEffects, DamageType.PET, allyOwnerStats);
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
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        var iterator = pendingBossWarnings.iterator();
        while (iterator.hasNext()) {
            PendingBossWarning pw = iterator.next();
            if (pw.boss() == boss && boss.isAlive()) {
                sendMessage("§c" + boss.getDisplayName() + "'s " +
                    pw.ability().abilityName().replace('_', ' ') + " resolves!");
                // Resolution burst — particles flash on all warning tiles as the attack lands
                java.util.List<GridPos> warnTiles = pw.ability().warningTiles();
                if (warnTiles != null && !warnTiles.isEmpty()) {
                    // Limit to 12 tiles max for burst to avoid lag on huge areas
                    int burstCount = Math.min(warnTiles.size(), 12);
                    for (int i = 0; i < burstCount; i++) {
                        GridPos tile = warnTiles.get(i);
                        if (!arena.isInBounds(tile)) continue;
                        BlockPos bp = arena.gridToBlockPos(tile);
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.FLASH,
                            bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5,
                            1, 0.0, 0.0, 0.0, 0.0);
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,
                            bp.getX() + 0.5, bp.getY() + 0.8, bp.getZ() + 0.5,
                            4, 0.2, 0.4, 0.2, 0.03);
                    }
                }
                // Ghast scream on attack resolution
                triggerGhastScream(boss, true);
                dispatchBossSubAction(pw.ability().resolvedAction());
                iterator.remove();
            }
        }
    }

    /**
     * Dispatch a single boss sub-action (used by BossAbility resolution and CompositeAction).
     */
    private void dispatchBossSubAction(EnemyAction action) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        switch (action) {
            case EnemyAction.Attack atk -> {
                int playerDefense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus();
                int actual = Math.max(1, atk.damage() - playerDefense);
                player.setHealth(Math.max(1, player.getHealth() - actual));
                // Impact particles at player
                world.spawnParticles(net.minecraft.particle.ParticleTypes.DAMAGE_INDICATOR,
                    player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.3, 0.3, 0.3, 0.01);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,
                    player.getX(), player.getY() + 0.8, player.getZ(), 8, 0.4, 0.5, 0.4, 0.02);
                sendMessage("§c  Hit for " + actual + " damage! (HP: " + getPlayerHp() + ")");
                if (getPlayerHp() <= 0) handlePlayerDeathOrGameOver();
            }
            case EnemyAction.SummonMinions sm -> spawnBossMinions(sm);
            case EnemyAction.SpawnProjectile sp -> spawnProjectiles(sp);
            case EnemyAction.AreaAttack aa -> resolveAreaAttack(aa);
            case EnemyAction.CreateTerrain ct -> resolveCreateTerrain(ct);
            case EnemyAction.LineAttack la -> resolveLineAttack(la);
            case EnemyAction.ModifySelf ms -> resolveModifySelf(currentEnemy, ms);
            case EnemyAction.ForcedMovement fm -> resolveForcedMovement(fm);
            case EnemyAction.Teleport tp -> {
                if (currentEnemy != null) {
                    // Departure particles at old position
                    MobEntity mob = currentEnemy.getMobEntity();
                    if (mob != null) {
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL,
                            mob.getX(), mob.getY() + 1.0, mob.getZ(), 20, 0.3, 0.8, 0.3, 0.5);
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.REVERSE_PORTAL,
                            mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, 0.2, 0.5, 0.2, 0.3);
                        double wx = arena.getOrigin().getX() + tp.target().x() + 0.5;
                        double wz = arena.getOrigin().getZ() + tp.target().z() + 0.5;
                        mob.requestTeleport(wx, arena.getOrigin().getY() + 1.0, wz);
                        // Arrival particles at new position
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL,
                            wx, arena.getOrigin().getY() + 2.0, wz, 20, 0.3, 0.8, 0.3, 0.5);
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.REVERSE_PORTAL,
                            wx, arena.getOrigin().getY() + 1.5, wz, 10, 0.2, 0.5, 0.2, 0.3);
                    }
                    arena.moveEntity(currentEnemy, tp.target());
                }
            }
            case EnemyAction.TeleportAndAttack tpa -> {
                if (currentEnemy != null) {
                    MobEntity mob = currentEnemy.getMobEntity();
                    if (mob != null) {
                        // Departure burst
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL,
                            mob.getX(), mob.getY() + 1.0, mob.getZ(), 15, 0.3, 0.8, 0.3, 0.5);
                        double wx = arena.getOrigin().getX() + tpa.target().x() + 0.5;
                        double wy = arena.getOrigin().getY() + 1.0;
                        double wz = arena.getOrigin().getZ() + tpa.target().z() + 0.5;
                        mob.requestTeleport(wx, wy, wz);
                        // Arrival + strike burst
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.REVERSE_PORTAL,
                            wx, wy + 1.0, wz, 15, 0.3, 0.5, 0.3, 0.3);
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,
                            wx, wy + 0.5, wz, 10, 0.4, 0.6, 0.4, 0.03);
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.DAMAGE_INDICATOR,
                            player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.3, 0.3, 0.3, 0.01);
                    }
                    arena.moveEntity(currentEnemy, tpa.target());
                    int playerDefense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus();
                    int actual = Math.max(1, tpa.damage() - playerDefense);
                    player.setHealth(Math.max(1, player.getHealth() - actual));
                    sendMessage("§c  Teleport strike for " + actual + " damage! (HP: " + getPlayerHp() + ")");
                    if (getPlayerHp() <= 0) handlePlayerDeathOrGameOver();
                }
            }
            case EnemyAction.RangedAttack ra -> {
                int playerDefense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus();
                int actual = Math.max(1, ra.damage() - playerDefense);
                player.setHealth(Math.max(1, player.getHealth() - actual));
                // Ranged impact particles based on attack type
                String rangedType = ra.effectName() != null ? ra.effectName() : "";
                switch (rangedType) {
                    case "frost_arrow" -> {
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.SNOWFLAKE,
                            player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANTED_HIT,
                            player.getX(), player.getY() + 0.8, player.getZ(), 5, 0.2, 0.3, 0.2, 0.01);
                    }
                    case "venomous_bite" -> {
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.ITEM_SLIME,
                            player.getX(), player.getY() + 0.8, player.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                            player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.2, 0.3, 0.2, 0.01);
                    }
                    case "hex_bolt" -> {
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.WITCH,
                            player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL,
                            player.getX(), player.getY() + 0.8, player.getZ(), 5, 0.2, 0.3, 0.2, 0.01);
                    }
                    case "shulker_bullet" -> {
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                            player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK,
                            player.getX(), player.getY() + 0.8, player.getZ(), 5, 0.2, 0.3, 0.2, 0.01);
                    }
                    default -> {
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,
                            player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                    }
                }
                sendMessage("§c  Ranged hit for " + actual + " damage! (HP: " + getPlayerHp() + ")");
                if (getPlayerHp() <= 0) handlePlayerDeathOrGameOver();
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
                mob.noClip = true;
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
                // Add to no-collision team
                net.minecraft.scoreboard.Scoreboard sb = world.getScoreboard();
                net.minecraft.scoreboard.Team noCollideTeam = sb.getTeam(ARENA_TEAM_NAME);
                if (noCollideTeam != null) {
                    sb.addScoreHolderToTeam(mob.getUuidAsString(), noCollideTeam);
                }
                // Register with boss AI for tracking
                if (currentEnemy != null && currentEnemy.isBoss()) {
                    EnemyAI bAi = AIRegistry.get(currentEnemy.getAiKey());
                    if (bAi instanceof com.crackedgames.craftics.combat.ai.boss.BossAI bossAi) {
                        bossAi.registerSpawnedMinion(ce.getEntityId());
                    }
                }
                // Boss-themed spawn particles based on minion type
                net.minecraft.particle.ParticleEffect spawnPrimary;
                net.minecraft.particle.ParticleEffect spawnSecondary;
                String minionType = sm.entityTypeId();
                if (minionType.contains("zombie") || minionType.contains("husk")) {
                    spawnPrimary = net.minecraft.particle.ParticleTypes.SMOKE;
                    spawnSecondary = net.minecraft.particle.ParticleTypes.SOUL;
                } else if (minionType.contains("spider") || minionType.contains("cave_spider")) {
                    spawnPrimary = net.minecraft.particle.ParticleTypes.ITEM_SLIME;
                    spawnSecondary = net.minecraft.particle.ParticleTypes.SMOKE;
                } else if (minionType.contains("blaze")) {
                    spawnPrimary = net.minecraft.particle.ParticleTypes.FLAME;
                    spawnSecondary = net.minecraft.particle.ParticleTypes.LAVA;
                } else if (minionType.contains("vex")) {
                    spawnPrimary = net.minecraft.particle.ParticleTypes.WITCH;
                    spawnSecondary = net.minecraft.particle.ParticleTypes.ENCHANT;
                } else if (minionType.contains("silverfish")) {
                    spawnPrimary = net.minecraft.particle.ParticleTypes.ASH;
                    spawnSecondary = net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE;
                } else if (minionType.contains("wither_skeleton")) {
                    spawnPrimary = net.minecraft.particle.ParticleTypes.SOUL;
                    spawnSecondary = net.minecraft.particle.ParticleTypes.LARGE_SMOKE;
                } else if (minionType.contains("enderman") || minionType.contains("endermite")) {
                    spawnPrimary = net.minecraft.particle.ParticleTypes.PORTAL;
                    spawnSecondary = net.minecraft.particle.ParticleTypes.REVERSE_PORTAL;
                } else if (minionType.contains("drowned")) {
                    spawnPrimary = net.minecraft.particle.ParticleTypes.SPLASH;
                    spawnSecondary = net.minecraft.particle.ParticleTypes.BUBBLE;
                } else if (minionType.contains("piglin")) {
                    spawnPrimary = net.minecraft.particle.ParticleTypes.CRIMSON_SPORE;
                    spawnSecondary = net.minecraft.particle.ParticleTypes.FLAME;
                } else if (minionType.contains("shulker")) {
                    spawnPrimary = net.minecraft.particle.ParticleTypes.END_ROD;
                    spawnSecondary = net.minecraft.particle.ParticleTypes.ENCHANTED_HIT;
                } else if (minionType.contains("magma_cube")) {
                    spawnPrimary = net.minecraft.particle.ParticleTypes.LAVA;
                    spawnSecondary = net.minecraft.particle.ParticleTypes.FLAME;
                } else {
                    spawnPrimary = net.minecraft.particle.ParticleTypes.POOF;
                    spawnSecondary = net.minecraft.particle.ParticleTypes.CLOUD;
                }
                world.spawnParticles(spawnPrimary,
                    spawnPos.getX() + 0.5, spawnPos.getY() + 0.8, spawnPos.getZ() + 0.5,
                    6, 0.3, 0.5, 0.3, 0.02);
                world.spawnParticles(spawnSecondary,
                    spawnPos.getX() + 0.5, spawnPos.getY() + 0.3, spawnPos.getZ() + 0.5,
                    4, 0.2, 0.3, 0.2, 0.01);
            }
        }
        if (spawned > 0) {
            sendMessage("§5  " + spawned + " " + sm.entityTypeId().substring(sm.entityTypeId().indexOf(':') + 1)
                + "(s) appeared!");
        }
    }

    /**
     * Spawn projectile entities for a boss SpawnProjectile action.
     */
    private void spawnProjectiles(EnemyAction.SpawnProjectile sp) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        EntityType<?> type = Registries.ENTITY_TYPE.get(Identifier.of(sp.entityTypeId()));
        int spawned = 0;

        // Determine which visual entity to spawn alongside the invisible tracking mob
        String visualEntityId = switch (sp.projectileType()) {
            case "ghast_fireball" -> "minecraft:fireball";
            case "wither_skull" -> "minecraft:wither_skull";
            default -> null;
        };

        for (int i = 0; i < sp.positions().size(); i++) {
            GridPos pos = sp.positions().get(i);
            int[] dir = sp.directions().get(i);

            if (!arena.isInBounds(pos) || arena.isOccupied(pos)) continue;
            GridTile tile = arena.getTile(pos);
            if (tile == null || !tile.isWalkable()) continue;

            BlockPos spawnPos = arena.gridToBlockPos(pos);

            // Spawn the visual projectile entity (fireball / wither skull)
            int spawnedVisualId = -1;
            if (visualEntityId != null) {
                EntityType<?> visualType = Registries.ENTITY_TYPE.get(Identifier.of(visualEntityId));
                var visualEntity = visualType.create(world);
                if (visualEntity != null) {
                    visualEntity.refreshPositionAndAngles(
                        spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5, 0, 0);
                    visualEntity.setNoGravity(true);
                    visualEntity.addCommandTag("craftics_arena");
                    visualEntity.addCommandTag("craftics_visual_projectile");
                    world.spawnEntity(visualEntity);
                    spawnedVisualId = visualEntity.getId();
                }
            }

            // Spawn the invisible tracking mob for grid logic
            var rawEntity = type.create(world, null, spawnPos, SpawnReason.MOB_SUMMONED, false, false);
            if (rawEntity == null) continue;

            rawEntity.refreshPositionAndAngles(
                spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
            if (rawEntity instanceof MobEntity mob) {
                mob.setAiDisabled(true);
                mob.setInvulnerable(true);
                mob.setNoGravity(true);
                mob.noClip = true;
                mob.setPersistent();
                mob.addCommandTag("craftics_arena");
            }
            world.spawnEntity(rawEntity);

            if (rawEntity instanceof MobEntity mob) {
                // Hide the tracking mob when a visual projectile entity exists
                if (visualEntityId != null) {
                    mob.setInvisible(true);
                    mob.setSilent(true);
                    // Scale to near-zero so fire/particle effects don't render
                    var scaleAttr = mob.getAttributeInstance(
                        net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
                    if (scaleAttr != null) scaleAttr.setBaseValue(0.01);
                }
                CombatEntity ce = new CombatEntity(
                    mob.getId(), sp.entityTypeId(), pos,
                    sp.hp(), sp.atk(), sp.def(), 1
                );
                ce.setMobEntity(mob);
                ce.setAiOverrideKey("projectile");
                ce.setProjectile(true);
                ce.setProjectileDirX(dir[0]);
                ce.setProjectileDirZ(dir[1]);
                ce.setProjectileType(sp.projectileType());
                ce.setProjectileOwnerId(currentEnemy != null ? currentEnemy.getEntityId() : -1);
                ce.setVisualProjectileEntityId(spawnedVisualId);
                // Display name based on projectile type
                if ("ghast_fireball".equals(sp.projectileType())) {
                    ce.setBossDisplayName("Fireball");
                } else if ("wither_skull".equals(sp.projectileType())) {
                    ce.setBossDisplayName("Wither Skull");
                }
                enemies.add(ce);
                arena.placeEntity(ce);
                spawned++;

                // Register with boss AI for tracking
                if (currentEnemy != null && currentEnemy.isBoss()) {
                    EnemyAI bAi = AIRegistry.get(currentEnemy.getAiKey());
                    if (bAi instanceof com.crackedgames.craftics.combat.ai.boss.BossAI bossAi) {
                        bossAi.registerSpawnedProjectile(ce.getEntityId());
                    }
                }

                // Themed projectile spawn particles
                if ("ghast_fireball".equals(sp.projectileType())) {
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                        spawnPos.getX() + 0.5, spawnPos.getY() + 0.8, spawnPos.getZ() + 0.5,
                        10, 0.3, 0.5, 0.3, 0.03);
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.LAVA,
                        spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5,
                        5, 0.2, 0.3, 0.2, 0.0);
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
                        spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5,
                        4, 0.15, 0.3, 0.15, 0.01);
                } else if ("wither_skull".equals(sp.projectileType())) {
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL,
                        spawnPos.getX() + 0.5, spawnPos.getY() + 0.8, spawnPos.getZ() + 0.5,
                        8, 0.3, 0.5, 0.3, 0.03);
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                        spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5,
                        5, 0.2, 0.3, 0.2, 0.02);
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                        spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5,
                        4, 0.15, 0.3, 0.15, 0.01);
                } else {
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.POOF,
                        spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5,
                        6, 0.3, 0.3, 0.3, 0.02);
                }
            }
        }
        if (spawned > 0) {
            String name = "ghast_fireball".equals(sp.projectileType()) ? "fireball" : "wither skull";
            sendMessage("§5  " + spawned + " " + name + "(s) launched!");
        }
    }

    /**
     * Sync the visual projectile entity to follow the invisible tracking mob.
     * Uses the stored entity ID for direct lookup — no bounding box search.
     */
    private void syncVisualProjectile(CombatEntity ce, double x, double y, double z) {
        int visualId = ce.getVisualProjectileEntityId();
        if (visualId == -1) return;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        net.minecraft.entity.Entity visual = world.getEntityById(visualId);
        if (visual != null) {
            visual.refreshPositionAndAngles(x, y, z, visual.getYaw(), visual.getPitch());
        }
    }

    /**
     * Kill the visual projectile entity when the combat projectile is destroyed.
     */
    private void killVisualProjectileNear(CombatEntity ce) {
        int visualId = ce.getVisualProjectileEntityId();
        if (visualId == -1 || player == null) return;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        net.minecraft.entity.Entity visual = world.getEntityById(visualId);
        if (visual != null) {
            visual.discard();
        }
    }

    /**
     * Resolve a projectile impact — AOE explosion for fireballs, wither effect for skulls.
     */
    private void resolveProjectileImpact(CombatEntity projectile, GridPos impactPos) {
        if (!projectile.isProjectile() || !projectile.isAlive()) return;

        String type = projectile.getProjectileType();
        int damage = projectile.getAttackPower();
        boolean redirected = projectile.isProjectileRedirected();
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        GridPos effectCenter = impactPos != null ? impactPos : projectile.getGridPos();
        BlockPos bp = arena.gridToBlockPos(effectCenter);

        if ("ghast_fireball".equals(type)) {
            sendMessage("§c\uD83D\uDD25 Fireball explodes!");
            world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION,
                bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.0);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5, 15, 0.8, 0.8, 0.8, 0.04);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.LAVA,
                bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 8, 0.5, 0.3, 0.5, 0.0);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                bp.getX() + 0.5, bp.getY() + 1.5, bp.getZ() + 0.5, 8, 0.6, 0.8, 0.6, 0.02);
            player.getWorld().playSound(null, bp,
                net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                net.minecraft.sound.SoundCategory.HOSTILE, 1.0f, 1.0f);

            int aoeRadius = 1;
            GridPos playerGridPos = arena.getPlayerGridPos();

            // Damage player if in AOE (skip for redirected fireballs — they're heading away)
            if (!redirected
                    && Math.abs(playerGridPos.x() - effectCenter.x()) <= aoeRadius
                    && Math.abs(playerGridPos.z() - effectCenter.z()) <= aoeRadius) {
                int playerDefense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus();
                int actual = Math.max(1, damage - playerDefense);
                player.setHealth(Math.max(1, player.getHealth() - actual));
                sendMessage("§c  Explosion hit for " + actual + " damage! (HP: " + getPlayerHp() + ")");
                if (!combatEffects.hasFireResistance()) {
                    combatEffects.addEffect(CombatEffects.EffectType.BURNING, 2, 0);
                    sendMessage("§6  You're burning! (-1 HP/turn for 2 turns)");
                }
                if (getPlayerHp() <= 0) handlePlayerDeathOrGameOver();
            }

            // Damage all enemies in AOE (always — this is how redirected fireballs damage the boss)
            for (CombatEntity e : new ArrayList<>(enemies)) {
                if (e == projectile || !e.isAlive()) continue;
                if (e.minDistanceTo(effectCenter) <= aoeRadius) {
                    int dealt = e.takeDamage(damage);
                    sendMessage("§6  Explosion hits " + e.getDisplayName() + " for " + dealt + "!");
                    checkAndHandleDeath(e);
                }
            }
        } else if ("wither_skull".equals(type)) {
            sendMessage("§8\u2620 Wither Skull impacts!");
            world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL,
                bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5, 12, 0.4, 0.6, 0.4, 0.03);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                bp.getX() + 0.5, bp.getY() + 0.8, bp.getZ() + 0.5, 8, 0.3, 0.4, 0.3, 0.02);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                bp.getX() + 0.5, bp.getY() + 1.3, bp.getZ() + 0.5, 6, 0.4, 0.5, 0.4, 0.01);

            // Damage + wither to player if hit
            GridPos playerGridPos = arena.getPlayerGridPos();
            if (effectCenter.equals(playerGridPos)) {
                int playerDefense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus();
                int actual = Math.max(1, damage - playerDefense);
                player.setHealth(Math.max(1, player.getHealth() - actual));
                sendMessage("§8  Wither Skull hits for " + actual + " damage!");
                combatEffects.addEffect(CombatEffects.EffectType.WITHER, 3, 0);
                sendMessage("§8  Wither applied! (-2 HP/turn for 3 turns)");
                if (getPlayerHp() <= 0) handlePlayerDeathOrGameOver();
            }
        }

        // Kill the projectile entity
        killEnemy(projectile);
    }

    /**
     * Resolve an AoE attack centered on a tile.
     */
    private void resolveAreaAttack(EnemyAction.AreaAttack aa) {
        GridPos center = aa.center();
        int radius = aa.radius();
        int damage = aa.damage();
        ServerWorld world = (ServerWorld) player.getEntityWorld();

        // Boss-specific particles based on the attack effect
        BlockPos centerBlock = arena.gridToBlockPos(center);
        double cx = centerBlock.getX() + 0.5, cy = centerBlock.getY() + 1.0, cz = centerBlock.getZ() + 0.5;
        double spread = Math.max(0.5, radius * 0.5);
        spawnAreaAttackParticles(world, aa.effectName(), cx, cy, cz, spread, radius);

        // Check player
        GridPos playerGridPos = arena.getPlayerGridPos();
        if (Math.abs(playerGridPos.x() - center.x()) <= radius
            && Math.abs(playerGridPos.z() - center.z()) <= radius) {
            int playerDefense = PlayerCombatStats.getDefense(player) + combatEffects.getResistanceBonus() + getProgDefenseBonus();
            int actual = Math.max(1, damage - playerDefense);
            player.setHealth(Math.max(1, player.getHealth() - actual));
            sendMessage("§c  Area hit for " + actual + " damage! (HP: " + getPlayerHp() + ")");
            // Apply named effects
            if (aa.effectName() != null) {
                applyBossAreaEffect(aa.effectName());
            }
            if (getPlayerHp() <= 0) { handlePlayerDeathOrGameOver(); return; }
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
            case "burning", "fire", "magma", "raining_fireball" -> {
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
     * Spawn thematic particles for boss area attacks based on the attack's effect name.
     * Each boss gets unique particles that match their identity.
     */
    private void spawnAreaAttackParticles(ServerWorld world, String effectName,
                                          double cx, double cy, double cz, double spread, int radius) {
        if (effectName == null) {
            // Generic fallback
            world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION,
                cx, cy, cz, 3, spread, 0.5, spread, 0.0);
            return;
        }
        switch (effectName) {
            // === Molten King ===
            case "magma_eruption" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.LAVA, cx, cy, cz, 15, spread, 0.8, spread, 0.0);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME, cx, cy + 0.5, cz, 20, spread, 1.0, spread, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE, cx, cy, cz, 8, spread, 0.5, spread, 0.01);
            }
            // === Frostbound Huntsman ===
            case "blizzard" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SNOWFLAKE, cx, cy + 1.0, cz, 25, spread, 1.2, spread, 0.03);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, cx, cy + 0.5, cz, 10, spread, 0.6, spread, 0.01);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANTED_HIT, cx, cy, cz, 6, spread, 0.3, spread, 0.0);
            }
            case "glacial_trap" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SNOWFLAKE, cx, cy, cz, 12, spread, 0.3, spread, 0.0);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANTED_HIT, cx, cy + 0.2, cz, 8, spread, 0.2, spread, 0.0);
            }
            // === Hollow King ===
            case "cave_in" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy + 0.5, cz, 12, spread, 0.8, spread, 0.01);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ASH, cx, cy, cz, 20, spread + 0.5, 1.0, spread + 0.5, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, cx, cy, cz, 8, spread, 0.4, spread, 0.0);
            }
            case "lights_out" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE, cx, cy, cz, 15, spread + 1, 1.0, spread + 1, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE, cx, cy + 0.5, cz, 25, spread + 2, 1.5, spread + 2, 0.01);
            }
            // === Rockbreaker ===
            case "seismic_slam" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy, cz, 15, spread, 0.3, spread, 0.0);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, cx, cy + 0.2, cz, 10, spread, 0.4, spread, 0.01);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT, cx, cy + 0.3, cz, 12, spread, 0.5, spread, 0.02);
            }
            case "boulder_toss" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, cx, cy + 0.5, cz, 8, 0.3, 0.5, 0.3, 0.01);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy, cz, 6, 0.4, 0.3, 0.4, 0.0);
            }
            case "avalanche" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ASH, cx, cy + 1.5, cz, 30, spread + 1, 1.5, spread + 1, 0.03);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, cx, cy + 0.8, cz, 15, spread, 1.0, spread, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy, cz, 10, spread, 0.5, spread, 0.01);
            }
            // === Sandstorm Pharaoh ===
            case "sandstorm" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ASH, cx, cy + 1.0, cz, 30, spread + 0.5, 1.5, spread + 0.5, 0.04);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy, cz, 12, spread, 0.8, spread, 0.02);
            }
            case "sand_burial" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ASH, cx, cy, cz, 15, spread, 0.3, spread, 0.0);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, cx, cy + 0.1, cz, 6, spread, 0.2, spread, 0.0);
            }
            case "plant_mine" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ASH, cx, cy, cz, 3, 0.2, 0.1, 0.2, 0.0);
            }
            case "curse_of_sands" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ASH, cx, cy + 0.5, cz, 12, 0.3, 0.5, 0.3, 0.01);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.WITCH, cx, cy + 1.0, cz, 5, 0.2, 0.3, 0.2, 0.0);
            }
            // === Chorus Mind ===
            case "resonance_cascade" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD, cx, cy + 0.5, cz, 20, spread + 1, 1.0, spread + 1, 0.03);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL, cx, cy, cz, 30, spread, 1.5, spread, 0.5);
            }
            case "entangle" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER, cx, cy, cz, 15, spread, 0.4, spread, 0.0);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.COMPOSTER, cx, cy + 0.3, cz, 10, spread, 0.3, spread, 0.0);
            }
            case "chorus_bomb", "chorus_bomb_pull" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL, cx, cy, cz, 25, spread, 1.0, spread, 0.5);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.REVERSE_PORTAL, cx, cy + 0.5, cz, 15, spread, 0.8, spread, 0.3);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD, cx, cy + 0.3, cz, 5, spread, 0.5, spread, 0.01);
            }
            // === Ashen Warlord ===
            case "ash_storm" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ASH, cx, cy + 1.0, cz, 30, spread + 0.5, 1.5, spread + 0.5, 0.03);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME, cx, cy + 0.5, cz, 10, spread, 0.8, spread, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy, cz, 8, spread, 0.5, spread, 0.01);
            }
            case "wither_slash" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL, cx, cy + 0.5, cz, 12, spread, 0.6, spread, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE, cx, cy, cz, 8, spread, 0.3, spread, 0.01);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT, cx, cy + 0.3, cz, 6, spread, 0.4, spread, 0.0);
            }
            case "fire_pillar_x" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME, cx, cy + 1.0, cz, 20, spread, 1.5, spread, 0.03);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME, cx, cy + 0.5, cz, 10, spread, 1.0, spread, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE, cx, cy, cz, 8, spread, 0.5, spread, 0.01);
            }
            // === Crimson Ravager ===
            case "rampage" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT, cx, cy + 0.5, cz, 20, spread, 0.8, spread, 0.03);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.DAMAGE_INDICATOR, cx, cy + 0.3, cz, 10, spread, 0.5, spread, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIMSON_SPORE, cx, cy, cz, 15, spread + 0.5, 0.6, spread + 0.5, 0.01);
            }
            // === Broodmother ===
            case "web_spray" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ITEM_SLIME, cx, cy + 0.5, cz, 15, spread, 0.8, spread, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, cx, cy, cz, 8, spread, 0.3, spread, 0.0);
            }
            case "web_spray_poison" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ITEM_SLIME, cx, cy + 0.5, cz, 15, spread, 0.8, spread, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER, cx, cy + 0.3, cz, 8, spread, 0.4, spread, 0.01);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, cx, cy, cz, 5, spread, 0.3, spread, 0.0);
            }
            case "pounce" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT, cx, cy + 0.3, cz, 12, spread, 0.5, spread, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, cx, cy, cz, 8, spread, 0.3, spread, 0.01);
            }
            // === Hexweaver ===
            case "cursed_fog" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.WITCH, cx, cy + 0.8, cz, 20, spread, 1.0, spread, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE, cx, cy, cz, 15, spread + 0.5, 0.5, spread + 0.5, 0.01);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL, cx, cy + 0.5, cz, 6, spread, 0.6, spread, 0.01);
            }
            // === Shulker Architect ===
            case "bullet_storm" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD, cx, cy + 0.5, cz, 15, spread, 0.8, spread, 0.03);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANTED_HIT, cx, cy + 0.3, cz, 10, spread, 0.5, spread, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 8, spread, 0.3, spread, 0.01);
            }
            // === Tidecaller ===
            case "trident_storm" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SPLASH, cx, cy + 0.5, cz, 20, spread, 0.8, spread, 0.03);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.BUBBLE, cx, cy, cz, 15, spread, 0.5, spread, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.DRIPPING_WATER, cx, cy + 1.0, cz, 8, spread, 0.3, spread, 0.0);
            }
            // === Void Herald ===
            case "lightning_strike" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK, cx, cy + 1.5, cz, 25, spread, 2.0, spread, 0.05);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.FLASH, cx, cy + 1.0, cz, 3, 0.1, 0.1, 0.1, 0.0);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD, cx, cy, cz, 8, spread, 0.5, spread, 0.01);
            }
            case "blink_assault" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL, cx, cy, cz, 20, spread, 1.0, spread, 0.5);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.REVERSE_PORTAL, cx, cy + 0.5, cz, 12, spread, 0.8, spread, 0.3);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT, cx, cy + 0.3, cz, 8, spread, 0.4, spread, 0.02);
            }
            // === Wither Boss ===
            case "wither_explosion" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL, cx, cy + 1.0, cz, 25, spread + 1, 1.5, spread + 1, 0.04);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE, cx, cy + 0.5, cz, 15, spread, 1.0, spread, 0.03);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE, cx, cy, cz, 20, spread + 0.5, 0.8, spread + 0.5, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION, cx, cy, cz, 5, spread, 0.5, spread, 0.0);
            }
            // === Wailing Revenant ===
            case "soul_chain" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL, cx, cy + 0.5, cz, 12, 0.3, 0.6, 0.3, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME, cx, cy + 0.3, cz, 8, 0.2, 0.4, 0.2, 0.01);
            }
            case "wail_despair", "wail_despair_slow" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SCULK_SOUL, cx, cy + 1.0, cz, 15, spread + 1, 1.5, spread + 1, 0.03);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL, cx, cy + 0.5, cz, 20, spread + 2, 1.0, spread + 2, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE, cx, cy, cz, 10, spread, 0.5, spread, 0.01);
            }
            case "raining_fireball" -> {
                // Individual fireball impact from the sky
                world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME, cx, cy + 2.0, cz, 12, 0.2, 1.5, 0.2, 0.04);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.LAVA, cx, cy + 0.5, cz, 6, 0.3, 0.3, 0.3, 0.0);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE, cx, cy + 1.0, cz, 5, 0.2, 0.8, 0.2, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION, cx, cy + 0.5, cz, 1, 0.1, 0.1, 0.1, 0.0);
            }
            // === Revenant ===
            case "death_charge" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE, cx, cy + 0.5, cz, 12, spread, 0.8, spread, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT, cx, cy + 0.3, cz, 8, spread, 0.5, spread, 0.01);
            }
            // === Fungal Growth (Crimson Ravager healing) ===
            case "fungal_growth" -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIMSON_SPORE, cx, cy + 0.5, cz, 20, spread, 0.8, spread, 0.02);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER, cx, cy + 0.3, cz, 8, spread, 0.4, spread, 0.0);
            }
            // === Generic fallback for unknown effects ===
            default -> {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION,
                    cx, cy, cz, 3, spread, 0.5, spread, 0.0);
            }
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
            // Update the visual block in the world (floor level, not above)
            BlockPos bp = new BlockPos(
                arena.getOrigin().getX() + pos.x(),
                arena.getOrigin().getY(),
                arena.getOrigin().getZ() + pos.z());
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
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        boolean hitPlayer = false;

        // Determine line particle type based on the current boss
        net.minecraft.particle.ParticleEffect lineParticle = net.minecraft.particle.ParticleTypes.CRIT;
        net.minecraft.particle.ParticleEffect trailParticle = net.minecraft.particle.ParticleTypes.SMOKE;
        if (currentEnemy != null) {
            String aiKey = currentEnemy.getAiKey();
            if (aiKey != null) {
                if (aiKey.contains("ashen_warlord") || aiKey.contains("AshenWarlord")) {
                    lineParticle = net.minecraft.particle.ParticleTypes.FLAME;
                    trailParticle = net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME;
                } else if (aiKey.contains("hexweaver") || aiKey.contains("Hexweaver")) {
                    lineParticle = net.minecraft.particle.ParticleTypes.WITCH;
                    trailParticle = net.minecraft.particle.ParticleTypes.SOUL;
                } else if (aiKey.contains("hollow_king") || aiKey.contains("HollowKing")) {
                    lineParticle = net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE;
                    trailParticle = net.minecraft.particle.ParticleTypes.ASH;
                } else if (aiKey.contains("revenant") || aiKey.contains("Revenant")) {
                    lineParticle = net.minecraft.particle.ParticleTypes.SMOKE;
                    trailParticle = net.minecraft.particle.ParticleTypes.SOUL;
                } else if (aiKey.contains("crimson") || aiKey.contains("Crimson")) {
                    lineParticle = net.minecraft.particle.ParticleTypes.CRIMSON_SPORE;
                    trailParticle = net.minecraft.particle.ParticleTypes.FLAME;
                } else if (aiKey.contains("wither") || aiKey.contains("Wither")) {
                    lineParticle = net.minecraft.particle.ParticleTypes.SOUL;
                    trailParticle = net.minecraft.particle.ParticleTypes.LARGE_SMOKE;
                } else if (aiKey.contains("tidecaller") || aiKey.contains("Tidecaller")) {
                    lineParticle = net.minecraft.particle.ParticleTypes.SPLASH;
                    trailParticle = net.minecraft.particle.ParticleTypes.BUBBLE;
                }
            }
        }

        for (int i = 0; i < la.length(); i++) {
            GridPos tile = new GridPos(la.start().x() + la.dx() * i, la.start().z() + la.dz() * i);
            if (!arena.isInBounds(tile)) continue;
            if (tile.equals(playerGridPos)) hitPlayer = true;

            // Spawn line particles along each tile
            BlockPos bp = arena.gridToBlockPos(tile);
            world.spawnParticles(lineParticle,
                bp.getX() + 0.5, bp.getY() + 0.8, bp.getZ() + 0.5,
                5, 0.2, 0.4, 0.2, 0.02);
            world.spawnParticles(trailParticle,
                bp.getX() + 0.5, bp.getY() + 0.3, bp.getZ() + 0.5,
                3, 0.15, 0.2, 0.15, 0.01);

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
            player.setHealth(Math.max(1, player.getHealth() - actual));
            sendMessage("§c  Line attack hits for " + actual + " damage! (HP: " + getPlayerHp() + ")");
            if (getPlayerHp() <= 0) handlePlayerDeathOrGameOver();
        }
    }

    /**
     * Resolve a boss self-modification (buff/debuff).
     */
    private void resolveModifySelf(CombatEntity boss, EnemyAction.ModifySelf ms) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        MobEntity bMob = boss.getMobEntity();
        double bx = bMob != null ? bMob.getX() : 0;
        double by = bMob != null ? bMob.getY() + 1.0 : 0;
        double bz = bMob != null ? bMob.getZ() : 0;

        switch (ms.stat()) {
            case "speed" -> {
                boss.setSpeedBonus(boss.getSpeedBonus() + ms.amount());
                // Speed boost — wind swirl
                if (bMob != null) {
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,
                        bx, by, bz, 8, 0.4, 0.6, 0.4, 0.03);
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.SWEEP_ATTACK,
                        bx, by - 0.3, bz, 2, 0.2, 0.1, 0.2, 0.0);
                }
                sendMessage("§d  " + boss.getDisplayName() + " gains +" + ms.amount() + " speed!");
            }
            case "defense" -> {
                boss.setDefensePenalty(boss.getDefensePenalty() - ms.amount());
                if (ms.duration() > 0) {
                    boss.setDefensePenaltyTurns(ms.duration());
                }
                // Defense boost — armored shimmer
                if (bMob != null) {
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANTED_HIT,
                        bx, by, bz, 12, 0.4, 0.6, 0.4, 0.02);
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                        bx, by + 0.3, bz, 5, 0.3, 0.5, 0.3, 0.01);
                }
                sendMessage("§d  " + boss.getDisplayName() + " gains +" + ms.amount() + " defense!");
            }
            case "attack" -> {
                boss.setAttackPenalty(boss.getAttackPenalty() - ms.amount());
                // Attack boost — aggressive red sparks
                if (bMob != null) {
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,
                        bx, by, bz, 10, 0.3, 0.5, 0.3, 0.03);
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.DAMAGE_INDICATOR,
                        bx, by + 0.3, bz, 5, 0.2, 0.3, 0.2, 0.01);
                }
                sendMessage("§d  " + boss.getDisplayName() + " gains +" + ms.amount() + " attack!");
            }
            case "hp", "heal" -> {
                boss.heal(ms.amount());
                // Heal — green sparkles + hearts
                if (bMob != null) {
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                        bx, by, bz, 10, 0.4, 0.6, 0.4, 0.02);
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.HEART,
                        bx, by + 0.5, bz, 3, 0.3, 0.3, 0.3, 0.0);
                }
                sendMessage("§a  " + boss.getDisplayName() + " heals for " + ms.amount() + " HP!");
            }
            default -> {
                if (bMob != null) {
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANT,
                        bx, by, bz, 8, 0.3, 0.5, 0.3, 0.02);
                }
                sendMessage("§d  " + boss.getDisplayName() + " modifies " + ms.stat() + "!");
            }
        }
    }

    /**
     * Resolve forced movement — push/pull a target entity.
     */
    private void resolveForcedMovement(EnemyAction.ForcedMovement fm) {
        ServerWorld fmWorld = (ServerWorld) player.getEntityWorld();
        if (fm.targetEntityId() == -1) {
            // Move the player — spawn wind particles along push/pull path
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
                    player.setHealth(Math.max(1, player.getHealth() - fallDmg));
                    sendMessage("§c  Pushed into the void for " + fallDmg + " damage! (HP: " + getPlayerHp() + ")");
                    if (getPlayerHp() <= 0) { handlePlayerDeathOrGameOver(); return; }
                    break;
                }
                if (tile == null || !tile.isWalkable()) break;
                // Wind trail along movement path
                BlockPos pathBp = arena.gridToBlockPos(candidate);
                fmWorld.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,
                    pathBp.getX() + 0.5, pathBp.getY() + 0.8, pathBp.getZ() + 0.5,
                    3, 0.2, 0.3, 0.2, 0.02);
                fmWorld.spawnParticles(net.minecraft.particle.ParticleTypes.SWEEP_ATTACK,
                    pathBp.getX() + 0.5, pathBp.getY() + 0.5, pathBp.getZ() + 0.5,
                    1, 0.1, 0.1, 0.1, 0.0);
                landingPos = candidate;
            }
            if (!landingPos.equals(playerGridPos)) {
                arena.setPlayerGridPos(landingPos);
                BlockPos bp = arena.gridToBlockPos(landingPos);
                player.requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                // Landing impact
                fmWorld.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,
                    bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5,
                    5, 0.3, 0.3, 0.3, 0.01);
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
        // Always highlight affected tiles via the client-side red overlay
        for (GridPos tile : warning.getAffectedTiles()) {
            highlightWarningTile(tile);
        }

        // Spawn thematic particles based on warning type and color
        switch (warning.getType()) {
            case GROUND_CRACK -> {
                // Pick thematic crack particles based on boss color
                net.minecraft.particle.ParticleEffect crackParticle = getWarningParticleForColor(warning.getColor());
                spawnWarningParticles(warning.getAffectedTiles(), crackParticle, 3);
                // Additional rumble particles for all ground cracks
                spawnWarningParticles(warning.getAffectedTiles(), net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE, 1);
            }
            case GATHERING_PARTICLES -> {
                // Converging particles — boss is charging up
                net.minecraft.particle.ParticleEffect gatherParticle = getWarningParticleForColor(warning.getColor());
                spawnWarningParticles(warning.getAffectedTiles(), gatherParticle, 2);
                spawnWarningParticles(warning.getAffectedTiles(), net.minecraft.particle.ParticleTypes.ENCHANT, 3);
            }
            case TILE_HIGHLIGHT -> {
                // Danger zone — subtle shimmer particles on top of the red overlay
                net.minecraft.particle.ParticleEffect shimmerParticle = getWarningParticleForColor(warning.getColor());
                spawnWarningParticles(warning.getAffectedTiles(), shimmerParticle, 2);
            }
            case DIRECTIONAL -> {
                // Arrow-like — directional wind/spark particles
                spawnWarningParticles(warning.getAffectedTiles(), net.minecraft.particle.ParticleTypes.CLOUD, 2);
                spawnWarningParticles(warning.getAffectedTiles(), net.minecraft.particle.ParticleTypes.CRIT, 1);
            }
            case ENTITY_GLOW -> {
                // Boss is powering up — bright sparks
                spawnWarningParticles(warning.getAffectedTiles(), net.minecraft.particle.ParticleTypes.END_ROD, 2);
                spawnWarningParticles(warning.getAffectedTiles(), net.minecraft.particle.ParticleTypes.ENCHANTED_HIT, 1);
            }
            case SOUND_ONLY -> {} // No particles for sound-only warnings
        }
    }

    /**
     * Pick a thematic warning particle based on the boss warning ARGB color.
     * Uses the dominant color channel to determine the boss theme.
     */
    private net.minecraft.particle.ParticleEffect getWarningParticleForColor(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        // Fire/lava bosses: heavy red, low blue
        if (r > 180 && b < 100 && g < 150) return net.minecraft.particle.ParticleTypes.FLAME;
        // Ice/water bosses: heavy blue
        if (b > 150 && r < 100) return net.minecraft.particle.ParticleTypes.SNOWFLAKE;
        // Water/tidal: moderate blue with some green
        if (b > 130 && g > 80 && r < 100) return net.minecraft.particle.ParticleTypes.SPLASH;
        // Nature/chorus: heavy green
        if (g > 150 && r < 150 && b < 150) return net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER;
        // Void/ender: purple tones (r and b both high, g low)
        if (r > 60 && b > 100 && g < 100) return net.minecraft.particle.ParticleTypes.PORTAL;
        // Wither/dark: very dark colors
        if (r < 80 && g < 80 && b < 80) return net.minecraft.particle.ParticleTypes.SOUL;
        // Sand/earth: tan/brown
        if (r > 150 && g > 100 && b < 100) return net.minecraft.particle.ParticleTypes.ASH;
        // White/bright: phase transitions
        if (r > 200 && g > 200 && b > 200) return net.minecraft.particle.ParticleTypes.FLASH;
        // Default
        return net.minecraft.particle.ParticleTypes.CRIT;
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
     * Spawn telegraph particles when a BossAbility warning is first placed.
     * These give the player an immediate visual cue that danger is incoming.
     */
    private void spawnBossAbilityTelegraphParticles(EnemyAction.BossAbility ba) {
        if (player == null || arena == null) return;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        String name = ba.abilityName();
        java.util.List<GridPos> tiles = ba.warningTiles();
        if (tiles == null || tiles.isEmpty()) return;

        // Pick telegraph particles based on ability name
        net.minecraft.particle.ParticleEffect primary;
        net.minecraft.particle.ParticleEffect secondary;
        switch (name) {
            case "ash_storm" -> { primary = net.minecraft.particle.ParticleTypes.ASH; secondary = net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME; }
            case "wither_slash", "wither_applied" -> { primary = net.minecraft.particle.ParticleTypes.SOUL; secondary = net.minecraft.particle.ParticleTypes.SMOKE; }
            case "fungal_growth" -> { primary = net.minecraft.particle.ParticleTypes.CRIMSON_SPORE; secondary = net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER; }
            case "absorb" -> { primary = net.minecraft.particle.ParticleTypes.LAVA; secondary = net.minecraft.particle.ParticleTypes.FLAME; }
            case "fortify_shell", "fortify_shell_reflect" -> { primary = net.minecraft.particle.ParticleTypes.END_ROD; secondary = net.minecraft.particle.ParticleTypes.ENCHANTED_HIT; }
            case "soul_chain" -> { primary = net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME; secondary = net.minecraft.particle.ParticleTypes.SOUL; }
            case "void_rift" -> { primary = net.minecraft.particle.ParticleTypes.PORTAL; secondary = net.minecraft.particle.ParticleTypes.REVERSE_PORTAL; }
            case "lights_out" -> { primary = net.minecraft.particle.ParticleTypes.LARGE_SMOKE; secondary = net.minecraft.particle.ParticleTypes.SMOKE; }
            default -> { primary = net.minecraft.particle.ParticleTypes.ENCHANT; secondary = net.minecraft.particle.ParticleTypes.CRIT; }
        }

        // Spawn a modest burst on each warned tile (not too many — these persist visually)
        for (GridPos tile : tiles) {
            if (!arena.isInBounds(tile)) continue;
            BlockPos bp = arena.gridToBlockPos(tile);
            world.spawnParticles(primary,
                bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5,
                3, 0.2, 0.3, 0.2, 0.01);
            world.spawnParticles(secondary,
                bp.getX() + 0.5, bp.getY() + 0.2, bp.getZ() + 0.5,
                2, 0.15, 0.15, 0.15, 0.005);
        }
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
                    // If tile reset to NORMAL, restore the floor block
                    if (tile.getType() == TileType.NORMAL) {
                        BlockPos bp = new BlockPos(
                            arena.getOrigin().getX() + x,
                            arena.getOrigin().getY(),
                            arena.getOrigin().getZ() + z);
                        world.setBlockState(bp, tile.getBlockType().getDefaultState());
                    }
                }
            }
        }
    }

    /**
     * Detonate all pending TNT blocks. Called at the start of each new round.
     * Deals AoE damage (8/5/3 by manhattan distance), damages the player too if close,
     * removes the TNT block, and spawns a big explosion effect.
     */
    private void detonatePendingTnts() {
        if (pendingTnts.isEmpty()) return;
        ServerWorld world = (ServerWorld) player.getEntityWorld();

        for (PendingTnt tnt : pendingTnts) {
            GridPos center = tnt.tile();
            BlockPos bp = tnt.blockPos();

            // Remove the TNT block — replace with the arena floor
            GridTile floorTile = arena.getTile(center);
            if (floorTile != null) {
                world.setBlockState(bp, floorTile.getBlockType().getDefaultState());
            } else {
                world.setBlockState(bp, Blocks.AIR.getDefaultState());
            }

            // --- Explosion particles ---
            world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION_EMITTER,
                bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5,
                1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION,
                bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5,
                5, 1.0, 0.8, 1.0, 0.0);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5,
                25, 1.5, 1.0, 1.5, 0.05);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.LAVA,
                bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5,
                12, 1.0, 0.5, 1.0, 0.0);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                bp.getX() + 0.5, bp.getY() + 1.5, bp.getZ() + 0.5,
                15, 1.2, 1.0, 1.2, 0.03);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                bp.getX() + 0.5, bp.getY() + 2.0, bp.getZ() + 0.5,
                8, 0.8, 0.5, 0.8, 0.01);

            // Explosion sound
            world.playSound(null, bp,
                net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                net.minecraft.sound.SoundCategory.BLOCKS, 1.5f, 0.9f);

            sendMessage("§c§lBOOM! §r§7TNT detonates!");

            // --- AoE damage to enemies ---
            int totalDamage = 0;
            int enemiesHit = 0;
            int killCount = 0;
            for (CombatEntity enemy : new ArrayList<>(enemies)) {
                if (!enemy.isAlive()) continue;
                int dist = Math.abs(enemy.getGridPos().x() - center.x())
                         + Math.abs(enemy.getGridPos().z() - center.z());
                if (dist <= 2) {
                    int dmg = dist == 0 ? 8 : (dist == 1 ? 5 : 3);
                    int dealt = enemy.takeDamage(dmg);
                    totalDamage += dealt;
                    enemiesHit++;
                    if (!enemy.isAlive()) {
                        killCount++;
                        checkAndHandleDeath(enemy);
                    }
                }
            }

            // Track TNT kills for achievement
            if (killCount > 0) {
                achievementTracker.recordTntKills(killCount);
            }

            // Self-damage if player is close
            GridPos playerPos = arena.getPlayerGridPos();
            int playerDist = Math.abs(playerPos.x() - center.x()) + Math.abs(playerPos.z() - center.z());
            if (playerDist <= 2) {
                int selfDmg = playerDist == 0 ? 6 : (playerDist == 1 ? 4 : 2);
                player.setHealth(Math.max(1, player.getHealth() - selfDmg));
                sendMessage("§c  Blast hit " + enemiesHit + " enemies for " + totalDamage + " total! You took " + selfDmg + " blast damage!");
                if (getPlayerHp() <= 0) { handlePlayerDeathOrGameOver(); return; }
            } else if (enemiesHit > 0) {
                sendMessage("§7  Hit " + enemiesHit + " enemies for " + totalDamage + " total damage!");
            }
        }
        pendingTnts.clear();
        sendSync();
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

        // Wailing Revenant (Ghast): position OUTSIDE the arena on the low-Z edge.
        // The mob sits beyond the front row, facing +Z (toward the arena).
        // The entire front row (z = 0) is registered as targetable.
        if (ai instanceof WailingRevenantAI) {
            int arenaW = arena.getWidth();
            int arenaH = arena.getHeight();

            // Remove from current grid position
            arena.removeEntity(bossEntity);

            // Mark as background boss — tiles it occupies don't block movement
            bossEntity.setBackgroundBoss(true);

            // Anchor at grid (0, 0) with size 1
            bossEntity.setGridPos(new GridPos(0, 0));
            bossEntity.setSize(1);

            // Register the boss on every tile in the front row (z=0) for targeting.
            // These tiles are still walkable thanks to the backgroundBoss flag.
            // If another enemy already occupies a tile, move it away first.
            for (int x = 0; x < arenaW; x++) {
                GridPos bossSlot = new GridPos(x, 0);
                CombatEntity existing = arena.getOccupant(bossSlot);
                if (existing != null && existing != bossEntity) {
                    // Relocate the existing enemy to the nearest open tile
                    GridPos relocated = findNearestValidSpawn(arena, new GridPos(x, 1), existing.getSize());
                    if (relocated != null) {
                        arena.moveEntity(existing, relocated);
                        MobEntity eMob = existing.getMobEntity();
                        if (eMob != null) {
                            BlockPos bp = arena.gridToBlockPos(relocated);
                            eMob.refreshPositionAndAngles(
                                bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5, eMob.getYaw(), eMob.getPitch());
                        }
                    }
                }
                arena.getOccupants().put(bossSlot, bossEntity);
            }

            // Place the mob entity OUTSIDE the arena, beyond the front edge.
            MobEntity mob = bossEntity.getMobEntity();
            if (mob != null) {
                double bossX = arena.getOrigin().getX() + (arenaW - 1) / 2.0 + 0.5; // true center of grid
                double bossZ = arena.getOrigin().getZ() - 10.0; // 10 blocks beyond front edge
                double bossY = arena.getOrigin().getY() + 2.0;
                mob.refreshPositionAndAngles(bossX, bossY, bossZ, 0.0f, 0.0f); // yaw 0 = face +Z
                mob.setYaw(0.0f);
                mob.setHeadYaw(0.0f);
                mob.setBodyYaw(0.0f);
            }
        }
    }

    /**
     * Trigger or reset the ghast scream animation (open mouth texture).
     * Only affects ghast entities.
     */
    private void triggerGhastScream(CombatEntity entity, boolean screaming) {
        MobEntity mob = entity.getMobEntity();
        if (mob instanceof net.minecraft.entity.mob.GhastEntity ghast) {
            ghast.setShooting(screaming);
            if (screaming) {
                // Play ghast scream sound
                ServerWorld world = (ServerWorld) player.getEntityWorld();
                world.playSound(null, mob.getBlockPos(),
                    net.minecraft.sound.SoundEvents.ENTITY_GHAST_SCREAM,
                    net.minecraft.sound.SoundCategory.HOSTILE, 1.5f, 1.0f);
            }
        }
    }

    private static final String ARENA_TEAM_NAME = "craftics_nocollide";

    /**
     * Create a scoreboard team with collision disabled and add all arena mobs + players to it.
     * This prevents vanilla entity pushing during combat.
     */
    private void setupNoCollisionTeam() {
        if (player == null) return;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        net.minecraft.scoreboard.Scoreboard scoreboard = world.getScoreboard();

        // Create or get the team
        net.minecraft.scoreboard.Team team = scoreboard.getTeam(ARENA_TEAM_NAME);
        if (team == null) {
            team = scoreboard.addTeam(ARENA_TEAM_NAME);
        }
        team.setCollisionRule(net.minecraft.scoreboard.AbstractTeam.CollisionRule.NEVER);

        // Add all arena mobs
        for (CombatEntity e : enemies) {
            MobEntity mob = e.getMobEntity();
            if (mob != null) {
                scoreboard.addScoreHolderToTeam(mob.getUuidAsString(), team);
            }
        }
        // Add all players
        for (ServerPlayerEntity p : getAllParticipants()) {
            if (p != null) {
                scoreboard.addScoreHolderToTeam(p.getUuidAsString(), team);
            }
        }
    }

    /**
     * Remove the no-collision team on combat end.
     */
    private void cleanupNoCollisionTeam() {
        if (player == null) return;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        net.minecraft.scoreboard.Scoreboard scoreboard = world.getScoreboard();
        net.minecraft.scoreboard.Team team = scoreboard.getTeam(ARENA_TEAM_NAME);
        if (team != null) {
            scoreboard.removeTeam(team);
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

            // Projectile impact after movement completes
            if (pendingAction instanceof EnemyAction.ProjectileMove pm && pm.impacts()) {
                resolveProjectileImpact(currentEnemy, pm.impactPos());
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
                return;
            }

            // If MoveAndAttack (any variant), proceed to attack
            if (pendingAction instanceof EnemyAction.MoveAndAttack
                    || pendingAction instanceof EnemyAction.MoveAndAttackMob
                    || pendingAction instanceof EnemyAction.MoveAndAttackWithKnockback) {
                startAttackAnimation(Math.max(1, CrafticsMod.CONFIG.enemyTurnDelay() / 2));
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
            // Face movement direction
            GridPos next = enemyMovePath.get(enemyMovePathIndex);
            GridPos prev = enemyMovePathIndex == 0 ? currentEnemy.getGridPos() : enemyMovePath.get(enemyMovePathIndex - 1);
            int dx = next.x() - prev.x();
            int dz = next.z() - prev.z();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            if ("minecraft:ghast".equals(currentEnemy.getEntityTypeId())) {
                yaw = snapToCardinalYaw(yaw);
            }
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

        // Sync visual projectile entity position with the invisible tracking mob
        if (currentEnemy.isProjectile()) {
            syncVisualProjectile(currentEnemy, x, y + 0.5, z);
        }

        if (enemyMoveTickCounter >= emTicks2) {
            arena.moveEntity(currentEnemy, next);

            // Check if enemy walked onto a void/death tile
            if (checkEnemyFallDeath(currentEnemy)) {
                enemyLerpInitialized = false;
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
                return;
            }

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

            // Check for honey trap — enemy loses all remaining movement
            if (tileEffects.containsKey(next) && "honey".equals(tileEffects.get(next))) {
                sendMessage("§e" + currentEnemy.getDisplayName() + " is stuck in honey!");
                tileEffects.remove(next);
                enemyLerpInitialized = false;
                // Skip remaining movement — go directly to DONE
                enemyMovePathIndex = enemyMovePath.size();
                enemyTurnState = EnemyTurnState.DONE;
                enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
                return;
            }

            // Check for cake — enemy heals 2 HP, one use consumed
            if (tileEffects.containsKey(next) && tileEffects.get(next).startsWith("cake")) {
                // cake format: "cake" or "cake:usesRemaining"
                String cakeData = tileEffects.get(next);
                int uses = 3;
                if (cakeData.contains(":")) {
                    try { uses = Integer.parseInt(cakeData.split(":")[1]); } catch (Exception ignored) {}
                }
                // Enemies heal from cake too
                if (currentEnemy.getCurrentHp() < currentEnemy.getMaxHp()) {
                    currentEnemy.heal(2);
                    sendMessage("§d" + currentEnemy.getDisplayName() + " eats cake! +2 HP");
                }
                uses--;
                if (uses <= 0) {
                    tileEffects.remove(next);
                } else {
                    tileEffects.put(next, "cake:" + uses);
                }
            }

            enemyLerpInitialized = false;
            enemyMovePathIndex++;
        }
    }

    /**
     * Returns the ranged projectile effect name for a mob type, or null if melee.
     * Used to determine animation style (ranged aim vs melee lunge).
     */
    private static String getRangedProjectileType(String entityTypeId) {
        return switch (entityTypeId) {
            case "minecraft:skeleton", "minecraft:bogged" -> "arrow";
            case "minecraft:stray" -> "frost_arrow";
            case "minecraft:pillager" -> "crossbow";
            case "minecraft:blaze" -> "fireball";
            case "minecraft:ghast" -> "fireball";
            case "minecraft:witch" -> "potion";
            case "minecraft:shulker" -> "shulker_bullet";
            case "minecraft:llama", "minecraft:trader_llama" -> "llama_spit";
            case "minecraft:drowned" -> "trident";
            case "minecraft:breeze" -> "fireball";
            default -> null;
        };
    }

    /** Initialize attack animation and transition to ANIMATING state. */
    private void startAttackAnimation(int delay) {
        attackAnimTick = 0;
        attackAnimSwung = false;
        enemyTurnState = EnemyTurnState.ANIMATING;
        enemyTurnDelay = delay;
    }

    /**
     * Attack animation phase: mob-type-specific animations before damage is applied.
     * Ranged mobs lean back to aim then snap forward on release.
     * Melee mobs lunge forward then return. Spider crouches before pouncing.
     * On completion, transitions to ATTACKING to apply damage.
     */
    private void tickEnemyAnimating() {
        net.minecraft.entity.mob.MobEntity mob = currentEnemy.getMobEntity();
        if (mob == null || !mob.isAlive() || mob.isRemoved()) {
            enemyTurnState = EnemyTurnState.ATTACKING;
            return;
        }

        attackAnimTick++;

        String entityType = currentEnemy.getEntityTypeId();
        // Ranged mobs use ranged anim even when they MoveAndAttack (move to get LOS, then shoot)
        boolean isRanged = pendingAction instanceof EnemyAction.RangedAttack
            || (pendingAction instanceof EnemyAction.MoveAndAttack && getRangedProjectileType(entityType) != null);

        // Tick 1: Face the player and initialize
        if (!attackAnimSwung) {
            attackAnimSwung = true;
            attackAnimOriginX = mob.getX();
            attackAnimOriginY = mob.getY();
            attackAnimOriginZ = mob.getZ();

            // Face toward the player
            double dx = player.getX() - mob.getX();
            double dz = player.getZ() - mob.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            // Ghasts must always face a cardinal direction
            if ("minecraft:ghast".equals(entityType)) {
                yaw = snapToCardinalYaw(yaw);
            }
            mob.setYaw(yaw);
            mob.setHeadYaw(yaw);

            // Compute direction unit vector toward player
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.01) {
                attackAnimLungeX = dx / dist;
                attackAnimLungeZ = dz / dist;
            } else {
                attackAnimLungeX = 0;
                attackAnimLungeZ = 1;
            }

            // Arm swing — only for melee mobs (ranged mobs don't swing)
            if (!isRanged) {
                mob.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            }

            // Send attack anim event to client for mob-specific particles
            int attackType = isRanged ? 2
                : (pendingAction instanceof EnemyAction.AttackWithKnockback
                    || pendingAction instanceof EnemyAction.MoveAndAttackWithKnockback) ? 1 : 0;
            GridPos playerGridPos = arena.getPlayerGridPos();
            ServerPlayNetworking.send(player, new com.crackedgames.craftics.network.CombatEventPayload(
                com.crackedgames.craftics.network.CombatEventPayload.EVENT_MOB_ATTACK_ANIM,
                currentEnemy.getMobEntity().getId(), attackType, 0,
                playerGridPos.x(), playerGridPos.z()
            ));
        }

        if (isRanged) {
            // === RANGED ANIMATION: lean back (draw), then snap forward (release) ===
            int totalTicks = RANGED_ANIM_TOTAL_TICKS;

            if (attackAnimTick <= RANGED_ANIM_DRAW_TICKS) {
                // Draw phase — lean AWAY from the player (pull back to aim)
                float progress = (float) attackAnimTick / RANGED_ANIM_DRAW_TICKS;
                float eased = progress * progress; // ease-in for tension build
                double offsetX = -attackAnimLungeX * RANGED_LEAN_DISTANCE * eased;
                double offsetZ = -attackAnimLungeZ * RANGED_LEAN_DISTANCE * eased;
                mob.requestTeleport(
                    attackAnimOriginX + offsetX, attackAnimOriginY, attackAnimOriginZ + offsetZ);
            } else if (attackAnimTick <= totalTicks) {
                // Release phase — snap forward past origin (recoil from shot)
                int releaseTick = attackAnimTick - RANGED_ANIM_DRAW_TICKS;
                float progress = (float) releaseTick / RANGED_ANIM_RELEASE_TICKS;
                float eased = 1.0f - (1.0f - progress) * (1.0f - progress); // ease-out
                // Go from full lean-back to slightly forward, then back to origin
                double leanBack = RANGED_LEAN_DISTANCE * (1.0 - eased);
                double offsetX = -attackAnimLungeX * leanBack;
                double offsetZ = -attackAnimLungeZ * leanBack;
                mob.requestTeleport(
                    attackAnimOriginX + offsetX, attackAnimOriginY, attackAnimOriginZ + offsetZ);

                // Trigger arm swing on first release tick (the "fire" moment)
                if (releaseTick == 1) {
                    mob.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                }
            }

            if (attackAnimTick >= totalTicks) {
                mob.requestTeleport(attackAnimOriginX, attackAnimOriginY, attackAnimOriginZ);
                enemyTurnState = EnemyTurnState.ATTACKING;
                enemyTurnDelay = 0;
            }
        } else if (isSpiderLike(entityType)) {
            // === SPIDER ANIMATION: crouch down then spring forward ===
            int crouchTicks = 4;
            int springTicks = 3;
            int returnTicks = 3;
            int total = crouchTicks + springTicks + returnTicks;

            if (attackAnimTick <= crouchTicks) {
                // Crouch: lower Y position
                float progress = (float) attackAnimTick / crouchTicks;
                float eased = progress * progress;
                mob.requestTeleport(attackAnimOriginX, attackAnimOriginY - 0.2 * eased, attackAnimOriginZ);
            } else if (attackAnimTick <= crouchTicks + springTicks) {
                // Spring forward and up
                int springTick = attackAnimTick - crouchTicks;
                float progress = (float) springTick / springTicks;
                float eased = 1.0f - (1.0f - progress) * (1.0f - progress);
                double offsetX = attackAnimLungeX * 0.7 * eased;
                double offsetZ = attackAnimLungeZ * 0.7 * eased;
                double offsetY = -0.2 + 0.35 * eased; // rise from crouch
                mob.requestTeleport(
                    attackAnimOriginX + offsetX, attackAnimOriginY + offsetY, attackAnimOriginZ + offsetZ);
                if (springTick == 1) mob.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            } else if (attackAnimTick <= total) {
                // Return
                int retTick = attackAnimTick - crouchTicks - springTicks;
                float progress = (float) retTick / returnTicks;
                float eased = progress * progress;
                double offsetX = attackAnimLungeX * 0.7 * (1.0 - eased);
                double offsetZ = attackAnimLungeZ * 0.7 * (1.0 - eased);
                double offsetY = 0.15 * (1.0 - eased);
                mob.requestTeleport(
                    attackAnimOriginX + offsetX, attackAnimOriginY + offsetY, attackAnimOriginZ + offsetZ);
            }

            if (attackAnimTick >= total) {
                mob.requestTeleport(attackAnimOriginX, attackAnimOriginY, attackAnimOriginZ);
                enemyTurnState = EnemyTurnState.ATTACKING;
                enemyTurnDelay = 0;
            }
        } else if (isHeavyHitter(entityType)) {
            // === HEAVY ANIMATION: slow wind-up overhead slam (iron golem, ravager, warden) ===
            int windupTicks = 6;
            int slamTicks = 2;
            int recoverTicks = 4;
            int total = windupTicks + slamTicks + recoverTicks;

            if (attackAnimTick <= windupTicks) {
                // Wind up: rise slightly (raising arms)
                float progress = (float) attackAnimTick / windupTicks;
                float eased = progress * progress;
                mob.requestTeleport(attackAnimOriginX, attackAnimOriginY + 0.25 * eased, attackAnimOriginZ);
            } else if (attackAnimTick <= windupTicks + slamTicks) {
                // Slam down: fast lunge forward + drop
                int slamTick = attackAnimTick - windupTicks;
                float progress = (float) slamTick / slamTicks;
                float eased = 1.0f - (1.0f - progress) * (1.0f - progress);
                double offsetX = attackAnimLungeX * 0.6 * eased;
                double offsetZ = attackAnimLungeZ * 0.6 * eased;
                mob.requestTeleport(
                    attackAnimOriginX + offsetX, attackAnimOriginY + 0.25 * (1.0 - eased), attackAnimOriginZ + offsetZ);
                if (slamTick == 1) mob.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            } else if (attackAnimTick <= total) {
                // Recover: return to origin
                int recTick = attackAnimTick - windupTicks - slamTicks;
                float progress = (float) recTick / recoverTicks;
                float eased = progress * progress;
                double offsetX = attackAnimLungeX * 0.6 * (1.0 - eased);
                double offsetZ = attackAnimLungeZ * 0.6 * (1.0 - eased);
                mob.requestTeleport(
                    attackAnimOriginX + offsetX, attackAnimOriginY, attackAnimOriginZ + offsetZ);
            }

            if (attackAnimTick >= total) {
                mob.requestTeleport(attackAnimOriginX, attackAnimOriginY, attackAnimOriginZ);
                enemyTurnState = EnemyTurnState.ATTACKING;
                enemyTurnDelay = 0;
            }
        } else if (isSwiftPouncer(entityType)) {
            // === SWIFT POUNCE: fast low dash forward (wolf, fox) ===
            int dashTicks = 3;
            int holdTicks = 2;
            int returnTicks = 3;
            int total = dashTicks + holdTicks + returnTicks;

            if (attackAnimTick <= dashTicks) {
                float progress = (float) attackAnimTick / dashTicks;
                float eased = 1.0f - (1.0f - progress) * (1.0f - progress);
                double offsetX = attackAnimLungeX * 0.65 * eased;
                double offsetZ = attackAnimLungeZ * 0.65 * eased;
                double offsetY = -0.1 * eased; // low crouch during dash
                mob.requestTeleport(
                    attackAnimOriginX + offsetX, attackAnimOriginY + offsetY, attackAnimOriginZ + offsetZ);
                if (attackAnimTick == 1) mob.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            } else if (attackAnimTick <= dashTicks + holdTicks) {
                // Hold at strike position
                mob.requestTeleport(
                    attackAnimOriginX + attackAnimLungeX * 0.65, attackAnimOriginY - 0.1, attackAnimOriginZ + attackAnimLungeZ * 0.65);
            } else if (attackAnimTick <= total) {
                int retTick = attackAnimTick - dashTicks - holdTicks;
                float progress = (float) retTick / returnTicks;
                float eased = progress * progress;
                double offsetX = attackAnimLungeX * 0.65 * (1.0 - eased);
                double offsetZ = attackAnimLungeZ * 0.65 * (1.0 - eased);
                double offsetY = -0.1 * (1.0 - eased);
                mob.requestTeleport(
                    attackAnimOriginX + offsetX, attackAnimOriginY + offsetY, attackAnimOriginZ + offsetZ);
            }

            if (attackAnimTick >= total) {
                mob.requestTeleport(attackAnimOriginX, attackAnimOriginY, attackAnimOriginZ);
                enemyTurnState = EnemyTurnState.ATTACKING;
                enemyTurnDelay = 0;
            }
        } else if (isBouncy(entityType)) {
            // === BOUNCE SLAM: hop up then slam down (slime, magma cube) ===
            int riseTicks = 4;
            int slamTicks = 2;
            int recoverTicks = 3;
            int total = riseTicks + slamTicks + recoverTicks;

            if (attackAnimTick <= riseTicks) {
                float progress = (float) attackAnimTick / riseTicks;
                float eased = (float) Math.sin(progress * Math.PI * 0.5); // smooth rise
                mob.requestTeleport(attackAnimOriginX, attackAnimOriginY + 0.6 * eased, attackAnimOriginZ);
            } else if (attackAnimTick <= riseTicks + slamTicks) {
                // Fast slam down toward player
                int slamTick = attackAnimTick - riseTicks;
                float progress = (float) slamTick / slamTicks;
                float eased = progress * progress; // accelerate down
                double offsetX = attackAnimLungeX * 0.5 * eased;
                double offsetZ = attackAnimLungeZ * 0.5 * eased;
                double offsetY = 0.6 * (1.0 - eased);
                mob.requestTeleport(
                    attackAnimOriginX + offsetX, attackAnimOriginY + offsetY, attackAnimOriginZ + offsetZ);
                if (slamTick == 1) mob.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            } else if (attackAnimTick <= total) {
                int recTick = attackAnimTick - riseTicks - slamTicks;
                float progress = (float) recTick / recoverTicks;
                float eased = progress * progress;
                double offsetX = attackAnimLungeX * 0.5 * (1.0 - eased);
                double offsetZ = attackAnimLungeZ * 0.5 * (1.0 - eased);
                mob.requestTeleport(
                    attackAnimOriginX + offsetX, attackAnimOriginY, attackAnimOriginZ + offsetZ);
            }

            if (attackAnimTick >= total) {
                mob.requestTeleport(attackAnimOriginX, attackAnimOriginY, attackAnimOriginZ);
                enemyTurnState = EnemyTurnState.ATTACKING;
                enemyTurnDelay = 0;
            }
        } else if (entityType != null && entityType.equals("minecraft:enderman")) {
            // === ENDERMAN: flicker-dash — brief vanish then appear right next to target ===
            int flickerTicks = 3;
            int strikeTicks = 2;
            int returnTicks = 4;
            int total = flickerTicks + strikeTicks + returnTicks;

            if (attackAnimTick <= flickerTicks) {
                // "Vanish" by sinking slightly (visual flicker effect via particles on client)
                float progress = (float) attackAnimTick / flickerTicks;
                mob.requestTeleport(attackAnimOriginX, attackAnimOriginY - 0.3 * progress, attackAnimOriginZ);
            } else if (attackAnimTick <= flickerTicks + strikeTicks) {
                // Appear close to player and strike
                double offsetX = attackAnimLungeX * 0.7;
                double offsetZ = attackAnimLungeZ * 0.7;
                mob.requestTeleport(
                    attackAnimOriginX + offsetX, attackAnimOriginY, attackAnimOriginZ + offsetZ);
                if (attackAnimTick == flickerTicks + 1) mob.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            } else if (attackAnimTick <= total) {
                // Return smoothly
                int retTick = attackAnimTick - flickerTicks - strikeTicks;
                float progress = (float) retTick / returnTicks;
                float eased = progress * progress;
                double offsetX = attackAnimLungeX * 0.7 * (1.0 - eased);
                double offsetZ = attackAnimLungeZ * 0.7 * (1.0 - eased);
                mob.requestTeleport(
                    attackAnimOriginX + offsetX, attackAnimOriginY, attackAnimOriginZ + offsetZ);
            }

            if (attackAnimTick >= total) {
                mob.requestTeleport(attackAnimOriginX, attackAnimOriginY, attackAnimOriginZ);
                enemyTurnState = EnemyTurnState.ATTACKING;
                enemyTurnDelay = 0;
            }
        } else {
            // === DEFAULT MELEE: lunge forward then return ===
            if (attackAnimTick <= ATTACK_ANIM_LUNGE_TICKS) {
                float progress = (float) attackAnimTick / ATTACK_ANIM_LUNGE_TICKS;
                float eased = 1.0f - (1.0f - progress) * (1.0f - progress);
                double offsetX = attackAnimLungeX * ATTACK_LUNGE_DISTANCE * eased;
                double offsetZ = attackAnimLungeZ * ATTACK_LUNGE_DISTANCE * eased;
                mob.requestTeleport(
                    attackAnimOriginX + offsetX, attackAnimOriginY, attackAnimOriginZ + offsetZ);
            } else if (attackAnimTick <= ATTACK_ANIM_TOTAL_TICKS) {
                int returnTick = attackAnimTick - ATTACK_ANIM_LUNGE_TICKS;
                float progress = (float) returnTick / ATTACK_ANIM_RETURN_TICKS;
                float eased = progress * progress;
                double offsetX = attackAnimLungeX * ATTACK_LUNGE_DISTANCE * (1.0 - eased);
                double offsetZ = attackAnimLungeZ * ATTACK_LUNGE_DISTANCE * (1.0 - eased);
                mob.requestTeleport(
                    attackAnimOriginX + offsetX, attackAnimOriginY, attackAnimOriginZ + offsetZ);
            }

            if (attackAnimTick >= ATTACK_ANIM_TOTAL_TICKS) {
                mob.requestTeleport(attackAnimOriginX, attackAnimOriginY, attackAnimOriginZ);
                enemyTurnState = EnemyTurnState.ATTACKING;
                enemyTurnDelay = 0;
            }
        }
    }

    private static boolean isSpiderLike(String entityTypeId) {
        return entityTypeId != null && (entityTypeId.equals("minecraft:spider")
            || entityTypeId.equals("minecraft:cave_spider"));
    }

    private static boolean isHeavyHitter(String entityTypeId) {
        return entityTypeId != null && (entityTypeId.equals("minecraft:iron_golem")
            || entityTypeId.equals("minecraft:ravager")
            || entityTypeId.equals("minecraft:warden")
            || entityTypeId.equals("minecraft:hoglin")
            || entityTypeId.equals("minecraft:zoglin"));
    }

    private static boolean isSwiftPouncer(String entityTypeId) {
        return entityTypeId != null && (entityTypeId.equals("minecraft:wolf")
            || entityTypeId.equals("minecraft:fox")
            || entityTypeId.equals("minecraft:ocelot")
            || entityTypeId.equals("minecraft:cat"));
    }

    private static boolean isBouncy(String entityTypeId) {
        return entityTypeId != null && (entityTypeId.equals("minecraft:slime")
            || entityTypeId.equals("minecraft:magma_cube"));
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
        // Banner tile effect: +2 DEF if player is within 2 tiles of a banner
        playerDefense += getBannerDefenseBonus(arena.getPlayerGridPos());
        damage = (int)(damage * com.crackedgames.craftics.CrafticsMod.CONFIG.enemyDamageMultiplier());
        int actual = Math.max(1, damage - playerDefense);
        player.setHealth(Math.max(1, player.getHealth() - actual));

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

        // Physical affinity: counterattack chance when attacked unarmed (fist / no weapon)
        if (DamageType.fromWeapon(player.getMainHandStack().getItem()) == DamageType.PHYSICAL
                && currentEnemy.isAlive()) {
            PlayerProgression.PlayerStats pStats = PlayerProgression.get(
                (ServerWorld) player.getEntityWorld()).getStats(player);
            int physPts = pStats.getAffinityPoints(PlayerProgression.Affinity.PHYSICAL);
            double counterChance = physPts * 0.03; // 0% base + 3% per point
            if (counterChance > 0 && Math.random() < counterChance) {
                int counterDmg = currentEnemy.takeDamage(PlayerCombatStats.getAttackPower(player));
                sendMessage("\u00a77\u270A Counter! You strike back for " + counterDmg + " damage!");
                if (!currentEnemy.isAlive()) {
                    achievementTracker.recordCounterKill();
                    killEnemy(currentEnemy);
                }
            }
        }

        sendSync();

        if (getPlayerHp() <= 0) {
            handlePlayerDeathOrGameOver();
            return;
        }

        enemyTurnState = EnemyTurnState.DONE;
        enemyTurnDelay = CrafticsMod.CONFIG.enemyTurnDelay();
    }

    /** Banner defense bonus: +2 DEF if pos is within 2 tiles of any banner tile effect. */
    private int getBannerDefenseBonus(GridPos pos) {
        for (var entry : tileEffects.entrySet()) {
            if ("banner".equals(entry.getValue())) {
                if (pos.manhattanDistance(entry.getKey()) <= 2) return 2;
            }
        }
        return 0;
    }

    /** Scaffold range bonus: +1 range if player is standing on a scaffold tile. */
    private int getScaffoldRangeBonus(GridPos pos) {
        String effect = tileEffects.get(pos);
        return "scaffold".equals(effect) ? 1 : 0;
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

    /**
     * Called when the active player's HP reaches 0. In a party, the dead player
     * becomes a spectator and combat continues with the next alive member.
     * Only triggers a full game over when ALL party members are dead (or solo play).
     */
    private void handlePlayerDeathOrGameOver() {
        // Totem of Undying: save the player from downing/death entirely
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                player.getInventory().getStack(i).decrement(1);
                player.setHealth(player.getMaxHealth() / 2);
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.REGENERATION, 900, 1));
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.ABSORPTION, 100, 1));
                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE, 800, 0));
                // Particles + sound (vanilla totem effects)
                ServerWorld world = (ServerWorld) player.getEntityWorld();
                world.spawnParticles(net.minecraft.particle.ParticleTypes.TOTEM_OF_UNDYING,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    100, 0.6, 1.0, 0.6, 0.5);
                player.getWorld().playSound(null, player.getBlockPos(),
                    net.minecraft.sound.SoundEvents.ITEM_TOTEM_USE,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
                sendMessage("\u00a76\u00a7l\u2726 TOTEM OF UNDYING ACTIVATES! \u2726 \u00a7rResurrected with half health!");
                achievementTracker.recordTotemProc();
                sendSync();
                return; // Player is saved — no downing, no game over
            }
        }

        // Solo play or no party → start death animation before game over
        if (partyPlayers.size() <= 1) {
            startPlayerDeathAnimation();
            return;
        }

        // Mark current player as dead
        deadPartyMembers.add(player.getUuid());
        player.setHealth(1); // keep at clamp threshold (avoids vanilla death screen)

        // --- Downed animation: distinct from death (shorter, different effects) ---
        // Downed sound: player hurt + anvil thud (not wither sounds like death)
        ServerWorld downedWorld = (ServerWorld) player.getEntityWorld();
        player.getWorld().playSound(null, player.getBlockPos(),
            net.minecraft.sound.SoundEvents.ENTITY_PLAYER_HURT,
            net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 0.7f);
        player.getWorld().playSound(null, player.getBlockPos(),
            net.minecraft.sound.SoundEvents.BLOCK_ANVIL_LAND,
            net.minecraft.sound.SoundCategory.PLAYERS, 0.4f, 0.5f);

        // Downed particles: red dust + damage indicator (NOT soul particles like death)
        downedWorld.spawnParticles(net.minecraft.particle.ParticleTypes.DAMAGE_INDICATOR,
            player.getX(), player.getY() + 1.0, player.getZ(),
            8, 0.3, 0.5, 0.3, 0.02);
        downedWorld.spawnParticles(
            new net.minecraft.particle.DustParticleEffect(
                new org.joml.Vector3f(1.0f, 0.2f, 0.2f), 1.5f),
            player.getX(), player.getY() + 0.5, player.getZ(),
            12, 0.4, 0.3, 0.4, 0.01);

        // Send downed event to client (orange vignette, different from red death vignette)
        sendToAllParty(new CombatEventPayload(
            CombatEventPayload.EVENT_PLAYER_DOWNED, 0, 0, 0, 0, 0));

        player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
        sendMessage("§c§l☠ " + player.getName().getString() + " has fallen!");

        // Find next alive party member
        ServerPlayerEntity nextAlive = null;
        for (ServerPlayerEntity member : partyPlayers) {
            if (!deadPartyMembers.contains(member.getUuid())
                    && member != null && !member.isRemoved() && !member.isDisconnected()) {
                nextAlive = member;
                break;
            }
        }

        if (nextAlive == null) {
            // Everyone dead → start death animation before game over
            startPlayerDeathAnimation();
            return;
        }

        // Transfer combat control to next alive member
        sendMessage("§e" + nextAlive.getName().getString() + " takes over the fight!");
        // Teleport new fighter to the current player's position on the grid
        nextAlive.requestTeleport(player.getX(), player.getY(), player.getZ());
        this.player = nextAlive;

        // Recalculate stats for the new active player
        PlayerProgression prog = PlayerProgression.get((ServerWorld) player.getEntityWorld());
        PlayerProgression.PlayerStats pStats = prog.getStats(player);
        this.apRemaining = pStats.getEffective(PlayerProgression.Stat.AP)
            + PlayerCombatStats.getSetApBonus(player);
        this.movePointsRemaining = pStats.getEffective(PlayerProgression.Stat.SPEED)
            + PlayerCombatStats.getSetSpeedBonus(player);
        this.activeTrimScan = TrimEffects.scan(player);

        sendSync();
        refreshHighlights();
    }

    /**
     * Begin the player death animation sequence. Freezes combat, plays dramatic
     * effects (sound, particles, camera event) over PLAYER_DEATH_ANIM_TICKS,
     * then transitions to the real handleGameOver().
     */
    private void startPlayerDeathAnimation() {
        phase = CombatPhase.PLAYER_DYING;
        playerDeathAnimTick = 0;

        // Clamp player health so vanilla death screen doesn't trigger
        player.setHealth(1);

        // Death sound — deep bass hit
        player.getWorld().playSound(null, player.getBlockPos(),
            net.minecraft.sound.SoundEvents.ENTITY_PLAYER_DEATH,
            net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 0.5f);

        // Send death event to client for screen effects (red vignette, camera drop)
        sendToAllParty(new CombatEventPayload(
            CombatEventPayload.EVENT_COMBAT_LOST, 0, PLAYER_DEATH_ANIM_TICKS, 0, 0, 0));

        sendMessage("§c§l☠ You have fallen...");
    }

    private void tickPlayerDying() {
        playerDeathAnimTick++;

        ServerWorld world = (ServerWorld) player.getEntityWorld();

        // Tick 1-15: rising soul particles from player
        if (playerDeathAnimTick <= 15 && playerDeathAnimTick % 3 == 0) {
            world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL,
                player.getX(), player.getY() + 1.0, player.getZ(),
                5, 0.3, 0.5, 0.3, 0.02);
        }

        // Tick 10: secondary death echo sound
        if (playerDeathAnimTick == 10) {
            player.getWorld().playSound(null, player.getBlockPos(),
                net.minecraft.sound.SoundEvents.ENTITY_WITHER_HURT,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.4f, 0.3f);
        }

        // Tick 20-40: smoke/ash particles (body fading)
        if (playerDeathAnimTick >= 20 && playerDeathAnimTick <= 40 && playerDeathAnimTick % 4 == 0) {
            world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                player.getX(), player.getY() + 0.5, player.getZ(),
                8, 0.4, 0.3, 0.4, 0.01);
        }

        // Tick 45: defeat sting
        if (playerDeathAnimTick == 45) {
            player.getWorld().playSound(null, player.getBlockPos(),
                net.minecraft.sound.SoundEvents.ENTITY_WITHER_DEATH,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.5f, 0.5f);
        }

        // Animation complete → proceed to actual game over logic
        if (playerDeathAnimTick >= PLAYER_DEATH_ANIM_TICKS) {
            handleGameOver();
        }
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
        CrafticsSavedData.PlayerData ld = data.getPlayerData(leaderUuid != null ? leaderUuid : player.getUuid());

        // Permadeath mode: lose ALL emeralds and reset biome progress
        if (CrafticsMod.CONFIG.permadeathMode()) {
            int allEmeralds = ld.emeralds;
            if (allEmeralds > 0) ld.spendEmeralds(allEmeralds);
            ld.highestBiomeUnlocked = 1;
            sendMessage("§4§lPERMADEATH: All progress lost!");
        }

        // Death penalty: lose 50% emeralds
        int emeraldLoss = CrafticsMod.CONFIG.permadeathMode() ? 0 : ld.emeralds / 2;
        if (emeraldLoss > 0) {
            ld.spendEmeralds(emeraldLoss);
            sendMessage("§cLost " + emeraldLoss + " emeralds!");
        }

        // Strip items from ALL party members (full team wipe)
        for (ServerPlayerEntity p : getAllParticipants()) {
            if (DeathProtectionComponent.hasRecoveryCompass(p)
                && DeathProtectionComponent.protectCombatInventory(p)) {
                // Particles + sound for recovery compass activation
                ServerWorld compassWorld = (ServerWorld) p.getEntityWorld();
                compassWorld.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL,
                    p.getX(), p.getY() + 1.0, p.getZ(),
                    40, 0.5, 0.8, 0.5, 0.02);
                compassWorld.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                    p.getX(), p.getY() + 1.5, p.getZ(),
                    25, 0.4, 0.6, 0.4, 0.05);
                p.getWorld().playSound(null, p.getBlockPos(),
                    net.minecraft.sound.SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.2f);
                sendMessageTo(p, "\u00a76\u00a7l\u2728 Recovery Compass activated! \u00a7rYour inventory was saved.");
                continue;
            }
            if (ld.isInBiomeRun() && ld.activeBiomeLevelIndex > 0) {
                // Past level 1: lose ALL items (the "Continue" screen warns about this)
                int itemsLost = 0;
                for (int slot = 0; slot < p.getInventory().size(); slot++) {
                    ItemStack stack = p.getInventory().getStack(slot);
                    if (stack.isEmpty()) continue;
                    Item item = stack.getItem();
                    if (item == Items.FEATHER || item instanceof com.crackedgames.craftics.item.GuideBookItem) continue;
                    p.getInventory().setStack(slot, ItemStack.EMPTY);
                    itemsLost++;
                }
                for (int slot = 0; slot < p.getInventory().armor.size(); slot++) {
                    if (!p.getInventory().armor.get(slot).isEmpty()) {
                        p.getInventory().armor.set(slot, ItemStack.EMPTY);
                        itemsLost++;
                    }
                }
                if (!p.getOffHandStack().isEmpty()) {
                    p.setStackInHand(net.minecraft.util.Hand.OFF_HAND, ItemStack.EMPTY);
                    itemsLost++;
                }
                if (itemsLost > 0) {
                    sendMessageTo(p, "§c§lLost all items! (" + itemsLost + " items)");
                }
            } else {
                // First level or no run: only drop main hand weapon
                var mainHand = p.getMainHandStack();
                if (!mainHand.isEmpty() && mainHand.getItem() != Items.FEATHER) {
                    sendMessageTo(p, "§cDropped: " + mainHand.getName().getString());
                    p.getInventory().setStack(p.getInventory().selectedSlot, ItemStack.EMPTY);
                }
            }
        }

        // Heal all participants (including dead spectators returning home)
        for (ServerPlayerEntity p : getAllParticipants()) {
            if (CrafticsMod.CONFIG.healBetweenLevels()) {
                p.setHealth(p.getMaxHealth());
            }
            p.clearStatusEffects();
        }

        // Reset biome run
        ld.endBiomeRun();
        ld.inCombat = false;
        data.markDirty();

        sendMessage("§7Returning to hub... Your biome run has ended.");
        sendMessage("§7Remaining emeralds: " + ld.emeralds);

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
        // Clean up visual projectile entity
        if (enemy.isProjectile()) {
            killVisualProjectileNear(enemy);
        }
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

        // Revive fallen party members with 2 hearts (4 HP)
        if (!deadPartyMembers.isEmpty()) {
            for (ServerPlayerEntity p : getAllParticipants()) {
                if (deadPartyMembers.contains(p.getUuid())) {
                    p.setHealth(4); // 2 hearts
                    p.clearStatusEffects();
                    p.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
                    sendMessageTo(p, "§a§l\u2726 REVIVED! §rYour party won — you're back with 2 hearts!");
                }
            }
            sendMessage("§aFallen allies have been revived!");
            deadPartyMembers.clear();
        }

        // Give mob drops to all party participants (Luck stat adds bonus items per mob)
        List<ServerPlayerEntity> rewardRecipients = getAllParticipants();
        int luckBonusItems = PlayerProgression.get((ServerWorld) player.getEntityWorld())
            .getStats(player).getPoints(PlayerProgression.Stat.LUCK);
        for (CombatEntity enemy : enemies) {
            // Skip drops for creepers that self-exploded (rewards killing them properly)
            if (enemy.isSelfExploded()) continue;
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
            ServerWorld lootWorld = (ServerWorld) player.getEntityWorld();
            com.crackedgames.craftics.level.BiomeTemplate lootBiome = null;
            if (levelDef instanceof com.crackedgames.craftics.level.GeneratedLevelDefinition gld) {
                lootBiome = gld.getBiomeTemplate();
            }
            final com.crackedgames.craftics.level.BiomeTemplate finalLootBiome = lootBiome;
            List<ItemStack> loot = new ArrayList<>();
            for (ItemStack stack : levelDef.rollCompletionLoot()) {
                loot.add(stack.isOf(Items.ENCHANTED_BOOK) ? randomEnchantedBook(lootWorld, stack.getCount(), finalLootBiome) : stack);
            }
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
        java.util.UUID dataOwner = leaderUuid != null ? leaderUuid : player.getUuid();
        CrafticsSavedData.PlayerData ld = data.getPlayerData(dataOwner);
        int biomeIndex = ld.activeBiomeLevelIndex;
        com.crackedgames.craftics.level.BiomeTemplate biomeTemplate = null;
        if (levelDef instanceof com.crackedgames.craftics.level.GeneratedLevelDefinition gld) {
            biomeTemplate = gld.getBiomeTemplate();
        }
        // Fallback: look up biome from active biome run (covers event levels like treasure vault)
        if (biomeTemplate == null && ld.isInBiomeRun()) {
            for (var b : com.crackedgames.craftics.level.BiomeRegistry.getAllBiomes()) {
                if (b.biomeId.equals(ld.activeBiomeId)) { biomeTemplate = b; break; }
            }
        }
        // Use path order for biome progression (not registry order)
        ld.initBranchIfNeeded();
        java.util.List<String> fullPath = com.crackedgames.craftics.level.BiomePath
            .getFullPath(Math.max(0, ld.branchChoice));
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
        data.markDirty();
        sendMessage("§a+ " + emeraldsEarned + " Emeralds");

        // Boss trim template drops (semi-rare: ~35% chance)
        if (isBoss && Math.random() < CrafticsMod.CONFIG.trimDropChance()) {
            // Determine dimension from biome position
            int overworldCount = com.crackedgames.craftics.level.BiomePath
                .getPath(Math.max(0, ld.branchChoice)).size();
            int netherCount = com.crackedgames.craftics.level.BiomePath.getNetherPath().size();
            String dimension;
            if (biomeOrdinal < overworldCount) dimension = "overworld";
            else if (biomeOrdinal < overworldCount + netherCount) dimension = "nether";
            else dimension = "end";

            net.minecraft.item.Item[] trimPool = TrimEffects.getBossDropTrims(dimension);
            if (trimPool.length > 0) {
                // Avoid giving the same trim back-to-back
                java.util.List<net.minecraft.item.Item> candidates = new java.util.ArrayList<>();
                for (net.minecraft.item.Item t : trimPool) {
                    if (t != lastDroppedTrim) candidates.add(t);
                }
                if (candidates.isEmpty()) {
                    for (net.minecraft.item.Item t : trimPool) candidates.add(t);
                }
                net.minecraft.item.Item trimItem = candidates.get(new java.util.Random().nextInt(candidates.size()));
                lastDroppedTrim = trimItem;
                ItemStack trimStack = new ItemStack(trimItem);
                for (ServerPlayerEntity recipient : rewardRecipients) {
                    recipient.getInventory().insertStack(trimStack.copy());
                }
                sendMessage("\u00a7b\u00a7l\u2726 RARE DROP: " + trimStack.getName().getString() + "!");
            }
        }

        // Check combat feat achievements for ALL victories (boss and non-boss)
        if (!isBoss) {
            for (ServerPlayerEntity recipient : rewardRecipients) {
                AchievementManager.checkCombatFeats(recipient, achievementTracker);
                AchievementManager.checkCollections(recipient, data.getPlayerData(recipient.getUuid()).emeralds);
            }
        }

        if (isBoss) {
            // Boss fights end the biome run immediately.
            sendToAllParty(new ExitCombatPayload(true));

            // Boss defeated — biome complete! Unlock next biome, go home
            sendMessage("§6§l*** BIOME COMPLETE! ***");
            int currentBiomeOrder = biomeOrdinal + 1;
            if (ld.highestBiomeUnlocked <= currentBiomeOrder) {
                ld.highestBiomeUnlocked = currentBiomeOrder + 1;
                data.markDirty();
                com.crackedgames.craftics.CrafticsMod.updateWorldIcon(player.getServer(), ld);
                // Unlock biome for all party members
                for (ServerPlayerEntity recipient : rewardRecipients) {
                    CrafticsSavedData.PlayerData pd = data.getPlayerData(recipient.getUuid());
                    if (pd.highestBiomeUnlocked <= currentBiomeOrder) {
                        pd.highestBiomeUnlocked = currentBiomeOrder + 1;
                    }
                }
                // Special messages when unlocking new dimensions
                int overworldBiomeCount = com.crackedgames.craftics.level.BiomePath
                    .getPath(Math.max(0, ld.branchChoice)).size();
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
                    ld.startNewGamePlus();
                    sendMessage("§6§l\u2605 NEW GAME+ " + ld.ngPlusLevel + " UNLOCKED! \u2605");
                    sendMessage("§eAll biomes reset. Enemies are now stronger. Your stats carry over.");
                }
            }
            ld.endBiomeRun();
            ld.inCombat = false;
            data.markDirty();
            world.setTimeOfDay(6000);
            // Teleport all party members home
            for (ServerPlayerEntity p : rewardRecipients) {
                teleportToHub(p);
            }

            // Check achievements before endCombat() clears state
            String achievBiomeId = biomeTemplate != null ? biomeTemplate.biomeId : "";
            String achievArmorSet = PlayerCombatStats.getArmorSet(player);
            CombatAchievementTracker savedTracker = achievementTracker;
            for (ServerPlayerEntity recipient : rewardRecipients) {
                AchievementManager.checkBossVictory(recipient, achievBiomeId, achievArmorSet, savedTracker);
            }
            // Check NG+ achievements
            if (ld.ngPlusLevel > 0) {
                for (ServerPlayerEntity recipient : rewardRecipients) {
                    AchievementManager.checkNewGamePlus(recipient, ld.ngPlusLevel);
                }
            }
            // Check emerald collection achievement
            for (ServerPlayerEntity recipient : rewardRecipients) {
                CrafticsSavedData.PlayerData rpd = data.getPlayerData(recipient.getUuid());
                AchievementManager.checkCollections(recipient, rpd.emeralds);
            }

            // Save party list before endCombat() clears it
            List<ServerPlayerEntity> savedParty = new ArrayList<>(rewardRecipients);
            ServerPlayerEntity savedPlayer = player;
            endCombat();

            // Grant level-up with diminishing returns per boss
            String bossbiomeId = biomeTemplate != null ? biomeTemplate.biomeId : "unknown";
            PlayerProgression progression = PlayerProgression.get(world);
            for (ServerPlayerEntity member : savedParty) {
                PlayerProgression.PlayerStats stats = progression.getStats(member);
                boolean earned = stats.recordBossKillAndCheckLevelUp(bossbiomeId);
                progression.saveStats(member);
                if (earned) {
                    stats.grantLevelUp();
                    progression.saveStats(member);
                    AchievementManager.checkProgression(member);
                } else {
                    int remaining = stats.getKillsUntilNextLevel(bossbiomeId);
                    sendMessageTo(member, "§7Defeat this boss §e" + remaining + "§7 more time" + (remaining != 1 ? "s" : "") + " for a level up.");
                }
                StringBuilder statData = new StringBuilder();
                for (PlayerProgression.Stat s : PlayerProgression.Stat.values()) {
                    if (statData.length() > 0) statData.append(":");
                    statData.append(stats.getPoints(s));
                }
                // Only show level-up screen if this kill earned a level
                if (earned) {
                    ServerPlayNetworking.send(member, new com.crackedgames.craftics.network.LevelUpPayload(
                        stats.level, stats.unspentPoints, statData.toString()
                    ));
                }
                StringBuilder affData = new StringBuilder();
                for (PlayerProgression.Affinity a : PlayerProgression.Affinity.values()) {
                    if (affData.length() > 0) affData.append(":");
                    affData.append(stats.getAffinityPoints(a));
                }
                CrafticsSavedData.PlayerData pd = data.getPlayerData(member.getUuid());
                ServerPlayNetworking.send(member, new com.crackedgames.craftics.network.PlayerStatsSyncPayload(
                    stats.level, stats.unspentPoints, statData.toString(), pd.emeralds, affData.toString()
                ));
            }
        } else {
            // Non-boss: show Go Home / Continue choice
            // Don't advance if this was a trial/ambush — those are bonus fights, not real levels
            if (!lastFightWasTrial) {
                ld.advanceBiomeRun();
                data.markDirty();
            }
            String biomeName = biomeTemplate != null ? biomeTemplate.displayName : "Unknown";
            int displayIndex = ld.activeBiomeLevelIndex;

            // Send choice screen to the party leader (first party member, or solo player)
            ServerPlayerEntity decisionPlayer = partyPlayers.isEmpty() ? player : partyPlayers.get(0);
            ServerPlayNetworking.send(decisionPlayer, new VictoryChoicePayload(
                emeraldsEarned, ld.emeralds, false, biomeName, displayIndex
            ));
            for (ServerPlayerEntity member : getAllParticipants()) {
                if (!member.getUuid().equals(decisionPlayer.getUuid())) {
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
                    // Restore allies and pets before continuing
                    spawnSavedPets();
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
                    // Restore allies and pets before entering trial
                    spawnSavedPets();
                    GridArena trialArena = buildArena(tcWorld, savedTrialDef);
                    transitionPartyToArena(choicePlayer, trialArena, savedTrialDef);
                    lastFightWasTrial = true;
                }
            }
            return;
        }

        // After a trial chamber or ambush victory, go directly to the pending next level (no more events)
        if (lastFightWasTrial && pendingNextLevelDef != null && !goHome) {
            lastFightWasTrial = false;
            ServerWorld world2 = (ServerWorld) choicePlayer.getEntityWorld();
            // Restore allies and pets before continuing
            spawnSavedPets();
            endCombat();
            GridArena nextArena = buildArena(world2, pendingNextLevelDef);
            transitionPartyToArena(choicePlayer, nextArena, pendingNextLevelDef);
            pendingNextLevelDef = null;
            pendingBiome = null;
            return;
        }
        lastFightWasTrial = false;

        // Only accept choice from the party leader (first party member, or current player if solo)
        ServerPlayerEntity leader = partyPlayers.isEmpty() ? player : partyPlayers.get(0);
        if (leader == null || !choicePlayer.getUuid().equals(leader.getUuid())) return;

        // Save references before endCombat() nulls them
        ServerPlayerEntity savedPlayer = player;
        List<ServerPlayerEntity> savedMembers = new ArrayList<>(getAllParticipants());
        ServerWorld world = (ServerWorld) savedPlayer.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(world);
        java.util.UUID dataOwner = leaderUuid != null ? leaderUuid : savedPlayer.getUuid();
        CrafticsSavedData.PlayerData ld = data.getPlayerData(dataOwner);

        // Snapshot biome state BEFORE endCombat can interfere
        String biomeId = ld.activeBiomeId;
        int levelIndex = ld.activeBiomeLevelIndex;
        int branchChoice = ld.branchChoice;
        int ngPlusLevel = ld.ngPlusLevel;

        // Persist tamed pets so they carry over to the next level
        savePets();

        if (goHome) {
            sendMessage("§7Returning to hub...");
            // Spawn saved pets at hub as permanent tamed mobs
            if (!savedPets.isEmpty()) {
                BlockPos hubPos = data.getHubTeleportPos(savedPlayer.getUuid());
                for (PetData pet : savedPets) {
                    var entityType = Registries.ENTITY_TYPE.get(Identifier.of(pet.entityType()));
                    var rawEntity = entityType.create(world, null, hubPos,
                        net.minecraft.entity.SpawnReason.MOB_SUMMONED, false, false);
                    if (rawEntity instanceof net.minecraft.entity.mob.MobEntity mob) {
                        mob.requestTeleport(hubPos.getX() + 1.5, hubPos.getY(), hubPos.getZ() + 0.5);
                        mob.setPersistent();
                        mob.setAiDisabled(false);
                        mob.setSilent(false);
                        world.spawnEntity(mob);
                        sendMessage("\u00a7a" + pet.entityType().substring(pet.entityType().indexOf(':') + 1).replace('_', ' ')
                            + " has been sent to your hub!");
                    }
                }
                savedPets.clear();
            }
            ld.endBiomeRun();
            ld.inCombat = false;
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
            // Broadcast to all BEFORE endCombat clears party state
            for (ServerPlayerEntity m : savedMembers) {
                sendMessageTo(m, "§aOnward!");
            }
            endCombat();

            try {
            // Start next level — use snapshotted biome state (safe from endCombat clobbering)
            com.crackedgames.craftics.level.BiomeTemplate biome = null;
            for (var b : com.crackedgames.craftics.level.BiomeRegistry.getAllBiomes()) {
                if (b.biomeId.equals(biomeId)) { biome = b; break; }
            }
            if (biome != null) {
                int globalLevel = biome.startLevel + levelIndex;
                com.crackedgames.craftics.level.LevelDefinition nextLevelDef =
                    com.crackedgames.craftics.level.LevelRegistry.get(globalLevel, branchChoice);
                if (nextLevelDef != null) {
                    java.util.Random eventRng = new java.util.Random();
                    float eventRoll = eventRng.nextFloat();

                    // Calculate biome position for difficulty scaling (use path ordinal, not registry index)
                    java.util.List<String> fullPath = com.crackedgames.craftics.level.BiomePath
                            .getFullPath(Math.max(0, branchChoice));
                    int biomeOrdinal = fullPath.indexOf(biome.biomeId);
                    if (biomeOrdinal < 0) biomeOrdinal = 0;

                    // No events on the very first continue (level index 1 = just beat level 1)
                    // Never have events before a boss fight
                    // Reduced event chance on early biomes (first 3 biomes)
                    boolean isBossLevel = biome.isBossLevel(globalLevel);
                    boolean skipEvents = levelIndex <= 1 || isBossLevel;
                    if (!skipEvents && biomeOrdinal < 3) {
                        // Early biomes: reduced event chance (configurable)
                        skipEvents = eventRoll > CrafticsMod.CONFIG.earlyBiomeEventChance();
                        if (!skipEvents) eventRoll = eventRng.nextFloat(); // re-roll within event pool
                    }

                    // Check for forced event from /craftics force_event
                    String forced = forcedNextEvent;
                    if (forced != null) {
                        forcedNextEvent = null;
                        skipEvents = false; // override skip
                    }

                    // Helper: broadcast event messages to all party members
                    // Use savedMembers (captured before endCombat) since party state is already cleared
                    List<ServerPlayerEntity> partyMsg = savedMembers;

                    // Build cumulative thresholds from config
                    float cOminous = CrafticsMod.CONFIG.ominousTrialChance();
                    float cTrial = cOminous + CrafticsMod.CONFIG.trialChamberChance();
                    float cAmbush = cTrial + CrafticsMod.CONFIG.ambushChance();
                    float cShrine = cAmbush + CrafticsMod.CONFIG.shrineChance();
                    float cTraveler = cShrine + CrafticsMod.CONFIG.travelerChance();
                    float cVault = cTraveler + CrafticsMod.CONFIG.vaultChance();
                    float cDigSite = cVault + CrafticsMod.CONFIG.digSiteChance();
                    float cEnchanter = cDigSite + 0.06f; // 6% enchanter chance
                    float cTrader = cEnchanter + CrafticsMod.CONFIG.traderSpawnChance();

                    if (skipEvents) {
                        // No event — go straight to next level
                        GridArena nextArena = buildArena(world, nextLevelDef);
                        transitionPartyToArena(savedPlayer, nextArena, nextLevelDef);
                    } else if (forced != null ? forced.equals("ominous_trial") : (eventRoll < cOminous && biomeOrdinal >= 10)) {
                        // Ominous Trial Chamber (late game only)
                        pendingNextLevelDef = nextLevelDef;
                        pendingBiome = biome;
                        trialChamberLevelDef = RandomEvents.generateOminousTrial(biomeOrdinal, ngPlusLevel);
                        trialChamberPending = true;
                        for (ServerPlayerEntity p : partyMsg) {
                            sendMessageTo(p, "\u00a74\u00a7l\u2694 OMINOUS TRIAL CHAMBER! \u2694");
                            sendMessageTo(p, "\u00a7cA dark and powerful trial awaits... with a WARDEN.");
                            sendMessageTo(p, "\u00a7eAccept for legendary loot?");
                        }
                        // Only leader gets the choice screen
                        ServerPlayNetworking.send(savedPlayer, new VictoryChoicePayload(
                            0, ld.emeralds, false, "Ominous Trial", -1
                        ));
                    } else if (forced != null ? forced.equals("trial") : (eventRoll < cTrial)) {
                        // Trial Chamber
                        pendingNextLevelDef = nextLevelDef;
                        pendingBiome = biome;
                        trialChamberLevelDef = TrialChamberEvent.generate(biomeOrdinal, ngPlusLevel);
                        trialChamberPending = true;
                        for (ServerPlayerEntity p : partyMsg) {
                            sendMessageTo(p, "\u00a76\u00a7l\u2694 TRIAL CHAMBER DISCOVERED! \u2694");
                            sendMessageTo(p, "\u00a77A mysterious trial awaits...");
                            sendMessageTo(p, "\u00a7eAccept the challenge for rare loot?");
                        }
                        ServerPlayNetworking.send(savedPlayer, new VictoryChoicePayload(
                            0, ld.emeralds, false, "Trial Chamber", -1
                        ));
                    } else if (forced != null ? forced.equals("ambush") : (eventRoll < cAmbush)) {
                        // Ambush (unavoidable!)
                        pendingNextLevelDef = nextLevelDef;
                        pendingBiome = biome;
                        var ambushDef = RandomEvents.generateAmbush(biomeOrdinal, ngPlusLevel);
                        for (ServerPlayerEntity p : partyMsg) {
                            sendMessageTo(p, "\u00a7c\u00a7l\u26a0 AMBUSH! \u26a0");
                            sendMessageTo(p, "\u00a7cEnemies surround you! No escape!");
                        }
                        lastFightWasTrial = true;
                        GridArena ambushArena = buildArena(world, ambushDef);
                        transitionPartyToArena(savedPlayer, ambushArena, ambushDef);
                    } else if (forced != null ? forced.equals("shrine") : (eventRoll < cShrine)) {
                        // Shrine of Fortune — interactive room
                        pendingNextLevelDef = nextLevelDef;
                        pendingBiome = biome;
                        offerShrine(savedPlayer);
                    } else if (forced != null ? forced.equals("traveler") : (eventRoll < cTraveler)) {
                        // Wounded Traveler — interactive room
                        pendingNextLevelDef = nextLevelDef;
                        pendingBiome = biome;
                        offerTraveler(savedPlayer);
                    } else if (forced != null ? forced.equals("vault") : (eventRoll < cVault)) {
                        // Treasure Vault — interactive room
                        pendingNextLevelDef = nextLevelDef;
                        pendingBiome = biome;
                        offerVault(savedPlayer, biomeOrdinal);
                    } else if (forced != null ? forced.equals("dig_site") : (eventRoll < cDigSite)) {
                        // Dig Site
                        pendingNextLevelDef = nextLevelDef;
                        pendingBiome = biome;
                        offerDigSite(savedPlayer, biome);
                    } else if (forced != null ? forced.equals("enchanter") : (eventRoll < cEnchanter)) {
                        // Enchanter — enhance a weapon or armor piece
                        pendingNextLevelDef = nextLevelDef;
                        pendingBiome = biome;
                        offerEnchanter(savedPlayer);
                    } else if (forced != null ? forced.equals("trader") : (eventRoll < cTrader)) {
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
            } catch (Exception ex) {
                CrafticsMod.LOGGER.error("Failed to start next level, sending player home", ex);
                sendMessageTo(savedPlayer, "§cError loading next level! Returning to hub...");
                ld.endBiomeRun();
                ld.inCombat = false;
                data.markDirty();
                teleportToHub(savedPlayer);
                // Send all party members home on error, not just the leader
                for (ServerPlayerEntity m : savedMembers) {
                    if (!m.getUuid().equals(savedPlayer.getUuid())) {
                        teleportToHub(m);
                    }
                    ServerPlayNetworking.send(m, new ExitCombatPayload(false));
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

    // ---- Interactive event rooms (shrine, traveler, vault) ----
    private boolean eventRoomPending = false;
    private String eventRoomType = null; // "shrine", "traveler", "vault"
    private net.minecraft.entity.passive.VillagerEntity spawnedTraveler;
    private int[] shrineCosts; // [small, medium, large]
    private java.util.List<int[]> travelerFoodSlots; // list of [slotIndex, foodTier]

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

        // Solid support layer under the floor so gravel/sand doesn't fall
        for (int x = 0; x < 9; x++)
            for (int z = 0; z < 9; z++)
                world.setBlockState(new BlockPos(ox + x, oy - 1, oz + z), Blocks.STONE.getDefaultState(), sf);

        // Central 5×5 dig pit (lowered floor of sand/gravel)
        for (int x = 2; x <= 6; x++)
            for (int z = 2; z <= 6; z++)
                world.setBlockState(new BlockPos(ox + x, oy, oz + z), groundBlock.getDefaultState(), sf);

        // Suspicious block in the center
        world.setBlockState(new BlockPos(ox + 4, oy + 1, oz + 4), suspiciousBlock.getDefaultState(), sf);

        // Barrier walls around perimeter (invisible 2-high walls prevent falling off)
        for (int x = -1; x <= 9; x++) {
            for (int y = 1; y <= 2; y++) {
                world.setBlockState(new BlockPos(ox + x, oy + y, oz - 1), Blocks.BARRIER.getDefaultState(), sf);
                world.setBlockState(new BlockPos(ox + x, oy + y, oz + 9), Blocks.BARRIER.getDefaultState(), sf);
            }
        }
        for (int z = -1; z <= 9; z++) {
            for (int y = 1; y <= 2; y++) {
                world.setBlockState(new BlockPos(ox - 1, oy + y, oz + z), Blocks.BARRIER.getDefaultState(), sf);
                world.setBlockState(new BlockPos(ox + 9, oy + y, oz + z), Blocks.BARRIER.getDefaultState(), sf);
            }
        }

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
        CrafticsSavedData.PlayerData traderPd = data.getPlayerData(leaderUuid != null ? leaderUuid : savedPlayer.getUuid());
        traderEmeraldsGiven = traderPd.emeralds;
        if (traderEmeraldsGiven > 0) {
            int remaining = traderEmeraldsGiven;
            while (remaining > 0) {
                int stackSize = Math.min(64, remaining);
                savedPlayer.getInventory().insertStack(new ItemStack(Items.EMERALD, stackSize));
                remaining -= stackSize;
            }
            traderPd.emeralds = 0;
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
        int signalEmeralds = signalData.getPlayerData(leaderUuid != null ? leaderUuid : savedPlayer.getUuid()).emeralds;
        ServerPlayNetworking.send(savedPlayer, new com.crackedgames.craftics.network.TraderOfferPayload(
            activeTraderOffer.type().displayName, activeTraderOffer.type().icon, "", signalEmeralds
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

        // Barrier walls around perimeter (invisible 2-high walls prevent falling off)
        for (int x = -1; x <= 9; x++) {
            for (int y = 1; y <= 2; y++) {
                world.setBlockState(new BlockPos(ox + x, oy + y, oz - 1), Blocks.BARRIER.getDefaultState(), sf);
                world.setBlockState(new BlockPos(ox + x, oy + y, oz + 9), Blocks.BARRIER.getDefaultState(), sf);
            }
        }
        for (int z = -1; z <= 9; z++) {
            for (int y = 1; y <= 2; y++) {
                world.setBlockState(new BlockPos(ox - 1, oy + y, oz + z), Blocks.BARRIER.getDefaultState(), sf);
                world.setBlockState(new BlockPos(ox + 9, oy + y, oz + z), Blocks.BARRIER.getDefaultState(), sf);
            }
        }
    }

    // ── Interactive Event Rooms (Shrine, Traveler, Vault) ──

    private BlockPos getEventRoomOrigin(ServerPlayerEntity refPlayer) {
        if (worldOwnerUuid != null && refPlayer != null) {
            ServerWorld w = (ServerWorld) refPlayer.getEntityWorld();
            CrafticsSavedData d = CrafticsSavedData.get(w);
            BlockPos origin = d.getTraderOrigin(worldOwnerUuid);
            if (origin != null) return origin;
        }
        return new BlockPos(500, 100, 500);
    }

    private void offerShrine(ServerPlayerEntity savedPlayer) {
        List<ServerPlayerEntity> members = getOnlinePartyMembers(savedPlayer);
        for (ServerPlayerEntity p : members) {
            ServerPlayNetworking.send(p, new ExitCombatPayload(false));
        }

        ServerWorld world = (ServerWorld) savedPlayer.getEntityWorld();
        eventRoomPending = true;
        eventRoomType = "shrine";
        shrineCosts = new int[]{2, 5, 10};

        CrafticsSavedData data = CrafticsSavedData.get(world);
        int playerEmeralds = data.getPlayerData(leaderUuid != null ? leaderUuid : savedPlayer.getUuid()).emeralds;

        BlockPos shrineOrigin = getEventRoomOrigin(savedPlayer);
        buildShrineArea(world, shrineOrigin);
        for (ServerPlayerEntity p : members) {
            p.requestTeleport(
                shrineOrigin.getX() + 4.5, shrineOrigin.getY() + 1, shrineOrigin.getZ() + 4.5);
        }

        String eventData = shrineCosts[0] + ":" + shrineCosts[1] + ":" + shrineCosts[2] + ":" + playerEmeralds;
        ServerPlayNetworking.send(savedPlayer, new EventRoomPayload("shrine", eventData));
    }

    private void offerTraveler(ServerPlayerEntity savedPlayer) {
        List<ServerPlayerEntity> members = getOnlinePartyMembers(savedPlayer);
        for (ServerPlayerEntity p : members) {
            ServerPlayNetworking.send(p, new ExitCombatPayload(false));
        }

        ServerWorld world = (ServerWorld) savedPlayer.getEntityWorld();
        eventRoomPending = true;
        eventRoomType = "traveler";

        // Scan player inventory for food items and assign quality tiers
        travelerFoodSlots = new java.util.ArrayList<>();
        StringBuilder foodData = new StringBuilder();
        for (int i = 0; i < savedPlayer.getInventory().size(); i++) {
            ItemStack stack = savedPlayer.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            int tier = getFoodTier(item);
            if (tier > 0) {
                travelerFoodSlots.add(new int[]{i, tier});
                if (foodData.length() > 0) foodData.append("|");
                foodData.append(i).append(":").append(stack.getName().getString()).append(":").append(tier);
            }
        }

        BlockPos travelerOrigin = getEventRoomOrigin(savedPlayer);
        buildTravelerArea(world, travelerOrigin);
        for (ServerPlayerEntity p : members) {
            p.requestTeleport(
                travelerOrigin.getX() + 4.5, travelerOrigin.getY() + 1, travelerOrigin.getZ() + 4.5);
        }

        // Spawn a villager NPC
        spawnedTraveler = (net.minecraft.entity.passive.VillagerEntity)
            net.minecraft.entity.EntityType.VILLAGER.spawn(world, travelerOrigin.up(), net.minecraft.entity.SpawnReason.EVENT);
        if (spawnedTraveler != null) {
            spawnedTraveler.refreshPositionAndAngles(
                travelerOrigin.getX() + 6.5, travelerOrigin.getY() + 1, travelerOrigin.getZ() + 4.5,
                -90f, 0f);
            spawnedTraveler.setAiDisabled(true);
            spawnedTraveler.setInvulnerable(true);
            spawnedTraveler.setBaby(false);
            world.spawnEntity(spawnedTraveler);
        }

        ServerPlayNetworking.send(savedPlayer, new EventRoomPayload("traveler", foodData.toString()));
    }

    // ---- Enchanter Event ----

    /** Tracks which inventory slots are enchantable for the current enchanter event. */
    private java.util.List<int[]> enchanterSlots; // list of [inventorySlot, isArmor(0/1)]

    private void offerEnchanter(ServerPlayerEntity savedPlayer) {
        List<ServerPlayerEntity> members = getOnlinePartyMembers(savedPlayer);
        for (ServerPlayerEntity p : members) {
            ServerPlayNetworking.send(p, new ExitCombatPayload(false));
        }

        ServerWorld world = (ServerWorld) savedPlayer.getEntityWorld();
        eventRoomPending = true;
        eventRoomType = "enchanter";

        // Scan player for weapons and armor that can be enhanced
        enchanterSlots = new java.util.ArrayList<>();
        StringBuilder itemData = new StringBuilder();

        // Scan entire inventory for a weapon (main hand may be empty after feather removal)
        for (int i = 0; i < savedPlayer.getInventory().size(); i++) {
            ItemStack stack = savedPlayer.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (item == Items.FEATHER || item instanceof com.crackedgames.craftics.item.GuideBookItem) continue;
            if (item instanceof net.minecraft.item.SwordItem || item instanceof net.minecraft.item.AxeItem
                    || item instanceof net.minecraft.item.HoeItem || item instanceof net.minecraft.item.ShovelItem
                    || item instanceof net.minecraft.item.MaceItem || item instanceof net.minecraft.item.TridentItem
                    || item instanceof net.minecraft.item.BowItem || item instanceof net.minecraft.item.CrossbowItem
                    || item == Items.STICK || item == Items.BAMBOO
                    || item == Items.BLAZE_ROD || item == Items.BREEZE_ROD
                    || item == Items.TUBE_CORAL || item == Items.BRAIN_CORAL
                    || item == Items.BUBBLE_CORAL || item == Items.FIRE_CORAL
                    || item == Items.HORN_CORAL) {
                enchanterSlots.add(new int[]{i, 0});
                if (itemData.length() > 0) itemData.append("|");
                itemData.append(i).append(":").append(stack.getName().getString()).append(":weapon");
                break; // only offer one weapon
            }
        }
        // Check armor slots
        for (int i = 0; i < savedPlayer.getInventory().armor.size(); i++) {
            ItemStack armor = savedPlayer.getInventory().armor.get(i);
            if (!armor.isEmpty()) {
                int slotId = 100 + i; // use 100+ to distinguish armor slots
                enchanterSlots.add(new int[]{slotId, 1});
                if (itemData.length() > 0) itemData.append("|");
                itemData.append(slotId).append(":").append(armor.getName().getString()).append(":armor");
            }
        }

        BlockPos enchanterOrigin = getEventRoomOrigin(savedPlayer);
        buildShrineArea(world, enchanterOrigin); // reuse shrine room
        for (ServerPlayerEntity p : members) {
            p.requestTeleport(
                enchanterOrigin.getX() + 4.5, enchanterOrigin.getY() + 1, enchanterOrigin.getZ() + 4.5);
        }

        // Spawn an enchanter villager
        spawnedTraveler = (net.minecraft.entity.passive.VillagerEntity)
            net.minecraft.entity.EntityType.VILLAGER.spawn(world, enchanterOrigin.up(), net.minecraft.entity.SpawnReason.EVENT);
        if (spawnedTraveler != null) {
            spawnedTraveler.refreshPositionAndAngles(
                enchanterOrigin.getX() + 4.5, enchanterOrigin.getY() + 1, enchanterOrigin.getZ() + 2.5,
                0f, 0f);
            spawnedTraveler.setAiDisabled(true);
            spawnedTraveler.setInvulnerable(true);
            spawnedTraveler.setBaby(false);
            world.spawnEntity(spawnedTraveler);
        }

        for (ServerPlayerEntity p : members) {
            sendMessageTo(p, "\u00a7d\u00a7l\u2728 A Wandering Enchanter appears!");
            sendMessageTo(p, "\u00a77\"Give me an item and I'll enhance it...\"");
        }

        ServerPlayNetworking.send(savedPlayer, new EventRoomPayload("enchanter", itemData.toString()));
    }

    /** Apply a random enchantment to a weapon or a random trim to armor. */
    private void applyRandomEnhancement(ServerPlayerEntity player, int slotId, boolean isArmor) {
        java.util.Random rng = new java.util.Random();
        ItemStack stack;
        if (slotId >= 100) {
            stack = player.getInventory().armor.get(slotId - 100);
        } else {
            stack = player.getInventory().getStack(slotId);
        }
        if (stack.isEmpty()) return;

        ServerWorld world = (ServerWorld) player.getEntityWorld();

        if (isArmor) {
            // Add a random trim (pattern + material)
            String[] patterns = {"sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide",
                "snout", "rib", "spire", "wayfinder", "shaper", "silence", "raiser", "host", "flow", "bolt"};
            String[] materials = {"iron", "copper", "gold", "lapis", "emerald", "diamond", "netherite",
                "redstone", "amethyst", "quartz"};
            String pattern = patterns[rng.nextInt(patterns.length)];
            String material = materials[rng.nextInt(materials.length)];

            // Apply trim via ArmorTrim component
            var patternRegistry = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.TRIM_PATTERN);
            var patternEntry = patternRegistry.streamEntries()
                .filter(e -> e.getKey().isPresent() && e.getKey().get().getValue().getPath().equals(pattern))
                .findFirst().orElse(null);
            var materialRegistry = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.TRIM_MATERIAL);
            var materialEntry = materialRegistry.streamEntries()
                .filter(e -> e.getKey().isPresent() && e.getKey().get().getValue().getPath().equals(material))
                .findFirst().orElse(null);

            if (patternEntry != null && materialEntry != null) {
                var trim = new net.minecraft.item.trim.ArmorTrim(materialEntry, patternEntry);
                stack.set(DataComponentTypes.TRIM, trim);
                sendMessage("\u00a7d\u2728 " + stack.getName().getString() + " received a §e" + pattern + " §dtrim with §e" + material + " §dmaterial!");
            } else {
                // Fallback: add enchant glint
                stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                sendMessage("\u00a7d\u2728 " + stack.getName().getString() + " shimmers with new power!");
            }
        } else {
            // Add a random weapon enchantment
            String[] enchantKeys = {"sharpness", "smite", "bane_of_arthropods", "fire_aspect",
                "knockback", "looting", "sweeping_edge", "unbreaking"};
            String chosenKey = enchantKeys[rng.nextInt(enchantKeys.length)];
            int level = 1 + rng.nextInt(3); // level 1-3

            var enchantRegistry = world.getRegistryManager()
                .get(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
            var enchantEntry = enchantRegistry.streamEntries()
                .filter(e -> e.getKey().isPresent() && e.getKey().get().getValue().getPath().equals(chosenKey))
                .findFirst().orElse(null);

            if (enchantEntry != null) {
                ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(
                    stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT));
                builder.add(enchantEntry, level);
                stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());
                sendMessage("\u00a7d\u2728 " + stack.getName().getString() + " received §e" + chosenKey.replace('_', ' ') + " " + level + "§d!");
            } else {
                stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                sendMessage("\u00a7d\u2728 " + stack.getName().getString() + " shimmers with new power!");
            }
        }
    }

    private void offerVault(ServerPlayerEntity savedPlayer, int biomeOrdinal) {
        List<ServerPlayerEntity> members = getOnlinePartyMembers(savedPlayer);
        for (ServerPlayerEntity p : members) {
            ServerPlayNetworking.send(p, new ExitCombatPayload(false));
        }

        ServerWorld world = (ServerWorld) savedPlayer.getEntityWorld();
        eventRoomPending = true;
        eventRoomType = "vault";

        BlockPos vaultOrigin = getEventRoomOrigin(savedPlayer);
        buildVaultArea(world, vaultOrigin);
        for (ServerPlayerEntity p : members) {
            p.requestTeleport(
                vaultOrigin.getX() + 4.5, vaultOrigin.getY() + 1, vaultOrigin.getZ() + 4.5);
        }

        ServerPlayNetworking.send(savedPlayer, new EventRoomPayload("vault", String.valueOf(biomeOrdinal)));
    }

    /** Determine food quality tier for the Wounded Traveler event. */
    private static int getFoodTier(Item item) {
        if (item == Items.ENCHANTED_GOLDEN_APPLE) return 4;
        if (item == Items.GOLDEN_APPLE || item == Items.GOLDEN_CARROT || item == Items.CAKE
            || item == Items.HONEY_BOTTLE) return 3;
        if (item == Items.COOKED_BEEF || item == Items.COOKED_PORKCHOP || item == Items.COOKED_CHICKEN
            || item == Items.COOKED_MUTTON || item == Items.COOKED_SALMON || item == Items.COOKED_COD
            || item == Items.MUSHROOM_STEW || item == Items.RABBIT_STEW || item == Items.BEETROOT_SOUP
            || item == Items.PUMPKIN_PIE) return 2;
        if (item == Items.BREAD || item == Items.BAKED_POTATO || item == Items.CARROT
            || item == Items.BEETROOT || item == Items.APPLE || item == Items.COOKIE
            || item == Items.DRIED_KELP || item == Items.SWEET_BERRIES || item == Items.MELON_SLICE
            || item == Items.POTATO || item == Items.COD || item == Items.SALMON) return 1;
        return 0;
    }

    /**
     * Handle a player's choice from an event room screen.
     * choiceIndex: 0+ = specific choice, -1 = skip/walk away
     */
    public void handleEventChoice(ServerPlayerEntity choicePlayer, int choiceIndex) {
        if (!eventRoomPending) return;
        eventRoomPending = false;
        String type = eventRoomType;
        eventRoomType = null;

        ServerWorld world = (ServerWorld) choicePlayer.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData ld = data.getPlayerData(leaderUuid != null ? leaderUuid : choicePlayer.getUuid());
        List<ServerPlayerEntity> partyMsg = getOnlinePartyMembers(choicePlayer);

        if ("shrine".equals(type)) {
            if (choiceIndex >= 0 && choiceIndex < 3) {
                int cost = shrineCosts[choiceIndex];
                if (ld.emeralds >= cost) {
                    ld.spendEmeralds(cost);
                    java.util.Random rng = new java.util.Random();
                    // Better offering = better odds
                    int roll = rng.nextInt(100);
                    int threshold = choiceIndex == 0 ? 40 : (choiceIndex == 1 ? 25 : 10);
                    ItemStack reward;
                    String desc;

                    if (roll < threshold) {
                        // Common
                        reward = switch (rng.nextInt(4)) {
                            case 0 -> new ItemStack(Items.GOLDEN_APPLE, 2);
                            case 1 -> new ItemStack(Items.ENDER_PEARL, 3);
                            case 2 -> new ItemStack(Items.ARROW, 32);
                            default -> new ItemStack(Items.COOKED_BEEF, 8);
                        };
                        desc = "§aThe shrine rewards your faith!";
                    } else if (roll < threshold + 35) {
                        // Good
                        reward = switch (rng.nextInt(4)) {
                            case 0 -> new ItemStack(Items.DIAMOND, 3);
                            case 1 -> new ItemStack(Items.SHIELD, 1);
                            case 2 -> new ItemStack(Items.IRON_SWORD, 1);
                            default -> new ItemStack(Items.TOTEM_OF_UNDYING, 1);
                        };
                        desc = "§bThe shrine glows brightly!";
                    } else if (roll < threshold + 55) {
                        // Great
                        reward = switch (rng.nextInt(3)) {
                            case 0 -> new ItemStack(Items.DIAMOND_SWORD, 1);
                            case 1 -> new ItemStack(Items.DIAMOND_CHESTPLATE, 1);
                            default -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1);
                        };
                        desc = "§d§lThe shrine erupts with light!";
                    } else {
                        // Jackpot
                        ld.addEmeralds(cost * 3);
                        data.markDirty();
                        reward = new ItemStack(Items.EMERALD, cost * 3);
                        desc = "§6§l✦ JACKPOT! ✦ §r§6Triple emeralds returned!";
                    }

                    choicePlayer.getInventory().insertStack(reward);
                    String rewardName = reward.getName().getString();
                    for (ServerPlayerEntity p : partyMsg) {
                        sendMessageTo(p, "§e§lShrine of Fortune! §r§7(" + cost + " emeralds offered)");
                        sendMessageTo(p, desc);
                        sendMessageTo(p, "§7Received: §f" + rewardName);
                    }
                    // Particles
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANT,
                        getEventRoomOrigin(choicePlayer).getX() + 4.5, getEventRoomOrigin(choicePlayer).getY() + 2.5, getEventRoomOrigin(choicePlayer).getZ() + 4.5,
                        30, 0.5, 1.0, 0.5, 0.1);
                } else {
                    for (ServerPlayerEntity p : partyMsg) {
                        sendMessageTo(p, "§cNot enough emeralds!");
                    }
                }
            } else {
                for (ServerPlayerEntity p : partyMsg) {
                    sendMessageTo(p, "§7You walk away from the shrine...");
                }
            }
        } else if ("traveler".equals(type)) {
            // Despawn the villager
            if (spawnedTraveler != null && spawnedTraveler.isAlive()) {
                spawnedTraveler.discard();
                spawnedTraveler = null;
            }

            if (choiceIndex >= 0 && travelerFoodSlots != null) {
                // Find the food slot matching the choice index
                int[] chosen = null;
                for (int[] slot : travelerFoodSlots) {
                    if (slot[0] == choiceIndex) { chosen = slot; break; }
                }
                if (chosen != null) {
                    int slotIdx = chosen[0];
                    int tier = chosen[1];
                    String foodName = choicePlayer.getInventory().getStack(slotIdx).getName().getString();
                    choicePlayer.getInventory().getStack(slotIdx).decrement(1);

                    // Reward based on food tier
                    java.util.Random rng = new java.util.Random();
                    ItemStack reward;
                    int roll = rng.nextInt(100);

                    // Tier shifts the reward brackets
                    int basicCap = switch (tier) { case 1 -> 40; case 2 -> 20; case 3 -> 5; default -> 0; };
                    int goodCap = basicCap + switch (tier) { case 1 -> 30; case 2 -> 35; case 3 -> 20; default -> 0; };
                    int greatCap = goodCap + switch (tier) { case 1 -> 20; case 2 -> 30; case 3 -> 40; default -> 30; };

                    if (roll < basicCap) {
                        reward = switch (rng.nextInt(4)) {
                            case 0 -> new ItemStack(Items.EMERALD, 3);
                            case 1 -> new ItemStack(Items.IRON_INGOT, 5);
                            case 2 -> new ItemStack(Items.BONE, 4);
                            default -> new ItemStack(Items.ARROW, 16);
                        };
                    } else if (roll < goodCap) {
                        reward = switch (rng.nextInt(3)) {
                            case 0 -> new ItemStack(Items.DIAMOND, 1);
                            case 1 -> new ItemStack(Items.GOLDEN_APPLE, 1);
                            default -> new ItemStack(Items.SPYGLASS, 1);
                        };
                    } else if (roll < greatCap) {
                        reward = switch (rng.nextInt(3)) {
                            case 0 -> new ItemStack(Items.SADDLE, 1);
                            case 1 -> new ItemStack(Items.ENCHANTED_BOOK, 1);
                            default -> new ItemStack(Items.NAME_TAG, 1);
                        };
                    } else {
                        reward = switch (rng.nextInt(2)) {
                            case 0 -> new ItemStack(Items.DIAMOND_SWORD, 1);
                            default -> new ItemStack(Items.TOTEM_OF_UNDYING, 1);
                        };
                    }

                    choicePlayer.getInventory().insertStack(reward);
                    String rewardName = reward.getName().getString();
                    for (ServerPlayerEntity p : partyMsg) {
                        sendMessageTo(p, "§e§lWounded Traveler! §r§7You give " + foodName + ".");
                        sendMessageTo(p, "§a\"Thank you, brave warrior!\"");
                        sendMessageTo(p, "§7Received: §f" + rewardName);
                    }
                    // Particles
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.HEART,
                        getEventRoomOrigin(choicePlayer).getX() + 6.5, getEventRoomOrigin(choicePlayer).getY() + 2.5, getEventRoomOrigin(choicePlayer).getZ() + 4.5,
                        10, 0.3, 0.5, 0.3, 0.02);
                }
            } else {
                for (ServerPlayerEntity p : partyMsg) {
                    sendMessageTo(p, "§7You leave the traveler behind...");
                }
            }
            travelerFoodSlots = null;
        } else if ("vault".equals(type)) {
            if (choiceIndex >= 0) {
                // Give loot from treasure vault table
                int biomeOrdinal = 0;
                try { biomeOrdinal = Integer.parseInt(eventRoomType != null ? "0" : "0"); } catch (Exception ignored) {}
                // Use the pending biome ordinal
                java.util.List<String> fullPath = com.crackedgames.craftics.level.BiomePath
                    .getFullPath(Math.max(0, CrafticsSavedData.get(world).getPlayerData(leaderUuid != null ? leaderUuid : choicePlayer.getUuid()).branchChoice));
                if (pendingBiome != null) {
                    biomeOrdinal = fullPath.indexOf(pendingBiome.biomeId);
                    if (biomeOrdinal < 0) biomeOrdinal = 0;
                }

                java.util.Random rng = new java.util.Random();
                int itemCount = 2 + rng.nextInt(3); // 2-4 items
                for (int i = 0; i < itemCount; i++) {
                    ItemStack loot = getVaultLootItem(biomeOrdinal, rng);
                    choicePlayer.getInventory().insertStack(loot);
                }
                for (ServerPlayerEntity p : partyMsg) {
                    sendMessageTo(p, "§6§l✦ TREASURE VAULT OPENED! ✦");
                    sendMessageTo(p, "§eYou claim " + itemCount + " treasures!");
                }
                // Particles
                world.spawnParticles(net.minecraft.particle.ParticleTypes.TOTEM_OF_UNDYING,
                    getEventRoomOrigin(choicePlayer).getX() + 4.5, getEventRoomOrigin(choicePlayer).getY() + 2, getEventRoomOrigin(choicePlayer).getZ() + 4.5,
                    50, 1.0, 1.0, 1.0, 0.3);
            } else {
                for (ServerPlayerEntity p : partyMsg) {
                    sendMessageTo(p, "§7You leave the vault untouched...");
                }
            }
        } else if ("enchanter".equals(type)) {
            // Despawn the enchanter villager
            if (spawnedTraveler != null && spawnedTraveler.isAlive()) {
                spawnedTraveler.discard();
                spawnedTraveler = null;
            }

            if (choiceIndex >= 0 && enchanterSlots != null) {
                // Find the slot matching the choice
                int[] chosen = null;
                for (int[] slot : enchanterSlots) {
                    if (slot[0] == choiceIndex) { chosen = slot; break; }
                }
                if (chosen != null) {
                    applyRandomEnhancement(choicePlayer, chosen[0], chosen[1] == 1);
                    // Particles
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANT,
                        getEventRoomOrigin(choicePlayer).getX() + 4.5, getEventRoomOrigin(choicePlayer).getY() + 2.5, getEventRoomOrigin(choicePlayer).getZ() + 4.5,
                        40, 0.5, 1.0, 0.5, 0.1);
                } else {
                    for (ServerPlayerEntity p : partyMsg) {
                        sendMessageTo(p, "\u00a77The enchanter couldn't find that item...");
                    }
                }
            } else {
                for (ServerPlayerEntity p : partyMsg) {
                    sendMessageTo(p, "\u00a77You decline the enchanter's offer...");
                }
            }
            enchanterSlots = null;
        }

        // Transition to next level after a short delay
        world.getServer().execute(() -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            if (pendingNextLevelDef != null && pendingBiome != null) {
                GridArena nextArena = buildArena(world, pendingNextLevelDef);
                transitionPartyToArena(choicePlayer, nextArena, pendingNextLevelDef);
                pendingNextLevelDef = null;
                pendingBiome = null;
            }
        });
    }

    /** Generate a random treasure vault loot item scaled to biome progression. */
    private ItemStack getVaultLootItem(int biomeOrdinal, java.util.Random rng) {
        if (biomeOrdinal >= 7) {
            return switch (rng.nextInt(8)) {
                case 0 -> new ItemStack(Items.DIAMOND, 2 + rng.nextInt(3));
                case 1 -> new ItemStack(Items.NETHERITE_SCRAP, 1);
                case 2 -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1);
                case 3 -> new ItemStack(Items.DIAMOND_SWORD, 1);
                case 4 -> new ItemStack(Items.DIAMOND_CHESTPLATE, 1);
                case 5 -> new ItemStack(Items.TOTEM_OF_UNDYING, 1);
                case 6 -> new ItemStack(Items.GOLDEN_APPLE, 2);
                default -> new ItemStack(Items.EMERALD, 5 + rng.nextInt(6));
            };
        } else if (biomeOrdinal >= 3) {
            return switch (rng.nextInt(8)) {
                case 0 -> new ItemStack(Items.DIAMOND, 1 + rng.nextInt(2));
                case 1 -> new ItemStack(Items.IRON_SWORD, 1);
                case 2 -> new ItemStack(Items.IRON_CHESTPLATE, 1);
                case 3 -> new ItemStack(Items.GOLDEN_APPLE, 1);
                case 4 -> new ItemStack(Items.ENDER_PEARL, 2);
                case 5 -> new ItemStack(Items.SHIELD, 1);
                case 6 -> new ItemStack(Items.GOLDEN_CARROT, 3);
                default -> new ItemStack(Items.EMERALD, 3 + rng.nextInt(4));
            };
        } else {
            return switch (rng.nextInt(8)) {
                case 0 -> new ItemStack(Items.IRON_INGOT, 3 + rng.nextInt(3));
                case 1 -> new ItemStack(Items.STONE_SWORD, 1);
                case 2 -> new ItemStack(Items.LEATHER_CHESTPLATE, 1);
                case 3 -> new ItemStack(Items.BREAD, 5);
                case 4 -> new ItemStack(Items.ARROW, 16 + rng.nextInt(16));
                case 5 -> new ItemStack(Items.COAL, 8);
                case 6 -> new ItemStack(Items.GOLDEN_CARROT, 2);
                default -> new ItemStack(Items.EMERALD, 2 + rng.nextInt(3));
            };
        }
    }

    private ItemStack randomEnchantedBook(ServerWorld world, int count, com.crackedgames.craftics.level.BiomeTemplate biome) {
        var registry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        java.util.Random rng = new java.util.Random();

        net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment> entry = null;

        // Use biome-specific enchantment pool if defined
        if (biome != null && biome.enchantmentLootIds.length > 0) {
            int totalWeight = 0;
            for (int w : biome.enchantmentLootWeights) totalWeight += w;
            int roll = rng.nextInt(totalWeight);
            int cumulative = 0;
            for (int i = 0; i < biome.enchantmentLootIds.length; i++) {
                cumulative += biome.enchantmentLootWeights[i];
                if (roll < cumulative) {
                    entry = registry.getEntry(net.minecraft.util.Identifier.of(biome.enchantmentLootIds[i])).orElse(null);
                    break;
                }
            }
        }

        // Fall back to a random enchantment from the full registry
        if (entry == null) {
            var entries = registry.streamEntries().toList();
            if (entries.isEmpty()) return new ItemStack(Items.ENCHANTED_BOOK, count);
            entry = entries.get(rng.nextInt(entries.size()));
        }

        int level = 1 + rng.nextInt(entry.value().getMaxLevel());
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK, count);
        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        builder.add(entry, level);
        book.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());
        return book;
    }

    // ── Event Room Builders ──

    private void buildShrineArea(ServerWorld world, BlockPos origin) {
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        net.minecraft.block.BlockState air = Blocks.AIR.getDefaultState();
        int sf = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE;

        // Clear 9x9, 6 high
        for (int x = 0; x < 9; x++)
            for (int z = 0; z < 9; z++)
                for (int y = 0; y <= 6; y++)
                    world.setBlockState(new BlockPos(ox + x, oy + y, oz + z), air, sf);

        // Floor: polished blackstone
        for (int x = 0; x < 9; x++)
            for (int z = 0; z < 9; z++)
                world.setBlockState(new BlockPos(ox + x, oy, oz + z), Blocks.POLISHED_BLACKSTONE.getDefaultState(), sf);

        // Gold accent cross in center
        for (int i = 2; i <= 6; i++) {
            world.setBlockState(new BlockPos(ox + 4, oy, oz + i), Blocks.GOLD_BLOCK.getDefaultState(), sf);
            world.setBlockState(new BlockPos(ox + i, oy, oz + 4), Blocks.GOLD_BLOCK.getDefaultState(), sf);
        }

        // Enchanting table as shrine centerpiece
        world.setBlockState(new BlockPos(ox + 4, oy + 1, oz + 4), Blocks.ENCHANTING_TABLE.getDefaultState(), sf);

        // Soul torches around shrine
        world.setBlockState(new BlockPos(ox + 3, oy + 1, oz + 3), Blocks.SOUL_TORCH.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 5, oy + 1, oz + 3), Blocks.SOUL_TORCH.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 3, oy + 1, oz + 5), Blocks.SOUL_TORCH.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 5, oy + 1, oz + 5), Blocks.SOUL_TORCH.getDefaultState(), sf);

        // Candles on corners
        world.setBlockState(new BlockPos(ox + 1, oy + 1, oz + 1), Blocks.CANDLE.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 7, oy + 1, oz + 1), Blocks.CANDLE.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 1, oy + 1, oz + 7), Blocks.CANDLE.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 7, oy + 1, oz + 7), Blocks.CANDLE.getDefaultState(), sf);

        // Barrier walls
        for (int x = -1; x <= 9; x++) {
            for (int y = 1; y <= 2; y++) {
                world.setBlockState(new BlockPos(ox + x, oy + y, oz - 1), Blocks.BARRIER.getDefaultState(), sf);
                world.setBlockState(new BlockPos(ox + x, oy + y, oz + 9), Blocks.BARRIER.getDefaultState(), sf);
            }
        }
        for (int z = -1; z <= 9; z++) {
            for (int y = 1; y <= 2; y++) {
                world.setBlockState(new BlockPos(ox - 1, oy + y, oz + z), Blocks.BARRIER.getDefaultState(), sf);
                world.setBlockState(new BlockPos(ox + 9, oy + y, oz + z), Blocks.BARRIER.getDefaultState(), sf);
            }
        }
    }

    private void buildTravelerArea(ServerWorld world, BlockPos origin) {
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        net.minecraft.block.BlockState air = Blocks.AIR.getDefaultState();
        int sf = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE;

        // Clear 9x9, 6 high
        for (int x = 0; x < 9; x++)
            for (int z = 0; z < 9; z++)
                for (int y = 0; y <= 6; y++)
                    world.setBlockState(new BlockPos(ox + x, oy + y, oz + z), air, sf);

        // Floor: dirt/grass
        for (int x = 0; x < 9; x++)
            for (int z = 0; z < 9; z++)
                world.setBlockState(new BlockPos(ox + x, oy, oz + z), Blocks.GRASS_BLOCK.getDefaultState(), sf);

        // Path to the traveler
        for (int z = 0; z <= 6; z++)
            world.setBlockState(new BlockPos(ox + 4, oy, oz + z), Blocks.DIRT_PATH.getDefaultState(), sf);

        // Campfire in center
        world.setBlockState(new BlockPos(ox + 4, oy + 1, oz + 4), Blocks.CAMPFIRE.getDefaultState(), sf);

        // Hay bale bed near traveler
        world.setBlockState(new BlockPos(ox + 6, oy + 1, oz + 5), Blocks.HAY_BLOCK.getDefaultState(), sf);

        // Lanterns
        world.setBlockState(new BlockPos(ox + 2, oy + 1, oz + 3), Blocks.LANTERN.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 6, oy + 1, oz + 3), Blocks.LANTERN.getDefaultState(), sf);

        // Oak logs as seating
        world.setBlockState(new BlockPos(ox + 3, oy + 1, oz + 3), Blocks.OAK_LOG.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 5, oy + 1, oz + 5), Blocks.OAK_LOG.getDefaultState(), sf);

        // Barrier walls
        for (int x = -1; x <= 9; x++) {
            for (int y = 1; y <= 2; y++) {
                world.setBlockState(new BlockPos(ox + x, oy + y, oz - 1), Blocks.BARRIER.getDefaultState(), sf);
                world.setBlockState(new BlockPos(ox + x, oy + y, oz + 9), Blocks.BARRIER.getDefaultState(), sf);
            }
        }
        for (int z = -1; z <= 9; z++) {
            for (int y = 1; y <= 2; y++) {
                world.setBlockState(new BlockPos(ox - 1, oy + y, oz + z), Blocks.BARRIER.getDefaultState(), sf);
                world.setBlockState(new BlockPos(ox + 9, oy + y, oz + z), Blocks.BARRIER.getDefaultState(), sf);
            }
        }
    }

    private void buildVaultArea(ServerWorld world, BlockPos origin) {
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        net.minecraft.block.BlockState air = Blocks.AIR.getDefaultState();
        int sf = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE;

        // Clear 9x9, 6 high
        for (int x = 0; x < 9; x++)
            for (int z = 0; z < 9; z++)
                for (int y = 0; y <= 6; y++)
                    world.setBlockState(new BlockPos(ox + x, oy + y, oz + z), air, sf);

        // Floor: deepslate bricks
        for (int x = 0; x < 9; x++)
            for (int z = 0; z < 9; z++)
                world.setBlockState(new BlockPos(ox + x, oy, oz + z), Blocks.DEEPSLATE_BRICKS.getDefaultState(), sf);

        // Gold center (3x3)
        for (int x = 3; x <= 5; x++)
            for (int z = 3; z <= 5; z++)
                world.setBlockState(new BlockPos(ox + x, oy, oz + z), Blocks.GOLD_BLOCK.getDefaultState(), sf);

        // Chests along walls
        world.setBlockState(new BlockPos(ox + 1, oy + 1, oz + 1), Blocks.CHEST.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 7, oy + 1, oz + 1), Blocks.CHEST.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 1, oy + 1, oz + 7), Blocks.CHEST.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 7, oy + 1, oz + 7), Blocks.CHEST.getDefaultState(), sf);

        // Vault block centerpiece (if available, otherwise use lodestone)
        world.setBlockState(new BlockPos(ox + 4, oy + 1, oz + 4), Blocks.LODESTONE.getDefaultState(), sf);

        // Lanterns
        world.setBlockState(new BlockPos(ox + 2, oy + 1, oz + 2), Blocks.LANTERN.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 6, oy + 1, oz + 2), Blocks.LANTERN.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 2, oy + 1, oz + 6), Blocks.LANTERN.getDefaultState(), sf);
        world.setBlockState(new BlockPos(ox + 6, oy + 1, oz + 6), Blocks.LANTERN.getDefaultState(), sf);

        // Deepslate pillar walls (partial, for vault feel)
        for (int y = 1; y <= 3; y++) {
            world.setBlockState(new BlockPos(ox, oy + y, oz), Blocks.DEEPSLATE_BRICK_WALL.getDefaultState(), sf);
            world.setBlockState(new BlockPos(ox + 8, oy + y, oz), Blocks.DEEPSLATE_BRICK_WALL.getDefaultState(), sf);
            world.setBlockState(new BlockPos(ox, oy + y, oz + 8), Blocks.DEEPSLATE_BRICK_WALL.getDefaultState(), sf);
            world.setBlockState(new BlockPos(ox + 8, oy + y, oz + 8), Blocks.DEEPSLATE_BRICK_WALL.getDefaultState(), sf);
        }

        // Barrier walls
        for (int x = -1; x <= 9; x++) {
            for (int y = 1; y <= 2; y++) {
                world.setBlockState(new BlockPos(ox + x, oy + y, oz - 1), Blocks.BARRIER.getDefaultState(), sf);
                world.setBlockState(new BlockPos(ox + x, oy + y, oz + 9), Blocks.BARRIER.getDefaultState(), sf);
            }
        }
        for (int z = -1; z <= 9; z++) {
            for (int y = 1; y <= 2; y++) {
                world.setBlockState(new BlockPos(ox - 1, oy + y, oz + z), Blocks.BARRIER.getDefaultState(), sf);
                world.setBlockState(new BlockPos(ox + 9, oy + y, oz + z), Blocks.BARRIER.getDefaultState(), sf);
            }
        }
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
        CrafticsSavedData.PlayerData traderPd = data.getPlayerData(leaderUuid != null ? leaderUuid : player.getUuid());
        int collectedEmeralds = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == Items.EMERALD) {
                collectedEmeralds += stack.getCount();
                player.getInventory().removeStack(i);
            }
        }
        traderPd.emeralds = collectedEmeralds;
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
        cleanupNoCollisionTeam();

        // Clean up any undetonated TNT blocks
        if (!pendingTnts.isEmpty() && player != null) {
            ServerWorld tntWorld = (ServerWorld) player.getEntityWorld();
            for (PendingTnt tnt : pendingTnts) {
                GridTile floorTile = arena.getTile(tnt.tile());
                if (floorTile != null) {
                    tntWorld.setBlockState(tnt.blockPos(), floorTile.getBlockType().getDefaultState());
                } else {
                    tntWorld.setBlockState(tnt.blockPos(), Blocks.AIR.getDefaultState());
                }
            }
            pendingTnts.clear();
        }

        // Return dropped trident to player before ending
        returnDroppedTrident();

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

        // Discard any lingering potion clouds and visual projectiles tagged as arena entities
        if (player != null) {
            ServerWorld cleanupWorld = (ServerWorld) player.getEntityWorld();
            for (net.minecraft.entity.Entity entity : cleanupWorld.iterateEntities()) {
                if (entity.getCommandTags().contains("craftics_arena")
                        && (entity instanceof net.minecraft.entity.AreaEffectCloudEntity
                            || entity.getCommandTags().contains("craftics_visual_projectile"))) {
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

        // Clear the inCombat flag for all participants so they don't get thrown
        // back into combat on rejoin (the flag is only useful for disconnect-recovery)
        for (ServerPlayerEntity p : getAllParticipants()) {
            if (p != null) {
                ServerWorld clearWorld = (ServerWorld) p.getEntityWorld();
                CrafticsSavedData clearData = CrafticsSavedData.get(clearWorld);
                clearData.getPlayerData(p.getUuid()).inCombat = false;
                clearData.markDirty();
            }
        }

        // Clean up party tracking
        cleanupPartyTracking();

        arena = null;
        enemies = null;
        player = null;
        leaderUuid = null;
        movePath = null;
        active = false;
        testRange = false;
        combatEffects.clear();
        tileEffects.clear();

        CrafticsMod.LOGGER.info("Combat ended.");
    }

    /** Save alive allied pets so they persist into the next level. */
    private void savePets() {
        if (enemies == null) return;
        for (CombatEntity e : enemies) {
            if (e.isAlive() && e.isAlly() && !e.isMounted()) {
                savedPets.add(new PetData(
                    e.getEntityTypeId(), e.getCurrentHp(), e.getMaxHp(),
                    e.getAttackPower(), e.getDefense(), e.getMoveSpeed(), e.getRange()));
            }
        }
        if (!savedPets.isEmpty()) {
            CrafticsMod.LOGGER.info("Saved {} pet(s) for next level.", savedPets.size());
        }
    }

    /** Spawn previously saved pets into the current arena near the player start. */
    private void spawnSavedPets() {
        if (savedPets.isEmpty() || arena == null || player == null) return;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        GridPos playerStart = arena.getPlayerGridPos();

        for (PetData pet : savedPets) {
            // Find a walkable tile near the player start
            GridPos spawnPos = null;
            for (int dx = -1; dx <= 1 && spawnPos == null; dx++) {
                for (int dz = -1; dz <= 1 && spawnPos == null; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    GridPos candidate = new GridPos(playerStart.x() + dx, playerStart.z() + dz);
                    if (arena.isInBounds(candidate) && !arena.isOccupied(candidate)) {
                        var tile = arena.getTile(candidate);
                        if (tile != null && tile.isWalkable()) spawnPos = candidate;
                    }
                }
            }
            if (spawnPos == null) continue;

            // Spawn the pet entity
            var entityType = Registries.ENTITY_TYPE.get(Identifier.of(pet.entityType()));
            BlockPos blockPos = arena.gridToBlockPos(spawnPos);
            var rawEntity = entityType.create(world, null, blockPos,
                net.minecraft.entity.SpawnReason.MOB_SUMMONED, false, false);
            if (!(rawEntity instanceof net.minecraft.entity.mob.MobEntity mob)) continue;

            mob.requestTeleport(blockPos.getX() + 0.5, blockPos.getY() + 1.0, blockPos.getZ() + 0.5);
            mob.setPersistent();
            mob.setAiDisabled(true);
            mob.noClip = true;
            mob.setSilent(true);
            mob.setGlowing(false);
            world.spawnEntity(mob);

            CombatEntity ce = new CombatEntity(
                mob.getId(), pet.entityType(), spawnPos,
                pet.maxHp(), pet.atk(), pet.def(), pet.range());
            // Restore saved HP (constructor sets to max, so damage down to saved value)
            if (pet.hp() < pet.maxHp()) {
                ce.takeDamage(pet.maxHp() - pet.hp());
            }
            ce.setAlly(true);
            ce.setMobEntity(mob);
            enemies.add(ce);
            arena.placeEntity(ce);

            sendMessage("\u00a7a" + ce.getDisplayName() + " rejoins the fight!");
        }
        savedPets.clear();
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
                java.util.Set<GridPos> reachable = Pathfinding.getReachableTiles(arena, arena.getPlayerGridPos(), movePointsRemaining, playerHasBoat());
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

                if (enemy.isBackgroundBoss()) {
                    // Background boss: check range to any of its registered tiles
                    boolean inRange = false;
                    java.util.List<GridPos> bossTiles = new java.util.ArrayList<>();
                    for (var occEntry : arena.getOccupants().entrySet()) {
                        if (occEntry.getValue() == enemy) {
                            bossTiles.add(occEntry.getKey());
                            int dist = playerPos.manhattanDistance(occEntry.getKey());
                            if (dist <= range) inRange = true;
                            if (range == PlayerCombatStats.RANGE_CROSSBOW_ROOK
                                    && PlayerCombatStats.isInCrossbowLine(arena, playerPos, occEntry.getKey())) {
                                inRange = true;
                            }
                        }
                    }
                    if (inRange) {
                        highlighted.add(enemy);
                        for (GridPos tile : bossTiles) {
                            attackList.add(tile.x());
                            attackList.add(tile.z());
                        }
                    }
                } else {
                    // Normal entity range check
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

            if (enemy.isBackgroundBoss()) {
                // Background bosses are registered on many tiles manually — collect them all
                for (var occEntry : arena.getOccupants().entrySet()) {
                    if (occEntry.getValue() == enemy) {
                        enemyMapList.add(occEntry.getKey().x());
                        enemyMapList.add(occEntry.getKey().z());
                        enemyMapList.add(enemy.getEntityId());
                    }
                }
            } else {
                // Normal entities: use getOccupiedTiles for multi-tile entities (e.g., 2x2 spider)
                for (GridPos occupied : GridArena.getOccupiedTiles(enemy.getGridPos(), enemy.getSize())) {
                    enemyMapList.add(occupied.x());
                    enemyMapList.add(occupied.z());
                    enemyMapList.add(enemy.getEntityId());
                }
            }
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
            return;
        }

        // Hint: "Press R to end turn" when player has no movement and no weapon can reach any enemy
        if (!endTurnHintSent && movePointsRemaining <= 0 && apRemaining > 0) {
            boolean canReachAny = false;
            GridPos playerPos = arena.getPlayerGridPos();
            // Check held weapon
            int heldRange = PlayerCombatStats.getWeaponRange(player);
            for (CombatEntity enemy : enemies) {
                if (!enemy.isAlive() || enemy.isAlly()) continue;
                if (heldRange == PlayerCombatStats.RANGE_CROSSBOW_ROOK) {
                    for (var tile : GridArena.getOccupiedTiles(enemy.getGridPos(), enemy.getSize())) {
                        if (PlayerCombatStats.isInCrossbowLine(arena, playerPos, tile)) { canReachAny = true; break; }
                    }
                } else if (enemy.minDistanceTo(playerPos) <= heldRange) {
                    canReachAny = true;
                }
                if (canReachAny) break;
            }
            // Also check if player has usable items (potions, throwables, sherds, etc.)
            if (!canReachAny) {
                for (int slot = 0; slot < player.getInventory().size(); slot++) {
                    var stack = player.getInventory().getStack(slot);
                    if (stack.isEmpty()) continue;
                    if (ItemUseHandler.isUsableItem(stack.getItem()) || PotterySherdSpells.isPotterySherd(stack.getItem())) {
                        canReachAny = true;
                        break;
                    }
                }
            }
            if (!canReachAny) {
                sendMessage("§7§oNo actions available. Press §fR§7§o to end your turn.");
                endTurnHintSent = true;
            }
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
            case BURNING, SOAKED, CONFUSION -> null; // no vanilla equivalent
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
            // Tag allies so client can display them separately
            if (e.isAlly()) {
                typeIds.append(";ally");
            }
            // Append status effects after type ID with ; separator
            StringBuilder efx = new StringBuilder();
            if (e.isStunned()) efx.append(";Stunned");
            if (e.isEnraged()) efx.append(";Enraged");
            if (e.getSpeedBonus() < 0) efx.append(";Slowed");
            if (e.getPoisonTurns() > 0) efx.append(";Poisoned(" + e.getPoisonTurns() + "t)");
            if (e.getAttackPenalty() > 0) efx.append(";Weakened(-" + e.getAttackPenalty() + "ATK)");
            if (e.getMobEntity() != null && e.getMobEntity().isOnFire()) efx.append(";Burning");
            if (e.getSoakedTurns() > 0) efx.append(";Soaked(" + e.getSoakedTurns() + "t)");
            if (e.getConfusionTurns() > 0) efx.append(";Confused(" + e.getConfusionTurns() + "t)");
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

        // Build party HP data: "uuid,name,hp,maxHp,dead|..." (empty when solo)
        String partyHpData = "";
        if (partyPlayers.size() > 1) {
            StringBuilder phb = new StringBuilder();
            for (ServerPlayerEntity member : partyPlayers) {
                if (member == null || member.isRemoved()) continue;
                if (phb.length() > 0) phb.append("|");
                boolean isDead = deadPartyMembers.contains(member.getUuid());
                int memberHp = isDead ? 0 : ((int) member.getHealth() <= 1 ? 0 : (int) member.getHealth());
                int memberMaxHp = (int) member.getMaxHealth();
                phb.append(member.getUuid().toString()).append(",")
                   .append(member.getName().getString()).append(",")
                   .append(memberHp).append(",")
                   .append(memberMaxHp).append(",")
                   .append(isDead ? 1 : 0);
            }
            partyHpData = phb.toString();
        }

        sendToAllParty(new CombatSyncPayload(
            phase.ordinal(), apRemaining, movePointsRemaining,
            getPlayerHp(), (int) player.getMaxHealth(), turnNumber,
            maxAp, maxSpeed, enemyData, typeIds.toString(),
            combatEffects.getDisplayString(), killStreak,
            partyHpData
        ));
    }

    private void sendMessage(String msg) {
        // Broadcast to regular chat for all party participants
        for (ServerPlayerEntity p : getAllParticipants()) {
            if (p != null) {
                p.sendMessage(Text.literal(msg), false);
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
            data.getPlayerData(leaderUuid != null ? leaderUuid : player.getUuid()).addEmeralds(bonusEmeralds);
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
