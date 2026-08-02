package com.jrpetty.aztecabyss.block;

import com.jrpetty.aztecabyss.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The text behind a Marker Block.
 *
 * <p>Signs were the original authoring surface and the right first answer: they
 * already exist, they already carry their text inside structure NBT, and an
 * author already knows how to write on one. What they are not is big enough.
 *
 * <p>A sign gives four lines and the editing screen stops you at roughly fifteen
 * characters each. That is fine for {@code [Box] price=950} and hopeless for a
 * spawner, which wants an entity id, a count, a first round, a repeat interval, a
 * health override and a damage override - a line and a half of which will not fit
 * on the whole sign. Authors were shortening option names and splitting one
 * marker across two signs to get round a limit that was never in the format, only
 * in the furniture.
 *
 * <p>So: {@value #MAX_LINES} lines of up to {@value #MAX_LENGTH} characters. Same
 * parser, same grammar, same {@code key=value} on every line after the first -
 * nothing an author has learned stops being true, there is simply room.
 */
public class MarkerBlockEntity extends BlockEntity {

    public static final int MAX_LINES = 8;
    public static final int MAX_LENGTH = 256;

    private final List<String> lines = new ArrayList<>();

    public MarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MARKER.get(), pos, state);
    }

    public List<String> lines() {
        return List.copyOf(lines);
    }

    public String line(int index) {
        return index >= 0 && index < lines.size() ? lines.get(index) : "";
    }

    /**
     * Writes one line, padding the list out if the author skipped ahead.
     *
     * <p>Setting line four of an empty marker gives you four lines, three of them
     * blank, rather than an error. Authors write markers out of order and a tool
     * that refuses the order they thought of it in is a tool they fight.
     */
    public void setLine(int index, String text) {
        if (index < 0 || index >= MAX_LINES) {
            return;
        }
        while (lines.size() <= index) {
            lines.add("");
        }
        lines.set(index, text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text);
        trimTrailing();
        setChanged();
        sync();
    }

    public void setLines(List<String> replacement) {
        lines.clear();
        for (String s : replacement) {
            if (lines.size() >= MAX_LINES) {
                break;
            }
            lines.add(s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH) : s);
        }
        trimTrailing();
        setChanged();
        sync();
    }

    /** The kind, without its brackets, or empty if line one is not a kind. */
    public String kind() {
        String head = line(0).trim();
        if (head.length() < 3 || !head.startsWith("[") || !head.endsWith("]")) {
            return "";
        }
        return head.substring(1, head.length() - 1).trim();
    }

    private void trimTrailing() {
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (String s : lines) {
            list.add(StringTag.valueOf(s));
        }
        tag.put("Lines", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lines.clear();
        ListTag list = tag.getList("Lines", Tag.TAG_STRING);
        for (int i = 0; i < list.size() && i < MAX_LINES; i++) {
            lines.add(list.getString(i));
        }
    }

    // The client needs the text to draw the creative-mode label, so the block
    // entity ships its whole payload on chunk load and on every edit.

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
