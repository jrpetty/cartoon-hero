package com.voxelia.mmo.client;

/**
 * Client-side cache of the server-only stats shown on the profile screen
 * (playtime, deaths, mob kills), fed by ProfileStatsPayload. Everything else on
 * the profile — best skill, prestiges, XP earned — is derived from data the
 * client already has (ClientSkillData / ClientTalents).
 */
public final class ClientProfile {
    private ClientProfile() {}

    private static volatile boolean received = false;
    private static volatile int playTimeTicks = 0;
    private static volatile int deaths = 0;
    private static volatile int mobKills = 0;

    public static void update(int playTime, int deathCount, int kills) {
        playTimeTicks = playTime;
        deaths = deathCount;
        mobKills = kills;
        received = true;
    }

    public static boolean hasData() { return received; }
    public static int playTimeTicks() { return playTimeTicks; }
    public static int deaths() { return deaths; }
    public static int mobKills() { return mobKills; }

    /** Drop the cache so a re-open re-requests fresh numbers. */
    public static void clear() { received = false; }
}
