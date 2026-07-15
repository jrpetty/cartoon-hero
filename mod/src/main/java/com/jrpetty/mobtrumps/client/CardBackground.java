package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.game.Category;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Paints a themed scene behind a card's portrait, chosen by the mob's
 * {@link Category}: a sunny farm, a deep ocean, a lava-lit Nether, a starry
 * End void, and so on. All drawing is stylised pixel-art built from fills and
 * gradients so it reads at any card scale, and it stays inside the portrait box
 * so the live mob renders cleanly on top.
 */
public final class CardBackground {

    private CardBackground() {
    }

    /** Draw the scene for {@code category} filling the box (x0,y0)-(x1,y1). */
    public static void draw(GuiGraphics g, Category category, int x0, int y0, int x1, int y1) {
        if (category == null) {
            g.fillGradient(x0, y0, x1, y1, 0xFFCFE4F2, 0xFFE8F2D9);
            return;
        }
        switch (category) {
            case FARM -> farm(g, x0, y0, x1, y1);
            case CREATURE -> creature(g, x0, y0, x1, y1);
            case VILLAGE -> village(g, x0, y0, x1, y1);
            case AQUATIC -> aquatic(g, x0, y0, x1, y1);
            case UNDEAD -> undead(g, x0, y0, x1, y1);
            case MONSTER -> monster(g, x0, y0, x1, y1);
            case END -> end(g, x0, y0, x1, y1);
            case NETHER -> nether(g, x0, y0, x1, y1);
            case ILLAGER -> illager(g, x0, y0, x1, y1);
            case BOSS -> boss(g, x0, y0, x1, y1);
        }
    }

    // --- scenes ---

    private static void farm(GuiGraphics g, int x0, int y0, int x1, int y1) {
        int horizon = y0 + (int) ((y1 - y0) * 0.58f);
        g.fillGradient(x0, y0, x1, horizon, 0xFF7FB8E6, 0xFFC3E2F2);
        g.fillGradient(x0, horizon, x1, y1, 0xFF74B93E, 0xFF4C8A26);
        // sun, top-right
        disc(g, x1 - 20, y0 + 15, 9, 0xFFFFE07A);
        disc(g, x1 - 20, y0 + 15, 5, 0xFFFFF4B4);
        // rolling hill mound
        disc(g, x0 + 34, horizon + 20, 30, 0xFF5FA531);
        // little red barn, bottom-left
        int bx = x0 + 8, by = horizon + 2, bw = 26, bh = y1 - by - 2;
        g.fill(bx, by + 5, bx + bw, y1 - 1, 0xFFA6392F);
        roof(g, bx - 2, by, bw + 4, 6, 0xFF7C2A22);
        g.fill(bx + bw / 2 - 4, y1 - 12, bx + bw / 2 + 4, y1 - 1, 0xFF54241C); // door
        // wheat rows along the very bottom
        for (int wx = x0 + 2; wx < x1 - 1; wx += 6) {
            g.fill(wx, y1 - 5, wx + 1, y1 - 1, 0xFFE7C24A);
        }
    }

    private static void creature(GuiGraphics g, int x0, int y0, int x1, int y1) {
        int horizon = y0 + (int) ((y1 - y0) * 0.6f);
        g.fillGradient(x0, y0, x1, horizon, 0xFF9AD2E4, 0xFFDCF0D2);
        g.fillGradient(x0, horizon, x1, y1, 0xFF62A83A, 0xFF3E7A22);
        // trees at both edges
        tree(g, x0 + 12, horizon + 2, 0xFF3F7A2A, 0xFF6B4A2A);
        tree(g, x1 - 16, horizon - 2, 0xFF357024, 0xFF5C3F24);
        tree(g, x0 + 30, horizon + 6, 0xFF2F6820, 0xFF5C3F24);
    }

    private static void village(GuiGraphics g, int x0, int y0, int x1, int y1) {
        int horizon = y0 + (int) ((y1 - y0) * 0.62f);
        g.fillGradient(x0, y0, x1, horizon, 0xFFF3C98A, 0xFFFCEBC8); // warm dusk
        g.fillGradient(x0, horizon, x1, y1, 0xFF8FA653, 0xFF6E7F3E);
        disc(g, x1 - 22, y0 + 14, 8, 0xFFFFD98A); // low sun
        house(g, x0 + 8, horizon - 14, 30, 0xFFE4D2A8, 0xFF7C4A2A);
        house(g, x1 - 40, horizon - 8, 26, 0xFFD8C79C, 0xFF6E3F24);
        g.fill(x0, y1 - 6, x1, y1 - 1, 0xFF9A6B3F); // dirt path
    }

    private static void aquatic(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fillGradient(x0, y0, x1, y1, 0xFF54AEDE, 0xFF0E3C64);
        // light rays from the surface
        for (int i = 0; i < 4; i++) {
            int rx = x0 + 12 + i * 34;
            for (int k = 0; k < (y1 - y0); k += 2) {
                g.fill(rx + k / 3, y0 + k, rx + k / 3 + 6, y0 + k + 1, 0x14FFFFFF);
            }
        }
        // bubbles
        bubble(g, x0 + 20, y1 - 18, 3);
        bubble(g, x0 + 24, y1 - 30, 2);
        bubble(g, x1 - 26, y1 - 22, 3);
        bubble(g, x1 - 30, y1 - 36, 2);
        // sandy floor
        g.fillGradient(x0, y1 - 6, x1, y1, 0xFF3E6A52, 0xFF2E5040);
    }

    private static void undead(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fillGradient(x0, y0, x1, y1, 0xFF1D2647, 0xFF3A2B48);
        disc(g, x0 + 22, y0 + 16, 8, 0xFFDDE2EC); // moon
        disc(g, x0 + 22, y0 + 16, 8, 0x22000000);
        // fog band
        g.fill(x0, y1 - 22, x1, y1 - 10, 0x22B8C4D8);
        // gravestones
        grave(g, x0 + 14, y1 - 18);
        grave(g, x0 + 40, y1 - 14);
        grave(g, x1 - 30, y1 - 20);
        g.fill(x0, y1 - 5, x1, y1 - 1, 0xFF2A2130); // dark earth
    }

    private static void monster(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fillGradient(x0, y0, x1, y1, 0xFF2C303C, 0xFF14171E);
        // stone speckle
        for (int i = 0; i < 26; i++) {
            int sx = x0 + (i * 37) % (x1 - x0 - 2);
            int sy = y0 + (i * 53) % (y1 - y0 - 2);
            g.fill(sx, sy, sx + 1, sy + 1, 0x30FFFFFF);
        }
        // mossy patches
        g.fill(x0 + 6, y1 - 10, x0 + 20, y1 - 4, 0x50447A2E);
        g.fill(x1 - 24, y1 - 8, x1 - 8, y1 - 3, 0x50447A2E);
        // ominous glowing eyes
        g.fill(x1 - 40, y0 + 22, x1 - 35, y0 + 25, 0xFF6BE04A);
        g.fill(x1 - 30, y0 + 22, x1 - 25, y0 + 25, 0xFF6BE04A);
    }

    private static void end(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fillGradient(x0, y0, x1, y1, 0xFF261A38, 0xFF120A1C);
        // stars
        for (int i = 0; i < 28; i++) {
            int sx = x0 + (i * 41) % (x1 - x0 - 2);
            int sy = y0 + (i * 29) % (y1 - y0 - 12);
            int c = (i % 3 == 0) ? 0xFFCBB0F0 : 0xFFEDE6FF;
            g.fill(sx, sy, sx + 1, sy + 1, c);
        }
        // end-stone platform
        g.fillGradient(x0 + 20, y1 - 12, x1 - 20, y1 - 2, 0xFFE4E6B0, 0xFFB9BC7E);
        g.fill(x0 + 20, y1 - 12, x1 - 20, y1 - 11, 0xFFF2F3C8);
    }

    private static void nether(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fillGradient(x0, y0, x1, y1, 0xFF4E1712, 0xFF83271A);
        // netherrack speckle
        for (int i = 0; i < 20; i++) {
            int sx = x0 + (i * 47) % (x1 - x0 - 2);
            int sy = y0 + (i * 31) % (y1 - y0 - 16);
            g.fill(sx, sy, sx + 1, sy + 1, 0x30000000);
        }
        // lava pool
        g.fillGradient(x0, y1 - 14, x1, y1, 0xFFFF8A1E, 0xFFFFD23A);
        g.fill(x0, y1 - 14, x1, y1 - 12, 0xFFFFF0A0); // bright surface line
        // rising embers
        ember(g, x0 + 24, y1 - 24);
        ember(g, x0 + 60, y1 - 34);
        ember(g, x1 - 30, y1 - 28);
    }

    private static void illager(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fillGradient(x0, y0, x1, y1, 0xFF31403C, 0xFF19231F);
        // rain streaks
        for (int i = 0; i < 14; i++) {
            int rx = x0 + (i * 23) % (x1 - x0 - 1);
            int ry = y0 + (i * 37) % (y1 - y0 - 12);
            g.fill(rx, ry, rx + 1, ry + 7, 0x30C8D4D0);
        }
        // ominous banner
        int bx = x1 - 34;
        g.fill(bx, y0 + 8, bx + 18, y0 + 40, 0xFF20302C);
        g.fill(bx + 5, y0 + 14, bx + 13, y0 + 22, 0xFFB9C4A8); // pale emblem
        g.fill(bx + 7, y0 + 24, bx + 11, y0 + 34, 0xFFB9C4A8);
        // dark ground
        g.fill(x0, y1 - 6, x1, y1 - 1, 0xFF141C19);
    }

    private static void boss(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, 0xFF08060E);
        int cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        // radial glow, largest & dimmest first
        disc(g, cx, cy, 46, 0x2233264A);
        disc(g, cx, cy, 32, 0x33403664);
        disc(g, cx, cy, 20, 0x33554888);
        // gold flecks
        for (int i = 0; i < 22; i++) {
            int sx = x0 + (i * 43) % (x1 - x0 - 2);
            int sy = y0 + (i * 59) % (y1 - y0 - 2);
            g.fill(sx, sy, sx + 1, sy + 1, 0xAAFFD54A);
        }
        // a small gold crown, top-centre
        int kx = cx - 9, ky = y0 + 6;
        g.fill(kx, ky + 4, kx + 18, ky + 8, 0xFFFFD54A);
        g.fill(kx, ky, kx + 3, ky + 6, 0xFFFFD54A);
        g.fill(kx + 8, ky - 2, kx + 11, ky + 6, 0xFFFFE68A);
        g.fill(kx + 15, ky, kx + 18, ky + 6, 0xFFFFD54A);
    }

    // --- primitives ---

    /** A blocky filled circle centred on (cx,cy). */
    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.floor(Math.sqrt((double) r * r - dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** A stepped triangular roof over a body of width w. */
    private static void roof(GuiGraphics g, int x, int y, int w, int h, int color) {
        for (int i = 0; i < h; i++) {
            int inset = (int) ((i / (float) h) * (w / 2f));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
    }

    private static void tree(GuiGraphics g, int cx, int groundY, int leaf, int trunk) {
        g.fill(cx - 2, groundY - 14, cx + 2, groundY, trunk);
        disc(g, cx, groundY - 20, 9, leaf);
        disc(g, cx - 6, groundY - 15, 6, leaf);
        disc(g, cx + 6, groundY - 15, 6, leaf);
    }

    private static void house(GuiGraphics g, int x, int y, int w, int wall, int roofColor) {
        int bh = 18;
        g.fill(x, y + 6, x + w, y + bh, wall);
        roof(g, x - 2, y, w + 4, 7, roofColor);
        g.fill(x + w / 2 - 3, y + bh - 8, x + w / 2 + 3, y + bh, 0xFF5A3A1E); // door
        g.fill(x + 3, y + 9, x + 8, y + 13, 0xFFB9D8E4); // window
    }

    private static void grave(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 10, y + 16, 0xFF8891A0);
        g.fill(x + 1, y - 2, x + 9, y + 4, 0xFF8891A0); // rounded top
        g.fill(x + 4, y + 3, x + 6, y + 12, 0xFF5A626E); // cross vertical
        g.fill(x + 2, y + 5, x + 8, y + 7, 0xFF5A626E);  // cross horizontal
    }

    private static void bubble(GuiGraphics g, int cx, int cy, int r) {
        disc(g, cx, cy, r, 0x40FFFFFF);
        g.fill(cx - 1, cy - r, cx + 1, cy - r + 1, 0x80FFFFFF);
    }

    private static void ember(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 2, y + 2, 0xFFFFB44A);
        g.fill(x + 1, y - 5, x + 2, y - 4, 0xC0FF8A2A);
        g.fill(x, y - 10, x + 1, y - 9, 0x80FF7A1A);
    }
}
