package com.gadgets;

import net.minecraft.core.BlockPos;

/**
 * A fill-level gadget the Command Hub can put on its board: something with a
 * stored amount, a capacity, and a low-level alert.
 *
 * <p>The Fluid Monitor and the Energy Monitor are the same instrument reading
 * different capabilities, and the hub, the Monitor Wand, the board renderers
 * and the config screen all work against this rather than against each type,
 * so a third gauge later needs no changes to any of them.
 */
public interface HubGauge {
    /** Which {@code CommandHubBlockEntity.TYPE_*} this reports as. */
    int gaugeType();

    /** Label for the board and the block's own face. */
    String displayName();

    /** Player-set name, empty when never set. */
    String getCustomName();

    void setCustomName(String name);

    /** Current contents, in the gauge's own units. */
    long gaugeStored();

    /** Total the source can hold, or 0 when it could not be read. */
    long gaugeCapacity();

    /** Alert level, as a percentage of capacity. */
    int getThreshold();

    void setThreshold(int percent);

    boolean isLow();

    /** False when nothing readable sits in front of the panel. */
    boolean hasSource();

    BlockPos getBlockPos();

    /** Fill as a percentage, or 0 when the capacity is unknown. */
    default int percent() {
        long capacity = gaugeCapacity();
        return capacity <= 0 ? 0 : (int) Math.min(100L, gaugeStored() * 100L / capacity);
    }

    /** The big line on the block's face. */
    default String faceValue() {
        return hasSource() ? percent() + "%" : "?";
    }

    /** The small line under it: the player's label, or what is being read. */
    String faceLabel();

    /** Stored against capacity in the gauge's own units, for screens. */
    String amountText();

    /** Alert levels offered on the screen, as percentages. */
    int[] THRESHOLDS = {5, 10, 25, 50, 75};
}
