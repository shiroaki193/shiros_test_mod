"""Generates the GameTest arena structures (src/main/resources/data/mobarmsrace/structure/).

Each is a stone floor at y=0 with open air above (tests run with sky access, so no ceiling):
  arena.nbt       48 x 40 x 9   ~40 block shots (apex ~20 blocks)
  arena_long.nbt  128 x 12 x 9  up to 120 block shots; the arc (apex ~70) flies above the box
  arena_wide.nbt  128 x 12 x 33 room for a small village scene beside the firing line
Regenerate after changing a size: python tools/gen_test_arena.py
"""
import gzip
import io
import pathlib
import struct

ARENAS = {"arena": (48, 40, 9), "arena_long": (128, 12, 9), "arena_wide": (128, 12, 33)}
DATA_VERSION = 4790  # Minecraft 26.1.2 ("world_version" in the Minecraft jar's version.json)
OUT_DIR = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/data/mobarmsrace/structure"

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def w_str(buf, s):
    data = s.encode("utf-8")
    buf.write(struct.pack(">H", len(data)))
    buf.write(data)


def w_payload(buf, tag, value):
    if tag == TAG_INT:
        buf.write(struct.pack(">i", value))
    elif tag == TAG_STRING:
        w_str(buf, value)
    elif tag == TAG_LIST:
        elem_tag, items = value
        buf.write(struct.pack(">bi", elem_tag if items else TAG_END, len(items)))
        for item in items:
            w_payload(buf, elem_tag, item)
    elif tag == TAG_COMPOUND:
        for name, (child_tag, child) in value.items():
            buf.write(struct.pack(">b", child_tag))
            w_str(buf, name)
            w_payload(buf, child_tag, child)
        buf.write(struct.pack(">b", TAG_END))


def int_list(*xs):
    return (TAG_LIST, (TAG_INT, list(xs)))


def write_arena(name, size_x, size_y, size_z):
    blocks = [
        {"pos": int_list(x, 0, z), "state": (TAG_INT, 0)}
        for x in range(size_x)
        for z in range(size_z)
    ]
    root = {
        "size": int_list(size_x, size_y, size_z),
        "entities": (TAG_LIST, (TAG_COMPOUND, [])),
        "blocks": (TAG_LIST, (TAG_COMPOUND, blocks)),
        "palette": (TAG_LIST, (TAG_COMPOUND, [{"Name": (TAG_STRING, "minecraft:stone")}])),
        "DataVersion": (TAG_INT, DATA_VERSION),
    }
    buf = io.BytesIO()
    buf.write(struct.pack(">b", TAG_COMPOUND))
    w_str(buf, "")
    w_payload(buf, TAG_COMPOUND, root)
    out = OUT_DIR / f"{name}.nbt"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(gzip.compress(buf.getvalue(), mtime=0))  # deterministic output
    print(f"wrote {out} ({size_x}x{size_y}x{size_z}, {len(blocks)} floor blocks)")


def main():
    for name, size in ARENAS.items():
        write_arena(name, *size)


if __name__ == "__main__":
    main()
