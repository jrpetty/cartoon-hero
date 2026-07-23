package com.jrpetty.aztecabyss.round;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Randomised mid-run supply caches. A cache drops every few rounds and its
 * contents roll on a weighted table so the haul swings from near-empty duds to
 * rare jackpots, with everything in between - the loot is deliberately variable
 * so a drop is always a gamble worth running for.
 *
 * The staple theme is sustainability (arrows, food, the odd golden apple); the
 * rarer tiers layer real power on top. Quantities are themselves randomised, and
 * higher rounds nudge the padding up so caches stay relevant late.
 */
public final class SupplyCache {

    private static final Random RNG = new Random();

    /** The rolled result: what's inside, plus a flavour line hinting at the quality. */
    public record Result(ItemStack[] loot, String flavor) {
    }

    private SupplyCache() {
    }

    /**
     * Rolls a cache for the given round. Tier is weighted:
     * 12% dud, 38% modest, 30% good, 15% great, 5% jackpot.
     */
    public static Result roll(int round) {
        int r = RNG.nextInt(100);
        if (r < 12) {
            return new Result(dud(round), "§7The cache is nearly bare — slim pickings.");
        } else if (r < 50) {
            return new Result(modest(round), "§fBasic supplies. Enough to keep pushing.");
        } else if (r < 80) {
            return new Result(good(round), "§aA solid haul.");
        } else if (r < 95) {
            return new Result(great(round), "§b§lA rich cache!");
        } else {
            return new Result(jackpot(round), "§6§lJACKPOT — the cache overflows!");
        }
    }

    // A near-worthless drop: the "bad luck" end of the spread.
    private static ItemStack[] dud(int round) {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Items.ROTTEN_FLESH, 2 + RNG.nextInt(4)));
        if (RNG.nextBoolean()) {
            loot.add(new ItemStack(Items.ARROW, 3 + RNG.nextInt(5)));
        }
        if (RNG.nextInt(3) == 0) {
            loot.add(new ItemStack(Items.BREAD, 1 + RNG.nextInt(2)));
        } else if (RNG.nextInt(4) == 0) {
            loot.add(new ItemStack(Items.BONE, 1 + RNG.nextInt(2)));
        }
        return loot.toArray(new ItemStack[0]);
    }

    // The bread-and-butter: keeps a run going without being exciting.
    private static ItemStack[] modest(int round) {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Items.ARROW, 12 + RNG.nextInt(20) + round));
        loot.add(new ItemStack(Items.COOKED_BEEF, 4 + RNG.nextInt(5)));
        if (RNG.nextBoolean()) {
            loot.add(new ItemStack(Items.GOLDEN_APPLE, 1));
        }
        if (RNG.nextInt(3) == 0) {
            loot.add(new ItemStack(Items.IRON_INGOT, 1 + RNG.nextInt(3)));
        }
        loot.add(new ItemStack(Items.TORCH, 4 + RNG.nextInt(5)));
        return loot.toArray(new ItemStack[0]);
    }

    // A good pull: meaningful resources.
    private static ItemStack[] good(int round) {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Items.ARROW, 32));
        loot.add(new ItemStack(Items.COOKED_BEEF, 8));
        loot.add(new ItemStack(Items.GOLDEN_APPLE, 1 + RNG.nextInt(2)));
        loot.add(new ItemStack(Items.IRON_INGOT, 3 + RNG.nextInt(5)));
        loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 4 + RNG.nextInt(8)));
        if (RNG.nextInt(3) == 0) {
            loot.add(new ItemStack(Items.DIAMOND, 1 + RNG.nextInt(2)));
        }
        return loot.toArray(new ItemStack[0]);
    }

    // A great pull: rare kit, a genuine boost.
    private static ItemStack[] great(int round) {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Items.DIAMOND, 2 + RNG.nextInt(4)));
        loot.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1));
        loot.add(new ItemStack(Items.SPECTRAL_ARROW, 16));
        loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 8 + RNG.nextInt(10)));
        loot.add(new ItemStack(Items.GOLDEN_CARROT, 6 + RNG.nextInt(6)));
        loot.add(randomDiamondGear());
        return loot.toArray(new ItemStack[0]);
    }

    // The top of the spread: run-defining loot.
    private static ItemStack[] jackpot(int round) {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Items.NETHERITE_SCRAP, 1 + RNG.nextInt(2)));
        loot.add(new ItemStack(Items.TOTEM_OF_UNDYING, 1));
        loot.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1 + RNG.nextInt(2)));
        loot.add(new ItemStack(RNG.nextBoolean() ? Items.DIAMOND_SWORD : Items.DIAMOND_AXE, 1));
        loot.add(new ItemStack(Items.DIAMOND, 4 + RNG.nextInt(5)));
        loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 24 + RNG.nextInt(16)));
        return loot.toArray(new ItemStack[0]);
    }

    private static ItemStack randomDiamondGear() {
        return switch (RNG.nextInt(4)) {
            case 0 -> new ItemStack(Items.DIAMOND_HELMET);
            case 1 -> new ItemStack(Items.DIAMOND_CHESTPLATE);
            case 2 -> new ItemStack(Items.DIAMOND_LEGGINGS);
            default -> new ItemStack(Items.DIAMOND_BOOTS);
        };
    }
}
