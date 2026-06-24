package com.claude.automata.block.entity;

import com.claude.automata.block.ConveyorBlock;
import com.claude.automata.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

/**
 * Drives a Conveyor Belt: each tick it nudges every entity resting on the belt
 * (items, mobs and players alike) in the belt's facing direction, up to a
 * capped speed, so they ride along a line of belts.
 */
public class ConveyorBlockEntity extends net.minecraft.block.entity.BlockEntity {
	private static final double ACCEL = 0.06;
	private static final double MAX_SPEED = 0.22;

	public ConveyorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CONVEYOR, pos, state);
	}

	public void tick(World world, BlockPos pos, BlockState state) {
		Direction facing = state.get(ConveyorBlock.FACING);
		Vec3i dir = facing.getVector();

		Box area = new Box(pos.getX(), pos.getY() + 0.1, pos.getZ(),
				pos.getX() + 1, pos.getY() + 1.1, pos.getZ() + 1);
		List<Entity> entities = world.getOtherEntities(null, area);
		for (Entity entity : entities) {
			Vec3d v = entity.getVelocity();
			double nx = v.x + dir.getX() * ACCEL;
			double nz = v.z + dir.getZ() * ACCEL;
			nx = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, nx));
			nz = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, nz));
			entity.setVelocity(nx, v.y, nz);
			entity.velocityModified = true;
		}
	}
}
