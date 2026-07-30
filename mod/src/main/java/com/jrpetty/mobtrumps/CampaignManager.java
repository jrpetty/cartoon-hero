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
 * <p>Each mission is one sixteen-card deck (see {@link CampaignDecks}) dealt
 * between the player and the opponent, so nobody has to own anything to play —
 * the mission supplies the cards. What the player's collection changes is
 * <em>power</em>: any card in the deal they have hunted plays at their earned
 * holo level, while the opponent's half plays at base.
 *
 * <p>Missions unlock in order and are cleared once. A clear that never drops a
 * single round is recorded as flawless, so a finished mission still has
 * something left to chase.
 */
public final class CampaignManager {

    public static final int CLEARED = 1;
    public static final int FLAWLESS = 2;

    private static final class Run {
        final CampaignMission mission;
        final Battle battle;
        int phase;
        int roundsLost;
        Battle.RoundResult lastResult;

        Run(CampaignMission mission, Battle battle) {
            this.mission = mission;
            this.battle = battle;
            this.phase = BattleSyncPayload.PLAYER_PICK;
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
        if (DuelManager.isInDuel(player) || TableBattleManager.isInBattle(player)) {
            player.sendSystemMessage(Component.literal("Finish your current game first.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        var rng = ThreadLocalRandom.current();

        // the mission's deck is fixed; only how it falls is random
        List<MobCard> deck = new ArrayList<>(CampaignDecks.deck(mission));
        Collections.shuffle(deck, java.util.Random.from(rng));

        int cpuCount = Math.min(deck.size() - 1, deck.size() / 2 + mission.cpuExtra());
        List<MobCard> cpuHand = new ArrayList<>(deck.subList(0, cpuCount));
        List<MobCard> playerHand = new ArrayList<>(deck.subList(cpuCount, deck.size()));

        // cards the player has actually hunted fight at their earned level
        playerHand = upgradeOwned(player, playerHand);

        Battle battle = new Battle(playerHand, cpuHand, rng);
        battle.setDifficulty(mission.brain());
        battle.setCardCounting(mission.counting());

        Run run = new Run(mission, battle);
        run.phase = battle.getTurn() == Battle.Side.CPU
                ? BattleSyncPayload.CPU_PICK : BattleSyncPayload.PLAYER_PICK;
        RUNS.put(player.getUUID(), run);

        player.sendSystemMessage(Component.literal("MISSION " + mission.index() + " · "
                        + mission.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("  " + mission.tagline())
                .withStyle(ChatFormatting.GRAY));
        BattleCommands.shuffleSound(player);
        send(player, run);
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

    private static void resolve(ServerPlayer player, Run run, Stat stat) {
        run.lastResult = run.battle.playRound(stat);
        if (run.lastResult.winner() == Battle.Side.CPU) {
            run.roundsLost++;
        }
        run.phase = BattleSyncPayload.RESULT;
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
            run.phase = BattleSyncPayload.FINISHED;
            if (run.battle.getWinner() == Battle.Side.PLAYER) {
                complete(player, run);
            }
            StatsTracker.bump(player, "games_played");
            AchievementManager.refresh(player);
        } else {
            run.phase = run.battle.getTurn() == Battle.Side.CPU
                    ? BattleSyncPayload.CPU_PICK : BattleSyncPayload.PLAYER_PICK;
        }
        send(player, run);
    }

    /** Record the clear, and hand over the Trophy on a first win. */
    private static void complete(ServerPlayer player, Run run) {
        CampaignMission mission = run.mission;
        Map<String, Integer> done = new HashMap<>(progress(player));
        int before = done.getOrDefault(mission.id(), 0);
        boolean flawless = run.roundsLost == 0;
        int now = Math.max(before, flawless ? FLAWLESS : CLEARED);
        done.put(mission.id(), now);
        player.setData(ModAttachments.CAMPAIGN.get(), done);
        sync(player);

        if (before == 0) {
            player.sendSystemMessage(Component.literal("★ MISSION " + mission.index()
                            + " CLEARED · " + mission.name())
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            grantTrophy(player, mission);
            CampaignRewards reward = CampaignRewards.forMission(mission.index());
            reward.grant(player);
            reward.announce(player);
            CampaignMission next = CampaignDecks.byIndex(mission.index() + 1);
            if (next != null) {
                player.sendSystemMessage(Component.literal("Unlocked: " + next.name())
                        .withStyle(ChatFormatting.GREEN));
            } else {
                player.sendSystemMessage(Component.literal(
                                "The campaign is finished. Every mission is yours.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            }
        }
        if (flawless && before < FLAWLESS) {
            player.sendSystemMessage(Component.literal("FLAWLESS — not a single round dropped.")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
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
    private static void grantTrophy(ServerPlayer player, CampaignMission mission) {
        MobCard card = MobCards.byId(mission.trophyMob());
        if (card == null) {
            return;
        }
        ItemStack trophy = MobCardItem.issued(player, card, CardEdition.TROPHY);
        CardActions.give(player, trophy);
        CollectionTracker.record(player, card.id(), false);
        player.sendSystemMessage(Component.literal("Trophy awarded: ")
                .withStyle(ChatFormatting.GRAY)
                .append(trophy.getHoverName().copy().withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" — a Trophy print, only ever won.")
                        .withStyle(ChatFormatting.DARK_GRAY)));
    }

    // --- screen sync --------------------------------------------------------

    private static void send(ServerPlayer player, Run run) {
        Battle b = run.battle;
        boolean reveal = run.phase == BattleSyncPayload.RESULT
                || run.phase == BattleSyncPayload.FINISHED;
        String playerId;
        String cpuId = "";
        int chosen = -1;
        int chooser = 2;
        int winner = 2;
        if (reveal && run.lastResult != null) {
            playerId = run.lastResult.playerCard().id();
            cpuId = run.lastResult.cpuCard().id();
            chosen = run.lastResult.stat().ordinal();
            chooser = sideIdx(run.lastResult.chooser());
            winner = sideIdx(run.lastResult.winner());
        } else {
            MobCard top = b.playerTopCard();
            playerId = top == null ? "" : top.id();
        }
        if (run.phase == BattleSyncPayload.FINISHED) {
            winner = sideIdx(b.getWinner());
        }
        Battle.Side coinSide = b.lastCoin();
        int coin = coinSide == Battle.Side.NONE ? 0 : (coinSide == Battle.Side.PLAYER ? 1 : 2);
        List<Integer> nums = new ArrayList<>(List.of(
                b.playerCardCount(), b.cpuCardCount(), b.potCount(), b.getRound(),
                chosen, chooser, winner, run.mission.brain().ordinal(), 0, 0, 0, 0, coin));
        PacketDistributor.sendToPlayer(player, new BattleSyncPayload(
                run.phase, playerId, cpuId, nums,
                "M" + run.mission.index() + " " + run.mission.name()));
    }

    private static void sendClosed(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new BattleSyncPayload(
                BattleSyncPayload.CLOSED, "", "", List.of(0, 0, 0, 0, -1, 2, 2, 0, 0, 0, 0, 0, 0), ""));
    }

    private static int sideIdx(Battle.Side side) {
        return switch (side) {
            case PLAYER -> 0;
            case CPU -> 1;
            default -> 2;
        };
    }
}
