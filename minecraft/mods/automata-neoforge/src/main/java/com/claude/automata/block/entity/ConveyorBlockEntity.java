package com.claude.automata.block.entity;

import com.claude.automata.block.ConveyorBlock;
import com.claude.automata.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Drives a Conveyor Belt: each tick it nudges every entity resting on the belt
 * (items, mobs and players alike) in the belt's facing direction, up to a
 * capped speed, so they ride along a line of belts.
 */
public class ConveyorBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
	private static final double ACCEL = 0.06;
	private static final double MAX_SPEED = 0.22;

	public ConveyorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CONVEYOR.get(), pos, state);
	}

	public void tick(Level world, BlockPos pos, BlockState state) {
		Direction facing = state.getValue(ConveyorBlock.FACING);
		Vec3i dir = facing.getNormal();

		AABB area = new AABB(pos.getX(), pos.getY() + 0.1, pos.getZ(),
				pos.getX() + 1, pos.getY() + 1.1, pos.getZ() + 1);
		List<Entity> entities = world.getEntities(null, area);
		for (Entity entity : entities) {
			Vec3 v = entity.getDeltaMovement();
			double nx = v.x + dir.getX() * ACCEL;
			double nz = v.z + dir.getZ() * ACCEL;
			nx = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, nx));
			nz = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, nz));
			entity.setDeltaMovement(nx, v.y, nz);
			entity.hasImpulse = true;
		}
	}
}
