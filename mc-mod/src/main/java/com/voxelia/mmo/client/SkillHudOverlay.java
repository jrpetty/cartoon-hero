package com.voxelia.mmo.client;

import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

/** Top-left skills HUD: a line per skill with level and progress to next. */
public final class SkillHudOverlay implements LayeredDraw.Layer {
    public static final SkillHudOverlay INSTANCE = new SkillHudOverlay();
    private SkillHudOverlay() {}

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !VoxeliaConfig.showHud()) return;
        if (!ClientSkillData.hasData()) return;

        final int x = 4;
        int y = 4;
        graphics.drawString(mc.font,
            Component.literal("Skills").withStyle(s -> s.withColor(0xFFCE54)), x, y, 0xFFFFFF);
        y += 11;

        for (Skill skill : Skill.values()) {
            int xp = ClientSkillData.xp(skill);
            int level = SkillCurve.levelForXp(xp);
            int into = SkillCurve.xpIntoLevel(xp);
            int span = SkillCurve.xpToNext(xp);
            String line = span > 0
                ? skill.display() + " " + level + "  " + into + "/" + span
                : skill.display() + " " + level + "  MAX";
            graphics.drawString(mc.font, line, x, y, 0xFF000000 | skill.color());
            y += 10;
        }
    }
}
