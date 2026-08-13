package com.voxelia.mmo.game;

/**
 * The card faces for {@link MemoryGame}. The first eleven mirror the skills (same
 * colours you already read on the HUD); the rest are material cards that fill out
 * the big 6×6 board, which needs 18 distinct pairs. Every face has a short code and
 * a colour far enough from its neighbours to tell apart at a glance.
 */
public enum MemoryFace {
    MINING("MIN", 0xBDBDBD),
    FORAGING("FOR", 0x81C784),
    COMBAT("CMB", 0xEF5350),
    FARMING("FRM", 0xFFD54F),
    ACROBATICS("ACR", 0x4FC3F7),
    FISHING("FSH", 0x4DD0E1),
    EXCAVATION("EXC", 0xC8A064),
    DEFENSE("DEF", 0x90A4AE),
    COOKING("COO", 0xFF8A65),
    ALCHEMY("ALC", 0xBA68C8),
    ARCHERY("ARC", 0x8D6E63),
    LAPIS("LAP", 0x3F6FE0),
    ROSE("ROS", 0xF06292),
    LIME("LIM", 0xA8E063),
    AMBER("AMB", 0xFFB300),
    TEAL("TEA", 0x26A69A),
    WINE("WIN", 0xA83E52),
    FROST("FRO", 0xECEFF1);

    private final String code;
    private final int color;

    MemoryFace(String code, int color) {
        this.code = code;
        this.color = color;
    }

    /** Three-letter label drawn on the card. */
    public String code() { return code; }

    /** RGB (no alpha) body colour of the card. */
    public int color() { return color; }

    public static MemoryFace byId(int id) {
        MemoryFace[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }
}
