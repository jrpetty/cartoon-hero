package com.voxelia.mmo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

/**
 * The prestige flourish: a brief full-screen celebration (golden vignette,
 * expanding rings, radiating sparks, and a scaled "PRESTIGE" banner naming the
 * skill and its new star count) that plays once when a skill is prestiged.
 * Driven entirely by {@link PrestigeCelebration}; draws nothing when idle.
 */
public final class PrestigeCelebrationOverlay implements LayeredDraw.Layer {
    public static final PrestigeCelebrationOverlay INSTANCE = new PrestigeCelebrationOverlay();
    private PrestigeCelebrationOverlay() {}

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        float p = PrestigeCelebration.progress();
        if (p < 0f) return;
        Skill skill = PrestigeCelebration.skill();
        if (skill == null) return;
        int count = PrestigeCelebration.prestigeCount();

        int w = g.guiWidth();
        int h = g.guiHeight();
        int cx = w / 2;
        int cy = h / 2;
        long now = Util.getMillis();

        // Envelope: quick fade-in, hold, ease-out.
        float alpha;
        if (p < 0.12f) alpha = p / 0.12f;
        else if (p > 0.75f) alpha = 1f - (p - 0.75f) / 0.25f;
        else alpha = 1f;
        alpha = Math.max(0f, Math.min(1f, alpha));

        int gold = VoxeliaUi.GOLD & 0xFFFFFF;
        int skillRgb = skill.color() & 0xFFFFFF;

        // Golden vignette dimming the world behind the banner.
        g.fill(0, 0, w, h, (int) (0x5C * alpha) << 24);

        // Expanding golden rings.
        float ringT = Math.min(1f, p / 0.6f);
        for (int i = 0; i < 3; i++) {
            float rt = ringT - i * 0.14f;
            if (rt <= 0f) continue;
            int radius = (int) (rt * Math.max(w, h) * 0.55f);
            int ringAlpha = (int) (alpha * 130 * (1f - rt));
            if (ringAlpha >= 6) drawRing(g, cx, cy, radius, (ringAlpha << 24) | gold);
        }

        // Radiating sparks, alternating gold and the skill's colour.
        int sparks = 18;
        float reach = Math.min(1f, p / 0.5f);
        for (int i = 0; i < sparks; i++) {
            double ang = (Math.PI * 2 * i / sparks) + now / 850.0;
            int dist = (int) ((0.25 + 0.75 * reach) * 78);
            int sx = cx + (int) (Math.cos(ang) * dist);
            int sy = cy + (int) (Math.sin(ang) * dist);
            int sa = (int) (alpha * 210);
            int col = (i % 2 == 0) ? gold : skillRgb;
            if (sa >= 6) g.fill(sx - 1, sy - 1, sx + 2, sy + 2, (sa << 24) | col);
        }

        int ta = Math.max(0, Math.min(255, (int) (alpha * 255)));
        if (ta <= 8) return; // nothing legible left to draw

        // "PRESTIGE" banner: pops in, then breathes.
        float intro = Math.min(1f, p / 0.15f);
        float pulse = 1f + 0.04f * (float) Math.sin(now / 150.0);
        float titleScale = (2.6f + 0.6f * intro) * pulse;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy - 34, 0);
        pose.scale(titleScale, titleScale, 1f);
        g.drawCenteredString(mc.font, "PRESTIGE", 0, 0, (ta << 24) | gold);
        pose.popPose();

        // Skill name, a touch larger than body text.
        pose.pushPose();
        pose.translate(cx, cy + 4, 0);
        pose.scale(1.7f, 1.7f, 1f);
        g.drawCenteredString(mc.font, skill.display(), 0, 0, (ta << 24) | skillRgb);
        pose.popPose();

        String stars = "✦".repeat(Math.min(count, 5));
        g.drawCenteredString(mc.font, stars + "  Prestige " + count + "  " + stars,
            cx, cy + 24, (ta << 24) | gold);
        int subA = Math.max(0, Math.min(255, (int) (alpha * 210)));
        if (subA > 8) {
            g.drawCenteredString(mc.font, "back to Level 1  ·  +1 talent point",
                cx, cy + 38, (subA << 24) | (VoxeliaUi.TEXT & 0xFFFFFF));
        }
    }

    /** Cheap circle outline: dots plotted around the circumference. */
    private static void drawRing(GuiGraphics g, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        int steps = Math.max(32, r);
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2 * i / steps;
            int x = cx + (int) (Math.cos(a) * r);
            int y = cy + (int) (Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
