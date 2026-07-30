package com.gadgets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/** Live summary on the hub's screen: nodes linked, combined rate, low-stock and stalled-counter alerts. */
public class CommandHubRenderer implements BlockEntityRenderer<CommandHubBlockEntity> {
    private static final int HEAD_COLOR = 0xE6AA3C;
    private static final int OK_COLOR = 0xFFC864;
    private static final int LOW_COLOR = 0xFF5555;

    private final Font font;

    public CommandHubRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(CommandHubBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof CommandHubBlock)) {
            return;
        }
        Direction front = state.getValue(HorizontalDirectionalBlock.FACING);

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-front.toYRot()));
        pose.translate(0.0, 0.0, 0.505);
        pose.scale(0.014F, -0.014F, 0.014F);

        int alarms = be.alarmCount();
        String l1 = "◈ " + be.nodeCount() + " linked";
        String l2 = ItemCounterBlockEntity.compact(be.totalRateMin()) + " /min";
        String l3 = alarms == 0 ? "all clear" : alarms + " alert" + (alarms == 1 ? "" : "s") + "!";
        Matrix4f matrix = pose.last().pose();
        int bright = LightTexture.FULL_BRIGHT;
        font.drawInBatch(l1, -font.width(l1) / 2.0F, -14.0F, HEAD_COLOR, false,
                matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, bright);
        font.drawInBatch(l2, -font.width(l2) / 2.0F, -3.0F, OK_COLOR, false,
                matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, bright);
        font.drawInBatch(l3, -font.width(l3) / 2.0F, 8.0F, alarms == 0 ? OK_COLOR : LOW_COLOR, false,
                matrix, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, bright);
        pose.popPose();
    }
}
