package com.claude.automata.item;

import com.claude.automata.block.entity.PowerSource;
import com.claude.automata.registry.ModComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A battery you carry: right-click a power source block (Dynamo, Capacitor, …)
 * to pull charge into it, and it automatically tops up any powered tools in your
 * inventory each tick. The item bar shows its charge.
 */
public class PortableChargerItem extends Item {
	private static final int PULL_PER_USE = 2000;
	private static final int FEED_PER_TICK = 200;
	private final int maxEnergy;

	public PortableChargerItem(int maxEnergy, Properties settings) {
		super(settings);
		this.maxEnergy = maxEnergy;
	}

	private int energy(ItemStack stack) {
		return stack.getOrDefault(ModComponents.ENERGY.get(), 0);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level world = context.getLevel();
		if (!world.isClientSide) {
			BlockEntity be = world.getBlockEntity(context.getClickedPos());
			ItemStack stack = context.getItemInHand();
			if (be instanceof PowerSource source) {
				int room = maxEnergy - energy(stack);
				int pull = Math.min(room, PULL_PER_USE);
				if (pull > 0 && source.extractEnergy(pull)) {
					stack.set(ModComponents.ENERGY.get(), energy(stack) + pull);
				}
				return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.PASS;
	}

	@Override
	public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
		if (world.isClientSide || !(entity instanceof Player player)) {
			return;
		}
		int available = energy(stack);
		if (available <= 0) {
			return;
		}
		for (ItemStack other : player.getInventory().items) {
			if (available <= 0) {
				break;
			}
			if (other.getItem() instanceof PoweredToolItem tool) {
				int current = other.getOrDefault(ModComponents.ENERGY.get(), 0);
				int room = tool.maxEnergy() - current;
				if (room > 0) {
					int give = Math.min(room, Math.min(available, FEED_PER_TICK));
					other.set(ModComponents.ENERGY.get(), current + give);
					available -= give;
				}
			}
		}
		stack.set(ModComponents.ENERGY.get(), available);
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
