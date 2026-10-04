package com.jrpetty.aztecabyss.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

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
 *
 * <h2>The size a screen was designed for</h2>
 *
 * <p>Minecraft's "Auto" GUI scale gives a 1080p monitor 480 by 270 GUI pixels
 * to work with, and that is what most players have. The Glade's screens were
 * laid out - and photographed, and checked - at 640 by 360. At 480 they ran off
 * the edges: the trade sheet's four columns hung over both sides and the
 * induction's cards ran under their own button.
 *
 * <p>A screen that would rather keep its layout than squeeze it says so with
 * {@link #keepDesignLayout}, and is then drawn at the largest whole GUI scale
 * that still gives it {@value #DESIGN_W} by {@value #DESIGN_H} - on a 1080p
 * monitor, scale 3 inside scale 4. A whole scale keeps every font pixel a whole
 * number of screen pixels, so the text is as sharp as any other screen's. The
 * mouse is converted on the way in, so a subclass and its widgets only ever see
 * their own layout's coordinates: that is why the mouse hooks here are final
 * and subclasses override {@link #clickedAt} and friends instead.
 */
public abstract class AbyssScreen extends Screen {

    /** The smallest space, in GUI pixels, a {@link #keepDesignLayout} screen is laid out in. */
    public static final int DESIGN_W = 600;
    public static final int DESIGN_H = 330;

    private final long openedAt = Util.getMillis();

    /** How much smaller than the GUI scale this screen is drawn; 1 for not at all. */
    private float fit = 1.0f;

    protected AbyssScreen(Component title) {
        super(title);
    }

    /**
     * True for a screen whose layout needs {@value #DESIGN_W} by
     * {@value #DESIGN_H} GUI pixels and should be drawn smaller rather than
     * squeezed when the GUI scale leaves less.
     */
    protected boolean keepDesignLayout() {
        return false;
    }

    /** How much smaller than the GUI scale this screen is being drawn; 1 for not at all. */
    public float fit() {
        return fit;
    }

    /**
     * Works out the size the screen is laid out at, then lets the subclass lay
     * itself out with {@link #initWidgets}. Final so that it always runs first:
     * vanilla sets {@code width} and {@code height} to the GUI's size just
     * before it calls this, on every open, resize and rebuild.
     */
    @Override
    protected final void init() {
        fit = 1.0f;
        if (keepDesignLayout() && this.minecraft != null) {
            Window window = this.minecraft.getWindow();
            int scale = Math.max(1, (int) Math.round(window.getGuiScale()));
            int fitted = scale;
            while (fitted > 1 && (window.getWidth() / fitted < DESIGN_W || window.getHeight() / fitted < DESIGN_H)) {
                fitted--;
            }
            if (fitted < scale) {
                fit = fitted / (float) scale;
                this.width = Mth.ceil(window.getGuiScaledWidth() / fit);
                this.height = Mth.ceil(window.getGuiScaledHeight() / fit);
            }
        }
        initWidgets();
    }

    /** Where a subclass adds its widgets, at the size it will be drawn at. */
    protected void initWidgets() {
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
        int mx = Mth.floor(mouseX / fit);
        int my = Mth.floor(mouseY / fit);
        g.pose().pushPose();
        if (fit != 1.0f) {
            g.pose().scale(fit, fit, 1.0f);
        }
        renderBackground(g, mx, my, partialTick);
        renderContent(g, mx, my, partialTick);
        for (Renderable r : this.renderables) {
            r.render(g, mx, my, partialTick);
        }
        renderOverlay(g, mx, my, partialTick);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------
    // The mouse, in the layout's own coordinates
    // ------------------------------------------------------------------

    @Override
    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return clickedAt(mouseX / fit, mouseY / fit, button);
    }

    @Override
    public final boolean mouseReleased(double mouseX, double mouseY, int button) {
        return releasedAt(mouseX / fit, mouseY / fit, button);
    }

    @Override
    public final boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return draggedAt(mouseX / fit, mouseY / fit, button, dragX / fit, dragY / fit);
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrolledAt(mouseX / fit, mouseY / fit, scrollX, scrollY);
    }

    /** A click, at the layout's coordinates. */
    protected boolean clickedAt(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** A release, at the layout's coordinates. */
    protected boolean releasedAt(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** A drag, at the layout's coordinates. */
    protected boolean draggedAt(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** A scroll, at the layout's coordinates. */
    protected boolean scrolledAt(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
