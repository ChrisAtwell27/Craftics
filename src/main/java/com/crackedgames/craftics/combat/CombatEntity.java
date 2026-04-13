package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;

public class CombatEntity {
    private final int entityId;
    private final String entityTypeId;
    private GridPos gridPos;
    private final int maxHp;
    private int currentHp;
    private final int attackPower;
    private final int defense;
    private final int range;
    private boolean alive;
    private MobEntity mobEntity;

    private boolean damagedSinceLastTurn = false;
    private int fuseTimer = 0;
    private boolean selfExploded = false; // creeper self-detonation = no drops
    private int size;
    private final int moveSpeed;
    private int speedBonus = 0;
    private int attackPenalty = 0;
    private int poisonTurns = 0;
    private int poisonAmplifier = 0; // 0 = level I, 1 = level II, etc
    private boolean enraged = false;
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

    public CombatEntity(int entityId, String entityTypeId, GridPos gridPos,
                        int maxHp, int attackPower, int defense, int range) {
        this(entityId, entityTypeId, gridPos, maxHp, attackPower, defense, range, -1);
    }

    public CombatEntity(int entityId, String entityTypeId, GridPos gridPos,
                        int maxHp, int attackPower, int defense, int range, int sizeOverride) {
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
        this.moveSpeed = getDefaultMoveSpeed(entityTypeId);
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
        int base = moveSpeed + speedBonus;
        if (soakedTurns > 0) base -= 1;
        if (slownessTurns > 0) base -= slownessPenalty;
        float mult = com.crackedgames.craftics.CrafticsMod.CONFIG.enemyMoveSpeedMultiplier();
        return Math.max(1, (int)(base * mult));
    }
    public void setSpeedBonus(int bonus) { this.speedBonus = bonus; }
    public int getSpeedBonus() { return speedBonus; }
    public boolean isEnraged() { return enraged; }
    public void setEnraged(boolean e) { this.enraged = e; }
    public boolean isDrownedWithTrident() { return drownedHasTrident; }
    public void setDrownedWithTrident(boolean hasTrident) { this.drownedHasTrident = hasTrident; }
    public int getAttackPenalty() { return attackPenalty; }
    public void setAttackPenalty(int p) { this.attackPenalty = p; }
    public int getPoisonTurns() { return poisonTurns; }
    public void setPoisonTurns(int t) { this.poisonTurns = t; }
    public int getPoisonAmplifier() { return poisonAmplifier; }
    public void setPoisonAmplifier(int a) { this.poisonAmplifier = a; }

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

    private boolean mounted = false;
    public boolean isMounted() { return mounted; }
    public void setMounted(boolean m) { this.mounted = m; }

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

    public void stackPoison(int turns, int ampIncrease) {
        poisonTurns = Math.max(poisonTurns, turns);
        poisonAmplifier = Math.min(MAX_EFFECT_AMPLIFIER, poisonAmplifier + ampIncrease);
    }

    public void stackBurning(int turns, int dmgIncrease) {
        burningTurns = Math.max(burningTurns, turns);
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

    public int getEffectiveDefense() {
        return Math.max(0, defense - defensePenalty - permanentDefReduction);
    }

    public int takeDamage(int rawDamage) {
        // Mirror images (Void Walker clones) vanish on any hit. No real HP loss
        // is reported so the attack still feels like a "wasted swing" on an illusion.
        if (mirrorClone) {
            currentHp = 0;
            alive = false;
            damagedSinceLastTurn = true;
            return 0;
        }
        // Each DEF point = 5% reduction, capped at 60%
        int effectiveDef = getEffectiveDefense();
        double reduction = Math.min(0.60, effectiveDef * 0.05);
        int actual = Math.max(1, (int)(rawDamage * (1.0 - reduction)));
        // Note: bleed is its own DOT and is classified as Special damage,
        // so it does NOT add bonus damage to direct hits anymore.
        applyDirectDamage(actual);
        return actual;
    }

    /**
     * Apply unmitigated damage directly to current HP, bypassing defense and bleed.
     * Used by DOT effects (bleed/burn ticks) where the source already computed final damage.
     */
    public void applyDirectDamage(int amount) {
        if (amount <= 0) return;
        if (mirrorClone) {
            currentHp = 0;
            alive = false;
            damagedSinceLastTurn = true;
            return;
        }
        currentHp = Math.max(0, currentHp - amount);
        damagedSinceLastTurn = true;
        if (currentHp == 0) {
            alive = false;
        }
    }

    public String getDisplayName() {
        if (bossDisplayName != null) return bossDisplayName;
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
                 "minecraft:ghast" -> 2;
            default -> 1;
        };
    }

    private static int getDefaultMoveSpeed(String entityTypeId) {
        return switch (entityTypeId) {
            case "minecraft:zombie" -> 1;
            case "minecraft:parrot" -> 3;
            case "minecraft:spider" -> 3;
            case "minecraft:ocelot" -> 4;
            case "minecraft:wolf" -> 3;
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
