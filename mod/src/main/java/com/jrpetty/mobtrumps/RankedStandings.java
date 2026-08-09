package com.jrpetty.mobtrumps;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and sends the ranked standings for one viewer.
 *
 * <p>The ladder was only ever printed into chat. That is fine for a number but
 * poor for a table, and a competitive ladder is the one screen where people
 * want to look up and down the list, find themselves, and see how far the next
 * rung is.
 */
public final class RankedStandings {

    /** How many places the board sends. Beyond this nobody is reading. */
    public static final int TOP = 50;

    private RankedStandings() {
    }

    public static void send(ServerPlayer viewer) {
        Leaderboard board = Leaderboard.get(viewer.serverLevel().getServer());
        List<Leaderboard.Entry> top = board.top(TOP);

        List<String> names = new ArrayList<>(top.size() + 1);
        List<Integer> nums = new ArrayList<>();
        for (int i = 0; i < RankedSyncPayload.HEADER; i++) {
            nums.add(0);
        }

        int youIndex = -1;
        for (int i = 0; i < top.size(); i++) {
            Leaderboard.Entry e = top.get(i);
            if (e.id().equals(viewer.getUUID())) {
                youIndex = i;
            }
            add(names, nums, e);
        }
        // Someone outside the top still gets their own row, appended on the end
        // and pointed at by YOU_INDEX, so the screen never has to ask twice.
        Leaderboard.Entry mine = board.entry(viewer.getUUID());
        if (youIndex < 0 && mine != null) {
            youIndex = names.size();
            add(names, nums, mine);
        }

        nums.set(RankedSyncPayload.SEASON, board.season());
        nums.set(RankedSyncPayload.SECONDS_LEFT, (int) Math.min(Integer.MAX_VALUE,
                board.msLeft() / 1000L));
        nums.set(RankedSyncPayload.YOU_INDEX, youIndex);
        nums.set(RankedSyncPayload.YOUR_PLACE, board.rankOf(viewer.getUUID()));
        nums.set(RankedSyncPayload.TOTAL_RANKED, board.rankedCount());
        nums.set(RankedSyncPayload.DECAY_FLOOR, RankTier.GOLD.min);

        PacketDistributor.sendToPlayer(viewer, new RankedSyncPayload(names, nums));
    }

    private static void add(List<String> names, List<Integer> nums, Leaderboard.Entry e) {
        names.add(e.name());
        nums.add(e.rating());
        nums.add(e.wins());
        nums.add(e.losses());
        nums.add(e.peak());
    }
}
