package com.claude.automata.block.entity;

import com.claude.automata.item.XpShardItem;
import com.claude.automata.registry.ModBlockEntities;
import com.claude.automata.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

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
		super(ModBlockEntities.XP_COLLECTOR.get(), pos, state, SIZE);
	}

	@Override
	public void tick(Level world, BlockPos pos, BlockState state) {
		progress++;
		if (progress < SCAN_INTERVAL) {
			return;
		}
		progress = 0;

		int radius = MachinePower.draw(world, pos, ENERGY_PER_SCAN) ? RADIUS_POWERED : RADIUS_UNPOWERED;
		AABB area = new AABB(
				pos.getX() - radius, pos.getY() - radius, pos.getZ() - radius,
				pos.getX() + radius + 1, pos.getY() + radius + 1, pos.getZ() + radius + 1);

		List<ExperienceOrb> orbs = world.getEntitiesOfClass(ExperienceOrb.class, area,
				orb -> orb.isAlive());
		boolean changed = false;
		for (ExperienceOrb orb : orbs) {
			storedXp += orb.getValue();
			orb.discard();
			changed = true;
		}

		// Crystallise stored experience into shards while there is room.
		while (storedXp >= XpShardItem.XP_VALUE && hasEmptySlot()) {
			addOutput(new net.minecraft.world.item.ItemStack(ModItems.XP_SHARD));
			storedXp -= XpShardItem.XP_VALUE;
			changed = true;
		}

		if (changed) {
			setChanged();
		}
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putInt("StoredXp", storedXp);
	}

	@Override
	public void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		storedXp = nbt.getInt("StoredXp");
	}
}
