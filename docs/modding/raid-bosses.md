# Authoring Raid Bosses

A raid boss is a single JSON file: a mob, a fixed health pool, a movepool of six to eight attacks pulled from the shared pool every boss in the mod draws from, an opening advantage it starts the fight already holding, an emerald bounty, and its own loot table. Nothing about a raid boss scales with how many players showed up. Admins write the file by hand or build it in game with `/raidboss edit`, and both routes write the exact same JSON, so there is only ever one artifact per boss.

This page covers the file format, the arena it fights in, the full command tree, and every config key that governs when raids happen.

## Where the files live

Raid bosses live in `config/craftics/raidbosses/`, one `<id>.json` file per boss. The first time that folder does not exist, Craftics copies its two bundled examples into it and logs that it did so. After that first copy the folder is the only source of truth: an admin's edits are never shadowed by a bundled file of the same id on a later update, and deleting a bundled example is permanent.

`/craftics raidboss reload` re-reads every file in the folder from disk without a server restart, for when a file was edited by hand outside the game. `/raidboss edit` (below) patches and hot-reloads a single definition the same way, immediately, without needing `reload` at all.

## The JSON schema

| Field | Type | Required | Default | Notes |
| --- | --- | --- | --- | --- |
| `id` | string | yes | - | Must exactly match the filename stem (`ashen_tyrant.json` needs `"id": "ashen_tyrant"`). |
| `name` | string | yes | - | Display name shown above the boss and in `/raidboss list`. |
| `entity` | string | yes | - | An entity type id, e.g. `minecraft:wither_skeleton`. Not checked against the entity registry at load time (the parser never touches Minecraft registries); an unresolvable id only surfaces as a problem when the boss tries to spawn. |
| `hp` | int | yes | - | Must be positive. Fixed max health, never scaled to party size. A raid can begin with one player, and one player will almost certainly lose. |
| `attack` | int | yes | - | Must be positive. |
| `defense` | int | no | `0` | Floored at 0. |
| `range` | int | no | `1` | Floored at 1. |
| `speed` | int | no | `0` | Floored at 0. |
| `weight` | int | no | `10` | Floored at 1. Relative chance of being picked on a day it is eligible (see `raidBossNoRepeatDays`). |
| `arena` | int | no | `0` | 1-based index into the `raidboss` schem set (see below). `0` or omitted rolls a random variant per raid instance. |
| `environment` | string | no | `"plains"` | Any registered environment id (the same ids biomes use - `plains`, `nether`, `river`, `desert`, `jungle`, `mountain`, `snowy`, `cave`, `deep_dark`, `soul_sand_valley`, `crimson_forest`, `warped_forest`, `basalt_deltas`, `end_city`, `outer_end_islands`, `chorus_grove`, `dragons_nest`, or an addon's own). An unregistered id silently falls back to the plains theme rather than erroring. |
| `moves` | array of strings | yes | - | 6 to 8 ability ids. See **Moves** below. |
| `power` | object | yes | - | Exactly one opening advantage. See **Power** below. |
| `bounty` | int | yes | - | Must be positive. Emerald payout to every raider who did not forfeit, on a win. |
| `loot` | array of objects | yes | - | At least one entry. See **Loot** below. |
| `obstacles` | array of objects | no | `[]` | Arena hazards scattered at build time. See **Obstacles** below. |

### Moves

`moves` names ability ids drawn from the same movepool Infinite Mode uses: every boss in the mod contributes its signature attacks as standalone casts (66 of them at the time of writing, and growing as new boss AIs are added). Tab-complete on `/raidboss edit moves <id> add <ability>` lists every id currently registered.

Validation is soft where it can be: an unknown ability id is dropped with a warning rather than failing the file, duplicates are silently removed, more than 8 is truncated to 8 with a warning, and fewer than 6 warns that the fight will be repetitive but still loads. Only ending up with zero valid ids after filtering is a hard error.

### Power

A raid boss starts the fight already ahead, in exactly one of two ways:

```json
{ "type": "double_move" }
```

The boss acts twice per enemy phase.

```json
{ "type": "buff", "effect": "regeneration", "amplifier": 1 }
```

The boss carries a permanent buff it can never lose or have cleansed, reasserted at the start of every one of its actions. `amplifier` is optional, defaults to `0`, and is floored at `0` (the file format itself has no enforced ceiling, though `/raidboss edit power` restricts it to 0-4 in game). `effect` is matched case-insensitively against:

| Effect | What it does |
| --- | --- |
| `strength` | +3 attack per level. |
| `resistance` | -2 damage taken per level. |
| `speed` | +2 speed per level. |
| `regeneration` | Heals +2 per level, every turn. |
| `absorption` | A one-time bonus to max HP of a quarter of the boss's authored `hp` per level, added and healed at spawn (re-applying it every turn like the others would just keep healing the boss, so this one is spawn-only). |
| `fire_resistance` | Immune to fire and lava damage. |

Only `effect` being present and non-blank is checked at parse time. A typo'd effect name (`"regen"` instead of `"regeneration"`) loads without any error or warning, and the boss simply gets no power at all - double check the spelling against the table above.

### Loot

Each entry is one weighted row:

```json
{ "item": "minecraft:diamond", "weight": 8, "min": 2, "max": 5 }
```

`weight` defaults to 5 and is floored at 1. `min` defaults to 1 and is floored at 1. `max` defaults to `min` and is floored at `min`. An entry with no `item` (or an empty one) is silently skipped; if every entry is skipped, `loot` ends up empty and the file is rejected. On a win, every raider who did not forfeit - including anyone who was downed along the way - gets the bounty and two independent rolls against this table.

### Obstacles

Optional arena hazards the boss scatters through its own arena when a raid starts, before players or the boss occupy any tile:

```json
{ "tile": "lava", "block": "minecraft:lava", "count": { "min": 3, "max": 6 }, "cluster": 3 }
```

| Field | Type | Required | Default | Notes |
| --- | --- | --- | --- | --- |
| `tile` | string | yes | - | A `TileType` name, case-insensitive. An unknown name is dropped with a warning, not a hard error. |
| `block` | string | no | - | What the tile looks like. Left empty, the placer picks a sensible default for that tile type (lava for `lava`, soul fire for `soul_fire`, and so on). An unparseable or unknown block id falls back to the same default rather than erroring. |
| `count` | int, or `{ "min": int, "max": int }` | no | `1` | How many separate placements to roll, inclusive, re-rolled fresh for every raid instance. A bare integer is shorthand for `min == max`. |
| `cluster` | int | no | `1` | How many tiles one placement grows into, by walking outward through free neighbouring tiles. `1` scatters singles; `3` to `6` grows a blob like a lava pool or a fallen tree. |

Placement happens tile by tile after all obstacles are rolled: obstacle-typed tiles (below) are only committed if the arena's playable floor stays fully connected to the player start afterward, and a placement that would cut the arena in two is reverted and the rest of the plan continues. Every other hazard type is always safe to place and skips that check.

**Fire and soul fire placed this way are live.** They are not decoration: they spread to flammable neighbours, collapse to magma, and burn themselves out exactly like a fire lit mid-fight by flint and steel or a fire charge. This is why both bundled examples keep their fire counts small (2 to 6) rather than painting whole rooms - a large seeded fire will happily eat the arena over the following turns. Soul fire also needs soul sand or soul soil under it or vanilla deletes the flame on the next tick; the placer stamps soul soil underneath automatically, the same way `CombatManager` does for soul fire lit in combat.

The tile types below are the ones actually worth scattering as obstacles - the ones the arena builder has explicit placement rules for. Other `TileType` values will still parse and place (most other names just replace the floor block), but they are built for other purposes (mid-fight miniboss mechanics, structural floor classification) and are not guaranteed to behave sensibly as a permanent, build-time arena feature.

| Tile | Behaviour |
| --- | --- |
| `obstacle` | Blocks movement and line of sight, like a rock pile, crate stack or fallen log. The only hazard type checked for connectivity, since it is the only one that can wall off part of the arena. |
| `lava` | Burns for heavy damage on step. |
| `fire` | Live fire - spreads, collapses to magma, burns out. See above. |
| `soul_fire` | Live soul fire - same burn cycle as `fire`, holds its flame a turn longer, and sets whoever stands in it to Burning III. See above. |
| `water` | Walkable without a boat; applies Soaked. |
| `ice` | Walkable, but stepping onto it does not stop movement - the mover slides to the end of the ice and one tile past it. |
| `powder_snow` | Walkable; sinks the mover and applies escalating freeze damage unless they have leather boots. |
| `mud` | Walkable, but each tile crossed has a 50% chance to stop movement there, like a probabilistic cobweb. |
| `tall_grass` / `tall_fern` | Walkable cover: hides whoever stands in it from being targeted except from an adjacent tile, and can be broken by attacking the tile for 1 AP. |

## Bundled examples

Both files below ship with the mod and are copied into `config/craftics/raidbosses/` on first run.

`ashen_tyrant.json`:

```json
{
  "id": "ashen_tyrant",
  "name": "The Ashen Tyrant",
  "entity": "minecraft:wither_skeleton",
  "hp": 900,
  "attack": 14,
  "defense": 6,
  "range": 1,
  "speed": 3,
  "moves": [
    "fireball_rain",
    "lava_cage",
    "summon_wither_skeletons",
    "gore_charge",
    "magma_eruption",
    "tremor_stomp"
  ],
  "power": { "type": "double_move" },
  "environment": "nether",
  "bounty": 64,
  "loot": [
    { "item": "minecraft:netherite_scrap", "weight": 2, "min": 1, "max": 2 },
    { "item": "minecraft:diamond", "weight": 8, "min": 2, "max": 5 },
    { "item": "minecraft:gold_block", "weight": 6, "min": 1, "max": 3 },
    { "item": "minecraft:blaze_rod", "weight": 10, "min": 2, "max": 6 }
  ],
  "obstacles": [
    { "tile": "lava", "block": "minecraft:lava", "count": { "min": 3, "max": 6 }, "cluster": 3 },
    { "tile": "soul_fire", "count": { "min": 2, "max": 4 } },
    { "tile": "obstacle", "block": "minecraft:basalt", "count": { "min": 6, "max": 12 }, "cluster": 2 }
  ],
  "weight": 10
}
```

`tideglass_leviathan.json`:

```json
{
  "id": "tideglass_leviathan",
  "name": "The Tideglass Leviathan",
  "entity": "minecraft:elder_guardian",
  "hp": 1100,
  "attack": 11,
  "defense": 8,
  "range": 3,
  "speed": 2,
  "moves": [
    "tidal_wave",
    "trident_storm",
    "harpoon_pull",
    "call_of_the_deep",
    "riptide_charge",
    "blizzard",
    "ice_wall"
  ],
  "power": { "type": "buff", "effect": "regeneration", "amplifier": 1 },
  "environment": "river",
  "bounty": 72,
  "loot": [
    { "item": "minecraft:heart_of_the_sea", "weight": 1, "min": 1, "max": 1 },
    { "item": "minecraft:prismarine_shard", "weight": 10, "min": 4, "max": 10 },
    { "item": "minecraft:diamond", "weight": 6, "min": 2, "max": 4 },
    { "item": "minecraft:nautilus_shell", "weight": 4, "min": 1, "max": 3 }
  ],
  "obstacles": [
    { "tile": "water", "block": "minecraft:water", "count": { "min": 5, "max": 9 }, "cluster": 5 },
    { "tile": "ice", "block": "minecraft:blue_ice", "count": { "min": 4, "max": 8 }, "cluster": 2 },
    { "tile": "obstacle", "block": "minecraft:prismarine", "count": { "min": 4, "max": 8 } }
  ],
  "weight": 10
}
```

## The `raidboss` arena

A raid boss's arena is one shared "raidboss" schem set, the same disk-then-jar lookup every biome arena uses, just with a fixed biome id (`raidboss`) instead of one derived from a level's biome template. Until a `raidboss` schem exists anywhere Craftics looks, arenas build procedurally instead - a plain checkerboard floor sized for eight players - so the feature works with zero authoring effort and gets better once someone builds a real arena for it.

### Where Craftics looks

In search order, for each of the server's run directory and its parents:

1. `craftics_arenas/raidboss.schem` - the primary variant.
2. `craftics_arenas/raidboss_2.schem` through `raidboss_10.schem` - additional variants at the same flat naming convention WorldEdit produces.
3. `craftics_arenas/raidboss/1.schem`, `2.schem`, ... - the same variants as a subfolder instead, stopping at the first missing number.
4. `config/worldedit/schematics/` and `run/config/worldedit/schematics/` directly - WorldEdit's own save location, searched with the same three patterns above. A freshly `//schem save raidboss`'d file is found there without moving it anywhere.
5. Finally, bundled datapack resources at `data/craftics/arenas/raidboss.schem` or `data/craftics/arenas/raidboss/<n>.schem`, for an addon or resource pack that ships its own set in the jar.

A boss's `arena` field (1-based) pins it to one specific numbered variant; `0` or omitted rolls a random one out of whichever variants exist for every raid that boss runs.

### Marker blocks

Build the arena like any other arena schematic, in creative, then mark it up before exporting:

| Block | Purpose |
| --- | --- |
| Diamond Block | One corner of the playable grid, outside the grid itself. |
| Emerald Block | The corner diagonally opposite the Diamond Block. Together these two define the rectangle that becomes the playable floor. |
| Gold Block | Where the first player (the one who opened the raid) spawns, inside the grid. |

Markers are consumed when the arena is scanned and never appear in the finished build. A raid roster can hold up to eight players, far more than the fixed marker slots other arenas use for player 2 through 4 (Iron/Copper/Coal); the remaining raiders are not assigned individual markers at all; they scatter onto free walkable tiles near the leader's spawn once the roster is pulled into the dimension. Export with WorldEdit (`//copy`, `//schem save raidboss`) the same way any other arena schematic is built - see the Reference page's arena authoring walkthrough for the full marker diagram and export steps shared by every arena type.

## The `/raidboss` command tree

Registered both as its own root (`/raidboss ...`) and under `/craftics raidboss ...` - the two are identical.

### Everyone

| Command | Effect |
| --- | --- |
| `/raidboss` | Joins the open raid if the window is open; otherwise behaves like `info`. |
| `/raidboss info` | Reports the open window and its countdown, or that a boss has been announced and is on its way, or the next scheduled slot and countdown to it. |
| `/raidboss list` | Lists every loaded raid boss's id and name. |

Join can be refused: already joined, mid-biome-run or mid-combat ("finish your run"), visiting another island's dimension, or every raid arena being full (`raidBossMaxInstances` x 8 total joiners across all open instances).

### Admin (permission level 2)

| Command | Effect |
| --- | --- |
| `/raidboss start <id>` | Force-starts a specific boss immediately. Fails if the id is unknown or a raid is already announced, open, or running. |
| `/raidboss cancel` | Cancels a pending, announced, or open raid. |
| `/raidboss reload` | Re-reads every JSON file in the raidbosses folder from disk. |
| `/raidboss schedule` | Prints whether raids are enabled, the current schedule phase, each configured slot's last-fired day, the no-repeat rotation history, and the number of active instances. |

### Authoring (`/raidboss edit ...`, permission level 2)

Every subcommand here validates the resulting definition through the same parser that loads files from disk before writing anything; a change that would make the definition unloadable is refused with the reason and the on-disk file is left untouched. A successful edit writes the file and hot-reloads that one definition immediately, with no restart and no need for a separate `reload`.

| Command | Effect |
| --- | --- |
| `edit create <id> <entity>` | Creates `<id>.json` with a small set of sane defaults (600 HP, double move, six starter abilities pulled from different bosses, a diamond loot entry) ready to tune further. |
| `edit set <id> <field> <value>` | Sets one scalar field: `name`, `entity`, `hp`, `attack`, `defense`, `range`, `speed`, `bounty`, `weight`, `arena` (1-based, as in the JSON), `environment`. |
| `edit moves <id> list` | Lists the current movepool. |
| `edit moves <id> add <ability>` | Adds one ability id (tab-completed from the full pool). Refused if already present or the pool is already at 8. |
| `edit moves <id> remove <ability>` | Removes one ability id. Refused if not present. |
| `edit power <id> double_move` | Sets the power to double move. |
| `edit power <id> buff <effect> <amplifier>` | Sets the power to a permanent buff. `amplifier` is restricted to 0-4 here (tab-completed effect names). |
| `edit loot <id> add <item> <weight> <min> <max>` | Adds one loot row. |
| `edit loot <id> remove <item>` | Removes the loot row for that item id. |
| `edit obstacles <id> list` | Lists the current obstacle rows with their tile, count, cluster and block. |
| `edit obstacles <id> add <tile> <count> [cluster] [block]` | Appends one obstacle row with an exact count (not a min/max range - hand-edit the JSON directly for a rolled range). `cluster` defaults to 1 if omitted; `block` defaults to empty (placer's default) if omitted. |
| `edit obstacles <id> remove <tile>` | Removes every obstacle row for that tile type (a boss can have more than one row for the same tile with different counts or blocks). |
| `edit delete <id> confirm` | Deletes the file and removes the definition from the running registry. |

## Configuration

All of these live under the `raidBosses` section of the Craftics config.

| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `raidBossesEnabled` | `true` | - | Master on/off switch for the whole feature. |
| `raidBossTimes` | `"18:00"` | - | Comma-separated server-local 24-hour times, e.g. `"18:00,21:30"` for two slots a day. |
| `raidBossAnnounceLeadMinutes` | `60` | 5-720 | How long before a slot the server broadcasts the announcement. A player who logs in after the announcement still receives it. |
| `raidBossJoinWindowSeconds` | `300` | 30-1800 | How long the `/raidboss` join window stays open once it opens. |
| `raidBossNoRepeatDays` | `7` | 0-60 | A boss that ran within this many days is excluded from that day's weighted roll. |
| `raidBossMaxInstances` | `8` | 1-32 | Hard cap on concurrent raid dimensions. Each instance holds up to 8 raiders (fixed, not configurable); the cap times 8 is the total number of joiners a window will accept before refusing further ones with "every raid arena is full". |
| `raidBossTurnSeconds` | `45` | 10-300 | Per-player turn timer inside a raid, overriding the normal turn timer. |
| `raidBossAfkStrikes` | `2` | 1-10 | Turn timeouts a player may take before being removed from the raid with no reward. |
| `raidBossMinArenaGrid` | `12` | 4-40 | Logs a warning when a chosen raid arena's playable grid is smaller than this on either axis. |
