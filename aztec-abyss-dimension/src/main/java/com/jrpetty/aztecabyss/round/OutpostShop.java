package com.jrpetty.aztecabyss.round;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * Wall buys and the Crucible - everything you can spend points on inside the
 * Outpost.
 *
 * <p>Wall buys are fixed: a named weapon at a named price, bolted to a wall you
 * have to be standing at. That is the whole appeal - the map's geography becomes
 * an economy, and "the crossbow wall" is a place as much as a purchase.
 *
 * <p><b>The Crucible</b> is the upgrade bench. It takes what you are holding and
 * pushes it one tier up the ladder - wood to stone, stone to iron, iron to
 * diamond, diamond to netherite - and at each step burns a perk into it. Perks
 * are the point: the tier makes the number bigger, the perk changes how you
 * play. They exist nowhere else in the mod and cannot leave the Outpost, because
 * nothing can.
 */
public final class OutpostShop {

    /** Marker written into a stack's custom data to record its Outpost perk. */
    private static final String PERK_KEY = "aztecabyss_perk";

    // ------------------------------------------------------------------
    // Perks
    // ------------------------------------------------------------------

    /**
     * The Outpost's own enchantments. Deliberately not real enchantments: these
     * are behaviours the vanilla enchantment system has no way to express, and
     * writing them as code on a data tag means they can key off things only this
     * game mode knows about - boards, windows, points, the horde's specialists.
     */
    public enum Perk {
        BOARDWRIGHT("Boardwright", "§7Boards go back on with no pause between them."),
        SCAVENGER("Scavenger", "§7Half again as many points from every kill."),
        RAMPART("Rampart", "§7+25% damage while you are stood at a window."),
        SIPHON("Siphon", "§7Every kill gives a heart back."),
        BREAKERS_BANE("Breaker's Bane", "§7Double damage to Breakers and Sappers.");

        public final String title;
        public final String blurb;

        Perk(String title, String blurb) {
            this.title = title;
            this.blurb = blurb;
        }
    }

    /** The perk burned into a stack, or null. */
    public static Perk perkOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        String name = data.copyTag().getString(PERK_KEY);
        if (name.isEmpty()) {
            return null;
        }
        try {
            return Perk.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean hasPerk(ItemStack stack, Perk perk) {
        return perkOf(stack) == perk;
    }

    private static void writePerk(ItemStack stack, Perk perk) {
        CompoundTag tag = new CompoundTag();
        tag.putString(PERK_KEY, perk.name());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // ------------------------------------------------------------------
    // Wall buys
    // ------------------------------------------------------------------

    /** One thing bolted to one wall. */
    public record Buy(String label, Item item, int count, int price) {
    }

    /**
     * The catalogue. Prices climb roughly with how much a thing changes a round,
     * not with what it costs to craft - a shield is worth more here than an iron
     * sword, because standing still behind one is the only way to survive a
     * window you have already lost.
     */
    public static final Buy[] CATALOGUE = {
            new Buy("Stone Sword", Items.STONE_SWORD, 1, 750),
            new Buy("Iron Sword", Items.IRON_SWORD, 1, 1500),
            new Buy("Iron Axe", Items.IRON_AXE, 1, 1200),
            new Buy("Bow", Items.BOW, 1, 800),
            new Buy("Arrows", Items.ARROW, 32, 300),
            new Buy("Crossbow", Items.CROSSBOW, 1, 1500),
            new Buy("Shield", Items.SHIELD, 1, 1000),
            new Buy("Iron Chestplate", Items.IRON_CHESTPLATE, 1, 2000),
            new Buy("Rations", Items.COOKED_BEEF, 8, 400),
    };

    /** Sells one wall buy to a player. */
    public static boolean buy(ServerLevel level, ServerPlayer player, Buy buy) {
        if (!OutpostEconomy.spend(player, buy.price(), buy.label())) {
            return false;
        }
        ItemStack stack = new ItemStack(buy.item(), buy.count());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.displayClientMessage(Component.literal(
                "§a✔ " + buy.label() + " §7— §e-" + buy.price()
                        + "§7, §e" + OutpostEconomy.points(player.getUUID()) + "§7 left."), true);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.BLOCKS, 0.6F, 1.6F);
        return true;
    }

    // ------------------------------------------------------------------
    // The Crucible
    // ------------------------------------------------------------------

    /** What each rung of the ladder costs. */
    private static final int[] TIER_PRICE = {2500, 5000, 8000, 12000};

    /**
     * Pushes a weapon one tier up and burns a perk into it.
     *
     * <p>Returns the message to show. Refuses politely rather than silently on
     * anything it cannot upgrade, because a machine that eats your only sword
     * without saying why is worse than one that does nothing.
     */
    public static void useCrucible(ServerLevel level, ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        Item next = nextTier(held.getItem());
        if (next == null) {
            player.displayClientMessage(Component.literal(
                    held.isEmpty()
                            ? "§7The Crucible wants something in your hand."
                            : "§7The Crucible has nothing left to do with that."), true);
            return;
        }
        int tier = tierOf(held.getItem());
        int price = TIER_PRICE[Math.min(tier, TIER_PRICE.length - 1)];
        if (!OutpostEconomy.spend(player, price, "This upgrade")) {
            return;
        }

        Perk perk = perkOf(held);
        if (perk == null) {
            // First trip through burns in a perk, chosen from the weapon's nature
            // so a bow and a sword do not come out of the fire the same.
            perk = held.getItem() == Items.BOW || held.getItem() == Items.CROSSBOW
                    ? Perk.BREAKERS_BANE
                    : Perk.values()[Math.abs(player.getUUID().hashCode() + tier) % Perk.values().length];
        }

        ItemStack out = new ItemStack(next);
        writePerk(out, perk);
        out.set(DataComponents.CUSTOM_NAME, Component.literal("§d§l" + perk.title + " " + nameOf(next)));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(perk.blurb));
        lore.add(Component.literal("§8Forged in the Crucible. It stays here."));
        out.set(DataComponents.LORE, new ItemLore(lore));

        player.getMainHandItem().shrink(1);
        if (!player.getInventory().add(out)) {
            player.drop(out, false);
        }
        player.displayClientMessage(Component.literal(
                "§d✦ " + perk.title + " " + nameOf(next) + " §7— §e-" + price), false);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.BLOCKS, 1.2F, 0.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.BLOCKS, 1.0F, 0.8F);
    }

    /** How far up the ladder an item already is, 0 for wood. */
    private static int tierOf(Item item) {
        if (item == Items.WOODEN_SWORD || item == Items.WOODEN_AXE) {
            return 0;
        }
        if (item == Items.STONE_SWORD || item == Items.STONE_AXE) {
            return 1;
        }
        if (item == Items.IRON_SWORD || item == Items.IRON_AXE) {
            return 2;
        }
        return 3;
    }

    /** The next rung, or null if there is none. */
    private static Item nextTier(Item item) {
        if (item == Items.WOODEN_SWORD) {
            return Items.STONE_SWORD;
        }
        if (item == Items.STONE_SWORD) {
            return Items.IRON_SWORD;
        }
        if (item == Items.IRON_SWORD) {
            return Items.DIAMOND_SWORD;
        }
        if (item == Items.DIAMOND_SWORD) {
            return Items.NETHERITE_SWORD;
        }
        if (item == Items.WOODEN_AXE) {
            return Items.STONE_AXE;
        }
        if (item == Items.STONE_AXE) {
            return Items.IRON_AXE;
        }
        if (item == Items.IRON_AXE) {
            return Items.DIAMOND_AXE;
        }
        if (item == Items.DIAMOND_AXE) {
            return Items.NETHERITE_AXE;
        }
        // Bows and crossbows have no tiers, so the Crucible only ever perks them -
        // once. A second trip would be paying full price for nothing.
        if (item == Items.BOW) {
            return Items.BOW;
        }
        if (item == Items.CROSSBOW) {
            return Items.CROSSBOW;
        }
        return null;
    }

    private static String nameOf(Item item) {
        return item.getDescription().getString();
    }

    private OutpostShop() {
    }
}
