package com.voxelia.mmo.client;

import com.voxelia.mmo.skill.Skill;

/** Client-side "selected ability" state (which skill's ability the use key fires). */
public final class ClientAbilities {
    private ClientAbilities() {}

    private static int selected = Skill.COMBAT.ordinal();

    public static int selected() { return selected; }

    public static Skill selectedSkill() { return Skill.values()[selected]; }

    public static void cycle(int dir) {
        int n = Skill.values().length;
        selected = ((selected + dir) % n + n) % n;
    }
}
