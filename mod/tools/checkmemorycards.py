#!/usr/bin/env python3
"""
The Memory board's sweep is run by the Java harness (tools/Regress.java), which
exercises MemoryLayout.solve itself rather than a copy of it. That leaves
exactly one thing the harness cannot see: it sweeps with the card's real
dimensions written as literals, because CardRenderer is client-side and the
harness has no Minecraft on its classpath.

So this checks the one link in the chain that is not otherwise verified -- that
CardRenderer still measures 170x236, the size the sweep covers -- and that the
screen has not quietly grown its own layout arithmetic again.

Run: python3 tools/checkmemorycards.py [src_root]
"""
import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/java"
RENDERER = os.path.join(ROOT, "com/jrpetty/mobtrumps/client/CardRenderer.java")
SCREEN = os.path.join(ROOT, "com/jrpetty/mobtrumps/client/MemoryScreen.java")
HARNESS = "tools/Regress.java"


def main():
    for path in (RENDERER, SCREEN, HARNESS):
        if not os.path.exists(path):
            print(f"FAIL  cannot find {path}")
            sys.exit(1)
    renderer = open(RENDERER, encoding="utf-8").read()
    screen = open(SCREEN, encoding="utf-8").read()
    harness = open(HARNESS, encoding="utf-8").read()

    dims = {}
    for name in ("CARD_W", "CARD_H"):
        m = re.search(r"\b" + name + r"\s*=\s*(\d+)\s*;", renderer)
        if not m:
            print(f"FAIL  cannot read {name} from CardRenderer")
            sys.exit(1)
        dims[name] = int(m.group(1))

    swept = set(re.findall(r"MemoryLayout\.solve\([^)]*?,\s*(\d+)\s*,\s*(\d+)\s*\)", harness))
    if not swept:
        print("FAIL  the harness no longer sweeps MemoryLayout.solve")
        sys.exit(1)
    want = (str(dims["CARD_W"]), str(dims["CARD_H"]))
    wrong = sorted(s for s in swept if s != want)
    if wrong:
        print(f"FAIL  the harness sweeps the board at {wrong[0][0]}x{wrong[0][1]} but a card "
              f"is {dims['CARD_W']}x{dims['CARD_H']}")
        print("      the sweep is measuring a card size that does not exist")
        sys.exit(1)

    # the screen must ask MemoryLayout rather than doing it again by hand
    if "MemoryLayout.solve(" not in screen:
        print("FAIL  MemoryScreen no longer uses MemoryLayout.solve, so the swept "
              "arithmetic is not the arithmetic being drawn")
        sys.exit(1)
    for owned in ("HEADER_H =", "FOOTER_H =", "SCALE_CAP =", "GAP ="):
        if owned in screen:
            print(f"FAIL  MemoryScreen declares its own {owned.strip(' =')}; the layout "
                  f"constants belong to MemoryLayout, which is what gets swept")
            sys.exit(1)
    print(f"PASS  a card is {dims['CARD_W']}x{dims['CARD_H']}, which is the size the "
          f"harness sweeps, and the screen defers to MemoryLayout for every "
          f"layout number.")


if __name__ == "__main__":
    main()
