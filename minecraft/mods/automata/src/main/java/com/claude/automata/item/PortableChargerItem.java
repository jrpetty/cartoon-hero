package com.claude.automata.item;

import com.claude.automata.block.entity.PowerSource;
import com.claude.automata.registry.ModComponents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;

/**
 * A battery you carry: right-click a power source block (Dynamo, Capacitor, …)
 * to pull charge into it, and it automatically tops up any powered tools in your
 * inventory each tick. The item bar shows its charge.
 */
public class PortableChargerItem extends Item {
	private static final int PULL_PER_USE = 2000;
	private static final int FEED_PER_TICK = 200;
	private final int maxEnergy;

	public PortableChargerItem(int maxEnergy, Settings settings) {
		super(settings);
		this.maxEnergy = maxEnergy;
	}

	private int energy(ItemStack stack) {
		return stack.getOrDefault(ModComponents.ENERGY, 0);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (!world.isClient) {
			BlockEntity be = world.getBlockEntity(context.getBlockPos());
			ItemStack stack = context.getStack();
			if (be instanceof PowerSource source) {
				int room = maxEnergy - energy(stack);
				int pull = Math.min(room, PULL_PER_USE);
				if (pull > 0 && source.extractEnergy(pull)) {
					stack.set(ModComponents.ENERGY, energy(stack) + pull);
				}
				return ActionResult.SUCCESS;
			}
		}
		return ActionResult.PASS;
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (world.isClient || !(entity instanceof PlayerEntity player)) {
			return;
		}
		int available = energy(stack);
		if (available <= 0) {
			return;
		}
		for (ItemStack other : player.getInventory().main) {
			if (available <= 0) {
				break;
			}
			if (other.getItem() instanceof PoweredToolItem tool) {
				int current = other.getOrDefault(ModComponents.ENERGY, 0);
				int room = tool.maxEnergy() - current;
				if (room > 0) {
					int give = Math.min(room, Math.min(available, FEED_PER_TICK));
					other.set(ModComponents.ENERGY, current + give);
					available -= give;
				}
			}
		}
		stack.set(ModComponents.ENERGY, available);
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
