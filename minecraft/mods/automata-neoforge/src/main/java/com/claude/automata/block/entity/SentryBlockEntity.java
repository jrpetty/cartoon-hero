package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Sentry — a powered turret that zaps the nearest hostile mob in range every
 * {@value #INTERVAL} ticks, while it has power. Defends a base hands-free; pair
 * with walls so mobs funnel into range.
 */
public class SentryBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
	private static final int RADIUS = 8;
	private static final int INTERVAL = 15;
	private static final float DAMAGE = 6.0f;
	private static final int ENERGY_PER_SHOT = 40;

	private int cooldown = 0;

	public SentryBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SENTRY.get(), pos, state);
	}

	public void tick(Level world, BlockPos pos, BlockState state) {
		if (++cooldown < INTERVAL) {
			return;
		}
		cooldown = 0;
		if (!(world instanceof ServerLevel serverWorld)) {
			return;
		}
		if (!MachinePower.draw(world, pos, ENERGY_PER_SHOT)) {
			return;
		}

		AABB area = new AABB(
				pos.getX() - RADIUS, pos.getY() - RADIUS, pos.getZ() - RADIUS,
				pos.getX() + RADIUS + 1, pos.getY() + RADIUS + 1, pos.getZ() + RADIUS + 1);
		List<Monster> mobs = world.getEntitiesOfClass(Monster.class, area, Monster::isAlive);
		if (mobs.isEmpty()) {
			return;
		}

		Vec3 center = Vec3.atCenterOf(pos);
		Monster target = null;
		double best = Double.MAX_VALUE;
		for (Monster mob : mobs) {
			double d = mob.distanceToSqr(center);
			if (d < best) {
				best = d;
				target = mob;
			}
		}
		if (target != null) {
			target.hurt(serverWorld.damageSources().magic(), DAMAGE);
			// PORT-NOTE: Yarn SoundEvents.BLOCK_BEACON_POWER_SELECT -> Mojmap SoundEvents.BEACON_POWER_SELECT
			serverWorld.playSound(null, pos, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT,
					net.minecraft.sounds.SoundSource.BLOCKS, 0.4f, 1.8f);
		}
	}
}
