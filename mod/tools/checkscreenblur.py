#!/usr/bin/env python3
"""
A screen must draw ON the vanilla background, not under it.

Screen.render draws the background itself -- including the blur pass that
frosts the world behind the menu. So the order inside an overriding render()
decides what gets blurred:

    super.render(...)      // background + blur
    ...your drawing...     // sits crisply on top          <- right

    ...your drawing...
    super.render(...)      // background + blur, AGAIN, over your menu
                                                            <- wrong

Getting it backwards does not fail to compile, does not throw, and does not
look wrong in code review. It looks like somebody smeared vaseline on the
screen, and only at runtime. The Memory table shipped exactly that way: the
board-size menu came out unreadable, text and buttons included, because
super.render sat at the END of both of its paths.

The rule checked here: in any overridden render(), no call to super.render
may come after the screen's first drawing call. renderBackground counts as
the background too, since that is the same pass by another name.

Run: python3 tools/checkscreenblur.py [src_root]
"""
import glob
import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/java"
DRAW = re.compile(r"\bg\.(fill|fillGradient|drawString|drawCenteredString|blit"
                  r"|renderOutline|renderFakeItem|drawWordWrap)\b")


def render_body(src):
    """The body of the overridden render(GuiGraphics...), or None."""
    m = re.search(r"public void render\(\s*GuiGraphics[^)]*\)\s*\{", src)
    if not m:
        return None
    i = m.end()
    depth = 1
    j = i
    while depth and j < len(src):
        if src[j] == "{":
            depth += 1
        elif src[j] == "}":
            depth -= 1
        j += 1
    return src[i:j]


def main():
    files = sorted(glob.glob(os.path.join(ROOT, "com/jrpetty/mobtrumps/client/*Screen.java")))
    if not files:
        print(f"FAIL  found no screens under {ROOT}; has the tree moved?")
        sys.exit(1)

    problems = []
    checked = 0
    for path in files:
        body = render_body(open(path, encoding="utf-8").read())
        if body is None:
            continue
        lines = [l.strip() for l in body.splitlines()]
        first_draw = None
        for n, line in enumerate(lines):
            if line.startswith("//") or line.startswith("*"):
                continue
            if first_draw is None and DRAW.search(line):
                first_draw = n
            if ("super.render(" in line or "renderBackground(" in line) \
                    and first_draw is not None and n > first_draw:
                problems.append(
                    f"{os.path.basename(path)}: the background is drawn at line {n + 1} of "
                    f"render(), after drawing starts at line {first_draw + 1} — "
                    f"everything above it comes out blurred")
                break
        checked += 1

    if problems:
        print("FAIL  screens that blur their own contents:")
        for p in problems:
            print("   " + p)
        sys.exit(1)
    print(f"PASS  {checked} screens: every one draws the vanilla background "
          f"before its own contents, so nothing frosts its own menu.")


if __name__ == "__main__":
    main()
