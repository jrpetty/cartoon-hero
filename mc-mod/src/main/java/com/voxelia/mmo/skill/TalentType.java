package com.voxelia.mmo.skill;

/** The talent branches available on every skill. */
public enum TalentType {
    PRODIGY("prodigy", "Prodigy", "More XP gained in this skill"),
    MASTERY("mastery", "Mastery", "Strengthens this skill's main bonus"),
    VITALITY("vitality", "Vitality", "Bonus max health");

    private final String id;
    private final String display;
    private final String desc;

    TalentType(String id, String display, String desc) {
        this.id = id;
        this.display = display;
        this.desc = desc;
    }

    public String id() { return id; }
    public String display() { return display; }
    public String desc() { return desc; }

    public static TalentType byId(String s) {
        for (TalentType t : values()) if (t.id.equalsIgnoreCase(s)) return t;
        return null;
    }
}
