# Craftics

**Turn-based tactical RPG combat inside Minecraft.**

Craftics turns Minecraft into a tactical RPG. Fight through 18 biomes across the Overworld, Nether, and End in grid-based combat using your actual inventory, weapons, armor, and potions. Every item has a purpose in battle.

&nbsp;

---

&nbsp;

## How It Works

You spawn in a hub room. Click the **Level Select Block** to pick a biome and enter combat. The camera locks to an isometric view, the cursor unlocks, and you control your character on a tile grid. Click to move, click to attack, use items from your hotbar. Defeat all enemies to win, collect loot, and push deeper.

Between fights, you return to your **hub room**. This is your base of operations. Craft gear at the crafting table, smelt ores in the furnace, brew potions at the brewing stand, enchant weapons at the enchanting table, and cook food in the smoker. Tame animals during combat and they'll live in your hub. Build it up over time with blocks and furniture you earn from victories. Everything you do here prepares you for the next fight.

**No new items. No new blocks (except the Level Select).** Craftics gives combat meaning to everything already in the game.


&nbsp;

---

&nbsp;

## Features

### Combat System
- **Action Points (AP):** 3 AP per turn to move, attack, or use items
- **Grid movement** with pathfinding and tile highlights showing your range
- **11 weapon types** with unique abilities:
  - Swords cleave to adjacent enemies
  - Axes stun targets for a full turn
  - Maces smash in a 3x3 AoE with knockback
  - Bows (range 3), Crossbows (range 4, piercing), Tridents (range 3, returning)
  - Diamond Sword crits for double damage. Netherite Sword executes low-HP targets.
- **Enchantments work in combat.** Sharpness adds damage, Protection adds defense, Fire Aspect ignites, Infinity means free arrows, Thorns reflects damage back.
- **Shield blocking.** Offhand shield gives +2 DEF passively, or spend 1 AP to brace for +5 DEF total.

### 28+ Usable Items in Battle
Every item does something:
- **Food** heals (steak = 5 HP, golden apple = 8 HP + absorption)
- **Potions** give turn-based buffs (Strength = +3 ATK for 5 turns)
- **Splash potions** arc through the air with particles, 4-tile range, hit enemies or heal yourself
- **Lingering potions** create poison clouds that tick damage each turn
- **Tipped arrows** apply their effect on hit (poison, slowness, harming)
- **Snowballs** knock enemies back. **Ender pearls** teleport you. **TNT** explodes in a 3x3.
- **Cobwebs** stun. **Fire charges** ignite. **Bells** stun everything in range.
- **Goat horns** have 8 variants with unique combat buffs and debuffs

### Armor Trim Combat System
Every trim pattern gives a **per-piece bonus** that stacks up to 4, plus a **full-set bonus** when all 4 match:

| Trim | Per Piece | Full Set Bonus |
|------|-----------|----------------|
| Sentry | +1 Ranged Power | Counter-attack ranged enemies |
| Wild | +1 AP | First attack each turn is free |
| Ward | +1 Defense | 50% less damage when stationary |
| Vex | Ignore 1 enemy DEF | 20% dodge chance |
| Silence | +1 Stealth | Invisible for first 2 turns |
| Flow | +1 Speed | Kills refund 1 AP |
| Bolt | +1 Melee Power | Crits stun the target |
| *...and 11 more* | | |

Templates drop from bosses. Every trim enables a different playstyle.

### Goat Horn Effects
Rare drops from goats (10% chance). 8 variants, each with its own sound and effect:

| Horn | AP | Effect |
|------|----|--------|
| Ponder | 1 | +2 Defense for 3 turns |
| Sing | 1 | +2 HP regen for 3 turns |
| Seek | 2 | +3 Attack for 3 turns |
| Feel | 1 | +2 Speed for 3 turns |
| Admire | 2 | All enemies -2 Attack for 2 turns |
| Call | 2 | All enemies -1 Speed for 2 turns |
| Yearn | 3 | All enemies Poisoned for 3 turns |
| Dream | 2 | Fire Resistance for 4 turns |


&nbsp;

---

&nbsp;

## 18 Biomes, 90 Levels

Progress through a branching path:

**Overworld:** Plains > Warm branch (Desert, Jungle, Dark Forest) or Cool branch (Snowy Tundra, Stony Peaks) > River Delta > Underground Caverns > **The Deep Dark** (Warden boss)

**Nether:** Nether Wastes > Soul Sand Valley > Crimson Forest > Warped Forest > Basalt Deltas

**The End:** Outer End Islands > End City > Chorus Grove > **Dragon's Nest** (Ender Dragon final boss)

5 levels per biome (4 regular + 1 boss). Branch order is randomized per world so every playthrough is different.


&nbsp;

---

&nbsp;

## 40+ Enemy AI Behaviors

Every mob thinks differently.

- **Skeletons** kite you. They back up while lining up shots. Get within 2 tiles and they panic.
- **Creepers** sneak close, start a fuse (they glow), then blow up next turn. Kill them or run.
- **Endermen** teleport behind you when you attack from the front.
- **Spiders** pounce over obstacles to land next to you.
- **Wolves** hunt sheep and chickens on the field. Hit one and it turns on you permanently.
- **Bees** are passive until you hit one. Then **every bee in the level** swarms you with poison.
- **Goats** wander around until hit, then ram you with knockback and never stop charging.
- **Cats** run from you unless you're holding a fish.
- **Phantoms** swoop in straight lines, damaging everything in their path.

Bosses have **phase transitions.** The Warden gets faster and gains a sonic boom at half HP. The Ender Dragon switches from swoops to charging breath attacks.


&nbsp;

---

&nbsp;

## Player Progression

Beat bosses to level up. Allocate points across 8 stats:

- **Speed** more tiles per turn
- **AP** more actions per turn
- **Melee Power / Ranged Power** bonus damage
- **Vitality** bonus max HP
- **Defense** flat damage reduction
- **Luck** better loot and crit chance
- **Resourceful** more emeralds, cheaper trades

Spend emeralds at **8 types of wandering traders** between levels. Weaponsmiths, Armorers, Alchemists, Craftsmen, and more.


&nbsp;

---

&nbsp;

## New Game+

Beat the Ender Dragon and the game resets, but you keep your stats, emeralds, and gear. Enemies scale +25% per cycle. How far can you go?


&nbsp;

---

&nbsp;

## Fully Configurable

45+ config options via Mod Menu:

- Enemy HP/ATK/DEF scaling per biome
- Boss HP multiplier
- Critical hit chance, ranged accuracy
- Passive mob wander chance, predator hunting toggle
- Permadeath mode, heal between levels
- Enemy animation speed, auto-end turn
- Enemy range hints, intention preview
- Colorblind mode, larger UI, camera shake toggle
- Loot multiplier, trader spawn chance, horn drop rate
- ...and more


&nbsp;

---

&nbsp;

## Modding & Datapacks

Craftics is built for extensibility.

- **Custom arena maps** build in creative, save as `.nbt`, drop in `data/craftics/structures/arenas/`
- **Custom biomes** JSON datapacks define enemies, loot, grid size, and environment style
- **Custom AI** register new mob behaviors through `CrafticsAPI.registerAI()`
- **Custom biome paths** add new biomes into the progression


&nbsp;

---

&nbsp;

## Requirements

- **Minecraft** 1.21.1
- **Fabric Loader** 0.16.9+
- **Fabric API** 0.102.0+
- **owo-lib** 0.12.15+


&nbsp;

---

&nbsp;

## Addon Policy

You're free to create **addons, datapacks, resource packs, and mods** that interact with Craftics through its API. You can also include Craftics in **modpacks** with credit.

You may **not** redistribute Craftics itself, include its source code in your projects, or create standalone copies.

**Allowed:**
- Addons that add new biomes, enemies, AI behaviors, or items via the API
- Datapacks with custom arenas, loot tables, or biome configs
- Resource packs that retexture Craftics content
- Modpacks on CurseForge, Modrinth, or similar platforms (with credit)
- Videos, streams, and reviews (no permission needed)

**Not allowed:**
- Redistributing Craftics outside of modpack launchers
- Copying source code into your own projects
- Re-uploading to other sites without permission


&nbsp;

---

&nbsp;

## Links

- [Source Code](https://github.com/your-repo-here)
- [Issue Tracker](https://github.com/your-repo-here/issues)
- [Discord](https://discord.gg/your-discord-here)
