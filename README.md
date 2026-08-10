# Craftics

**Turn-based tactical RPG combat in Minecraft.**

[CurseForge](https://legacy.curseforge.com/minecraft/mc-mods/craftics-a-tactical-rpg-mod) · [Documentation](https://chrisatwell27.github.io/Craftics/)

Craftics replaces Minecraft's real-time fighting with a grid-based tactical RPG. You get a personal island, a hub, and a level select that sends you into procedurally generated arenas where combat runs on turns, action points, and isometric point-and-click. Your gear is your build: the sword in your hand, the armor set you wear, the trims on it, the potions you brewed, and the throwables in your pack all resolve into different tactics.

Almost nothing here is a new item. The mod adds the Level Select Block, a Guide Book, a locked-slot Move tool, and a few admin marker blocks. Everything else in a fight is vanilla content given a combat role.

| Field | Value |
|---|---|
| **Mod ID** | `craftics` |
| **Version** | 0.3.6.3 |
| **Minecraft** | 1.21.1, 1.21.3, 1.21.4, 1.21.5 |
| **Loader** | Fabric (Loader >=0.16.0) |
| **Required** | Fabric API, owo-lib |
| **Optional** | Cardinal Components API, Player Animator, and compat for Simply Swords, Basic Weapons, Artifacts, Creeper Overhaul, Golem Overhaul, Variants and Ventures, More Totems, Deeper and Darker, Immersive Armors, Paladins, and others |
| **Java** | 21+ |
| **License** | All Rights Reserved |

## Documentation

The wiki is the real reference. This file is only an overview.

| Page | Covers |
|------|--------|
| [Combat](https://chrisatwell27.github.io/Craftics/combat.html) | Weapons, abilities, armor sets, trims, damage formula, action points |
| [Enemies](https://chrisatwell27.github.io/Craftics/enemies.html) | Every mob AI, boss phases, on-hit effects |
| [Biomes](https://chrisatwell27.github.io/Craftics/biomes.html) | The 18 campaign biomes, branching order, arena generation |
| [Progression](https://chrisatwell27.github.io/Craftics/progression.html) | Stats, leveling, traders, NG+, build guides |
| [Effects](https://chrisatwell27.github.io/Craftics/effects.html) | The turn-based status effects |
| [Items](https://chrisatwell27.github.io/Craftics/items.html) | Food, potions, throwables, horns, sherds |
| [Modding](https://chrisatwell27.github.io/Craftics/modding.html) | Custom arenas, JSON biomes, campaigns, raid bosses, Java API |

## Install

Drop the JAR for your Minecraft version into `mods/` alongside Fabric API and owo-lib.

Create a world with the **Craftics** world preset (it is the default in the world creation screen). You land in the central lobby. Run `/new` to get your personal island, or `/new hardcore` for one that is deleted along with your items, XP, and progression if your whole party wipes.

`/home` returns you to your island hub, `/lobby` to the lobby.

## How it plays

Right-click the Level Select Block in your hub to open the map and pick a level. When a fight starts the camera swings to an isometric view, the cursor unlocks, and WASD is disabled. You click tiles to move and enemies to attack, spending a pool of action points (3 by default) each turn.

Damage is not reduced by armor. Armor grants Armor Class, which is a chance to dodge the hit outright, capped at 60%. A hit that lands lands in full.

Between fights you craft, brew, and spend emeralds with traders. Emeralds are a banked currency, not a stack in your inventory.

### Controls

| Input | Action |
|-------|--------|
| Left click | Move to tile, attack enemy, or use the held item (context-sensitive) |
| `R` | End turn |
| `G` | Guide book |
| `Y` | Threat overlay |
| `U` | Toggle combat UI |
| `H` / `J` | Respec stats / affinities |
| `M` | Mount ability |
| Arrow keys | Cycle move slot |
| Scroll | Zoom (8 to 22 blocks) |
| Right-drag | Orbit camera (default 55° pitch, 225° yaw) |
| Middle-drag | Pan camera, shift+middle-click resets it |

All of these are rebindable under Options > Controls.

## What is in it

**Campaign.** 18 biomes across the Overworld, Nether, and End, 7 levels each (Dragon's Nest has 3), each ending in a boss. The Overworld order branches per world seed, and you visit all nine either way. Beating the Ender Dragon starts New Game+, which keeps your stats and emeralds and scales enemies up.

**Infinite mode.** A roguelike loop with its own wallet and its own level-1 profile. Five levels per biome, a randomized boss, a rest room, then another random biome forever. Difficulty keys off your cleared count, and the best score is banked on a global board.

**Daily raid bosses.** 62 boss definitions on a weighted rotation with a no-repeat window. The server announces the raid an hour ahead, opens a 5 minute join window, and packs up to 8 players per arena, spawning duplicate instances beyond that. Every survivor and every corpse gets the bounty. Fully JSON-defined, including move pools and arena obstacles.

**Islands and multiplayer.** Each player owns a persistent runtime dimension holding their hub, arenas, traders, and scenes. Idle islands unload. Parties run biomes together under a leader, and `/visit` grants look-only access to someone else's island.

**Enemies.** 74 AI classes including 25 boss AIs, plus minibosses with their own arena mechanics. Enemies telegraph their attacks with tile highlights through the early biomes, and the threat overlay shows their reach.

**Terrain.** 27 tile types. Lava, water, ice you slide on, mud that stops you, spreading fire, soul fire, sculk jaws, fungus that debuffs on contact, decay the Wither leaves behind. Arenas are procedurally generated or loaded from 75 packaged schematics, with per-biome environmental effects on top.

**Gear depth.** Armor set classes, 18 trim patterns with per-piece and full-set bonuses, 40 custom enchantments, weapon abilities per weapon type, goat horns, pottery sherd spells, and combat roles for food, potions, and throwables. 25 turn-based status effects, converted from Minecraft's tick-based ones.

**Rewards.** Emerald economy, weighted biome loot, eight trader types, random events between levels, trial chamber encounters, and emerald-priced lootboxes with a full public odds table (`/craftics lootbox odds <type>`). 86 achievements.

## Building from source

```bash
git clone https://github.com/ChrisAtwell27/Craftics.git
cd Craftics
./gradlew build
```

The project uses [Stonecutter](https://stonecutter.kikugie.dev/) to build all four Minecraft versions from one source tree. Version-specific code lives in `//?` comment blocks. Output JARs land in `versions/<mc_version>/build/libs/`.

`./gradlew runClient` starts a dev client on the active shard (1.21.1 by default). Java 21 is required, and Gradle needs 4GB of heap for the parallel remap.

## Modding

Three levels, in ascending order of effort.

**Custom arenas, no code.** Export a build as a `.nbt` or `.schem` and drop it in `data/craftics/arenas/<biome_id>/`. Mark the playable grid with a Gold Block at one corner and an Emerald Block at the other. Numbered presets are picked at random per fight.

**Datapack JSON, no code.** Biomes go in `data/<namespace>/craftics/biomes/`, campaigns in `craftics/campaigns/`, raid bosses in `data/craftics/raidbosses/`. Reuse a built-in `id` to override it. `/reload` picks up changes without a restart.

**Java addon.** Implement `CrafticsAddon` and declare it under the `"craftics"` entrypoint in your `fabric.mod.json`:

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

`CrafticsAPI` has 23 registration methods spanning AI, weapons, usable items, enemies, allies, effects, environments, campaigns, armor sets, trims, events, barter content, traders, enchantments, and attack animations. Custom AI implements one method returning an `EnemyAction`, a sealed interface with 38 record types.

See the [modding guide](https://chrisatwell27.github.io/Craftics/modding.html) for schemas, the Maven coordinate, and worked examples.

## Addon policy

**Allowed:** addons through the API, datapacks and resource packs, modpacks on CurseForge or Modrinth with credit, videos and streams.

**Not allowed:** redistributing Craftics outside modpack launchers, copying the source, re-uploading without permission.
