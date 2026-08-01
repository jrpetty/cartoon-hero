package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.GuessQuestion;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Guess Who: one hidden mob, eighty-one faces, and a fixed list of questions.
 *
 * <p><b>The elimination happens here, not on the client.</b> That is the whole
 * design. The server holds the hidden mob and the set still standing; a question
 * arrives as "template 4, value 7", the server answers it against the mob it is
 * hiding, strikes off everything that would have answered the other way, and
 * sends back what is left. The client draws that and nothing else.
 *
 * <p>So there is nothing to cheat with. A player cannot decline to eliminate a
 * card they would rather keep, cannot miscount in their own favour, and a
 * modified client cannot ask what the mob is — the only reply it ever receives
 * is a yes, a no, and a shorter list of faces. The hidden mob's id is not sent
 * until the game is over.
 *
 * <p>Against the house for now, the same as Twenty-One: the server hides a mob
 * and you hunt it. Two players hiding a mob from each other is the same machinery
 * with a second board, and nothing here would have to change to allow it.
 */
public final class GuessWhoManager {

    /** A wrong name ends the game — so the last question is a real decision. */
    public static final int PHASE_PLAYING = 0;
    public static final int PHASE_WON = 1;
    public static final int PHASE_LOST = 2;

    /** One question that was asked, and what came back. */
    private record Asked(int template, int value, boolean answer) {
    }

    private static final class Board {
        MobCard secret;
        final List<MobCard> alive = new ArrayList<>(MobCards.ALL);
        final List<Asked> log = new ArrayList<>();
        int phase = PHASE_PLAYING;
    }

    private static final Map<UUID, Board> BOARDS = new ConcurrentHashMap<>();

    private GuessWhoManager() {
    }

    public static void open(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new GuessWhoMenuPayload());
        BOARDS.computeIfAbsent(player.getUUID(), id -> newBoard());
        send(player);
    }

    private static Board newBoard() {
        Board board = new Board();
        board.secret = MobCards.ALL.get(ThreadLocalRandom.current().nextInt(MobCards.ALL.size()));
        return board;
    }

    public static void handle(ServerPlayer player, int action, int template, int value,
                              String mobId) {
        switch (action) {
            case GuessWhoActionPayload.NEW_GAME -> {
                BOARDS.put(player.getUUID(), newBoard());
                sound(player, SoundEvents.BOOK_PAGE_TURN, 1.3F);
                send(player);
            }
            case GuessWhoActionPayload.ASK -> ask(player, template, value);
            case GuessWhoActionPayload.GUESS -> guess(player, mobId);
            case GuessWhoActionPayload.LEAVE -> BOARDS.remove(player.getUUID());
            default -> {
            }
        }
    }

    /**
     * Ask a question. The server answers it about the mob it is hiding, then
     * removes every face that would have answered differently.
     */
    private static void ask(ServerPlayer player, int templateIndex, int value) {
        Board board = BOARDS.get(player.getUUID());
        if (board == null || board.phase != PHASE_PLAYING || !BlockReach.canReach(player)) {
            return;
        }
        List<GuessQuestion.Template> all = GuessQuestion.TEMPLATES;
        if (templateIndex < 0 || templateIndex >= all.size()) {
            return;
        }
        GuessQuestion.Template template = all.get(templateIndex);
        int v = template.needsValue() ? template.clamp(value) : 0;
        // a question that cannot separate anything would burn a turn for nothing
        if (!GuessQuestion.isUseful(board.alive, template, v)) {
            player.sendSystemMessage(Component.literal(
                            "Every face left answers that the same way — ask something else.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        boolean answer = GuessQuestion.ask(template, v, board.secret);
        int before = board.alive.size();
        List<MobCard> left = GuessQuestion.eliminate(board.alive, template, v, answer);
        board.alive.clear();
        board.alive.addAll(left);
        board.log.add(new Asked(templateIndex, v, answer));
        sound(player, answer ? SoundEvents.NOTE_BLOCK_BELL.value()
                : SoundEvents.NOTE_BLOCK_BASS.value(), answer ? 1.4F : 0.8F);
        player.sendSystemMessage(Component.literal(
                        (answer ? "YES" : "NO") + " — " + template.text(v) + "  ("
                                + before + " → " + board.alive.size() + " left)")
                .withStyle(answer ? ChatFormatting.GREEN : ChatFormatting.RED));
        send(player);
    }

    /** Name a mob. Right wins; wrong ends the game. */
    private static void guess(ServerPlayer player, String mobId) {
        Board board = BOARDS.get(player.getUUID());
        if (board == null || board.phase != PHASE_PLAYING || !BlockReach.canReach(player)) {
            return;
        }
        MobCard named = MobCards.byId(mobId);
        if (named == null) {
            return;
        }
        // naming a face already struck off is a misclick, not a move
        boolean stillUp = board.alive.stream().anyMatch(c -> c.id().equals(named.id()));
        if (!stillUp) {
            return;
        }
        boolean right = named.id().equals(board.secret.id());
        board.phase = right ? PHASE_WON : PHASE_LOST;
        StatsTracker.bump(player, "guesswho_played");
        if (right) {
            StatsTracker.bump(player, "guesswho_wins");
            RecyclerManager.giveFragments(player, reward(board.log.size()));
            player.sendSystemMessage(Component.literal(
                            "Guess Who: " + named.displayName() + ", in "
                                    + board.log.size() + " question"
                                    + (board.log.size() == 1 ? "" : "s") + " — "
                                    + reward(board.log.size()) + " fragments.")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        } else {
            player.sendSystemMessage(Component.literal(
                            "Guess Who: not " + named.displayName() + " — it was "
                                    + board.secret.displayName() + ".")
                    .withStyle(ChatFormatting.RED));
        }
        sound(player, right ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ITEM_BREAK,
                right ? 1.2F : 0.8F);
        send(player);
    }

    /**
     * What finding it pays. The theoretical floor is log2(81) ≈ 6.34 questions
     * and a careful player needs six or seven, so the ladder rewards the rare
     * genuinely sharp game without making a normal one feel like a failure.
     */
    public static int reward(int questions) {
        if (questions <= 5) {
            return 24;
        }
        if (questions <= 7) {
            return 12;
        }
        if (questions <= 10) {
            return 6;
        }
        return 2;
    }

    private static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent event,
                              float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                event, SoundSource.PLAYERS, 0.7F, pitch);
    }

    public static void handleLogout(ServerPlayer player) {
        BOARDS.remove(player.getUUID());
    }

    // --- wire format ---------------------------------------------------------

    private static void send(ServerPlayer player) {
        Board board = BOARDS.get(player.getUUID());
        if (board == null) {
            return;
        }
        List<String> alive = new ArrayList<>(board.alive.size());
        for (MobCard card : board.alive) {
            alive.add(card.id());
        }
        List<String> log = new ArrayList<>(board.log.size());
        for (Asked asked : board.log) {
            log.add(asked.template() + "|" + asked.value() + "|" + (asked.answer() ? 1 : 0));
        }
        // the hidden mob crosses the wire only once the game is settled — until
        // then no packet a client can capture contains the answer
        String secret = board.phase == PHASE_PLAYING ? "" : board.secret.id();
        PacketDistributor.sendToPlayer(player, new GuessWhoSyncPayload(
                board.phase, board.log.size(), alive, log, secret));
    }
}
