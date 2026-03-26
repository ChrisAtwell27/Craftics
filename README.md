# Craftics

**Turn-based tactical RPG combat in Minecraft.**

Craftics is a Fabric mod that transforms Minecraft into a tactical RPG. Fight through 18 procedurally generated biome arenas across the Overworld, Nether, and End, using vanilla items, crafting, and potions, in a complete turn-based combat system with an isometric camera, grid movement, and 40+ unique enemy AI behaviors.

> **No new items. No new blocks.** (Except the Level Select.) Every item already in the game has a purpose in battle.

- **Mod ID:** `craftics`
- **Version:** 0.1.0
- **Minecraft:** 1.21.11
- **Mod Loader:** Fabric (Fabric Loader ≥0.16.0, Fabric API)
- **Java:** 21+
- **Author:** CrackedGames
- **License:** All Rights Reserved

### 📖 Documentation

Full documentation with in-depth guides:

| Page | Description |
|------|-------------|
| [**Combat System**](https://chrisatwell27.github.io/Craftics/combat.html) | Weapons, abilities, armor trims, goat horns, items, damage formula |
| [**Enemy AI**](https://chrisatwell27.github.io/Craftics/enemies.html) | 40+ mob behaviors, boss phases, on-hit effects |
| [**Biome Progression**](https://chrisatwell27.github.io/Craftics/biomes.html) | 18 biomes, branching paths, procedural generation |
| [**Progression & Builds**](https://chrisatwell27.github.io/Craftics/progression.html) | Stats, leveling, traders, NG+, recommended class builds |
| [**Modding Guide**](https://chrisatwell27.github.io/Craftics/modding.html) | Custom arenas, JSON biomes, Java API, addon policy |

---

## Table of Contents

- [Overview](#overview)
- [Getting Started](#getting-started)
  - [Requirements](#requirements)
  - [Building from Source](#building-from-source)
  - [Running](#running)
- [Gameplay](#gameplay)
  - [World Creation](#world-creation)
  - [The Hub](#the-hub)
  - [Level Select](#level-select)
  - [Combat](#combat)
  - [Action Points](#action-points)
  - [Damage Calculation](#damage-calculation)
  - [Weapon Abilities](#weapon-abilities)
  - [Usable Items](#usable-items)
  - [Enemy AI](#enemy-ai)
  - [Victory and Defeat](#victory-and-defeat)
  - [Trader System](#trader-system)
  - [Player Progression](#player-progression)
  - [New Game Plus](#new-game-plus)
- [Biome Progression](#biome-progression)
  - [Overworld (8 Biomes)](#overworld-8-biomes)
  - [Nether (5 Biomes)](#nether-5-biomes)
  - [The End (4 Biomes)](#the-end-4-biomes)
- [Controls](#controls)
- [Technical Architecture](#technical-architecture)
  - [Project Structure](#project-structure)
  - [Core Systems](#core-systems)
  - [Client Mixins](#client-mixins)
  - [Networking](#networking)
  - [World Generation](#world-generation)
  - [Data-Driven Biomes](#data-driven-biomes)
- [Modding & Extensibility](#modding--extensibility)
- [Development Status](#development-status)

---

## Overview

When you create a world with the **Craftics** world preset, Minecraft generates a void world with a decorative cottage hub at the origin. From the hub, you interact with a Level Select Block to enter procedurally generated grid arenas where combat plays out on a turn-based system.

The camera locks to a fixed isometric angle (55° pitch, 225° yaw), the mouse cursor is freed for point-and-click interaction, and WASD movement is disabled. You click tiles to move, click enemies to attack, and manage a limited pool of Action Points each turn.

Your real Minecraft inventory is your inventory. Equip a diamond sword for more damage. Wear iron armor for defense. Brew potions for combat buffs. Eat food to heal. Everything ties back to vanilla mechanics.

Between combat encounters, you craft, smelt, brew, and trade using emeralds earned from victories. A branching biome progression leads through 18 arenas across three dimensions, culminating in a boss fight against the Ender Dragon and a New Game+ system that resets the world with scaled-up enemies while keeping your stats and currency.

---

## Getting Started

### Requirements

- **Java 21** (Eclipse Adoptium JDK 21 recommended)
- **Minecraft 1.21.11**
- **Fabric Loader** ≥0.16.0
- **Fabric API** 0.141.3+1.21.11

### Building from Source

```bash
git clone <repository-url>
cd TacticalRPGMod
./gradlew build
```

The compiled mod JAR is output to `build/libs/`.

### Running

**Development client:**

```bash
./gradlew runClient
```

**Manual install:** Copy the built JAR from `build/libs/` into your `.minecraft/mods/` folder alongside the Fabric API JAR.

---

## Gameplay

### World Creation

Select the **Craftics** world preset when creating a new world. This generates a void world with no terrain, no structures, no natural mob spawning. The hub is built automatically at the world origin and you are teleported there on first join.

### The Hub

A decorated cottage at spawn serves as your base of operations. It contains:

| Block | Purpose |
|-------|---------|
| **Crafting Table** | Standard vanilla crafting |
| **Furnace** | Smelting ores and cooking food |
| **Chest** | Overflow item storage |
| **Brewing Stand** | Potion brewing (available from the start) |
| **Level Select Block** | Opens the biome map to start combat arenas |
| **Armor Stand** | Display your loadout |

The hub is protected. Blocks in the hub shell cannot be broken.

### Level Select

Right-clicking the Level Select Block opens a map-style GUI with three dimension sections (Overworld, Nether, End). Biomes are displayed as a node graph with connecting lines showing progression paths. Locked biomes are grayed out; the current biome glows. Branching paths let you choose your route through the Overworld.

Each biome contains 5 levels. The last level of each biome is a boss fight. Defeating a boss unlocks the next biome(s) on the path.

### Combat

When you enter an arena:

1. The arena is procedurally generated from the biome template: floor blocks, obstacles, edge terrain, and decorations
2. Enemies spawn at randomized positions based on the biome's mob pool
3. The camera locks to an isometric view
4. The mouse cursor unlocks for point-and-click control
5. WASD movement is disabled
6. Combat begins on the player's turn

Combat alternates between **Player Turn** and **Enemy Turn** until all enemies are defeated or the player dies.

### Action Points

The player gets **3 AP** per turn (modified by stats and potions).

| Action | AP Cost | Description |
|--------|---------|-------------|
| **Move** | 1 AP per tile | Click a highlighted tile to path there. Move speed is 3 tiles by default (upgradeable). |
| **Melee Attack** | 2 AP | Click an adjacent enemy with a melee weapon equipped. |
| **Ranged Attack** | 2 AP | Click an enemy in range with a bow, crossbow, or trident. Requires line of sight and arrows (for bows/crossbows). |
| **Use Item** | 1 AP | Eat food (heals HP), drink a potion (applies combat effect), or throw a throwable item. |
| **Block** | 1 AP | Shield in offhand. Halves the next incoming damage. |
| **End Turn** | 0 AP | Press **R** to end your turn. Remaining AP is lost. No banking. |

### Damage Calculation

```
Damage = Weapon Attack Power + Stat Bonuses + Damage Type Bonus - Target Defense
Minimum damage = 1
```

**Weapon Attack Power** (from equipped main hand):

| Weapon | Attack | Damage Type |
|--------|--------|-------------|
| Fist | 1 | Physical |
| Wooden Sword | 2 | Sword |
| Stone Sword | 3 | Sword |
| Iron Sword | 4 | Sword |
| Diamond Sword | 5 | Sword |
| Netherite Sword | 6 | Sword |
| Wooden/Stone/Iron/Diamond/Gold/Netherite Axe | 2-6 | Cleaving |
| Bow | 3 (range 3) | Ranged |
| Crossbow | 4 (range 4) | Ranged |
| Trident | 4 (range 3) | Water |
| Mace | 5 | Blunt |
| Stick | 1 | Blunt |

**Weapon durability:** Each attack costs **10 durability** instead of the usual 1. Weapons break during combat if they run out.

**Defense** (from equipped armor):

| Armor Set | Defense |
|-----------|---------|
| None | 0 |
| Full Leather | 3 |
| Full Iron | 7 |
| Full Diamond | 10 |
| Full Netherite | 12+ |

### Damage Types

There are **8 damage types**: Sword, Cleaving, Blunt, Water, Magic, Pet, Ranged, and Physical. Bonus damage from damage types stacks from three sources:

**Armor Set Specializations:**

| Armor Set | Class | Damage Type Bonus |
|-----------|-------|-------------------|
| Chainmail | Rogue | Sword +2 |
| Iron | Guard | Cleaving +2 |
| Gold | Gambler | Magic +2 |
| Diamond | Knight | Blunt +2 |
| Netherite | Juggernaut | ALL types +1 |
| Turtle | Aquatic | Water +3 |
| Leather | Scout | None |

**Armor Trim Bonuses** (per-piece, max +4 from 4 armor pieces):

| Trim Pattern | Bonus |
|--------------|-------|
| Bolt | Sword Power +1 |
| Snout | Cleaving Power +1 |
| Coast | Water Power +1 |
| Rib | Magic Power +1 |
| Sentry | Ranged Power +1 |
| Raiser | Pet/Ally Damage +1 |
| Wild, Dune, etc. | Generic Melee Power +1 (stacks with Sword/Cleaving/Blunt) |

Wearing 4 pieces with the **same trim pattern** activates a **full-set bonus**:

| Trim | Full Set Bonus |
|------|---------------|
| Sentry | Counter-attack ranged enemies |
| Wild | First attack each turn is free |
| Ward | 50% less damage when stationary |
| Vex | 20% dodge chance |
| Silence | Invisible for first 2 turns |
| Flow | Kills refund 1 AP |
| Bolt | Crits stun the target |

Templates drop from bosses. Every trim enables a different playstyle. See the [full trim reference](https://chrisatwell27.github.io/Craftics/combat.html#armor-trims) for all 19 patterns.

**Combat Effect Bonuses:**

| Effect | Bonus |
|--------|-------|
| Water Breathing | Water +2 |
| Fire Resistance | Magic +1 |

### Weapon Abilities

Each weapon type has a unique combat ability:

| Weapon | Ability | Effect |
|--------|---------|--------|
| **Swords** | Cleave | Hits the target AND one adjacent tile in the same direction |
| **Diamond Sword** | Cleave + Crit | 30% chance to deal double damage |
| **Netherite Sword** | Cleave + Execute | Instant kill on targets below 30% HP |
| **Axes** | Stun | Target loses 2 AP on their next turn |
| **Spears** | Pierce | Damage passes through the target to hit the tile behind |
| **Mace** | Smash | 3×3 AoE damage + knockback |
| **Crossbow** | Pierce-Through | Bolt passes through the first target to hit a second |

### Goat Horn Effects

Rare drops from goats (10% chance). 8 variants, each with its own sound and combat effect:

| Horn | AP | Effect |
|------|----|--------|
| Ponder | 1 | +2 Defense for 3 turns |
| Sing | 1 | +2 HP regen for 3 turns |
| Seek | 2 | +3 Attack for 3 turns |
| Feel | 1 | +2 Speed for 3 turns |
| Admire | 2 | All enemies −2 Attack for 2 turns |
| Call | 2 | All enemies −1 Speed for 2 turns |
| Yearn | 3 | All enemies Poisoned for 3 turns |
| Dream | 2 | Fire Resistance for 4 turns |

See the [full combat reference](https://chrisatwell27.github.io/Craftics/combat.html#goat-horns) for strategy tips.

### Usable Items

**Food:** 28 food items can be eaten during combat for HP recovery (1 AP). Examples: steak heals the most, bread and cooked meats offer moderate healing, berries and raw foods heal less.

**Potions:** Drinking a potion applies a turn-based effect (converted from Minecraft's tick-based system):

| Potion | Combat Effect |
|--------|---------------|
| Strength | +3 attack for duration |
| Swiftness | +1 AP per turn for duration |
| Fire Resistance | Immune to lava and fire tiles |
| Regeneration | Heal 1 HP per turn |
| Healing (Instant) | Restore 4 HP immediately |

Effects can also be frozen. Potions consumed in the hub activate when combat starts.

**Throwables:**

| Item | Effect |
|------|--------|
| Snowball | Knockback 1 tile |
| Egg | 1 damage |
| Ender Pearl | Teleport to target tile (costs 2 HP) |
| Fire Charge | Sets target tile on fire |
| Wind Charge | Knockback 2 tiles + 1 damage |
| TNT | Timed 3×3 AoE explosion |
| Splash Potion | 3×3 area effect (arcs with particles, 4-tile range) |
| Lingering Potion | Creates a poison cloud that ticks damage each turn |
| Cobweb | Stuns target for 1 turn |
| Bell | Stuns all enemies in range |
| Tipped Arrow | Applies potion effect on hit (poison, slowness, harming) |

### Enemy AI

The mod includes 20+ unique AI strategies registered for 40+ mob types. Unregistered mobs default to passive behavior (flee when attacked). Each AI controls movement, targeting, and special mechanics. See the [full Enemy AI reference](https://chrisatwell27.github.io/Craftics/enemies.html) for every mob.

| AI | Mobs | Key Behavior |
|----|------|-------------|
| **PassiveAI** | Cow, Pig, Sheep, Chicken, Rabbit | Idle; flees when attacked |
| **GoatAI** | Goat, Wolf | Passive until hit, then 50% ram (knockback) or flee |
| **ZombieAI** | Zombie | Beeline melee, speed 1 |
| **HuskAI** | Husk | Zombie variant with +1 bonus damage (hunger) |
| **DrownedAI** | Drowned | Trident throw (range 3) or melee rush |
| **SkeletonAI** | Skeleton | Ranged (range 3), seeks cardinal line-of-sight |
| **StrayAI** | Stray | Ranged + kiting (prefers max distance) |
| **PillagerAI** | Pillager | Crossbow (range 4), retreats from melee |
| **VindicatorAI** | Vindicator | Speed 2 aggressive melee rush |
| **CreeperAI** | Creeper | Approaches → fuse when adjacent → explodes next turn (2× damage, radius 1). Fuse resets if player moves away |
| **SpiderAI** | Spider | 2×2 body, speed 2, pounce attack over obstacles |
| **WitchAI** | Witch | Potion throw (range 2–3), random debuffs, keeps distance |
| **EndermanAI** | Enderman | Teleports behind player to attack; teleports away when damaged |
| **PhantomAI** | Phantom | Cardinal-line swoops (speed 4), damages all tiles in path |
| **OcelotAI** | Ocelot | Speed 2 hit-and-run; strikes then flees |
| **MountedAI** | Camel | Speed 3 charge; dismounts at 50% HP (speed drops to 1) |
| **GhastAI** | Ghast | Long-range fireball (range 5–6), flees from close range |
| **BlazeAI** | Blaze | Medium-range fire (range 3–4), applies burning, retreats from melee |
| **ShulkerAI** | Shulker | Stationary turret, range 4–5, high defense, rarely moves |
| **WardenAI** | Warden | Boss. Phase 1: slow (speed 1), massive melee. Phase 2 (<50% HP): speed 2, sonic boom (range 3) |
| **DragonAI** | Ender Dragon | Final boss. Phase 1: arena swoops. Phase 2 (<50% HP): charge + AoE breath |

Enemy types also inflict special effects on hit:
- **Wither Skeleton** → Wither debuff
- **Blaze** → Burning
- **Husk** → Weakness
- **Stray** → Slowness

### Victory and Defeat

**Victory:** When all enemies in an arena are killed, the player receives:
- Mob drops from each defeated enemy
- Biome loot rolled from the biome's weighted loot table
- Emerald reward (scaled by Resourceful stat)
- Boss kills unlock the next biome and grant a level-up

After a non-boss victory, the player chooses:
- **Go Home**: Keep loot, return to hub, reset the current biome run
- **Continue**: Proceed to the next level in the biome (risk/reward; dying loses everything from the run)

There is a 40% chance a trader appears between levels when continuing.

**Defeat:** If the player dies:
- Inventory is cleared (items from the current run are lost)
- The player is teleported back to the hub
- The biome run resets (must start from level 1 of that biome)
- No permadeath. Progress and stats are kept

### Trader System

Traders appear randomly between combat levels (40% chance). There are 7 trader types:

| Trader | Specialty |
|--------|-----------|
| Weaponsmith | Weapons (scales wood→netherite by tier) |
| Armorer | Armor sets |
| Fletcher | Bows, crossbows, arrows |
| Alchemist | Potions and brewing ingredients |
| Chef | Cooked food and golden apples |
| Toolsmith | Shields, utility items |
| Curiosity Dealer | Rare and unique items |

Trades cost emeralds. Each encounter offers 3–5 random trades from the trader's pool. Weapon/armor quality scales with biome progression tier (1–9).

### Player Progression

Defeating a biome boss grants a level-up with stat points to allocate. There are 8 stats:

| Stat | Base | Effect |
|------|------|--------|
| **Speed** | 3 | Tiles you can move per turn |
| **AP** | 3 | Action Points per turn |
| **Melee Power** | 0 | Bonus melee damage |
| **Ranged Power** | 0 | Bonus ranged damage |
| **Vitality** | 0 | Bonus max HP |
| **Defense** | 0 | Flat damage reduction |
| **Luck** | 0 | Better loot rolls |
| **Resourceful** | 0 | Bonus emerald rewards |

Stats are persistent per player (UUID-based, supports multiplayer) and saved with the world. A stat panel is displayed on the right side of the inventory screen showing your current level, all stat values, unspent points, and emerald count.

### New Game Plus

After defeating the Ender Dragon in Dragon's Nest (the final biome), New Game Plus activates:
- All biome progression resets
- Player stats and emeralds carry over
- Enemies scale by **+25% per NG+ cycle** (HP, attack, defense)
- The NG+ tier is displayed on the level select screen

See the [Recommended Builds guide](https://chrisatwell27.github.io/Craftics/progression.html#recommended-builds) for class builds optimized for each stage of the game.

---

## Biome Progression

The mod features 18 biomes across three dimensions, organized in a branching progression:

### Overworld (8 Biomes)

A branching path with two choice points:

```
Plains → Dark Forest → ┬─ Desert ──┬─→ River Delta → ┬─ Stony Peaks ──┬─→ Underground Caverns → Deep Dark
                        └─ Jungle ──┘                 └─ Snowy Tundra ──┘
```

| Biome | Grid Size | Environment | Boss | Notable Mechanics |
|-------|-----------|-------------|------|-------------------|
| **Plains** | 8×8 (+1 width/level) | Grassy hills, flowers | Varies | Tutorial biome, open field |
| **Dark Forest** | 9×9 | Dense trees, dark atmosphere | Varies | Tree obstacles, low visibility |
| **Scorching Desert** | 10×10 | Sand dunes, cacti | Varies | Open terrain, cactus damage |
| **Dense Jungle** | 10×10 (+1 width) | Dense vegetation, vines | Varies | Heavy obstacles, vine cover |
| **River Delta** | 9×9 | Sandy banks, water features | Varies | Water edge tiles |
| **Stony Peaks** | 10×10 | Stone terrain | Varies | Minimal cover |
| **Snowy Tundra** | 9×9 | Snow, ice patches | Varies | Powder snow edges |
| **Underground Caverns** | 10×10 (+1 both) | Cave structures, ore veins | Varies | Dense obstacles, night mode |
| **The Deep Dark** | 11×11 (+1 both) | Deepslate, sculk | **Warden** | Very dark, high danger |

### Nether (5 Biomes)

Unlocked after completing The Deep Dark:

| Biome | Grid Size | Environment | Boss | Notable Mechanics |
|-------|-----------|-------------|------|-------------------|
| **Nether Wastes** | 10×10 | Netherrack, fire | Varies | Lava edges, fire hazards |
| **Soul Sand Valley** | 10×10 (+1 width) | Soul sand, soul fire | Varies | Slow terrain |
| **Crimson Forest** | 11×11 | Crimson nylium, fungi | Varies | Dense fungal obstacles |
| **Warped Forest** | 11×11 (+1 width) | Warped nylium | Varies | Sparse obstacles |
| **Basalt Deltas** | 12×12 (+1 both) | Basalt, magma | Boss | Lava edges, dense pillars |

### The End (4 Biomes)

Unlocked after completing Basalt Deltas:

| Biome | Grid Size | Environment | Boss | Notable Mechanics |
|-------|-----------|-------------|------|-------------------|
| **Outer End Islands** | 10×10 | End stone, chorus | Varies | Void edges, sparse |
| **End City** | 11×11 (+1 width) | Purpur, end bricks | Varies | Shulker turrets |
| **Chorus Grove** | 11×11 (+1 both) | Chorus plants | Varies | Organic obstacles |
| **Dragon's Nest** | 12×12 (+1 both) | End stone | **Ender Dragon** | Void edges, final boss |

### Procedural Level Generation

Each level within a biome is procedurally generated:
- Grid dimensions grow per level based on the biome's growth settings
- Obstacles are placed with increasing density
- Edges are carved with noise-based organic shapes and biome-specific edge blocks (water, lava, void, powder snow)
- Decorative elements are placed around the arena matching the environment style (trees, ferns, snow layers, cave structures, nether vegetation, end pillars)
- An underground base of stone and glowstone lighting is generated below the floor

---

## Controls

### In Combat

| Input | Action |
|-------|--------|
| **Left Click** (empty/sword) | Move to tile OR melee attack enemy (context-sensitive) |
| **Left Click** (bow/crossbow/trident) | Ranged attack on enemy in range |
| **Left Click** (food/potion/throwable) | Use held item |
| **R** | End turn |
| **Scroll Wheel** | Zoom camera in/out (±1.5 per notch, range 8–30) |
| **Middle Mouse Drag** | Pan camera (world-space, accounts for yaw) |
| **Shift + Middle Click** | Reset camera pan |
| **F6** | Toggle debug combat mode |

### In Hub

Standard Minecraft controls. Interact with blocks normally (crafting table, furnace, brewing stand, chest). Right-click the Level Select Block to open the biome map.

---

## Technical Architecture

### Project Structure

```
src/
├── main/java/com/crackedgames/craftics/
│   ├── CrafticsMod.java              : Main mod entrypoint (ModInitializer)
│   ├── api/
│   │   └── CrafticsAPI.java          : Public modding API
│   ├── block/
│   │   ├── ModBlocks.java               : Block registration
│   │   ├── LevelSelectBlock.java        : Interactive block with GUI
│   │   ├── LevelSelectBlockEntity.java  : Block entity with screen data
│   │   ├── LevelSelectScreenHandler.java : Screen handler
│   │   └── ModScreenHandlers.java       : Screen handler registration
│   ├── combat/
│   │   ├── CombatManager.java           : Server-side combat state machine (~1500 lines)
│   │   ├── CombatEntity.java            : Combat enemy data
│   │   ├── CombatPhase.java             : Phase enum
│   │   ├── CombatEffects.java           : Turn-based effect system (12 effects)
│   │   ├── PlayerCombatStats.java       : Equipment → combat stat reader
│   │   ├── PlayerProgression.java       : Persistent stat/leveling system
│   │   ├── Pathfinding.java             : A* pathfinding + BFS reachability
│   │   ├── WeaponAbility.java           : Per-weapon-type abilities
│   │   ├── ItemUseHandler.java          : Food, potions, throwables in combat
│   │   ├── TileHighlightManager.java    : Carpet-based tile highlights
│   │   ├── LootPool.java               : Weighted loot rolling
│   │   ├── TraderSystem.java            : 7 trader types, tiered loot pools
│   │   └── ai/
│   │       ├── EnemyAI.java             : AI interface
│   │       ├── EnemyAction.java         : 13 action types (sealed interface)
│   │       ├── AIRegistry.java          : 35+ mob→AI mappings
│   │       ├── AIUtils.java             : Shared AI utilities
│   │       └── (20+ AI classes)         : One per mob behavior archetype
│   ├── core/
│   │   ├── GridArena.java               : Width×height tile grid with occupants
│   │   ├── GridPos.java                 : 2D grid coordinate
│   │   ├── GridTile.java                : Tile data (type, block, timer)
│   │   └── TileType.java               : NORMAL, OBSTACLE, LAVA, FIRE, VOID, EXIT
│   ├── level/
│   │   ├── BiomeTemplate.java           : Biome data class
│   │   ├── BiomeRegistry.java           : Central biome registry (JSON + API)
│   │   ├── BiomeJsonLoader.java         : Datapack JSON parser
│   │   ├── BiomePath.java              : Branching progression graph (3 dimensions)
│   │   ├── LevelDefinition.java         : Abstract level layout
│   │   ├── LevelGenerator.java          : Procedural arena generation
│   │   ├── GeneratedLevelDefinition.java : Concrete generated level
│   │   ├── LevelRegistry.java           : Level lookup facade
│   │   ├── ArenaBuilder.java            : Places blocks in world from definition
│   │   ├── EnvironmentStyle.java        : 11 visual styles enum
│   │   └── MobPoolEntry.java            : Enemy pool data record
│   ├── network/
│   │   ├── ModNetworking.java           : Registers 6 C2S + 8 S2C payloads
│   │   └── (14 payload record classes)  : Individual packet definitions
│   └── world/
│       ├── VoidChunkGenerator.java      : Empty void world generation
│       ├── HubRoomBuilder.java          : Decorated cottage at spawn
│       └── CrafticsSavedData.java    : Persistent world state (progress, emeralds, NG+)
│
├── main/resources/
│   ├── fabric.mod.json
│   ├── craftics.mixins.json
│   ├── assets/craftics/
│   │   ├── lang/en_us.json
│   │   ├── textures/ (icon.png, level_select_block.png)
│   │   ├── blockstates/
│   │   └── models/
│   └── data/craftics/craftics/biomes/
│       └── (18 biome JSON files)
│
├── client/java/com/crackedgames/craftics/
│   ├── CrafticsClient.java           : Client entrypoint (ClientModInitializer)
│   ├── client/
│   │   ├── CombatState.java             : Client combat state + camera settings
│   │   ├── CombatHudOverlay.java        : HUD rendering (HP, AP, enemies, log)
│   │   ├── CombatInputHandler.java      : Click-to-move, click-to-attack, pan, zoom
│   │   ├── CombatLog.java               : Scrolling combat message log
│   │   ├── CombatTooltips.java          : Item tooltip enrichment
│   │   ├── TileRaycast.java             : Cursor → grid tile raycasting
│   │   ├── ClientGridHelper.java        : Client-side grid utilities
│   │   ├── LevelSelectScreen.java       : Biome map GUI
│   │   ├── VictoryChoiceScreen.java     : Post-victory Go Home/Continue
│   │   ├── LevelUpScreen.java           : Stat allocation GUI
│   │   └── TraderScreen.java            : Trading GUI
│   └── mixin/client/
│       ├── CameraLockMixin.java         : Isometric camera lock
│       ├── ScrollZoomMixin.java         : Scroll wheel → zoom
│       ├── MovementDisableMixin.java    : Disable WASD in combat
│       ├── MouseUnlockMixin.java        : Free cursor in combat
│       ├── InputAccessor.java           : Movement vector accessor
│       ├── OverlayMessageMixin.java     : Action bar → combat log
│       └── InventoryStatsMixin.java     : Stat panel in inventory screen
│
└── client/resources/
    └── craftics.client.mixins.json   : 7 client mixins
```

**Total: 99 Java files, 27 JSON configs, 2 textures.**

### Core Systems

- **CombatManager**: Server-authoritative state machine driving the entire combat lifecycle. Handles player actions, A* pathfinding, animated movement (lerp over 4 ticks), weapon abilities, enemy AI turns (with per-phase delays: DECIDING→MOVING→ATTACKING→DONE), per-turn status effects, environmental hazard damage, victory/defeat conditions, loot distribution, and biome progression.

- **GridArena**: 2D tile array with occupant tracking. Supports multi-tile entities (e.g., 2×2 spiders). Arena origins are spaced at `X = level × 1000, Y = 100, Z = 0` to prevent collision.

- **Pathfinding**: A* pathfinding for movement with BFS flood-fill for reachable tile calculation. Respects obstacles, multi-tile entities, and movement speed limits.

- **PlayerProgression**: Persistent per-player stat system using Minecraft's `PersistentState` and `Codec` serialization. UUID-based for multiplayer support.

- **CombatEffects**: Turn-based effect system replacing Minecraft's tick-based effects. 12 distinct effects with frozen state (apply in hub, activate on combat start).

- **BiomeRegistry**: Central registry loading biomes from JSON datapacks (`data/*/craftics/biomes/*.json`) and programmatic API registration. Hot-reloadable via `/reload`.

### Client Mixins

| Mixin | Target | Purpose |
|-------|--------|---------|
| **CameraLockMixin** | `Camera.update` | Locks camera to isometric view (55° pitch, 225° yaw) centered on arena. Computes offset from arena center. |
| **ScrollZoomMixin** | `Mouse.onMouseScroll` | Intercepts scroll wheel for zoom control (range 8–30 blocks). Cancels hotbar switching. |
| **MovementDisableMixin** | `KeyboardInput.tick` | Zeroes all movement input during combat. |
| **MouseUnlockMixin** | `Mouse.lockCursor` | Prevents cursor grab during combat for free mouse interaction. |
| **InputAccessor** | `Input` | Accessor mixin exposing `setMovementVector`. |
| **OverlayMessageMixin** | `InGameHud.setOverlayMessage` | Routes action bar messages to the combat log for persistent display. |
| **InventoryStatsMixin** | `InventoryScreen.render` | Renders player stats panel alongside the inventory screen. |

### Networking

6 client-to-server payloads:

| Payload | Purpose |
|---------|---------|
| `StartLevelPayload` | Request to start a combat level |
| `CombatActionPayload` | Player combat action (move, attack, use item, end turn, hover) |
| `PostLevelChoicePayload` | Go Home vs Continue after victory |
| `TraderBuyPayload` | Purchase a trade |
| `TraderDonePayload` | Finish trading |
| `StatChoicePayload` | Allocate a stat point |

8 server-to-client payloads:

| Payload | Purpose |
|---------|---------|
| `EnterCombatPayload` | Combat started, arena dimensions, origin |
| `CombatEventPayload` | Combat event (damage, movement, effect) |
| `ExitCombatPayload` | Combat ended |
| `CombatSyncPayload` | Full combat state sync (phase, AP, HP, enemies) |
| `VictoryChoicePayload` | Prompt Go Home/Continue screen |
| `TraderOfferPayload` | Trader encountered, show trades |
| `LevelUpPayload` | Level up, show stat allocation |
| `PlayerStatsSyncPayload` | Sync progression data to client |

### World Generation

- **VoidChunkGenerator**: Produces an empty world with no terrain, entities, or structures. Codec-serialized for world preset compatibility.
- **HubRoomBuilder**: Constructs a decorated cottage at the world origin with cobblestone foundation, oak plank floor, dark oak posts, windows, pitched roof, porch, chimney, furniture, and lighting. Supports versioned rebuilds.
- **CrafticsSavedData**: Persistent world-level state stored via Minecraft's `PersistentState` system. Tracks hub state, biome progression, emerald currency, active biome run, branch choices, discovered biomes, and NG+ level.

### Data-Driven Biomes

All 18 built-in biomes are defined as JSON files in `data/craftics/craftics/biomes/`. The schema supports:

- Grid dimensions with per-level growth
- Floor and obstacle block lists
- Obstacle density with per-level scaling
- Environment style (11 visual styles)
- Day/night mode
- Passive, hostile, and boss mob pools with full stats
- Weighted loot tables

Biomes are hot-reloadable. Run `/reload` in-game to reload all biome definitions without restarting.

---

## Modding & Extensibility

Craftics supports three levels of modding:

### Custom Arena Maps (No Code)

Build arena maps in Minecraft's Structure Block system and export them as `.nbt` files. Place them in a datapack or the mod's resources at:
```
data/craftics/structures/arenas/<biome_id>/<number>.nbt
```
Mark the playable grid area with **Gold Block** (player start corner) and **Emerald Block** (opposite corner). Everything outside the markers becomes terrain/decoration. Multiple numbered presets per biome (1.nbt, 2.nbt, etc.) are picked randomly each fight.

See [MODDING.md](MODDING.md) for the full custom maps guide with step-by-step instructions.

### Datapack Biome Modding (No Code)

Create custom biomes by adding JSON files to a datapack at:
```
data/<namespace>/craftics/biomes/<biome_name>.json
```

Override built-in biomes by using the same biome `id`. Add multiple biome variations by creating separate JSON files with unique IDs. See [MODDING.md](MODDING.md) for the full JSON schema reference, field documentation, and examples.

### Fabric Mod Integration (Java API)

The `CrafticsAPI` class provides:

```java
// Register custom AI for a mob type
CrafticsAPI.registerAI("mymod:custom_mob", new MyCustomAI());

// Register a new biome programmatically
CrafticsAPI.registerBiome(myBiomeTemplate);

// Query the total number of levels
int total = CrafticsAPI.getTotalLevels();
```

Custom AI implements the `EnemyAI` interface with a single method:
```java
EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos);
```

13 action types are available: Move, Attack, MoveAndAttack, Flee, Teleport, TeleportAndAttack, Pounce, Explode, RangedAttack, Swoop, StartFuse, Detonate, and Idle.

See [MODDING.md](MODDING.md) for complete API documentation, or the [online Modding Guide](https://chrisatwell27.github.io/Craftics/modding.html) for the full reference with examples.

---

## Configuration

45+ config options via Mod Menu:

- **Combat Balance:** Enemy HP/ATK/DEF scaling per biome, boss HP multiplier, critical hit chance, ranged accuracy
- **Mob Behavior:** Passive mob wander chance, predator hunting toggle, enemy animation speed, auto-end turn
- **Difficulty:** Permadeath mode, heal between levels, enemy range hints, intention preview
- **Accessibility:** Colorblind mode, larger UI, camera shake toggle
- **Economy:** Loot multiplier, trader spawn chance, horn drop rate, emerald rewards

---

## Addon Policy

You're free to create **addons, datapacks, resource packs, and mods** that interact with Craftics through its API. You can also include Craftics in **modpacks** with credit.

**Allowed:** Addons via API, datapacks with custom arenas/loot/biomes, resource packs, modpacks on CurseForge/Modrinth (with credit), videos/streams/reviews.

**Not allowed:** Redistributing Craftics outside modpack launchers, copying source code, re-uploading without permission.

---

## Development Status

The mod is in active development (v0.1.0). The following systems are implemented:

- [x] Void world generation with Craftics world preset
- [x] Hub cottage with crafting stations and Level Select Block
- [x] Isometric camera lock with zoom and pan
- [x] Mouse cursor unlock and WASD disable during combat
- [x] Full grid data model (GridArena, GridTile, GridPos, TileType)
- [x] Procedural arena generation from biome templates
- [x] Arena building with environment-specific decorations
- [x] Turn-based combat state machine (CombatManager)
- [x] Action Point system
- [x] Click-to-move with A* pathfinding
- [x] Click-to-attack (melee and ranged)
- [x] Weapon abilities (cleave, stun, pierce, smash, crit, execute)
- [x] Equipment-based combat stats
- [x] 20+ unique enemy AI strategies for 40+ mob types
- [x] Turn-based status effects (12 effects, frozen effects)
- [x] Food, potion, and throwable item use in combat
- [x] Tile highlights (carpet-based movement/attack display)
- [x] Tile raycasting (cursor → grid position)
- [x] Combat HUD (HP bar, AP pips, turn indicator, enemy list, combat log)
- [x] 18 biome definitions (JSON datapacks)
- [x] Branching biome progression across 3 dimensions
- [x] Level Select map GUI with node graph
- [x] Victory/defeat flow with Go Home/Continue choice
- [x] Trader system (7 types, tier-scaled inventory)
- [x] Player progression (8 stats, level-up screen)
- [x] Emerald economy
- [x] New Game+ system (+25% enemy scaling per cycle)
- [x] Persistent world state (progress, stats, currency)
- [x] Client/server networking (14 packet types)
- [x] Combat tooltips on items
- [x] Stat panel in inventory screen
- [x] Modding API (custom AI + custom biomes)
- [x] Datapack biome loading with hot reload

### Known Limitations

- **CombatHudRenderer** (world-space rendering for grid overlay and 3D enemy HP bars) is stubbed, blocked by Minecraft 1.21.11 render pipeline changes
- Legacy hardcoded level definitions (Level1Definition, Level2Definition) remain in the codebase but are superseded by the procedural generation system
