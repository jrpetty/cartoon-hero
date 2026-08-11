#!/usr/bin/env python3
"""
Every award must be obtainable.

AchievementManager.metric() resolves an award's metric key to the player's
current value and ends:

    default -> 0;

so a key it does not handle is not an error. It is zero, forever, for everyone.
The award appears in the book with a progress bar stuck at nothing, and no log
line, no crash and no warning ever says why. A single mistyped character in a
catalogue of sixty-five is enough.

This checks the three ways an award can be born dead:

  UNRESOLVED  its metric is not handled by metric()
  UNREACHABLE its target is zero or negative, so it unlocks instantly, or the
              catalogue lists the same id twice and one silently shadows
  MALFORMED   the add(...) call does not have the shape everything else does

The arguments are split by walking the call and respecting nesting, quoting and
escapes, NOT by matching "[^"]*" — a regex like that cannot cross a description
built with `+` from two literals, and there is already one of those in the
catalogue. An earlier version of this check missed exactly that award and
reported a clean pass over sixty-four of the sixty-five.

Run: python3 tools/checkawards.py [src_root]
"""
import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/java"
CATALOGUE = os.path.join(ROOT, "com/jrpetty/mobtrumps/game/Achievements.java")
MANAGER = os.path.join(ROOT, "com/jrpetty/mobtrumps/AchievementManager.java")


def strip_comments(src):
    """Blank the comments but keep every newline, so reported line numbers are
    the line numbers in the file the reader will open."""
    src = re.sub(r"//[^\n]*", "", src)
    return re.sub(r"/\*.*?\*/",
                  lambda m: "\n" * m.group(0).count("\n"), src, flags=re.S)


def split_args(text, start):
    """Arguments of a call whose '(' has just been consumed, honouring nesting,
    string literals and escapes."""
    args, depth, cur, i = [], 0, [], start
    in_str = False
    while i < len(text):
        c = text[i]
        if in_str:
            cur.append(c)
            if c == "\\":
                if i + 1 < len(text):
                    cur.append(text[i + 1])
                    i += 2
                    continue
            elif c == '"':
                in_str = False
            i += 1
            continue
        if c == '"':
            in_str = True
            cur.append(c)
        elif c in "([":
            depth += 1
            cur.append(c)
        elif c in ")]":
            if depth == 0:
                args.append("".join(cur).strip())
                return args, i
            depth -= 1
            cur.append(c)
        elif c == "," and depth == 0:
            args.append("".join(cur).strip())
            cur = []
        else:
            cur.append(c)
        i += 1
    return args, i


def literal(arg):
    """The value of a string argument, including one built with `+`."""
    parts = re.findall(r'"((?:\\.|[^"\\])*)"', arg)
    return "".join(parts) if parts else None


def main():
    for path in (CATALOGUE, MANAGER):
        if not os.path.exists(path):
            print(f"FAIL  cannot find {path}")
            sys.exit(1)
    cat = strip_comments(open(CATALOGUE, encoding="utf-8").read())
    mgr = strip_comments(open(MANAGER, encoding="utf-8").read())
    handled = set(re.findall(r'case\s+"([a-z0-9_]+)"\s*->', mgr))

    # add(...) also appears once as its own declaration. That is the only
    # occurrence allowed not to be a call, and it is found by name rather than
    # skipped by shape -- "the first one is the declaration" would quietly
    # swallow a genuinely malformed award if the catalogue were ever reordered.
    decl = re.search(r"void\s+add\s*\(", cat)
    if not decl:
        print("FAIL  cannot find the add(...) declaration; has Achievements.java "
              "changed shape?")
        sys.exit(1)
    decl_at = decl.end()

    problems = []
    seen = {}
    count = 0
    for m in re.finditer(r"(?<![\w.])add\s*\(", cat):
        if m.end() == decl_at:
            continue
        args, _ = split_args(cat, m.end())
        line = cat[:m.start()].count("\n") + 1
        if len(args) < 6:
            problems.append(f"MALFORMED  line {line}: add(...) has {len(args)} arguments")
            continue
        count += 1
        aid = literal(args[0])
        metric = literal(args[4])
        target = args[5].strip()
        if aid is None or metric is None:
            problems.append(f"MALFORMED  line {line}: id or metric is not a literal")
            continue
        if aid in seen:
            problems.append(f"UNREACHABLE {aid}: already defined at line {seen[aid]}")
        seen[aid] = line
        if metric not in handled:
            problems.append(f"UNRESOLVED  {aid}: metric \"{metric}\" is not handled by "
                            f"metric(), so it is zero forever and the award "
                            f"can never unlock")
        if not re.fullmatch(r"-?\d+", target) or int(target) <= 0:
            problems.append(f"UNREACHABLE {aid}: target is {target!r}")
        for item in re.findall(r'new\s+Reward\s*\(\s*"([^"]*)"', ",".join(args[6:])):
            # Only the SHAPE is checked. Whether minecraft:foo exists needs the
            # real registry, which is not here -- and a hand-written list of
            # vanilla item ids would be invented data pretending to be a check.
            if not re.fullmatch(r"[a-z0-9/._-]+", item):
                problems.append(f"UNPAYABLE   {aid}: reward item {item!r} is not a "
                                f"valid resource path, so it can never resolve")

    # Most rewards are written through helpers -- iron(20), diamond(4) -- whose
    # item ids sit in the helper body, not in the add(...) call, so the sweep
    # above never sees them. Check every literal in the file.
    items = set(re.findall(r'new\s+Reward\s*\(\s*"([^"]*)"', cat))
    for item in sorted(items):
        if not re.fullmatch(r"[a-z0-9/._-]+", item):
            problems.append(f"UNPAYABLE   reward item {item!r} is not a valid "
                            f"resource path, so it can never resolve")

    if problems:
        print("FAIL  awards that can never be earned:")
        for p in problems:
            print("   " + p)
        sys.exit(1)
    print(f"PASS  {count} awards: every metric resolves, every id is unique, "
          f"every target is a positive number and all {len(items)} reward item "
          f"ids are well formed.")


if __name__ == "__main__":
    main()
