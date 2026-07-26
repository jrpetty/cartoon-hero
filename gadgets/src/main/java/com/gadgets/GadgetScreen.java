package com.gadgets;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

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

    protected GadgetScreen(Text title, int panelW, int panelH) {
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
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(left, top, left + panelW, top + panelH, BG);
        ctx.drawBorder(left, top, panelW, panelH, FRAME);
        ctx.fill(left + 1, top + 1, left + panelW - 1, top + 14, HEAD_BG);
        ctx.drawCenteredTextWithShadow(textRenderer, title, left + panelW / 2, top + 3, AMBER);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    protected static void send(BlockPos pos, String key, int value) {
        ClientPlayNetworking.send(new GadgetConfigPayload(pos, key, value));
    }
}
