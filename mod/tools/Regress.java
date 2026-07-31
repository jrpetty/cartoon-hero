import com.jrpetty.mobtrumps.game.*;
import java.util.*;

public class Regress {
    static int fails = 0;
    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok    " : "  FAIL  ") + what);
        if (!ok) fails++;
    }
    public static void main(String[] a) {
        System.out.println("card set");
        check(MobCards.ALL.size() == 81, "81 cards");
        check(MobCards.orderIntact(), "card order matches ORDER_FINGERPRINT");
        Set<Integer> ords = new HashSet<>();
        for (MobCard c : MobCards.ALL) ords.add(MobCards.ordinal(c.id()));
        check(ords.size() == 81 && Collections.min(ords) == 0 && Collections.max(ords) == 80,
                "ordinals are 0..80, unique");

        System.out.println("campaign");
        boolean sized = true, dupes = false, det = true, anchored = true;
        for (CampaignMission m : CampaignDecks.ALL) {
            List<MobCard> d = CampaignDecks.cpuDeck(m);
            if (d.size() != 16) sized = false;
            if (new HashSet<>(d).size() != d.size()) dupes = true;
            if (!d.equals(CampaignDecks.cpuDeck(m))) det = false;
            for (String id : MobCategories.members(m.anchor()))
                if (MobCards.byId(id) != null && MobCategories.size(m.anchor()) <= 16
                        && d.stream().noneMatch(c -> c.id().equals(id))) anchored = false;
        }
        check(CampaignDecks.count() == 20, "20 missions");
        check(sized, "every opponent deck is exactly 16");
        check(!dupes, "no duplicate cards in a deck");
        check(det, "decks are deterministic");
        check(anchored, "every anchor member present");

        System.out.println("recycler");
        boolean flat = true, ordered = true;
        int prevMax = 0;
        for (Tier t : Tier.values()) {
            for (int s = Recycler.MIN_STAKE; s <= Recycler.maxStake(t); s++)
                if (Math.abs(Recycler.expectedCost(t, s) - Recycler.maxStake(t)) > 0.001) flat = false;
            if (Recycler.maxStake(t) <= prevMax) ordered = false;
            prevMax = Recycler.maxStake(t);
            if (Recycler.yield(t, 100) != Recycler.baseYield(t)) flat = false;
            if (Recycler.yield(t, 0) < 1) flat = false;
            if (Recycler.yield(t, 0) > Recycler.yield(t, 100)) flat = false;
        }
        check(flat, "expected cost flat at every stake; yield mint>=ruined>=1");
        check(ordered, "max stake rises with tier");
        check(Recycler.percent(Tier.COMMON, 30) == 100 && Recycler.percent(Tier.COMMON, 15) == 50,
                "common: 30 guarantees, 15 is a coin flip");

        System.out.println("stats");
        check(Stat.RARITY.lowerWins, "rarity is lower-wins");
        check(Stat.RARITY.score(1) > Stat.RARITY.score(10), "rarity 1 beats rarity 10");
        check(Stat.HEALTH.score(10) > Stat.HEALTH.score(1), "health 10 beats health 1");

        System.out.println(fails == 0 ? "\nALL REGRESSION CHECKS PASS" : "\n*** " + fails + " FAILURES ***");
        if (fails > 0) System.exit(1);
    }
}
