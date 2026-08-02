package com.jrpetty.aztecabyss.engine;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/**
 * A shop, written on a sign.
 *
 * <p>This is the smallest complete piece of the engine, and deliberately the first
 * one built: it is a working feature on its own, and it is the pattern everything
 * else in the engine follows. A map author places a sign, writes what it sells and
 * what it costs, and that is the entire authoring step. No file, no reload, no
 * coordinates, no code.
 *
 * <pre>
 *   [Dealer]
 *   minecraft:crossbow
 *   1750 points
 *   x1
 * </pre>
 *
 * <p>Line 1 is the header. Line 2 is what you get - a full item id, or a bare name
 * like {@code crossbow}, which is assumed to be vanilla. Line 3 is the price, and
 * optionally which currency it is in; leave the currency off and the map's default
 * is used. Line 4 is optional, and currently understands {@code xN} for a stack
 * size.
 *
 * <p>The sign is left exactly as the author wrote it. It is the shop front - the
 * thing a player reads across a dark room to decide whether to walk over - so the
 * engine has no business rewriting it.
 */
public final class DealerSign {

    public static final String HEADER = "[dealer]";

    private DealerSign() {
    }

    /**
     * What a dealer sign is offering.
     *
     * <p>{@code fromRound} and {@code limit} are the two that change how a shop
     * behaves rather than what it sells. A weapon that does not appear until round
     * ten gives a map somewhere to get to; a weapon you may buy twice makes the
     * decision of <em>when</em> matter as much as whether.
     */
    public record Offer(ItemStack stack, int price, Currency currency,
                        int enchant, int fromRound, int limit) {

        /** The plain form, for callers that only care what and how much. */
        public Offer(ItemStack stack, int price, Currency currency) {
            this(stack, price, currency, 0, 0, 0);
        }
    }

    private static int clampInt(String raw, int lo, int hi, int fallback) {
        try {
            return Math.max(lo, Math.min(hi, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Reads one line of a sign as plain text, front face then back.
     *
     * <p>A sign has two faces and the shop was only ever using one of them, which
     * is where the four-line ceiling came from - the format was never the limit,
     * the front of the sign was. Lines 0-3 are the front, 4-7 the back, so a dealer
     * has eight lines without anything a player sees changing: the shop front still
     * reads item, price, quantity at a glance, and the extra room is round the back
     * where the author put the details.
     */
    private static String line(SignBlockEntity sign, int index) {
        Component c = index < 4
                ? sign.getFrontText().getMessage(index, false)
                : sign.getBackText().getMessage(index - 4, false);
        return c.getString().trim();
    }

    /** How many lines a dealer can use, across both faces. */
    public static final int LINES = 8;

    public static boolean isDealer(SignBlockEntity sign) {
        return line(sign, 0).toLowerCase(java.util.Locale.ROOT).equals(HEADER);
    }

    /**
     * Turns a sign into an offer, or null if it does not describe one.
     *
     * <p>Every failure here is silent and returns null rather than throwing. A
     * typo on a sign in the middle of a map should make that one sign inert, not
     * take down the run.
     */
    public static Offer parse(SignBlockEntity sign) {
        if (!isDealer(sign)) {
            return null;
        }
        Item item = itemFrom(line(sign, 1));
        if (item == null) {
            return null;
        }
        String[] priceParts = line(sign, 2).split("\\s+");
        int price;
        try {
            price = Integer.parseInt(priceParts[0].replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        if (price < 0) {
            return null;
        }
        Currency currency = priceParts.length > 1
                ? Currency.byId(priceParts[1].toLowerCase(java.util.Locale.ROOT))
                : Currency.getDefault();

        // Line 4 onward is free-form key=value, on either face, so a dealer can
        // carry options that never fitted before: a stack size, an enchantment
        // level, a round it unlocks at, a limit on how many may be sold.
        int count = 1;
        int enchant = 0;
        int fromRound = 0;
        int limit = 0;
        for (int i = 3; i < LINES; i++) {
            String raw = line(sign, i).toLowerCase(java.util.Locale.ROOT);
            if (raw.isEmpty()) {
                continue;
            }
            for (String token : raw.split("\\s+")) {
                if (token.startsWith("x") && token.length() > 1 && Character.isDigit(token.charAt(1))) {
                    count = clampInt(token.substring(1), 1, 64, count);
                    continue;
                }
                int eq = token.indexOf('=');
                if (eq <= 0 || eq >= token.length() - 1) {
                    continue;
                }
                String key = token.substring(0, eq);
                String val = token.substring(eq + 1);
                switch (key) {
                    case "count", "amount" -> count = clampInt(val, 1, 64, count);
                    case "enchant" -> enchant = clampInt(val, 0, 30, enchant);
                    case "round", "from_round" -> fromRound = clampInt(val, 0, 1000, fromRound);
                    case "limit" -> limit = clampInt(val, 0, 999, limit);
                    default -> { }
                }
            }
        }
        return new Offer(new ItemStack(item, count), price, currency, enchant, fromRound, limit);
    }

    /**
     * Repairs and reloads what a player already has, rather than duplicating it.
     *
     * <p>Ammunition is handled by refilling whatever the weapon eats - arrows for a
     * bow, rockets for a crossbow - so a map does not have to sell ammunition as a
     * separate line to make ranged weapons work.
     *
     * @return true if the purchase was spent on an item already carried
     */
    private static boolean service(ServerPlayer player, Offer offer) {
        Item wanted = offer.stack().getItem();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack held = player.getInventory().getItem(i);
            if (!held.is(wanted)) {
                continue;
            }
            boolean did = false;
            if (held.isDamaged()) {
                held.setDamageValue(0);
                did = true;
            }
            ItemStack ammo = ammoFor(wanted);
            if (!ammo.isEmpty()) {
                if (!player.getInventory().add(ammo)) {
                    player.drop(ammo, false);
                }
                did = true;
            }
            if (did) {
                return true;
            }
            // Owned, undamaged and needs no ammunition: let the sale go through as
            // an ordinary purchase rather than charging for nothing.
            return false;
        }
        return false;
    }

    /** What a weapon needs feeding, if anything. */
    private static ItemStack ammoFor(Item weapon) {
        if (weapon == Items.BOW) {
            return new ItemStack(Items.ARROW, 32);
        }
        if (weapon == Items.CROSSBOW) {
            return new ItemStack(Items.ARROW, 24);
        }
        if (weapon == Items.TRIDENT || weapon == Items.SNOWBALL) {
            return ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    /** Resolves an item id, tolerating a bare name with no namespace. */
    private static Item itemFrom(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(
                raw.toLowerCase(java.util.Locale.ROOT).replace(' ', '_'));
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? null : item;
    }

    /**
     * Attempts a purchase.
     *
     * @return true if the click was a dealer interaction at all - whether or not
     *         it succeeded - so the caller knows to swallow the click.
     */
    public static boolean buy(ServerLevel level, ServerPlayer player, SignBlockEntity sign) {
        Offer offer = parse(sign);
        if (offer == null) {
            return false;
        }
        String label = offer.stack().getCount() > 1
                ? offer.stack().getCount() + "x " + offer.stack().getHoverName().getString()
                : offer.stack().getHoverName().getString();

        // A locked line is refused before it is charged for. Saying "not yet" is
        // the whole point of from_round; taking the money and then saying it would
        // be worse than not having the feature.
        EngineArena arena = EngineArena.active();
        if (offer.fromRound() > 0 && arena != null && arena.round() < offer.fromRound()) {
            player.displayClientMessage(Component.literal(
                    "§7" + label + " §8— §7sealed until round §f" + offer.fromRound()), true);
            level.playSound(null, sign.getBlockPos(), SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.BLOCKS, 0.7F, 0.8F);
            return true;
        }
        if (offer.limit() > 0 && arena != null
                && !arena.canBuyFrom(sign.getBlockPos(), offer.limit())) {
            player.displayClientMessage(Component.literal(
                    "§7" + label + " §8— §7sold out. §8(" + offer.limit() + " a run)"), true);
            level.playSound(null, sign.getBlockPos(), SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.BLOCKS, 0.7F, 0.8F);
            return true;
        }

        if (!offer.currency().charge(player, offer.price())) {
            player.displayClientMessage(Component.literal(
                    "§cNot enough. §7" + label + " costs "
                            + offer.currency().format(offer.price())
                            + " §7— you have "
                            + offer.currency().format(offer.currency().balance(player))), true);
            level.playSound(null, sign.getBlockPos(), SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.BLOCKS, 0.7F, 1.0F);
            return true;
        }

        // Buying a weapon you already hold repairs and reloads it instead of
        // handing you a second one. Without this a wall buy is a one-off purchase
        // and the shop stops mattering the moment everyone owns everything - and
        // an inventory slowly fills with worn duplicates of the same sword.
        ItemStack given = offer.stack().copy();
        if (offer.enchant() > 0) {
            try {
                given = net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(
                        level.getRandom(), given, offer.enchant(),
                        level.registryAccess(), java.util.Optional.empty());
            } catch (RuntimeException ignored) {
                // Sold plain rather than not sold.
            }
        }
        if (offer.limit() > 0 && arena != null) {
            arena.recordBuy(sign.getBlockPos());
        }
        boolean serviced = service(player, offer);
        if (!serviced && !player.getInventory().add(given)) {
            player.drop(given, false);
        }
        if (serviced) {
            player.displayClientMessage(Component.literal(
                    "§a✔ Serviced §f" + label + " §7— " + offer.currency().format(offer.price())), true);
            level.playSound(null, sign.getBlockPos(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.BLOCKS, 0.6F, 1.1F);
            return true;
        }
        player.displayClientMessage(Component.literal(
                "§a✔ " + label + " §7— " + offer.currency().format(offer.price())
                        + " §8(" + offer.currency().format(offer.currency().balance(player))
                        + " §8left)"), true);
        level.playSound(null, sign.getBlockPos(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.BLOCKS, 0.7F, 1.4F);
        return true;
    }
}
