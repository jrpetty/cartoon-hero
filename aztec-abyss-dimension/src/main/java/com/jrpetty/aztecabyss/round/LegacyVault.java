package com.jrpetty.aztecabyss.round;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The retired Outpost's gear vault, kept only so that it can be emptied.
 *
 * <p>The Outpost took your real inventory at the door and handed it back on the
 * way out, and it kept that inventory in the world's saved data rather than in
 * memory so that a crash could never eat it. The map is gone now, and with it
 * every way of reaching that hand-back - which would have turned the one thing
 * it was careful about into a way to lose somebody's netherite for good.
 *
 * <p>So the vault outlives the map by exactly this much: the same file, read in
 * the same format, and the first time anybody with something still in it logs
 * in, it all goes back to them - added to what they have now, never replacing
 * it, with anything that does not fit dropped at their feet. In practice the
 * vault will almost always be empty; the Outpost was shelved long before it was
 * removed. "Almost always" is not a reason to delete somebody's gear.
 */
public final class LegacyVault extends SavedData {

    /** The Outpost's own file name, so existing worlds find what it stored. */
    private static final String NAME = "aztecabyss_outpost_vault";

    private final Map<UUID, ListTag> vault = new HashMap<>();

    /** Gives back anything the Outpost was still holding for this player. */
    public static void returnHeldGear(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        LegacyVault store = server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
        ListTag saved = store.vault.remove(player.getUUID());
        if (saved == null) {
            return;
        }
        store.setDirty();
        Inventory held = new Inventory(player);
        held.load(saved);
        int stacks = 0;
        for (int i = 0; i < held.getContainerSize(); i++) {
            ItemStack stack = held.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack copy = stack.copy();
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
            stacks++;
        }
        player.displayClientMessage(Component.literal(
                "§6✦ The Outpost is gone, and it was still holding your gear. §7"
                        + stacks + " stack" + (stacks == 1 ? "" : "s") + " returned."), false);
    }

    private static SavedData.Factory<LegacyVault> factory() {
        return new SavedData.Factory<>(LegacyVault::new, LegacyVault::load, null);
    }

    private static LegacyVault load(CompoundTag tag, HolderLookup.Provider provider) {
        LegacyVault out = new LegacyVault();
        CompoundTag held = tag.getCompound("vault");
        for (String key : held.getAllKeys()) {
            try {
                out.vault.put(UUID.fromString(key), held.getList(key, Tag.TAG_COMPOUND));
            } catch (IllegalArgumentException ignored) {
                // A malformed key is not worth losing the rest of the vault over.
            }
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag held = new CompoundTag();
        for (Map.Entry<UUID, ListTag> e : vault.entrySet()) {
            held.put(e.getKey().toString(), e.getValue());
        }
        tag.put("vault", held);
        return tag;
    }
}
