package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.network.EngineHudPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * The run panel for maps played on the Arena Engine.
 *
 * <p>A player's map from the portal, or a play-test in the Workshop, used to
 * show nothing but a boss bar. On a map with a points economy - most of them -
 * that meant playing a whole run without ever seeing your points: the only
 * place your balance appeared was the line that came up while you looked at a
 * shop sign. This is the same dark plate the built-in arenas use, in the same
 * corner, worded by the server.
 *
 * <p>The panel goes away by itself if the server stops talking about it for a
 * few seconds, so a run that ends by a route nobody announced - a dropped
 * connection, a teleport - cannot leave it stuck on screen.
 */
@EventBusSubscriber(modid = AztecAbyssConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class EngineHud {

    private static final int PANEL = 0xD90B0A10;
    private static final long STALE_MS = 4000L;

    private static EngineHudPayload shown = EngineHudPayload.cleared();
    private static long receivedAt;

    private EngineHud() {
    }

    public static void accept(EngineHudPayload payload) {
        shown = payload;
        receivedAt = Util.getMillis();
    }

    /** Whether the panel is up - the arena HUD keeps out of its way. */
    public static boolean showing() {
        return !shown.title().isEmpty() && Util.getMillis() - receivedAt < STALE_MS;
    }

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        shown = EngineHudPayload.cleared();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !showing() || !ClientAbyssState.isHudVisible()) {
            return;
        }
        draw(event.getGuiGraphics(), mc.font, shown);
    }

    private static void draw(GuiGraphics g, Font font, EngineHudPayload hud) {
        int x = 8;
        int y = 8;
        int w = 156;
        for (String line : hud.lines()) {
            w = Math.max(w, Math.min(220, font.width(line) + 18));
        }
        int h = 30 + (hud.progress() >= 0 ? 8 : 0) + hud.lines().size() * 11 + 2;
        int accent = hud.accent() == 0 ? UiKit.GOLD : hud.accent();

        g.fill(x, y, x + w, y + h, PANEL);
        UiKit.outline(g, x, y, w, h, UiKit.EDGE);
        g.fill(x, y, x + 2, y + h, accent);

        int tx = x + 8;
        int ty = y + 5;
        g.drawString(font, font.plainSubstrByWidth(UiKit.strip(hud.title()).toUpperCase(java.util.Locale.ROOT),
                w - 16), tx, ty, UiKit.TEXT_FAINT, true);
        ty += 11;
        g.drawString(font, Component.literal(hud.headline()).withStyle(st -> st.withBold(true)),
                tx, ty, accent, true);
        if (!hud.right().isEmpty()) {
            g.drawString(font, hud.right(), x + w - 6 - font.width(hud.right()), ty, UiKit.TEXT_DIM, true);
        }
        ty += 12;
        if (hud.progress() >= 0) {
            UiKit.meter(g, tx, ty, w - 14, 4, Math.min(1000, hud.progress()) / 1000.0f, accent);
            ty += 8;
        }
        for (String line : hud.lines()) {
            g.drawString(font, font.plainSubstrByWidth(line, w - 14), tx, ty, UiKit.TEXT, true);
            ty += 11;
        }
    }
}
