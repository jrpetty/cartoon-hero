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
        // No full-screen backdrop — the live game shows through; only the panel is drawn.
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

        Skill selected = ClientAbilities.selectedSkill();
        int ry = y + headerH;
        for (Skill skill : Skill.values()) {
            int xp = ClientSkillData.xp(skill);
            int level = SkillCurve.levelForXp(xp);
            int into = SkillCurve.xpIntoLevel(xp);
            int span = SkillCurve.xpToNext(xp);
            int color = 0xFF000000 | skill.color();
            boolean sel = skill == selected;

            if (sel) g.fill(x + 4, ry - 2, x + w - 4, ry + rowH - 4, 0x2089C7FF);
            g.drawString(this.font, (sel ? "▶ " : "  ") + skill.display(), x + 8, ry, color);
            g.drawString(this.font, skill.abilityName(), x + 120, ry, 0xFF89C7FF);
            g.drawString(this.font, "Lv " + level, x + w - 50, ry, 0xFFFFFFFF);

            int barX = x + 12, barW = w - 24, barY = ry + 12, barH = 6;
            g.fill(barX, barY, barX + barW, barY + barH, 0xFF000000);
            float frac = span > 0 ? (float) into / span : 1f;
            g.fill(barX, barY, barX + (int) (barW * frac), barY + barH, color);
            g.drawString(this.font, span > 0 ? into + " / " + span + " xp" : "MAX",
                barX, barY + barH + 1, span > 0 ? 0xFFAAAAAA : 0xFFAAFFAA);
            ry += rowH;
        }

        String useKey = VoxeliaKeys.USE_ABILITY.getTranslatedKeyMessage().getString();
        String cycleKey = VoxeliaKeys.CYCLE_ABILITY.getTranslatedKeyMessage().getString();
        g.drawCenteredString(this.font,
            "Use [" + useKey + "]   Cycle [" + cycleKey + "]   —   Selected: " + selected.abilityName(),
            this.width / 2, y + h - 16, 0xFF89C7FF);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Disable the vanilla menu blur so the game stays crisp behind the panel.
    @Override
    protected void renderBlurredBackground(float partialTick) {}
}
