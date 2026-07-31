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
            BridgeBuilder.EXTRACTION,
            BridgeBuilder.CENTER_X, BridgeBuilder.CENTER_Z,
            220.0);

    private final String title;
    private final String blurb;
    private final String difficulty;
    private final int difficultyColor;
    private final BlockPos arrival;
    private final BlockPos[] gates;
    private final BlockPos extraction;
    private final int borderCenterX;
    private final int borderCenterZ;
    private final double borderSize;

    ArenaMap(String title, String blurb, String difficulty, int difficultyColor,
             BlockPos arrival, BlockPos[] gates, BlockPos extraction,
             int borderCenterX, int borderCenterZ, double borderSize) {
        this.title = title;
        this.blurb = blurb;
        this.difficulty = difficulty;
        this.difficultyColor = difficultyColor;
        this.arrival = arrival;
        this.gates = gates;
        this.extraction = extraction;
        this.borderCenterX = borderCenterX;
        this.borderCenterZ = borderCenterZ;
        this.borderSize = borderSize;
    }

    public static ArenaMap byId(int id) {
        ArenaMap[] all = values();
        return (id >= 0 && id < all.length) ? all[id] : TEMPLE;
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
     * Whether the horde gates on this map are boarded up and have to be broken
     * through - and can be nailed back together by hunters.
     *
     * <p>Temple only, and deliberately so. Its four gates surround you, which is
     * exactly the shape barricades need: more gates than you can hold, so every
     * round is a question of which two you concede. The Bridge is the opposite -
     * one gate, 135 blocks from the fort, so boarding it would mean abandoning
     * the Heart to maintain something you cannot see. Each map gets one thing to
     * look after, and they stay different games because of it.
     */
    public boolean hasBarricades() {
        return this == TEMPLE;
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
