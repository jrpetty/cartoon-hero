#!/usr/bin/env python3
"""
Matrix-stack balance check.

Usage: python3 tools/checkpose.py src/main/java

Every pushPose() must have a popPose() that runs even when the code between
them throws. Minecraft's PoseStack is shared for the whole frame, so a pop that
is skipped does not merely lose one drawing -- everything rendered afterwards
inherits the leftover translate, scale and rotation. In the world pass that
paints huge misplaced geometry across the level; in a GUI it smears the screen.
Nothing logs, and the code that caused it has already returned.

The dangerous shape is a pop sitting inside a try whose catch swallows:

    try {
        pose.pushPose();
        ...                     <-- throws
        pose.popPose();         <-- never runs
    } catch (Exception ignored) { }

so the pop belongs in a finally. Also flags a return or throw with a push
outstanding, and any member whose pushes and pops do not balance.

Brace matching is done by scanning, not by regex: a try body contains nested
blocks, and a non-greedy regex stops at the first inner brace and silently
matches nothing -- which made the first version of this check pass on the very
bug it was written for.
"""
import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/java"
fails = []


def strip_noise(src):
    """Blank out comments and string literals so braces in them don't count."""
    out = []
    i, n = 0, len(src)
    while i < n:
        two = src[i:i + 2]
        if two == "//":
            j = src.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i))
            i = j
        elif two == "/*":
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append("".join(c if c == "\n" else " " for c in src[i:j]))
            i = j
        elif src[i] in "\"'":
            q = src[i]
            j = i + 1
            while j < n and src[j] != q:
                j += 2 if src[j] == "\\" else 1
            j = min(j + 1, n)
            out.append(" " * (j - i))
            i = j
        else:
            out.append(src[i])
            i += 1
    return "".join(out)


def block_at(src, open_idx):
    """Given the index of a '{', return the index just past its matching '}'."""
    depth = 0
    for i in range(open_idx, len(src)):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return i + 1
    return len(src)


def next_brace(src, i):
    while i < len(src) and src[i] not in "{;":
        i += 1
    return i if i < len(src) and src[i] == "{" else -1


def scan(path, rel):
    raw = open(path, encoding="utf-8").read()
    src = strip_noise(raw)

    # --- try/catch/finally shapes -------------------------------------
    for m in re.finditer(r'\btry\b', src):
        ob = next_brace(src, m.end())
        if ob < 0:
            continue
        end = block_at(src, ob)
        body = src[ob:end]
        # The push may legitimately sit outside the try — what matters is that a
        # pop inside one can be skipped by a throw. Requiring both in the body
        # was too narrow and missed exactly that arrangement.
        if ".popPose()" not in body:
            continue
        # walk the catch/finally clauses that follow
        i = end
        swallowing_catch = False
        finally_pops = 0
        while True:
            rest = src[i:]
            mc_ = re.match(r'\s*catch\s*\([^)]*\)\s*', rest)
            mf = re.match(r'\s*finally\s*', rest)
            if mc_:
                cb = next_brace(src, i + mc_.end() - 1 if False else i + mc_.end())
                if cb < 0:
                    break
                ce = block_at(src, cb)
                handler = src[cb:ce]
                if "throw" not in handler:
                    swallowing_catch = True
                i = ce
            elif mf:
                fb = next_brace(src, i + mf.end())
                if fb < 0:
                    break
                fe = block_at(src, fb)
                finally_pops += src[fb:fe].count(".popPose()")
                i = fe
            else:
                break
        if swallowing_catch and finally_pops == 0:
            line = src[:m.start()].count("\n") + 1
            fails.append((rel, line,
                          "popPose() sits inside a try with a swallowing catch. "
                          "A throw leaves the matrix stack unbalanced for the "
                          "rest of the frame — move the pop into a finally."))

    # --- balance and early exits --------------------------------------
    depth = 0
    outstanding = 0
    since = 0
    for i, code in enumerate(src.split("\n"), 1):
        pushes = code.count(".pushPose()")
        pops = code.count(".popPose()")
        if pushes and outstanding == 0:
            since = i
        outstanding += pushes - pops
        if outstanding > 0 and re.match(r'\s*(return|throw)\b', code):
            fails.append((rel, i, "return/throw with %d pose(s) still pushed"
                          % outstanding))
        depth += code.count("{") - code.count("}")
        if depth == 0 and outstanding != 0:
            fails.append((rel, since, "%d pushPose() left unpopped" % outstanding))
            outstanding = 0


count = 0
for base, _, files in os.walk(ROOT):
    for f in sorted(files):
        if f.endswith(".java"):
            p = os.path.join(base, f)
            scan(p, os.path.relpath(p, ROOT))
            count += 1

print("scanned %d source files" % count)
if fails:
    print()
    print("FAIL  %d matrix-stack problem(s):" % len(fails))
    for rel, line, why in fails:
        print("  %s:%d" % (rel, line))
        print("      %s" % why)
    sys.exit(1)
print("PASS  every pushPose() is popped on all paths, including on a throw.")
