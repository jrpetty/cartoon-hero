package com.voxelia.mmo.client;

import com.voxelia.mmo.skill.Skill;

/** Client-side ability state: which ability is selected + per-ability cooldown for the HUD. */
public final class ClientAbilities {
    private ClientAbilities() {}

    private static final int COUNT = Skill.values().length;

    private static int selected = Skill.COMBAT.ordinal();
    private static long clientTick = 0;
    private static final long[] cooldownEnd = new long[COUNT];
    private static final int[] cooldownTotal = new int[COUNT];

    public static int selected() { return selected; }

    public static Skill selectedSkill() { return Skill.values()[selected]; }

    public static void cycle(int dir) {
        // advance to the next skill that actually has an active ability
        for (int i = 0; i < COUNT; i++) {
            selected = ((selected + dir) % COUNT + COUNT) % COUNT;
            if (Skill.values()[selected].active()) return;
        }
    }

    public static void clientTick() { clientTick++; }

    public static void setCooldown(int ordinal, int durationTicks) {
        if (ordinal < 0 || ordinal >= COUNT) return;
        cooldownEnd[ordinal] = clientTick + durationTicks;
        cooldownTotal[ordinal] = Math.max(1, durationTicks);
    }

    public static long cooldownRemainingTicks(int ordinal) {
        if (ordinal < 0 || ordinal >= COUNT) return 0;
        return Math.max(0, cooldownEnd[ordinal] - clientTick);
    }

    /** 1.0 just-used -> 0.0 ready. */
    public static float cooldownFraction(int ordinal) {
        if (ordinal < 0 || ordinal >= COUNT || cooldownTotal[ordinal] <= 0) return 0f;
        return (float) cooldownRemainingTicks(ordinal) / cooldownTotal[ordinal];
    }
}
