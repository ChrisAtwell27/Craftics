"""Read and write Sponge .schem files (the format WorldEdit saves) with no dependencies.

Shared by build_example_arenas.py and check_arena.py. You do not need this to make an arena:
build it in game and save it with WorldEdit. It is here so the example arenas can be rebuilt
from a script you can read, and so check_arena.py can open any schematic you hand it.

A .schem is gzipped NBT. Blocks are stored as a palette (block state string -> number) plus one
number per block, written as a varint, in the order y, then z, then x.
"""

import gzip
import io
import struct

# NBT tag ids
END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)

# Minecraft 1.21.1. Newer versions upgrade an older schematic when it loads.
DATA_VERSION = 3955

AIR = "minecraft:air"


# --------------------------------------------------------------------------- reading

def _read_payload(buf, tag):
    def take(fmt):
        return struct.unpack(fmt, buf.read(struct.calcsize(fmt)))

    if tag == BYTE:
        return take(">b")[0]
    if tag == SHORT:
        return take(">h")[0]
    if tag == INT:
        return take(">i")[0]
    if tag == LONG:
        return take(">q")[0]
    if tag == FLOAT:
        return take(">f")[0]
    if tag == DOUBLE:
        return take(">d")[0]
    if tag == BYTE_ARRAY:
        (n,) = take(">i")
        return buf.read(n)
    if tag == STRING:
        (n,) = take(">H")
        return buf.read(n).decode("utf-8")
    if tag == LIST:
        (inner,) = take(">b")
        (n,) = take(">i")
        return [_read_payload(buf, inner) for _ in range(n)]
    if tag == COMPOUND:
        out = {}
        while True:
            (inner,) = take(">b")
            if inner == END:
                return out
            (n,) = take(">H")
            name = buf.read(n).decode("utf-8")
            out[name] = _read_payload(buf, inner)
    if tag == INT_ARRAY:
        (n,) = take(">i")
        return list(take(">%di" % n))
    if tag == LONG_ARRAY:
        (n,) = take(">i")
        return list(take(">%dq" % n))
    raise ValueError("unknown NBT tag %d" % tag)


def read_nbt(path):
    """Return the root compound of a gzipped NBT file as plain dicts, lists and numbers."""
    with gzip.open(path, "rb") as f:
        buf = io.BytesIO(f.read())
    (tag,) = struct.unpack(">b", buf.read(1))
    if tag != COMPOUND:
        raise ValueError("not an NBT compound")
    (n,) = struct.unpack(">H", buf.read(2))
    buf.read(n)  # root name, always empty
    return _read_payload(buf, COMPOUND)


class Schematic:
    """A block volume. x runs along the width, y is up, z runs along the length."""

    def __init__(self, width, height, length):
        self.width, self.height, self.length = width, height, length
        self.blocks = [AIR] * (width * height * length)
        self.version = 3

    # -- editing ------------------------------------------------------------

    def index(self, x, y, z):
        return (y * self.length + z) * self.width + x

    def inside(self, x, y, z):
        return 0 <= x < self.width and 0 <= y < self.height and 0 <= z < self.length

    def get(self, x, y, z):
        return self.blocks[self.index(x, y, z)] if self.inside(x, y, z) else AIR

    def set(self, x, y, z, state):
        if self.inside(x, y, z):
            self.blocks[self.index(x, y, z)] = state

    def fill(self, x1, y1, z1, x2, y2, z2, state):
        for y in range(min(y1, y2), max(y1, y2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                for x in range(min(x1, x2), max(x1, x2) + 1):
                    self.set(x, y, z, state)

    def find(self, block_id):
        """Every (x, y, z) holding this block id, ignoring its [state]."""
        hits = []
        for i, state in enumerate(self.blocks):
            if state.split("[", 1)[0] == block_id:
                y, rest = divmod(i, self.length * self.width)
                z, x = divmod(rest, self.width)
                hits.append((x, y, z))
        # Craftics scans x first, then y, then z. Match it so "first one found" agrees.
        hits.sort()
        return hits

    def namespaces(self):
        return sorted({state.split(":", 1)[0] for state in self.blocks if ":" in state})

    # -- loading ------------------------------------------------------------

    @classmethod
    def load(cls, path):
        root = read_nbt(path)
        body = root.get("Schematic", root)  # v3 wraps everything, v2 is flat
        width = body["Width"] & 0xFFFF
        height = body["Height"] & 0xFFFF
        length = body["Length"] & 0xFFFF
        if "Blocks" in body:
            palette, data = body["Blocks"]["Palette"], body["Blocks"]["Data"]
            version = 3
        else:
            palette, data = body["Palette"], body["BlockData"]
            version = 2

        names = {number: state for state, number in palette.items()}
        schem = cls(width, height, length)
        schem.version = version
        i = pos = 0
        total = width * height * length
        while i < len(data) and pos < total:
            value = shift = 0
            while True:
                byte = data[i]
                i += 1
                value |= (byte & 0x7F) << shift
                shift += 7
                if not byte & 0x80:
                    break
            schem.blocks[pos] = names.get(value, AIR)
            pos += 1
        if pos != total:
            raise ValueError("block data ends early: %d of %d blocks" % (pos, total))
        return schem

    # -- saving -------------------------------------------------------------

    def save(self, path):
        """Write a Sponge version 3 schematic, the same layout WorldEdit 7.3 produces."""
        palette = {AIR: 0}
        data = bytearray()
        for state in self.blocks:
            number = palette.setdefault(state, len(palette))
            while True:
                byte = number & 0x7F
                number >>= 7
                data.append(byte | (0x80 if number else 0))
                if not number:
                    break

        out = io.BytesIO()

        def name(tag, text):
            raw = text.encode("utf-8")
            out.write(struct.pack(">bH", tag, len(raw)) + raw)

        name(COMPOUND, "")
        name(COMPOUND, "Schematic")
        name(INT, "Version"); out.write(struct.pack(">i", 3))
        name(INT, "DataVersion"); out.write(struct.pack(">i", DATA_VERSION))
        name(SHORT, "Width"); out.write(struct.pack(">H", self.width))
        name(SHORT, "Height"); out.write(struct.pack(">H", self.height))
        name(SHORT, "Length"); out.write(struct.pack(">H", self.length))
        name(INT_ARRAY, "Offset"); out.write(struct.pack(">i3i", 3, 0, 0, 0))
        name(COMPOUND, "Blocks")
        name(COMPOUND, "Palette")
        for state, number in palette.items():
            name(INT, state); out.write(struct.pack(">i", number))
        out.write(b"\x00")  # end Palette
        name(BYTE_ARRAY, "Data"); out.write(struct.pack(">i", len(data)) + bytes(data))
        name(LIST, "BlockEntities"); out.write(struct.pack(">bi", COMPOUND, 0))
        out.write(b"\x00")  # end Blocks
        out.write(b"\x00")  # end Schematic
        out.write(b"\x00")  # end root

        # mtime=0 keeps the file byte-identical between runs, so rebuilding changes nothing in git.
        with open(path, "wb") as raw:
            with gzip.GzipFile(fileobj=raw, mode="wb", mtime=0, filename="") as gz:
                gz.write(out.getvalue())
