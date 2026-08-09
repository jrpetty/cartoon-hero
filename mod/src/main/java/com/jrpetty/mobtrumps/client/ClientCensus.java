package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.game.MobCards;

import java.util.List;

/**
 * How many of each card this server has printed, as this client last heard.
 *
 * <p>Indexed by catalogue position rather than id, matching the payload — the
 * card order is fingerprinted at startup, so position is a safe key.
 */
public final class ClientCensus {

    private static volatile List<Integer> printed = List.of();

    private ClientCensus() {
    }

    public static void set(List<Integer> counts) {
        printed = List.copyOf(counts);
    }

    public static boolean known() {
        return !printed.isEmpty();
    }

    /** Prints of one card, or -1 when the server has not said. */
    public static int printedOf(String mobId) {
        int i = MobCards.ordinal(mobId);
        if (i < 0 || i >= printed.size()) {
            return -1;
        }
        return printed.get(i);
    }

    /** Every card printed on this server, added up. */
    public static int total() {
        int sum = 0;
        for (int n : printed) {
            sum += n;
        }
        return sum;
    }

    /**
     * Where this card sits when the whole catalogue is ordered by print run,
     * rarest first — 1 is the rarest thing on the server. Cards nobody has ever
     * printed are excluded, since "never printed" is not the same as "rare" and
     * would otherwise fill the whole top of the table.
     */
    public static int rarityPlace(String mobId) {
        int mine = printedOf(mobId);
        if (mine <= 0) {
            return -1;
        }
        int place = 1;
        for (int n : printed) {
            if (n > 0 && n < mine) {
                place++;
            }
        }
        return place;
    }

    /** How many cards have been printed at least once, for the ranking's scale. */
    public static int printedKinds() {
        int kinds = 0;
        for (int n : printed) {
            if (n > 0) {
                kinds++;
            }
        }
        return kinds;
    }
}
