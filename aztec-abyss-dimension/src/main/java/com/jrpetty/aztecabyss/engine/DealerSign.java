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

    /** What a dealer sign is offering. */
    public record Offer(ItemStack stack, int price, Currency currency) {
    }

    /** Reads one line of a sign as plain text, with formatting stripped. */
    private static String line(SignBlockEntity sign, int index) {
        Component c = sign.getFrontText().getMessage(index, false);
        return c.getString().trim();
    }

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

        int count = 1;
        String extra = line(sign, 3).toLowerCase(java.util.Locale.ROOT);
        if (extra.startsWith("x")) {
            try {
                count = Math.max(1, Math.min(64, Integer.parseInt(extra.substring(1).trim())));
            } catch (NumberFormatException ignored) {
                count = 1;
            }
        }
        return new Offer(new ItemStack(item, count), price, currency);
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
        return item == net.minecraft.world.item.Items.AIR ? null : item;
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

        ItemStack given = offer.stack().copy();
        if (!player.getInventory().add(given)) {
            player.drop(given, false);
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
