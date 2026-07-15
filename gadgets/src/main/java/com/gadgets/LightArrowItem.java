package com.gadgets;

import java.util.List;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Right-click while aiming to "fire" a torch onto the surface you hit — a
 * standing torch on a floor, a wall torch on a wall. Light a cave from afar.
 */
public class LightArrowItem extends Item {
    private static final double RANGE = 32.0;

    public LightArrowItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        Vec3d start = user.getEyePos();
        Vec3d end = start.add(user.getRotationVec(1.0F).multiply(RANGE));
        BlockHitResult hit = world.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, user));

        if (hit.getType() != HitResult.Type.BLOCK) {
            return TypedActionResult.pass(stack);
        }
        Direction side = hit.getSide();
        if (side == Direction.DOWN) {
            return TypedActionResult.pass(stack); // torches can't hang from a ceiling
        }

        if (!world.isClient()) {
            BlockPos place = hit.getBlockPos().offset(side);
            BlockState torch = side == Direction.UP
                    ? Blocks.TORCH.getDefaultState()
                    : Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, side);
            BlockState existing = world.getBlockState(place);
            if ((!existing.isAir() && !existing.isReplaceable()) || !torch.canPlaceAt(world, place)) {
                return TypedActionResult.pass(stack);
            }
            world.setBlockState(place, torch);
            if (!user.getAbilities().creativeMode) {
                stack.decrement(1);
            }
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_ARROW_HIT, SoundCategory.PLAYERS, 0.8F, 1.4F);
            user.getItemCooldownManager().set(this, 10);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        Tips.append(tooltip, "tip.gadgets.light_arrow.1", "tip.gadgets.light_arrow.2");
    }
}
