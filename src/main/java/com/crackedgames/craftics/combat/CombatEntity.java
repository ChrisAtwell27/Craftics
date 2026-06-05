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
    private int size;
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
    private boolean enraged = false;
    /**
     * When set, the entity is fully immobilized this turn — used by the Creaking
     * gaze mechanic. Bypasses the {@link #getMoveSpeed} clamp (which floors at 1)
     * so a frozen Creaking truly can't move, and AI implementations check this
     * flag at the top of {@code decideAction} to skip attacks too.
     */
    private boolean frozen = false;
    private boolean drownedHasTrident = false;
    private int defensePenalty = 0;
    private int defensePenaltyTurns = 0;
    private int burningTurns = 0;
    private int burningDamage = 0;
    private int soakedTurns = 0;
    private int soakedAmplifier = 0;
    private int confusionTurns = 0;
    private int confusionAmplifier = 0;
    private int slownessTurns = 0;
    private int slownessPenalty = 0;
    private int bleedStacks = 0; // +1 bonus damage per stack when attacked
    private int permanentDefReduction = 0; // from Breach, never expires
    private int attackBoost = 0;
    private int defenseBoost = 0;
    private int rangeOverride = -1;
    private boolean backgroundBoss = false; // targetable but doesn't block movement
    private int visualProjectileEntityId = -1;
    private boolean deathProcessed = false; // guards against double death handling
    private boolean hasSplit = false; // guards against double-splitting (Molten King)
    private EnemyAI aiInstance = null; // per-entity AI override (for split copies with own state)
    private int linkedHeartId = -1; // creaking → heart link (heart death kills creaking)
    private int linkedCreakingId = -1; // heart → creaking back-link

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
        this.size = sizeOverride > 0 ? sizeOverride : getDefaultSize(entityTypeId);
        this.moveSpeed = speedOverride > 0 ? speedOverride : getDefaultMoveSpeed(entityTypeId);
    }

    public int getEntityId() { return entityId; }
    public String getEntityTypeId() { return entityTypeId; }
    public GridPos getGridPos() { return gridPos; }
    public void setGridPos(GridPos pos) { this.gridPos = pos; }
    public int getMaxHp() { return maxHp; }
    public int getCurrentHp() { return currentHp; }
    public void heal(int amount) { currentHp = Math.min(getEffectiveMaxHp(), currentHp + amount); }
    public int getAttackPower() { return Math.max(0, attackPower + attackBoost - attackPenalty); }
    public int getDefense() { return defense + defenseBoost; }
    public int getRange() { return rangeOverride >= 0 ? rangeOverride : range; }
    public int getAttackBoost() { return attackBoost; }
    public void setAttackBoost(int boost) { this.attackBoost = boost; }
    public void setDefenseBoost(int boost) { this.defenseBoost = boost; }
    public void setRangeOverride(int r) { this.rangeOverride = r; }
    public boolean isAlive() { return alive; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public boolean isBackgroundBoss() { return backgroundBoss; }
    public void setBackgroundBoss(boolean bg) { this.backgroundBoss = bg; }
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
    public EnemyAI getAiInstance() { return aiInstance; }
    public void setAiInstance(EnemyAI ai) { this.aiInstance = ai; }

    /** Minimum manhattan distance from a point to any tile this entity occupies. */
    public int minDistanceTo(GridPos from) {
        return minDistanceFromSizedEntity(gridPos, size, from);
    }

    /** Returns the occupied tile of this entity closest to the given point. */
    public GridPos nearestTileTo(GridPos from) {
        if (size <= 1) return gridPos;
        GridPos best = gridPos;
        int bestDist = Integer.MAX_VALUE;
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                GridPos tile = new GridPos(gridPos.x() + dx, gridPos.z() + dz);
                int dist = Math.abs(from.x() - tile.x()) + Math.abs(from.z() - tile.z());
                if (dist < bestDist) { bestDist = dist; best = tile; }
            }
        }
        return best;
    }

    /** Minimum manhattan distance from any tile of a sized entity at origin to a target point. */
    public static int minDistanceFromSizedEntity(GridPos origin, int entitySize, GridPos target) {
        if (entitySize <= 1) return origin.manhattanDistance(target);
        int min = Integer.MAX_VALUE;
        for (int dx = 0; dx < entitySize; dx++) {
            for (int dz = 0; dz < entitySize; dz++) {
                int dist = Math.abs(target.x() - (origin.x() + dx)) + Math.abs(target.z() - (origin.z() + dz));
                if (dist < min) min = dist;
            }
        }
        return min;
    }
    public int getMoveSpeed() {
        // Frozen entities (Creaking under gaze) bypass the +1 floor entirely —
        // they truly can't move this turn.
        if (frozen) return 0;
        int base = moveSpeed + speedBonus;
        if (soakedTurns > 0) base -= 1;
        if (slownessTurns > 0) base -= slownessPenalty;
        float mult = com.crackedgames.craftics.CrafticsMod.CONFIG.enemyMoveSpeedMultiplier();
        // Allies always keep a usable move budget — never below 2 tiles/turn,
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
    public void setWitherAmplifier(int a) { this.witherAmplifier = a; }

    /** Bonus damage that scales DOTs (poison/wither) with the target's max HP.
     *  +1 per 20 max HP, with a floor of 1. Encourages DOT use against tougher
     *  enemies and bosses without making them devastating against trash mobs. */
    public int getMaxHpDotBonus() {
        return Math.max(1, maxHp / 20);
    }

    /** Damage one tick of poison would deal at this moment. Returns 0 if not
     *  poisoned. Formula: {@code 1 + amplifier + maxHpBonus}. */
    public int getPoisonTickDamage() {
        if (poisonTurns <= 0) return 0;
        return 1 + poisonAmplifier + getMaxHpDotBonus();
    }

    /** Damage one tick of wither would deal at this moment. Returns 0 if not
     *  withered. Formula: {@code remainingTurns + 1 + amplifier + maxHpBonus}.
     *  Damage tapers as the wither wears off — early ticks are heaviest. */
    public int getWitherTickDamage() {
        if (witherTurns <= 0) return 0;
        return witherTurns + 1 + witherAmplifier + getMaxHpDotBonus();
    }

    public MobEntity getMobEntity() { return mobEntity; }
    public void setMobEntity(MobEntity mob) { this.mobEntity = mob; }

    public boolean wasDamagedSinceLastTurn() { return damagedSinceLastTurn; }
    public void setDamagedSinceLastTurn(boolean v) { this.damagedSinceLastTurn = v; }

    public int getFuseTimer() { return fuseTimer; }
    public void setFuseTimer(int timer) { this.fuseTimer = timer; }

    public boolean isSelfExploded() { return selfExploded; }
    public void setSelfExploded(boolean v) { this.selfExploded = v; }

    private boolean stunned = false;
    public boolean isStunned() { return stunned; }
    public void setStunned(boolean s) { this.stunned = s; }

    private boolean boss = false;
    public boolean isBoss() { return boss; }
    public void setBoss(boolean b) { this.boss = b; }

    /** If true, knockback stops before hazard tiles (void, deep water, lava) instead of landing on them. */
    private boolean hazardImmune = false;
    public boolean isHazardImmune() { return hazardImmune; }
    public void setHazardImmune(boolean v) { this.hazardImmune = v; }

    /** Spider ceiling mechanic — true when the spider is hanging from the ceiling (off-grid). */
    private boolean onCeiling = false;
    public boolean isOnCeiling() { return onCeiling; }
    public void setOnCeiling(boolean v) { this.onCeiling = v; }

    /** If true, boss entities can pathfind through this entity (e.g., egg sacs). */
    private boolean passableForBoss = false;
    public boolean isPassableForBoss() { return passableForBoss; }
    public void setPassableForBoss(boolean v) { this.passableForBoss = v; }

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
     * Void Walker mirror image flag. If true, any incoming damage instantly kills this
     * entity regardless of the damage amount, and zero HP loss is reported. The clone
     * looks identical to the parent boss (same name, stats) until it is hit.
     */
    private boolean mirrorClone = false;
    public boolean isMirrorClone() { return mirrorClone; }
    public void setMirrorClone(boolean v) { this.mirrorClone = v; }

    /** Entity ID of the boss this clone mirrors. -1 when not a clone. */
    private int cloneOfBossId = -1;
    public int getCloneOfBossId() { return cloneOfBossId; }
    public void setCloneOfBossId(int id) { this.cloneOfBossId = id; }

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
     * hub after combat — they exist solely for this fight.
     */
    private boolean temporaryAlly = false;
    public boolean isTemporaryAlly() { return temporaryAlly; }
    public void setTemporaryAlly(boolean v) { this.temporaryAlly = v; }

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

    public int getDefensePenalty() { return defensePenalty; }
    public void setDefensePenalty(int p) { this.defensePenalty = p; }
    public int getDefensePenaltyTurns() { return defensePenaltyTurns; }
    public void setDefensePenaltyTurns(int t) { this.defensePenaltyTurns = t; }
    public int getBurningTurns() { return burningTurns; }
    public void setBurningTurns(int t) { this.burningTurns = t; }
    public int getBurningDamage() { return burningDamage; }
    public void setBurningDamage(int d) { this.burningDamage = d; }
    public int getSoakedTurns() { return soakedTurns; }
    public void setSoakedTurns(int t) { this.soakedTurns = t; }
    public int getSoakedAmplifier() { return soakedAmplifier; }
    public void setSoakedAmplifier(int a) { this.soakedAmplifier = a; }
    public int getConfusionTurns() { return confusionTurns; }
    public void setConfusionTurns(int t) { this.confusionTurns = t; }
    public int getConfusionAmplifier() { return confusionAmplifier; }
    public void setConfusionAmplifier(int a) { this.confusionAmplifier = a; }

    // ── Marked status ──
    // A marked entity takes extra damage from all attacks (2x normally, 1.5x for
    // bosses). Reusable for any entity (player/ally/enemy); currently applied only by
    // the player's spyglass. markedTurns counts down once per round.
    private int markedTurns = 0;
    public boolean isMarked() { return markedTurns > 0; }
    public int getMarkedTurns() { return markedTurns; }
    public void setMarkedTurns(int t) { this.markedTurns = t; }
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
    public int getSlownessTurns() { return slownessTurns; }
    public void setSlownessTurns(int t) { this.slownessTurns = t; }
    public int getSlownessPenalty() { return slownessPenalty; }
    public void setSlownessPenalty(int p) { this.slownessPenalty = p; }
    public int getBleedStacks() { return bleedStacks; }
    public void setBleedStacks(int s) { this.bleedStacks = s; }
    public void stackBleed(int stacks) { this.bleedStacks = Math.min(MAX_EFFECT_AMPLIFIER, this.bleedStacks + stacks); }

    /**
     * Damage dealt by a single bleed tick. Scales quadratically with stack count
     * (triangular: 1 stack = 1, 2 = 3, 3 = 6, 4 = 10...) so stacking bleed is meaningful.
     */
    public static int computeBleedTickDamage(int stacks) {
        return Math.max(0, stacks);
    }
    public int getPermanentDefReduction() { return permanentDefReduction; }
    public void addPermanentDefReduction(int amount) { this.permanentDefReduction += amount; }

    // Duration refreshes to longer value, intensity stacks up to cap
    private static final int MAX_EFFECT_AMPLIFIER = 999;

    public void stackWither(int turns, int ampIncrease) {
        witherTurns = Math.max(witherTurns, turns);
        witherAmplifier = Math.min(MAX_EFFECT_AMPLIFIER, witherAmplifier + ampIncrease);
    }

    public void stackPoison(int turns, int ampIncrease) {
        poisonTurns = Math.max(poisonTurns, turns);
        poisonAmplifier = Math.min(MAX_EFFECT_AMPLIFIER, poisonAmplifier + ampIncrease);
    }

    public void stackBurning(int turns, int dmgIncrease) {
        // Repeat hits EXTEND the burn: the new turns add onto whatever's left
        // (so re-applying Fire Aspect prolongs the fire), capped at the
        // configured max effect duration so it can't run away.
        int maxDur = com.crackedgames.craftics.CrafticsMod.CONFIG.maxCombatEffectDuration();
        burningTurns = Math.min(maxDur, burningTurns + turns);
        burningDamage = Math.min(MAX_EFFECT_AMPLIFIER + 1, burningDamage + dmgIncrease);
    }

    public void stackSoaked(int turns, int ampIncrease) {
        soakedTurns = Math.max(soakedTurns, turns);
        soakedAmplifier = Math.min(MAX_EFFECT_AMPLIFIER, soakedAmplifier + ampIncrease);
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
     * Live custom-effect state — effect id to {@code [turnsRemaining, amplifier]}.
     * CombatManager ticks, applies, and expires these each round.
     */
    public java.util.Map<String, int[]> getCustomEffects() { return customEffects; }

    public int getEffectiveDefense() {
        return Math.max(0, defense - defensePenalty - permanentDefReduction);
    }

    public int takeDamage(int rawDamage) {
        return takeDamage(rawDamage, 0);
    }

    /**
     * Variant that adds an externally-computed defense bonus on top of the
     * entity's own defense for the reduction calculation. Used for tile-based
     * auras (e.g. allied banner zones) so per-tile buffs compose with the
     * entity's static defense without each caller duplicating the 5%/cap formula.
     */
    public int takeDamage(int rawDamage, int bonusDefense) {
        // Mirror images (Void Walker clones) vanish on any hit. No real HP loss
        // is reported so the attack still feels like a "wasted swing" on an illusion.
        if (mirrorClone) {
            currentHp = 0;
            alive = false;
            damagedSinceLastTurn = true;
            return 0;
        }
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
        if (mirrorClone) {
            currentHp = 0;
            alive = false;
            damagedSinceLastTurn = true;
            return amount;
        }
        int before = currentHp;
        currentHp = Math.max(0, currentHp - amount);
        damagedSinceLastTurn = true;
        if (currentHp == 0) {
            alive = false;
        }
        return before - currentHp;
    }

    /** Most-recent damager — used by per-mob loot drops to credit the killer. */
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

    public static int getDefaultSizeStatic(String entityTypeId) {
        return getDefaultSize(entityTypeId);
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

    private static int getDefaultSize(String entityTypeId) {
        return switch (entityTypeId) {
            case "minecraft:spider", "minecraft:hoglin",
                 "minecraft:slime", "minecraft:magma_cube",
                 "minecraft:ghast",
                 "minecraft:polar_bear", "minecraft:camel" -> 2;
            default -> 1;
        };
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
