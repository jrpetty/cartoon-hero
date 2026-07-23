package com.jrpetty.aztecabyss.block;

import com.jrpetty.aztecabyss.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The lit portal surface for the Abyss dimension. Visually distinct from a
 * nether portal: near-black base colour, a swirling {@code black_portal_swirl}
 * particle instead of the purple nether portal particle, and a low, cold hum
 * instead of the nether portal's ambient noise.
 *
 * Actual teleportation is handled by a tick-based watcher in
 * {@code com.jrpetty.aztecabyss.event.PortalEvents} rather than vanilla's
 * built-in nether/end portal-forcer plumbing, so this block stays fully
 * independent of vanilla portal internals.
 */
public class AbyssPortalBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = EnumProperty.create("axis", Direction.Axis.class,
            Direction.Axis.X, Direction.Axis.Z);

    private static final VoxelShape X_SHAPE = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    private static final VoxelShape Z_SHAPE = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    public AbyssPortalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(80) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS,
                    0.4F + random.nextFloat() * 0.2F, random.nextFloat() * 0.3F + 0.5F, false);
        }

        // Dense swirling core.
        for (int i = 0; i < 5; i++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            double vx = (random.nextDouble() - 0.5) * 0.4;
            double vy = (random.nextDouble() - 0.5) * 0.4;
            double vz = (random.nextDouble() - 0.5) * 0.4;
            level.addParticle(ModParticles.BLACK_PORTAL_SWIRL.get(), x, y, z, vx, vy, vz);
        }
        // Cold soul-fire energy bleeding off the surface - reads as a live tear.
        for (int i = 0; i < 3; i++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            double spread = 0.6;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z,
                    (random.nextDouble() - 0.5) * spread, random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * spread);
        }
        // Occasional ash motes drifting up from the frame and a wisp of smoke.
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.WHITE_ASH,
                    pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
                    0.0, 0.03, 0.0);
        }
        if (random.nextInt(20) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 0.02, 0);
        }
    }

    @Override
    protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) {
        return false;
    }
}
