#!/usr/bin/env python3
"""Retheme a Sponge v3 .schem by rewriting its block palette in place.

Only the palette's block ids change. The BlockData index array, the dimensions and
every marker block are left byte-identical, so the build is guaranteed to keep its
exact geometry and its scene wiring (stand_marker / npc_marker / scene_spawn are the
booth contract; rewriting one would silently break the scene).

Usage:
    python tools/retheme_schem.py <in.schem> <out.schem> --theme nether
    python tools/retheme_schem.py <in.schem> --list          # print the palette and exit

Sponge v3 layout, for the reader: gzipped NBT, and the palette is a TAG_Compound named
"Palette" holding TAG_Int entries keyed by block id string. Rewriting a name changes the
entry's length prefix, so the whole compound is re-serialised rather than patched.
"""

import argparse
import gzip
import re
import struct
import sys
from pathlib import Path

# Vanilla-to-nether mapping. Keys match the id WITHOUT its [state] suffix, so a block's
# properties survive the swap: dark_oak_log[axis=y] -> warped_stem[axis=y]. Only blocks
# that share a property set may be mapped, or the state string will not parse on load
# (e.g. a log's axis exists on a stem, but a grass_block's snowy does not exist on
# netherrack - see SNOWY_DROP below).
NETHER_THEME = {
    # Ground
    "minecraft:grass_block": "minecraft:netherrack",
    "minecraft:dirt": "minecraft:netherrack",
    "minecraft:coarse_dirt": "minecraft:netherrack",
    "minecraft:podzol": "minecraft:warped_nylium",
    "minecraft:dirt_path": "minecraft:nether_bricks",
    "minecraft:gravel": "minecraft:soul_soil",
    "minecraft:sand": "minecraft:soul_sand",
    "minecraft:stone": "minecraft:blackstone",
    "minecraft:cobblestone": "minecraft:blackstone",
    "minecraft:stone_bricks": "minecraft:polished_blackstone_bricks",
    # Wood: every overworld species maps onto warped so one hall reads as one build.
    "minecraft:dark_oak_log": "minecraft:warped_stem",
    "minecraft:oak_log": "minecraft:warped_stem",
    "minecraft:birch_log": "minecraft:warped_stem",
    "minecraft:spruce_log": "minecraft:warped_stem",
    "minecraft:dark_oak_planks": "minecraft:warped_planks",
    "minecraft:oak_planks": "minecraft:warped_planks",
    "minecraft:birch_planks": "minecraft:warped_planks",
    "minecraft:spruce_planks": "minecraft:warped_planks",
    "minecraft:birch_slab": "minecraft:warped_slab",
    "minecraft:oak_slab": "minecraft:warped_slab",
    "minecraft:dark_oak_slab": "minecraft:warped_slab",
    "minecraft:spruce_slab": "minecraft:warped_slab",
    "minecraft:birch_stairs": "minecraft:warped_stairs",
    "minecraft:oak_stairs": "minecraft:warped_stairs",
    "minecraft:dark_oak_stairs": "minecraft:warped_stairs",
    "minecraft:birch_fence": "minecraft:warped_fence",
    "minecraft:oak_fence": "minecraft:warped_fence",
    "minecraft:dark_oak_fence": "minecraft:warped_fence",
    "minecraft:oak_door": "minecraft:warped_door",
    "minecraft:birch_door": "minecraft:warped_door",
    "minecraft:oak_trapdoor": "minecraft:warped_trapdoor",
    # Cloth: the piglins' own colours.
    "minecraft:white_wool": "minecraft:black_wool",
    "minecraft:red_wool": "minecraft:orange_wool",
    "minecraft:white_wall_banner": "minecraft:black_wall_banner",
    "minecraft:red_wall_banner": "minecraft:orange_wall_banner",
    "minecraft:white_banner": "minecraft:black_banner",
    "minecraft:red_banner": "minecraft:orange_banner",
    "minecraft:white_carpet": "minecraft:black_carpet",
    "minecraft:red_carpet": "minecraft:orange_carpet",
    # Light
    "minecraft:torch": "minecraft:soul_torch",
    "minecraft:wall_torch": "minecraft:soul_wall_torch",
    "minecraft:lantern": "minecraft:soul_lantern",
    "minecraft:campfire": "minecraft:soul_campfire",
    "minecraft:glowstone": "minecraft:shroomlight",
    # Flora
    "minecraft:oak_leaves": "minecraft:nether_wart_block",
    "minecraft:grass": "minecraft:warped_roots",
    "minecraft:short_grass": "minecraft:warped_roots",
    "minecraft:tall_grass": "minecraft:warped_roots",
    "minecraft:poppy": "minecraft:crimson_fungus",
    "minecraft:dandelion": "minecraft:warped_fungus",
    # Liquid: an overworld pond becomes lava in the nether.
    "minecraft:water": "minecraft:lava",
}

THEMES = {"nether": NETHER_THEME}

# Properties that do not exist on the mapped target and must be dropped. Leaving one in
# makes the state string unparseable, and the loader silently resolves it to AIR: the
# block just vanishes from the build with no error.
SNOWY_DROP = {"minecraft:netherrack", "minecraft:warped_nylium", "minecraft:soul_soil"}

# Never rewrite these. They are the scene's wiring, not its theme: stand_marker places a
# booth, npc_marker places the trader, scene_spawn is where the player lands.
PROTECTED_NAMESPACES = ("craftics:",)


def split_state(block_id):
    """'minecraft:oak_log[axis=y]' -> ('minecraft:oak_log', '[axis=y]')"""
    i = block_id.find("[")
    return (block_id, "") if i < 0 else (block_id[:i], block_id[i:])


def strip_props(state, drop):
    """Remove named properties from a '[a=1,b=2]' state string."""
    if not state or not drop:
        return state
    inner = state[1:-1]
    kept = [p for p in inner.split(",") if p.split("=")[0] not in drop]
    return "[" + ",".join(kept) + "]" if kept else ""


def retheme_id(block_id, mapping):
    base, state = split_state(block_id)
    if base.startswith(PROTECTED_NAMESPACES):
        return block_id
    target = mapping.get(base)
    if target is None:
        return block_id
    if target in SNOWY_DROP:
        state = strip_props(state, {"snowy"})
    return target + state


def find_block_data(data):
    """Return (payload_start, payload_end, raw) for the TAG_Byte_Array holding the indices."""
    for key_name in (b"BlockData", b"Data"):
        key = bytes([7]) + struct.pack(">H", len(key_name)) + key_name
        i = data.find(key)
        if i < 0:
            continue
        ln_at = i + len(key)
        ln = struct.unpack(">i", data[ln_at:ln_at + 4])[0]
        start = ln_at + 4
        return start, start + ln, data[start:start + ln]
    raise SystemExit("no BlockData found: not a Sponge v3 .schem?")


def decode_varints(raw):
    out = []
    i = 0
    while i < len(raw):
        val = 0
        shift = 0
        while True:
            b = raw[i]
            i += 1
            val |= (b & 0x7F) << shift
            if not (b & 0x80):
                break
            shift += 7
        out.append(val)
    return out


def encode_varints(ids):
    out = bytearray()
    for v in ids:
        while True:
            b = v & 0x7F
            v >>= 7
            if v:
                out.append(b | 0x80)
            else:
                out.append(b)
                break
    return bytes(out)


def remap_block_data(data, remap):
    """Rewrite the index array so every id in `remap` points at its canonical id."""
    _, _, raw = find_block_data(data)
    ids = decode_varints(raw)
    ids = [remap.get(v, v) for v in ids]
    return encode_varints(ids)


def replace_block_data(data, new_raw):
    start, end, _ = find_block_data(data)
    ln_at = start - 4
    return data[:ln_at] + struct.pack(">i", len(new_raw)) + new_raw + data[end:]


def read_nbt(path):
    with open(path, "rb") as f:
        raw = f.read()
    if raw[:2] == b"\x1f\x8b":
        return gzip.decompress(raw), True
    return raw, False


def find_palette(data):
    """Return (start, end, entries) for the TAG_Compound named 'Palette'."""
    key = bytes([10]) + struct.pack(">H", 7) + b"Palette"
    i = data.find(key)
    if i < 0:
        raise SystemExit("no Palette compound found: not a Sponge v3 .schem?")
    j = i + len(key)
    start = j
    entries = []
    while j < len(data) and data[j] == 3:  # TAG_Int
        ln = struct.unpack(">H", data[j + 1:j + 3])[0]
        name = data[j + 3:j + 3 + ln].decode("utf-8")
        val = struct.unpack(">i", data[j + 3 + ln:j + 7 + ln])[0]
        entries.append((name, val))
        j += 7 + ln
    if j >= len(data) or data[j] != 0:
        raise SystemExit("palette did not end with TAG_End: refusing to rewrite")
    return start, j, entries


def encode_palette(entries):
    out = bytearray()
    for name, val in entries:
        nb = name.encode("utf-8")
        out += bytes([3]) + struct.pack(">H", len(nb)) + nb + struct.pack(">i", val)
    return bytes(out)


def main():
    ap = argparse.ArgumentParser(description="Retheme a .schem's block palette in place.")
    ap.add_argument("input")
    ap.add_argument("output", nargs="?")
    ap.add_argument("--theme", default="nether", choices=sorted(THEMES))
    ap.add_argument("--list", action="store_true", help="print the palette and exit")
    args = ap.parse_args()

    data, was_gzipped = read_nbt(args.input)
    start, end, entries = find_palette(data)

    if args.list:
        for name, val in sorted(entries, key=lambda e: e[1]):
            print(f"  {val:3d}  {name}")
        return

    if not args.output:
        ap.error("output path required unless --list")

    mapping = THEMES[args.theme]
    new_entries = []
    changed, protected, unmapped = [], [], []
    for name, val in entries:
        new = retheme_id(name, mapping)
        new_entries.append((new, val))
        base, _ = split_state(name)
        if new != name:
            changed.append((name, new))
        elif base.startswith(PROTECTED_NAMESPACES):
            protected.append(name)
        elif base != "minecraft:air":
            unmapped.append(name)

    # Two source blocks mapping onto one target (dirt AND grass_block -> netherrack) would
    # write the palette compound twice under the same key. NBT compounds are keyed maps, so
    # the duplicate does NOT survive parsing: SchemLoader builds its palette by iterating
    # getKeys(), the second entry wins, and the first id is left null in the palette array.
    # A null palette slot renders as AIR, so every block using the losing id silently
    # vanishes from the build.
    #
    # Merging is still what we want visually, so keep one canonical id and rewrite the
    # index array to point the losing ids at the winner. That keeps every key unique, which
    # is the format's actual requirement.
    canonical = {}
    remap = {}
    deduped = []
    for name, val in new_entries:
        if name in canonical:
            remap[val] = canonical[name]
        else:
            canonical[name] = val
            deduped.append((name, val))
    counts = {}
    for name, _ in new_entries:
        counts[name] = counts.get(name, 0) + 1
    merged = sorted(n for n, c in counts.items() if c > 1)

    block_data = None
    if remap:
        block_data = remap_block_data(data, remap)

    out = data[:start] + encode_palette(deduped) + data[end:]
    if block_data is not None:
        out = replace_block_data(out, block_data)
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    with open(args.output, "wb") as f:
        f.write(gzip.compress(out) if was_gzipped else out)

    print(f"{args.input} -> {args.output}  (theme: {args.theme})")
    print(f"\n  rethemed ({len(changed)}):")
    for old, new in changed:
        print(f"    {old}\n      -> {new}")
    if protected:
        print(f"\n  preserved markers ({len(protected)}): {', '.join(protected)}")
    if unmapped:
        print(f"\n  left as-is, no mapping ({len(unmapped)}):")
        for n in unmapped:
            print(f"    {n}")
    if merged:
        print(f"\n  merged onto one palette id ({len(merged)}): {', '.join(merged)}")
        print(f"    {len(remap)} duplicate id(s) folded, block data reindexed")


if __name__ == "__main__":
    main()
