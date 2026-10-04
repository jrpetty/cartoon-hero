package com.jrpetty.aztecabyss.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A button that draws itself as a card.
 *
 * <p>The picker's arena cards and mode tiles used to be either loose rectangles
 * with a hand-rolled hit test - no keyboard, no focus, no narration - or
 * vanilla buttons with a dark-grey caption on a grey stone face, which nobody
 * could read. This is both halves at once: a real widget (Tab reaches it,
 * Enter presses it, the narrator says its name) whose face is whatever the
 * screen paints.
 */
public final class TileButton extends AbstractButton {

    /** Paints the tile's face. {@code hot} is hovered-or-focused. */
    @FunctionalInterface
    public interface Painter {
        void paint(GuiGraphics g, TileButton tile, boolean hot);
    }

    private final Painter painter;
    private final Runnable action;

    public TileButton(int x, int y, int w, int h, Component name, Painter painter, Runnable action) {
        super(x, y, w, h, name);
        this.painter = painter;
        this.action = action;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        painter.paint(g, this, this.active && this.isHoveredOrFocused());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
