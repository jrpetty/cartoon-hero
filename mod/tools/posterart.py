#!/usr/bin/env python3
"""
Print-resolution art of the Mob Trumps cards, for posters and banners.

NOT screenshots -- Minecraft cannot run here. Every card is drawn from the
transcription of CardRenderer in previewmemory.py, using real card data emitted
by the game's own MobCards, so the frame, the tier ring, the stat table and the
set numbers are the game's. The portrait is the 8x8 pixel face from
tools/mobfaces.py, standing in for the live 3D mob the game poses there.

Assets are written at ZOOM x the card's native 170x236, so ZOOM=6 gives a
1020x1416 card -- big enough for A3 at 300dpi.

Run: python3 tools/posterart.py <showcase.json> <board_scenes.json> <out_dir>
"""
import json
import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import previewmemory as pv

CARD_ZOOM = 6          # 170x236 -> 1020x1416 per card
FELT_TOP = (26, 70, 52)
FELT_BOTTOM = (7, 28, 20)
GOLD = (233, 196, 106)
CREAM = (247, 241, 226)


def poster_font(size, bold=True):
    names = ["DejaVuSans-Bold.ttf", "DejaVuSans.ttf"] if bold else ["DejaVuSans.ttf"]
    for n in names:
        try:
            return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/" + n, size)
        except OSError:
            continue
    return ImageFont.load_default()


def render_card(tile, zoom=CARD_ZOOM):
    """One card at full detail, as RGBA with square corners."""
    pv.ZOOM = zoom
    img = Image.new("RGB", (pv.CARD_W * zoom, pv.CARD_H * zoom), pv.FACE)
    pv.draw_face(img, 0, 0, pv.CARD_W, pv.CARD_H, tile)
    return img.convert("RGBA")


def with_shadow(card, angle, blur=26, spread=18, alpha=170):
    """Rotate a card and lay a soft drop shadow under it."""
    rot = card.rotate(angle, expand=True, resample=Image.BICUBIC)
    pad = blur * 3
    canvas = Image.new("RGBA", (rot.width + pad * 2, rot.height + pad * 2), (0, 0, 0, 0))
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    mask = rot.split()[3]
    shadow.paste((0, 0, 0, alpha), (pad, pad + spread), mask)
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    canvas.alpha_composite(shadow)
    canvas.alpha_composite(rot, (pad, pad))
    return canvas


def felt(w, h):
    """The dueling table's green, with grain and a vignette."""
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)],
               fill=tuple(int(FELT_TOP[i] + (FELT_BOTTOM[i] - FELT_TOP[i]) * t) for i in range(3)))
    grain = Image.new("L", (w // 2, h // 2))
    gd = ImageDraw.Draw(grain)
    for i in range(w * h // 900):
        x = (i * 7919) % (w // 2)
        y = (i * 104729) % (h // 2)
        gd.point((x, y), fill=40)
    img = Image.blend(img, Image.composite(Image.new("RGB", (w, h), (255, 255, 255)), img,
                                           grain.resize((w, h))), 0.05)
    vig = Image.new("L", (w, h), 0)
    vd = ImageDraw.Draw(vig)
    vd.ellipse([-w * 0.25, -h * 0.45, w * 1.25, h * 1.45], fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(min(w, h) // 8))
    return Image.composite(img, Image.new("RGB", (w, h), (4, 16, 11)), vig)


def hero_fan(cards, path, w=2600, h=1500):
    """
    The money shot: a fan of cards across the table.

    The card size is SOLVED from the canvas rather than picked, because the
    first version picked one and the cards ran off three sides of the frame
    with their own stat tables cropped. The fan has to fit inside the art, and
    the art has to leave the title somewhere to live.
    """
    bg = felt(w, h).convert("RGBA")
    n = len(cards)
    spread = 13.0                               # degrees between neighbours
    title_band = int(h * 0.21)                  # kept clear of the cards
    margin = int(w * 0.045)

    # start from the height the band leaves, then shrink if the fan is too wide
    card_h = int((h - title_band) * 0.88)
    card_w = round(card_h * pv.CARD_W / pv.CARD_H)
    overlap = 0.58
    swing = math.radians(spread * (n - 1) / 2)
    for _ in range(40):
        fan_w = (n - 1) * card_w * overlap + card_w * math.cos(swing) \
            + card_h * math.sin(swing)
        if fan_w <= w - margin * 2:
            break
        card_h = int(card_h * 0.96)
        card_w = round(card_h * pv.CARD_W / pv.CARD_H)

    step = int(card_w * overlap)
    cy = title_band + (h - title_band) // 2
    for i, tile in enumerate(cards):
        angle = (i - (n - 1) / 2) * spread
        card = render_card(tile).resize((card_w, card_h), Image.LANCZOS)
        piece = with_shadow(card, -angle, blur=20, spread=14)
        arc = int(abs(i - (n - 1) / 2) ** 2 * card_h * 0.012)
        x = int(w / 2 + (i - (n - 1) / 2) * step - piece.width / 2)
        y = cy - piece.height // 2 + arc
        bg.alpha_composite(piece, (x, y))

    d = ImageDraw.Draw(bg)
    title = poster_font(int(h * 0.105))
    sub = poster_font(int(h * 0.030))
    d.text((w / 2, title_band * 0.42), "MOB TRUMPS", font=title, fill=GOLD, anchor="mm")
    d.text((w / 2, title_band * 0.78), "ALL 81 MINECRAFT MOBS  \u00b7  ONE DECK",
           font=sub, fill=CREAM, anchor="mm")
    bg.convert("RGB").save(path)
    return path


def tier_lineup(cards, path, pad=70, label_h=110):
    """One card per tier, so the rings read at a glance. Transparent ground."""
    rendered = [render_card(c, zoom=4) for c in cards]
    cw, ch = rendered[0].size
    w = pad + len(rendered) * (cw + pad)
    h = ch + pad * 2 + label_h
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    f = poster_font(int(label_h * 0.30))
    for i, (card, tile) in enumerate(zip(rendered, cards)):
        x = pad + i * (cw + pad)
        shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
        shadow.paste((0, 0, 0, 140), (x, pad + 14), card.split()[3])
        img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(16)))
        img.alpha_composite(card, (x, pad))
        band = pv.TIER_BAND.get(tile["tier"], (120, 120, 120))
        cx = x + cw // 2
        # Only the tier word, in the tier's own colour. The mob's name is
        # already on the card, and a second line in cream would be invisible
        # on a light poster -- this asset ships on transparency.
        d.text((cx, pad + ch + 44), tile["tier"].capitalize(), font=f, fill=band, anchor="ma")
    img.save(path)
    return path


def single_card(tile, path, zoom=8):
    """One card, huge, on transparency — drop it anywhere on the poster."""
    card = render_card(tile, zoom=zoom)
    out = with_shadow(card, 0, blur=30, spread=22, alpha=150)
    out.save(path)
    return path


def card_back_tile(path, cols=6, rows=4, zoom=3):
    """A field of card backs, for a poster background."""
    pv.ZOOM = zoom
    cw, ch = pv.CARD_W, pv.CARD_H
    gap = 8
    w = cols * (cw + gap) + gap
    h = rows * (ch + gap) + gap
    img = Image.new("RGB", (w * zoom, h * zoom), (18, 46, 34))
    d = ImageDraw.Draw(img)
    d._image = img
    for r in range(rows):
        for c in range(cols):
            pv.draw_back(d, gap + c * (cw + gap), gap + r * (ch + gap), cw, ch)
    img.save(path)
    return path


def board_shot(scene, path, zoom=5):
    """The Memory table at poster resolution, from the real solved layout."""
    pv.ZOOM = zoom
    w, h = scene["w"], scene["h"]
    img = felt(w * zoom, h * zoom).convert("RGB")
    d = ImageDraw.Draw(img)
    d._image = img
    header = scene["headerH"]
    d.rectangle([0, 0, w * zoom, (header - 2) * zoom], fill=(14, 32, 22))
    f = poster_font(int(11 * zoom * 0.85))
    d.text((6 * zoom, 4 * zoom), "jrpetty", font=f, fill=(255, 224, 130))
    d.text((6 * zoom, 15 * zoom), "4 pairs", font=f, fill=(255, 255, 255))
    d.text(((w - 6) * zoom, 4 * zoom), "Steve", font=f, fill=(187, 187, 187), anchor="ra")
    d.text(((w - 6) * zoom, 15 * zoom), "4 pairs", font=f, fill=(154, 144, 131), anchor="ra")
    d.text((w * zoom / 2, 4 * zoom), "YOUR TURN", font=f, fill=(140, 224, 122), anchor="ma")
    cw, ch = scene["cardW"], scene["cardH"]
    for tile in scene["tiles"]:
        if tile["state"] == 0:
            pv.draw_back(d, tile["x"], tile["y"], cw, ch)
        else:
            pv.draw_face(img, tile["x"], tile["y"], cw, ch, tile, dim=(tile["state"] == 2))
    d.text((w * zoom / 2, (h - scene["footerH"] + 1) * zoom), "Remember them…",
           font=f, fill=(200, 200, 200), anchor="ma")
    img.save(path)
    return path


# --- showpiece helpers -------------------------------------------------------

def solve_perspective(src, dst):
    """
    Coefficients mapping dst -> src for Image.PERSPECTIVE.

    Eight unknowns from four point pairs, by plain Gaussian elimination --
    there is no numpy here, and pulling one in for one 8x8 solve would be a
    dependency for the sake of twenty lines.
    """
    m = []
    for (sx, sy), (dx, dy) in zip(src, dst):
        m.append([dx, dy, 1, 0, 0, 0, -sx * dx, -sx * dy, sx])
        m.append([0, 0, 0, dx, dy, 1, -sy * dx, -sy * dy, sy])
    for col in range(8):
        piv = max(range(col, 8), key=lambda r: abs(m[r][col]))
        m[col], m[piv] = m[piv], m[col]
        if abs(m[col][col]) < 1e-9:
            raise ValueError("degenerate quad")
        f = m[col][col]
        m[col] = [v / f for v in m[col]]
        for r in range(8):
            if r != col and m[r][col]:
                k = m[r][col]
                m[r] = [a - k * b for a, b in zip(m[r], m[col])]
    return [row[8] for row in m]


def rays(size, centre, count=22, colour=(255, 226, 150), peak=90, blur=14):
    """A radial burst behind the subject."""
    w, h = size
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    cx, cy = centre
    reach = (w + h)
    for i in range(count):
        a0 = (360 / count) * i
        a1 = a0 + (360 / count) * 0.46
        p = [(cx, cy)]
        for a in (a0, a1):
            r = math.radians(a)
            p.append((cx + math.cos(r) * reach, cy + math.sin(r) * reach))
        d.polygon(p, fill=peak)
    mask = mask.filter(ImageFilter.GaussianBlur(blur))
    # fade the rays out with distance so they do not slab the whole frame
    fade = Image.new("L", (w, h), 0)
    fd = ImageDraw.Draw(fade)
    steps = 60
    for i in range(steps, 0, -1):
        t = i / steps
        rr = min(w, h) * 0.95 * t
        fd.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=int(255 * (1 - t) ** 1.4))
    mask = ImageChops.multiply(mask, fade)
    layer = Image.new("RGBA", (w, h), colour + (0,))
    layer.putalpha(mask)
    return layer


def glow(card, colour, radius=60, alpha=190, grow=14):
    """A soft coloured halo shaped like the card."""
    pad = radius * 3
    canvas = Image.new("RGBA", (card.width + pad * 2, card.height + pad * 2), (0, 0, 0, 0))
    silhouette = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    body = card.split()[3].resize((card.width + grow * 2, card.height + grow * 2))
    silhouette.paste(colour + (alpha,), (pad - grow, pad - grow), body)
    return canvas, silhouette.filter(ImageFilter.GaussianBlur(radius)), pad


def sparkles(img, n=110, seed=7, colour=(255, 255, 255)):
    """Little four-point stars, for foil and legendary pieces."""
    d = ImageDraw.Draw(img, "RGBA")
    w, h = img.size
    r = seed
    for i in range(n):
        r = (1103515245 * r + 12345) % (1 << 31)
        x = r % w
        r = (1103515245 * r + 12345) % (1 << 31)
        y = r % h
        r = (1103515245 * r + 12345) % (1 << 31)
        s = 3 + (r % max(1, w // 220))
        a = 90 + (r % 150)
        d.line([(x - s, y), (x + s, y)], fill=colour + (a,), width=max(1, s // 3))
        d.line([(x, y - s), (x, y + s)], fill=colour + (a,), width=max(1, s // 3))
    return img


def render_back(zoom=CARD_ZOOM):
    pv.ZOOM = zoom
    img = Image.new("RGB", (pv.CARD_W * zoom, pv.CARD_H * zoom), pv.KRAFT_BACK)
    d = ImageDraw.Draw(img)
    d._image = img
    pv.draw_back(d, 0, 0, pv.CARD_W, pv.CARD_H)
    return img.convert("RGBA")


def dark(w, h, top=(18, 24, 30), bottom=(6, 9, 12)):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)],
               fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return img


# --- the showpieces ----------------------------------------------------------

def spotlight(tile, path, w=1800, h=2300, accent=(233, 176, 60), caption=None):
    """One card under a burst of light. Built for the top of a poster."""
    bg = dark(w, h, (30, 26, 20), (8, 7, 6)).convert("RGBA")
    cy = int(h * 0.46)
    bg.alpha_composite(rays((w, h), (w / 2, cy), count=24, colour=accent, peak=86))
    # behind the card: sparkles drawn after it speckle the card's own face,
    # which reads as dirt on the print rather than light in the air
    bg = sparkles(bg, n=90, seed=13, colour=(255, 244, 210))

    card_h = int(h * 0.62)
    card_w = round(card_h * pv.CARD_W / pv.CARD_H)
    card = render_card(tile, zoom=8).resize((card_w, card_h), Image.LANCZOS)
    canvas, halo, pad = glow(card, accent, radius=70, alpha=200, grow=18)
    x, y = int(w / 2 - card_w / 2), int(cy - card_h / 2)
    bg.alpha_composite(halo, (x - pad, y - pad))
    shadow = Image.new("RGBA", bg.size, (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 190), (x, y + 34), card.split()[3])
    bg.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(34)))
    bg.alpha_composite(card, (x, y))
    d = ImageDraw.Draw(bg)
    band = pv.TIER_BAND.get(tile["tier"], accent)
    big = poster_font(int(h * 0.062))
    small = poster_font(int(h * 0.024))
    d.text((w / 2, h * 0.885), tile["tier"].upper(), font=big, fill=accent, anchor="mm")
    d.text((w / 2, h * 0.935),
           caption or f"{tile['name'].upper()}  \u00b7  CARD {tile['no']} OF {tile['of']}",
           font=small, fill=CREAM, anchor="mm")
    bg.convert("RGB").save(path)
    return path


def foil_showcase(tile, path, w=1800, h=2300):
    """The holographic print: rainbow bands and a light sweep, as the game draws it."""
    bg = dark(w, h, (24, 20, 40), (6, 6, 14)).convert("RGBA")
    cy = int(h * 0.45)
    bg.alpha_composite(rays((w, h), (w / 2, cy), count=30,
                            colour=(180, 200, 255), peak=52))
    bg = sparkles(bg, n=150, seed=29, colour=(230, 240, 255))
    holo = dict(tile)
    holo["foil"] = True
    card_h = int(h * 0.62)
    card_w = round(card_h * pv.CARD_W / pv.CARD_H)
    card = render_card(holo, zoom=8).resize((card_w, card_h), Image.LANCZOS)

    # the sweeping highlight the game runs across a holo, as one bright band
    sweep = Image.new("RGBA", card.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(sweep)
    bw = card_w // 5
    for i in range(bw):
        a = int(120 * math.sin(math.pi * i / bw))
        sd.line([(card_w * 0.34 + i, 0), (card_w * 0.34 + i - card_h * 0.32, card_h)],
                fill=(255, 255, 255, a), width=6)
    card = Image.alpha_composite(card, sweep)

    x, y = int(w / 2 - card_w / 2), int(cy - card_h / 2)
    _, halo, pad = glow(card, (150, 120, 255), radius=80, alpha=200, grow=20)
    bg.alpha_composite(halo, (x - pad, y - pad))
    bg.alpha_composite(card, (x, y))
    d = ImageDraw.Draw(bg)
    d.text((w / 2, h * 0.885), "HOLOGRAPHIC", font=poster_font(int(h * 0.058)),
           fill=(196, 214, 255), anchor="mm")
    d.text((w / 2, h * 0.935), "HUNT THE MOB TO LEVEL THE CARD",
           font=poster_font(int(h * 0.022)), fill=CREAM, anchor="mm")
    bg.convert("RGB").save(path)
    return path


def pack_burst(cards, path, w=2600, h=1600):
    """
    Cards erupting out of a booster pack.

    Laid out in bands -- title, then the arc, then the pack -- because the
    first pass placed all three from the same centre and got a pack running
    off the bottom of the frame with cards sitting on top of the title.
    """
    bg = dark(w, h, (22, 40, 32), (5, 12, 9)).convert("RGBA")
    title_band = int(h * 0.20)
    pack_w, pack_h = int(w * 0.135), int(h * 0.26)
    pack_x, pack_y = int(w / 2 - pack_w / 2), int(h - pack_h - h * 0.035)
    bg.alpha_composite(rays((w, h), (w / 2, pack_y), count=26,
                            colour=(255, 236, 170), peak=95))

    n = len(cards)
    arc_top, arc_bottom = title_band + int(h * 0.03), pack_y - int(h * 0.02)
    card_h = int((arc_bottom - arc_top) * 0.86)
    card_w = round(card_h * pv.CARD_W / pv.CARD_H)
    for i, tile in enumerate(cards):
        t = (i - (n - 1) / 2) / max(1, (n - 1) / 2)      # -1 .. 1
        angle = -t * 30
        card = render_card(tile).resize((card_w, card_h), Image.LANCZOS)
        piece = with_shadow(card, angle, blur=22, spread=16, alpha=150)
        px = int(w / 2 + t * w * 0.215 - piece.width / 2)
        # the middle of the arc rides highest, the wings drop away
        py = int(arc_top + (1 - (1 - abs(t)) * 0.55) * (arc_bottom - arc_top - card_h)
                 - (piece.height - card_h) / 2)
        bg.alpha_composite(piece, (px, py))

    pack = Image.new("RGBA", (pack_w, pack_h), pv.KRAFT + (255,))
    pd = ImageDraw.Draw(pack)
    pd.rectangle([0, 0, pack_w - 1, pack_h - 1], outline=pv.KRAFT_DARK,
                 width=max(2, pack_w // 40))
    pd.rectangle([pack_w * 0.10, pack_h * 0.20, pack_w * 0.90, pack_h * 0.92],
                 outline=GOLD, width=max(2, pack_w // 70))
    # a torn top edge, cut out of the pack
    tear = [(-2, -2)]
    step = pack_w / 12
    for i in range(13):
        tear.append((i * step, (pack_h * 0.06) if i % 2 else 0))
    tear += [(pack_w + 2, -2)]
    pd.polygon(tear, fill=(0, 0, 0, 0))
    pf = poster_font(int(pack_w * 0.20))
    pd.text((pack_w / 2, pack_h * 0.50), "MOB", font=pf, fill=pv.INK + (255,), anchor="mm")
    pd.text((pack_w / 2, pack_h * 0.72), "TRUMPS", font=poster_font(int(pack_w * 0.135)),
            fill=pv.INK + (255,), anchor="mm")
    shadow = Image.new("RGBA", bg.size, (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 200), (pack_x, pack_y + 24), pack.split()[3])
    bg.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(28)))
    bg.alpha_composite(pack, (pack_x, pack_y))

    d = ImageDraw.Draw(bg)
    d.text((w / 2, title_band * 0.40), "RIP A PACK", font=poster_font(int(h * 0.095)),
           fill=GOLD, anchor="mm")
    d.text((w / 2, title_band * 0.78), "EVERY MOB YOU KILL DROPS ITS CARD",
           font=poster_font(int(h * 0.030)), fill=CREAM, anchor="mm")
    bg.convert("RGB").save(path)
    return path


def versus(left, right, path, w=2600, h=1500):
    """Two cards squaring up, for the duelling half of a poster."""
    bg = felt(w, h).convert("RGBA")
    card_h = int(h * 0.72)
    card_w = round(card_h * pv.CARD_W / pv.CARD_H)
    for tile, sign in ((left, -1), (right, 1)):
        card = render_card(tile).resize((card_w, card_h), Image.LANCZOS)
        # a gentle tilt: past about 8 degrees the shear pulls each stat's value
        # visibly out of line with its own label, which on a card whose whole
        # point is the numbers looks like a misprint
        piece = with_shadow(card, -sign * 7, blur=24, spread=18, alpha=170)
        x = int(w / 2 + sign * w * 0.185 - piece.width / 2)
        bg.alpha_composite(piece, (x, int(h * 0.53 - piece.height / 2)))

    # clash: a hot vertical seam between them
    seam = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sd = ImageDraw.Draw(seam)
    for i in range(40):
        a = int(120 * (1 - i / 40))
        sd.rectangle([w / 2 - i * 2, h * 0.14, w / 2 + i * 2, h * 0.92],
                     fill=(255, 232, 160, max(0, a // 6)))
    bg.alpha_composite(seam.filter(ImageFilter.GaussianBlur(26)))

    d = ImageDraw.Draw(bg)
    r = int(h * 0.115)
    cx, cy = w / 2, h * 0.53
    d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)],
              fill=(14, 34, 24), outline=GOLD, width=max(3, r // 16))
    d.text((cx, cy), "VS", font=poster_font(int(r * 1.05)), fill=GOLD, anchor="mm")
    d.text((w / 2, h * 0.075), "PICK A STAT. TAKE THE CARD.",
           font=poster_font(int(h * 0.055)), fill=CREAM, anchor="mm")
    bg.convert("RGB").save(path)
    return path


def collection_wall(cards, path, w=2600, h=1500):
    """
    A wall of cards receding into the dark: the size of the set, at a glance.

    Small cards, many of them. The first pass used cards a third of the frame
    high and it read as six cards colliding rather than a collection, with the
    caption sitting unreadably on top of them. The caption now gets its own
    band and the art fades into it.
    """
    bg = dark(w, h, (20, 30, 26), (5, 9, 8)).convert("RGBA")
    caption_band = int(h * 0.20)
    card_h = int(h * 0.215)
    card_w = round(card_h * pv.CARD_W / pv.CARD_H)
    gap = max(4, int(card_w * 0.09))
    cols = w // (card_w + gap) + 2
    rows = int((h - caption_band) / (card_h * 0.78)) + 1

    faces = [render_card(c, zoom=3).resize((card_w, card_h), Image.LANCZOS) for c in cards]
    back = render_back(zoom=3).resize((card_w, card_h), Image.LANCZOS)
    art = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    k = 0
    for r in range(rows):
        for c in range(cols):
            # a scattering of face-down cards through the faces, so the wall
            # reads as a collection in progress rather than a finished sheet
            piece = back if (r * 5 + c * 3) % 7 == 0 else faces[k % len(faces)]
            k += 1
            x = int(c * (card_w + gap) - (r % 2) * (card_w + gap) / 2 - card_w * 0.2)
            y = int(h * 0.02 + r * (card_h + gap) * 0.80)
            art.alpha_composite(piece, (x, y))

    # fade the art out top and bottom rather than ringing it with a vignette
    fade = Image.new("L", (w, h), 255)
    fd = ImageDraw.Draw(fade)
    for i in range(int(h * 0.16)):
        fd.line([(0, i), (w, i)], fill=int(255 * i / (h * 0.16)))
    band = int(caption_band * 1.35)          # start the fade above the words
    for i in range(band):
        y = h - band + i
        fd.line([(0, y), (w, y)], fill=int(255 * (1 - i / band) ** 3.0))
    art.putalpha(ImageChops.multiply(art.split()[3], fade))
    bg.alpha_composite(art)

    d = ImageDraw.Draw(bg)
    d.text((w / 2, h - caption_band * 0.52), "81 CARDS TO COLLECT",
           font=poster_font(int(h * 0.095)), fill=GOLD, anchor="mm")
    d.text((w / 2, h - caption_band * 0.16), "EVERY MOB IN THE GAME  \u00b7  FIVE TIERS",
           font=poster_font(int(h * 0.030)), fill=CREAM, anchor="mm")
    bg.convert("RGB").save(path)
    return path


def fit_font(text, max_w, start_px, bold=True):
    """The largest font that keeps `text` inside `max_w`."""
    size = start_px
    while size > 8:
        f = poster_font(size, bold)
        if f.getbbox(text)[2] - f.getbbox(text)[0] <= max_w:
            return f
        size = int(size * 0.94)
    return poster_font(8, bold)


def banner(cards, path, w=3200, h=1000):
    """
    A wide header strip: wordmark left, a fan of cards right.

    The text is fitted to the room the cards leave it. Set at a fixed size it
    ran straight under the fan -- the wordmark lost its last letter and the
    strapline lost a whole word.
    """
    bg = felt(w, h).convert("RGBA")
    card_h = int(h * 0.80)
    card_w = round(card_h * pv.CARD_W / pv.CARD_H)
    fan_cx = w * 0.775
    for i, tile in enumerate(cards):
        card = render_card(tile).resize((card_w, card_h), Image.LANCZOS)
        piece = with_shadow(card, -(i - 1) * 10, blur=18, spread=12)
        x = int(fan_cx + (i - 1) * card_w * 0.60 - piece.width / 2)
        bg.alpha_composite(piece, (x, int(h * 0.52 - piece.height / 2)))

    # everything left of the fan, minus a breathing gap
    text_left = w * 0.055
    text_room = int(fan_cx - card_w * 1.35 - text_left)
    d = ImageDraw.Draw(bg)
    wordmark = fit_font("MOB TRUMPS", text_room, int(h * 0.26))
    strap = fit_font("COLLECT  \u00b7  DUEL  \u00b7  REMEMBER", text_room, int(h * 0.085))
    d.text((text_left, h * 0.40), "MOB TRUMPS", font=wordmark, fill=GOLD, anchor="lm")
    d.text((text_left + 4, h * 0.64), "COLLECT  \u00b7  DUEL  \u00b7  REMEMBER",
           font=strap, fill=CREAM, anchor="lm")
    bg.convert("RGB").save(path)
    return path


def table_angle(scene, path, w=2600, h=1600):
    """The Memory board laid on the table, seen from a player's chair."""
    zoom = 4
    pv.ZOOM = zoom
    bw, bh = scene["w"] * zoom, scene["h"] * zoom
    board = Image.new("RGB", (bw, bh), (17, 46, 34))
    d = ImageDraw.Draw(board)
    d._image = board
    cw, ch = scene["cardW"], scene["cardH"]
    for tile in scene["tiles"]:
        if tile["state"] == 0:
            pv.draw_back(d, tile["x"], tile["y"], cw, ch)
        else:
            pv.draw_face(board, tile["x"], tile["y"], cw, ch, tile,
                         dim=(tile["state"] == 2))
    # Crop to the grid before warping. Warping the whole frame put a small
    # board in the middle of a large empty plane -- the cards are the subject,
    # the felt around them is not.
    xs = [t["x"] for t in scene["tiles"]]
    ys = [t["y"] for t in scene["tiles"]]
    m = int(cw * 0.22)
    box = (max(0, (min(xs) - m) * zoom), max(0, (min(ys) - m) * zoom),
           min(bw, (max(xs) + cw + m) * zoom), min(bh, (max(ys) + ch + m) * zoom))
    board = board.crop(box).convert("RGBA")
    bw, bh = board.size

    bg = felt(w, h).convert("RGBA")
    dst = [(w * 0.10, h * 0.30), (w * 0.90, h * 0.30),
           (w * 1.02, h * 0.98), (w * -0.02, h * 0.98)]
    src = [(0, 0), (bw, 0), (bw, bh), (0, bh)]
    coeffs = solve_perspective(src, dst)
    warped = board.transform((w, h), Image.PERSPECTIVE, coeffs, Image.BICUBIC)
    shade = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    shade.paste((0, 0, 0, 170), (0, 26), warped.split()[3])
    bg.alpha_composite(shade.filter(ImageFilter.GaussianBlur(30)))
    bg.alpha_composite(warped)
    d2 = ImageDraw.Draw(bg)
    d2.text((w / 2, h * 0.10), "MEMORY", font=poster_font(int(h * 0.105)),
            fill=GOLD, anchor="mm")
    d2.text((w / 2, h * 0.185), "TURN TWO CARDS  \u00b7  KEEP THE PAIRS",
            font=poster_font(int(h * 0.032)), fill=CREAM, anchor="mm")
    bg.convert("RGB").save(path)
    return path


def main():
    cards = {c["face"]: c for c in json.load(open(sys.argv[1], encoding="utf-8"))}
    scenes = {}
    with open(sys.argv[2], encoding="utf-8") as fh:
        for line in fh:
            if line.strip():
                sc = json.loads(line)
                scenes[sc["name"]] = sc
    out = sys.argv[3].rstrip("/")
    os.makedirs(out, exist_ok=True)

    made = []
    fan = ["creeper", "enderman", "warden", "ravager", "bee"]
    made.append(hero_fan([cards[k] for k in fan], f"{out}/poster-hero-fan.png"))
    made.append(banner([cards[k] for k in ("enderman", "warden", "creeper")],
                       f"{out}/poster-banner.png"))
    made.append(spotlight(cards["warden"], f"{out}/poster-spotlight-warden.png"))
    made.append(spotlight(cards["ender_dragon"], f"{out}/poster-spotlight-dragon.png",
                          accent=(206, 118, 235)))
    made.append(foil_showcase(cards["axolotl"], f"{out}/poster-foil.png"))
    made.append(pack_burst([cards[k] for k in
                            ("creeper", "blaze", "warden", "wolf", "enderman")],
                           f"{out}/poster-pack-burst.png"))
    made.append(versus(cards["warden"], cards["ender_dragon"], f"{out}/poster-versus.png"))
    made.append(collection_wall(list(cards.values()), f"{out}/poster-collection-wall.png"))
    made.append(table_angle(scenes["hard-midgame"], f"{out}/poster-memory-table.png"))
    tiers = ["creeper", "bee", "enderman", "ravager", "warden"]
    made.append(tier_lineup([cards[k] for k in tiers], f"{out}/poster-tiers.png"))
    made.append(single_card(cards["warden"], f"{out}/poster-card-warden.png"))
    made.append(single_card(cards["creeper"], f"{out}/poster-card-creeper.png"))
    made.append(card_back_tile(f"{out}/poster-card-backs.png"))
    for p in made:
        im = Image.open(p)
        print(f"  {os.path.basename(p):32} {im.size[0]}x{im.size[1]}")


if __name__ == "__main__":
    main()
