package com.voxelia.mmo.client;

import com.voxelia.mmo.network.SpendTalentPacket;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.Talent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Talent tree (N): pick a skill on the left, spend its points on that skill's own
 * thematic talents on the right — Mining boosts break speed and ore Fortune, Combat
 * boosts damage and life steal, and so on. Each talent card shows exactly what a
 * point buys, at its current rank and the next.
 */
public final class TalentScreen extends Screen {
    private static final int PAD = 6;
    private static final int TITLE_H = 17;
    private static final int FOOTER_H = 14;
    private static final int SKILL_W = 104;
    private static final int SKILL_ROW_H = 15;
    private static final int GAP = 6;
    private static final int RIGHT_W = 232;
    private static final int PANEL_W = PAD + SKILL_W + GAP + RIGHT_W + PAD;
    private static final int CARD_H = 30;
    private static final int CARD_GAP = 4;

    private static Skill selectedSkill = Skill.MINING;

    private record SkillRow(int x1, int y1, int x2, int y2, Skill skill) {}
    private record Card(int x1, int y1, int x2, int y2, Talent talent) {}
    private final List<SkillRow> skillRows = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();
    private int[] tabSkills = new int[4];
    private int[] tabTalents = new int[4];

    public TalentScreen() {
        super(Component.literal("Talents"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No full-screen backdrop — the live game shows through; only the panel is drawn.
        skillRows.clear();
        cards.clear();

        int listH = Skill.values().length * SKILL_ROW_H;
        int maxCards = 0;
        for (Skill s : Skill.values()) maxCards = Math.max(maxCards, Talent.forSkill(s).size());
        int rightH = 14 + maxCards * CARD_H + (maxCards - 1) * CARD_GAP;
        int contentH = Math.max(listH, rightH);
        int h = TITLE_H + 4 + contentH + 4 + FOOTER_H;
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - h) / 2;

        VoxeliaUi.panel(g, x, y, PANEL_W, h);
        VoxeliaUi.titleBar(g, this.font, x, y, PANEL_W, "VOXELIA");
        tabTalents = VoxeliaUi.tab(g, this.font, "Talents [" + keyName(VoxeliaKeys.OPEN_TALENTS) + "]",
            x + PANEL_W - 4, y, true, mouseX, mouseY);
        tabSkills = VoxeliaUi.tab(g, this.font, "Skills [" + keyName(VoxeliaKeys.OPEN_MENU) + "]",
            tabTalents[0] - 2, y, false, mouseX, mouseY);

        int contentTop = y + TITLE_H + 4;

        // ── Left: skill selector ────────────────────────────────────────────
        SkillRow hoveredSkill = null;
        int lx = x + PAD;
        for (int i = 0; i < Skill.values().length; i++) {
            Skill s = Skill.values()[i];
            int ry = contentTop + i * SKILL_ROW_H;
            boolean sel = s == selectedSkill;
            boolean over = mouseX >= lx && mouseX < lx + SKILL_W && mouseY >= ry && mouseY < ry + SKILL_ROW_H;
            int color = 0xFF000000 | s.color();

            if (sel) g.fill(lx, ry, lx + SKILL_W, ry + SKILL_ROW_H - 1, 0x2889C7FF);
            else if (over) g.fill(lx, ry, lx + SKILL_W, ry + SKILL_ROW_H - 1, 0x14FFFFFF);
            g.fill(lx, ry, lx + 3, ry + SKILL_ROW_H - 1, color);
            g.drawString(this.font, s.display(), lx + 7, ry + 3, sel ? 0xFFFFFFFF : color);

            int pts = ClientTalents.available(s);
            if (pts > 0) {
                String badge = String.valueOf(pts);
                g.drawString(this.font, badge, lx + SKILL_W - 5 - this.font.width(badge), ry + 3, VoxeliaUi.GOOD);
            }
            SkillRow row = new SkillRow(lx, ry, lx + SKILL_W, ry + SKILL_ROW_H, s);
            skillRows.add(row);
            if (over) hoveredSkill = row;
        }

        // separator between panes
        int sepX = x + PAD + SKILL_W + GAP / 2;
        g.fill(sepX, contentTop, sepX + 1, contentTop + listH, 0x30FFFFFF);

        // ── Right: selected skill's talents ─────────────────────────────────
        int rx = x + PAD + SKILL_W + GAP;
        int available = ClientTalents.available(selectedSkill);
        g.drawString(this.font, selectedSkill.display() + " Talents",
            rx, contentTop, 0xFF000000 | selectedSkill.color());
        String ptsLabel = available + " pt" + (available == 1 ? "" : "s");
        g.drawString(this.font, ptsLabel, rx + RIGHT_W - this.font.width(ptsLabel), contentTop,
            available > 0 ? VoxeliaUi.GOOD : VoxeliaUi.DISABLED);

        int max = ClientTalents.maxRank();
        List<Talent> talents = Talent.forSkill(selectedSkill);
        Card hoveredCard = null;
        int cardsTop = contentTop + 14;
        for (int i = 0; i < talents.size(); i++) {
            Talent t = talents.get(i);
            int cy = cardsTop + i * (CARD_H + CARD_GAP);
            int rank = ClientTalents.rank(t);
            boolean canBuy = available > 0 && rank < max;
            boolean maxed = rank >= max;
            boolean over = mouseX >= rx && mouseX < rx + RIGHT_W && mouseY >= cy && mouseY < cy + CARD_H;

            int bg = canBuy ? 0xC01E3326 : (maxed ? 0xC0332C1A : 0xC01A222C);
            g.fill(rx, cy, rx + RIGHT_W, cy + CARD_H, over ? VoxeliaUi.brighten(bg, 22) : bg);
            g.fill(rx, cy, rx + 3, cy + CARD_H, 0xFF000000 | selectedSkill.color());
            if (canBuy) { // breathing green edge invites the click
                int a = 0x50 + (int) (0x60 * VoxeliaUi.pulse());
                int glow = (a << 24) | 0x6EE86E;
                g.fill(rx, cy, rx + RIGHT_W, cy + 1, glow);
                g.fill(rx, cy + CARD_H - 1, rx + RIGHT_W, cy + CARD_H, glow);
            }

            // line 1: name + rank
            g.drawString(this.font, t.display(), rx + 8, cy + 2, maxed ? VoxeliaUi.GOLD : 0xFFFFFFFF);
            String rankStr = rank + "/" + max;
            g.drawString(this.font, rankStr, rx + RIGHT_W - 6 - this.font.width(rankStr), cy + 2,
                maxed ? VoxeliaUi.GOLD : VoxeliaUi.MUTED);

            // line 2: current concrete bonus (left, trimmed to the pip block) + rank pips (right)
            int pipBlockW = max >= 1 && max <= 10 ? max * 6 + (max - 1) * 2 : 0;
            int line2W = RIGHT_W - 8 - pipBlockW - 10;
            g.drawString(this.font, trim(rank > 0 ? t.bonusText(rank) : "No bonus yet", line2W), rx + 8, cy + 11,
                rank > 0 ? VoxeliaUi.LINK : VoxeliaUi.DISABLED);
            drawPipsRight(g, rx + RIGHT_W - 6, cy + 12, rank, max, 0xFF000000 | selectedSkill.color());

            // line 3: what the next point buys
            String next;
            int nextColor;
            if (maxed) { next = "Maxed out"; nextColor = VoxeliaUi.GOLD; }
            else if (canBuy) { next = "Click: " + t.bonusText(rank + 1) + "  (" + t.perRankText() + ")"; nextColor = VoxeliaUi.GOOD; }
            else { next = "Next: " + t.bonusText(rank + 1) + "  (need a point)"; nextColor = VoxeliaUi.DISABLED; }
            g.drawString(this.font, trim(next, RIGHT_W - 12), rx + 8, cy + 20, nextColor);

            Card card = new Card(rx, cy, rx + RIGHT_W, cy + CARD_H, t);
            cards.add(card);
            if (over) hoveredCard = card;
        }

        VoxeliaUi.footer(g, x, y + h - FOOTER_H, PANEL_W, FOOTER_H);
        g.drawCenteredString(this.font, "Pick a skill, click a talent to spend  •  /voxelia talent reset to refund",
            x + PANEL_W / 2, y + h - FOOTER_H + 3, VoxeliaUi.MUTED);

        super.render(g, mouseX, mouseY, partialTick);

        if (hoveredCard != null) renderCardTooltip(g, hoveredCard.talent, max, mouseX, mouseY);
        else if (hoveredSkill != null && hoveredSkill.skill != selectedSkill) {
            g.renderComponentTooltip(this.font, List.of(
                Component.literal(hoveredSkill.skill.display()).withStyle(ChatFormatting.GOLD),
                Component.literal(ClientTalents.available(hoveredSkill.skill) + " point(s) to spend")
                    .withStyle(ChatFormatting.GREEN)), mouseX, mouseY);
        }
    }

    /** Rank pips whose right edge ends at {@code xRight}. */
    private void drawPipsRight(GuiGraphics g, int xRight, int y, int rank, int max, int fillColor) {
        if (max < 1 || max > 10) return;
        int pw = 6, gap = 2;
        int x = xRight - (max * pw + (max - 1) * gap);
        for (int p = 0; p < max; p++) {
            int px = x + p * (pw + gap);
            if (p < rank) {
                g.fill(px, y, px + pw, y + 5, fillColor);
                g.fill(px, y, px + pw, y + 1, 0x60FFFFFF);
            } else {
                g.fill(px, y, px + pw, y + 5, 0xFF10161D);
            }
        }
    }

    private void renderCardTooltip(GuiGraphics g, Talent t, int max, int mouseX, int mouseY) {
        int rank = ClientTalents.rank(t);
        int available = ClientTalents.available(t.skill());
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(t.display() + " · " + t.skill().display()).withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal(t.blurb()).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Rank " + rank + " / " + max).withStyle(ChatFormatting.WHITE));
        if (rank > 0) tip.add(Component.literal("Now: " + t.bonusText(rank)).withStyle(ChatFormatting.AQUA));
        if (rank >= max) {
            tip.add(Component.literal("Fully upgraded").withStyle(ChatFormatting.YELLOW));
        } else {
            tip.add(Component.literal("Next rank: " + t.bonusText(rank + 1) + "  (" + t.perRankText() + ")")
                .withStyle(available > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            if (available <= 0) {
                tip.add(Component.literal("Level up " + t.skill().display() + " for a point")
                    .withStyle(ChatFormatting.RED));
            }
        }
        g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
    }

    private String trim(String s, int maxW) {
        if (this.font.width(s) <= maxW) return s;
        while (s.length() > 1 && this.font.width(s + "…") > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
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
            if (in(tabSkills, mouseX, mouseY)) {
                Minecraft.getInstance().setScreen(new SkillsScreen());
                return true;
            }
            if (in(tabTalents, mouseX, mouseY)) return true;
            for (SkillRow r : skillRows) {
                if (mouseX >= r.x1 && mouseX < r.x2 && mouseY >= r.y1 && mouseY < r.y2) {
                    selectedSkill = r.skill;
                    return true;
                }
            }
            for (Card c : cards) {
                if (mouseX >= c.x1 && mouseX < c.x2 && mouseY >= c.y1 && mouseY < c.y2) {
                    if (ClientTalents.available(c.talent.skill()) > 0
                        && ClientTalents.rank(c.talent) < ClientTalents.maxRank()) {
                        PacketDistributor.sendToServer(new SpendTalentPacket(c.talent.ordinal()));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (VoxeliaKeys.OPEN_TALENTS.matches(keyCode, scanCode)) { // same key toggles closed
            this.onClose();
            return true;
        }
        if (VoxeliaKeys.OPEN_MENU.matches(keyCode, scanCode)) { // hop to the sibling screen
            Minecraft.getInstance().setScreen(new SkillsScreen());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // Disable the vanilla menu blur so the game stays crisp behind the panel.
    @Override
    protected void renderBlurredBackground(float partialTick) {}
}
