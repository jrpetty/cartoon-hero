package com.claude.automata.item;

import com.claude.automata.registry.ModComponents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A mining tool that runs on stored energy instead of durability: it mines at
 * full speed while it has charge (draining {@value} per block), and falls back
 * to bare-hand speed when empty — but never breaks. Recharge it with a Portable
 * Charger. The item bar shows charge, not durability.
 */
public class PoweredToolItem extends MiningToolItem {
	private final int maxEnergy;
	private final int costPerBlock;

	public PoweredToolItem(ToolMaterial material, TagKey<Block> effectiveBlocks, float attackDamage,
			float attackSpeed, int maxEnergy, int costPerBlock, Settings settings) {
		super(material, effectiveBlocks,
				settings.attributeModifiers(createAttributeModifiers(material, attackDamage, attackSpeed)));
		this.maxEnergy = maxEnergy;
		this.costPerBlock = costPerBlock;
	}

	public int maxEnergy() {
		return maxEnergy;
	}

	private int energy(ItemStack stack) {
		return stack.getOrDefault(ModComponents.ENERGY, 0);
	}

	@Override
	public float getMiningSpeedMultiplier(ItemStack stack, BlockState state) {
		return energy(stack) >= costPerBlock ? super.getMiningSpeedMultiplier(stack, state) : 1.0f;
	}

	@Override
	public boolean postMine(ItemStack stack, World world, BlockState state, BlockPos pos, LivingEntity miner) {
		if (!world.isClient && state.getHardness(world, pos) != 0.0f) {
			int current = energy(stack);
			if (current > 0) {
				stack.set(ModComponents.ENERGY, Math.max(0, current - costPerBlock));
			}
		}
		return true;
	}

	@Override
	public boolean isItemBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getItemBarStep(ItemStack stack) {
		return Math.round(energy(stack) * 13.0f / maxEnergy);
	}

	@Override
	public int getItemBarColor(ItemStack stack) {
		return 0x33CCFF;
	}
}
