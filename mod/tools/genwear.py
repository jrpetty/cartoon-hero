#!/usr/bin/env python3
"""Build the in-hand wear look for mob cards.

A card in your hand should show how battered it is. Rather than repainting
81 mobs x 5 conditions of card art, each mob gets five extra `item/generated`
models that keep the same layer0 and stack a shared wear overlay on layer1.
That is five new 16x16 textures and a pile of ~150-byte JSONs, instead of 405
new images.

Overlay stages match CardCondition.stage():
  1 Excellent  corners rubbed, one faint crease
  2 Good       creases across the face, edge dirt
  3 Worn       yellowing, several creases, chipped corners
  4 Damaged    nicks bitten out of the edges, staining
  5 Ruined     a tear through the card, heavy grime
"""
import json
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "mobtrumps")
TEX = os.path.join(ASSETS, "textures", "item", "wear")
MODELS = os.path.join(ASSETS, "models", "item", "card")
os.makedirs(TEX, exist_ok=True)

S = 16
STAGES = 5

# the card art occupies roughly this box inside the 16x16 icon
CARD = (2, 2, 13, 13)


def overlay(stage):
    """One 16x16 RGBA wear layer. Deliberately heavy-handed: at item scale a
    subtle scuff is invisible, so each stage has to be legible on its own."""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = CARD
    grime = (74, 56, 28)

    # 1+ : corners rub back to card stock
    rub = 1 + stage // 2
    for cx, cy in ((x0, y0), (x1 - rub + 1, y0), (x0, y1 - rub + 1), (x1 - rub + 1, y1 - rub + 1)):
        d.rectangle([cx, cy, cx + rub - 1, cy + rub - 1],
                    fill=(196, 176, 132, 150 + stage * 20))

    # 2+ : a hard crease across the face, another at 4
    if stage >= 2:
        cy = y0 + (y1 - y0) // 2
        d.line([(x0, cy), (x1, cy)], fill=(252, 246, 230, 190))
        d.line([(x0, cy + 1), (x1, cy + 1)], fill=(52, 38, 18, 200))
    if stage >= 4:
        cy = y0 + (y1 - y0) // 4
        d.line([(x0, cy), (x1, cy)], fill=(252, 246, 230, 170))
        d.line([(x0, cy + 1), (x1, cy + 1)], fill=(52, 38, 18, 180))

    # 3+ : the card yellows and the edges dirty
    if stage >= 3:
        d.rectangle([x0, y0, x1, y1], fill=(186, 158, 96, 40 + (stage - 2) * 34))
        d.rectangle([x0, y0, x1, y1], outline=grime + (150 + stage * 18,))

    # 4+ : chunks bitten out of the edge, and a stain
    if stage >= 4:
        for nx, ny in ((x0 + 3, y0), (x1 - 3, y0), (x0 + 5, y1), (x1, y0 + 5), (x0, y1 - 4)):
            d.rectangle([nx, ny, nx + 1, ny + 1], fill=(16, 10, 5, 235))
        d.ellipse([x0 + 4, y0 + 6, x0 + 8, y0 + 10], fill=(96, 66, 24, 165))

    # 5 : torn, and filthy with it
    if stage >= 5:
        # grime first, so it does not mute the stage-4 damage underneath
        d.rectangle([x0, y0, x1, y1], fill=(48, 34, 16, 55))
        d.ellipse([x0 + 1, y1 - 5, x0 + 5, y1 - 1], fill=(78, 54, 20, 175))
        for i in range(9):
            tx = x1 - 1 - i
            ty = y0 + 1 + i
            d.point((tx, ty), fill=(250, 246, 236, 245))
            d.point((tx - 1, ty), fill=(20, 12, 6, 245))
        # a corner missing outright
        for nx, ny in ((x1, y0), (x1 - 1, y0), (x1, y0 + 1), (x0, y1), (x0, y1 - 1)):
            d.point((nx, ny), fill=(10, 7, 3, 255))
    return img


for stage in range(1, STAGES + 1):
    overlay(stage).save(os.path.join(TEX, "stage_%d.png" % stage))
    print("wrote textures/item/wear/stage_%d.png" % stage)

# --- per-mob worn models, and the master override table ---------------------
mobs = sorted(f[:-5] for f in os.listdir(MODELS)
              if f.endswith(".json") and "_w" not in f)
print("mobs:", len(mobs))

for mob in mobs:
    for stage in range(1, STAGES + 1):
        model = {
            "parent": "minecraft:item/generated",
            "textures": {
                "layer0": "mobtrumps:item/card/" + mob,
                "layer1": "mobtrumps:item/wear/stage_%d" % stage,
            },
        }
        with open(os.path.join(MODELS, "%s_w%d.json" % (mob, stage)), "w") as fh:
            json.dump(model, fh, separators=(", ", ": "))

# The master model. Vanilla picks the LAST override whose every predicate is
# <= the item's property value, so entries must run mob_index ascending and,
# within a mob, wear ascending.
# Read indices only from the BASE entries -- rerunning must not treat the
# worn models this script already wrote as if they were extra mobs.
order = json.load(open(os.path.join(ASSETS, "models", "item", "mob_card.json")))
base = set(mobs)
index_of = {}
for entry in order["overrides"]:
    name = entry["model"].rsplit("/", 1)[1]
    if name in base:
        index_of[name] = entry["predicate"]["mobtrumps:mob_index"]
assert len(index_of) == len(mobs), "lost a mob: %d of %d" % (len(index_of), len(mobs))

overrides = []
for mob in sorted(index_of, key=lambda m: index_of[m]):
    idx = index_of[mob]
    overrides.append({"predicate": {"mobtrumps:mob_index": idx, "mobtrumps:wear": 0},
                      "model": "mobtrumps:item/card/" + mob})
    for stage in range(1, STAGES + 1):
        overrides.append({"predicate": {"mobtrumps:mob_index": idx, "mobtrumps:wear": stage},
                          "model": "mobtrumps:item/card/%s_w%d" % (mob, stage)})

master = {
    "parent": "minecraft:item/generated",
    "textures": {"layer0": "mobtrumps:item/mob_card"},
    "overrides": overrides,
}
with open(os.path.join(ASSETS, "models", "item", "mob_card.json"), "w") as fh:
    json.dump(master, fh, indent=2)
print("wrote mob_card.json with", len(overrides), "overrides")
