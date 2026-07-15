package com.gadgets;

import net.minecraft.util.Formatting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Emits a full redstone signal while a matching entity is within range. Tune it
 * by right-clicking with a spawn egg (locks onto that mob type) or with an empty
 * hand (cycles players → monsters → animals → all).
 */
public class PlayerSensorBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty POWERED = Properties.POWERED;

    public PlayerSensorBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(POWERED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof SpawnEggItem) {
            if (!world.isClient() && world.getBlockEntity(pos) instanceof PlayerSensorBlockEntity be) {
                String eggId = Registries.ITEM.getId(stack.getItem()).toString();
                String mobId = eggId.endsWith("_spawn_egg")
                        ? eggId.substring(0, eggId.length() - "_spawn_egg".length())
                        : eggId;
                be.setTarget(mobId);
                player.sendMessage(Text.literal("Sensor now detects: " + mobId).formatted(Formatting.GREEN), true);
            }
            return ItemActionResult.SUCCESS;
        }
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof PlayerSensorBlockEntity be) {
            String mode = be.cycleMode();
            player.sendMessage(Text.literal("Sensor now detects: " + mode).formatted(Formatting.GOLD), true);
        }
        return ActionResult.success(world.isClient());
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PlayerSensorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) {
            return null;
        }
        return validateTicker(type, Gadgets.PLAYER_SENSOR_BE);
    }

    @Override
    protected boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    protected int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return state.get(POWERED) ? 15 : 0;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T extends BlockEntity> BlockEntityTicker<T> validateTicker(
            BlockEntityType<T> given, BlockEntityType<PlayerSensorBlockEntity> expected) {
        return given == expected ? (BlockEntityTicker<T>) (BlockEntityTicker<PlayerSensorBlockEntity>) PlayerSensorBlockEntity::tick : null;
    }
}
