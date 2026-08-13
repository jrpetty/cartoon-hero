#!/usr/bin/env python3
"""
Draw the Memory table from the layout the game actually computes.

These are NOT screenshots. Minecraft is not running here and cannot be. What
is real is the geometry and the board: tools/previewmemory.java emits the
output of MemoryLayout.solve and a board dealt and played through Memory's own
rules, and this only paints it. So the grid position, the card size at each
window size, and which tiles are face down, face up or taken are exactly what
the mod does. The card ART is an approximation -- the real CardRenderer draws a
framed portrait with a live 3D mob in it, which cannot be reproduced outside
the game.

Read them as a faithful diagram of the layout, not as a photograph of the
result.

Run: python3 tools/previewmemory.py <scenes.json> <out_dir>
"""
import json
import sys

from PIL import Image, ImageDraw, ImageFont

# Transcribed from CardRenderer: the same constants the game draws with.
KRAFT = (154, 123, 84)
KRAFT_DARK = (95, 74, 50)
KRAFT_BACK = (122, 95, 62)
FACE = (250, 246, 236)
INK = (53, 40, 26)
TABLE = (28, 42, 30)
BORDER_IVORY = (242, 238, 227)
PIN_GOLD = (217, 180, 91)
ROW_BLUE = (207, 233, 246)
ROW_GREEN = (216, 238, 205)
ROW_GOLD = (243, 226, 167)
LABEL_BLUE = (28, 75, 107)
LABEL_GREEN = (44, 94, 46)
PORTRAIT_TOP = (207, 228, 242)
PORTRAIT_BOTTOM = (232, 242, 217)
FACT_BG = (237, 227, 206)

CARD_W, CARD_H = 170, 236
STAT_TOP, ROW_H = 121, 13

# the outer ring the tier owns, and the print-friendly ink of the tier line
TIER_BAND = {"COMMON": (90, 95, 102), "UNCOMMON": (46, 125, 50), "RARE": (21, 104, 140),
             "EPIC": (110, 49, 168), "LEGENDARY": (154, 107, 0)}
TIER_PRINT = {"COMMON": (107, 107, 107), "UNCOMMON": (61, 139, 61), "RARE": (28, 127, 168),
              "EPIC": (135, 70, 201), "LEGENDARY": (166, 124, 0)}

ZOOM = 3   # everything below is in game pixels, then scaled up to be readable


def font(size):
    for path in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


def rect(d, x, y, w, h, fill=None, outline=None, width=1):
    d.rectangle([x * ZOOM, y * ZOOM, (x + w) * ZOOM - 1, (y + h) * ZOOM - 1],
                fill=fill, outline=outline, width=width)


def text(d, x, y, s, fill, size=8, anchor="la"):
    d.text((x * ZOOM, y * ZOOM), s, font=font(int(size * ZOOM * 0.9)), fill=fill, anchor=anchor)


def draw_back(d, x, y, w, h):
    """The mod's card back: kraft, a lattice, and a medallion."""
    rect(d, x, y, w, h, fill=KRAFT_DARK)
    rect(d, x + 1, y + 1, w - 2, h - 2, fill=KRAFT_BACK)
    step = max(3, w // 5)
    for i in range(-h, w + h, step):
        d.line([((x + i) * ZOOM, y * ZOOM), ((x + i + h) * ZOOM, (y + h) * ZOOM)],
               fill=(104, 81, 53), width=max(1, ZOOM // 2))
    cx, cy = x + w / 2, y + h / 2
    r = min(w, h) * 0.22
    d.ellipse([(cx - r) * ZOOM, (cy - r) * ZOOM, (cx + r) * ZOOM, (cy + r) * ZOOM],
              fill=KRAFT, outline=KRAFT_DARK, width=max(1, ZOOM // 2))
    d.polygon([(cx * ZOOM, (cy - r * 0.55) * ZOOM), ((cx + r * 0.55) * ZOOM, cy * ZOOM),
               (cx * ZOOM, (cy + r * 0.55) * ZOOM), ((cx - r * 0.55) * ZOOM, cy * ZOOM)],
              fill=KRAFT_DARK)



# --- mob portraits -----------------------------------------------------------
# Minecraft mob faces ARE 8x8 textures, so each portrait here is an 8x8 pixel
# grid in that same idiom, drawn where the live 3D mob poses in the real game.
# '.' is transparent and lets the portrait backdrop show through. These are
# hand-made renditions of the vanilla faces, not the game's own textures.

def _fish(body, stripe, eye):
    """Side-on fish, facing right; used by the fish that have no 'face'."""
    pal = {"o": body, "w": stripe, "k": eye}
    rows = ["........",
            "o..ooo..",
            "ooowwoo.",
            "ooowwoko",
            "ooowwoo.",
            "o..ooo..",
            "........",
            "........"]
    return pal, rows


MOB_FACES = {
    "pig": ({"p": (229, 153, 150), "d": (198, 110, 110), "n": (86, 40, 42),
             "w": (240, 240, 240), "k": (30, 30, 30)},
            ["pppppppp", "pppppppp", "wkppppkw", "pppppppp",
             "pddddddp", "pdnddndp", "pddddddp", "pppppppp"]),
    "enderman": ({"k": (18, 16, 20), "m": (208, 78, 250), "w": (236, 190, 255)},
                 ["kkkkkkkk", "kkkkkkkk", "kkkkkkkk", "mwmkkmwm",
                  "kkkkkkkk", "kkkkkkkk", "kkkkkkkk", "kkkkkkkk"]),
    "warden": ({"t": (16, 62, 66), "c": (92, 225, 210), "k": (8, 30, 32)},
               ["tttttttt", "tttttttt", "tcttttct", "tcttttct",
                "tttttttt", "ttkkkktt", "tttttttt", "tttttttt"]),
    "panda": ({"w": (235, 235, 235), "k": (44, 44, 44)},
              ["kwwwwwwk", "wwwwwwww", "wkkwwkkw", "wkkwwkkw",
               "wwwwwwww", "wwwkkwww", "wwwwwwww", "wwwwwwww"]),
    "mule": ({"b": (94, 66, 46), "k": (40, 28, 20), "w": (235, 235, 235),
              "m": (168, 132, 96), "n": (60, 42, 30)},
             ["bkbbbbkb", "bbbbbbbb", "bwkbbkwb", "bbbbbbbb",
              "bmmmmmmb", "bmnmmnmb", "bmmmmmmb", "bbbbbbbb"]),
    "tropical_fish": _fish((235, 122, 42), (245, 245, 245), (20, 20, 20)),
    "cod": _fish((150, 122, 92), (196, 176, 148), (20, 20, 20)),
    "fox": ({"o": (219, 122, 44), "k": (35, 30, 26), "w": (242, 238, 230)},
            ["ko....ok", "oooooooo", "okooooko", "oooooooo",
             "owwwwwwo", "wwwkkwww", "wwwwwwww", "........"]),
    "goat": ({"c": (206, 199, 184), "h": (148, 138, 118), "k": (36, 34, 30),
              "p": (226, 220, 206)},
             ["hhcccchh", "cccccccc", "ckcccckc", "cccccccc",
              "cccccccc", "ccppppcc", "ccpkkpcc", "cccccccc"]),
    "vex": ({"v": (134, 152, 184), "d": (92, 106, 132), "k": (28, 32, 44)},
            ["vvvvvvvv", "vvvvvvvv", "vkvvvvkv", "vvvvvvvv",
             "vvddddvv", "vvvvvvvv", "vvvvvvvv", "vvvvvvvv"]),
    "bogged": ({"g": (108, 140, 74), "s": (170, 180, 142), "k": (38, 44, 32)},
               ["gggggggg", "ssssssss", "skssssks", "ssssssss",
                "ssssssss", "sksksksk", "ssssssss", "ssssssss"]),
    "strider": ({"r": (180, 58, 58), "d": (122, 32, 34), "k": (30, 20, 20),
                 "e": (222, 218, 214)},
                ["rrrrrrrr", "ekrrrrke", "rrrrrrrr", "dddddddd",
                 "rrrrrrrr", "dddddddd", "rrrrrrrr", "rrrrrrrr"]),
}


def draw_mob_face(c, mob_id, band):
    """The 8x8 portrait, centred in the well; a plain head if the mob has no grid."""
    well_x0, well_y0, well_x1, well_y1 = 12, 38, CARD_W - 12, 116
    face = MOB_FACES.get(mob_id)
    px = min((well_x1 - well_x0 - 10) // 8, (well_y1 - well_y0 - 10) // 8)
    fx = (well_x0 + well_x1 - px * 8) // 2
    fy = (well_y0 + well_y1 - px * 8) // 2
    if face is None:
        rect(c, fx, fy, px * 8, px * 8, fill=band, outline=KRAFT_DARK, width=max(1, ZOOM))
        rect(c, fx + px * 2, fy + px * 2, px, px, fill=(30, 30, 30))
        rect(c, fx + px * 5, fy + px * 2, px, px, fill=(30, 30, 30))
        return
    pal, rows = face
    # a one-pixel drop shadow behind every solid pixel, so a white panda still
    # reads against the pale backdrop without boxing the fish into a frame
    off = max(1, px // 3)
    for ry, row in enumerate(rows):
        for rx, ch in enumerate(row):
            if ch != ".":
                rect(c, fx + rx * px + off, fy + ry * px + off, px, px, fill=(70, 78, 66))
    for ry, row in enumerate(rows):
        for rx, ch in enumerate(row):
            if ch != ".":
                rect(c, fx + rx * px, fy + ry * px, px, px, fill=pal[ch])


def draw_face(img, x, y, w, h, tile, dim=False):
    """
    The real card, transcribed from CardRenderer.renderCard.

    Drawn at the card's true 170x236 and then scaled down to the tile, which is
    exactly what the game does (it draws at 170x236 inside a pose scaled to
    fit). So the proportions, the tier ring, the stat table and the fact strip
    are the card's own, not a sketch of one.

    Two things genuinely cannot be reproduced here: the live 3D mob that poses
    in the portrait well, and CardBackground's per-category scene behind it.
    Those are drawn as the portrait gradient with the mob's silhouette.
    """
    card = Image.new("RGB", (CARD_W * ZOOM, CARD_H * ZOOM), FACE)
    c = ImageDraw.Draw(card)
    c._image = card
    band = TIER_BAND.get(tile["tier"], (90, 95, 102))

    # frame: the tier owns the outer ring, ivory interior, gold pinline
    rect(c, 0, 0, CARD_W, CARD_H, fill=band)
    rect(c, 2, 2, CARD_W - 4, CARD_H - 4, fill=BORDER_IVORY)
    rect(c, 4, 4, CARD_W - 8, CARD_H - 8, outline=PIN_GOLD, width=max(1, ZOOM))
    rect(c, 6, 6, CARD_W - 12, CARD_H - 12, fill=FACE)

    # name at 1.5x, centred, then the starred tier line at y=27
    text(c, CARD_W / 2, 11, tile["name"], INK, size=12, anchor="ma")
    text(c, CARD_W / 2, 27, f"\u2605 {tile['tier'].capitalize()} \u2605",
         TIER_PRINT.get(tile["tier"], INK), size=8, anchor="ma")

    # portrait well: the real one holds a live mob over a category scene
    for i in range(38, 116):
        t = (i - 38) / 78
        col = tuple(int(PORTRAIT_TOP[k] + (PORTRAIT_BOTTOM[k] - PORTRAIT_TOP[k]) * t)
                    for k in range(3))
        c.rectangle([12 * ZOOM, i * ZOOM, (CARD_W - 12) * ZOOM, (i + 1) * ZOOM], fill=col)
    rect(c, 11, 37, CARD_W - 22, 81, outline=KRAFT_DARK, width=max(1, ZOOM))
    draw_mob_face(c, tile["face"], band)

    # stat table: five rows alternating blue/green, then the gold rating row
    rows = [("HP", tile["hp"], False), ("ATK", tile["atk"], False), ("SIZE", tile["size"], False),
            ("SPD", tile["spd"], False), ("FARM", tile["farm"], False),
            ("RARE", tile["rar"], True)]
    row_y = STAT_TOP
    for i, (label, value, lower) in enumerate(rows):
        blue = i % 2 == 0
        rect(c, 12, row_y, CARD_W - 24, ROW_H - 1, fill=ROW_BLUE if blue else ROW_GREEN)
        text(c, 16, row_y + 3, label, LABEL_BLUE if blue else LABEL_GREEN, size=8)
        if lower:   # the caret marking the one stat where lower wins
            text(c, 16 + 26, row_y + 3, "\u25be", LABEL_BLUE if blue else LABEL_GREEN, size=8)
        text(c, CARD_W - 16, row_y + 3, str(value), INK, size=8, anchor="ra")
        row_y += ROW_H
    rect(c, 12, row_y, CARD_W - 24, ROW_H - 1, fill=ROW_GOLD)
    text(c, 16, row_y + 3, "MOB RATING", INK, size=8)
    text(c, CARD_W - 16, row_y + 3, str(tile["total"]), INK, size=8, anchor="ra")
    row_y += ROW_H

    # fact strip: category and the card's number in the set
    rect(c, 12, row_y + 3, CARD_W - 24, CARD_H - 9 - row_y - 3, fill=FACT_BG)
    rect(c, 11, row_y + 2, CARD_W - 22, CARD_H - 9 - row_y - 1, outline=KRAFT_DARK,
         width=max(1, ZOOM))
    text(c, CARD_W / 2, row_y + 5, f"{tile['cat']}  \u00b7  {tile['no']} / {tile['of']}",
         KRAFT_DARK, size=7, anchor="ma")

    if tile.get("foil"):
        sheen = Image.new("RGBA", card.size, (0, 0, 0, 0))
        sd = ImageDraw.Draw(sheen)
        for by in range(8, CARD_H - 8, 3):
            hue = (by / CARD_H) % 1.0
            sd.rectangle([8 * ZOOM, by * ZOOM, (CARD_W - 8) * ZOOM, (by + 2) * ZOOM],
                         fill=hsv(hue, 0.55, 1.0) + (30,))
        card = Image.alpha_composite(card.convert("RGBA"), sheen).convert("RGB")

    card = card.resize((max(1, w * ZOOM), max(1, h * ZOOM)), Image.LANCZOS)
    if dim:
        card = Image.blend(card, Image.new("RGB", card.size, (32, 24, 8)), 0.58)
    img.paste(card, (x * ZOOM, y * ZOOM))


def hsv(h, s, v):
    import colorsys
    r, g, b = colorsys.hsv_to_rgb(h, s, v)
    return (int(r * 255), int(g * 255), int(b * 255))


def caption_for(scene, mine=True, them=""):
    """The footer line the screen would actually show for this board state."""
    up = sum(1 for t in scene["tiles"] if t["state"] == 1)
    if up >= 2:
        return "Remember them\u2026"          # a miss is being peeked at
    if not mine:
        return "Waiting for " + them
    return "Turn over two cards"


def draw_scene(scene, path, caption, two_player=None):
    w, h = scene["w"], scene["h"]
    img = Image.new("RGB", (w * ZOOM, h * ZOOM), TABLE)
    d = ImageDraw.Draw(img)
    d._image = img

    # felt
    for i in range(0, h, 4):
        d.line([(0, i * ZOOM), (w * ZOOM, i * ZOOM)], fill=(31, 46, 33), width=1)

    header = scene["headerH"]
    rect(d, 0, 0, w, header - 2, fill=(0, 0, 0))
    rect(d, 0, 0, w, header - 2, fill=(18, 26, 19))

    if two_player:
        you, them, sy, st, mine = two_player
        text(d, 6, 4, you, (255, 224, 130) if mine else (187, 187, 187), size=8)
        text(d, 6, 14, f"{sy} pairs", (255, 255, 255) if mine else (154, 144, 131), size=8)
        text(d, w - 6, 4, them, (255, 224, 130) if not mine else (187, 187, 187), size=8, anchor="ra")
        text(d, w - 6, 14, f"{st} pairs", (255, 255, 255) if not mine else (154, 144, 131),
             size=8, anchor="ra")
        text(d, w / 2, 4, "YOUR TURN" if mine else "THEIR TURN",
             (140, 224, 122) if mine else (216, 160, 160), size=8, anchor="ma")
        text(d, w / 2, 14, "1:12", (154, 144, 131), size=8, anchor="ma")
    else:
        text(d, 6, 5, f"Moves {scene['moves']}", (232, 220, 192), size=8)
        text(d, w / 2, 5, f"{scene['pairs']} / {scene['cols'] * scene['rows'] // 2} pairs",
             (255, 255, 255), size=8, anchor="ma")
        text(d, w - 6, 5, "0:47", (232, 220, 192), size=8, anchor="ra")

    cw, ch = scene["cardW"], scene["cardH"]
    for tile in scene["tiles"]:
        x, y, state = tile["x"], tile["y"], tile["state"]
        if state == 0:
            draw_back(d, x, y, cw, ch)
        else:
            draw_face(img, x, y, cw, ch, tile, dim=(state == 2))

    text(d, w / 2, h - scene["footerH"] + 2, caption, (187, 187, 187), size=8, anchor="ma")
    img.save(path)
    return path


def draw_flip_strip(scene, path):
    """The turn: squeeze to |1-2t|, swap the side at halfway."""
    tile = next(t for t in scene["tiles"] if t["state"] == 1)
    cw, ch = scene["cardW"], scene["cardH"]
    steps = [0.0, 0.2, 0.38, 0.5, 0.62, 0.8, 1.0]
    pad, gap = 10, 12
    w = pad * 2 + len(steps) * cw + (len(steps) - 1) * gap
    h = ch + 42
    img = Image.new("RGB", (w * ZOOM, h * ZOOM), TABLE)
    d = ImageDraw.Draw(img)
    d._image = img
    text(d, w / 2, 6, "One card turning over", (232, 220, 192), size=9, anchor="ma")
    for i, t in enumerate(steps):
        squeeze = max(0.02, abs(1 - 2 * t)) if t < 1 else 1.0
        show_front = True if t >= 0.5 else False
        cx = pad + i * (cw + gap) + cw / 2
        dw = max(1, int(cw * squeeze))
        layer = Image.new("RGB", (cw * ZOOM, ch * ZOOM), TABLE)
        ld = ImageDraw.Draw(layer)
        ld._image = layer
        if show_front:
            draw_face(layer, 0, 0, cw, ch, tile)
        else:
            draw_back(ld, 0, 0, cw, ch)
        layer = layer.resize((max(1, dw * ZOOM), ch * ZOOM))
        img.paste(layer, (int((cx - dw / 2) * ZOOM), 22 * ZOOM))
        text(d, cx, ch + 26, f"t={t:.2f}", (154, 144, 131), size=7, anchor="ma")
    img.save(path)
    return path


def main():
    scenes = {}
    with open(sys.argv[1], encoding="utf-8") as fh:
        for line in fh:
            if line.strip():
                s = json.loads(line)
                scenes[s["name"]] = s
    out = sys.argv[2].rstrip("/")

    made = []
    hard = scenes["hard-midgame"]
    # the two scores must add up to the pairs actually gone from the board,
    # or the picture is showing a game that cannot have happened
    yours = (hard["pairs"] + 1) // 2
    made.append(draw_scene(hard, f"{out}/memory-hard.png", caption_for(hard),
                           two_player=("jrpetty", "Steve", yours,
                                       hard["pairs"] - yours, True)))
    made.append(draw_scene(scenes["easy"], f"{out}/memory-easy.png",
                           caption_for(scenes["easy"])))
    med = scenes["medium-peek"]
    mine = med["pairs"] // 2
    made.append(draw_scene(med, f"{out}/memory-medium.png", caption_for(med, False, "Alex"),
                           two_player=("jrpetty", "Alex", mine,
                                       med["pairs"] - mine, False)))
    made.append(draw_scene(scenes["hard-tiny"], f"{out}/memory-smallest.png",
                           caption_for(scenes["hard-tiny"])))
    made.append(draw_flip_strip(scenes["hard-midgame"], f"{out}/memory-flip.png"))
    for p in made:
        print("wrote", p)


if __name__ == "__main__":
    main()
