package com.jrpetty.mobtrumps.game;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Plain descriptive facts about each mob, for asking questions about it.
 *
 * <p>These exist because a card's six numbers cannot tell every mob apart:
 * Skeleton and Zombie, Bogged and Stray, and Cow and Sheep are identical on all
 * six <em>and</em> share a category, so no arithmetic separates them. A deduction
 * game built on numbers alone would end in a coin flip whenever the hidden mob
 * was one of those six. They are better questions besides — "does it fly?" is
 * something a player actually wants to ask; "is its Size below 4?" is not.
 *
 * <p>Descriptive only. Nothing here is consulted during a battle: a card still
 * carries nothing but its numbers, and these tags never change a duel.
 */
public enum MobTrait {

    FLIES("Does it fly?",
            "allay", "bat", "bee", "blaze", "breeze", "ender_dragon", "ghast",
            "parrot", "phantom", "vex", "wither"),

    HOSTILE("Is it hostile?",
            "blaze", "bogged", "breeze", "cave_spider", "creaking", "creeper",
            "drowned", "elder_guardian", "ender_dragon", "enderman",
            "endermite", "evoker", "ghast", "guardian", "hoglin", "husk",
            "magma_cube", "phantom", "piglin", "piglin_brute", "pillager",
            "ravager", "shulker", "silverfish", "skeleton", "slime", "spider",
            "stray", "sulfur_cube", "vex", "vindicator", "warden", "witch",
            "wither", "wither_skeleton", "zoglin", "zombie", "zombie_villager",
            "zombified_piglin"),

    RANGED("Does it attack from range?",
            "blaze", "bogged", "breeze", "elder_guardian", "ender_dragon",
            "ghast", "guardian", "llama", "pillager", "shulker", "skeleton",
            "snow_golem", "stray", "trader_llama", "witch", "wither"),

    TAMEABLE("Can it be tamed?",
            "cat", "donkey", "horse", "llama", "mule", "parrot", "wolf"),

    RIDEABLE("Can you ride it?",
            "camel", "donkey", "horse", "mule", "pig", "skeleton_horse",
            "strider"),

    SWIMS("Does it live in water?",
            "axolotl", "cod", "dolphin", "drowned", "elder_guardian", "frog",
            "glow_squid", "guardian", "pufferfish", "salmon", "squid",
            "tadpole", "tropical_fish", "turtle"),

    SHEARABLE("Can you shear it?",
            "mooshroom", "sheep", "snow_golem"),

    GLOWS("Does it give off light?",
            "allay", "blaze", "glow_squid", "magma_cube", "sulfur_cube",
            "warden"),

    COLD("Does it live somewhere cold?",
            "goat", "polar_bear", "snow_golem", "stray"),

    BABY("Does it have a baby form?",
            "armadillo", "camel", "cat", "chicken", "cow", "donkey", "drowned",
            "fox", "goat", "hoglin", "horse", "husk", "llama", "mooshroom",
            "mule", "ocelot", "panda", "pig", "piglin", "polar_bear", "rabbit",
            "sheep", "sniffer", "strider", "trader_llama", "turtle", "villager",
            "wolf", "zoglin", "zombie", "zombie_villager", "zombified_piglin"),

    ARTHROPOD("Is it a bug?",
            "bee", "cave_spider", "endermite", "silverfish", "spider"),

    HUMANOID("Does it stand on two legs?",
            "allay", "bogged", "drowned", "evoker", "husk", "iron_golem",
            "piglin", "piglin_brute", "pillager", "skeleton", "snow_golem",
            "stray", "vex", "villager", "vindicator", "wandering_trader",
            "warden", "witch", "wither_skeleton", "zombie", "zombie_villager",
            "zombified_piglin"),

    TRADES("Can you trade with it?",
            "piglin", "villager", "wandering_trader"),

    NOCTURNAL("Does it burn in daylight?",
            "bogged", "drowned", "phantom", "skeleton", "stray", "zombie",
            "zombie_villager");

    private final String question;
    private final Set<String> members;

    MobTrait(String question, String... ids) {
        this.question = question;
        this.members = new LinkedHashSet<>(Arrays.asList(ids));
    }

    /** The question as a player reads it, e.g. "Does it fly?". */
    public String question() {
        return question;
    }

    public boolean has(String mobId) {
        return mobId != null && members.contains(mobId);
    }

    /** How many of the 81 carry this trait — a wide question or a narrow one. */
    public int count() {
        return members.size();
    }
}
