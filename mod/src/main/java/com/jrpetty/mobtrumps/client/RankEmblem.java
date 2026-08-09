package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.RankTier;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The crest for a rank, drawn rather than shipped.
 *
 * <p>A shield silhouette built row by row, a bevelled field in the tier's
 * metal, and a glyph that escalates with the tier: one chevron at Bronze, two
 * at Silver, a crown at Gold, an open diamond at Platinum, a cut diamond at
 * Diamond, a star at Master. Division pips sit under the shield.
 *
 * <p>Flat fills only, for the same reason {@link TableArt} is: no texture to
 * ship, no atlas to keep in step, and it scales to any size a screen asks for —
 * the same crest reads at 14 pixels in a standings row and at 56 on a profile.
 */
public final class RankEmblem {

    private RankEmblem() {
    }

    /**
     * Width of the shield at each of sixteen rows, as a fraction of half-width.
     * A flat top with rounded shoulders that tapers to a point — read down the
     * list and you are looking at the outline.
     */
    private static final float[] PROFILE = {
            0.72f, 0.92f, 1.00f, 1.00f, 1.00f, 0.99f, 0.97f, 0.94f,
            0.90f, 0.85f, 0.78f, 0.70f, 0.60f, 0.47f, 0.32f, 0.16f
    };

    /** Draw the crest for {@code rating} with its top-left at x,y. */
    public static void draw(GuiGraphics g, int x, int y, int size, int rating) {
        draw(g, x, y, size, RankTier.of(rating), RankTier.division(rating), true);
    }

    /**
     * Draw a crest. {@code size} is the shield's width in pixels; its height
     * comes out about a fifth taller, plus room for pips when they are shown.
     */
    public static void draw(GuiGraphics g, int x, int y, int size, RankTier tier,
                            int division, boolean pips) {
        int rows = PROFILE.length;
        int half = Math.max(2, size / 2);
        int rowH = Math.max(1, Math.round(size * 1.18f / rows));
        int cx = x + half;

        int face = tier.rgb;
        int light = TableArt.lighten(face, 1.35f);
        int dark = TableArt.darken(face, 0.55f);
        int edge = TableArt.darken(face, 0.34f);

        // shadow, then the field, drawn as horizontal bands down the profile
        for (int r = 0; r < rows; r++) {
            int w = Math.max(1, Math.round(half * PROFILE[r]));
            int ry = y + r * rowH;
            g.fill(cx - w + 1, ry + 2, cx + w + 1, ry + rowH + 2, 0x55000000);
        }
        for (int r = 0; r < rows; r++) {
            int w = Math.max(1, Math.round(half * PROFILE[r]));
            int ry = y + r * rowH;
            // top third catches the light, bottom third falls into shade
            int band = r < rows / 3 ? TableArt.lighten(face, 1.12f)
                    : (r > rows * 2 / 3 ? TableArt.darken(face, 0.78f) : face);
            g.fill(cx - w, ry, cx + w, ry + rowH, band);
            // rim
            g.fill(cx - w, ry, cx - w + 1, ry + rowH, edge);
            g.fill(cx + w - 1, ry, cx + w, ry + rowH, edge);
        }
        // a highlight along the top edge is what makes it read as metal
        int topW = Math.max(1, Math.round(half * PROFILE[0]));
        g.fill(cx - topW, y, cx + topW, y + Math.max(1, rowH / 2), light);

        glyph(g, cx, y + Math.round(size * 0.46f), size, tier, light, dark);

        if (pips && size >= 12) {
            int shown = tier == RankTier.MASTER ? 1 : Math.max(1, 4 - division);
            int pipW = Math.max(2, size / 7);
            int gap = Math.max(1, pipW / 2);
            int total = shown * pipW + (shown - 1) * gap;
            int px = cx - total / 2;
            int py = y + rows * rowH + 2;
            for (int i = 0; i < shown; i++) {
                g.fill(px, py, px + pipW, py + Math.max(2, pipW - 1), light);
                g.fill(px, py, px + pipW, py + 1, 0x66FFFFFF);
                px += pipW + gap;
            }
        }
    }

    /** Total height {@link #draw} occupies, so a caller can lay out around it. */
    public static int height(int size, boolean pips) {
        int rowH = Math.max(1, Math.round(size * 1.18f / PROFILE.length));
        int h = PROFILE.length * rowH;
        return pips && size >= 12 ? h + 2 + Math.max(2, size / 7 - 1) : h;
    }

    /** The mark inside the shield — it escalates with the tier. */
    private static void glyph(GuiGraphics g, int cx, int cy, int size,
                              RankTier tier, int light, int dark) {
        int u = Math.max(1, size / 8);   // one glyph unit
        switch (tier) {
            case BRONZE -> chevron(g, cx, cy, u, light, dark);
            case SILVER -> {
                chevron(g, cx, cy - u, u, light, dark);
                chevron(g, cx, cy + u, u, light, dark);
            }
            case GOLD -> crown(g, cx, cy, u, light, dark);
            case PLATINUM -> diamond(g, cx, cy, u * 2, light, dark, false);
            case DIAMOND -> diamond(g, cx, cy, u * 2, light, dark, true);
            case MASTER -> star(g, cx, cy, u * 2, light, dark);
        }
    }

    private static void chevron(GuiGraphics g, int cx, int cy, int u, int light, int dark) {
        int arm = u * 2;
        for (int i = 0; i < arm; i++) {
            int t = Math.max(1, u / 2);
            g.fill(cx - i - t, cy - i + u, cx - i, cy - i + u + t, i == 0 ? light : dark);
            g.fill(cx + i, cy - i + u, cx + i + t, cy - i + u + t, i == 0 ? light : dark);
        }
    }

    private static void crown(GuiGraphics g, int cx, int cy, int u, int light, int dark) {
        int w = u * 3;
        int base = cy + u;
        g.fill(cx - w, base, cx + w, base + Math.max(1, u), light);
        // three points, the middle one taller
        for (int i = -1; i <= 1; i++) {
            int px = cx + i * (w - u / 2) - u / 2;
            int h = i == 0 ? u * 3 : u * 2;
            for (int r = 0; r < h; r++) {
                int shrink = Math.round(r * (u / (float) h));
                g.fill(px + shrink, base - r, px + u - shrink, base - r + 1,
                        r > h - 2 ? light : dark);
            }
        }
    }

    private static void diamond(GuiGraphics g, int cx, int cy, int r, int light,
                                int dark, boolean cut) {
        for (int dy = -r; dy <= r; dy++) {
            int w = r - Math.abs(dy);
            if (w <= 0) continue;
            g.fill(cx - w, cy + dy, cx + w, cy + dy + 1, dy < 0 ? light : dark);
        }
        if (cut) {
            // a facet line across the girdle, which is what separates a cut
            // stone from a plain rhombus at this size
            g.fill(cx - r + 1, cy, cx + r - 1, cy + 1, 0x66FFFFFF);
            g.fill(cx - r / 2, cy - r / 2, cx - r / 2 + 1, cy, 0x44FFFFFF);
            g.fill(cx + r / 2, cy - r / 2, cx + r / 2 + 1, cy, 0x44FFFFFF);
        }
    }

    private static void star(GuiGraphics g, int cx, int cy, int r, int light, int dark) {
        // a five-point star approximated by a bright core and four tapering arms
        for (int dy = -r; dy <= r; dy++) {
            int w = Math.max(0, (r - Math.abs(dy)) / 2);
            if (w > 0) g.fill(cx - w, cy + dy, cx + w, cy + dy + 1, dy < 0 ? light : dark);
        }
        for (int dx = -r; dx <= r; dx++) {
            int h = Math.max(0, (r - Math.abs(dx)) / 2);
            if (h > 0) g.fill(cx + dx, cy - h, cx + dx + 1, cy + h, dx < 0 ? light : dark);
        }
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
    }
}
