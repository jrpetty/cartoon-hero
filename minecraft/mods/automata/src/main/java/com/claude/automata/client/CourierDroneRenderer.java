package com.claude.automata.client;

import com.claude.automata.entity.CourierDroneEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Renders a Courier Drone as a small quadcopter that hovers with a gentle bob,
 * its four rotors spinning. Purely client-side.
 */
@Environment(EnvType.CLIENT)
public class CourierDroneRenderer extends EntityRenderer<CourierDroneEntity> {
	private static final Identifier TEXTURE = Identifier.of("automata", "textures/entity/courier_drone.png");

	private final CourierDroneModel model;

	public CourierDroneRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.model = new CourierDroneModel(context.getPart(CourierDroneModel.LAYER));
	}

	@Override
	public Identifier getTexture(CourierDroneEntity entity) {
		return TEXTURE;
	}

	@Override
	public void render(CourierDroneEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		matrices.push();
		float age = entity.age + tickDelta;
		float bob = (float) Math.sin(age * 0.2f) * 0.08f;
		matrices.translate(0.0, 0.3 + bob, 0.0);
		matrices.scale(0.6f, 0.6f, 0.6f);
		// Entity model space is built upside-down relative to world space; flip it
		// upright (same convention as vanilla box-model entity renderers).
		matrices.scale(1.0f, -1.0f, -1.0f);
		matrices.translate(0.0, -1.5, 0.0);
		this.model.setAngles(entity, 0.0f, 0.0f, age, 0.0f, 0.0f);
		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		this.model.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}
}
