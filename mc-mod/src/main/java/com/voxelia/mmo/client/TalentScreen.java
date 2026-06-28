package com.voxelia.mmo.client;

import com.voxelia.mmo.network.SpendTalentPacket;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.TalentType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Clickable talent tree: a row per skill with its three talents; click to spend a point. */
public final class TalentScreen extends Screen {
    private static final int ROW_H = 20;
    private static final int CELL_W = 62;
    private static final int PANEL_W = 150 + TalentType.values().length * (CELL_W + 4) + 12;

    private record Cell(int x1, int y1, int x2, int y2, Skill skill, TalentType type) {}
    private final List<Cell> cells = new ArrayList<>();

    public TalentScreen() {
        super(Component.literal("Talents"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xB0101418);
        cells.clear();

        int rows = Skill.values().length;
        int h = 40 + rows * ROW_H + 16;
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - h) / 2;

        g.fill(x, y, x + PANEL_W, y + h, 0xD0101820);
        g.fill(x, y, x + PANEL_W, y + 22, 0xFF1D2733);
        g.drawCenteredString(this.font, "TALENT TREE", this.width / 2, y + 7, 0xFFFFCE54);

        // column headers
        int colX = x + 150;
        for (TalentType t : TalentType.values()) {
            g.drawString(this.font, t.display(), colX + 6, y + 26, 0xFF89C7FF);
            colX += CELL_W + 4;
        }

        int ry = y + 40;
        for (Skill skill : Skill.values()) {
            int points = ClientTalents.available(skill);
            g.drawString(this.font, skill.display(), x + 8, ry + 5, 0xFF000000 | skill.color());
            g.drawString(this.font, points + "p", x + 116, ry + 5, points > 0 ? 0xFF7CFC00 : 0xFF707070);

            int cx = x + 150;
            for (TalentType type : TalentType.values()) {
                int rank = ClientTalents.rank(skill, type);
                int max = ClientTalents.maxRank();
                boolean canBuy = points > 0 && rank < max;
                int bg = canBuy ? 0xFF2E7D32 : (rank >= max ? 0xFF555555 : 0xFF2A2A2A);
                int cy = ry;
                g.fill(cx, cy, cx + CELL_W, cy + ROW_H - 3, bg);
                g.drawString(this.font, rank + "/" + max, cx + 8, cy + 5, 0xFFFFFFFF);
                if (canBuy) g.drawString(this.font, "+", cx + CELL_W - 12, cy + 5, 0xFFFFFFFF);
                cells.add(new Cell(cx, cy, cx + CELL_W, cy + ROW_H - 3, skill, type));
                cx += CELL_W + 4;
            }
            ry += ROW_H;
        }

        g.drawCenteredString(this.font, "Click a cell to spend • /voxelia talent reset to refund",
            this.width / 2, y + h - 12, 0xFF9FB0BD);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Cell c : cells) {
                if (mouseX >= c.x1 && mouseX <= c.x2 && mouseY >= c.y1 && mouseY <= c.y2) {
                    if (ClientTalents.available(c.skill) > 0
                        && ClientTalents.rank(c.skill, c.type) < ClientTalents.maxRank()) {
                        PacketDistributor.sendToServer(
                            new SpendTalentPacket(c.skill.ordinal(), c.type.ordinal()));
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
