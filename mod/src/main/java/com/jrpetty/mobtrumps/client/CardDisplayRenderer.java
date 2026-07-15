package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.CardDisplayBlockEntity;
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

/** Renders the mounted card flat against the display block's front face. */
public class CardDisplayRenderer implements BlockEntityRenderer<CardDisplayBlockEntity> {

    private final ItemRenderer itemRenderer;

    public CardDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(CardDisplayBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        ItemStack card = be.getCard();
        if (card.isEmpty()) {
            return;
        }
        Direction facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);

        // mirror vanilla's item-frame transform: to centre, out along the face
        // normal, then rotate so the flat item faces outward
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        double off = 0.47;
        pose.translate(facing.getStepX() * off, 0.0, facing.getStepZ() * off);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        pose.scale(0.9F, 0.9F, 0.9F);

        int lit = be.getLevel() == null ? light
                : LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().relative(facing));
        itemRenderer.renderStatic(card, ItemDisplayContext.FIXED, lit, overlay,
                pose, buffers, be.getLevel(), 0);
        pose.popPose();
    }
}
