package com.claude.automata.client;

import com.claude.automata.entity.CourierDroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renders a Courier Drone as a small floating quadcopter that bobs gently as it
 * flies, with its four rotors spinning. Purely client-side.
 */
@OnlyIn(Dist.CLIENT)
public class CourierDroneRenderer extends EntityRenderer<CourierDroneEntity> {
	private static final ResourceLocation TEXTURE =
			ResourceLocation.fromNamespaceAndPath("automata", "textures/entity/courier_drone.png");

	private final CourierDroneModel model;

	public CourierDroneRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new CourierDroneModel(context.bakeLayer(CourierDroneModel.LAYER));
	}

	@Override
	public ResourceLocation getTextureLocation(CourierDroneEntity entity) {
		return TEXTURE;
	}

	@Override
	public void render(CourierDroneEntity entity, float yaw, float tickDelta, PoseStack matrices,
			MultiBufferSource vertexConsumers, int light) {
		matrices.pushPose();
		float age = entity.tickCount + tickDelta;
		float bob = (float) Math.sin(age * 0.2F) * 0.08F;
		matrices.translate(0.0, 0.3 + bob, 0.0);
		matrices.scale(0.6F, 0.6F, 0.6F);
		// Minecraft entity models are built in an inverted space; flip 180deg about
		// X (and translate down) so the box model renders upright.
		matrices.mulPose(Axis.XP.rotationDegrees(180.0F));
		matrices.translate(0.0, -1.5, 0.0);
		model.setupAnim(entity, 0.0F, 0.0F, age, 0.0F, 0.0F);
		VertexConsumer vc = vertexConsumers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		model.renderToBuffer(matrices, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		matrices.popPose();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}
}
