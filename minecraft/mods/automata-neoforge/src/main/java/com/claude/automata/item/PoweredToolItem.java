package com.claude.automata.item;

import com.claude.automata.registry.ModComponents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

/**
 * A mining tool that runs on stored energy instead of durability: it mines at
 * full speed while it has charge (draining {@value} per block), and falls back
 * to bare-hand speed when empty — but never breaks. Recharge it with a Portable
 * Charger. The item bar shows charge, not durability.
 */
public class PoweredToolItem extends DiggerItem {
	private final int maxEnergy;
	private final int costPerBlock;

	public PoweredToolItem(Tier material, TagKey<Block> effectiveBlocks, float attackDamage,
			float attackSpeed, int maxEnergy, int costPerBlock, Properties settings) {
		super(material, effectiveBlocks,
				settings.attributes(DiggerItem.createAttributes(material, attackDamage, attackSpeed)));
		this.maxEnergy = maxEnergy;
		this.costPerBlock = costPerBlock;
	}

	public int maxEnergy() {
		return maxEnergy;
	}

	private int energy(ItemStack stack) {
		return stack.getOrDefault(ModComponents.ENERGY.get(), 0);
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level world, BlockState state, BlockPos pos, LivingEntity miner) {
		if (!world.isClientSide && state.getDestroySpeed(world, pos) != 0.0f) {
			int current = energy(stack);
			if (current > 0) {
				stack.set(ModComponents.ENERGY.get(), Math.max(0, current - costPerBlock));
			}
		}
		return true;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(energy(stack) * 13.0f / maxEnergy);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0x33CCFF;
	}
}
