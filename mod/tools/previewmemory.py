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

# the mod's own palette, from CardRenderer
KRAFT = (154, 123, 84)
KRAFT_DARK = (95, 74, 50)
KRAFT_BACK = (122, 95, 62)
FACE = (250, 246, 236)
INK = (53, 40, 26)
TABLE = (28, 42, 30)

TIER_RGB = {
    "COMMON": (170, 170, 170),
    "UNCOMMON": (85, 168, 47),
    "RARE": (63, 167, 214),
    "EPIC": (181, 126, 220),
    "LEGENDARY": (255, 190, 60),
}

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


def draw_face(d, x, y, w, h, tile, dim=False):
    """An approximation of the real card: frame, tier band, name, stats."""
    tier = TIER_RGB.get(tile["tier"], (170, 170, 170))
    rect(d, x, y, w, h, fill=KRAFT_DARK)
    rect(d, x + 1, y + 1, w - 2, h - 2, fill=FACE)
    rect(d, x + 1, y + 1, w - 2, max(2, h // 9), fill=tier)
    if w >= 34:
        text(d, x + w / 2, y + 1 + h / 18, tile["name"][:13], INK,
             size=max(4, w / 8), anchor="mm")
    # portrait well
    py = y + 1 + max(2, h // 9) + 1
    ph = int(h * 0.42)
    rect(d, x + 2, py, w - 4, ph, fill=(214, 203, 178), outline=KRAFT_DARK)
    cx, cy = x + w / 2, py + ph / 2
    r = ph * 0.30
    d.ellipse([(cx - r) * ZOOM, (cy - r) * ZOOM, (cx + r) * ZOOM, (cy + r) * ZOOM],
              fill=tier, outline=KRAFT_DARK)
    # stat block
    sy = py + ph + 1
    rows = [("HP", tile["hp"]), ("ATK", tile["atk"]), ("SPD", tile["spd"]), ("RAR", tile["rar"])]
    room = y + h - 2 - sy
    rh = room / len(rows)
    if rh >= 2.2 and w >= 30:
        for i, (label, val) in enumerate(rows):
            ry = sy + i * rh
            text(d, x + 3, ry, label, (120, 108, 88), size=max(3.4, rh * 0.85))
            text(d, x + w - 3, ry, str(val), INK, size=max(3.4, rh * 0.85), anchor="ra")
    else:
        for i in range(len(rows)):
            ry = sy + i * rh
            rect(d, x + 3, ry + rh * 0.25, w - 6, max(1, int(rh * 0.4)), fill=(206, 195, 172))
    if dim:
        overlay = Image.new("RGBA", (w * ZOOM, h * ZOOM), (32, 24, 8, 155))
        d._image.paste(overlay, (x * ZOOM, y * ZOOM), overlay)


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
            draw_face(d, x, y, cw, ch, tile, dim=(state == 2))

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
            draw_face(ld, 0, 0, cw, ch, tile)
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
