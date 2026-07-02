package com.voxelia.mmo.client;

import com.voxelia.mmo.network.SpendTalentPacket;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.TalentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Talent tree (N): a row per skill × six talent routes. Ranks render as pips,
 * affordable cells breathe with a green glow, branch headers explain themselves
 * on hover, and tabs (or the K key) hop back to the Skills screen.
 */
public final class TalentScreen extends Screen {
    private static final int LABEL_W = 122;
    private static final int CELL_W = 40;
    private static final int GAP = 2;
    private static final int CELL_H = 14;
    private static final int ROW_H = 16;
    private static final int N = TalentType.values().length;
    private static final int PANEL_W = LABEL_W + N * (CELL_W + GAP) + 14;
    private static final int TITLE_H = 17;
    private static final int HEADER_ROW_H = 15;
    private static final int FOOTER_H = 14;

    private record Cell(int x1, int y1, int x2, int y2, Skill skill, TalentType type) {}
    private final List<Cell> cells = new ArrayList<>();
    private int[] tabSkills = new int[4];
    private int[] tabTalents = new int[4];

    public TalentScreen() {
        super(Component.literal("Talents"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No full-screen backdrop — the live game shows through; only the panel is drawn.
        cells.clear();

        int rows = Skill.values().length;
        int h = TITLE_H + HEADER_ROW_H + 2 + rows * ROW_H + FOOTER_H;
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - h) / 2;

        VoxeliaUi.panel(g, x, y, PANEL_W, h);
        VoxeliaUi.titleBar(g, this.font, x, y, PANEL_W, "VOXELIA");
        tabTalents = VoxeliaUi.tab(g, this.font, "Talents [" + keyName(VoxeliaKeys.OPEN_TALENTS) + "]",
            x + PANEL_W - 4, y, true, mouseX, mouseY);
        tabSkills = VoxeliaUi.tab(g, this.font, "Skills [" + keyName(VoxeliaKeys.OPEN_MENU) + "]",
            tabTalents[0] - 2, y, false, mouseX, mouseY);

        // Points summary (left) + branch column headers (with hover tooltips).
        int headerY = y + TITLE_H + 4;
        int totalPts = 0;
        for (Skill s : Skill.values()) totalPts += ClientTalents.available(s);
        g.drawString(this.font, totalPts > 0 ? totalPts + " pts unspent" : "No points",
            x + 8, headerY, totalPts > 0 ? VoxeliaUi.GOOD : VoxeliaUi.DISABLED);

        int hoveredHeader = -1;
        for (int i = 0; i < N; i++) {
            int cx = x + LABEL_W + i * (CELL_W + GAP);
            boolean over = mouseX >= cx && mouseX < cx + CELL_W && mouseY >= headerY - 2 && mouseY < headerY + 11;
            if (over) hoveredHeader = i;
            g.drawCenteredString(this.font, TalentType.values()[i].code(), cx + CELL_W / 2, headerY,
                over ? 0xFFFFFFFF : VoxeliaUi.LINK);
            g.fill(cx + 5, headerY + 10, cx + CELL_W - 5, headerY + 11, over ? 0x9089C7FF : 0x4089C7FF);
        }

        Cell hovered = null;
        int max = ClientTalents.maxRank();
        int ry = y + TITLE_H + HEADER_ROW_H + 3;
        boolean shade = false;
        for (Skill skill : Skill.values()) {
            if (shade) g.fill(x + 1, ry - 1, x + PANEL_W - 1, ry + CELL_H + 1, 0x14FFFFFF);
            shade = !shade;
            g.fill(x + 1, ry - 1, x + 4, ry + CELL_H + 1, 0xFF000000 | skill.color()); // skill accent
            g.drawString(this.font, skill.display(), x + 8, ry + 3, 0xFF000000 | skill.color());

            int points = ClientTalents.available(skill);
            g.drawString(this.font, points + "p", x + LABEL_W - 22, ry + 3,
                points > 0 ? VoxeliaUi.GOOD : VoxeliaUi.DISABLED);

            for (int i = 0; i < N; i++) {
                TalentType type = TalentType.values()[i];
                int rank = ClientTalents.rank(skill, type);
                boolean canBuy = points > 0 && rank < max;
                int cx = x + LABEL_W + i * (CELL_W + GAP);
                boolean over = mouseX >= cx && mouseX <= cx + CELL_W && mouseY >= ry && mouseY <= ry + CELL_H;

                int bg = canBuy ? 0xFF1F3826 : (rank >= max ? 0xFF4A3D1E : 0xFF232D38);
                g.fill(cx, ry, cx + CELL_W, ry + CELL_H, over ? VoxeliaUi.brighten(bg, 32) : bg);
                g.fill(cx, ry, cx + CELL_W, ry + 1, 0x24FFFFFF);            // top bevel
                g.fill(cx, ry + CELL_H - 1, cx + CELL_W, ry + CELL_H, 0x40000000); // bottom shade
                if (canBuy && !over) { // breathing green outline invites the click
                    int a = 0x50 + (int) (0x60 * VoxeliaUi.pulse());
                    int glow = (a << 24) | 0x6EE86E;
                    g.fill(cx, ry, cx + CELL_W, ry + 1, glow);
                    g.fill(cx, ry + CELL_H - 1, cx + CELL_W, ry + CELL_H, glow);
                    g.fill(cx, ry, cx + 1, ry + CELL_H, glow);
                    g.fill(cx + CELL_W - 1, ry, cx + CELL_W, ry + CELL_H, glow);
                }
                if (over) {
                    g.fill(cx, ry, cx + CELL_W, ry + 1, 0xFFFFFFFF);
                    g.fill(cx, ry + CELL_H - 1, cx + CELL_W, ry + CELL_H, 0xFFFFFFFF);
                }

                drawRank(g, cx, ry, rank, max, skill);

                Cell cell = new Cell(cx, ry, cx + CELL_W, ry + CELL_H, skill, type);
                cells.add(cell);
                if (over) hovered = cell;
            }
            ry += ROW_H;
        }

        VoxeliaUi.footer(g, x, y + h - FOOTER_H, PANEL_W, FOOTER_H);
        g.drawCenteredString(this.font, "Click a cell to spend  •  /voxelia talent reset to refund",
            x + PANEL_W / 2, y + h - FOOTER_H + 3, VoxeliaUi.MUTED);

        super.render(g, mouseX, mouseY, partialTick);

        if (hovered != null) {
            renderCellTooltip(g, hovered, max, mouseX, mouseY);
        } else if (hoveredHeader >= 0) {
            TalentType t = TalentType.values()[hoveredHeader];
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(t.display()).withStyle(ChatFormatting.GOLD));
            tip.add(Component.literal(t.desc()).withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
        }
    }

    /** Rank pips (or a plain fraction when maxRank is too large for pips). */
    private void drawRank(GuiGraphics g, int cx, int ry, int rank, int max, Skill skill) {
        if (max >= 1 && max <= 8) {
            int pw = Math.max(2, (CELL_W - 8 - (max - 1)) / max);
            int total = max * pw + (max - 1);
            int px = cx + (CELL_W - total) / 2;
            int py = ry + (CELL_H - 5) / 2;
            for (int p = 0; p < max; p++) {
                if (p < rank) {
                    g.fill(px, py, px + pw, py + 5,
                        rank >= max ? VoxeliaUi.GOLD : 0xFF000000 | skill.color());
                    g.fill(px, py, px + pw, py + 1, 0x50FFFFFF);
                } else {
                    g.fill(px, py, px + pw, py + 5, 0xFF10161D);
                }
                px += pw + 1;
            }
        } else {
            g.drawCenteredString(this.font, rank + "/" + max, cx + CELL_W / 2, ry + 3, 0xFFFFFFFF);
        }
    }

    private void renderCellTooltip(GuiGraphics g, Cell c, int max, int mouseX, int mouseY) {
        int rank = ClientTalents.rank(c.skill, c.type);
        int points = ClientTalents.available(c.skill);
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(c.skill.display() + " · " + c.type.display())
            .withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal(c.type.desc()).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Rank " + rank + " / " + max).withStyle(ChatFormatting.WHITE));
        tip.add(rank >= max
            ? Component.literal("Maxed").withStyle(ChatFormatting.YELLOW)
            : (points > 0
                ? Component.literal("Click to spend (" + points + " available)").withStyle(ChatFormatting.GREEN)
                : Component.literal("No points — level " + c.skill.display()).withStyle(ChatFormatting.RED)));
        g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
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
            for (Cell c : cells) {
                if (mouseX >= c.x1 && mouseX <= c.x2 && mouseY >= c.y1 && mouseY <= c.y2) {
                    if (ClientTalents.available(c.skill) > 0
                        && ClientTalents.rank(c.skill, c.type) < ClientTalents.maxRank()) {
                        PacketDistributor.sendToServer(new SpendTalentPacket(c.skill.ordinal(), c.type.ordinal()));
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
