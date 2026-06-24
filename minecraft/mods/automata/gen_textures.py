#!/usr/bin/env python3
"""Generate simple 16x16 placeholder textures (and a mod icon) for Automata.

Pure standard library so it runs anywhere. Replace the PNGs under
src/main/resources/assets/automata/textures/ with real art whenever you like.
"""
import math
import struct
import zlib
from pathlib import Path

BASE = Path(__file__).parent / "src/main/resources/assets/automata/textures"


def write_png(path: Path, pixels, w, h):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            r, g, b, a = pixels[y * w + x]
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print("wrote", path)


def machine_tile(w, h, base, border, panel):
    """A bordered machine block face with an inset panel."""
    px = []
    for y in range(h):
        for x in range(w):
            edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
            inset = 4 <= x < w - 4 and 4 <= y < h - 4
            if edge:
                px.append(border)
            elif inset:
                px.append(panel)
            else:
                px.append(base)
    return px


def gear(w, h, metal, dark, bg=(0, 0, 0, 0)):
    cx, cy = (w - 1) / 2, (h - 1) / 2
    px = []
    for y in range(h):
        for x in range(w):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            tooth = 6.6 + 1.4 * math.cos(ang * 8)
            if d <= 2.2:
                px.append(bg)            # hub hole
            elif d <= 3.4:
                px.append(dark)
            elif d <= tooth:
                px.append(metal if (int(math.degrees(ang)) // 22) % 2 == 0 else dark)
            else:
                px.append(bg)
    return px


def frame(w, h, metal, dark, bg=(0, 0, 0, 0)):
    px = []
    for y in range(h):
        for x in range(w):
            edge = x in (0, w - 1) or y in (0, h - 1)
            inner = x in (3, w - 4) or y in (3, h - 4)
            if edge:
                px.append(dark)
            elif inner and 3 <= x <= w - 4 and 3 <= y <= h - 4:
                px.append(metal)
            elif 4 <= x <= w - 5 and 4 <= y <= h - 5:
                px.append(bg)
            else:
                px.append(metal)
    return px


def speckle(w, h, base, light, dark):
    """A grainy, ash-like fill (deterministic, no RNG)."""
    px = []
    for y in range(h):
        for x in range(w):
            n = (x * 7 + y * 13 + x * y) % 5
            if n == 0:
                px.append(light)
            elif n == 1:
                px.append(dark)
            else:
                px.append(base)
    return px


def generator_face(w, h, body, dark, vent):
    """A machine face with a glowing combustion vent in the middle."""
    px = []
    for y in range(h):
        for x in range(w):
            edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
            vent_zone = 5 <= x < w - 5 and 6 <= y < h - 3
            grill = vent_zone and (y % 2 == 0)
            if edge:
                px.append(dark)
            elif grill:
                px.append(dark)
            elif vent_zone:
                px.append(vent)
            else:
                px.append(body)
    return px


def belt(w, h, base, dark, arrow):
    """A conveyor surface with chevrons pointing toward -Z (top of texture)."""
    px = []
    for y in range(h):
        for x in range(w):
            # horizontal tread lines
            c = base if (y % 4 != 0) else dark
            # two chevrons pointing up
            for cy in (4, 10):
                if abs(x - 8) == (y - cy) and 0 <= (y - cy) <= 4:
                    c = arrow
            px.append(c)
    return px


def drawer_face(w, h, wood, dark, glass):
    """A wooden drawer with a glass readout window in the centre."""
    px = []
    for y in range(h):
        for x in range(w):
            if x == 0 or y == 0 or x == w - 1 or y == h - 1:
                px.append(dark)
            elif 4 <= x < w - 4 and 5 <= y < h - 5:
                px.append(glass)
            else:
                px.append(wood)
    return px


def hazard(w, h, a, b):
    """Diagonal hazard stripes (for the void hatch)."""
    px = []
    for y in range(h):
        for x in range(w):
            px.append(a if ((x + y) // 3) % 2 == 0 else b)
    return px


def multitool(w, h, head, handle, dark, bg=(0, 0, 0, 0)):
    """A diagonal tool: metal head top-left, handle to bottom-right."""
    px = []
    for y in range(h):
        for x in range(w):
            on_diag = abs((x - y)) <= 1
            head_zone = x + y <= 12
            if on_diag and head_zone:
                px.append(dark if (x + y) % 2 else head)
            elif on_diag:
                px.append(handle)
            elif head_zone and x + y <= 8 and (x <= 6 and y <= 6):
                px.append(head if (x + y) % 3 else dark)
            else:
                px.append(bg)
    return px


STEEL = (120, 128, 140, 255)
STEEL_DK = (70, 76, 86, 255)
ORANGE = (217, 119, 87, 255)
ORANGE_DK = (150, 78, 55, 255)
PANEL = (60, 64, 72, 255)
EMBER = (230, 110, 60, 255)
EMBER_DK = (120, 40, 20, 255)
WOOD = (140, 100, 60, 255)

write_png(BASE / "block/fabricator.png",
          machine_tile(16, 16, STEEL, STEEL_DK, ORANGE), 16, 16)
write_png(BASE / "block/forge_core.png",
          machine_tile(16, 16, STEEL_DK, (40, 44, 50, 255), EMBER), 16, 16)
write_png(BASE / "block/generator.png",
          generator_face(16, 16, (90, 70, 55, 255), (45, 34, 26, 255), EMBER), 16, 16)
# Crusher: heavy steel with a dark grinding aperture. Sawmill: wood body with a steel blade band.
write_png(BASE / "block/crusher.png",
          generator_face(16, 16, STEEL, STEEL_DK, (35, 38, 44, 255)), 16, 16)
write_png(BASE / "block/sawmill.png",
          machine_tile(16, 16, WOOD, (90, 64, 38, 255), STEEL), 16, 16)
# Circuit Assembler: green board with traces. Logic Circuit item: green chip.
write_png(BASE / "block/circuit_assembler.png",
          machine_tile(16, 16, (40, 120, 70, 255), (20, 70, 40, 255), (210, 200, 120, 255)), 16, 16)
write_png(BASE / "item/logic_circuit.png",
          machine_tile(16, 16, (50, 140, 80, 255), (25, 80, 45, 255), (220, 200, 110, 255)), 16, 16)
# Power Conduit: orange cable core. Power Pylon: blue emitter with a bright core.
write_png(BASE / "block/conduit.png",
          machine_tile(16, 16, (200, 120, 60, 255), (120, 70, 35, 255), (240, 170, 90, 255)), 16, 16)
write_png(BASE / "block/pylon.png",
          generator_face(16, 16, (60, 90, 130, 255), (30, 46, 70, 255), (120, 200, 240, 255)), 16, 16)
# Conveyor belt with direction chevrons; Drawer with a readout window; Void hazard stripes.
write_png(BASE / "block/conveyor.png",
          belt(16, 16, (60, 64, 70, 255), (40, 43, 48, 255), (235, 205, 70, 255)), 16, 16)
write_png(BASE / "block/drawer.png",
          drawer_face(16, 16, (140, 100, 60, 255), (90, 64, 38, 255), (120, 200, 220, 255)), 16, 16)
write_png(BASE / "block/void_hatch.png",
          hazard(16, 16, (30, 30, 34, 255), (210, 60, 60, 255)), 16, 16)
# Recycler: red-tinted grinder. Fluid Pump: cyan body with a fluid port.
write_png(BASE / "block/recycler.png",
          generator_face(16, 16, (130, 70, 60, 255), (70, 34, 30, 255), (210, 200, 200, 255)), 16, 16)
write_png(BASE / "block/fluid_pump.png",
          machine_tile(16, 16, (70, 150, 160, 255), (35, 80, 90, 255), (160, 220, 230, 255)), 16, 16)
# Auto-Miner: steel body with a dark downward drill aperture. Collector: a glassy blue intake.
write_png(BASE / "block/miner.png",
          generator_face(16, 16, STEEL_DK, (38, 42, 48, 255), (150, 150, 156, 255)), 16, 16)
write_png(BASE / "block/collector.png",
          machine_tile(16, 16, (90, 150, 190, 255), (45, 80, 110, 255), (170, 220, 245, 255)), 16, 16)
# Logistics Router: steel body with a green routing core.
write_png(BASE / "block/router.png",
          machine_tile(16, 16, STEEL, STEEL_DK, (70, 190, 110, 255)), 16, 16)
# Item Sorter: steel body with a purple sorting core.
write_png(BASE / "block/sorter.png",
          machine_tile(16, 16, STEEL, STEEL_DK, (150, 90, 200, 255)), 16, 16)
# Solar Array: dark frame with blue photovoltaic cells.
write_png(BASE / "block/solar_array.png",
          machine_tile(16, 16, (40, 50, 80, 255), (20, 24, 36, 255), (60, 120, 210, 255)), 16, 16)
# Thermal Generator: steel body with a molten lava vent.
write_png(BASE / "block/thermal_generator.png",
          generator_face(16, 16, STEEL_DK, (40, 44, 50, 255), (240, 140, 40, 255)), 16, 16)
# Capacitor Bank: steel body with a blue charge core.
write_png(BASE / "block/capacitor.png",
          machine_tile(16, 16, STEEL, STEEL_DK, (80, 120, 230, 255)), 16, 16)
write_png(BASE / "item/iron_gear.png", gear(16, 16, STEEL, STEEL_DK), 16, 16)
write_png(BASE / "item/machine_frame.png", frame(16, 16, STEEL, STEEL_DK), 16, 16)
write_png(BASE / "item/ash.png",
          speckle(16, 16, (110, 110, 114, 255), (170, 170, 174, 255), (70, 70, 74, 255)), 16, 16)
write_png(BASE / "item/iron_dust.png",
          speckle(16, 16, (150, 152, 158, 255), (195, 197, 202, 255), (96, 98, 104, 255)), 16, 16)
write_png(BASE / "item/gold_dust.png",
          speckle(16, 16, (224, 188, 70, 255), (255, 226, 120, 255), (170, 130, 40, 255)), 16, 16)
write_png(BASE / "item/copper_dust.png",
          speckle(16, 16, (200, 116, 80, 255), (236, 150, 110, 255), (150, 78, 50, 255)), 16, 16)
write_png(BASE / "item/pulsar_multitool.png",
          multitool(16, 16, ORANGE, WOOD, ORANGE_DK), 16, 16)
write_png(BASE / "item/logistics_wrench.png",
          multitool(16, 16, (70, 190, 110, 255), STEEL, (40, 120, 70, 255)), 16, 16)
# Upgrade modules: gear-shaped circuit chips.
write_png(BASE / "item/speed_upgrade.png", gear(16, 16, (90, 200, 235, 255), (40, 110, 150, 255)), 16, 16)
write_png(BASE / "item/efficiency_upgrade.png", gear(16, 16, (235, 205, 90, 255), (150, 120, 40, 255)), 16, 16)

# ---- Machine GUI (256x256): container background + progress-arrow strip ----
def gen_machine_gui():
    w = h = 256
    img = [(0, 0, 0, 0)] * (w * h)

    def px(x, y, c):
        if 0 <= x < w and 0 <= y < h:
            img[y * w + x] = c

    def rect(x0, y0, x1, y1, c):
        for yy in range(y0, y1):
            for xx in range(x0, x1):
                px(xx, yy, c)

    panel = (198, 198, 198, 255)
    light = (255, 255, 255, 255)
    dark = (85, 85, 85, 255)
    slot_bg = (139, 139, 139, 255)

    bw, bh = 176, 166
    # Main panel with a bevelled border.
    rect(0, 0, bw, bh, panel)
    rect(0, 0, bw, 1, light)
    rect(0, 0, 1, bh, light)
    rect(0, bh - 1, bw, bh, dark)
    rect(bw - 1, 0, bw, bh, dark)

    def slot(sx, sy):
        rect(sx, sy, sx + 18, sy + 18, dark)
        rect(sx + 1, sy + 1, sx + 17, sy + 17, slot_bg)

    slot(55, 34)   # input  (addSlot 56,35)
    slot(115, 34)  # output (addSlot 116,35)
    for row in range(3):
        for col in range(9):
            slot(7 + col * 18, 83 + row * 18)
    for col in range(9):
        slot(7 + col * 18, 141)

    def arrow_mask(ax, ay):
        if 0 <= ax <= 15 and 6 <= ay <= 10:
            return True
        if 16 <= ax <= 23 and abs(ay - 8) <= (23 - ax):
            return True
        return False

    # Empty arrow on the panel, filled arrow strip at u=176.
    for ay in range(17):
        for ax in range(24):
            if arrow_mask(ax, ay):
                px(79 + ax, 34 + ay, (160, 160, 160, 255))
                px(176 + ax, 0 + ay, (230, 150, 60, 255))

    out = Path(__file__).parent / "src/main/resources/assets/automata/textures/gui/machine.png"
    write_png(out, img, w, h)


gen_machine_gui()

# 128x128 mod icon — an orange-to-steel gradient with a gear stamped in.
icon = []
g = gear(128, 128, (235, 235, 240, 255), (180, 120, 90, 255), bg=None)
for y in range(128):
    for x in range(128):
        gp = g[y * 128 + x]
        if gp is not None:
            icon.append(gp)
        else:
            t = (x + y) / 254
            icon.append((int(217 - 100 * t), int(119 - 50 * t), int(87 + 20 * t), 255))
write_png(Path(__file__).parent / "src/main/resources/assets/automata/icon.png",
          icon, 128, 128)
