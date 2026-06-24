package com.claude.automata.client;

import com.claude.automata.entity.CourierDroneEntity;
import com.claude.automata.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renders a Courier Drone as a small floating machine component, bobbing as it
 * flies. Purely client-side.
 */
@OnlyIn(Dist.CLIENT)
public class CourierDroneRenderer extends EntityRenderer<CourierDroneEntity> {
	private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/experience_orb.png");

	public CourierDroneRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(CourierDroneEntity entity) {
		return TEXTURE;
	}

	@Override
	public void render(CourierDroneEntity entity, float yaw, float tickDelta, PoseStack matrices,
			MultiBufferSource vertexConsumers, int light) {
		matrices.pushPose();
		float bob = (float) Math.sin((entity.tickCount + tickDelta) * 0.2f) * 0.08f;
		matrices.translate(0.0, 0.3 + bob, 0.0);
		matrices.scale(0.7f, 0.7f, 0.7f);
		ItemStack stack = new ItemStack(ModItems.MACHINE_FRAME.get());
		Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND, light,
				OverlayTexture.NO_OVERLAY, matrices, vertexConsumers, entity.level(), 0);
		matrices.popPose();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}
}
