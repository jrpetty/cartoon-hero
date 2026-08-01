package com.jrpetty.aztecabyss.engine;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Money, as content rather than as a constant.
 *
 * <p>Nothing in the engine is allowed to assume "points". A currency is declared -
 * eventually by a ruleset, for now by {@link #register} - and every price anywhere
 * in a map names one. That is what lets a single map sell ammunition for scrap,
 * weapons for points and perks for souls without a line of code changing.
 *
 * <p>The three backings are not skins on the same idea. They play differently, and
 * choosing between them is a real design decision for whoever builds the map:
 *
 * <ul>
 *   <li><b>Virtual</b> - a number the engine holds. It cannot be dropped, handed to
 *       a teammate or stolen off your corpse, and it dies with you. This is the
 *       round-survival model: your score and your spending power are the same thing.
 *   <li><b>Item</b> - a real stack in your inventory <em>is</em> the money. Now it
 *       takes up space, it can be thrown to someone across the map, and it is lying
 *       on the floor where you died for anyone who dares go and get it.
 *   <li><b>Experience</b> - your XP. Buying a weapon competes directly with
 *       enchanting one, which makes every purchase cost something you wanted anyway.
 * </ul>
 */
public final class Currency {

    public enum Backing {
        /** A number the engine tracks per player. */
        VIRTUAL,
        /** A real item stack in the inventory. */
        ITEM,
        /** The player's experience points. */
        EXPERIENCE
    }

    private final String id;
    private final String name;
    private final String symbol;
    private final String colour;
    private final Backing backing;
    private final Item item;
    private final int start;

    private Currency(String id, String name, String symbol, String colour,
                     Backing backing, Item item, int start) {
        this.id = id;
        this.name = name;
        this.symbol = symbol;
        this.colour = colour;
        this.backing = backing;
        this.item = item;
        this.start = start;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String symbol() {
        return symbol;
    }

    /** A legacy colour code, so prices read in the currency's own colour. */
    public String colour() {
        return colour;
    }

    public Backing backing() {
        return backing;
    }

    public int start() {
        return start;
    }

    // ------------------------------------------------------------------
    // The registry
    // ------------------------------------------------------------------

    private static final Map<String, Currency> BY_ID = new LinkedHashMap<>();
    private static String defaultId = "points";

    static {
        // The stock currency, so a map that never mentions money still works.
        register(new Currency("points", "Points", "✦", "§e",
                Backing.VIRTUAL, null, 500));
    }

    public static void register(Currency c) {
        BY_ID.put(c.id(), c);
    }

    /** Builds a virtual currency. */
    public static Currency virtual(String id, String name, String symbol, String colour, int start) {
        return new Currency(id, name, symbol, colour, Backing.VIRTUAL, null, start);
    }

    /** Builds a currency backed by a real item; unknown items fall back to virtual. */
    public static Currency backedByItem(String id, String name, String symbol, String colour,
                                        String itemId) {
        Item found = null;
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
            found = BuiltInRegistries.ITEM.get(rl);
        }
        return found == null
                ? virtual(id, name, symbol, colour, 0)
                : new Currency(id, name, symbol, colour, Backing.ITEM, found, 0);
    }

    public static Currency experience(String id, String name, String symbol, String colour) {
        return new Currency(id, name, symbol, colour, Backing.EXPERIENCE, null, 0);
    }

    public static Currency byId(String id) {
        Currency c = BY_ID.get(id == null ? defaultId : id);
        return c != null ? c : BY_ID.get(defaultId);
    }

    public static Currency getDefault() {
        return BY_ID.get(defaultId);
    }

    public static void setDefault(String id) {
        if (BY_ID.containsKey(id)) {
            defaultId = id;
        }
    }

    public static java.util.Collection<Currency> all() {
        return BY_ID.values();
    }

    /** Drops every non-stock currency. Called when a ruleset is (re)loaded. */
    public static void clearCustom() {
        BY_ID.keySet().removeIf(k -> !k.equals("points"));
        defaultId = "points";
    }

    // ------------------------------------------------------------------
    // Balances
    // ------------------------------------------------------------------

    /** Virtual balances, keyed by player then currency. */
    private static final Map<UUID, Map<String, Integer>> WALLETS = new java.util.HashMap<>();

    public int balance(ServerPlayer player) {
        return switch (backing) {
            case VIRTUAL -> WALLETS
                    .getOrDefault(player.getUUID(), Map.of())
                    .getOrDefault(id, 0);
            case ITEM -> countItem(player);
            case EXPERIENCE -> player.totalExperience;
        };
    }

    /** Hands out money. Never goes negative. */
    public void award(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return;
        }
        switch (backing) {
            case VIRTUAL -> WALLETS
                    .computeIfAbsent(player.getUUID(), k -> new java.util.HashMap<>())
                    .merge(id, amount, Integer::sum);
            case ITEM -> {
                ItemStack stack = new ItemStack(item, amount);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
            case EXPERIENCE -> player.giveExperiencePoints(amount);
        }
    }

    /**
     * Takes money if there is enough, and reports whether it happened.
     *
     * <p>Deliberately all-or-nothing: a purchase never half-charges. The check and
     * the deduction are the same call so nothing can wedge between them and leave a
     * player paying for something they did not get.
     */
    public boolean charge(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (balance(player) < amount) {
            return false;
        }
        switch (backing) {
            case VIRTUAL -> WALLETS
                    .computeIfAbsent(player.getUUID(), k -> new java.util.HashMap<>())
                    .merge(id, -amount, Integer::sum);
            case ITEM -> takeItem(player, amount);
            case EXPERIENCE -> player.giveExperiencePoints(-amount);
        }
        return true;
    }

    /** Sets a virtual balance outright. Item and XP currencies ignore this. */
    public void set(ServerPlayer player, int amount) {
        if (backing == Backing.VIRTUAL) {
            WALLETS.computeIfAbsent(player.getUUID(), k -> new java.util.HashMap<>())
                    .put(id, Math.max(0, amount));
        }
    }

    public static void resetWallet(UUID id) {
        WALLETS.remove(id);
    }

    public static void resetAllWallets() {
        WALLETS.clear();
    }

    private int countItem(ServerPlayer player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Walks the inventory taking what it needs. Only ever called after a balance check. */
    private void takeItem(ServerPlayer player, int amount) {
        int left = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(item)) {
                continue;
            }
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            left -= take;
        }
    }

    /** "§e1750 ✦" - a price in this currency's own colour and mark. */
    public String format(int amount) {
        return colour + amount + " " + symbol;
    }
}
