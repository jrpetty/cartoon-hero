#!/usr/bin/env python3
"""
A sound needs three things to exist, and nothing checks that they agree.

  ModSounds.java     registers the SoundEvent, so the code can name it
  sounds.json        maps that name to a file, so the client can find it
  sounds/<name>.ogg  is the audio itself

Miss the middle one and the game is simply silent where you expected a sound:
no crash, no missing-texture chequerboard, nothing to notice except a card
that flips without a noise. Miss the third and the client logs a resource
error that nobody reads.

This compares the three against each other. It invents nothing -- each side is
read from the file that defines it, and the check is only that they match.

Sound paths in sounds.json are relative to the sounds/ folder, so
"mobtrumps:card_flip" means sounds/card_flip.ogg. (A first draft of this file
insisted on a "sounds/" prefix and reported all seven entries broken. They
were correct; the rule was invented.)

Subtitles are checked too: a subtitle key with no translation shows the player
the raw key on screen when subtitles are on.

Run: python3 tools/checksoundwiring.py [src_root] [resources_root]
"""
import json
import os
import re
import sys

SRC = sys.argv[1] if len(sys.argv) > 1 else "src/main/java"
RES = sys.argv[2] if len(sys.argv) > 2 else "src/main/resources"

MOD = os.path.join(SRC, "com/jrpetty/mobtrumps/ModSounds.java")
JSON = os.path.join(RES, "assets/mobtrumps/sounds.json")
OGGS = os.path.join(RES, "assets/mobtrumps/sounds")
LANG = os.path.join(RES, "assets/mobtrumps/lang/en_us.json")


def main():
    for path in (MOD, JSON, OGGS):
        if not os.path.exists(path):
            print(f"FAIL  cannot find {path}")
            sys.exit(1)

    registered = set(re.findall(r'register\("([a-z0-9_]+)"\)',
                                open(MOD, encoding="utf-8").read()))
    if not registered:
        print("FAIL  found no registered sounds; has ModSounds.java changed shape?")
        sys.exit(1)
    entries = json.load(open(JSON, encoding="utf-8"))
    oggs = {f for f in os.listdir(OGGS) if f.endswith(".ogg")}
    lang = json.load(open(LANG, encoding="utf-8")) if os.path.exists(LANG) else {}

    problems = []
    for name in sorted(registered - set(entries)):
        problems.append(f"SILENT      '{name}' is registered but has no sounds.json "
                        f"entry, so it plays nothing at all")
    for name in sorted(set(entries) - registered):
        problems.append(f"ORPHAN      sounds.json defines '{name}' but no code "
                        f"registers it")

    referenced = set()
    for key, entry in entries.items():
        for s in entry.get("sounds", []):
            name = s if isinstance(s, str) else s.get("name", "")
            ns, sep, path = name.partition(":")
            if not sep:
                ns, path = "mobtrumps", ns
            if ns != "mobtrumps":
                continue  # a vanilla sound, not ours to ship
            filename = path.split("/")[-1] + ".ogg"
            referenced.add(filename)
            if filename not in oggs:
                problems.append(f"MISSING     '{key}' points at {name!r} but "
                                f"{filename} is not in sounds/")
        subtitle = entry.get("subtitle")
        if subtitle and subtitle not in lang:
            problems.append(f"UNTRANSLATED '{key}' declares subtitle "
                            f"{subtitle!r}, which has no translation, so "
                            f"players with subtitles on see the raw key")

    for f in sorted(oggs - referenced):
        problems.append(f"UNUSED      {f} is shipped but nothing references it")

    if problems:
        print("FAIL  the sound wiring does not line up:")
        for p in problems:
            print("   " + p)
        sys.exit(1)
    print(f"PASS  {len(registered)} sounds: each is registered, mapped in "
          f"sounds.json, backed by an ogg and has a translated subtitle.")


if __name__ == "__main__":
    main()
