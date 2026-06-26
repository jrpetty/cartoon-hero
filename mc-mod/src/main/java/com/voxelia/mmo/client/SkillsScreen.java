package com.voxelia.mmo.client;

import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Full-screen skills panel: character level, every skill with its XP bar, and ability hints. */
public final class SkillsScreen extends Screen {
    public SkillsScreen() {
        super(Component.literal("Voxelia Skills"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Flat dimmed backdrop (no vanilla blur).
        g.fill(0, 0, this.width, this.height, 0xB0101418);

        int w = 290;
        int rowH = 26;
        int headerH = 46;
        int footerH = 24;
        int h = headerH + Skill.values().length * rowH + footerH;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        g.fill(x, y, x + w, y + h, 0xD0101820);
        g.fill(x, y, x + w, y + 22, 0xFF1D2733);
        g.drawCenteredString(this.font, "VOXELIA SKILLS", this.width / 2, y + 7, 0xFFFFCE54);

        int total = 0;
        for (Skill s : Skill.values()) total += ClientSkillData.level(s);
        int charLevel = Math.max(1, Math.round(total / (float) Skill.values().length));
        g.drawCenteredString(this.font, "Character Level " + charLevel + "  •  " + total + " total levels",
            this.width / 2, y + 28, 0xFFB0C4D4);

        int ry = y + headerH;
        for (Skill skill : Skill.values()) {
            int xp = ClientSkillData.xp(skill);
            int level = SkillCurve.levelForXp(xp);
            int into = SkillCurve.xpIntoLevel(xp);
            int span = SkillCurve.xpToNext(xp);
            int color = 0xFF000000 | skill.color();

            g.drawString(this.font, skill.display(), x + 12, ry, color);
            g.drawString(this.font, "Lv " + level, x + w - 52, ry, 0xFFFFFFFF);

            int barX = x + 12, barW = w - 24, barY = ry + 12, barH = 6;
            g.fill(barX, barY, barX + barW, barY + barH, 0xFF000000);
            float frac = span > 0 ? (float) into / span : 1f;
            g.fill(barX, barY, barX + (int) (barW * frac), barY + barH, color);
            g.drawString(this.font, span > 0 ? into + " / " + span + " xp" : "MAX",
                barX, barY + barH + 1, span > 0 ? 0xFFAAAAAA : 0xFFAAFFAA);
            ry += rowH;
        }

        String frenzyKey = VoxeliaKeys.FRENZY.getTranslatedKeyMessage().getString();
        String leapKey = VoxeliaKeys.LEAP.getTranslatedKeyMessage().getString();
        g.drawCenteredString(this.font,
            "Abilities — Frenzy [" + frenzyKey + "]   Leap [" + leapKey + "]",
            this.width / 2, y + h - 16, 0xFF89C7FF);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
