package com.jrpetty.aztecabyss.round;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Easter-egg ritual reward: a good-sized but grounded, randomised haul.
 * Deliberately avoids run-warping items (totems, elytra, nether stars) - it's a
 * solid pile of useful materials that varies run to run, so completing the
 * ritual pays off and stays a surprise without handing out anything crazy.
 */
public final class RitualReward {

    private static final Random RNG = new Random();

    private RitualReward() {
    }

    /** The vault chest contents: a dependable base plus one or two randomised bonuses. */
    public static ItemStack[] rollVault() {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Items.DIAMOND, 4 + RNG.nextInt(5)));           // 4-8
        loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 8 + RNG.nextInt(9))); // 8-16
        loot.add(new ItemStack(Items.EMERALD, 4 + RNG.nextInt(6)));           // 4-9
        loot.add(bonus());
        if (RNG.nextBoolean()) {
            loot.add(bonus());
        }
        return loot.toArray(new ItemStack[0]);
    }

    /** A single randomised token added to the run-end chest for completing the ritual. */
    public static ItemStack runEndToken() {
        return bonus();
    }

    /** Reasonable bonus pool - useful materials, nothing run-warping. */
    private static ItemStack bonus() {
        return switch (RNG.nextInt(6)) {
            case 0 -> new ItemStack(Items.NETHERITE_INGOT, 1);
            case 1 -> new ItemStack(Items.GOLD_INGOT, 6 + RNG.nextInt(6));
            case 2 -> new ItemStack(Items.IRON_INGOT, 8 + RNG.nextInt(8));
            case 3 -> new ItemStack(Items.GOLDEN_APPLE, 2 + RNG.nextInt(2));
            case 4 -> new ItemStack(Items.DIAMOND, 3 + RNG.nextInt(4));
            default -> new ItemStack(Items.NETHERITE_SCRAP, 1 + RNG.nextInt(2));
        };
    }
}
