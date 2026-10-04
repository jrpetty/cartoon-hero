package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Battle;
import com.jrpetty.mobtrumps.game.CampaignDecks;
import com.jrpetty.mobtrumps.game.CampaignMission;
import com.jrpetty.mobtrumps.game.CardEdition;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The twenty-mission campaign.
 *
 * <p>Six against six. The mission fields its own themed deck (see
 * {@link CampaignDecks}) and the player fields six cards out of their
 * Collection Book, played at whatever holo level they have earned on each.
 * The campaign therefore does not open until the book holds
 * {@value #REQUIRED_CARDS} cards — the collection is the entry fee, and what
 * you have hunted is what you take into the fight.
 *
 * <p>Like the CPU table, the opponent takes its own turns after a moment's
 * thought, and everything a mission pays — the clear, the Trophy, the reward,
 * the next mission unlocking — is told on the result panel, not in chat.
 *
 * <p>Missions unlock in order and are cleared once. A clear that never drops a
 * single round is recorded as flawless, so a finished mission still has
 * something left to chase.
 */
public final class CampaignManager {

    public static final int CLEARED = 1;
    public static final int FLAWLESS = 2;

    /**
     * Cards that must be filed in the Collection Book before the campaign
     * opens at all. You bring your own hand and the mission brings its own,
     * so until the book can field a full hand there is nothing to play with.
     */
    public static final int REQUIRED_CARDS = CampaignDecks.DECK_SIZE;

    private static final class Run {
        final CampaignMission mission;
        final Battle battle;
        final long startedMs = System.currentTimeMillis();
        int phase;
        long phaseAt = System.currentTimeMillis();
        int roundsLost;
        int xp;
        String note = "";
        String note2 = "";
        Battle.RoundResult lastResult;

        Run(CampaignMission mission, Battle battle) {
            this.mission = mission;
            this.battle = battle;
            this.phase = BattleSyncPayload.PLAYER_PICK;
        }

        void enter(int next) {
            phase = next;
            phaseAt = System.currentTimeMillis();
        }
    }

    private static final Map<UUID, Run> RUNS = new ConcurrentHashMap<>();

    private CampaignManager() {
    }

    public static boolean isPlaying(ServerPlayer player) {
        return RUNS.containsKey(player.getUUID());
    }

    public static void clear(UUID uuid) {
        RUNS.remove(uuid);
    }

    // --- progress -----------------------------------------------------------

    public static Map<String, Integer> progress(ServerPlayer player) {
        return player.getData(ModAttachments.CAMPAIGN.get());
    }

    /** The highest mission number cleared, 0 if none. */
    public static int highestCleared(ServerPlayer player) {
        Map<String, Integer> done = progress(player);
        int best = 0;
        for (CampaignMission m : CampaignDecks.ALL) {
            if (done.getOrDefault(m.id(), 0) > 0) {
                best = Math.max(best, m.index());
            }
        }
        return best;
    }

    /** A mission is playable once everything before it has been cleared. */
    public static boolean isUnlocked(ServerPlayer player, CampaignMission mission) {
        return mission.index() <= highestCleared(player) + 1;
    }

    /** How many cards are filed in the book, plain and foil. */
    public static int filedCards(ServerPlayer player) {
        return player.getData(ModAttachments.STORED.get()).size()
                + player.getData(ModAttachments.STORED_FOIL.get()).size();
    }

    /** The campaign is shut until the book can field a full hand. */
    public static boolean canPlay(ServerPlayer player) {
        return filedCards(player) >= REQUIRED_CARDS;
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new CampaignSyncPayload(progress(player)));
    }

    // --- playing ------------------------------------------------------------

    /** Deal a mission and open the battle screen. */
    public static void begin(ServerPlayer player, int index) {
        CampaignMission mission = CampaignDecks.byIndex(index);
        if (mission == null || !isUnlocked(player, mission)) {
            return;
        }
        // refusals land on the action bar, over the briefing the player is
        // looking at, rather than in chat behind it
        if (DuelManager.isInDuel(player) || TableBattleManager.isInBattle(player)) {
            player.displayClientMessage(Component.literal("Finish your current game first.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!canPlay(player)) {
            player.displayClientMessage(Component.literal("The campaign needs "
                            + REQUIRED_CARDS + " cards filed in your Collection Book — you have "
                            + filedCards(player) + ".")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        var rng = ThreadLocalRandom.current();

        // You bring six of your own; the mission brings its own six. What the
        // collection changes is no longer just power, it is what you can field
        // at all.
        List<MobCard> playerHand = playerDeck(player);
        if (playerHand.size() < CampaignDecks.DECK_SIZE) {
            player.displayClientMessage(Component.literal(
                            "Your book cannot field " + CampaignDecks.DECK_SIZE + " cards yet.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        // the opponent fields its deck as premium prints where the mission says
        // so -- the early categories are simply weak cards, and a base-print
        // farm animal cannot make a game of it against a real collection
        List<MobCard> cpuHand = new ArrayList<>();
        for (MobCard card : CampaignDecks.cpuDeck(mission)) {
            cpuHand.add(card.upgraded(mission.cpuLevel()));
        }
        Collections.shuffle(cpuHand, java.util.Random.from(rng));
        Collections.shuffle(playerHand, java.util.Random.from(rng));

        Battle battle = new Battle(playerHand, cpuHand, rng);
        battle.setDifficulty(mission.brain());
        battle.setCardCounting(mission.counting());

        Run run = new Run(mission, battle);
        run.enter(battle.getTurn() == Battle.Side.CPU
                ? BattleSyncPayload.CPU_PICK : BattleSyncPayload.PLAYER_PICK);
        RUNS.put(player.getUUID(), run);

        // the mission's name rides the battle screen's header the whole game
        BattleCommands.shuffleSound(player);
        send(player, run);
    }

    /**
     * The six cards the player takes into a mission.
     *
     * <p>Their saved battle deck first, filtered to what is actually filed in
     * the book — a deck entry whose card has been sold or pocketed cannot be
     * played — then topped up from the rest of the book so a player who has
     * never opened the deck builder can still start. Every card plays at the
     * holo level its owner has earned.
     */
    private static List<MobCard> playerDeck(ServerPlayer player) {
        java.util.Set<String> filed = new java.util.LinkedHashSet<>(
                player.getData(ModAttachments.STORED.get()));
        filed.addAll(player.getData(ModAttachments.STORED_FOIL.get()));

        java.util.LinkedHashSet<String> chosen = new java.util.LinkedHashSet<>();
        for (String id : player.getData(ModAttachments.DECK.get())) {
            if (filed.contains(id) && chosen.size() < CampaignDecks.DECK_SIZE) {
                chosen.add(id);
            }
        }
        for (String id : filed) {
            if (chosen.size() >= CampaignDecks.DECK_SIZE) {
                break;
            }
            chosen.add(id);
        }

        List<MobCard> hand = new ArrayList<>(chosen.size());
        for (String id : chosen) {
            MobCard card = MobCards.byId(id);
            if (card != null) {
                hand.add(card);
            }
        }
        return upgradeOwned(player, hand);
    }

    /** Apply the player's holo levels to any card in the deal that they own. */
    private static List<MobCard> upgradeOwned(ServerPlayer player, List<MobCard> hand) {
        List<String> collected = player.getData(ModAttachments.COLLECTED.get());
        if (collected.isEmpty()) {
            return hand;
        }
        java.util.Set<String> owned = new java.util.HashSet<>(collected);
        java.util.Set<String> foils =
                new java.util.HashSet<>(player.getData(ModAttachments.COLLECTED_FOIL.get()));
        Map<String, Integer> kills = player.getData(ModAttachments.KILLS.get());
        List<MobCard> out = new ArrayList<>(hand.size());
        for (MobCard card : hand) {
            out.add(owned.contains(card.id())
                    ? card.effective(foils.contains(card.id()), kills.getOrDefault(card.id(), 0))
                    : card);
        }
        return out;
    }

    /** Handle a battle-screen action during a mission. */
    public static void action(ServerPlayer player, int action, int statIdx) {
        Run run = RUNS.get(player.getUUID());
        if (run == null) {
            return;
        }
        switch (action) {
            case BattleActionPayload.PICK -> {
                if (run.phase == BattleSyncPayload.PLAYER_PICK
                        && run.battle.getTurn() == Battle.Side.PLAYER) {
                    Stat[] all = Stat.values();
                    if (statIdx >= 0 && statIdx < all.length) {
                        StatsTracker.recordPick(player, all[statIdx]);
                        resolve(player, run, all[statIdx]);
                    }
                }
            }
            case BattleActionPayload.NEXT -> {
                // only for the state the client was looking at — see ticket()
                if (statIdx != BattleActionPayload.ticket(run.phase, run.battle.getRound())) {
                    return;
                }
                if (run.phase == BattleSyncPayload.CPU_PICK) {
                    resolve(player, run, run.battle.cpuChoice());
                } else if (run.phase == BattleSyncPayload.RESULT) {
                    advance(player, run);
                }
            }
            case BattleActionPayload.PLAY_AGAIN -> {
                if (run.phase == BattleSyncPayload.FINISHED) {
                    begin(player, run.mission.index());
                }
            }
            case BattleActionPayload.FORFEIT -> {
                RUNS.remove(player.getUUID());
                sendClosed(player);
            }
            default -> {
            }
        }
    }

    /** The mission's opponent plays once it has thought for as long as the CPU table's does. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (RUNS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Run> entry : RUNS.entrySet()) {
            Run run = entry.getValue();
            if (run.phase != BattleSyncPayload.CPU_PICK
                    || now - run.phaseAt < TableBattleManager.CPU_THINK_MS) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                resolve(player, run, run.battle.cpuChoice());
            }
        }
    }

    private static void resolve(ServerPlayer player, Run run, Stat stat) {
        run.lastResult = run.battle.playRound(stat);
        if (run.lastResult.winner() == Battle.Side.CPU) {
            run.roundsLost++;
        }
        run.enter(BattleSyncPayload.RESULT);
        float pitch = switch (run.lastResult.winner()) {
            case PLAYER -> 1.3F;
            case CPU -> 0.7F;
            default -> 1.0F;
        };
        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6F, pitch);
        send(player, run);
    }

    private static void advance(ServerPlayer player, Run run) {
        if (run.battle.isFinished()) {
            run.enter(BattleSyncPayload.FINISHED);
            run.xp = GameRewards.payGame(player, run.startedMs, false);
            if (run.battle.getWinner() == Battle.Side.PLAYER) {
                complete(player, run);
            } else {
                run.note = "The mission stands. Change your six and try again.";
                run.note2 = "";
            }
            StatsTracker.bump(player, "games_played");
            AchievementManager.refresh(player);
        } else {
            run.enter(run.battle.getTurn() == Battle.Side.CPU
                    ? BattleSyncPayload.CPU_PICK : BattleSyncPayload.PLAYER_PICK);
        }
        send(player, run);
    }

    /**
     * Record the clear, and hand over the Trophy on a first win. What it paid
     * is written onto the run, for the result panel to tell the player.
     */
    private static void complete(ServerPlayer player, Run run) {
        CampaignMission mission = run.mission;
        Map<String, Integer> done = new HashMap<>(progress(player));
        int before = done.getOrDefault(mission.id(), 0);
        boolean flawless = run.roundsLost == 0;
        int now = Math.max(before, flawless ? FLAWLESS : CLEARED);
        done.put(mission.id(), now);
        player.setData(ModAttachments.CAMPAIGN.get(), done);
        sync(player);

        String perfect = flawless && before < FLAWLESS ? " · FLAWLESS" : "";
        if (before == 0) {
            MobCard trophy = grantTrophy(player, mission);
            CampaignRewards reward = CampaignRewards.forMission(mission.index());
            reward.grant(player);
            run.note = "Mission " + mission.index() + " cleared" + perfect
                    + (trophy == null ? "" : " · Trophy: " + trophy.displayName());
            CampaignMission next = CampaignDecks.byIndex(mission.index() + 1);
            String paid = reward.label();
            run.note2 = (paid.isEmpty() ? "" : "Reward: " + paid + "  ·  ")
                    + (next != null ? "Unlocked: " + next.name() : "Every mission is yours");
        } else {
            run.note = flawless && before < FLAWLESS
                    ? "FLAWLESS — not a single round dropped"
                    : "Mission cleared again";
            run.note2 = "";
        }
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.0F);
        player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.6, 0.7, 0.6, 0.2);
    }

    /**
     * The mission's Trophy card. Stat-identical to any other print of that mob —
     * its whole worth is that only clearing the mission produces one.
     */
    private static MobCard grantTrophy(ServerPlayer player, CampaignMission mission) {
        MobCard card = MobCards.byId(mission.trophyMob());
        if (card == null) {
            return null;
        }
        ItemStack trophy = MobCardItem.issued(player, card, CardEdition.TROPHY);
        CardActions.give(player, trophy);
        CollectionTracker.record(player, card.id(), false);
        return card;
    }

    // --- screen sync --------------------------------------------------------

    private static void send(ServerPlayer player, Run run) {
        boolean reveal = run.phase == BattleSyncPayload.RESULT
                || run.phase == BattleSyncPayload.FINISHED;
        // MISSION marks this as a campaign game, so closing the battle screen
        // puts the player back on the route instead of dumping them in the
        // world; the levels BattleView sends carry the opponent's upgraded
        // prints, so the screen draws the numbers the round was decided on
        BattleView view = BattleView.of(run.phase, run.battle, Battle.Side.PLAYER)
                .shown(reveal ? run.lastResult : null)
                .difficulty(run.mission.brain().ordinal())
                .mission(run.mission.index())
                .counts(true)
                .label("M" + run.mission.index() + " " + run.mission.name());
        if (run.phase == BattleSyncPayload.FINISHED) {
            view.summary().xp(run.xp).notes(run.note, run.note2);
        }
        PacketDistributor.sendToPlayer(player, view.build());
    }

    private static void sendClosed(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, BattleView.closed());
    }
}
