package com.jrpetty.aztecabyss.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The base every screen in the mod draws on, and the order it draws in.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Since 1.20.2, vanilla's {@code Screen.render} paints the background
 * itself before it draws the widgets. Every screen here was written the older
 * way round: paint the background, draw the screen's own panels and text, then
 * call {@code super.render} for the buttons - which painted the opaque
 * backdrop a second time, straight over everything the screen had just drawn.
 *
 * <p>What survived was only what happened to sit in front of the backdrop:
 * shadowed text (whose glyphs are drawn a hair forward of their shadow), item
 * icons and the buttons themselves. Every panel, every card, every bar and
 * every line of unshadowed text was painted over - which is why the picker's
 * cards were bodiless, the trade sheet's columns empty and the Glade's chart a
 * black square. Nobody had ever seen these screens as written.
 *
 * <p>So the order now belongs to this class and cannot be got wrong by a
 * subclass: {@link #render} is final and runs backdrop, then
 * {@link #renderContent}, then the widgets, then {@link #renderOverlay} -
 * once each, every frame.
 */
public abstract class AbyssScreen extends Screen {

    private final long openedAt = Util.getMillis();

    protected AbyssScreen(Component title) {
        super(title);
    }

    /**
     * A wash of colour falling from the top of the backdrop, as ARGB with a low
     * alpha; zero for none. Warm by default - the Abyss's torchlight. The maze's
     * screens cool it.
     */
    protected int glow() {
        return 0x24C08A30;
    }

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        renderContent(g, mouseX, mouseY, partialTick);
        for (Renderable r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        renderOverlay(g, mouseX, mouseY, partialTick);
    }

    /** The screen's own drawing: under the widgets, over the backdrop. */
    protected abstract void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    /** Anything that must sit over the widgets - tooltips, mostly. */
    protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        UiKit.backdrop(g, this.width, this.height, glow());
    }

    /**
     * No blur pass at all. Vanilla runs it from its own background hook, so a
     * screen that merely paints over the result is still paying for it.
     */
    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Intentionally empty.
    }

    /**
     * Time since the screen opened, in sixtieths of a second.
     *
     * <p>The screens' gentle pulses were tuned against a frame counter, which
     * made them run twice as fast at 120 frames a second and stutter at 30.
     * This is the same unit at any frame rate.
     */
    protected int age() {
        return (int) ((Util.getMillis() - openedAt) * 60L / 1000L);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
