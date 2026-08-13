package com.voxelia.mmo.game;

import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.Talent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The Memory deck, built from the mod's own cards — nothing invented for the
 * minigame. Faces are, in order of preference:
 *
 * <ol>
 *   <li>the eleven <b>skill cards</b>, in their HUD colours;</li>
 *   <li>the gold <b>Character card</b> from the skills screen;</li>
 *   <li><b>talent cards</b> — one per talent category, drawn in the badge style
 *       the talent screen already uses, which is what fills out the 6×6 board.</li>
 * </ol>
 *
 * Small boards are pure skill cards; only the big ones reach into the talents.
 */
public final class MemoryDeck {
    private MemoryDeck() {}

    public enum Kind { SKILL, CHARACTER, TALENT }

    /** One card face: {@code code} is drawn on the card, {@code label} names it in full. */
    public record Face(String code, String label, int color, Kind kind) {}

    private static final List<Face> FACES = build();
    /** How many faces come from skills + the character card (the ones we use first). */
    private static final int CORE_COUNT = Skill.values().length + 1;

    private static List<Face> build() {
        List<Face> list = new ArrayList<>();
        Set<String> codes = new HashSet<>();

        for (Skill s : Skill.values()) {
            String code = abbreviate(s.display());
            codes.add(code);
            list.add(new Face(code, s.display(), s.color(), Kind.SKILL));
        }
        codes.add("CHR");
        list.add(new Face("CHR", "Character", 0xFFCE54, Kind.CHARACTER));

        // One talent per category, so no two talent cards share a colour.
        EnumSet<Talent.Category> seen = EnumSet.noneOf(Talent.Category.class);
        for (Talent t : Talent.values()) {
            if (!seen.add(t.category())) continue;
            if (!codes.add(t.code())) continue; // never two cards with the same label
            list.add(new Face(t.code(), t.display(), t.category().color(), Kind.TALENT));
        }
        return List.copyOf(list);
    }

    private static String abbreviate(String name) {
        return name.length() <= 3
            ? name.toUpperCase(java.util.Locale.ROOT)
            : name.substring(0, 3).toUpperCase(java.util.Locale.ROOT);
    }

    public static int count() { return FACES.size(); }

    public static Face byId(int id) {
        return id >= 0 && id < FACES.size() ? FACES.get(id) : null;
    }

    /**
     * Face ids in draw order: the skill/character cards first (shuffled among
     * themselves), then the talent cards (also shuffled). A board takes as many as
     * it needs from the front, so an 8-pair board is all skills and only the 18-pair
     * board dips into the talents.
     */
    public static List<Integer> pool(Random rng) {
        List<Integer> core = new ArrayList<>();
        for (int i = 0; i < CORE_COUNT && i < FACES.size(); i++) core.add(i);
        Collections.shuffle(core, rng);

        List<Integer> extra = new ArrayList<>();
        for (int i = CORE_COUNT; i < FACES.size(); i++) extra.add(i);
        Collections.shuffle(extra, rng);

        core.addAll(extra);
        return core;
    }
}
