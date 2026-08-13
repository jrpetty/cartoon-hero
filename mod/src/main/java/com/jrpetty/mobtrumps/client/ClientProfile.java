package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.ProfileRequestPayload;
import com.jrpetty.mobtrumps.ProfileSyncPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The last profile the server sent, and the request that asks for a fresh one.
 *
 * <p>Starts as {@link ProfileSyncPayload#empty()} rather than null so the page
 * renders zeroes for the moment between opening the book and the reply
 * arriving, instead of needing a null check at every one of forty call sites.
 */
public final class ClientProfile {

    /** Don't re-ask more often than this; the page is opened by clicking a tab. */
    private static final long MIN_GAP_MS = 1000L;

    private static volatile ProfileSyncPayload state = ProfileSyncPayload.empty();
    private static volatile long requestedAt;

    private ClientProfile() {
    }

    public static void set(ProfileSyncPayload payload) {
        state = payload;
    }

    public static ProfileSyncPayload state() {
        return state;
    }

    /** Ask the server for a fresh profile, at most once a second. */
    public static void request() {
        long now = System.currentTimeMillis();
        if (now - requestedAt < MIN_GAP_MS) {
            return;
        }
        requestedAt = now;
        PacketDistributor.sendToServer(ProfileRequestPayload.get());
    }
}
