#!/usr/bin/env python3
"""Generate placeholder textures for the Gadgets mod (pure Python, no Pillow)."""
import struct
import zlib
import os

BASE = os.path.join(os.path.dirname(__file__), "src", "main", "resources", "assets", "gadgets", "textures")


def write_png(path, pixels):
    h = len(pixels)
    w = len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw), 9)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(sig + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b""))
    print("wrote", path)


def grid(rows, palette):
    return [[palette[ch] for ch in row] for row in rows]


C = lambda r, g, b, a=255: (r, g, b, a)
CLEAR = (0, 0, 0, 0)

# --- Player Sensor block: dark metal with a glowing eye ---
SENSOR = [
    "MMMMMMMMMMMMMMMM",
    "MddddddddddddddM",
    "Mdd..........ddM",
    "Md...EEEEEE...dM",
    "Md..EEEEEEEE..dM",
    "Md..EE.WW.EE..dM",
    "Md..EE.WW.EE..dM",
    "Md..EEEEEEEE..dM",
    "Md..EEEEEEEE..dM",
    "Md..EE.WW.EE..dM",
    "Md..EE.WW.EE..dM",
    "Md...EEEEEE...dM",
    "Mdd..........ddM",
    "MddddddddddddddM",
    "MMMMMMMMMMMMMMMM",
    "MMMMMMMMMMMMMMMM",
]
sensor_pal = {"M": C(40, 42, 48), "d": C(70, 74, 82), ".": C(55, 58, 66),
              "E": C(220, 60, 60), "W": C(255, 200, 120)}
write_png(os.path.join(BASE, "block", "player_sensor.png"), grid(SENSOR, sensor_pal))

# --- Filter Hopper block: dark metal funnel with a slot ---
HOPPER = [
    "MMMMMMMMMMMMMMMM",
    "MmmmmmmmmmmmmmmM",
    "Mm..........mmM" + "",
    "Mm.kkkkkkkkk.mM",
    "Mm.k.......k.mM",
    "Mm.k.YYYYY.k.mM",
    "Mm.k.Y...Y.k.mM",
    "Mm.kk.....kk.mM",
    "Mm..kk...kk..mM",
    "Mm...kk.kk...mM",
    "Mm....kkk....mM",
    "Mm.....k.....mM",
    "Mm...........mM",
    "MmmmmmmmmmmmmmM",
    "MMMMMMMMMMMMMMMM",
    "MMMMMMMMMMMMMMMM",
]
# normalize widths to 16
HOPPER = [(r + "M" * 16)[:16] for r in HOPPER]
hopper_pal = {"M": C(40, 42, 48), "m": C(70, 74, 82), ".": C(55, 58, 66),
              "k": C(30, 32, 36), "Y": C(120, 200, 255)}
write_png(os.path.join(BASE, "block", "filter_hopper.png"), grid(HOPPER, hopper_pal))

# --- Rope Arrow item: arrow with a coil of rope ---
ROPE = [
    "................",
    ".............ww.",
    "............wwww",
    "...........ww.ww",
    "..........ww..ww",
    ".........ww..ww.",
    "ssss....ww..ww..",
    "ssssssssww.ww...",
    "ssssssssww.ww...",
    "ssss....ww..ww..",
    ".........ww..ww.",
    "..........ww..ww",
    "...........ww.ww",
    "............wwww",
    ".............ww.",
    "................",
]
rope_pal = {".": CLEAR, "s": C(120, 120, 130), "w": C(170, 120, 60)}
write_png(os.path.join(BASE, "item", "rope_arrow.png"), grid(ROPE, rope_pal))

# --- Torch Arrow item: a torch-tipped arrow ---
LIGHT = [
    ".......F........",
    "......FfF.......",
    ".....FfffF......",
    "......FfF.......",
    ".......t........",
    ".......t........",
    ".......s........",
    ".......s........",
    ".......s........",
    ".......s........",
    ".......s........",
    ".....b.s.b......",
    "......bsb.......",
    ".......s........",
    ".......s........",
    "................",
]
light_pal = {".": CLEAR, "s": C(150, 150, 160), "t": C(120, 80, 40),
             "f": C(255, 150, 40), "F": C(255, 215, 110), "b": C(185, 185, 195)}
write_png(os.path.join(BASE, "item", "light_arrow.png"), grid(LIGHT, light_pal))

# --- Rope block: braided brown, fully opaque (no cutout needed) ---
DARK = (96, 62, 30, 255)
MID = (132, 90, 46, 255)
LIGHT = (168, 120, 66, 255)
rope_rows = []
for y in range(16):
    row = []
    for x in range(16):
        band = (x + y) % 6
        if band < 2:
            row.append(LIGHT)
        elif band < 4:
            row.append(MID)
        else:
            row.append(DARK)
    rope_rows.append(row)
write_png(os.path.join(BASE, "block", "rope.png"), rope_rows)

# --- Redstone Transmitter block: dark metal with a red antenna broadcasting up ---
TX = [
    "MMMMMMMMMMMMMMMM",
    "MddddddddddddddM",
    "Md....r..r....dM",
    "Md.....rr.....dM",
    "Md..r..rr..r..dM",
    "Md...r.rr.r...dM",
    "Md....rrrr....dM",
    "Md.....rr.....dM",
    "Md.....rr.....dM",
    "Md.....rr.....dM",
    "Md....RRRR....dM",
    "Md....RRRR....dM",
    "MddddddddddddddM",
    "MMMMMMMMMMMMMMMM",
    "MMMMMMMMMMMMMMMM",
    "MMMMMMMMMMMMMMMM",
]
tx_pal = {"M": C(40, 42, 48), "d": C(70, 74, 82), ".": C(55, 58, 66),
          "r": C(220, 60, 60), "R": C(150, 30, 30)}
write_png(os.path.join(BASE, "block", "redstone_transmitter.png"), grid(TX, tx_pal))

# --- Redstone Receiver block: dark metal with a red dish catching a signal ---
RX = [
    "MMMMMMMMMMMMMMMM",
    "MddddddddddddddM",
    "Md....RRRR....dM",
    "Md....RRRR....dM",
    "Md.....rr.....dM",
    "Md.....rr.....dM",
    "Md.....rr.....dM",
    "Md....rrrr....dM",
    "Md...r.rr.r...dM",
    "Md..r..rr..r..dM",
    "Md.....rr.....dM",
    "Md....r..r....dM",
    "MddddddddddddddM",
    "MMMMMMMMMMMMMMMM",
    "MMMMMMMMMMMMMMMM",
    "MMMMMMMMMMMMMMMM",
]
rx_pal = {"M": C(40, 42, 48), "d": C(70, 74, 82), ".": C(55, 58, 66),
          "r": C(220, 60, 60), "R": C(150, 30, 30)}
write_png(os.path.join(BASE, "block", "redstone_receiver.png"), grid(RX, rx_pal))

# --- Redstone Linker item: a tuning wand with a glowing red tip ---
LINK = [
    "................",
    "............ggg.",
    "...........gRRRg",
    "...........gRrRg",
    "...........gRRRg",
    "............ggg.",
    "..........gg....",
    ".........gg.....",
    "........gg......",
    ".......hh.......",
    "......hh........",
    ".....hh.........",
    "....hh..........",
    "...hh...........",
    "..hh............",
    ".h..............",
]
link_pal = {".": CLEAR, "g": C(90, 96, 105), "h": C(120, 84, 44),
            "R": C(220, 60, 60), "r": C(255, 170, 170)}
write_png(os.path.join(BASE, "item", "redstone_linker.png"), grid(LINK, link_pal))

# --- Display Pedestal block: pale carved stone plinth ---
PED = [
    "pppppppppppppppp",
    "pPPPPPPPPPPPPPPp",
    "pP............Pp",
    "pP.pppppppppp.Pp"[:16],
    "pP.p........p.Pp"[:16],
    "pP.p.qqqqqq.p.Pp"[:16],
    "pP.p.q....q.p.Pp"[:16],
    "pP.p.q....q.p.Pp"[:16],
    "pP.p.q....q.p.Pp"[:16],
    "pP.p.qqqqqq.p.Pp"[:16],
    "pP.p........p.Pp"[:16],
    "pP.pppppppppp.Pp"[:16],
    "pP............Pp",
    "pPPPPPPPPPPPPPPp",
    "pppppppppppppppp",
    "pppppppppppppppp",
]
PED = [(r + "p" * 16)[:16] for r in PED]
ped_pal = {"p": C(150, 150, 158), "P": C(120, 120, 128),
           ".": C(170, 170, 178), "q": C(100, 100, 110)}
write_png(os.path.join(BASE, "block", "display_pedestal.png"), grid(PED, ped_pal))

# --- Item Sender block: dark metal with a blue up-arrow (items out) ---
SEND = [
    "MMMMMMMMMMMMMMMM",
    "MddddddddddddddM",
    "Md.....bb.....dM",
    "Md....bbbb....dM",
    "Md...bbbbbb...dM",
    "Md..bbbbbbbb..dM",
    "Md.bbb.bb.bbb.dM",
    "Md.b...bb...b.dM",
    "Md.....bb.....dM",
    "Md.....bb.....dM",
    "Md.....bb.....dM",
    "Md.....bb.....dM",
    "MddddddddddddddM",
    "MMMMMMMMMMMMMMMM",
    "MMMMMMMMMMMMMMMM",
    "MMMMMMMMMMMMMMMM",
]
send_pal = {"M": C(40, 42, 48), "d": C(70, 74, 82), ".": C(55, 58, 66),
            "b": C(90, 160, 230)}
write_png(os.path.join(BASE, "block", "item_sender.png"), grid(SEND, send_pal))

# --- Item Receiver block: dark metal with a blue down-arrow (items in) ---
RECV = [
    "MMMMMMMMMMMMMMMM",
    "MddddddddddddddM",
    "Md.....bb.....dM",
    "Md.....bb.....dM",
    "Md.....bb.....dM",
    "Md.....bb.....dM",
    "Md.b...bb...b.dM",
    "Md.bbb.bb.bbb.dM",
    "Md..bbbbbbbb..dM",
    "Md...bbbbbb...dM",
    "Md....bbbb....dM",
    "Md.....bb.....dM",
    "MddddddddddddddM",
    "MMMMMMMMMMMMMMMM",
    "MMMMMMMMMMMMMMMM",
    "MMMMMMMMMMMMMMMM",
]
recv_pal = {"M": C(40, 42, 48), "d": C(70, 74, 82), ".": C(55, 58, 66),
            "b": C(90, 160, 230)}
write_png(os.path.join(BASE, "block", "item_receiver.png"), grid(RECV, recv_pal))

# ============ v2 art: richer detail, gold-riveted steel frames ============
K2 = C(25, 26, 30)      # near-black frame
S2 = C(140, 146, 158)   # light steel
m2 = C(80, 85, 96)      # mid steel
d2 = C(52, 55, 64)      # dark inset
g2 = C(222, 178, 62)    # gold rivet
r2 = C(205, 55, 45)     # red
R2 = C(245, 95, 70)     # bright red
W2 = C(255, 220, 190)   # white-hot core
b2 = C(66, 135, 220)    # blue
B2 = C(125, 195, 255)   # bright blue

def fix16(rows):
    return [(r + "." * 16)[:16] for r in rows]

# --- Redstone Transmitter v2: glowing gem with radiating rays + antenna base ---
TX2 = fix16([
    "KKKKKKKKKKKKKKKK",
    "KgSSSSSSSSSSSSgK",
    "KSmmmmmmmmmmmmSK",
    "KSm....rr....mSK",
    "KSm..r.rr.r..mSK",
    "KSm.r..rr..r.mSK",
    "KSm...RRRR...mSK",
    "KSm..RRWWRR..mSK",
    "KSm..RRWWRR..mSK",
    "KSm...RRRR...mSK",
    "KSm..........mSK",
    "KSm..dddddd..mSK",
    "KSm.dKKKKKKd.mSK",
    "KSmmmmmmmmmmmmSK",
    "KgSSSSSSSSSSSSgK",
    "KKKKKKKKKKKKKKKK",
])
pal2 = {".": d2, "K": K2, "S": S2, "m": m2, "d": C(40, 42, 50), "g": g2,
        "r": r2, "R": R2, "W": W2, "b": b2, "B": B2}
write_png(os.path.join(BASE, "block", "redstone_transmitter.png"), grid(TX2, pal2))

# --- Redstone Receiver v2: gem inside a receiving ring, feed line below ---
RX2 = fix16([
    "KKKKKKKKKKKKKKKK",
    "KgSSSSSSSSSSSSgK",
    "KSmmmmmmmmmmmmSK",
    "KSm...dddd...mSK",
    "KSm..d....d..mSK",
    "KSm.d.RRRR.d.mSK",
    "KSm.d.RWWR.d.mSK",
    "KSm.d.RWWR.d.mSK",
    "KSm.d.RRRR.d.mSK",
    "KSm..d....d..mSK",
    "KSm...dddd...mSK",
    "KSm....rr....mSK",
    "KSm....rr....mSK",
    "KSmmmmmmmmmmmmSK",
    "KgSSSSSSSSSSSSgK",
    "KKKKKKKKKKKKKKKK",
])
write_png(os.path.join(BASE, "block", "redstone_receiver.png"), grid(RX2, pal2))

# --- Item Sender v2: bold blue up-arrow on brushed steel ---
SEND2 = fix16([
    "KKKKKKKKKKKKKKKK",
    "KgSSSSSSSSSSSSgK",
    "KSmmmmmmmmmmmmSK",
    "KSm....BB....mSK",
    "KSm...BBBB...mSK",
    "KSm..BBBBBB..mSK",
    "KSm.BBBBBBBB.mSK",
    "KSm....bb....mSK",
    "KSm....bb....mSK",
    "KSm....bb....mSK",
    "KSm....bb....mSK",
    "KSm...dbbd...mSK",
    "KSm..dddddd..mSK",
    "KSmmmmmmmmmmmmSK",
    "KgSSSSSSSSSSSSgK",
    "KKKKKKKKKKKKKKKK",
])
write_png(os.path.join(BASE, "block", "item_sender.png"), grid(SEND2, pal2))

# --- Item Receiver v2: bold blue down-arrow ---
RECV2 = fix16([
    "KKKKKKKKKKKKKKKK",
    "KgSSSSSSSSSSSSgK",
    "KSmmmmmmmmmmmmSK",
    "KSm..dddddd..mSK",
    "KSm...dbbd...mSK",
    "KSm....bb....mSK",
    "KSm....bb....mSK",
    "KSm....bb....mSK",
    "KSm....bb....mSK",
    "KSm.BBBBBBBB.mSK",
    "KSm..BBBBBB..mSK",
    "KSm...BBBB...mSK",
    "KSm....BB....mSK",
    "KSmmmmmmmmmmmmSK",
    "KgSSSSSSSSSSSSgK",
    "KKKKKKKKKKKKKKKK",
])
write_png(os.path.join(BASE, "block", "item_receiver.png"), grid(RECV2, pal2))

# --- Pedestal base: white marble with veins and a gold border ---
PED_PAL = {"g": C(212, 172, 70), "w": C(236, 233, 226), "W": C(248, 246, 240), "v": C(203, 198, 188)}
PED2 = fix16([
    "gggggggggggggggg",
    "gwwwwwWwwwwvwwwg",
    "gwWwwwwwvwwwwwwg",
    "gwwwwvwwwwwwWwwg",
    "gwwvwwwwWwwwwvwg",
    "gwWwwwwvwwwwwwwg",
    "gwwwwwwwwvwwWwwg",
    "gwvwwWwwwwwwwwvg",
    "gwwwwwwvwwWwwwwg",
    "gwwWwwwwwwwwvwwg",
    "gwvwwwvwwWwwwwwg",
    "gwwwwWwwwwwwvwwg",
    "gwWwwwwwvwwwwWwg",
    "gwwwvwwwwwWwwwwg",
    "gwwwwwWwwwwwvwwg",
    "gggggggggggggggg",
])
# fix16 pads with "." — replace any pad with marble
PED2 = [row.replace(".", "w") for row in PED2]
write_png(os.path.join(BASE, "block", "pedestal_base.png"), grid(PED2, PED_PAL))

# --- Drain: dark steel grate with slits and gold rivets ---
DR_PAL = {"K": C(30, 32, 36), "m": C(110, 116, 126), "g": C(222, 178, 62), "_": C(12, 12, 14)}
DRAIN2 = [
    "KKKKKKKKKKKKKKKK",
    "KgmmmmmmmmmmmmgK",
    "KmmmmmmmmmmmmmmK",
    "Kmm__mm__mm__mmK",
    "Kmm__mm__mm__mmK",
    "Kmm__mm__mm__mmK",
    "Kmm__mm__mm__mmK",
    "KmmmmmmmmmmmmmmK",
    "KmmmmmmmmmmmmmmK",
    "Kmm__mm__mm__mmK",
    "Kmm__mm__mm__mmK",
    "Kmm__mm__mm__mmK",
    "Kmm__mm__mm__mmK",
    "KmmmmmmmmmmmmmmK",
    "KgmmmmmmmmmmmmgK",
    "KKKKKKKKKKKKKKKK",
]
write_png(os.path.join(BASE, "block", "drain.png"), grid(DRAIN2, DR_PAL))

# ============ logic gate, alarm, wireless remote ============
# --- Logic Gate: circuit board with gold traces and an amber processor ---
LG_PAL = {"K": C(22, 30, 24), "b": C(30, 60, 40), "g": C(220, 180, 70),
          "a": C(255, 176, 60), "A": C(255, 224, 130), ".": C(38, 74, 50)}
LG = [
    "KKKKKKKKKKKKKKKK",
    "KbbbbbbbbbbbbbbK",
    "Kb.g..g..g..g.bK",
    "Kb.gggggggggg.bK",
    "Kb.g..aaaa..g.bK",
    "Kb.g.aAAAAa.g.bK",
    "Kb.g.aAAAAa.g.bK",
    "Kb.g.aAAAAa.g.bK",
    "Kb.g.aAAAAa.g.bK",
    "Kb.g..aaaa..g.bK",
    "Kb.gggggggggg.bK",
    "Kb.g..g..g..g.bK",
    "KbbbbbbbbbbbbbbK",
    "KKKKKKKKKKKKKKKK",
    "KKKKKKKKKKKKKKKK",
    "KKKKKKKKKKKKKKKK",
]
LG = [(r + "K" * 16)[:16] for r in LG]
write_png(os.path.join(BASE, "block", "logic_gate.png"), grid(LG, LG_PAL))

# --- Alarm: steel housing with a red warning bell ---
AL_PAL = {"K": C(25, 26, 30), "S": C(140, 146, 158), "m": C(80, 85, 96),
          "r": C(210, 55, 45), "R": C(250, 90, 70), "y": C(250, 210, 70),
          "d": C(52, 55, 64), ".": C(66, 70, 80)}
AL = [
    "KKKKKKKKKKKKKKKK",
    "KSSSSSSSSSSSSSSK",
    "KSmmmmmmmmmmmmSK",
    "KSm...rrrr...mSK",
    "KSm..rRRRRr..mSK",
    "KSm.rRRRRRRr.mSK",
    "KSm.rRRRRRRr.mSK",
    "KSm.rRRRRRRr.mSK",
    "KSm.rRRRRRRr.mSK",
    "KSm..rRRRRr..mSK",
    "KSm...yyyy...mSK",
    "KSm....yy....mSK",
    "KSmmmmmmmmmmmmSK",
    "KSSSSSSSSSSSSSSK",
    "KKKKKKKKKKKKKKKK",
    "KKKKKKKKKKKKKKKK",
]
AL = [(r + "K" * 16)[:16] for r in AL]
write_png(os.path.join(BASE, "block", "alarm.png"), grid(AL, AL_PAL))

# --- Wireless Remote item: dark handheld with a red button and antenna ---
RM_PAL = {".": CLEAR, "K": C(40, 42, 48), "m": C(80, 85, 96), "s": C(150, 156, 168),
          "r": C(225, 60, 55), "R": C(255, 120, 110), "g": C(120, 126, 140)}
RM = [
    ".......s........",
    ".......s........",
    "......ggg.......",
    "......KKK.......",
    ".....KmmmK......",
    ".....KrRrK......",
    ".....KRRRK......",
    ".....KrRrK......",
    ".....KmmmK......",
    ".....Km.mK......",
    ".....Km.mK......",
    ".....KmmmK......",
    ".....KmmmK......",
    "......KKK.......",
    "................",
    "................",
]
write_png(os.path.join(BASE, "item", "wireless_remote.png"), grid(RM, RM_PAL))

# --- Redstone Receiver front: the output port face ---
RXF = fix16([
    "KKKKKKKKKKKKKKKK",
    "KgSSSSSSSSSSSSgK",
    "KSmmmmmmmmmmmmSK",
    "KSm..........mSK",
    "KSm..rrrrrr..mSK",
    "KSm..r....r..mSK",
    "KSm..r.RR.r..mSK",
    "KSm..r.RR.r..mSK",
    "KSm..r....r..mSK",
    "KSm..rrrrrr..mSK",
    "KSm..........mSK",
    "KSm..........mSK",
    "KSmmmmmmmmmmmmSK",
    "KgSSSSSSSSSSSSgK",
    "KKKKKKKKKKKKKKKK",
    "KKKKKKKKKKKKKKKK",
]) 
write_png(os.path.join(BASE, "block", "redstone_receiver_front.png"), grid(RXF, pal2))

# ============ redesigned Display Pedestal: solid marble+gold base ============
_MB_L = C(238, 235, 228)   # marble light
_MB_M = C(214, 210, 201)   # marble mid
_MB_V = C(196, 191, 181)   # marble vein
_GOLD = C(214, 172, 66)    # gold
_GOLD_D = C(166, 128, 44)  # gold dark
_GOLD_L = C(244, 212, 120) # gold light

def _ped_top():
    rows = []
    cx, cy = 7.5, 7.5
    for y in range(16):
        row = []
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d < 1.8:
                c = _GOLD_L                      # inlaid centre where the item sits
            elif d < 2.6:
                c = _GOLD
            elif 4.2 <= d <= 5.4:
                c = _GOLD                        # medallion ring
            elif 5.4 < d <= 6.0:
                c = _GOLD_D
            else:
                c = _MB_V if ((x * 3 + y * 5) % 13 < 2) else (_MB_M if ((x + y) % 7 == 0) else _MB_L)
            # gold corner pips
            if (x, y) in [(1,1),(14,1),(1,14),(14,14),(2,1),(1,2),(13,1),(14,2),(1,13),(2,14),(14,13),(13,14)]:
                c = _GOLD
            row.append(c)
        rows.append(row)
    return rows

def _ped_side():
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if y in (0, 15):
                c = _GOLD_D
            elif y in (1, 2, 13, 14):
                c = _GOLD if (x % 4 != 0) else _GOLD_L
            else:
                c = _MB_V if ((x * 7 + y * 3) % 11 < 2) else (_MB_M if ((x * 2 + y) % 9 == 0) else _MB_L)
            row.append(c)
        rows.append(row)
    return rows

def _ped_bottom():
    return [[_MB_M if ((x + y) % 5 == 0) else _MB_L for x in range(16)] for y in range(16)]

write_png(os.path.join(BASE, "block", "pedestal_top.png"), _ped_top())
write_png(os.path.join(BASE, "block", "pedestal_side.png"), _ped_side())
write_png(os.path.join(BASE, "block", "pedestal_bottom.png"), _ped_bottom())

# --- Item Counter: sensor lens front, segmented counter screen back, tally casing ---
IC_PAL = {
    "M": C(40, 42, 48), "d": C(70, 74, 82), ".": C(55, 58, 66),
    "k": C(30, 32, 36), "r": C(100, 106, 116), "t": C(120, 126, 138),
    "_": C(15, 18, 24), "a": C(140, 88, 30), "p": C(80, 52, 22),
    "~": C(38, 30, 20), "A": C(255, 186, 70), "P": C(255, 150, 50),
    "c": C(60, 140, 180), "C": C(150, 235, 255),
}

IC_CASING = [
    "MMMMMMMMMMMMMMMM",
    "MddddddddddddddM",
    "Md.r........r.dM",
    "Md............dM",
    "MdkkkkkkkkkkkkdM",
    "Mdk..........kdM",
    "Mdk.t.t.t.t..kdM",
    "Mdk..........kdM",
    "MdkkkkkkkkkkkkdM",
    "Md............dM",
    "Md............dM",
    "Md............dM",
    "Md.r........r.dM",
    "Md............dM",
    "MddddddddddddddM",
    "MMMMMMMMMMMMMMMM",
]

def ic_display(edge, scr):
    # Clean screen — the live readout text is drawn over it by the renderer.
    return [
        "MMMMMMMMMMMMMMMM",
        "MddddddddddddddM",
        "Md.kkkkkkkkkk.dM",
        "Md.k" + edge * 8 + "k.dM",
    ] + ["Md.k" + scr * 8 + "k.dM" for _ in range(7)] + [
        "Md.k" + edge * 8 + "k.dM",
        "Md.kkkkkkkkkk.dM",
        "Md............dM",
        "MddddddddddddddM",
        "MMMMMMMMMMMMMMMM",
    ]

IC_SENSOR = [
    "MMMMMMMMMMMMMMMM",
    "MddddddddddddddM",
    "Md.r........r.dM",
    "Md....kkkk....dM",
    "Md...k....k...dM",
    "Md..k..cc..k..dM",
    "Md..k.cCCc.k..dM",
    "Md..k.cCCc.k..dM",
    "Md..k..cc..k..dM",
    "Md...k....k...dM",
    "Md....kkkk....dM",
    "Md............dM",
    "Md.r........r.dM",
    "Md............dM",
    "MddddddddddddddM",
    "MMMMMMMMMMMMMMMM",
]

write_png(os.path.join(BASE, "block", "item_counter.png"), grid(IC_CASING, IC_PAL))
write_png(os.path.join(BASE, "block", "item_counter_sensor.png"), grid(IC_SENSOR, IC_PAL))
write_png(os.path.join(BASE, "block", "item_counter_display.png"), grid(ic_display("p", "_"), IC_PAL))
write_png(os.path.join(BASE, "block", "item_counter_display_on.png"), grid(ic_display("A", "~"), IC_PAL))

# --- Item Magnet: dark metal casing with a red/blue bar magnet and steel poles ---
IM_PAL = {
    "M": C(40, 42, 48), "d": C(70, 74, 82), ".": C(55, 58, 66),
    "R": C(200, 60, 55), "B": C(60, 96, 200), "s": C(205, 210, 220),
    "r": C(150, 42, 40), "b": C(44, 70, 150),
}

# 10-wide content rows sit between "Md." and ".dM"; bar is steel-capped, red|blue halves.
def _im_rows():
    bg = "." * 10
    cap = ".sRRRBBBs."   # rounded ends
    mid = ".RRRRBBBB."   # solid core
    shine = ".rRRRBBBb."  # a touch of shading on the top row
    content = [bg, bg, cap, shine, mid, mid, mid, shine, cap, bg, bg, bg]
    rows = ["M" * 16, "M" + "d" * 14 + "M"]
    for c in content:
        rows.append("Md." + c + ".dM")
    rows.append("M" + "d" * 14 + "M")
    rows.append("M" * 16)
    return rows

write_png(os.path.join(BASE, "block", "item_magnet.png"), grid(_im_rows(), IM_PAL))

# --- Trash Can: full-bleed bin side (ridges + hazard band) and radial steel lid ---
TC_PAL = {
    "M": C(40, 42, 48), "d": C(70, 74, 82), ".": C(55, 58, 66),
    "s": C(200, 205, 215), "S": C(170, 176, 188), "R": C(200, 60, 55),
    "r": C(150, 42, 40), "k": C(30, 32, 36),
}

def _tc_side():
    rows = ["kkkkkkkkkkkkkkkk", "RRRRRRRRRRRRRRRR", "RRRRRRRRRRRRRRRR", "rrrrrrrrrrrrrrrr"]
    for y in range(4, 15):
        rows.append("".join("d" if x % 3 == 0 else "." for x in range(16)))
    rows.append("kkkkkkkkkkkkkkkk")
    return rows

def _tc_lid():
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            ring = min(x, y, 15 - x, 15 - y)
            row.append("k" if ring == 0 else ("S" if ring % 3 == 1 else "s"))
        rows.append("".join(row))
    return rows

write_png(os.path.join(BASE, "block", "trash_can.png"), grid(_tc_side(), TC_PAL))
write_png(os.path.join(BASE, "block", "trash_can_lid.png"), grid(_tc_lid(), TC_PAL))

# --- Item Magnet horseshoe + sensor eye dome + antenna parts (flat shaded tiles) ---
def _shaded(base, hi, lo):
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            n = (x * 73856093 ^ y * 19349663) % 7
            row.append(hi if (y < 2 or n == 0) else (lo if (y > 13 or n == 1) else base))
        rows.append(row)
    return rows

write_png(os.path.join(BASE, "block", "magnet_red.png"), _shaded(C(198, 58, 52), C(225, 92, 84), C(150, 42, 40)))
write_png(os.path.join(BASE, "block", "magnet_blue.png"), _shaded(C(58, 94, 198), C(92, 128, 225), C(42, 70, 150)))
write_png(os.path.join(BASE, "block", "magnet_steel.png"), _shaded(C(188, 194, 204), C(222, 226, 234), C(146, 152, 164)))
write_png(os.path.join(BASE, "block", "antenna_mast.png"), _shaded(C(120, 126, 138), C(160, 166, 178), C(88, 94, 106)))
write_png(os.path.join(BASE, "block", "antenna_tip_tx.png"), _shaded(C(220, 70, 60), C(255, 140, 120), C(170, 50, 44)))
write_png(os.path.join(BASE, "block", "antenna_tip_rx.png"), _shaded(C(70, 190, 200), C(130, 235, 245), C(50, 140, 150)))

SENSOR_EYE = [
    "kkkkkkkkkkkkkkkk",
    "kkkddddddddddkkk",
    "kkdddddddddddkkk"[:16],
    "kdddEEEEEEdddddk"[:16],
    "kddEEEEEEEEEdddk"[:16],
    "kdEEEWWWWEEEEddk"[:16],
    "kdEEWWWWWWEEEddk"[:16],
    "kdEEWWWWWWEEEddk"[:16],
    "kdEEEWWWWEEEEddk"[:16],
    "kddEEEEEEEEEdddk"[:16],
    "kdddEEEEEEdddddk"[:16],
    "kkdddddddddddkkk"[:16],
    "kkkddddddddddkkk",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
    "kkkkkkkkkkkkkkkk",
]
SENSOR_EYE = [(r + "k" * 16)[:16] for r in SENSOR_EYE]
eye_pal = {"k": C(30, 32, 36), "d": C(70, 74, 82), "E": C(220, 60, 60), "W": C(255, 210, 140)}
write_png(os.path.join(BASE, "block", "sensor_eye.png"), grid(SENSOR_EYE, eye_pal))
