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
    private static volatile int enemiesRemaining = 0;
    private static volatile int playersUp = 0;
    private static volatile int playersTotal = 0;
    private static volatile int myKills = 0;

    /** Whether the live HUD panel is shown (toggled by the keybind). */
    private static volatile boolean hudVisible = true;

    /** Epoch millis the re-entry cooldown ends (0 / past = no active lockout). */
    private static volatile long cooldownUntil = 0L;

    /** Latest squadmate snapshot for the co-op teammate HUD. */
    private static volatile java.util.List<com.jrpetty.aztecabyss.network.TeammateInfo> squad = java.util.List.of();

    /** Counts down while the arrival cinematic plays (client ticks). 0 = not playing. */
    private static int cinematicTicks = 0;

    private ClientAbyssState() {
    }

    public static void accept(AbyssStatePayload payload) {
        boolean wasInRun = inRun;
        inRun = payload.inRun();
        round = payload.round();
        fogRound = payload.fogRound();
        enemiesRemaining = payload.enemiesRemaining();
        playersUp = payload.playersUp();
        playersTotal = payload.playersTotal();
        myKills = payload.myKills();
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

    public static void openMapPicker(com.jrpetty.aztecabyss.network.OpenMapPickerPayload payload) {
        Minecraft.getInstance().setScreen(new MapSelectScreen(payload.currentChoice()));
    }

    public static void acceptCooldown(com.jrpetty.aztecabyss.network.AbyssCooldownPayload payload) {
        cooldownUntil = payload.cooldownUntil();
    }

    /** Millis remaining on the re-entry lockout, or 0 if none. */
    public static long cooldownRemainingMillis() {
        return Math.max(0L, cooldownUntil - System.currentTimeMillis());
    }

    public static void acceptSquad(com.jrpetty.aztecabyss.network.SquadPayload payload) {
        squad = payload.teammates();
    }

    public static java.util.List<com.jrpetty.aztecabyss.network.TeammateInfo> getSquad() {
        return squad;
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

    public static int getEnemiesRemaining() {
        return enemiesRemaining;
    }

    public static int getPlayersUp() {
        return playersUp;
    }

    public static int getPlayersTotal() {
        return playersTotal;
    }

    public static int getMyKills() {
        return myKills;
    }

    public static boolean isHudVisible() {
        return hudVisible;
    }

    public static void toggleHud() {
        hudVisible = !hudVisible;
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
