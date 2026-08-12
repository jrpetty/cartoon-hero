package com.gadgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A button drawn in the gadgets' own instrument-panel style.
 *
 * <p>Every screen in this mod is a dark machined console, and a vanilla stone
 * button sitting in the middle of one is the loudest possible signal that the
 * UI is assembled rather than designed. This keeps vanilla {@link Button}
 * behaviour — focus, narration, activation — and replaces only the paint:
 * a dark face with a lit top edge, an amber ring and amber text under the
 * cursor, and a face that visibly goes dead when the button does.
 */
public class PanelButton extends Button {
    private static final int FACE = 0xFF20262E;
    private static final int FACE_HOT = 0xFF2C3442;
    private static final int FACE_DEAD = 0xFF161A20;
    private static final int RING = 0xFF3C424E;
    private static final int RING_HOT = 0xFFFFC864;
    private static final int RING_DEAD = 0xFF272C34;
    private static final int TEXT = 0xFFCBD2DC;
    private static final int TEXT_HOT = 0xFFFFC864;
    private static final int TEXT_DEAD = 0xFF5C6472;
    private static final int SHEEN = 0x2EFFFFFF;
    private static final int SHEEN_HOT = 0x50FFC864;
    private static final int FOOT = 0x40000000;

    private PanelButton(Component message, OnPress onPress, int x, int y, int w, int h) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
    }

    public static PanelButton of(Component message, OnPress onPress, int x, int y, int w, int h) {
        return new PanelButton(message, onPress, x, y, w, h);
    }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        boolean hot = active && isHoveredOrFocused();
        int x0 = getX();
        int y0 = getY();
        gfx.fill(x0, y0, x0 + width, y0 + height, !active ? FACE_DEAD : hot ? FACE_HOT : FACE);
        if (active) {
            // A lit top edge and a shaded foot — the two pixels that make a
            // flat rectangle read as a key that can be pressed.
            gfx.fill(x0 + 1, y0 + 1, x0 + width - 1, y0 + 2, hot ? SHEEN_HOT : SHEEN);
            gfx.fill(x0 + 1, y0 + height - 2, x0 + width - 1, y0 + height - 1, FOOT);
        }
        gfx.renderOutline(x0, y0, width, height, !active ? RING_DEAD : hot ? RING_HOT : RING);
        Font font = Minecraft.getInstance().font;
        int tw = font.width(getMessage());
        gfx.drawString(font, getMessage(), x0 + (width - tw) / 2, y0 + (height - 8) / 2,
                !active ? TEXT_DEAD : hot ? TEXT_HOT : TEXT, false);
    }
}
