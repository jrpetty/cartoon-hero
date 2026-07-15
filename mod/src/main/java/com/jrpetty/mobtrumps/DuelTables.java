package com.jrpetty.mobtrumps;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks who is "seated" at each dueling table, and in which game mode. The
 * first player to right-click takes a seat (default best-of-3); sneak-right-
 * click while seated cycles the mode. When a different player right-clicks
 * the table, the game starts in whichever mode the seated player left it on.
 * Seats expire so a table never gets stuck.
 */
public final class DuelTables {

    private static final long SEAT_TTL_MS = 45_000L;

    /** The game modes selectable at a table, in cycle order. */
    public enum Mode {
        BO1("Best of 1", 1),
        BO3("Best of 3", 3),
        BO5("Best of 5", 5),
        DRAFT("Draft", 0);

        final String label;
        final int bestOf; // 0 = not applicable (draft)

        Mode(String label, int bestOf) {
            this.label = label;
            this.bestOf = bestOf;
        }

        Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private record Seat(UUID player, long at, Mode mode) {
        Seat withMode(Mode m) {
            return new Seat(player, at, m);
        }
    }

    private static final Map<BlockPos, Seat> SEATS = new ConcurrentHashMap<>();

    private DuelTables() {
    }

    public static void interact(BlockPos pos, ServerPlayer player, boolean sneaking) {
        BlockPos key = pos.immutable();
        long now = System.currentTimeMillis();
        Seat seat = SEATS.get(key);
        if (seat != null && now - seat.at() > SEAT_TTL_MS) {
            seat = null;
        }

        // sneak-clicking your own seat cycles the mode instead of re-sitting
        if (seat != null && seat.player().equals(player.getUUID()) && sneaking) {
            Mode next = seat.mode().next();
            SEATS.put(key, new Seat(player.getUUID(), now, next));
            announceMode(player, next);
            return;
        }

        // no one waiting, or you re-clicking your own seat: (re)take the seat
        if (seat == null || seat.player().equals(player.getUUID())) {
            if (DuelManager.isInDuel(player) || DraftManager.isDrafting(player)) {
                player.sendSystemMessage(err("Finish your current game first."));
                return;
            }
            Mode mode = seat == null ? Mode.BO3 : seat.mode();
            SEATS.put(key, new Seat(player.getUUID(), now, mode));
            player.sendSystemMessage(Component.literal("You take a seat at the dueling table — mode: ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(mode.label).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                    .append(Component.literal(". Sneak-right-click to change it; another player "
                            + "right-clicks to challenge you.").withStyle(ChatFormatting.GREEN)));
            player.serverLevel().playSound(null, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                    SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 1.0F);
            return;
        }

        // someone else is seated: try to start the game in their chosen mode
        ServerPlayer opponent = player.serverLevel().getServer().getPlayerList().getPlayer(seat.player());
        Mode mode = seat.mode();
        SEATS.remove(key);
        if (opponent == null) {
            // the seated player logged off — just take the seat instead
            SEATS.put(key, new Seat(player.getUUID(), now, mode));
            player.sendSystemMessage(Component.literal("You take a seat at the dueling table.")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        if (DuelManager.isInDuel(player) || DraftManager.isDrafting(player)) {
            player.sendSystemMessage(err("Finish your current game first."));
            SEATS.put(key, seat); // put the original seat back — they're still waiting
            return;
        }
        if (DuelManager.isInDuel(opponent) || DraftManager.isDrafting(opponent)) {
            player.sendSystemMessage(err(opponent.getGameProfile().getName()
                    + " started another game while waiting — take the seat instead."));
            return;
        }

        if (mode == Mode.DRAFT) {
            if (!DraftManager.startDirect(opponent, player)) {
                player.sendSystemMessage(err("Couldn't start the draft — someone's already busy."));
            }
        } else {
            DuelManager.startFromTable(opponent, player, mode.bestOf);
        }
    }

    private static void announceMode(ServerPlayer player, Mode mode) {
        player.sendSystemMessage(Component.literal("Table set to ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(mode.label).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(mode == Mode.DRAFT
                                ? " — you'll draft a deck together before duelling."
                                : " — a single challenger will play " + mode.label.toLowerCase(java.util.Locale.ROOT) + ".")
                        .withStyle(ChatFormatting.YELLOW)));
        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.7F, 1.2F);
    }

    /** Drop a player's seats when they leave, so tables don't hold ghosts. */
    public static void clearSeatsOf(UUID player) {
        SEATS.values().removeIf(s -> s.player().equals(player));
    }

    private static Component err(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }
}
