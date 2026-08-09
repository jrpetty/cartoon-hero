#!/usr/bin/env python3
"""
Sweep DuelWagerScreen's layout across every window it can be opened in.

Mirrors the arithmetic in render() exactly. The point is not to draw the
screen but to answer three questions the Java cannot answer offline: does the
content ever run into the button row, does anything cross the rail, and does
any row overlap the one above it. Every previous layout bug on this project
was one of those three.

Run: python3 tools/checkwagerlayout.py
"""
import sys

RAIL = 7
ROOMY_H = 214
FONT_H = 9          # Minecraft's font.lineHeight
QUICK = [0, 10, 50, 250, 1000]


def text_w(s):
    """Close enough to the vanilla font: 6px per char, and it only ever needs
    to be an over-estimate for the fit() budgets to stay honest."""
    return 6 * len(s)


def quick_label(a):
    if a == 0:
        return "Free"
    if a >= 1000:
        return f"{a // 1000}k"
    return str(a)


def layout(width, height, best_of, card_wager, price):
    """Return (rows, notes) where rows are (top, bottom, name) spans."""
    roomy = height >= ROOMY_H
    pw = min(320, width - 2 * RAIL - 8)
    rows = []

    y = RAIL + (8 if roomy else 3)
    rows.append((y, y + int(FONT_H * 1.5), "title"))
    y += 16 if roomy else 14
    rows.append((y, y + FONT_H, "versus"))
    y += 11
    if best_of > 1:
        rows.append((y, y + FONT_H, "bestof"))
        y += 10
    rows.append((y, y + 1, "rule"))
    y += 8 if roomy else 5

    plate_h = 34 if roomy else 26
    plate_w = min(pw - 40, 220)
    rows.append((y, y + plate_h, "price plate"))
    y += plate_h + (7 if roomy else 4)

    if card_wager:
        rows.append((y, y + FONT_H, "card note"))
        y += 11

    rows.append((y, y + 16, "price box"))
    y += 20

    if roomy:
        rows.append((y, y + 13, "quick row"))
        y += 17

    rows.append((y, y + 15, "lamps"))
    y += 18
    rows.append((y, y + FONT_H, "purse"))
    y += 10
    rows.append((y, y + FONT_H, "note"))
    content_bottom = y + FONT_H

    by = height - RAIL - 30
    rows.append((by, by + 19, "buttons"))
    clock_y = by - 11
    rows.append((clock_y, clock_y + FONT_H, "clock"))

    # widths that have to fit
    widths = []
    bw = min(96, width // 3)
    set_w = text_w("SET") + 14
    widths.append((bw + 4 + set_w, pw, "price row"))
    widths.append((plate_w, pw, "price plate"))
    if roomy:
        total = sum(text_w(quick_label(q)) + 12 + 3 for q in QUICK)
        widths.append((total, width - 2 * RAIL, "quick row"))
    lamp_w = min((pw - 24) // 2, 130)
    widths.append((lamp_w * 2 + 6, pw, "lamps"))
    leave_w = min(58, pw // 4)
    reset_w = min(96, pw // 3)
    agree_w = min(pw - leave_w - reset_w - 8, 130)
    widths.append((agree_w + reset_w + leave_w + 8, pw, "button row"))
    widths.append((0 if agree_w > 20 else 1, 0, "agree button too narrow"))

    return rows, widths, content_bottom, clock_y, pw


def main():
    bad = []
    checks = 0
    # every plausible GUI-space window: Minecraft floors at 320x240 and the
    # widest common ultrawide at gui scale 1 is about 3840
    for width in list(range(320, 800, 7)) + [854, 1024, 1280, 1920, 2560, 3840]:
        for height in list(range(240, 620, 5)) + [720, 1080, 1440]:
            for best_of in (1, 3):
                for card_wager in (False, True):
                    for price in (0, 250, 1000000):
                        checks += 1
                        rows, widths, content_bottom, clock_y, pw = layout(
                            width, height, best_of, card_wager, price)
                        where = f"{width}x{height} bo{best_of} card={card_wager} p={price}"

                        if content_bottom > clock_y:
                            bad.append(f"{where}: content runs into the clock "
                                       f"({content_bottom} > {clock_y})")
                        for top, bottom, nm in rows:
                            if top < RAIL:
                                bad.append(f"{where}: {nm} crosses the top rail ({top} < {RAIL})")
                            if bottom > height - RAIL:
                                bad.append(f"{where}: {nm} crosses the bottom rail "
                                           f"({bottom} > {height - RAIL})")
                        ordered = [r for r in rows if r[2] not in ("clock", "buttons")]
                        for i in range(len(ordered) - 1):
                            if ordered[i][1] > ordered[i + 1][0]:
                                bad.append(f"{where}: {ordered[i][2]} overlaps "
                                           f"{ordered[i + 1][2]}")
                        for got, cap, nm in widths:
                            if got > cap:
                                bad.append(f"{where}: {nm} is {got}px in {cap}px")
                        if pw < 100:
                            bad.append(f"{where}: panel collapsed to {pw}px")

    if bad:
        print(f"FAIL  {len(bad)} layout problems across {checks} windows:")
        # one line per KIND of problem, with the first window that shows it —
        # a real break trips thousands of windows and the list is unreadable
        seen = {}
        for b in bad:
            where, what = b.split(": ", 1)
            seen.setdefault(what, [0, where])[0] += 1
        for what, (count, first) in sorted(seen.items(), key=lambda kv: -kv[1][0]):
            print(f"   {what}   (first at {first}, {count} windows)")
        sys.exit(1)
    print(f"PASS  {checks} window/mode combinations: nothing crosses a rail, "
          f"overlaps its neighbour, or outgrows the panel.")


if __name__ == "__main__":
    main()
