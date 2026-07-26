package com.gadgets;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;

/** Shows the can's filter item slowly spinning above the lid, so the setting is visible at a glance. */
public class TrashCanRenderer implements BlockEntityRenderer<TrashCanBlockEntity> {
    private final ItemRenderer itemRenderer;

    public TrashCanRenderer(BlockEntityRendererFactory.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(TrashCanBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (be.getFilter() == Items.AIR) {
            return;
        }
        World world = be.getWorld();
        double time = (world != null ? world.getTime() : 0L) + tickDelta;

        matrices.push();
        matrices.translate(0.5, 1.25 + Math.sin(time * 0.06) * 0.03, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) ((time * 1.2) % 360.0)));
        matrices.scale(0.35F, 0.35F, 0.35F);
        itemRenderer.renderItem(new ItemStack(be.getFilter()), ModelTransformationMode.GROUND, light,
                OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, world, 0);
        matrices.pop();
    }
}
