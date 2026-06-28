package com.voxelia.mmo.skill;

/** The talent branches available on every skill. */
public enum TalentType {
    PRODIGY("prodigy", "Prodigy", "XP", "More XP gained in this skill"),
    MASTERY("mastery", "Mastery", "PWR", "Strengthens this skill's main bonus"),
    VITALITY("vitality", "Vitality", "HP", "Bonus max health"),
    SWIFTNESS("swiftness", "Swiftness", "SPD", "Bonus movement speed"),
    TOUGHNESS("toughness", "Toughness", "ARM", "Bonus armor"),
    FORTUNE("fortune", "Fortune", "LCK", "Bonus luck (better loot rolls)");

    private final String id;
    private final String display;
    private final String code;
    private final String desc;

    TalentType(String id, String display, String code, String desc) {
        this.id = id;
        this.display = display;
        this.code = code;
        this.desc = desc;
    }

    public String id() { return id; }
    public String display() { return display; }
    public String code() { return code; }
    public String desc() { return desc; }

    public static TalentType byId(String s) {
        for (TalentType t : values()) if (t.id.equalsIgnoreCase(s)) return t;
        return null;
    }
}
