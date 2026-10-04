package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Battle;
import com.jrpetty.mobtrumps.game.Difficulty;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs solo (vs CPU) battles started at a dueling table, played through the
 * on-screen {@code BattleScreen} rather than chat. The {@link Battle} game logic
 * and CPU difficulty are reused; this class owns the per-player phase machine
 * and streams each state to the client via {@link BattleSyncPayload}.
 *
 * <p>Both sides hold {@link Battle#HAND_SIZE} cards. The CPU takes its own
 * turns: it "thinks" for {@link #CPU_THINK_MS} and then plays, so a game never
 * stalls on a button whose only job was to let the computer move.
 */
public final class TableBattleManager {

    /** How long the CPU appears to think before it plays its stat. */
    static final long CPU_THINK_MS = 1_100L;

    private static final class Game {
        final Battle battle;
        final Difficulty difficulty;
        final boolean useDeck;
        /**
         * A "Random deal" game costs nothing to enter — no collection, no deck,
         * no hunting — so it is practice only and never feeds wins, games
         * played or any award. Only a game fought with your OWN deck counts.
         */
        final boolean ranked;
        final long startedMs = System.currentTimeMillis();
        int phase;
        /** When the current phase began, for the CPU's thinking time. */
        long phaseAt = System.currentTimeMillis();
        int xp;
        Battle.RoundResult lastResult;

        Game(Battle battle, Difficulty difficulty, boolean useDeck, boolean ranked) {
            this.battle = battle;
            this.difficulty = difficulty;
            this.useDeck = useDeck;
            this.ranked = ranked;
            this.phase = BattleSyncPayload.PLAYER_PICK;
        }

        void enter(int next) {
            phase = next;
            phaseAt = System.currentTimeMillis();
        }
    }

    private static final Map<UUID, Game> GAMES = new ConcurrentHashMap<>();

    private TableBattleManager() {
    }

    public static boolean isInBattle(ServerPlayer player) {
        return GAMES.containsKey(player.getUUID());
    }

    /**
     * Deal a fresh battle at the chosen difficulty and open the screen. With
     * {@code useDeck} the player fights with their own six-card deck (kill-tier
     * boosts included); otherwise they're dealt a random hand. The CPU always
     * gets the SAME number of cards, drawn on the collector curve — mostly
     * commons, a fair spread of the rest, and never more than one legendary.
     */
    public static void start(ServerPlayer player, Difficulty difficulty, boolean useDeck) {
        var rng = ThreadLocalRandom.current();
        List<MobCard> hand = useDeck ? DeckManager.deckCards(player) : List.of();
        boolean deckOk = hand.size() == Battle.HAND_SIZE;
        List<Integer> levels = deckOk ? DeckManager.deckLevels(player) : List.of();
        Set<String> mine = deckOk ? DeckManager.deckIds(player) : new java.util.HashSet<>();
        if (!deckOk) {
            hand = MobCards.shuffledDeck(Battle.HAND_SIZE, rng);
            for (MobCard c : hand) mine.add(c.id());
        }
        // the CPU brings DIFFERENT mobs, upgraded to the same degree as yours —
        // otherwise a well-hunted deck of boosted cards walks every battle
        List<MobCard> cpuHand = MobCards.matchLevels(
                MobCards.cpuDeck(hand.size(), rng, mine), levels, rng);
        Battle battle = new Battle(hand, cpuHand, rng);
        battle.setDifficulty(difficulty);
        Game game = new Game(battle, difficulty, useDeck, useDeck && deckOk);
        game.enter(battle.getTurn() == Battle.Side.CPU
                ? BattleSyncPayload.CPU_PICK : BattleSyncPayload.PLAYER_PICK);
        GAMES.put(player.getUUID(), game);
        BattleCommands.shuffleSound(player);
        send(player, game);
    }

    /** Handle a client action from the battle screen. */
    public static void action(ServerPlayer player, int action, int statIdx) {
        Game game = GAMES.get(player.getUUID());
        if (game == null) {
            return;
        }
        switch (action) {
            case BattleActionPayload.PICK -> {
                if (game.phase == BattleSyncPayload.PLAYER_PICK
                        && game.battle.getTurn() == Battle.Side.PLAYER) {
                    Stat[] all = Stat.values();
                    if (statIdx >= 0 && statIdx < all.length) {
                        StatsTracker.recordPick(player, all[statIdx]);
                        resolve(player, game, all[statIdx]);
                    }
                }
            }
            case BattleActionPayload.NEXT -> {
                // only honoured for the state the client was actually looking
                // at, so a double press can never skip a round unseen
                if (statIdx != BattleActionPayload.ticket(game.phase, game.battle.getRound())) {
                    return;
                }
                if (game.phase == BattleSyncPayload.CPU_PICK) {
                    resolve(player, game, game.battle.cpuChoice());
                } else if (game.phase == BattleSyncPayload.RESULT) {
                    advance(player, game);
                }
            }
            case BattleActionPayload.PLAY_AGAIN -> {
                if (game.phase == BattleSyncPayload.FINISHED) {
                    start(player, game.difficulty, game.useDeck);
                }
            }
            case BattleActionPayload.FORFEIT -> {
                GAMES.remove(player.getUUID());
                sendClosed(player);
            }
            default -> {
            }
        }
    }

    /** The CPU plays once it has "thought" for {@link #CPU_THINK_MS} (every server tick). */
    public static void tick(MinecraftServer server) {
        if (GAMES.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Game> entry : GAMES.entrySet()) {
            Game game = entry.getValue();
            if (game.phase != BattleSyncPayload.CPU_PICK || now - game.phaseAt < CPU_THINK_MS) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                resolve(player, game, game.battle.cpuChoice());
            }
        }
    }

    private static void resolve(ServerPlayer player, Game game, Stat stat) {
        game.lastResult = game.battle.playRound(stat);
        game.enter(BattleSyncPayload.RESULT);
        float pitch = switch (game.lastResult.winner()) {
            case PLAYER -> 1.3F;
            case CPU -> 0.7F;
            default -> 1.0F;
        };
        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6F, pitch);
        send(player, game);
    }

    private static void advance(ServerPlayer player, Game game) {
        if (game.battle.isFinished()) {
            game.enter(BattleSyncPayload.FINISHED);
            boolean won = game.battle.getWinner() == Battle.Side.PLAYER;
            // every finished game pays its experience, practice deals included —
            // only wins and awards are kept for games fought with your own deck
            game.xp = GameRewards.payGame(player, game.startedMs, false);
            if (game.ranked) {
                StatsTracker.bump(player, "games_played");
                if (won) {
                    StatsTracker.bump(player, "battle_wins");
                    // per-difficulty tallies drive the Arena awards
                    StatsTracker.bump(player, "battle_wins_"
                            + game.difficulty.name().toLowerCase(java.util.Locale.ROOT));
                }
                AchievementManager.refresh(player);
            }
            if (won) {
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
            }
        } else {
            game.enter(game.battle.getTurn() == Battle.Side.CPU
                    ? BattleSyncPayload.CPU_PICK : BattleSyncPayload.PLAYER_PICK);
        }
        send(player, game);
    }

    /** Drop a player's game when they log out so nothing lingers. */
    public static void clear(UUID uuid) {
        GAMES.remove(uuid);
    }

    private static void send(ServerPlayer player, Game game) {
        boolean reveal = game.phase == BattleSyncPayload.RESULT
                || game.phase == BattleSyncPayload.FINISHED;
        BattleView view = BattleView.of(game.phase, game.battle, Battle.Side.PLAYER)
                .shown(reveal ? game.lastResult : null)
                .difficulty(game.difficulty.ordinal())
                .counts(game.ranked);
        if (game.phase == BattleSyncPayload.FINISHED) {
            view.summary().xp(game.xp).notes(game.ranked
                    ? "Counts toward your wins and Arena awards"
                    : "A random deal: practice only — build a deck to play for keeps", "");
        }
        PacketDistributor.sendToPlayer(player, view.build());
    }

    private static void sendClosed(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, BattleView.closed());
    }
}
