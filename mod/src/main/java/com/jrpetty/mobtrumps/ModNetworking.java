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
                        () -> ClientCollection.set(payload.collected(), payload.foils())));
        registrar.playToClient(PackOpenedPayload.TYPE, PackOpenedPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientHooks.openPackReveal(payload.pulls())));
    }
}
