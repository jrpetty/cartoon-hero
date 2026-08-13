package com.voxelia.mmo.client;

import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Skills panel (K) — the mod's hub screen: a 2-column card grid, one card per skill
 * plus a gold Character card, with animated XP bars and rich hover tooltips.
 * Active-ability cards are clickable to select that ability, and the Menu button in
 * the title bar reaches everything else (talents, profile, display toggles).
 * Compact enough (~215px tall) to fit a 240px-tall scaled GUI.
 */
public final class SkillsScreen extends Screen {
    private static final int PAD = 6;
    private static final int CARD_W = 154;
    private static final int CARD_H = 26;
    private static final int GAP = 4;
    private static final int PANEL_W = PAD + CARD_W + 4 + CARD_W + PAD;
    private static final int TITLE_H = 17;
    private static final int FOOTER_H = 14;

    private record Card(int x1, int y1, int x2, int y2, Skill skill) {}
    private final List<Card> cards = new ArrayList<>();
    private final ScreenMenu menu = new ScreenMenu();
    private int[] charCard = new int[4];
    // 100ms hover crossfades: one slot per skill card + one for the Character card.
    private final float[] hoverA = new float[Skill.values().length + 1];
    private long lastFrameMs = Util.getMillis();

    public SkillsScreen() {
        super(Component.literal("Voxelia Skills"));
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

        cards.clear();
        Skill[] all = Skill.values();
        int rows = (all.length + 2) / 2; // skills + the character card
        int h = TITLE_H + GAP + rows * CARD_H + (rows - 1) * GAP + GAP + FOOTER_H;
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - h) / 2;

        VoxeliaUi.panel(g, x, y, PANEL_W, h);
        VoxeliaUi.titleBar(g, this.font, x, y, PANEL_W, "VOXELIA");
        int totalPts = 0;
        for (Skill s : all) totalPts += ClientTalents.available(s);
        menu.renderButton(g, this.font, x, y, PANEL_W, mouseX, mouseY, totalPts > 0);

        Skill selected = ClientAbilities.selectedSkill();
        Card hovered = null;
        for (int i = 0; i < all.length; i++) {
            Skill skill = all[i];
            int cx = x + PAD + (i % 2) * (CARD_W + 4);
            int cy = y + TITLE_H + GAP + (i / 2) * (CARD_H + GAP);
            boolean over = !menu.isOpen()
                && mouseX >= cx && mouseX < cx + CARD_W && mouseY >= cy && mouseY < cy + CARD_H;
            boolean sel = skill == selected;
            int color = 0xFF000000 | skill.color();
            hoverA[i] = Math.max(0f, Math.min(1f, hoverA[i] + (over ? hdt : -hdt)));
            float a = hoverA[i];

            // Lacquered card body: top-lit gradient that lifts as the hover fades in.
            int bodyA = 0xC8 + (int) (0x20 * a);
            int topC = (bodyA << 24) | (VoxeliaUi.lerp(0xFF1E2B3A, 0xFF263850, a) & 0xFFFFFF);
            int botC = (bodyA << 24) | (VoxeliaUi.lerp(0xFF131C27, 0xFF1A2536, a) & 0xFFFFFF);
            g.fillGradient(cx, cy, cx + CARD_W, cy + CARD_H, topC, botC);
            g.fill(cx, cy + CARD_H - 1, cx + CARD_W, cy + CARD_H, 0x40000000); // bottom seat
            g.fillGradient(cx, cy, cx + 3, cy + CARD_H,
                VoxeliaUi.brighten(color, 30), VoxeliaUi.lerp(color, 0xFF0A0F14, 0.35f));
            if (sel) {
                g.fill(cx, cy, cx + CARD_W, cy + 1, VoxeliaUi.LINK);
                g.fill(cx, cy + CARD_H - 1, cx + CARD_W, cy + CARD_H, VoxeliaUi.LINK);
                g.fill(cx, cy, cx + 1, cy + CARD_H, VoxeliaUi.LINK);
                g.fill(cx + CARD_W - 1, cy, cx + CARD_W, cy + CARD_H, VoxeliaUi.LINK);
            } else if (a > 0.02f) {
                int ha = (((int) (0x60 * a)) << 24) | 0xFFFFFF;
                g.fill(cx, cy, cx + CARD_W, cy + 1, ha);
                g.fill(cx, cy + CARD_H - 1, cx + CARD_W, cy + CARD_H, ha);
            }

            int xp = ClientSkillData.xp(skill);
            int level = SkillCurve.levelForXp(xp);
            int into = SkillCurve.xpIntoLevel(xp);
            int span = SkillCurve.xpToNext(xp);

            g.drawString(this.font, skill.display(), cx + 7, cy + 4, color);
            String lv = "Lv " + level;
            g.drawString(this.font, lv, cx + CARD_W - 6 - this.font.width(lv), cy + 4,
                span > 0 ? 0xFFFFFFFF : VoxeliaUi.GOOD);
            int pts = ClientTalents.available(skill);
            if (pts > 0) { // unspent talent points, in the Talent screen's green pill language
                VoxeliaUi.pill(g, this.font, cx + CARD_W - 6 - this.font.width(lv) - 4, cy + 2,
                    String.valueOf(pts), 0x6EE86E, true);
            }
            if (skill.active()) { // small cyan corner dot: "this one is clickable"
                g.fill(cx + CARD_W - 5, cy + 2, cx + CARD_W - 2, cy + 5, sel ? VoxeliaUi.GOLD : VoxeliaUi.LINK);
            }
            float frac = span > 0 ? (float) into / span : 1f;
            VoxeliaUi.bar(g, cx + 7, cy + 17, CARD_W - 13, 4, frac, span > 0 ? skill.color() : 0x7CFC00, true);

            Card card = new Card(cx, cy, cx + CARD_W, cy + CARD_H, skill);
            cards.add(card);
            if (over) hovered = card;
        }

        // Character card in the last grid slot — the premium gold card.
        int ccx = x + PAD + CARD_W + 4;
        int ccy = y + TITLE_H + GAP + (rows - 1) * (CARD_H + GAP);
        boolean overChar = !menu.isOpen()
            && mouseX >= ccx && mouseX < ccx + CARD_W && mouseY >= ccy && mouseY < ccy + CARD_H;
        int ci = all.length;
        hoverA[ci] = Math.max(0f, Math.min(1f, hoverA[ci] + (overChar ? hdt : -hdt)));
        float ca = hoverA[ci];
        charCard = new int[]{ccx, ccy, ccx + CARD_W, ccy + CARD_H};
        int cBodyA = 0xC8 + (int) (0x20 * ca);
        int cTop = (cBodyA << 24) | (VoxeliaUi.lerp(0xFF2C2A1E, 0xFF383426, ca) & 0xFFFFFF);
        int cBot = (cBodyA << 24) | (VoxeliaUi.lerp(0xFF181610, 0xFF201C12, ca) & 0xFFFFFF);
        g.fillGradient(ccx, ccy, ccx + CARD_W, ccy + CARD_H, cTop, cBot);
        g.fill(ccx, ccy + CARD_H - 1, ccx + CARD_W, ccy + CARD_H, 0x40000000);
        g.fillGradient(ccx, ccy, ccx + 3, ccy + CARD_H,
            VoxeliaUi.brighten(0xFFFFCE54, 30), VoxeliaUi.lerp(0xFFFFCE54, 0xFF0A0F14, 0.35f));
        if (ca > 0.02f) {
            int ga = (((int) (0x80 * ca)) << 24) | 0xFFCE54;
            g.fill(ccx, ccy, ccx + CARD_W, ccy + 1, ga);
            g.fill(ccx, ccy + CARD_H - 1, ccx + CARD_W, ccy + CARD_H, ga);
        }
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
        g.fill(ccx + CARD_W - 5, ccy + 2, ccx + CARD_W - 2, ccy + 5, VoxeliaUi.GOLD);
        VoxeliaUi.bar(g, ccx + 7, ccy + 17, CARD_W - 13, 4, progress / all.length, 0xFFCE54, true);

        // Footer: key hints with the keys in cyan, then the selected ability (trimmed to fit).
        VoxeliaUi.footer(g, x, y + h - FOOTER_H, PANEL_W, FOOTER_H);
        int fy = y + h - FOOTER_H + 3;
        int fx = x + PAD;
        fx = seg(g, fx, fy, "Use ", VoxeliaUi.MUTED);
        fx = seg(g, fx, fy, "[" + VoxeliaUi.trim(this.font, keyName(VoxeliaKeys.USE_ABILITY), 40) + "]", VoxeliaUi.LINK);
        fx = seg(g, fx, fy, "  Cycle ", VoxeliaUi.MUTED);
        fx = seg(g, fx, fy, "[" + VoxeliaUi.trim(this.font, keyName(VoxeliaKeys.CYCLE_ABILITY), 40) + "]", VoxeliaUi.LINK);
        fx = seg(g, fx, fy, "  ▶ ", VoxeliaUi.MUTED);
        seg(g, fx, fy, VoxeliaUi.trim(this.font, selected.abilityName(), x + PANEL_W - PAD - fx), VoxeliaUi.LINK);

        menu.renderDropdown(g, this.font, ScreenMenu.Page.SKILLS, mouseX, mouseY);
        g.pose().popPose();
        super.render(g, mouseX, mouseY, partialTick);

        if (menu.isOpen()) return; // the dropdown owns the pointer while it's down
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
            tip.add(Component.literal("Click for your full profile").withStyle(ChatFormatting.GREEN));
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
            tip.add(Component.literal(pts + " talent point" + (pts == 1 ? "" : "s")
                + " unspent — Menu ▸ Talent Tree").withStyle(ChatFormatting.GREEN));
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
            if (menu.mouseClicked(mouseX, mouseY, ScreenMenu.Page.SKILLS)) return true;
            if (in(charCard, mouseX, mouseY)) {
                Minecraft.getInstance().setScreen(new ProfileScreen());
                return true;
            }
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
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && menu.close()) return true; // ESC closes the dropdown first
        if (VoxeliaKeys.OPEN_MENU.matches(keyCode, scanCode)) { // same key toggles closed
            this.onClose();
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
