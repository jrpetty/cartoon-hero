package com.jrpetty.mobtrumps;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Holo Projector: a glowing pedestal that projects a rotating 3D hologram
 * of the linked mob above it, with the card shown on a plaque on its face.
 * It reuses {@link CardDisplayBlock}'s picker/anti-theft interaction wholesale.
 */
public class HoloProjectorBlock extends CardDisplayBlock {

    public static final MapCodec<HoloProjectorBlock> CODEC = simpleCodec(HoloProjectorBlock::new);

    public HoloProjectorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HoloProjectorBlockEntity(pos, state);
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext ctx) {
        return net.minecraft.world.phys.shapes.Shapes.block(); // a solid pedestal
    }

    /**
     * A pedestal, not a picture: it stands wherever you put it — floor, table,
     * anywhere — and simply turns to face you. The parent's item-frame mounting
     * rules are deliberately NOT inherited.
     */
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.core.Direction direction,
                                     BlockState neighbour, net.minecraft.world.level.LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return state; // nothing to fall off
    }
}
