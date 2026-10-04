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
    private static volatile int mapOrdinal = -1;
    /**
     * The most enemies this round has had left at once - its size, as far as
     * the client can tell - so the HUD can show how much of the wave is down.
     */
    private static volatile int waveTotal = 0;

    /** Whether the live HUD panel is shown (toggled by the keybind). */
    private static volatile boolean hudVisible = true;

    /** Epoch millis the re-entry cooldown ends (0 / past = no active lockout). */
    private static volatile long cooldownUntil = 0L;

    /** Latest squadmate snapshot for the co-op teammate HUD. */
    private static volatile java.util.List<com.jrpetty.aztecabyss.network.TeammateInfo> squad = java.util.List.of();

    private ClientAbyssState() {
    }

    public static void accept(AbyssStatePayload payload) {
        if (payload.round() != round || !payload.inRun()) {
            waveTotal = 0;
        }
        waveTotal = Math.max(waveTotal, payload.enemiesRemaining());
        if (payload.mapOrdinal() >= 0) {
            mapOrdinal = payload.mapOrdinal();
        }
        inRun = payload.inRun();
        round = payload.round();
        fogRound = payload.fogRound();
        enemiesRemaining = payload.enemiesRemaining();
        playersUp = payload.playersUp();
        playersTotal = payload.playersTotal();
        myKills = payload.myKills();
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
        Minecraft.getInstance().setScreen(new MapSelectScreen(
                payload.currentChoice(), bests, payload.customMaps()));
    }

    /** The escape ceremony, opened over the arrival back home. */
    public static void openVictory(com.jrpetty.aztecabyss.network.MazeVictoryPayload payload) {
        Minecraft.getInstance().setScreen(
                new com.jrpetty.aztecabyss.client.MazeVictoryScreen(payload));
    }

    /** The trade board's sign-up sheet: one trade, described, with a confirm. */
    public static void openTradeBoard(com.jrpetty.aztecabyss.network.TradeBoardPayload payload) {
        Minecraft.getInstance().setScreen(
                new com.jrpetty.aztecabyss.client.TradeBoardScreen(payload));
    }

    /**
     * The induction: all four trades, for a Greenie who may not move yet.
     *
     * <p>A repeat while the screen is already up refreshes its roster lines
     * in place rather than replacing the screen - the server re-sends every
     * few seconds as insurance, and insurance must not yank somebody mid-read
     * back to the top of the sheet.
     */
    public static void openInduction(com.jrpetty.aztecabyss.network.MazeInductionPayload payload) {
        if (Minecraft.getInstance().screen
                instanceof com.jrpetty.aztecabyss.client.MazeInductionScreen open) {
            open.refresh(payload);
            return;
        }
        Minecraft.getInstance().setScreen(
                new com.jrpetty.aztecabyss.client.MazeInductionScreen(payload));
    }

    /** The trade sheet, opened fresh each time the server sends one. */
    public static void openSkills(com.jrpetty.aztecabyss.network.SkillTreePayload payload) {
        Minecraft.getInstance().setScreen(
                new com.jrpetty.aztecabyss.client.SkillTreeScreen(payload));
    }

    /**
     * The requisition slate.
     *
     * <p>Re-sent after every click, so this replaces the screen rather than
     * patching it. The tab you were on is carried across by hand, because being
     * bounced back to Metal every time you add a torch would make ordering ten
     * things unbearable.
     */
    public static void openRequisition(com.jrpetty.aztecabyss.network.RequisitionPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        int tab = mc.screen instanceof com.jrpetty.aztecabyss.client.RequisitionScreen open
                ? open.currentTab() : 0;
        mc.setScreen(new com.jrpetty.aztecabyss.client.RequisitionScreen(payload, tab));
    }

    /** The Map Creator's password box. */
    public static void openCreatorGate(com.jrpetty.aztecabyss.network.CreatorGatePayload payload) {
        Minecraft.getInstance().setScreen(new CreatorPasswordScreen(payload.failed()));
    }

    /**
     * The Creator Console. A fresh copy while it is open refreshes it in place,
     * keeping the page, the selected map and anything half-typed - every button
     * on it is answered by one of these.
     */
    public static void openCreatorConsole(com.jrpetty.aztecabyss.network.CreatorConsolePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CreatorConsoleScreen open) {
            open.accept(payload);
            return;
        }
        mc.setScreen(new CreatorConsoleScreen(payload));
    }

    /** The records screen, opened over whatever asked for it. */
    public static void openLeaderboards(com.jrpetty.aztecabyss.network.LeaderboardPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new com.jrpetty.aztecabyss.client.LeaderboardScreen(mc.screen, payload));
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

    /** The arena the run is on, or -1 before the server has said. */
    public static int getMapOrdinal() {
        return mapOrdinal;
    }

    /** The round's size as the client has seen it; 0 between rounds. */
    public static int getWaveTotal() {
        return waveTotal;
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
