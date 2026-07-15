package com.gadgets;

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
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Rings when a chosen target enters range. Tune with a spawn egg (specific mob)
 * or an empty hand (cycle players → monsters → animals → all).
 */
public class AlarmBlock extends Block implements BlockEntityProvider {
    public AlarmBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new AlarmBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) {
            return null;
        }
        return validateTicker(type, Gadgets.ALARM_BE);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof SpawnEggItem) {
            if (!world.isClient() && world.getBlockEntity(pos) instanceof AlarmBlockEntity be) {
                String eggId = Registries.ITEM.getId(stack.getItem()).toString();
                String mobId = eggId.endsWith("_spawn_egg")
                        ? eggId.substring(0, eggId.length() - "_spawn_egg".length())
                        : eggId;
                be.setTarget(mobId);
                player.sendMessage(Text.literal("Alarm now watches for: " + mobId), true);
            }
            return ItemActionResult.SUCCESS;
        }
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof AlarmBlockEntity be) {
            String mode = be.cycleMode();
            player.sendMessage(Text.literal("Alarm now watches for: " + mode), true);
        }
        return ActionResult.success(world.isClient());
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T extends BlockEntity> BlockEntityTicker<T> validateTicker(
            BlockEntityType<T> given, BlockEntityType<AlarmBlockEntity> expected) {
        return given == expected
                ? (BlockEntityTicker<T>) (BlockEntityTicker<AlarmBlockEntity>) AlarmBlockEntity::tick
                : null;
    }
}
