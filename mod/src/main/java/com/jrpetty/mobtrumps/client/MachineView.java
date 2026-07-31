package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.game.MobCard;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The working half of a recycler screen: an actual machine, drawn.
 *
 * <p>The design note asked for "a machine, not a button" — a thing you feed,
 * a visible grind, a progress bar — and the first build was a list and a chat
 * message. This is the machine. A card rides down into the throat, the drum
 * turns and chews, sparks come off the teeth, a bar fills, and the fragments
 * tumble into the tray underneath. The press does the same in reverse: a blank
 * sheet feeds through the rollers and either a card rises out of the slot or the
 * sheet tears.
 *
 * <p>All of it is driven by one clock — {@code phase}, 0 to 1 across the whole
 * run — so nothing can desynchronise from anything else.
 */
public final class MachineView {

    /** How long a full run takes, and where the beats fall inside it. */
    public static final long RUN_MS = 1700L;
    private static final float FEED_END = 0.30f;
    private static final float WORK_END = 0.78f;

    private static final int STEEL = 0xFF6E7480;
    private static final int STEEL_DARK = 0xFF3A3F49;
    private static final int STEEL_LIT = 0xFF98A0AE;
    private static final int WELL = 0xFF0E0C16;
    private static final int GOLD = 0xFFE3C071;
    private static final int TOOTH = 0xFFC8CEDA;

    private MachineView() {
    }

    /** 0 = feeding, 1 = working, 2 = paying out. */
    public static int beat(float phase) {
        return phase < FEED_END ? 0 : phase < WORK_END ? 1 : 2;
    }

    /** How far through the current beat, 0-1. */
    private static float local(float phase) {
        if (phase < FEED_END) return phase / FEED_END;
        if (phase < WORK_END) return (phase - FEED_END) / (WORK_END - FEED_END);
        return (phase - WORK_END) / (1f - WORK_END);
    }

    // --- the shredder -------------------------------------------------------

    /**
     * @param phase  -1 when idle, else 0-1 through a run
     * @param card   the card being eaten, or the one queued when idle
     * @param payout fragments this run produced, for the tray
     */
    public static void shredder(GuiGraphics g, Font font, int x, int y, int w, int h,
                                float phase, MobCard card, LivingEntity mob, boolean foil,
                                int payout, long now) {
        boolean running = phase >= 0f;
        int throatY = y + 6;
        // the drum sits low so the card has real distance to fall into it
        int drumY = y + Math.round(h * 0.52f);
        int drumH = Math.min(26, Math.max(14, h / 6));
        // and the tray takes what is left, never climbing back over the drum
        int trayY = Math.max(drumY + drumH + 15, y + h - 34);

        g.fill(x, y, x + w, y + h, WELL);

        // --- the card, riding down into the throat --------------------------
        if (card != null) {
            float t = running ? Math.min(1f, local(phase) * (beat(phase) == 0 ? 1f : 2f)) : 0f;
            float drop = beat(phase) == 0 ? t : (running ? 1f : 0f);
            float scale = Math.min((w - 22) / (float) CardRenderer.CARD_W,
                    (drumY - throatY - 6) / (float) CardRenderer.CARD_H);
            int cw = Math.round(CardRenderer.CARD_W * scale);
            int ch = Math.round(CardRenderer.CARD_H * scale);
            int cx = x + (w - cw) / 2;
            int cy = throatY + Math.round(drop * (drumY - throatY - ch / 2f));
            // clipped at the drum: the card disappears INTO the machine
            g.enableScissor(x, y, x + w, drumY + 2);
            CardRenderer.renderCard(g, font, card, 0, cx, cy, scale, 0, 0, mob, foil, false);
            g.disableScissor();
        } else if (!running) {
            String hint = "Point at a card";
            g.drawString(font, hint, x + (w - font.width(hint)) / 2, throatY + 20, 0xFF7E7590, false);
        }

        // --- the throat: a funnel narrowing into the drum -------------------
        for (int i = 0; i < 8; i++) {
            int inset = i;
            g.fill(x + inset, drumY - 10 + i, x + inset + 2, drumY - 9 + i, STEEL_DARK);
            g.fill(x + w - inset - 2, drumY - 10 + i, x + w - inset, drumY - 9 + i, STEEL_DARK);
        }

        // --- the drum -------------------------------------------------------
        g.fill(x + 2, drumY, x + w - 2, drumY + drumH, STEEL_DARK);
        g.fill(x + 4, drumY + 2, x + w - 4, drumY + drumH - 2, 0xFF23262E);
        // two counter-rotating rows of teeth
        float spin = running && beat(phase) >= 1 ? (now % 260L) / 260f : (now % 2600L) / 2600f;
        for (int row = 0; row < 2; row++) {
            int ty = drumY + 5 + row * 10;
            for (int i = -1; i < (w - 8) / 8 + 1; i++) {
                float off = (spin * (row == 0 ? 1 : -1) + i) * 8f;
                int tx = x + 4 + Math.floorMod(Math.round(off), w - 8);
                g.fill(tx, ty, tx + 5, ty + 6, row == 0 ? TOOTH : STEEL);
                g.fill(tx, ty, tx + 5, ty + 1, STEEL_LIT);
            }
        }
        g.renderOutline(x + 2, drumY, w - 4, drumH, STEEL);

        // sparks off the teeth while it is actually cutting
        if (running && beat(phase) == 1) {
            for (int i = 0; i < 7; i++) {
                float sp = ((now / 40L) + i * 13) % 30 / 30f;
                int sx = x + 6 + (int) ((i * 37 + now / 30) % (w - 12));
                int sy = drumY + drumH + (int) (sp * 10);
                g.fill(sx, sy, sx + 1, sy + 2, (int) ((1 - sp) * 0xFF) << 24 | 0x00FFC864);
            }
        }

        // --- progress bar ---------------------------------------------------
        int barY = drumY + drumH + 6;
        g.fill(x + 6, barY, x + w - 6, barY + 6, 0xFF1A1D24);
        if (running) {
            float p = Mth.clamp((phase - FEED_END * 0.5f) / (WORK_END - FEED_END * 0.5f), 0f, 1f);
            g.fillGradient(x + 6, barY, x + 6 + (int) ((w - 12) * p), barY + 6, 0xFFE07A3A, 0xFFB4482E);
        }
        g.renderOutline(x + 6, barY, w - 12, 6, 0x55FFFFFF);

        // --- the tray, filling with fragments --------------------------------
        g.fill(x + 4, trayY, x + w - 4, y + h - 4, 0xFF17141F);
        g.renderOutline(x + 4, trayY, w - 8, y + h - 4 - trayY, STEEL_DARK);
        if (payout > 0) {
            float fall = running ? (beat(phase) == 2 ? local(phase) : 0f) : 1f;
            int pile = (int) (Math.min(1f, payout / 40f) * (y + h - 10 - trayY) * fall);
            for (int i = 0; i < pile; i++) {
                int py = y + h - 6 - i;
                int jitter = (i * 7) % 5;
                g.fill(x + 7 + jitter, py, x + w - 7 - jitter, py + 1,
                        (i % 3 == 0) ? 0xFFC8A874 : 0xFF9A7B54);
            }
            if (fall > 0.05f) {
                String got = "+" + payout;
                g.drawString(font, got, x + (w - font.width(got)) / 2, trayY - 10, GOLD, true);
            }
        }
    }

    // --- the press ----------------------------------------------------------

    /**
     * @param phase -1 when idle, else 0-1 through a run
     * @param hit   true once the run has resolved into a card, false for a misprint
     * @param card  the printed card, or null while it is still unknown
     */
    public static void press(GuiGraphics g, Font font, int x, int y, int w, int h,
                             float phase, boolean resolved, boolean hit, MobCard card,
                             LivingEntity mob, long now) {
        boolean running = phase >= 0f;
        int rollY = y + Math.round(h * 0.34f);
        int rollH = Math.min(20, Math.max(12, h / 8));
        int slotY = Math.max(rollY + rollH + 14, y + h - 40);

        g.fill(x, y, x + w, y + h, WELL);

        // --- the blank sheet feeding in ------------------------------------
        float feed = !running ? 0f : beat(phase) == 0 ? local(phase) : 1f;
        int sheetW = w - 26;
        int sheetH = 26;
        int sheetY = y + 4 + Math.round(feed * Math.max(0, rollY - y - 10));
        g.enableScissor(x, y, x + w, rollY + 4);
        g.fill(x + 13, sheetY, x + 13 + Math.max(8, sheetW), sheetY + sheetH, 0xFFEDE6D4);
        g.fill(x + 13, sheetY, x + 13 + sheetW, sheetY + 2, 0xFFFFFAF0);
        g.disableScissor();

        // --- the rollers ----------------------------------------------------
        g.fill(x + 2, rollY, x + w - 2, rollY + rollH, STEEL_DARK);
        float spin = running && beat(phase) >= 1 ? (now % 200L) / 200f : (now % 3000L) / 3000f;
        for (int row = 0; row < 2; row++) {
            int ry = rollY + 2 + row * (rollH / 2);
            g.fill(x + 5, ry, x + w - 5, ry + 6, 0xFF2A2E36);
            for (int i = -1; i < (w - 10) / 10 + 1; i++) {
                float off = (spin * (row == 0 ? 1 : -1) + i) * 10f;
                int rx = x + 5 + Math.floorMod(Math.round(off), w - 10);
                g.fill(rx, ry, rx + 2, ry + 6, STEEL_LIT);
            }
        }
        g.renderOutline(x + 2, rollY, w - 4, rollH, STEEL);

        // --- progress bar ---------------------------------------------------
        int barY = rollY + rollH + 6;
        g.fill(x + 6, barY, x + w - 6, barY + 6, 0xFF1A1D24);
        if (running) {
            float p = Mth.clamp(phase / WORK_END, 0f, 1f);
            g.fillGradient(x + 6, barY, x + 6 + (int) ((w - 12) * p), barY + 6, 0xFF4B8F3E, 0xFF2E6B26);
        }
        g.renderOutline(x + 6, barY, w - 12, 6, 0x55FFFFFF);

        // --- the output slot ------------------------------------------------
        g.fill(x + 4, slotY, x + w - 4, slotY + 4, 0xFF0A0810);
        g.renderOutline(x + 4, slotY, w - 8, 4, STEEL_DARK);

        boolean showing = running && beat(phase) == 2 && resolved;
        float rise = showing ? local(phase) : 0f;
        if (showing && hit && card != null) {
            float scale = Math.min((w - 20) / (float) CardRenderer.CARD_W,
                    (y + h - slotY - 6) / (float) CardRenderer.CARD_H);
            int cw = Math.round(CardRenderer.CARD_W * scale);
            int ch = Math.round(CardRenderer.CARD_H * scale);
            int cx = x + (w - cw) / 2;
            int cy = slotY + 4 + Math.round((1f - rise) * ch);
            g.enableScissor(x, slotY + 4, x + w, y + h);
            CardRenderer.renderCard(g, font, card, 0, cx, cy, scale, 0, 0, mob, false, false);
            g.disableScissor();
            // a glow that blooms as it clears the slot
            int a = (int) (rise * 0x55) << 24;
            g.fill(x + 4, slotY, x + w - 4, y + h, a | 0x00FFE08A);
        } else if (showing) {
            // a misprint: the sheet comes out torn and scorched
            int ty = slotY + 6;
            g.fill(x + 12, ty, x + w - 12, ty + 24, 0xFF7A6A54);
            for (int i = 0; i < 12; i++) {
                int tx = x + 12 + i * ((w - 24) / 12);
                int cut = (int) ((Math.sin(i * 1.7) * 0.5 + 0.5) * 10 * rise);
                g.fill(tx, ty + 24 - cut, tx + 3, ty + 24, WELL);
            }
            g.fill(x + 12, ty, x + w - 12, ty + 24, ((int) (rise * 0x66) << 24) | 0x00C03020);
            String miss = "MISPRINT";
            g.drawString(font, miss, x + (w - font.width(miss)) / 2, ty + 30,
                    0xFFE06A50, true);
        }
    }
}
