package com.voxelia.mmo.client;

import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Skills panel (K): a 2-column card grid — one card per skill plus a gold Character
 * card — with animated XP bars and rich hover tooltips. Active-ability cards are
 * clickable to select that ability; tabs (or the N key) hop to the Talent screen.
 * Compact enough (~208px tall) to fit a 240px-tall scaled GUI.
 */
public final class SkillsScreen extends Screen {
    private static final int PAD = 6;
    private static final int CARD_W = 154;
    private static final int CARD_H = 26;
    private static final int GAP = 3;
    private static final int PANEL_W = PAD + CARD_W + 4 + CARD_W + PAD;
    private static final int TITLE_H = 17;
    private static final int FOOTER_H = 14;

    private record Card(int x1, int y1, int x2, int y2, Skill skill) {}
    private final List<Card> cards = new ArrayList<>();
    private int[] tabSkills = new int[4];
    private int[] tabTalents = new int[4];
    private int[] charCard = new int[4];

    public SkillsScreen() {
        super(Component.literal("Voxelia Skills"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No full-screen backdrop — the live game shows through; only the panel is drawn.
        cards.clear();
        Skill[] all = Skill.values();
        int rows = (all.length + 2) / 2; // skills + the character card
        int h = TITLE_H + GAP + rows * CARD_H + (rows - 1) * GAP + GAP + FOOTER_H;
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - h) / 2;

        VoxeliaUi.panel(g, x, y, PANEL_W, h);
        VoxeliaUi.titleBar(g, this.font, x, y, PANEL_W, "VOXELIA");
        tabTalents = VoxeliaUi.tab(g, this.font, "Talents [" + keyName(VoxeliaKeys.OPEN_TALENTS) + "]",
            x + PANEL_W - 4, y, false, mouseX, mouseY);
        tabSkills = VoxeliaUi.tab(g, this.font, "Skills [" + keyName(VoxeliaKeys.OPEN_MENU) + "]",
            tabTalents[0] - 2, y, true, mouseX, mouseY);

        Skill selected = ClientAbilities.selectedSkill();
        Card hovered = null;
        for (int i = 0; i < all.length; i++) {
            Skill skill = all[i];
            int cx = x + PAD + (i % 2) * (CARD_W + 4);
            int cy = y + TITLE_H + GAP + (i / 2) * (CARD_H + GAP);
            boolean over = mouseX >= cx && mouseX < cx + CARD_W && mouseY >= cy && mouseY < cy + CARD_H;
            boolean sel = skill == selected;
            int color = 0xFF000000 | skill.color();

            g.fill(cx, cy, cx + CARD_W, cy + CARD_H, over ? 0xE0203040 : 0xC0182430);
            g.fill(cx, cy, cx + 3, cy + CARD_H, color);
            if (sel) {
                g.fill(cx, cy, cx + CARD_W, cy + 1, VoxeliaUi.LINK);
                g.fill(cx, cy + CARD_H - 1, cx + CARD_W, cy + CARD_H, VoxeliaUi.LINK);
                g.fill(cx, cy, cx + 1, cy + CARD_H, VoxeliaUi.LINK);
                g.fill(cx + CARD_W - 1, cy, cx + CARD_W, cy + CARD_H, VoxeliaUi.LINK);
            } else if (over) {
                g.fill(cx, cy, cx + CARD_W, cy + 1, 0x60FFFFFF);
                g.fill(cx, cy + CARD_H - 1, cx + CARD_W, cy + CARD_H, 0x60FFFFFF);
            }

            int xp = ClientSkillData.xp(skill);
            int level = SkillCurve.levelForXp(xp);
            int into = SkillCurve.xpIntoLevel(xp);
            int span = SkillCurve.xpToNext(xp);

            g.drawString(this.font, skill.display(), cx + 7, cy + 4, color);
            String lv = "Lv " + level;
            g.drawString(this.font, lv, cx + CARD_W - 6 - this.font.width(lv), cy + 4,
                span > 0 ? 0xFFFFFFFF : VoxeliaUi.GOOD);
            if (skill.active()) { // small cyan corner dot: "this one is clickable"
                g.fill(cx + CARD_W - 5, cy + 2, cx + CARD_W - 2, cy + 5, sel ? VoxeliaUi.GOLD : VoxeliaUi.LINK);
            }
            float frac = span > 0 ? (float) into / span : 1f;
            VoxeliaUi.bar(g, cx + 7, cy + 17, CARD_W - 13, 4, frac, span > 0 ? skill.color() : 0x7CFC00, true);

            Card card = new Card(cx, cy, cx + CARD_W, cy + CARD_H, skill);
            cards.add(card);
            if (over) hovered = card;
        }

        // Character card in the last grid slot.
        int ccx = x + PAD + CARD_W + 4;
        int ccy = y + TITLE_H + GAP + (rows - 1) * (CARD_H + GAP);
        boolean overChar = mouseX >= ccx && mouseX < ccx + CARD_W && mouseY >= ccy && mouseY < ccy + CARD_H;
        charCard = new int[]{ccx, ccy, ccx + CARD_W, ccy + CARD_H};
        g.fill(ccx, ccy, ccx + CARD_W, ccy + CARD_H, overChar ? 0xE0242C38 : 0xC01C222E);
        g.fill(ccx, ccy, ccx + 3, ccy + CARD_H, VoxeliaUi.GOLD);
        int total = 0;
        float progress = 0f;
        for (Skill s : all) {
            total += ClientSkillData.level(s);
            int sp = SkillCurve.xpToNext(ClientSkillData.xp(s));
            progress += sp > 0 ? (float) SkillCurve.xpIntoLevel(ClientSkillData.xp(s)) / sp : 1f;
        }
        int charLevel = Math.max(1, Math.round(total / (float) all.length));
        g.drawString(this.font, "Character", ccx + 7, ccy + 4, VoxeliaUi.GOLD);
        String clv = "Lv " + charLevel;
        g.drawString(this.font, clv, ccx + CARD_W - 6 - this.font.width(clv), ccy + 4, 0xFFFFFFFF);
        VoxeliaUi.bar(g, ccx + 7, ccy + 17, CARD_W - 13, 4, progress / all.length, 0xFFCE54, true);

        // Footer: key hints with the keys in cyan, then the selected ability.
        VoxeliaUi.footer(g, x, y + h - FOOTER_H, PANEL_W, FOOTER_H);
        int fy = y + h - FOOTER_H + 3;
        int fx = x + PAD;
        fx = seg(g, fx, fy, "Use ", VoxeliaUi.MUTED);
        fx = seg(g, fx, fy, "[" + keyName(VoxeliaKeys.USE_ABILITY) + "]", VoxeliaUi.LINK);
        fx = seg(g, fx, fy, "  Cycle ", VoxeliaUi.MUTED);
        fx = seg(g, fx, fy, "[" + keyName(VoxeliaKeys.CYCLE_ABILITY) + "]", VoxeliaUi.LINK);
        fx = seg(g, fx, fy, "  ▶ ", VoxeliaUi.MUTED);
        seg(g, fx, fy, selected.abilityName(), VoxeliaUi.LINK);

        super.render(g, mouseX, mouseY, partialTick);

        if (hovered != null) {
            renderSkillTooltip(g, hovered.skill, selected, mouseX, mouseY);
        } else if (overChar) {
            Skill top = all[0];
            for (Skill s : all) if (ClientSkillData.level(s) > ClientSkillData.level(top)) top = s;
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal("Character Lv " + charLevel + " ✦ " + top.noun())
                .withStyle(ChatFormatting.GOLD));
            tip.add(Component.literal(total + " total skill levels").withStyle(ChatFormatting.WHITE));
            tip.add(Component.literal("Highest: " + top.display() + " Lv " + ClientSkillData.level(top))
                .withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
        }
    }

    private void renderSkillTooltip(GuiGraphics g, Skill skill, Skill selected, int mouseX, int mouseY) {
        int xp = ClientSkillData.xp(skill);
        int level = SkillCurve.levelForXp(xp);
        int into = SkillCurve.xpIntoLevel(xp);
        int span = SkillCurve.xpToNext(xp);
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(skill.display() + " — " + skill.noun()).withStyle(ChatFormatting.GOLD));
        if (span > 0) {
            int pct = (int) (100f * into / span);
            tip.add(Component.literal("Level " + level + "  (" + pct + "% to " + (level + 1) + ")")
                .withStyle(ChatFormatting.WHITE));
            tip.add(Component.literal(String.format(Locale.ROOT, "%,d / %,d xp", into, span))
                .withStyle(ChatFormatting.GRAY));
        } else {
            tip.add(Component.literal("Level " + level).withStyle(ChatFormatting.WHITE));
            tip.add(Component.literal("MAX LEVEL").withStyle(ChatFormatting.YELLOW));
        }
        if (skill.active()) {
            tip.add(skill == selected
                ? Component.literal("Ability: " + skill.abilityName() + " — selected").withStyle(ChatFormatting.AQUA)
                : Component.literal("Ability: " + skill.abilityName() + " — click to select").withStyle(ChatFormatting.GREEN));
        } else {
            tip.add(Component.literal("Passive: " + skill.abilityName()).withStyle(ChatFormatting.GRAY));
        }
        int pts = ClientTalents.available(skill);
        if (pts > 0) {
            tip.add(Component.literal(pts + " talent point" + (pts == 1 ? "" : "s") + " unspent ["
                + keyName(VoxeliaKeys.OPEN_TALENTS) + "]").withStyle(ChatFormatting.GREEN));
        }
        g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
    }

    private int seg(GuiGraphics g, int x, int y, String text, int color) {
        g.drawString(this.font, text, x, y, color);
        return x + this.font.width(text);
    }

    private static String keyName(net.minecraft.client.KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }

    private static boolean in(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3];
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (in(tabTalents, mouseX, mouseY)) {
                Minecraft.getInstance().setScreen(new TalentScreen());
                return true;
            }
            if (in(tabSkills, mouseX, mouseY)) return true;
            for (Card c : cards) {
                if (mouseX >= c.x1 && mouseX < c.x2 && mouseY >= c.y1 && mouseY < c.y2) {
                    if (c.skill.active()) ClientAbilities.select(c.skill);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (VoxeliaKeys.OPEN_MENU.matches(keyCode, scanCode)) { // same key toggles closed
            this.onClose();
            return true;
        }
        if (VoxeliaKeys.OPEN_TALENTS.matches(keyCode, scanCode)) { // hop to the sibling screen
            Minecraft.getInstance().setScreen(new TalentScreen());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Disable the vanilla menu blur so the game stays crisp behind the panel.
    @Override
    protected void renderBlurredBackground(float partialTick) {}
}
