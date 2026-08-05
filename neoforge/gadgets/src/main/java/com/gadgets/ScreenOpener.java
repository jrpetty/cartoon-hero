package com.gadgets;

import java.util.function.Consumer;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Client-screen indirection: blocks call these hooks on the logical client;
 * {@link GadgetsClient} swaps in the real screen-opening lambdas at client
 * init. On a dedicated server they stay as no-ops, so no client classes are
 * ever touched from common code.
 */
public final class ScreenOpener {
    public static Consumer<BlockEntity> HUB = be -> {};
    public static Consumer<BlockEntity> SENSOR = be -> {};
    public static Consumer<BlockEntity> COUNTER = be -> {};
    public static Consumer<BlockEntity> MONITOR = be -> {};
    public static Consumer<BlockEntity> HUB_MONITOR = be -> {};
    public static Consumer<BlockEntity> GAUGE = be -> {};
    public static Consumer<BlockEntity> TRANSFER = be -> {};

    private ScreenOpener() {
    }
}
