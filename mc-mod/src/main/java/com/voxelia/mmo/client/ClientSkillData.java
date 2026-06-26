package com.voxelia.mmo.client;

import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.Skill;

/** Client-side cache of the local player's skills, fed by SkillsSyncPayload. */
public final class ClientSkillData {
    private ClientSkillData() {}

    private static volatile PlayerSkills current = null;

    public static void update(PlayerSkills skills) {
        current = skills;
    }

    public static int xp(Skill s) { return current == null ? 0 : current.getXp(s); }

    public static int level(Skill s) { return current == null ? 1 : current.getLevel(s); }

    public static boolean hasData() { return current != null; }
}
