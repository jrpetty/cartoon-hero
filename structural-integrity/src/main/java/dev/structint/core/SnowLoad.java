package dev.structint.core;

/**
 * Snow load: the one deterministic way weight-from-above enters the system.
 *
 * <p>Snow piled on top of a block eats into that block's usable span, so a roof that spans exactly
 * its material limit sheds its far edge once the snow gets deep enough. This is a flat integer
 * penalty derived from the snow depth alone — not an accumulating weight simulation — so the
 * result stays a pure function of the blocks present and the whole system stays predictable:
 * clear the snow (or roof in something stronger) and the original span comes straight back.
 *
 * <p>Strong materials shrug it off entirely: anything whose base span reaches
 * {@code immuneMinSpan} ignores snow, which is what separates a steel gantry from a plank roof.
 */
public final class SnowLoad {

    /** Snow layers in a full block of snow — vanilla's {@code SnowLayerBlock} maximum. */
    public static final int FULL_DEPTH = 8;

    /**
     * The span penalty for a given snow depth: one span per {@code layersPerSpan} layers, never
     * more than {@code maxPenalty}.
     *
     * @param layers        snow layers resting on the block (0–8)
     * @param layersPerSpan layers that cost one block of span; {@code <= 0} disables snow load
     * @param maxPenalty    hard ceiling on the penalty
     */
    public static int penalty(int layers, int layersPerSpan, int maxPenalty) {
        if (layers <= 0 || layersPerSpan <= 0 || maxPenalty <= 0) {
            return 0;
        }
        return Math.min(maxPenalty, layers / layersPerSpan);
    }

    /**
     * The effective span of a block carrying snow. Materials at or above {@code immuneMinSpan}
     * are unaffected; everything else loses span with depth, floored at 0 (a fully-laden weak
     * block can hold up only what sits directly beneath it).
     */
    public static int loadedSpan(int baseSpan, int layers, int layersPerSpan, int maxPenalty,
                                 int immuneMinSpan) {
        if (baseSpan >= immuneMinSpan) {
            return baseSpan;
        }
        return Math.max(0, baseSpan - penalty(layers, layersPerSpan, maxPenalty));
    }

    private SnowLoad() {
    }
}
