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
            220.0),

    /**
     * A sealed burial compound built around the boarded windows: a cross of four
     * short arms, eight windows clustered at the ends, nothing more than a
     * four-second sprint from the middle.
     */
    CRYPT(
            "The Crypt",
            "Eight boarded windows and nowhere to run. Hold the compound, mend the boards, and pick which side you can afford to lose.",
            "HARD",
            0xFFD04040,
            CryptBuilder.ARRIVAL,
            CryptBuilder.GATES,
            CryptBuilder.GATE_FACINGS,
            CryptBuilder.EXTRACTION,
            CryptBuilder.CENTER_X, CryptBuilder.CENTER_Z,
            80.0);

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

    /** Which way each gate faces out of the arena, in {@link #gates()} order. */
    public net.minecraft.core.Direction[] gateFacings() {
        return gateFacings;
    }

    /** What to call a way in on this map - the Crypt has windows, not gates. */
    public String gateNoun() {
        return this == CRYPT ? "WINDOW" : "GATE";
    }

    /** Short label for a gate, for HUD gauges and callouts. */
    public String gateLabel(int i) {
        if (this == CRYPT) {
            return i >= 0 && i < CryptBuilder.GATE_LABELS.length ? CryptBuilder.GATE_LABELS[i] : "?";
        }
        String[] compass = {"NORTH", "SOUTH", "EAST", "WEST"};
        return i >= 0 && i < compass.length ? compass[i] : "?";
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
     * <p>The Crypt is built around it - eight windows, all within a few seconds
     * of the middle. The Temple carries it too, though at 134 blocks across its
     * four gates are a long way apart, so it plays as the sparse version.
     *
     * <p>The Bridge deliberately does not: one gate, 135 blocks from the fort,
     * so boarding it would mean abandoning the Heart to maintain something you
     * cannot even see. That map already has its one thing to look after.
     */
    public boolean hasBarricades() {
        return this == TEMPLE || this == CRYPT;
    }

    /** The volume wave mobs are tracked and swept within for this map. */
    public AABB bounds() {
        if (this == CRYPT) {
            return CryptBuilder.bounds();
        }
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
