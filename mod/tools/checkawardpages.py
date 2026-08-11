#!/usr/bin/env python3
"""
No award may be dropped off the bottom of its page.

CollectionBookScreen draws award rows down the spread and ends the loop body
with:

    if (ry + rowH > b[3]) {
        continue; // never draw a row the page cannot hold
    }

which is a safe way to avoid drawing outside the page and a silent way to lose
an award: a row past the fold is invisible AND unclickable, so the reward
cannot be collected and nothing says why. That exact bug shipped once, when a
sixteen-pixel row-height floor met the Parlour's twenty-one awards.

The floor is still there — it has to be, or the rows become unreadable — so
whether an award is reachable depends on the group's SIZE, which changes every
time somebody adds one. This models the same arithmetic and sweeps the windows
the book can be opened in, asserting the skip branch never fires.

Group sizes are counted from Achievements.java and the per-spread capacity is
read from the screen, so adding an award — or changing how many fit — is
covered automatically rather than when somebody remembers to update this file.

Run: python3 tools/checkawardpages.py [src_root]
"""
import math
import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/java"
BOOK = os.path.join(ROOT, "com/jrpetty/mobtrumps/client/CollectionBookScreen.java")
CATALOGUE = os.path.join(ROOT, "com/jrpetty/mobtrumps/game/Achievements.java")

UI = 1.25
FOOTER_H, TAB_RESERVE, HINT_RESERVE = 26, 20, 22
SPINE = 24
CARD_W, CARD_H = 170, 236
MIN_SCALE = 0.26
BOOK_SCALE_CAP = None      # filled from the source below
# (cols, rows, headerH) — read from CollectionBookScreen, not guessed. An
# earlier version of this file invented the table and modelled a book that
# does not exist.
PLANS = []


def load_plans(path):
    """Read the plan table and the scale cap straight out of the Java."""
    src = open(path, encoding="utf-8").read()
    m = re.search(r"int\[\]\[\]\s+plans\s*=\s*\{(.+?)\};", src, re.S)
    if not m:
        raise SystemExit("FAIL  could not find the plans table in " + path)
    plans = [tuple(int(x) for x in re.findall(r"-?\d+", grp))
             for grp in re.findall(r"\{([^{}]*)\}", m.group(1))]
    cap = re.search(r"BOOK_SCALE_CAP\s*=\s*([0-9.]+)f", src)
    return plans, float(cap.group(1)) if cap else 1.0


def lg(v):
    return round(v / UI)


def group_sizes(path):
    src = open(path, encoding="utf-8").read()
    src = re.sub(r"//[^\n]*", "", src)
    out = {}
    for m in re.finditer(r"Group\.([A-Z]+)", src):
        out[m.group(1)] = out.get(m.group(1), 0) + 1
    return out


def solve_panel(width, height):
    """The book's own scale/plan solve, transcribed from the Java."""
    avail = height - TAB_RESERVE - HINT_RESERVE
    cols, rows, header = PLANS[-1]
    scale = 0.12
    for i, (c, r, h) in enumerate(PLANS):
        s_h = (((avail - h - FOOTER_H) / r) - 8) / CARD_H
        s_w = (((width - 28 - SPINE) / 2) / c - 8) / CARD_W
        s = min(BOOK_SCALE_CAP, min(s_w, s_h))
        if s >= MIN_SCALE or i == len(PLANS) - 1:
            cols, rows, header = c, r, h
            scale = max(0.12, s)
            break
    scale = min(scale, ((width - 12 - SPINE - 28) / (2 * cols) - 8) / CARD_W)
    scale = min(scale, (((avail - header - FOOTER_H) / rows) - 8) / CARD_H)
    scale = max(0.08, scale)

    cell_w = round(CARD_W * scale) + 8
    cell_h = round(CARD_H * scale) + 8
    page_w = cols * cell_w
    panel_w = 2 * page_w + SPINE + 28
    panel_h = header + rows * cell_h + FOOTER_H
    panel_x = max(0, (width - panel_w) // 2)
    band = max(0, avail - panel_h)
    panel_y = max(TAB_RESERVE, min(TAB_RESERVE + band // 2,
                                   max(TAB_RESERVE, height - HINT_RESERVE - panel_h)))
    page_bottom = panel_y + panel_h - 26
    return panel_x, panel_y, panel_w, page_bottom


def rows_dropped(n, width, height):
    """How many of a group's n awards the page silently refuses to draw."""
    _, panel_y, _, page_bottom = solve_panel(width, height)
    top = lg(panel_y + 48)
    bottom = lg(page_bottom)
    # drawPageHeading: +6, then +10, then returns y + 12
    y = top + 6 + 10 + 12
    available = bottom - y
    two_up = n * 16 > available
    per_column = math.ceil(n / 2) if two_up else n
    row_h = max(12 if two_up else 16, min(30, available // max(1, per_column)))
    dropped = 0
    for i in range(n):
        ry = y + (i % per_column) * row_h
        if ry + row_h > bottom:
            dropped += 1
    return dropped


def main():
    global PLANS, BOOK_SCALE_CAP
    if os.path.exists(BOOK):
        PLANS, BOOK_SCALE_CAP = load_plans(BOOK)
    if not os.path.exists(BOOK) or not os.path.exists(CATALOGUE):
        print("FAIL  cannot find the book screen or the award catalogue")
        sys.exit(1)
    cap = re.search(r"AWARDS_PER_SPREAD\s*=\s*(\d+)",
                    open(BOOK, encoding="utf-8").read())
    per_spread = int(cap.group(1)) if cap else 10 ** 9
    groups = group_sizes(CATALOGUE)
    if not groups:
        print("FAIL  found no award groups; has Achievements.java changed shape?")
        sys.exit(1)

    worst = {}
    checks = 0
    for width in list(range(320, 900, 11)) + [1024, 1280, 1920, 2560]:
        for height in list(range(240, 700, 7)) + [720, 1080, 1440]:
            for name, n in groups.items():
                # each spread only draws its own slice, so check every slice
                for start in range(0, max(1, n), per_spread):
                    on_page = min(per_spread, n - start)
                    checks += 1
                    lost = rows_dropped(on_page, width, height)
                    if lost and lost > worst.get(name, (0,))[0]:
                        worst[name] = (lost, width, height, on_page)

    if worst:
        print("FAIL  awards are being dropped off the page (invisible and unclaimable):")
        for name, (lost, w, h, n) in sorted(worst.items()):
            print(f"   {name}: up to {lost} of {n} on a spread lost, worst at {w}x{h}")
        sys.exit(1)
    biggest = max(groups.items(), key=lambda kv: kv[1])
    print(f"PASS  {checks} window/group combinations: every award is drawn and "
          f"clickable (largest group {biggest[0]} at {biggest[1]}).")


if __name__ == "__main__":
    main()
