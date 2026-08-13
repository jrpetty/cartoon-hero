package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Achievement;
import com.jrpetty.mobtrumps.game.Achievements;
import com.jrpetty.mobtrumps.game.Category;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Gathers everything the collection book's Profile page shows into one packet.
 *
 * <p>Every number here is read from the counter that already owns it —
 * {@link StatsTracker}, {@link Leaderboard}, {@link MatchHistory}, the
 * attachments — rather than kept in a second tally that could drift. The
 * collection figures go through {@link AchievementManager#metric} for the same
 * reason: "sets finished" and "cards at Holo III" are exactly the numbers the
 * awards are judged on, so the page cannot disagree with the award sitting
 * next to it.
 */
public final class PlayerProfile {

    /** Rivals are the page's longest list; past this it stops being readable. */
    public static final int MAX_RIVALS = 6;

    private PlayerProfile() {
    }

    public static ProfileSyncPayload snapshot(ServerPlayer player) {
        Leaderboard board = Leaderboard.get(player.serverLevel().getServer());
        Leaderboard.Entry me = board.entry(player.getUUID());

        int rating = me == null ? Leaderboard.START : me.rating();
        int collected = player.getData(ModAttachments.COLLECTED.get()).size();

        int[] n = new int[ProfileSyncPayload.HEADER];
        n[ProfileSyncPayload.RATING] = rating;
        // The peak on the leaderboard is the season's; ranked_peak is the best
        // ever held. Show the larger, so a decayed rating never makes a player's
        // own high-water mark appear to fall.
        n[ProfileSyncPayload.PEAK] = Math.max(me == null ? rating : me.peak(),
                StatsTracker.count(player, "ranked_peak"));
        // rankOf is 1-based and returns -1 when unranked; the page reads 0 as
        // "not on the ladder", so normalise here rather than at both ends.
        n[ProfileSyncPayload.PLACE] = Math.max(0, board.rankOf(player.getUUID()));
        n[ProfileSyncPayload.TOTAL_RANKED] = board.rankedCount();
        n[ProfileSyncPayload.LB_WINS] = me == null ? 0 : me.wins();
        n[ProfileSyncPayload.LB_LOSSES] = me == null ? 0 : me.losses();
        n[ProfileSyncPayload.STREAK] = StatsTracker.count(player, "ranked_streak");
        n[ProfileSyncPayload.STREAK_BEST] = StatsTracker.count(player, "ranked_streak_best");
        n[ProfileSyncPayload.GIANT] = StatsTracker.count(player, "ranked_giant");
        n[ProfileSyncPayload.BADGES] = player.getData(ModAttachments.RANKED_BADGES.get()).size();
        n[ProfileSyncPayload.SEASON] = board.season();
        n[ProfileSyncPayload.RANKED_WINS] = StatsTracker.count(player, "ranked_wins");
        n[ProfileSyncPayload.RANKED_LOSSES] = StatsTracker.count(player, "ranked_losses");
        n[ProfileSyncPayload.DUEL_WINS] = player.getData(ModAttachments.DUEL_WINS.get());

        n[ProfileSyncPayload.CPU_EASY] = StatsTracker.count(player, "battle_wins_easy");
        n[ProfileSyncPayload.CPU_NORMAL] = StatsTracker.count(player, "battle_wins_normal");
        n[ProfileSyncPayload.CPU_HARD] = StatsTracker.count(player, "battle_wins_hard");
        n[ProfileSyncPayload.CPU_TOTAL] = StatsTracker.count(player, "battle_wins");
        n[ProfileSyncPayload.GAMES] = StatsTracker.count(player, "games_played");

        n[ProfileSyncPayload.T21_PLAYED] = StatsTracker.count(player, "twentyone_played");
        n[ProfileSyncPayload.T21_WINS] = StatsTracker.count(player, "twentyone_wins");
        n[ProfileSyncPayload.T21_EXACT] = StatsTracker.count(player, "twentyone_exact");
        n[ProfileSyncPayload.GW_PLAYED] = StatsTracker.count(player, "guesswho_played");
        n[ProfileSyncPayload.GW_WINS] = StatsTracker.count(player, "guesswho_wins");
        n[ProfileSyncPayload.GW_SWIFT] = StatsTracker.count(player, "guesswho_swift");
        n[ProfileSyncPayload.GW_SHARP] = StatsTracker.count(player, "guesswho_sharp");
        n[ProfileSyncPayload.GW_HIGH] = StatsTracker.count(player, "guesswho_highroller");
        n[ProfileSyncPayload.BLUFF_WINS] = StatsTracker.count(player, "bluff_wins");
        n[ProfileSyncPayload.BLUFF_LOSSES] = StatsTracker.count(player, "bluff_losses");
        n[ProfileSyncPayload.BLUFF_CATCHES] = StatsTracker.count(player, "bluff_catches");
        n[ProfileSyncPayload.BLUFF_LASTCARD] = StatsTracker.count(player, "bluff_lastcard");

        n[ProfileSyncPayload.KILLS] = StatsTracker.totalKills(player);
        n[ProfileSyncPayload.COLLECTED] = collected;
        n[ProfileSyncPayload.FOILS] = player.getData(ModAttachments.COLLECTED_FOIL.get()).size();
        n[ProfileSyncPayload.HOLO_MAX] = AchievementManager.metric(player, "holo3");
        n[ProfileSyncPayload.SETS_DONE] = AchievementManager.metric(player, "categories");
        n[ProfileSyncPayload.SETS_TOTAL] = Category.values().length;
        n[ProfileSyncPayload.AWARDS_CLAIMED] = claimed(player);
        n[ProfileSyncPayload.AWARDS_TOTAL] = Achievements.ALL.size();
        // "Filed in book" is not sent: the client already tracks it through
        // StorageSyncPayload, and a second copy could disagree with the shelf
        // the player is looking at.

        List<MatchHistory.Record> rivals = MatchHistory.rivals(player);
        int shown = Math.min(MAX_RIVALS, rivals.size());
        n[ProfileSyncPayload.RIVALS] = shown;

        List<Integer> nums = new ArrayList<>(n.length + shown * ProfileSyncPayload.PER_RIVAL);
        for (int v : n) {
            nums.add(v);
        }
        List<String> texts = new ArrayList<>();
        texts.add(player.getGameProfile().getName());
        texts.add(StatsTracker.title(rating, collected));
        Stat fav = StatsTracker.favoriteStat(player);
        texts.add(fav == null ? "" : fav.label);
        String nemesis = StatsTracker.nemesis(player);
        texts.add(nemesis == null ? "" : nemesis);
        for (int i = 0; i < shown; i++) {
            MatchHistory.Record r = rivals.get(i);
            texts.add(r.name());
            nums.add(r.wins());
            nums.add(r.losses());
        }
        return new ProfileSyncPayload(texts, nums);
    }

    private static int claimed(ServerPlayer player) {
        int n = 0;
        for (Achievement a : Achievements.ALL) {
            if (AchievementManager.state(player, a.id()) == AchievementManager.CLAIMED) {
                n++;
            }
        }
        return n;
    }

    /** Total cards in the set, so the client never has to hardcode 81. */
    public static int cardTotal() {
        return MobCards.ALL.size();
    }
}
