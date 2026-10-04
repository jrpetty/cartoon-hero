package com.gadgets;

/**
 * Reads direction out of a node's recent history.
 *
 * <p>A reading answers "how much"; this answers "which way, and how soon". Both
 * questions are only asked of real history — with too few points, or a line
 * that is effectively flat, the answer is nothing rather than a guess, because
 * a confident "dry in 4m" that turns out to be noise teaches players to ignore
 * the one line that should make them move.
 */
public final class Trend {
    /** Fewest points before a slope is believed: a minute of history. */
    private static final int MIN_POINTS = 4;
    /** Seconds between points — one sample every {@link CommandHubBlockEntity#SAMPLE_EVERY} one-second refreshes. */
    private static final double STEP_SECONDS = CommandHubBlockEntity.SAMPLE_EVERY;
    /** Past this, an ETA is too far out to be worth a line of the board. */
    private static final double HORIZON_SECONDS = 48 * 3600;

    private Trend() {
    }

    /** Least-squares slope, in units per point; 0 when there is too little history. */
    static double slope(int[] hist) {
        int n = hist.length;
        if (n < MIN_POINTS) {
            return 0;
        }
        double meanX = (n - 1) / 2.0;
        double meanY = 0;
        for (int v : hist) {
            meanY += v;
        }
        meanY /= n;
        double num = 0;
        double den = 0;
        for (int i = 0; i < n; i++) {
            double dx = i - meanX;
            num += dx * (hist[i] - meanY);
            den += dx * dx;
        }
        return den == 0 ? 0 : num / den;
    }

    /**
     * How long until the reading crosses {@code target}, as "12m" / "3h", or
     * empty when it is not heading there, already past it, or will not get
     * there inside the horizon.
     */
    public static String etaTo(int[] hist, long target) {
        double seconds = secondsTo(hist, target);
        return seconds < 0 ? "" : duration(seconds);
    }

    /** Seconds until the reading crosses {@code target}, or -1 for "not happening". */
    public static double secondsTo(int[] hist, long target) {
        if (hist.length < MIN_POINTS) {
            return -1;
        }
        double perPoint = slope(hist);
        double now = hist[hist.length - 1];
        double gap = target - now;
        // Heading the right way, and moving by more than a rounding error.
        if (perPoint == 0 || Math.signum(gap) != Math.signum(perPoint) || Math.abs(perPoint) < 0.5) {
            return -1;
        }
        double seconds = gap / perPoint * STEP_SECONDS;
        return seconds <= 0 || seconds > HORIZON_SECONDS ? -1 : seconds;
    }

    /** Recent average against early average, as a whole percentage; 0 without enough history. */
    public static int changePercent(int[] hist) {
        if (hist.length < MIN_POINTS * 2) {
            return 0;
        }
        int k = Math.max(2, hist.length / 3);
        double early = 0;
        double late = 0;
        for (int i = 0; i < k; i++) {
            early += hist[i];
            late += hist[hist.length - 1 - i];
        }
        early /= k;
        late /= k;
        if (early < 1) {
            return 0; // growth from nothing is a start-up, not a trend
        }
        return (int) Math.round((late - early) / early * 100);
    }

    static String duration(double seconds) {
        if (seconds < 60) {
            return "<1m";
        }
        if (seconds < 3600) {
            return Math.round(seconds / 60) + "m";
        }
        long hours = Math.round(seconds / 3600);
        return hours < 48 ? hours + "h" : (hours / 24) + "d";
    }
}
