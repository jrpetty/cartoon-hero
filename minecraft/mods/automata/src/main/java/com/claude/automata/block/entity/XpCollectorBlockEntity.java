package com.claude.automata.block.entity;

import com.claude.automata.item.XpShardItem;
import com.claude.automata.registry.ModBlockEntities;
import com.claude.automata.registry.ModItems;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * The XP Collector — vacuums up nearby experience orbs and crystallises them
 * into XP Shards in its output. Small radius by hand, larger when powered.
 */
public class XpCollectorBlockEntity extends OutputMachineBlockEntity {
	private static final int SIZE = 9;
	private static final int SCAN_INTERVAL = 8;
	private static final int RADIUS_UNPOWERED = 3;
	private static final int RADIUS_POWERED = 6;
	private static final int ENERGY_PER_SCAN = 10;

	private int storedXp = 0;

	public XpCollectorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.XP_COLLECTOR, pos, state, SIZE);
	}

	@Override
	public void tick(World world, BlockPos pos, BlockState state) {
		progress++;
		if (progress < SCAN_INTERVAL) {
			return;
		}
		progress = 0;

		int radius = MachinePower.draw(world, pos, ENERGY_PER_SCAN) ? RADIUS_POWERED : RADIUS_UNPOWERED;
		Box area = new Box(
				pos.getX() - radius, pos.getY() - radius, pos.getZ() - radius,
				pos.getX() + radius + 1, pos.getY() + radius + 1, pos.getZ() + radius + 1);

		List<ExperienceOrbEntity> orbs = world.getEntitiesByClass(ExperienceOrbEntity.class, area,
				orb -> orb.isAlive());
		boolean changed = false;
		for (ExperienceOrbEntity orb : orbs) {
			storedXp += orb.getExperienceAmount();
			orb.discard();
			changed = true;
		}

		// Crystallise stored experience into shards while there is room.
		while (storedXp >= XpShardItem.XP_VALUE && hasEmptySlot()) {
			addOutput(new net.minecraft.item.ItemStack(ModItems.XP_SHARD));
			storedXp -= XpShardItem.XP_VALUE;
			changed = true;
		}

		if (changed) {
			markDirty();
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putInt("StoredXp", storedXp);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		storedXp = nbt.getInt("StoredXp");
	}
}
