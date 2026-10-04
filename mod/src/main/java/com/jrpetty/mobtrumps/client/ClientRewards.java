package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.MobTrumps;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The "+25 XP" a finished game pays, shown where vanilla shows its own toasts:
 * top right, sliding in from the edge, gone again in under three seconds.
 *
 * <p>Drawn after whatever screen is open, so it sits on top of the game that
 * paid it, and by a HUD layer when no screen is open. The battle screen never
 * gets one — its result panel shows the experience as part of the result.
 */
@EventBusSubscriber(modid = MobTrumps.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientRewards {

    private static final long SHOW_MS = 2800L;
    private static final long SLIDE_MS = 220L;
    private static final long FADE_MS = 450L;
    private static final int XP_GREEN = 0x80FF20;

    private static volatile int amount;
    private static volatile long at;

    private ClientRewards() {
    }

    public static void xp(int gained) {
        amount = gained;
        at = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        draw(event.getGuiGraphics());
    }

    /** HUD layer: only with no screen open, since an open screen draws it on top itself. */
    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        if (Minecraft.getInstance().screen == null) {
            draw(g);
        }
    }

    private static void draw(GuiGraphics g) {
        long age = System.currentTimeMillis() - at;
        if (amount <= 0 || age < 0 || age > SHOW_MS) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        String text = "+" + amount + " XP";
        int w = font.width(text) + 24;
        int h = 16;
        float in = ClientPrefs.reducedMotion() ? 1f : Mth.clamp(age / (float) SLIDE_MS, 0f, 1f);
        float ease = 1f - (1f - in) * (1f - in) * (1f - in);
        float alpha = age > SHOW_MS - FADE_MS ? (SHOW_MS - age) / (float) FADE_MS : 1f;
        int a = Mth.clamp(Math.round(alpha * 255f), 0, 255);
        if (a < 8) {
            return; // nearly gone; a near-zero alpha renders as fully opaque text
        }
        int x = g.guiWidth() - 6 - Math.round(w * ease);
        int y = 6;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 400); // above anything the screen drew
        g.fill(x, y, x + w, y + h, (Math.min(a, 0xE0) << 24) | 0x0E1A0C);
        g.renderOutline(x, y, w, h, (a << 24) | XP_GREEN);
        // the orb: a lime core in a darker ring, like the one that just flew in
        int ox = x + 6;
        int oy = y + 4;
        g.fill(ox, oy, ox + 8, oy + 8, (a << 24) | 0x3E8E12);
        g.fill(ox + 1, oy + 1, ox + 7, oy + 7, (a << 24) | XP_GREEN);
        g.fill(ox + 2, oy + 2, ox + 4, oy + 4, (a << 24) | 0xF2FFB0);
        g.drawString(font, text, x + 18, y + 4, (a << 24) | XP_GREEN, true);
        pose.popPose();
    }
}
