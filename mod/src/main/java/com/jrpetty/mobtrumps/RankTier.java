package com.jrpetty.mobtrumps;

import net.minecraft.ChatFormatting;

/**
 * Competitive tiers for the ranked ladder. Each non-Master tier spans 150
 * rating split into three divisions (III at the bottom, I at the top), so a
 * player ranks up roughly every couple of wins — the "just one more game" hook.
 */
public enum RankTier {

    BRONZE("Bronze", 0, 0xFFCD7F32, ChatFormatting.GOLD, 4),
    SILVER("Silver", 900, 0xFFC7CCD1, ChatFormatting.GRAY, 8),
    GOLD("Gold", 1050, 0xFFFFD54A, ChatFormatting.YELLOW, 16),
    PLATINUM("Platinum", 1200, 0xFF5FE0D0, ChatFormatting.AQUA, 28),
    DIAMOND("Diamond", 1350, 0xFF6FB4FF, ChatFormatting.BLUE, 44),
    MASTER("Master", 1500, 0xFFC08BFF, ChatFormatting.LIGHT_PURPLE, 64);

    private static final int SPAN = 150;
    private static final int DIV = 50;

    public final String title;
    public final int min;
    public final int rgb;
    public final ChatFormatting color;
    public final int reward; // emeralds paid when a season ends in this tier

    RankTier(String title, int min, int rgb, ChatFormatting color, int reward) {
        this.title = title;
        this.min = min;
        this.rgb = rgb;
        this.color = color;
        this.reward = reward;
    }

    public static RankTier of(int rating) {
        RankTier tier = BRONZE;
        for (RankTier t : values()) {
            if (rating >= t.min) tier = t;
        }
        return tier;
    }

    /** Division 1 (I, best) .. 3 (III, worst) within a tier; Master is always I. */
    public static int division(int rating) {
        RankTier t = of(rating);
        if (t == MASTER) return 1;
        int into = Math.min(SPAN - 1, rating - t.min);
        return 3 - Math.min(2, into / DIV);
    }

    /** A monotonic score where higher is a strictly better rank. */
    public static int score(int rating) {
        return of(rating).ordinal() * 3 + (3 - division(rating));
    }

    public static String label(int rating) {
        RankTier t = of(rating);
        if (t == MASTER) return "Master (" + rating + ")";
        return t.title + " " + roman(division(rating));
    }

    /** Short badge form, e.g. "Gold I" or "Master". */
    public static String badgeLabel(int rating) {
        RankTier t = of(rating);
        return t == MASTER ? "Master" : t.title + " " + roman(division(rating));
    }

    private static String roman(int div) {
        return switch (div) {
            case 1 -> "I";
            case 2 -> "II";
            default -> "III";
        };
    }

    /**
     * Rating at which the next division begins, or -1 at the top of the ladder.
     *
     * <p>Master has no next rung, so a climb bar there would be a lie.
     */
    public static int nextStep(int rating) {
        RankTier t = of(rating);
        if (t == MASTER) {
            return -1;
        }
        int div = division(rating);
        if (div > 1) {
            // still climbing inside this tier
            return t.min + (3 - div + 1) * DIV;
        }
        // top division: the next step is the tier above
        RankTier[] all = values();
        return all[Math.min(all.length - 1, t.ordinal() + 1)].min;
    }

    /** Rating the current division started at, for a progress bar's left edge. */
    public static int stepFloor(int rating) {
        RankTier t = of(rating);
        if (t == MASTER) {
            return MASTER.min;
        }
        return t.min + (3 - division(rating)) * DIV;
    }

    /**
     * How far through the current division a rating sits, 0..1. Master is
     * always full — there is nothing above it to be part-way towards.
     */
    public static float progress(int rating) {
        int next = nextStep(rating);
        if (next < 0) {
            return 1f;
        }
        int floor = stepFloor(rating);
        int span = Math.max(1, next - floor);
        return Math.max(0f, Math.min(1f, (rating - floor) / (float) span));
    }
}
