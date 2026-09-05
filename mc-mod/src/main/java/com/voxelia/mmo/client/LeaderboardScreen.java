package com.voxelia.mmo.client;

import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Leaderboards (Menu ▸ Leaderboards): pick a skill on the left, see the server's
 * top ten on the right. The server ranks everyone who has ever played, not just
 * whoever happens to be online, and your own standing sits in the footer.
 */
public final class LeaderboardScreen extends Screen {
    private static final int PAD = 6;
    private static final int TITLE_H = 17;
    private static final int FOOTER_H = 14;
    private static final int LIST_W = 104;
    private static final int ROW_H = 15;
    private static final int GAP = 6;
    private static final int RIGHT_W = 180;
    private static final int PANEL_W = PAD + LIST_W + GAP + RIGHT_W + PAD;
    private static final int ENTRY_H = 13;

    /** Remembered between openings so you come back to the board you were reading. */
    private static Skill selected;

    private record SkillRow(int x1, int y1, int x2, int y2, Skill skill) {}

    private final ScreenMenu menu = new ScreenMenu();
    private final List<SkillRow> skillRows = new ArrayList<>();
    private final float[] rowHoverA = new float[Skill.values().length + 1];
    private long lastFrameMs = net.minecraft.Util.getMillis();

    public LeaderboardScreen() {
        super(Component.literal("Voxelia Leaderboards"));
        ClientLeaderboard.request(selected);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = net.minecraft.Util.getMillis();
        float hdt = Math.min(50, now - lastFrameMs) / 100f;
        lastFrameMs = now;
        float e = VoxeliaUi.introT();
        g.pose().pushPose();
        g.pose().translate(0, (1f - e) * 6f, 0);

        skillRows.clear();
        Skill[] all = Skill.values();
        int listH = (all.length + 1) * ROW_H; // skills + the Character row
        int h = TITLE_H + 4 + listH + 4 + FOOTER_H;
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - h) / 2;

        VoxeliaUi.panel(g, x, y, PANEL_W, h);
        VoxeliaUi.titleBar(g, this.font, x, y, PANEL_W, "VOXELIA");
        int totalPts = 0;
        for (Skill s : all) totalPts += ClientTalents.available(s);
        menu.renderButton(g, this.font, x, y, PANEL_W, mouseX, mouseY, totalPts > 0);

        int contentTop = y + TITLE_H + 4;

        // ── Left: what to rank by ───────────────────────────────────────────
        int lx = x + PAD;
        for (int i = 0; i <= all.length; i++) {
            Skill s = i < all.length ? all[i] : null; // last row is the character ranking
            int ry = contentTop + i * ROW_H;
            boolean sel = s == selected;
            boolean over = !menu.isOpen()
                && mouseX >= lx && mouseX < lx + LIST_W && mouseY >= ry && mouseY < ry + ROW_H;
            int color = s != null ? 0xFF000000 | s.color() : VoxeliaUi.GOLD;
            rowHoverA[i] = Math.max(0f, Math.min(1f, rowHoverA[i] + (over ? hdt : -hdt)));

            if (sel) g.fill(lx, ry, lx + LIST_W, ry + ROW_H - 1, 0x2889C7FF);
            else if (rowHoverA[i] > 0.02f) {
                g.fill(lx, ry, lx + LIST_W, ry + ROW_H - 1, (((int) (0x14 * rowHoverA[i])) << 24) | 0xFFFFFF);
            }
            g.fill(lx, ry, lx + 3, ry + ROW_H - 1, color);
            g.drawString(this.font, s != null ? s.display() : "Character",
                lx + 7, ry + 3, sel ? 0xFFFFFFFF : color);
            skillRows.add(new SkillRow(lx, ry, lx + LIST_W, ry + ROW_H, s));
        }

        int sepX = x + PAD + LIST_W + GAP / 2;
        g.fill(sepX, contentTop, sepX + 1, contentTop + listH, 0x30FFFFFF);

        // ── Right: the standings ────────────────────────────────────────────
        int rx = x + PAD + LIST_W + GAP;
        Skill shown = ClientLeaderboard.skill();
        String heading = (shown != null ? shown.display() : "Character").toUpperCase(Locale.ROOT);
        g.drawString(this.font, heading, rx, contentTop,
            shown != null ? 0xFF000000 | shown.color() : VoxeliaUi.GOLD);
        String tracked = ClientLeaderboard.tracked() + " tracked";
        g.drawString(this.font, tracked, rx + RIGHT_W - this.font.width(tracked), contentTop, VoxeliaUi.MUTED);

        List<ClientLeaderboard.Row> rows = ClientLeaderboard.rows();
        int ry = contentTop + 14;
        if (rows.isEmpty()) {
            g.drawString(this.font, ClientLeaderboard.waiting() ? "Loading…" : "Nobody ranked yet.",
                rx, ry + 4, VoxeliaUi.DISABLED);
        }
        for (int i = 0; i < rows.size(); i++) {
            ClientLeaderboard.Row row = rows.get(i);
            int cy = ry + i * ENTRY_H;
            if (i % 2 == 1) g.fill(rx, cy - 2, rx + RIGHT_W, cy + 10, 0x0DFFFFFF);
            if (row.self()) { // your own line, marked and lit
                g.fill(rx, cy - 2, rx + RIGHT_W, cy + 10, 0x2089C7FF);
                g.fill(rx, cy - 2, rx + 1, cy + 10, VoxeliaUi.LINK);
            }

            String rank = "#" + row.rank();
            int rankColor = switch (row.rank()) {
                case 1 -> VoxeliaUi.GOLD;
                case 2 -> 0xFFC8D6E0;
                case 3 -> 0xFFC8A064;
                default -> VoxeliaUi.DISABLED;
            };
            g.drawString(this.font, rank, rx + 2, cy, rankColor);

            String lvl = "Lv " + row.level();
            int nameX = rx + 22;
            int nameW = RIGHT_W - 22 - this.font.width(lvl) - 8;
            g.drawString(this.font, VoxeliaUi.trim(this.font, row.name(), nameW), nameX, cy,
                row.self() ? 0xFFFFFFFF : VoxeliaUi.TEXT);
            g.drawString(this.font, lvl, rx + RIGHT_W - this.font.width(lvl), cy,
                row.level() >= SkillCurve.MAX_LEVEL ? VoxeliaUi.GOOD : 0xFFFFFFFF);
        }

        // ── Footer: where you sit ───────────────────────────────────────────
        int footY = y + h - FOOTER_H;
        VoxeliaUi.footer(g, x, footY, PANEL_W, FOOTER_H);
        int yourRank = ClientLeaderboard.yourRank();
        String you = yourRank > 0
            ? "You: #" + yourRank + "  ·  Lv " + ClientLeaderboard.yourLevel()
            : "You aren't ranked here yet — go earn some XP.";
        g.drawCenteredString(this.font, you, x + PANEL_W / 2, footY + 3,
            yourRank == 1 ? VoxeliaUi.GOLD : VoxeliaUi.MUTED);

        menu.renderDropdown(g, this.font, ScreenMenu.Page.LEADERBOARD, mouseX, mouseY);
        g.pose().popPose();
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (menu.mouseClicked(mouseX, mouseY, ScreenMenu.Page.LEADERBOARD)) return true;
            for (SkillRow r : skillRows) {
                if (mouseX >= r.x1 && mouseX < r.x2 && mouseY >= r.y1 && mouseY < r.y2) {
                    if (r.skill != selected || ClientLeaderboard.rows().isEmpty()) {
                        selected = r.skill;
                        ClientLeaderboard.request(selected);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && menu.close()) return true;
        if (VoxeliaKeys.OPEN_MENU.matches(keyCode, scanCode)) {
            Minecraft.getInstance().setScreen(new SkillsScreen());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void renderBlurredBackground(float partialTick) {}
}
