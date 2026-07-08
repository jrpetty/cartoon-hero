package com.gadgets;

/** Implemented by the transmitter/receiver block entities so the Linker can retune either. */
public interface ChannelBlockEntity {
    String getChannel();

    void setChannel(String channel);
}
