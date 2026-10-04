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

    /** Directly select a skill's ability (Skills screen). Locked abilities can't be selected. */
    public static boolean select(Skill s) {
        if (!s.active() || !unlocked(s)) return false;
        selected = s.ordinal();
        return true;
    }

    /**
     * Steps to the next ability you can actually use. With nothing unlocked yet it
     * walks every ability, so the cycle key still previews what's coming.
     */
    public static void cycle(int dir) {
        boolean any = anyUnlocked();
        for (int i = 0; i < COUNT; i++) {
            selected = ((selected + dir) % COUNT + COUNT) % COUNT;
            Skill s = Skill.values()[selected];
            if (s.active() && (!any || unlocked(s))) return;
        }
    }

    /** Level this skill's ability unlocks at, or 0 if the server has it disabled. */
    public static int unlockLevel(Skill s) {
        int lvl = ClientPerks.abilityLevel(s);
        return lvl == ClientPerks.UNKNOWN ? 1 : lvl;
    }

    /** True when the player's level in {@code s} meets the server's unlock level. */
    public static boolean unlocked(Skill s) {
        int lvl = ClientPerks.abilityLevel(s);
        if (lvl == ClientPerks.UNKNOWN) return true; // not synced yet: don't flash a lock
        return lvl > 0 && ClientSkillData.level(s) >= lvl;
    }

    public static boolean anyUnlocked() {
        for (Skill s : Skill.values()) if (s.active() && unlocked(s)) return true;
        return false;
    }

    /** The locked ability closest to unlocking (fewest levels to go), or null if none are locked. */
    public static Skill nextToUnlock() {
        Skill best = null;
        int bestGap = Integer.MAX_VALUE;
        for (Skill s : Skill.values()) {
            int lvl = unlockLevel(s);
            if (!s.active() || lvl <= 0 || unlocked(s)) continue;
            int gap = lvl - ClientSkillData.level(s);
            if (gap < bestGap) { bestGap = gap; best = s; }
        }
        return best;
    }

    /**
     * If the selected ability is locked but another one isn't, move the selection to the
     * highest-level unlocked skill — so a new unlock is ready to fire without a keypress.
     */
    public static void ensureUsable() {
        Skill cur = selectedSkill();
        if (unlocked(cur) || !anyUnlocked()) return;
        Skill best = null;
        for (Skill s : Skill.values()) {
            if (!s.active() || !unlocked(s)) continue;
            if (best == null || ClientSkillData.level(s) > ClientSkillData.level(best)) best = s;
        }
        if (best != null) selected = best.ordinal();
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
