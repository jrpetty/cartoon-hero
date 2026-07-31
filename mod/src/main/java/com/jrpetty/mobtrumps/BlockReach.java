package com.jrpetty.mobtrumps;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a block-driven screen honest about where its player is standing.
 *
 * <p>Every one of these menus opens because somebody right-clicked a block, and
 * every action they then send is a plain packet. Nothing stopped a modified
 * client sending those packets from the other side of the world, or after the
 * block had been broken — shredding cards, printing them, or driving a duel
 * table it was nowhere near.
 *
 * <p>So the server remembers which block opened the screen, and every action
 * re-checks it: same dimension, block still there, player still within reach.
 * The record is per player and is replaced whenever another menu opens, which
 * is also what closes the previous one.
 *
 * <p>Deliberately server-side and derived from what the server already knows —
 * a position sent by the client would be exactly as forgeable as the action.
 */
public final class BlockReach {

    /** Squared distance a player may act at. Vanilla reach is ~4.5; be generous. */
    public static final double MAX_DISTANCE_SQ = 8.0 * 8.0;

    private record Open(BlockPos pos, Block block, String dimension) {
    }

    private static final Map<UUID, Open> OPEN = new ConcurrentHashMap<>();

    private BlockReach() {
    }

    /** Remember that this block opened a screen for this player. */
    public static void opened(ServerPlayer player, BlockPos pos) {
        OPEN.put(player.getUUID(), new Open(pos.immutable(),
                player.level().getBlockState(pos).getBlock(),
                player.level().dimension().location().toString()));
    }

    public static void closed(ServerPlayer player) {
        OPEN.remove(player.getUUID());
    }

    public static void forget(UUID player) {
        OPEN.remove(player);
    }

    /**
     * Whether the player may still act on the block whose screen they opened.
     * False if they never opened one, walked away, changed dimension, or the
     * block has since been broken or replaced.
     */
    public static boolean canReach(ServerPlayer player) {
        Open open = OPEN.get(player.getUUID());
        if (open == null) {
            return false;
        }
        if (!player.level().dimension().location().toString().equals(open.dimension())) {
            return false;
        }
        if (player.level().getBlockState(open.pos()).getBlock() != open.block()) {
            return false;   // broken, or swapped for something else
        }
        return player.distanceToSqr(open.pos().getX() + 0.5,
                open.pos().getY() + 0.5, open.pos().getZ() + 0.5) <= MAX_DISTANCE_SQ;
    }

    /**
     * As {@link #canReach}, but for an action that names its own position —
     * the position still has to be the one the server handed out.
     */
    public static boolean canReach(ServerPlayer player, BlockPos claimed) {
        Open open = OPEN.get(player.getUUID());
        return open != null && open.pos().equals(claimed) && canReach(player);
    }
}
