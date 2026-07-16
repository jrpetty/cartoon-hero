package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.client.ClientCollection;
import com.jrpetty.mobtrumps.client.ClientHooks;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {

    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(CollectionSyncPayload.TYPE, CollectionSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientCollection.set(payload.collected(), payload.foils(),
                                payload.duelWins(), payload.deck(), payload.displayFoil(),
                                payload.kills())));
        registrar.playToClient(PackOpenedPayload.TYPE, PackOpenedPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHooks.openPackReveal(payload.pulls())));
        registrar.playToServer(SetDeckPayload.TYPE, SetDeckPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        DeckManager.saveDeck(sp, payload.deck());
                    }
                }));
        registrar.playToServer(SetDisplayPayload.TYPE, SetDisplayPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        CollectionTracker.setDisplayFoil(sp, payload.mobId(), payload.foil());
                    }
                }));
        registrar.playToClient(StorageSyncPayload.TYPE, StorageSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientCollection.setStorage(payload.stored(), payload.storedFoil())));
        registrar.playToServer(StorageActionPayload.TYPE, StorageActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        switch (payload.action()) {
                            case StorageActionPayload.DEPOSIT_ALL -> BinderStorage.depositAll(sp);
                            case StorageActionPayload.WITHDRAW -> BinderStorage.withdraw(sp, payload.mobId(), payload.foil());
                            default -> { }
                        }
                    }
                }));
        registrar.playToServer(LinkDisplayPayload.TYPE, LinkDisplayPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        LinkDisplayPayload.handle(payload, sp);
                    }
                }));
        registrar.playToClient(BattleSyncPayload.TYPE, BattleSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHooks.updateBattle(payload)));
        registrar.playToServer(BattleActionPayload.TYPE, BattleActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        TableBattleManager.action(sp, payload.action(), payload.stat());
                    }
                }));
        registrar.playToClient(TableMenuPayload.TYPE, TableMenuPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHooks.openTableMenu(payload)));
        registrar.playToServer(TableActionPayload.TYPE, TableActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        DuelTables.handleAction(sp, payload);
                    }
                }));
    }
}
