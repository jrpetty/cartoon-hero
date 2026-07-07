package com.voxelia.mmo.client;

import com.voxelia.mmo.skill.Skill;
import net.minecraft.Util;

/**
 * Client-side state for the prestige celebration flourish. The server fires
 * {@code PrestigeCelebrationPacket} on a successful prestige; that sets the
 * active animation which {@link PrestigeCelebrationOverlay} draws for
 * {@link #DURATION_MS} before fading out.
 */
public final class PrestigeCelebration {
    private PrestigeCelebration() {}

    public static final long DURATION_MS = 3600L;

    private static volatile Skill skill = null;
    private static volatile int prestigeCount = 0;
    private static volatile long startMillis = 0L;

    /** Begin the flourish for a freshly prestiged skill. Called on the client thread. */
    public static void trigger(int skillOrdinal, int count) {
        Skill[] all = Skill.values();
        if (skillOrdinal < 0 || skillOrdinal >= all.length) return;
        skill = all[skillOrdinal];
        prestigeCount = Math.max(1, count);
        startMillis = Util.getMillis();
    }

    /** 0..1 progress through the animation, or -1 when nothing is playing. */
    public static float progress() {
        if (skill == null) return -1f;
        long elapsed = Util.getMillis() - startMillis;
        if (elapsed < 0 || elapsed >= DURATION_MS) {
            skill = null;
            return -1f;
        }
        return elapsed / (float) DURATION_MS;
    }

    public static Skill skill() { return skill; }

    public static int prestigeCount() { return prestigeCount; }

    /** Forget any running animation (world change / logout). */
    public static void clear() { skill = null; }
}
