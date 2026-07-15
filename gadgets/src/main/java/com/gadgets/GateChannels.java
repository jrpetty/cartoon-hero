package com.gadgets;

/**
 * A block whose channels the Redstone Linker binds differently from a plain
 * transmitter/receiver: copying reads its single OUTPUT channel, while pasting
 * fills its two INPUT slots in turn.
 */
public interface GateChannels {
    /** Returns the output channel, minting a fresh one if unset. */
    String copyOutputChannel();

    /** Writes the given channel into the next input slot; returns a label like "input A". */
    String bindNextInput(String channel);
}
