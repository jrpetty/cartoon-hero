#!/usr/bin/env python3
"""
The Profile page must have room for every statistic it promises.

drawProfileColumn draws every row it is given, with no scrollbar and no "3
more" note, because this check guarantees the rows fit. That is a deliberate
trade: the awards pages took the other option once -- skip the row that would
not fit -- and a skipped row is invisible AND unclickable, which looks exactly
like a row that does not exist. Overflowing onto the footer would at least be
visible. Neither is acceptable, so the fit is checked instead of handled.

There are twenty-five things to say and a 320x240 window leaves eight lines in
a leaf, which is why the page is two spreads of two columns rather than one of
everything. Only the head-to-head list may give way, and it does so on purpose:
the screen drops rivals one at a time until the column fits. The emblem strip
is drawn only once the rows are known to fit, so decoration can never be what
pushes a statistic off the page.

The row counts are COUNTED FROM THE SOURCE rather than written down here, and
the book's panel solve is imported from checkawardpages rather than copied, so
adding a statistic or changing the book's geometry is covered automatically
instead of when somebody remembers to update this file.

Run: python3 tools/checkprofilepage.py [src_root]
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import checkawardpages as book

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/java"
BOOK = os.path.join(ROOT, "com/jrpetty/mobtrumps/client/CollectionBookScreen.java")


def method_body(src, name):
    """The text of a method, from its opening brace to its matching close."""
    m = re.search(r"private\s+List<ProfileRow>\s+" + name + r"\s*\([^)]*\)\s*\{", src)
    if not m:
        raise SystemExit(f"FAIL  cannot find {name}(...) in the book screen")
    i = m.end()
    depth = 1
    while i < len(src) and depth:
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
        i += 1
    return src[m.end():i]


def fixed_rows(body):
    """
    Rows the column ALWAYS draws.

    A rows.add(...) nested inside an `if` or a `for` is conditional, so it is
    counted separately -- those are the head-to-head entries and the "No duels
    yet" placeholder, which stand in for each other.
    """
    fixed = conditional = 0
    depth = 0
    i = 0
    while i < len(body):
        c = body[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
        elif body.startswith("rows.add(", i):
            if depth == 0:
                fixed += 1
            else:
                conditional += 1
            i += len("rows.add(")
            continue
        i += 1
    return fixed, conditional


def geometry(src):
    """The page's own numbers, read from the render method."""
    body = src[src.index("private void renderProfilePage("):]
    body = body[:body.index("\n    /** Emblem, name and the one-line summary")]

    def num(pattern, what):
        m = re.search(pattern, body)
        if not m:
            raise SystemExit(f"FAIL  cannot read {what} from renderProfilePage")
        return int(m.group(1))
    return num(r"int\s+rowH\s*=\s*(\d+)", "the row height")


def main():
    if not os.path.exists(BOOK):
        print(f"FAIL  cannot find {BOOK}")
        sys.exit(1)
    src = open(BOOK, encoding="utf-8").read()
    book.PLANS, book.BOOK_SCALE_CAP = book.load_plans(BOOK)

    groups = {}
    for name in ("profileRanked", "profileRivals", "profileGames", "profileCollection"):
        groups[name] = fixed_rows(method_body(src, name))
    varying = [n for n, (_, cond) in groups.items() if cond]
    if varying != ["profileRivals"]:
        print(f"FAIL  only the head-to-head list may vary in length, but these do: "
              f"{', '.join(varying) or 'none'}")
        sys.exit(1)

    row_h = geometry(src)
    # Each spread shows two of the groups side by side, so a leaf must hold the
    # taller of its pair. The strip is decoration the page skips when short, so
    # it is deliberately NOT counted here.
    need = max(fixed for fixed, _ in groups.values())
    worst = None
    checks = 0
    for width in list(range(320, 900, 11)) + [1024, 1280, 1920, 2560]:
        for height in list(range(240, 700, 7)) + [720, 1080, 1440]:
            checks += 1
            _, panel_y, _, page_bottom = book.solve_panel(width, height)
            # drawPageHeading: +6, then +10, then returns y + 12
            y = book.lg(panel_y + 48) + 6 + 10 + 12
            capacity = (book.lg(page_bottom) - y) // row_h
            if capacity < need and (worst is None or capacity < worst[0]):
                worst = (capacity, width, height)

    if worst:
        capacity, w, h = worst
        tallest = max(groups.items(), key=lambda kv: kv[1][0])
        print("FAIL  the Profile page cannot fit its own statistics:")
        print(f"   {tallest[0]} needs {need} rows, the leaf holds {capacity} at {w}x{h}")
        print("   the rows past that would spill over the footer -- see this "
              "file's header for why neither dropping nor spilling is acceptable")
        sys.exit(1)
    print(f"PASS  {checks} window sizes: every profile group fits its leaf "
          f"(tallest {need} rows at {row_h}px); only the head-to-head list "
          f"gives way, and the emblem strip only draws once the rows fit.")


if __name__ == "__main__":
    main()
