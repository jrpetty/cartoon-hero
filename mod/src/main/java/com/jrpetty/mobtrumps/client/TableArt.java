package com.jrpetty.mobtrumps.client;

import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The furniture a card table is made of: felt, light, a timber rail, brass
 * fittings and bevelled panels.
 *
 * <p>Split out from the screen that uses it because none of it knows anything
 * about Bluff — it is scenery, and a second table would want the same pieces.
 *
 * <p>Everything here is built from flat fills. That is a deliberate limit
 * rather than a shortcut: it means no texture to ship, no atlas to keep in
 * step, and — for the rotated pieces — nothing that can go wrong the way
 * rotating a card carrying a live mob model can, since the only thing being
 * turned is a rectangle.
 */
public final class TableArt {

    public static final int FELT_EDGE = 0xFF0C2019;
    public static final int FELT_MID = 0xFF14372B;
    public static final int FELT_LIT = 0xFF1E5040;

    public static final int RAIL_FACE = 0xFF40291A;
    public static final int RAIL_LIGHT = 0xFF6E4A2C;
    public static final int RAIL_DARK = 0xFF1D1109;

    public static final int BRASS = 0xFFE9C46A;
    public static final int BRASS_HI = 0xFFF7E3AE;
    public static final int BRASS_DIM = 0xFF8A6A2A;
    public static final int BRASS_DARK = 0xFF4E3A16;

    public static final int SLATE = 0xFF16261E;
    public static final int SLATE_HI = 0xFF2A4438;

    public static final int INK = 0xFFF4EEE0;
    public static final int DIM = 0xFF9DB3A6;
    public static final int FAINT = 0xFF5B7266;
    public static final int GOOD = 0xFF63D97A;
    public static final int BAD = 0xFFE2564C;

    /** Width of the timber rail around the table. */
    public static final int RAIL = 7;

    private TableArt() {
    }

    /** Felt, a warm pool of light over the middle of it, and darkened edges. */
    public static void felt(GuiGraphics g, int width, int height, int lightX, int lightY) {
        g.fillGradient(0, 0, width, height, FELT_MID, FELT_EDGE);
        pool(g, lightX, lightY, Math.max(width, height) / 2, FELT_LIT, 0x2C);
        vignette(g, width, height);
    }

    /**
     * A soft round-ish pool of colour, built from nested rectangles that shrink
     * faster than they fade. Squares would read as a box; stepping the corners
     * in twice as fast rounds them enough to pass for light at this scale.
     */
    public static void pool(GuiGraphics g, int cx, int cy, int radius, int color, int peakAlpha) {
        int steps = 18;
        for (int i = steps; i >= 1; i--) {
            float t = i / (float) steps;
            int r = Math.round(radius * t);
            int inset = Math.round(r * 0.30f);
            int alpha = Math.round(peakAlpha * (1f - t) * (1f - t));
            if (alpha <= 0) {
                continue;
            }
            int argb = (alpha << 24) | (color & 0x00FFFFFF);
            g.fill(cx - r, cy - r + inset, cx + r, cy + r - inset, argb);
            g.fill(cx - r + inset, cy - r, cx + r - inset, cy + r, argb);
        }
    }

    /** Darkness creeping in from every edge, heavier in the corners. */
    public static void vignette(GuiGraphics g, int width, int height) {
        int rings = 22;
        int step = Math.max(1, Math.min(width, height) / (rings * 2));
        for (int i = 0; i < rings; i++) {
            int inset = i * step;
            int alpha = Math.round(14f * (1f - i / (float) rings));
            if (alpha <= 0) {
                continue;
            }
            int argb = (alpha << 24);
            g.fill(inset, inset, width - inset, inset + step, argb);
            g.fill(inset, height - inset - step, width - inset, height - inset, argb);
            g.fill(inset, inset, inset + step, height - inset, argb);
            g.fill(width - inset - step, inset, width - inset, height - inset, argb);
        }
    }

    /** The timber rail around the table, with a lit top edge and brass studs. */
    public static void rail(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, RAIL, RAIL_FACE);
        g.fill(0, height - RAIL, width, height, RAIL_FACE);
        g.fill(0, 0, RAIL, height, RAIL_FACE);
        g.fill(width - RAIL, 0, width, height, RAIL_FACE);
        // light falls from above: the top rail catches it, the bottom is in shade
        g.fill(0, 0, width, 1, RAIL_LIGHT);
        g.fill(0, height - 1, width, height, RAIL_DARK);
        // the inner lip, which is what makes it read as a raised edge
        g.fill(RAIL - 1, RAIL - 1, width - RAIL + 1, RAIL, RAIL_DARK);
        g.fill(RAIL - 1, height - RAIL, width - RAIL + 1, height - RAIL + 1, RAIL_LIGHT);
        g.fill(RAIL - 1, RAIL - 1, RAIL, height - RAIL + 1, RAIL_DARK);
        g.fill(width - RAIL, RAIL - 1, width - RAIL + 1, height - RAIL + 1, RAIL_DARK);
        for (int[] c : new int[][]{{2, 2}, {width - 5, 2}, {2, height - 5}, {width - 5, height - 5}}) {
            g.fill(c[0], c[1], c[0] + 3, c[1] + 3, BRASS_DIM);
            g.fill(c[0], c[1], c[0] + 2, c[1] + 1, BRASS_HI);
        }
    }

    /**
     * A bevelled panel: a lit top-left edge and a shaded bottom-right one, which
     * is the whole trick to making a flat rectangle look like an object.
     */
    public static void bevel(GuiGraphics g, int x, int y, int w, int h, int face,
                             int light, int dark) {
        g.fill(x, y, x + w, y + h, face);
        g.fill(x, y, x + w, y + 1, light);
        g.fill(x, y, x + 1, y + h, light);
        g.fill(x, y + h - 1, x + w, y + h, dark);
        g.fill(x + w - 1, y, x + w, y + h, dark);
    }

    /** A dark slate panel in a brass surround — the mounting for anything read. */
    public static void plate(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x66000000);
        bevel(g, x, y, w, h, SLATE, SLATE_HI, 0xFF0A140F);
        g.renderOutline(x, y, w, h, accent);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
    }

    /** One card back, turned. Safe at any angle: it is nothing but rectangles. */
    public static void back(GuiGraphics g, int cx, int cy, int w, int h, float degrees,
                            int edge) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(degrees));
        int hw = w / 2;
        int hh = h / 2;
        g.fill(-hw + 1, -hh + 2, hw + 1, hh + 2, 0x77000000);
        g.fillGradient(-hw, -hh, hw, hh, 0xFF2A2154, 0xFF171034);
        g.renderOutline(-hw, -hh, w, h, edge);
        g.fill(-hw + 1, -hh + 1, hw - 1, -hh + 2, 0x33FFFFFF);
        if (w >= 12 && h >= 14) {
            // the deck's lozenge, shrunk to a mark
            int r = Math.max(2, Math.min(hw, hh) - 3);
            for (int dy = -r; dy <= r; dy++) {
                int half = r - Math.abs(dy);
                g.fill(-half, dy, half + 1, dy + 1, 0x55E3C071);
            }
        }
        pose.popPose();
    }

    /**
     * A brass button with real depth, and a pressed look when it is not
     * available. Returns nothing — the caller owns the hit rectangle, so the
     * drawing can never disagree with what is clickable.
     */
    public static void button(GuiGraphics g, int x, int y, int w, int h, int face,
                              boolean hot, boolean enabled) {
        if (hot && enabled) {
            g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x33E9C46A);
        }
        g.fill(x + 1, y + 2, x + w + 1, y + h + 2, 0x77000000);
        int lit = enabled ? lighten(face, hot ? 1.45f : 1.22f) : lighten(face, 1.05f);
        int shade = darken(face, 0.62f);
        bevel(g, x, y, w, h, enabled ? (hot ? lighten(face, 1.15f) : face) : darken(face, 0.7f),
                lit, shade);
        g.renderOutline(x, y, w, h, enabled ? (hot ? BRASS : BRASS_DIM) : 0xFF2A3A33);
        // a glint across the top third, so it reads as metal rather than paint
        g.fill(x + 2, y + 2, x + w - 2, y + 2 + Math.max(1, h / 5), 0x1AFFFFFF);
    }

    public static int lighten(int argb, float by) {
        int a = argb >>> 24;
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * by));
        int gg = Math.min(255, Math.round(((argb >> 8) & 0xFF) * by));
        int b = Math.min(255, Math.round((argb & 0xFF) * by));
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    /**
     * Same multiply as {@link #lighten}, named for what a caller passing a
     * factor below one is actually asking for. Kept separate because
     * {@code darken(face, 0.62f)} reads as an intention and
     * {@code lighten(face, 0.62f)} reads as a mistake.
     */
    public static int darken(int argb, float by) {
        return lighten(argb, by);
    }

    /** A small tick or cross, drawn rather than typed — the default font has
     *  no glyph for either, and a missing one prints as a box. */
    public static void mark(GuiGraphics g, int x, int y, boolean tick, int color) {
        if (tick) {
            g.fill(x, y + 2, x + 1, y + 4, color);
            g.fill(x + 1, y + 3, x + 2, y + 5, color);
            g.fill(x + 2, y + 2, x + 3, y + 4, color);
            g.fill(x + 3, y + 1, x + 4, y + 3, color);
            g.fill(x + 4, y, x + 5, y + 2, color);
        } else {
            for (int i = 0; i < 5; i++) {
                g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
                g.fill(x + 4 - i, y + i, x + 5 - i, y + i + 1, color);
            }
        }
    }

    /** Fade a colour to a given alpha without touching its hue. */
    public static int alpha(int argb, int a) {
        return ((a & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }
}
