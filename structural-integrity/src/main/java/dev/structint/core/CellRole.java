package dev.structint.core;

/**
 * The structural role a block position plays in the integrity graph.
 *
 * <p>Every position the solver ever looks at resolves to exactly one of these roles.
 * The distinction is deliberately coarse: the system only cares whether a cell can
 * <em>provide</em> support ({@link #ANCHOR}), whether it <em>needs</em> support and can
 * relay it ({@link #STRUCTURAL}), or whether it is invisible to the rules ({@link #EMPTY}).
 */
public enum CellRole {

    /**
     * Not part of the structural graph: air, fluids, and non-structural decorations
     * (torches, panes, fences, …). These never provide support and never collapse.
     */
    EMPTY,

    /**
     * An always-stable support source. Natural (unmodified) terrain, bedrock-touching
     * natural blocks, and designated foundation blocks. Anchors seed support into the
     * graph and never collapse, regardless of what happens around them.
     */
    ANCHOR,

    /**
     * A player-placed structural block (logs, planks, stone bricks, concrete, metal, …).
     * It is subject to the integrity rules: it stays only while it can trace a valid load
     * path back to an anchor within its material's span, and it relays support to its
     * neighbours while it stands.
     */
    STRUCTURAL
}
