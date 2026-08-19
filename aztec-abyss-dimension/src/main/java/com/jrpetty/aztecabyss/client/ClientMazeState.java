package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.network.MazeHubPayload;
import com.jrpetty.aztecabyss.network.MazeStatePayload;
import com.jrpetty.aztecabyss.network.RequestMazeHubPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The client's copy of what the maze just said.
 *
 * <p>Mirrors {@link ClientAbyssState} exactly: payloads land here, the HUD and
 * the hub read from here, and nothing on this side ever computes a fact about
 * the game - it repeats the server's last word and says how old that word is.
 *
 * <h2>Staleness is part of the state</h2>
 *
 * <p>The feed arrives once a second while the maze is ticking, so a snapshot
 * more than a few seconds old means the player left, the dimension unloaded,
 * or the server stalled - and in every one of those cases the right HUD is no
 * HUD. Drawing a frozen clock would be worse than drawing nothing, because a
 * frozen clock looks exactly like a working one until the doors shut on you.
 */
public final class ClientMazeState {

    /** How old the snapshot may grow before the HUD stops trusting it. */
    private static final int STALE_TICKS = 80;

    private static MazeStatePayload state;
    /** Client game time when the snapshot landed, for smooth countdowns. */
    private static long receivedAt;

    private ClientMazeState() {
    }

    public static void accept(MazeStatePayload payload) {
        state = payload;
        Minecraft mc = Minecraft.getInstance();
        receivedAt = mc.level != null ? mc.level.getGameTime() : 0L;
    }

    public static void openHub(MazeHubPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new MazeHubScreen(payload));
    }

    /** Asks the server for the hub sheet; the answer opens the screen. */
    public static void requestHub() {
        PacketDistributor.sendToServer(RequestMazeHubPayload.INSTANCE);
    }

    public static MazeStatePayload state() {
        return state;
    }

    /** Ticks since the snapshot landed - the smooth half of the countdown. */
    public static int ticksSince() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || state == null) {
            return 0;
        }
        return (int) Math.max(0L, Math.min(STALE_TICKS, mc.level.getGameTime() - receivedAt));
    }

    /** Whether there is a live maze to draw: right dimension, fresh snapshot. */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return state != null
                && mc.level != null
                && mc.level.dimension().equals(AztecAbyssConstants.MAZE_LEVEL_KEY)
                && mc.level.getGameTime() - receivedAt <= STALE_TICKS;
    }

    /**
     * Seconds until the doors move next, ticking smoothly between packets.
     *
     * <p>The packet carries raw phase and the two lengths precisely so this can
     * count client-side: a countdown that only moves when a packet lands
     * stutters, and a stuttering countdown reads as lag rather than as a clock.
     */
    public static int doorSeconds() {
        if (state == null) {
            return 0;
        }
        int end = state.isNight() ? state.dayLen() + state.nightLen() : state.dayLen();
        return Math.max(0, (end - (state.phase() + ticksSince())) / 20);
    }
}
