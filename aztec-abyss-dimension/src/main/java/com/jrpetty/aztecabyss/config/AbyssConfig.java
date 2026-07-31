package com.jrpetty.aztecabyss.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * All server-side tunables for the Abyss, exposed in
 * {@code config/aztecabyss-common.toml}. Everything that governs pacing,
 * difficulty, rewards and the run economy lives here so it can be balanced
 * without touching code.
 *
 * (Follows the same {@link ModConfigSpec} pattern as a known-good NeoForge
 * 1.21.1 build, so the field/spec wiring compiles against 21.1.x.)
 */
public final class AbyssConfig {

    public static final ModConfigSpec SPEC;

    // --- Rounds / difficulty ---
    public static final ModConfigSpec.IntValue MAX_ROUND;
    public static final ModConfigSpec.IntValue BASE_ZOMBIES;
    public static final ModConfigSpec.IntValue ZOMBIES_PER_ROUND;
    public static final ModConfigSpec.DoubleValue ROUND_SIZE_MULTIPLIER;
    public static final ModConfigSpec.IntValue MAX_CONCURRENT_ALIVE;
    public static final ModConfigSpec.DoubleValue HEALTH_SCALE_PER_ROUND;
    public static final ModConfigSpec.DoubleValue DAMAGE_SCALE_PER_ROUND;
    public static final ModConfigSpec.IntValue FIRST_ROUND_DELAY_TICKS;
    public static final ModConfigSpec.IntValue BETWEEN_ROUND_TICKS;

    // --- Cooldown / entry ---
    public static final ModConfigSpec.LongValue REENTRY_COOLDOWN_HOURS;
    public static final ModConfigSpec.BooleanValue REQUIRE_ENTRY_CONFIRMATION;

    // --- Co-op / downed ---
    public static final ModConfigSpec.DoubleValue PER_PLAYER_SCALING;
    public static final ModConfigSpec.IntValue BLEEDOUT_TICKS;
    public static final ModConfigSpec.IntValue REVIVE_TICKS;
    public static final ModConfigSpec.DoubleValue REVIVE_RADIUS;

    // --- Loadout ---
    public static final ModConfigSpec.BooleanValue GIVE_STARTING_LOADOUT;

    // --- Extraction gambit ---
    public static final ModConfigSpec.BooleanValue ENABLE_EXTRACTION;
    public static final ModConfigSpec.IntValue EXTRACTION_CHANNEL_TICKS;

    // --- Easter egg ---
    public static final ModConfigSpec.BooleanValue ENABLE_RITUAL;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("rounds");
        MAX_ROUND = b.comment("Final round. Clearing it triggers the grand-prize reward.")
                .defineInRange("maxRound", 20, 1, 200);
        BASE_ZOMBIES = b.comment("Zombies in round 1.")
                .defineInRange("baseZombies", 6, 1, 200);
        ZOMBIES_PER_ROUND = b.comment("Additional zombies added each round.")
                .defineInRange("zombiesPerRound", 4, 0, 200);
        ROUND_SIZE_MULTIPLIER = b.comment("Global multiplier on each round's total zombie count (1.6 = +60%).")
                .defineInRange("roundSizeMultiplier", 1.6, 0.1, 10.0);
        MAX_CONCURRENT_ALIVE = b.comment("Hard ceiling on wave zombies alive at once across the whole arena.")
                .defineInRange("maxConcurrentAlive", 120, 1, 500);
        HEALTH_SCALE_PER_ROUND = b.comment("Fractional zombie max-health increase per round (0.18 = +18%/round).")
                .defineInRange("healthScalePerRound", 0.18, 0.0, 5.0);
        DAMAGE_SCALE_PER_ROUND = b.comment("Fractional zombie damage increase per round.")
                .defineInRange("damageScalePerRound", 0.14, 0.0, 5.0);
        FIRST_ROUND_DELAY_TICKS = b.comment("Ticks after arrival before round 1 begins (20 ticks = 1s).")
                .defineInRange("firstRoundDelayTicks", 100, 0, 2400);
        BETWEEN_ROUND_TICKS = b.comment("Breather between rounds, in ticks (200 = 10s).")
                .defineInRange("betweenRoundTicks", 200, 0, 2400);
        b.pop();

        b.push("entry");
        REENTRY_COOLDOWN_HOURS = b.comment("Real-world hours a player is locked out after dying in the Abyss.")
                .defineInRange("reentryCooldownHours", 20L, 0L, 720L);
        REQUIRE_ENTRY_CONFIRMATION = b.comment("If true, a player must confirm before being pulled into a run.")
                .define("requireEntryConfirmation", true);
        b.pop();

        b.push("coop");
        PER_PLAYER_SCALING = b.comment(
                "Wave-size multiplier applied per extra player in the lobby (compounding).",
                "1.6 = each additional player scales the wave by +60%. Solo is unaffected.",
                "Total wave = perRoundCount * thisValue^(players-1).")
                .defineInRange("perPlayerScaling", 1.6, 1.0, 5.0);
        BLEEDOUT_TICKS = b.comment("How long a downed player bleeds out before dying for good.")
                .defineInRange("bleedoutTicks", 600, 40, 6000);
        REVIVE_TICKS = b.comment("How long a teammate must channel to revive a downed player.")
                .defineInRange("reviveTicks", 100, 10, 2000);
        REVIVE_RADIUS = b.comment("How close a teammate must stand to revive.")
                .defineInRange("reviveRadius", 2.5, 1.0, 8.0);
        b.pop();

        b.push("loadout");
        GIVE_STARTING_LOADOUT = b.comment("Give players a starting kit when they enter the Abyss.")
                .define("giveStartingLoadout", true);
        b.pop();

        b.push("extraction");
        ENABLE_EXTRACTION = b.comment("Show the extraction glyph between rounds so players can bank rewards and leave safely.")
                .define("enableExtraction", true);
        EXTRACTION_CHANNEL_TICKS = b.comment("How long a player must stand on the glyph to extract (20 ticks = 1s).")
                .defineInRange("extractionChannelTicks", 40, 5, 600);
        b.pop();

        b.push("maze");
        GRIEVERS_ENABLED = b.comment("Grievers hunt the maze after dark.")
                .define("grieversEnabled", true);
        GRIEVER_BASE_CAP = b.comment("Grievers per runner in week one.")
                .defineInRange("grieverBaseCapPerPlayer", 2, 0, 20);
        GRIEVER_MAX_CAP = b.comment("Ceiling on Grievers per runner; the cap grows by one each week.")
                .defineInRange("grieverMaxCapPerPlayer", 7, 0, 40);
        GRIEVER_HEALTH = b.comment("Griever max health (a vanilla spider is 16).")
                .defineInRange("grieverHealth", 60.0, 1.0, 1024.0);
        GRIEVER_SPEED = b.comment("Griever movement speed (a vanilla spider is 0.3; below ~0.25 a runner outruns one).")
                .defineInRange("grieverSpeed", 0.33, 0.05, 2.0);
        GRIEVER_DAMAGE = b.comment("Griever attack damage.")
                .defineInRange("grieverAttackDamage", 7.0, 0.0, 100.0);
        MAZE_DEATH_LOCKOUT_SECONDS = b.comment("Seconds you are locked out of the maze after dying in it. 0 lets you walk straight back in.")
                .defineInRange("mazeDeathLockoutSeconds", 60, 0, 3600);
        MAZE_SHOW_BRIEFING = b.comment("Show newcomers the one-time maze rules message.")
                .define("mazeShowBriefing", true);
        b.pop();

        b.push("easteregg");
        ENABLE_RITUAL = b.comment("Enable the hidden brazier ritual and its secret reward.")
                .define("enableRitual", true);
        b.pop();

        SPEC = b.build();
    }

    private AbyssConfig() {
    }

    public static final ModConfigSpec.BooleanValue GRIEVERS_ENABLED;
    public static final ModConfigSpec.IntValue GRIEVER_BASE_CAP;
    public static final ModConfigSpec.IntValue GRIEVER_MAX_CAP;
    public static final ModConfigSpec.DoubleValue GRIEVER_HEALTH;
    public static final ModConfigSpec.DoubleValue GRIEVER_SPEED;
    public static final ModConfigSpec.DoubleValue GRIEVER_DAMAGE;
    public static final ModConfigSpec.IntValue MAZE_DEATH_LOCKOUT_SECONDS;
    public static final ModConfigSpec.BooleanValue MAZE_SHOW_BRIEFING;

    public static long cooldownMillis() {
        return REENTRY_COOLDOWN_HOURS.get() * 60L * 60L * 1000L;
    }
}
