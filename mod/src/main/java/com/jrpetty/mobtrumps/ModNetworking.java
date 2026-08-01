package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.client.ClientCollection;
import com.jrpetty.mobtrumps.client.ClientHooks;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
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
        registrar.playToClient(CampaignSyncPayload.TYPE, CampaignSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.jrpetty.mobtrumps.client.ClientCampaign.set(payload.states())));
        registrar.playToServer(CampaignActionPayload.TYPE, CampaignActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp
                            && payload.action() == CampaignActionPayload.BEGIN) {
                        CampaignManager.begin(sp, payload.mission());
                    }
                }));
        registrar.playToClient(HallSyncPayload.TYPE, HallSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.jrpetty.mobtrumps.client.ClientHall.set(payload)));
        registrar.playToServer(HallRequestPayload.TYPE, HallRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp
                            && sp.getServer() != null) {
                        PacketDistributor.sendToPlayer(sp, HallOfFame.get(sp.getServer()).snapshot());
                    }
                }));
        registrar.playToClient(RecyclerMenuPayload.TYPE, RecyclerMenuPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHooks.openRecycler(payload.mode())));
        registrar.playToClient(RecyclerSyncPayload.TYPE, RecyclerSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.jrpetty.mobtrumps.client.ClientRecycler.set(payload.fragments())));
        registrar.playToClient(RecyclerResultPayload.TYPE, RecyclerResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.jrpetty.mobtrumps.client.ClientRecycler.result(payload)));
        // --- Guess Who ---
        registrar.playToClient(GuessWhoMenuPayload.TYPE, GuessWhoMenuPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.jrpetty.mobtrumps.client.ClientHooks.openGuessWho()));
        registrar.playToClient(GuessWhoSyncPayload.TYPE, GuessWhoSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.jrpetty.mobtrumps.client.ClientGuessWho.set(payload)));
        registrar.playToServer(GuessWhoActionPayload.TYPE, GuessWhoActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        GuessWhoManager.handle(sp, payload.action(), payload.template(),
                                payload.value(), payload.mobId());
                    }
                }));
        // --- Bluff ---
        registrar.playToClient(BluffMenuPayload.TYPE, BluffMenuPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.jrpetty.mobtrumps.client.ClientHooks.openBluff()));
        registrar.playToClient(BluffSyncPayload.TYPE, BluffSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.jrpetty.mobtrumps.client.ClientBluff.set(payload)));
        registrar.playToServer(BluffActionPayload.TYPE, BluffActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        BluffManager.handle(sp, payload.action(), payload.picks(), payload.stake());
                    }
                }));
        // --- Twenty-One ---
        registrar.playToClient(BlackjackMenuPayload.TYPE, BlackjackMenuPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.jrpetty.mobtrumps.client.ClientHooks.openBlackjack()));
        registrar.playToClient(BlackjackSyncPayload.TYPE, BlackjackSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.jrpetty.mobtrumps.client.ClientBlackjack.set(payload)));
        registrar.playToServer(BlackjackActionPayload.TYPE, BlackjackActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        BlackjackManager.handle(sp, payload.action(), payload.stat());
                    }
                }));
        registrar.playToServer(RecyclerActionPayload.TYPE, RecyclerActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        switch (payload.action()) {
                            case RecyclerActionPayload.SHRED ->
                                    RecyclerManager.shredOne(sp, payload.mobId(), payload.flags() == 1);
                            case RecyclerActionPayload.SHRED_ALL -> RecyclerManager.shredAll(sp);
                            case RecyclerActionPayload.PRINT ->
                                    RecyclerManager.print(sp, payload.flags(), payload.stake());
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
                        } else if (CampaignManager.isPlaying(sp)) {
                            CampaignManager.action(sp, payload.action(), payload.stat());
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
