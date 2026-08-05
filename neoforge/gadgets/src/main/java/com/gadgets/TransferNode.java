package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * One end of a teleporting item link, and how fast it is allowed to run.
 *
 * <p>Teleporting items across dimensions with no cost and no limit made hoppers
 * and rails pointless, so a link is now something you invest in rather than
 * something you finish. A fresh pair moves one item a minute; each upgrade
 * doubles that, to thirty-two a minute at the sixth and last level.
 *
 * <p>A link runs at the <em>lower</em> of its two ends. Upgrading only the
 * sender does nothing on its own, which is the intuitive reading of a chain
 * being as fast as its slowest part, and it means both ends of a long-haul line
 * have to be worth the pearls.
 */
public interface TransferNode extends ChannelBlockEntity {
    int MIN_TIER = 1;
    int MAX_TIER = 6;

    /** Ticks in a minute — the unit the levels are quoted in. */
    int MINUTE = 1200;

    /** What one upgrade costs, taken from the player's inventory. */
    int UPGRADE_IRON_BLOCKS = 7;
    int UPGRADE_ENDER_PEARLS = 16;
    int UPGRADE_HOPPERS = 1;

    /** Items a minute at a given level: 1, 2, 4, 8, 16, 32. */
    static int ratePerMinute(int tier) {
        return 1 << (Mth.clamp(tier, MIN_TIER, MAX_TIER) - MIN_TIER);
    }

    /** How long one item takes at a given level, rounded down to whole ticks. */
    static int ticksPerItem(int tier) {
        return Math.max(1, MINUTE / ratePerMinute(tier));
    }

    int getTier();

    void setTier(int tier);

    /** Player-set label, empty when never named. */
    String getCustomName();

    void setCustomName(String name);

    /** A name for screens: the player's label, or what the block is. */
    String displayName();

    /**
     * The item this end is about to pass, or has just passed. A sender knows
     * what it will send next because it can see the container it draws from; a
     * receiver can only report what last came through, since nothing tells it
     * what is queued at the far end.
     */
    ItemStack flowing();

    BlockPos getBlockPos();
}
