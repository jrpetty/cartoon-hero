package com.jrpetty.mobtrumps.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;

/** The full collection: every Minecraft mob as a card. */
public final class MobCards {

    private static final Map<String, MobCard> BY_ID = new LinkedHashMap<>();
    private static final Map<String, Integer> ORDINALS = new HashMap<>();
    public static final List<MobCard> ALL;

    private static void card(String id, String name, int health, int attack,
                             int size, int speed, int farmable, int rarity) {
        BY_ID.put(id, new MobCard(id, name, health, attack, size, speed, farmable, rarity));
    }

    static {
        card("allay", "Allay", 2, 0, 1, 4, 5, 3);
        card("armadillo", "Armadillo", 2, 0, 2, 3, 4, 4);
        card("axolotl", "Axolotl", 2, 2, 2, 4, 6, 4);
        card("bat", "Bat", 1, 0, 1, 4, 1, 8);
        card("bee", "Bee", 1, 1, 1, 4, 8, 7);
        card("blaze", "Blaze", 4, 4, 3, 5, 8, 5);
        card("bogged", "Bogged", 3, 3, 3, 3, 7, 6);
        card("breeze", "Breeze", 3, 4, 3, 6, 3, 4);
        card("camel", "Camel", 6, 0, 5, 3, 5, 3);
        card("cat", "Cat", 2, 0, 2, 4, 4, 6);
        card("cave_spider", "Cave Spider", 2, 3, 2, 6, 6, 6);
        card("chicken", "Chicken", 1, 0, 1, 3, 10, 10);
        card("cod", "Cod", 1, 0, 1, 3, 8, 9);
        card("cow", "Cow", 2, 0, 3, 2, 10, 9);
        card("creaking", "Creaking", 5, 4, 4, 3, 2, 4);
        card("creeper", "Creeper", 3, 8, 3, 4, 9, 9);
        card("dolphin", "Dolphin", 2, 2, 3, 8, 2, 7);
        card("donkey", "Donkey", 4, 0, 4, 3, 6, 5);
        card("drowned", "Drowned", 3, 3, 3, 3, 8, 8);
        card("elder_guardian", "Elder Guardian", 8, 6, 7, 3, 2, 1);
        card("ender_dragon", "Ender Dragon", 10, 9, 10, 5, 1, 1);
        card("enderman", "Enderman", 6, 5, 7, 4, 6, 6);
        card("endermite", "Endermite", 1, 2, 1, 4, 3, 3);
        card("evoker", "Evoker", 3, 5, 3, 3, 5, 2);
        card("fox", "Fox", 2, 0, 2, 5, 4, 5);
        card("frog", "Frog", 2, 0, 1, 4, 4, 6);
        card("ghast", "Ghast", 2, 8, 8, 4, 7, 6);
        card("glow_squid", "Glow Squid", 2, 0, 2, 3, 4, 7);
        card("goat", "Goat", 2, 2, 3, 4, 4, 5);
        card("guardian", "Guardian", 3, 4, 3, 4, 6, 4);
        card("hoglin", "Hoglin", 8, 6, 6, 5, 9, 7);
        card("horse", "Horse", 5, 0, 4, 6, 7, 6);
        card("husk", "Husk", 3, 3, 3, 3, 7, 8);
        card("iron_golem", "Iron Golem", 8, 7, 8, 2, 10, 5);
        card("llama", "Llama", 3, 1, 4, 3, 5, 5);
        card("magma_cube", "Magma Cube", 3, 3, 4, 4, 7, 6);
        card("mooshroom", "Mooshroom", 2, 0, 3, 2, 9, 2);
        card("mule", "Mule", 4, 0, 4, 3, 5, 3);
        card("ocelot", "Ocelot", 2, 0, 2, 5, 3, 4);
        card("panda", "Panda", 4, 2, 4, 2, 4, 3);
        card("parrot", "Parrot", 1, 0, 1, 5, 4, 3);
        card("phantom", "Phantom", 2, 3, 2, 8, 3, 6);
        card("pig", "Pig", 2, 0, 3, 3, 10, 9);
        card("piglin", "Piglin", 3, 4, 3, 5, 8, 8);
        card("piglin_brute", "Piglin Brute", 5, 6, 3, 4, 4, 3);
        card("pillager", "Pillager", 3, 4, 3, 4, 6, 6);
        card("polar_bear", "Polar Bear", 4, 3, 5, 3, 2, 3);
        card("pufferfish", "Pufferfish", 1, 1, 1, 2, 5, 7);
        card("rabbit", "Rabbit", 1, 0, 1, 6, 7, 8);
        card("ravager", "Ravager", 7, 7, 7, 4, 5, 3);
        card("salmon", "Salmon", 1, 0, 1, 4, 8, 9);
        card("sheep", "Sheep", 2, 0, 3, 2, 10, 9);
        card("shulker", "Shulker", 3, 3, 2, 1, 4, 2);
        card("silverfish", "Silverfish", 1, 2, 1, 6, 4, 7);
        card("skeleton", "Skeleton", 3, 3, 3, 3, 9, 10);
        card("skeleton_horse", "Skeleton Horse", 4, 0, 4, 5, 5, 2);
        card("slime", "Slime", 3, 2, 4, 4, 9, 8);
        card("sniffer", "Sniffer", 2, 0, 6, 2, 3, 2);
        card("snow_golem", "Snow Golem", 2, 1, 3, 4, 7, 5);
        card("spider", "Spider", 3, 2, 4, 6, 8, 10);
        card("squid", "Squid", 2, 0, 2, 3, 7, 9);
        card("stray", "Stray", 3, 3, 3, 3, 7, 6);
        card("strider", "Strider", 3, 0, 3, 3, 6, 7);
        card("sulfur_cube", "Sulfur Cube", 4, 5, 4, 5, 6, 3);
        card("tadpole", "Tadpole", 1, 0, 1, 3, 4, 7);
        card("trader_llama", "Trader Llama", 3, 1, 4, 3, 4, 4);
        card("tropical_fish", "Tropical Fish", 1, 0, 1, 4, 6, 8);
        card("turtle", "Turtle", 3, 0, 2, 2, 6, 5);
        card("vex", "Vex", 2, 4, 1, 9, 1, 3);
        card("villager", "Villager", 3, 0, 3, 3, 10, 7);
        card("vindicator", "Vindicator", 4, 5, 3, 4, 5, 4);
        card("wandering_trader", "Wandering Trader", 3, 0, 3, 3, 5, 3);
        card("warden", "Warden", 10, 10, 10, 4, 1, 1);
        card("witch", "Witch", 3, 4, 3, 3, 7, 6);
        card("wither", "Wither", 9, 8, 8, 5, 5, 1);
        card("wither_skeleton", "Wither Skeleton", 4, 5, 4, 4, 8, 6);
        card("wolf", "Wolf", 3, 3, 2, 4, 7, 7);
        card("zombie", "Zombie", 3, 3, 3, 3, 9, 10);
        card("zombie_villager", "Zombie Villager", 3, 3, 3, 3, 9, 7);
        card("zombified_piglin", "Zombified Piglin", 3, 4, 3, 4, 9, 9);
        card("zoglin", "Zoglin", 6, 6, 4, 5, 6, 4);

        ALL = List.copyOf(BY_ID.values());
        for (int i = 0; i < ALL.size(); i++) {
            ORDINALS.put(ALL.get(i).id(), i);
        }
    }

    private MobCards() {
    }

    /** Look up a card by id, e.g. "ender_dragon". Returns null if unknown. */
    public static MobCard byId(String id) {
        if (id == null) return null;
        return BY_ID.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * A stable fingerprint of the card ORDER, checked at startup.
     *
     * <p>The catalogue number ("No. 12 / 81") is the mob's fixed position in
     * the set, derived from declaration order in this file. Serials, the
     * collection book's paging and every saved deck are read against it.
     * Inserting a mob mid-list would silently renumber every card after it —
     * harmless the day it happens, wrong forever afterwards. This does not
     * prevent that; it makes it impossible to do by accident and not notice.
     */
    public static final long ORDER_FINGERPRINT = -5084331604706219575L;

    /** Recompute the fingerprint from the live list. */
    public static long fingerprint() {
        long h = 1125899906842597L;
        for (MobCard card : ALL) {
            for (int i = 0; i < card.id().length(); i++) {
                h = 31 * h + card.id().charAt(i);
            }
            h = 31 * h + '/';
        }
        return h;
    }

    /** True if the card order still matches what everything else was built on. */
    public static boolean orderIntact() {
        return fingerprint() == ORDER_FINGERPRINT;
    }

    /** Position of a card in the collection (0-based), or -1 if unknown. */
    public static int ordinal(String id) {
        if (id == null) return -1;
        return ORDINALS.getOrDefault(id.toLowerCase(Locale.ROOT), -1);
    }

    /**
     * Odds that {@code value} on {@code stat} beats a random card from the whole
     * set (ties count as half a win). A smart CPU leads with the stat that gives
     * it the best chance, not merely its highest raw number.
     */
    public static double winOdds(Stat stat, int value) {
        int below = 0;
        int equal = 0;
        // score() folds in the stat's direction, so "lower wins" stats (Rarity)
        // score correctly without a second code path
        int mine = stat.score(value);
        for (MobCard c : ALL) {
            int v = stat.score(c.stat(stat));
            if (v < mine) below++;
            else if (v == mine) equal++;
        }
        return (below + equal / 2.0) / ALL.size();
    }

    /**
     * Draw {@code count} distinct cards weighted by spawn rarity: a rarity 10
     * mob is ten times more likely to be pulled than a rarity 1 legendary.
     */
    public static List<MobCard> openPack(int count, RandomGenerator random) {
        return openPack(ALL, count, 0.0, random);
    }

    /**
     * Draw {@code count} distinct cards from {@code source}. {@code bias} in
     * [0,1] tilts the odds toward rarer cards: 0 is normal spawn weighting,
     * 1 fully favours the rarest. Nothing is guaranteed — premium packs just
     * roll from a better-stacked bag.
     */
    public static List<MobCard> openPack(List<MobCard> source, int count, double bias,
                                         RandomGenerator random) {
        List<MobCard> from = (source == null || source.isEmpty()) ? ALL : source;
        count = Math.max(1, Math.min(count, from.size()));
        List<MobCard> pool = new ArrayList<>(from);
        List<MobCard> pulls = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            drawWeighted(pool, pulls, bias, random);
        }
        return pulls;
    }

    private static void drawWeighted(List<MobCard> pool, List<MobCard> out, double bias,
                                     RandomGenerator random) {
        if (pool.isEmpty()) return;
        double total = 0;
        for (MobCard c : pool) total += weight(c, bias);
        double roll = random.nextDouble() * total;
        for (int j = 0; j < pool.size(); j++) {
            roll -= weight(pool.get(j), bias);
            if (roll < 0) {
                out.add(pool.remove(j));
                return;
            }
        }
        out.add(pool.remove(pool.size() - 1));
    }

    /**
     * Draw weight for a card: a blend of its spawn rarity (favouring commons)
     * and its inverse (favouring the rare collector tiers), by {@code bias}.
     */
    private static double weight(MobCard c, double bias) {
        double common = c.rarity();          // 10 = everywhere
        double rare = 11 - c.rarity();        // 10 = legendary
        return Math.max(0.2, common * (1 - bias) + rare * bias);
    }

    /** Look up several cards by id, skipping any unknown ones. */
    public static List<MobCard> byIds(String... ids) {
        List<MobCard> out = new ArrayList<>();
        for (String id : ids) {
            MobCard c = byId(id);
            if (c != null) out.add(c);
        }
        return out;
    }

    /** A shuffled deck of {@code size} distinct cards. */
    public static List<MobCard> shuffledDeck(int size, RandomGenerator random) {
        List<MobCard> deck = new ArrayList<>(ALL);
        Collections.shuffle(deck, java.util.Random.from(random));
        return new ArrayList<>(deck.subList(0, Math.min(size, deck.size())));
    }

    /**
     * The CPU's hand for a table battle: {@code size} distinct cards picked at
     * random but on a fair collector curve — the majority are commons, a decent
     * spread of uncommons and rares, a couple of epics, and at most ONE
     * legendary. If a tier runs out of mobs the balance tops up from the next
     * tiers down, never adding a second legendary.
     */
    public static List<MobCard> cpuDeck(int size, RandomGenerator random) {
        return cpuDeck(size, random, java.util.Set.of());
    }

    /**
     * As {@link #cpuDeck(int, RandomGenerator)}, but PREFERRING not to deal a
     * card whose id is in {@code exclude} — normally the player's own hand, so
     * the two sides bring different mobs where possible. Identical cards tie on
     * every stat, so a mirrored deal is decided almost entirely by coin flips.
     *
     * <p>Deliberately a preference and not a ban. There are only twelve commons
     * in the whole set but a 16-card deck wants nine of them, so treating the
     * exclusion as absolute starved the common bucket and silently promoted
     * those slots to uncommons — handing the CPU a richer deck than the curve
     * allows, exactly when the player was running a humble common-heavy one.
     * The collector curve is the guarantee; avoiding overlap is a courtesy.
     */
    public static List<MobCard> cpuDeck(int size, RandomGenerator random, java.util.Set<String> exclude) {
        size = Math.max(2, Math.min(size, ALL.size()));
        java.util.Map<Tier, List<MobCard>> buckets = new java.util.EnumMap<>(Tier.class);
        java.util.Map<Tier, List<MobCard>> overlap = new java.util.EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) {
            buckets.put(t, new ArrayList<>());
            overlap.put(t, new ArrayList<>());
        }
        for (MobCard c : ALL) {
            (exclude.contains(c.id()) ? overlap : buckets).get(c.tier()).add(c);
        }
        java.util.Random shuffler = java.util.Random.from(random);
        // take() draws from the tail, so put the preferred cards last: a tier
        // only dips into the player's own mobs once it has exhausted the rest
        for (Tier t : Tier.values()) {
            List<MobCard> preferred = buckets.get(t);
            List<MobCard> fallback = overlap.get(t);
            Collections.shuffle(preferred, shuffler);
            Collections.shuffle(fallback, shuffler);
            fallback.addAll(preferred);
            buckets.put(t, fallback);
        }

        // the curve: exactly one legendary (decks of 6+), a few epics/rares,
        // a fifth uncommons, and commons fill the rest — always the majority
        int legendary = size >= 6 ? 1 : 0;
        int epic = Math.round(size * 0.08f);
        int rare = Math.round(size * 0.14f);
        int uncommon = Math.round(size * 0.20f);

        List<MobCard> out = new ArrayList<>(size);
        take(buckets.get(Tier.LEGENDARY), legendary, out);
        take(buckets.get(Tier.EPIC), epic, out);
        take(buckets.get(Tier.RARE), rare, out);
        take(buckets.get(Tier.UNCOMMON), uncommon, out);
        take(buckets.get(Tier.COMMON), size - out.size(), out);
        // commons exhausted (big decks): top up from the lower tiers, never legendary
        for (Tier t : new Tier[]{Tier.UNCOMMON, Tier.RARE, Tier.EPIC}) {
            if (out.size() >= size) break;
            take(buckets.get(t), size - out.size(), out);
        }
        Collections.shuffle(out, shuffler);
        return out;
    }

    /**
     * Give {@code hand} the same spread of holo upgrade levels as {@code levels}
     * describes, so an opponent dealt against a well-hunted deck is upgraded to
     * the same degree rather than fielding plain cards against boosted ones.
     *
     * <p>The levels are shuffled across the hand rather than stacked onto its
     * best cards: the point is to match the player's investment, not to play
     * optimally with it. Any shortfall is padded with unupgraded cards.
     */
    public static List<MobCard> matchLevels(List<MobCard> hand, List<Integer> levels,
                                            RandomGenerator random) {
        if (hand.isEmpty() || levels == null || levels.isEmpty()) {
            return hand;
        }
        List<Integer> pool = new ArrayList<>(levels);
        while (pool.size() < hand.size()) {
            pool.add(0);
        }
        Collections.shuffle(pool, java.util.Random.from(random));
        List<MobCard> out = new ArrayList<>(hand.size());
        for (int i = 0; i < hand.size(); i++) {
            out.add(hand.get(i).upgraded(Math.max(0, pool.get(i))));
        }
        return out;
    }

    private static void take(List<MobCard> from, int count, List<MobCard> into) {
        for (int i = 0; i < count && !from.isEmpty(); i++) {
            into.add(from.remove(from.size() - 1));
        }
    }
}
