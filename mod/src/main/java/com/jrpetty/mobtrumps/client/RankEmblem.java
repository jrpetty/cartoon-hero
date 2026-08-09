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

    /** The pinline is brass on every tier, tying the crests to the tables. */
    private static final int BRASS_PIN = 0xFFF7E3AE;

    private RankEmblem() {
    }

    /**
     * Width of the shield at each of sixteen rows, as a fraction of half-width.
     * Read down the list and you are looking at the outline: a flat top, sides
     * that barely move for the first half, then a curve that accelerates into a
     * point. The acceleration matters — an even taper leaves a narrow stub at
     * the bottom and the crest reads as a balloon rather than a shield.
     */
    private static final float[] PROFILE = {
            1.00f, 1.00f, 1.00f, 0.99f, 0.98f, 0.96f, 0.94f, 0.91f,
            0.87f, 0.82f, 0.76f, 0.68f, 0.58f, 0.45f, 0.29f, 0.11f
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
        // the inner pinline — a second, lighter line inset one pixel — is what
        // separates a crest from a coloured shape. Only at sizes with room.
        if (size >= 16) {
            int inset = Math.max(1, size / 11);
            for (int r = inset; r < rows - inset; r++) {
                int w = Math.max(1, Math.round(half * PROFILE[r])) - inset;
                if (w <= 1) continue;
                int ry = y + r * rowH;
                int pin = TableArt.alpha(BRASS_PIN, 0x50);
                g.fill(cx - w, ry, cx - w + 1, ry + rowH, pin);
                g.fill(cx + w - 1, ry, cx + w, ry + rowH, pin);
            }
            int wTop = Math.max(1, Math.round(half * PROFILE[inset])) - inset;
            g.fill(cx - wTop, y + inset * rowH, cx + wTop, y + inset * rowH + 1,
                    TableArt.alpha(BRASS_PIN, 0x50));
        }
        // one glint on the top-left shoulder, so every crest catches the same light
        g.fill(cx - topW + 1, y + rowH, cx - topW + 1 + Math.max(1, size / 8),
                y + rowH + 1, 0x66FFFFFF);

        glyph(g, cx, y + Math.round(size * 0.46f), size, tier, light, dark);

        // Master has no divisions, so a lone pip under it would be a rank
        // marker that marks nothing — and directly below the shield's point it
        // reads as a little stand rather than a rating.
        if (pips && size >= 12 && tier != RankTier.MASTER) {
            int shown = Math.max(1, 4 - division);
            int pipW = Math.max(2, size / 7);
            int gap = Math.max(1, pipW / 2);
            int total = shown * pipW + (shown - 1) * gap;
            int px = cx - total / 2;
            int py = y + rows * rowH + 4;   // clearance, or they look like a base
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
        return pips && size >= 12 ? h + 4 + Math.max(2, size / 7 - 1) : h;
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
            case MASTER -> star(g, cx, cy, u * 3, light, dark);
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
        // three solid triangles standing on a band, middle one tallest. Drawn
        // in the dark metal with lit tips: at thirteen pixels a crown has to be
        // shape and contrast, because there is no room for detail.
        for (int i = -1; i <= 1; i++) {
            int peak = cx + i * (w - u);
            int h = i == 0 ? u * 3 : u * 2;
            for (int r = 0; r < h; r++) {
                int half = Math.max(1, Math.round(u * (1f - r / (float) h)));
                g.fill(peak - half, base - r, peak + half, base - r + 1,
                        r >= h - 1 ? light : dark);
            }
        }
        g.fill(cx - w, base, cx + w, base + Math.max(2, u), dark);
        g.fill(cx - w, base, cx + w, base + 1, light);
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
        // Four long tapering arms and four short diagonals — an eight-point
        // sparkle. Arms taper to a third rather than a half, which is what
        // stops it reading as a plus sign at small sizes.
        for (int d = -r; d <= r; d++) {
            int t = Math.max(0, (r - Math.abs(d)) / 3);
            if (t > 0) {
                g.fill(cx - t, cy + d, cx + t, cy + d + 1, d < 0 ? light : dark);
                g.fill(cx + d, cy - t, cx + d + 1, cy + t, d < 0 ? light : dark);
            }
        }
        int s = Math.max(1, r / 2);
        for (int i = 0; i < s; i++) {
            int t = Math.max(1, (s - i) / 2);
            g.fill(cx + i, cy + i, cx + i + t, cy + i + t, dark);
            g.fill(cx - i - t, cy + i, cx - i, cy + i + t, dark);
            g.fill(cx + i, cy - i - t, cx + i + t, cy - i, light);
            g.fill(cx - i - t, cy - i - t, cx - i, cy - i, light);
        }
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
    }
}
