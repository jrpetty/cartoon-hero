package com.voxelia.mmo.client;

import com.voxelia.mmo.skill.Skill;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The vanilla item that stands for each skill in menus, lists and toasts. */
public final class SkillIcons {
    private SkillIcons() {}

    private static ItemStack[] stacks;
    private static ItemStack character;

    public static ItemStack of(Skill skill) {
        if (stacks == null) {
            stacks = new ItemStack[Skill.values().length];
            for (Skill s : Skill.values()) stacks[s.ordinal()] = new ItemStack(switch (s) {
                case MINING -> Items.DIAMOND_PICKAXE;
                case FORAGING -> Items.IRON_AXE;
                case COMBAT -> Items.DIAMOND_SWORD;
                case FARMING -> Items.WHEAT;
                case ACROBATICS -> Items.FEATHER;
                case FISHING -> Items.FISHING_ROD;
                case EXCAVATION -> Items.IRON_SHOVEL;
                case DEFENSE -> Items.SHIELD;
                case COOKING -> Items.COOKED_BEEF;
                case ALCHEMY -> Items.BREWING_STAND;
                case ARCHERY -> Items.BOW;
            });
        }
        return stacks[skill.ordinal()];
    }

    /** The Character card / overall-ranking icon. */
    public static ItemStack character() {
        if (character == null) character = new ItemStack(Items.NETHER_STAR);
        return character;
    }
}
