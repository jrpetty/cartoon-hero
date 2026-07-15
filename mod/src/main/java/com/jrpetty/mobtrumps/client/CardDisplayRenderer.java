package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.CardDisplayBlockEntity;
import com.jrpetty.mobtrumps.MobCardItem;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * Renders the card a display is projecting from its owner's collection. The
 * card floats gently just in front of the frame; nothing physical is stored.
 */
public class CardDisplayRenderer implements BlockEntityRenderer<CardDisplayBlockEntity> {

    private final ItemRenderer itemRenderer;

    public CardDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(CardDisplayBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (!be.hasCard()) {
            return;
        }
        MobCard card = MobCards.byId(be.getMobId());
        if (card == null) {
            return;
        }
        ItemStack stack = MobCardItem.stackOf(card, be.isFoil());
        Direction facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);

        // a slow float + bob so the display feels alive
        long time = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
        float t = (time + partialTick) * 0.04f;
        float bob = (float) Math.sin(t) * 0.012f;

        pose.pushPose();
        pose.translate(0.5, 0.5 + bob, 0.5);
        double off = 0.47;
        pose.translate(facing.getStepX() * off, 0.0, facing.getStepZ() * off);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        pose.scale(0.92F, 0.92F, 0.92F);

        int lit = be.getLevel() == null ? light
                : LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().relative(facing));
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, lit, overlay,
                pose, buffers, be.getLevel(), 0);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CardDisplayBlockEntity be) {
        return false;
    }
}
