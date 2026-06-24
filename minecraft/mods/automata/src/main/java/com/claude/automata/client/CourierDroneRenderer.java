package com.claude.automata.client;

import com.claude.automata.entity.CourierDroneEntity;
import com.claude.automata.registry.ModItems;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.util.Identifier;

/**
 * Renders a Courier Drone as a small floating machine component, bobbing as it
 * flies. Purely client-side.
 */
@Environment(EnvType.CLIENT)
public class CourierDroneRenderer extends EntityRenderer<CourierDroneEntity> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/experience_orb.png");

	public CourierDroneRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(CourierDroneEntity entity) {
		return TEXTURE;
	}

	@Override
	public void render(CourierDroneEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		matrices.push();
		float bob = (float) Math.sin((entity.age + tickDelta) * 0.2f) * 0.08f;
		matrices.translate(0.0, 0.3 + bob, 0.0);
		matrices.scale(0.7f, 0.7f, 0.7f);
		ItemStack stack = new ItemStack(ModItems.MACHINE_FRAME);
		MinecraftClient.getInstance().getItemRenderer().renderItem(stack, ModelTransformationMode.GROUND, light,
				OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, entity.getWorld(), 0);
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}
}
