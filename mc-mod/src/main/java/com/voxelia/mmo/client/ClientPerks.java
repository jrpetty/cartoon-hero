package com.voxelia.mmo.client;

import com.voxelia.mmo.progression.Milestones;
import com.voxelia.mmo.skill.Skill;

import java.util.ArrayList;
import java.util.List;

/** Client-side cache of each skill's live perk summary and the server's unlock levels. */
public final class ClientPerks {
    private ClientPerks() {}

    /** Unlock level when the server hasn't said yet: treated as "unlocked" so nothing flashes locked. */
    public static final int UNKNOWN = -1;

    private static List<String> lines = List.of();
    private static List<Integer> unlocks = List.of();

    public static void update(List<String> incoming, List<Integer> unlockTable) {
        lines = List.copyOf(incoming);
        unlocks = List.copyOf(unlockTable);
    }

    /** The raw summary for a skill, or empty if the server hasn't sent one yet. */
    public static String line(Skill skill) {
        int i = skill.ordinal();
        return i < lines.size() ? lines.get(i) : "";
    }

    /** The summary split into one bullet per bonus, ready for a tooltip. */
    public static List<String> bullets(Skill skill) {
        String line = line(skill);
        List<String> out = new ArrayList<>();
        if (line.isEmpty()) return out;
        for (String part : line.split(", ")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    /** Level this skill's ability unlocks at (0 = disabled on this server, {@link #UNKNOWN} = not synced). */
    public static int abilityLevel(Skill skill) {
        int i = skill.ordinal();
        return i < unlocks.size() ? unlocks.get(i) : UNKNOWN;
    }

    /** Level a passive perk unlocks at (0 = disabled, {@link #UNKNOWN} = not synced). */
    public static int passiveLevel(Milestones.Kind kind) {
        if (kind == Milestones.Kind.ABILITY) return UNKNOWN;
        int i = Skill.values().length + kind.ordinal() - 1; // ABILITY is ordinal 0 and has no slot
        return i < unlocks.size() ? unlocks.get(i) : UNKNOWN;
    }
}
