package com.gadgets;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Right-click while aiming to "fire" light: a glowstone block is planted on the
 * surface you hit. Light up a cave from the entrance.
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

        BlockPos place = hit.getBlockPos().offset(hit.getSide());
        if (!world.isClient()) {
            BlockState existing = world.getBlockState(place);
            if (!existing.isAir() && !existing.isReplaceable()) {
                return TypedActionResult.pass(stack);
            }
            world.setBlockState(place, Blocks.GLOWSTONE.getDefaultState());
            if (!user.getAbilities().creativeMode) {
                stack.decrement(1);
            }
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_ARROW_HIT, SoundCategory.PLAYERS, 0.8F, 1.4F);
            user.getItemCooldownManager().set(this, 10);
        }
        return TypedActionResult.success(stack, world.isClient());
    }
}
