package com.voxelia.mmo.client;

import com.voxelia.mmo.skill.Skill;

import java.util.ArrayList;
import java.util.List;

/** Client-side cache of each skill's live perk summary, as computed by the server. */
public final class ClientPerks {
    private ClientPerks() {}

    private static List<String> lines = List.of();

    public static void update(List<String> incoming) {
        lines = List.copyOf(incoming);
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
}
