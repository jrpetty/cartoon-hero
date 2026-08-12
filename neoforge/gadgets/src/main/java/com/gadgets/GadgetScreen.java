package com.gadgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Shared instrument-panel chrome for every gadget screen.
 *
 * <p>One console, drawn once, worn by all of them: a drop shadow so the panel
 * sits above the dimmed world, a brushed header plate with an amber signature
 * line, a machined bevel — lit top-left, shaded bottom-right — corner rivets,
 * and a whisper of amber light spilling down from the header. Every screen
 * inherits the same face, so the mod reads as one instrument family rather
 * than nine dialogs that happen to share a background colour.
 */
public abstract class GadgetScreen extends Screen {
    protected static final int BG = 0xF0101418;
    protected static final int HEAD_BG = 0xFF1C2028;
    protected static final int FRAME = 0xFF3C424E;
    protected static final int AMBER = 0xFFFFC864;
    protected static final int DIM = 0xFFC08840;
    protected static final int GRAY = 0xFF8E96A4;
    protected static final int RED = 0xFFFF5555;
    protected static final int GREEN = 0xFF7CE87C;
    /** The recessed ground {@link #panel} wells are cut into. */
    protected static final int WELL = 0xFF0C0F13;

    private static final int HEAD_TOP = 0xFF232833;
    private static final int HEAD_BOTTOM = 0xFF181C24;
    private static final int BEVEL_LIGHT = 0xFF4A5260;
    private static final int BEVEL_DARK = 0xFF14181E;
    private static final int RIVET = 0xFF5A6270;
    private static final int RIVET_SHADOW = 0xFF0A0C10;
    private static final int SHADOW = 0x66000000;
    private static final int GLOW = 0x16FFC864;
    private static final int FOOT_SHADE = 0x30000000;

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
        gfx.fill(left + 4, top + 5, left + panelW + 4, top + panelH + 5, SHADOW);
        gfx.fill(left, top, left + panelW, top + panelH, BG);
        // Header plate, its amber signature line, light spilling below it and
        // shade pooling at the foot — what turns a flat fill into a lit panel.
        gfx.fillGradient(left + 1, top + 1, left + panelW - 1, top + 14, HEAD_TOP, HEAD_BOTTOM);
        gfx.fill(left + 1, top + 14, left + panelW - 1, top + 15, DIM);
        gfx.fillGradient(left + 1, top + 15, left + panelW - 1, top + 33, GLOW, GLOW & 0x00FFFFFF);
        gfx.fillGradient(left + 1, top + panelH - 14, left + panelW - 1, top + panelH - 1,
                FOOT_SHADE & 0x00FFFFFF, FOOT_SHADE);
        // Machined bevel under the outline: lit where the light lands, dark
        // where the edge falls away.
        gfx.fill(left + 1, top + 1, left + panelW - 1, top + 2, BEVEL_LIGHT);
        gfx.fill(left + 1, top + 2, left + 2, top + panelH - 1, BEVEL_LIGHT);
        gfx.fill(left + 1, top + panelH - 2, left + panelW - 1, top + panelH - 1, BEVEL_DARK);
        gfx.fill(left + panelW - 2, top + 2, left + panelW - 1, top + panelH - 1, BEVEL_DARK);
        gfx.renderOutline(left, top, panelW, panelH, FRAME);
        rivet(gfx, left + 4, top + 4);
        rivet(gfx, left + panelW - 6, top + 4);
        rivet(gfx, left + 4, top + panelH - 6);
        rivet(gfx, left + panelW - 6, top + panelH - 6);
        gfx.drawCenteredString(font, title, left + panelW / 2, top + 3, AMBER);
    }

    /** A corner screw: two lit pixels and one in shadow. */
    private static void rivet(GuiGraphics gfx, int x, int y) {
        gfx.fill(x, y, x + 2, y + 2, RIVET);
        gfx.fill(x + 1, y + 1, x + 2, y + 2, RIVET_SHADOW);
    }

    /** A recessed well with a hairline frame — the ground grouped readouts sit in. */
    protected void panel(GuiGraphics gfx, int x0, int y0, int x1, int y1, int fill) {
        gfx.fill(x0, y0, x1, y1, WELL);
        gfx.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
        gfx.renderOutline(x0, y0, x1 - x0, y1 - y0, FRAME);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected static void send(BlockPos pos, String key, int value) {
        PacketDistributor.sendToServer(new GadgetConfigPayload(pos, key, value, ""));
    }

    protected static void sendText(BlockPos pos, String key, String text) {
        PacketDistributor.sendToServer(new GadgetConfigPayload(pos, key, 0, text));
    }
}
