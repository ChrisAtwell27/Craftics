# Craftics — Minecraft Mod Plan of Action

## Overview

A Fabric 1.20.1 client/singleplayer mod that adds a new world type called **"Craftics."** When selected, Minecraft becomes a turn-based tactical combat game played on 3D grid arenas with a locked isometric-style camera. Levels are themed around Minecraft's achievement progression leading up to the Ender Dragon.

Between levels, players use vanilla crafting, smelting, and brewing mechanics. Your real inventory is your inventory. Gear you equip determines your combat stats.

---

## Technical Stack

- **Fabric 1.20.1** + Fabric API
- **Mixins** for camera lock, input override, mouse cursor unlock
- **Singleplayer only** — no custom networking needed, client calls integrated server directly via `Minecraft.getInstance().getSingleplayerServer()`
- **Vanilla systems reused:** Inventory, crafting table GUI, furnace GUI, brewing stand GUI, potion effects, item stats

---

## Game Flow

```
[World Created] → [Hub Room Spawns] → [Player Crafts/Prepares]
       ↓
[Player Interacts with Level Select Block]
       ↓
[Arena Generates at Offset Location] → [Player Teleported In]
       ↓
[Camera Locks, Input Overridden, Combat Begins]
       ↓
[Turn-Based Combat Loop]
   → Player Turn: Move / Attack / Use Item (3 AP)
   → Enemy Turn: AI acts per unit
   → Repeat until win/loss
       ↓
[Victory] → [Loot Drops to Inventory] → [Teleport to Hub]
       ↓
[Next Level Unlocked on Level Select Block]
       ↓
[Repeat until Level 4 Complete]
```

---

## Levels

> **Note:** Each dimension theme (Overworld, Cave, Nether, End) will eventually contain multiple levels, not just one level per dimension. The level system should be designed to support subsets of levels per dimension theme.

### Level 1 — "Stone Age" (Overworld Plains)

- **Grid:** 8×8, grass block floor
- **Obstacles:** None — open field
- **Enemies:**
  - **Zombies** (×3) — Melee. 1 tile move. Attack adjacent. 10 HP. Predictable beeline AI.
  - **Skeletons** (×2) — Ranged. 1 tile move. Shoot up to 3 tiles in a line (blocked by obstacles). 8 HP. Tries to maintain 2-3 tile distance.
- **Gimmick:** None. This is the tutorial level. Learn movement, attacking, and AP management.
- **Win Condition:** Kill all enemies.
- **Loot Drops:** Cobblestone ×32, Oak Log ×16, Leather ×5, String ×4, Feathers ×6, Flint ×4, Coal ×8

### Level 2 — "Into the Depths" (Cave/Mine)

- **Grid:** 10×10, stone block floor
- **Obstacles:** Ore blocks (iron, coal, gold) scattered across the grid. Block movement and line of sight.
- **Enemies:**
  - **Creepers** (×3) — Melee. 1 tile move. When adjacent to player, start a 2-turn fuse (they flash/glow). On detonation: 3×3 AoE damage AND destroys ore block obstacles in range. 12 HP. Can be killed before detonation.
  - **Spiders** (×3) — Melee. 2 tile move. Can move through/over obstacle blocks (climbing). 8 HP. Flanking AI — tries to get behind the player.
- **Gimmick:** Destructible terrain. Creeper explosions reshape the arena. Players can bait creepers into blowing open paths or clearing cover that enemies are hiding behind.
- **Win Condition:** Kill all enemies OR reach the exit tile (a ladder block on the far side of the grid).
- **Loot Drops:** Iron Ore ×16, Gold Ore ×4, Redstone ×8, Lapis ×8, Diamond ×2, Coal ×16

### Level 3 — "Nether Fortress"

- **Grid:** 10×12, nether brick floor
- **Hazard Tiles:** Lava blocks (scattered). Stepping on lava deals 3 damage to ANY unit (player or enemy). Fire tiles can be created by Blaze attacks and last 2 turns.
- **Enemies:**
  - **Blazes** (×2) — Ranged. 1 tile move (floating). Shoot fireballs up to 4 tiles. Fireball leaves a fire tile on the target block for 2 turns. 10 HP.
  - **Wither Skeletons** (×2) — Melee. 2 tile move. Attack applies "Wither" debuff (reduce max HP by 2 for the rest of the level). 14 HP. Aggressive rush AI.
  - **Magma Cubes** (×2) — Melee. 1 tile move. On death, splits into 2 smaller Magma Cubes (4 HP each, 1 tile move, 1 damage). Immune to lava/fire tiles.
- **Gimmick:** Fire and lava hazard management. Positioning is critical. Knockback attacks can push enemies into lava. Blazes create dangerous zones that reshape safe movement paths each turn.
- **Win Condition:** Kill all enemies.
- **Loot Drops:** Blaze Rod ×6, Nether Wart ×4, Glowstone Dust ×12, Magma Cream ×4, Ghast Tear ×1, Diamond ×3

### Level 4 — "The End"

- **Grid:** 12×12, end stone floor
- **Hazard Tiles:** Void tiles around the outer 1-tile border. Any unit knocked into void = instant death.
- **Phase 1:**
  - **Endermen** (×4) — Melee. 2 tile move. When attacked from the front, teleport to a random empty tile. Must be attacked from behind (player positions behind them first) or trapped against a wall. 12 HP.
  - **End Crystals** (×4) — Stationary. Placed on the 4 edges of the arena. Each turn, heal ALL enemies for 2 HP. Must be destroyed (5 HP each, attackable like enemies). Explode on death dealing 2 damage in a 2-tile radius.
- **Phase 2 (triggers when all Endermen + Crystals dead):**
  - **Ender Dragon** — Large unit, occupies 2×2 tiles. Moves every other turn (alternates between move turn and attack turn). Attack turn: Breath attack hits an entire row or column (player chooses to dodge). Move turn: Repositions and summons 2 Endermites (1 HP, 1 move, 1 damage, nuisance fodder). 40 HP.
- **Gimmick:** Void edges for knockback kills. Crystal priority targeting. Dragon phase transition. Enderman facing/positioning puzzle.
- **Win Condition:** Kill the Ender Dragon.
- **Loot Drops:** Bragging rights. Dragon Egg placed in hub room.

---

## Combat System

### Action Points (AP)

- Player gets **3 AP** per turn
- **Move:** 1 AP per tile moved (can move multiple tiles per turn)
- **Attack:** 2 AP (melee or ranged depending on weapon)
- **Use Item:** 1 AP (drink potion from hotbar)
- **Block:** 1 AP (if shield equipped — halve next incoming damage)
- **End Turn:** Remaining AP is lost (no banking)

### Turn Order

1. **Player Turn** — spend AP freely
2. **Enemy Turn** — each enemy acts in sequence (ordered by speed/type)
3. Repeat

### Damage Calculation

```
Damage = Weapon Attack Power - Target Defense
Minimum damage = 1
```

- **Attack Power** is read from the vanilla item in the player's main hand:
  - Fist = 1
  - Wood Sword = 2
  - Stone Sword = 3
  - Iron Sword = 4
  - Diamond Sword = 5
  - Bow (if arrows in inventory) = 3, Range 3 tiles
- **Defense** is read from the player's equipped vanilla armor:
  - No armor = 0
  - Full leather = 3
  - Full iron = 7
  - Full diamond = 10
  - Shield in offhand = enables Block action
- Enemy attack/defense values are hardcoded per enemy type

### Special Weapon Abilities

- **Iron Sword:** Unlocks "Cleave" — attack hits the target AND one adjacent tile in the same direction
- **Diamond Sword:** Cleave + 20% chance to crit (double damage)
- **Bow:** Ranged attack (3 tiles, line of sight required). Consumes 1 arrow from inventory per shot.
- **Trident (if crafted/found):** Can be thrown 4 tiles, returns to player next turn. Player has no weapon equipped until it returns.

### Potion Effects

Vanilla potions work as-is. The combat system checks for active potion effects:

- **Strength** → +3 attack for duration
- **Swiftness** → +1 AP per turn for duration
- **Fire Resistance** → Immune to lava and fire tiles
- **Regeneration** → Heal 1 HP per turn
- **Healing (Instant)** → Restore 4 HP immediately on use

### Death / Game Over

- If player HP reaches 0, they respawn in the hub room
- All items are kept (no drops)
- Level must be restarted from scratch
- No permadeath, no penalty beyond time lost

---

## Hub Room

### Layout

A 9×9×5 bedrock-walled room generated at world origin (0, 64, 0). Contains:

- **Crafting Table** — vanilla, placed in room
- **Furnace** — vanilla, placed in room
- **Chest** — vanilla, for overflow storage
- **Brewing Stand** — vanilla, appears after Level 3 is beaten
- **Level Select Block** — custom block (unique texture). Right-click opens a GUI showing available levels (locked levels grayed out)
- **Armor Stand** — display your loadout
- **Door** — sealed. Purely decorative. You leave via the Level Select Block.

### Unlocking Progression

| Level Beaten | Unlocks |
|---|---|
| Start | Level 1 available. Crafting table + furnace in hub. |
| Level 1 | Level 2 available. Stone/leather tier resources in inventory. |
| Level 2 | Level 3 available. Iron tier resources. |
| Level 3 | Level 4 available. Diamond tier + brewing stand appears in hub. |
| Level 4 | Victory screen. Dragon egg trophy in hub. |

---

## Technical Architecture

### Mod Entry Points

- **Main Entrypoint** (`ModInitializer`): Register custom blocks (Level Select Block), custom world type, game state manager
- **Client Entrypoint** (`ClientModInitializer`): Register mixins, HUD overlay renderer, keybinds, block entity screen handlers

### Custom World Type

- Register a new `WorldPreset` called "Craftics"
- Custom `ChunkGenerator` that produces a void world (no terrain, no structures, no mob spawning)
- On world creation event, build the hub room at origin and teleport player there
- Set player to Adventure mode by default in hub (prevent breaking hub blocks). Switch to a custom mode or keep adventure during combat.

### Grid Data Model

```
GridArena
├── width: int
├── height: int
├── tiles: GridTile[][]
├── occupants: Map<GridPos, Entity>
├── turnOrder: List<Entity>
├── currentTurn: int
├── phase: PLAYER_TURN | ENEMY_TURN | ANIMATING | LEVEL_COMPLETE | GAME_OVER
│
GridTile
├── type: NORMAL | OBSTACLE | LAVA | FIRE | VOID | EXIT
├── blockType: Block (what Minecraft block to place)
├── walkable: boolean
├── damageOnStep: int
├── turnsRemaining: int (for temporary tiles like fire)
│
GridPos
├── x: int
├── z: int
├── toBlockPos(arenaOrigin): BlockPos
```

### Arena Builder

- Each level has a `LevelDefinition` that specifies grid size, tile layout, enemy spawns, and loot table
- When a level starts:
  1. Calculate arena origin (e.g., Level 1 at 1000/100/0, Level 2 at 2000/100/0, etc.)
  2. Place blocks in world matching the tile layout
  3. Place barrier blocks around perimeter (invisible walls)
  4. Spawn enemy entities at defined positions with vanilla AI stripped
  5. Teleport player to start position
  6. Trigger combat mode on client

### Mixins Required

#### Camera Lock Mixin
- **Target:** `net.minecraft.client.Camera` → `setup()` method
- **Action:** When combat mode is active, override pitch to ~60° and yaw to a fixed cardinal direction. Disable all camera rotation input.
- **Fallback:** If `Camera.setup()` isn't clean enough, mixin into `GameRenderer.renderLevel()` to modify the view matrix post-setup.

#### Mouse Cursor Unlock Mixin
- **Target:** `net.minecraft.client.MouseHandler` → `grabMouse()` / `releaseMouse()`
- **Action:** When combat mode active, release the mouse grab so cursor moves freely across the screen. Need to call `GLFW.glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL)`.
- **Also:** Intercept mouse clicks to perform tile raycasting instead of normal interaction.

#### Movement Disable Mixin
- **Target:** `net.minecraft.client.player.KeyboardInput` → `tick()` method
- **Action:** When combat mode active, zero out all movement vectors (forwardImpulse, leftImpulse, jumping, sneaking). Player cannot move via WASD/space/shift.

#### Entity AI Override
- **Target:** `net.minecraft.world.entity.Mob` → `registerGoals()` or use `EntityJoinLevelEvent` equivalent
- **Action:** For entities spawned in a grid arena, clear all AI goal selectors and replace with a no-op. Movement is controlled entirely by the turn system.
- **Also:** Make entities invulnerable to all damage sources except our combat resolution system (prevent fall damage, suffocation, etc. from interfering).

### HUD Overlay

Rendered via Fabric's `HudRenderCallback` during combat mode:

- **Top bar:** Level name, turn counter, "Player Turn" / "Enemy Turn" indicator
- **Bottom bar:** AP remaining (shown as pips), action buttons (Move / Attack / Use Item / Block / End Turn)
- **Enemy HP bars:** Floating above each enemy entity (rendered in world space or as GUI overlay matched to screen position)
- **Tile highlights:** When in "Move" mode, highlight reachable tiles in blue. When in "Attack" mode, highlight attackable tiles/enemies in red.
- **Grid overlay:** Semi-transparent grid lines rendered on top of the arena floor blocks

### Game State Persistence

- Use a custom `PersistentState` (saved data) attached to the overworld
- Stores:
  - Current level unlocked (1-4)
  - Levels completed (boolean per level)
  - Hub state (has brewing stand appeared, etc.)
  - Arena state if player saves mid-combat (grid state, enemy HP, turn count)
- Saved to disk with the world, loads on world open

### Click-to-Move System

1. Player is in "Move" mode (default at turn start)
2. Mouse cursor is free. Player hovers over tiles.
3. Raycast from camera through cursor position to find which block/tile is being pointed at
4. Highlight reachable tiles (within AP budget) in blue, hovered tile in bright highlight
5. Player clicks a reachable tile
6. Calculate path (simple A* on the grid)
7. Deduct AP for tiles traversed
8. Smoothly move the player entity along the path (lerp position over several ticks)
9. After arrival, player can continue spending AP or end turn

### Click-to-Attack System

1. Player switches to "Attack" mode (keybind or button)
2. Attackable enemies highlighted in red (within weapon range, line of sight)
3. Player clicks an enemy
4. Play attack animation (player swings weapon)
5. Resolve damage on server
6. Deduct 2 AP
7. If enemy dies, play death animation and remove from grid
8. Player can continue spending AP or end turn

---

## File Structure

```
craftics/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── src/main/
│   ├── java/com/crackedgames/craftics/
│   │   ├── CrafticsMod.java              — Main mod entrypoint
│   │   ├── CrafticsClient.java           — Client entrypoint
│   │   │
│   │   ├── core/
│   │   │   ├── GameStateManager.java        — Singleton managing game flow state machine
│   │   │   ├── CombatManager.java           — Turn logic, AP, combat resolution
│   │   │   ├── GridArena.java               — Grid data structure
│   │   │   ├── GridTile.java                — Tile enum/data
│   │   │   ├── GridPos.java                 — Grid coordinate helper
│   │   │   └── PlayerCombatData.java        — Read player gear → combat stats
│   │   │
│   │   ├── level/
│   │   │   ├── LevelDefinition.java         — Abstract level layout definition
│   │   │   ├── LevelRegistry.java           — All 4 levels registered here
│   │   │   ├── ArenaBuilder.java            — Places blocks in world from LevelDefinition
│   │   │   ├── Level1Plains.java            — Stone Age layout + enemies
│   │   │   ├── Level2Mines.java             — Into the Depths layout + enemies
│   │   │   ├── Level3Nether.java            — Nether Fortress layout + enemies
│   │   │   └── Level4End.java               — The End layout + dragon boss
│   │   │
│   │   ├── entity/
│   │   │   ├── GridEntityAI.java            — Base grid AI (pathfinding, targeting)
│   │   │   ├── ZombieGridAI.java            — Beeline melee
│   │   │   ├── SkeletonGridAI.java          — Kiting ranged
│   │   │   ├── CreeperGridAI.java           — Rush + fuse + explode
│   │   │   ├── SpiderGridAI.java            — Flanking, climb obstacles
│   │   │   ├── BlazeGridAI.java             — Ranged fire, create fire tiles
│   │   │   ├── WitherSkeletonGridAI.java    — Aggressive rush, wither debuff
│   │   │   ├── MagmaCubeGridAI.java         — Split on death
│   │   │   ├── EndermanGridAI.java          — Teleport when front-attacked
│   │   │   └── EnderDragonGridAI.java       — 2-phase boss logic
│   │   │
│   │   ├── client/
│   │   │   ├── CombatHudOverlay.java        — AP bar, turn indicator, action buttons
│   │   │   ├── GridHighlightRenderer.java   — Tile highlights (move blue, attack red)
│   │   │   ├── EnemyHpBarRenderer.java      — Floating HP bars above enemies
│   │   │   ├── TileSelectionHandler.java    — Raycast cursor → grid tile logic
│   │   │   └── LevelSelectScreen.java       — GUI for the Level Select Block
│   │   │
│   │   ├── mixin/
│   │   │   ├── CameraLockMixin.java         — Lock camera pitch/yaw during combat
│   │   │   ├── MouseUnlockMixin.java        — Free cursor during combat
│   │   │   ├── MovementDisableMixin.java    — Kill WASD during combat
│   │   │   └── MobAIDisableMixin.java       — Strip AI from arena mobs
│   │   │
│   │   ├── block/
│   │   │   ├── LevelSelectBlock.java        — Custom block, opens level select GUI
│   │   │   └── LevelSelectBlockEntity.java  — Stores unlock state (or reads from GameState)
│   │   │
│   │   └── world/
│   │       ├── CrafticsWorldPreset.java      — Custom world type registration
│   │       ├── VoidChunkGenerator.java       — Generates empty void world
│   │       ├── HubRoomBuilder.java           — Builds the hub room at origin
│   │       └── CrafticsSavedData.java     — Persistent game state (level progress, etc.)
│   │
│   └── resources/
│       ├── fabric.mod.json                   — Mod metadata
│       ├── craftics.mixins.json           — Mixin config
│       └── assets/craftics/
│           ├── lang/en_us.json               — Translations
│           ├── textures/
│           │   ├── block/level_select.png    — Level Select Block texture
│           │   └── gui/
│           │       ├── hud_icons.png         — AP pips, action icons
│           │       ├── level_select_gui.png  — Level select screen background
│           │       └── tile_highlight.png    — Tile selection overlay
│           ├── blockstates/level_select.json
│           └── models/block/level_select.json
```

---

## Build Order

### Phase 1 — Foundation (Get Something on Screen)
1. **Mod skeleton** — `fabric.mod.json`, entrypoints, build.gradle, mixin config
2. **Void world type** — custom `ChunkGenerator` producing empty world
3. **Hub room builder** — generate the bedrock room with vanilla blocks on world create
4. **Level Select Block** — custom block with texture, right-click opens placeholder GUI

### Phase 2 — Camera & Input (Highest Risk — Prove Early)
5. **Camera lock mixin** — force fixed isometric angle during combat flag
6. **Mouse unlock mixin** — free cursor when combat active
7. **Movement disable mixin** — zero WASD input during combat
8. **Combat mode toggle** — keybind or trigger to flip between hub mode and combat mode for testing

### Phase 3 — Grid & Arena
9. **Grid data model** — `GridArena`, `GridTile`, `GridPos` classes
10. **Arena builder** — read a `LevelDefinition`, place real blocks in the world
11. **Level 1 definition** — 8×8 grass grid, enemy spawn points
12. **Player teleport into arena** — triggered from Level Select GUI

### Phase 4 — Combat Core
13. **Turn system** — state machine (PLAYER_TURN, ENEMY_TURN, ANIMATING)
14. **AP tracking** — 3 AP per turn, deduct on action
15. **Click-to-move** — raycast tile selection → pathfind → lerp player to tile
16. **Click-to-attack** — target enemy → damage resolution → death handling
17. **Player combat stats** — read equipped vanilla items for attack/defense values
18. **End turn** — trigger enemy turn sequence

### Phase 5 — Enemy AI
19. **Mob AI disable mixin** — strip vanilla AI from arena-spawned mobs
20. **Grid AI base class** — pathfinding on grid, targeting logic
21. **Zombie AI** — beeline toward player, attack when adjacent
22. **Skeleton AI** — maintain distance, shoot in line if clear LOS

### Phase 6 — HUD & Polish
23. **HUD overlay** — AP pips, turn indicator, action mode buttons
24. **Tile highlights** — blue for movement range, red for attack range
25. **Enemy HP bars** — floating bars above each enemy
26. **Level 1 fully playable loop** — start → fight → win → loot → return to hub

### Phase 7 — Remaining Content
27. **Level Select GUI** — show locked/unlocked levels, start level on click
28. **Game state persistence** — save/load progress with the world
29. **Level 2** — mine grid + creeper explosion + spider climb + destructible terrain
30. **Level 3** — nether grid + lava/fire tiles + blaze AI + wither skeleton debuff + magma cube split
31. **Level 4** — end grid + void tiles + enderman teleport + crystal healing + dragon 2-phase boss
32. **Victory state** — dragon egg in hub, credits or message

### Phase 8 — Final Polish
33. Sound effects for combat actions
34. Particle effects (attack impacts, explosions, fire)
35. Smooth entity movement animations
36. Level transition effects (fade to black, etc.)
37. Playtesting and balance tuning

---

## Risk Assessment

| Risk | Severity | Mitigation |
|---|---|---|
| Camera mixin doesn't work cleanly | **HIGH** | Test in Phase 2 before building anything else. Fallback: override view matrix in `GameRenderer` instead of `Camera`. Worst case: render a 2D GUI overlay of the arena. |
| Mouse cursor unlock conflicts with GUI system | **MEDIUM** | Fabric has clean access to GLFW. May need to track state carefully to re-lock on pause/inventory screens. |
| Vanilla mob AI leaks through during combat | **MEDIUM** | Make all arena mobs invulnerable via `LivingEntity.hurt()` override. Only our combat system deals damage. Also remove all goals and disable `mob.setNoAi(true)` as baseline. |
| Entity movement jank (teleporting vs lerping) | **MEDIUM** | Use `Entity.moveTo()` on server, smooth interpolation on client via position lerp in render tick. May need mixin into entity renderer for extra smoothness. |
| Grid raycasting inaccuracy | **LOW** | Floor is flat at a known Y level. Raycast to that Y plane and snap to grid. Should be reliable. |
| Save state corruption mid-combat | **LOW** | On save during combat, store full grid state. On load, rebuild arena and restore positions. Or: force-end combat on save and return player to hub. |

---

## Estimated Effort

| Phase | Estimated Time |
|---|---|
| Phase 1 — Foundation | 2–3 days |
| Phase 2 — Camera & Input | 3–5 days (mixin debugging) |
| Phase 3 — Grid & Arena | 2–3 days |
| Phase 4 — Combat Core | 5–7 days |
| Phase 5 — Enemy AI | 3–4 days |
| Phase 6 — HUD & Polish | 3–4 days |
| Phase 7 — Content (Levels 2–4) | 5–7 days |
| Phase 8 — Final Polish | 3–5 days |
| **Total** | **~26–38 days** |

This assumes focused work sessions. Calendar time could stretch to 2-3 months with a normal schedule.