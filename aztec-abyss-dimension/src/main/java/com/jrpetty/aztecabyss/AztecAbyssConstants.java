package com.jrpetty.aztecabyss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Fixed, shared constants for the Aztec Abyss dimension.
 * The Abyss is a single, server-wide arena - every portal leads to the same
 * arrival point, and the arena layout (temple, forest, walls) is generated
 * deterministically the first time the dimension is loaded.
 */
public final class AztecAbyssConstants {

    public static final String MOD_ID = "aztecabyss";

    /** The dimension key players are teleported into. */
    public static final ResourceKey<Level> ABYSS_LEVEL_KEY = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "abyss")
    );

    public static final ResourceKey<DimensionType> ABYSS_DIMENSION_TYPE_KEY = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "abyss")
    );

    /** The Maze Runner dimension - a separate world from the arena Abyss. */
    public static final ResourceKey<Level> MAZE_LEVEL_KEY = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "maze")
    );

    /**
     * The Workshop: an empty, permanently lit void for building maps in.
     *
     * <p>Its own dimension rather than a corner of an existing one, because a map
     * under construction wants none of what a world provides - no terrain to
     * build around or clear away, no day, no weather, no wandering mobs, and no
     * chance of a half-finished arena being stamped over by something else.
     */
    public static final ResourceKey<Level> WORKSHOP_LEVEL_KEY = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "workshop")
    );

    // ------------------------------------------------------------------
    // Arena layout - all fixed, all deterministic. Do not randomize.
    // ------------------------------------------------------------------

    /** Radius (blocks) of the whole playable arena, centred on (0,*,0). Beyond this = bedrock wall. */
    public static final int ARENA_RADIUS = 67; // 30% tighter than the original 96

    /** Y level of the arena floor. */
    public static final int ARENA_FLOOR_Y = 64;

    /** Build height of the perimeter bedrock wall above the floor. */
    public static final int WALL_HEIGHT = 48;

    /** Centre of the temple (and of the whole arena). */
    public static final BlockPos TEMPLE_CENTER = new BlockPos(0, ARENA_FLOOR_Y, 0);

    /** Half-width of the temple's square base footprint. */
    public static final int TEMPLE_BASE_HALF_WIDTH = 24;

    /** Number of stepped tiers on the pyramid. */
    public static final int TEMPLE_TIERS = 9;

    /** Height in blocks of each tier. */
    public static final int TEMPLE_TIER_HEIGHT = 3;

    /** How many blocks narrower each tier is than the one below it (per side). */
    public static final int TEMPLE_STEP_IN = 2;

    // ------------------------------------------------------------------
    // Interior / ritual landmarks - fixed, deterministic positions shared
    // between the temple builder and the ritual system.
    // ------------------------------------------------------------------

    /** Centre of the hollow interior altar chamber (floor level). */
    public static final BlockPos ALTAR_POS = new BlockPos(0, ARENA_FLOOR_Y + 1, 0);

    /** The block a player right-clicks with an offering to feed the altar. */
    public static final BlockPos ALTAR_OFFERING_POS = new BlockPos(0, ARENA_FLOOR_Y + 2, 0);

    /**
     * The four ritual braziers, indexed 0-3. The Easter-egg ritual requires
     * lighting them in a fixed order (see RitualHandler). Placed at the corners
     * of the altar chamber.
     */
    public static final BlockPos[] BRAZIER_POSITIONS = new BlockPos[]{
            new BlockPos(-6, ARENA_FLOOR_Y + 1, -6), // 0
            new BlockPos(6, ARENA_FLOOR_Y + 1, -6),  // 1
            new BlockPos(6, ARENA_FLOOR_Y + 1, 6),   // 2
            new BlockPos(-6, ARENA_FLOOR_Y + 1, 6),  // 3
    };

    /** Correct order the braziers must be lit in to advance the ritual. */
    public static final int[] RITUAL_ORDER = new int[]{0, 2, 1, 3};

    /** Chest inside the sealed vault, revealed only when the ritual completes. */
    public static final BlockPos VAULT_CHEST_POS = new BlockPos(0, ARENA_FLOOR_Y - 6, -10);

    /** Centre of the vault door (a 3-wide, 3-tall wall) that the ritual dissolves. */
    public static final BlockPos VAULT_DOOR_POS = new BlockPos(0, ARENA_FLOOR_Y - 5, -6);

    /**
     * The extraction glyph: appears on the south approach only between rounds.
     * Standing on it banks the run's rewards and sends the player home safely
     * (no cooldown) instead of risking the next wave.
     */
    public static final BlockPos EXTRACTION_POS = new BlockPos(0, ARENA_FLOOR_Y, 34);

    /**
     * The leaderboard monument, beside the arrival spawn so every player reads
     * the standings the moment they step through. Its carved signs face west
     * toward the arrival walkway and are rewritten live as runs end.
     */
    public static final BlockPos MONUMENT_POS = new BlockPos(7, ARENA_FLOOR_Y, 52);

    /**
     * Fixed arrival point in the Abyss - every portal in every overworld leads here.
     * Placed at the forest's edge so the temple is visible through the trees on arrival,
     * per the brief ("the spawn for the portal should always be in the same place").
     */
    public static final BlockPos ABYSS_ARRIVAL_POS = new BlockPos(0, ARENA_FLOOR_Y + 1, 52);

    /** Distance from centre of the four mob spawn-gates (just inside the wall). */
    public static final int MOB_GATE_DISTANCE = ARENA_RADIUS - 6;

    /**
     * The four horde gates - dark portal arches at the cardinal points of the
     * wall that every wave mob pours out of (N, S, E, W).
     */
    public static final BlockPos[] MOB_GATES = new BlockPos[]{
            new BlockPos(0, ARENA_FLOOR_Y + 1, -MOB_GATE_DISTANCE), // north
            new BlockPos(0, ARENA_FLOOR_Y + 1, MOB_GATE_DISTANCE),  // south
            new BlockPos(MOB_GATE_DISTANCE, ARENA_FLOOR_Y + 1, 0),  // east
            new BlockPos(-MOB_GATE_DISTANCE, ARENA_FLOOR_Y + 1, 0), // west
    };

    /** Facing the arrival portal so players look toward the temple when they step through. */
    public static final net.minecraft.core.Direction ABYSS_ARRIVAL_FACING = net.minecraft.core.Direction.SOUTH;

    // ------------------------------------------------------------------
    // Zombies mode tuning
    // ------------------------------------------------------------------

    public static final int MAX_ROUND = 20;

    /** Real-world milliseconds a player must wait after dying/leaving before re-entering. */
    public static final long REENTRY_COOLDOWN_MS = 20L * 60L * 60L * 1000L; // 20 hours

    private AztecAbyssConstants() {
    }
}
