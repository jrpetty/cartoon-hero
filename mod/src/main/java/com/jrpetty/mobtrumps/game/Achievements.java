package com.jrpetty.mobtrumps.game;

import com.jrpetty.mobtrumps.game.Achievement.Group;
import com.jrpetty.mobtrumps.game.Achievement.Reward;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The award catalogue behind the collection book's last pages.
 *
 * <p>Rewards are deliberately gentle at the bottom — a handful of iron for
 * beating the Easy CPU, because that is not a hard thing to do — and climb
 * steeply only where the ask does: fifty Hard-CPU wins, a hundred PvP duels,
 * every one of the 81 cards. Nothing here should ever feel like the fastest way
 * to get diamonds; it is a record of what you have done that happens to pay.
 */
public final class Achievements {

    private static final Map<String, Achievement> BY_ID = new LinkedHashMap<>();
    private static final Map<Group, List<Achievement>> BY_GROUP = new EnumMap<>(Group.class);
    public static final List<Achievement> ALL;

    private static void add(String id, Group group, String title, String description,
                            String metric, int target, Reward... rewards) {
        BY_ID.put(id, new Achievement(id, group, title, description, metric, target,
                List.of(rewards)));
    }

    private static Reward iron(int n) {
        return new Reward("iron_ingot", n);
    }

    private static Reward gold(int n) {
        return new Reward("gold_ingot", n);
    }

    private static Reward diamond(int n) {
        return new Reward("diamond", n);
    }

    private static Reward xp(int n) {
        return new Reward("experience_bottle", n);
    }

    private static Reward scrap(int n) {
        return new Reward("netherite_scrap", n);
    }

    static {
        // --- Collector: filling the binder -------------------------------
        add("first_card", Group.COLLECTOR, "First Print",
                "Collect your first mob card", "cards", 1, iron(3));
        add("cards_10", Group.COLLECTOR, "Getting Started",
                "Collect 10 different cards", "cards", 10, iron(6), gold(2));
        add("cards_30", Group.COLLECTOR, "Shoebox Full",
                "Collect 30 different cards", "cards", 30, iron(10), gold(4), diamond(1));
        add("cards_60", Group.COLLECTOR, "Serious Collector",
                "Collect 60 different cards", "cards", 60, diamond(3), gold(8));
        add("cards_all", Group.COLLECTOR, "The Complete Set",
                "Collect all 81 mob cards", "cards", 81,
                diamond(8), new Reward("netherite_ingot", 1), xp(16));
        add("foil_1", Group.COLLECTOR, "Caught the Light",
                "Collect your first holographic", "foils", 1, iron(4), gold(1));
        add("foil_10", Group.COLLECTOR, "Shelf of Shine",
                "Collect 10 holographics", "foils", 10, diamond(2), gold(6));
        add("foil_30", Group.COLLECTOR, "Prism Case",
                "Collect 30 holographics", "foils", 30, diamond(5), scrap(2));
        add("legends_5", Group.COLLECTOR, "Legends in the Binder",
                "Own 5 Legendary-tier cards", "legendaries", 5,
                diamond(3), new Reward("gold_block", 1));
        add("sets_all", Group.COLLECTOR, "Curator of Everything",
                "Complete every one of the 10 sets", "categories", 10,
                new Reward("netherite_ingot", 1), new Reward("diamond_block", 1),
                new Reward("enchanted_golden_apple", 1));

        // --- The Arena: games against the CPU ----------------------------
        // small rewards on purpose — the CPU is practice, not a diamond mine
        add("cpu_easy_1", Group.ARENA, "Sparring Partner",
                "Beat the Easy CPU once", "cpu_wins_easy", 1, iron(3));
        add("cpu_easy_10", Group.ARENA, "Warmed Up",
                "Beat the Easy CPU 10 times", "cpu_wins_easy", 10, iron(8), gold(2));
        add("cpu_easy_25", Group.ARENA, "Practice Makes Perfect",
                "Beat the Easy CPU 25 times", "cpu_wins_easy", 25, iron(12), gold(4), diamond(1));
        add("cpu_normal_5", Group.ARENA, "Fair Fight",
                "Beat the Normal CPU 5 times", "cpu_wins_normal", 5, iron(6), gold(3));
        add("cpu_normal_20", Group.ARENA, "Steady Hand",
                "Beat the Normal CPU 20 times", "cpu_wins_normal", 20, diamond(1), gold(8));
        add("cpu_normal_50", Group.ARENA, "House Favourite",
                "Beat the Normal CPU 50 times", "cpu_wins_normal", 50, diamond(3), gold(12), xp(8));
        add("cpu_hard_3", Group.ARENA, "Reading the Machine",
                "Beat the Hard CPU 3 times", "cpu_wins_hard", 3, diamond(1), gold(6));
        add("cpu_hard_15", Group.ARENA, "Card Counter",
                "Beat the Hard CPU 15 times", "cpu_wins_hard", 15,
                diamond(3), new Reward("gold_block", 1));
        add("cpu_hard_40", Group.ARENA, "Ghost in the Deck",
                "Beat the Hard CPU 40 times", "cpu_wins_hard", 40, diamond(6), scrap(2));
        add("games_25", Group.ARENA, "Pull Up a Chair",
                "Play 25 games of Mob Trumps", "games", 25, iron(6), gold(4));
        add("games_100", Group.ARENA, "Table Regular",
                "Play 100 games of Mob Trumps", "games", 100, diamond(3), xp(16));

        // --- Duelist: beating real players --------------------------------
        add("duel_1", Group.DUELIST, "First Blood",
                "Beat another player in a duel", "duel_wins", 1, iron(4), gold(2));
        add("duel_5", Group.DUELIST, "Rival",
                "Beat other players 5 times", "duel_wins", 5, diamond(1), gold(6));
        add("duel_15", Group.DUELIST, "Contender",
                "Beat other players 15 times", "duel_wins", 15,
                diamond(3), new Reward("gold_block", 1));
        add("duel_40", Group.DUELIST, "Table Boss",
                "Beat other players 40 times", "duel_wins", 40, diamond(6), scrap(2), xp(8));
        add("duel_100", Group.DUELIST, "Grandmaster",
                "Beat other players 100 times", "duel_wins", 100,
                new Reward("netherite_ingot", 2), new Reward("enchanted_golden_apple", 1));

        // --- the parlour: the other games the deck plays -------------------
        add("twentyone_1", Group.PARLOUR, "Twenty-One",
                "Beat the dealer at Twenty-One", "twentyone_wins", 1, iron(6), xp(1));
        add("twentyone_10", Group.PARLOUR, "Counting the Deck",
                "Beat the dealer 10 times", "twentyone_wins", 10, gold(5), xp(3));
        add("twentyone_30", Group.PARLOUR, "House's Nightmare",
                "Beat the dealer 30 times", "twentyone_wins", 30, diamond(3), xp(6));
        add("guesswho_1", Group.PARLOUR, "Got Your Number",
                "Find the hidden mob in Guess Who", "guesswho_wins", 1, iron(6), xp(1));
        add("guesswho_10", Group.PARLOUR, "Twenty Questions",
                "Win Guess Who 10 times", "guesswho_wins", 10, gold(5), xp(3));
        add("guesswho_25", Group.PARLOUR, "Mind Reader",
                "Win Guess Who 25 times", "guesswho_wins", 25, diamond(3), xp(6));
        add("bluff_1", Group.PARLOUR, "Straight Face",
                "Win a game of Mob Bluff", "bluff_wins", 1, iron(6), xp(1));
        add("bluff_10", Group.PARLOUR, "Card Sharp",
                "Win Mob Bluff 10 times", "bluff_wins", 10, gold(5), xp(3));
        add("bluff_30", Group.PARLOUR, "Poker Face",
                "Win Mob Bluff 30 times", "bluff_wins", 30, diamond(4), xp(8));
        add("bluff_catch_15", Group.PARLOUR, "Called It",
                "Catch 15 liars in Mob Bluff", "bluff_catches", 15, gold(6), xp(4));

        // easy — just turn up and win one of each
        add("parlour_taster", Group.PARLOUR, "Pull Up a Stool",
                "Win at Twenty-One, Guess Who and Mob Bluff", "parlour_all", 3,
                gold(4), xp(2));
        add("guesswho_50", Group.PARLOUR, "Face in the Crowd",
                "Win Guess Who 50 times", "guesswho_wins", 50, diamond(5), xp(8));
        add("bluff_catch_50", Group.PARLOUR, "Nothing Gets Past",
                "Catch 50 liars in Mob Bluff", "bluff_catches", 50, diamond(4), xp(8));

        // hard — these need the game played well, not just played
        add("guesswho_swift_1", Group.PARLOUR, "Five and Out",
                "Find the mob in five questions or fewer", "guesswho_swift", 1,
                gold(8), xp(4));
        add("guesswho_swift_10", Group.PARLOUR, "Short Work",
                "Do it in five or fewer, ten times", "guesswho_swift", 10,
                diamond(5), xp(10));
        add("twentyone_exact_1", Group.PARLOUR, "On the Nose",
                "Land on exactly 21", "twentyone_exact", 1, gold(8), xp(4));
        add("bluff_lastcard_1", Group.PARLOUR, "Last Card Standing",
                "Win Mob Bluff by catching a liar on your final card",
                "bluff_lastcard", 1, diamond(4), xp(8));

        // brutal — the rarest things the parlour can produce
        add("guesswho_sharp", Group.PARLOUR, "Four Questions",
                "Find the mob in four questions or fewer. Eighty-one faces need "
                        + "six to separate — this one needs the board to fall your way too",
                "guesswho_sharp", 1, diamond(10), scrap(2), xp(16));
        add("guesswho_highroller", Group.PARLOUR, "House Money",
                "Win a Guess Who round with 10,000 fragments or more on the table",
                "guesswho_highroller", 1, new Reward("netherite_ingot", 1), xp(20));
        add("twentyone_exact_10", Group.PARLOUR, "Dead Reckoning",
                "Land on exactly 21 ten times", "twentyone_exact", 10,
                new Reward("netherite_ingot", 1), diamond(6), xp(20));
        add("bluff_100", Group.PARLOUR, "The Whole Table",
                "Win Mob Bluff 100 times", "bluff_wins", 100,
                new Reward("netherite_ingot", 1), new Reward("diamond_block", 1), xp(24));

        // --- Hunter: out in the world -------------------------------------
        add("kills_25", Group.HUNTER, "Field Work",
                "Hunt 25 mobs", "kills", 25, iron(4));
        add("kills_250", Group.HUNTER, "Big Game",
                "Hunt 250 mobs", "kills", 250, iron(10), gold(6), diamond(1));
        add("kills_1000", Group.HUNTER, "Exterminator",
                "Hunt 1000 mobs", "kills", 1000, diamond(5), scrap(2));
        add("picks_300", Group.HUNTER, "Tactician",
                "Pick a stat 300 times in battle", "picks", 300, diamond(2), gold(8));
        add("holo_3", Group.HUNTER, "Perfect Print",
                "Take any card all the way to Holo III", "holo3", 1,
                diamond(3), new Reward("gold_block", 1), xp(8));

        ALL = List.copyOf(BY_ID.values());
        for (Group g : Group.values()) {
            List<Achievement> list = new ArrayList<>();
            for (Achievement a : ALL) {
                if (a.group() == g) list.add(a);
            }
            BY_GROUP.put(g, List.copyOf(list));
        }
    }

    private Achievements() {
    }

    public static Achievement byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** Every achievement in a group, in catalogue (increasing difficulty) order. */
    public static List<Achievement> of(Group group) {
        return BY_GROUP.getOrDefault(group, List.of());
    }
}
