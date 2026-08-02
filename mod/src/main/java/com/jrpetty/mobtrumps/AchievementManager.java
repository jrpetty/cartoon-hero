package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Achievement;
import com.jrpetty.mobtrumps.game.Achievements;
import com.jrpetty.mobtrumps.game.Category;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
import com.jrpetty.mobtrumps.game.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks progress toward every {@link Achievements} award and pays them out on
 * request. Progress is read live from counters the mod already keeps, so no
 * event needs to remember to bump anything: {@link #refresh} recomputes the
 * whole board and is safe to call as often as you like.
 *
 * <p>Rewards are never granted automatically. Reaching the target only unlocks
 * the award — the player collects it from the book's award pages, which is what
 * makes it feel like opening a drawer of things you earned.
 */
public final class AchievementManager {

    /** Award states stored in {@link ModAttachments#ACHIEVEMENTS}. */
    public static final int UNLOCKED = 1;
    public static final int CLAIMED = 2;

    private AchievementManager() {
    }

    // --- metrics ------------------------------------------------------------

    /** The player's current value for an achievement metric key. */
    public static int metric(ServerPlayer player, String key) {
        return switch (key) {
            case "cards" -> player.getData(ModAttachments.COLLECTED.get()).size();
            case "foils" -> player.getData(ModAttachments.COLLECTED_FOIL.get()).size();
            case "duel_wins" -> player.getData(ModAttachments.DUEL_WINS.get());
            case "kills" -> StatsTracker.totalKills(player);
            case "cpu_wins_easy" -> StatsTracker.count(player, "battle_wins_easy");
            case "cpu_wins_normal" -> StatsTracker.count(player, "battle_wins_normal");
            case "cpu_wins_hard" -> StatsTracker.count(player, "battle_wins_hard");
            case "cpu_wins" -> StatsTracker.count(player, "battle_wins");
            case "games" -> StatsTracker.count(player, "games_played");
            case "twentyone_wins" -> StatsTracker.count(player, "twentyone_wins");
            case "guesswho_wins" -> StatsTracker.count(player, "guesswho_wins");
            case "bluff_wins" -> StatsTracker.count(player, "bluff_wins");
            case "bluff_catches" -> StatsTracker.count(player, "bluff_catches");
            case "bluff_lastcard" -> StatsTracker.count(player, "bluff_lastcard");
            case "guesswho_swift" -> StatsTracker.count(player, "guesswho_swift");
            case "guesswho_sharp" -> StatsTracker.count(player, "guesswho_sharp");
            case "guesswho_highroller" -> StatsTracker.count(player, "guesswho_highroller");
            case "twentyone_exact" -> StatsTracker.count(player, "twentyone_exact");
            case "parlour_all" -> parlourSpread(player);
            case "picks" -> totalPicks(player);
            case "legendaries" -> tierCount(player, Tier.LEGENDARY);
            case "categories" -> categoriesComplete(player);
            case "holo3" -> maxedCards(player);
            default -> 0;
        };
    }

    /** How many of the three parlour games the player has actually won. */
    private static int parlourSpread(ServerPlayer player) {
        int n = 0;
        for (String key : new String[]{"twentyone_wins", "guesswho_wins", "bluff_wins"}) {
            if (StatsTracker.count(player, key) > 0) {
                n++;
            }
        }
        return n;
    }

    private static int totalPicks(ServerPlayer player) {
        int total = 0;
        for (Stat s : Stat.values()) {
            total += StatsTracker.count(player, "pick_" + s.key());
        }
        return total;
    }

    private static int tierCount(ServerPlayer player, Tier tier) {
        int n = 0;
        for (String id : player.getData(ModAttachments.COLLECTED.get())) {
            MobCard card = MobCards.byId(id);
            if (card != null && card.tier() == tier) n++;
        }
        return n;
    }

    /**
     * How many sets are finished. Builds ONE hash set of the player's
     * collection and tests every category against it: the old version asked
     * CategoryRewards ten times, and each of those did a linear scan of the
     * collected LIST once per member — around 6,500 string comparisons, on
     * every mob kill.
     */
    private static int categoriesComplete(ServerPlayer player) {
        java.util.Set<String> owned =
                new java.util.HashSet<>(player.getData(ModAttachments.COLLECTED.get()));
        int done = 0;
        for (Category c : Category.values()) {
            if (owned.containsAll(com.jrpetty.mobtrumps.game.MobCategories.members(c))) done++;
        }
        return done;
    }

    /** How many owned cards have been hunted all the way to Holo III. */
    private static int maxedCards(ServerPlayer player) {
        Map<String, Integer> kills = player.getData(ModAttachments.KILLS.get());
        int n = 0;
        for (var e : kills.entrySet()) {
            MobCard card = MobCards.byId(e.getKey());
            if (card != null && card.tier().upgradeLevel(e.getValue()) >= card.tier().maxLevel()) {
                n++;
            }
        }
        return n;
    }

    // --- state --------------------------------------------------------------

    public static int state(ServerPlayer player, String id) {
        return player.getData(ModAttachments.ACHIEVEMENTS.get()).getOrDefault(id, 0);
    }

    /** Snapshot of every metric an award cares about, for the client's pages. */
    public static Map<String, Integer> progressMap(ServerPlayer player) {
        Map<String, Integer> out = new HashMap<>();
        for (Achievement a : Achievements.ALL) {
            out.computeIfAbsent(a.metric(), k -> metric(player, k));
        }
        return out;
    }

    /**
     * Recompute unlocks, announce anything newly earned, and push the board to
     * the client. Called after collecting a card, finishing a game, or logging
     * in — anything that could move a counter.
     */
    public static void refresh(ServerPlayer player) {
        ServerSync.markAwards(player);
    }

    /**
     * The real work: recompute every metric, announce anything newly earned and
     * push the board. Expensive enough that it must not run per kill, so it is
     * driven by {@link ServerSync}'s flush.
     */
    public static void recheckNow(ServerPlayer player) {
        Map<String, Integer> states = new HashMap<>(player.getData(ModAttachments.ACHIEVEMENTS.get()));
        List<Achievement> unlocked = new ArrayList<>();
        for (Achievement a : Achievements.ALL) {
            if (states.getOrDefault(a.id(), 0) != 0) {
                continue; // already unlocked or claimed
            }
            if (metric(player, a.metric()) >= a.target()) {
                states.put(a.id(), UNLOCKED);
                unlocked.add(a);
            }
        }
        if (!unlocked.isEmpty()) {
            player.setData(ModAttachments.ACHIEVEMENTS.get(), states);
            for (Achievement a : unlocked) {
                announce(player, a);
            }
        }
        sendNow(player);
    }

    private static void announce(ServerPlayer player, Achievement a) {
        player.sendSystemMessage(Component.literal("★ AWARD UNLOCKED · " + a.title())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("  " + a.description()
                        + " — collect it in your collection book (Awards).")
                .withStyle(ChatFormatting.GRAY));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.1F);
    }

    // --- claiming -----------------------------------------------------------

    /** Pay out one unlocked award straight into the player's inventory. */
    public static void claim(ServerPlayer player, String id) {
        Achievement a = Achievements.byId(id);
        if (a == null) {
            return;
        }
        Map<String, Integer> states = new HashMap<>(player.getData(ModAttachments.ACHIEVEMENTS.get()));
        int st = states.getOrDefault(id, 0);
        if (st == CLAIMED) {
            return;
        }
        if (st != UNLOCKED && metric(player, a.metric()) < a.target()) {
            return; // not earned yet
        }
        // mark first: a hiccup mid-payout must never let it be claimed twice
        states.put(id, CLAIMED);
        player.setData(ModAttachments.ACHIEVEMENTS.get(), Map.copyOf(states));

        for (Achievement.Reward r : a.rewards()) {
            ItemStack stack = stackOf(r);
            if (!stack.isEmpty()) {
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
        player.sendSystemMessage(Component.literal("Collected: " + a.title() + " — " + a.rewardLabel())
                .withStyle(ChatFormatting.GREEN));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
        sendNow(player);
    }

    private static ItemStack stackOf(Achievement.Reward reward) {
        Item item = BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.withDefaultNamespace(reward.item()))
                .orElse(null);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, Math.max(1, reward.count()));
    }

    // --- set spawn eggs -----------------------------------------------------

    /**
     * Hand over the single spawn egg a completed set is worth. Verified end to
     * end: the set must be waiting for a choice, and the mob must belong to it
     * and actually have an egg.
     */
    public static void chooseEgg(ServerPlayer player, String categoryName, String mobId) {
        Category category = SetEggs.category(categoryName);
        if (category == null) {
            return;
        }
        List<String> pending = player.getData(ModAttachments.EGG_PENDING.get());
        if (!pending.contains(category.name())) {
            return;
        }
        Item egg = SetEggs.choices(category).contains(mobId) ? SetEggs.eggFor(mobId) : null;
        if (egg == null) {
            return;
        }
        List<String> nextPending = new ArrayList<>(pending);
        nextPending.remove(category.name());
        List<String> claimed = new ArrayList<>(player.getData(ModAttachments.EGG_CLAIMED.get()));
        claimed.add(category.name() + ":" + mobId);
        player.setData(ModAttachments.EGG_PENDING.get(), List.copyOf(nextPending));
        player.setData(ModAttachments.EGG_CLAIMED.get(), List.copyOf(claimed));

        ItemStack stack = new ItemStack(egg, 1);
        player.getInventory().placeItemBackInInventory(stack);
        player.sendSystemMessage(Component.literal("Set reward claimed: ")
                .withStyle(ChatFormatting.GRAY)
                .append(stack.getHoverName().copy().withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" — one per set, so choose wisely next time.")
                        .withStyle(ChatFormatting.DARK_GRAY)));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.2F);
        sendNow(player);
    }

    /** Queue a completed set's spawn-egg choice (no-op if the set has no eggs). */
    public static void offerEgg(ServerPlayer player, Category category) {
        if (!SetEggs.hasAny(category)) {
            return;
        }
        List<String> pending = player.getData(ModAttachments.EGG_PENDING.get());
        if (pending.contains(category.name())) {
            return;
        }
        for (String entry : player.getData(ModAttachments.EGG_CLAIMED.get())) {
            if (entry.startsWith(category.name() + ":")) {
                return; // already taken, once ever
            }
        }
        List<String> next = new ArrayList<>(pending);
        next.add(category.name());
        player.setData(ModAttachments.EGG_PENDING.get(), List.copyOf(next));
        player.sendSystemMessage(Component.literal("Set complete — pick a free spawn egg from "
                        + category.label() + " in your collection book (Awards).")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    // --- sync ---------------------------------------------------------------

    public static void sync(ServerPlayer player) {
        ServerSync.markAwards(player);
    }

    /** Build and send the award board. */
    public static void sendNow(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new AchievementSyncPayload(
                progressMap(player),
                player.getData(ModAttachments.ACHIEVEMENTS.get()),
                player.getData(ModAttachments.EGG_PENDING.get()),
                player.getData(ModAttachments.EGG_CLAIMED.get())));
    }
}
