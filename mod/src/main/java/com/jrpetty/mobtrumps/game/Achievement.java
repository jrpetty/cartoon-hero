package com.jrpetty.mobtrumps.game;

import java.util.List;
import java.util.Locale;

/**
 * One achievement in the collection book's award pages. Deliberately plain data
 * so the client can lay out the pages and the server can pay out the reward from
 * exactly the same definition — see {@link Achievements} for the catalogue.
 *
 * <p>{@code metric} names a counter (see {@code AchievementManager}); the award
 * unlocks the moment that counter reaches {@code target}, and stays claimable
 * until the player presses Collect.
 */
public record Achievement(String id, Group group, String title, String description,
                          String metric, int target, List<Reward> rewards) {

    /** One stack in a payout: a vanilla item id (no namespace) and a count. */
    public record Reward(String item, int count) {

        /** "iron_ingot" x8 -> "8x Iron Ingot" for the book page. */
        public String label() {
            StringBuilder sb = new StringBuilder();
            for (String word : item.split("_")) {
                if (word.isEmpty()) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase(Locale.ROOT));
            }
            return count + "x " + sb;
        }
    }

    /** The award groups the book pages are split into. */
    public enum Group {
        COLLECTOR("Collector", "Filling the binder", 0xFF3FA7D6),
        ARENA("The Arena", "Games against the CPU", 0xFF7FB069),
        DUELIST("Duelist", "Beating real players", 0xFFF2C14E),
        PARLOUR("The Parlour", "Twenty-One, Guess Who and Bluff", 0xFF9B6BD9),
        HUNTER("Hunter", "Out in the world", 0xFFD65A31);

        private final String label;
        private final String blurb;
        private final int accent;

        Group(String label, String blurb, int accent) {
            this.label = label;
            this.blurb = blurb;
            this.accent = accent;
        }

        public String label() {
            return label;
        }

        /** One-line description shown under the group heading. */
        public String blurb() {
            return blurb;
        }

        /** ARGB accent for this group's heading and progress bars. */
        public int accent() {
            return accent;
        }
    }

    /** A single-line summary of the payout, e.g. "8x Iron Ingot · 2x Gold Ingot". */
    public String rewardLabel() {
        StringBuilder sb = new StringBuilder();
        for (Reward r : rewards) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(r.label());
        }
        return sb.toString();
    }
}
