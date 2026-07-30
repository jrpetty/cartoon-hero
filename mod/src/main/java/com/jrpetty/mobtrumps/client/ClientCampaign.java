package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.game.CampaignDecks;
import com.jrpetty.mobtrumps.game.CampaignMission;

import java.util.Map;

/** Client-side cache of campaign progress. Pure data holder. */
public final class ClientCampaign {

    public static final int CLEARED = 1;
    public static final int FLAWLESS = 2;

    private static volatile Map<String, Integer> states = Map.of();

    private ClientCampaign() {
    }

    public static void set(Map<String, Integer> next) {
        states = Map.copyOf(next);
    }

    public static int state(CampaignMission mission) {
        return states.getOrDefault(mission.id(), 0);
    }

    public static boolean cleared(CampaignMission mission) {
        return state(mission) > 0;
    }

    public static boolean flawless(CampaignMission mission) {
        return state(mission) >= FLAWLESS;
    }

    /** The highest mission cleared, 0 if none — the next one along is playable. */
    public static int highestCleared() {
        int best = 0;
        for (CampaignMission m : CampaignDecks.ALL) {
            if (cleared(m)) best = Math.max(best, m.index());
        }
        return best;
    }

    public static boolean unlocked(CampaignMission mission) {
        return mission.index() <= highestCleared() + 1;
    }

    public static int clearedCount() {
        int n = 0;
        for (CampaignMission m : CampaignDecks.ALL) {
            if (cleared(m)) n++;
        }
        return n;
    }
}
