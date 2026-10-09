# Craftics Example Arena Addon

A working example of replacing Craftics' built-in arenas with your own. No code. An arena is a
schematic file with a few marker blocks in it, and putting it in the right folder under the right
name is all it takes.

This folder replaces three Plains arenas so you can see it work, then walks through doing the
same with something you built.

```text
Example_Arena_Addon/
  datapack/                      a ready-made data pack
    pack.mcmeta
    data/craftics/arenas/plains/
      1.schem                    a rectangle arena with a pond
      2.schem                    an eight-sided arena
      boss.schem                 a boss room in a stone ruin
  tools/
    check_arena.py               tells you what Craftics will make of any .schem
    build_example_arenas.py      rebuilds the three examples, with every block explained
    schem.py                     the .schem reader and writer the two scripts share
```

The scripts are optional and need only Python 3. Everything else works without them.

## Try it

1. Copy the `datapack` folder into your world's `datapacks` folder and give it any name, for
   example `saves/My World/datapacks/example_arenas`. On a server that is
   `world/datapacks/example_arenas`.
2. Start the world.
3. If anyone has already fought in Plains on that island, run `/craftics rebuild_arenas plains`.
   Arenas are built once and then reused, so an island that has seen the old ones keeps them
   until you ask.
4. Play Plains. The levels alternate between the pond and the eight-sided arena, and the boss
   waits in the ruin.

To go back, delete the folder and run the rebuild command again.

## Make your own

### 1. Build it

Build in any creative world that has Craftics and WorldEdit installed. An arena is three things:
a flat floor to fight on, scenery around it, and marker blocks that tell Craftics where the floor
is.

**The floor layer** is the layer of blocks the fighters stand on. Every marker goes *in* that
layer, replacing a floor block. Not on top of it.

**The Arena Corner Marker** draws the arena. Find it in the Craftics creative tab, or run
`/give @s craftics:arena_corner`. How many you place decides what you get:

| Markers | Arena |
| --- | --- |
| Two | A rectangle. Put them on two opposite corner tiles. Both tiles are part of the arena, so markers nine blocks apart give a 9 by 9 arena. This is `1.schem` and `boss.schem`. |
| Three or more | Any shape. Walk the outline and drop one at each corner, in any order. Here the markers are the border: the arena is the tiles strictly inside the line they draw. This is `2.schem`. |

For shapes:

- Shapes made only of straight north-south and east-west edges (an L, a T, a plus) are traced
  exactly. They always have an even number of corners.
- A marker that is not a corner is set aside, four at most: one part way along a straight edge,
  or one inside the shape. So a room built from cells can have a marker at every cell corner.
  `check_arena.py` lists the ones it set aside.
- A walled room works. A marker can lie in the floor with a wall built on top of it, or be
  hidden under the floor up to two blocks down. The floor is the height most of the arena can
  be stood on.
- Shapes with diagonal edges work when they bulge outward everywhere (an octagon, a hexagon, a
  diamond). A shape that has diagonals and also bends inward will not come out right.

**A gold block** in the floor layer, inside the arena, is where player 1 starts. It is optional.
Leave it out and Craftics picks a start for you: the middle column one row in from the camera on
a rectangle, the tile nearest the middle on any other shape.

Craftics removes every marker and patches the hole with ordinary ground, so none of them shows
in the finished arena.

**The camera** sits at a marker on the west side of the arena and looks at the middle. On a
rectangle that is the western of the two markers, so choose the diagonal that puts a marker
where you want the camera. To see an arena from another side, turn the build before you save
it. Keep trees and walls away from that corner or they block the view.

**Numbered arenas and boss rooms are treated differently.** This is the part that surprises
people, so decide which one you are building first.

| | Numbered arena (`1.schem`, `2.schem`, ...) | Boss room (`boss.schem`) |
| --- | --- | --- |
| Floor inside the arena | Repainted with the biome's own floor blocks. Water, lava and gaps are kept. | Kept exactly as built. |
| Blocks standing on that floor | Removed. Carpets are kept. | Kept. They become obstacles. |
| Around the arena | A ring of concrete is laid as a border. | Nothing is added. |
| Edge of the schematic | The outer 4 blocks are cut into a slope. | Kept exactly as built. |

So a numbered arena is terrain: you supply the shape, the water and the scenery, and Craftics
dresses the floor to match the biome. A boss room is yours block for block, which also means its
edges have to look finished on their own. Trial chambers are kept as built too.

In both kinds, everything two or more blocks above the floor is cleared inside the arena, apart
from cobwebs. Keep roofs, branches and tall pillars outside it. Craftics also adds lamp posts
around every arena, and on rectangular ones the biome's usual scatter (tall grass on Plains,
cactus in the Desert, and so on).

**What a block means**, wherever it is kept:

| In the arena | Becomes |
| --- | --- |
| A block standing on the floor | An obstacle. Fences, walls and glass panes count. |
| Stairs or a bottom slab on the floor | A step. Full blocks beside a step become a raised platform you can walk onto. |
| Stairs or a bottom slab on a raised platform | A second step. Full blocks beside it become a second floor. Three floors up is the limit. |
| A cobweb on the floor, or one block up | A web trap. |
| Water in the floor layer, one block deep | Shallow water. Walkable, and it soaks you. |
| Water two or more blocks deep | Deep water. Nobody can enter it. |
| Lava or a magma block in the floor layer | A lava tile. |
| Powder snow in the floor layer | A powder snow tile. |
| A gap in the floor with ground one block down | A pit you can walk through. |
| A gap with nothing under it | A void tile. Nobody can walk onto it, and anything knocked in falls to its death. |

**Size.** Keep the whole schematic under about 110 blocks wide and long and 100 tall. For a
numbered arena, keep the arena itself at least 4 blocks in from every edge of the schematic so
the slope does not eat into it, and keep scenery 2 blocks clear of the arena so the border and
lamp posts have room. The built-in arenas start at about 9 by 9 tiles, and the largest boss
rooms are a little over 20 by 20.

**Blocks from other mods** work, but anyone without that mod gets air in their place, and a hole
in the floor is a void tile. Stick to vanilla blocks in anything you plan to share widely.

**Older arenas** mark the two corners of a rectangle with a diamond block and an emerald block
instead. Those still load and do not need converting (a diamond block among Arena Corner Markers
still sets the camera corner, too), but use the Arena Corner Marker for anything new.

### 2. Save it

With WorldEdit, select the whole build including the scenery and the ground under it, then:

```text
//copy
//schem save plains_1
```

That writes `config/worldedit/schematics/plains_1.schem` in your game folder. Any tool that
saves Sponge `.schem` files (version 2 or 3) works.

### 3. Check it (optional)

```text
python tools/check_arena.py path/to/plains_1.schem
```

It finds the markers, works out the shape, floor, size and start the way the mod does, and draws
the arena:

```text
  shape       rectangle, from 2 Arena Corner Markers at (9, 3, 9) and (17, 3, 17)
  floor       y=3
  size        9 x 9 tiles, 81 playable
  player 1    gold block, tile (4, 1)
  camera      sits at the corner at (9, 9), looking at the middle

      C........
      ....P....
      .........
      .........
      .....~~..
      .....~~..
```

Anything that will surprise you is listed as a warning: a marker in the wrong layer, blocks
that will be cleared, void tiles, blocks from other mods, an arena too close to the edge. Pass
`--boss` to check a file as a boss room. The map is a close guess, since the script cannot know
every block's shape the way the game does.

### 4. Put it where Craftics looks

There are three places. The first two are loose files in the game folder, the third is a data
pack.

| Where | Files | Good for |
| --- | --- | --- |
| `config/worldedit/schematics/` in the game folder | `plains_1.schem` ... `plains_10.schem`, `plains_boss.schem` | Testing. This is where WorldEdit already saved it. |
| `craftics_arenas/plains/` in the game folder | `1.schem` ... `10.schem`, `boss.schem` | Keeping your arenas apart from other schematics. |
| A data pack: `data/craftics/arenas/plains/` | `1.schem` ... `10.schem`, `boss.schem` | Sharing, and anything you want tied to one world. |

The game folder is `.minecraft` (or your launcher's instance folder) in single player, and the
folder you start the server from on a server.

Rules that apply everywhere:

- **Number from 1 with no gaps.** Craftics stops at the first missing number, so `1`, `2`, `4`
  is read as two arenas.
- **Levels cycle through the files.** Level 1 of a biome uses `1.schem`, level 2 uses
  `2.schem`, and it wraps round when it runs out. The last level of a biome is the boss and
  uses `boss.schem`.
- **Loose files replace the whole set.** If Craftics finds any loose numbered file for Plains,
  loose files are the only numbered Plains arenas it uses. Boss files are counted separately,
  so adding only `plains_boss.schem` leaves the numbered arenas alone.
- **A data pack folder counts as loose files. A zipped data pack replaces file by file.** Zip
  the example and it replaces Plains arenas 1 and 2 while the built-in 3 and 4 stay in the
  rotation. Leave it as a folder and Plains uses only the two examples.

Built-in biome folders: `plains`, `forest`, `desert`, `jungle`, `river`, `mountain`, `snowy`,
`cave`, `deep_dark`, `nether_wastes`, `soul_sand_valley`, `crimson_forest`, `warped_forest`,
`basalt_deltas`, `outer_end_islands`, `end_city`, `chorus_grove`, `dragons_nest`. Trial
chambers use `trial_chamber` and `trial_chamber_ominous`. The Pale Garden is a single file,
`forest/pale_garden.schem`.

One exception: `outer_end_islands` ignores loose files. Use a data pack for it.

### 5. Rebuild and test

```text
/craftics rebuild_arenas plains
```

This rebuilds every Plains arena on your island from the files as they are now. It is for
operators only unless the server config opens it up, and it refuses while anyone on the island
is in a run. Loose files are read fresh each time. If you changed a data pack while the world
was running, run `/reload` first.

`/craftics skip_level` wins the current fight, which is the quick way to step through a biome
and see each arena in turn.

Then read `logs/latest.log`. A good load looks like this:

```text
ArenaBuilder: resolved biomeId='plains' isBoss=false ...
Found 2 disk .schem candidate(s) for biome 'plains': [...]
Structure 1.schem marker scan: diamond=false, emerald=false, corners=2, spawns=[true,false,false,false]
Arena built. origin=..., size=9x9, playerStart=GridPos[x=4, z=1], polygon=false
```

An arena from a zipped data pack is reported as `Loading bundled arena: craftics:arenas/plains/1.schem`,
the same wording as a built-in one.

### 6. Share it

Zip the *contents* of the `datapack` folder, so that `pack.mcmeta` and `data` sit at the top of
the zip, and hand out the zip. It goes in `datapacks` like any other data pack. The included
`pack.mcmeta` covers Minecraft 1.21.1 to 1.21.5.

Inside a mod jar, the same `data/craftics/arenas/...` layout works, but only use it for biomes
your mod adds. For replacing a built-in arena, ship a data pack: when two mods provide the same
file there is no telling which one wins, and a world's data pack always does.

## When it does not work

| What you see | Why, and what to do |
| --- | --- |
| The old arena is still there | Arenas are reused once built. Run `/craftics rebuild_arenas <biome>`. |
| Nothing changed even after a rebuild | Check the file name and folder against step 4, and that the numbering starts at 1 with no gaps. The log says which file it loaded. |
| Every level now uses my one arena | Loose files replace the whole set. Add more numbered files, or ship it as a zipped data pack to replace one arena. |
| My floor was repainted and my decorations are gone | That is what happens to a numbered arena. Keep decorations outside the arena, or build a boss room. |
| The log says `has no arena corner markers` | No markers were found, so Craftics guessed. Put them in the floor layer and include them in the selection you save. |
| The arena is one tile smaller all round than I expected | With three or more markers they are the border, not part of the floor. Move them out a tile, or use two for a rectangle. |
| Players start in a strange place | The gold block has to be in the floor layer and inside the arena. |
| The camera faces the wrong way | It sits at a marker on the west side. Turn the build and save it again. |
| There are holes in the floor | Either gaps you left, or blocks from a mod that is not installed. The log names the missing mod. |
| The log says `Skipping arena ... needs missing mod(s)` | A loose arena that uses another mod's blocks is skipped when that mod is absent, and the built-in arenas are used instead. |

## Arenas for a biome of your own

A new biome is a JSON file, and its arenas are found by the biome's id. A biome with the id
`mymod:cavern` keeps them in `data/mymod/arenas/cavern/`, with the same file names and the same
markers as everything above. A biome with no arenas at all still works: Craftics generates a
plain one from the biome's grid size and floor blocks.

The modding guide has both halves:

- The biome JSON, and how it finds its arenas:
  <https://chrisatwell27.github.io/Craftics/modding/world.html#arenas>
- The arena rules on this page, in reference form:
  <https://chrisatwell27.github.io/Craftics/modding/reference.html#arena-maps>

## Rebuilding the examples

```text
python tools/build_example_arenas.py
```

It writes the three `.schem` files again, byte for byte. Read it alongside this page: each
block it places has a comment saying why.
