package com.voxelia.mmo.client;

import com.voxelia.mmo.progression.Milestones;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.Util;

import java.util.ArrayList;
import java.util.List;

/** Client-side queue of perk-unlock toasts, newest last. Drawn by {@link MilestoneToastOverlay}. */
public final class MilestoneToasts {
    private MilestoneToasts() {}

    /** Fade in, hold, fade out. */
    public static final long DURATION_MS = 4600L;
    private static final int MAX_SHOWN = 3;

    public record Toast(Skill skill, Milestones.Kind kind, int level, long start) {}

    private static final List<Toast> ACTIVE = new ArrayList<>();

    /** Called from the packet handler on the client thread. */
    public static void trigger(int skillOrdinal, int kindOrdinal, int level) {
        Skill[] skills = Skill.values();
        Milestones.Kind[] kinds = Milestones.Kind.values();
        if (skillOrdinal < 0 || skillOrdinal >= skills.length) return;
        if (kindOrdinal < 0 || kindOrdinal >= kinds.length) return;

        // Stagger arrivals so two unlocks on one level-up don't land on top of each other.
        long now = Util.getMillis();
        long start = now;
        for (Toast t : ACTIVE) start = Math.max(start, t.start() + 400L);
        ACTIVE.add(new Toast(skills[skillOrdinal], kinds[kindOrdinal], level, start));
    }

    /** Live toasts, oldest first, expired ones dropped. */
    public static List<Toast> active() {
        long now = Util.getMillis();
        ACTIVE.removeIf(t -> now - t.start() > DURATION_MS);
        if (ACTIVE.size() <= MAX_SHOWN) return ACTIVE;
        return ACTIVE.subList(ACTIVE.size() - MAX_SHOWN, ACTIVE.size());
    }

    /** 0..1 fade envelope for one toast, or -1 if it hasn't started or is finished. */
    public static float alpha(Toast toast) {
        long dt = Util.getMillis() - toast.start();
        if (dt < 0 || dt > DURATION_MS) return -1f;
        if (dt < 220L) return dt / 220f;
        if (dt > DURATION_MS - 420L) return (DURATION_MS - dt) / 420f;
        return 1f;
    }
}
