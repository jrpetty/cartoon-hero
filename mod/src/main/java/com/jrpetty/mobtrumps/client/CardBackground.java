package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.game.Category;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The themed scene behind a card's portrait, chosen by the mob's
 * {@link Category}. Each category has a hand-tuned 146x78 pixel-art texture
 * (dithered skies, radial glows, silhouettes) shipped in the jar — far richer
 * than the old runtime rectangles. Textures are authored at exactly the
 * portrait box size, so they render 1:1 in card-local space and scale with
 * the card.
 */
public final class CardBackground {

    /** Native size of the background art = the card's portrait box. */
    public static final int BG_W = 146;
    public static final int BG_H = 78;

    private static final Map<Category, ResourceLocation> TEXTURES = new EnumMap<>(Category.class);

    static {
        for (Category c : Category.values()) {
            TEXTURES.put(c, ResourceLocation.fromNamespaceAndPath("mobtrumps",
                    "textures/gui/bg/" + c.name().toLowerCase(Locale.ROOT) + ".png"));
        }
    }

    private CardBackground() {
    }

    /** Draw the scene for {@code category} into the portrait box at (x0,y0). */
    public static void draw(GuiGraphics g, Category category, int x0, int y0, int x1, int y1) {
        if (category == null) {
            g.fillGradient(x0, y0, x1, y1, 0xFFCFE4F2, 0xFFE8F2D9);
            return;
        }
        g.blit(TEXTURES.get(category), x0, y0, 0f, 0f, BG_W, BG_H, BG_W, BG_H);
    }
}
