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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A sleek wall panel that PROJECTS a card from its owner's collection. No item
 * is ever inside it, so the card can't be stolen — the owner links a card via
 * a picker (right-click), swaps it the same way, and sneak-right-clicks to
 * clear. Everyone else right-clicks to admire the card full-screen.
 *
 * <p>It hangs like an item frame rather than sitting like a block: it mounts on
 * the face you click, its panel is flush against that supporting wall, it has
 * no collision so you can walk right up to it, and it pops off if the wall it
 * hangs on is broken. {@code FACING} is the direction the panel LOOKS — the
 * wall it clings to is always one block behind it.
 */
public class CardDisplayBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<CardDisplayBlock> CODEC = simpleCodec(CardDisplayBlock::new);

    /** How deep the panel is, in sixteenths — it hugs the wall it hangs on. */
    private static final double DEPTH = 1.5;

    // the panel sits against the supporting wall, i.e. on the side OPPOSITE
    // the direction it faces (facing north => wall to the south => z 14.5..16)
    private static final VoxelShape NORTH = Block.box(1, 1, 16 - DEPTH, 15, 15, 16);
    private static final VoxelShape SOUTH = Block.box(1, 1, 0, 15, 15, DEPTH);
    private static final VoxelShape WEST = Block.box(16 - DEPTH, 1, 1, 16, 15, 15);
    private static final VoxelShape EAST = Block.box(0, 1, 1, DEPTH, 15, 15);

    public CardDisplayBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CardDisplayBlockEntity(pos, state);
    }

    /**
     * Hang it on the face that was clicked, like an item frame. If the click
     * landed on a floor or ceiling, fall back to whichever horizontal direction
     * the player is looking that actually has a wall to hang on.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction clicked = ctx.getClickedFace();
        if (clicked.getAxis().isHorizontal()
                && canAttach(ctx.getLevel(), ctx.getClickedPos(), clicked)) {
            return defaultBlockState().setValue(FACING, clicked);
        }
        for (Direction look : ctx.getNearestLookingDirections()) {
            if (!look.getAxis().isHorizontal()) {
                continue;
            }
            Direction facing = look.getOpposite();
            if (canAttach(ctx.getLevel(), ctx.getClickedPos(), facing)) {
                return defaultBlockState().setValue(FACING, facing);
            }
        }
        return null; // nothing to hang it on
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canAttach(level, pos, state.getValue(FACING));
    }

    /** Pop off when the wall it hangs on goes away. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    /** Is there a solid face behind a display at {@code pos} looking {@code facing}? */
    private static boolean canAttach(LevelReader level, BlockPos pos, Direction facing) {
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CardDisplayBlockEntity be)) {
            return InteractionResult.PASS;
        }

        // owner sneak-click clears the projection (server side)
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                if (be.hasCard() && be.canEdit(sp.getUUID())) {
                    be.clearProjection();
                    sp.sendSystemMessage(Component.literal("Display cleared.")
                            .withStyle(ChatFormatting.GRAY));
                } else if (be.hasCard()) {
                    sp.sendSystemMessage(Component.literal("Only " + be.getOwnerName()
                            + " can change this display.").withStyle(ChatFormatting.RED));
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // plain click: the client opens the picker (yours/empty) or the card view
        if (level.isClientSide) {
            com.jrpetty.mobtrumps.client.ClientHooks.openDisplayInteract(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
