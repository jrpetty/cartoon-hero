package dev.structint.core;

/**
 * A structural material tier, defined purely by its <b>maximum unsupported horizontal
 * span</b> &mdash; how many blocks a beam of this material may cantilever out from a
 * vertical support before it can no longer hold itself.
 *
 * <p>Span is the single tuning knob of the whole system. Bigger span = the material can
 * bridge wider gaps and reach further from a pillar. These are sensible defaults; the
 * Minecraft layer lets server operators override every value through config.
 */
public record Material(String id, int maxSpan) {

    public Material {
        if (maxSpan < 0) {
            throw new IllegalArgumentException("maxSpan must be >= 0, got " + maxSpan);
        }
    }

    /** Crumbly fill. Must essentially rest directly on something. */
    public static final Material DIRT = new Material("dirt", 1);

    /** Unknown / generic full blocks a player placed (wool, glass, …). Weak but valid. */
    public static final Material GENERIC = new Material("generic", 2);

    /** Logs and planks. The first material that can build real cantilevers. */
    public static final Material WOOD = new Material("wood", 4);

    /** Stone family: stone bricks, cobble, deepslate, bricks. */
    public static final Material STONE = new Material("stone", 7);

    /** Reinforced materials: concrete and the mod's reinforced beam. */
    public static final Material REINFORCED = new Material("reinforced", 12);

    /** Heavy structural metal: iron/netherite blocks and the mod's heavy girder. */
    public static final Material HEAVY_METAL = new Material("heavy_metal", 20);
}
