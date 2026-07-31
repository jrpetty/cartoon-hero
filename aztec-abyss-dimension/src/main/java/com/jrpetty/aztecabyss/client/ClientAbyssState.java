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

    /** Boards left on each horde gate, or empty on maps without barricades. */
    private static volatile int[] gateBoards = new int[0];

    /** Whether the live HUD panel is shown (toggled by the keybind). */
    private static volatile boolean hudVisible = true;

    /** Epoch millis the re-entry cooldown ends (0 / past = no active lockout). */
    private static volatile long cooldownUntil = 0L;

    /** Latest squadmate snapshot for the co-op teammate HUD. */
    private static volatile java.util.List<com.jrpetty.aztecabyss.network.TeammateInfo> squad = java.util.List.of();

    private ClientAbyssState() {
    }

    public static void accept(AbyssStatePayload payload) {
        inRun = payload.inRun();
        round = payload.round();
        fogRound = payload.fogRound();
        enemiesRemaining = payload.enemiesRemaining();
        playersUp = payload.playersUp();
        playersTotal = payload.playersTotal();
        myKills = payload.myKills();
        if (!payload.hasGates()) {
            gateBoards = new int[0];
        } else {
            int[] g = new int[4];
            int n = 0;
            for (int i = 0; i < 4; i++) {
                int v = payload.gateBoards(i);
                if (v == AbyssStatePayload.NO_GATE) {
                    break;
                }
                g[n++] = v;
            }
            gateBoards = java.util.Arrays.copyOf(g, n);
        }
    }

    /** Boards left per horde gate; empty when the active map has no barricades. */
    public static int[] getGateBoards() {
        return gateBoards;
    }

    public static void openRecap(RunRecapPayload payload) {
        Minecraft.getInstance().setScreen(new RunRecapScreen(payload));
    }

    public static void openMapPicker(com.jrpetty.aztecabyss.network.OpenMapPickerPayload payload) {
        int n = com.jrpetty.aztecabyss.worldgen.ArenaMap.values().length;
        int[] bests = new int[n];
        for (int i = 0; i < n; i++) {
            bests[i] = payload.bestOn(i);
        }
        Minecraft.getInstance().setScreen(new MapSelectScreen(payload.currentChoice(), bests));
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

    /** 0.0 at round 0, ramping to 1.0 by the final round - drives fog thickness etc. */
    public static float intensity(int maxRound) {
        if (!inRun || maxRound <= 0) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, (float) round / (float) maxRound));
    }
}
