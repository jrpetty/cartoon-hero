package com.jrpetty.aztecabyss.maze;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;
import java.util.Optional;

/**
 * Griever Serum: what you cut out of one after you have killed it.
 *
 * <p>Made from a vanilla potion carrying custom effects rather than a registered
 * item. That is not a shortcut - it is the better build here. A new item would
 * need a model and a texture that do not exist in this mod, and a missing-texture
 * bottle is worse than a real one; meanwhile a potion already drinks, stacks,
 * renders and shows its own effects with no code at all.
 *
 * <p>What it does is deliberately not "heals you". Invisibility is the point:
 * Grievers drop a target they cannot see, so a serum is a way out of a corridor
 * you should not have been in - once. Night vision and speed come with it
 * because the thing you do next is run, in the dark, having just used up your
 * one escape.
 */
public final class MazeSerum {

    /** One in this many Grievers is worth cutting open. */
    private static final int DROP_CHANCE = 4;

    private MazeSerum() {
    }

    /** The serum itself. */
    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.POTION);
        // Three fields, not four: the custom-name component only arrives in
        // 1.21.2, and this builds against 1.21.1.
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(
                Optional.empty(),
                Optional.of(0x7FBF3F), // sickly Griever green
                List.of(
                        new MobEffectInstance(MobEffects.INVISIBILITY, 300, 0, false, true),
                        new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0, false, true),
                        new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 1, false, true))));
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("§2§lGriever Serum"));
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
                List.of(Component.literal("§7Fifteen seconds they cannot see you."),
                        Component.literal("§8Cut from something that nearly had you."))));
        return stack;
    }

    /** Rolls a serum from a dead Griever. Returns true if one dropped. */
    public static boolean maybeDrop(ServerLevel level, Mob griever, RandomSource rng) {
        if (rng.nextInt(DROP_CHANCE) != 0) {
            return false;
        }
        ItemEntity drop = new ItemEntity(level,
                griever.getX(), griever.getY() + 0.5, griever.getZ(), create());
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
        return true;
    }
}
