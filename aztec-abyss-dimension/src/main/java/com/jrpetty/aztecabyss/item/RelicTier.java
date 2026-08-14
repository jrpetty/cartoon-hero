package com.jrpetty.aztecabyss.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * A material for the relics.
 *
 * <p>Written out rather than borrowed from {@code Tiers}, because neither
 * obsidian nor Griever chitin behaves like anything on that list: one hits
 * like netherite but wears out in a fraction of the time, the other is quick
 * and light and mends with a thing you cut off a corpse. The interface is six
 * methods; a bespoke material is cheaper than pretending these are iron.
 */
public record RelicTier(int uses, float speed, float attackDamageBonus,
                        int enchantmentValue, Supplier<Ingredient> repair) implements Tier {

    @Override
    public int getUses() {
        return uses;
    }

    @Override
    public float getSpeed() {
        return speed;
    }

    @Override
    public float getAttackDamageBonus() {
        return attackDamageBonus;
    }

    /**
     * Neither relic is a pickaxe, so this only decides what they fail to mine
     * properly - which for a sword is everything. Diamond's list keeps them
     * from being a back-pocket mining tool.
     */
    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {
        return BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
    }

    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return repair.get();
    }
}
