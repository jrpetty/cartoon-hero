package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Battle;
import com.jrpetty.mobtrumps.game.Difficulty;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.Stat;
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
 * Runs solo (vs CPU) battles started at a dueling table, played through the
 * on-screen {@code BattleScreen} rather than chat. The {@link Battle} game logic
 * and CPU difficulty are reused; this class owns the per-player phase machine
 * and streams each state to the client via {@link BattleSyncPayload}.
 */
public final class TableBattleManager {

    private static final int DECK_SIZE = 16;

    private static final class Game {
        final Battle battle;
        final Difficulty difficulty;
        final boolean useDeck;
        int phase;
        Battle.RoundResult lastResult;

        Game(Battle battle, Difficulty difficulty, boolean useDeck) {
            this.battle = battle;
            this.difficulty = difficulty;
            this.useDeck = useDeck;
            this.phase = BattleSyncPayload.PLAYER_PICK;
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
     * {@code useDeck} the player fights with their own custom deck (kill-tier
     * boosts included); otherwise they're dealt a random hand. The CPU always
     * gets the SAME number of cards, drawn on the collector curve — mostly
     * commons, a fair spread of the rest, and never more than one legendary.
     */
    public static void start(ServerPlayer player, Difficulty difficulty, boolean useDeck) {
        var rng = ThreadLocalRandom.current();
        List<MobCard> hand = useDeck ? DeckManager.deckCards(player) : List.of();
        boolean deckOk = hand.size() >= DeckManager.MIN_DECK;
        if (!deckOk) {
            hand = com.jrpetty.mobtrumps.game.MobCards.shuffledDeck(DECK_SIZE, rng);
        }
        List<MobCard> cpuHand = com.jrpetty.mobtrumps.game.MobCards.cpuDeck(hand.size(), rng);
        Battle battle = new Battle(hand, cpuHand, rng);
        battle.setDifficulty(difficulty);
        Game game = new Game(battle, difficulty, useDeck && deckOk);
        game.phase = battle.getTurn() == Battle.Side.CPU
                ? BattleSyncPayload.CPU_PICK : BattleSyncPayload.PLAYER_PICK;
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

    private static void resolve(ServerPlayer player, Game game, Stat stat) {
        game.lastResult = game.battle.playRound(stat);
        game.phase = BattleSyncPayload.RESULT;
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
            game.phase = BattleSyncPayload.FINISHED;
            StatsTracker.bump(player, "games_played");
            if (game.battle.getWinner() == Battle.Side.PLAYER) {
                StatsTracker.bump(player, "battle_wins");
                // per-difficulty tallies drive the Arena awards
                StatsTracker.bump(player, "battle_wins_"
                        + game.difficulty.name().toLowerCase(java.util.Locale.ROOT));
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
            }
            AchievementManager.refresh(player);
        } else {
            game.phase = game.battle.getTurn() == Battle.Side.CPU
                    ? BattleSyncPayload.CPU_PICK : BattleSyncPayload.PLAYER_PICK;
        }
        send(player, game);
    }

    /** Drop a player's game when they log out so nothing lingers. */
    public static void clear(UUID uuid) {
        GAMES.remove(uuid);
    }

    private static void send(ServerPlayer player, Game game) {
        Battle b = game.battle;
        boolean reveal = game.phase == BattleSyncPayload.RESULT
                || game.phase == BattleSyncPayload.FINISHED;
        String playerId;
        String cpuId = "";
        int chosen = -1;
        int chooser = 2;
        int winner = 2;
        if (reveal && game.lastResult != null) {
            playerId = game.lastResult.playerCard().id();
            cpuId = game.lastResult.cpuCard().id();
            chosen = game.lastResult.stat().ordinal();
            chooser = sideIdx(game.lastResult.chooser());
            winner = sideIdx(game.lastResult.winner());
        } else {
            MobCard top = b.playerTopCard();
            playerId = top == null ? "" : top.id();
        }
        if (game.phase == BattleSyncPayload.FINISHED) {
            winner = sideIdx(b.getWinner());
        }
        // 1 = the coin flip on a drawn round went to you, 2 = to the CPU
        Battle.Side coinSide = b.lastCoin();
        int coin = coinSide == Battle.Side.NONE ? 0 : (coinSide == Battle.Side.PLAYER ? 1 : 2);
        List<Integer> nums = new ArrayList<>(List.of(
                b.playerCardCount(), b.cpuCardCount(), b.potCount(), b.getRound(),
                chosen, chooser, winner, game.difficulty.ordinal(), 0, 0, 0, 0, coin));
        PacketDistributor.sendToPlayer(player,
                new BattleSyncPayload(game.phase, playerId, cpuId, nums, ""));
    }

    private static void sendClosed(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new BattleSyncPayload(
                BattleSyncPayload.CLOSED, "", "", List.of(0, 0, 0, 0, -1, 2, 2, 0, 0, 0, 0), ""));
    }

    private static int sideIdx(Battle.Side side) {
        return switch (side) {
            case PLAYER -> 0;
            case CPU -> 1;
            default -> 2;
        };
    }
}
