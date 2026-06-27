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

/** Skills HUD: one line + XP bar per skill, placed in a configurable corner. */
public final class SkillHudOverlay implements LayeredDraw.Layer {
    public static final SkillHudOverlay INSTANCE = new SkillHudOverlay();
    private static final int LINE_H = 14;
    private static final int BLOCK_W = 120;

    private SkillHudOverlay() {}

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !VoxeliaClientConfig.showHud()) return;
        if (!ClientSkillData.hasData()) return;

        int lines = Skill.values().length + 2; // title + ability line
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
        y += 11;

        for (Skill skill : Skill.values()) {
            int xp = ClientSkillData.xp(skill);
            int level = SkillCurve.levelForXp(xp);
            int into = SkillCurve.xpIntoLevel(xp);
            int span = SkillCurve.xpToNext(xp);
            int color = 0xFF000000 | skill.color();

            String line = span > 0 ? skill.display() + " " + level : skill.display() + " " + level + " MAX";
            graphics.drawString(mc.font, line, x, y, color);

            // thin XP bar under the label
            int barW = BLOCK_W - 4, barH = 2, barY = y + 9;
            graphics.fill(x, barY, x + barW, barY + barH, 0x80000000);
            float frac = span > 0 ? (float) into / span : 1f;
            graphics.fill(x, barY, x + (int) (barW * frac), barY + barH, color);
            y += LINE_H;
        }

        // selected ability + cooldown indicator
        int sel = ClientAbilities.selected();
        long remTicks = ClientAbilities.cooldownRemainingTicks(sel);
        boolean ready = remTicks <= 0;
        String key = VoxeliaKeys.USE_ABILITY.getTranslatedKeyMessage().getString();
        String name = ClientAbilities.selectedSkill().abilityName();
        String label = "▶ " + name + " [" + key + "]" + (ready ? "" : "  " + (remTicks / 20 + 1) + "s");
        graphics.drawString(mc.font, label, x, y + 2, ready ? 0xFF89C7FF : 0xFF8893A0);
        if (!ready) {
            int barW = BLOCK_W - 4, barH = 2, barY = y + 12;
            graphics.fill(x, barY, x + barW, barY + barH, 0x80000000);
            float frac = 1f - ClientAbilities.cooldownFraction(sel); // refills as it readies
            graphics.fill(x, barY, x + (int) (barW * frac), barY + barH, 0xFF89C7FF);
        }
    }
}
