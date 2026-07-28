package com.grapplinghook;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Enhancement Bench: ring a Grappling Hook with a material to fuse an
 * upgrade into it, or patch it back to full with iron and string.
 */
public class EnhancementTableBlock extends Block {
    private static final Text TITLE = Text.translatable("container.grapplinghook.enhancement_table");
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 15, 16);

    public EnhancementTableBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                         net.minecraft.block.ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient()) {
            player.openHandledScreen(createScreenHandlerFactory(state, world, pos));
        }
        return ActionResult.success(world.isClient());
    }

    @Nullable
    @Override
    protected NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
        return new SimpleNamedScreenHandlerFactory((syncId, inventory, player) ->
                new EnhancementScreenHandler(syncId, inventory, ScreenHandlerContext.create(world, pos)), TITLE);
    }
}
