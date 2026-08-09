#!/usr/bin/env python3
"""
Render previews of the mod's screens by re-running their layout arithmetic.

These are NOT screenshots. Minecraft cannot run here, so this file ports the
drawing maths out of the Java — RankEmblem's shield profile and glyphs,
RankedScreen's header and rows, TableArt's palette — and draws the same
rectangles with PIL. Where the Java says `g.fill(x, y, x + w, y + h, colour)`
this says `rect`, with the same numbers.

That makes them useful for judging layout, colour and proportion, and useless
for judging anything the port does not cover: the real font is Minecraft's
(this uses DejaVu), the felt gradient is approximated, and live mob portraits
obviously cannot appear. A previous mock on this project drew blank grey bars
where card stats go and I nearly "fixed" a bug that did not exist, so: trust
these for arrangement, not for proof.

Run: python3 tools/preview.py [outdir]
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

SCALE = 3          # draw at 1x then upscale with nearest, like a GUI scale
FONT_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_PATH_R = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

# --- TableArt palette, copied from TableArt.java ---
FELT_EDGE = (0x0C, 0x20, 0x19)
FELT_MID = (0x14, 0x37, 0x2B)
FELT_LIT = (0x1E, 0x50, 0x40)
RAIL_FACE = (0x40, 0x29, 0x1A)
RAIL_LIGHT = (0x6E, 0x4A, 0x2C)
RAIL_DARK = (0x1D, 0x11, 0x09)
BRASS = (0xE9, 0xC4, 0x6A)
BRASS_HI = (0xF7, 0xE3, 0xAE)
BRASS_DIM = (0x8A, 0x6A, 0x2A)
BRASS_DARK = (0x4E, 0x3A, 0x16)
SLATE = (0x16, 0x26, 0x1E)
SLATE_HI = (0x2A, 0x44, 0x38)
INK = (0xF4, 0xEE, 0xE0)
DIM = (0x9D, 0xB3, 0xA6)
FAINT = (0x5B, 0x72, 0x66)
GOOD = (0x63, 0xD9, 0x7A)
BAD = (0xE2, 0x56, 0x4C)
RAIL = 7

# --- RankTier.java ---
TIERS = [
    ("Bronze", 0, (0xCD, 0x7F, 0x32)),
    ("Silver", 900, (0xC7, 0xCC, 0xD1)),
    ("Gold", 1050, (0xFF, 0xD5, 0x4A)),
    ("Platinum", 1200, (0x5F, 0xE0, 0xD0)),
    ("Diamond", 1350, (0x6F, 0xB4, 0xFF)),
    ("Master", 1500, (0xC0, 0x8B, 0xFF)),
]
SPAN, DIV = 150, 50


def tier_of(rating):
    out = 0
    for i, (_, lo, _) in enumerate(TIERS):
        if rating >= lo:
            out = i
    return out


def division(rating):
    t = tier_of(rating)
    if t == 5:
        return 1
    into = min(SPAN - 1, rating - TIERS[t][1])
    return 3 - min(2, into // DIV)


def roman(d):
    return {1: "I", 2: "II"}.get(d, "III")


def badge_label(rating):
    t = tier_of(rating)
    return "Master" if t == 5 else f"{TIERS[t][0]} {roman(division(rating))}"


def next_step(rating):
    t = tier_of(rating)
    if t == 5:
        return -1
    d = division(rating)
    if d > 1:
        return TIERS[t][1] + (3 - d + 1) * DIV
    return TIERS[min(5, t + 1)][1]


def step_floor(rating):
    t = tier_of(rating)
    if t == 5:
        return TIERS[5][1]
    return TIERS[t][1] + (3 - division(rating)) * DIV


def progress(rating):
    nxt = next_step(rating)
    if nxt < 0:
        return 1.0
    fl = step_floor(rating)
    return max(0.0, min(1.0, (rating - fl) / max(1, nxt - fl)))


# --- helpers mirroring GuiGraphics ---
def lighten(c, by):
    return tuple(min(255, round(v * by)) for v in c)


def darken(c, by):
    return tuple(max(0, round(v * by)) for v in c)


def rect(d, x, y, x2, y2, c):
    if x2 <= x or y2 <= y:
        return
    d.rectangle([x, y, x2 - 1, y2 - 1], fill=c)


def outline(d, x, y, w, h, c):
    rect(d, x, y, x + w, y + 1, c)
    rect(d, x, y + h - 1, x + w, y + h, c)
    rect(d, x, y, x + 1, y + h, c)
    rect(d, x + w - 1, y, x + w, y + h, c)


def bevel(d, x, y, w, h, face, light, dark):
    rect(d, x, y, x + w, y + h, face)
    rect(d, x, y, x + w, y + 1, light)
    rect(d, x, y, x + 1, y + h, light)
    rect(d, x, y + h - 1, x + w, y + h, dark)
    rect(d, x + w - 1, y, x + w, y + h, dark)


def plate(d, x, y, w, h, accent):
    bevel(d, x, y, w, h, SLATE, SLATE_HI, (0x0A, 0x14, 0x0F))
    outline(d, x, y, w, h, accent)


def pool(d, cx, cy, radius, color, peak):
    steps = 18
    base = None
    for i in range(steps, 0, -1):
        t = i / steps
        r = round(radius * t)
        inset = round(r * 0.30)
        alpha = round(peak * (1 - t) * (1 - t))
        if alpha <= 0:
            continue
        # PIL has no alpha fills on RGB; blend towards the colour instead
        def blend(x0, y0, x1, y1):
            if x1 <= x0 or y1 <= y0:
                return
            box = (max(0, x0), max(0, y0), x1, y1)
            try:
                region = img_for_blend.crop(box)
            except Exception:
                return
            overlay = Image.new("RGB", region.size, color)
            img_for_blend.paste(Image.blend(region, overlay, alpha / 255), box)
        blend(cx - r, cy - r + inset, cx + r, cy + r - inset)
        blend(cx - r + inset, cy - r, cx + r - inset, cy + r)


img_for_blend = None


def felt(d, w, h):
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(round(FELT_MID[i] + (FELT_EDGE[i] - FELT_MID[i]) * t) for i in range(3))
        rect(d, 0, y, w, y + 1, c)


def rail(d, w, h):
    rect(d, 0, 0, w, RAIL, RAIL_FACE)
    rect(d, 0, h - RAIL, w, h, RAIL_FACE)
    rect(d, 0, 0, RAIL, h, RAIL_FACE)
    rect(d, w - RAIL, 0, w, h, RAIL_FACE)
    rect(d, 0, 0, w, 1, RAIL_LIGHT)
    rect(d, 0, h - 1, w, h, RAIL_DARK)
    rect(d, RAIL - 1, RAIL - 1, w - RAIL + 1, RAIL, RAIL_DARK)
    rect(d, RAIL - 1, h - RAIL, w - RAIL + 1, h - RAIL + 1, RAIL_LIGHT)
    for cx, cy in ((2, 2), (w - 5, 2), (2, h - 5), (w - 5, h - 5)):
        rect(d, cx, cy, cx + 3, cy + 3, BRASS_DIM)
        rect(d, cx, cy, cx + 2, cy + 1, BRASS_HI)


# --- RankEmblem.java, ported ---
PROFILE = [1.00, 1.00, 1.00, 0.99, 0.98, 0.96, 0.94, 0.91,
           0.87, 0.82, 0.76, 0.68, 0.58, 0.45, 0.29, 0.11]


def chevron(d, cx, cy, u, light, dark):
    arm = u * 2
    for i in range(arm):
        t = max(1, u // 2)
        rect(d, cx - i - t, cy - i + u, cx - i, cy - i + u + t, light if i == 0 else dark)
        rect(d, cx + i, cy - i + u, cx + i + t, cy - i + u + t, light if i == 0 else dark)


def crown(d, cx, cy, u, light, dark):
    w = u * 3
    base = cy + u
    for i in (-1, 0, 1):
        peak = cx + i * (w - u)
        h = u * 3 if i == 0 else u * 2
        for r in range(h):
            half = max(1, round(u * (1 - r / h)))
            rect(d, peak - half, base - r, peak + half, base - r + 1,
                 light if r >= h - 1 else dark)
    rect(d, cx - w, base, cx + w, base + max(2, u), dark)
    rect(d, cx - w, base, cx + w, base + 1, light)


def diamond(d, cx, cy, r, light, dark, cut):
    for dy in range(-r, r + 1):
        w = r - abs(dy)
        if w <= 0:
            continue
        rect(d, cx - w, cy + dy, cx + w, cy + dy + 1, light if dy < 0 else dark)
    if cut:
        rect(d, cx - r + 1, cy, cx + r - 1, cy + 1, (255, 255, 255))
        rect(d, cx - r // 2, cy - r // 2, cx - r // 2 + 1, cy, (200, 200, 200))
        rect(d, cx + r // 2, cy - r // 2, cx + r // 2 + 1, cy, (200, 200, 200))


def star(d, cx, cy, r, light, dark):
    for dd in range(-r, r + 1):
        t = max(0, (r - abs(dd)) // 3)
        if t:
            rect(d, cx - t, cy + dd, cx + t, cy + dd + 1, light if dd < 0 else dark)
            rect(d, cx + dd, cy - t, cx + dd + 1, cy + t, light if dd < 0 else dark)
    sm = max(1, r // 2)
    for i in range(sm):
        t = max(1, (sm - i) // 2)
        rect(d, cx + i, cy + i, cx + i + t, cy + i + t, dark)
        rect(d, cx - i - t, cy + i, cx - i, cy + i + t, dark)
        rect(d, cx + i, cy - i - t, cx + i + t, cy - i, light)
        rect(d, cx - i - t, cy - i - t, cx - i, cy - i, light)
    rect(d, cx - 1, cy - 1, cx + 1, cy + 1, (255, 255, 255))


def emblem(d, x, y, size, rating, pips=True):
    t = tier_of(rating)
    face = TIERS[t][2]
    rows = len(PROFILE)
    half = max(2, size // 2)
    row_h = max(1, round(size * 1.18 / rows))
    cx = x + half
    light = lighten(face, 1.35)
    edge = darken(face, 0.34)
    dark = darken(face, 0.55)

    for r in range(rows):
        w = max(1, round(half * PROFILE[r]))
        ry = y + r * row_h
        band = lighten(face, 1.12) if r < rows // 3 else (
            darken(face, 0.78) if r > rows * 2 // 3 else face)
        rect(d, cx - w, ry, cx + w, ry + row_h, band)
        rect(d, cx - w, ry, cx - w + 1, ry + row_h, edge)
        rect(d, cx + w - 1, ry, cx + w, ry + row_h, edge)
    top_w = max(1, round(half * PROFILE[0]))
    rect(d, cx - top_w, y, cx + top_w, y + max(1, row_h // 2), light)
    if size >= 16:
        inset = max(1, size // 11)
        pin = tuple(round(c * 0.35 + f * 0.65) for c, f in zip((0xF7, 0xE3, 0xAE), face))
        for r in range(inset, rows - inset):
            w = max(1, round(half * PROFILE[r])) - inset
            if w <= 1:
                continue
            ry = y + r * row_h
            rect(d, cx - w, ry, cx - w + 1, ry + row_h, pin)
            rect(d, cx + w - 1, ry, cx + w, ry + row_h, pin)
        w_top = max(1, round(half * PROFILE[inset])) - inset
        rect(d, cx - w_top, y + inset * row_h, cx + w_top, y + inset * row_h + 1, pin)
    rect(d, cx - top_w + 1, y + row_h, cx - top_w + 1 + max(1, size // 8), y + row_h + 1,
         (0xBF, 0xBF, 0xBF))

    gy = y + round(size * 0.46)
    u = max(1, size // 8)
    if t == 0:
        chevron(d, cx, gy, u, light, dark)
    elif t == 1:
        chevron(d, cx, gy - u, u, light, dark)
        chevron(d, cx, gy + u, u, light, dark)
    elif t == 2:
        crown(d, cx, gy, u, light, dark)
    elif t == 3:
        diamond(d, cx, gy, u * 2, light, dark, False)
    elif t == 4:
        diamond(d, cx, gy, u * 2, light, dark, True)
    else:
        star(d, cx, gy, u * 3, light, dark)

    if pips and size >= 12 and t != 5:
        shown = max(1, 4 - division(rating))
        pip_w = max(2, size // 7)
        gap = max(1, pip_w // 2)
        total = shown * pip_w + (shown - 1) * gap
        px = cx - total // 2
        py = y + rows * row_h + 4
        for _ in range(shown):
            rect(d, px, py, px + pip_w, py + max(2, pip_w - 1), light)
            px += pip_w + gap
    return rows * row_h


def font(size, bold=True):
    return ImageFont.truetype(FONT_PATH if bold else FONT_PATH_R, size)


def text(d, x, y, s, c, size=8, bold=False, shadow=False):
    f = font(size, bold)
    if shadow:
        d.text((x + 1, y + 1), s, font=f, fill=(0, 0, 0))
    d.text((x, y), s, font=f, fill=c)


def tw(s, size=8, bold=False):
    return round(font(size, bold).getlength(s))


# --- the ranked screen ---
LADDER = [
    ("Ravenpaw", 1586, 41, 12, 1586),
    ("jrpetty", 1478, 33, 15, 1502),
    ("Skarn", 1402, 28, 18, 1440),
    ("Mossveil", 1361, 22, 14, 1375),
    ("Tinderbox", 1288, 19, 16, 1310),
    ("QuietStorm", 1214, 15, 13, 1240),
    ("HalfPint", 1102, 11, 17, 1160),
    ("Grimshaw", 1043, 9, 20, 1090),
    ("Pebble", 968, 5, 19, 1020),
]
YOU = 1


def ranked_screen(w=470, h=280):
    global img_for_blend
    img = Image.new("RGB", (w, h))
    img_for_blend = img
    d = ImageDraw.Draw(img)
    felt(d, w, h)
    rail(d, w, h)

    HEAD_H, ROW_H = 86, 18
    pw = min(430, w - 2 * RAIL - 8)
    px = (w - pw) // 2
    top = RAIL + 4

    text(d, px + 4, top - 1, "RANKED", BRASS, 13, True, True)
    text(d, px + 4, top + 16, "SEASON 4", DIM, 8)
    clock = "season ends in 2d 7h"
    text(d, px + pw - tw(clock, 8), top + 16, clock, FAINT, 8)

    name, rating, wins, losses, peak = LADDER[YOU]
    t = tier_of(rating)
    card_y, card_h = top + 28, 38
    plate(d, px, card_y, pw, card_h, BRASS_DIM)
    # crest pool of light (approximated flat)
    for rr in range(24, 0, -3):
        a = (24 - rr) / 24 * 0.18
        c = tuple(round(SLATE[i] + (TIERS[t][2][i] - SLATE[i]) * a) for i in range(3))
        rect(d, px + 19 - rr, card_y + card_h // 2 - rr * 2 // 3,
             px + 19 + rr, card_y + card_h // 2 + rr * 2 // 3, c)
    emblem(d, px + 7, card_y + 4, 26, rating, pips=False)
    big = str(rating)
    text(d, px + pw - 12 - tw(big, 13, True), card_y + 8, big, TIERS[t][2], 13, True, True)
    tx = px + 42
    text(d, tx, card_y + 6, badge_label(rating).upper(), TIERS[t][2], 9, True, True)
    sub = f"#{YOU + 1} of 9   {wins}W {losses}L   peak {peak}"
    text(d, tx, card_y + 18, sub, DIM, 8)

    bar_x, bar_y = tx, card_y + 29
    bar_w = max(40, px + pw - 88 - bar_x)
    rect(d, bar_x - 1, bar_y - 1, bar_x + bar_w + 1, bar_y + 5, (0x0A, 0x14, 0x0F))
    outline(d, bar_x - 1, bar_y - 1, bar_w + 2, 6, BRASS_DARK)
    fill = round(bar_w * progress(rating))
    rect(d, bar_x, bar_y, bar_x + fill, bar_y + 4, TIERS[t][2])
    rect(d, bar_x, bar_y, bar_x + fill, bar_y + 1, lighten(TIERS[t][2], 1.35))
    goal = f"{next_step(rating) - rating} to {badge_label(next_step(rating))}"
    text(d, bar_x + bar_w - tw(goal, 8), bar_y - 9, goal, FAINT, 8)
    warn = "decays if idle"
    text(d, px + pw - tw(warn, 8), card_y + card_h - 12, warn, FAINT, 8)
    rect(d, px, HEAD_H - 4, px + pw, HEAD_H - 3, BRASS_DARK)

    for i, (nm, rt, wn, ls, pk) in enumerate(LADDER):
        y = HEAD_H + i * ROW_H
        if y + ROW_H > h - RAIL - 14:
            break
        mine = i == YOU
        tt = tier_of(rt)
        bg = (30, 62, 46) if mine else ((18, 30, 25) if i % 2 == 0 else (14, 24, 20))
        rect(d, px, y, px + pw, y + ROW_H - 1, bg)
        if mine:
            outline(d, px, y, pw, ROW_H - 1, GOOD)
        rect(d, px, y, px + 2, y + ROW_H - 1, TIERS[tt][2])
        if i < 3:
            medal = [(255, 213, 74), (199, 204, 209), (205, 127, 50)][i]
            mc, my = px + 12, y + ROW_H // 2 - 1
            for r in range(5, 0, -1):
                shade = darken(medal, 0.55) if r == 5 else (medal if r >= 4 else lighten(medal, 1.2))
                rect(d, mc - r, my - r + 1, mc + r, my + r - 1, shade)
                rect(d, mc - r + 1, my - r, mc + r - 1, my + r, shade)
            n = str(i + 1)
            text(d, mc - tw(n, 8, True) // 2 + 1, my - 5, n, (0x1A, 0x12, 0x06), 8, True)
        else:
            text(d, px + 6, y + 5, f"#{i + 1}", FAINT, 8)
        emblem(d, px + 30, y + 1, 13, rt, pips=False)
        text(d, px + 50, y + 5, nm, INK if mine else (0xDC, 0xD5, 0xC6), 8)
        text(d, px + pw // 2 + 10, y + 5, badge_label(rt), TIERS[tt][2], 8)
        rs = str(rt)
        text(d, px + pw - 8 - tw(rs, 8), y + 5, rs, TIERS[tt][2], 8)
        wl = f"{wn}W {ls}L"
        text(d, px + pw - 52 - tw(wl, 8), y + 5, wl, DIM, 8)

    text(d, px, h - RAIL - 11, "9 ranked", FAINT, 8)
    return img


def emblem_sheet():
    w, h = 300, 78
    img = Image.new("RGB", (w, h), (18, 30, 25))
    d = ImageDraw.Draw(img)
    ratings = [40, 950, 1100, 1250, 1400, 1600]
    x = 12
    for r in ratings:
        emblem(d, x, 12, 30, r)
        lab = badge_label(r)
        text(d, x + 15 - tw(lab, 7) // 2, 60, lab, DIM, 7)
        x += 48
    return img


def divisions_sheet():
    w, h = 300, 74
    img = Image.new("RGB", (w, h), (18, 30, 25))
    d = ImageDraw.Draw(img)
    text(d, 10, 6, "DIAMOND III / II / I  -  pips count up as you climb", DIM, 8)
    x = 24
    for r in (1350, 1400, 1450):
        emblem(d, x, 20, 30, r)
        lab = badge_label(r)
        text(d, x + 15 - tw(lab, 7) // 2, 62, lab, DIM, 7)
        x += 64
    return img


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "preview"
    os.makedirs(out, exist_ok=True)
    sounds = "src/main/resources/assets/mobtrumps/sounds"
    made = {"ranked_screen": ranked_screen(), "emblems": emblem_sheet(),
            "divisions": divisions_sheet(), "head_to_head": head_to_head(),
            "census": census_tooltip(), "waveforms": waveforms(sounds)}
    for name, img in made.items():
        big = img.resize((img.width * SCALE, img.height * SCALE), Image.NEAREST)
        path = os.path.join(out, name + ".png")
        big.save(path)
        print(f"  {name:16} {img.width}x{img.height} -> {path}")




# --- the rest: chat output, the book's census line, and real waveforms ---
def chat_panel(title, lines, w=470):
    h = 22 + len(lines) * 12 + 8
    img = Image.new("RGB", (w, h), (16, 16, 18))
    d = ImageDraw.Draw(img)
    rect(d, 0, 0, w, 1, (60, 60, 66))
    text(d, 8, 6, title, (0x9A, 0x9A, 0xA2), 8)
    y = 22
    for s, c in lines:
        text(d, 10, y, s, c, 9)
        y += 12
    return img


def head_to_head():
    G, R, W, Y, D = ((0x55, 0xE0, 0x6A), (0xE2, 0x56, 0x4C), (0xEE, 0xEE, 0xEE),
                     (0xF7, 0xD8, 0x4A), (0x88, 0x88, 0x90))
    return chat_panel("after a duel  ·  /mobtrumps record", [
        ("Diamond I  1478 (+18)  rank #2", (0x6F, 0xB4, 0xFF)),
        ("You lead Skarn 7-2", G),
        ("", D),
        ("=== YOUR RECORD ===", Y),
        ("Overall 33-15 across 6 opponents", Y),
        ("  Skarn   7-2    (9 played)", G),
        ("  Ravenpaw   3-8    (11 played)", R),
        ("  Mossveil   5-5    (10 played)", (0xF7, 0xD8, 0x4A)),
        ("--- last 4 ---", D),
        ("  WON  vs Skarn  +18   2 minutes ago", G),
        ("  LOST vs Ravenpaw  -14   1 hour ago", R),
        ("  WON  vs Mossveil  +16   3 hours ago", G),
        ("  WON  vs Tinderbox  +11   yesterday", G),
    ])


def census_tooltip(w=330):
    lines = [
        ("LEGENDARY  ·  Rarity 1 (lower wins)", (0xD9, 0xB4, 0x5B)),
        ("No. 78 / 81  ·  Boss", (0x8E, 0x81, 0x74)),
        ("Mob rating 47  ·  drops 1 in 1", (0x8E, 0x81, 0x74)),
        ("Hunted 3 times", (0x8E, 0x81, 0x74)),
        ("Holo II  ·  next at 25 kills", (0xA7, 0x66, 0xE9)),
        ("4 printed here  ·  2nd rarest of 63", (0x8E, 0x81, 0x74)),
        ("Filed in this book - Take out to pocket it", (0x2E, 0x8B, 0x3A)),
    ]
    h = 16 + len(lines) * 11 + 8
    img = Image.new("RGB", (w, h), (0x1A, 0x14, 0x0E))
    d = ImageDraw.Draw(img)
    outline(d, 0, 0, w, h, (0xD9, 0xB4, 0x5B))
    text(d, 7, 5, "Warden", (0xD9, 0xB4, 0x5B), 9, True)
    y = 18
    for s, c in lines:
        text(d, 7, y, s, c, 8)
        y += 11
    return img


def waveforms(sound_dir, w=470):
    import numpy as np
    import soundfile as sf
    names = ["card_flip", "card_place", "shuffle", "pack_rip",
             "foil_shimmer", "gavel", "chip"]
    rowh, pad = 40, 6
    h = len(names) * rowh + 10
    img = Image.new("RGB", (w, h), (16, 26, 22))
    d = ImageDraw.Draw(img)
    loaded = []
    for nm in names:
        data, sr = sf.read(os.path.join(sound_dir, nm + ".ogg"), dtype="float64")
        if data.ndim > 1:
            data = data.mean(axis=1)
        loaded.append((nm, data, sr))
    # a shared time axis, so the picture shows how much shorter a chip is than
    # a shimmer instead of stretching every sound to the same width
    longest = max(len(d) / sr for _, d, sr in loaded)
    for i, (nm, data, sr) in enumerate(loaded):
        y0 = 6 + i * rowh
        mid = y0 + (rowh - pad) // 2
        rect(d, 96, mid, 100 + max(1, int((w - 108) * (len(data) / sr) / longest)),
             mid + 1, (34, 54, 46))
        full = w - 108
        n = max(1, int(full * (len(data) / sr) / longest))
        step = max(1, len(data) // n)
        peak = 0.30                      # the checker's hard ceiling, as the scale
        for px in range(n):
            chunk = data[px * step:(px + 1) * step]
            if not len(chunk):
                break
            a = float(np.max(np.abs(chunk))) / peak
            hgt = max(1, int(a * (rowh - pad) / 2))
            rect(d, 100 + px, mid - hgt, 101 + px, mid + hgt, (0xE9, 0xC4, 0x6A))
        text(d, 8, mid - 8, nm, (0xDC, 0xD5, 0xC6), 8)
        text(d, 8, mid + 1, f"{len(data)/sr:.2f}s", (0x5B, 0x72, 0x66), 7)
    return img


if __name__ == "__main__":
    main()
