#!/usr/bin/env python3
"""Rebuild the three example arenas in ../datapack from scratch.

    python tools/build_example_arenas.py

You would normally build an arena in game and save it with WorldEdit. This script does the
same job in code so that every block in the examples has a reason you can read. The three
arenas show the three things Craftics can be told:

    1.schem     a rectangle: two Arena Corner Markers
    2.schem     any other shape: three or more Arena Corner Markers
    boss.schem  a boss room, which is kept exactly as built

Read build_meadow first. The other two only point out what is different.
"""

from pathlib import Path

from schem import Schematic

OUT = Path(__file__).resolve().parent.parent / "datapack" / "data" / "craftics" / "arenas" / "plains"

# Block states are written the way WorldEdit writes them.
STONE = "minecraft:stone"
DIRT = "minecraft:dirt"
GRASS = "minecraft:grass_block[snowy=false]"
PATH = "minecraft:dirt_path"
WATER = "minecraft:water[level=0]"
LOG = "minecraft:oak_log[axis=y]"
# persistent=true: these leaves never decay, whatever is or is not next to them.
LEAVES = "minecraft:oak_leaves[distance=7,persistent=true,waterlogged=false]"
SHORT_GRASS = "minecraft:short_grass"
DANDELION = "minecraft:dandelion"
POPPY = "minecraft:poppy"
COBBLE = "minecraft:cobblestone"
MOSSY_COBBLE = "minecraft:mossy_cobblestone"
BRICKS = "minecraft:stone_bricks"
MOSSY_BRICKS = "minecraft:mossy_stone_bricks"
CRACKED_BRICKS = "minecraft:cracked_stone_bricks"
CHISELED_BRICKS = "minecraft:chiseled_stone_bricks"
ANDESITE = "minecraft:polished_andesite"

# The markers. Craftics finds these, reads the arena off them, and swaps each one for
# ordinary ground, so none of them is visible in the finished arena.
CORNER = "craftics:arena_corner"       # the Arena Corner Marker: it draws the arena
GOLD = "minecraft:gold_block"          # where player 1 starts


def lay_ground(schem, floor_y):
    """Stone, a layer of dirt, grass on top. The grass layer is the arena floor."""
    last_x, last_z = schem.width - 1, schem.length - 1
    schem.fill(0, 0, 0, last_x, floor_y - 2, last_z, STONE)
    schem.fill(0, floor_y - 1, 0, last_x, floor_y - 1, last_z, DIRT)
    schem.fill(0, floor_y, 0, last_x, floor_y, last_z, GRASS)


def plant_tree(schem, x, ground_y, z):
    for y in range(ground_y + 1, ground_y + 5):
        schem.set(x, y, z, LOG)
    for y in (ground_y + 3, ground_y + 4):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if (dx, dz) != (0, 0):
                    schem.set(x + dx, y, z + dz, LEAVES)
    for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        schem.set(x + dx, ground_y + 5, z + dz, LEAVES)


def scatter_plants(schem, floor_y, keep_clear):
    """Grass tufts and flowers on bare ground, in a fixed pattern so every build is identical."""
    for z in range(4, schem.length - 4):
        for x in range(4, schem.width - 4):
            if keep_clear(x, z):
                continue
            if schem.get(x, floor_y, z) != GRASS or schem.get(x, floor_y + 1, z) != "minecraft:air":
                continue
            roll = (x * 7 + z * 13) % 11
            if roll == 0:
                schem.set(x, floor_y + 1, z, DANDELION)
            elif roll == 5:
                schem.set(x, floor_y + 1, z, POPPY)
            elif roll in (2, 8):
                schem.set(x, floor_y + 1, z, SHORT_GRASS)


def build_meadow():
    """1.schem: a 9 x 9 rectangle with a pond.

    This replaces a numbered arena, so Craftics treats it as terrain to fight on: it repaints
    the floor inside the rectangle with the biome's own floor blocks, clears anything standing
    on it, shaves the outer four blocks of the schematic into a slope, and adds its own grass
    and lamp posts. What survives from here is the shape, the water, and the scenery.
    """
    floor = 3
    schem = Schematic(27, 12, 27)
    lay_ground(schem, floor)

    # Two markers make a rectangle. They sit IN the floor layer, on two opposite corner tiles
    # of the area you fight in, and both of those tiles are playable. That makes this 9 wide,
    # 9 deep. The camera sits at the western one and looks toward the middle.
    lo, hi = 9, 17
    schem.set(lo, floor, lo, CORNER)
    schem.set(hi, floor, hi, CORNER)

    # Player 1 starts here. It must be on the floor layer and inside the rectangle.
    schem.set(13, floor, 10, GOLD)

    # Water in the floor layer stays water. One block deep is a shallow tile you can wade.
    schem.fill(14, floor, 13, 15, floor, 14, WATER)

    # Scenery goes outside the rectangle, at least two blocks from it so the border and the
    # lamp posts have room, and at least four blocks from the schematic's edge so the slope
    # does not cut it. Tall things go on the side away from the camera.
    schem.fill(13, floor, 0, 13, floor, lo - 2, PATH)
    for x, z in ((20, 21), (21, 13), (21, 5), (6, 21)):
        plant_tree(schem, x, floor, z)
    schem.set(5, floor + 1, 14, COBBLE)
    schem.set(5, floor + 1, 15, MOSSY_COBBLE)
    schem.set(6, floor + 1, 15, COBBLE)

    def near_arena(x, z):
        return lo - 2 <= x <= hi + 2 and lo - 2 <= z <= hi + 2

    scatter_plants(schem, floor, near_arena)
    return schem


def build_ring():
    """2.schem: an octagon, drawn with eight markers.

    Different from the meadow in one way: with three or more markers they are the OUTLINE,
    one tile outside the floor. Craftics joins them up, and the tiles strictly inside the line
    are the arena.
    """
    floor = 3
    schem = Schematic(31, 12, 31)
    lay_ground(schem, floor)

    # Walk the outline in any order. Craftics sorts the points itself.
    outline = [(12, 8), (18, 8), (22, 12), (22, 18), (18, 22), (12, 22), (8, 18), (8, 12)]
    for x, z in outline:
        schem.set(x, floor, z, CORNER)

    # The camera sits at a marker on the west side, here the one at (8, 12), so player 1
    # starts near it and the trees stand to the east where they are not in the way.
    schem.set(10, floor, 13, GOLD)

    for x, z in ((25, 25), (25, 15), (25, 5), (16, 25)):
        plant_tree(schem, x, floor, z)
    schem.set(4, floor + 1, 15, MOSSY_COBBLE)
    schem.set(4, floor + 1, 16, COBBLE)

    def near_arena(x, z):
        return 6 <= x <= 24 and 6 <= z <= 24

    scatter_plants(schem, floor, near_arena)
    return schem


def build_boss_ruin():
    """boss.schem: an 11 x 11 ruin.

    Different from the meadow in one way: a boss room is kept exactly as built. The floor is
    not repainted and the edges are not sloped, so the floor pattern and the pillars below
    are what the player gets, and the island has to look finished on its own.
    """
    floor = 4
    size = 31
    schem = Schematic(size, 14, size)
    centre = size // 2

    # A round island that narrows toward the bottom, since nothing will slope the edge for us.
    for y in range(floor + 1):
        radius = 14 - (floor - y) * 2
        for z in range(size):
            for x in range(size):
                if (x - centre) ** 2 + (z - centre) ** 2 <= radius * radius:
                    schem.set(x, y, z, GRASS if y == floor else DIRT if y == floor - 1 else STONE)

    lo, hi = 10, 20
    for z in range(lo, hi + 1):
        for x in range(lo, hi + 1):
            on_cross = x == centre or z == centre
            worn = (x * 5 + z * 3) % 7
            block = ANDESITE if on_cross else MOSSY_BRICKS if worn == 0 else CRACKED_BRICKS if worn == 3 else BRICKS
            schem.set(x, floor, z, block)

    schem.set(lo, floor, lo, CORNER)
    schem.set(hi, floor, hi, CORNER)
    # No gold block. Craftics then starts player 1 in the middle column, one row in from the
    # camera side. A gold block is swapped for the biome's own floor block, which on a custom
    # floor like this one would leave a single grass tile behind.

    # One block standing on the floor is an obstacle. Keep them one block tall: everything
    # from two blocks above the floor upward is cleared inside the arena.
    for x, z in ((12, 12), (18, 12), (12, 18), (18, 18)):
        schem.set(x, floor + 1, z, CHISELED_BRICKS)

    # Broken walls outside the arena, tallest on the side away from the camera.
    for x in range(lo, hi + 1):
        if x % 4 != 2:
            height = 3 if x % 3 else 2
            for y in range(floor + 1, floor + 1 + height):
                schem.set(x, y, hi + 3, MOSSY_BRICKS if (x + y) % 3 == 0 else BRICKS)
    for z in range(lo, hi + 1):
        if z % 4 != 0:
            height = 2 if z % 3 else 1
            for y in range(floor + 1, floor + 1 + height):
                schem.set(hi + 3, y, z, CRACKED_BRICKS if (z + y) % 4 == 0 else BRICKS)

    for x, z in ((24, 24), (7, 23), (23, 7)):
        plant_tree(schem, x, floor, z)

    def near_arena(x, z):
        return lo - 2 <= x <= hi + 3 and lo - 2 <= z <= hi + 3

    scatter_plants(schem, floor, near_arena)
    return schem


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for file_name, build in (("1.schem", build_meadow), ("2.schem", build_ring), ("boss.schem", build_boss_ruin)):
        schem = build()
        schem.save(OUT / file_name)
        print("wrote %s  (%d x %d x %d)" % (OUT / file_name, schem.width, schem.height, schem.length))


if __name__ == "__main__":
    main()
