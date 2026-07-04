package com.voxelia.mmo.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Skill-specific talents. Every skill owns its own 3-4 talents that amplify that
 * skill's real mechanics and add flavourful extras (reach, footspeed, vitality,
 * attack speed, luck) — spend points earned from that skill's levels to rank them up.
 *
 * <p>All display text and per-rank magnitudes live here in the shared enum, so the
 * client can render exact "point upgrade" tooltips without the server syncing any
 * numbers — only the player's ranks are sent.
 */
public enum Talent {
    // ── Mining ───────────────────────────────────────────────────────────────
    MINING_EFFICIENCY (Skill.MINING,   "Efficiency",   "SPD", Category.SIGNATURE, 0.05, "mining break speed",
        "Mine ores and stone faster."),
    MINING_PROSPECTOR (Skill.MINING,   "Prospector",   "FTN", Category.FORTUNE,   0.06, "ore Fortune",
        "Better chance at extra drops from ores."),
    MINING_REACH      (Skill.MINING,   "Deepreach",    "RCH", Category.REACH,     0.15, "reach",
        "Break blocks from farther away."),
    MINING_PRODIGY    (Skill.MINING,   "Prodigy",      "XP",  Category.XP,        0.08, "Mining XP",
        "Level Mining faster."),

    // ── Foraging ─────────────────────────────────────────────────────────────
    FORAGING_SAWMILL  (Skill.FORAGING, "Sawmill",      "SPD", Category.SIGNATURE, 0.05, "foraging break speed",
        "Chop wood and clear leaves faster."),
    FORAGING_BOUNTIFUL(Skill.FORAGING, "Bountiful",    "FTN", Category.FORTUNE,   0.06, "log & leaf Fortune",
        "Better chance at extra drops from wood."),
    FORAGING_TRAILBLAZER(Skill.FORAGING, "Trailblazer","MOV", Category.SPEED,     0.02, "movement speed",
        "Move through the wilds more quickly."),
    FORAGING_PRODIGY  (Skill.FORAGING, "Prodigy",      "XP",  Category.XP,        0.08, "Foraging XP",
        "Level Foraging faster."),

    // ── Excavation ───────────────────────────────────────────────────────────
    EXCAVATION_LANDSCAPER(Skill.EXCAVATION, "Landscaper", "SPD", Category.SIGNATURE, 0.05, "dig speed",
        "Dig dirt, sand and gravel faster."),
    EXCAVATION_SIFTER (Skill.EXCAVATION, "Sifter",      "FTN", Category.FORTUNE,   0.06, "shovel-block Fortune",
        "Better chance at extra drops from shovel blocks."),
    EXCAVATION_REACH  (Skill.EXCAVATION, "Earthmover",  "RCH", Category.REACH,     0.15, "reach",
        "Dig blocks from farther away."),
    EXCAVATION_PRODIGY(Skill.EXCAVATION, "Prodigy",     "XP",  Category.XP,        0.08, "Excavation XP",
        "Level Excavation faster."),

    // ── Combat ───────────────────────────────────────────────────────────────
    COMBAT_BRUTALITY  (Skill.COMBAT,   "Brutality",    "PWR", Category.SIGNATURE, 0.05, "bonus attack damage",
        "Hit harder in melee."),
    COMBAT_BLOODTHIRST(Skill.COMBAT,   "Bloodthirst",  "LIF", Category.LIFESTEAL, 0.10, "life steal on kill",
        "Heal more from each kill."),
    COMBAT_SWIFTBLADE (Skill.COMBAT,   "Swiftblade",   "AS",  Category.ATTACK_SPEED, 0.05, "attack speed",
        "Swing your weapon faster."),
    COMBAT_PRODIGY    (Skill.COMBAT,   "Prodigy",      "XP",  Category.XP,        0.08, "Combat XP",
        "Level Combat faster."),

    // ── Acrobatics ───────────────────────────────────────────────────────────
    ACROBATICS_EVASION(Skill.ACROBATICS, "Evasion",    "DGE", Category.SIGNATURE, 0.05, "dodge chance",
        "Dodge attacks more often."),
    ACROBATICS_FEATHERFALL(Skill.ACROBATICS, "Featherfall", "FTH", Category.FALL, 0.06, "fall damage reduction",
        "Take less fall damage."),
    ACROBATICS_FLEETFOOT(Skill.ACROBATICS, "Fleetfoot","MOV", Category.SPEED,     0.02, "movement speed",
        "Dash around on quick feet."),
    ACROBATICS_PRODIGY(Skill.ACROBATICS, "Prodigy",    "XP",  Category.XP,        0.08, "Acrobatics XP",
        "Level Acrobatics faster."),

    // ── Fishing ──────────────────────────────────────────────────────────────
    FISHING_LUCK      (Skill.FISHING,  "Angler's Luck","LCK", Category.SIGNATURE, 0.06, "fishing luck",
        "Reel in better loot while fishing."),
    FISHING_TREASURE  (Skill.FISHING,  "Treasure Hunter","TRS", Category.TREASURE, 0.08, "treasure-catch chance",
        "More bonus treasure catches."),
    FISHING_FORTUNE   (Skill.FISHING,  "Sea Fortune",  "LUK", Category.LUCK,      0.30, "luck",
        "Improves loot rolls everywhere, not just fishing."),
    FISHING_PRODIGY   (Skill.FISHING,  "Prodigy",      "XP",  Category.XP,        0.08, "Fishing XP",
        "Level Fishing faster."),

    // ── Farming ──────────────────────────────────────────────────────────────
    FARMING_HEARTINESS(Skill.FARMING,  "Heartiness",   "HP",  Category.SIGNATURE, 0.05, "bonus max health",
        "Farming grants even more max health."),
    FARMING_VITALITY  (Skill.FARMING,  "Vitality",     "VIT", Category.HEALTH,    1.00, "max health",
        "A flat boost to your maximum health."),
    FARMING_REACH     (Skill.FARMING,  "Long Hoe",     "RCH", Category.REACH,     0.15, "reach",
        "Till and harvest from farther away."),
    FARMING_PRODIGY   (Skill.FARMING,  "Prodigy",      "XP",  Category.XP,        0.08, "Farming XP",
        "Level Farming faster."),

    // ── Defense ──────────────────────────────────────────────────────────────
    DEFENSE_IRONCLAD  (Skill.DEFENSE,  "Ironclad",     "ARM", Category.SIGNATURE, 0.05, "armor, toughness & knockback resist",
        "All of Defense's protective bonuses grow."),
    DEFENSE_FORTITUDE (Skill.DEFENSE,  "Fortitude",    "VIT", Category.HEALTH,    1.00, "max health",
        "A flat boost to your maximum health."),
    DEFENSE_PRODIGY   (Skill.DEFENSE,  "Prodigy",      "XP",  Category.XP,        0.08, "Defense XP",
        "Level Defense faster."),

    // ── Cooking ──────────────────────────────────────────────────────────────
    COOKING_GOURMET   (Skill.COOKING,  "Gourmet",      "REG", Category.SIGNATURE, 0.06, "Well Fed regeneration",
        "Well Fed regenerates you for longer."),
    COOKING_WELLNESS  (Skill.COOKING,  "Wellness",     "VIT", Category.HEALTH,    1.00, "max health",
        "A flat boost to your maximum health."),
    COOKING_PRODIGY   (Skill.COOKING,  "Prodigy",      "XP",  Category.XP,        0.08, "Cooking XP",
        "Level Cooking faster."),

    // ── Alchemy ──────────────────────────────────────────────────────────────
    ALCHEMY_POTENT    (Skill.ALCHEMY,  "Potent Brews", "DUR", Category.SIGNATURE, 0.06, "potion duration",
        "Your beneficial potions last longer."),
    ALCHEMY_SERENDIPITY(Skill.ALCHEMY, "Serendipity",  "LUK", Category.LUCK,      0.30, "luck",
        "Improves loot rolls from chests and mobs."),
    ALCHEMY_PRODIGY   (Skill.ALCHEMY,  "Prodigy",      "XP",  Category.XP,        0.08, "Alchemy XP",
        "Level Alchemy faster."),

    // ── Archery ──────────────────────────────────────────────────────────────
    ARCHERY_MARKSMANSHIP(Skill.ARCHERY, "Marksmanship","PWR", Category.SIGNATURE, 0.06, "Power Shot damage",
        "Fully-drawn arrows hit even harder."),
    ARCHERY_NIMBLE    (Skill.ARCHERY,  "Nimble",       "MOV", Category.SPEED,     0.02, "movement speed",
        "Keep your distance on lighter feet."),
    ARCHERY_PRODIGY   (Skill.ARCHERY,  "Prodigy",      "XP",  Category.XP,        0.08, "Archery XP",
        "Level Archery faster.");

    /**
     * What a talent does — drives which multiplier or attribute the game reads it
     * through. {@code percent} talents display as "+X%"; the rest add a flat amount.
     */
    public enum Category {
        SIGNATURE(true), FORTUNE(true), XP(true), LIFESTEAL(true), FALL(true), TREASURE(true),
        SPEED(true), ATTACK_SPEED(true),
        HEALTH(false), REACH(false), LUCK(false);

        private final boolean percent;
        Category(boolean percent) { this.percent = percent; }
        public boolean isPercent() { return percent; }
    }

    private final Skill skill;
    private final String display;
    private final String code;
    private final Category category;
    private final double perRank;
    private final String noun;
    private final String blurb;

    Talent(Skill skill, String display, String code, Category category,
           double perRank, String noun, String blurb) {
        this.skill = skill;
        this.display = display;
        this.code = code;
        this.category = category;
        this.perRank = perRank;
        this.noun = noun;
        this.blurb = blurb;
    }

    public Skill skill()        { return skill; }
    public String display()     { return display; }
    public String code()        { return code; }
    public Category category()  { return category; }
    public double perRank()     { return perRank; }
    public String noun()        { return noun; }
    public String blurb()       { return blurb; }

    /** Stable persistence id, e.g. "mining_efficiency". */
    public String id() { return name().toLowerCase(Locale.ROOT); }

    /** The talent's multiplier contribution at a given rank (1.0 at rank 0). Percent talents only. */
    public double multiplierAt(int rank) { return 1.0 + rank * perRank; }

    /** The talent's flat contribution at a given rank. Flat (attribute) talents only. */
    public double flatAt(int rank) { return rank * perRank; }

    /** e.g. "+15% ore Fortune" or "+3 max health" at the given rank. */
    public String bonusText(int rank) {
        return category.percent
            ? String.format(Locale.ROOT, "+%s%% %s", trim(perRank * rank * 100), noun)
            : String.format(Locale.ROOT, "+%s %s", trim(perRank * rank), noun);
    }

    /** e.g. "+5% per rank" or "+1 per rank" */
    public String perRankText() {
        return category.percent
            ? String.format(Locale.ROOT, "+%s%% per rank", trim(perRank * 100))
            : String.format(Locale.ROOT, "+%s per rank", trim(perRank));
    }

    private static String trim(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static final List<Talent>[] BY_SKILL = buildBySkill();

    @SuppressWarnings("unchecked")
    private static List<Talent>[] buildBySkill() {
        List<Talent>[] lists = new List[Skill.values().length];
        for (int i = 0; i < lists.length; i++) lists[i] = new ArrayList<>();
        for (Talent t : values()) lists[t.skill.ordinal()].add(t);
        return lists;
    }

    /** The talents belonging to a skill, in declaration order. */
    public static List<Talent> forSkill(Skill skill) {
        return BY_SKILL[skill.ordinal()];
    }

    /** The skill's talent in a category, or null if it has none. */
    public static Talent of(Skill skill, Category category) {
        for (Talent t : BY_SKILL[skill.ordinal()]) {
            if (t.category == category) return t;
        }
        return null;
    }

    public static Talent byId(String id) {
        if (id == null) return null;
        for (Talent t : values()) {
            if (t.id().equalsIgnoreCase(id)) return t;
        }
        return null;
    }
}
