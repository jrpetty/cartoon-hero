package com.voxelia.mmo.client;

import com.voxelia.mmo.network.ProfileRequestPacket;
import com.voxelia.mmo.progression.Milestones;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import com.voxelia.mmo.skill.Talent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Character sheet (Menu ▸ Character Profile): your face and title, the career stats
 * (best skill, talents, XP, playtime, deaths, kills), a tile for every skill with its
 * level and progress, and "what's next" — the closest unlock and the closest talent
 * point. Playtime/deaths/kills arrive from the server (requested on open); everything
 * else is derived from the client caches.
 */
public final class ProfileScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PAD = 8;
    private static final int TITLE_H = 17;
    private static final int FOOTER_H = 14;
    private static final int HEADER_H = 34;
    private static final int STAT_H = 12;
    private static final int TILE_W = 44;
    private static final int TILE_H = 32;
    private static final int TILE_GAP = 4;
    private static final int COLS = 6;

    private record Tile(int x1, int y1, int x2, int y2, Skill skill) {}

    private final ScreenMenu menu = new ScreenMenu();
    private final List<Tile> tiles = new ArrayList<>();
    private int[] talentLink = new int[4];

    public ProfileScreen() {
        super(Component.literal("Voxelia Profile"));
        ClientProfile.clear();
        PacketDistributor.sendToServer(new ProfileRequestPacket());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float e = VoxeliaUi.introT();
        g.pose().pushPose();
        g.pose().translate(0, (1f - e) * 6f, 0);
        tiles.clear();

        Skill[] all = Skill.values();

        // Derived headline stats.
        int totalLvl = 0;
        Skill best = all[0];
        int talentsSpent = 0, talentsTotal = 0, unlockedAbilities = 0;
        long xpEarned = 0;
        float progress = 0f;
        for (Skill s : all) {
            int lvl = ClientSkillData.level(s);
            totalLvl += lvl;
            if (lvl > ClientSkillData.level(best)) best = s;
            talentsSpent += ClientTalents.spentIn(s);
            talentsTotal += Talent.forSkill(s).size() * ClientTalents.maxRank();
            xpEarned += ClientSkillData.xp(s);
            if (s.active() && ClientAbilities.unlocked(s)) unlockedAbilities++;
            int sp = SkillCurve.xpToNext(ClientSkillData.xp(s));
            progress += sp > 0 ? (float) SkillCurve.xpIntoLevel(ClientSkillData.xp(s)) / sp : 1f;
        }
        int charLevel = Math.max(1, Math.round(totalLvl / (float) all.length));

        int gridRows = (all.length + COLS - 1) / COLS;
        int gridH = gridRows * TILE_H + (gridRows - 1) * TILE_GAP;
        int h = TITLE_H + 4 + HEADER_H + 4 + 3 * STAT_H + 6 + 10 + gridH + 6 + 24 + 4 + FOOTER_H;
        int x = (this.width - PANEL_W) / 2 - menu.panelShift(this.font, PANEL_W, this.width);
        int y = (this.height - h) / 2;

        VoxeliaUi.panel(g, x, y, PANEL_W, h);
        VoxeliaUi.titleBar(g, this.font, x, y, PANEL_W, "VOXELIA");
        int totalPts = 0;
        for (Skill s : all) totalPts += ClientTalents.available(s);
        menu.renderButton(g, this.font, x, y, PANEL_W, mouseX, mouseY, totalPts > 0);

        // ── Header: face, name, title, character progress ───────────────────
        Minecraft mc = Minecraft.getInstance();
        int hy = y + TITLE_H + 4;
        int faceX = x + PAD, faceY = hy;
        g.fill(faceX - 1, faceY - 1, faceX + 25, faceY + 25, 0xFF52667B);
        g.fill(faceX, faceY, faceX + 24, faceY + 24, 0xFF0B1118);
        if (mc.player != null) PlayerFaceRenderer.draw(g, mc.player.getSkin(), faceX, faceY, 24);
        String name = mc.player != null ? mc.player.getGameProfile().getName() : "Adventurer";
        int tx = faceX + 31;
        g.drawString(this.font, name, tx, hy + 2, VoxeliaUi.GOLD);
        g.drawString(this.font, "Character Lv " + charLevel + " · " + best.noun(), tx, hy + 14, VoxeliaUi.MUTED);
        String right1 = String.format(Locale.ROOT, "%,d", totalLvl) + " total levels";
        String right2 = unlockedAbilities + " / " + all.length + " abilities";
        g.drawString(this.font, right1, x + PANEL_W - PAD - this.font.width(right1), hy + 2, VoxeliaUi.TEXT);
        g.drawString(this.font, right2, x + PANEL_W - PAD - this.font.width(right2), hy + 14,
            unlockedAbilities == all.length ? VoxeliaUi.GOLD : VoxeliaUi.MUTED);
        VoxeliaUi.bar(g, tx, hy + 26, x + PANEL_W - PAD - tx, 3, progress / all.length, 0xFFCE54, false);

        // ── Career stats: two columns over quiet zebra stripes ──────────────
        int sy = hy + HEADER_H + 4;
        int colW = (PANEL_W - 2 * PAD - 10) / 2;
        int c1 = x + PAD, c2 = x + PAD + colW + 10;
        g.fill(x + 5, sy + STAT_H - 2, x + PANEL_W - 5, sy + 2 * STAT_H - 2, 0x0DFFFFFF);
        boolean loaded = ClientProfile.hasData();
        stat(g, c1, sy, colW, "Best skill", best.display() + " " + ClientSkillData.level(best), 0xFF000000 | best.color());
        stat(g, c2, sy, colW, "XP earned", String.format(Locale.ROOT, "%,d", xpEarned), VoxeliaUi.TEXT);
        stat(g, c1, sy + STAT_H, colW, "Talents spent", talentsSpent + " / " + talentsTotal,
            talentsSpent >= talentsTotal ? VoxeliaUi.GOLD : VoxeliaUi.TEXT);
        stat(g, c2, sy + STAT_H, colW, "Playtime", loaded ? playtime(ClientProfile.playTimeTicks()) : "…", VoxeliaUi.TEXT);
        stat(g, c1, sy + 2 * STAT_H, colW, "Mob kills",
            loaded ? String.format(Locale.ROOT, "%,d", ClientProfile.mobKills()) : "…", VoxeliaUi.TEXT);
        stat(g, c2, sy + 2 * STAT_H, colW, "Deaths", loaded ? String.valueOf(ClientProfile.deaths()) : "…",
            loaded && ClientProfile.deaths() > 0 ? VoxeliaUi.WARN : VoxeliaUi.TEXT);

        // ── Skill tiles ─────────────────────────────────────────────────────
        int ly = sy + 3 * STAT_H + 6;
        g.drawString(this.font, "SKILLS", x + PAD, ly, VoxeliaUi.MUTED);
        g.fill(x + PAD + this.font.width("SKILLS") + 5, ly + 4, x + PANEL_W - PAD, ly + 5, 0x30FFFFFF);
        int gy = ly + 10;
        Tile hovered = null;
        for (int i = 0; i < all.length; i++) {
            Skill s = all[i];
            int row = i / COLS, col = i % COLS;
            int inRow = Math.min(COLS, all.length - row * COLS);
            int rowW = inRow * TILE_W + (inRow - 1) * TILE_GAP;
            int tx1 = x + (PANEL_W - rowW) / 2 + col * (TILE_W + TILE_GAP); // short last row is centred
            int ty1 = gy + row * (TILE_H + TILE_GAP);
            boolean over = !menu.isOpen() && mouseX >= tx1 && mouseX < tx1 + TILE_W
                && mouseY >= ty1 && mouseY < ty1 + TILE_H;
            tile(g, s, tx1, ty1, over);
            Tile t = new Tile(tx1, ty1, tx1 + TILE_W, ty1 + TILE_H, s);
            tiles.add(t);
            if (over) hovered = t;
        }

        // ── What's next: the closest unlock and the closest talent point ────
        int ny = gy + gridH + 6;
        g.fill(x + 5, ny - 2, x + PANEL_W - 5, ny + 22, 0x14FFFFFF);
        g.fill(x + 5, ny - 2, x + 6, ny + 22, VoxeliaUi.GOLD);
        nextUnlockLine(g, x + PAD, ny + 1);
        nextPointLine(g, x + PAD, ny + 12);

        // Footer hint with a real, clickable Talents link.
        VoxeliaUi.footer(g, x, y + h - FOOTER_H, PANEL_W, FOOTER_H);
        int fy = y + h - FOOTER_H + 3;
        int fx = seg(g, x + PAD, fy, totalPts > 0 ? totalPts + " talent point" + (totalPts == 1 ? "" : "s")
            + " waiting — open the " : "Plan your build on the ", totalPts > 0 ? VoxeliaUi.GOOD : VoxeliaUi.MUTED);
        String link = "Talent Tree";
        int linkX2 = fx + this.font.width(link);
        talentLink = new int[]{fx, fy - 2, linkX2, fy + 11};
        boolean overLink = !menu.isOpen() && in(talentLink, mouseX, mouseY);
        g.drawString(this.font, link, fx, fy, overLink ? VoxeliaUi.brighten(VoxeliaUi.LINK, 30) : VoxeliaUi.LINK);
        g.fill(fx, fy + 9, linkX2, fy + 10, 0x8089C7FF);

        menu.renderDropdown(g, this.font, ScreenMenu.Page.PROFILE, mouseX, mouseY);
        g.pose().popPose();
        super.render(g, mouseX, mouseY, partialTick);
        g.flush(); // finish the panel before any tooltip, or its text bleeds through

        if (!menu.isOpen() && hovered != null) tileTooltip(g, hovered.skill, mouseX, mouseY);
    }

    private void stat(GuiGraphics g, int x, int y, int w, String label, String value, int valueColor) {
        g.drawString(this.font, label, x, y, VoxeliaUi.MUTED);
        g.drawString(this.font, value, x + w - this.font.width(value), y, valueColor);
    }

    /** One skill tile: icon (locked ability → padlock), level, and a thin progress bar. */
    private void tile(GuiGraphics g, Skill s, int x, int y, boolean over) {
        int accent = 0xFF000000 | s.color();
        g.fillGradient(x, y, x + TILE_W, y + TILE_H, over ? 0xD0263850 : 0xB81B2735, over ? 0xD01A2536 : 0xB8111922);
        g.fill(x, y, x + TILE_W, y + 1, (accent & 0xFFFFFF) | 0xA0000000);
        int xp = ClientSkillData.xp(s);
        int lvl = SkillCurve.levelForXp(xp);
        int span = SkillCurve.xpToNext(xp);
        int ix = x + (TILE_W - 16) / 2;
        VoxeliaUi.icon(g, SkillIcons.of(s), ix, y + 3, 16);
        if (!ClientAbilities.unlocked(s)) VoxeliaUi.lockedBadge(g, ix, y + 3, 16);
        String lv = span > 0 ? String.valueOf(lvl) : "MAX";
        g.drawString(this.font, lv, x + (TILE_W - this.font.width(lv)) / 2, y + 20,
            span > 0 ? 0xFFFFFFFF : VoxeliaUi.GOOD);
        float frac = span > 0 ? (float) SkillCurve.xpIntoLevel(xp) / span : 1f;
        g.fill(x + 2, y + TILE_H - 3, x + TILE_W - 2, y + TILE_H - 1, VoxeliaUi.TRACK);
        g.fill(x + 2, y + TILE_H - 3, x + 2 + (int) ((TILE_W - 4) * frac), y + TILE_H - 1, accent);
    }

    private void tileTooltip(GuiGraphics g, Skill s, int mouseX, int mouseY) {
        int xp = ClientSkillData.xp(s);
        int lvl = SkillCurve.levelForXp(xp);
        int span = SkillCurve.xpToNext(xp);
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(s.display() + " — Lv " + lvl).withStyle(ChatFormatting.GOLD));
        if (span > 0) {
            tip.add(Component.literal(String.format(Locale.ROOT, "%,d / %,d xp to %d",
                SkillCurve.xpIntoLevel(xp), span, lvl + 1)).withStyle(ChatFormatting.GRAY));
        }
        int at = ClientAbilities.unlockLevel(s);
        if (at > 0) {
            tip.add(ClientAbilities.unlocked(s)
                ? Component.literal(s.abilityName() + " unlocked").withStyle(ChatFormatting.AQUA)
                : Component.literal(s.abilityName() + " at Lv " + at + " (" + (at - lvl) + " to go)")
                    .withStyle(ChatFormatting.RED));
        }
        tip.add(Component.literal("Talents: " + ClientTalents.spentIn(s) + " spent, "
            + ClientTalents.available(s) + " to spend").withStyle(ChatFormatting.DARK_GRAY));
        g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
    }

    /** "Next unlock  Feast · Cooking 75 · 42 to go" — the nearest locked ability or passive. */
    private void nextUnlockLine(GuiGraphics g, int x, int y) {
        String what = null;
        Skill skill = null;
        int at = 0, gap = Integer.MAX_VALUE;
        for (Skill s : Skill.values()) {
            int lvl = ClientAbilities.unlockLevel(s);
            int have = ClientSkillData.level(s);
            if (s.active() && lvl > have && lvl - have < gap) {
                gap = lvl - have; at = lvl; skill = s; what = s.abilityName();
            }
        }
        for (Milestones.Kind k : Milestones.Kind.values()) {
            if (k == Milestones.Kind.ABILITY) continue;
            int lvl = ClientPerks.passiveLevel(k);
            Skill s = Milestones.passiveSkill(k);
            int have = ClientSkillData.level(s);
            if (lvl > have && lvl - have < gap) {
                gap = lvl - have; at = lvl; skill = s; what = passiveName(k);
            }
        }
        int cx = seg(g, x, y, "Next unlock  ", VoxeliaUi.MUTED);
        if (skill == null) {
            seg(g, cx, y, "Everything unlocked", VoxeliaUi.GOLD);
            return;
        }
        cx = seg(g, cx, y, what, 0xFF000000 | skill.color());
        seg(g, cx, y, "  ·  " + skill.display() + " " + at + "  ·  " + gap + " to go", VoxeliaUi.TEXT);
    }

    /** "Next point  Fishing Lv 80 · 4 to go" — the skill closest to its next talent point. */
    private void nextPointLine(GuiGraphics g, int x, int y) {
        int per = Math.max(1, ClientTalents.levelsPerPoint());
        Skill best = null;
        int bestAt = 0, gap = Integer.MAX_VALUE;
        for (Skill s : Skill.values()) {
            int lvl = ClientSkillData.level(s);
            if (lvl >= SkillCurve.MAX_LEVEL) continue;
            int next = (lvl / per + 1) * per;
            if (next - lvl < gap) { gap = next - lvl; bestAt = next; best = s; }
        }
        int cx = seg(g, x, y, "Next point   ", VoxeliaUi.MUTED);
        if (best == null) {
            seg(g, cx, y, "Every talent point earned", VoxeliaUi.GOLD);
            return;
        }
        cx = seg(g, cx, y, best.display() + " Lv " + bestAt, 0xFF000000 | best.color());
        seg(g, cx, y, "  ·  " + gap + " to go", VoxeliaUi.TEXT);
    }

    private static String passiveName(Milestones.Kind k) {
        return switch (k) {
            case HASTE -> "Haste";
            case TELEKINESIS -> "Telekinesis";
            case LAST_STAND -> "Last Stand";
            case WELL_FED -> "Well Fed";
            case ABILITY -> "Ability";
        };
    }

    private static String playtime(int ticks) {
        long secs = ticks / 20L;
        long hrs = secs / 3600L;
        long mins = (secs % 3600L) / 60L;
        if (hrs > 0) return hrs + "h " + mins + "m";
        if (mins > 0) return mins + "m " + (secs % 60L) + "s";
        return secs + "s";
    }

    private int seg(GuiGraphics g, int x, int y, String text, int color) {
        g.drawString(this.font, text, x, y, color);
        return x + this.font.width(text);
    }

    private static boolean in(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3];
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (menu.mouseClicked(mouseX, mouseY, ScreenMenu.Page.PROFILE)) return true;
            if (in(talentLink, mouseX, mouseY)) {
                Minecraft.getInstance().setScreen(new TalentScreen());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && menu.close()) return true; // ESC closes the dropdown first
        if (VoxeliaKeys.OPEN_MENU.matches(keyCode, scanCode)) { // back to the hub screen
            Minecraft.getInstance().setScreen(new SkillsScreen());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {}
}
