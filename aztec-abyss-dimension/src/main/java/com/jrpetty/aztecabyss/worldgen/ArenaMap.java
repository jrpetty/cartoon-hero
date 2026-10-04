package com.jrpetty.aztecabyss.worldgen;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * The playable arenas inside the Abyss. Both live in the same dimension, far
 * apart on the X axis, so only one datapack dimension is ever needed - the
 * active map decides where players arrive, where the horde gates sit, and where
 * the world border is drawn.
 *
 * Rewards, rounds, bosses and scoring are identical across maps; only the
 * battlefield changes.
 *
 * <p>Ordinals are stored: a player's chosen map and the per-map best rounds are
 * kept by index. New maps go on the end, and a removed map's index must never
 * be reused - which is why the third arena's removal left this list at two
 * rather than renumbering anything.
 */
public enum ArenaMap {

    /** The original: a stepped Aztec pyramid ringed by ruins, hordes from four gates. */
    TEMPLE(
            "The Aztec Temple",
            "An open ruin-field around a stepped pyramid. Hordes pour in from all four sides at once.",
            "HARD",
            0xFFD04040,
            AztecAbyssConstants.ABYSS_ARRIVAL_POS,
            AztecAbyssConstants.MOB_GATES,
            new net.minecraft.core.Direction[]{
                    net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                    net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST},
            AztecAbyssConstants.EXTRACTION_POS,
            0, 0,
            (AztecAbyssConstants.ARENA_RADIUS - 2) * 2.0),

    /** A last stand on a high bridge over the void - everything funnels in from one end. */
    BRIDGE(
            "The Long Bridge",
            "Hold one bridge and keep the Heart alive. A single choke to defend — but if the Heart falls, the run is over.",
            "MEDIUM",
            0xFFE0B040,
            BridgeBuilder.ARRIVAL,
            BridgeBuilder.GATES,
            new net.minecraft.core.Direction[]{net.minecraft.core.Direction.NORTH},
            BridgeBuilder.EXTRACTION,
            BridgeBuilder.CENTER_X, BridgeBuilder.CENTER_Z,
            220.0);

    private final String title;
    private final String blurb;
    private final String difficulty;
    private final int difficultyColor;
    private final BlockPos arrival;
    private final BlockPos[] gates;
    private final net.minecraft.core.Direction[] gateFacings;
    private final BlockPos extraction;
    private final int borderCenterX;
    private final int borderCenterZ;
    private final double borderSize;

    ArenaMap(String title, String blurb, String difficulty, int difficultyColor,
             BlockPos arrival, BlockPos[] gates, net.minecraft.core.Direction[] gateFacings,
             BlockPos extraction, int borderCenterX, int borderCenterZ, double borderSize) {
        this.title = title;
        this.blurb = blurb;
        this.difficulty = difficulty;
        this.difficultyColor = difficultyColor;
        this.arrival = arrival;
        this.gates = gates;
        this.gateFacings = gateFacings;
        this.extraction = extraction;
        this.borderCenterX = borderCenterX;
        this.borderCenterZ = borderCenterZ;
        this.borderSize = borderSize;
    }

    /**
     * A stored map choice, made safe.
     *
     * <p>One choke point covers every reader - the portal, the round manager, the
     * picker - so an index from a map that no longer exists (a player who last
     * chose the retired third arena has a 2 saved on them) quietly becomes the
     * Temple instead of an exception.
     */
    public static ArenaMap byId(int id) {
        ArenaMap[] all = values();
        return id >= 0 && id < all.length ? all[id] : TEMPLE;
    }

    /**
     * A map from its leaderboard key ("temple", "bridge"), or null. The retired
     * third arena's records are still on disk under "outpost"; readers skip any
     * key this returns null for.
     */
    public static ArenaMap byKey(String key) {
        for (ArenaMap m : values()) {
            if (m.name().equalsIgnoreCase(key)) {
                return m;
            }
        }
        return null;
    }

    public String title() {
        return title;
    }

    public String blurb() {
        return blurb;
    }

    /** Short difficulty tag shown on the picker card. */
    public String difficulty() {
        return difficulty;
    }

    public int difficultyColor() {
        return difficultyColor;
    }

    public BlockPos arrival() {
        return arrival;
    }

    public BlockPos[] gates() {
        return gates;
    }

    /** Which way each gate faces out of the arena, in {@link #gates()} order. */
    public net.minecraft.core.Direction[] gateFacings() {
        return gateFacings;
    }

    public BlockPos extraction() {
        return extraction;
    }

    public int borderCenterX() {
        return borderCenterX;
    }

    public int borderCenterZ() {
        return borderCenterZ;
    }

    public double borderSize() {
        return borderSize;
    }

    /**
     * The block the horde makes for on this map, or null if there's nothing to
     * defend and they simply hunt players.
     */
    public BlockPos objective() {
        return this == BRIDGE ? BridgeBuilder.HEART : null;
    }

    /**
     * The place a map's announcements are measured from - "a supply cache lands
     * to the east of ___". The temple on one map, the fort on the other. Both
     * announcements used to measure from the world origin and name the temple,
     * so on the Bridge - two thousand blocks east of the origin - every cache
     * landed "to the east of the temple", on a map with no temple in it.
     */
    public BlockPos landmark() {
        return this == BRIDGE
                ? new BlockPos(BridgeBuilder.CENTER_X, BridgeBuilder.DECK_Y, BridgeBuilder.ISLAND_CENTER_Z)
                : AztecAbyssConstants.TEMPLE_CENTER;
    }

    /** What that landmark is called, for those same announcements. */
    public String landmarkName() {
        return this == BRIDGE ? "the fort" : "the temple";
    }

    /** The volume wave mobs are tracked and swept within for this map. */
    public AABB bounds() {
        if (this == BRIDGE) {
            return new AABB(
                    BridgeBuilder.CENTER_X - 60, BridgeBuilder.DECK_Y - 10, BridgeBuilder.NORTH_END - 20,
                    BridgeBuilder.CENTER_X + 60, BridgeBuilder.DECK_Y + 40, BridgeBuilder.ISLAND_CENTER_Z + 40);
        }
        int r = AztecAbyssConstants.ARENA_RADIUS;
        return new AABB(-r, AztecAbyssConstants.ARENA_FLOOR_Y - 8, -r,
                r, AztecAbyssConstants.ARENA_FLOOR_Y + AztecAbyssConstants.WALL_HEIGHT, r);
    }
}
