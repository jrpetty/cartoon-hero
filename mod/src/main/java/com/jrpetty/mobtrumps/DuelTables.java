package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Difficulty;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The dueling table's server brain. Right-clicking the table opens the on
 * screen home menu ({@code TableMenuScreen}); the menu's choices come back as
 * {@link TableActionPayload}s — sit and wait for a challenger in a chosen PvP
 * mode, challenge whoever is seated, or start a solo battle against the CPU.
 * Seats expire so a table never gets stuck.
 */
public final class DuelTables {

    private static final long SEAT_TTL_MS = 120_000L;
    private static final double MAX_REACH_SQ = 64.0;

    /** The PvP modes a seat can wait in. */
    public enum Mode {
        BO1("Best of 1", 1),
        BO3("Best of 3", 3),
        BO5("Best of 5", 5),
        DRAFT("Draft", 0);

        public final String label;
        final int bestOf; // 0 = not applicable (draft)

        Mode(String label, int bestOf) {
            this.label = label;
            this.bestOf = bestOf;
        }
    }

    private record Seat(UUID player, long at, Mode mode) {
    }

    private static final Map<BlockPos, Seat> SEATS = new ConcurrentHashMap<>();

    private DuelTables() {
    }

    /** Right-click on the table: send the client everything the menu needs. */
    public static void openMenu(BlockPos pos, ServerPlayer player) {
        BlockReach.opened(player, pos);
        BlockPos key = pos.immutable();
        Seat seat = validSeat(key);
        String name = "";
        int mode = Mode.BO3.ordinal();
        boolean self = false;
        if (seat != null) {
            self = seat.player().equals(player.getUUID());
            mode = seat.mode().ordinal();
            if (self) {
                name = player.getGameProfile().getName();
            } else {
                ServerPlayer seated = player.serverLevel().getServer()
                        .getPlayerList().getPlayer(seat.player());
                if (seated == null) {
                    SEATS.remove(key); // ghost seat — player left
                    name = "";
                    mode = Mode.BO3.ordinal();
                } else {
                    name = seated.getGameProfile().getName();
                }
            }
        }
        PacketDistributor.sendToPlayer(player, new TableMenuPayload(key, name, mode, self));
    }

    /** A menu choice arriving from the client. Everything is re-validated. */
    public static void handleAction(ServerPlayer player, TableActionPayload payload) {
        // the table has to be the one the server opened, and still in reach
        if (!BlockReach.canReach(player, payload.pos())) {
            return;
        }
        BlockPos key = payload.pos().immutable();
        if (key.distToCenterSqr(player.getX(), player.getY(), player.getZ()) > MAX_REACH_SQ) {
            return; // too far away to be a legitimate click
        }
        long now = System.currentTimeMillis();
        switch (payload.action()) {
            case TableActionPayload.SEAT -> {
                if (busy(player)) {
                    player.sendSystemMessage(err("Finish your current game first."));
                    return;
                }
                Seat seat = validSeat(key);
                if (seat != null && !seat.player().equals(player.getUUID())) {
                    player.sendSystemMessage(err("Someone is already seated — challenge them instead."));
                    return;
                }
                Mode mode = Mode.values()[clamp(payload.arg(), Mode.values().length)];
                SEATS.put(key, new Seat(player.getUUID(), now, mode));
                player.sendSystemMessage(Component.literal("You take a seat — ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(mode.label).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                        .append(Component.literal(". Waiting for a challenger to right-click the table...")
                                .withStyle(ChatFormatting.GREEN)));
                tableSound(player, key, 1.0F);
            }
            case TableActionPayload.STAND -> {
                Seat seat = SEATS.get(key);
                if (seat != null && seat.player().equals(player.getUUID())) {
                    SEATS.remove(key);
                    player.sendSystemMessage(Component.literal("You stand up from the table.")
                            .withStyle(ChatFormatting.GRAY));
                }
            }
            case TableActionPayload.CHALLENGE -> challenge(player, key);
            case TableActionPayload.CPU -> {
                if (busy(player)) {
                    player.sendSystemMessage(err("Finish your current game first."));
                    return;
                }
                Difficulty difficulty = Difficulty.values()[clamp(payload.arg(), Difficulty.values().length)];
                tableSound(player, key, 1.1F);
                TableBattleManager.start(player, difficulty, payload.useDeck());
            }
            case TableActionPayload.GUESS_WHO -> {
                if (busy(player)) {
                    player.sendSystemMessage(err("Finish your current game first."));
                    return;
                }
                tableSound(player, key, 1.2F);
                GuessWhoManager.openSolo(player);
            }
            case TableActionPayload.BLUFF -> {
                if (busy(player)) {
                    player.sendSystemMessage(err("Finish your current game first."));
                    return;
                }
                tableSound(player, key, 0.9F);
                BluffManager.open(player);
            }
            case TableActionPayload.TWENTY_ONE -> {
                if (busy(player)) {
                    player.sendSystemMessage(err("Finish your current game first."));
                    return;
                }
                tableSound(player, key, 1.3F);
                BlackjackManager.open(player);
            }
            default -> {
            }
        }
    }

    private static void challenge(ServerPlayer player, BlockPos key) {
        Seat seat = validSeat(key);
        if (seat == null || seat.player().equals(player.getUUID())) {
            player.sendSystemMessage(err("No one is waiting at this table right now."));
            return;
        }
        ServerPlayer opponent = player.serverLevel().getServer().getPlayerList().getPlayer(seat.player());
        if (opponent == null) {
            SEATS.remove(key);
            player.sendSystemMessage(err("They've gone offline — the seat is free now."));
            return;
        }
        if (busy(player)) {
            player.sendSystemMessage(err("Finish your current game first."));
            return;
        }
        if (busy(opponent)) {
            player.sendSystemMessage(err(opponent.getGameProfile().getName()
                    + " started another game while waiting — take the seat instead."));
            return;
        }
        SEATS.remove(key);
        if (seat.mode() == Mode.DRAFT) {
            if (!DraftManager.startDirect(opponent, player)) {
                player.sendSystemMessage(err("Couldn't start the draft — someone's already busy."));
            }
        } else {
            DuelManager.startFromTable(opponent, player, seat.mode().bestOf);
        }
    }

    private static Seat validSeat(BlockPos key) {
        Seat seat = SEATS.get(key);
        if (seat != null && System.currentTimeMillis() - seat.at() > SEAT_TTL_MS) {
            SEATS.remove(key);
            return null;
        }
        return seat;
    }

    private static boolean busy(ServerPlayer player) {
        return TableBattleManager.isInBattle(player)
                || DuelManager.isInDuel(player) || DraftManager.isDrafting(player);
    }

    private static void tableSound(ServerPlayer player, BlockPos pos, float pitch) {
        player.serverLevel().playSound(null, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, pitch);
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(value, size - 1));
    }

    /** Drop a player's seats when they leave, so tables don't hold ghosts. */
    public static void clearSeatsOf(UUID player) {
        SEATS.values().removeIf(s -> s.player().equals(player));
    }

    private static Component err(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }
}
