package com.claude.automata.client;

import com.claude.automata.entity.CourierDroneEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Box-only quadcopter model for the Courier Drone. A central body with a small
 * cargo box slung underneath, four arms in a "+" layout, and four spinning
 * rotors (each its own part) at the arm ends.
 */
@Environment(EnvType.CLIENT)
public class CourierDroneModel extends EntityModel<CourierDroneEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(
			Identifier.of("automata", "courier_drone"), "main");

	private final ModelPart root;
	private final ModelPart rotor0;
	private final ModelPart rotor1;
	private final ModelPart rotor2;
	private final ModelPart rotor3;

	public CourierDroneModel(ModelPart root) {
		super(RenderLayer::getEntityCutoutNoCull);
		this.root = root;
		this.rotor0 = root.getChild("rotor0");
		this.rotor1 = root.getChild("rotor1");
		this.rotor2 = root.getChild("rotor2");
		this.rotor3 = root.getChild("rotor3");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		// Central body: 8 x 3 x 8 centered on the origin.
		root.addChild("body",
				ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -2.0F, -4.0F, 8.0F, 3.0F, 8.0F),
				ModelTransform.NONE);

		// Cargo box slung underneath the body: 5 x 3 x 5.
		root.addChild("cargo",
				ModelPartBuilder.create().uv(0, 16).cuboid(-2.5F, 1.0F, -2.5F, 5.0F, 3.0F, 5.0F),
				ModelTransform.NONE);

		// Four arms in a "+" layout: thin 1 x 1 bars reaching ~6 out along the axes.
		root.addChild("armPX",
				ModelPartBuilder.create().uv(28, 0).cuboid(4.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F),
				ModelTransform.NONE);
		root.addChild("armNX",
				ModelPartBuilder.create().uv(28, 0).cuboid(-10.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F),
				ModelTransform.NONE);
		root.addChild("armPZ",
				ModelPartBuilder.create().uv(28, 0).cuboid(-0.5F, -0.5F, 4.0F, 1.0F, 1.0F, 6.0F),
				ModelTransform.NONE);
		root.addChild("armNZ",
				ModelPartBuilder.create().uv(28, 0).cuboid(-0.5F, -0.5F, -10.0F, 1.0F, 1.0F, 6.0F),
				ModelTransform.NONE);

		// Four rotors, each its own part so it can spin about its own Y axis.
		// Disc-like 6 x 1 x 6 box, pivoted at the arm end (~9 out, slightly above body).
		ModelPartBuilder rotor = ModelPartBuilder.create().uv(0, 24).cuboid(-3.0F, 0.0F, -3.0F, 6.0F, 1.0F, 6.0F);
		root.addChild("rotor0", rotor, ModelTransform.pivot(9.0F, -3.0F, 0.0F));
		root.addChild("rotor1", rotor, ModelTransform.pivot(-9.0F, -3.0F, 0.0F));
		root.addChild("rotor2", rotor, ModelTransform.pivot(0.0F, -3.0F, 9.0F));
		root.addChild("rotor3", rotor, ModelTransform.pivot(0.0F, -3.0F, -9.0F));

		return TexturedModelData.of(modelData, 64, 64);
	}

	@Override
	public void setAngles(CourierDroneEntity entity, float limbAngle, float limbDistance,
			float animationProgress, float headYaw, float headPitch) {
		float spin = animationProgress * 2.0F;
		this.rotor0.yaw = spin;
		this.rotor1.yaw = spin;
		this.rotor2.yaw = spin;
		this.rotor3.yaw = spin;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
		this.root.render(matrices, vertices, light, overlay, color);
	}
}
