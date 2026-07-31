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
     * A derelict two-storey house built around the boarded windows: start sealed
     * in one room, dig out the rubble to open the rest, and take on more windows
     * than you can hold in exchange for everything worth having.
     */
    OUTPOST(
            "The Outpost",
            "Endless. A bombed-out house, pitch dark — twelve boarded windows across three floors, and no final round. Extract while you still can.",
            "ENDLESS",
            0xFFC03080,
            OutpostBuilder.ARRIVAL,
            OutpostBuilder.GATES,
            OutpostBuilder.GATE_FACINGS,
            OutpostBuilder.EXTRACTION,
            OutpostBuilder.CENTER_X, OutpostBuilder.CENTER_Z,
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

    /** What to call a way in on this map - the Outpost has breaches, not gates. */
    public String gateNoun() {
        return this == OUTPOST ? "BREACH" : "GATE";
    }

    /** Short label for a gate, for HUD gauges and callouts. */
    public String gateLabel(int i) {
        if (this == OUTPOST) {
            return i >= 0 && i < OutpostBuilder.GATE_LABELS.length ? OutpostBuilder.GATE_LABELS[i] : "?";
        }
        String[] compass = {"NORTH", "SOUTH", "EAST", "WEST"};
        return i >= 0 && i < compass.length ? compass[i] : "?";
    }

    /**
     * Which sealed-off area of the map a gate opens into. Areas that have not
     * been dug out never spawn anything, so rubble genuinely holds the horde
     * back rather than just holding you back.
     */
    public int gateArea(int i) {
        return this == OUTPOST && i >= 0 && i < OutpostBuilder.GATE_AREAS.length
                ? OutpostBuilder.GATE_AREAS[i] : 0;
    }

    /** How many separately-sealed areas this map has. */
    public int areaCount() {
        return this == OUTPOST ? 4 : 1;
    }

    /**
     * Whether this map runs forever. Endless maps have no final round and no
     * victory screen - the only way to bank a run is to walk out on the
     * extraction glyph while you still can, which makes every extra round a bet
     * you are choosing to take.
     */
    public boolean isEndless() {
        return this == OUTPOST;
    }

    /**
     * A flat difficulty lift applied on top of the usual per-round scaling.
     * The Outpost is meant to be the one you lose on.
     */
    public double difficultyMultiplier() {
        return this == OUTPOST ? 1.15 : 1.0;
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
     * <p>The Outpost alone. It is built around the mechanic - ten windows in one
     * small house, all within seconds of each other. The Temple is deliberately
     * left as it always was: an open ruin-field where the horde simply comes for
     * you, with nothing to maintain. The Bridge has the Heart, which is already
     * its one thing to look after.
     */
    /**
     * Whether the horde materialises in a sealed chamber behind each way in.
     *
     * <p>Kept separate from {@link #hasBarricades()} on purpose. The two used to
     * be the same question, and when the boards went the pens would have gone
     * with them - dropping the whole horde straight into the room instead of
     * behind the breaches, with no walk-up and nowhere for the sound to come
     * from. The pens are the better half of that mechanic and they outlive the
     * boards.
     */
    public boolean hasPens() {
        return this == OUTPOST;
    }

    public boolean hasBarricades() {
        // Nothing does any more. The Outpost's windows became open breaches: you
        // walk through them, and so does everything else, which makes the map a
        // question of where you stand rather than what you have nailed shut.
        return false;
    }

    /**
     * Whether this map runs its own closed economy: no gear in, points earned
     * inside, nothing but materials out.
     *
     * <p>Outpost only. The arenas are a test of the gear you already own, which
     * is the opposite proposition, and mixing the two would make both worse.
     */
    public boolean hasEconomy() {
        return this == OUTPOST;
    }

    /** The volume wave mobs are tracked and swept within for this map. */
    public AABB bounds() {
        if (this == OUTPOST) {
            return OutpostBuilder.bounds();
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
