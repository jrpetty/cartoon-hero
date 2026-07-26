package com.gadgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shared dark LED-panel chrome for every gadget screen. */
public abstract class GadgetScreen extends Screen {
    protected static final int BG = 0xF0101418;
    protected static final int HEAD_BG = 0xFF1C2028;
    protected static final int FRAME = 0xFF3C424E;
    protected static final int AMBER = 0xFFFFC864;
    protected static final int DIM = 0xFFC08840;
    protected static final int GRAY = 0xFF8E96A4;
    protected static final int RED = 0xFFFF5555;
    protected static final int GREEN = 0xFF7CE87C;

    protected final int panelW;
    protected final int panelH;
    protected int left;
    protected int top;

    protected GadgetScreen(Component title, int panelW, int panelH) {
        super(title);
        this.panelW = panelW;
        this.panelH = panelH;
    }

    @Override
    protected void init() {
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
    }

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        super.renderBackground(gfx, mouseX, mouseY, delta);
        gfx.fill(left, top, left + panelW, top + panelH, BG);
        gfx.renderOutline(left, top, panelW, panelH, FRAME);
        gfx.fill(left + 1, top + 1, left + panelW - 1, top + 14, HEAD_BG);
        gfx.drawCenteredString(font, title, left + panelW / 2, top + 3, AMBER);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected static void send(BlockPos pos, String key, int value) {
        PacketDistributor.sendToServer(new GadgetConfigPayload(pos, key, value));
    }
}
