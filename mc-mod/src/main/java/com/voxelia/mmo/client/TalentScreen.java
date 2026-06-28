package com.voxelia.mmo.client;

import com.voxelia.mmo.network.SpendTalentPacket;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.TalentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Polished, clickable talent tree: a row per skill × six talent routes. */
public final class TalentScreen extends Screen {
    private static final int LABEL_W = 122;
    private static final int CELL_W = 40;
    private static final int GAP = 2;
    private static final int CELL_H = 14;
    private static final int ROW_H = 16;
    private static final int N = TalentType.values().length;
    private static final int PANEL_W = LABEL_W + N * (CELL_W + GAP) + 14;

    private record Cell(int x1, int y1, int x2, int y2, Skill skill, TalentType type) {}
    private final List<Cell> cells = new ArrayList<>();

    public TalentScreen() {
        super(Component.literal("Talents"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No full-screen backdrop — the live game shows through; only the panel is drawn.
        cells.clear();

        int rows = Skill.values().length;
        int h = 42 + rows * ROW_H + 16;
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - h) / 2;

        // panel + border
        g.fill(x - 1, y - 1, x + PANEL_W + 1, y + h + 1, 0xFF3A4E63);
        g.fill(x, y, x + PANEL_W, y + h, 0xE0121A24);
        g.fill(x, y, x + PANEL_W, y + 20, 0xFF1D2B3A);
        g.drawCenteredString(this.font, "✦ TALENT TREE ✦", x + PANEL_W / 2, y + 6, 0xFFFFCE54);

        // column headers
        int headerY = y + 26;
        for (int i = 0; i < N; i++) {
            int cx = x + LABEL_W + i * (CELL_W + GAP);
            g.drawCenteredString(this.font, TalentType.values()[i].code(), cx + CELL_W / 2, headerY, 0xFF89C7FF);
        }

        Cell hovered = null;
        int ry = y + 40;
        boolean shade = false;
        for (Skill skill : Skill.values()) {
            if (shade) g.fill(x + 1, ry - 1, x + PANEL_W - 1, ry + CELL_H + 1, 0x18FFFFFF);
            shade = !shade;
            g.fill(x + 1, ry - 1, x + 4, ry + CELL_H + 1, 0xFF000000 | skill.color()); // skill accent
            g.drawString(this.font, skill.display(), x + 8, ry + 3, 0xFF000000 | skill.color());

            int points = ClientTalents.available(skill);
            g.drawString(this.font, points + "p", x + LABEL_W - 22, ry + 3, points > 0 ? 0xFF7CFC00 : 0xFF606A74);

            for (int i = 0; i < N; i++) {
                TalentType type = TalentType.values()[i];
                int rank = ClientTalents.rank(skill, type);
                int max = ClientTalents.maxRank();
                boolean canBuy = points > 0 && rank < max;
                int cx = x + LABEL_W + i * (CELL_W + GAP);
                boolean over = mouseX >= cx && mouseX <= cx + CELL_W && mouseY >= ry && mouseY <= ry + CELL_H;

                int bg = canBuy ? 0xFF2E7D32 : (rank >= max ? 0xFF6B5B1E : 0xFF26303A);
                g.fill(cx, ry, cx + CELL_W, ry + CELL_H, over ? brighten(bg) : bg);
                if (over) {
                    g.fill(cx, ry, cx + CELL_W, ry + 1, 0xFFFFFFFF);
                    g.fill(cx, ry + CELL_H - 1, cx + CELL_W, ry + CELL_H, 0xFFFFFFFF);
                }
                g.drawCenteredString(this.font, rank + "/" + max, cx + CELL_W / 2, ry + 3, 0xFFFFFFFF);

                Cell cell = new Cell(cx, ry, cx + CELL_W, ry + CELL_H, skill, type);
                cells.add(cell);
                if (over) hovered = cell;
            }
            ry += ROW_H;
        }

        g.drawCenteredString(this.font, "Click a cell to spend  •  /voxelia talent reset to refund",
            x + PANEL_W / 2, y + h - 12, 0xFF8FA0AD);

        super.render(g, mouseX, mouseY, partialTick);

        if (hovered != null) {
            int rank = ClientTalents.rank(hovered.skill, hovered.type);
            int max = ClientTalents.maxRank();
            int points = ClientTalents.available(hovered.skill);
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(hovered.skill.display() + " · " + hovered.type.display())
                .withStyle(ChatFormatting.GOLD));
            tip.add(Component.literal(hovered.type.desc()).withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("Rank " + rank + " / " + max).withStyle(ChatFormatting.WHITE));
            tip.add(rank >= max
                ? Component.literal("Maxed").withStyle(ChatFormatting.YELLOW)
                : (points > 0
                    ? Component.literal("Click to spend (" + points + " available)").withStyle(ChatFormatting.GREEN)
                    : Component.literal("No points — level " + hovered.skill.display()).withStyle(ChatFormatting.RED)));
            g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
        }
    }

    private static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 40);
        int gg = Math.min(255, ((argb >> 8) & 0xFF) + 40);
        int b = Math.min(255, (argb & 0xFF) + 40);
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
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
    public boolean isPauseScreen() { return false; }
}
