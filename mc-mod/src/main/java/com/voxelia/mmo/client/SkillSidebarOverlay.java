package com.voxelia.mmo.client;

import com.voxelia.mmo.config.VoxeliaClientConfig;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

/**
 * A vanilla-scoreboard-style sidebar (right edge, vertically centred) listing
 * every skill's level, capped by a Character line. Toggled
 * with /voxelia sidebar, the sidebar keybind, or the client config. Independent
 * of the corner HUD so players can run either, both, or neither.
 */
public final class SkillSidebarOverlay implements LayeredDraw.Layer {
    public static final SkillSidebarOverlay INSTANCE = new SkillSidebarOverlay();
    private static final int ROW_H = 10;
    private static final int MARGIN = 3;
    private static final int PAD_X = 4;
    private static final int GAP = 12; // min gap between a row's name and its value

    private SkillSidebarOverlay() {}

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !VoxeliaClientConfig.showSidebar()) return;
        if (!ClientSkillData.hasData()) return;

        Font font = mc.font;
        Skill[] all = Skill.values();
        String title = "✦ VOXELIA ✦";

        String[] names = new String[all.length];
        String[] vals = new String[all.length];
        int contentW = font.width(title);
        int totalLvl = 0;
        for (int i = 0; i < all.length; i++) {
            Skill s = all[i];
            int xp = ClientSkillData.xp(s);
            int lvl = ClientSkillData.level(s);
            totalLvl += lvl;
            names[i] = s.display();
            vals[i] = SkillCurve.xpToNext(xp) > 0 ? String.valueOf(lvl) : "MAX";
            contentW = Math.max(contentW, font.width(names[i]) + GAP + font.width(vals[i]));
        }
        int charLvl = Math.max(1, Math.round(totalLvl / (float) all.length));
        String charVal = "Lv " + charLvl;
        contentW = Math.max(contentW, font.width("Character") + GAP + font.width(charVal));

        int w = contentW + PAD_X * 2;
        int titleH = 12;
        int h = titleH + all.length * ROW_H + 3 + ROW_H + 3;
        int x = g.guiWidth() - MARGIN - w;
        int y = (g.guiHeight() - h) / 2;

        VoxeliaUi.panel(g, x, y, w, h);
        g.fillGradient(x, y, x + w, y + titleH, 0xFF24354A, 0xFF16202C);
        g.drawString(font, title, x + (w - font.width(title)) / 2, y + 2, VoxeliaUi.GOLD);
        g.fill(x + 3, y + titleH, x + w - 3, y + titleH + 1, 0x66FFCE54);

        int ry = y + titleH + 2;
        for (int i = 0; i < all.length; i++) {
            if ((i & 1) == 1) g.fill(x + 1, ry - 1, x + w - 1, ry + ROW_H - 1, 0x18FFFFFF);
            g.fill(x + 1, ry, x + 3, ry + 8, 0xFF000000 | all[i].color());
            g.drawString(font, names[i], x + PAD_X + 2, ry, VoxeliaUi.TEXT);
            boolean max = vals[i].equals("MAX");
            g.drawString(font, vals[i], x + w - PAD_X - font.width(vals[i]), ry,
                max ? VoxeliaUi.GOOD : 0xFFFFD54F);
            ry += ROW_H;
        }

        g.fill(x + 3, ry + 1, x + w - 3, ry + 2, 0x50FFCE54);
        ry += 3;
        g.drawString(font, "Character", x + PAD_X + 2, ry, VoxeliaUi.GOLD);
        g.drawString(font, charVal, x + w - PAD_X - font.width(charVal), ry, 0xFFFFFFFF);
    }
}
