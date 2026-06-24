package com.claude.automata.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * An XP Shard — bottled experience produced by the XP Collector. Right-click to
 * cash it in for {@value #XP_VALUE} experience.
 */
public class XpShardItem extends Item {
	public static final int XP_VALUE = 7;

	public XpShardItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient) {
			user.addExperience(XP_VALUE);
			stack.decrement(1);
		}
		return TypedActionResult.success(stack, world.isClient);
	}
}
