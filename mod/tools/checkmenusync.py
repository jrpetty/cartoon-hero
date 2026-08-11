#!/usr/bin/env python3
"""
A screen must not open before the state it reads has been sent.

Opening one of these machines is two packets: a *MenuPayload that tells the
client to show the screen, and a state packet that tells it what to show. Send
only the first and the screen renders whatever the client last heard, which
after a login is nothing at all.

That is not always a cosmetic problem. The printing press greys out its PRINT
button and ignores the click when the fragment count is below the stake — so a
press opened without a fragment sync showed zero and flatly refused to spend
fragments the player was carrying. Bluff, Twenty-One and Guess Who all sent
their state on open; the recycler was the one that did not, and the bug sat
there until somebody tried to print something.

The rule: any method that sends a *MenuPayload must also, in the same method,
send state — either a *SyncPayload directly or via a sync()/send() helper.

Unless the menu payload IS the state. TableMenuPayload carries the seat, the
occupant's name, the mode and whether it is yours: four components, and the
screen is built from them. A payload with no fields, or a single one selecting
which screen to show (RecyclerMenuPayload's mode), cannot be carrying state and
so must be followed by some. That is the distinction the check makes, rather
than a list of names to skip.

Run: python3 tools/checkmenusync.py [src_root]
"""
import glob
import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/java"

MENU = re.compile(r"new\s+(\w*MenuPayload)\s*\(")
# what counts as "and here is the state"
STATE = re.compile(r"new\s+\w*SyncPayload\s*\(|(?<![\w.])(?:sync|send|sendAll|syncWager)\s*\(")


def payload_components(root):
    """How many record components each *MenuPayload declares."""
    out = {}
    for path in glob.glob(os.path.join(root, "**", "*MenuPayload.java"), recursive=True):
        src = open(path, encoding="utf-8").read()
        name = os.path.basename(path)[:-5]
        m = re.search(r"\brecord\s+" + name + r"\s*\(([^)]*)\)", src, re.S)
        body = (m.group(1).strip() if m else "")
        out[name] = 0 if not body else len([p for p in body.split(",") if p.strip()])
    return out


def methods(src):
    """Crude method splitter: good enough, because these are all short static
    methods and we only need the body that contains the menu send."""
    out = []
    for m in re.finditer(r"\n    (?:public|private|protected|static)[^\n;=]*\)\s*\{", src):
        start = m.start()
        depth, i = 0, m.end() - 1
        while i < len(src):
            if src[i] == "{":
                depth += 1
            elif src[i] == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        out.append((src[start:m.end()].strip(), src[start:i + 1]))
    return out


def main():
    components = payload_components(ROOT)
    bad = []
    checked = 0
    for path in sorted(glob.glob(os.path.join(ROOT, "**", "*.java"), recursive=True)):
        src = open(path, encoding="utf-8").read()
        src = re.sub(r"//[^\n]*", "", src)
        src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
        if not MENU.search(src):
            continue
        for sig, body in methods(src):
            menu = MENU.search(body)
            if not menu:
                continue
            checked += 1
            # a payload with two or more components is carrying the state itself
            if components.get(menu.group(1), 0) >= 2:
                continue
            if not STATE.search(body):
                line = src[:src.index(body)].count("\n") + 1
                bad.append(f"{path}:{line}: sends {menu.group(1)} but no state "
                           f"— the screen will open showing whatever the client last heard")

    if bad:
        print("FAIL  a screen is opened without its state:")
        for b in bad:
            print("   " + b)
        sys.exit(1)
    print(f"PASS  {checked} menu-opening methods: every one sends state as well.")


if __name__ == "__main__":
    main()
