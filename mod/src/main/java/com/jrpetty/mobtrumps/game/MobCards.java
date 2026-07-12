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

    /** Position of a card in the collection (0-based), or -1 if unknown. */
    public static int ordinal(String id) {
        if (id == null) return -1;
        return ORDINALS.getOrDefault(id.toLowerCase(Locale.ROOT), -1);
    }

    /**
     * Draw {@code count} distinct cards weighted by spawn rarity: a rarity 10
     * mob is ten times more likely to be pulled than a rarity 1 legendary.
     */
    public static List<MobCard> openPack(int count, RandomGenerator random) {
        return openPack(ALL, count, null, random);
    }

    /**
     * Draw {@code count} distinct cards from {@code source}, weighted by spawn
     * rarity. If {@code guarantee} is non-null, at least one pull is of that
     * collector tier or better (a lower-tier pull is swapped out to ensure it).
     */
    public static List<MobCard> openPack(List<MobCard> source, int count, Tier guarantee,
                                         RandomGenerator random) {
        List<MobCard> from = (source == null || source.isEmpty()) ? ALL : source;
        count = Math.max(1, Math.min(count, from.size()));
        List<MobCard> pool = new ArrayList<>(from);
        List<MobCard> pulls = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            drawWeighted(pool, pulls, random);
        }

        if (guarantee != null && pulls.stream().noneMatch(c -> meetsTier(c, guarantee))) {
            // pull a guaranteed high-rarity card that isn't already in the pack
            List<MobCard> high = new ArrayList<>();
            for (MobCard c : from) {
                if (meetsTier(c, guarantee) && !pulls.contains(c)) high.add(c);
            }
            if (!high.isEmpty()) {
                MobCard swap = high.get(random.nextInt(high.size()));
                // replace the lowest-tier (most common) pull
                int worst = 0;
                for (int i = 1; i < pulls.size(); i++) {
                    if (pulls.get(i).tier().ordinal() < pulls.get(worst).tier().ordinal()) worst = i;
                }
                pulls.set(worst, swap);
            }
        }
        return pulls;
    }

    private static void drawWeighted(List<MobCard> pool, List<MobCard> out, RandomGenerator random) {
        if (pool.isEmpty()) return;
        int totalWeight = pool.stream().mapToInt(MobCard::rarity).sum();
        int roll = random.nextInt(Math.max(1, totalWeight));
        for (int j = 0; j < pool.size(); j++) {
            roll -= pool.get(j).rarity();
            if (roll < 0) {
                out.add(pool.remove(j));
                return;
            }
        }
        out.add(pool.remove(pool.size() - 1));
    }

    private static boolean meetsTier(MobCard c, Tier guarantee) {
        return c.tier().ordinal() >= guarantee.ordinal();
    }

    /** Cards whose collector tier is {@code tier} or better. */
    public static List<MobCard> tierAtLeast(Tier tier) {
        List<MobCard> out = new ArrayList<>();
        for (MobCard c : ALL) {
            if (c.tier().ordinal() >= tier.ordinal()) out.add(c);
        }
        return out;
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
}
