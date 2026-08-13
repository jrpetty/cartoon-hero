package com.voxelia.mmo.client;

import com.voxelia.mmo.network.ProfileRequestPacket;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Character sheet (Menu ▸ Character Profile): headline identity plus the stats that make a run feel like
 * a career — best skill, total prestiges, XP earned, playtime, deaths, mob kills.
 * Playtime/deaths/kills arrive from the server (requested on open); everything
 * else is derived from the client caches.
 */
public final class ProfileScreen extends Screen {
    private static final int PANEL_W = 248;
    private static final int PAD = 8;
    private static final int TITLE_H = 17;
    private static final int FOOTER_H = 14;
    private static final int ROW_H = 13;
    private static final int ROWS = 6;

    private final ScreenMenu menu = new ScreenMenu();
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

        Skill[] all = Skill.values();

        // Derived headline stats.
        int totalLvl = 0;
        Skill best = all[0];
        int prestiges = 0;
        long xpEarned = 0;
        float progress = 0f;
        int maxXp = SkillCurve.xpForLevel(SkillCurve.MAX_LEVEL);
        for (Skill s : all) {
            int lvl = ClientSkillData.level(s);
            totalLvl += lvl;
            if (lvl > ClientSkillData.level(best)) best = s;
            int pres = ClientTalents.prestige(s);
            prestiges += pres;
            xpEarned += (long) pres * maxXp + ClientSkillData.xp(s);
            int sp = SkillCurve.xpToNext(ClientSkillData.xp(s));
            progress += sp > 0 ? (float) SkillCurve.xpIntoLevel(ClientSkillData.xp(s)) / sp : 1f;
        }
        int charLevel = Math.max(1, Math.round(totalLvl / (float) all.length));

        int headerH = 30;
        int h = TITLE_H + headerH + ROWS * ROW_H + PAD + FOOTER_H;
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - h) / 2;

        VoxeliaUi.panel(g, x, y, PANEL_W, h);
        VoxeliaUi.titleBar(g, this.font, x, y, PANEL_W, "VOXELIA");
        int totalPts = 0;
        for (Skill s : all) totalPts += ClientTalents.available(s);
        menu.renderButton(g, this.font, x, y, PANEL_W, mouseX, mouseY, totalPts > 0);

        // Header: player name + character line + real character-progress bar.
        int hy = y + TITLE_H + 4;
        String name = Minecraft.getInstance().player != null
            ? Minecraft.getInstance().player.getGameProfile().getName() : "Adventurer";
        g.drawString(this.font, name, x + PAD, hy, VoxeliaUi.GOLD);
        String stars = prestiges > 0 ? "  " + "✦".repeat(Math.min(prestiges, 5)) : "";
        g.drawString(this.font, "Character Lv " + charLevel + " · " + best.noun() + stars,
            x + PAD, hy + 12, VoxeliaUi.MUTED);
        VoxeliaUi.bar(g, x + PAD, hy + 22, PANEL_W - 2 * PAD, 3, progress / all.length, 0xFFCE54, false);

        // Stat rows over quiet zebra stripes.
        int ry = y + TITLE_H + headerH;
        for (int i = 1; i < ROWS; i += 2) {
            g.fill(x + 5, ry + i * ROW_H - 2, x + PANEL_W - 5, ry + i * ROW_H + 11, 0x0DFFFFFF);
        }
        boolean loaded = ClientProfile.hasData();
        String bestVal = best.display() + "  Lv " + ClientSkillData.level(best);
        row(g, x, ry, "Best skill", bestVal, 0xFF000000 | best.color());
        row(g, x, ry + ROW_H, "Total prestiges",
            prestiges + (prestiges > 0 ? "  " + "✦".repeat(Math.min(prestiges, 5)) : ""), VoxeliaUi.GOLD);
        row(g, x, ry + ROW_H * 2, "XP earned", String.format(Locale.ROOT, "%,d", xpEarned), VoxeliaUi.TEXT);
        row(g, x, ry + ROW_H * 3, "Playtime", loaded ? playtime(ClientProfile.playTimeTicks()) : "…", VoxeliaUi.TEXT);
        row(g, x, ry + ROW_H * 4, "Deaths", loaded ? String.valueOf(ClientProfile.deaths()) : "…",
            loaded && ClientProfile.deaths() > 0 ? VoxeliaUi.WARN : VoxeliaUi.TEXT);
        row(g, x, ry + ROW_H * 5, "Mob kills", loaded ? String.valueOf(ClientProfile.mobKills()) : "…", VoxeliaUi.TEXT);

        // Footer hint with a real, clickable Talents link.
        VoxeliaUi.footer(g, x, y + h - FOOTER_H, PANEL_W, FOOTER_H);
        int fy = y + h - FOOTER_H + 3;
        int fx = seg(g, x + PAD, fy, "Prestige a maxed skill on the ", VoxeliaUi.MUTED);
        String link = "Talent Tree";
        int linkX2 = fx + this.font.width(link);
        talentLink = new int[]{fx, fy - 2, linkX2, fy + 11};
        boolean overLink = !menu.isOpen() && in(talentLink, mouseX, mouseY);
        g.drawString(this.font, link, fx, fy, overLink ? VoxeliaUi.brighten(VoxeliaUi.LINK, 30) : VoxeliaUi.LINK);
        g.fill(fx, fy + 9, linkX2, fy + 10, 0x8089C7FF);

        menu.renderDropdown(g, this.font, ScreenMenu.Page.PROFILE, mouseX, mouseY);
        g.pose().popPose();
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void row(GuiGraphics g, int x, int y, String label, String value, int valueColor) {
        g.drawString(this.font, label, x + PAD, y, VoxeliaUi.MUTED);
        g.drawString(this.font, value, x + PANEL_W - PAD - this.font.width(value), y, valueColor);
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
