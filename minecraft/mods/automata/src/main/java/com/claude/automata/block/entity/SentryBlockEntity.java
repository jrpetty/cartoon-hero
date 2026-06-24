package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Sentry — a powered turret that zaps the nearest hostile mob in range every
 * {@value #INTERVAL} ticks, while it has power. Defends a base hands-free; pair
 * with walls so mobs funnel into range.
 */
public class SentryBlockEntity extends net.minecraft.block.entity.BlockEntity {
	private static final int RADIUS = 8;
	private static final int INTERVAL = 15;
	private static final float DAMAGE = 6.0f;
	private static final int ENERGY_PER_SHOT = 40;

	private int cooldown = 0;

	public SentryBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SENTRY, pos, state);
	}

	public void tick(World world, BlockPos pos, BlockState state) {
		if (++cooldown < INTERVAL) {
			return;
		}
		cooldown = 0;
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}
		if (!MachinePower.draw(world, pos, ENERGY_PER_SHOT)) {
			return;
		}

		Box area = new Box(
				pos.getX() - RADIUS, pos.getY() - RADIUS, pos.getZ() - RADIUS,
				pos.getX() + RADIUS + 1, pos.getY() + RADIUS + 1, pos.getZ() + RADIUS + 1);
		List<HostileEntity> mobs = world.getEntitiesByClass(HostileEntity.class, area, HostileEntity::isAlive);
		if (mobs.isEmpty()) {
			return;
		}

		Vec3d center = Vec3d.ofCenter(pos);
		HostileEntity target = null;
		double best = Double.MAX_VALUE;
		for (HostileEntity mob : mobs) {
			double d = mob.squaredDistanceTo(center);
			if (d < best) {
				best = d;
				target = mob;
			}
		}
		if (target != null) {
			target.damage(serverWorld.getDamageSources().magic(), DAMAGE);
			serverWorld.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_BEACON_POWER_SELECT,
					net.minecraft.sound.SoundCategory.BLOCKS, 0.4f, 1.8f);
		}
	}
}
