package com.jrpetty.mobtrumps;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Holo Projector: a glowing pedestal that projects a rotating 3D hologram
 * of the linked mob above it, with the card shown on a plaque on its face.
 *
 * <p>It stands wherever you put it and turns to face you. No item is ever
 * inside it — it projects a card straight from its owner's collection, so
 * there is nothing to steal. The owner right-clicks to pick a card and
 * sneak-right-clicks to clear it; everyone else just admires it.
 */
public class HoloProjectorBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<HoloProjectorBlock> CODEC = simpleCodec(HoloProjectorBlock::new);

    public HoloProjectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
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
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.block(); // a solid pedestal
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof HoloProjectorBlockEntity be)) {
            return InteractionResult.PASS;
        }

        // owner sneak-click clears the projection (server side)
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                if (be.hasCard() && be.canEdit(sp.getUUID())) {
                    be.clearProjection();
                    sp.sendSystemMessage(Component.literal("Projector cleared.")
                            .withStyle(ChatFormatting.GRAY));
                } else if (be.hasCard()) {
                    sp.sendSystemMessage(Component.literal("Only " + be.getOwnerName()
                            + " can change this projector.").withStyle(ChatFormatting.RED));
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // plain click: the client opens the picker (yours/empty) or the card view
        if (level.isClientSide) {
            com.jrpetty.mobtrumps.client.ClientHooks.openProjectorInteract(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
