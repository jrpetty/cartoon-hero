package com.jrpetty.mobtrumps.game;

/**
 * A themed group of mobs. Collecting every mob in a category pays out a
 * one-time reward that scales with the category's {@link #difficulty()}, and
 * the card art wears a background themed to the category.
 */
public enum Category {
    FARM("Farm Animals", 1, 0xFFF2C14E),
    CREATURE("Wild Creatures", 1, 0xFF7FB069),
    VILLAGE("Villagers & Golems", 1, 0xFFC49A6C),
    AQUATIC("Aquatic", 2, 0xFF3FA7D6),
    UNDEAD("Undead", 2, 0xFF8CA184),
    MONSTER("Overworld Monsters", 2, 0xFF8A8A9E),
    END("The End", 3, 0xFFB57EDC),
    NETHER("Nether", 3, 0xFFD65A31),
    ILLAGER("Illagers", 3, 0xFF5E8A6E),
    BOSS("Bosses", 4, 0xFFFFD54A);

    private final String label;
    private final int difficulty;
    private final int accent;

    Category(String label, int difficulty, int accent) {
        this.label = label;
        this.difficulty = difficulty;
        this.accent = accent;
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
