package com.gadgets;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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
    private int nextInput = 0; // which input slot the linker fills next

    public LogicGateBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.LOGIC_GATE_BE, pos, state);
    }

    public String typeName() {
        return TYPES[type];
    }

    public String cycleType() {
        type = (type + 1) % TYPES.length;
        markDirty();
        return TYPES[type];
    }

    public static void tick(World world, BlockPos pos, BlockState state, LogicGateBlockEntity be) {
        if (world.getTime() % INTERVAL != 0L || be.out.isEmpty()) {
            return;
        }
        long now = world.getTime();
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
        String source = world.getRegistryKey().getValue() + "|" + pos.asLong();
        WirelessNetwork.publish(be.out, source, result ? 15 : 0, now);
    }

    @Override
    public String copyOutputChannel() {
        if (out.isEmpty()) {
            out = String.format("CH-%04X", world != null ? world.getRandom().nextInt(0x10000) : 0);
            markDirty();
        }
        return out;
    }

    @Override
    public String bindNextInput(String channel) {
        if (nextInput == 0) {
            inA = channel;
            nextInput = 1;
            markDirty();
            return "input A";
        } else {
            inB = channel;
            nextInput = 0;
            markDirty();
            return "input B";
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("Type", type);
        nbt.putString("InA", inA);
        nbt.putString("InB", inB);
        nbt.putString("Out", out);
        nbt.putInt("Next", nextInput);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        type = nbt.getInt("Type");
        inA = nbt.getString("InA");
        inB = nbt.getString("InB");
        out = nbt.getString("Out");
        nextInput = nbt.getInt("Next");
    }
}
