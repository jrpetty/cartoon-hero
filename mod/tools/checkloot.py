#!/usr/bin/env python3
"""
Every block we register must drop something when it is broken.

A block with no loot table does not fail, warn, or log — it silently drops
nothing, and the player has simply lost it. Two of the four blocks in this mod
shipped that way (the card shredder and the printing press) and nobody noticed,
because the failure looks exactly like normal mining until you check your
inventory.

Reads the registered block names straight out of ModBlocks so a new block is
covered the moment it exists, rather than when somebody remembers to add it
here. Also verifies each table actually names the block it belongs to, since a
copy-pasted table that drops the wrong block is the other half of this bug.

Run: python3 tools/checkloot.py [src_root] [resources_root]
"""
import json
import os
import re
import sys

SRC = sys.argv[1] if len(sys.argv) > 1 else "src/main/java"
RES = sys.argv[2] if len(sys.argv) > 2 else "src/main/resources"

MODID = "mobtrumps"
BLOCKS_JAVA = os.path.join(SRC, "com/jrpetty/mobtrumps/ModBlocks.java")
LOOT_DIR = os.path.join(RES, "data", MODID, "loot_table", "blocks")


def main():
    if not os.path.exists(BLOCKS_JAVA):
        print(f"FAIL  cannot find {BLOCKS_JAVA}")
        sys.exit(1)
    src = open(BLOCKS_JAVA, encoding="utf-8").read()
    src = re.sub(r"//[^\n]*", "", src)
    # BLOCKS.register("name", ...) — the block registry only
    names = re.findall(r"BLOCKS\.register\(\s*\"([a-z0-9_]+)\"", src)
    if not names:
        print("FAIL  found no registered blocks; has ModBlocks changed shape?")
        sys.exit(1)

    problems = []
    for name in names:
        path = os.path.join(LOOT_DIR, name + ".json")
        if not os.path.exists(path):
            problems.append(f"{name}: no loot table — breaking it drops nothing")
            continue
        try:
            data = json.load(open(path, encoding="utf-8"))
        except json.JSONDecodeError as bad:
            problems.append(f"{name}: loot table is not valid JSON ({bad})")
            continue
        dropped = set(re.findall(r'"name"\s*:\s*"([^"]+)"', json.dumps(data)))
        want = f"{MODID}:{name}"
        if want not in dropped:
            problems.append(f"{name}: loot table never drops {want} "
                            f"(drops {sorted(dropped) or 'nothing'})")

    if problems:
        print("FAIL  blocks that vanish or drop the wrong thing when broken:")
        for p in problems:
            print("   " + p)
        sys.exit(1)
    print(f"PASS  {len(names)} blocks: every one has a loot table that drops itself.")


if __name__ == "__main__":
    main()
