package com.crackedgames.craftics.client;

public class CombatState {
    private static boolean inCombat = false;

    // Camera settings for isometric view
    private static float combatPitch = 55.0f;  // Looking down
    private static float combatYaw = 225.0f;   // SW-facing isometric angle
    private static float combatCameraDistance = 15.0f; // Distance from focus point
    private static final float MIN_CAMERA_DISTANCE = 8.0f;
    private static final float MAX_CAMERA_DISTANCE = 22.0f;

    // Camera pan offset (added to arena center)
    private static double cameraPanX = 0;
    private static double cameraPanZ = 0;
    private static final double MAX_PAN = 10.0;

    // Arena origin and dimensions
    private static int arenaOriginX = 0;
    private static int arenaOriginY = 100;
    private static int arenaOriginZ = 0;
    private static int arenaWidth = 0;
    private static int arenaHeight = 0;

    // Arena center — camera focuses here (with pan offset applied)
    private static double arenaCenterX = 0;
    private static double arenaCenterY = 100;
    private static double arenaCenterZ = 0;
    private static double arenaBaseCenterX = 0;
    private static double arenaBaseCenterZ = 0;

    public static boolean isInCombat() {
        return inCombat;
    }

    public static void setInCombat(boolean combat) {
        inCombat = combat;
    }

    public static void toggleCombat() {
        inCombat = !inCombat;
    }

    public static void enterCombat(int originX, int originY, int originZ, int width, int height) {
        inCombat = true;
        clearTileSets();
        arenaOriginX = originX;
        arenaOriginY = originY;
        arenaOriginZ = originZ;
        arenaWidth = width;
        arenaHeight = height;
        arenaBaseCenterX = originX + width / 2.0;
        arenaBaseCenterZ = originZ + height / 2.0;
        arenaCenterX = arenaBaseCenterX;
        arenaCenterY = originY + 1.0;
        arenaCenterZ = arenaBaseCenterZ;
        // Reset camera to defaults for each new combat
        combatCameraDistance = 15.0f;
        cameraPanX = 0;
        cameraPanZ = 0;
        // Initialize focus position to arena center
        focusCurrentX = arenaBaseCenterX;
        focusCurrentZ = arenaBaseCenterZ;
        focusZoomCurrent = 15.0f;
        focusZoomTarget = 15.0f;
        hasFocus = false;
        // Reset combat stats
        resetCombatStats();
    }

    /** Zoom camera in/out. Called from scroll wheel. */
    public static void zoom(float amount) {
        combatCameraDistance = Math.max(MIN_CAMERA_DISTANCE,
            Math.min(MAX_CAMERA_DISTANCE, combatCameraDistance - amount));
    }

    /** Pan camera by screen-relative offset. Called from middle mouse drag. */
    public static void pan(double dx, double dz) {
        cameraPanX = Math.max(-MAX_PAN, Math.min(MAX_PAN, cameraPanX + dx));
        cameraPanZ = Math.max(-MAX_PAN, Math.min(MAX_PAN, cameraPanZ + dz));
        // Don't snap — let tickCameraFocus lerp to the new target
        arenaCenterX = arenaBaseCenterX + cameraPanX;
        arenaCenterZ = arenaBaseCenterZ + cameraPanZ;
        // Release any entity focus when user manually pans
        releaseFocus();
    }

    /** Reset camera pan to arena center. */
    public static void resetPan() {
        cameraPanX = 0;
        cameraPanZ = 0;
        arenaCenterX = arenaBaseCenterX;
        arenaCenterZ = arenaBaseCenterZ;
        releaseFocus();
    }

    public static float getCombatPitch() {
        return combatPitch;
    }

    public static float getCombatYaw() {
        return combatYaw;
    }

    public static void setCombatYaw(float yaw) {
        combatYaw = yaw;
    }

    public static float getCombatCameraDistance() {
        return combatCameraDistance;
    }

    public static double getArenaCenterX() {
        return arenaCenterX;
    }

    public static double getArenaCenterY() {
        return arenaCenterY;
    }

    public static double getArenaCenterZ() {
        return arenaCenterZ;
    }

    public static int getArenaOriginX() { return arenaOriginX; }
    public static int getArenaOriginY() { return arenaOriginY; }
    public static int getArenaOriginZ() { return arenaOriginZ; }
    public static int getArenaWidth() { return arenaWidth; }
    public static int getArenaHeight() { return arenaHeight; }

    // --- Camera focus/zoom system ---
    private static double focusTargetX = 0, focusTargetZ = 0;
    private static double focusCurrentX = 0, focusCurrentZ = 0;
    private static float focusZoomTarget = 15.0f;
    private static float focusZoomCurrent = 15.0f;
    private static boolean hasFocus = false;
    private static int focusTimer = 0;
    private static final float FOCUS_ZOOM_DISTANCE = 10.0f; // closer zoom when focused
    private static final float FOCUS_LERP_SPEED = 0.35f; // smooth interpolation for focus animations
    private static final float PAN_LERP_SPEED = 0.85f;  // near-instant for pan/zoom to keep raycast aligned

    /** Focus camera on a world position (e.g. an entity taking action). */
    public static void focusOn(double worldX, double worldZ) {
        focusTargetX = worldX;
        focusTargetZ = worldZ;
        focusZoomTarget = FOCUS_ZOOM_DISTANCE;
        hasFocus = true;
        focusTimer = 40; // auto-release after 2 seconds
    }

    /** Focus on a grid tile position. */
    public static void focusOnTile(int gridX, int gridZ) {
        focusOn(arenaOriginX + gridX + 0.5, arenaOriginZ + gridZ + 0.5);
    }

    /** Release focus back to arena overview. */
    public static void releaseFocus() {
        hasFocus = false;
        focusZoomTarget = combatCameraDistance;
    }

    /** Tick camera focus lerp (call each client tick). */
    public static void tickCameraFocus() {
        if (!isInCombat()) return;

        if (hasFocus) {
            focusTimer--;
            if (focusTimer <= 0) {
                releaseFocus();
            }
        }

        // Lerp toward target or back to arena center
        double targetX = hasFocus ? focusTargetX : arenaBaseCenterX + cameraPanX;
        double targetZ = hasFocus ? focusTargetZ : arenaBaseCenterZ + cameraPanZ;
        float targetZoom = hasFocus ? focusZoomTarget : combatCameraDistance;

        float speed = hasFocus ? FOCUS_LERP_SPEED : PAN_LERP_SPEED;
        focusCurrentX += (targetX - focusCurrentX) * speed;
        focusCurrentZ += (targetZ - focusCurrentZ) * speed;
        focusZoomCurrent += (targetZoom - focusZoomCurrent) * speed;
    }

    /** Get the camera focus X (used by CameraLockMixin instead of arenaCenterX). */
    public static double getCameraFocusX() {
        return hasFocus || Math.abs(focusCurrentX - (arenaBaseCenterX + cameraPanX)) > 0.01
            ? focusCurrentX : arenaCenterX;
    }

    /** Get the camera focus Z. */
    public static double getCameraFocusZ() {
        return hasFocus || Math.abs(focusCurrentZ - (arenaBaseCenterZ + cameraPanZ)) > 0.01
            ? focusCurrentZ : arenaCenterZ;
    }

    /** Get the smooth zoom distance. */
    public static float getCameraFocusZoom() {
        return focusZoomCurrent;
    }

    // --- Synced combat data (from CombatSyncPayload) ---
    private static int phase = 0;
    private static int apRemaining = 0;
    private static int movePointsRemaining = 0;
    private static int playerHp = 20;
    private static int playerMaxHp = 20;
    private static int turnNumber = 1;
    private static int maxAp = 3;
    private static int maxSpeed = 3;
    private static java.util.Map<Integer, int[]> enemyHpMap = new java.util.LinkedHashMap<>();
    private static java.util.Map<Integer, String> enemyTypeMap = new java.util.LinkedHashMap<>();
    private static java.util.Map<Integer, int[]> allyHpMap = new java.util.LinkedHashMap<>();
    private static java.util.Map<Integer, String> allyTypeMap = new java.util.LinkedHashMap<>();

    // Active player effects string (e.g. "Poison(2)|Burning(1)")
    private static String playerEffects = "";
    private static int killStreak = 0;

    // Party member HP list (empty in solo play)
    public record PartyMemberHp(String uuid, String name, int hp, int maxHp, boolean dead) {}
    private static java.util.List<PartyMemberHp> partyHpList = new java.util.ArrayList<>();

    // Hovered enemy inspection
    private static int hoveredEnemyId = -1;
    public static int getHoveredEnemyId() { return hoveredEnemyId; }
    public static void setHoveredEnemyId(int id) { hoveredEnemyId = id; }

    // Combat stats tracking
    private static int totalDamageDealt = 0;
    private static int totalDamageTaken = 0;
    private static int enemiesKilled = 0;
    private static int turnsPlayed = 0;
    private static int lastKnownEnemyCount = 0;

    public static String getPlayerEffects() { return playerEffects; }
    public static int getKillStreak() { return killStreak; }
    public static int getTotalDamageDealt() { return totalDamageDealt; }
    public static int getTotalDamageTaken() { return totalDamageTaken; }
    public static int getEnemiesKilled() { return enemiesKilled; }
    public static int getTurnsPlayed() { return turnsPlayed; }

    public static void resetCombatStats() {
        totalDamageDealt = 0;
        totalDamageTaken = 0;
        enemiesKilled = 0;
        turnsPlayed = 0;
        lastKnownEnemyCount = 0;
        killStreak = 0;
        playerEffects = "";
        partyHpList.clear();
    }

    public static void updateFromSync(int phase, int ap, int movePoints,
                                       int playerHp, int playerMaxHp, int turnNumber,
                                       int maxAp, int maxSpeed,
                                       int[] enemyData, String enemyTypeIds,
                                       String playerEffects, int killStreak,
                                       String partyHpData) {
        // Save old HP before overwriting so we can detect damage/heal
        int oldHp = CombatState.playerHp;

        CombatState.phase = phase;
        CombatState.apRemaining = ap;
        CombatState.movePointsRemaining = movePoints;
        CombatState.playerHp = playerHp;
        CombatState.playerMaxHp = playerMaxHp;
        CombatState.turnNumber = turnNumber;

        // Detect player damage/heal for visual effects
        if (playerHp < oldHp && oldHp > 0) {
            int dmg = oldHp - playerHp;
            CombatVisualEffects.spawnDamageNumber(dmg, true);
            CombatVisualEffects.flashDamage();
            // Shake harder when player takes damage (more alarming)
            float shakeAmount = Math.min(1.0f, dmg / 6.0f) * 0.8f + 0.2f;
            CombatVisualEffects.triggerShake(shakeAmount);
        } else if (playerHp > oldHp && oldHp > 0) {
            CombatVisualEffects.flashHeal();
        }

        CombatState.maxAp = maxAp;
        CombatState.maxSpeed = maxSpeed;
        CombatState.playerEffects = playerEffects;
        CombatState.killStreak = killStreak;

        // Track combat stats
        if (playerHp < oldHp && oldHp > 0) {
            totalDamageTaken += (oldHp - playerHp);
        }
        if (turnNumber > turnsPlayed) {
            turnsPlayed = turnNumber;
        }

        // Track enemy kills: count how many enemies disappeared
        int newEnemyCount = enemyData.length / 3;
        if (lastKnownEnemyCount > 0 && newEnemyCount < lastKnownEnemyCount) {
            enemiesKilled += (lastKnownEnemyCount - newEnemyCount);
        }
        lastKnownEnemyCount = newEnemyCount;

        enemyHpMap.clear();
        enemyTypeMap.clear();
        allyHpMap.clear();
        allyTypeMap.clear();
        String[] typeIds = enemyTypeIds.isEmpty() ? new String[0] : enemyTypeIds.split("\\|");
        for (int i = 0; i + 2 < enemyData.length; i += 3) {
            int idx = i / 3;
            boolean isAlly = idx < typeIds.length && typeIds[idx].contains(";ally");
            if (isAlly) {
                allyHpMap.put(enemyData[i], new int[]{enemyData[i + 1], enemyData[i + 2]});
                if (idx < typeIds.length) allyTypeMap.put(enemyData[i], typeIds[idx]);
            } else {
                enemyHpMap.put(enemyData[i], new int[]{enemyData[i + 1], enemyData[i + 2]});
                if (idx < typeIds.length) enemyTypeMap.put(enemyData[i], typeIds[idx]);
            }
            if (idx < typeIds.length) {
                // Unlock bestiary entry when we see this mob type
                com.crackedgames.craftics.client.guide.GuideBookData.unlockMob(typeIds[idx]);
            }
        }

        // Parse party HP data — put self at top of the list
        partyHpList.clear();
        if (partyHpData != null && !partyHpData.isEmpty()) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            String myUuid = mc.getSession().getUuidOrNull() != null
                ? mc.getSession().getUuidOrNull().toString() : "";
            PartyMemberHp self = null;
            java.util.List<PartyMemberHp> others = new java.util.ArrayList<>();
            for (String entry : partyHpData.split("\\|")) {
                String[] parts = entry.split(",");
                if (parts.length < 5) continue;
                String uuid = parts[0];
                String name = parts[1];
                int hp = Integer.parseInt(parts[2]);
                int mHp = Integer.parseInt(parts[3]);
                boolean dead = "1".equals(parts[4]);
                PartyMemberHp member = new PartyMemberHp(uuid, name, hp, mHp, dead);
                if (uuid.equals(myUuid)) {
                    self = member;
                } else {
                    others.add(member);
                }
            }
            if (self != null) partyHpList.add(self);
            partyHpList.addAll(others);
        }
    }

    public static java.util.List<PartyMemberHp> getPartyHpList() { return partyHpList; }

    public static int getPhase() { return phase; }
    public static int getApRemaining() { return apRemaining; }
    public static int getMovePointsRemaining() { return movePointsRemaining; }
    public static int getPlayerHp() { return playerHp; }
    public static int getPlayerMaxHp() { return playerMaxHp; }
    public static int getTurnNumber() { return turnNumber; }
    public static int getMaxAp() { return maxAp; }
    public static int getMaxSpeed() { return maxSpeed; }
    public static java.util.Map<Integer, int[]> getEnemyHpMap() { return enemyHpMap; }
    public static java.util.Map<Integer, String> getEnemyTypeMap() { return enemyTypeMap; }
    public static java.util.Map<Integer, int[]> getAllyHpMap() { return allyHpMap; }
    public static java.util.Map<Integer, String> getAllyTypeMap() { return allyTypeMap; }

    public static boolean isPlayerTurn() { return phase == 0; } // CombatPhase.PLAYER_TURN ordinal
    public static boolean isEnemyTurn() { return phase == 1; }  // CombatPhase.ENEMY_TURN ordinal

    // Emerald currency (persisted on server, synced to client for HUD display)
    private static int emeralds = 0;
    public static int getEmeralds() { return emeralds; }
    public static void setEmeralds(int amount) { emeralds = amount; }

    // --- Trader state ---
    private static boolean traderActive = false;
    public static boolean isTraderActive() { return traderActive; }
    public static void setTraderActive(boolean active) { traderActive = active; }

    // --- Player progression stats (synced from server for inventory display) ---
    private static int playerLevel = 1;
    private static int unspentPoints = 0;
    private static int[] statPoints = new int[8]; // one per PlayerProgression.Stat ordinal
    private static int[] affinityPoints = new int[8]; // one per PlayerProgression.Affinity ordinal (SLASHING,CLEAVING,BLUNT,RANGED,WATER,SPECIAL,PHYSICAL,PET)

    public static void updateStats(int level, int unspent, String statData, String affinityData) {
        playerLevel = level;
        unspentPoints = unspent;
        String[] parts = statData.split(":");
        for (int i = 0; i < statPoints.length && i < parts.length; i++) {
            try { statPoints[i] = Integer.parseInt(parts[i]); }
            catch (NumberFormatException e) { statPoints[i] = 0; }
        }
        // Parse affinity data
        if (affinityData != null && !affinityData.isEmpty()) {
            String[] affParts = affinityData.split(":");
            for (int i = 0; i < affinityPoints.length && i < affParts.length; i++) {
                try { affinityPoints[i] = Integer.parseInt(affParts[i]); }
                catch (NumberFormatException e) { affinityPoints[i] = 0; }
            }
        }
    }

    public static int getPlayerLevel() { return playerLevel; }
    public static int getUnspentPoints() { return unspentPoints; }
    public static int getStatPoints(int ordinal) {
        return ordinal >= 0 && ordinal < statPoints.length ? statPoints[ordinal] : 0;
    }

    public static int getAffinityPoints(int ordinal) {
        return ordinal >= 0 && ordinal < affinityPoints.length ? affinityPoints[ordinal] : 0;
    }

    // === Client-side tile set cache (from TileSetPayload) ===

    private static final java.util.Set<com.crackedgames.craftics.core.GridPos> cachedMoveTiles = new java.util.HashSet<>();
    private static final java.util.Set<com.crackedgames.craftics.core.GridPos> cachedAttackTiles = new java.util.HashSet<>();
    private static final java.util.Set<com.crackedgames.craftics.core.GridPos> cachedDangerTiles = new java.util.HashSet<>();
    private static final java.util.Set<com.crackedgames.craftics.core.GridPos> cachedWarningTiles = new java.util.HashSet<>();
    private static final java.util.Map<com.crackedgames.craftics.core.GridPos, Integer> enemyGridMap = new java.util.HashMap<>();
    private static final java.util.Map<com.crackedgames.craftics.core.GridPos, String> enemyGridTypeMap = new java.util.HashMap<>();

    // Teammate hover positions
    private static final java.util.Map<java.util.UUID, com.crackedgames.craftics.core.GridPos> teammateHovers = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, String> teammateNames = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> teammateHoverTimestamps = new java.util.concurrent.ConcurrentHashMap<>();

    // Client-local hover (used directly by renderer, no server round-trip)
    private static com.crackedgames.craftics.core.GridPos hoveredTile = null;

    public static java.util.Set<com.crackedgames.craftics.core.GridPos> getMoveTiles() { return cachedMoveTiles; }
    public static java.util.Set<com.crackedgames.craftics.core.GridPos> getAttackTiles() { return cachedAttackTiles; }
    public static java.util.Set<com.crackedgames.craftics.core.GridPos> getDangerTiles() { return cachedDangerTiles; }
    public static java.util.Set<com.crackedgames.craftics.core.GridPos> getWarningTiles() { return cachedWarningTiles; }
    public static java.util.Map<com.crackedgames.craftics.core.GridPos, Integer> getEnemyGridMap() { return enemyGridMap; }
    public static java.util.Map<com.crackedgames.craftics.core.GridPos, String> getEnemyGridTypeMap() { return enemyGridTypeMap; }
    public static com.crackedgames.craftics.core.GridPos getHoveredTile() { return hoveredTile; }
    public static void setHoveredTile(com.crackedgames.craftics.core.GridPos tile) { hoveredTile = tile; }

    public static java.util.Map<java.util.UUID, com.crackedgames.craftics.core.GridPos> getTeammateHovers() { return teammateHovers; }
    public static java.util.Map<java.util.UUID, String> getTeammateNames() { return teammateNames; }
    public static java.util.Map<java.util.UUID, Long> getTeammateHoverTimestamps() { return teammateHoverTimestamps; }

    public static void updateTileSets(int[] moveTiles, int[] attackTiles, int[] dangerTiles,
                                       int[] warningTiles, int[] enemyMapData, String enemyTypes) {
        cachedMoveTiles.clear();
        cachedAttackTiles.clear();
        cachedDangerTiles.clear();
        cachedWarningTiles.clear();
        enemyGridMap.clear();
        enemyGridTypeMap.clear();

        for (int i = 0; i + 1 < moveTiles.length; i += 2)
            cachedMoveTiles.add(new com.crackedgames.craftics.core.GridPos(moveTiles[i], moveTiles[i + 1]));
        for (int i = 0; i + 1 < attackTiles.length; i += 2)
            cachedAttackTiles.add(new com.crackedgames.craftics.core.GridPos(attackTiles[i], attackTiles[i + 1]));
        for (int i = 0; i + 1 < dangerTiles.length; i += 2)
            cachedDangerTiles.add(new com.crackedgames.craftics.core.GridPos(dangerTiles[i], dangerTiles[i + 1]));
        for (int i = 0; i + 1 < warningTiles.length; i += 2)
            cachedWarningTiles.add(new com.crackedgames.craftics.core.GridPos(warningTiles[i], warningTiles[i + 1]));

        String[] types = enemyTypes.isEmpty() ? new String[0] : enemyTypes.split("\\|");
        for (int i = 0; i + 2 < enemyMapData.length; i += 3) {
            var pos = new com.crackedgames.craftics.core.GridPos(enemyMapData[i], enemyMapData[i + 1]);
            enemyGridMap.put(pos, enemyMapData[i + 2]);
            int typeIdx = i / 3;
            if (typeIdx < types.length) enemyGridTypeMap.put(pos, types[typeIdx]);
        }
    }

    public static void updateTeammateHover(java.util.UUID uuid, String name, int gridX, int gridZ) {
        teammateHovers.put(uuid, new com.crackedgames.craftics.core.GridPos(gridX, gridZ));
        teammateNames.put(uuid, name);
        teammateHoverTimestamps.put(uuid, System.currentTimeMillis());
    }

    public static void clearTileSets() {
        cachedMoveTiles.clear();
        cachedAttackTiles.clear();
        cachedDangerTiles.clear();
        cachedWarningTiles.clear();
        enemyGridMap.clear();
        enemyGridTypeMap.clear();
        teammateHovers.clear();
        teammateNames.clear();
        teammateHoverTimestamps.clear();
        hoveredTile = null;
    }
}
