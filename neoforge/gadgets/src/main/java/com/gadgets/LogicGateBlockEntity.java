package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads two wireless channels, applies a boolean gate, and broadcasts the result
 * (15 or 0) on an output channel — a programmable node for the wireless network.
 */
public class LogicGateBlockEntity extends BlockEntity implements GateChannels {
    private static final int INTERVAL = 4;

    /** Gate types cycled with an empty hand. */
    public static final String[] TYPES = {"AND", "OR", "XOR", "NOT", "NAND", "NOR", "XNOR"};

    private int type = 0;
    private String inA = "";
    private String inB = "";
    private String out = "";
    private int nextInput = 0;

    public LogicGateBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.LOGIC_GATE_BE.get(), pos, state);
    }

    public String typeName() {
        return TYPES[type];
    }

    public String cycleType() {
        type = (type + 1) % TYPES.length;
        setChanged();
        return TYPES[type];
    }

    public static void tick(Level level, BlockPos pos, BlockState state, LogicGateBlockEntity be) {
        if (level.getGameTime() % INTERVAL != 0L || be.out.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        boolean a = WirelessNetwork.strength(be.inA, now) > 0;
        boolean b = WirelessNetwork.strength(be.inB, now) > 0;
        boolean result = switch (be.type) {
            case 0 -> a && b;        // AND
            case 1 -> a || b;        // OR
            case 2 -> a ^ b;         // XOR
            case 3 -> !a;            // NOT (uses input A only)
            case 4 -> !(a && b);     // NAND
            case 5 -> !(a || b);     // NOR
            default -> !(a ^ b);     // XNOR
        };
        String source = level.dimension().location() + "|" + pos.asLong();
        WirelessNetwork.publish(be.out, source, result ? 15 : 0, now);
    }

    @Override
    public String copyOutputChannel() {
        if (out.isEmpty()) {
            out = String.format("CH-%04X", level != null ? level.getRandom().nextInt(0x10000) : 0);
            setChanged();
        }
        return out;
    }

    @Override
    public String bindNextInput(String channel) {
        if (nextInput == 0) {
            inA = channel;
            nextInput = 1;
            setChanged();
            return "input A";
        } else {
            inB = channel;
            nextInput = 0;
            setChanged();
            return "input B";
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Type", type);
        tag.putString("InA", inA);
        tag.putString("InB", inB);
        tag.putString("Out", out);
        tag.putInt("Next", nextInput);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        type = tag.getInt("Type");
        inA = tag.getString("InA");
        inB = tag.getString("InB");
        out = tag.getString("Out");
        nextInput = tag.getInt("Next");
    }
}
