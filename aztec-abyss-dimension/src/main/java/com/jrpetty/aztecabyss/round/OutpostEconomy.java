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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Outpost's economy: you arrive with nothing, you buy what you carry, and
 * you leave with none of it.
 *
 * <p>This reverses the rule the arenas run on, and deliberately. On the Temple
 * and the Bridge you bring your own gear, so a run is a test of what you already
 * own. The Outpost is the opposite: everyone starts with a wooden sword and an
 * empty bar, and what you are holding on round fifteen is entirely a record of
 * how well you played rounds one to fourteen. Nothing you brought matters and
 * nothing you found leaves with you.
 *
 * <p>What does leave is materials - and only after you die. Survive further, and
 * the Outpost pays out more. That way a run that ends badly is still worth
 * having made, without any of the gear economy leaking back into the overworld.
 *
 * <p>Your real inventory is put in a vault the moment you step in and handed
 * back the moment you step out. That vault is persisted with the world rather
 * than held in memory, because a server that crashes mid-run must not be a
 * server that eats somebody's netherite.
 */
public final class OutpostEconomy extends SavedData {

    public static final String NAME = "aztecabyss_outpost_vault";

    /** Points on a hit, on a kill, and the bonus for a headshot. */
    public static final int POINTS_HIT = 10;
    public static final int POINTS_KILL = 50;
    public static final int POINTS_HEADSHOT = 25;
    /** Boarding a window pays, exactly as the genre has always paid for it. */
    public static final int POINTS_BOARD = 10;
    /** Everyone starts here. */
    public static final int STARTING_POINTS = 500;

    private static final Map<UUID, Integer> POINTS = new HashMap<>();

    /** Stashed real-world inventories, keyed by player. */
    private final Map<UUID, ListTag> vault = new HashMap<>();

    public OutpostEconomy() {
    }

    // ------------------------------------------------------------------
    // Points
    // ------------------------------------------------------------------

    public static int points(UUID id) {
        return POINTS.getOrDefault(id, 0);
    }

    public static void reset(UUID id) {
        POINTS.put(id, STARTING_POINTS);
    }

    public static void clearAll() {
        POINTS.clear();
    }

    /** Adds points, with the Scavenger perk taken into account by the caller. */
    public static void award(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return;
        }
        POINTS.merge(player.getUUID(), amount, Integer::sum);
    }

    /** Tries to spend. Returns false and tells the player if they cannot afford it. */
    public static boolean spend(ServerPlayer player, int cost, String what) {
        int have = points(player.getUUID());
        if (have < cost) {
            player.displayClientMessage(Component.literal(
                    "§cNot enough. §7" + what + " costs §e" + cost
                            + "§7; you have §e" + have + "§7."), true);
            return false;
        }
        POINTS.put(player.getUUID(), have - cost);
        return true;
    }

    // ------------------------------------------------------------------
    // The vault
    // ------------------------------------------------------------------

    /**
     * Takes everything a player owns, stores it, and hands them a wooden sword.
     * Idempotent: a second call while something is already vaulted does nothing,
     * so a stray entry event can never overwrite a stored inventory with an
     * empty one.
     */
    public static void enter(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        OutpostEconomy store = get(server);
        if (store.vault.containsKey(player.getUUID())) {
            return;
        }
        ListTag saved = new ListTag();
        player.getInventory().save(saved);
        store.vault.put(player.getUUID(), saved);
        store.setDirty();

        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(Items.WOODEN_SWORD));
        reset(player.getUUID());
        player.displayClientMessage(Component.literal(
                "§7Your gear is held at the door. §fA wooden sword and §e"
                        + STARTING_POINTS + "§f points is what you get."), false);
    }

    /**
     * Hands back what they came in with, and pays out whatever the run earned.
     * Everything picked up inside the Outpost is destroyed on the way out.
     */
    public static void leave(ServerPlayer player, int roundReached) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        OutpostEconomy store = get(server);
        ListTag saved = store.vault.remove(player.getUUID());
        POINTS.remove(player.getUUID());
        player.getInventory().clearContent();
        if (saved != null) {
            player.getInventory().load(saved);
            store.setDirty();
        }
        for (ItemStack stack : payout(roundReached)) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    /** True if this player has gear waiting - used to avoid double-vaulting. */
    public static boolean hasVault(MinecraftServer server, UUID id) {
        return get(server).vault.containsKey(id);
    }

    /**
     * What the Outpost pays for a run. Materials only - never gear, never
     * anything enchanted, and nothing that would shortcut the arenas.
     */
    private static List<ItemStack> payout(int round) {
        List<ItemStack> out = new ArrayList<>();
        if (round <= 0) {
            return out;
        }
        out.add(new ItemStack(Items.IRON_INGOT, Math.min(64, 2 + round * 2)));
        if (round >= 5) {
            out.add(new ItemStack(Items.GOLD_INGOT, Math.min(64, round)));
        }
        if (round >= 10) {
            out.add(new ItemStack(Items.DIAMOND, Math.min(32, round / 5)));
        }
        if (round >= 20) {
            out.add(new ItemStack(Items.NETHERITE_SCRAP, Math.min(8, round / 10)));
        }
        return out;
    }

    /** A one-line summary of the payout, for the message shown on the way out. */
    public static String payoutSummary(int round) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack s : payout(round)) {
            if (sb.length() > 0) {
                sb.append("§7, ");
            }
            sb.append("§f").append(s.getCount()).append("x ")
                    .append(s.getItem().getDescription().getString());
        }
        return sb.length() == 0 ? "§7nothing" : sb.toString();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public static OutpostEconomy get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static SavedData.Factory<OutpostEconomy> factory() {
        return new SavedData.Factory<>(OutpostEconomy::new, OutpostEconomy::load, null);
    }

    private static OutpostEconomy load(CompoundTag tag, HolderLookup.Provider provider) {
        OutpostEconomy out = new OutpostEconomy();
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
