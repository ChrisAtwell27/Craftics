#!/usr/bin/env python3
"""
Generate the 8x8 status-effect icons drawn above combatants' heads in combat.

Source of truth for the artwork. Each icon is hand-drawn below as an 8x8 grid, so the shapes
are deliberate rather than generated noise. Re-run after adding an effect to EffectIcons.java:

    python tools/generate_effect_icons.py

Writes PNGs to src/main/resources/assets/craftics/textures/gui/effects/.

Pure stdlib: PNGs are built by hand (zlib + struct), so this needs no Pillow/numpy.

Grid legend, per icon:
    '.' = transparent
    '#' = the effect's main color
    '+' = a lighter shade of it (highlight)
    '-' = a darker shade of it (shade/outline)
    'o' = white
    'x' = black
"""

import os
import struct
import zlib

OUT_DIR = os.path.join("src", "main", "resources", "assets", "craftics",
                       "textures", "gui", "effects")

SIZE = 8

# Colors must match the constants in EffectIcons.java.
RED        = 0xFF5555
DARK_RED   = 0xAA0000
ORANGE     = 0xFF8800
YELLOW     = 0xFFDD44
GREEN      = 0x55FF55
DARK_GREEN = 0x228822
AQUA       = 0x55FFFF
BLUE       = 0x5599FF
PURPLE     = 0xAA55FF
PINK       = 0xFF77CC
GRAY       = 0xAAAAAA
DARK_GRAY  = 0x555555
WHITE      = 0xFFFFFF
BLACK      = 0x222222
BROWN      = 0xAA7744

# name -> (main color, 8 rows of 8 chars)
ICONS = {
    # ---------------- Buffs ----------------
    # Regeneration: a plus / cross (healing).
    "regeneration": (GREEN, [
        "...##...",
        "...##...",
        "...##...",
        "########",
        "########",
        "...##...",
        "...##...",
        "...##...",
    ]),
    # Strength: a raised fist / bicep-ish blocky arm.
    "strength": (RED, [
        "..####..",
        ".######.",
        "###..###",
        "##....##",
        "##....##",
        "###..###",
        ".######.",
        "..####..",
    ]),
    # Resistance: a shield.
    "resistance": (BLUE, [
        ".######.",
        "########",
        "##+..+##",
        "##+..+##",
        "##....##",
        ".##..##.",
        "..####..",
        "...##...",
    ]),
    # Speed: a forward chevron / motion arrow.
    "speed": (AQUA, [
        "..#.....",
        "..##....",
        "..###...",
        "..####..",
        "..####..",
        "..###...",
        "..##....",
        "..#.....",
    ]),
    # Airtime: an upward double-chevron (being lofted into the air).
    "airtime": (AQUA, [
        "...##...",
        "..####..",
        ".######.",
        "########",
        "...##...",
        "..####..",
        ".######.",
        "########",
    ]),
    # Haste: a lightning bolt.
    "haste": (YELLOW, [
        "....##..",
        "...##...",
        "..##....",
        ".#####..",
        "...##...",
        "..##....",
        ".##.....",
        ".#......",
    ]),
    # Absorption: a heart (extra HP).
    "absorption": (YELLOW, [
        ".##..##.",
        "########",
        "########",
        "########",
        ".######.",
        "..####..",
        "...##...",
        "........",
    ]),
    # Luck: a four-leaf clover.
    "luck": (GREEN, [
        "..#..#..",
        ".######.",
        ".######.",
        "..#####.",
        "..#..#..",
        "...##...",
        "...##...",
        "...##...",
    ]),
    # Slow Falling: a feather drifting down.
    "slow_falling": (WHITE, [
        ".....##.",
        "....###.",
        "...####.",
        "..####..",
        ".####...",
        ".###....",
        "##......",
        "#.......",
    ]),
    # Fire Resistance: a flame inside a shield-ish ring.
    "fire_resistance": (ORANGE, [
        "...##...",
        "..####..",
        ".##++##.",
        ".##++##.",
        "########",
        "########",
        ".######.",
        "..####..",
    ]),
    # Invisibility: a dashed outline of a figure (hollow).
    "invisibility": (GRAY, [
        "..####..",
        ".#....#.",
        "#......#",
        "........",
        "#......#",
        ".#....#.",
        "..#..#..",
        "...##...",
    ]),
    # Water Breathing: a bubble.
    "water_breathing": (AQUA, [
        "..####..",
        ".##oo##.",
        "##o...##",
        "##....##",
        "##....##",
        "##....##",
        ".######.",
        "..####..",
    ]),

    # ---------------- Debuffs ----------------
    # Poison: skull glyph (skull.png shape, recolored to the poison green).
    "poison": (DARK_GREEN, [
        "........",
        ".######.",
        "#......#",
        "........",
        "........",
        ".##..##.",
        "........",
        "........",
    ]),
    # Wither: skull glyph (skull.png shape, recolored to the wither black).
    "wither": (BLACK, [
        "........",
        ".######.",
        "#......#",
        "........",
        "........",
        ".##..##.",
        "........",
        "........",
    ]),
    # Burning: a flame.
    "burning": (ORANGE, [
        "...#....",
        "..##....",
        "..###...",
        ".##+##..",
        ".#+++#..",
        "##+++##.",
        "##+++##.",
        ".#####..",
    ]),
    # Bleeding: falling blood drops.
    "bleeding": (DARK_RED, [
        "...#....",
        "...#....",
        "..###...",
        "..###...",
        "........",
        "..#..#..",
        ".###.###",
        ".###.###",
    ]),
    # Slowness: a downward chevron (dragged down).
    "slowness": (BROWN, [
        "........",
        "########",
        ".######.",
        "..####..",
        "...##...",
        "........",
        "########",
        "........",
    ]),
    # Weakness: a cracked / broken sword.
    "weakness": (GRAY, [
        ".....#..",
        "....##..",
        "...##...",
        "..#.....",
        ".##.....",
        "###.....",
        ".#......",
        "........",
    ]),
    # Blindness: a closed eye.
    "blindness": (DARK_GRAY, [
        "........",
        "........",
        "########",
        "........",
        "#..#..#.",
        ".#..#..#",
        "........",
        "........",
    ]),
    # Mining Fatigue: a drooping pickaxe.
    "mining_fatigue": (DARK_GRAY, [
        "##....##",
        ".##..##.",
        "..####..",
        "...##...",
        "...#....",
        "..#.....",
        ".#......",
        "#.......",
    ]),
    # Levitation: an up arrow.
    "levitation": (PURPLE, [
        "...##...",
        "..####..",
        ".######.",
        "########",
        "...##...",
        "...##...",
        "...##...",
        "...##...",
    ]),
    # Darkness: a filled circle (a void).
    "darkness": (BLACK, [
        "..####..",
        ".######.",
        "########",
        "########",
        "########",
        "########",
        ".######.",
        "..####..",
    ]),
    # Soaked: a water droplet.
    "soaked": (BLUE, [
        "...##...",
        "...##...",
        "..####..",
        "..####..",
        ".######.",
        "##+++###",
        "##+++##.",
        ".######.",
    ]),
    # Confusion: a question mark.
    "confusion": (PINK, [
        ".#####..",
        "##...##.",
        "......##",
        "....##..",
        "...##...",
        "...##...",
        "........",
        "...##...",
    ]),

    # ---------------- Enemy-only states ----------------
    # Stunned: a starburst.
    "stunned": (YELLOW, [
        "#..##..#",
        ".#.##.#.",
        "..####..",
        "########",
        "########",
        "..####..",
        ".#.##.#.",
        "#..##..#",
    ]),
    # Enraged: angry slanted brows.
    "enraged": (RED, [
        "##....##",
        ".##..##.",
        "..####..",
        "........",
        "..####..",
        ".######.",
        "##....##",
        "........",
    ]),
    # Marked: a crosshair / target.
    "marked": (RED, [
        "...##...",
        "..####..",
        ".##..##.",
        "##.##.##",
        "##.##.##",
        ".##..##.",
        "..####..",
        "...##...",
    ]),
    # Frozen: a snowflake.
    "frozen": (AQUA, [
        "#..##..#",
        ".#.##.#.",
        "..####..",
        "########",
        "########",
        "..####..",
        ".#.##.#.",
        "#..##..#",
    ]),
    # Taunting: a flag / banner.
    "taunting": (ORANGE, [
        "##......",
        "######..",
        "########",
        "######..",
        "##......",
        "##......",
        "##......",
        "##......",
    ]),
    # Exposed: a cracked shield (defense broken).
    "exposed": (PURPLE, [
        ".###.##.",
        "###..###",
        "##....##",
        "#..##..#",
        "##....##",
        ".##..##.",
        "..#..#..",
        "...##...",
    ]),

    # Fallback for an addon effect Craftics has no art for.
    "unknown": (WHITE, [
        "..####..",
        ".##..##.",
        "......##",
        "....##..",
        "...##...",
        "........",
        "...##...",
        "........",
    ]),

    # Not a status effect: the bobbing arrow over the combatant under the cursor.
    "hover_arrow": (GREEN, [
        "..####..",
        "..####..",
        "..####..",
        "..####..",
        "########",
        ".######.",
        "..####..",
        "...##...",
    ]),
}


def shade(rgb, factor):
    """Lighten (factor > 1) or darken (factor < 1) an 0xRRGGBB color."""
    r = min(255, int(((rgb >> 16) & 0xFF) * factor))
    g = min(255, int(((rgb >> 8) & 0xFF) * factor))
    b = min(255, int((rgb & 0xFF) * factor))
    return (r, g, b)


def rgba_rows(color, grid):
    """Turn an 8x8 char grid into raw RGBA scanlines."""
    main = shade(color, 1.0)
    light = shade(color, 1.45)
    dark = shade(color, 0.55)

    palette = {
        "#": main + (255,),
        "+": light + (255,),
        "-": dark + (255,),
        "o": (255, 255, 255, 255),
        "x": (20, 20, 20, 255),
        ".": (0, 0, 0, 0),
    }

    rows = []
    for y in range(SIZE):
        line = grid[y] if y < len(grid) else ""
        line = (line + "." * SIZE)[:SIZE]  # pad/truncate to exactly 8
        row = bytearray()
        for ch in line:
            row.extend(palette.get(ch, palette["."]))
        rows.append(bytes(row))
    return rows


def write_png(path, rows):
    """Write an 8x8 RGBA PNG by hand (no image library needed)."""
    raw = b"".join(b"\x00" + r for r in rows)  # filter byte 0 per scanline

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)  # 8-bit RGBA
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, (color, grid) in sorted(ICONS.items()):
        path = os.path.join(OUT_DIR, name + ".png")
        write_png(path, rgba_rows(color, grid))
        print("wrote", path)
    print("\n%d icons -> %s" % (len(ICONS), OUT_DIR))


if __name__ == "__main__":
    main()
