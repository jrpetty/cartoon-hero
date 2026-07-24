package com.jrpetty.aztecabyss.round;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Rewards scale with the round reached. Death cuts the run short but still
 * pays out for progress made; reaching (and clearing) Round 20 pays out a
 * deliberately generous, "worth bragging about" haul.
 *
 * Tuning is centralised here so it's easy to rebalance without touching the
 * round-loop logic in {@link RoundManager}.
 */
public final class RewardTable {

    private static final Random RNG = new Random();

    private RewardTable() {
    }

    public static ItemStack[] rewardsFor(int roundReached, boolean victory, boolean ritualComplete) {
        List<ItemStack> loot = new ArrayList<>(List.of(baseRewards(roundReached, victory)));
        if (ritualComplete) {
            // The Easter-egg ritual adds a single small, randomised token - a mystery,
            // not a guaranteed windfall.
            loot.add(RitualReward.runEndToken());
        }
        return loot.toArray(new ItemStack[0]);
    }

    private static ItemStack[] baseRewards(int roundReached, boolean victory) {
        List<ItemStack> loot = new ArrayList<>();

        if (victory) {
            return grandPrize();
        }

        if (roundReached <= 0) {
            loot.add(new ItemStack(Items.BREAD, 8));
            loot.add(new ItemStack(Items.TORCH, 16));
            return loot.toArray(new ItemStack[0]);
        } else if (roundReached < 5) {
            // Tier 1: basic - got your feet wet.
            loot.add(new ItemStack(Items.IRON_INGOT, 4 + RNG.nextInt(4)));
            loot.add(new ItemStack(Items.COAL, 8));
            loot.add(new ItemStack(Items.GOLDEN_APPLE, 1));
            loot.add(new ItemStack(Items.ARROW, 32));
            loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 8));
        } else if (roundReached < 10) {
            // Tier 2: good - you held a line.
            loot.add(new ItemStack(Items.DIAMOND, 2 + RNG.nextInt(3)));
            loot.add(new ItemStack(Items.GOLD_INGOT, 8));
            loot.add(new ItemStack(Items.GOLDEN_APPLE, 2));
            loot.add(new ItemStack(Items.GOLDEN_CARROT, 8));
            loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 16));
            loot.add(new ItemStack(Items.SPECTRAL_ARROW, 16));
        } else if (roundReached < 15) {
            // Tier 3: great - most players never make it this far.
            loot.add(new ItemStack(Items.DIAMOND, 6 + RNG.nextInt(4)));
            loot.add(new ItemStack(Items.NETHERITE_SCRAP, 2));
            loot.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1));
            loot.add(new ItemStack(Items.DIAMOND, 4));
            loot.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 32));
            loot.add(new ItemStack(Items.DIAMOND_CHESTPLATE, 1));
        } else {
            // Tier 4: epic - round 15-19, one step from legend.
            loot.add(new ItemStack(Items.NETHERITE_INGOT, 2));
            loot.add(new ItemStack(Items.DIAMOND, 10));
            loot.add(new ItemStack(Items.TOTEM_OF_UNDYING, 1));
            loot.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2));
            loot.add(new ItemStack(Items.ANCIENT_DEBRIS, 3));
            loot.add(new ItemStack(Items.NETHERITE_SCRAP, 2));
        }

        return loot.toArray(new ItemStack[0]);
    }

    /** Round 20, cleared. This is meant to feel excessive. */
    private static ItemStack[] grandPrize() {
        return new ItemStack[]{
                new ItemStack(Items.NETHERITE_INGOT, 8),
                new ItemStack(Items.NETHERITE_SWORD, 1),
                new ItemStack(Items.NETHERITE_CHESTPLATE, 1),
                new ItemStack(Items.NETHERITE_HELMET, 1),
                new ItemStack(Items.NETHERITE_LEGGINGS, 1),
                new ItemStack(Items.NETHERITE_BOOTS, 1),
                new ItemStack(Items.TOTEM_OF_UNDYING, 3),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 6),
                new ItemStack(Items.DIAMOND, 32),
                new ItemStack(Items.DIAMOND_BLOCK, 4),
                new ItemStack(Items.NETHER_STAR, 2),
                new ItemStack(Items.ELYTRA, 1),
                new ItemStack(Items.EXPERIENCE_BOTTLE, 64),
        };
    }
}
