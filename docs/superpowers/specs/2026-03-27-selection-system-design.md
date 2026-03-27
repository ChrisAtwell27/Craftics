# Selection System Fortification Design

**Goal:** Replace the server-dependent carpet-block highlight system with a client-side overlay renderer for instant, accurate tile selection — and fix the "enemy not on tile" bug by using server-authoritative entity positions.

**Scope:** Client-side rendering, new tile set payload, hover flow overhaul, teammate hover sharing. Does NOT change combat logic, turn system, or action validation.

---

## 1. Problem Analysis

### Current architecture (broken)
1. Client raycasts to find grid tile under crosshair
2. Client sends `ACTION_HOVER` packet to server every time tile changes
3. Server places colored carpet blocks at valid tiles
4. Minecraft propagates block changes back to client
5. Client sees highlight ~20-50ms later (or not at all if packet dropped)

### Root causes
- **Hover latency:** 20-50ms round-trip for every tile change means highlights lag behind crosshair or don't appear at all on busy servers
- **Enemy mismatch:** Client scans world entities to detect who's on a tile (`findEnemyAtGridPos`), while server tracks positions in `arena.getEntityAt()`. Mob visual positions drift from logical grid positions due to entity interpolation, walk animations, and Pehkui scaling — causing "enemy not on tile" false negatives
- **Physical blocks as UI:** Carpet blocks are a world mutation for a UI concern. They interact with lighting, entity spawning, and block update propagation. Wrong abstraction layer.

---

## 2. Client-Side Tile Overlay Renderer

### New class: `TileOverlayRenderer`
Hooks into Minecraft's `WorldRenderEvents.AFTER_TRANSLUCENT` event. Draws colored translucent quads on top of grid tiles at Y = arenaOriginY + 1. No blocks placed in the world.

### Overlay types

| Type | Color | Colorblind Alt | Source | Update Frequency |
|------|-------|---------------|--------|-----------------|
| Hover | Bright cyan, pulsing | Bright lime | Client raycast | Every frame (0ms) |
| Move range | Light blue | Lime | Server tile set | On state change (~3-5/turn) |
| Attack range | Red | Yellow | Server tile set | On state change |
| Danger zones | Orange | Orange | Server tile set | On state change |
| Teammate hover | Dim white / player color | Same | Server relay | 4-5 Hz per teammate |

### Rendering details
- Quads drawn at Y + 0.01 above the floor plane (avoids z-fighting)
- Alpha blending for translucency (~0.35 for ranges, ~0.5 for hover, ~0.2 for teammate)
- Hover tile gets a subtle pulse animation (alpha oscillates 0.4-0.6 over ~1 second)
- Renders only within arena bounds (check grid dimensions from `CombatState`)

### What gets deleted
- `TileHighlightManager.java` — entire class removed
- All carpet-placement logic in `CombatManager.refreshHighlights()` and `clearHighlights()`
- Carpet color constants and colorblind carpet mappings

---

## 3. Server Tile Set Payload

### New payload: `TileSetPayload` (S2C)

```
TileSetPayload {
    int[] moveTiles      // flat array: [x1, z1, x2, z2, ...]
    int[] attackTiles    // flat array: [x1, z1, x2, z2, ...]
    int[] dangerTiles    // flat array: [x1, z1, x2, z2, ...]
    int[] enemyMap       // flat array: [x, z, entityId, x, z, entityId, ...]
    String[] enemyTypes  // parallel to enemyMap triplets: entity type IDs
}
```

### When sent
- Player's turn starts (full tile set for current mode)
- After player takes an action (move/attack changes available tiles)
- Player switches held item (move mode vs attack mode)
- Combat ends or turn ends (empty sets — clears all highlights)

### Server-side changes
- `refreshHighlights()` stops placing carpet blocks. Instead, it builds the tile arrays from existing pathfinding/range computation and sends `TileSetPayload`
- `clearHighlights()` sends an empty `TileSetPayload`
- The pathfinding and range computation logic is unchanged — only the output format changes (arrays instead of block placement)

### Client-side caching
- `CombatState` stores the latest tile sets: `Set<GridPos> moveTiles`, `Set<GridPos> attackTiles`, `Set<GridPos> dangerTiles`, `Map<GridPos, Integer> enemyGridMap` (pos → entityId), `Map<GridPos, String> enemyTypeMap` (pos → type ID)
- Updated when `TileSetPayload` arrives
- Cleared on `ExitCombatPayload`

---

## 4. Hover Flow Overhaul

### New flow (zero latency)
1. `TileRaycast` computes grid position every frame (unchanged)
2. `CombatInputHandler` updates `CombatState.hoveredTile` locally — no packet sent to server
3. `TileOverlayRenderer` reads `CombatState.hoveredTile` and draws hover quad immediately
4. Client checks `hoveredTile` against cached `attackTiles` and `enemyGridMap` to determine target — updates inspect panel instantly
5. On click: client reads `entityId` from `enemyGridMap` for the hovered tile and sends `CombatActionPayload(ATTACK, x, z, entityId)` — server validates as before

### What gets deleted
- `ACTION_HOVER` constant in `CombatActionPayload` — no more hover packets for highlights
- `handleHover()` method in `CombatManager` — server no longer processes hover
- Hover darkening logic in `refreshHighlights()`
- `findEnemyAtGridPos()` in `CombatInputHandler` — replaced by `enemyGridMap` lookup

### What stays unchanged
- `TileRaycast.java` — already works well
- Click-to-action sends `CombatActionPayload(MOVE/ATTACK)` to server — server remains authoritative for all game actions
- Server validates moves, attacks, AP costs, range — no client trust

### Enemy detection fix
The "enemy not on tile" bug is completely eliminated. The client no longer scans world entities to figure out who's where. It reads `enemyGridMap` from the server's `TileSetPayload`. If the server says entity #42 is at (3,5), the client trusts that. Click on (3,5) → send attack with entityId=42 → server validates.

---

## 5. Teammate Hover Sharing

### New payloads
- `HoverUpdatePayload(int gridX, int gridZ)` — C2S, sent by client at 4-5 Hz (every ~200ms) when hovered tile changes
- `TeammateHoverPayload(UUID playerUuid, int gridX, int gridZ)` — S2C, relayed by server to party members only

### Client sending
- `CombatInputHandler` tracks a `lastHoverBroadcastTime` timestamp
- Every 200ms, if `hoveredTile` has changed since last broadcast, send `HoverUpdatePayload`
- Only sent when in a party (skip for solo players — no one to relay to)

### Server relay
- `ModNetworking` handler for `HoverUpdatePayload`:
  - Look up player's party via `CrafticsSavedData.getPlayerParty(uuid)`
  - For each online party member (except sender), send `TeammateHoverPayload`
  - No game logic, no validation — pure relay

### Client rendering
- `CombatState` stores `Map<UUID, GridPos> teammateHovers` and `Map<UUID, Long> teammateHoverTimestamps`
- On receiving `TeammateHoverPayload`: update map + timestamp
- `TileOverlayRenderer` draws dim colored overlays for each teammate hover
- Entries older than 500ms fade out (teammate stopped moving cursor or disconnected)
- Teammate name rendered as small text above the hover indicator

### Performance
- 4-5 packets/sec per player, ~30 bytes each
- 4 players = ~600 bytes/sec total relay traffic. Negligible.

---

## 6. Files Overview

| File | Action | Responsibility |
|------|--------|---------------|
| `TileOverlayRenderer.java` (new) | Create | Client-side quad rendering for all overlay types |
| `TileSetPayload.java` (new) | Create | S2C payload with move/attack/danger tiles + enemy map |
| `HoverUpdatePayload.java` (new) | Create | C2S payload for teammate hover broadcast |
| `TeammateHoverPayload.java` (new) | Create | S2C payload relaying teammate hover to party members |
| `CombatState.java` | Modify | Cache tile sets, enemy grid map, teammate hovers |
| `CombatInputHandler.java` | Modify | Remove server hover packets, use local state + enemyGridMap |
| `CombatManager.java` | Modify | refreshHighlights sends TileSetPayload instead of placing carpets |
| `TileHighlightManager.java` | Delete | Carpet block system fully replaced |
| `CombatActionPayload.java` | Modify | Remove ACTION_HOVER constant |
| `ModNetworking.java` | Modify | Register new payloads, add hover relay handler |
| `CrafticsClient.java` | Modify | Register client payload handlers, hook up renderer |

---

## 7. What This Does NOT Change

- **Combat logic:** Turn system, AP costs, damage, pathfinding, enemy AI — all untouched
- **Action validation:** Server still validates every move/attack. Client cannot cheat.
- **TileRaycast.java:** Already works well. No changes needed.
- **CombatSyncPayload:** Still used for HP, effects, phase changes. TileSetPayload is additive.
- **Arena building:** Physical arena blocks (floor, obstacles) unchanged. Only the highlight layer moves to client rendering.
