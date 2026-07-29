package com.jrpetty.aztecabyss.network;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.client.ClientAbyssState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the custom payload and provides the server-side send helper.
 * The client handler defers to {@link ClientAbyssState} (loaded only on the
 * client) to avoid touching client classes on a dedicated server.
 */
@EventBusSubscriber(modid = AztecAbyssConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetworking {

    private ModNetworking() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                AbyssStatePayload.TYPE,
                AbyssStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientAbyssState.accept(payload)));
        registrar.playToClient(
                RunRecapPayload.TYPE,
                RunRecapPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientAbyssState.openRecap(payload)));
        registrar.playToClient(
                AbyssCooldownPayload.TYPE,
                AbyssCooldownPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientAbyssState.acceptCooldown(payload)));
        registrar.playToClient(
                SquadPayload.TYPE,
                SquadPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientAbyssState.acceptSquad(payload)));
        registrar.playToServer(
                PingPayload.TYPE,
                PingPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        com.jrpetty.aztecabyss.round.RoundManager.onPing(
                                sp, new net.minecraft.core.BlockPos(payload.x(), payload.y(), payload.z()));
                    }
                }));
    }

    public static void sendState(ServerPlayer player, boolean inRun, int round) {
        sendState(player, inRun, round, false);
    }

    public static void sendState(ServerPlayer player, boolean inRun, int round, boolean fogRound) {
        PacketDistributor.sendToPlayer(player, new AbyssStatePayload(inRun, round, fogRound, 0, 0, 0));
    }

    /** Full in-run state including the live HUD figures. */
    public static void sendHud(ServerPlayer player, int round, boolean fogRound,
                               int enemiesRemaining, int playersUp, int playersTotal, int myKills) {
        PacketDistributor.sendToPlayer(player, new AbyssStatePayload(
                true, round, fogRound, enemiesRemaining,
                AbyssStatePayload.packPlayers(playersUp, playersTotal), myKills));
    }

    /** Pushes the player's re-entry cooldown deadline so their screen can count it down. */
    public static void sendCooldown(ServerPlayer player, long cooldownUntil) {
        PacketDistributor.sendToPlayer(player, new AbyssCooldownPayload(cooldownUntil));
    }

    /** Pushes a player's squadmate list for the co-op teammate HUD. */
    public static void sendSquad(ServerPlayer player, java.util.List<TeammateInfo> teammates) {
        PacketDistributor.sendToPlayer(player, new SquadPayload(teammates));
    }

    public static void sendRecap(ServerPlayer player, int round, int kills, int revives, int survivalSeconds,
                                 int previousBest, boolean victory, boolean multiplayer, boolean extracted,
                                 int headshots, int deaths, boolean ritual) {
        PacketDistributor.sendToPlayer(player, new RunRecapPayload(
                round, kills, headshots, survivalSeconds,
                RunRecapPayload.pack(previousBest, deaths, revives),
                RunRecapPayload.packFlags(victory, multiplayer, extracted, ritual)));
    }
}
