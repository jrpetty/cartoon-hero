package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.AbyssStatePayload;
import com.jrpetty.aztecabyss.network.RunRecapPayload;
import net.minecraft.client.Minecraft;

/**
 * Client-only mirror of the small bit of run state the atmosphere effects need,
 * plus the hooks that open the recap screen and kick off the arrival cinematic.
 * Updated from the server payloads; read by the client FX and HUD.
 */
public final class ClientAbyssState {

    private static volatile boolean inRun = false;
    private static volatile int round = 0;
    private static volatile boolean fogRound = false;

    /** Counts down while the arrival cinematic plays (client ticks). 0 = not playing. */
    private static int cinematicTicks = 0;

    private ClientAbyssState() {
    }

    public static void accept(AbyssStatePayload payload) {
        boolean wasInRun = inRun;
        inRun = payload.inRun();
        round = payload.round();
        fogRound = payload.fogRound();
        // Entering an active run triggers the arrival cinematic.
        if (inRun && !wasInRun) {
            cinematicTicks = 110; // ~5.5s
        }
        if (!inRun) {
            cinematicTicks = 0;
        }
    }

    public static void openRecap(RunRecapPayload payload) {
        Minecraft.getInstance().setScreen(new RunRecapScreen(payload));
    }

    public static boolean isInRun() {
        return inRun;
    }

    public static int getRound() {
        return round;
    }

    public static boolean isFogRound() {
        return inRun && fogRound;
    }

    public static int getCinematicTicks() {
        return cinematicTicks;
    }

    public static void decrementCinematic() {
        if (cinematicTicks > 0) {
            cinematicTicks--;
        }
    }

    /** 0.0 at round 0, ramping to 1.0 by the final round - drives fog thickness etc. */
    public static float intensity(int maxRound) {
        if (!inRun || maxRound <= 0) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, (float) round / (float) maxRound));
    }
}
