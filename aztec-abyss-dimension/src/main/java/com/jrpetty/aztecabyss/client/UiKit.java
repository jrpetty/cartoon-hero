package com.jrpetty.aztecabyss.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The one ink set, and the handful of shapes every screen in the mod is built
 * from.
 *
 * <p>Every screen used to carry its own copy of the palette, its own four-fill
 * border and its own colour pulse - ten screens, ten slightly different greys,
 * and a "faint" ink so close to the backdrop that half of each screen's
 * secondary text could not be read on a normal monitor. One table, one set of
 * shapes, and inks chosen against the actual ground they sit on.
 *
 * <h2>Inks</h2>
 *
 * <p>{@link #TEXT} for what you came to read, {@link #TEXT_DIM} for what
 * explains it, {@link #TEXT_FAINT} for labels and hints - all three readable on
 * {@link #PANEL}. {@link #TEXT_MUTED} is decoration only: rules, separators,
 * the dot between two figures. Nothing a player needs is ever drawn in it.
 */
public final class UiKit {

    // --- the ground ------------------------------------------------------
    public static final int BG_TOP = 0xFF100E17;
    public static final int BG_BOTTOM = 0xFF06050A;

    // --- surfaces ---------------------------------------------------------
    public static final int PANEL = 0xFF171622;
    public static final int PANEL_HOT = 0xFF201E2E;
    public static final int PANEL_DEEP = 0xFF0D0C13;
    public static final int EDGE = 0xFF35324A;
    public static final int EDGE_HOT = 0xFF6E6A8C;
    private static final int SHADOW = 0x90000000;

    // --- inks -------------------------------------------------------------
    public static final int TEXT = 0xFFEEEBF7;
    public static final int TEXT_DIM = 0xFFB6B2CA;
    public static final int TEXT_FAINT = 0xFF8A85A3;
    public static final int TEXT_MUTED = 0xFF5E5A76;

    // --- accents ------------------------------------------------------------
    public static final int GOLD = 0xFFFFC94A;
    public static final int AMBER = 0xFFE0A040;
    public static final int RED = 0xFFE5534B;
    public static final int DEEP_RED = 0xFFB0302A;
    public static final int GREEN = 0xFF5FD38A;
    public static final int CYAN = 0xFF4FD4E4;
    public static final int BLUE = 0xFF6AB7FF;
    public static final int PURPLE = 0xFFD08CF0;

    /** The maze screens' backdrop wash: daylight through leaves, not torchlight. */
    public static final int MAZE_GLOW = 0x2240B5A0;

    private UiKit() {
    }

    // ------------------------------------------------------------------
    // Ground
    // ------------------------------------------------------------------

    /**
     * The backdrop: a near-black gradient, opaque, with an optional wash of
     * colour falling from the top edge.
     *
     * <p>Opaque on purpose. A screen full of type over a live, moving world
     * reads as out of focus whatever the contrast of the type itself.
     */
    public static void backdrop(GuiGraphics g, int w, int h, int glow) {
        g.fillGradient(0, 0, w, h, BG_TOP, BG_BOTTOM);
        if ((glow >>> 24) != 0) {
            g.fillGradient(0, 0, w, Math.max(1, h * 2 / 5), glow, glow & 0x00FFFFFF);
        }
    }

    // ------------------------------------------------------------------
    // Shapes
    // ------------------------------------------------------------------

    /** A panel in the standard surface and edge. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        panel(g, x, y, w, h, PANEL, EDGE);
    }

    /**
     * A raised panel: a soft drop shadow, the fill, a one-pixel edge, and a
     * faint sheen along the top so it reads as a surface rather than a hole.
     */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int edge) {
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, SHADOW);
        g.fill(x, y, x + w, y + h, fill);
        outline(g, x, y, w, h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x12FFFFFF);
    }

    /** A one-pixel rectangle outline. */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y + 1, x + 1, y + h - 1, colour);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    /** A horizontal hairline. */
    public static void rule(GuiGraphics g, int x0, int x1, int y, int colour) {
        g.fill(x0, y, x1, y + 1, colour);
    }

    /**
     * The one ornament: a hairline with a small stepped pyramid at its centre.
     * Used under mastheads, where it reads as carved stone at a glance and
     * costs half a dozen rectangles.
     */
    public static void fret(GuiGraphics g, int cx, int y, int halfWidth, int colour) {
        int soft = alpha(colour, 0x70);
        g.fill(cx - halfWidth, y + 2, cx - 10, y + 3, soft);
        g.fill(cx + 10, y + 2, cx + halfWidth, y + 3, soft);
        g.fill(cx - 8, y + 2, cx + 8, y + 3, colour);
        g.fill(cx - 5, y + 1, cx + 5, y + 2, colour);
        g.fill(cx - 2, y, cx + 2, y + 1, colour);
        for (int x = cx - 16; x > cx - halfWidth + 2; x -= 8) {
            g.fill(x, y + 1, x + 2, y + 2, soft);
        }
        for (int x = cx + 14; x < cx + halfWidth - 4; x += 8) {
            g.fill(x, y + 1, x + 2, y + 2, soft);
        }
    }

    /**
     * A small tag - a dark plate topped with a line of its own colour, the
     * text in that colour. Returns the width it took.
     */
    public static int tag(GuiGraphics g, Font font, String text, int x, int y, int colour) {
        int w = tagWidth(font, text);
        g.fill(x, y, x + w, y + 12, 0xFF07060B);
        g.fill(x, y, x + w, y + 1, colour);
        g.drawString(font, text, x + 4, y + 3, colour, true);
        return w;
    }

    public static int tagWidth(Font font, String text) {
        return font.width(text) + 8;
    }

    /** A filled bar on a dark track, with a highlight line along the fill. */
    public static void meter(GuiGraphics g, int x, int y, int w, int h, float fraction, int colour) {
        float f = Math.max(0.0f, Math.min(1.0f, fraction));
        int fill = Math.round(w * f);
        g.fill(x, y, x + w, y + h, 0xFF0A0910);
        if (fill > 0) {
            g.fill(x, y, x + fill, y + h, colour);
            g.fill(x, y, x + fill, y + 1, lerp(colour, 0xFFFFFFFF, 0.35f));
        }
    }

    /**
     * A horizontal gradient, which {@link GuiGraphics} does not have: it only
     * shades top to bottom. Drawn as a run of narrow strips, which at GUI
     * scale is indistinguishable from a true gradient.
     */
    public static void hGradient(GuiGraphics g, int x0, int y0, int x1, int y1, int left, int right) {
        int w = x1 - x0;
        if (w <= 0 || y1 <= y0) {
            return;
        }
        int steps = Math.min(w, 32);
        for (int i = 0; i < steps; i++) {
            int a = x0 + w * i / steps;
            int b = x0 + w * (i + 1) / steps;
            g.fill(a, y0, b, y1, lerp(left, right, (i + 0.5f) / steps));
        }
    }

    // ------------------------------------------------------------------
    // Type
    // ------------------------------------------------------------------

    /**
     * The masthead every screen opens with: a small label line, then the
     * title at double size, both centred. Returns the y just under it.
     */
    public static int masthead(GuiGraphics g, Font font, String eyebrow, String title, int cx, int y, int colour) {
        if (eyebrow != null && !eyebrow.isEmpty()) {
            g.drawCenteredString(font, eyebrow, cx, y, TEXT_FAINT);
            y += 11;
        }
        big(g, font, Component.literal(title), cx, y, 2.0f, colour);
        return y + 19;
    }

    /** Centred text at a scale, with its shadow. */
    public static void big(GuiGraphics g, Font font, Component text, int cx, int y, float scale, int colour) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawCenteredString(font, text, 0, 0, colour);
        g.pose().popPose();
    }

    /** A label on the left and a value right-aligned to {@code right}, on one line. */
    public static void row(GuiGraphics g, Font font, String label, String value,
                           int x, int right, int y, int labelColour, int valueColour) {
        g.drawString(font, label, x, y, labelColour, true);
        g.drawString(font, value, right - font.width(value), y, valueColour, true);
    }

    // ------------------------------------------------------------------
    // Colour
    // ------------------------------------------------------------------

    /** A colour scaled toward black by {@code t}, alpha forced opaque. */
    public static int pulse(int colour, float t) {
        int r = (int) (((colour >> 16) & 0xFF) * t);
        int gr = (int) (((colour >> 8) & 0xFF) * t);
        int b = (int) ((colour & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    /** The colour with its alpha replaced. */
    public static int alpha(int colour, int alpha) {
        return (alpha << 24) | (colour & 0x00FFFFFF);
    }

    /** Straight interpolation of all four channels. */
    public static int lerp(int a, int b, float t) {
        float u = Math.max(0.0f, Math.min(1.0f, t));
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            out |= (Math.round(ca + (cb - ca) * u) & 0xFF) << shift;
        }
        return out;
    }

    /** Colour codes out. Names arrive from the server with their colours on. */
    public static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }
}
