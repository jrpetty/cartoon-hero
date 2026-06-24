package com.claude.automata.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * An XP Shard — bottled experience produced by the XP Collector. Right-click to
 * cash it in for {@value #XP_VALUE} experience.
 */
public class XpShardItem extends Item {
	public static final int XP_VALUE = 7;

	public XpShardItem(Properties settings) {
		super(settings);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);
		if (!world.isClientSide) {
			user.giveExperiencePoints(XP_VALUE);
			stack.shrink(1);
		}
		return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
	}
}
