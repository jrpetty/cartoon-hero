package com.jrpetty.aztecabyss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Spider;

/**
 * What a Griever looks like.
 *
 * <p>It was a vanilla spider scaled up, which reads as a big spider rather than as
 * the thing the maze is about. This draws dark segmented plate over the whole
 * model and eight burning eyes on top of it, so a Griever is unmistakable at the
 * far end of a corridor and unmistakably not a spider you might find in a cave.
 *
 * <h2>Why a layer and not a model</h2>
 *
 * <p>A new entity type with its own geometry would mean a model class, a renderer,
 * a registration and a spawn-egg's worth of scaffolding, and the result would be a
 * shape invented from nothing. A spider already moves the way this should move -
 * eight legs, low body, that awful scuttle - and the animation is the half that
 * sells it. Re-skinning what is already crawling toward you is both the smaller
 * change and the better-looking one.
 *
 * <p>The plate is drawn with {@code entityCutoutNoCull} so it sits over the vanilla
 * skin, and the eyes with {@link RenderType#eyes}, which is the render type
 * vanilla uses for the spider's own eyes - full-bright, unaffected by the dark
 * corridor it is standing in. That is the point: the last thing you see is the
 * eyes.
 */
public class GrieverLayer extends RenderLayer<Spider, SpiderModel<Spider>> {

    private static final ResourceLocation PLATE = ResourceLocation.fromNamespaceAndPath(
            com.jrpetty.aztecabyss.AztecAbyssConstants.MOD_ID, "textures/entity/griever.png");
    private static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath(
            com.jrpetty.aztecabyss.AztecAbyssConstants.MOD_ID, "textures/entity/griever_eyes.png");

    private static final String TAG = "aztecabyss_griever";

    public GrieverLayer(RenderLayerParent<Spider, SpiderModel<Spider>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int packedLight,
                       Spider entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // Ordinary spiders are left completely alone. This layer is attached to
        // the shared spider renderer, so it runs for every spider in the world and
        // the tag is the only thing that separates the maze's monster from a cave.
        if (!entity.getPersistentData().getBoolean(TAG) || entity.isInvisible()) {
            return;
        }

        VertexConsumer plate = buffers.getBuffer(RenderType.entityCutoutNoCull(PLATE));
        getParentModel().renderToBuffer(pose, plate, packedLight,
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

        VertexConsumer eyes = buffers.getBuffer(RenderType.eyes(EYES));
        getParentModel().renderToBuffer(pose, eyes, 15728640,
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }
}
