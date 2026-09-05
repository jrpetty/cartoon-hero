package com.voxelia.mmo.client;

import com.voxelia.mmo.config.VoxeliaClientConfig;
import com.voxelia.mmo.config.VoxeliaClientConfig.Anchor;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

import java.util.Arrays;
import java.util.Locale;

/**
 * Skills HUD: shared Voxelia chrome, per-skill accent ticks, right-aligned levels,
 * a "+N" flash on the row that just earned XP, and the selected ability with a
 * decimal cooldown countdown. Placed in a configurable corner.
 */
public final class SkillHudOverlay implements LayeredDraw.Layer {
    public static final SkillHudOverlay INSTANCE = new SkillHudOverlay();
    private static final int LINE_H = 14;
    private static final int BLOCK_W = 120;
    private static final long FLASH_MS = 2500;

    // XP-delta flash state (client-only, per skill).
    private final int[] lastXp = new int[Skill.values().length];
    private final int[] lastGain = new int[Skill.values().length];
    private final long[] flashUntil = new long[Skill.values().length];
    private boolean seeded = false;

    private SkillHudOverlay() {}

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !VoxeliaClientConfig.showHud()) return;
        if (!ClientSkillData.hasData()) {
            seeded = false; // re-seed on the next world/character (no ghost flashes)
            return;
        }

        long now = Util.getMillis();
        Skill[] all = Skill.values();
        if (!seeded) { // don't flash everything on login
            for (Skill s : all) lastXp[s.ordinal()] = ClientSkillData.xp(s);
            Arrays.fill(flashUntil, 0L);
            seeded = true;
        }

        int blockH = 12 + all.length * LINE_H + 20; // title strip + rows + ability block

        Anchor anchor = VoxeliaClientConfig.anchor();
        int ox = VoxeliaClientConfig.offsetX();
        int oy = VoxeliaClientConfig.offsetY();
        boolean left = anchor == Anchor.TOP_LEFT || anchor == Anchor.BOTTOM_LEFT;
        boolean top = anchor == Anchor.TOP_LEFT || anchor == Anchor.TOP_RIGHT;

        int x = left ? ox : graphics.guiWidth() - ox - BLOCK_W;
        int y = top ? oy : graphics.guiHeight() - oy - blockH;

        // Lacquered-slate panel, more transparent than the screens (it lives during gameplay).
        if (VoxeliaClientConfig.hudPanel()) {
            graphics.fill(x - 5, y - 5, x + BLOCK_W + 1, y + blockH + 1, 0x803A4E63);
            graphics.fillGradient(x - 4, y - 4, x + BLOCK_W, y + blockH, 0xA80C141C, 0xB8070C12);
            graphics.fillGradient(x - 4, y - 4, x + BLOCK_W, y + 9, 0xC01D2B3A, 0xC0141E29);
            graphics.fill(x - 4, y + 9, x + BLOCK_W, y + 10, 0x50FFCE54);
        }

        graphics.drawString(mc.font, "SKILLS", x, y, VoxeliaUi.GOLD);
        y += 12;

        int sel = ClientAbilities.selected();
        for (Skill skill : all) {
            int i = skill.ordinal();
            int xp = ClientSkillData.xp(skill);
            int level = SkillCurve.levelForXp(xp);
            int into = SkillCurve.xpIntoLevel(xp);
            int span = SkillCurve.xpToNext(xp);
            int color = 0xFF000000 | skill.color();

            if (xp != lastXp[i]) {
                if (xp > lastXp[i]) { // gained: flash the row (accumulate rapid gains)
                    lastGain[i] = (xp - lastXp[i]) + (now < flashUntil[i] ? lastGain[i] : 0);
                    flashUntil[i] = now + FLASH_MS;
                } else {
                    flashUntil[i] = 0; // loss (death penalty): kill any running gain flash
                }
                lastXp[i] = xp;
            }
            boolean flash = now < flashUntil[i];

            if (flash) graphics.fill(x - 2, y - 1, x + BLOCK_W - 4, y + 12, 0x1E89C7FF);
            graphics.fill(x - 2, y, x, y + 8, color); // identity tick
            if (i == sel && skill.active()) graphics.fill(x - 4, y, x - 2, y + 8, VoxeliaUi.LINK);
            graphics.drawString(mc.font, skill.display(), x + 2, y, VoxeliaUi.TEXT);

            String val = span > 0 ? String.valueOf(level) : "MAX";
            int valX = x + BLOCK_W - 6 - mc.font.width(val);
            graphics.drawString(mc.font, val, valX, y, span > 0 ? 0xFFFFFFFF : VoxeliaUi.GOOD);
            if (flash) { // gain shown next to the level, so level-ups stay visible
                String gain = "+" + lastGain[i];
                graphics.drawString(mc.font, gain, valX - 3 - mc.font.width(gain), y, VoxeliaUi.GOOD);
            }

            // thin XP bar under the label
            int barW = BLOCK_W - 8, barY = y + 9;
            float frac = span > 0 ? (float) into / span : 1f;
            VoxeliaUi.bar(graphics, x + 2, barY, barW, 2, frac, skill.color(), false);
            if (flash) { // white afterglow fading over the filled portion
                int a = (int) (150f * (flashUntil[i] - now) / FLASH_MS);
                int fw = (int) (barW * frac);
                if (a >= 8 && fw > 0) graphics.fill(x + 2, barY, x + 2 + fw, barY + 2, (a << 24) | 0xFFFFFF);
            }
            y += LINE_H;
        }

        // Selected ability: skill-color swatch, name + key, readiness pulse or countdown.
        long remTicks = ClientAbilities.cooldownRemainingTicks(sel);
        boolean ready = remTicks <= 0;
        String key = VoxeliaKeys.USE_ABILITY.getTranslatedKeyMessage().getString();
        Skill selSkill = ClientAbilities.selectedSkill();
        graphics.fill(x, y + 3, x + 3, y + 6, 0xFF000000 | selSkill.color());
        String label = selSkill.abilityName() + " [" + key + "]";
        int labelColor;
        if (ready) {
            float t = (now % 1600L) / 800f;
            if (t > 1f) t = 2f - t;
            labelColor = VoxeliaUi.lerp(VoxeliaUi.LINK, 0xFFDBF1FF, t);
        } else {
            labelColor = 0xFF8893A0;
        }
        graphics.drawString(mc.font, label, x + 6, y + 2, labelColor);
        if (!ready) {
            float cdFrac = ClientAbilities.cooldownFraction(sel);
            String cd = String.format(Locale.ROOT, "%.1fs", remTicks / 20f);
            graphics.drawString(mc.font, cd, x + BLOCK_W - 6 - mc.font.width(cd), y + 2,
                cdFrac > 0.5f ? VoxeliaUi.WARN : VoxeliaUi.GOLD);
            VoxeliaUi.bar(graphics, x + 2, y + 12, BLOCK_W - 8, 2, 1f - cdFrac, 0x89C7FF, false);
        }
    }
}
