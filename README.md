# Craftics

https://legacy.curseforge.com/minecraft/mc-mods/craftics-a-tactical-rpg-mod

**Turn-based tactical RPG combat in Minecraft.**

Craftics is a Fabric mod that turns Minecraft into a tactical RPG. Fight through 18 procedurally generated biome arenas across the Overworld, Nether, and End, using vanilla items, crafting, and potions, in a turn-based combat system with an isometric camera, grid movement, and 60+ unique enemy AI behaviors.

> **No new items. No new blocks.** (Except the Level Select.) Every item already in the game has a purpose in battle.

- **Mod ID:** `craftics`
- **Version:** 0.2.10
- **Minecraft:** 1.21.1, 1.21.3, 1.21.4, 1.21.5
- **Mod Loader:** Fabric (Fabric Loader ≥0.16.0, Fabric API)
- **Required:** owo-lib
- **Optional:** Player Animator, Cardinal Components API, Fantasy
- **Java:** 21+
- **Author:** CrackedGames
- **License:** All Rights Reserved

### Documentation

Full documentation and guides:

| Page | Description |
|------|-------------|
| [**Combat System**](https://chrisatwell27.github.io/Craftics/combat.html) | Weapons, abilities, armor trims, goat horns, items, damage formula |
| [**Enemy AI**](https://chrisatwell27.github.io/Craftics/enemies.html) | 60+ mob behaviors, boss phases, on-hit effects |
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

When you create a world with the Craftics world preset, Minecraft generates a void world with a decorative cottage hub at the origin. From the hub, you interact with a Level Select Block to enter procedurally generated grid arenas where combat plays out on a turn-based system.

The camera locks to a fixed isometric angle (55° pitch, 225° yaw), the mouse cursor is freed for point-and-click interaction, and WASD movement is disabled. You click tiles to move, click enemies to attack, and manage a limited pool of Action Points each turn.

Your real Minecraft inventory is your inventory. Equip a diamond sword for more damage. Wear iron armor for defense. Brew potions for combat buffs. Eat food to heal. Everything ties back to vanilla mechanics.

Between combat encounters, you craft, smelt, brew, and trade using emeralds earned from victories. Biome progression leads through 18 arenas across three dimensions, culminating in a boss fight against the Ender Dragon and a New Game+ system that resets the world with scaled-up enemies while keeping your stats and currency.

---

## Getting Started

### Requirements

- **Java 21** (Eclipse Adoptium JDK 21 recommended)
- **Minecraft** 1.21.1, 1.21.3, 1.21.4, or 1.21.5
- **Fabric Loader** ≥0.16.0
- **Fabric API**
- **owo-lib** (UI framework, the only hard dependency besides Fabric API)

Optional, used when present: Cardinal Components API (data persistence), Player Animator (player animations), Fantasy (runtime island dimensions). Exact dependency versions differ per Minecraft version; see the `gradle.properties` for each shard under `versions/`.

### Building from Source

```bash
git clone <repository-url>
cd Craftics
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

Select the Craftics world preset when creating a new world. This generates a void world with no terrain, no structures, no natural mob spawning. The hub is built automatically at the world origin and you are teleported there on first join.

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

Right-clicking the Level Select Block opens a map-style GUI with three dimension sections (Overworld, Nether, End). Biomes are displayed as a node graph with connecting lines showing progression paths. Locked biomes are grayed out; the current biome glows. The Overworld ordering is fixed per world seed, not chosen at runtime.

Each biome contains 5 levels, except Dragon's Nest which has 3, for 88 total. The last level of each biome is a boss fight. Defeating a boss unlocks the next biome(s) on the path.

### Combat

When you enter an arena:

1. The arena is procedurally generated from the biome template: floor blocks, obstacles, edge terrain, and decorations
2. Enemies spawn at randomized positions based on the biome's mob pool
3. The camera locks to an isometric view
4. The mouse cursor unlocks for point-and-click control
5. WASD movement is disabled
6. Combat begins on the player's turn

Combat alternates between Player Turn and Enemy Turn until all enemies are defeated or the player dies.

### Action Points

The player gets 3 AP per turn (modified by stats and potions).

| Action | AP Cost | Description |
|--------|---------|-------------|
| **Move** | 0 (uses Speed) | Click a highlighted tile. Speed stat (base 3) determines range. |
| **Attack** | 2 AP by default | Per-weapon costs come from the weapon registry; `attackApCost` sets the default. |
| **Ranged Attack** | Same as attack | Bow (range 3), Crossbow (unlimited cardinal range), Trident (range 3). |
| **Use Item** | 1-6 AP | Most items 1 AP. Bell/Jukebox/Crossbow/Spyglass/Elytra/TNT 2 AP. Milk and Fishing Rod 3 AP. Goat horns 5 AP. Pottery sherds 2-6 AP. |
| **Block** | Passive | Shield in offhand. Grants +1 Armor Class and a 25% chance to block an attack. |
| **End Turn** | 0 AP | Press **R** to end your turn. Remaining AP is lost. |

### Damage Calculation

```
rawDamage = Weapon Attack + Stat Bonuses + Damage Type Bonus + Enchantments
```

Defense does not reduce damage. Armor grants **Armor Class**, which is a chance to dodge the hit entirely. A hit that lands deals full damage. Each armor piece contributes AC by material; full-set totals run from 7 (leather, gold) to 23 (netherite). See `ArmorClassTable.java` and `DodgeRoll.java`.

**Weapon Attack Power** (defaults, all configurable):

| Weapon | Attack | Range | Damage Type |
|--------|--------|-------|-------------|
| Fist | 1 | 1 | Physical |
| Wooden Sword | 5 | 1 | Slashing |
| Stone Sword | 6 | 1 | Slashing |
| Iron Sword | 9 | 1 | Slashing |
| Diamond Sword | 12 | 1 | Slashing |
| Golden Sword | 14 | 1 | Slashing |
| Netherite Sword | 15 | 1 | Slashing |
| Axes (wood to netherite) | 8 to 27 | 1 | Cleaving |
| Bow | 11 | 3 | Ranged |
| Crossbow | 14 | Cardinal (unlimited) | Ranged |
| Trident | 10 | 1 / 3 (thrown) | Water |
| Mace | 15 | 1 | Blunt |
| Stick / Bamboo | 2 / 6 | 1 | Blunt |
| Blaze Rod / Breeze Rod | 12 | 1 | Blunt |
| Corals | 2 to 11 | 1 | Water |

**Weapon durability:** Each attack costs 10 durability. Weapons break during combat.

### Damage Types

There are 8 damage types: Slashing, Cleaving, Blunt, Water, Special, Pet, Ranged, and Physical. Bonus damage from damage types stacks from three sources:

**Armor Set Classes** (full 4-piece set bonus):

| Armor Set | Class | Bonuses | Damage Affinity |
|-----------|-------|---------|-----------------|
| Leather | Brawler | +2 Speed, +1 AP, +2 Fist damage, 2x kill streak | Physical +2 |
| Chainmail | Rogue | +1 Speed, attacks cost -1 AP (min 1) | Sword +2 |
| Iron | Guard | +2 Defense, immune to knockback | Cleaving +2 |
| Gold | Gambler | +3 Luck/crit, +1 emerald per kill | Magic +2 |
| Diamond | Knight | +3 Defense, +1 Attack | Blunt +2 |
| Netherite | Juggernaut | +4 Defense, +2 Attack, fire immune | ALL +1 |
| Turtle | Aquatic | Water walkable, +1 HP regen, +1 water range | Water +3 |

**Armor Trim Bonuses** (per-piece, max +4 from 4 armor pieces):

| Trim Pattern | Bonus |
|--------------|-------|
| Bolt | Sword Power +1 |
| Snout | Cleaving Power +1 |
| Coast | Water Power +1 |
| Rib | Magic Power +1 |
| Sentry | Ranged Power +1 |
| Raiser | Pet/Ally Damage +1 |
| Wild | AP +1 |
| Ward | Defense +1 |
| Dune | Blunt Power +1 |
| Eye | Attack Range +1 |
| Vex | Armor Penetration +1 |
| Tide | HP Regen +1 |
| Spire | Luck +1 |
| Wayfinder | Speed +1 |
| Shaper | Defense +1 |
| Silence | Stealth Range +1 |
| Host | Max HP +1 |
| Flow | Speed +1 |

Wearing 4 pieces with the same trim pattern activates a full-set bonus:

| Trim | Name | Full Set Bonus |
|------|------|---------------|
| Sentry | Overwatch | Counter-attack ranged enemies |
| Wild | Feral | 1.3x damage per kill streak level |
| Ward | Fortress | 50% less damage when stationary |
| Vex | Ethereal | 20% dodge chance |
| Silence | Phantom | Invisible for first 2 turns |
| Flow | Current | Kills refund 1 AP |
| Bolt | Thunderstrike | Crits stun the target |
| Dune | Sandstorm | Enemies within 2 tiles lose 1 Speed |
| Coast | Tidal | Water tiles heal 1 HP/turn |
| Snout | Brute Force | Melee attacks splash to adjacent |
| Rib | Infernal | Fire attacks deal +3 damage |
| Spire | Fortune's Peak | Double emerald rewards |
| Host | Symbiote | Heal 1 HP per enemy killed |

Templates drop from bosses. See the [full trim reference](https://chrisatwell27.github.io/Craftics/combat.html#armor-trims) for all 18 patterns, trim material bonuses, and remaining full-set effects.

**Combat Effect Bonuses:**

| Effect | Bonus |
|--------|-------|
| Water Breathing | Water +2 |
| Fire Resistance | Magic +1 |

### Weapon Abilities

Each weapon type has a unique combat ability:

| Weapon | Ability | Effect |
|--------|---------|--------|
| **Swords** | Sweeping Edge | Hits the target AND one adjacent enemy for half damage |
| **Diamond Sword** | Sweep + Crit | 30% chance to deal double damage |
| **Netherite Sword** | Sweep + Execute | Triple damage on targets below 30% HP |
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

The mod includes 60+ unique AI strategies registered for 63 mob types. Unregistered mobs default to passive behavior (flee when attacked). Each AI controls movement, targeting, and special mechanics. See the [full Enemy AI reference](https://chrisatwell27.github.io/Craftics/enemies.html) for every mob.

| AI | Mobs | Key Behavior |
|----|------|-------------|
| **PassiveAI** | Cow, Pig, Sheep, Chicken, Rabbit | Idle; flees when attacked |
| **GoatAI** | Goat, Wolf | Passive until hit, then 50% ram (knockback) or flee |
| **ZombieAI** | Zombie | Beeline melee, speed 1 |
| **HuskAI** | Husk | Zombie variant with +1 bonus damage (hunger) |
| **DrownedAI** | Drowned | 50% spawn trident; diagonal throw range ≤3 or melee rush |
| **SkeletonAI** | Skeleton | Ranged (range 3), seeks cardinal line-of-sight |
| **StrayAI** | Stray | Ranged + kiting (prefers max distance) |
| **PillagerAI** | Pillager | Crossbow (range 4), retreats from melee |
| **VindicatorAI** | Vindicator | Speed 2 aggressive melee rush |
| **CreeperAI** | Creeper | Approaches → fuse when adjacent → explodes next turn (2× damage, radius 1). Fuse resets if player moves away |
| **SpiderAI** | Spider | 2×2 body, speed 2, pounce attack over obstacles |
| **WitchAI** | Witch | Potion throw (range 2-3), random debuffs, keeps distance |
| **EndermanAI** | Enderman | Teleports behind player to attack; teleports away when damaged |
| **PhantomAI** | Phantom | Cardinal-line swoops (speed 4), damages all tiles in path |
| **OcelotAI** | Ocelot | Speed 2 hit-and-run; strikes then flees |
| **MountedAI** | Camel | Speed 3 charge; dismounts at 50% HP (speed drops to 1) |
| **GhastAI** | Ghast | Long-range fireball (range 5-6), flees from close range |
| **BlazeAI** | Blaze | Medium-range fire (range 3-4), applies burning, retreats from melee |
| **ShulkerAI** | Shulker | Stationary turret, range 4-5, high defense, rarely moves |
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

There is a 25% chance a trader appears between levels when continuing, set by `traderSpawnChance`.

**Defeat:** If the player dies:
- Inventory is cleared (items from the current run are lost)
- The player is teleported back to the hub
- The biome run resets (must start from level 1 of that biome)
- No permadeath. Progress and stats are kept

### Trader System

Traders appear randomly between combat levels (25% chance, configurable). Trader types are an open registry that addons can extend. The built-in ones:

| Trader | Specialty |
|--------|-----------|
| Weaponsmith | Weapons (scales wood to netherite by tier) |
| Armorer | Armor sets |
| Provisioner | Food, golden apples, golden carrots |
| Alchemist | Potions and brewing ingredients |
| Supplier | Raw materials (iron, gold, diamond, netherite) |
| Decorator | Hub furnishing (lanterns, paintings, beds) |
| Craftsman | Crafting stations (furnace, anvil, enchanting table) |
| Curiosity Dealer | Rare items (trident, totem, elytra) |

Trades cost emeralds. Each encounter offers 3-5 random trades. Quality scales with biome tier (1-9).

### Player Progression

Defeating a biome boss grants a level-up with stat points to allocate. There are 8 stats:

| Stat | Base | Effect |
|------|------|--------|
| **Speed** | 3 | +1 movement per turn, per point |
| **AP** | 3 | +1 action per turn, per point |
| **Melee Power** | 0 | +1 and +6% melee damage, per point |
| **Ranged Power** | 0 | +1 and +6% ranged damage, per point |
| **Vitality** | 0 | +8 max HP per point |
| **Defense** | 0 | +2 Armor Class per point (dodge chance, not damage reduction) |
| **Luck** | 0 | +8% crit and +2% loot, per point |
| **Resourceful** | 0 | +2 emeralds per level and a trader discount, per point |

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

The mod features 18 biomes across three dimensions. The Overworld has two possible orderings; the Nether and End are linear.

### Overworld (8 Biomes)

The Overworld has two fixed orderings. One is chosen per world seed, and you visit all 9 biomes either way.

```
Branch 0 (Warm First):
Plains → Scorching Desert → Dense Jungle → Dark Forest → River Delta → Snowy Tundra → Stony Peaks → Underground Caverns → The Deep Dark

Branch 1 (Cool First):
Plains → Snowy Tundra → Stony Peaks → River Delta → Scorching Desert → Dense Jungle → Dark Forest → Underground Caverns → The Deep Dark
```

| Biome | Grid Size | Environment | Boss | Notable Mechanics |
|-------|-----------|-------------|------|-------------------|
| **Plains** | 8×8 (+1 width) | Grassy hills, flowers | The Revenant | Tutorial biome, open field |
| **Scorching Desert** | 10×10 (+1 both) | Sand dunes, cacti | Sandstorm Pharaoh | Open terrain, cactus damage |
| **Dense Jungle** | 11×11 (+1 width) | Dense vegetation, vines | The Broodmother | Heavy obstacles, vine cover |
| **Dark Forest** | 9×9 (+1 width) | Dense trees, dark atmosphere | The Hexweaver | Tree obstacles, low visibility |
| **River Delta** | 10×10 (+1 both) | Sandy banks, water features | The Tidecaller | Water edge tiles |
| **Snowy Tundra** | 9×9 (+1 both) | Snow, ice patches | Frostbound Huntsman | Powder snow edges |
| **Stony Peaks** | 10×10 (+1 width) | Stone terrain | The Rockbreaker | Minimal cover |
| **Underground Caverns** | 11×11 (+1 both) | Cave structures, ore veins | The Hollow King | Dense obstacles, night mode |
| **The Deep Dark** | 12×12 (+1 both) | Deepslate, sculk | **The Warden** | Very dark, high danger |

### Nether (5 Biomes)

Unlocked after completing The Deep Dark:

| Biome | Grid Size | Environment | Boss | Notable Mechanics |
|-------|-----------|-------------|------|-------------------|
| **Nether Wastes** | 9×9 (+1 width) | Netherrack, fire | The Molten King | Lava edges, fire hazards |
| **Soul Sand Valley** | 9×9 (+1 both) | Soul sand, soul fire | Wailing Revenant | Slow terrain |
| **Crimson Forest** | 10×10 (+1 width) | Crimson nylium, fungi | The Bastion Brute | Dense fungal obstacles |
| **Warped Forest** | 10×10 (+1 both) | Warped nylium | The Void Walker | Sparse obstacles |
| **Basalt Deltas** | 10×10 (+1 both) | Basalt, magma | **The Wither** | Lava edges, dense pillars |

### The End (4 Biomes)

Unlocked after completing Basalt Deltas:

| Biome | Grid Size | Environment | Boss | Notable Mechanics |
|-------|-----------|-------------|------|-------------------|
| **Outer End Islands** | 10×10 (+1 both) | End stone, chorus | The Void Herald | Void edges, sparse |
| **End City** | 10×10 (+1 both) | Purpur, end bricks | Shulker Architect | Shulker turrets |
| **Chorus Grove** | 11×11 (+1 both) | Chorus plants | The Chorus Mind | Organic obstacles |
| **Dragon's Nest** | 12×12 (+1 both) | End stone | **Ender Dragon** | Void edges, final boss. 3 levels, not 5 |

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
| **Scroll Wheel** | Zoom camera in/out (±1.5 per notch, range 8-30) |
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
│   │   └── CrafticsAPI.java          : Public modding API (23 registration methods, plus a registry/ package)
│   ├── block/
│   │   ├── ModBlocks.java               : Block registration
│   │   ├── LevelSelectBlock.java        : Interactive block with GUI
│   │   ├── LevelSelectBlockEntity.java  : Block entity with screen data
│   │   ├── LevelSelectScreenHandler.java : Screen handler
│   │   └── ModScreenHandlers.java       : Screen handler registration
│   ├── combat/
│   │   ├── CombatManager.java           : Server-side combat state machine (~14,000 lines)
│   │   ├── CombatEntity.java            : Combat enemy data
│   │   ├── CombatPhase.java             : Phase enum
│   │   ├── CombatEffects.java           : Turn-based effect system (24 effects)
│   │   ├── PlayerCombatStats.java       : Equipment → combat stat reader
│   │   ├── PlayerProgression.java       : Persistent stat/leveling system
│   │   ├── Pathfinding.java             : A* pathfinding + BFS reachability
│   │   ├── WeaponAbility.java           : Per-weapon-type abilities
│   │   ├── ItemUseHandler.java          : Food, potions, throwables in combat
│   │   ├── TileHighlightManager.java    : Carpet-based tile highlights
│   │   ├── LootPool.java               : Weighted loot rolling
│   │   ├── TraderSystem.java            : tiered loot pools, registry-driven trader types
│   │   └── ai/
│   │       ├── EnemyAI.java             : AI interface
│   │       ├── EnemyAction.java         : 34 action types (sealed interface)
│   │       ├── AIRegistry.java          : 78+ mob→AI mappings
│   │       ├── AIUtils.java             : Shared AI utilities
│   │       └── (65 AI classes)           : One per mob behavior archetype (including 17 boss AIs)
│   ├── core/
│   │   ├── GridArena.java               : Width×height tile grid with occupants
│   │   ├── GridPos.java                 : 2D grid coordinate
│   │   ├── GridTile.java                : Tile data (type, block, timer)
│   │   └── TileType.java               : 14 types incl. NORMAL, OBSTACLE, LAVA, FIRE, VOID, WATER, DEEP_WATER, POWDER_SNOW, STAIR, ELEVATED
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
│   │   ├── EnvironmentStyle.java        : 13 visual styles enum
│   │   └── MobPoolEntry.java            : Enemy pool data record
│   ├── network/
│   │   ├── ModNetworking.java           : Registers 21 C2S + 27 S2C payloads
│   │   └── (50 payload record classes)  : Individual packet definitions
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
│       ├── AttackSuppressMixin.java     : Suppress vanilla attacks in combat
│       ├── CameraLockMixin.java         : Isometric camera lock
│       ├── EnderDragonRendererMixin.java: Dragon visibility during combat
│       ├── FovLockMixin.java            : Lock FOV during combat
│       ├── HudTransitionMixin.java      : HUD transition effects
│       ├── InputAccessor.java           : Movement vector accessor
│       ├── InventoryStatsMixin.java     : Stat panel in inventory screen
│       ├── MouseUnlockMixin.java        : Free cursor in combat
│       ├── MovementDisableMixin.java    : Disable WASD in combat
│       ├── OverlayMessageMixin.java     : Action bar → combat log
│       ├── PlayerListHudMixin.java      : Player list HUD changes
│       ├── PlayerNameTagMixin.java      : Player name tag rendering
│       ├── ScrollZoomMixin.java         : Scroll wheel → zoom
│       ├── TitleScreenMixin.java        : Title screen modifications
│       └── WorldCreatorMixin.java       : Default world preset to Craftics
│
└── client/resources/
    └── craftics.client.mixins.json   : 15 client mixins
```

**Total: 624 Java files.**

### Core Systems

- **CombatManager**: Server-authoritative state machine driving the entire combat lifecycle. Handles player actions, A* pathfinding, animated movement (lerp over 4 ticks), weapon abilities, enemy AI turns (with per-phase delays: DECIDING→MOVING→ATTACKING→DONE), per-turn status effects, environmental hazard damage, victory/defeat conditions, loot distribution, and biome progression.

- **GridArena**: 2D tile array with occupant tracking. Supports multi-tile entities (e.g., 2×2 spiders). Arena origins are spaced at `X = level × 1000, Y = 100, Z = 0` to prevent collision.

- **Pathfinding**: A* pathfinding for movement with BFS flood-fill for reachable tile calculation. Respects obstacles, multi-tile entities, and movement speed limits.

- **PlayerProgression**: Persistent per-player stat system using Minecraft's `PersistentState` and `Codec` serialization. UUID-based for multiplayer support.

- **CombatEffects**: Turn-based effect system replacing Minecraft's tick-based effects. 24 distinct effects with frozen state (apply in hub, activate on combat start).

- **BiomeRegistry**: Central registry loading biomes from JSON datapacks (`data/*/craftics/biomes/*.json`) and programmatic API registration. Hot-reloadable via `/reload`.

### Client Mixins

| Mixin | Target | Purpose |
|-------|--------|---------|
| **AttackSuppressMixin** | `ClientPlayerInteractionManager` | Suppresses vanilla attacks during combat |
| **CameraLockMixin** | `Camera.update` | Locks camera to isometric view (55° pitch, 225° yaw) centered on arena |
| **EnderDragonRendererMixin** | `EnderDragonEntityRenderer` | Hides dragon when off-stage during boss fight |
| **FovLockMixin** | `GameRenderer` | Prevents FOV changes during combat |
| **HudTransitionMixin** | `InGameHud` | Combat HUD transition effects |
| **InputAccessor** | `Input` | Accessor mixin exposing `setMovementVector` |
| **InventoryStatsMixin** | `InventoryScreen.render` | Renders player stats panel alongside the inventory screen |
| **MouseUnlockMixin** | `Mouse.lockCursor` | Prevents cursor grab during combat for free mouse interaction |
| **MovementDisableMixin** | `KeyboardInput.tick` | Zeroes all movement input during combat |
| **OverlayMessageMixin** | `InGameHud.setOverlayMessage` | Routes action bar messages to the combat log for persistent display |
| **PlayerListHudMixin** | `PlayerListHud` | Customizes player list display |
| **PlayerNameTagMixin** | `EntityRenderer` | Customizes player name tag rendering |
| **ScrollZoomMixin** | `Mouse.onMouseScroll` | Intercepts scroll wheel for zoom control (range 8-30 blocks) |
| **TitleScreenMixin** | `TitleScreen` | Title screen modifications |
| **WorldCreatorMixin** | `WorldCreator` | Defaults world preset to Craftics void world |

### Networking

21 client-to-server payloads, including:

| Payload | Purpose |
|---------|---------|
| `StartLevelPayload` | Request to start a combat level |
| `CombatActionPayload` | Player combat action (move, attack, use item, end turn) |
| `PostLevelChoicePayload` | Go Home vs Continue after victory |
| `TraderBuyPayload` | Purchase a trade |
| `TraderDonePayload` | Finish trading |
| `StatChoicePayload` | Allocate a stat point |
| `AffinityChoicePayload` | Choose damage affinity |
| `EventChoicePayload` | Event room choice (shrine/traveler/vault) |
| `RespecPayload` | Refund and reallocate stat points |
| `HoverUpdatePayload` | Relay tile hover to party members |

27 server-to-client payloads, including:

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
| `TileSetPayload` | Dynamic tile state updates |
| `TeammateHoverPayload` | Relay teammate hover position |
| `EventRoomPayload` | Event room encounter data |
| `AchievementUnlockPayload` | Achievement unlock notification |
| `GuideBookSyncPayload` | Guide book data sync |
| `AddonBonusSyncPayload` | Addon bonus data sync |
| `LoadingScreenPayload` | Loading screen state |
| `ScoreboardSyncPayload` | Scoreboard data sync |

### World Generation

- **VoidChunkGenerator**: Produces an empty world with no terrain, entities, or structures. Codec-serialized for world preset compatibility.
- **HubRoomBuilder**: Constructs a decorated cottage at the world origin with cobblestone foundation, oak plank floor, dark oak posts, windows, pitched roof, porch, chimney, furniture, and lighting. Supports versioned rebuilds.
- **CrafticsSavedData**: Persistent world-level state stored via Minecraft's `PersistentState` system. Tracks hub state, biome progression, emerald currency, active biome run, branch choices, discovered biomes, and NG+ level.

### Data-Driven Biomes

All 18 built-in biomes are defined as JSON files in `data/craftics/craftics/biomes/`. The schema supports:

- Grid dimensions with per-level growth
- Floor and obstacle block lists
- Obstacle density with per-level scaling
- Environment style (13 visual styles)
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
Mark the playable grid area with Gold Block (player start corner) and Emerald Block (opposite corner). Everything outside the markers becomes terrain/decoration. Multiple numbered presets per biome (1.nbt, 2.nbt, etc.) are picked randomly each fight.

See the [online Modding Guide](https://chrisatwell27.github.io/Craftics/modding.html) for the full custom maps guide with step-by-step instructions.

### Datapack Biome Modding (No Code)

Create custom biomes by adding JSON files to a datapack at:
```
data/<namespace>/craftics/biomes/<biome_name>.json
```

Override built-in biomes by using the same biome `id`. Add multiple biome variations by creating separate JSON files with unique IDs. See the [online Modding Guide](https://chrisatwell27.github.io/Craftics/modding.html) for the full JSON schema reference, field documentation, and examples.

### Fabric Mod Integration (Java API)

Implement the `CrafticsAddon` entrypoint so your registrations run at the right time. Declare it under the `"craftics"` entrypoint in your `fabric.mod.json`:

```java
public class MyAddon implements CrafticsAddon {
    @Override
    public void onCrafticsInit() {
        CrafticsAPI.registerAI("mymod:custom_mob", new MyCustomAI());
        CrafticsAPI.registerWeapon(myWeaponEntry);
        CrafticsAPI.registerUsableItem(myItemEntry);
    }
}
```

`CrafticsAPI` exposes 23 registration methods covering AI, weapons, usable items, enemies, allies, effects, environments, campaigns, armor sets, hybrid sets, trim patterns and materials, events, barter categories and rewards, traders, enchantments, and attack animations.

Custom AI implements the `EnemyAI` interface with a single method:
```java
EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos);
```

`EnemyAction` is a sealed interface with 34 record types, from `Move` / `Attack` / `RangedAttack` through to the composite and boss-specific actions (`BossAbility`, `SummonMinions`, `CreateTerrain`, `CompositeAction`, and others).

See the [online Modding Guide](https://chrisatwell27.github.io/Craftics/modding.html) for the full reference, the Maven coordinate, and examples.

---

## Configuration

100+ config options via Mod Menu:

- **Combat Balance:** Enemy HP/ATK/DEF scaling per biome, boss HP multiplier, critical hit chance, ranged accuracy
- **Mob Behavior:** Passive mob wander chance, predator hunting toggle, enemy animation speed, auto-end turn
- **Difficulty:** Permadeath mode, heal between levels, enemy range hints, intention preview
- **Accessibility:** Colorblind mode, larger UI, camera shake toggle
- **Economy:** Loot multiplier, trader spawn chance, horn drop rate, emerald rewards

---

## Addon Policy

You're free to create addons, datapacks, resource packs, and mods that interact with Craftics through its API. You can also include Craftics in modpacks with credit.

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
- [x] Weapon abilities (sweep, stun, pierce, smash, crit, execute)
- [x] Equipment-based combat stats
- [x] 60+ unique enemy AI strategies for 63 mob types
- [x] Turn-based status effects (24 effects, frozen effects)
- [x] Food, potion, and throwable item use in combat
- [x] Tile highlights (carpet-based movement/attack display)
- [x] Tile raycasting (cursor → grid position)
- [x] Combat HUD (HP bar, AP pips, turn indicator, enemy list, combat log)
- [x] 18 biome definitions (JSON datapacks)
- [x] Branching biome progression across 3 dimensions
- [x] Level Select map GUI with node graph
- [x] Victory/defeat flow with Go Home/Continue choice
- [x] Trader system (8 types, tier-scaled inventory)
- [x] Player progression (8 stats, level-up screen)
- [x] Emerald economy
- [x] New Game+ system (+25% enemy scaling per cycle)
- [x] Persistent world state (progress, stats, currency)
- [x] Client/server networking (48 packet types)
- [x] Combat tooltips on items
- [x] Stat panel in inventory screen
- [x] Modding API (custom AI + custom biomes)
- [x] Datapack biome loading with hot reload

### Known Limitations

- **CombatHudRenderer** (world-space rendering for grid overlay and 3D enemy HP bars) is stubbed, blocked by Minecraft 1.21.1 render pipeline changes
- Legacy hardcoded level definitions (Level1Definition, Level2Definition) remain in the codebase but are superseded by the procedural generation system
