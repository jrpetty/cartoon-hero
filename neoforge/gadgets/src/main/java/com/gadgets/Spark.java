package com.gadgets;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A sparkline in a recessed well: a faint area fill, a one-pixel line, a lit
 * endpoint, and optionally a dashed alert line.
 *
 * <p>Drawn entirely from fills, so it costs nothing a text row doesn't already,
 * and scaled to the series' own range unless a fixed ceiling is given — a tank
 * is drawn against its capacity, so "half full" looks half full, while a rate
 * is drawn against its own recent best, so a slowdown is visible at any size.
 */
final class Spark {
    private static final int WELL = 0xFF0C0F13;
    private static final int FACE = 0xFF141820;
    private static final int GRID = 0xFF1E242E;
    private static final int RING = 0xFF2A303A;

    private Spark() {
    }

    /**
     * @param floor   bottom of the scale
     * @param ceiling top of the scale, or -1 to use the series' own maximum
     * @param mark    an alert level to draw dashed, or -1 for none
     */
    static void draw(GuiGraphics gfx, int x, int y, int w, int h, int[] hist,
                     long floor, long ceiling, long mark, int colour) {
        gfx.fill(x, y, x + w, y + h, WELL);
        gfx.fill(x + 1, y + 1, x + w - 1, y + h - 1, FACE);
        gfx.fill(x + 1, y + h / 2, x + w - 1, y + h / 2 + 1, GRID);
        gfx.renderOutline(x, y, w, h, RING);

        int n = hist.length;
        int ix = x + 2;
        int iw = w - 4;
        int iy = y + 2;
        int ih = h - 4;
        if (n < 2) {
            // Not enough to draw a line yet — a dotted baseline says "listening".
            for (int dx = 0; dx < iw; dx += 3) {
                gfx.fill(ix + dx, iy + ih - 1, ix + dx + 1, iy + ih, 0xFF3A404C);
            }
            return;
        }

        long top = ceiling;
        if (top < 0) {
            top = floor + 1;
            for (int v : hist) {
                top = Math.max(top, v);
            }
            if (mark > 0) {
                top = Math.max(top, mark + mark / 2);
            }
        }
        long span = Math.max(1, top - floor);

        if (mark > 0 && mark <= top) {
            int my = iy + ih - 1 - (int) ((mark - floor) * (ih - 1) / span);
            for (int dx = 0; dx < iw; dx += 4) {
                gfx.fill(ix + dx, my, ix + Math.min(iw, dx + 2), my + 1, 0xAAFF5555);
            }
        }

        int area = (colour & 0x00FFFFFF) | 0x30000000;
        int prevY = -1;
        for (int col = 0; col < iw; col++) {
            // Where along the series this column falls, interpolated between points.
            double t = col * (n - 1) / (double) Math.max(1, iw - 1);
            int i = (int) Math.floor(t);
            int j = Math.min(n - 1, i + 1);
            double v = hist[i] + (hist[j] - hist[i]) * (t - i);
            double clamped = Math.max(floor, Math.min(top, v));
            int py = iy + ih - 1 - (int) Math.round((clamped - floor) * (ih - 1) / span);
            gfx.fill(ix + col, py + 1, ix + col + 1, iy + ih, area);
            // Join steep steps vertically so the line never breaks into dots.
            int a = prevY < 0 ? py : Math.min(prevY, py);
            int b = prevY < 0 ? py : Math.max(prevY, py);
            gfx.fill(ix + col, a, ix + col + 1, b + 1, colour);
            prevY = py;
        }
        // The newest point, lit — "now" is the one value worth finding at a glance.
        gfx.fill(ix + iw - 2, prevY - 1, ix + iw + 1, prevY + 2, colour);
        gfx.fill(ix + iw - 1, prevY, ix + iw, prevY + 1, 0xFFFFFFFF);
    }
}
