package com.jrpetty.mobtrumps.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The twenty campaign missions and the deck each one is played with.
 *
 * <p>A mission is fought hand against hand: the opponent's {@link #DECK_SIZE}
 * cards against the same number of the player's own. The deck is built round
 * the mission's anchor category — {@link CampaignMission#anchorCards()} of its
 * members, always including the trophy mob — and padded from OUTSIDE that
 * category. The tier band is where the difficulty lives: it decides which
 * members of the set turn up and what kind of stray is brought in. Mission 1
 * props four farm animals up with two mild strays; mission 20 backs all three
 * bosses with three legendaries.
 *
 * <p>Padding prefers categories that feel related to the anchor, so the deck
 * still reads as a themed set rather than a shuffle, and it never draws a
 * boss: the Dragon, the Wither and the Warden are saved for the two missions
 * built round them. Everything is drawn against a seed derived from the
 * mission id, so a mission's six cards never change.
 */
public final class CampaignDecks {

    public static final int DECK_SIZE = Battle.HAND_SIZE;

    /** Which categories feel like near neighbours, for choosing padding. */
    private static final Map<Category, List<Category>> AFFINITY = new EnumMap<>(Category.class);

    private static final Map<String, CampaignMission> BY_ID = new LinkedHashMap<>();
    public static final List<CampaignMission> ALL;

    private static void mission(int index, String id, String name, String tagline,
                                Category anchor, int anchorCards,
                                Tier min, Tier max, Difficulty brain, boolean counting,
                                int cpuLevel, String trophyMob) {
        BY_ID.put(id, new CampaignMission(index, id, name, tagline, anchor, anchorCards, min, max,
                brain, counting, cpuLevel, trophyMob));
    }

    static {
        AFFINITY.put(Category.FARM, List.of(Category.CREATURE, Category.VILLAGE));
        AFFINITY.put(Category.CREATURE, List.of(Category.FARM, Category.AQUATIC, Category.VILLAGE));
        AFFINITY.put(Category.VILLAGE, List.of(Category.FARM, Category.ILLAGER, Category.CREATURE));
        AFFINITY.put(Category.AQUATIC, List.of(Category.CREATURE, Category.MONSTER));
        AFFINITY.put(Category.UNDEAD, List.of(Category.MONSTER, Category.ILLAGER, Category.NETHER));
        AFFINITY.put(Category.MONSTER, List.of(Category.UNDEAD, Category.END, Category.ILLAGER));
        AFFINITY.put(Category.NETHER, List.of(Category.MONSTER, Category.UNDEAD, Category.ILLAGER));
        AFFINITY.put(Category.ILLAGER, List.of(Category.VILLAGE, Category.UNDEAD, Category.MONSTER));
        AFFINITY.put(Category.END, List.of(Category.MONSTER, Category.NETHER));
        AFFINITY.put(Category.BOSS, List.of(Category.END, Category.NETHER, Category.ILLAGER));

        mission(1, "first_pasture", "The First Pasture",
                "The farmyard regulars, and a couple of strays that wandered in.",
                Category.FARM, 4, Tier.UNCOMMON, Tier.UNCOMMON, Difficulty.NORMAL, false, 0, "cow");
        mission(2, "woodland_wanderers", "Woodland Wanderers",
                "The quiet things in the trees, and what keeps them company.",
                Category.CREATURE, 4, Tier.UNCOMMON, Tier.RARE, Difficulty.NORMAL, false, 0, "fox");
        mission(3, "shallow_water", "Shallow Water",
                "Reef and riverbed. Nothing down here bites hard yet.",
                Category.AQUATIC, 4, Tier.UNCOMMON, Tier.UNCOMMON, Difficulty.NORMAL, false, 1, "dolphin");
        mission(4, "shallow_graves", "Shallow Graves",
                "The restless dead, still close to the surface.",
                Category.UNDEAD, 4, Tier.UNCOMMON, Tier.RARE, Difficulty.NORMAL, false, 1, "drowned");
        mission(5, "stampede", "Stampede",
                "The same herd, and this time something is driving it.",
                Category.FARM, 4, Tier.RARE, Tier.RARE, Difficulty.NORMAL, false, 1, "horse");
        mission(6, "things_that_hiss", "Things That Hiss",
                "Strangers stand in the dark behind the creepers.",
                Category.MONSTER, 4, Tier.UNCOMMON, Tier.RARE, Difficulty.NORMAL, false, 0, "creeper");
        mission(7, "tooth_and_claw", "Tooth and Claw",
                "The woods again, and the woods have grown teeth.",
                Category.CREATURE, 4, Tier.RARE, Tier.RARE, Difficulty.NORMAL, false, 1, "wolf");
        mission(8, "trading_post", "The Trading Post",
                "Four villagers, and the two best hands they could hire.",
                Category.VILLAGE, 4, Tier.RARE, Tier.RARE, Difficulty.NORMAL, false, 1, "villager");
        mission(9, "the_deep", "The Deep",
                "Past the reef, where the guardians are.",
                Category.AQUATIC, 4, Tier.RARE, Tier.EPIC, Difficulty.NORMAL, false, 1, "elder_guardian");
        mission(10, "ashlands", "Ashlands",
                "Everything here is on fire or about to be.",
                Category.NETHER, 4, Tier.RARE, Tier.EPIC, Difficulty.NORMAL, false, 0, "hoglin");
        mission(11, "the_long_night", "The Long Night",
                "The graves gave up pretending to be shallow.",
                Category.UNDEAD, 4, Tier.EPIC, Tier.EPIC, Difficulty.HARD, false, 0, "wither_skeleton");
        mission(12, "raid_bells", "Raid Bells",
                "Six illagers, and a great many reasons to run.",
                Category.ILLAGER, 6, Tier.RARE, Tier.EPIC, Difficulty.HARD, false, 1, "ravager");
        mission(13, "cave_in", "Cave-In",
                "Everything that lives in the dark, all at once.",
                Category.MONSTER, 6, Tier.EPIC, Tier.EPIC, Difficulty.HARD, false, 1, "breeze");
        mission(14, "iron_and_emerald", "Iron and Emerald",
                "The village hired better help this time.",
                Category.VILLAGE, 3, Tier.EPIC, Tier.EPIC, Difficulty.HARD, false, 2, "iron_golem");
        mission(15, "the_fortress", "The Fortress",
                "Bridges over the lava, and nothing on them that will step aside.",
                Category.NETHER, 4, Tier.EPIC, Tier.EPIC, Difficulty.HARD, false, 2, "piglin_brute");
        mission(16, "void_touched", "Void-Touched",
                "Three things out of the End, and everything they brought with them.",
                Category.END, 3, Tier.EPIC, Tier.EPIC, Difficulty.HARD, true, 1, "shulker");
        mission(17, "the_mansion", "The Mansion",
                "Every room is occupied. It knows which cards are left.",
                Category.ILLAGER, 4, Tier.EPIC, Tier.LEGENDARY, Difficulty.HARD, true, 1, "evoker");
        mission(18, "outer_isles", "The Outer Isles",
                "The far islands, and nothing gentle on them.",
                Category.END, 3, Tier.EPIC, Tier.LEGENDARY, Difficulty.HARD, true, 2, "enderman");
        mission(19, "reckoning", "Reckoning",
                "Two of the three are here. Bring everything.",
                Category.BOSS, 2, Tier.EPIC, Tier.LEGENDARY, Difficulty.HARD, true, 0, "wither");
        mission(20, "last_trump", "The Last Trump",
                "Dragon, Wither, Warden — and the legends still standing behind them.",
                Category.BOSS, 3, Tier.LEGENDARY, Tier.LEGENDARY, Difficulty.HARD, true, 0, "warden");

        ALL = List.copyOf(BY_ID.values());
    }

    private CampaignDecks() {
    }

    public static CampaignMission byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** The mission at a 1-based index, or null. */
    public static CampaignMission byIndex(int index) {
        return index < 1 || index > ALL.size() ? null : ALL.get(index - 1);
    }

    public static int count() {
        return ALL.size();
    }

    /**
     * Build a mission's deck: its share of the anchor category, padded to
     * {@link #DECK_SIZE} from outside it. Deterministic — the same mission
     * always produces the same six cards, in the same order: the trophy mob,
     * the rest of the anchor share, then the padding.
     */
    public static List<MobCard> deck(CampaignMission mission) {
        return deck(mission, DECK_SIZE);
    }

    /**
     * The opponent's deck: always exactly {@link #DECK_SIZE}. The player brings
     * the same number of their own, so neither side ever holds more cards than
     * the other — the difficulty lives in the cards themselves and in how the
     * opponent plays them, never in a card-count advantage.
     */
    public static List<MobCard> cpuDeck(CampaignMission mission) {
        return deck(mission, DECK_SIZE);
    }

    public static List<MobCard> deck(CampaignMission mission, int size) {
        Random rng = new Random(seed(mission.id()));
        List<MobCard> deck = anchorPicks(mission, Math.min(size, mission.anchorCount()), rng);
        int need = size - deck.size();
        if (need > 0) {
            deck.addAll(subsidy(mission, need, rng));
        }
        return deck;
    }

    /**
     * The anchor set's share of the deck: the trophy mob first, because it is
     * the face of the mission, then the members nearest the mission's tier
     * band, nearest first. A seeded shuffle decides between members that are
     * equally near, and a set no bigger than its share is simply used whole.
     */
    private static List<MobCard> anchorPicks(CampaignMission mission, int count, Random rng) {
        List<MobCard> members = new ArrayList<>();
        for (String id : MobCategories.members(mission.anchor())) {
            MobCard card = MobCards.byId(id);
            if (card != null) {
                members.add(card);
            }
        }
        Collections.shuffle(members, rng);
        // a stable sort, so the shuffle above is what breaks the ties
        members.sort(Comparator
                .comparingInt((MobCard c) -> c.id().equals(mission.trophyMob()) ? 0 : 1)
                .thenComparingInt(c -> tierDistance(c.tier(), mission.minTier(), mission.maxTier())));
        return new ArrayList<>(members.subList(0, Math.min(count, members.size())));
    }

    /**
     * The padding. Candidates are every card outside the anchor whose tier is
     * in the mission's band, ordered so that near-neighbour categories come
     * first, then shuffled within each band of preference by the mission seed.
     * If the band cannot supply enough, it widens outward rather than failing.
     * A boss is never padding — not even in a band that reaches legendary.
     */
    private static List<MobCard> subsidy(CampaignMission mission, int need, Random rng) {
        List<Category> affine = AFFINITY.getOrDefault(mission.anchor(), List.of());

        List<MobCard> preferred = new ArrayList<>();
        List<MobCard> fallback = new ArrayList<>();
        for (MobCard card : MobCards.ALL) {
            Category cat = card.category();
            if (!mayPad(mission, card)) {
                continue;
            }
            if (!inBand(card.tier(), mission.minTier(), mission.maxTier())) {
                continue;
            }
            (affine.contains(cat) ? preferred : fallback).add(card);
        }
        Collections.shuffle(preferred, rng);
        Collections.shuffle(fallback, rng);

        List<MobCard> out = new ArrayList<>(need);
        take(preferred, need - out.size(), out);
        take(fallback, need - out.size(), out);

        // the band was too narrow to fill the deck — widen a tier at a time
        for (int widen = 1; out.size() < need && widen <= Tier.values().length; widen++) {
            List<MobCard> extra = new ArrayList<>();
            for (MobCard card : MobCards.ALL) {
                if (!mayPad(mission, card) || out.contains(card)) {
                    continue;
                }
                int distance = tierDistance(card.tier(), mission.minTier(), mission.maxTier());
                if (distance == widen) {
                    extra.add(card);
                }
            }
            Collections.shuffle(extra, rng);
            take(extra, need - out.size(), out);
        }
        return out;
    }

    /** Padding comes from outside the anchor, and never from the bosses. */
    private static boolean mayPad(CampaignMission mission, MobCard card) {
        Category cat = card.category();
        return cat != mission.anchor() && cat != Category.BOSS;
    }

    private static void take(List<MobCard> from, int count, List<MobCard> into) {
        for (int i = 0; i < count && i < from.size(); i++) {
            into.add(from.get(i));
        }
    }

    private static boolean inBand(Tier tier, Tier min, Tier max) {
        return tier.ordinal() >= min.ordinal() && tier.ordinal() <= max.ordinal();
    }

    /** How many tiers outside the band a card sits, 0 if inside it. */
    private static int tierDistance(Tier tier, Tier min, Tier max) {
        if (inBand(tier, min, max)) {
            return 0;
        }
        return tier.ordinal() < min.ordinal()
                ? min.ordinal() - tier.ordinal()
                : tier.ordinal() - max.ordinal();
    }

    /** A stable seed from the mission id, so decks survive restarts unchanged. */
    private static long seed(String id) {
        long h = 1125899906842597L;
        for (int i = 0; i < id.length(); i++) {
            h = 31 * h + id.charAt(i);
        }
        return h;
    }
}
