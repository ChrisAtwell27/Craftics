# Setting up a Village or Bartering Station scene

The Village and Bartering Station are walk-around merchant hubs the player teleports into from the level select. A scene is built from a schematic, the same way arenas are, using three **scene marker blocks**: one that sets where the player appears, a pair that marks the clickable area of each merchant booth ("stand"), and one that places the booth's NPC. You build the layout, drop the `.schem` in the scenes folder, and Craftics scans the markers when the scene loads.

The Village and the Bartering Station are authored exactly the same way. The only differences are which occupant ids you give the NPC markers (villager professions vs piglin barter categories) and which file name you save under (`village.schem` vs `barter_station.schem`).

> Note on current status: the shipped tooling is the marker blocks and the scanner that turns a placed schematic into a layout. The in-game runtime that consumes the scene (teleport, third person, click to walk between booths, trading) is a later unit and is not wired up yet. This guide is the authoring workflow for building the scenes.
>
> Markers are invisible in the finished scene. When the scene is scanned, each marker block is replaced with the most common block touching it, so it blends into the surrounding floor and the player never sees a marker (the same way arena corner markers disappear).

## The marker blocks

Give yourself the blocks in creative:

```
/give @s craftics:scene_spawn
/give @s craftics:stand_marker
/give @s craftics:npc_marker
```

| Block | How many | What it does |
| --- | --- | --- |
| `craftics:scene_spawn` | Exactly one per scene | Where the player materializes when they enter, and the direction the third-person camera faces. Place it looking toward the booths. The block remembers the way you were facing when you placed it. If a scene has no spawn marker, the scanner reports an authoring error and the scene will not open. |
| `craftics:stand_marker` | Two per booth | Corner markers, placed in pairs. The two mark opposite corners of a rectangle, and clicking anywhere inside that rectangle counts as clicking the booth. Corner markers hold no data of their own. |
| `craftics:npc_marker` | One per booth, inside the corner rectangle | Sits inside the booth's two-corner rectangle and is the booth's identity: it places the NPC (position and facing) and carries the occupant id. The player walks up one tile in front of the NPC, facing it. Every booth needs exactly one NPC marker inside its corners; that is also how the scanner knows which two corners form which booth. |

## Build steps

1. Build the booth structures and walkways however you like, with any blocks. This is the visible scene.
2. Place one `craftics:scene_spawn` on the floor where the player should appear, facing the booths.
3. At each booth, place two `craftics:stand_marker` blocks at opposite corners of the area you want to be clickable for that booth.
4. Place one `craftics:npc_marker` inside that rectangle, where the NPC should stand, facing the player's approach. This positions the NPC and ties the two corners together into one booth.
5. Select the whole build, including the markers, and export it as a Sponge `.schem`. WorldEdit, FAWE, and Litematica all produce this format (the same one arenas use).
6. Place the file where Craftics will find it:
   - Bundled in a datapack at `data/craftics/scenes/village.schem` or `data/craftics/scenes/barter_station.schem`, or
   - On disk in a `craftics_scenes/` folder next to the server run directory (for example `craftics_scenes/village.schem`).
   - The disk copy wins if both exist, so you can iterate without rebuilding the jar.

## How booths are paired

For each `npc_marker`, the scanner finds the tight pair of stand corners whose rectangle contains it (and contains no other corner). Those two corners plus the NPC marker form one booth, and the rectangle is its clickable area. If an NPC marker has no clean containing pair around it, or the layout is ambiguous, that booth is skipped with a warning in the log and the rest of the scene still loads.

## Clicking a booth in the scene

The player walks up to a booth by clicking anywhere inside its corner rectangle. The player then walks to the spot in front of the NPC and faces it. Clicks are ignored while the player is already walking or mid-trade.

## Assigning booth occupants

A booth's **occupant** decides which merchant stands there, and it lives on the booth's `npc_marker`. Occupants are **not baked into the schematic**. Schematic export saves block states but not a marker's stored data, so an NPC marker loaded from a `.schem` comes in blank. Instead the scene assigns occupants when it is built, keyed by **booth index** (the deterministic order the scanner produces).

This keeps "where the booths are" (the schematic) separate from "who staffs them" (live player progression). A booth shows its merchant for a player who has met that merchant, and sits empty for a player who has not.

### Occupant id values

| Occupant id | Meaning |
| --- | --- |
| A merchant id such as `craftics:weaponsmith` (a villager profession) or `craftics:warmonger` (a piglin barter category) | **Dedicated booth.** Hosts exactly that one merchant. Empty until the player has met that merchant during a normal level. |
| `villager:addon` | **Overflow booth.** Hosts every met villager merchant that does not have a dedicated booth of its own (addon or modded traders). Walking up shows a choice of which one to talk to. Use on the village scene. |
| `piglin:addon` | Overflow booth for met piglin barter categories without a dedicated booth. Use on the bartering station scene. |

### Testing a booth in-world

While iterating you can set an NPC marker's occupant directly with a command, then re-scan the live world rather than re-exporting:

```
/data merge block ~ ~ ~ {Occupant:"craftics:weaponsmith"}
```

Target the `npc_marker` and confirm it stuck:

```
/data get block ~ ~ ~ Occupant
```

This is for ad-hoc testing only. The real scene populates occupants by booth index when it builds.

## Shipping a scene as an addon

Because scenes load from `data/<namespace>/craftics/scenes/<name>.schem` via datapack, an addon can ship its own `village.schem` or `barter_station.schem` to replace the built-in layout. Booth occupants resolve from the merchant registries at build time, so a modded trader or barter category that the player has met automatically appears at the matching overflow booth (`villager:addon` or `piglin:addon`) with no schematic change required.

## Quick reference

- Blocks: `craftics:scene_spawn` (one), `craftics:stand_marker` (two corners per booth), `craftics:npc_marker` (one per booth, inside the corners)
- Schematic paths: `data/craftics/scenes/village.schem`, `data/craftics/scenes/barter_station.schem`
- Disk override folder: `craftics_scenes/`
- Booth pairing: each `npc_marker` pairs with the tight stand-corner rectangle containing it; ambiguous or unpaired markers are skipped with a log warning
- Click target: anywhere inside the booth's corner rectangle
- Occupant lives on the `npc_marker`; ids are a merchant id (dedicated), `villager:addon` or `piglin:addon` (overflow)
- Occupants are assigned at build time by booth index, not stored in the schematic
- All markers are replaced by the most common neighboring block on scan, so they are invisible in the built scene
