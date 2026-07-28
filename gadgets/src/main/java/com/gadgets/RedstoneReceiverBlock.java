package com.gadgets;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Outputs the redstone level received on its channel, but only out of its front
 * face — exactly like a repeater's output, so it drives a single line rather than
 * powering everything around it.
 */
public class RedstoneReceiverBlock extends Block implements BlockEntityProvider {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty POWER = IntProperty.of("power", 0, 15);

    public RedstoneReceiverBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(POWER, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWER);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // Mirror the repeater: the output side ends up facing where the player looks.
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        // Never swallow the Linker's click — it has to reach the item.
        return stack.getItem() instanceof RedstoneLinkerItem
                ? ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof RedstoneReceiverBlockEntity be) {
            String channel = be.getChannel();
            int power = state.get(POWER);
            player.sendMessage(channel.isEmpty()
                    ? Text.literal("Receiver ▸ no channel — copy one from a Transmitter with the Linker").formatted(Formatting.RED)
                    : Text.literal("Receiver ▸ " + channel + " · output " + power + "/15 out the "
                            + state.get(FACING).getName() + " face")
                            .formatted(power > 0 ? Formatting.GREEN : Formatting.GRAY), true);
        }
        return ActionResult.success(world.isClient());
    }

    @Override
    protected boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    protected int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        // Weak power only: drives the redstone dust/component directly in front of
        // the output face, one line — never powers through a block into an area.
        return state.get(FACING) == direction ? state.get(POWER) : 0;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneReceiverBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) {
            return null;
        }
        return validateTicker(type, Gadgets.REDSTONE_RECEIVER_BE);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T extends BlockEntity> BlockEntityTicker<T> validateTicker(
            BlockEntityType<T> given, BlockEntityType<RedstoneReceiverBlockEntity> expected) {
        return given == expected
                ? (BlockEntityTicker<T>) (BlockEntityTicker<RedstoneReceiverBlockEntity>) RedstoneReceiverBlockEntity::tick
                : null;
    }
}
