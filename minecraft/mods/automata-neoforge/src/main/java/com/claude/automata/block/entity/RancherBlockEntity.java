package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The Rancher — tends penned animals around it. It breeds adults using the feed
 * in its input, and once the pen is crowded it culls an adult for drops (caught
 * by an adjacent Item Collector). Slow by hand, wider/faster with power.
 *
 * <p>Slot 0 is the feed input (wheat, seeds, carrots… whatever the animals eat).
 */
public class RancherBlockEntity extends MachineBlockEntity {
	private static final int[] INPUT = {0};
	private static final int INTERVAL = 20;
	private static final int RADIUS_UNPOWERED = 4;
	private static final int RADIUS_POWERED = 8;
	private static final int CROWD_LIMIT = 8;
	private static final int ENERGY_PER_OP = 10;

	public RancherBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.RANCHER.get(), pos, state, 1);
	}

	@Override
	protected int[] inputSlots() {
		return INPUT;
	}

	@Override
	protected int outputSlot() {
		return 0;
	}

	@Override
	protected boolean isValidInput(ItemStack stack) {
		return true;
	}

	@Override
	protected int maxProgress() {
		return INTERVAL;
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return INPUT;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return slot == 0;
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
		return false;
	}

	@Override
	public void tick(Level world, BlockPos pos, BlockState state) {
		if (++progress < INTERVAL) {
			return;
		}
		progress = 0;
		if (!(world instanceof ServerLevel serverWorld)) {
			return;
		}

		int radius = MachinePower.draw(world, pos, ENERGY_PER_OP) ? RADIUS_POWERED : RADIUS_UNPOWERED;
		AABB area = new AABB(
				pos.getX() - radius, pos.getY() - radius, pos.getZ() - radius,
				pos.getX() + radius + 1, pos.getY() + radius + 1, pos.getZ() + radius + 1);
		List<Animal> animals = world.getEntitiesOfClass(Animal.class, area, Animal::isAlive);

		int adults = 0;
		for (Animal a : animals) {
			if (!a.isBaby()) {
				adults++;
			}
		}

		// Cull an adult when the pen is crowded.
		if (adults >= CROWD_LIMIT) {
			for (Animal a : animals) {
				if (!a.isBaby()) {
					a.hurt(serverWorld.damageSources().generic(), 1000.0f);
					return;
				}
			}
		}

		// Otherwise try to breed a matching pair using the feed.
		ItemStack feed = inventory.get(0);
		if (feed.isEmpty()) {
			return;
		}
		Animal first = null;
		for (Animal a : animals) {
			if (isReady(a) && a.isFood(feed)) {
				first = a;
				break;
			}
		}
		if (first == null) {
			return;
		}
		for (Animal b : animals) {
			if (b != first && b.getType() == first.getType() && isReady(b)) {
				first.setInLove(null);
				b.setInLove(null);
				feed.shrink(2);
				setChanged();
				return;
			}
		}
	}

	private boolean isReady(Animal animal) {
		return !animal.isBaby() && animal.getAge() == 0 && !animal.isInLove();
	}
}
