package com.jrpetty.mobtrumps.client;

/** Client-side fragment count, pushed after each machine run. */
public final class ClientRecycler {

    private static volatile int fragments;

    private ClientRecycler() {
    }

    public static void set(int count) {
        fragments = Math.max(0, count);
    }

    public static int fragments() {
        return fragments;
    }
}
