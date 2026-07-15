package com.jrpetty.mobtrumps;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks who is "seated" at each dueling table. The first player to right-click
 * takes a seat; when a different player right-clicks the same table, a best-of-3
 * duel starts between them. Seats expire so a table never gets stuck.
 */
public final class DuelTables {

    private static final long SEAT_TTL_MS = 45_000L;

    private record Seat(UUID player, long at) {
    }

    private static final Map<BlockPos, Seat> SEATS = new ConcurrentHashMap<>();

    private DuelTables() {
    }

    public static void interact(BlockPos pos, ServerPlayer player) {
        BlockPos key = pos.immutable();
        long now = System.currentTimeMillis();
        Seat seat = SEATS.get(key);
        if (seat != null && now - seat.at() > SEAT_TTL_MS) {
            seat = null;
        }

        // no one waiting, or you re-clicking your own seat: (re)take the seat
        if (seat == null || seat.player().equals(player.getUUID())) {
            if (DuelManager.isInDuel(player)) {
                player.sendSystemMessage(err("Finish your current duel first."));
                return;
            }
            SEATS.put(key, new Seat(player.getUUID(), now));
            player.sendSystemMessage(Component.literal("You take a seat at the dueling table — "
                            + "another player right-clicks it to challenge you (best of 3).")
                    .withStyle(ChatFormatting.GREEN));
            player.serverLevel().playSound(null, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                    net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
            return;
        }

        // someone else is seated: try to start the duel
        ServerPlayer opponent = player.serverLevel().getServer().getPlayerList().getPlayer(seat.player());
        SEATS.remove(key);
        if (opponent == null) {
            // the seated player logged off — just take the seat instead
            SEATS.put(key, new Seat(player.getUUID(), now));
            player.sendSystemMessage(Component.literal("You take a seat at the dueling table.")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        DuelManager.startFromTable(opponent, player);
    }

    /** Drop a player's seats when they leave, so tables don't hold ghosts. */
    public static void clearSeatsOf(UUID player) {
        SEATS.values().removeIf(s -> s.player().equals(player));
    }

    private static Component err(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }
}
