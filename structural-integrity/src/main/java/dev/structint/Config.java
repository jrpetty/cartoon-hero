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
