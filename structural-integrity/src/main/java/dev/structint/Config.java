package dev.structint;

import dev.structint.core.SupportSolver;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side configuration. Every span and every budget the system uses is exposed here so
 * a server operator can retune the difficulty curve (wood → stone → metal) or dial back the
 * performance envelope without touching code.
 */
public final class Config {

    public static final ModConfigSpec SPEC;

    // --- behaviour ----------------------------------------------------------------------
    public static final ModConfigSpec.BooleanValue ENABLE_COLLAPSE;
    public static final ModConfigSpec.BooleanValue ONLY_PLAYER_PLACED;
    public static final ModConfigSpec.BooleanValue COLLAPSE_WARNING_EFFECTS;
    public static final ModConfigSpec.IntValue COLLAPSE_WARN_DELAY_TICKS;
    public static final ModConfigSpec.DoubleValue COLLAPSE_IMPACT_DAMAGE_SCALE;
    public static final ModConfigSpec.IntValue SNOW_LOAD_LAYERS_PER_SPAN;
    public static final ModConfigSpec.IntValue SNOW_LOAD_MAX_PENALTY;
    public static final ModConfigSpec.IntValue SNOW_LOAD_IMMUNE_MIN_SPAN;
    public static final ModConfigSpec.IntValue SNOW_LOAD_SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue SNOW_LOAD_SCAN_BUDGET;
    public static final ModConfigSpec.IntValue SNOW_LOAD_SCAN_RADIUS_CHUNKS;

    // --- material spans -----------------------------------------------------------------
    public static final ModConfigSpec.IntValue SPAN_DIRT;
    public static final ModConfigSpec.IntValue SPAN_GENERIC;
    public static final ModConfigSpec.IntValue SPAN_WOOD;
    public static final ModConfigSpec.IntValue SPAN_STONE;
    public static final ModConfigSpec.IntValue SPAN_REINFORCED;
    public static final ModConfigSpec.IntValue SPAN_METAL;

    // --- performance --------------------------------------------------------------------
    public static final ModConfigSpec.IntValue SUPPORT_CAP;
    public static final ModConfigSpec.IntValue MAX_REGION_NODES;
    public static final ModConfigSpec.IntValue CHANGE_BUDGET_PER_TICK;
    public static final ModConfigSpec.IntValue COLLAPSE_BUDGET_PER_TICK;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("behaviour");
        ENABLE_COLLAPSE = b
                .comment("Master switch. When false, structural state is still tracked but",
                        "nothing is ever made to fall (useful for testing or creative servers).")
                .define("enableCollapse", true);
        ONLY_PLAYER_PLACED = b
                .comment("When true, only blocks a player placed are subject to the rules;",
                        "natural terrain is always stable. Strongly recommended — leave this true.",
                        "WARNING: setting it false makes ALL natural terrain collapsible too, which",
                        "with the 'every block is structural' rule will tear down unsupported",
                        "overhangs, cave ceilings and floating islands across loaded chunks. Expert/",
                        "experimental only.")
                .define("onlyPlayerPlaced", true);
        COLLAPSE_WARNING_EFFECTS = b
                .comment("Play a 'creak' sound and a puff of crumbling particles the moment a block",
                        "is judged unsupported, a beat before it actually falls.")
                .define("collapseWarningEffects", true);
        COLLAPSE_WARN_DELAY_TICKS = b
                .comment("Ticks between the warning and the fall (20 ticks = 1 second). 0 = fall as",
                        "soon as it is processed (the warning still plays).")
                .defineInRange("collapseWarnDelayTicks", 15, 0, 200);
        COLLAPSE_IMPACT_DAMAGE_SCALE = b
                .comment("Collapsing blocks damage entities they land on, scaled by the",
                        "material's structural strength (span) — stone lands like an anvil,",
                        "metal hits harder, dirt barely stings. This multiplies both the",
                        "damage-per-block-fallen and the damage cap. 0 disables impact damage.")
                .defineInRange("collapseImpactDamageScale", 1.0, 0.0, 10.0);

        b.comment("Snow load — snow piled on a block eats into the span it can carry, so roofs",
                        "need clearing, a steeper pitch, or stronger material through a hard winter.",
                        "A flat penalty from depth alone: no accumulating weight, and clearing the",
                        "snow restores the original span immediately.")
                .push("snowLoad");
        SNOW_LOAD_LAYERS_PER_SPAN = b
                .comment("Snow layers that cost one block of span (vanilla snow is 1-8 layers deep).",
                        "0 disables snow load entirely.")
                .defineInRange("layersPerSpan", 3, 0, 8);
        SNOW_LOAD_MAX_PENALTY = b
                .comment("Never take more than this many blocks of span, however deep the snow.")
                .defineInRange("maxPenalty", 3, 0, 64);
        SNOW_LOAD_IMMUNE_MIN_SPAN = b
                .comment("Materials with a base span of at least this ignore snow entirely - with",
                        "the default spans that is reinforced (12) and metal (20), while wood and",
                        "stone roofs feel it.")
                .defineInRange("immuneMinSpan", 12, 0, 256);
        SNOW_LOAD_SCAN_INTERVAL_TICKS = b
                .comment("Snow accumulates and melts without firing a block event, so laden blocks",
                        "are re-checked by a periodic sweep. Ticks between sweeps (20 = 1 second).")
                .defineInRange("scanIntervalTicks", 80, 20, 12000);
        SNOW_LOAD_SCAN_BUDGET = b
                .comment("Maximum managed blocks examined per sweep. A very large base is covered",
                        "across several sweeps rather than in one spike.")
                .defineInRange("scanBudget", 4096, 64, 262144);
        SNOW_LOAD_SCAN_RADIUS_CHUNKS = b
                .comment("Chunk radius around each player that the sweep considers. Snow only falls",
                        "in ticking chunks near players, so this needs no more than that reach.")
                .defineInRange("scanRadiusChunks", 4, 1, 32);
        b.pop();
        b.pop();

        b.push("spans");
        b.comment("Maximum number of blocks each material may cantilever out from a support.");
        SPAN_DIRT = b.comment("Crumbly fill (dirt, gravel, sand-likes).")
                .defineInRange("dirt", 1, 0, 256);
        SPAN_GENERIC = b.comment("Fallback for any other full block a player places.")
                .defineInRange("generic", 2, 0, 256);
        SPAN_WOOD = b.comment("Logs and planks.")
                .defineInRange("wood", 4, 0, 256);
        SPAN_STONE = b.comment("Stone family: stone, cobble, deepslate, bricks, stone bricks.")
                .defineInRange("stone", 7, 0, 256);
        SPAN_REINFORCED = b.comment("Concrete and the reinforced beam.")
                .defineInRange("reinforced", 12, 0, 256);
        SPAN_METAL = b.comment("Metal blocks and the heavy girder.")
                .defineInRange("metal", 20, 0, 256);
        b.pop();

        b.push("performance");
        SUPPORT_CAP = b
                .comment("The 'infinite' reach assigned to anchors. Must exceed your largest span.")
                .defineInRange("supportCap", SupportSolver.DEFAULT_CAP, 16, 4096);
        MAX_REGION_NODES = b
                .comment("Largest connected structure analysed in a single local check. Structures",
                        "bigger than this are treated as stable (never mass-collapsed) as a",
                        "fail-safe. Bound on per-edit CPU cost.")
                .defineInRange("maxRegionNodes", 4096, 64, 200_000);
        CHANGE_BUDGET_PER_TICK = b
                .comment("Max block-change origins re-analysed per server tick (per level).")
                .defineInRange("changeBudgetPerTick", 64, 1, 100_000);
        COLLAPSE_BUDGET_PER_TICK = b
                .comment("Max blocks made to fall per server tick (per level). Lower = more",
                        "gradual, visually staggered collapses.")
                .defineInRange("collapseBudgetPerTick", 16, 1, 100_000);
        b.pop();

        SPEC = b.build();
    }

    private Config() {
    }
}
