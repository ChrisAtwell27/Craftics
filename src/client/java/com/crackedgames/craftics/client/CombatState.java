package com.crackedgames.craftics.client;

public class CombatState {
    private static boolean inCombat = false;

    // Non-combat event cinematic flag (mirrors inCombat; gates camera + movement
    // lock during dialogue / event cutscenes). Driven by EnterEventCinematicPayload /
    // ExitEventCinematicPayload, read by CameraLockMixin and MovementDisableMixin.
    private static boolean cinematicActive = false;
    public static boolean isCinematicActive() { return cinematicActive; }
    public static void setCinematicActive(boolean v) { cinematicActive = v; }

    // Explicit "in a merchant scene" flag, toggled by the server's SceneStatePayload
    // at SceneController.build/leave. Kept separate from cinematicActive because that
    // flag is also raised by non-combat EVENTS (shrine/trader/vault) - only this one
    // means a click-to-walk scene is live, so it gates scene tile-clicks + Leave button.
    private static boolean inScene = false;
    public static boolean isInScene() { return inScene; }
    public static void setInScene(boolean v) { inScene = v; }

    /**
     * Seed the arena origin + grid size for a merchant SCENE (not combat). A scene
     * never calls {@link #enterCombat}, so without this {@link TileRaycast} reads
     * all-zero bounds and every floor click resolves to null. Sets exactly the same
     * arena fields {@code enterCombat} does (origin, width/height, base/center) so the
     * raycast and the tile tooltip line up, but does NOT set {@code inCombat} and leaves
     * the polygon mask null (null mask -> {@code isInPolygon} returns true for the whole
     * rectangle). Pass all-zero / call {@link #clearSceneBounds} when the scene ends.
     *
     * <p>{@code originY} must be the floor-BLOCK Y; {@code TileRaycast} intersects the
     * floor plane at {@code originY + 1} (the walkable top surface).
     */
    public static void setSceneBounds(int originX, int originY, int originZ, int width, int height) {
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
        // Full-rectangle scene floor: no polygon mask, so isInPolygon() is always true.
        setPolygonMask(null, width, height);
    }

    /** Clear the scene grid bounds (call when the scene state goes inactive). */
    public static void clearSceneBounds() {
        setSceneBounds(0, 0, 0, 0, 0);
    }

    /**
     * Snap the camera focus point onto the local player at the start of an event
     * cinematic, so {@link #tickCameraFocus()} begins following from the player's
     * position instead of sweeping in from the stale arena center / origin.
     */
    public static void seedCinematicFocusOnPlayer() {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return;
        focusCurrentX = mc.player.getX();
        focusCurrentZ = mc.player.getZ();
        focusZoomCurrent = FOCUS_ZOOM_DISTANCE;
        focusZoomTarget = FOCUS_ZOOM_DISTANCE;
        arenaCenterY = mc.player.getY() + 1.0;
    }

    // Camera settings for isometric view
    private static float combatPitch = 55.0f;  // Looking down
    private static float combatYaw = 225.0f;   // SW-facing isometric angle
    private static float combatCameraDistance = 15.0f; // Distance from focus point
    private static final float MIN_CAMERA_DISTANCE = 8.0f;
    private static final float MAX_CAMERA_DISTANCE = 22.0f;
    private static final float MIN_CAMERA_PITCH = 25.0f;
    private static final float MAX_CAMERA_PITCH = 85.0f;

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

    // Arena center - camera focuses here (with pan offset applied)
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
        boolean wasInCombat = inCombat;
        inCombat = true;
        inScene = false; // FIX 4: clear stale scene flag so Leave button never renders over combat
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
        if (!wasInCombat) {
            // Only reset camera on first combat entry, not between levels
            combatCameraDistance = 15.0f;
            cameraPanX = 0;
            cameraPanZ = 0;
        } else {
            // Transitioning between levels - keep camera orientation, just reset pan
            cameraPanX = 0;
            cameraPanZ = 0;
        }
        // Initialize focus position to arena center
        focusCurrentX = arenaBaseCenterX;
        focusCurrentZ = arenaBaseCenterZ;
        focusZoomCurrent = combatCameraDistance;
        focusZoomTarget = combatCameraDistance;
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
        // Don't snap - let tickCameraFocus lerp to the new target
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

    public static void setCombatPitch(float pitch) {
        combatPitch = Math.max(MIN_CAMERA_PITCH, Math.min(MAX_CAMERA_PITCH, pitch));
    }

    /** Adjust camera orbit by the given yaw/pitch deltas (degrees). Used by right-click drag. */
    public static void adjustCameraAngles(float dYaw, float dPitch) {
        combatYaw = (combatYaw + dYaw) % 360.0f;
        if (combatYaw < 0) combatYaw += 360.0f;
        setCombatPitch(combatPitch + dPitch);
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
    /** True while the camera is smoothly easing back to the user's saved pan
     *  after an auto-focus expired. Cleared when the camera settles or the user
     *  takes manual pan control. While true, {@code tickCameraFocus} uses the
     *  slow focus lerp speed instead of the near-instant pan speed so the
     *  auto-return is as smooth as the auto-focus-out was. */
    private static boolean returningFromFocus = false;
    private static final float FOCUS_ZOOM_DISTANCE = 10.0f; // closer zoom when focused
    private static final float FOCUS_LERP_SPEED = 0.35f; // smooth interpolation for focus animations
    private static final float PAN_LERP_SPEED = 0.85f;  // near-instant for pan/zoom to keep raycast aligned
    private static final double RETURN_SETTLE_EPSILON = 0.05; // distance at which return is considered "done"

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
        // Auto-focus only while allies/enemies take their turn. During the player's
        // own turn, combat events (the player's own moves/attacks) would otherwise
        // yank the camera off the framing the player set. Phase is global, so in a
        // party, spectators won't follow a teammate during that teammate's
        // PLAYER_TURN - an acceptable v1 tradeoff. The damage numbers / hit-flash /
        // shake in the event handlers still run; only this focus call no-ops.
        if (!isInCombat() || !isEnemyTurn()) return;
        focusOn(arenaOriginX + gridX + 0.5, arenaOriginZ + gridZ + 0.5);
    }

    /**
     * Release focus back to the user's saved pan. Called from user-action paths
     * ({@link #pan}, {@link #resetPan}) - no smooth return lerp needed because the
     * user just took manual control.
     */
    public static void releaseFocus() {
        hasFocus = false;
        returningFromFocus = false;
        focusZoomTarget = combatCameraDistance;
    }

    /**
     * End an auto-focus (e.g. attack/move event timer expired) and smoothly lerp
     * the camera back to the user's manually-set pan/zoom instead of snapping.
     * The lerp target in {@link #tickCameraFocus} naturally switches to
     * {@code arenaBaseCenterX + cameraPanX} once {@code hasFocus} is false, and
     * the {@code returningFromFocus} flag keeps the lerp speed slow (focus speed
     * instead of near-instant pan speed) until the camera settles.
     * <p>
     * Previously this method overwrote {@code cameraPanX/Z} with the focus target,
     * which meant each attack permanently pulled the camera off the user's set view.
     * Over an enemy turn, the player's carefully-framed angle would drift anywhere
     * the last focus event happened to land.
     */
    private static void endFocusWithReturn() {
        hasFocus = false;
        returningFromFocus = true;
        focusZoomTarget = combatCameraDistance;
    }

    /** Tick camera focus lerp (call each client tick). */
    public static void tickCameraFocus() {
        if (!isInCombat() && !isCinematicActive()) return;

        if (isCinematicActive() && !isInCombat()) {
            // During the event walk-up, smoothly follow the local player so the
            // camera tracks them up to the trader instead of sitting on a stale
            // arena center (which tickCameraFocus used to early-return on).
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player != null) {
                double tx = mc.player.getX();
                double tz = mc.player.getZ();
                focusCurrentX += (tx - focusCurrentX) * FOCUS_LERP_SPEED;
                focusCurrentZ += (tz - focusCurrentZ) * FOCUS_LERP_SPEED;
                focusZoomCurrent += (FOCUS_ZOOM_DISTANCE - focusZoomCurrent) * FOCUS_LERP_SPEED;
                // Track the player's Y too so the camera height matches the walk-up
                // (CameraLockMixin reads getArenaCenterY() for the camera's Y origin).
                arenaCenterY += (mc.player.getY() + 1.0 - arenaCenterY) * FOCUS_LERP_SPEED;
            }
            return;
        }

        if (hasFocus) {
            focusTimer--;
            if (focusTimer <= 0) {
                endFocusWithReturn();
            }
        }

        // Lerp toward target or back to the user's saved pan
        double targetX = hasFocus ? focusTargetX : arenaBaseCenterX + cameraPanX;
        double targetZ = hasFocus ? focusTargetZ : arenaBaseCenterZ + cameraPanZ;
        float targetZoom = hasFocus ? focusZoomTarget : combatCameraDistance;

        // While easing back from an expired focus, keep using the slow focus lerp
        // so the return is as smooth as the auto-focus-out was. Once we're close
        // enough to the user's saved pan, switch back to the fast pan lerp so
        // subsequent manual pans feel near-instant again.
        float speed;
        if (hasFocus) {
            speed = FOCUS_LERP_SPEED;
        } else if (returningFromFocus) {
            double dx = targetX - focusCurrentX;
            double dz = targetZ - focusCurrentZ;
            if (dx * dx + dz * dz < RETURN_SETTLE_EPSILON * RETURN_SETTLE_EPSILON) {
                returningFromFocus = false;
                speed = PAN_LERP_SPEED;
            } else {
                speed = FOCUS_LERP_SPEED;
            }
        } else {
            speed = PAN_LERP_SPEED;
        }
        focusCurrentX += (targetX - focusCurrentX) * speed;
        focusCurrentZ += (targetZ - focusCurrentZ) * speed;
        focusZoomCurrent += (targetZoom - focusZoomCurrent) * speed;
    }

    /** Get the camera focus X (used by CameraLockMixin instead of arenaCenterX). */
    public static double getCameraFocusX() {
        // During a (non-combat) cinematic, focusCurrentX tracks the live player
        // position; arena center is stale, so always use the smoothed focus point.
        if (isCinematicActive() && !isInCombat()) return focusCurrentX;
        return hasFocus || Math.abs(focusCurrentX - (arenaBaseCenterX + cameraPanX)) > 0.01
            ? focusCurrentX : arenaCenterX;
    }

    /** Get the camera focus Z. */
    public static double getCameraFocusZ() {
        if (isCinematicActive() && !isInCombat()) return focusCurrentZ;
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
    /** Local client's player HP - distinct from {@link #playerHp} which carries
     *  the current turn-holder's HP in party combat. Used as the basis for the
     *  damage/heal visual effects so they don't retrigger on turn rotation. */
    private static int localPlayerHp = 20;
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

    /** Per-player combat readout for the hover inspect panel, keyed by uuid string. */
    public record PlayerStats(String uuid, String name, int hp, int maxHp, boolean dead,
                              int atk, int ac, int ap, int speed) {}
    private static final java.util.Map<String, PlayerStats> playerStatsMap = new java.util.LinkedHashMap<>();
    public static java.util.Map<String, PlayerStats> getPlayerStatsMap() { return playerStatsMap; }
    public static PlayerStats getPlayerStats(String uuid) { return playerStatsMap.get(uuid); }

    /** UUID of the player whose tile is under the cursor, or null when none. */
    private static String hoveredPlayerUuid = null;
    public static String getHoveredPlayerUuid() { return hoveredPlayerUuid; }
    public static void setHoveredPlayerUuid(String uuid) { hoveredPlayerUuid = uuid; }

    // Turn order list (empty in solo play)
    public record TurnOrderEntry(String uuid, String name, boolean isCurrent) {}
    private static java.util.List<TurnOrderEntry> turnOrderList = new java.util.ArrayList<>();
    public static java.util.List<TurnOrderEntry> getTurnOrderList() { return turnOrderList; }

    /**
     * Entity ids of every synced unit (enemies AND allies) in server sync order,
     * which mirrors the server's `enemies` list - the exact order units act in
     * during the enemy phase. Drives the HUD act-order strip.
     */
    private static final java.util.List<Integer> unitActOrder = new java.util.ArrayList<>();
    public static java.util.List<Integer> getUnitActOrder() { return unitActOrder; }

    // The unit currently taking its action, learned from the attack-animation
    // AND movement events (the server announces movement-only turns via
    // EVENT_MOVED, so walks, teleports and ceiling hops light up too).
    // Highlighted in the act-order strip while fresh.
    private static int actingEnemyId = -1;
    private static long actingEnemyMs = 0;

    public static void noteActingEnemy(int entityId) {
        actingEnemyId = entityId;
        actingEnemyMs = System.currentTimeMillis();
    }

    /** Entity id of the unit acting right now, or -1 when unknown/stale. */
    public static int getActingEnemyId() {
        if (actingEnemyId == -1) return -1;
        if (!isEnemyTurn()) return -1;
        // 4s window: the move note fires at walk START, and a long path's lerp
        // can outlive a short window - the attack note (if any) refreshes it.
        if (System.currentTimeMillis() - actingEnemyMs > 4000) return -1;
        return actingEnemyId;
    }

    /**
     * Whether the local client's player is the current turn-holder. Solo (empty
     * turn order) is always true. Used to keep per-turn-player HUD (AP / move
     * pips) and animations from showing the active player's state on a
     * non-acting teammate's screen.
     */
    public static boolean isLocalPlayersTurn() {
        if (turnOrderList.isEmpty()) return true;
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        String myUuid = mc.getSession().getUuidOrNull() != null
            ? mc.getSession().getUuidOrNull().toString() : "";
        for (TurnOrderEntry entry : turnOrderList) {
            if (entry.isCurrent() && entry.uuid().equals(myUuid)) return true;
        }
        return false;
    }

    // Hovered enemy inspection
    private static int hoveredEnemyId = -1;
    public static int getHoveredEnemyId() { return hoveredEnemyId; }
    public static void setHoveredEnemyId(int id) { hoveredEnemyId = id; }

    /**
     * Compute movement tiles for the currently hovered enemy. Parses the {@code mv=} tag
     * from the enemy type metadata and dispatches to the matching pattern in
     * {@link ClientGridHelper#getMovePatternTiles}, so each mob's hover preview reflects its
     * actual AI (rook dash for vindicators, blink for endermites, teleport for endermen, etc.).
     */
    public static java.util.Set<com.crackedgames.craftics.core.GridPos> getHoveredEnemyMoveTiles() {
        if (hoveredEnemyId == -1) return new java.util.HashSet<>();
        return computeMovePreviewTiles(hoveredEnemyId);
    }

    /**
     * Movement-pattern preview for any unit by entity id - the per-style reach
     * the hover preview shows, also unioned per enemy by the threat overlay.
     */
    private static java.util.Set<com.crackedgames.craftics.core.GridPos> computeMovePreviewTiles(int entityId) {
        java.util.Set<com.crackedgames.craftics.core.GridPos> result = new java.util.HashSet<>();

        // Find the unit's grid position (reverse lookup from enemyGridMap)
        com.crackedgames.craftics.core.GridPos enemyPos = null;
        for (var entry : enemyGridMap.entrySet()) {
            if (entry.getValue() == entityId) {
                enemyPos = entry.getKey();
                break;
            }
        }
        if (enemyPos == null) return result;

        // Parse speed and movement-style tags from enemy type metadata
        String typeData = enemyTypeMap.getOrDefault(entityId, "");
        int speed = 1;
        com.crackedgames.craftics.combat.MoveStyle style = com.crackedgames.craftics.combat.MoveStyle.WALK;
        for (String part : typeData.split(";")) {
            if (part.startsWith("spd=")) {
                try { speed = Integer.parseInt(part.substring(4)); } catch (NumberFormatException ignored) {}
            } else if (part.startsWith("mv=")) {
                style = com.crackedgames.craftics.combat.MoveStyle.fromTag(part.substring(3));
            }
        }

        // For pounce-style mobs (spider) the destination is determined by the player's position
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        com.crackedgames.craftics.core.GridPos playerPos =
            ClientGridHelper.getPlayerGridPos(client);

        return ClientGridHelper.getMovePatternTiles(client, enemyPos, speed, style, playerPos);
    }

    // ─── Threat overlay (danger zone) ─────────────────────────────────────
    // Hotkey-toggled union of every enemy's movement reach plus the attack
    // range it could strike from after moving - the classic tactics-game
    // "danger zone". Computed client-side from the same per-style preview
    // machinery the hover preview uses; cached briefly since it walks a BFS
    // per enemy.

    private static boolean threatOverlayEnabled = false;
    private static long threatCacheMs = 0;
    private static java.util.Set<com.crackedgames.craftics.core.GridPos> threatCache = java.util.Set.of();

    public static boolean isThreatOverlayEnabled() { return threatOverlayEnabled; }

    /** Toggle the threat overlay; returns the new state. */
    public static boolean toggleThreatOverlay() {
        threatOverlayEnabled = !threatOverlayEnabled;
        threatCacheMs = 0;
        return threatOverlayEnabled;
    }

    /**
     * Tiles any enemy could reach AND attack this turn (movement reach expanded
     * by each enemy's attack range - Chebyshev ring for melee, Manhattan diamond
     * for ranged, matching the server's reach conventions). Empty when the
     * overlay is off. Refreshed at most every 400 ms.
     */
    public static java.util.Set<com.crackedgames.craftics.core.GridPos> getThreatTiles() {
        if (!threatOverlayEnabled) return java.util.Set.of();
        long now = System.currentTimeMillis();
        if (now - threatCacheMs < 400 && threatCache != null) return threatCache;
        threatCacheMs = now;

        java.util.Set<com.crackedgames.craftics.core.GridPos> threat = new java.util.HashSet<>();
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        com.crackedgames.craftics.core.GridPos playerPos = ClientGridHelper.getPlayerGridPos(client);

        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (var entry : enemyGridMap.entrySet()) {
            int id = entry.getValue();
            if (!seen.add(id)) continue;          // multi-tile mobs map several tiles to one id
            if (allyHpMap.containsKey(id)) continue; // allies don't threaten the player
            if (!enemyHpMap.containsKey(id)) continue;

            int range = 1;
            String typeData = enemyTypeMap.getOrDefault(id, "");
            for (String part : typeData.split(";")) {
                if (part.startsWith("range=")) {
                    try { range = Integer.parseInt(part.substring(6)); } catch (NumberFormatException ignored) {}
                }
            }

            java.util.Set<com.crackedgames.craftics.core.GridPos> reach =
                new java.util.HashSet<>(computeMovePreviewTiles(id));
            reach.add(entry.getKey()); // attacking without moving
            for (com.crackedgames.craftics.core.GridPos from : reach) {
                threat.add(from);
                if (range <= 1) {
                    // Melee: the 8 surrounding tiles (Chebyshev 1).
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            threat.add(new com.crackedgames.craftics.core.GridPos(from.x() + dx, from.z() + dz));
                        }
                    }
                } else {
                    // Ranged: Manhattan diamond.
                    for (int dx = -range; dx <= range; dx++) {
                        for (int dz = -range; dz <= range; dz++) {
                            if (Math.abs(dx) + Math.abs(dz) > range) continue;
                            threat.add(new com.crackedgames.craftics.core.GridPos(from.x() + dx, from.z() + dz));
                        }
                    }
                }
            }
        }

        // Clip to the arena and drop the player's own tile (it's where you stand,
        // not useful information - matches the server's danger-tile convention).
        java.util.Set<com.crackedgames.craftics.core.GridPos> clipped = new java.util.HashSet<>();
        for (com.crackedgames.craftics.core.GridPos t : threat) {
            if (t.x() < 0 || t.x() >= arenaWidth || t.z() < 0 || t.z() >= arenaHeight) continue;
            if (!isInPolygon(t.x(), t.z())) continue;
            if (t.equals(playerPos)) continue;
            clipped.add(t);
        }
        threatCache = clipped;
        return clipped;
    }

    // Combat stats tracking
    private static int totalDamageDealt = 0;
    private static int totalDamageTaken = 0;
    private static int enemiesKilled = 0;
    private static int turnsPlayed = 0;
    private static int lastKnownEnemyCount = 0;

    public static String getPlayerEffects() { return playerEffects; }

    /**
     * Returns the current level (stack count) of the named combat effect on the player,
     * or 0 if the effect is not active. Level I = 1, Level II = 2, etc. Parses the
     * {@code CombatEffects.getDisplayString()} format: {@code "Name(turns) | Other II (3t)"}.
     */
    public static int getCombatEffectLevel(String effectName) {
        if (playerEffects == null || playerEffects.isEmpty()) return 0;
        for (String part : playerEffects.split(" \\| ")) {
            int paren = part.indexOf('(');
            String head = paren >= 0 ? part.substring(0, paren).trim() : part.trim();
            // Split name from Roman numeral level suffix (if any).
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\s+([IVX]+)$").matcher(head);
            int level = 1;
            String name = head;
            if (m.find()) {
                String roman = m.group(1);
                name = head.substring(0, m.start()).trim();
                level = romanToInt(roman);
            }
            if (name.equalsIgnoreCase(effectName)) return level;
        }
        return 0;
    }

    private static int romanToInt(String r) {
        return switch (r) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            case "VI" -> 6;
            case "VII" -> 7;
            case "VIII" -> 8;
            case "IX" -> 9;
            case "X" -> 10;
            default -> 1;
        };
    }

    public static boolean hasCombatEffect(String effectName) {
        return getCombatEffectLevel(effectName) > 0;
    }

    /**
     * Returns true if the player currently has the Blindness combat effect active.
     * Used client-side to hide boss warning tiles, enemy movement previews and
     * enemy stat tooltips - the player is literally blind.
     */
    public static boolean hasBlindness() { return hasCombatEffect("Blindness"); }
    public static boolean hasPoison() { return hasCombatEffect("Poison"); }
    public static boolean hasBurning() { return hasCombatEffect("Burning"); }

    public static int getBlindnessLevel() { return getCombatEffectLevel("Blindness"); }
    public static int getDarknessLevel() { return getCombatEffectLevel("Darkness"); }
    public static int getPoisonLevel() { return getCombatEffectLevel("Poison"); }
    public static int getBurningLevel() { return getCombatEffectLevel("Burning"); }
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
        turnOrderList.clear();
        unitActOrder.clear();
        actingEnemyId = -1;
        threatOverlayEnabled = false;
        threatCacheMs = 0;
        threatCache = java.util.Set.of();
        // Reset to 0 (not the 20 default) so the first sync of the next combat
        // sees oldLocalHp == 0 and skips the damage/heal flash + screen shake.
        // Otherwise a player who ended the last combat at a different HP would
        // get a spurious flash comparing the new combat's HP against the stale
        // carried-over value (the oldLocalHp > 0 guards suppress it at 0).
        localPlayerHp = 0;
    }

    public static void updateFromSync(int phase, int ap, int movePoints,
                                       int playerHp, int playerMaxHp, int turnNumber,
                                       int maxAp, int maxSpeed,
                                       int[] enemyData, String enemyTypeIds,
                                       String playerEffects, int killStreak,
                                       String partyHpData, String turnOrderData,
                                       String playerStatsData) {
        // Save old HP before overwriting so we can detect damage/heal.
        // In party combat the {@code playerHp} field carries the CURRENT TURN
        // PLAYER's HP (same value for every recipient), so naively comparing
        // it across syncs flashes "damage"/"heal" on every turn rotation. Pull
        // the local player's actual HP out of partyHpData below and compare on
        // that instead. Solo falls through to the playerHp field.
        int oldLocalHp = CombatState.localPlayerHp;
        int oldPhase = CombatState.phase;

        CombatState.phase = phase;
        // Enemy/ally turn just ended - ease the camera back to the player's framing
        // the instant control returns, rather than leaving it parked on the last
        // actor. Fires only on the transition into PLAYER_TURN (ordinal 0).
        if (oldPhase != 0 && phase == 0) {
            endFocusWithReturn();
        }
        CombatState.apRemaining = ap;
        CombatState.movePointsRemaining = movePoints;
        CombatState.playerHp = playerHp;
        CombatState.playerMaxHp = playerMaxHp;
        CombatState.turnNumber = turnNumber;

        // Resolve THIS client's player HP from partyHpData (per-UUID) before
        // running damage/heal detection. Falls back to the broadcast playerHp
        // field when solo (partyHpData is empty in that case).
        int newLocalHp = playerHp;
        if (partyHpData != null && !partyHpData.isEmpty()) {
            net.minecraft.client.MinecraftClient mcDetect =
                net.minecraft.client.MinecraftClient.getInstance();
            String myUuidDetect = mcDetect.getSession().getUuidOrNull() != null
                ? mcDetect.getSession().getUuidOrNull().toString() : "";
            for (String entry : partyHpData.split("\\|")) {
                String[] parts = entry.split(",");
                if (parts.length < 5) continue;
                if (parts[0].equals(myUuidDetect)) {
                    try { newLocalHp = Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) {}
                    break;
                }
            }
        }
        CombatState.localPlayerHp = newLocalHp;

        // Detect player damage/heal for visual effects (local player only)
        if (newLocalHp < oldLocalHp && oldLocalHp > 0) {
            int dmg = oldLocalHp - newLocalHp;
            CombatVisualEffects.spawnDamageNumber(dmg, true);
            CombatVisualEffects.flashDamage();
            // Shake harder when player takes damage (more alarming)
            float shakeAmount = Math.min(1.0f, dmg / 6.0f) * 0.8f + 0.2f;
            CombatVisualEffects.triggerShake(shakeAmount);
        } else if (newLocalHp > oldLocalHp && oldLocalHp > 0) {
            CombatVisualEffects.flashHeal();
        }

        CombatState.maxAp = maxAp;
        CombatState.maxSpeed = maxSpeed;
        CombatState.playerEffects = playerEffects;
        CombatState.killStreak = killStreak;

        // Track combat stats (local player damage only)
        if (newLocalHp < oldLocalHp && oldLocalHp > 0) {
            totalDamageTaken += (oldLocalHp - newLocalHp);
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
        unitActOrder.clear();
        String[] typeIds = enemyTypeIds.isEmpty() ? new String[0] : enemyTypeIds.split("\\|");
        for (int i = 0; i + 2 < enemyData.length; i += 3) {
            int idx = i / 3;
            boolean isAlly = idx < typeIds.length && typeIds[idx].contains(";ally");
            // Sync order mirrors the server's `enemies` list, which is also the
            // order units act in during the enemy phase - preserved here for the
            // HUD act-order strip.
            unitActOrder.add(enemyData[i]);
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

        // Parse party HP data - put self at top of the list
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
                    // Override the broadcast `playerHp` / `playerMaxHp` / `playerEffects`
                    // fields with our OWN values from partyHpData. The server
                    // sets those broadcast fields from this.player on the host
                    // CM, so after a death handoff they reflect the new
                    // turn-holder's stats - making a dead player's HUD show
                    // their teammate's HP / effects ("player 1 took player 2's
                    // health"). Reading from partyHpData (per-UUID) keeps each
                    // client's HUD locked to their own state.
                    CombatState.playerHp = hp;
                    CombatState.playerMaxHp = mHp;
                    if (parts.length >= 6) {
                        String myEffects = parts[5].replace("/", " | ").replace(";", ",");
                        CombatState.playerEffects = myEffects;
                    }
                } else {
                    others.add(member);
                }
            }
            if (self != null) partyHpList.add(self);
            partyHpList.addAll(others);
        }

        // Parse turn order data: "uuid,name,isCurrent|..."
        turnOrderList.clear();
        if (turnOrderData != null && !turnOrderData.isEmpty()) {
            for (String entry : turnOrderData.split("\\|")) {
                String[] parts = entry.split(",");
                if (parts.length < 3) continue;
                turnOrderList.add(new TurnOrderEntry(parts[0], parts[1], "1".equals(parts[2])));
            }
        }

        // Parse per-player combat stats: "uuid,name,hp,maxHp,dead,atk,ac,ap,speed|..."
        playerStatsMap.clear();
        if (playerStatsData != null && !playerStatsData.isEmpty()) {
            for (String entry : playerStatsData.split("\\|")) {
                String[] p = entry.split(",");
                if (p.length < 9) continue;
                try {
                    playerStatsMap.put(p[0], new PlayerStats(p[0], p[1],
                        Integer.parseInt(p[2]), Integer.parseInt(p[3]), "1".equals(p[4]),
                        Integer.parseInt(p[5]), Integer.parseInt(p[6]),
                        Integer.parseInt(p[7]), Integer.parseInt(p[8])));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    public static java.util.List<PartyMemberHp> getPartyHpList() { return partyHpList; }

    public static int getPhase() { return phase; }
    public static int getApRemaining() { return apRemaining; }
    public static int getMovePointsRemaining() { return movePointsRemaining; }
    public static int getPlayerHp() { return playerHp; }
    public static int getPlayerMaxHp() { return playerMaxHp; }

    /**
     * The LOCAL player's HP fraction. In party combat {@code playerHp} is
     * already overridden per-client from partyHpData during sync, so this is
     * safe on every member's screen. Drives the low-HP warning vignette.
     */
    public static float getLocalHpFraction() {
        return playerMaxHp > 0 ? (float) playerHp / playerMaxHp : 1.0f;
    }
    public static int getTurnNumber() { return turnNumber; }
    public static int getMaxAp() { return maxAp; }
    public static int getMaxSpeed() { return maxSpeed; }
    public static java.util.Map<Integer, int[]> getEnemyHpMap() { return enemyHpMap; }
    public static java.util.Map<Integer, String> getEnemyTypeMap() { return enemyTypeMap; }
    public static java.util.Map<Integer, int[]> getAllyHpMap() { return allyHpMap; }
    public static java.util.Map<Integer, String> getAllyTypeMap() { return allyTypeMap; }

    public static boolean isPlayerTurn() { return phase == 0; } // CombatPhase.PLAYER_TURN ordinal
    public static boolean isEnemyTurn() { return phase == 1; }  // CombatPhase.ENEMY_TURN ordinal

    // --- Polygon mask (non-rectangular arenas) ---
    // Packed row-major bits over polygonMaskW × polygonMaskH (bit index x*H + z),
    // sent in EnterCombatPayload. Null = rectangular arena (whole bbox is valid).
    private static byte[] polygonMask = null;
    private static int polygonMaskW = 0, polygonMaskH = 0;

    public static void setPolygonMask(byte[] mask, int w, int h) {
        polygonMask = (mask == null || mask.length == 0) ? null : mask;
        polygonMaskW = w;
        polygonMaskH = h;
    }

    /** True if the grid tile is inside the playable polygon. Rectangular arenas
     *  (no mask) treat the whole bbox as valid. Keeps the cursor/hover from
     *  targeting tiles outside an irregular arena's shape. */
    public static boolean isInPolygon(int gx, int gz) {
        if (polygonMask == null) return true;
        if (gx < 0 || gz < 0 || gx >= polygonMaskW || gz >= polygonMaskH) return false;
        int idx = gx * polygonMaskH + gz;
        int byteIdx = idx >> 3;
        if (byteIdx < 0 || byteIdx >= polygonMask.length) return false;
        return (polygonMask[byteIdx] & (1 << (idx & 7))) != 0;
    }

    // Emerald currency (persisted on server, synced to client for HUD display)
    private static int emeralds = 0;
    public static int getEmeralds() { return emeralds; }
    public static void setEmeralds(int amount) { emeralds = amount; }

    // --- Server scoreboard (synced every 5s) ---
    public record ScoreboardEntry(String name, int score) {}
    private static java.util.List<ScoreboardEntry> scoreboardEntries = new java.util.ArrayList<>();
    public static java.util.List<ScoreboardEntry> getScoreboardEntries() { return scoreboardEntries; }
    public static void updateScoreboard(String scoreData) {
        java.util.List<ScoreboardEntry> entries = new java.util.ArrayList<>();
        if (scoreData != null && !scoreData.isEmpty()) {
            for (String entry : scoreData.split("\\|")) {
                String[] parts = entry.split(",");
                if (parts.length >= 2) {
                    try {
                        entries.add(new ScoreboardEntry(parts[0], Integer.parseInt(parts[1])));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        scoreboardEntries = entries;
    }
    /** Get a player's score by name, or -1 if not found. */
    public static int getPlayerScore(String name) {
        for (ScoreboardEntry e : scoreboardEntries) {
            if (e.name().equals(name)) return e.score();
        }
        return -1;
    }

    // --- Trader state ---
    private static boolean traderActive = false;
    public static boolean isTraderActive() { return traderActive; }
    public static void setTraderActive(boolean active) { traderActive = active; }

    // --- Player progression stats (synced from server for inventory display) ---
    private static int playerLevel = 1;
    private static int unspentPoints = 0;
    private static int[] statPoints = new int[8]; // one per PlayerProgression.Stat ordinal
    private static int[] affinityPoints = new int[8]; // one per PlayerProgression.Affinity ordinal (SLASHING,CLEAVING,BLUNT,RANGED,WATER,SPECIAL,PET,PHYSICAL)

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

    // --- Inventory stats/affinity overlay visibility (toggled by a keybind) ---
    private static boolean statsOverlayVisible = true;

    /** Whether the inventory stats + damage-affinity panels should be drawn. */
    public static boolean isStatsOverlayVisible() { return statsOverlayVisible; }

    /** Flips the inventory stats + damage-affinity panel visibility. */
    public static void toggleStatsOverlay() { statsOverlayVisible = !statsOverlayVisible; }

    // --- Per-panel minimize state (per session, not persisted) ---
    private static boolean statsPanelCollapsed = false;
    private static boolean affinityPanelCollapsed = false;

    /** Whether the right-side Stats panel is minimized to its icon strip. */
    public static boolean isStatsPanelCollapsed() { return statsPanelCollapsed; }
    /** Toggle the right-side Stats panel between full and minimized. */
    public static void toggleStatsPanelCollapsed() { statsPanelCollapsed = !statsPanelCollapsed; }

    /** Whether the left-side Damage Affinity panel is minimized to its icon strip. */
    public static boolean isAffinityPanelCollapsed() { return affinityPanelCollapsed; }
    /** Toggle the left-side Damage Affinity panel between full and minimized. */
    public static void toggleAffinityPanelCollapsed() { affinityPanelCollapsed = !affinityPanelCollapsed; }

    // === Addon equipment scanner bonuses (from AddonBonusSyncPayload) ===

    private static final java.util.Map<String, Integer> addonBonuses = new java.util.HashMap<>();

    public static void updateAddonBonuses(String bonusData) {
        addonBonuses.clear();
        if (bonusData == null || bonusData.isEmpty()) return;
        for (String entry : bonusData.split(",")) {
            String[] kv = entry.split(":");
            if (kv.length == 2) {
                try { addonBonuses.put(kv[0], Integer.parseInt(kv[1])); }
                catch (NumberFormatException ignored) {}
            }
        }
    }

    public static int getAddonBonus(String bonusKey) {
        return addonBonuses.getOrDefault(bonusKey, 0);
    }

    // === Client-side tile set cache (from TileSetPayload) ===

    private static final java.util.Set<com.crackedgames.craftics.core.GridPos> cachedMoveTiles = new java.util.HashSet<>();
    private static final java.util.Set<com.crackedgames.craftics.core.GridPos> cachedAttackTiles = new java.util.HashSet<>();
    private static final java.util.Set<com.crackedgames.craftics.core.GridPos> cachedDangerTiles = new java.util.HashSet<>();
    private static final java.util.Set<com.crackedgames.craftics.core.GridPos> cachedWarningTiles = new java.util.HashSet<>();
    // Netherite mount 1×3 footprint side tiles - rendered as the golem's body.
    private static final java.util.Set<com.crackedgames.craftics.core.GridPos> cachedMountTiles = new java.util.HashSet<>();
    // Steampunk radar: the route enemies will walk next turn, and the tiles they will strike.
    private static final java.util.Set<com.crackedgames.craftics.core.GridPos> cachedForecastPath = new java.util.HashSet<>();
    private static final java.util.Set<com.crackedgames.craftics.core.GridPos> cachedForecastStrike = new java.util.HashSet<>();
    private static final java.util.Map<com.crackedgames.craftics.core.GridPos, Integer> enemyGridMap = new java.util.HashMap<>();
    private static final java.util.Map<com.crackedgames.craftics.core.GridPos, String> enemyGridTypeMap = new java.util.HashMap<>();

    // Teammate hover positions
    private static final java.util.Map<java.util.UUID, com.crackedgames.craftics.core.GridPos> teammateHovers = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, String> teammateNames = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> teammateHoverTimestamps = new java.util.concurrent.ConcurrentHashMap<>();

    // Active AoE tile flashes (server-sent on attack resolve). Held then faded by the renderer.
    public record TileFlash(java.util.List<com.crackedgames.craftics.core.GridPos> tiles, int color, long startMs, long durationMs) {}
    private static final java.util.List<TileFlash> tileFlashes = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Add a flash from a server payload. tiles = packed alternating x,z; durationTicks at 20/s -> ms. */
    public static void addTileFlash(int[] packed, int color, int durationTicks) {
        java.util.List<com.crackedgames.craftics.core.GridPos> list = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < packed.length; i += 2)
            list.add(new com.crackedgames.craftics.core.GridPos(packed[i], packed[i + 1]));
        if (list.isEmpty()) return;
        long durMs = Math.max(1L, durationTicks * 50L);
        tileFlashes.add(new TileFlash(list, color, System.currentTimeMillis(), durMs));
    }

    /** Live flashes; the renderer removes expired ones as it reads. */
    public static java.util.List<TileFlash> getTileFlashes() { return tileFlashes; }
    public static void clearTileFlashes() { tileFlashes.clear(); }

    // Client-local hover (used directly by renderer, no server round-trip)
    private static com.crackedgames.craftics.core.GridPos hoveredTile = null;

    /**
     * Client-local: the ally entity id currently selected by a Lead command.
     * Cleared on combat end and whenever the player either commits a command
     * or clicks the same ally again to cancel. Read by the input handler and
     * the renderer (to glow the selected mob).
     */
    private static Integer leadSelectedAllyId = null;
    public static Integer getLeadSelectedAllyId() { return leadSelectedAllyId; }
    public static void setLeadSelectedAllyId(Integer id) { leadSelectedAllyId = id; }

    public static java.util.Set<com.crackedgames.craftics.core.GridPos> getMoveTiles() { return cachedMoveTiles; }
    public static java.util.Set<com.crackedgames.craftics.core.GridPos> getAttackTiles() { return cachedAttackTiles; }
    public static java.util.Set<com.crackedgames.craftics.core.GridPos> getDangerTiles() { return cachedDangerTiles; }
    public static java.util.Set<com.crackedgames.craftics.core.GridPos> getWarningTiles() { return cachedWarningTiles; }

    /** One directional telegraph arrow: a tile plus the cardinal the attack travels. */
    public record WarningArrow(com.crackedgames.craftics.core.GridPos pos, int dx, int dz) {}
    private static final java.util.List<WarningArrow> cachedWarningArrows = new java.util.ArrayList<>();
    /** Directional telegraph arrows (charge lanes, pulls, gales); empty when none pending. */
    public static java.util.List<WarningArrow> getWarningArrows() { return cachedWarningArrows; }
    public static java.util.Set<com.crackedgames.craftics.core.GridPos> getMountTiles() { return cachedMountTiles; }
    /** Steampunk radar: tiles enemies will walk through on their next turn. */
    public static java.util.Set<com.crackedgames.craftics.core.GridPos> getForecastPath() { return cachedForecastPath; }
    /** Steampunk radar: tiles that will be struck on the enemies' next turn. */
    public static java.util.Set<com.crackedgames.craftics.core.GridPos> getForecastStrike() { return cachedForecastStrike; }
    public static java.util.Map<com.crackedgames.craftics.core.GridPos, Integer> getEnemyGridMap() { return enemyGridMap; }
    public static java.util.Map<com.crackedgames.craftics.core.GridPos, String> getEnemyGridTypeMap() { return enemyGridTypeMap; }
    public static com.crackedgames.craftics.core.GridPos getHoveredTile() { return hoveredTile; }
    public static void setHoveredTile(com.crackedgames.craftics.core.GridPos tile) { hoveredTile = tile; }

    public static java.util.Map<java.util.UUID, com.crackedgames.craftics.core.GridPos> getTeammateHovers() { return teammateHovers; }
    public static java.util.Map<java.util.UUID, String> getTeammateNames() { return teammateNames; }
    public static java.util.Map<java.util.UUID, Long> getTeammateHoverTimestamps() { return teammateHoverTimestamps; }

    public static void updateTileSets(int[] moveTiles, int[] attackTiles, int[] dangerTiles,
                                       int[] warningTiles, int[] enemyMapData, String enemyTypes,
                                       int[] mountTiles, int[] warningArrows,
                                       int[] forecastPath, int[] forecastStrike) {
        cachedMoveTiles.clear();
        cachedAttackTiles.clear();
        cachedDangerTiles.clear();
        cachedWarningTiles.clear();
        cachedMountTiles.clear();
        cachedWarningArrows.clear();
        cachedForecastPath.clear();
        cachedForecastStrike.clear();
        enemyGridMap.clear();
        enemyGridTypeMap.clear();

        for (int i = 0; i + 1 < forecastPath.length; i += 2)
            cachedForecastPath.add(new com.crackedgames.craftics.core.GridPos(forecastPath[i], forecastPath[i + 1]));
        for (int i = 0; i + 1 < forecastStrike.length; i += 2)
            cachedForecastStrike.add(new com.crackedgames.craftics.core.GridPos(forecastStrike[i], forecastStrike[i + 1]));

        for (int i = 0; i + 3 < warningArrows.length; i += 4)
            cachedWarningArrows.add(new WarningArrow(
                new com.crackedgames.craftics.core.GridPos(warningArrows[i], warningArrows[i + 1]),
                warningArrows[i + 2], warningArrows[i + 3]));

        for (int i = 0; i + 1 < mountTiles.length; i += 2)
            cachedMountTiles.add(new com.crackedgames.craftics.core.GridPos(mountTiles[i], mountTiles[i + 1]));
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
        cachedMountTiles.clear();
        cachedWarningArrows.clear();
        enemyGridMap.clear();
        enemyGridTypeMap.clear();
        teammateHovers.clear();
        teammateNames.clear();
        teammateHoverTimestamps.clear();
        tileFlashes.clear();
        hoveredTile = null;
        leadSelectedAllyId = null;
    }

    /**
     * Wipe all client combat state. Called from the disconnect handler so that
     * leaving a world mid-battle (before the server can send ExitCombatPayload)
     * doesn't leave the camera permanently locked into isometric view on the
     * title screen or in subsequent worlds - previously this bricked the client
     * until a full restart.
     */
    public static void resetAll() {
        inCombat = false;
        cinematicActive = false;
        inScene = false;
        clearTileSets();
        resetCombatStats();
        combatPitch = 55.0f;
        combatYaw = 225.0f;
        combatCameraDistance = 15.0f;
        cameraPanX = 0;
        cameraPanZ = 0;
        arenaCenterX = 0;
        arenaCenterY = 100;
        arenaCenterZ = 0;
        arenaBaseCenterX = 0;
        arenaBaseCenterZ = 0;
        arenaOriginX = 0;
        arenaOriginY = 100;
        arenaOriginZ = 0;
        arenaWidth = 0;
        arenaHeight = 0;
        hasFocus = false;
        returningFromFocus = false;
        focusTimer = 0;
        focusTargetX = 0;
        focusTargetZ = 0;
        focusCurrentX = 0;
        focusCurrentZ = 0;
        focusZoomCurrent = 15.0f;
        focusZoomTarget = 15.0f;
        hoveredEnemyId = -1;
        hoveredPlayerUuid = null;
        playerStatsMap.clear();
        traderActive = false;
    }
}
