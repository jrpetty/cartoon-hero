package com.voxelia.mmo.client;

import com.voxelia.mmo.progression.Milestones;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

import java.util.List;

/**
 * "Perk unlocked" toasts, in the mod's own chrome rather than a vanilla one:
 * a slim lacquered card at the top of the screen with the skill's accent stripe,
 * what you just earned, and how to use it. Sits top-centre, clear of the corner
 * HUD and the sidebar wherever the player has put them.
 */
public final class MilestoneToastOverlay implements LayeredDraw.Layer {
    public static final MilestoneToastOverlay INSTANCE = new MilestoneToastOverlay();
    private MilestoneToastOverlay() {}

    private static final int H = 26;
    private static final int GAP = 4;
    private static final int TOP = 12;

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        List<MilestoneToasts.Toast> toasts = MilestoneToasts.active();
        if (toasts.isEmpty()) return;

        Font font = mc.font;
        int screenW = g.guiWidth();
        int row = 0;
        for (MilestoneToasts.Toast toast : toasts) {
            float a = MilestoneToasts.alpha(toast);
            if (a <= 0f) continue;

            String title = title(toast);
            String blurb = blurb(font, toast);
            int w = Math.max(font.width(title), font.width(blurb)) + 22;
            int x = (screenW - w) / 2;
            int y = TOP + row * (H + GAP) + (int) ((1f - a) * -6f); // drifts down as it fades in
            row++;

            int alpha = (int) (255 * a);
            int accent = 0xFF000000 | toast.skill().color();

            // panel
            g.fill(x + 2, y + H, x + w + 2, y + H + 2, (int) (0x50 * a) << 24);
            g.fill(x - 1, y - 1, x + w + 1, y + H + 1, argb(a, 0x52667B));
            g.fillGradient(x, y, x + w, y + H, argb(a * 0.92f, 0x0E1620), argb(a * 0.96f, 0x080D14));
            g.fill(x, y, x + w, y + 1, argb(a * 0.19f, 0xFFFFFF));
            g.fillGradient(x, y, x + 3, y + H,
                argb(a, VoxeliaUi.brighten(accent, 40) & 0xFFFFFF),
                argb(a, VoxeliaUi.lerp(accent, 0xFF0A0F14, 0.4f) & 0xFFFFFF));

            g.drawString(font, title, x + 9, y + 5, (alpha << 24) | (VoxeliaUi.GOLD & 0xFFFFFF));
            g.drawString(font, blurb, x + 9, y + 15, (alpha << 24) | (VoxeliaUi.MUTED & 0xFFFFFF));
        }
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (255 * alpha)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static String title(MilestoneToasts.Toast toast) {
        Skill skill = toast.skill();
        return switch (toast.kind()) {
            case ABILITY -> skill.abilityName() + " unlocked";
            case HASTE -> "Haste unlocked";
            case TELEKINESIS -> "Telekinesis unlocked";
            case LAST_STAND -> "Last Stand unlocked";
            case WELL_FED -> "Well Fed unlocked";
        };
    }

    private static String blurb(Font font, MilestoneToasts.Toast toast) {
        String where = toast.skill().display() + " " + toast.level() + "  ·  ";
        return where + switch (toast.kind()) {
            case ABILITY -> "cycle to it with " + key(font, VoxeliaKeys.CYCLE_ABILITY)
                + ", use it with " + key(font, VoxeliaKeys.USE_ABILITY);
            case HASTE -> "Haste while you hold a pickaxe";
            case TELEKINESIS -> "mined drops go straight to your inventory";
            case LAST_STAND -> "Resistance kicks in below 35% health";
            case WELL_FED -> "saturation and a short regen after eating";
        };
    }

    private static String key(Font font, net.minecraft.client.KeyMapping mapping) {
        return "[" + VoxeliaUi.trim(font, mapping.getTranslatedKeyMessage().getString(), 40) + "]";
    }
}
