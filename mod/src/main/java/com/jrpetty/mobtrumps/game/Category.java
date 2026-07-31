package com.jrpetty.mobtrumps.game;

/**
 * A themed group of mobs. Collecting every mob in a category pays out a
 * one-time reward that scales with the category's {@link #difficulty()}, and
 * the card art wears a background themed to the category.
 */
public enum Category {
    FARM("Farm Animals", 1, 0xFFF2C14E, "Is it a farm animal?"),
    CREATURE("Wild Creatures", 1, 0xFF7FB069, "Is it a wild creature?"),
    VILLAGE("Villagers & Golems", 1, 0xFFC49A6C, "Is it a villager or a golem?"),
    AQUATIC("Aquatic", 2, 0xFF3FA7D6, "Is it a sea creature?"),
    UNDEAD("Undead", 2, 0xFF8CA184, "Is it undead?"),
    MONSTER("Overworld Monsters", 2, 0xFF8A8A9E, "Is it an overworld monster?"),
    END("The End", 3, 0xFFB57EDC, "Is it from the End?"),
    NETHER("Nether", 3, 0xFFD65A31, "Is it from the Nether?"),
    ILLAGER("Illagers", 3, 0xFF5E8A6E, "Is it an illager?"),
    BOSS("Bosses", 4, 0xFFFFD54A, "Is it a boss?");

    private final String label;
    private final int difficulty;
    private final int accent;
    private final String question;

    Category(String label, int difficulty, int accent, String question) {
        this.label = label;
        this.difficulty = difficulty;
        this.accent = accent;
        this.question = question;
    }

    /**
     * How this category reads as a Guess Who question. Written out rather than
     * built from the label, because the labels are plural collection headings
     * — "Is it a Farm Animals?" and "Is it a The End?" are not questions.
     */
    public String question() {
        return question;
    }

    public String label() {
        return label;
    }

    /** 1 (easy) to 4 (brutal) — drives how rich the completion reward is. */
    public int difficulty() {
        return difficulty;
    }

    /** ARGB accent colour used for this category's badge and UI trim. */
    public int accent() {
        return accent;
    }
}
