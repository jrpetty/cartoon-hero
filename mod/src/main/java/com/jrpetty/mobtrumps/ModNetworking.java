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
        registrar.playToServer(ClientPrefsPayload.TYPE, ClientPrefsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        ClientPrefsPayload.store(sp, payload.flags());
                    }
                }));
        registrar.playToClient(AchievementSyncPayload.TYPE, AchievementSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.jrpetty.mobtrumps.client.ClientAwards.set(payload.progress(),
                                payload.states(), payload.eggPending(), payload.eggClaimed())));
        registrar.playToServer(AwardActionPayload.TYPE, AwardActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        switch (payload.action()) {
                            case AwardActionPayload.CLAIM -> AchievementManager.claim(sp, payload.key());
                            case AwardActionPayload.CHOOSE_EGG ->
                                    AchievementManager.chooseEgg(sp, payload.key(), payload.mobId());
                            default -> { }
                        }
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
                            case StorageActionPayload.DEPOSIT_ONE -> BinderStorage.deposit(sp, payload.mobId(), payload.foil());
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
                        // REMATCH fires after the duel has ended (player no longer "in" it)
                        if (payload.action() == BattleActionPayload.REMATCH) {
                            DuelManager.handleScreenRematch(sp);
                        } else if (DuelManager.isInDuel(sp)) {
                            DuelManager.handleScreenAction(sp, payload.action(), payload.stat());
                        } else {
                            TableBattleManager.action(sp, payload.action(), payload.stat());
                        }
                    }
                }));
        registrar.playToClient(BattleEmotePayload.TYPE, BattleEmotePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHooks.showBattleEmote(payload.side(), payload.text())));
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
