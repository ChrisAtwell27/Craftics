# Craftics Modding Guide

Craftics is fully data-driven and supports three levels of modding:

1. **Custom arena maps** — Build .nbt structures in Minecraft, no code required
2. **Datapack biome modding** — JSON files for custom biomes, enemies, and loot
3. **Fabric mod integration** — Java API for custom AI and programmatic biomes

---

## Table of Contents

- [Custom Arena Maps](#custom-arena-maps)
  - [Overview](#overview)
  - [Step-by-Step Guide](#step-by-step-guide)
  - [Marker Blocks](#marker-blocks)
  - [File Naming & Directory Structure](#file-naming--directory-structure)
  - [Multiple Map Variations](#multiple-map-variations)
  - [Boss-Specific Maps](#boss-specific-maps)
  - [Tips for Map Builders](#tips-for-map-builders)
- [Custom Biomes (JSON)](#custom-biomes-json)
  - [Biome JSON Schema](#biome-json-schema)
  - [Field Reference](#field-reference)
  - [Creating a New Biome](#creating-a-new-biome)
  - [Overriding Built-In Biomes](#overriding-built-in-biomes)
  - [Multiple Biome Variations](#multiple-biome-variations)
  - [Enemy Definitions](#enemy-definitions)
  - [Loot Tables](#loot-tables)
- [Datapack Setup](#datapack-setup)
- [Hot Reload](#hot-reload)
- [Fabric Mod API (Java)](#fabric-mod-api-java)

---

## Custom Arena Maps

### Overview

Arena maps are `.nbt` structure files built using Minecraft's Structure Block. You design the entire scene — terrain, decorations, atmosphere — then place two marker blocks to define where the combat grid sits.

The mod overlays gameplay tiles (floor blocks, obstacles) onto your marker-defined area while keeping everything outside the grid as visual decoration.

### Step-by-Step Guide

#### 1. Build your arena in creative mode

Create the full scene including:
- A flat area where combat will happen (the grid)
- Surrounding terrain and decorations (trees, ruins, water, cliffs, etc.)
- Any blocks you want — the build can be any size

#### 2. Place marker blocks

There are two types of markers:

**Boundary Markers** (placed OUTSIDE the grid, on the corners):

| Block | Purpose |
|-------|---------|
| **Emerald Block** | Arena boundary corner. Place at least 2 on opposite corners outside the playable area. |

The playable grid is the rectangle **inside** the emerald boundary. Emeralds are replaced with stone brick border blocks at runtime.

**Player Spawn Markers** (placed INSIDE the grid):

| Block | Player | Purpose |
|-------|--------|---------|
| **Gold Block** | Player 1 | Primary spawn point |
| **Iron Block** | Player 2 | Second player spawn (multiplayer) |
| **Copper Block** | Player 3 | Third player spawn (multiplayer) |
| **Coal Block** | Player 4 | Fourth player spawn (multiplayer) |

Spawn markers are replaced with floor blocks at runtime. Their grid positions are recorded as spawn points.

All markers must be at the **same Y level** — this becomes the combat floor.

#### 3. Export your build

**Option A: WorldEdit (recommended)**

1. Select your build: `//pos1` and `//pos2`
2. Copy: `//copy`
3. Save: `//schem save <name>`
4. Find the `.schem` file in `config/worldedit/schematics/`
5. Copy it to `craftics_arenas/<biome>/1.schem` in your game's run directory

> Craftics natively supports WorldEdit's `.schem` format — no conversion needed!

**Option B: Vanilla Structure Block** (48×48×48 limit)

1. `/give @s structure_block`
2. Set to **Save** mode, define bounding box, name it, click **SAVE**
3. Find the `.nbt` file in `<world>/generated/minecraft/structures/`
4. Copy it to `data/craftics/structures/arenas/<biome>/1.nbt`

#### 4. Place the file in the correct directory

Copy the file to one of these locations:

**For `.schem` (WorldEdit):**
```
craftics_arenas/<biome_id>/<number>.schem
```
This goes in your game's **run directory** (same folder as `mods/`, `config/`, etc.)

**For `.nbt` (Structure Block):**
```
data/craftics/structures/arenas/<biome_id>/<number>.nbt
```
This goes inside a **datapack**.

Example for the first Plains variation:
```
craftics_arenas/plains/1.schem
```

#### 5. Test it

Enter combat in that biome — the mod automatically discovers and uses your structure.

### Marker Blocks

```
  ┌─────────────────────────────┐
  │      Decorations...          │
  │  EMERALD─────────────EMERALD │  ← Emerald corners OUTSIDE the grid
  │  │  GOLD                  │  │  ← Gold = Player 1 spawn (inside grid)
  │  │     (playable grid)    │  │
  │  │  IRON                  │  │  ← Iron = Player 2 spawn (optional)
  │  │                        │  │
  │  EMERALD─────────────EMERALD │  ← More emerald corners
  │      More terrain...         │
  └─────────────────────────────┘
```

- **Emerald blocks** define the outer boundary — the grid is the rectangle INSIDE them
- Emeralds are replaced with **stone brick border** blocks at runtime
- **Gold/Iron/Copper/Coal** blocks mark player spawn positions inside the grid
- Spawn markers are replaced with **floor blocks** at runtime
- Everything outside the emerald boundary is kept as decoration
- Need at least **2 emerald blocks** on opposite corners
- If markers are missing, the mod falls back to centering the grid

### File Naming & Directory Structure

**WorldEdit `.schem` files** (in game run directory):
```
craftics_arenas/
  plains/
    1.schem            ← Regular map variation 1
    2.schem            ← Regular map variation 2
    3.schem            ← Regular map variation 3
    boss_1.schem       ← Boss-only map
  forest/
    1.schem
    2.schem
```

**Structure Block `.nbt` files** (in datapack):
```
data/craftics/structures/arenas/
  plains/
    1.nbt
    boss_1.nbt
  desert/
    1.nbt
```

Both formats use the same numbering convention and marker block system. `.schem` files are checked first.

**Biome IDs** (match the `id` field in biome JSON):

| Overworld | Nether | End |
|-----------|--------|-----|
| `plains` | `nether_wastes` | `outer_end_islands` |
| `forest` | `soul_sand_valley` | `end_city` |
| `desert` | `crimson_forest` | `chorus_grove` |
| `jungle` | `warped_forest` | `dragons_nest` |
| `river` | `basalt_deltas` | |
| `mountain` | | |
| `snowy` | | |
| `cave` | | |
| `deep_dark` | | |

### Multiple Map Variations

Create numbered files for the same biome to add variety:

```
arenas/plains/1.nbt   → Open meadow with scattered oaks
arenas/plains/2.nbt   → Riverside clearing with bridge
arenas/plains/3.nbt   → Hilltop with stone ruins
arenas/plains/4.nbt   → Wheat farm battlefield
```

**Selection rules:**
- Files are scanned sequentially: `1.nbt`, `2.nbt`, `3.nbt`, ...
- Scanning stops at the first missing number
- One is picked **randomly** each fight
- The same biome shows a different map every time

### Boss-Specific Maps

The final level of each biome (the boss fight) can have dedicated maps:

```
arenas/forest/boss_1.nbt   → Epic boss arena with pillars
arenas/forest/boss_2.nbt   → Different boss layout
```

**Priority order for boss levels:**
1. `boss_1.nbt`, `boss_2.nbt`, etc. (boss-specific)
2. `1.nbt`, `2.nbt`, etc. (regular maps, used if no boss maps exist)
3. Procedural generation (used if no maps exist at all)

### Tips for Map Builders

- **Size your grid appropriately.** Plains starts at 8x8 and grows +1 width per level. Design maps to accommodate the full range.
- **Keep the camera in mind.** The isometric camera looks down at ~55 degrees from the SW. Tall structures on the north/east edges may block the view.
- **Floor level is the marker Y.** Below = underground. Above = air/decoration.
- **Obstacles are overlaid by the game.** The biome's obstacle blocks get placed on your structure's grid area based on the biome's density settings.
- **Add atmosphere.** Lanterns, torches, campfires, and glowstone outside the grid won't affect gameplay but make the map feel alive.
- **Structure Block limit is 48x48x48.** For larger builds, use mods that extend this or keep decorations compact.
- **Under-floor lighting is automatic.** The mod places glowstone under the floor every 3 blocks.

---

## Custom Biomes (JSON)

### Biome JSON Schema

```json
{
  "id": "my_biome",
  "name": "My Custom Biome",
  "order": 20,
  "levels": 5,
  "grid": {
    "base_width": 10,
    "base_height": 10,
    "width_growth": 1,
    "height_growth": 1
  },
  "floor_blocks": ["minecraft:stone_bricks", "minecraft:mossy_stone_bricks"],
  "obstacle_blocks": ["minecraft:cobblestone_wall"],
  "obstacle_density": 0.1,
  "obstacle_density_growth": 0.02,
  "environment": "cave",
  "night": true,
  "enemies": {
    "passive": [
      {"type": "minecraft:bat", "weight": 3, "hp": 2, "attack": 0, "defense": 0, "range": 1}
    ],
    "hostile": [
      {"type": "minecraft:skeleton", "weight": 8, "hp": 10, "attack": 3, "defense": 0, "range": 3},
      {"type": "minecraft:spider", "weight": 6, "hp": 8, "attack": 3, "defense": 0, "range": 1}
    ],
    "boss": {"type": "minecraft:warden", "hp": 50, "attack": 8, "defense": 4, "range": 2}
  },
  "loot": [
    {"item": "minecraft:diamond", "weight": 5},
    {"item": "minecraft:iron_ingot", "weight": 15}
  ]
}
```

### Field Reference

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | **Required.** Unique identifier. Used for structure file lookup and overriding. |
| `name` | string | Display name shown in HUD, victory screen, and level select. |
| `order` | int | Sort position in progression. Lower = earlier. Biomes are auto-assigned level ranges based on this. |
| `levels` | int | Number of levels in this biome (typically 5). Last level is the boss. |
| `grid.base_width` | int | Starting grid width (tiles) for level 1. |
| `grid.base_height` | int | Starting grid height. |
| `grid.width_growth` | int | Width increases by this each level within the biome. |
| `grid.height_growth` | int | Height increase per level. |
| `floor_blocks` | string[] | Block IDs for floor tiles. Alternated in a checkerboard pattern. |
| `obstacle_blocks` | string[] | Block IDs for obstacle tiles. |
| `obstacle_density` | float | Base probability (0.0-1.0) of a tile being an obstacle. |
| `obstacle_density_growth` | float | Density increase per level within the biome. |
| `environment` | string | Visual style for procedural arenas (see table below). |
| `night` | bool | If true, combat happens at night. Prevents undead from burning. |
| `enemies.passive` | array | Passive mobs for early levels. |
| `enemies.hostile` | array | Hostile mobs, weighted random selection. |
| `enemies.boss` | object | Boss mob spawned on the final level. |
| `loot` | array | Weighted loot table rolled on level completion. |

**Environment styles:** `plains`, `forest`, `desert`, `jungle`, `river`, `mountain`, `snowy`, `cave`, `nether_wastes`, `end`, `deep_dark`

### Creating a New Biome

1. Create a `.json` file with a unique `id`
2. Set `order` to control where it appears in progression (gaps between numbers are fine)
3. Define enemies with appropriate stats for your intended difficulty
4. Place at `data/craftics/craftics/biomes/my_biome.json`
5. Run `/reload` or restart

The biome is automatically assigned level numbers based on its `order` relative to other biomes.

### Overriding Built-In Biomes

Use the same `id` as a built-in biome to replace it entirely:

```json
{
  "id": "plains",
  "name": "Cursed Plains",
  ...
}
```

### Multiple Biome Variations

**Option A: Multiple biome JSONs (different gameplay)**

Create separate biome files with unique IDs for distinct gameplay experiences:

```
biomes/plains_meadow.json    → id: "plains_meadow",   order: 1
biomes/plains_farmland.json  → id: "plains_farmland",  order: 2
biomes/plains_riverside.json → id: "plains_riverside",  order: 3
```

Each is its own biome with its own enemies, loot, and difficulty.

**Option B: Multiple .nbt maps per biome (same gameplay, different scenery)**

Add multiple numbered structure files for visual variety within the same biome:

```
arenas/plains/1.nbt  → Open field
arenas/plains/2.nbt  → River crossing
arenas/plains/3.nbt  → Hillside
```

Same enemies and gameplay, but the arena looks different each fight.

**Option C: Combine both**

```
biomes/plains_meadow.json + arenas/plains_meadow/1.nbt, 2.nbt, 3.nbt
biomes/plains_farmland.json + arenas/plains_farmland/1.nbt, 2.nbt
```

3 biome variations with 5 total unique maps = massive variety.

### Enemy Definitions

```json
{"type": "minecraft:zombie", "weight": 8, "hp": 6, "attack": 2, "defense": 0, "range": 1}
```

| Field | Description |
|-------|-------------|
| `type` | Minecraft entity type ID |
| `weight` | Spawn probability (higher = more common) |
| `hp` | Base health (scaled by NG+ and progression) |
| `attack` | Base damage per hit |
| `defense` | Flat damage reduction |
| `range` | Tiles. 1=melee, 3=ranged, 5+=artillery |

**Supported mob types with AI:** zombie, husk, drowned, skeleton, stray, spider, creeper, vindicator, enderman, phantom, ocelot, witch, blaze, ghast, pillager, shulker, warden, ender_dragon, goat, camel, breeze, bogged, cave_spider, silverfish, slime, wolf, magma_cube, hoglin, piglin, wither_skeleton, guardian, zombified_piglin

Mobs without registered AI fall back to PassiveAI (stand idle).

### Loot Tables

```json
{"item": "minecraft:diamond", "weight": 5}
```

1-3 items are rolled per level completion. Weight determines relative probability.

---

## Datapack Setup

```
my_arena_pack/
  pack.mcmeta
  data/
    craftics/
      craftics/
        biomes/
          my_custom_biome.json       ← Custom biome definition
      structures/
        arenas/
          my_custom_biome/
            1.nbt                    ← Map variation 1
            2.nbt                    ← Map variation 2
            boss_1.nbt               ← Boss map
```

**pack.mcmeta:**
```json
{
  "pack": {
    "pack_format": 48,
    "description": "Custom Craftics arenas"
  }
}
```

Place in your world's `datapacks/` folder.

---

## Hot Reload

- **Biome JSONs:** Run `/reload` to reload all biome definitions without restarting
- **Structure files (.nbt):** Loaded on-demand when combat starts — new files are picked up immediately
- **No server restart needed** for either type of change

---

## Fabric Mod API (Java)

Register custom AI for any mob type:

```java
import com.crackedgames.craftics.combat.ai.AIRegistry;

AIRegistry.register("mymod:custom_mob", (self, arena, playerPos) -> {
    // Your AI logic here
    // Return one of: Move, Attack, MoveAndAttack, Flee, Teleport,
    //   TeleportAndAttack, Pounce, Explode, RangedAttack, Swoop,
    //   StartFuse, Detonate, Idle
    return new EnemyAction.Attack(self.getAttackPower());
});
```

See `src/main/java/com/crackedgames/craftics/combat/ai/` for 25+ built-in AI implementations as reference.
