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
    private boolean selfExploded = false; // creeper exploded on its own (no drops)
    private int size;
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
    private int soakedTurns = 0; // turns of soaked remaining
    private int soakedAmplifier = 0; // 0 = level I, 1 = level II, etc.
    private int confusionTurns = 0; // turns of confusion remaining
    private int confusionAmplifier = 0; // 0 = level I, 1 = level II, etc.
    private int slownessTurns = 0; // turns of slowness remaining
    private int slownessPenalty = 0; // speed reduction while slowed
    private int attackBoost = 0; // permanent attack boost (from pet stats override)
    private int defenseBoost = 0; // permanent defense boost (from pet stats override)
    private int rangeOverride = -1; // if set, overrides base range
    private boolean backgroundBoss = false; // true = occupies tiles for targeting but doesn't block movement
    private int visualProjectileEntityId = -1; // MC entity ID of the visual fireball/skull entity

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
        // Soaked reduces movement by 1
        if (soakedTurns > 0) base -= 1;
        // Slowness reduces movement
        if (slownessTurns > 0) base -= slownessPenalty;
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

    public boolean isSelfExploded() { return selfExploded; }
    public void setSelfExploded(boolean v) { this.selfExploded = v; }

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

    /** If true, boss entities can pathfind through this entity (e.g., egg sacs). */
    private boolean passableForBoss = false;
    public boolean isPassableForBoss() { return passableForBoss; }
    public void setPassableForBoss(boolean v) { this.passableForBoss = v; }

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

    // Projectile state — for boss-spawned projectile entities (fireballs, wither skulls)
    private boolean projectile = false;
    private int projectileDirX = 0;
    private int projectileDirZ = 0;
    private String projectileType = null; // "ghast_fireball" or "wither_skull"
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

            // Basic movement
            case "minecraft:zombie" -> 1;

            // Fast Passive
            case "minecraft:parrot" -> 3;       // agile swimmer
            
            // Fast predators
            case "minecraft:spider" -> 3;         // pounce + web
            case "minecraft:ocelot" -> 4;          // hit-and-run specialist
            case "minecraft:wolf" -> 3;            // pack hunter

            // Rush melee
            case "minecraft:vindicator" -> 3;      // axe berserker
            case "minecraft:hoglin" -> 3;           // charging beast
            case "minecraft:wither_skeleton" -> 3;  // relentless pursuer
            case "minecraft:ravager" -> 3;          // mounted melee, charges + stomps

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
