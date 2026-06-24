package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

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
		super(ModBlockEntities.RANCHER, pos, state, 1);
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
	public int[] getAvailableSlots(Direction side) {
		return INPUT;
	}

	@Override
	public boolean canInsert(int slot, ItemStack stack, Direction dir) {
		return slot == 0;
	}

	@Override
	public boolean canExtract(int slot, ItemStack stack, Direction dir) {
		return false;
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		if (++progress < INTERVAL) {
			return;
		}
		progress = 0;
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}

		int radius = MachinePower.draw(world, pos, ENERGY_PER_OP) ? RADIUS_POWERED : RADIUS_UNPOWERED;
		Box area = new Box(
				pos.getX() - radius, pos.getY() - radius, pos.getZ() - radius,
				pos.getX() + radius + 1, pos.getY() + radius + 1, pos.getZ() + radius + 1);
		List<AnimalEntity> animals = world.getEntitiesByClass(AnimalEntity.class, area, AnimalEntity::isAlive);

		int adults = 0;
		for (AnimalEntity a : animals) {
			if (!a.isBaby()) {
				adults++;
			}
		}

		// Cull an adult when the pen is crowded.
		if (adults >= CROWD_LIMIT) {
			for (AnimalEntity a : animals) {
				if (!a.isBaby()) {
					a.damage(serverWorld.getDamageSources().generic(), 1000.0f);
					return;
				}
			}
		}

		// Otherwise try to breed a matching pair using the feed.
		ItemStack feed = inventory.get(0);
		if (feed.isEmpty()) {
			return;
		}
		AnimalEntity first = null;
		for (AnimalEntity a : animals) {
			if (isReady(a) && a.isBreedingItem(feed)) {
				first = a;
				break;
			}
		}
		if (first == null) {
			return;
		}
		for (AnimalEntity b : animals) {
			if (b != first && b.getType() == first.getType() && isReady(b)) {
				first.lovePlayer(null);
				b.lovePlayer(null);
				feed.decrement(2);
				markDirty();
				return;
			}
		}
	}

	private boolean isReady(AnimalEntity animal) {
		return !animal.isBaby() && animal.getBreedingAge() == 0 && !animal.isInLove();
	}
}
