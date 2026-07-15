package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.CardScannerItem;
import com.jrpetty.mobtrumps.MobCardItem;
import com.jrpetty.mobtrumps.MobTrumps;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Client behaviour for the Card Scanner: while you look through it, mobs get
 * their card projected above them and their stats shown in a readout. The
 * vanilla spyglass scope vignette shows for free because the item uses the
 * spyglass use-animation.
 */
@EventBusSubscriber(modid = MobTrumps.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ScannerClient {

    private static final double RANGE = 40.0;

    private ScannerClient() {
    }

    public static boolean isUsingScanner(Player player) {
        return player != null && player.isUsingItem()
                && player.getUseItem().getItem() instanceof CardScannerItem;
    }

    /** The living mob the player is aiming at within scanner range, or null. */
    public static LivingEntity target(Minecraft mc) {
        Entity cam = mc.getCameraEntity();
        if (cam == null) return null;
        Vec3 eye = cam.getEyePosition(1.0F);
        Vec3 look = cam.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(RANGE));
        AABB box = cam.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(cam, eye, end, box,
                e -> e instanceof LivingEntity && e.isPickable() && !e.isSpectator(), RANGE * RANGE);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    public static MobCard cardFor(LivingEntity entity) {
        if (entity == null) return null;
        Registry<net.minecraft.world.entity.EntityType<?>> reg = BuiltInRegistries.ENTITY_TYPE;
        var key = reg.getKey(entity.getType());
        return key == null ? null : MobCards.byId(key.getPath());
    }

    // --- zoom while scoping ---

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        if (isUsingScanner(event.getPlayer())) {
            event.setNewFovModifier(0.4F);
        }
    }

    // --- the card projected above the targeted mob ---

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !isUsingScanner(mc.player)) {
            return;
        }
        LivingEntity mob = target(mc);
        MobCard card = cardFor(mob);
        if (card == null) {
            return;
        }
        try {
            float pt = mc.getTimer().getGameTimeDeltaPartialTick(false);
            Vec3 camPos = event.getCamera().getPosition();
            double x = Mth.lerp(pt, mob.xOld, mob.getX());
            double y = Mth.lerp(pt, mob.yOld, mob.getY()) + mob.getBbHeight() + 0.75;
            double z = Mth.lerp(pt, mob.zOld, mob.getZ());

            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(x - camPos.x, y - camPos.y, z - camPos.z);
            pose.mulPose(event.getCamera().rotation());
            pose.scale(-0.7F, -0.7F, 0.7F); // billboard toward the camera, flip
            ItemStack stack = MobCardItem.stackOf(card, false);
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    pose, buffers, mc.level, 0);
            buffers.endBatch();
            pose.popPose();
        } catch (Exception ignored) {
            // rendering must never crash the client
        }
    }

    // --- the on-screen stat readout (called from a registered GUI layer) ---

    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!isUsingScanner(mc.player)) {
            return;
        }
        LivingEntity mob = target(mc);
        MobCard card = cardFor(mob);
        Font font = mc.font;
        int sw = g.guiWidth();
        int sh = g.guiHeight();

        if (card == null) {
            String hint = "SCANNING — no card for this target";
            if (mob != null) {
                g.drawString(font, hint, (sw - font.width(hint)) / 2, sh - 78, 0xFF9AA0A6, true);
            }
            return;
        }

        int panelW = 200;
        int panelH = 60;
        int px = (sw - panelW) / 2;
        int py = sh - panelH - 46;
        int tier = com.jrpetty.mobtrumps.client.CardRenderer.tierPrintColor(card) | 0xFF000000;

        g.fill(px - 1, py - 1, px + panelW + 1, py + panelH + 1, 0xFF05070A);
        g.fill(px, py, px + panelW, py + panelH, 0xE0121722);
        g.fill(px, py, px + panelW, py + 2, tier); // tier accent bar

        g.drawString(font, card.displayName(), px + 8, py + 6, tier, false);
        String sub = card.tier().label() + "  ·  scanning";
        g.drawString(font, sub, px + 8, py + 18, 0xFFB9BFC9, false);

        // six stats in two columns
        Stat[] stats = Stat.values();
        for (int i = 0; i < stats.length; i++) {
            Stat s = stats[i];
            int col = i / 3;
            int row = i % 3;
            int tx = px + 8 + col * 98;
            int ty = py + 30 + row * 10;
            Integer sc = MobCardItem.statColor(s).getColor();
            int color = (sc == null ? 0xFFFFFF : sc) | 0xFF000000;
            g.drawString(font, s.shortLabel, tx, ty, color, false);
            String v = String.valueOf(card.stat(s));
            g.drawString(font, v, tx + 84 - font.width(v), ty, 0xFFFFFFFF, false);
        }
    }
}
