package com.gadgets;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;

/** Draws the displayed item floating above the base, centred over the whole group. */
public class DisplayPedestalRenderer implements BlockEntityRenderer<DisplayPedestalBlockEntity> {
    /** Degrees per tick for spin settings Off/Slow/Medium/Fast. */
    private static final float[] SPIN_SPEED = {0.0F, 1.5F, 4.0F, 8.0F};

    private final ItemRenderer itemRenderer;

    public DisplayPedestalRenderer(BlockEntityRendererFactory.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(DisplayPedestalBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        // Re-skin the base: draw the disguise block in place of the marble.
        BlockState disguise = be.getDisguiseState();
        if (disguise != null) {
            matrices.push();
            MinecraftClient.getInstance().getBlockRenderManager()
                    .renderBlockAsEntity(disguise, matrices, vertexConsumers, light, overlay);
            matrices.pop();
        }

        ItemStack stack = be.getDisplayed();
        if (stack.isEmpty()) {
            return; // only the block that holds the item draws the showcase
        }
        World world = be.getWorld();
        double time = (world != null ? world.getTime() : 0L) + tickDelta;

        // Centre the item over the whole connected group and grow it to match.
        int[] g = world != null ? DisplayPedestalBlock.groupBounds(world, be.getPos()) : null;
        double cx = 0.5, cz = 0.5;
        float sizeMul = 1.0F;
        if (g != null) {
            cx = (g[0] + g[2] + 1) / 2.0 - be.getPos().getX();
            cz = (g[1] + g[3] + 1) / 2.0 - be.getPos().getZ();
            sizeMul = 1.0F + 0.6F * (Math.max(g[2] - g[0], g[3] - g[1]));
        }

        int spin = Math.min(Math.max(be.getSpin(), 0), SPIN_SPEED.length - 1);
        float bob = (float) (Math.sin(time * 0.08) * 0.04);
        float angle = spin == 0 ? 0.0F : (float) ((time * SPIN_SPEED[spin]) % 360.0);
        float scale = (0.45F + be.getScale() * 0.25F) * sizeMul;

        matrices.push();
        matrices.translate(cx, 1.15 + bob, cz);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
        matrices.scale(scale, scale, scale);
        itemRenderer.renderItem(stack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV,
                matrices, vertexConsumers, world, 0);
        matrices.pop();
    }
}
