package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.HoloProjectorBlockEntity;
import com.jrpetty.mobtrumps.MobCardItem;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import java.util.HashMap;
import java.util.Map;

/** Draws the projector's card on its front plaque and a rotating 3D mob hologram above it. */
public class HoloProjectorRenderer implements BlockEntityRenderer<HoloProjectorBlockEntity> {

    private final ItemRenderer itemRenderer;
    private final Map<String, LivingEntity> cache = new HashMap<>();

    public HoloProjectorRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(HoloProjectorBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (!be.hasCard()) {
            return;
        }
        MobCard card = MobCards.byId(be.getMobId());
        if (card == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Direction facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        long time = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
        float t = (time + partialTick);

        // --- card plaque, standing just in front of the pedestal face ---
        try {
            ItemStack stack = MobCardItem.stackOf(card, be.isFoil());
            pose.pushPose();
            // plaque sits flush on the column face (column spans 4..12px),
            // centred on its height
            pose.translate(0.5, 0.5, 0.5);
            pose.translate(facing.getStepX() * 0.27, 0.0, facing.getStepZ() * 0.27);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
            pose.scale(0.42F, 0.42F, 0.42F);
            itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay,
                    pose, buffers, be.getLevel(), 0);
            pose.popPose();
        } catch (Exception ignored) {
        }

        // --- rotating 3D mob hologram floating above ---
        LivingEntity mob = CardRenderer.portraitEntity(mc, card, cache);
        if (mob == null) {
            return;
        }
        try {
            float bob = (float) Math.sin(t * 0.05F) * 0.06F;
            float spin = t * 2.2F;
            float fit = 0.55F / Math.max(1.0F, mob.getBbHeight());

            pose.pushPose();
            pose.translate(0.5, 1.35 + bob, 0.5);
            pose.scale(fit, fit, fit);
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            mob.setYBodyRot(0.0F);
            mob.yBodyRotO = 0.0F;
            mob.setYRot(0.0F);
            mob.yRotO = 0.0F;
            mob.setYHeadRot(0.0F);
            mob.yHeadRotO = 0.0F;

            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            dispatcher.setRenderShadow(false);
            dispatcher.render(mob, 0.0, 0.0, 0.0, 0.0F, partialTick, pose, buffers,
                    LightTexture.FULL_BRIGHT);
            dispatcher.setRenderShadow(true);
            pose.popPose();
        } catch (Exception ignored) {
            // never let a rogue entity model crash the client
        }
    }
}
