package com.voxelia.mmo.client;

import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared "lacquered slate" chrome for every Voxelia surface (K screen, N screen, HUD)
 * so they read as one system: semi-transparent gradient panels floating over the live
 * game (never a backdrop, never blur), gold titles, cyan interactive accents, and one
 * XP-bar recipe. Pure vector — fills, gradients, and text only.
 */
public final class VoxeliaUi {
    private VoxeliaUi() {}

    public static final int BORDER   = 0xFF3A4E63;
    public static final int GOLD     = 0xFFFFCE54;
    public static final int LINK     = 0xFF89C7FF;
    public static final int TEXT     = 0xFFE8EEF4;
    public static final int MUTED    = 0xFF8FA0AD;
    public static final int DISABLED = 0xFF606A74;
    public static final int GOOD     = 0xFF7CFC00;
    public static final int WARN     = 0xFFEF5350;
    public static final int TRACK    = 0xE0090D12;

    /** Drop-shadowed, top-lit beveled frame around a gradient slate body. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        // shadow first (bottom, offset right; then right side)
        g.fill(x + 2, y + h + 1, x + w + 3, y + h + 3, 0x50000000);
        g.fill(x + w + 1, y + 2, x + w + 3, y + h + 1, 0x50000000);
        // bevel: lit top/left, shaded right/bottom
        g.fill(x - 1, y - 1, x + w + 1, y, 0xFF52667B);
        g.fill(x - 1, y, x, y + h + 1, 0xFF52667B);
        g.fill(x + w, y, x + w + 1, y + h + 1, 0xFF293847);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFF293847);
        g.fillGradient(x, y, x + w, y + h, 0xE60E1620, 0xEE080D14);
        g.fill(x, y, x + w, y + 1, 0x30FFFFFF);
    }

    /** 16px gradient title bar with a gold hairline rule under it (total 17px). */
    public static void titleBar(GuiGraphics g, Font font, int x, int y, int w, String title) {
        g.fillGradient(x, y, x + w, y + 16, 0xFF24354A, 0xFF16202C);
        g.drawString(font, title, x + 6, y + 4, GOLD);
        g.fill(x + 4, y + 16, x + w - 4, y + 17, 0x66FFCE54);
    }

    /**
     * Draws a tab label right-aligned so it ends at {@code xRight}, inside a 16px title bar
     * starting at {@code barY}. The active tab sits in a pressed well and its gold underline
     * fuses with the title bar's hairline rule. Returns the tab's hitbox as {x1, y1, x2, y2}.
     */
    public static int[] tab(GuiGraphics g, Font font, String label, int xRight, int barY,
                            boolean active, int mouseX, int mouseY) {
        int tw = font.width(label) + 10;
        int x1 = xRight - tw;
        boolean hover = mouseX >= x1 && mouseX < xRight && mouseY >= barY && mouseY < barY + 16;
        if (active) g.fill(x1, barY + 2, x1 + tw, barY + 16, 0x30060B12);
        g.drawString(font, label, x1 + 5, barY + 4, active ? GOLD : (hover ? 0xFFC8D6E0 : MUTED));
        if (active) {
            g.fill(x1 + 2, barY + 15, x1 + tw - 2, barY + 17, GOLD);
        } else if (hover) {
            g.fill(x1 + 2, barY + 14, x1 + tw - 2, barY + 15, 0x60FFCE54);
        }
        return new int[]{x1, barY, x1 + tw, barY + 16};
    }

    /** Muted footer strip. */
    public static void footer(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0x40000000);
    }

    /**
     * The shared XP-bar recipe: seated dark track (1px rim + inset top shadow),
     * vertical-gradient fill, 1px bright leading edge, a position-phased sheen so
     * neighboring bars never sweep in lockstep, and a breathing halo when nearly full.
     */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int color, boolean sheen) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x9004080D);
        g.fill(x, y, x + w, y + h, TRACK);
        g.fill(x, y, x + w, y + 1, 0x50000000);
        int fw = (int) (w * Math.min(1f, Math.max(0f, frac)));
        if (fw <= 0) return;
        int base = 0xFF000000 | (color & 0xFFFFFF);
        g.fillGradient(x, y, x + fw, y + h, brighten(base, 30), darken(base));
        if (fw >= 2) g.fill(x + fw - 1, y, x + fw, y + h, 0xB0FFFFFF);
        if (frac >= 0.92f && frac < 1f) {
            int a = 0x28 + (int) (0x30 * pulse());
            int halo = (a << 24) | (color & 0xFFFFFF);
            g.fill(x, y - 1, x + fw, y, halo);
            g.fill(x, y + h, x + fw, y + h + 1, halo);
        }
        if (sheen && fw > 6) {
            long ph = (Util.getMillis() + x * 37L + y * 91L) % 2600L;
            int sx = x + (int) (ph / 2600f * (w + 10)) - 10;
            // clamp arithmetically instead of scissoring: scissor ignores pose translation
            int s1 = Math.max(x, sx);
            int s2 = Math.min(x + fw, sx + 10);
            if (s1 < s2) g.fill(s1, y, s2, y + h, 0x28FFFFFF);
        }
    }

    /** A small rounded pill ending at {@code xRight}; returns its left edge x. */
    public static int pill(GuiGraphics g, Font font, int xRight, int y, String text, int rgb, boolean filled) {
        int w = font.width(text) + 8;
        int x = xRight - w;
        int col = 0xFF000000 | rgb;
        if (filled) {
            g.fill(x + 1, y, xRight - 1, y + 11, col);
            g.fill(x, y + 1, x + 1, y + 10, col);
            g.fill(xRight - 1, y + 1, xRight, y + 10, col);
            g.fill(x + 1, y, xRight - 1, y + 1, 0x50FFFFFF);
            g.drawString(font, text, x + 4, y + 2, 0xFF14181C);
        } else {
            g.fill(x + 1, y, xRight - 1, y + 11, 0x30FFFFFF);
            g.drawString(font, text, x + 4, y + 2, col);
        }
        return x;
    }

    /** Ellipsis-trims {@code s} so it fits in {@code maxW} pixels. */
    public static String trim(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /** " [Key]" suffix for labels — or nothing when the bound key's name is too wide to fit. */
    public static String keyTag(Font font, KeyMapping key) {
        String name = key.getTranslatedKeyMessage().getString();
        return font.width(name) <= 36 ? " [" + name + "]" : "";
    }

    /** 250ms white acknowledgement flash over a rect, fading out from {@code startMillis}. */
    public static void flash(GuiGraphics g, int x1, int y1, int x2, int y2, long startMillis) {
        long dt = Util.getMillis() - startMillis;
        if (dt < 0 || dt > 250) return;
        int a = (int) (0x60 * (1f - dt / 250f));
        g.fill(x1, y1, x2, y2, (a << 24) | 0xFFFFFF);
    }

    // ── 120ms rise-in shared by the three screens; replays only on a fresh open ──
    private static long introStart = -1;
    private static long lastSeen;

    /** 0..1 ease-out of the screen-open transition. Tab hops (rendered back-to-back) don't replay it. */
    public static float introT() {
        long now = Util.getMillis();
        if (now - lastSeen > 250) introStart = now; // fresh open → replay
        lastSeen = now;
        float t = Math.min(1f, (now - introStart) / 120f);
        return 1f - (1f - t) * (1f - t);
    }

    /** 0..1 breathing wave shared by everything that invites a click. */
    public static float pulse() {
        return 0.5f + 0.5f * (float) Math.sin(Util.getMillis() / 300.0);
    }

    /** Lifts each RGB channel by {@code amt}, clamped; keeps alpha. */
    public static int brighten(int argb, int amt) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + amt);
        int gr = Math.min(255, ((argb >> 8) & 0xFF) + amt);
        int b = Math.min(255, (argb & 0xFF) + amt);
        return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    private static int darken(int argb) {
        int r = (int) (((argb >> 16) & 0xFF) * 0.72f);
        int gr = (int) (((argb >> 8) & 0xFF) * 0.72f);
        int b = (int) ((argb & 0xFF) * 0.72f);
        return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    /** Per-channel lerp between two opaque colors (alpha forced opaque). */
    public static int lerp(int from, int to, float t) {
        int r = ((from >> 16) & 0xFF) + (int) ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int gr = ((from >> 8) & 0xFF) + (int) ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (from & 0xFF) + (int) (((to & 0xFF) - (from & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }
}
