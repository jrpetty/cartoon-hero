package com.gadgets;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click while aiming to "fire" a torch onto the surface you hit — a
 * standing torch on a floor, a wall torch on a wall. Light a cave from afar.
 */
public class LightArrowItem extends Item {
    private static final double RANGE = 32.0;

    public LightArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        Vec3 start = player.getEyePosition(1.0F);
        Vec3 end = start.add(player.getViewVector(1.0F).scale(RANGE));
        BlockHitResult hit = level.clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }
        Direction side = hit.getDirection();
        if (side == Direction.DOWN) {
            return InteractionResultHolder.pass(stack); // torches can't hang from a ceiling
        }

        if (!level.isClientSide()) {
            BlockPos place = hit.getBlockPos().relative(side);
            BlockState torch = side == Direction.UP
                    ? Blocks.TORCH.defaultBlockState()
                    : Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, side);
            BlockState existing = level.getBlockState(place);
            if ((!existing.isAir() && !existing.canBeReplaced()) || !torch.canSurvive(level, place)) {
                return InteractionResultHolder.pass(stack);
            }
            level.setBlockAndUpdate(place, torch);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.8F, 1.4F);
            player.getCooldowns().addCooldown(this, 10);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Tips.append(tooltip, "tip.gadgets.light_arrow.1", "tip.gadgets.light_arrow.2");
    }
}
