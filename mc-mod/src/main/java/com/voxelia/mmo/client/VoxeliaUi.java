package com.voxelia.mmo.client;

import net.minecraft.Util;
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

    /** 1px steel frame, gradient slate body, 1px inner top highlight. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BORDER);
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
     * starting at {@code barY}. Returns the tab's hitbox as {x1, y1, x2, y2}.
     */
    public static int[] tab(GuiGraphics g, Font font, String label, int xRight, int barY,
                            boolean active, int mouseX, int mouseY) {
        int tw = font.width(label) + 10;
        int x1 = xRight - tw;
        boolean hover = mouseX >= x1 && mouseX < xRight && mouseY >= barY && mouseY < barY + 16;
        g.drawString(font, label, x1 + 5, barY + 4, active ? GOLD : (hover ? 0xFFC8D6E0 : MUTED));
        if (active) {
            g.fill(x1 + 2, barY + 14, x1 + tw - 2, barY + 15, GOLD);
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
     * The shared XP-bar recipe: dark track, vertical-gradient fill in the given color,
     * 1px bright leading edge, and (optionally) a slow sheen sweeping the filled part.
     */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int color, boolean sheen) {
        g.fill(x, y, x + w, y + h, TRACK);
        int fw = (int) (w * Math.min(1f, Math.max(0f, frac)));
        if (fw <= 0) return;
        int base = 0xFF000000 | (color & 0xFFFFFF);
        g.fillGradient(x, y, x + fw, y + h, brighten(base, 30), darken(base));
        if (fw >= 2) g.fill(x + fw - 1, y, x + fw, y + h, 0xB0FFFFFF);
        if (sheen && fw > 6) {
            int sx = x + (int) ((Util.getMillis() % 2400L) / 2400f * (w + 10)) - 10;
            g.enableScissor(x, y, x + fw, y + h);
            g.fill(sx, y, sx + 10, y + h, 0x28FFFFFF);
            g.disableScissor();
        }
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
