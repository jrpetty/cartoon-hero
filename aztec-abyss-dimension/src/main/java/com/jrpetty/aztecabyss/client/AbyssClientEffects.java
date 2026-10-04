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
        // Drain the keybinds every tick, regardless of dimension.
        while (ClientSetup.TOGGLE_HUD.consumeClick()) {
            ClientAbyssState.toggleHud();
        }
        boolean pingPressed = false;
        while (ClientSetup.PING.consumeClick()) {
            pingPressed = true;
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
        if (pingPressed) {
            sendPing(mc, player);
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

        // Floating embers drifting up for depth over the base spore layer.
        for (int i = 0; i < 3; i++) {
            double x = player.getX() + (mc.level.random.nextDouble() - 0.5) * 30;
            double y = player.getY() + mc.level.random.nextDouble() * 10 - 2;
            double z = player.getZ() + (mc.level.random.nextDouble() - 0.5) * 30;
            mc.level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0, 0.02 + mc.level.random.nextDouble() * 0.02, 0);
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

    /** Ray-casts where the player is looking (up to 48 blocks) and pings that spot to the squad. */
    private static void sendPing(Minecraft mc, LocalPlayer player) {
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition(1.0F);
        net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0F);
        net.minecraft.world.phys.Vec3 end = eye.add(look.scale(48.0));
        net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(
                eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player);
        net.minecraft.core.BlockPos target = mc.level.clip(ctx).getBlockPos();
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.jrpetty.aztecabyss.network.PingPayload(target.getX(), target.getY(), target.getZ()));
        // The server plays the ping blip back to the whole squad, including us.
    }

    /** The co-op squad panel: each teammate's name + health bar, and a marker to anyone downed. */
    private static void drawSquad(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, int y) {
        java.util.List<com.jrpetty.aztecabyss.network.TeammateInfo> squad = ClientAbyssState.getSquad();
        if (squad.isEmpty() || mc.player == null) {
            return;
        }
        net.minecraft.client.gui.Font font = mc.font;
        int x = 8;
        int rowH = 12;
        int panelW = 150;
        int panelH = 18 + squad.size() * rowH + 3;
        g.fill(x, y, x + panelW, y + panelH, HUD_PANEL);
        UiKit.outline(g, x, y, panelW, panelH, UiKit.EDGE);
        g.fill(x, y, x + 2, y + panelH, UiKit.GOLD);
        g.drawString(font, "SQUAD", x + 8, y + 5, UiKit.GOLD, true);

        int ry = y + 18;
        for (com.jrpetty.aztecabyss.network.TeammateInfo t : squad) {
            if (t.downed()) {
                double dx = t.x() - mc.player.getX();
                double dz = t.z() - mc.player.getZ();
                int dist = (int) Math.sqrt(dx * dx + dz * dz);
                String card = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "E" : "W") : (dz > 0 ? "S" : "N");
                int blink = UiKit.pulse(UiKit.RED, 0.6f + 0.4f * (float) Math.sin(clientTick / 3.0));
                g.drawString(font, "⚑ " + font.plainSubstrByWidth(t.name(), 60), x + 8, ry, blink, true);
                String where = "DOWN " + dist + "m " + card;
                g.drawString(font, where, x + panelW - 6 - font.width(where), ry, UiKit.RED, true);
            } else {
                g.drawString(font, font.plainSubstrByWidth(t.name(), 80), x + 8, ry, UiKit.TEXT, true);
                float frac = Math.max(0f, Math.min(1f, t.health() / 100.0f));
                int col = frac > 0.5f ? UiKit.GREEN : frac > 0.25f ? UiKit.AMBER : UiKit.RED;
                UiKit.meter(g, x + panelW - 52, ry + 2, 46, 4, frac, col);
            }
            ry += rowH;
        }
    }

    private static final int HUD_PANEL = 0xD90B0A10;

    /**
     * The live run panel: which arena, the round, how much of the wave is down,
     * what is left of it, your kills and - in co-op - how many of the squad are
     * still standing. Same shape as the maze's panel: a dark plate in the top
     * left with the round's colour down its spine, quiet unless something is
     * worth looking at.
     *
     * @return the y just under the panel, for the squad panel to stack below
     */
    private static int drawHud(net.minecraft.client.gui.GuiGraphics g, Minecraft mc) {
        net.minecraft.client.gui.Font font = mc.font;
        int round = ClientAbyssState.getRound();
        int enemies = ClientAbyssState.getEnemiesRemaining();
        int waveTotal = ClientAbyssState.getWaveTotal();
        int up = ClientAbyssState.getPlayersUp();
        int total = ClientAbyssState.getPlayersTotal();
        int kills = ClientAbyssState.getMyKills();
        int map = ClientAbyssState.getMapOrdinal();
        String where = map >= 0
                ? com.jrpetty.aztecabyss.worldgen.ArenaMap.byId(map).title().toUpperCase(java.util.Locale.ROOT)
                : "THE AZTEC ABYSS";

        int x = 8;
        int y = 8;
        int w = 150;
        boolean fog = ClientAbyssState.isFogRound();
        boolean squad = total > 1;
        int h = 46 + (fog ? 11 : 0) + (squad ? 11 : 0);
        int spine = round >= 15 ? UiKit.RED : round >= 8 ? UiKit.AMBER : UiKit.GOLD;

        g.fill(x, y, x + w, y + h, HUD_PANEL);
        UiKit.outline(g, x, y, w, h, UiKit.EDGE);
        g.fill(x, y, x + 2, y + h, spine);

        int tx = x + 8;
        int ty = y + 5;
        g.drawString(font, font.plainSubstrByWidth(where, w - 16), tx, ty, UiKit.TEXT_FAINT, true);
        ty += 11;

        if (round <= 0) {
            g.drawString(font, "Preparing the hunt…", tx, ty, UiKit.TEXT_DIM, true);
        } else {
            g.drawString(font, net.minecraft.network.chat.Component.literal("ROUND " + round)
                    .withStyle(st -> st.withBold(true)), tx, ty, spine, true);
            String left = enemies > 0 ? enemies + " left" : "wave clear";
            g.drawString(font, left, x + w - 6 - font.width(left), ty,
                    enemies > 0 ? UiKit.RED : UiKit.GREEN, true);
        }
        ty += 12;

        // How much of the wave is down - the one figure that answers "nearly
        // there?" without arithmetic.
        float done = waveTotal > 0 ? 1.0f - (float) enemies / waveTotal : (round > 0 ? 1.0f : 0.0f);
        UiKit.meter(g, tx, ty, w - 14, 4, done, enemies > 0 ? UiKit.RED : UiKit.GREEN);
        ty += 8;

        if (fog) {
            g.drawString(font, "≈ fog round — stay sharp", tx, ty, 0xFF9AA59A, true);
            ty += 11;
        }
        g.drawString(font, "✦ " + kills + (kills == 1 ? " kill" : " kills"), tx, ty, UiKit.CYAN, true);
        if (squad) {
            ty += 11;
            g.drawString(font, "❤ squad " + up + "/" + total + " up", tx, ty,
                    up < total ? UiKit.AMBER : UiKit.TEXT_DIM, true);
        }
        return y + h + 6;
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
        // Nothing of this mod's is drawn outside its own dimension. The
        // re-entry countdown used to sit in the overworld's top-right corner
        // for up to twenty hours; the portal already says the same thing
        // when you touch it, which is the only moment it matters.
        if (!active()) {
            return;
        }
        int w = event.getGuiGraphics().guiWidth();
        int h = event.getGuiGraphics().guiHeight();

        // Live run HUD (toggle with the keybind, default H).
        if (ClientAbyssState.isInRun() && ClientAbyssState.isHudVisible()) {
            int under = drawHud(event.getGuiGraphics(), mc);
            drawSquad(event.getGuiGraphics(), mc, under);
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
            // The side bands fade sideways. GuiGraphics only shades top to
            // bottom, so these used to fade downward instead - a red stripe
            // down each edge, dark at the top, rather than a vignette.
            UiKit.hGradient(event.getGuiGraphics(), 0, 0, band, h, col, col & 0x00FFFFFF);
            UiKit.hGradient(event.getGuiGraphics(), w - band, 0, w, h, col & 0x00FFFFFF, col);
        }
    }
}
