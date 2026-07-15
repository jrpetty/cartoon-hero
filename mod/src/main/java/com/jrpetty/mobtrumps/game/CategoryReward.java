package com.jrpetty.mobtrumps.game;

/**
 * The one-time haul for completing a category. Materials scale with the
 * category's difficulty, plus a single randomly-enchanted armour piece whose
 * material rises with difficulty (iron → diamond → netherite). No emeralds —
 * just the good stuff. Amounts are the base values before the config
 * {@code categoryRewardMultiplier} is applied.
 */
public record CategoryReward(int iron, int gold, int diamond, ArmorTier armor) {

    public static CategoryReward of(Category category) {
        return switch (category.difficulty()) {
            case 1 -> new CategoryReward(8, 4, 1, ArmorTier.IRON);       // Farm, Creatures, Village
            case 2 -> new CategoryReward(6, 6, 3, ArmorTier.IRON);       // Aquatic, Undead, Monsters
            case 3 -> new CategoryReward(4, 8, 5, ArmorTier.DIAMOND);    // End, Nether, Illagers
            default -> new CategoryReward(2, 6, 8, ArmorTier.NETHERITE); // Bosses
        };
    }

    /** Material of the free enchanted armour piece a category awards. */
    public enum ArmorTier {
        IRON, DIAMOND, NETHERITE
    }
}
