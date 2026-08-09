package com.jrpetty.mobtrumps.game;

/**
 * What an idle rating loses, and — more importantly — what it never loses.
 *
 * <p>Decay exists for one reason: a ladder where somebody reached Master in
 * week one and stopped playing is not a ranking of who is good, it is a
 * ranking of who peaked earliest. The top of the table should mean "currently
 * among the best", and that requires the seat to be given up if it is not
 * defended.
 *
 * <p>It is not a punishment for having a life, so three rules bound it:
 *
 * <p><b>A grace period.</b> Nothing at all happens for {@link #GRACE_DAYS}. A
 * player who misses a weekend comes back to exactly the rating they left.
 *
 * <p><b>A floor.</b> Decay stops dead at {@link #floor()} — the bottom of the
 * bracket where holding a place actually matters. A casual player parked in
 * Silver is never touched by any of this, and someone drifting down from
 * Master lands at the floor rather than in the basement.
 *
 * <p><b>It is slow.</b> {@link #POINTS_PER_DAY} is under half a win. Coming
 * back after a fortnight costs a few games, not a season.
 */
public final class RankDecay {

    /** Idle days that cost nothing whatsoever. */
    public static final int GRACE_DAYS = 5;

    /** Rating shed per idle day once the grace period is over. */
    public static final int POINTS_PER_DAY = 12;

    public static final long DAY_MS = 86_400_000L;

    private RankDecay() {
    }

    /** Whole idle days beyond the grace period; never negative. */
    public static int decayDays(long idleMs) {
        if (idleMs <= 0L) {
            return 0;
        }
        long days = idleMs / DAY_MS;
        return (int) Math.max(0L, days - GRACE_DAYS);
    }

    /**
     * The rating after sitting idle for {@code idleMs}.
     *
     * <p>Anyone already at or below the floor is returned untouched, so this is
     * safe to run over the whole table without filtering first.
     */
    public static int decayed(int rating, long idleMs, int floor) {
        if (rating <= floor) {
            return rating;
        }
        int days = decayDays(idleMs);
        if (days <= 0) {
            return rating;
        }
        long lost = (long) days * POINTS_PER_DAY;
        return (int) Math.max(floor, rating - lost);
    }

    /** How much {@link #decayed} would take off — for a message, or a test. */
    public static int loss(int rating, long idleMs, int floor) {
        return rating - decayed(rating, idleMs, floor);
    }

    /** True when this player is high enough for decay to ever apply. */
    public static boolean applies(int rating, int floor) {
        return rating > floor;
    }
}
