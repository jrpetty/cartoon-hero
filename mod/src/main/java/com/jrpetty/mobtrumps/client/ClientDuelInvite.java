package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.DuelInvitePayload;
import com.jrpetty.mobtrumps.MobTrumps;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** The challenge waiting for an answer, if there is one. */
@EventBusSubscriber(modid = MobTrumps.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientDuelInvite {

    private static volatile String from = "";
    private static volatile String terms = "";
    private static volatile long expiresAt;

    private ClientDuelInvite() {
    }

    /**
     * An invite that landed while the player was mid-screen still has to reach
     * them. The packet handler refuses to snatch an open screen away — dropping
     * someone out of their inventory mid-drag, or off a live Bluff table, to ask
     * a question would be worse than the chat line it replaced — so the invite
     * simply waits here and opens the moment they are back in the world.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!pending()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            mc.setScreen(new DuelInviteScreen());
        }
    }

    public static void set(DuelInvitePayload payload) {
        if (payload.action() == DuelInvitePayload.CLEAR) {
            from = "";
            terms = "";
            expiresAt = 0;
            return;
        }
        from = payload.from();
        terms = payload.terms();
        expiresAt = System.currentTimeMillis() + payload.seconds() * 1000L;
    }

    /** True while an invite is open and has not run out of time. */
    public static boolean pending() {
        return !from.isEmpty() && System.currentTimeMillis() < expiresAt;
    }

    public static String from() {
        return from;
    }

    public static String terms() {
        return terms;
    }

    /** Whole seconds left to answer, never below zero. */
    public static int secondsLeft() {
        return (int) Math.max(0, (expiresAt - System.currentTimeMillis() + 999) / 1000);
    }

    public static void dismiss() {
        from = "";
        terms = "";
        expiresAt = 0;
    }
}
