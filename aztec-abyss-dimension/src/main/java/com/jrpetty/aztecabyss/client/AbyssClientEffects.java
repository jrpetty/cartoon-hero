package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.config.AbyssConfig;
import com.jrpetty.aztecabyss.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The "Upside Down" atmosphere, all client-side:
 *   - dark red fog that closes in as the round climbs
 *   - floating spore/ash motes drifting around the player
 *   - occasional red lightning flashes with distant thunder
 *   - a red vignette + quickening heartbeat when the player is low on health
 *
 * Driven by {@link ClientAbyssState}, which the server keeps in sync.
 */
@EventBusSubscriber(modid = AztecAbyssConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class AbyssClientEffects {

    private static int flashTicks = 0;      // remaining ticks of a red lightning flash
    private static int heartbeatCooldown = 0;
    private static long clientTick = 0;

    private AbyssClientEffects() {
    }

    private static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null
                && mc.level.dimension().equals(AztecAbyssConstants.ABYSS_LEVEL_KEY);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Drain the HUD-toggle key every tick, regardless of dimension.
        while (ClientSetup.TOGGLE_HUD.consumeClick()) {
            ClientAbyssState.toggleHud();
        }
        if (!active()) {
            flashTicks = 0;
            return;
        }
        clientTick++;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        if (ClientAbyssState.getCinematicTicks() > 0) {
            playArrivalCinematic(mc, player);
        }

        // Floating spores.
        for (int i = 0; i < 6; i++) {
            double x = player.getX() + (mc.level.random.nextDouble() - 0.5) * 24;
            double y = player.getY() + mc.level.random.nextDouble() * 8;
            double z = player.getZ() + (mc.level.random.nextDouble() - 0.5) * 24;
            mc.level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0, 0.01, 0);
            if (mc.level.random.nextInt(4) == 0) {
                mc.level.addParticle(ParticleTypes.WARPED_SPORE, x, y, z, 0, 0.02, 0);
            }
        }

        // Fog round: thicken the air with extra low-drifting ash motes.
        if (ClientAbyssState.isFogRound()) {
            for (int i = 0; i < 10; i++) {
                double x = player.getX() + (mc.level.random.nextDouble() - 0.5) * 16;
                double y = player.getY() + mc.level.random.nextDouble() * 4;
                double z = player.getZ() + (mc.level.random.nextDouble() - 0.5) * 16;
                mc.level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0, 0.005, 0);
            }
        }

        // Drifting wisps that scatter away when you get close.
        for (int i = 0; i < 2; i++) {
            double wx = player.getX() + (mc.level.random.nextDouble() - 0.5) * 30;
            double wy = player.getY() + 1 + mc.level.random.nextDouble() * 5;
            double wz = player.getZ() + (mc.level.random.nextDouble() - 0.5) * 30;
            double dx = wx - player.getX();
            double dz = wz - player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz) + 0.001;
            // Close wisps flee outward; distant ones drift gently.
            double flee = dist < 6 ? 0.12 : 0.0;
            mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz,
                    (dx / dist) * flee, 0.01, (dz / dist) * flee);
        }

        // Red lightning flash - rarer, but more frequent at higher rounds.
        int round = ClientAbyssState.getRound();
        int chance = Math.max(120, 600 - round * 20);
        if (flashTicks <= 0 && mc.level.random.nextInt(chance) == 0) {
            flashTicks = 6;
            player.playSound(net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, 0.4f, 0.6f);
        }
        if (flashTicks > 0) {
            flashTicks--;
        }

        // Low-health heartbeat.
        if (heartbeatCooldown > 0) {
            heartbeatCooldown--;
        }
        float hp = player.getHealth() / player.getMaxHealth();
        if (hp <= 0.35f && heartbeatCooldown <= 0) {
            player.playSound(ModSounds.HEARTBEAT.get(), 0.9f, 1.0f);
            heartbeatCooldown = (int) (14 + hp * 20); // faster as HP drops
        }
    }

    /**
     * The arrival sequence: the view swings to settle on the temple through the
     * trees while a staged title crawls and a low drone swells, before control
     * returns and the first round horn sounds.
     */
    private static void playArrivalCinematic(Minecraft mc, LocalPlayer player) {
        int t = ClientAbyssState.getCinematicTicks(); // counts DOWN from ~110
        ClientAbyssState.decrementCinematic();

        // Ease the camera to face the temple centre (0, ~70, 0).
        double dx = 0.0 - player.getX();
        double dy = (AztecAbyssConstants.ARENA_FLOOR_Y + 14) - player.getEyeY();
        double dz = 0.0 - player.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        player.setYRot(lerpAngle(player.getYRot(), targetYaw, 0.08f));
        player.setXRot(Mth.lerp(0.08f, player.getXRot(), targetPitch));

        // Drone swell at the very start.
        if (t == 110) {
            player.playSound(ModSounds.AMBIENT_DREAD.get(), 1.0f, 0.5f);
            mc.gui.setTimes(8, 50, 20);
            mc.gui.setTitle(net.minecraft.network.chat.Component.literal("§0§lTHE AZTEC ABYSS"));
            mc.gui.setSubtitle(net.minecraft.network.chat.Component.literal("§8Something ancient has been waiting."));
        }
        if (t == 55) {
            mc.gui.setTimes(8, 40, 15);
            mc.gui.setTitle(net.minecraft.network.chat.Component.literal(""));
            mc.gui.setSubtitle(net.minecraft.network.chat.Component.literal("§7The temple watches through the trees..."));
        }
    }

    private static float lerpAngle(float from, float to, float f) {
        float delta = Mth.wrapDegrees(to - from);
        return from + delta * f;
    }

    /** Top-right countdown to when the Abyss reopens for a player on a death lockout. */
    private static void drawCooldownTimer(net.minecraft.client.gui.GuiGraphics g, Minecraft mc) {
        long ms = ClientAbyssState.cooldownRemainingMillis();
        if (ms <= 0L) {
            return;
        }
        long totalSec = ms / 1000L;
        long h = totalSec / 3600L;
        long m = (totalSec / 60L) % 60L;
        long s = totalSec % 60L;
        String time = (h > 0 ? h + "h " : "") + m + "m " + s + "s";
        net.minecraft.network.chat.Component label =
                net.minecraft.network.chat.Component.literal("§5⟡ Abyss reopens in §d" + time);
        net.minecraft.client.gui.Font font = mc.font;
        int screenW = g.guiWidth();
        int tw = font.width(label);
        int x = screenW - tw - 6;
        int y = 6;
        g.fill(x - 4, y - 2, screenW - 2, y + font.lineHeight + 2, 0x88000000);
        g.drawString(font, label, x, y, 0xFFFFFF, true);
    }

    /** The live run HUD panel: round, enemies remaining, squad headcount, personal kills. */
    private static void drawHud(net.minecraft.client.gui.GuiGraphics g, Minecraft mc) {
        net.minecraft.client.gui.Font font = mc.font;
        int round = ClientAbyssState.getRound();
        int enemies = ClientAbyssState.getEnemiesRemaining();
        int up = ClientAbyssState.getPlayersUp();
        int total = ClientAbyssState.getPlayersTotal();
        int kills = ClientAbyssState.getMyKills();

        java.util.List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        lines.add(net.minecraft.network.chat.Component.literal("§6§lTHE AZTEC ABYSS"));
        if (round <= 0) {
            lines.add(net.minecraft.network.chat.Component.literal("§7Preparing the hunt..."));
        } else {
            lines.add(net.minecraft.network.chat.Component.literal("§fRound §e§l" + round));
        }
        if (ClientAbyssState.isFogRound()) {
            lines.add(net.minecraft.network.chat.Component.literal("§7§oA fog round — stay sharp"));
        }
        if (round > 0) {
            lines.add(enemies > 0
                    ? net.minecraft.network.chat.Component.literal("§fEnemies left: §c" + enemies)
                    : net.minecraft.network.chat.Component.literal("§aWave clear"));
        }
        if (total > 1) {
            lines.add(net.minecraft.network.chat.Component.literal("§fSquad: §a" + up + "§7/" + total + " up"));
        }
        lines.add(net.minecraft.network.chat.Component.literal("§fYour kills: §b" + kills));

        int pad = 4;
        int lineH = font.lineHeight + 2;
        int maxW = 0;
        for (net.minecraft.network.chat.Component c : lines) {
            maxW = Math.max(maxW, font.width(c));
        }
        int x = 6;
        int y = 6;
        int boxW = maxW + pad * 2;
        int boxH = lines.size() * lineH + pad * 2 - 2;
        g.fill(x, y, x + boxW, y + boxH, 0x88000000);
        g.fill(x, y, x + boxW, y + 1, 0xAA7A0E0E); // thin blood-red accent edge
        int ty = y + pad;
        for (net.minecraft.network.chat.Component c : lines) {
            g.drawString(font, c, x + pad, ty, 0xFFFFFF, true);
            ty += lineH;
        }
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!active()) {
            return;
        }
        float intensity = ClientAbyssState.intensity(AbyssConfig.MAX_ROUND.get());
        // Base eerie fog even at round 0; it tightens toward the player as rounds climb.
        float far = Mth.lerp(intensity, 64.0f, 20.0f);
        float near = Mth.lerp(intensity, 8.0f, 1.0f);
        if (ClientAbyssState.isFogRound()) {
            // Fog round: a pea-soup mist - they'll be on you before you see them.
            far = Math.min(far, 14.0f);
            near = Math.min(near, 2.0f);
        }
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), far));
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), near));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!active()) {
            return;
        }
        float flash = flashTicks > 0 ? 0.35f : 0.0f;
        if (ClientAbyssState.isFogRound()) {
            // Fog round: a sickly grey-green murk, distinct from the usual red haze.
            event.setRed(0.12f + flash);
            event.setGreen(0.13f);
            event.setBlue(0.12f);
        } else {
            // Push the fog toward a dim, blood-tinged near-black.
            event.setRed(0.06f + flash);
            event.setGreen(0.01f);
            event.setBlue(0.03f);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // The re-entry cooldown counts down wherever the player is waiting.
        drawCooldownTimer(event.getGuiGraphics(), mc);
        if (!active()) {
            return;
        }
        int w = event.getGuiGraphics().guiWidth();
        int h = event.getGuiGraphics().guiHeight();

        // Live run HUD (toggle with the keybind, default H).
        if (ClientAbyssState.isInRun() && ClientAbyssState.isHudVisible()) {
            drawHud(event.getGuiGraphics(), mc);
        }

        // Red flash overlay.
        if (flashTicks > 0) {
            int alpha = (int) (90 * (flashTicks / 6.0)) << 24;
            event.getGuiGraphics().fill(0, 0, w, h, alpha | 0x00FF1010);
        }

        // Low-health vignette: four edge bands that intensify as HP drops.
        float hp = mc.player.getHealth() / mc.player.getMaxHealth();
        if (hp <= 0.5f) {
            float t = 1.0f - (hp / 0.5f);
            int a = (int) (140 * t) << 24;
            int col = a | 0x00A00000;
            int band = (int) (h * 0.18f * (0.5f + t));
            event.getGuiGraphics().fillGradient(0, 0, w, band, col, 0x00000000);
            event.getGuiGraphics().fillGradient(0, h - band, w, h, 0x00000000, col);
            event.getGuiGraphics().fillGradient(0, 0, band, h, col, 0x00000000);
            event.getGuiGraphics().fillGradient(w - band, 0, w, h, 0x00000000, col);
        }
    }
}
