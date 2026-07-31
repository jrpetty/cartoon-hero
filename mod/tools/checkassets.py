#!/usr/bin/env python3
"""
Asset integrity, checked against the built jar.

Usage: python3 tools/checkassets.py build/libs/mobtrumps-1.2.3.jar [repo-root]

Three failure modes here are silent in-game -- nothing logs, nothing crashes,
you just see the wrong thing, possibly hundreds of cards in:

  1. A model override points at a model that isn't in the jar. The card falls
     back to the base model, so every wear level looks pristine.
  2. A model points at a texture that isn't in the jar. Purple-and-black checks.
  3. The override list is out of order. Vanilla scans overrides in file order
     and keeps the LAST one whose every predicate is <= the item's property
     values, so the list must run mob_index ascending and, within one mob,
     wear ascending. Get it wrong and a worn Blaze renders as some other mob.

(3) has no other way of being caught: tools/genwear.py writes 486 override
entries and a single misordered line would stay invisible until somebody's
card wore down. The predicate ranges are checked against the Java source too,
since a card's index in MobCards.ALL *is* its mob_index -- inserting a mob in
the middle of that list silently repoints every card after it.
"""
import json
import re
import sys
import zipfile

NS = "mobtrumps"


def rl(path):
    ns, _, name = path.partition(":")
    if not name:
        ns, name = "minecraft", ns
    return ns, name


def declared_cards(root):
    """How many mobs the Java source declares, so jar and code can't drift."""
    try:
        with open("%s/src/main/java/com/jrpetty/mobtrumps/game/MobCards.java" % root,
                  encoding="utf-8") as fh:
            # the list is built by repeated `card("id", "Name", ...)` calls in
            # a static block; that call order is the mob_index order
            return len(re.findall(r"^\s*card\(\"", fh.read(), re.MULTILINE))
    except OSError:
        return None


def main(jar_path, root="."):
    fails, notes = [], []
    with zipfile.ZipFile(jar_path) as z:
        entries = set(z.namelist())
        models = sorted(e for e in entries
                        if e.startswith("assets/%s/models/" % NS) and e.endswith(".json"))
        textures = {e for e in entries
                    if e.startswith("assets/%s/textures/" % NS) and e.endswith(".png")}

        def have_model(path):
            ns, name = rl(path)
            return ns != NS or "assets/%s/models/%s.json" % (ns, name) in entries

        def have_texture(path):
            ns, name = rl(path)
            return ns != NS or "assets/%s/textures/%s.png" % (ns, name) in entries

        docs, overrides_seen = {}, 0
        for m in models:
            try:
                docs[m] = json.loads(z.read(m))
            except ValueError as exc:
                fails.append("%s: not valid JSON (%s)" % (m, exc))
                continue
            doc = docs[m]
            if "parent" in doc and not have_model(doc["parent"]):
                fails.append("%s: missing parent %s" % (m, doc["parent"]))
            for key, tex in (doc.get("textures") or {}).items():
                if not tex.startswith("#") and not have_texture(tex):
                    fails.append("%s: missing texture '%s' -> %s" % (m, key, tex))
            for ov in doc.get("overrides") or []:
                overrides_seen += 1
                if not have_model(ov["model"]):
                    fails.append("%s: override -> missing model %s" % (m, ov["model"]))

        notes.append("%d models, %d textures, %d override entries"
                     % (len(models), len(textures), overrides_seen))

        for m, doc in docs.items():
            ovs = doc.get("overrides") or []
            if not ovs:
                continue
            keys = sorted({k for ov in ovs for k in ov["predicate"]})

            def vec(ov):
                return tuple(ov["predicate"].get(k, 0.0) for k in keys)

            drops = [i for i in range(1, len(ovs)) if vec(ovs[i]) < vec(ovs[i - 1])]
            if drops:
                i = drops[0]
                fails.append("%s: override list is not monotonic (%d breaks, first at "
                             "entry %d: %s follows %s). Vanilla takes the LAST match, so "
                             "cards will render as the wrong mob."
                             % (m, len(drops), i, vec(ovs[i]), vec(ovs[i - 1])))
            seen, shadowed = set(), 0
            for ov in ovs:
                v = vec(ov)
                shadowed += v in seen
                seen.add(v)
            if shadowed:
                fails.append("%s: %d duplicate predicate vectors -- those models can "
                             "never be selected" % (m, shadowed))
            if not drops and not shadowed:
                notes.append("%s: %d overrides on %s -- monotonic, nothing shadowed"
                             % (m.split("/")[-1], len(ovs),
                                ", ".join(k.split(":")[-1] for k in keys)))

            mi, wear = "%s:mob_index" % NS, "%s:wear" % NS
            if mi in keys:
                idx = sorted({int(ov["predicate"][mi]) for ov in ovs})
                declared = declared_cards(root)
                if idx != list(range(len(idx))):
                    fails.append("%s: mob_index values are not contiguous from 0" % m)
                elif declared is not None and len(idx) != declared:
                    fails.append("%s: covers %d mobs but MobCards declares %d -- rerun "
                                 "tools/genwear.py" % (m, len(idx), declared))
                else:
                    notes.append("mob_index covers 0..%d%s" % (
                        len(idx) - 1,
                        ", matching MobCards" if declared else ""))
            if wear in keys:
                stages = sorted({int(ov["predicate"][wear]) for ov in ovs})
                if stages != [0, 1, 2, 3, 4, 5]:
                    fails.append("%s: wear stages are %s, but CardCondition.stage "
                                 "returns 0..5" % (m, stages))
                else:
                    notes.append("wear covers stages 0..5, matching CardCondition.stage")

    for n in notes:
        print("  " + n)
    print()
    if fails:
        print("FAIL  %d problem(s):" % len(fails))
        for f in fails[:40]:
            print("  " + f)
        if len(fails) > 40:
            print("  ...and %d more" % (len(fails) - 40))
        return 1
    print("PASS  every model, texture and override reference resolves; every override")
    print("      list is monotonic with nothing shadowed; ranges match the code.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "."))
