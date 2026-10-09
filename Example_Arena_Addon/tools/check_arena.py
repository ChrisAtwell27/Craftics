#!/usr/bin/env python3
"""Say what Craftics will make of an arena schematic, without starting the game.

    python tools/check_arena.py my_arena.schem
    python tools/check_arena.py datapack            (every .schem under a folder)
    python tools/check_arena.py --boss my_arena.schem

It finds the marker blocks, works out the shape, floor height, size and player start the same
way the mod does, draws the arena as text, and lists anything that will surprise you.

A file named boss.schem or <biome>_boss.schem is read as a boss room. Pass --boss to read any
other file that way. The map is a close guess, not a promise: this script cannot know every
block's shape the way the game does.
"""

import itertools
import math
import sys
from pathlib import Path

from schem import AIR, Schematic

CORNER = "craftics:arena_corner"
SPAWNS = (("gold", "minecraft:gold_block"), ("iron", "minecraft:iron_block"),
          ("copper", "minecraft:copper_block"), ("coal", "minecraft:coal_block"))
# The old markers. They still load, so they are still read here, but nothing new should use them.
DIAMOND, EMERALD = "minecraft:diamond_block", "minecraft:emerald_block"
LEGACY_NOTE = ("diamond and emerald blocks are the old markers. They still load, but use Arena "
               "Corner Markers in anything new.")

# Craftics clears a box this far either side of an arena before building in it, and this far up.
SAFE_SPAN, SAFE_HEIGHT = 110, 100
# Numbered arenas have their outer blocks cut into a slope this many blocks deep.
SLOPE = 4

# Things you can stand in. Anything else standing on the floor counts as an obstacle.
SOFT = ("short_grass", "tall_grass", "fern", "dandelion", "poppy", "orchid", "allium", "azure_bluet",
        "tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "sunflower", "lilac", "rose_bush",
        "peony", "torch", "carpet", "vine", "sapling", "rail", "pressure_plate", "button", "sign",
        "banner", "lever", "redstone_wire", "tripwire", "sugar_cane", "bush", "seagrass", "glow_lichen",
        "sculk_vein", "structure_void", "petals", "hanging_roots", "crimson_roots", "warped_roots",
        "fungus", "sprouts")
SOFT_EXACT = ("snow", "brown_mushroom", "red_mushroom", "fire", "soul_fire", "kelp", "kelp_plant")


def block_id(state):
    return state.split("[", 1)[0]


def is_air(state):
    return block_id(state) in (AIR, "minecraft:cave_air", "minecraft:void_air")


def is_fluid(state):
    return block_id(state) in ("minecraft:water", "minecraft:lava") or "waterlogged=true" in state


def holds_you_up(state):
    if is_air(state) or is_fluid(state):
        return False
    name = block_id(state).split(":", 1)[-1]
    return name not in SOFT_EXACT and not any(word in name for word in SOFT)


# ----------------------------------------------------------------------- shape

MAX_SPARE = 4      # more markers than this set aside and the outline is a guess
MAX_SEARCHED = 40  # past this many markers the search for spares is not tried


def order_outline(points):
    """Join corner points into one closed outline. Mirrors ArenaOutline.trace.

    Returns (ring, spare). Spare markers are the ones that turned out not to be corners:
    on a straight edge, or inside the shape.
    """
    points = list(dict.fromkeys(points))
    ring = rectilinear_ring(points)
    if ring:
        return ring, []
    trimmed = without_spares(points)
    if trimmed:
        return trimmed
    cx = sum(p[0] for p in points) / len(points)
    cz = sum(p[1] for p in points) / len(points)
    return sorted(points, key=lambda p: math.atan2(p[1] - cz, p[0] - cx)), []


def without_spares(points):
    """The outline left when the fewest markers are set aside, each still on it or inside it."""
    n = len(points)
    if n > MAX_SEARCHED:
        return None
    for count in range(1, MAX_SPARE + 1):
        if n - count < 4 or (n - count) % 2:
            continue
        best, best_area = None, -1
        for drop in itertools.combinations(range(n), count):
            kept = [p for i, p in enumerate(points) if i not in drop]
            ring = rectilinear_ring(kept)
            if not ring or crosses_itself(ring):
                continue
            spare = [points[i] for i in drop]
            if not all(contains(ring, x, z) for x, z in spare):
                continue
            area = twice_area(ring)
            if area > best_area:
                best, best_area = (ring, spare), area
        if best:
            return best
    return None


def crosses_itself(ring):
    edges = [(ring[i], ring[(i + 1) % len(ring)]) for i in range(len(ring))]
    for a, b in edges:
        if a[0] != b[0]:
            continue
        z0, z1 = sorted((a[1], b[1]))
        for c, d in edges:
            if c[1] != d[1]:
                continue
            x0, x1 = sorted((c[0], d[0]))
            if x0 < a[0] < x1 and z0 < c[1] < z1:
                return True
    return False


def twice_area(ring):
    return abs(sum(a[0] * b[1] - b[0] * a[1]
                   for a, b in zip(ring, ring[1:] + ring[:1])))


def on_outline(ring, x, z):
    for a, b in zip(ring, ring[1:] + ring[:1]):
        if (b[0] - a[0]) * (z - a[1]) - (b[1] - a[1]) * (x - a[0]) != 0:
            continue
        if min(a[0], b[0]) <= x <= max(a[0], b[0]) and min(a[1], b[1]) <= z <= max(a[1], b[1]):
            return True
    return False


def contains(ring, x, z):
    """On the outline or inside it."""
    if on_outline(ring, x, z):
        return True
    inside = False
    j = len(ring) - 1
    for i in range(len(ring)):
        (ax, az), (bx, bz) = ring[i], ring[j]
        if (az > z) != (bz > z) and x < (bx - ax) * (z - az) / (bz - az) + ax:
            inside = not inside
        j = i
    return inside


def rectilinear_ring(points):
    """Exact outline for shapes made only of straight north-south and east-west edges."""
    n = len(points)
    if n < 4 or n % 2:
        return None
    columns, rows = {}, {}
    for i, (x, z) in enumerate(points):
        columns.setdefault(x, []).append(i)
        rows.setdefault(z, []).append(i)
    up_down, left_right = [-1] * n, [-1] * n
    for group, partner, axis in ((columns, up_down, 1), (rows, left_right, 0)):
        for members in group.values():
            if len(members) % 2:
                return None
            members.sort(key=lambda i: points[i][axis])
            for k in range(0, len(members), 2):
                partner[members[k]], partner[members[k + 1]] = members[k + 1], members[k]
    ring, seen, at, vertical = [], set(), 0, True
    for _ in range(n):
        if at in seen:
            return None
        seen.add(at)
        ring.append(points[at])
        at = up_down[at] if vertical else left_right[at]
        if at < 0:
            return None
        vertical = not vertical
    return ring if at == 0 else None


def polygon_masks(ring, min_x, min_z, width, depth):
    """outer = on or inside the outline. floor = outer shrunk by one tile. Mirrors ArenaOutline."""
    outer = [[contains(ring, min_x + tx, min_z + tz) for tz in range(depth)] for tx in range(width)]
    floor = [[False] * depth for _ in range(width)]
    for tx in range(1, width - 1):
        for tz in range(1, depth - 1):
            floor[tx][tz] = (outer[tx][tz] and outer[tx - 1][tz] and outer[tx + 1][tz]
                             and outer[tx][tz - 1] and outer[tx][tz + 1])
    # An inward corner has outline on two sides and floor on the other two. It is rim, not floor.
    for x, z in ring:
        floor[x - min_x][z - min_z] = False
    return outer, floor


def standable(schem, inside, min_x, y, min_z):
    """How many tiles inside the outline have ground at this height and room above it."""
    count = 0
    for tx, column in enumerate(inside):
        for tz, is_inside in enumerate(column):
            if not is_inside:
                continue
            ground = schem.get(min_x + tx, y, min_z + tz)
            above = schem.get(min_x + tx, y + 1, min_z + tz)
            if (is_fluid(ground) or holds_you_up(ground)) and not is_fluid(above) and not holds_you_up(above):
                count += 1
    return count


# ----------------------------------------------------------------------- report

class Report:
    def __init__(self):
        self.lines, self.warnings, self.problems = [], [], []

    def add(self, label, text):
        self.lines.append("  %-11s %s" % (label, text))


def describe(path, boss):
    schem = Schematic.load(path)
    report = Report()
    report.add("format", "Sponge version %d, %d wide x %d tall x %d long"
               % (schem.version, schem.width, schem.height, schem.length))
    report.add("read as", "a boss room, kept exactly as built" if boss
               else "a numbered arena: floor repainted, edges sloped")

    corners = schem.find(CORNER)
    spawns = {name: schem.find(block) for name, block in SPAWNS}
    diamonds, emeralds = schem.find(DIAMOND), schem.find(EMERALD)
    # The mod keeps the last diamond and emerald it scans, so more than one is a coin toss.
    diamond = diamonds[-1] if diamonds else None
    emerald = emeralds[-1] if emeralds else None
    if len(diamonds) > 1:
        report.warnings.append("%d diamond blocks. Only one is read as a marker, so remove the rest."
                               % len(diamonds))
    if len(emeralds) > 1:
        report.warnings.append("%d emerald blocks. Only one is read as a marker, so remove the rest."
                               % len(emeralds))

    foreign = [ns for ns in schem.namespaces() if ns not in ("minecraft", "craftics")]
    if foreign:
        report.warnings.append("Uses blocks from %s. Without that mod they load as air, which leaves holes."
                               % ", ".join(foreign))
    if max(schem.width, schem.length) > SAFE_SPAN:
        report.warnings.append("Over %d blocks across. Bigger than that can leave pieces behind when the "
                               "arena is rebuilt." % SAFE_SPAN)
    if schem.height > SAFE_HEIGHT:
        report.warnings.append("Over %d blocks tall. The top may not be cleared on a rebuild." % SAFE_HEIGHT)

    if len(corners) >= 3 or (len(corners) >= 2 and diamond):
        arena = read_outline(schem, report, corners, diamond, emerald, spawns)
    elif len(corners) == 2:
        # Two markers are the two corner tiles of a rectangle. The first one scanned, which is
        # the western one, is the camera corner.
        arena = read_rectangle(schem, report, corners[0], corners[1], "2 Arena Corner Markers", spawns)
    else:
        if len(corners) == 1:
            report.warnings.append("A single Arena Corner Marker means nothing and is ignored.")
        arena = read_rectangle(schem, report, diamond, emerald, "a diamond and an emerald block", spawns)
        if arena:
            report.add("note", LEGACY_NOTE)

    if arena:
        draw(schem, report, boss, *arena)
    return report


def read_rectangle(schem, report, camera_corner, far_corner, markers, spawns):
    if not camera_corner or not far_corner:
        report.problems.append("No arena outline. Put two Arena Corner Markers on opposite corner tiles "
                               "of the floor, or three or more around a shape. Without them Craftics "
                               "guesses a floor and lays the biome's default grid over the middle of the "
                               "build.")
        return None
    if abs(camera_corner[1] - far_corner[1]) > 3:
        report.problems.append("The two corner markers (y=%d and y=%d) are more than 3 blocks apart in "
                               "height, so they are ignored." % (camera_corner[1], far_corner[1]))
        return None
    floor_y = max(camera_corner[1], far_corner[1])
    if camera_corner[1] != far_corner[1]:
        report.warnings.append("The two corner markers are at different heights. The higher one is used.")
    min_x, max_x = sorted((camera_corner[0], far_corner[0]))
    min_z, max_z = sorted((camera_corner[2], far_corner[2]))
    width, depth = max_x - min_x + 1, max_z - min_z + 1
    report.add("shape", "rectangle, from %s at %s and %s" % (markers, camera_corner, far_corner))
    report.add("floor", "y=%d" % floor_y)
    report.add("size", "%d x %d tiles, %d playable" % (width, depth, width * depth))

    start = None
    for name, hits in spawns.items():
        good = [p for p in hits if p[1] == floor_y and min_x <= p[0] <= max_x and min_z <= p[2] <= max_z]
        if hits and not good:
            report.warnings.append("The %s block is not in the floor layer inside the arena, so it is ignored."
                                   % name)
        if good and name == "gold":
            start = (good[0][0] - min_x, good[0][2] - min_z)
    if start:
        report.add("player 1", "gold block, tile %s" % (start,))
    else:
        centre_z = (min_z + max_z) / 2
        z = 1 if camera_corner[2] < centre_z else depth - 2
        start = (min(width - 1, max(0, width // 2)), min(depth - 1, max(0, z)))
        report.add("player 1", "no gold block, so tile %s: middle column, one row in from the camera" % (start,))
    report.add("camera", "sits at the corner at (%d, %d), looking at the middle"
               % (camera_corner[0], camera_corner[2]))

    inside = [[True] * depth for _ in range(width)]
    return (floor_y, min_x, min_z, width, depth, inside, start,
            (camera_corner[0] - min_x, camera_corner[2] - min_z))


def read_outline(schem, report, corners, diamond, emerald, spawns):
    points = list(corners) + ([diamond] if diamond else [])
    if emerald:
        report.warnings.append("An emerald block does nothing in a shaped arena and will be left showing.")

    # What the markers themselves say: each may be hidden under up to two solid blocks, and
    # the floor is the height most of them agree on.
    votes = {}
    for x, y, z in points:
        top = y
        for _ in range(2):
            if not holds_you_up(schem.get(x, top + 1, z)):
                break
            top += 1
        votes[top] = votes.get(top, 0) + 1
    marker_floor_y = max(votes, key=lambda y: (votes[y], y))

    min_x, max_x = min(p[0] for p in points), max(p[0] for p in points)
    min_z, max_z = min(p[2] for p in points), max(p[2] for p in points)
    width, depth = max_x - min_x + 1, max_z - min_z + 1
    ring, spare = order_outline([(p[0], p[2]) for p in points])
    outer, inside = polygon_masks(ring, min_x, min_z, width, depth)
    playable = sum(row.count(True) for row in inside)
    if spare:
        report.warnings.append("%d of the markers are not corners of the shape (on a straight edge, or "
                               "inside it) and are set aside: %s. The arena still works. Remove them if "
                               "the shape drawn below is not the one you meant."
                               % (len(spare), ", ".join("(%d, %d)" % p for p in spare)))

    # The room has the last word. A marker under a wall looks just like a marker buried under
    # the floor, so the floor is the height most of the inside can be stood on, and the
    # markers only settle a tie.
    floor_y = marker_floor_y
    if playable:
        heights = sorted({y + up for _, y, _ in points for up in (0, 1, 2)})
        room = {y: standable(schem, inside, min_x, y, min_z) for y in heights}
        for y in heights:
            if room[y] > room[floor_y]:
                floor_y = y

    report.add("shape", "outline of %d Arena Corner Markers" % len(corners)
               + (", plus a diamond block as the camera corner" if diamond else ""))
    if diamond:
        report.add("note", LEGACY_NOTE)
    report.add("floor", "y=%d" % floor_y)
    report.add("size", "%d x %d tiles across, %d playable" % (width, depth, playable))
    if playable == 0:
        report.problems.append("Nothing fits inside the outline. The markers are the border, one tile "
                               "outside the floor, so the shape needs to be at least 3 tiles across.")
        return None

    start = None
    for name, hits in spawns.items():
        good = [p for p in hits if abs(p[1] - floor_y) <= 1
                and min_x <= p[0] <= max_x and min_z <= p[2] <= max_z
                and inside[p[0] - min_x][p[2] - min_z]]
        if hits and not good:
            report.warnings.append("The %s block is not inside the outline at floor height, so it is ignored."
                                   % name)
        if good and name == "gold":
            start = (good[0][0] - min_x, good[0][2] - min_z)
    if start:
        report.add("player 1", "gold block, tile %s" % (start,))
    else:
        tiles = [(x, z) for x in range(width) for z in range(depth) if inside[x][z]]
        start = min(tiles, key=lambda t: abs(t[0] - width // 2) + abs(t[1] - depth // 2))
        report.add("player 1", "no gold block, so tile %s: the one nearest the middle" % (start,))

    anchor = (diamond[0], diamond[2]) if diamond else ring[0]
    report.add("camera", "sits at the corner at (%d, %d), looking at the middle" % anchor)
    return floor_y, min_x, min_z, width, depth, inside, start, (anchor[0] - min_x, anchor[1] - min_z)


def draw(schem, report, boss, floor_y, min_x, min_z, width, depth, inside, start, camera):
    cleared_on_floor = cleared_above = voids = 0
    rows = []
    for tz in range(depth):
        row = []
        for tx in range(width):
            x, z = min_x + tx, min_z + tz
            if not inside[tx][tz]:
                row.append("C" if (tx, tz) == camera else " ")
                continue
            ground, standing = schem.get(x, floor_y, z), schem.get(x, floor_y + 1, z)
            for y in range(floor_y + 2, schem.height):
                if not is_air(schem.get(x, y, z)) and block_id(schem.get(x, y, z)) != "minecraft:cobweb":
                    cleared_above += 1
            symbol = "."
            if block_id(ground) == "minecraft:water":
                # Two or more blocks of water is deep: nobody can enter it.
                symbol = "=" if is_fluid(schem.get(x, floor_y - 1, z)) else "~"
            elif block_id(ground) in ("minecraft:lava", "minecraft:magma_block"):
                symbol = "!"
            elif is_air(ground):
                symbol = "o" if holds_you_up(schem.get(x, floor_y - 1, z)) else "X"
                voids += symbol == "X"
            elif block_id(standing) == "minecraft:cobweb":
                symbol = "w"
            elif "stairs" in standing or "slab" in standing:
                symbol = "/"
            elif holds_you_up(standing):
                if boss:
                    symbol = "#"
                else:
                    cleared_on_floor += 1
            if (tx, tz) == start:
                symbol = "P"
            elif (tx, tz) == camera and symbol == ".":
                symbol = "C"
            row.append(symbol)
        rows.append("".join(row))

    report.lines.append("")
    report.lines.append("  map (top row is north, the lowest z)")
    report.lines.extend("      " + row for row in rows)
    report.lines.append("      . floor   ~ shallow water   = deep water   # obstacle   / stair or slab")
    report.lines.append("      o pit   X void   ! lava   w web   P player 1   C camera corner")

    if voids:
        report.warnings.append("%d void tile(s): no floor there and nothing under it. Nobody can walk onto "
                               "them, and anything knocked in falls to its death. Fine for a gap you meant, "
                               "a hole to fill if you did not." % voids)
    if cleared_on_floor:
        report.warnings.append("%d block(s) standing on the arena floor will be removed. A numbered arena is "
                               "repainted, so build obstacles into boss rooms instead." % cleared_on_floor)
    if cleared_above:
        report.warnings.append("%d block(s) two or more above the floor, inside the arena, will be removed. "
                               "Only cobwebs are kept up there." % cleared_above)
    if not boss:
        edge = min(min_x, min_z, schem.width - (min_x + width), schem.length - (min_z + depth))
        if edge < SLOPE:
            report.warnings.append("The arena is %d block(s) from the edge of the schematic. The outer %d "
                                   "blocks of a numbered arena are cut into a slope, so leave more room."
                                   % (edge, SLOPE))
    if min(width, depth) < 5:
        report.warnings.append("Under 5 tiles on one side. That is very tight for a fight.")


def main(argv):
    boss_flag = "--boss" in argv
    targets = [a for a in argv if a != "--boss"]
    if not targets:
        print(__doc__)
        return 2
    files = []
    for target in targets:
        path = Path(target)
        files.extend(sorted(path.rglob("*.schem")) if path.is_dir() else [path])
    if not files:
        print("No .schem files found.")
        return 2

    failed = False
    for path in files:
        print(path)
        try:
            boss = boss_flag or path.stem == "boss" or path.stem.endswith("_boss")
            report = describe(path, boss)
        except Exception as error:  # a file that is not a schematic at all
            print("  PROBLEM     cannot read this file: %s\n" % error)
            failed = True
            continue
        for line in report.lines:
            print(line)
        for text in report.problems:
            print("  PROBLEM     " + text)
        for text in report.warnings:
            print("  warning     " + text)
        if not report.problems and not report.warnings:
            print("  ok          nothing to fix")
        print()
        failed = failed or bool(report.problems)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
