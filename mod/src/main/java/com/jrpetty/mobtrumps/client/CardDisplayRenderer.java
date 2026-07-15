package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.CardDisplayBlockEntity;
import com.jrpetty.mobtrumps.MobCardItem;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.joml.Matrix4f;

/**
 * Renders the projected card as a full, readable stat card drawn straight onto
 * the display panel — name, tier, a six-row stat table with value bars, and the
 * total rating. Uses in-world text/quads (the same primitives signs use), so it
 * renders reliably on the block face. No physical card is stored.
 */
public class CardDisplayRenderer implements BlockEntityRenderer<CardDisplayBlockEntity> {

    // virtual "card canvas" in text pixels; scaled to sit on the block face
    private static final float CANVAS_H = 150f;
    private static final float FACE_FILL = 0.72f; // fraction of the 1-block face the card fills

    private final Font font;

    public CardDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(CardDisplayBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (!be.hasCard()) {
            return;
        }
        MobCard card = MobCards.byId(be.getMobId());
        if (card == null) {
            return;
        }
        Direction facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        boolean foil = be.isFoil();

        try {
            pose.pushPose();
            // same orientation that correctly faces the item sprite outward, then
            // scale into card-pixel space (positive x so glyphs aren't mirrored,
            // negative y because text lays out downward like a sign)
            pose.translate(0.5, 0.5, 0.5);
            pose.translate(facing.getStepX() * 0.47, 0.0, facing.getStepZ() * 0.47);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
            float s = FACE_FILL / CANVAS_H;
            pose.scale(s, -s, s);
            Matrix4f mtx = pose.last().pose();

            int tierCol = argb(MobCardItem.tierColor(card.tier()));
            int total = 0;
            for (Stat st : Stat.values()) total += card.stat(st);

            // --- header: name (auto-fit) + tier line ---
            String name = card.displayName();
            float nameScale = Math.min(1.35f, 104f / Math.max(1, font.width(name)));
            centered(name, 0f, -70f, tierCol, nameScale, pose, buffers);
            centered(card.tier().label().toUpperCase(java.util.Locale.ROOT), 0f, -55f,
                    tierCol, 0.9f, pose, buffers);
            if (foil) {
                text("HOLO", -50f, -70f, argb(ChatFormatting.LIGHT_PURPLE), mtx, buffers);
            }

            // --- six stat rows: coloured label, value bar, value ---
            Stat[] stats = Stat.values();
            float rowTop = -36f;
            float rowStep = 16f;
            for (int i = 0; i < stats.length; i++) {
                Stat st = stats[i];
                float y = rowTop + i * rowStep;
                int col = argb(MobCardItem.statColor(st));
                int value = card.stat(st);
                text(st.label.toUpperCase(java.util.Locale.ROOT), -50f, y, col, mtx, buffers);
                // value bar: track then filled portion (0..10 -> 40px)
                bar(buffers, mtx, 6f, y - 1f, 46f, y + 7f, 0xC0141821);
                bar(buffers, mtx, 6f, y - 1f, 6f + value * 4f, y + 7f, col);
                right(String.valueOf(value), 52f, y, 0xFFFFFFFF, mtx, buffers);
            }

            // --- total rating footer ---
            bar(buffers, mtx, -52f, 60f, 52f, 74f, 0xE0F3E2A7);
            text("MOB RATING", -48f, 63f, 0xFF35281A, mtx, buffers);
            right(String.valueOf(total), 48f, 63f, 0xFF35281A, mtx, buffers);

            pose.popPose();
        } catch (Exception ignored) {
            // in-world text/quads must never crash the client
        }
    }

    private void centered(String txt, float cx, float y, int color, float scale,
                          PoseStack pose, MultiBufferSource buffers) {
        pose.pushPose();
        pose.translate(cx, y, 0.0);
        pose.scale(scale, scale, 1.0f);
        float w = font.width(txt);
        font.drawInBatch(txt, -w / 2f, 0f, color, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private void text(String txt, float x, float y, int color, Matrix4f mtx, MultiBufferSource buffers) {
        font.drawInBatch(txt, x, y, color, false, mtx, buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    private void right(String txt, float xRight, float y, int color, Matrix4f mtx, MultiBufferSource buffers) {
        font.drawInBatch(txt, xRight - font.width(txt), y, color, false, mtx, buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    /** A flat coloured rectangle on the card, drawn with the nameplate-background type. */
    private void bar(MultiBufferSource buffers, Matrix4f mtx, float x0, float y0, float x1, float y1, int argb) {
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        vc.addVertex(mtx, x0, y1, 0f).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(mtx, x1, y1, 0f).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(mtx, x1, y0, 0f).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(mtx, x0, y0, 0f).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
    }

    private static int argb(ChatFormatting cf) {
        Integer c = cf.getColor();
        return (c == null ? 0xFFFFFF : c) | 0xFF000000;
    }

    @Override
    public boolean shouldRenderOffScreen(CardDisplayBlockEntity be) {
        return false;
    }
}
