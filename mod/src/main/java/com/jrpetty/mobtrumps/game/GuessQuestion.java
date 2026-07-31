package com.jrpetty.mobtrumps.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The questions a player may ask in Guess Who, and what they eliminate.
 *
 * <p>Questions come from a fixed catalogue rather than being typed, so the
 * server can answer them itself. That is what makes the game trustworthy — the
 * opponent never adjudicates, so they cannot lie, cannot misremember, and do not
 * even have to be online — and it is why the board can strike cards off by
 * itself instead of asking the player to keep count honestly.
 *
 * <p>The catalogue is a list of <em>templates</em>, not of finished questions.
 * A threshold question carries its number separately: one row reading "Does it
 * have less than [ ] Health?" with a value box, rather than ten near-identical
 * rows. That keeps the list at {@value #TEMPLATE_COUNT} rows — short enough to
 * scroll and search — while still offering all
 * {@code 6 stats x 10 values x 2 directions + categories + traits} questions.
 *
 * <p>Pure logic, no Minecraft: the same catalogue answers questions on the
 * server and greys out tiles on the client, so the two can never disagree.
 */
public final class GuessQuestion {

    /** What a template asks about. */
    public enum Kind {
        /** Is this stat strictly below the value? */
        STAT_BELOW,
        /** Is this stat strictly above the value? */
        STAT_ABOVE,
        /** Is the mob in this category? */
        CATEGORY,
        /** Does the mob have this trait? */
        TRAIT
    }

    /**
     * One row of the question list.
     *
     * @param kind    what it asks about
     * @param index   which stat, category or trait — an ordinal into that enum
     * @param label   how the row reads, with {@code %d} where a value goes
     */
    public record Template(Kind kind, int index, String label) {

        /** True when this row needs a number before it can be asked. */
        public boolean needsValue() {
            return kind == Kind.STAT_BELOW || kind == Kind.STAT_ABOVE;
        }

        /** The stat a threshold row is about, or null. */
        public Stat stat() {
            return needsValue() ? Stat.values()[index] : null;
        }

        /** How the row reads with a value filled in. */
        public String text(int value) {
            return needsValue() ? String.format(label, value) : label;
        }

        /** Lowest value worth asking — below this the answer is never in doubt. */
        public int minValue() {
            return kind == Kind.STAT_BELOW ? 1 : 0;
        }

        /** Highest value worth asking. */
        public int maxValue() {
            return kind == Kind.STAT_BELOW ? 10 : 9;
        }

        public int clamp(int value) {
            return Math.max(minValue(), Math.min(maxValue(), value));
        }
    }

    /** Rows in the catalogue: 12 threshold, 10 category, 14 trait. */
    public static final int TEMPLATE_COUNT = 36;

    public static final List<Template> TEMPLATES;

    static {
        List<Template> all = new ArrayList<>();
        for (Stat stat : Stat.values()) {
            all.add(new Template(Kind.STAT_BELOW, stat.ordinal(),
                    "Is its " + stat.label + " less than %d?"));
            all.add(new Template(Kind.STAT_ABOVE, stat.ordinal(),
                    "Is its " + stat.label + " more than %d?"));
        }
        for (Category category : Category.values()) {
            all.add(new Template(Kind.CATEGORY, category.ordinal(), category.question()));
        }
        for (MobTrait trait : MobTrait.values()) {
            all.add(new Template(Kind.TRAIT, trait.ordinal(), trait.question()));
        }
        TEMPLATES = List.copyOf(all);
    }

    private GuessQuestion() {
    }

    /**
     * Answer a question about one mob.
     *
     * <p>Rarity is asked exactly as it is printed. It is a lower-is-rarer stat,
     * and quietly flipping the comparison so "less than 3" meant "commoner
     * than 3" would make the card in the player's hand disagree with the answer
     * they were just given.
     */
    public static boolean ask(Template template, int value, MobCard card) {
        if (card == null) {
            return false;
        }
        return switch (template.kind()) {
            case STAT_BELOW -> card.stat(Stat.values()[template.index()]) < value;
            case STAT_ABOVE -> card.stat(Stat.values()[template.index()]) > value;
            case CATEGORY -> MobCategories.of(card.id()) == Category.values()[template.index()];
            case TRAIT -> MobTrait.values()[template.index()].has(card.id());
        };
    }

    /**
     * The mobs still possible after an answer: those that would have produced
     * the same answer. Everything else is struck off the board.
     */
    public static List<MobCard> eliminate(List<MobCard> pool, Template template,
                                          int value, boolean answer) {
        List<MobCard> left = new ArrayList<>();
        for (MobCard card : pool) {
            if (ask(template, value, card) == answer) {
                left.add(card);
            }
        }
        return left;
    }

    /** How many of a pool would answer yes — used to reject useless questions. */
    public static int yesCount(List<MobCard> pool, Template template, int value) {
        int yes = 0;
        for (MobCard card : pool) {
            if (ask(template, value, card)) {
                yes++;
            }
        }
        return yes;
    }

    /**
     * Whether asking this would tell you anything. A question every remaining
     * mob answers the same way eliminates nothing and would waste a turn, so
     * the list greys it out rather than letting it be spent.
     */
    public static boolean isUseful(List<MobCard> pool, Template template, int value) {
        int yes = yesCount(pool, template, value);
        return yes > 0 && yes < pool.size();
    }

    /**
     * Rows matching a search box, by the words in their text. Empty query
     * returns everything, so the list is complete until the player narrows it.
     */
    public static List<Template> search(String query) {
        if (query == null || query.isBlank()) {
            return TEMPLATES;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Template> hits = new ArrayList<>();
        for (Template template : TEMPLATES) {
            // match against the label with the placeholder removed, so typing
            // "health" finds the row whether or not a value has been set yet
            String text = template.label().replace("%d", "").toLowerCase(Locale.ROOT);
            if (text.contains(needle)) {
                hits.add(template);
            }
        }
        return hits;
    }
}
