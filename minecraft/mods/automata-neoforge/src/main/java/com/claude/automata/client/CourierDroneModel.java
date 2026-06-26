package com.claude.automata.client;

import com.claude.automata.entity.CourierDroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A small quadcopter model for the Courier Drone, built entirely from boxes:
 * a central body, a cargo box slung underneath, four arms in a "+" layout, and
 * four rotors (each its own part) that spin about their own vertical axis.
 */
@OnlyIn(Dist.CLIENT)
public class CourierDroneModel extends EntityModel<CourierDroneEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(
			ResourceLocation.fromNamespaceAndPath("automata", "courier_drone"), "main");

	private final ModelPart root;
	private final ModelPart rotor0;
	private final ModelPart rotor1;
	private final ModelPart rotor2;
	private final ModelPart rotor3;

	public CourierDroneModel(ModelPart root) {
		this.root = root;
		this.rotor0 = root.getChild("rotor0");
		this.rotor1 = root.getChild("rotor1");
		this.rotor2 = root.getChild("rotor2");
		this.rotor3 = root.getChild("rotor3");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition body = root.addOrReplaceChild("body",
				CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -2.0F, -4.0F, 8.0F, 3.0F, 8.0F),
				PartPose.ZERO);

		body.addOrReplaceChild("cargo",
				CubeListBuilder.create().texOffs(0, 16).addBox(-2.5F, 1.0F, -2.5F, 5.0F, 3.0F, 5.0F),
				PartPose.ZERO);

		// Four arms in a "+" layout, thin 1x1 bars ~6 long along +X, -X, +Z, -Z.
		root.addOrReplaceChild("armPX",
				CubeListBuilder.create().texOffs(28, 0).addBox(4.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F),
				PartPose.ZERO);
		root.addOrReplaceChild("armNX",
				CubeListBuilder.create().texOffs(28, 0).addBox(-10.0F, -0.5F, -0.5F, 6.0F, 1.0F, 1.0F),
				PartPose.ZERO);
		root.addOrReplaceChild("armPZ",
				CubeListBuilder.create().texOffs(28, 0).addBox(-0.5F, -0.5F, 4.0F, 1.0F, 1.0F, 6.0F),
				PartPose.ZERO);
		root.addOrReplaceChild("armNZ",
				CubeListBuilder.create().texOffs(28, 0).addBox(-0.5F, -0.5F, -10.0F, 1.0F, 1.0F, 6.0F),
				PartPose.ZERO);

		// Four rotors, each its own part so it can spin about its own vertical axis.
		root.addOrReplaceChild("rotor0",
				CubeListBuilder.create().texOffs(0, 24).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 1.0F, 6.0F),
				PartPose.offset(9.0F, -3.0F, 0.0F));
		root.addOrReplaceChild("rotor1",
				CubeListBuilder.create().texOffs(0, 24).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 1.0F, 6.0F),
				PartPose.offset(-9.0F, -3.0F, 0.0F));
		root.addOrReplaceChild("rotor2",
				CubeListBuilder.create().texOffs(0, 24).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 1.0F, 6.0F),
				PartPose.offset(0.0F, -3.0F, 9.0F));
		root.addOrReplaceChild("rotor3",
				CubeListBuilder.create().texOffs(0, 24).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 1.0F, 6.0F),
				PartPose.offset(0.0F, -3.0F, -9.0F));

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(CourierDroneEntity entity, float limbSwing, float limbSwingAmount,
			float ageInTicks, float netHeadYaw, float headPitch) {
		float spin = ageInTicks * 2.0F;
		this.rotor0.yRot = spin;
		this.rotor1.yRot = spin;
		this.rotor2.yRot = spin;
		this.rotor3.yRot = spin;
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
			int packedOverlay, int color) {
		this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}
}
