package com.jrpetty.aztecabyss.round;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Easter-egg ritual reward, deliberately kept a mystery: modest and
 * randomised rather than a fixed grand hoard. Completing the ritual is worth it
 * for the surprise, not for a guaranteed pile of loot - you never quite know
 * what the vault will yield, and it might be humble or (rarely) something great.
 */
public final class RitualReward {

    private static final Random RNG = new Random();

    private RitualReward() {
    }

    /** The vault chest contents: a small base plus one rolled "mystery prize". */
    public static ItemStack[] rollVault() {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 4 + RNG.nextInt(6)));
        loot.add(mysteryPrize());
        if (RNG.nextInt(3) == 0) {
            loot.add(minorTrinket());
        }
        return loot.toArray(new ItemStack[0]);
    }

    /** A single small, randomised token added to the run-end chest for completing the ritual. */
    public static ItemStack runEndToken() {
        return mysteryPrize();
    }

    /** Weighted roll from humble to (rarely) great - the heart of the "you don't know" surprise. */
    private static ItemStack mysteryPrize() {
        int r = RNG.nextInt(100);
        if (r < 40) {
            return new ItemStack(Items.DIAMOND, 2 + RNG.nextInt(3));       // 40% modest
        } else if (r < 65) {
            return new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1);         // 25%
        } else if (r < 82) {
            return new ItemStack(Items.NETHERITE_INGOT, 1);                // 17%
        } else if (r < 94) {
            return new ItemStack(Items.TOTEM_OF_UNDYING, 1);               // 12%
        } else if (r < 99) {
            return new ItemStack(Items.DIAMOND_BLOCK, 1);                  // 5%
        } else {
            return new ItemStack(Items.ELYTRA, 1);                         // 1% jackpot
        }
    }

    private static ItemStack minorTrinket() {
        return switch (RNG.nextInt(4)) {
            case 0 -> new ItemStack(Items.EMERALD, 4 + RNG.nextInt(6));
            case 1 -> new ItemStack(Items.GOLD_INGOT, 4 + RNG.nextInt(5));
            case 2 -> new ItemStack(Items.GOLDEN_APPLE, 1 + RNG.nextInt(2));
            default -> new ItemStack(Items.NETHERITE_SCRAP, 1);
        };
    }
}
