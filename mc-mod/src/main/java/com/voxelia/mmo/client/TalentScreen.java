package com.voxelia.mmo.client;

import com.voxelia.mmo.network.PrestigePacket;
import com.voxelia.mmo.network.SpendTalentPacket;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import com.voxelia.mmo.skill.Talent;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    private static final int CARD_H = 32;
    private static final int CARD_GAP = 4;
    private static final int BADGE_W = 24;

    private static Skill selectedSkill = Skill.MINING;

    private record SkillRow(int x1, int y1, int x2, int y2, Skill skill) {}
    private record Card(int x1, int y1, int x2, int y2, Talent talent) {}
    private final List<SkillRow> skillRows = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();
    private int[] tabSkills = new int[4];
    private int[] tabTalents = new int[4];
    private int[] tabProfile = new int[4];
    private int[] prestigeBtn = new int[4];
    private boolean prestigeReady = false;
    // Prestige takes two clicks: the first arms the button, the second confirms.
    private boolean prestigeArmed = false;
    private long armedAt;
    // 100ms hover crossfades + instant spend feedback state.
    private final float[] cardHoverA = new float[Talent.values().length];
    private final float[] rowHoverA = new float[Skill.values().length];
    private long lastFrameMs = Util.getMillis();
    private Talent flashTalent;
    private int flashRank;
    private long flashMs;
    private long denyMs;

    public TalentScreen() {
        super(Component.literal("Talents"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No full-screen backdrop — the live game shows through; only the panel is drawn.
        long now = Util.getMillis();
        float hdt = Math.min(50, now - lastFrameMs) / 100f;
        lastFrameMs = now;
        float e = VoxeliaUi.introT();
        g.pose().pushPose();
        g.pose().translate(0, (1f - e) * 6f, 0);

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
        tabTalents = VoxeliaUi.tab(g, this.font, "Talents" + VoxeliaUi.keyTag(this.font, VoxeliaKeys.OPEN_TALENTS),
            x + PANEL_W - 4, y, true, mouseX, mouseY);
        tabSkills = VoxeliaUi.tab(g, this.font, "Skills" + VoxeliaUi.keyTag(this.font, VoxeliaKeys.OPEN_MENU),
            tabTalents[0] - 2, y, false, mouseX, mouseY);
        tabProfile = VoxeliaUi.tab(g, this.font, "Profile" + VoxeliaUi.keyTag(this.font, VoxeliaKeys.OPEN_PROFILE),
            tabSkills[0] - 2, y, false, mouseX, mouseY);

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
            rowHoverA[i] = Math.max(0f, Math.min(1f, rowHoverA[i] + (over ? hdt : -hdt)));

            if (sel) g.fill(lx, ry, lx + SKILL_W, ry + SKILL_ROW_H - 1, 0x2889C7FF);
            else if (rowHoverA[i] > 0.02f) {
                g.fill(lx, ry, lx + SKILL_W, ry + SKILL_ROW_H - 1, (((int) (0x14 * rowHoverA[i])) << 24) | 0xFFFFFF);
            }
            g.fill(lx, ry, lx + 3, ry + SKILL_ROW_H - 1, color);
            g.drawString(this.font, s.display(), lx + 7, ry + 3, sel ? 0xFFFFFFFF : color);

            // Stars shrink to "✦N" (and hide as a last resort) rather than colliding with the pill.
            int pts = ClientTalents.available(s);
            int pillL = pts > 0 ? lx + SKILL_W - 3 - (this.font.width(String.valueOf(pts)) + 8) : lx + SKILL_W - 3;
            int pres = ClientTalents.prestige(s);
            if (pres > 0) {
                int sx = lx + 8 + this.font.width(s.display());
                String stars = "✦".repeat(Math.min(pres, 3));
                if (sx + this.font.width(stars) > pillL - 2) stars = pres > 1 ? "✦" + pres : "✦";
                if (sx + this.font.width(stars) <= pillL - 2) {
                    g.drawString(this.font, stars, sx, ry + 3, VoxeliaUi.GOLD);
                }
            }
            if (pts > 0) VoxeliaUi.pill(g, this.font, lx + SKILL_W - 3, ry + 2, String.valueOf(pts), 0x6EE86E, true);
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
        int max = ClientTalents.maxRank();
        List<Talent> talents = Talent.forSkill(selectedSkill);

        // Header: skill name (left) + a points pill and ranks-spent count (right).
        g.drawString(this.font, selectedSkill.display().toUpperCase(Locale.ROOT),
            rx, contentTop, 0xFF000000 | selectedSkill.color());
        int spent = 0, capped = talents.size() * max;
        for (Talent t : talents) spent += ClientTalents.rank(t);
        String spentLabel = spent + "/" + capped;
        int pillRight = rx + RIGHT_W;
        int pillRgb = available > 0 ? 0x6EE86E
            : (Util.getMillis() - denyMs < 300 ? 0xEF5350 : 0x59636E); // deny blink
        int pillLeft = VoxeliaUi.pill(g, this.font, pillRight, contentTop - 1,
            available + (available == 1 ? " point" : " points"), pillRgb, available > 0);
        g.drawString(this.font, spentLabel, pillLeft - 6 - this.font.width(spentLabel), contentTop, VoxeliaUi.MUTED);

        Card hoveredCard = null;
        int cardsTop = contentTop + 14;
        if (flashTalent != null && Util.getMillis() - flashMs > 400) flashTalent = null;
        for (int i = 0; i < talents.size(); i++) {
            Talent t = talents.get(i);
            int cat = 0xFF000000 | t.category().color();
            int cy = cardsTop + i * (CARD_H + CARD_GAP);
            int rank = ClientTalents.rank(t);
            boolean canBuy = available > 0 && rank < max;
            boolean maxed = rank >= max;
            boolean over = mouseX >= rx && mouseX < rx + RIGHT_W && mouseY >= cy && mouseY < cy + CARD_H;
            cardHoverA[t.ordinal()] = Math.max(0f, Math.min(1f, cardHoverA[t.ordinal()] + (over ? hdt : -hdt)));
            float a = cardHoverA[t.ordinal()];

            int bg = canBuy ? 0xC01E3326 : (maxed ? 0xC0332C1A : 0xC0161C25);
            g.fillGradient(rx, cy, rx + RIGHT_W, cy + CARD_H,
                VoxeliaUi.brighten(bg, 12 + (int) (18 * a)), VoxeliaUi.brighten(bg, (int) (16 * a)));
            g.fill(rx, cy + CARD_H - 1, rx + RIGHT_W, cy + CARD_H, 0x38000000); // bottom seat
            g.fill(rx, cy, rx + 3, cy + CARD_H, cat); // category accent
            if (canBuy) { // breathing green edge invites the click
                int ga = 0x50 + (int) (0x60 * VoxeliaUi.pulse());
                int glow = (ga << 24) | 0x6EE86E;
                g.fill(rx, cy, rx + RIGHT_W, cy + 1, glow);
                g.fill(rx, cy + CARD_H - 1, rx + RIGHT_W, cy + CARD_H, glow);
            }

            // category badge (colored chip with the short code)
            int bx = rx + 7, by = cy + (CARD_H - 20) / 2;
            g.fill(bx, by, bx + BADGE_W, by + 20, cat);
            g.fill(bx, by, bx + BADGE_W, by + 1, 0x50FFFFFF);
            g.fill(bx, by + 19, bx + BADGE_W, by + 20, 0x40000000);
            g.drawCenteredString(this.font, t.code(), bx + BADGE_W / 2, by + 6, 0xFF14181C);

            int tx = bx + BADGE_W + 6;
            // line 1: name + rank
            g.drawString(this.font, t.display(), tx, cy + 3, maxed ? VoxeliaUi.GOLD : 0xFFFFFFFF);
            String rankStr = rank + "/" + max;
            g.drawString(this.font, rankStr, rx + RIGHT_W - 6 - this.font.width(rankStr), cy + 3,
                maxed ? VoxeliaUi.GOLD : VoxeliaUi.MUTED);

            // line 2: current concrete bonus (left, trimmed to the pip block) + rank pips (right)
            int pipBlockW = max >= 1 && max <= 10 ? max * 6 + (max - 1) * 2 : 0;
            int line2W = rx + RIGHT_W - 6 - pipBlockW - 6 - tx;
            g.drawString(this.font, VoxeliaUi.trim(this.font, rank > 0 ? t.bonusText(rank) : "No bonus yet", line2W),
                tx, cy + 13, rank > 0 ? cat : VoxeliaUi.DISABLED);
            drawPipsRight(g, rx + RIGHT_W - 6, cy + 14, rank, max, cat);

            // line 3: what the next point buys
            String next;
            int nextColor;
            if (maxed) { next = "Maxed out"; nextColor = VoxeliaUi.GOLD; }
            else if (canBuy) { next = "Click to raise: " + t.bonusText(rank + 1); nextColor = VoxeliaUi.GOOD; }
            else { next = "Next: " + t.bonusText(rank + 1) + " · need a point"; nextColor = VoxeliaUi.DISABLED; }
            g.drawString(this.font, VoxeliaUi.trim(this.font, next, rx + RIGHT_W - 6 - tx), tx, cy + 22, nextColor);

            // Instant spend acknowledgement: white flash + ghost pip until the server sync lands.
            if (t == flashTalent) {
                VoxeliaUi.flash(g, rx, cy, rx + RIGHT_W, cy + CARD_H, flashMs);
                if (ClientTalents.rank(t) == flashRank && flashRank < max) {
                    int pw = 6, pg = 2;
                    int px = rx + RIGHT_W - 6 - (max * pw + (max - 1) * pg) + flashRank * (pw + pg);
                    g.fill(px, cy + 14, px + pw, cy + 19, 0x80000000 | (cat & 0xFFFFFF));
                }
            }

            Card card = new Card(rx, cy, rx + RIGHT_W, cy + CARD_H, t);
            cards.add(card);
            if (over) hoveredCard = card;
        }

        // Footer: normally a hint; a Prestige button once the selected skill is maxed.
        int footY = y + h - FOOTER_H;
        VoxeliaUi.footer(g, x, footY, PANEL_W, FOOTER_H);
        int prestige = ClientTalents.prestige(selectedSkill);
        boolean atMax = ClientSkillData.level(selectedSkill) >= SkillCurve.MAX_LEVEL;
        prestigeReady = atMax && prestige < ClientTalents.prestigeMax();
        if (prestigeArmed && Util.getMillis() - armedAt > 4000) prestigeArmed = false; // armed fuse ran out
        if (!prestigeReady) prestigeArmed = false;
        boolean overPrestige = false;
        if (prestigeReady) {
            String label = prestigeArmed
                ? "CLICK AGAIN — RESET " + selectedSkill.display().toUpperCase(Locale.ROOT) + " TO LV 1"
                : "✦ PRESTIGE " + selectedSkill.display() + " ✦";
            int lw = this.font.width(label) + 14;
            int bx = x + (PANEL_W - lw) / 2;
            int by = footY + 1;
            overPrestige = mouseX >= bx && mouseX < bx + lw && mouseY >= by && mouseY < by + 12;
            int base = prestigeArmed
                ? VoxeliaUi.lerp(0xFF8A2E2E, 0xFFC24444, 0.5f + 0.5f * (float) Math.sin(Util.getMillis() / 120.0))
                : 0xFF7A34A8;
            g.fill(bx, by, bx + lw, by + 12, overPrestige ? VoxeliaUi.brighten(base, 34) : base);
            if (!prestigeArmed) { // breathing gold frame: the established "invites a click" language
                int ga = 0x50 + (int) (0x50 * VoxeliaUi.pulse());
                int gc = (ga << 24) | 0xFFCE54;
                g.fill(bx - 1, by - 1, bx + lw + 1, by, gc);
                g.fill(bx - 1, by + 12, bx + lw + 1, by + 13, gc);
                g.fill(bx - 1, by, bx, by + 12, gc);
                g.fill(bx + lw, by, bx + lw + 1, by + 12, gc);
            }
            g.fill(bx, by, bx + lw, by + 1, 0x60FFFFFF);
            if (prestigeArmed) { // draining fuse: how long until the button disarms itself
                float left = 1f - (Util.getMillis() - armedAt) / 4000f;
                g.fill(bx + 1, by + 11, bx + 1 + (int) ((lw - 2) * left), by + 12, 0xB0FFFFFF);
            }
            g.drawCenteredString(this.font, label, x + PANEL_W / 2, by + 2, 0xFFFFFFFF);
            prestigeBtn = new int[]{bx, by, bx + lw, by + 12};
        } else {
            prestigeBtn = new int[]{0, 0, 0, 0};
            if (atMax && prestige > 0) {
                g.drawCenteredString(this.font, "✦ " + selectedSkill.display() + " fully prestiged (" + prestige + ")",
                    x + PANEL_W / 2, footY + 3, VoxeliaUi.GOLD);
            } else if (available == 0) { // when there's nothing to spend, show the path to the next point
                int lvl = ClientSkillData.level(selectedSkill);
                int next = (lvl / ClientTalents.levelsPerPoint() + 1) * ClientTalents.levelsPerPoint();
                int togo = next - lvl;
                g.drawCenteredString(this.font, "Next " + selectedSkill.display() + " point at Lv " + next
                        + " — " + togo + (togo == 1 ? " level" : " levels") + " to go",
                    x + PANEL_W / 2, footY + 3, VoxeliaUi.MUTED);
            } else {
                g.drawCenteredString(this.font, "Pick a skill, click a talent to spend  •  /voxelia talent reset to refund",
                    x + PANEL_W / 2, footY + 3, VoxeliaUi.MUTED);
            }
        }

        g.pose().popPose();
        super.render(g, mouseX, mouseY, partialTick);

        if (hoveredCard != null) renderCardTooltip(g, hoveredCard.talent, max, mouseX, mouseY);
        else if (overPrestige) {
            int perPrestige = ClientTalents.pointsPerPrestige();
            g.renderComponentTooltip(this.font, List.of(
                Component.literal("Prestige " + selectedSkill.display()).withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.literal("Resets it to level 1 and refunds its talents,").withStyle(ChatFormatting.GRAY),
                Component.literal("but grants " + perPrestige + " permanent extra talent point"
                    + (perPrestige == 1 ? "" : "s") + ".").withStyle(ChatFormatting.GRAY),
                Component.literal("Prestige " + prestige + " → " + (prestige + 1)).withStyle(ChatFormatting.WHITE),
                prestigeArmed
                    ? Component.literal("Click again to confirm.").withStyle(ChatFormatting.RED)
                    : Component.literal("Takes two clicks — no accidents.").withStyle(ChatFormatting.DARK_GRAY)),
                mouseX, mouseY);
        } else if (hoveredSkill != null && hoveredSkill.skill != selectedSkill) {
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
                int lvl = ClientSkillData.level(t.skill());
                if (lvl < SkillCurve.MAX_LEVEL) {
                    int next = (lvl / ClientTalents.levelsPerPoint() + 1) * ClientTalents.levelsPerPoint();
                    tip.add(Component.literal("Next point at " + t.skill().display() + " Lv " + next
                        + " (" + (next - lvl) + " to go)").withStyle(ChatFormatting.RED));
                } else {
                    tip.add(Component.literal("Prestige " + t.skill().display() + " for more points")
                        .withStyle(ChatFormatting.RED));
                }
            }
        }
        g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
    }

    private static boolean in(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3];
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean onPrestigeBtn = prestigeReady && in(prestigeBtn, mouseX, mouseY);
            if (!onPrestigeBtn) prestigeArmed = false; // clicking anywhere else disarms
            if (in(tabSkills, mouseX, mouseY)) {
                Minecraft.getInstance().setScreen(new SkillsScreen());
                return true;
            }
            if (in(tabProfile, mouseX, mouseY)) {
                Minecraft.getInstance().setScreen(new ProfileScreen());
                return true;
            }
            if (in(tabTalents, mouseX, mouseY)) return true;
            if (onPrestigeBtn) {
                if (prestigeArmed) {
                    PacketDistributor.sendToServer(new PrestigePacket(selectedSkill.ordinal()));
                    prestigeArmed = false;
                } else {
                    prestigeArmed = true;
                    armedAt = Util.getMillis();
                }
                return true;
            }
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
                        flashTalent = c.talent;
                        flashRank = ClientTalents.rank(c.talent);
                        flashMs = Util.getMillis();
                    } else {
                        denyMs = Util.getMillis(); // no point to spend → blink the points pill red
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
        if (VoxeliaKeys.OPEN_MENU.matches(keyCode, scanCode)) { // hop to the sibling screens
            Minecraft.getInstance().setScreen(new SkillsScreen());
            return true;
        }
        if (VoxeliaKeys.OPEN_PROFILE.matches(keyCode, scanCode)) {
            Minecraft.getInstance().setScreen(new ProfileScreen());
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
