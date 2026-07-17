package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;

public class CombatEntity {
    // Stack transformations (Zombie Stack, Slime Tower, etc.) mutate these in
    // place via applyStackLayer when the entity is "killed" but still has
    // remaining layers, so they intentionally aren't final.
    private int entityId;
    private String entityTypeId;
    private GridPos gridPos;
    private int maxHp;
    private int currentHp;
    private int attackPower;
    private int defense;
    private int range;
    private boolean alive;
    private MobEntity mobEntity;

    private boolean damagedSinceLastTurn = false;
    /** UUID of the party member who most recently dealt damage to this entity.
     *  Used to attribute per-mob loot drops to the player who killed it instead
     *  of splitting them across the whole party. */
    private java.util.UUID lastDamagerUuid = null;
    private int fuseTimer = 0;
    private boolean selfExploded = false; // creeper self-detonation = no drops
    /**
     * Grid footprint in tiles. Independent per axis so long mobs (sniffer, camel,
     * ravager, horses) can occupy e.g. 1x2 or 2x3 rectangles instead of being
     * squeezed into squares. The anchor tile is gridPos; the footprint covers
     * (x+dx, z+dz) for dx in [0,sizeX), dz in [0,sizeZ). Orientation is fixed at
     * spawn (see {@link #orientFootprint}) - it does not rotate with model yaw.
     */
    private int sizeX;
    private int sizeZ;
    private int moveSpeed;
    private int speedBonus = 0;
    private int attackPenalty = 0;
    private int attackPenaltyTurns = 0;
    private int poisonTurns = 0;
    private int poisonAmplifier = 0; // 0 = level I, 1 = level II, etc
    /** True once this enemy has taken its turn in the current round; cleared each player turn. */
    private boolean actedThisRound = false;
    private int witherTurns = 0;
    private int witherAmplifier = 0; // 0 = level I, 1 = level II, etc
    /** Highest duration this wither has reached, so the ramp in {@link EffectFormulas#witherTick}
     *  measures elapsed turns from the wither's real start rather than restarting every time it
     *  is re-applied. Mirrors the player's {@code ActiveEffect.peakTurns}. Reset when wither
     *  expires or is cleansed. */
    private int witherPeakTurns = 0;
    private boolean enraged = false;
    /**
     * When set, the entity is fully immobilized this turn - used by the Creaking
     * gaze mechanic. Bypasses the {@link #getMoveSpeed} clamp (which floors at 1)
     * so a frozen Creaking truly can't move, and AI implementations check this
     * flag at the top of {@code decideAction} to skip attacks too.
     */
    private boolean frozen = false;
    private boolean drownedHasTrident = false;
    private int defensePenalty = 0;
    private int defensePenaltyTurns = 0;
    private int burningTurns = 0;
    /** Burning's LEVEL, stored 0-based (amplifier 0 = level I) like poison's and wither's.
     *  This was once {@code burningDamage} and held a per-turn damage number, which is how
     *  damage values ended up passed into a slot that means "level". The name is part of the
     *  fix: a field called burningDamage holding a level is exactly how that bug happened. */
    private int burningAmplifier = 0;
    private int soakedTurns = 0;
    private int soakedAmplifier = 0;
    /** Visual+movement Airtime state for enemies/allies. No damage hook. */
    private int airtimeStateTurns = 0;
    /** Visual+movement Levitation state; amplifier drives the per-level move slow. */
    private int levitationStateTurns = 0;
    private int levitationStateAmplifier = 0;
    private int confusionTurns = 0;
    private int confusionAmplifier = 0;
    private int slownessTurns = 0;
    private int slownessPenalty = 0;
    private int bleedStacks = 0; // drives the triangular bleed DOT (1, 3, 6, 10...) - not a per-hit bonus
    private int permanentDefReduction = 0; // from Breach, never expires
    private int attackBoost = 0;
    private int defenseBoost = 0;
    private int rangeOverride = -1;
    private boolean backgroundBoss = false; // targetable but doesn't block movement
    // Out of the arena entirely: nothing in it can select or damage this entity (Revenant Burrow).
    // Not the same as backgroundBoss, which stays targetable.
    private boolean untargetable = false;
    private int visualProjectileEntityId = -1;
    private boolean deathProcessed = false; // guards against double death handling
    private boolean hasSplit = false; // guards against double-splitting (Molten King)
    private EnemyAI aiInstance = null; // per-entity AI override (for split copies with own state)
    private int linkedHeartId = -1; // creaking → heart link (heart death kills creaking)
    private int linkedCreakingId = -1; // heart → creaking back-link
    /** Craftics × Artifacts: curio this enemy carries (EMPTY = none). Its stat buffs are
     *  folded in at spawn; the stack itself is kept for the on-kill drop roll.
     *  Stored as null internally (the getter maps null → EMPTY): an inline
     *  {@code ItemStack.EMPTY} initializer triggers Minecraft's registry
     *  bootstrap at construction, which breaks every pure-logic CombatEntity
     *  unit test (buffs/DoT) that runs without the game. */
    private net.minecraft.item.ItemStack wornArtifact = null;

    public CombatEntity(int entityId, String entityTypeId, GridPos gridPos,
                        int maxHp, int attackPower, int defense, int range) {
        this(entityId, entityTypeId, gridPos, maxHp, attackPower, defense, range, -1, -1);
    }

    public CombatEntity(int entityId, String entityTypeId, GridPos gridPos,
                        int maxHp, int attackPower, int defense, int range, int sizeOverride) {
        this(entityId, entityTypeId, gridPos, maxHp, attackPower, defense, range, sizeOverride, -1);
    }

    /**
     * @param sizeOverride  grid size in tiles, or {@code <= 0} to use the entity type's default
     * @param speedOverride combat move speed in tiles per turn, or {@code <= 0} to use the
     *                      entity type's default (see {@link #getDefaultMoveSpeed})
     */
    public CombatEntity(int entityId, String entityTypeId, GridPos gridPos,
                        int maxHp, int attackPower, int defense, int range,
                        int sizeOverride, int speedOverride) {
        this.entityId = entityId;
        this.entityTypeId = entityTypeId;
        this.gridPos = gridPos;
        this.maxHp = Math.max(1, maxHp);
        this.currentHp = this.maxHp;
        this.attackPower = Math.max(1, attackPower);
        this.defense = defense;
        this.range = range;
        this.alive = true;
        if (sizeOverride > 0) {
            this.sizeX = sizeOverride;
            this.sizeZ = sizeOverride;
        } else {
            int[] fp = getDefaultFootprint(entityTypeId);
            this.sizeX = fp[0];
            this.sizeZ = fp[1];
        }
        this.moveSpeed = speedOverride > 0 ? speedOverride : getDefaultMoveSpeed(entityTypeId);
    }

    public int getEntityId() { return entityId; }
    public String getEntityTypeId() { return entityTypeId; }
    public GridPos getGridPos() { return gridPos; }
    public void setGridPos(GridPos pos) { this.gridPos = pos; }
    public int getMaxHp() { return maxHp; }
    public int getCurrentHp() { return currentHp; }
    public void heal(int amount) { currentHp = Math.min(getEffectiveMaxHp(), currentHp + amount); }
    public int getAttackPower() { return Math.max(0, attackPower + attackBoost + getAttackBuffBonus() - attackPenalty); }
    public int getDefense() { return defense + defenseBoost; }
    public int getRange() { return rangeOverride >= 0 ? rangeOverride : range; }
    public int getAttackBoost() { return attackBoost; }
    public void setAttackBoost(int boost) { this.attackBoost = boost; }
    public void setDefenseBoost(int boost) { this.defenseBoost = boost; }
    public void setRangeOverride(int r) { this.rangeOverride = r; }
    public boolean isAlive() { return alive; }
    /** Footprint width in tiles along the grid X axis. */
    public int getSizeX() { return sizeX; }
    /** Footprint depth in tiles along the grid Z axis. */
    public int getSizeZ() { return sizeZ; }
    /** Largest footprint dimension - for scalar consumers (VFX radii, clearance scans). */
    public int getMaxSize() { return Math.max(sizeX, sizeZ); }
    /** True when the footprint covers more than one tile on either axis. */
    public boolean isMultiTile() { return sizeX > 1 || sizeZ > 1; }
    /** Square footprint setter (bosses, dragon/revenant background-boss resets). */
    public void setSize(int size) { this.sizeX = size; this.sizeZ = size; }
    /** Rectangular footprint setter; use {@link #orientFootprint} to pick the axes. */
    public void setFootprint(int sizeX, int sizeZ) {
        this.sizeX = Math.max(1, sizeX);
        this.sizeZ = Math.max(1, sizeZ);
    }
    public boolean isBackgroundBoss() { return backgroundBoss; }
    public void setBackgroundBoss(boolean bg) { this.backgroundBoss = bg; }
    /**
     * True while the entity is physically out of the arena and nothing in it can reach the
     * entity: currently the Revenant's Burrow. Distinct from {@link #isBackgroundBoss}, which
     * means "pass-through but still targetable" and does NOT block damage.
     *
     * <p>Enforced at the {@link #takeDamage} chokepoint rather than only at target selection,
     * so AoE, splash, and chain effects that reach for a combatant directly cannot bypass it.
     * DOT ticks route through {@link #applyDirectDamage} and are deliberately still applied:
     * the entity went under carrying the poison.
     */
    public boolean isUntargetable() { return untargetable; }
    public void setUntargetable(boolean u) { this.untargetable = u; }
    public int getVisualProjectileEntityId() { return visualProjectileEntityId; }
    public void setVisualProjectileEntityId(int id) { this.visualProjectileEntityId = id; }
    public boolean isDeathProcessed() { return deathProcessed; }
    public void markDeathProcessed() { this.deathProcessed = true; }
    public boolean hasSplit() { return hasSplit; }
    public void markSplit() { this.hasSplit = true; }
    public int getLinkedHeartId() { return linkedHeartId; }
    public void setLinkedHeartId(int id) { this.linkedHeartId = id; }
    public int getLinkedCreakingId() { return linkedCreakingId; }
    public void setLinkedCreakingId(int id) { this.linkedCreakingId = id; }
    /** Never null: internal null (unset) reads as EMPTY. */
    public net.minecraft.item.ItemStack getWornArtifact() {
        return wornArtifact == null ? net.minecraft.item.ItemStack.EMPTY : wornArtifact;
    }
    public void setWornArtifact(net.minecraft.item.ItemStack stack) {
        this.wornArtifact = stack;
    }
    public EnemyAI getAiInstance() { return aiInstance; }
    public void setAiInstance(EnemyAI ai) { this.aiInstance = ai; }

    /**
     * Per-entity AI scratch state. AIRegistry hands out ONE shared AI instance per
     * entity type, so any mutable field on an {@code EnemyAI} subclass is silently
     * shared by every mob of that type across every fight (e.g. one evoker's
     * "already summoned" flag muted all future evokers). Stateful AIs keep their
     * counters/flags here instead - the map lives and dies with the entity.
     */
    private final java.util.Map<String, Integer> aiMemory = new java.util.HashMap<>();
    public int getAiMemory(String key, int defaultValue) {
        return aiMemory.getOrDefault(key, defaultValue);
    }
    public void setAiMemory(String key, int value) { aiMemory.put(key, value); }
    public boolean hasAiMemory(String key) { return aiMemory.containsKey(key); }

    /** Minimum manhattan distance from a point to any tile this entity occupies. */
    public int minDistanceTo(GridPos from) {
        return minDistanceFromSizedEntity(gridPos, sizeX, sizeZ, from);
    }

    /**
     * Minimum Chebyshev (king-move) distance from any tile of this entity to a
     * point. Chebyshev counts a diagonal step as distance 1, so a melee range
     * check using this lets the player hit from the 8 surrounding tiles, diagonals
     * included. Used for melee reach on both the server and the attack highlight.
     */
    public int minChebyshevDistanceTo(GridPos from) {
        if (sizeX <= 1 && sizeZ <= 1) {
            return Math.max(Math.abs(from.x() - gridPos.x()), Math.abs(from.z() - gridPos.z()));
        }
        int min = Integer.MAX_VALUE;
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                int d = Math.max(Math.abs(from.x() - (gridPos.x() + dx)),
                                 Math.abs(from.z() - (gridPos.z() + dz)));
                if (d < min) min = d;
            }
        }
        return min;
    }

    /** Returns the occupied tile of this entity closest to the given point. */
    public GridPos nearestTileTo(GridPos from) {
        if (sizeX <= 1 && sizeZ <= 1) return gridPos;
        GridPos best = gridPos;
        int bestDist = Integer.MAX_VALUE;
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                GridPos tile = new GridPos(gridPos.x() + dx, gridPos.z() + dz);
                int dist = Math.abs(from.x() - tile.x()) + Math.abs(from.z() - tile.z());
                if (dist < bestDist) { bestDist = dist; best = tile; }
            }
        }
        return best;
    }

    /** Square-footprint convenience overload of the rectangular version below. */
    public static int minDistanceFromSizedEntity(GridPos origin, int entitySize, GridPos target) {
        return minDistanceFromSizedEntity(origin, entitySize, entitySize, target);
    }

    /** Minimum manhattan distance from any tile of a sized entity at origin to a target point. */
    public static int minDistanceFromSizedEntity(GridPos origin, int sizeX, int sizeZ, GridPos target) {
        if (sizeX <= 1 && sizeZ <= 1) return origin.manhattanDistance(target);
        int min = Integer.MAX_VALUE;
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                int dist = Math.abs(target.x() - (origin.x() + dx)) + Math.abs(target.z() - (origin.z() + dz));
                if (dist < min) min = dist;
            }
        }
        return min;
    }
    public int getMoveSpeed() {
        // Frozen entities (Creaking under gaze) bypass the +1 floor entirely -
        // they truly can't move this turn.
        if (frozen) return 0;
        int base = moveSpeed + speedBonus + getSpeedBuffBonus();
        if (soakedTurns > 0) base -= 1;
        if (slownessTurns > 0) base -= slownessPenalty;
        if (levitationStateTurns > 0) base -= (1 + levitationStateAmplifier);
        var speedCfg = com.crackedgames.craftics.CrafticsMod.CONFIG;
        float mult = speedCfg != null ? speedCfg.enemyMoveSpeedMultiplier() : 1.0f;
        // Allies always keep a usable move budget - never below 2 tiles/turn,
        // even under the speed multiplier or Soaked/Slowness debuffs. Enemies
        // keep the standard floor of 1.
        return Math.max(ally ? 2 : 1, (int)(base * mult));
    }
    public void setSpeedBonus(int bonus) { this.speedBonus = bonus; }
    public int getSpeedBonus() { return speedBonus; }
    public boolean isFrozen() { return frozen; }
    public void setFrozen(boolean f) { this.frozen = f; }
    public boolean isEnraged() { return enraged; }
    public void setEnraged(boolean e) { this.enraged = e; }
    public boolean isDrownedWithTrident() { return drownedHasTrident; }
    public void setDrownedWithTrident(boolean hasTrident) { this.drownedHasTrident = hasTrident; }
    public int getAttackPenalty() { return attackPenalty; }
    public void setAttackPenalty(int p) { this.attackPenalty = p; }
    public int getAttackPenaltyTurns() { return attackPenaltyTurns; }
    public void setAttackPenaltyTurns(int t) { this.attackPenaltyTurns = t; }
    public boolean hasActedThisRound() { return actedThisRound; }
    public void setActedThisRound(boolean acted) { this.actedThisRound = acted; }
    public int getPoisonTurns() { return poisonTurns; }
    public void setPoisonTurns(int t) { this.poisonTurns = t; }
    public int getPoisonAmplifier() { return poisonAmplifier; }
    public void setPoisonAmplifier(int a) { this.poisonAmplifier = a; }
    public int getWitherTurns() { return witherTurns; }
    public void setWitherTurns(int t) { this.witherTurns = t; }
    public int getWitherAmplifier() { return witherAmplifier; }
    public void setWitherAmplifier(int a) {
        this.witherAmplifier = a;
        // The wither-expiry site zeroes the amplifier once the turns run out; clearing the ramp's
        // peak with it means a fresh wither starts its ramp from the beginning instead of
        // inheriting the last one's elapsed count and opening at full strength.
        if (a == 0 && witherTurns <= 0) witherPeakTurns = 0;
    }

    /** Bonus damage that scales DOTs (poison/wither) with the target's max HP.
     *  +1 per 20 max HP, with a floor of 1. Encourages DOT use against tougher
     *  enemies and bosses without making them devastating against trash mobs. */
    public int getMaxHpDotBonus() {
        return Math.max(1, maxHp / 20);
    }

    /**
     * Poison damage for one tick. The shared player formula plus this mob's max-HP term.
     *
     * <p>This used to be a flat {@code 1 + amplifier}, which meant poison behaved completely
     * differently depending on whether it landed on you or on a mob. The formula now comes from
     * {@link EffectFormulas}, so it front-loads on a mob exactly as it does on a player.
     */
    public int getPoisonTickDamage() {
        if (poisonTurns <= 0) return 0;
        return EffectFormulas.poisonTick(poisonAmplifier + 1, poisonTurns, 0) + getMaxHpDotBonus();
    }

    /**
     * Wither damage for one tick. The shared player formula plus this mob's max-HP term.
     *
     * <p>This used to TAPER on a mob while ramping on a player - the two were mirror images of
     * each other under one name. It now ramps on both.
     */
    public int getWitherTickDamage() {
        if (witherTurns <= 0) return 0;
        return EffectFormulas.witherTick(witherAmplifier + 1, witherPeakTurns, witherTurns, 0)
            + getMaxHpDotBonus();
    }

    /**
     * Burning damage for one tick. The shared player formula plus this mob's max-HP term.
     *
     * <p>This used to return a stored damage number outright, so burning was the one DoT whose
     * strength was decided by its caller rather than by the rules - the same effect meant whatever
     * each of its thirty call sites happened to pass. It now reads a LEVEL, like poison and
     * wither, and {@link EffectFormulas} owns the per-turn number.
     */
    public int getBurningTickDamage() {
        if (burningTurns <= 0) return 0;
        return EffectFormulas.burningTick(burningAmplifier + 1, 0) + getMaxHpDotBonus();
    }

    public MobEntity getMobEntity() { return mobEntity; }
    public void setMobEntity(MobEntity mob) { this.mobEntity = mob; }

    /** True when the backing mob is fire-immune in vanilla (blaze, magma cube, strider,
     *  ghast, wither, ...), so fire and burning must not damage it. */
    public boolean isFireImmune() {
        return mobEntity != null && mobEntity.isFireImmune();
    }

    public boolean wasDamagedSinceLastTurn() { return damagedSinceLastTurn; }
    public void setDamagedSinceLastTurn(boolean v) { this.damagedSinceLastTurn = v; }

    public int getFuseTimer() { return fuseTimer; }
    public void setFuseTimer(int timer) { this.fuseTimer = timer; }

    public boolean isSelfExploded() { return selfExploded; }
    public void setSelfExploded(boolean v) { this.selfExploded = v; }

    private boolean stunned = false;
    public boolean isStunned() { return stunned; }
    public void setStunned(boolean s) {
        if (s) {
            if (isStunImmune()) return; // blunt-resistant mobs shrug off stuns
            // Bosses resist half of every stun attempt (halves the effective stun chance from
            // any source) so they can't be stun-locked. Blunt-resistant bosses are fully immune above.
            if (isBoss() && Math.random() >= 0.5) return;
        }
        this.stunned = s;
    }

    /** Blunt-resistant mobs (spiders, golems, ravager, warden, the Wither, etc.) are immune
     *  to stun: a crushing blow that barely dents them can't lock them down. Single gate so
     *  every stun source (mace, breeze, sherds, arrows, hybrid sets) respects it. */
    public boolean isStunImmune() {
        return MobResistances.isResistant(entityTypeId, getAiKey(), DamageType.BLUNT);
    }

    private boolean boss = false;
    public boolean isBoss() { return boss; }
    public void setBoss(boolean b) { this.boss = b; }

    /** If true, knockback stops before hazard tiles (void, deep water, lava) instead of landing on them. */
    private boolean hazardImmune = false;
    public boolean isHazardImmune() { return hazardImmune; }
    public void setHazardImmune(boolean v) { this.hazardImmune = v; }

    /** True for stationary "virtual block" enemies (e.g. the Creaking Heart) that must NEVER
     *  be displaced. Their in-world block doesn't move with the grid entity, so any
     *  knockback/pull desyncs the target and makes them unhittable. {@link GridArena#moveEntity}
     *  refuses to move these and knockback code skips them. */
    private boolean immovable = false;
    public boolean isImmovable() { return immovable; }
    public void setImmovable(boolean v) { this.immovable = v; }

    /** Spider ceiling mechanic - true when the spider is hanging from the ceiling (off-grid). */
    private boolean onCeiling = false;
    public boolean isOnCeiling() { return onCeiling; }
    public void setOnCeiling(boolean v) { this.onCeiling = v; }

    /** If true, boss entities can pathfind through this entity (e.g., egg sacs). */
    private boolean passableForBoss = false;
    public boolean isPassableForBoss() { return passableForBoss; }
    public void setPassableForBoss(boolean v) { this.passableForBoss = v; }

    /**
     * Scenery: an attackable object that is NOT a must-kill for room-clear (Revenant graves, Bastion
     * Brute war banners). These are optional counterplay, so the level must be able to end with them
     * still standing. Being in `enemies` is what makes them attackable at all, so the victory check
     * cannot simply be "the list is empty"; it has to skip these.
     *
     * <p>Deliberately NOT set on the Broodmother's egg sacs, which ARE must-kill. That is why
     * BroodmotherAI.initEggSacs gates on reachability: an unreachable must-kill soft-locks the run.
     *
     */
    private boolean scenery = false;
    public boolean isScenery() { return scenery; }
    public void setScenery(boolean v) { this.scenery = v; }

    /**
     * True if this entity is a reason the room has not cleared yet. The single rule behind every
     * victory check: a live, non-ally, non-scenery entity must still be dealt with.
     *
     * <p>Lives here rather than in CombatManager so it is one decision rather than one per call
     * site, and so it is unit-testable without an arena.
     */
    public boolean blocksRoomClear() {
        return alive && !ally && !scenery;
    }

    /**
     * An inert block-backed object (grave, war banner, egg sac): it occupies a tile and has HP, but
     * never acts. Sibling to {@link #isScenery}, not the same axis: this is "never does anything on
     * its turn", scenery is "does not block room-clear". The egg sac is inert but NOT scenery.
     *
     * <p>Suppresses the per-turn "waits..." line, which these would otherwise print every single
     * turn. They still take their turn slot, since the enemy rotation is where per-entity
     * bookkeeping runs.
     */
    private boolean inertObject = false;
    public boolean isInertObject() { return inertObject; }
    public void setInertObject(boolean v) { this.inertObject = v; }

    private String bossDisplayName = null;
    public void setBossDisplayName(String name) { this.bossDisplayName = name; }

    /**
     * Stack chain: the layers UNDER the current one, base-first. When this is
     * non-empty and the entity would die, it instead consumes the head of the
     * list and re-arms with that layer's stats and display name. Used by the
     * stacked-enemy variants (Zombie Stack, Slime Tower, Blaze Tower, etc).
     */
    private java.util.List<StackVariants.Layer> stackChain = new java.util.ArrayList<>();
    public java.util.List<StackVariants.Layer> getStackChain() { return stackChain; }
    public void setStackChain(java.util.List<StackVariants.Layer> chain) {
        this.stackChain = chain != null ? new java.util.ArrayList<>(chain) : new java.util.ArrayList<>();
    }
    public boolean hasStackLayers() { return stackChain != null && !stackChain.isEmpty(); }

    /** Display name override used by stack-layer transformations (overrides the species name). */
    private String stackDisplayName = null;
    public void setStackDisplayName(String name) { this.stackDisplayName = name; }
    public String getStackDisplayName() { return stackDisplayName; }

    /** Stack id (e.g. "zombie_stack") for blaze-tower triple-fire logic and similar branches. */
    private String stackId = null;
    public void setStackId(String id) { this.stackId = id; }
    public String getStackId() { return stackId; }

    /** Mutate the entity into a new layer of its stack chain. Called by CombatManager on "death". */
    public void applyStackLayer(StackVariants.Layer next, int entityIdForNewMob) {
        this.entityTypeId = next.entityTypeId();
        this.maxHp = Math.max(1, next.hp());
        this.currentHp = this.maxHp;
        this.attackPower = Math.max(1, next.attack());
        this.defense = next.defense();
        this.range = next.range();
        if (next.speed() > 0) this.moveSpeed = next.speed();
        else this.moveSpeed = getDefaultMoveSpeed(next.entityTypeId());
        this.stackDisplayName = next.displayName();
        this.alive = true;
        this.deathProcessed = false;
        this.entityId = entityIdForNewMob;
    }

    /**
     * Void Walker mirror image flag. Marks an entity as one of the boss's decoy
     * clones: it wears the boss's name but is a weak decoy (low HP/ATK) that takes
     * double damage (see {@link #damageTakenMultiplier}). The flag drives the
     * no-loot/no-XP death path and the mass-dispel-on-real-boss-hit logic in
     * CombatManager; it no longer changes how damage is applied.
     */
    private boolean mirrorClone = false;
    public boolean isMirrorClone() { return mirrorClone; }
    public void setMirrorClone(boolean v) { this.mirrorClone = v; }

    /**
     * Flat multiplier applied to all incoming damage at the single damage chokepoint
     * ({@link #applyDirectDamage}). Defaults to {@code 1.0} (no-op) for every entity;
     * Void Walker mirror clones set this to {@code 2.0} so they take double damage.
     */
    private double damageTakenMultiplier = 1.0;
    public double getDamageTakenMultiplier() { return damageTakenMultiplier; }
    public void setDamageTakenMultiplier(double m) { this.damageTakenMultiplier = m; }

    /** Entity ID of the boss this clone mirrors. -1 when not a clone. */
    private int cloneOfBossId = -1;
    public int getCloneOfBossId() { return cloneOfBossId; }
    public void setCloneOfBossId(int id) { this.cloneOfBossId = id; }

    /**
     * Sentinel flag: this entity exists only to occupy the netherite golem mount's
     * 1×3 wall tiles in the arena occupant map (so enemies can't path through them).
     * It is never added to the enemy/ally lists or a turn queue; consumers that walk
     * the occupant map (client enemy sync, AoE collectors) must skip it.
     */
    private boolean mountWall = false;
    public boolean isMountWall() { return mountWall; }
    public void setMountWall(boolean v) { this.mountWall = v; }

    private String aiOverrideKey = null;
    public String getAiOverrideKey() { return aiOverrideKey; }
    public void setAiOverrideKey(String key) { this.aiOverrideKey = key; }
    /** AIRegistry uses this instead of entityTypeId when aiOverrideKey is set. */
    public String getAiKey() { return aiOverrideKey != null ? aiOverrideKey : entityTypeId; }

    private int maxHpReduction = 0; // wither effect, permanent
    public int getMaxHpReduction() { return maxHpReduction; }
    public void addMaxHpReduction(int amount) { this.maxHpReduction += amount; }
    public int getEffectiveMaxHp() { return Math.max(1, maxHp - maxHpReduction); }

    private boolean projectile = false;
    private int projectileDirX = 0;
    private int projectileDirZ = 0;
    private String projectileType = null;
    private int projectileOwnerId = -1;
    private boolean projectileRedirected = false;

    public boolean isProjectile() { return projectile; }
    public void setProjectile(boolean p) { this.projectile = p; }
    public int getProjectileDirX() { return projectileDirX; }
    public void setProjectileDirX(int d) { this.projectileDirX = d; }
    public int getProjectileDirZ() { return projectileDirZ; }
    public void setProjectileDirZ(int d) { this.projectileDirZ = d; }
    public String getProjectileType() { return projectileType; }
    public void setProjectileType(String t) { this.projectileType = t; }
    public int getProjectileOwnerId() { return projectileOwnerId; }
    public void setProjectileOwnerId(int id) { this.projectileOwnerId = id; }
    public boolean isProjectileRedirected() { return projectileRedirected; }
    public void setProjectileRedirected(boolean r) { this.projectileRedirected = r; }

    private boolean ally = false;
    public boolean isAlly() { return ally; }
    public void setAlly(boolean a) { this.ally = a; }

    /** Null if not owned by a party member. */
    private java.util.UUID ownerUuid = null;
    public java.util.UUID getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(java.util.UUID uuid) { this.ownerUuid = uuid; }

    /** Null for non-hub-tamed allies. */
    private NbtCompound originalHubNbt = null;
    public NbtCompound getOriginalHubNbt() { return originalHubNbt; }
    public void setOriginalHubNbt(NbtCompound nbt) { this.originalHubNbt = nbt; }

    /**
     * True for allies summoned for the current battle only (e.g. spawn-egg summons),
     * as opposed to recruited hub pets. Temporary allies are NOT saved/returned to the
     * hub after combat - they exist solely for this fight.
     */
    private boolean temporaryAlly = false;
    public boolean isTemporaryAlly() { return temporaryAlly; }
    public void setTemporaryAlly(boolean v) { this.temporaryAlly = v; }

    /**
     * Rounds remaining before a timed summon auto-despawns. {@code -1} means
     * permanent (the default for normal allies). Decremented once per round by
     * the ally round-hook loop; on reaching {@code 0} the summon is removed
     * without going through the kill/loot path. Used by the honey golem's bee.
     */
    private int summonLifespanRounds = -1;
    public int getSummonLifespanRounds() { return summonLifespanRounds; }
    public void setSummonLifespanRounds(int rounds) { this.summonLifespanRounds = rounds; }

    private boolean mounted = false;
    public boolean isMounted() { return mounted; }
    public void setMounted(boolean m) { this.mounted = m; }

    /**
     * Resolved combat AI for this entity when it is an ally. Set when a party
     * mob is spawned into the arena (see {@code AllyArchetypes}); {@code null}
     * for enemies or allies whose AI hasn't been resolved, in which case
     * {@code handleAllyTurn} falls back to the registry / default melee AI.
     */
    private com.crackedgames.craftics.combat.ai.ally.AllyAI allyAi = null;
    public com.crackedgames.craftics.combat.ai.ally.AllyAI getAllyAi() { return allyAi; }
    public void setAllyAi(com.crackedgames.craftics.combat.ai.ally.AllyAI ai) { this.allyAi = ai; }

    private int aggroAllyEntityId = -1; // pet that hit us, for aggro target selection
    public int getAggroAllyEntityId() { return aggroAllyEntityId; }
    public void setAggroAllyEntityId(int id) { this.aggroAllyEntityId = id; }

    /** Terracotta golem taunt: while true, enemies are forced to target this ally. */
    private boolean taunting = false;
    public boolean isTaunting() { return taunting; }
    public void setTaunting(boolean v) { this.taunting = v; }

    /** Coal golem lit state: boosted, applies burn, and dies after its next attack. */
    private boolean litOneShot = false;
    public boolean isLitOneShot() { return litOneShot; }
    public void setLitOneShot(boolean v) { this.litOneShot = v; }

    /**
     * A player-summoned seeker vex (Archer sherd): flies at the nearest enemy on its own and
     * destroys itself on attack. The ally-side counterpart of the enemy's shulker-bullet seekers.
     */
    private boolean seekerProjectile = false;
    public boolean isSeekerProjectile() { return seekerProjectile; }
    public void setSeekerProjectile(boolean v) { this.seekerProjectile = v; }

    /** Marks a victim for a bonus loot roll, e.g. by a barrel golem kill. */
    private boolean bonusLootRoll = false;
    public boolean isBonusLootRoll() { return bonusLootRoll; }
    public void setBonusLootRoll(boolean v) { this.bonusLootRoll = v; }

    public int getDefensePenalty() { return defensePenalty; }
    public void setDefensePenalty(int p) { this.defensePenalty = p; }
    public int getDefensePenaltyTurns() { return defensePenaltyTurns; }
    public void setDefensePenaltyTurns(int t) { this.defensePenaltyTurns = t; }
    public int getBurningTurns() { return burningTurns; }
    public void setBurningTurns(int t) { this.burningTurns = t; }
    public int getBurningAmplifier() { return burningAmplifier; }
    public void setBurningAmplifier(int a) { this.burningAmplifier = a; }
    public int getSoakedTurns() { return soakedTurns; }
    public void setSoakedTurns(int t) { this.soakedTurns = t; }
    /** True while a Soaked status is active. THE predicate lightning sources use. */
    public boolean isSoaked() { return soakedTurns > 0; }
    public int getSoakedAmplifier() { return soakedAmplifier; }
    public void setSoakedAmplifier(int a) { this.soakedAmplifier = a; }
    public int getConfusionTurns() { return confusionTurns; }
    public void setConfusionTurns(int t) { this.confusionTurns = t; }
    public int getConfusionAmplifier() { return confusionAmplifier; }
    public void setConfusionAmplifier(int a) { this.confusionAmplifier = a; }

    public void applyAirtimeState(int turns) { this.airtimeStateTurns = Math.max(this.airtimeStateTurns, turns); }
    public boolean hasAirtimeState() { return airtimeStateTurns > 0; }
    public int getAirtimeStateTurns() { return airtimeStateTurns; }
    public void tickAirtimeState() { if (airtimeStateTurns > 0) airtimeStateTurns--; }

    public void applyLevitationState(int turns, int amplifier) {
        this.levitationStateTurns = Math.max(this.levitationStateTurns, turns);
        this.levitationStateAmplifier = Math.max(this.levitationStateAmplifier, amplifier);
    }
    public boolean hasLevitationState() { return levitationStateTurns > 0; }
    public int getLevitationStateTurns() { return levitationStateTurns; }
    public int getLevitationStateAmplifier() { return levitationStateAmplifier; }
    public void tickLevitationState() {
        if (levitationStateTurns > 0) {
            levitationStateTurns--;
            if (levitationStateTurns == 0) levitationStateAmplifier = 0;
        }
    }

    // ── Marked status ──
    // A marked entity takes extra damage from all attacks (2x normally, 1.5x for
    // bosses). Reusable for any entity (player/ally/enemy); currently applied only by
    // the player's spyglass. markedTurns counts down once per round.
    private int markedTurns = 0;
    public boolean isMarked() { return markedTurns > 0; }
    public int getMarkedTurns() { return markedTurns; }
    public void setMarkedTurns(int t) { this.markedTurns = t; }

    /**
     * Mark this entity for {@code turns}, but ONLY if it isn't already Marked.
     *
     * <p>Deliberately not additive and not a refresh: it neither stacks the duration nor
     * resets the timer. Spectral arrows use this, and without it a player could hold a stack
     * of them and keep re-marking a single target every shot, pinning a boss under permanent
     * double damage.
     *
     * @return true if the mark was applied, false if the entity was already Marked
     */
    public boolean markNonAdditive(int turns) {
        if (markedTurns > 0) return false;
        markedTurns = turns;
        return true;
    }
    /** The damage-taken multiplier while marked: 1.5x for bosses, 2x otherwise. 1.0 if unmarked. */
    public double getMarkedDamageMultiplier() {
        if (markedTurns <= 0) return 1.0;
        return isBoss() ? 1.5 : 2.0;
    }

    // ── Blinded status (enemy) ──
    // A blinded enemy can't see to attack: it fumbles its turn (deals no damage)
    // for the duration. Applied by the MoreTotems Tentacled totem. Counts down once
    // per round, mirroring the confusion field.
    private int blindedTurns = 0;
    public boolean isBlinded() { return blindedTurns > 0; }
    public int getBlindedTurns() { return blindedTurns; }
    public void setBlindedTurns(int t) { this.blindedTurns = t; }
    /** Apply/refresh blindness to the longer of the current and new duration. */
    public void stackBlinded(int turns) { this.blindedTurns = Math.max(this.blindedTurns, turns); }

    /**
     * How many distinct debuffs are currently active on this entity. Each type counts once
     * regardless of stacks or amplifier: Poison, Wither, Burning, Bleed, Slowness, Blinded,
     * Soaked, and Confusion. Read by the Executioner enchantment, which scales its bonus with
     * the number of debuffs on the target.
     */
    public int activeDebuffCount() {
        int count = 0;
        if (poisonTurns > 0) count++;
        if (witherTurns > 0) count++;
        if (burningTurns > 0) count++;
        if (bleedStacks > 0) count++;
        if (slownessTurns > 0) count++;
        if (blindedTurns > 0) count++;
        if (soakedTurns > 0) count++;
        if (confusionTurns > 0) count++;
        return count;
    }

    // --- Positive buffs (instrument support performances). Ticked by tickBuffs(). ---
    private int regenTurns = 0;
    private int regenPerTurn = 0;       // HP per turn (2 * (amplifier + 1))
    private int absorptionTurns = 0;
    private int absorptionHp = 0;       // temporary extra HP, absorbed before real HP
    private int slowFallingTurns = 0;   // > 0 = immune to knockback
    private int attackBuffTurns = 0;
    private int attackBuffBonus = 0;
    private int speedBuffTurns = 0;
    private int speedBuffBonus = 0;
    private int resistanceTurns = 0;
    private int resistanceLevel = 0;    // damage reduction per level

    // Remaining-turn readers for the buffs above. The magnitude getters
    // (getAttackBuffBonus, getResistanceLevel, ...) already return 0 once a buff expires, but
    // the status-icon overlay needs the DURATION to show a countdown, and it needs to know a
    // buff is active even where the magnitude alone can't say so.
    public int getRegenTurns() { return regenTurns; }
    public int getRegenPerTurn() { return regenPerTurn; }
    public int getAbsorptionTurns() { return absorptionTurns; }
    public int getSlowFallingTurns() { return slowFallingTurns; }
    public int getAttackBuffTurns() { return attackBuffTurns; }
    public int getSpeedBuffTurns() { return speedBuffTurns; }
    public int getResistanceTurns() { return resistanceTurns; }

    public int getSlownessTurns() { return slownessTurns; }
    public void setSlownessTurns(int t) { this.slownessTurns = t; }
    public int getSlownessPenalty() { return slownessPenalty; }
    public void setSlownessPenalty(int p) { this.slownessPenalty = p; }
    public int getBleedStacks() { return bleedStacks; }
    public void setBleedStacks(int s) { this.bleedStacks = s; }
    public void stackBleed(int stacks) { this.bleedStacks = Math.min(MAX_EFFECT_AMPLIFIER, this.bleedStacks + stacks); }

    /**
     * Damage dealt by a single bleed tick, triangular in the stack count (1, 3, 6, 10...).
     *
     * <p>Shared with the player via {@link EffectFormulas}; EffectParityTest pins the two
     * together. This method used to return a flat {@code stacks} while the player path charged
     * the triangular curve, so the same effect hit far harder on a player than on an enemy.
     */
    public static int computeBleedTickDamage(int stacks) {
        return EffectFormulas.bleedTick(stacks);
    }
    public int getPermanentDefReduction() { return permanentDefReduction; }
    public void addPermanentDefReduction(int amount) { this.permanentDefReduction += amount; }

    // Duration refreshes to longer value, intensity stacks up to cap
    private static final int MAX_EFFECT_AMPLIFIER = 999;

    public void stackWither(int turns, int ampIncrease) {
        witherTurns = Math.max(witherTurns, turns);
        witherAmplifier = Math.min(MAX_EFFECT_AMPLIFIER, witherAmplifier + ampIncrease);
        witherPeakTurns = Math.max(witherPeakTurns, witherTurns);
    }

    public void stackPoison(int turns, int ampIncrease) {
        poisonTurns = Math.max(poisonTurns, turns);
        poisonAmplifier = Math.min(MAX_EFFECT_AMPLIFIER, poisonAmplifier + ampIncrease);
    }

    /**
     * Set a target alight for {@code turns} turns at {@code ampIncrease} added levels.
     *
     * <p>The second argument is an AMPLIFIER (0 = level I), matching {@link #stackPoison} and
     * {@link #stackWither} and the {@code amplifier} the public addon API has always passed here.
     * It used to be read as a per-turn damage number at about half its call sites, so burning's
     * strength depended on which site applied it; {@link EffectFormulas} owns that number now.
     */
    public void stackBurning(int turns, int ampIncrease) {
        if (isFireImmune()) return; // fire-immune mobs (blaze, magma cube, ...) don't burn
        // A drenched target can't catch light. Without this, Soaked's douse could be undone
        // by any fire proc landing later in the same turn, and the "water beats fire" rule
        // would hold only until the next hit.
        if (isDrenched()) return;
        // Repeat hits EXTEND the burn: the new turns add onto whatever's left
        // (so re-applying Fire Aspect prolongs the fire), capped at the
        // configured max effect duration so it can't run away.
        var cfg = com.crackedgames.craftics.CrafticsMod.CONFIG;
        int maxDur = cfg != null ? cfg.maxCombatEffectDuration() : 10;
        burningTurns = Math.min(maxDur, burningTurns + turns);
        burningAmplifier = Math.min(MAX_EFFECT_AMPLIFIER, burningAmplifier + ampIncrease);
    }

    /**
     * Drench this entity, putting out any fire on it. Water beats fire: getting Soaked
     * extinguishes Burning outright rather than the two ticking side by side.
     *
     * <p>Every source of Soaked routes through here (coral weapons, bubble columns, water
     * tiles, creeper blasts, addon procs), so the douse applies to all of them.
     */
    public void stackSoaked(int turns, int ampIncrease) {
        soakedTurns = Math.max(soakedTurns, turns);
        soakedAmplifier = Math.min(MAX_EFFECT_AMPLIFIER, soakedAmplifier + ampIncrease);
        extinguish();
    }

    /**
     * Put out any fire on this entity, in both the combat model and the vanilla entity.
     * Clearing the fire TICKS as well as the flag matters: the burning visual is driven by
     * the tick timer, so dropping only the combat-model turns would leave a mob that takes no
     * fire damage but still looks like it's alight.
     */
    public void extinguish() {
        burningTurns = 0;
        burningAmplifier = 0;
        if (mobEntity != null) {
            mobEntity.setFireTicks(0);
            mobEntity.setOnFire(false);
        }
    }

    /** True if this entity is currently drenched, which prevents it catching fire. */
    public boolean isDrenched() {
        return soakedTurns > 0;
    }

    public void stackSlowness(int turns, int penaltyIncrease) {
        slownessTurns = Math.max(slownessTurns, turns);
        slownessPenalty = Math.min(MAX_EFFECT_AMPLIFIER, slownessPenalty + penaltyIncrease);
    }

    public void stackConfusion(int turns, int ampIncrease) {
        confusionTurns = Math.max(confusionTurns, turns);
        confusionAmplifier = Math.min(MAX_EFFECT_AMPLIFIER, confusionAmplifier + ampIncrease);
    }

    public void stackDefensePenalty(int turns, int penaltyIncrease) {
        defensePenaltyTurns = Math.max(defensePenaltyTurns, turns);
        defensePenalty = Math.min(MAX_EFFECT_AMPLIFIER, defensePenalty + penaltyIncrease);
    }

    // --- Custom (addon-registered) status effects ---------------------------
    // Keyed by effect id; value is [turnsRemaining, amplifier]. Ticked by
    // CombatManager alongside the built-in DOTs. See the Craftics Addon SDK.
    private final java.util.Map<String, int[]> customEffects = new java.util.LinkedHashMap<>();

    /**
     * Apply (or refresh) a custom status effect. Duration takes the longer value;
     * amplifier stacks up to the shared effect cap.
     */
    public void applyCustomEffect(String effectId, int turns, int amplifier) {
        int[] existing = customEffects.get(effectId);
        if (existing != null) {
            existing[0] = Math.max(existing[0], turns);
            existing[1] = Math.min(MAX_EFFECT_AMPLIFIER, existing[1] + amplifier);
        } else {
            customEffects.put(effectId, new int[]{turns, amplifier});
        }
    }

    /** Whether a custom effect with at least one turn remaining is active. */
    public boolean hasCustomEffect(String effectId) {
        int[] e = customEffects.get(effectId);
        return e != null && e[0] > 0;
    }

    /** Current amplifier of a custom effect ({@code 0} if not active). */
    public int getCustomEffectAmplifier(String effectId) {
        int[] e = customEffects.get(effectId);
        return e != null ? e[1] : 0;
    }

    /**
     * Live custom-effect state - effect id to {@code [turnsRemaining, amplifier]}.
     * CombatManager ticks, applies, and expires these each round.
     */
    public java.util.Map<String, int[]> getCustomEffects() { return customEffects; }

    // --- Positive buff application (instrument support) ---------------------

    /** Heal-over-time: {@code perTurnHp} HP at the start of each of {@code turns} ticks. */
    public void applyRegeneration(int turns, int amplifier) {
        this.regenTurns = Math.max(this.regenTurns, turns);
        this.regenPerTurn = Math.max(this.regenPerTurn, 2 * (amplifier + 1));
    }

    /** Temporary extra HP that soaks damage before real HP (see takeDamage, wired in Task 4). */
    public void applyAbsorption(int hp, int turns) {
        this.absorptionHp = Math.max(this.absorptionHp, hp);
        this.absorptionTurns = Math.max(this.absorptionTurns, turns);
    }

    /** Knockback immunity for {@code turns}. */
    public void applySlowFalling(int turns) {
        this.slowFallingTurns = Math.max(this.slowFallingTurns, turns);
    }

    /** Timed +attack buff (separate from the permanent setAttackBoost poke). */
    public void applyAttackBuff(int bonus, int turns) {
        this.attackBuffBonus = Math.max(this.attackBuffBonus, bonus);
        this.attackBuffTurns = Math.max(this.attackBuffTurns, turns);
    }

    /** Timed +movement buff. */
    public void applySpeedBuff(int bonus, int turns) {
        this.speedBuffBonus = Math.max(this.speedBuffBonus, bonus);
        this.speedBuffTurns = Math.max(this.speedBuffTurns, turns);
    }

    /** Timed damage reduction ({@code level} points). */
    public void applyResistance(int level, int turns) {
        this.resistanceLevel = Math.max(this.resistanceLevel, level);
        this.resistanceTurns = Math.max(this.resistanceTurns, turns);
    }

    public int getAbsorption() { return absorptionHp; }
    public boolean hasSlowFalling() { return slowFallingTurns > 0; }
    public int getAttackBuffBonus() { return attackBuffTurns > 0 ? attackBuffBonus : 0; }
    public int getSpeedBuffBonus() { return speedBuffTurns > 0 ? speedBuffBonus : 0; }
    public int getResistanceLevel() { return resistanceTurns > 0 ? resistanceLevel : 0; }

    /** Remove all positive buffs (used when combat ends; not a cleanse of debuffs). */
    public void clearBuffs() {
        regenTurns = regenPerTurn = 0;
        absorptionTurns = absorptionHp = 0;
        slowFallingTurns = 0;
        attackBuffTurns = attackBuffBonus = 0;
        speedBuffTurns = speedBuffBonus = 0;
        resistanceTurns = resistanceLevel = 0;
    }

    /** Remove all active debuffs (instrument cleanse). Does not touch positive buffs. */
    public void clearDebuffs() {
        poisonTurns = poisonAmplifier = 0;
        witherTurns = witherAmplifier = witherPeakTurns = 0;
        burningTurns = burningAmplifier = 0;
        soakedTurns = soakedAmplifier = 0;
        slownessTurns = slownessPenalty = 0;
        confusionTurns = confusionAmplifier = 0;
        bleedStacks = 0;
        blindedTurns = 0;
        attackPenalty = attackPenaltyTurns = 0;
        defensePenalty = defensePenaltyTurns = 0;
    }

    /**
     * Advance positive buffs by one turn: apply regen healing, then decrement all
     * buff timers. Called by CombatManager each round alongside the debuff ticks (wired in Task 4).
     */
    public void tickBuffs() {
        if (regenTurns > 0) {
            heal(regenPerTurn);
            if (--regenTurns == 0) regenPerTurn = 0;
        }
        if (absorptionTurns > 0 && --absorptionTurns == 0) absorptionHp = 0;
        if (slowFallingTurns > 0) slowFallingTurns--;
        if (attackBuffTurns > 0 && --attackBuffTurns == 0) attackBuffBonus = 0;
        if (speedBuffTurns > 0 && --speedBuffTurns == 0) speedBuffBonus = 0;
        if (resistanceTurns > 0 && --resistanceTurns == 0) resistanceLevel = 0;
    }

    /** Reduce the absorption pool by {@code amount}; clears the timer when depleted. */
    public void consumeAbsorption(int amount) {
        absorptionHp = Math.max(0, absorptionHp - amount);
        if (absorptionHp == 0) absorptionTurns = 0;
    }

    public int getEffectiveDefense() {
        return Math.max(0, defense - defensePenalty - permanentDefReduction);
    }

    public int takeDamage(int rawDamage) {
        return takeDamage(rawDamage, 0);
    }

    /** Bonus damage equal to a fraction of this entity's max HP, used by offensive special
     *  items (TNT, damage sherds, harming potions) so their flat damage keeps scaling into
     *  late-game HP pools. Bosses take a third of the percent so they aren't trivialized. */
    public int percentMaxHpDamage(double percent) {
        double pct = isBoss() ? percent / 3.0 : percent;
        return (int) Math.round(getMaxHp() * pct);
    }

    /** Offensive special-item hit: flat damage plus a percent of max HP. Returns damage dealt. */
    public int takeSpecialDamage(int flat, double pctMaxHp) {
        return takeDamage(flat + percentMaxHpDamage(pctMaxHp));
    }

    /**
     * Lightning damage against this target. A Soaked target takes 2x - this is THE
     * rule every lightning source (trident Channeling, Guster sherd, lightning rod,
     * and any future source) routes through, so the Soaked bonus is applied
     * consistently and can never be silently forgotten. Returns damage dealt.
     */
    public int takeLightningDamage(int rawDamage) {
        int dmg = isSoaked() ? rawDamage * 2 : rawDamage;
        return takeDamage(dmg);
    }

    /** Lightning variant of takeSpecialDamage: flat + percent-of-maxHP, doubled on Soaked. */
    public int takeSpecialLightningDamage(int flat, double pctMaxHp) {
        int base = flat + percentMaxHpDamage(pctMaxHp);
        return takeLightningDamage(base);
    }

    /**
     * Variant that adds an externally-computed defense bonus on top of the
     * entity's own defense for the reduction calculation. Used for tile-based
     * auras (e.g. allied banner zones) so per-tile buffs compose with the
     * entity's static defense without each caller duplicating the 5%/cap formula.
     */
    public int takeDamage(int rawDamage, int bonusDefense) {
        // Underground: no attack, AoE, splash, or chain in the arena reaches it. Checked here
        // rather than only at target selection because most damage sources reach for a
        // CombatEntity directly and never consult the occupant map. DOT ticks bypass this by
        // calling applyDirectDamage, which is intended: poison applied before the dig keeps
        // ticking underground.
        if (untargetable) return 0;
        // Timed resistance buff reduces incoming damage by its level (flat), before defense %.
        if (getResistanceLevel() > 0) {
            rawDamage = Math.max(0, rawDamage - getResistanceLevel());
        }
        // Absorption soaks damage before it reaches real HP.
        if (getAbsorption() > 0 && rawDamage > 0) {
            int soak = Math.min(getAbsorption(), rawDamage);
            consumeAbsorption(soak);
            rawDamage -= soak;
        }
        if (rawDamage <= 0) return 0;
        // Each DEF point = 5% reduction, capped at 60%
        int effectiveDef = getEffectiveDefense() + Math.max(0, bonusDefense);
        double reduction = Math.min(0.60, effectiveDef * 0.05);
        int actual = Math.max(1, (int)(rawDamage * (1.0 - reduction)));
        // Note: bleed is its own DOT and is classified as Special damage,
        // so it does NOT add bonus damage to direct hits anymore.
        // applyDirectDamage applies the Marked multiplier and returns the amount
        // actually subtracted, so the returned value (used for "hits for X") reflects it.
        int applied = applyDirectDamage(actual);
        if (alive && mobEntity != null) {
            com.crackedgames.craftics.combat.animation.MobAnimations.set(
                mobEntity,
                com.crackedgames.craftics.combat.animation.AnimState.HIT);
        }
        return applied;
    }

    /**
     * Apply unmitigated damage directly to current HP, bypassing defense and bleed.
     * Used by DOT effects (bleed/burn ticks) where the source already computed final damage.
     */
    public int applyDirectDamage(int amount) {
        if (amount <= 0) return 0;
        // Marked: amplify every damage source to this entity (2x, or 1.5x for bosses).
        // Centralised here so direct hits, AoE/ricochet, and DOT ticks all benefit.
        double markedMult = getMarkedDamageMultiplier();
        if (markedMult != 1.0) {
            amount = Math.max(1, (int) Math.round(amount * markedMult));
        }
        // Flat damage-taken multiplier (e.g. Void Walker clones take 2x). No-op at 1.0.
        if (damageTakenMultiplier != 1.0) {
            amount = Math.max(1, (int) Math.round(amount * damageTakenMultiplier));
        }
        int before = currentHp;
        currentHp = Math.max(0, currentHp - amount);
        damagedSinceLastTurn = true;
        if (currentHp == 0) {
            alive = false;
        }
        return before - currentHp;
    }

    /** Most-recent damager - used by per-mob loot drops to credit the killer. */
    public java.util.UUID getLastDamagerUuid() { return lastDamagerUuid; }
    public void setLastDamagerUuid(java.util.UUID uuid) { this.lastDamagerUuid = uuid; }

    public String getDisplayName() {
        if (bossDisplayName != null) return bossDisplayName;
        if (stackDisplayName != null) return stackDisplayName;
        String id = entityTypeId;
        int colon = id.indexOf(':');
        if (colon >= 0) id = id.substring(colon + 1);
        String[] words = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return sb.toString();
    }

    /** Largest default footprint dimension - for scalar consumers (spawn clearance). */
    public static int getDefaultSizeStatic(String entityTypeId) {
        int[] fp = getDefaultFootprint(entityTypeId);
        return Math.max(fp[0], fp[1]);
    }

    /** Returns true for mobs that must spawn and live on water tiles. */
    public static boolean isAquatic(String entityTypeId) {
        return switch (entityTypeId) {
            case "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish",
                 "minecraft:pufferfish", "minecraft:squid", "minecraft:glow_squid",
                 "minecraft:axolotl", "minecraft:dolphin" -> true;
            default -> false;
        };
    }

    /**
     * Default grid footprint per species as {@code {width, length}}, where
     * {@code length} is the mob's nose-to-tail axis and {@code width} is
     * shoulder-to-shoulder. 1 tile = 1 world block, so dimensions track the
     * mob's actual floor coverage (hitbox width, visual body length):
     * long quadrupeds get 1x2 or 2x3 instead of being crammed into one tile.
     * The canonical orientation puts length along Z; spawn code rotates the
     * pair via {@link #orientFootprint} so the long axis faces the target.
     */
    public static int[] getDefaultFootprint(String entityTypeId) {
        return switch (entityTypeId) {
            // Bulky squares: ~1.3-2.0 blocks wide, roughly as long as wide.
            case "minecraft:spider", "minecraft:hoglin", "minecraft:zoglin",
                 "minecraft:slime", "minecraft:magma_cube",
                 "minecraft:ghast", // spawned at 0.5 scale, so 4x4 hitbox -> 2x2
                 "minecraft:polar_bear", "minecraft:panda",
                 "minecraft:iron_golem", "minecraft:elder_guardian" -> new int[]{2, 2};
            // Long heavyweights: ~2 blocks wide, ~3 blocks nose to tail.
            case "minecraft:sniffer", "minecraft:camel", "minecraft:ravager" -> new int[]{2, 3};
            // Long but narrow: horses, llamas - one tile wide, two long.
            case "minecraft:horse", "minecraft:skeleton_horse", "minecraft:zombie_horse",
                 "minecraft:donkey", "minecraft:mule",
                 "minecraft:llama", "minecraft:trader_llama" -> new int[]{1, 2};
            // Giant zombie: 3.6-block hitbox width.
            case "minecraft:giant" -> new int[]{3, 3};
            default -> new int[]{1, 1};
        };
    }

    /**
     * Rotate a canonical {@code {width, length}} footprint so its length axis
     * runs along the dominant direction from {@code from} toward {@code toward},
     * returning {@code {sizeX, sizeZ}}. Long mobs advance on their target, so
     * pointing nose-to-tail at it at spawn keeps the model over its tiles for
     * most of the fight (footprints never rotate mid-combat). Ties and null
     * targets keep the canonical length-along-Z orientation.
     */
    public static int[] orientFootprint(int[] widthLength, GridPos from, GridPos toward) {
        int w = widthLength[0], l = widthLength[1];
        if (w == l || from == null || toward == null) return new int[]{w, l};
        int adx = Math.abs(toward.x() - from.x());
        int adz = Math.abs(toward.z() - from.z());
        return adx > adz ? new int[]{l, w} : new int[]{w, l};
    }

    /**
     * Mobs that should hover above the floor in combat. Flyers ignore the
     * water/low-ground Y dip and float a block higher when they end up on an
     * obstacle tile so the mob model sits on top of the obstacle instead of
     * inside it.
     */
    public boolean isFlying() {
        return isFlyingType(entityTypeId);
    }

    public static boolean isFlyingType(String entityTypeId) {
        if (entityTypeId == null) return false;
        return switch (entityTypeId) {
            case "minecraft:bee",
                 "minecraft:parrot",
                 "minecraft:allay",
                 "minecraft:bat",
                 "minecraft:vex",
                 "minecraft:phantom",
                 "minecraft:ghast",
                 "minecraft:blaze",
                 "minecraft:wither",
                 "minecraft:ender_dragon" -> true;
            default -> false;
        };
    }

    private static int getDefaultMoveSpeed(String entityTypeId) {
        return switch (entityTypeId) {
            case "minecraft:zombie" -> 1;
            case "minecraft:parrot" -> 3;
            case "minecraft:spider" -> 3;
            case "minecraft:ocelot" -> 4;
            case "minecraft:wolf" -> 3;
            case "minecraft:bee" -> 4;
            case "minecraft:vindicator" -> 3;
            case "minecraft:hoglin" -> 3;
            case "minecraft:wither_skeleton" -> 3;
            case "minecraft:ravager" -> 3;
            case "minecraft:skeleton", "minecraft:stray" -> 2;
            case "minecraft:pillager", "minecraft:piglin" -> 2;
            case "minecraft:blaze" -> 2;
            case "minecraft:drowned" -> 2;
            case "minecraft:phantom" -> 4;
            case "minecraft:enderman" -> 5;
            case "minecraft:camel" -> 4;
            case "minecraft:magma_cube" -> 3;
            case "minecraft:creeper" -> 2;
            case "minecraft:ghast" -> 1;
            case "minecraft:witch" -> 2;
            case "minecraft:shulker" -> 1;
            case "minecraft:ender_dragon" -> 4;
            case "minecraft:warden" -> 3;
            default -> 2;
        };
    }
}
