"""Generates the GameTest arena structure (src/main/resources/data/mobarmsrace/structure/arena.nbt).

48 x 40 x 9 blocks: a stone floor at y=0 and open air above, big enough for ~40 block mortar
shots (apex ~20 blocks). Regenerate after changing the size: python tools/gen_test_arena.py
"""
import gzip
import io
import pathlib
import struct

SIZE_X, SIZE_Y, SIZE_Z = 48, 40, 9
DATA_VERSION = 4786  # Minecraft 26.1 (build/mc-src/version.json "world_version")
OUT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/data/mobarmsrace/structure/arena.nbt"

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


def main():
    blocks = [
        {"pos": int_list(x, 0, z), "state": (TAG_INT, 0)}
        for x in range(SIZE_X)
        for z in range(SIZE_Z)
    ]
    root = {
        "size": int_list(SIZE_X, SIZE_Y, SIZE_Z),
        "entities": (TAG_LIST, (TAG_COMPOUND, [])),
        "blocks": (TAG_LIST, (TAG_COMPOUND, blocks)),
        "palette": (TAG_LIST, (TAG_COMPOUND, [{"Name": (TAG_STRING, "minecraft:stone")}])),
        "DataVersion": (TAG_INT, DATA_VERSION),
    }
    buf = io.BytesIO()
    buf.write(struct.pack(">b", TAG_COMPOUND))
    w_str(buf, "")
    w_payload(buf, TAG_COMPOUND, root)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(gzip.compress(buf.getvalue()))
    print(f"wrote {OUT} ({SIZE_X}x{SIZE_Y}x{SIZE_Z}, {len(blocks)} floor blocks)")


if __name__ == "__main__":
    main()
