package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import net.minecraft.entity.mob.MobEntity;

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

    // AI state
    private boolean damagedSinceLastTurn = false;
    private int fuseTimer = 0;
    private final int size;
    private final int moveSpeed;
    private int speedBonus = 0;
    private int attackPenalty = 0;
    private int poisonTurns = 0; // turns of poison remaining
    private int poisonAmplifier = 0; // 0 = level I (1 dmg/turn), 1 = level II (2 dmg/turn)
    private boolean enraged = false; // for vindicator/warden rage mechanic
    private int defensePenalty = 0; // temporary defense reduction (from Scrape sherd)
    private int defensePenaltyTurns = 0;
    private int burningTurns = 0; // fire damage over time
    private int burningDamage = 0; // damage per turn while burning
    private int attackBoost = 0; // permanent attack boost (from pet stats override)
    private int defenseBoost = 0; // permanent defense boost (from pet stats override)
    private int rangeOverride = -1; // if set, overrides base range

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
    public void heal(int amount) { currentHp = Math.min(maxHp, currentHp + amount); }
    public int getAttackPower() { return Math.max(0, attackPower + attackBoost - attackPenalty); }
    public int getDefense() { return defense + defenseBoost; }
    public int getRange() { return rangeOverride >= 0 ? rangeOverride : range; }
    public void setAttackBoost(int boost) { this.attackBoost = boost; }
    public void setDefenseBoost(int boost) { this.defenseBoost = boost; }
    public void setRangeOverride(int r) { this.rangeOverride = r; }
    public boolean isAlive() { return alive; }
    public int getSize() { return size; }

    /** Minimum manhattan distance from a point to any tile this entity occupies. */
    public int minDistanceTo(GridPos from) {
        return minDistanceFromSizedEntity(gridPos, size, from);
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
        float mult = com.crackedgames.craftics.CrafticsMod.CONFIG.enemyMoveSpeedMultiplier();
        return Math.max(1, (int)(base * mult));
    }
    public void setSpeedBonus(int bonus) { this.speedBonus = bonus; }
    public int getSpeedBonus() { return speedBonus; }
    public boolean isEnraged() { return enraged; }
    public void setEnraged(boolean e) { this.enraged = e; }
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

    private boolean stunned = false;
    public boolean isStunned() { return stunned; }
    public void setStunned(boolean s) { this.stunned = s; }

    private boolean boss = false;
    public boolean isBoss() { return boss; }
    public void setBoss(boolean b) { this.boss = b; }

    /** Spider ceiling mechanic — true when the spider is hanging from the ceiling (off-grid). */
    private boolean onCeiling = false;
    public boolean isOnCeiling() { return onCeiling; }
    public void setOnCeiling(boolean v) { this.onCeiling = v; }

    private String bossDisplayName = null;
    public void setBossDisplayName(String name) { this.bossDisplayName = name; }

    private String aiOverrideKey = null;
    /** When set, AIRegistry uses this key instead of entityTypeId for AI lookup. */
    public String getAiOverrideKey() { return aiOverrideKey; }
    public void setAiOverrideKey(String key) { this.aiOverrideKey = key; }
    /** Returns the key to use for AIRegistry lookup — aiOverrideKey if set, otherwise entityTypeId. */
    public String getAiKey() { return aiOverrideKey != null ? aiOverrideKey : entityTypeId; }

    private int maxHpReduction = 0; // wither effect — permanent max HP reduction
    public int getMaxHpReduction() { return maxHpReduction; }
    public void addMaxHpReduction(int amount) { this.maxHpReduction += amount; }
    public int getEffectiveMaxHp() { return Math.max(1, maxHp - maxHpReduction); }

    private boolean ally = false;
    public boolean isAlly() { return ally; }
    public void setAlly(boolean a) { this.ally = a; }

    private boolean mounted = false;
    public boolean isMounted() { return mounted; }
    public void setMounted(boolean m) { this.mounted = m; }

    public int getDefensePenalty() { return defensePenalty; }
    public void setDefensePenalty(int p) { this.defensePenalty = p; }
    public int getDefensePenaltyTurns() { return defensePenaltyTurns; }
    public void setDefensePenaltyTurns(int t) { this.defensePenaltyTurns = t; }
    public int getBurningTurns() { return burningTurns; }
    public void setBurningTurns(int t) { this.burningTurns = t; }
    public int getBurningDamage() { return burningDamage; }
    public void setBurningDamage(int d) { this.burningDamage = d; }

    public int getEffectiveDefense() {
        return Math.max(0, defense - defensePenalty);
    }

    public int takeDamage(int rawDamage) {
        // Percentage-based defense: each point = 5% reduction, capped at 60%
        int effectiveDef = getEffectiveDefense();
        double reduction = Math.min(0.60, effectiveDef * 0.05);
        int actual = Math.max(1, (int)(rawDamage * (1.0 - reduction)));
        currentHp = Math.max(0, currentHp - actual);
        damagedSinceLastTurn = true;
        if (currentHp == 0) {
            alive = false;
        }
        return actual;
    }

    public String getDisplayName() {
        if (bossDisplayName != null) return bossDisplayName;
        String id = entityTypeId;
        int colon = id.indexOf(':');
        if (colon >= 0) id = id.substring(colon + 1);
        return id.substring(0, 1).toUpperCase() + id.substring(1);
    }

    public static int getDefaultSizeStatic(String entityTypeId) {
        return getDefaultSize(entityTypeId);
    }

    private static int getDefaultSize(String entityTypeId) {
        return switch (entityTypeId) {
            case "minecraft:spider", "minecraft:hoglin" -> 2;
            default -> 1;
        };
    }

    private static int getDefaultMoveSpeed(String entityTypeId) {
        return switch (entityTypeId) {
            // Fast predators
            case "minecraft:spider" -> 3;         // pounce + web
            case "minecraft:ocelot" -> 4;          // hit-and-run specialist
            case "minecraft:wolf" -> 3;            // pack hunter

            // Rush melee
            case "minecraft:vindicator" -> 3;      // axe berserker
            case "minecraft:hoglin" -> 3;           // charging beast
            case "minecraft:wither_skeleton" -> 3;  // relentless pursuer

            // Ranged (move + shoot)
            case "minecraft:skeleton", "minecraft:stray" -> 2;
            case "minecraft:pillager", "minecraft:piglin" -> 2;
            case "minecraft:blaze" -> 2;            // hover + fireball
            case "minecraft:drowned" -> 2;          // trident + melee

            // Special movement
            case "minecraft:phantom" -> 4;          // swoop range
            case "minecraft:enderman" -> 5;         // teleport range
            case "minecraft:camel" -> 4;            // mounted cavalry
            case "minecraft:magma_cube" -> 3;       // bouncy
            case "minecraft:creeper" -> 2;          // sneaks up, then booms

            // Long range / stationary
            case "minecraft:ghast" -> 1;            // slow but range 5-6
            case "minecraft:witch" -> 2;            // potion lobber
            case "minecraft:shulker" -> 1;          // turret

            // Bosses
            case "minecraft:ender_dragon" -> 4;     // devastating swoop
            case "minecraft:warden" -> 3;           // slow but terrifying

            // Default — everything should move at least 2
            default -> 2;
        };
    }
}
