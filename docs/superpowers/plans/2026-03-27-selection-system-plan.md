# Selection System Fortification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace server-dependent carpet-block highlights with client-side overlay rendering for instant tile selection, fix the "enemy not on tile" bug with server-authoritative entity positions, and add teammate hover sharing.

**Architecture:** New `TileSetPayload` sends valid move/attack/danger tiles + enemy grid map from server to client on state changes. New `TileOverlayRenderer` draws colored translucent quads client-side at framerate. Hover is fully client-local (zero latency). Teammate hovers are relayed through the server at 4-5Hz. `TileHighlightManager` (carpet blocks) is deleted entirely.

**Tech Stack:** Java 21, Minecraft Fabric 1.21.1, Fabric Networking API, Fabric Rendering API (`WorldRenderEvents`)

---

## Task 1: Create `TileSetPayload` (S2C)

**Files:**
- Create: `src/main/java/com/crackedgames/craftics/network/TileSetPayload.java`

- [ ] **Step 1: Create the payload record**

```java
package com.crackedgames.craftics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server-to-client payload containing valid move/attack/danger tiles and enemy grid positions.
 * Replaces the old carpet-block highlight system with client-side cached tile sets.
 */
public record TileSetPayload(
    int[] moveTiles,     // flat: [x1, z1, x2, z2, ...]
    int[] attackTiles,   // flat: [x1, z1, x2, z2, ...]
    int[] dangerTiles,   // flat: [x1, z1, x2, z2, ...]
    int[] enemyMap,      // flat: [x, z, entityId, x, z, entityId, ...]
    String enemyTypes    // pipe-separated entity type IDs parallel to enemyMap triplets
) implements CustomPayload {

    public static final Id<TileSetPayload> ID =
        new Id<>(Identifier.of("craftics", "tile_set"));

    public static final PacketCodec<RegistryByteBuf, TileSetPayload> CODEC =
        PacketCodec.of(TileSetPayload::encode, TileSetPayload::decode);

    private static TileSetPayload decode(RegistryByteBuf buf) {
        int[] move = readIntArray(buf);
        int[] attack = readIntArray(buf);
        int[] danger = readIntArray(buf);
        int[] enemy = readIntArray(buf);
        String types = buf.readString();
        return new TileSetPayload(move, attack, danger, enemy, types);
    }

    private void encode(RegistryByteBuf buf) {
        writeIntArray(buf, moveTiles);
        writeIntArray(buf, attackTiles);
        writeIntArray(buf, dangerTiles);
        writeIntArray(buf, enemyMap);
        buf.writeString(enemyTypes);
    }

    private static void writeIntArray(RegistryByteBuf buf, int[] arr) {
        buf.writeVarInt(arr.length);
        for (int v : arr) buf.writeVarInt(v);
    }

    private static int[] readIntArray(RegistryByteBuf buf) {
        int len = buf.readVarInt();
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = buf.readVarInt();
        return arr;
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
```

- [ ] **Step 2: Verify build**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew compileJava
```

- [ ] **Step 3: Commit**

```
feat: add TileSetPayload for client-side tile highlight data
```

---

## Task 2: Create `HoverUpdatePayload` (C2S) and `TeammateHoverPayload` (S2C)

**Files:**
- Create: `src/main/java/com/crackedgames/craftics/network/HoverUpdatePayload.java`
- Create: `src/main/java/com/crackedgames/craftics/network/TeammateHoverPayload.java`

- [ ] **Step 1: Create HoverUpdatePayload**

```java
package com.crackedgames.craftics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server: tells server which grid tile this player is hovering.
 * Sent at 4-5Hz (every ~200ms) when hovered tile changes.
 * Server relays to party members as TeammateHoverPayload.
 */
public record HoverUpdatePayload(int gridX, int gridZ) implements CustomPayload {

    public static final Id<HoverUpdatePayload> ID =
        new Id<>(Identifier.of("craftics", "hover_update"));

    public static final PacketCodec<RegistryByteBuf, HoverUpdatePayload> CODEC =
        PacketCodec.of(
            (payload, buf) -> { buf.writeVarInt(payload.gridX); buf.writeVarInt(payload.gridZ); },
            buf -> new HoverUpdatePayload(buf.readVarInt(), buf.readVarInt())
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
```

- [ ] **Step 2: Create TeammateHoverPayload**

```java
package com.crackedgames.craftics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Server-to-client: relays a party member's hover position to other members.
 */
public record TeammateHoverPayload(UUID playerUuid, String playerName,
                                    int gridX, int gridZ) implements CustomPayload {

    public static final Id<TeammateHoverPayload> ID =
        new Id<>(Identifier.of("craftics", "teammate_hover"));

    public static final PacketCodec<RegistryByteBuf, TeammateHoverPayload> CODEC =
        PacketCodec.of(
            (payload, buf) -> {
                buf.writeUuid(payload.playerUuid);
                buf.writeString(payload.playerName);
                buf.writeVarInt(payload.gridX);
                buf.writeVarInt(payload.gridZ);
            },
            buf -> new TeammateHoverPayload(buf.readUuid(), buf.readString(),
                                            buf.readVarInt(), buf.readVarInt())
        );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
```

- [ ] **Step 3: Commit**

```
feat: add HoverUpdatePayload and TeammateHoverPayload for multiplayer hover sharing
```

---

## Task 3: Add tile set cache to `CombatState`

**Files:**
- Modify: `src/client/java/com/crackedgames/craftics/client/CombatState.java`

- [ ] **Step 1: Add tile set and enemy map fields**

After the existing arena fields (around line 23), add:

```java
// Cached tile sets from server (for client-side rendering)
private static final java.util.Set<com.crackedgames.craftics.core.GridPos> cachedMoveTiles = new java.util.HashSet<>();
private static final java.util.Set<com.crackedgames.craftics.core.GridPos> cachedAttackTiles = new java.util.HashSet<>();
private static final java.util.Set<com.crackedgames.craftics.core.GridPos> cachedDangerTiles = new java.util.HashSet<>();
private static final java.util.Map<com.crackedgames.craftics.core.GridPos, Integer> enemyGridMap = new java.util.HashMap<>(); // pos -> entityId
private static final java.util.Map<com.crackedgames.craftics.core.GridPos, String> enemyTypeMap = new java.util.HashMap<>(); // pos -> typeId

// Teammate hover positions
private static final java.util.Map<java.util.UUID, com.crackedgames.craftics.core.GridPos> teammateHovers = new java.util.concurrent.ConcurrentHashMap<>();
private static final java.util.Map<java.util.UUID, String> teammateNames = new java.util.concurrent.ConcurrentHashMap<>();
private static final java.util.Map<java.util.UUID, Long> teammateHoverTimestamps = new java.util.concurrent.ConcurrentHashMap<>();

// Client-local hover (not sent to server for highlights — used directly by renderer)
private static com.crackedgames.craftics.core.GridPos hoveredTile = null;
```

- [ ] **Step 2: Add getters and update methods**

```java
public static java.util.Set<com.crackedgames.craftics.core.GridPos> getMoveTiles() { return cachedMoveTiles; }
public static java.util.Set<com.crackedgames.craftics.core.GridPos> getAttackTiles() { return cachedAttackTiles; }
public static java.util.Set<com.crackedgames.craftics.core.GridPos> getDangerTiles() { return cachedDangerTiles; }
public static java.util.Map<com.crackedgames.craftics.core.GridPos, Integer> getEnemyGridMap() { return enemyGridMap; }
public static java.util.Map<com.crackedgames.craftics.core.GridPos, String> getEnemyTypeMap() { return enemyTypeMap; }
public static com.crackedgames.craftics.core.GridPos getHoveredTile() { return hoveredTile; }
public static void setHoveredTile(com.crackedgames.craftics.core.GridPos tile) { hoveredTile = tile; }

public static java.util.Map<java.util.UUID, com.crackedgames.craftics.core.GridPos> getTeammateHovers() { return teammateHovers; }
public static java.util.Map<java.util.UUID, String> getTeammateNames() { return teammateNames; }
public static java.util.Map<java.util.UUID, Long> getTeammateHoverTimestamps() { return teammateHoverTimestamps; }

public static void updateTileSets(int[] moveTiles, int[] attackTiles, int[] dangerTiles,
                                   int[] enemyMapData, String enemyTypes) {
    cachedMoveTiles.clear();
    cachedAttackTiles.clear();
    cachedDangerTiles.clear();
    enemyGridMap.clear();
    enemyTypeMap.clear();

    for (int i = 0; i + 1 < moveTiles.length; i += 2)
        cachedMoveTiles.add(new com.crackedgames.craftics.core.GridPos(moveTiles[i], moveTiles[i + 1]));
    for (int i = 0; i + 1 < attackTiles.length; i += 2)
        cachedAttackTiles.add(new com.crackedgames.craftics.core.GridPos(attackTiles[i], attackTiles[i + 1]));
    for (int i = 0; i + 1 < dangerTiles.length; i += 2)
        cachedDangerTiles.add(new com.crackedgames.craftics.core.GridPos(dangerTiles[i], dangerTiles[i + 1]));

    String[] types = enemyTypes.isEmpty() ? new String[0] : enemyTypes.split("\\|");
    for (int i = 0; i + 2 < enemyMapData.length; i += 3) {
        var pos = new com.crackedgames.craftics.core.GridPos(enemyMapData[i], enemyMapData[i + 1]);
        enemyGridMap.put(pos, enemyMapData[i + 2]);
        int typeIdx = i / 3;
        if (typeIdx < types.length) enemyTypeMap.put(pos, types[typeIdx]);
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
    enemyGridMap.clear();
    enemyTypeMap.clear();
    teammateHovers.clear();
    teammateNames.clear();
    teammateHoverTimestamps.clear();
    hoveredTile = null;
}
```

- [ ] **Step 3: Call `clearTileSets()` in the existing `enterCombat()` and when exiting combat**

In the `enterCombat()` method, add `clearTileSets();` at the start. Also add it wherever combat exit clears state (search for where `inCombat = false` is set).

- [ ] **Step 4: Commit**

```
feat: add tile set cache and teammate hover state to CombatState
```

---

## Task 4: Create `TileOverlayRenderer`

**Files:**
- Create: `src/client/java/com/crackedgames/craftics/client/TileOverlayRenderer.java`

- [ ] **Step 1: Create the renderer**

```java
package com.crackedgames.craftics.client;

import com.crackedgames.craftics.core.GridPos;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Renders colored translucent quads on arena tiles for highlights.
 * Replaces the old TileHighlightManager carpet-block system.
 * All rendering is client-side — zero server round-trips.
 */
public class TileOverlayRenderer {

    private static long frameCounter = 0;

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (!CombatState.isInCombat()) return;
            frameCounter++;
            render(context);
        });
    }

    private static void render(WorldRenderEvents.AfterTranslucentContext context) {
        MatrixStack matrices = context.matrixStack();
        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();

        int originX = CombatState.getArenaOriginX();
        int originY = CombatState.getArenaOriginY();
        int originZ = CombatState.getArenaOriginZ();
        float renderY = originY + 1.01f; // slightly above floor to avoid z-fighting

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        var vertexConsumers = context.consumers();
        if (vertexConsumers == null) { matrices.pop(); return; }
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getTranslucentMovingBlock());
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Draw move tiles (light blue)
        boolean colorblind = com.crackedgames.craftics.CrafticsConfigClient.colorblindMode;
        for (GridPos tile : CombatState.getMoveTiles()) {
            float r = colorblind ? 0.2f : 0.2f;
            float g = colorblind ? 0.9f : 0.6f;
            float b = colorblind ? 0.2f : 1.0f;
            drawTileQuad(matrix, buffer, originX + tile.x(), renderY, originZ + tile.z(), r, g, b, 0.35f);
        }

        // Draw attack tiles (red)
        for (GridPos tile : CombatState.getAttackTiles()) {
            float r = colorblind ? 0.9f : 1.0f;
            float g = colorblind ? 0.9f : 0.2f;
            float b = colorblind ? 0.2f : 0.2f;
            drawTileQuad(matrix, buffer, originX + tile.x(), renderY, originZ + tile.z(), r, g, b, 0.35f);
        }

        // Draw danger tiles (orange)
        for (GridPos tile : CombatState.getDangerTiles()) {
            drawTileQuad(matrix, buffer, originX + tile.x(), renderY, originZ + tile.z(), 1.0f, 0.6f, 0.1f, 0.25f);
        }

        // Draw teammate hovers (dim, with fade)
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, GridPos> entry : CombatState.getTeammateHovers().entrySet()) {
            Long timestamp = CombatState.getTeammateHoverTimestamps().get(entry.getKey());
            if (timestamp == null) continue;
            long age = now - timestamp;
            if (age > 1000) continue; // expired
            float fadeAlpha = age > 500 ? 0.2f * (1.0f - (age - 500) / 500.0f) : 0.2f;
            if (fadeAlpha <= 0) continue;
            GridPos tile = entry.getValue();
            drawTileQuad(matrix, buffer, originX + tile.x(), renderY, originZ + tile.z(), 0.9f, 0.9f, 0.9f, fadeAlpha);
        }

        // Draw hover tile (bright, pulsing) — LAST so it renders on top
        GridPos hover = CombatState.getHoveredTile();
        if (hover != null) {
            float pulse = (float) (0.45 + 0.1 * Math.sin(frameCounter * 0.08));
            boolean isAttackTile = CombatState.getAttackTiles().contains(hover);
            if (isAttackTile) {
                float r = colorblind ? 1.0f : 1.0f;
                float g = colorblind ? 1.0f : 0.3f;
                float b = colorblind ? 0.3f : 0.3f;
                drawTileQuad(matrix, buffer, originX + hover.x(), renderY + 0.005f, originZ + hover.z(), r, g, b, pulse);
            } else {
                float r = colorblind ? 0.3f : 0.3f;
                float g = colorblind ? 1.0f : 0.9f;
                float b = colorblind ? 0.3f : 1.0f;
                drawTileQuad(matrix, buffer, originX + hover.x(), renderY + 0.005f, originZ + hover.z(), r, g, b, pulse);
            }
        }

        matrices.pop();
    }

    private static void drawTileQuad(Matrix4f matrix, VertexConsumer buffer,
                                      float x, float y, float z,
                                      float r, float g, float b, float a) {
        float margin = 0.05f; // slight inset so tiles don't bleed into each other
        float x0 = x + margin;
        float x1 = x + 1.0f - margin;
        float z0 = z + margin;
        float z1 = z + 1.0f - margin;

        // Quad: 4 vertices, normal facing up
        buffer.vertex(matrix, x0, y, z0).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x0, y, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x1, y, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x1, y, z0).color(r, g, b, a).normal(0, 1, 0);
    }
}
```

**Note:** The exact `VertexConsumer` API may need adjustment based on the Minecraft 1.21.1 rendering API. The vertex format for `RenderLayer.getTranslucentMovingBlock()` may require UV coordinates or light values. Check `net.minecraft.client.render.VertexConsumer` method signatures and adjust. If `getTranslucentMovingBlock` doesn't work, try `RenderLayer.getTranslucent()` or a custom `RenderLayer`. Also check if `CrafticsConfigClient` exists — if not, read colorblind mode from the server config sync or a client-side config.

- [ ] **Step 2: Verify build (expect rendering API adjustments may be needed)**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew compileJava
```

Fix any vertex format issues based on compiler errors.

- [ ] **Step 3: Commit**

```
feat: add TileOverlayRenderer for client-side tile highlights
```

---

## Task 5: Register new payloads and client handlers

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/network/ModNetworking.java`
- Modify: `src/client/java/com/crackedgames/craftics/CrafticsClient.java`

- [ ] **Step 1: Register S2C payloads in ModNetworking**

In `registerServer()`, after the existing S2C payload registrations (around line 33), add:

```java
PayloadTypeRegistry.playS2C().register(TileSetPayload.ID, TileSetPayload.CODEC);
PayloadTypeRegistry.playS2C().register(TeammateHoverPayload.ID, TeammateHoverPayload.CODEC);
```

- [ ] **Step 2: Register C2S payload and handler in ModNetworking**

After the existing C2S registrations, add:

```java
PayloadTypeRegistry.playC2S().register(HoverUpdatePayload.ID, HoverUpdatePayload.CODEC);
```

After the existing `ServerPlayNetworking.registerGlobalReceiver` calls, add the hover relay:

```java
// Relay hover to party members
ServerPlayNetworking.registerGlobalReceiver(HoverUpdatePayload.ID, (payload, context) -> {
    ServerPlayerEntity hoverPlayer = context.player();
    ServerWorld world = (ServerWorld) hoverPlayer.getEntityWorld();
    com.crackedgames.craftics.world.CrafticsSavedData data =
        com.crackedgames.craftics.world.CrafticsSavedData.get(world);
    com.crackedgames.craftics.world.Party party = data.getPlayerParty(hoverPlayer.getUuid());
    if (party == null) return; // solo player, no relay needed
    String senderName = hoverPlayer.getName().getString();
    for (java.util.UUID memberUuid : party.getMemberUuids()) {
        if (memberUuid.equals(hoverPlayer.getUuid())) continue; // don't relay to self
        ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(memberUuid);
        if (member != null) {
            ServerPlayNetworking.send(member, new TeammateHoverPayload(
                hoverPlayer.getUuid(), senderName, payload.gridX(), payload.gridZ()
            ));
        }
    }
});
```

- [ ] **Step 3: Register client handlers in CrafticsClient**

In `onInitializeClient()`, in the network handler section, add:

```java
// Tile set updates (replaces carpet highlights)
net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
    com.crackedgames.craftics.network.TileSetPayload.ID, (payload, context) -> {
        context.client().execute(() -> {
            CombatState.updateTileSets(payload.moveTiles(), payload.attackTiles(),
                payload.dangerTiles(), payload.enemyMap(), payload.enemyTypes());
        });
    });

// Teammate hover relay
net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
    com.crackedgames.craftics.network.TeammateHoverPayload.ID, (payload, context) -> {
        context.client().execute(() -> {
            CombatState.updateTeammateHover(payload.playerUuid(), payload.playerName(),
                payload.gridX(), payload.gridZ());
        });
    });
```

- [ ] **Step 4: Register TileOverlayRenderer in CrafticsClient**

In `onInitializeClient()`, near the HUD renderer registrations (around line 186), add:

```java
TileOverlayRenderer.register();
```

- [ ] **Step 5: Verify build**

- [ ] **Step 6: Commit**

```
feat: register tile set, hover, and teammate hover payloads with client handlers
```

---

## Task 6: Update `CombatManager.refreshHighlights()` to send `TileSetPayload`

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatManager.java`

- [ ] **Step 1: Rewrite `refreshHighlights()` (line ~4653)**

Replace the current method that calls `TileHighlightManager.show*()` with one that builds arrays and sends `TileSetPayload`:

```java
public void refreshHighlights() {
    if (!active || player == null || phase != CombatPhase.PLAYER_TURN) return;
    ServerWorld world = (ServerWorld) player.getEntityWorld();

    // Determine mode from player's held item
    var held = player.getMainHandStack().getItem();
    boolean isMoveMode = (held == Items.FEATHER);

    // Build move tiles
    java.util.List<Integer> moveList = new java.util.ArrayList<>();
    java.util.List<Integer> attackList = new java.util.ArrayList<>();

    if (isMoveMode) {
        // Reachable tiles based on remaining move points
        var reachable = arena.getReachableTiles(arena.getPlayerGridPos(), movePointsRemaining);
        for (var tile : reachable) {
            moveList.add(tile.x());
            moveList.add(tile.z());
        }
    } else {
        // Attack tiles: enemy positions within weapon range
        int range = PlayerCombatStats.getWeaponRange(player);
        GridPos playerPos = arena.getPlayerGridPos();
        for (CombatEntity enemy : enemies) {
            if (!enemy.isAlive() || enemy.isAlly()) continue;
            GridPos enemyPos = enemy.getGridPos();
            int dist = Math.abs(playerPos.x() - enemyPos.x()) + Math.abs(playerPos.z() - enemyPos.z());
            if (dist <= range) {
                attackList.add(enemyPos.x());
                attackList.add(enemyPos.z());
            }
        }
    }

    // Build danger tiles
    java.util.List<Integer> dangerList = new java.util.ArrayList<>();
    if (CrafticsMod.CONFIG.showEnemyRangeHints()) {
        for (CombatEntity enemy : enemies) {
            if (!enemy.isAlive() || enemy.isAlly()) continue;
            var dangerTiles = arena.getReachableTiles(enemy.getGridPos(), enemy.getMoveSpeed());
            for (var tile : dangerTiles) {
                dangerList.add(tile.x());
                dangerList.add(tile.z());
            }
        }
    }

    // Build enemy map
    java.util.List<Integer> enemyMapList = new java.util.ArrayList<>();
    StringBuilder enemyTypes = new StringBuilder();
    for (CombatEntity enemy : enemies) {
        if (!enemy.isAlive()) continue;
        GridPos pos = enemy.getGridPos();
        enemyMapList.add(pos.x());
        enemyMapList.add(pos.z());
        enemyMapList.add(enemy.getEntityId());
        if (enemyTypes.length() > 0) enemyTypes.append("|");
        enemyTypes.append(enemy.getEntityTypeId());
    }

    // Send to client
    ServerPlayNetworking.send(player, new com.crackedgames.craftics.network.TileSetPayload(
        moveList.stream().mapToInt(Integer::intValue).toArray(),
        attackList.stream().mapToInt(Integer::intValue).toArray(),
        dangerList.stream().mapToInt(Integer::intValue).toArray(),
        enemyMapList.stream().mapToInt(Integer::intValue).toArray(),
        enemyTypes.toString()
    ));

    // Auto-end turn when AP is depleted (configurable)
    if (CrafticsMod.CONFIG.autoEndTurn() && apRemaining <= 0 && movePointsRemaining <= 0) {
        handleEndTurn();
    }
}
```

**IMPORTANT:** The exact method for getting reachable tiles depends on what `arena.getReachableTiles()` is called (it may be named differently — check `GridArena.java`). Also check how `TileHighlightManager.showMoveHighlights` currently computes reachable tiles and replicate that logic. The attack range computation should match `TileHighlightManager.showAttackHighlights`. Read those methods and adapt.

- [ ] **Step 2: Update `clearHighlights()` (line ~4731)**

Replace with:

```java
private void clearHighlights() {
    if (player != null) {
        // Send empty tile sets to clear client highlights
        ServerPlayNetworking.send(player, new com.crackedgames.craftics.network.TileSetPayload(
            new int[0], new int[0], new int[0], new int[0], ""
        ));
    }
}
```

- [ ] **Step 3: Commit**

```
refactor: refreshHighlights sends TileSetPayload instead of placing carpet blocks
```

---

## Task 7: Remove hover from server, update client input handler

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatManager.java`
- Modify: `src/client/java/com/crackedgames/craftics/client/CombatInputHandler.java`
- Modify: `src/main/java/com/crackedgames/craftics/network/CombatActionPayload.java`

- [ ] **Step 1: Remove `handleHover()` from CombatManager (line ~757-799)**

Delete the entire `handleHover` method. Also remove the `hoveredTile` field.

In `handleAction()` (the method that dispatches based on `actionType`), remove the `ACTION_HOVER` case.

- [ ] **Step 2: Remove `ACTION_HOVER` from CombatActionPayload (line 23)**

Delete:
```java
public static final int ACTION_HOVER = 4;
```

- [ ] **Step 3: Update CombatInputHandler.tick() to use local state**

In `CombatInputHandler.tick()` (line ~80), replace the hover section that sends `ACTION_HOVER` packets with local state updates:

```java
// Update hover tile locally (no server packet)
GridPos hoverPos = TileRaycast.getGridPosUnderCursor();
CombatState.setHoveredTile(hoverPos);

// Look up hovered enemy from server-authoritative enemy grid map
if (hoverPos != null) {
    Integer entityId = CombatState.getEnemyGridMap().get(hoverPos);
    CombatState.setHoveredEnemyId(entityId != null ? entityId : -1);
} else {
    CombatState.setHoveredEnemyId(-1);
}
```

Remove the old hover packet sending code and the `findEnemyAtGridPos()` call.

- [ ] **Step 4: Add hover broadcast throttle for teammates**

Still in `CombatInputHandler.tick()`, add after the hover update:

```java
// Broadcast hover to teammates at 4-5Hz
long now = System.currentTimeMillis();
if (hoverPos != null && (lastHoverBroadcastTime == 0 || now - lastHoverBroadcastTime > 200)) {
    if (!java.util.Objects.equals(hoverPos, lastBroadcastedHover)) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new com.crackedgames.craftics.network.HoverUpdatePayload(hoverPos.x(), hoverPos.z())
        );
        lastBroadcastedHover = hoverPos;
        lastHoverBroadcastTime = now;
    }
}
```

Add static fields:
```java
private static long lastHoverBroadcastTime = 0;
private static GridPos lastBroadcastedHover = null;
```

- [ ] **Step 5: Update `handleClick()` to use enemyGridMap**

In `handleClick()`, where it currently calls `findEnemyAtGridPos()`, replace with:

```java
Integer entityId = CombatState.getEnemyGridMap().get(gridPos);
int targetEntityId = entityId != null ? entityId : -1;
```

- [ ] **Step 6: Delete `findEnemyAtGridPos()` method entirely** (line ~197-235)

No longer needed — replaced by `enemyGridMap` lookup.

- [ ] **Step 7: Commit**

```
refactor: remove server hover, use client-local state and enemyGridMap for selection
```

---

## Task 8: Delete `TileHighlightManager` and clean up references

**Files:**
- Delete: `src/main/java/com/crackedgames/craftics/combat/TileHighlightManager.java`
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatManager.java`

- [ ] **Step 1: Delete TileHighlightManager.java**

Remove the entire file.

- [ ] **Step 2: Remove all TileHighlightManager references in CombatManager**

Search for `TileHighlightManager` in CombatManager and remove/replace all references. After Tasks 6-7, there should be few remaining — likely just some `trackHighlight()` calls that were part of the old hover system (already removed). Grep to confirm:

```bash
grep -n "TileHighlightManager" src/main/java/com/crackedgames/craftics/combat/CombatManager.java
```

Fix any remaining references.

- [ ] **Step 3: Verify build**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew compileJava
```

- [ ] **Step 4: Commit**

```
refactor: delete TileHighlightManager — carpet-block highlights fully replaced by client overlay
```

---

## Task 9: Full build and verification

**Files:** None (verification only)

- [ ] **Step 1: Full build**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew build
```

- [ ] **Step 2: Search for stale references**

```bash
grep -rn "TileHighlightManager\|ACTION_HOVER\|findEnemyAtGridPos\|showMoveHighlights\|showAttackHighlights\|showEnemyDangerZones" src/
```

Expected: zero matches.

- [ ] **Step 3: Manual test plan**

Launch the game and test:

1. **Hover responsiveness:** Enter combat. Move crosshair across tiles. Verify highlight follows crosshair instantly with zero delay. Should feel like it's painted under your cursor.
2. **Move highlights:** Hold Feather. Verify blue tiles appear showing reachable positions. Move — verify they update.
3. **Attack highlights:** Hold sword. Verify red tiles on enemies in range. Verify hover on enemy shows inspect panel.
4. **Enemy targeting:** Click an enemy tile. Verify attack fires correctly with no "enemy not on tile" error.
5. **Danger zones:** Enable enemy range hints in config. Verify orange tiles appear.
6. **Colorblind mode:** Toggle colorblind. Verify alternate colors.
7. **Teammate hover (if testable):** With 2 players in a party, verify one player sees the other's hover position as a dim overlay.

- [ ] **Step 4: Commit any fixes**
