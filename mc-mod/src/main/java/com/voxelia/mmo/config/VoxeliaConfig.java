package com.voxelia.mmo.config;

import com.voxelia.mmo.skill.SkillCurve;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common (server-authoritative) config: XP rate, per-level reward coefficients,
 * milestone perks, and the original six skills' active-ability tuning. The newer
 * skills (Excavation, Defense, Cooking, Alchemy, Archery) are passive.
 */
public final class VoxeliaConfig {
    private VoxeliaConfig() {}

    public static final ModConfigSpec SPEC;

    // rewards
    private static final ModConfigSpec.DoubleValue XP_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue COMBAT_DAMAGE;
    private static final ModConfigSpec.DoubleValue FARMING_HEALTH;
    private static final ModConfigSpec.DoubleValue MINING_SPEED;
    private static final ModConfigSpec.DoubleValue FORAGING_SPEED;
    private static final ModConfigSpec.DoubleValue EXCAV_SPEED;
    private static final ModConfigSpec.DoubleValue MINING_FORTUNE;
    private static final ModConfigSpec.DoubleValue FORAGING_FORTUNE;
    private static final ModConfigSpec.DoubleValue EXCAV_FORTUNE;
    private static final ModConfigSpec.DoubleValue ACRO_JUMP;
    private static final ModConfigSpec.DoubleValue FISHING_LUCK_MAX;
    private static final ModConfigSpec.DoubleValue DEF_ARMOR;
    private static final ModConfigSpec.DoubleValue DEF_TOUGH;
    private static final ModConfigSpec.DoubleValue DEF_KB;
    private static final ModConfigSpec.DoubleValue ALCH_DURATION;
    private static final ModConfigSpec.DoubleValue ARCHERY_POWER;

    // perks
    private static final ModConfigSpec.DoubleValue COMBAT_LIFESTEAL;
    private static final ModConfigSpec.DoubleValue ACRO_FALL_REDUCTION;
    private static final ModConfigSpec.DoubleValue FISHING_TREASURE_MAX;
    private static final ModConfigSpec.IntValue MINING_HASTE_LEVEL;
    private static final ModConfigSpec.IntValue TELEKINESIS_LEVEL;
    private static final ModConfigSpec.IntValue LAST_STAND_LEVEL;
    private static final ModConfigSpec.IntValue COOKING_FEAST_LEVEL;
    private static final ModConfigSpec.DoubleValue DEATH_XP_LOSS;
    private static final ModConfigSpec.IntValue MASTERY_KILLS_PER_TIER;
    private static final ModConfigSpec.IntValue MASTERY_MAX_TIER;
    private static final ModConfigSpec.DoubleValue MASTERY_DAMAGE_PER_TIER;
    private static final ModConfigSpec.DoubleValue MASTERY_LOOT_PER_TIER;
    private static final ModConfigSpec.BooleanValue SHOW_CHAT_TITLE;
    private static final ModConfigSpec.IntValue TALENT_MAX_RANK;

    // active abilities (original six)
    private static final ModConfigSpec.IntValue FRENZY_LEVEL;
    private static final ModConfigSpec.IntValue FRENZY_COOLDOWN;
    private static final ModConfigSpec.IntValue LEAP_LEVEL;
    private static final ModConfigSpec.IntValue LEAP_COOLDOWN;
    private static final ModConfigSpec.IntValue FOCUS_LEVEL;
    private static final ModConfigSpec.IntValue FOCUS_COOLDOWN;
    private static final ModConfigSpec.IntValue OVERGROWTH_LEVEL;
    private static final ModConfigSpec.IntValue OVERGROWTH_COOLDOWN;
    private static final ModConfigSpec.IntValue MEAL_LEVEL;
    private static final ModConfigSpec.IntValue MEAL_COOLDOWN;
    private static final ModConfigSpec.IntValue MAELSTROM_LEVEL;
    private static final ModConfigSpec.IntValue MAELSTROM_COOLDOWN;
    private static final ModConfigSpec.IntValue EXCAVATE_LEVEL;
    private static final ModConfigSpec.IntValue EXCAVATE_COOLDOWN;
    private static final ModConfigSpec.IntValue BULWARK_LEVEL;
    private static final ModConfigSpec.IntValue BULWARK_COOLDOWN;
    private static final ModConfigSpec.IntValue FEAST_LEVEL;
    private static final ModConfigSpec.IntValue FEAST_COOLDOWN;
    private static final ModConfigSpec.IntValue PANACEA_LEVEL;
    private static final ModConfigSpec.IntValue PANACEA_COOLDOWN;
    private static final ModConfigSpec.IntValue VOLLEY_LEVEL;
    private static final ModConfigSpec.IntValue VOLLEY_COOLDOWN;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("XP and per-level reward tuning (max level " + SkillCurve.MAX_LEVEL + "). "
            + "Per-level rates are tuned so a level-500 skill lands where the old level-100 cap did.").push("rewards");
        XP_MULTIPLIER = b.comment("Global multiplier applied to all skill XP gains.")
            .defineInRange("xpMultiplier", 1.0, 0.0, 1000.0);
        COMBAT_DAMAGE = b.comment("Combat: bonus attack damage per level.")
            .defineInRange("combatDamagePerLevel", 0.02, 0.0, 100.0);
        FARMING_HEALTH = b.comment("Farming: bonus max health (HP) per level.")
            .defineInRange("farmingHealthPerLevel", 0.04, 0.0, 100.0);
        MINING_SPEED = b.comment("Mining: block-break-speed bonus per level (fraction).")
            .defineInRange("miningSpeedPerLevel", 0.001, 0.0, 10.0);
        FORAGING_SPEED = b.comment("Foraging: block-break-speed bonus per level (fraction).")
            .defineInRange("foragingSpeedPerLevel", 0.001, 0.0, 10.0);
        EXCAV_SPEED = b.comment("Excavation: block-break-speed bonus per level (fraction).")
            .defineInRange("excavationSpeedPerLevel", 0.001, 0.0, 10.0);
        MINING_FORTUNE = b.comment("Mining: Fortune factor per level on ores (no Silk Touch).")
            .defineInRange("miningFortunePerLevel", 0.002, 0.0, 10.0);
        FORAGING_FORTUNE = b.comment("Foraging: Fortune factor per level on logs/leaves (no Silk Touch).")
            .defineInRange("foragingFortunePerLevel", 0.002, 0.0, 10.0);
        EXCAV_FORTUNE = b.comment("Excavation: Fortune factor per level on shovel blocks (no Silk Touch).")
            .defineInRange("excavationFortunePerLevel", 0.002, 0.0, 10.0);
        ACRO_JUMP = b.comment("Acrobatics: bonus jump strength per level (0.00012 => about +14% jump strength at level 500, base 0.42).")
            .defineInRange("acrobaticsJumpPerLevel", 0.00012, 0.0, 0.02);
        FISHING_LUCK_MAX = b.comment("Fishing: max bonus luck (while fishing) at max level.")
            .defineInRange("fishingLuckMax", 4.0, 0.0, 100.0);
        DEF_ARMOR = b.comment("Defense: bonus armor per level.")
            .defineInRange("defenseArmorPerLevel", 0.024, 0.0, 100.0);
        DEF_TOUGH = b.comment("Defense: bonus armor toughness per level.")
            .defineInRange("defenseToughnessPerLevel", 0.016, 0.0, 100.0);
        DEF_KB = b.comment("Defense: bonus knockback resistance per level.")
            .defineInRange("defenseKnockbackResistPerLevel", 0.0006, 0.0, 0.01);
        ALCH_DURATION = b.comment("Alchemy: beneficial-effect duration extension per level on finishing a potion (0.001 => +50% at 500).")
            .defineInRange("alchemyDurationPerLevel", 0.001, 0.0, 1.0);
        ARCHERY_POWER = b.comment("Archery: Power Shot bonus damage per level on fully-drawn (critical) arrows (0.012 => +6 at 500).")
            .defineInRange("archeryPowerShotPerLevel", 0.012, 0.0, 10.0);
        b.pop();

        b.comment("Milestone & conditional perks.").push("perks");
        COMBAT_LIFESTEAL = b.comment("Combat: health healed on a kill, per level.")
            .defineInRange("combatLifeStealPerLevel", 0.01, 0.0, 10.0);
        ACRO_FALL_REDUCTION = b.comment("Acrobatics: fall damage reduced per level.")
            .defineInRange("acrobaticsFallReductionPerLevel", 0.0018, 0.0, 0.01);
        FISHING_TREASURE_MAX = b.comment("Fishing: max treasure-bonus chance per catch at max level.")
            .defineInRange("fishingTreasureChanceMax", 0.5, 0.0, 1.0);
        MINING_HASTE_LEVEL = b.comment("Mining: level at which holding a pickaxe grants Haste (0 disables).")
            .defineInRange("miningHasteLevel", 25, 0, SkillCurve.MAX_LEVEL);
        TELEKINESIS_LEVEL = b.comment("Mining: level at which mined drops go to your inventory (0 disables).")
            .defineInRange("telekinesisLevel", 100, 0, SkillCurve.MAX_LEVEL);
        LAST_STAND_LEVEL = b.comment("Defense: level that unlocks Last Stand — Resistance while below 35% health (0 disables).")
            .defineInRange("lastStandLevel", 20, 0, SkillCurve.MAX_LEVEL);
        COOKING_FEAST_LEVEL = b.comment("Cooking: level that unlocks Well Fed — a short regeneration after eating (0 disables).")
            .defineInRange("cookingWellFedLevel", 20, 0, SkillCurve.MAX_LEVEL);
        DEATH_XP_LOSS = b.comment("Fraction of EVERY skill's XP lost on death (0.0 = keep everything, 0.2 = lose 20%, can drop levels).")
            .defineInRange("deathXpLossPercent", 0.20, 0.0, 1.0);
        b.pop();

        b.comment("Mob Mastery / Bestiary — repeatedly killing a mob type grants a bonus vs that type.").push("mastery");
        MASTERY_KILLS_PER_TIER = b.comment("Kills of a mob type needed per mastery tier.")
            .defineInRange("killsPerTier", 50, 1, 100000);
        MASTERY_MAX_TIER = b.comment("Maximum mastery tier per mob type.")
            .defineInRange("maxTier", 10, 0, 1000);
        MASTERY_DAMAGE_PER_TIER = b.comment("Bonus damage vs a mastered type per tier (0.02 = +2%/tier).")
            .defineInRange("damagePerTier", 0.02, 0.0, 10.0);
        MASTERY_LOOT_PER_TIER = b.comment("Chance per tier to double a drop from a mastered type (0.03 = +3%/tier).")
            .defineInRange("lootChancePerTier", 0.03, 0.0, 1.0);
        b.pop();

        b.comment("Social / display.").push("social");
        SHOW_CHAT_TITLE = b.comment("Prefix chat with the player's level + title, e.g. [Lv 42 • Master Miner].")
            .define("showChatTitle", true);
        b.pop();

        b.comment("Talent tree — each skill has its own talents; spend points earned from that skill's levels. "
            + "Per-rank magnitudes are defined per talent in code (see the Talent enum).").push("talents");
        TALENT_MAX_RANK = b.comment("Maximum rank per talent.")
            .defineInRange("maxRank", 5, 1, 100);
        b.pop();

        b.comment("Active keybind abilities (cooldown-balanced). The newer skills' 'ultimates' are "
            + "powerful but sit on long cooldowns.").push("abilities");
        FRENZY_LEVEL = b.comment("Combat: Frenzy unlock level (0 disables).").defineInRange("frenzyLevel", 20, 0, SkillCurve.MAX_LEVEL);
        FRENZY_COOLDOWN = b.comment("Frenzy cooldown (seconds).").defineInRange("frenzyCooldownSeconds", 50, 1, 3600);
        LEAP_LEVEL = b.comment("Acrobatics: Leap unlock level (0 disables).").defineInRange("leapLevel", 15, 0, SkillCurve.MAX_LEVEL);
        LEAP_COOLDOWN = b.comment("Leap cooldown (seconds).").defineInRange("leapCooldownSeconds", 6, 1, 3600);
        FOCUS_LEVEL = b.comment("Mining: Miner's Focus unlock level (0 disables).").defineInRange("minersFocusLevel", 20, 0, SkillCurve.MAX_LEVEL);
        FOCUS_COOLDOWN = b.comment("Miner's Focus cooldown (seconds).").defineInRange("minersFocusCooldownSeconds", 60, 1, 3600);
        OVERGROWTH_LEVEL = b.comment("Foraging: Overgrowth unlock level (0 disables).").defineInRange("overgrowthLevel", 25, 0, SkillCurve.MAX_LEVEL);
        OVERGROWTH_COOLDOWN = b.comment("Overgrowth cooldown (seconds).").defineInRange("overgrowthCooldownSeconds", 45, 1, 3600);
        MEAL_LEVEL = b.comment("Farming: Hearty Meal unlock level (0 disables).").defineInRange("heartyMealLevel", 20, 0, SkillCurve.MAX_LEVEL);
        MEAL_COOLDOWN = b.comment("Hearty Meal cooldown (seconds).").defineInRange("heartyMealCooldownSeconds", 60, 1, 3600);
        MAELSTROM_LEVEL = b.comment("Fishing: Maelstrom unlock level (0 disables).").defineInRange("maelstromLevel", 15, 0, SkillCurve.MAX_LEVEL);
        MAELSTROM_COOLDOWN = b.comment("Maelstrom cooldown (seconds).").defineInRange("maelstromCooldownSeconds", 90, 1, 3600);
        EXCAVATE_LEVEL = b.comment("Excavation: Excavate (mass-dig) unlock level (0 disables).").defineInRange("excavateLevel", 30, 0, SkillCurve.MAX_LEVEL);
        EXCAVATE_COOLDOWN = b.comment("Excavate cooldown (seconds).").defineInRange("excavateCooldownSeconds", 180, 1, 3600);
        BULWARK_LEVEL = b.comment("Defense: Bulwark (deflect) unlock level (0 disables).").defineInRange("bulwarkLevel", 30, 0, SkillCurve.MAX_LEVEL);
        BULWARK_COOLDOWN = b.comment("Bulwark cooldown (seconds) — a powerful 5s deflect, so a long cooldown.").defineInRange("bulwarkCooldownSeconds", 300, 1, 3600);
        FEAST_LEVEL = b.comment("Cooking: Feast (full heal) unlock level (0 disables).").defineInRange("feastLevel", 30, 0, SkillCurve.MAX_LEVEL);
        FEAST_COOLDOWN = b.comment("Feast cooldown (seconds) — a full heal, so a long 10-minute cooldown.").defineInRange("feastCooldownSeconds", 600, 1, 3600);
        PANACEA_LEVEL = b.comment("Alchemy: Panacea (cleanse + ward) unlock level (0 disables).").defineInRange("panaceaLevel", 30, 0, SkillCurve.MAX_LEVEL);
        PANACEA_COOLDOWN = b.comment("Panacea cooldown (seconds).").defineInRange("panaceaCooldownSeconds", 180, 1, 3600);
        VOLLEY_LEVEL = b.comment("Archery: Volley (arrow fan) unlock level (0 disables).").defineInRange("volleyLevel", 30, 0, SkillCurve.MAX_LEVEL);
        VOLLEY_COOLDOWN = b.comment("Volley cooldown (seconds).").defineInRange("volleyCooldownSeconds", 150, 1, 3600);
        b.pop();

        SPEC = b.build();
    }

    public static double xpMultiplier()        { return XP_MULTIPLIER.get(); }
    public static double combatDamagePerLevel(){ return COMBAT_DAMAGE.get(); }
    public static double farmingHealthPerLevel(){ return FARMING_HEALTH.get(); }
    public static double miningSpeedPerLevel() { return MINING_SPEED.get(); }
    public static double foragingSpeedPerLevel(){ return FORAGING_SPEED.get(); }
    public static double excavationSpeedPerLevel(){ return EXCAV_SPEED.get(); }
    public static double miningFortunePerLevel(){ return MINING_FORTUNE.get(); }
    public static double foragingFortunePerLevel(){ return FORAGING_FORTUNE.get(); }
    public static double excavationFortunePerLevel(){ return EXCAV_FORTUNE.get(); }
    public static double acrobaticsJumpPerLevel(){ return ACRO_JUMP.get(); }
    public static double fishingLuckMax()      { return FISHING_LUCK_MAX.get(); }
    public static double defenseArmorPerLevel(){ return DEF_ARMOR.get(); }
    public static double defenseToughnessPerLevel(){ return DEF_TOUGH.get(); }
    public static double defenseKnockbackResistPerLevel(){ return DEF_KB.get(); }
    public static double alchemyDurationPerLevel(){ return ALCH_DURATION.get(); }
    public static double archeryPowerShotPerLevel(){ return ARCHERY_POWER.get(); }

    public static double combatLifeStealPerLevel() { return COMBAT_LIFESTEAL.get(); }
    public static double acrobaticsFallReductionPerLevel() { return ACRO_FALL_REDUCTION.get(); }
    public static double fishingTreasureChanceMax() { return FISHING_TREASURE_MAX.get(); }
    public static int miningHasteLevel()       { return MINING_HASTE_LEVEL.get(); }
    public static int telekinesisLevel()       { return TELEKINESIS_LEVEL.get(); }
    public static int lastStandLevel()         { return LAST_STAND_LEVEL.get(); }
    public static int cookingWellFedLevel()    { return COOKING_FEAST_LEVEL.get(); }
    public static double deathXpLossPercent()  { return DEATH_XP_LOSS.get(); }
    public static int masteryKillsPerTier()    { return MASTERY_KILLS_PER_TIER.get(); }
    public static int masteryMaxTier()         { return MASTERY_MAX_TIER.get(); }
    public static double masteryDamagePerTier(){ return MASTERY_DAMAGE_PER_TIER.get(); }
    public static double masteryLootChancePerTier(){ return MASTERY_LOOT_PER_TIER.get(); }
    public static boolean showChatTitle()      { return SHOW_CHAT_TITLE.get(); }
    public static int talentMaxRank()          { return TALENT_MAX_RANK.get(); }

    public static int frenzyLevel()            { return FRENZY_LEVEL.get(); }
    public static int frenzyCooldownSeconds()  { return FRENZY_COOLDOWN.get(); }
    public static int leapLevel()              { return LEAP_LEVEL.get(); }
    public static int leapCooldownSeconds()    { return LEAP_COOLDOWN.get(); }
    public static int minersFocusLevel()       { return FOCUS_LEVEL.get(); }
    public static int minersFocusCooldownSeconds() { return FOCUS_COOLDOWN.get(); }
    public static int overgrowthLevel()        { return OVERGROWTH_LEVEL.get(); }
    public static int overgrowthCooldownSeconds() { return OVERGROWTH_COOLDOWN.get(); }
    public static int heartyMealLevel()        { return MEAL_LEVEL.get(); }
    public static int heartyMealCooldownSeconds() { return MEAL_COOLDOWN.get(); }
    public static int maelstromLevel()         { return MAELSTROM_LEVEL.get(); }
    public static int maelstromCooldownSeconds(){ return MAELSTROM_COOLDOWN.get(); }
    public static int excavateLevel()          { return EXCAVATE_LEVEL.get(); }
    public static int excavateCooldownSeconds(){ return EXCAVATE_COOLDOWN.get(); }
    public static int bulwarkLevel()           { return BULWARK_LEVEL.get(); }
    public static int bulwarkCooldownSeconds() { return BULWARK_COOLDOWN.get(); }
    public static int feastLevel()             { return FEAST_LEVEL.get(); }
    public static int feastCooldownSeconds()   { return FEAST_COOLDOWN.get(); }
    public static int panaceaLevel()           { return PANACEA_LEVEL.get(); }
    public static int panaceaCooldownSeconds() { return PANACEA_COOLDOWN.get(); }
    public static int volleyLevel()            { return VOLLEY_LEVEL.get(); }
    public static int volleyCooldownSeconds()  { return VOLLEY_COOLDOWN.get(); }
}
