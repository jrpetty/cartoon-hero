#!/usr/bin/env python3
"""
Catch a live mob rendered into the corner of the window.

CardRenderer.renderCard draws the card through the pose stack but places the
portrait mob in SCREEN SPACE, from the x,y it is handed:

    int px1 = x + Math.round(13 * scale);
    ...
    InventoryScreen.renderEntityInInventoryFollowsMouse(g, px1, py1, px2, py2, ...)

So a caller that passes 0,0 and moves the card with a pose translate gets a
card in the right place and a mob rendered — and scissored — at the top-left
corner of the window. It is not a crash and it is not obvious: the card looks
almost right, the mob is simply absent, and you pay the full cost of an entity
render for nothing.

This has shipped twice. Twenty-One drew no mobs for a release. Guess Who did
it eighty-one times a frame, which is what made the board lag.

Flagged only when BOTH are true, so a deliberately mobless card (the deck fan
on the table menu, a card back) is not a false positive:
  * x and y are the literals 0, 0
  * the mob argument is something other than the literal null

Run: python3 tools/checkcardcoords.py [src_root]
"""
import glob
import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/java"

# renderCard has two shapes; the mob is always the argument after mouseX,mouseY
#   (g, font, card,        x, y, scale, mouseX, mouseY, mob, ...)
#   (g, font, card, level, x, y, scale, mouseX, mouseY, mob, ...)
CALL = re.compile(r"CardRenderer\.renderCard\s*\(", re.S)


def split_args(text, start):
    """Split the argument list beginning at start (just past the '('),
    respecting nesting so a nested call is one argument."""
    args, depth, cur, i = [], 0, [], start
    while i < len(text):
        c = text[i]
        if c in "([":
            depth += 1
        elif c in ")]":
            if depth == 0:
                args.append("".join(cur).strip())
                return args, i
            depth -= 1
        if c == "," and depth == 0:
            args.append("".join(cur).strip())
            cur = []
        else:
            cur.append(c)
        i += 1
    return args, i


def main():
    bad = []
    checked = 0
    for path in sorted(glob.glob(os.path.join(ROOT, "**", "*.java"), recursive=True)):
        src = open(path, encoding="utf-8").read()
        # strip comments so a code sample in a javadoc cannot trip this
        body = re.sub(r"//[^\n]*", "", src)
        body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
        for m in CALL.finditer(body):
            args, _ = split_args(body, m.end())
            if len(args) < 9:
                continue
            checked += 1
            # the level overload has one extra leading int before x
            has_level = len(args) >= 12
            xi = 4 if has_level else 3
            x, y = args[xi], args[xi + 1]
            mob = args[xi + 5]
            if x == "0" and y == "0" and mob != "null":
                line = body[:m.start()].count("\n") + 1
                bad.append((path, line, mob))

    if bad:
        print("FAIL  a live mob is being rendered at the window corner:")
        for path, line, mob in bad:
            print(f"   {path}:{line}: renderCard(..., 0, 0, ..., {mob}, ...)"
                  f"  — pass the card's real x,y")
        sys.exit(1)
    print(f"PASS  {checked} renderCard calls: every one with a live mob passes "
          f"the card's real screen position.")


if __name__ == "__main__":
    main()
