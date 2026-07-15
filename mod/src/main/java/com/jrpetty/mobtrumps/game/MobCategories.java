package com.jrpetty.mobtrumps.game;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Assigns every one of the 81 mobs to exactly one {@link Category}. */
public final class MobCategories {

    private static final Map<String, Category> BY_ID = new LinkedHashMap<>();
    private static final Map<Category, List<String>> MEMBERS = new EnumMap<>(Category.class);

    private static void put(Category cat, String... ids) {
        List<String> members = new ArrayList<>();
        for (String id : ids) {
            BY_ID.put(id, cat);
            members.add(id);
        }
        MEMBERS.put(cat, List.copyOf(members));
    }

    static {
        put(Category.FARM, "cow", "sheep", "pig", "chicken", "mooshroom", "rabbit",
                "horse", "donkey", "mule", "goat", "llama", "sniffer", "camel", "armadillo");
        put(Category.CREATURE, "cat", "wolf", "fox", "ocelot", "parrot", "panda",
                "polar_bear", "bee", "bat", "allay", "snow_golem");
        put(Category.VILLAGE, "villager", "wandering_trader", "trader_llama", "iron_golem");
        put(Category.AQUATIC, "cod", "salmon", "tropical_fish", "pufferfish", "squid",
                "glow_squid", "dolphin", "turtle", "axolotl", "tadpole", "frog",
                "guardian", "elder_guardian");
        put(Category.UNDEAD, "zombie", "zombie_villager", "husk", "drowned", "skeleton",
                "stray", "bogged", "wither_skeleton", "skeleton_horse", "phantom",
                "zombified_piglin");
        put(Category.MONSTER, "creeper", "spider", "cave_spider", "silverfish", "slime",
                "breeze", "creaking");
        put(Category.END, "enderman", "endermite", "shulker");
        put(Category.NETHER, "blaze", "ghast", "magma_cube", "strider", "hoglin",
                "piglin", "piglin_brute", "zoglin", "sulfur_cube");
        put(Category.ILLAGER, "pillager", "vindicator", "evoker", "ravager", "witch", "vex");
        put(Category.BOSS, "ender_dragon", "wither", "warden");
    }

    private MobCategories() {
    }

    /** The category of a mob, or null if the id is unknown. */
    public static Category of(String id) {
        return id == null ? null : BY_ID.get(id.toLowerCase(Locale.ROOT));
    }

    /** All mob ids in a category, in display order. */
    public static List<String> members(Category category) {
        return MEMBERS.getOrDefault(category, List.of());
    }

    /** How many mobs a category contains. */
    public static int size(Category category) {
        return members(category).size();
    }
}
