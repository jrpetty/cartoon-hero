package com.voxelia.mmo.client;

import com.voxelia.mmo.config.VoxeliaClientConfig;
import com.voxelia.mmo.config.VoxeliaClientConfig.Anchor;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

/** Skills HUD: one line per skill, placed in a configurable screen corner. */
public final class SkillHudOverlay implements LayeredDraw.Layer {
    public static final SkillHudOverlay INSTANCE = new SkillHudOverlay();
    private static final int LINE_H = 10;
    private static final int BLOCK_W = 120;

    private SkillHudOverlay() {}

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !VoxeliaClientConfig.showHud()) return;
        if (!ClientSkillData.hasData()) return;

        int lines = Skill.values().length + 1; // +1 for the title
        int blockH = lines * LINE_H + 2;

        Anchor anchor = VoxeliaClientConfig.anchor();
        int ox = VoxeliaClientConfig.offsetX();
        int oy = VoxeliaClientConfig.offsetY();
        boolean left = anchor == Anchor.TOP_LEFT || anchor == Anchor.BOTTOM_LEFT;
        boolean top = anchor == Anchor.TOP_LEFT || anchor == Anchor.TOP_RIGHT;

        int x = left ? ox : graphics.guiWidth() - ox - BLOCK_W;
        int y = top ? oy : graphics.guiHeight() - oy - blockH;

        graphics.drawString(mc.font,
            Component.literal("Skills").withStyle(s -> s.withColor(0xFFCE54)), x, y, 0xFFFFFF);
        y += LINE_H + 1;

        for (Skill skill : Skill.values()) {
            int xp = ClientSkillData.xp(skill);
            int level = SkillCurve.levelForXp(xp);
            int into = SkillCurve.xpIntoLevel(xp);
            int span = SkillCurve.xpToNext(xp);
            String line = span > 0
                ? skill.display() + " " + level + "  " + into + "/" + span
                : skill.display() + " " + level + "  MAX";
            graphics.drawString(mc.font, line, x, y, 0xFF000000 | skill.color());
            y += LINE_H;
        }
    }
}
