package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.MobCard;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Physical card storage inside the Collection Book. The book holds one of each
 * card type; depositing vacuums loose singles out of the inventory to keep it
 * tidy, leaving spare duplicates behind for trading and foil-pressing.
 */
public final class BinderStorage {

    private BinderStorage() {
    }

    /** Whether a mob's normal card is filed in the book. */
    public static boolean isStored(ServerPlayer player, String mobId, boolean foil) {
        return player.getData(foil ? ModAttachments.STORED_FOIL.get() : ModAttachments.STORED.get())
                .contains(mobId);
    }

    /**
     * File one of every loose card type into the book. A type already in the
     * book is skipped, so spare duplicates stay in the inventory.
     */
    public static int depositAll(ServerPlayer player) {
        Set<String> stored = new LinkedHashSet<>(player.getData(ModAttachments.STORED.get()));
        Set<String> storedFoil = new LinkedHashSet<>(player.getData(ModAttachments.STORED_FOIL.get()));

        int deposited = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            MobCard card = MobCardItem.cardOf(s);
            if (card == null) continue;
            boolean foil = MobCardItem.isFoilCard(s);
            Set<String> target = foil ? storedFoil : stored;
            if (target.add(card.id())) {
                keep(player, s.split(1)); // the real card goes in, serial and all
                deposited++;
            }
        }

        if (deposited > 0) {
            player.setData(ModAttachments.STORED.get(), List.copyOf(stored));
            player.setData(ModAttachments.STORED_FOIL.get(), List.copyOf(storedFoil));
            // filing a card also records it as collected
            for (String id : stored) CollectionTracker.record(player, id, false);
            for (String id : storedFoil) CollectionTracker.record(player, id, true);
            sync(player);
            ServerSync.flushNow(player);
            player.sendSystemMessage(Component.literal("Filed " + deposited + " card"
                            + (deposited == 1 ? "" : "s") + " into your Collection Book.")
                    .withStyle(ChatFormatting.GREEN));
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.9F, 1.1F);
        } else {
            player.sendSystemMessage(Component.literal(
                            "No loose cards to file — your book already has one of each you're carrying.")
                    .withStyle(ChatFormatting.GRAY));
        }
        return deposited;
    }

    /**
     * File ONE specific loose card from the inventory into the book. Used by
     * the per-card control in the book, so a player selling off a collection
     * can place and remove them one at a time instead of all or nothing.
     */
    public static boolean deposit(ServerPlayer player, String mobId, boolean foil) {
        MobCard card = com.jrpetty.mobtrumps.game.MobCards.byId(mobId);
        if (card == null) return false;
        var attachment = foil ? ModAttachments.STORED_FOIL.get() : ModAttachments.STORED.get();
        List<String> current = player.getData(attachment);
        if (current.contains(mobId)) return false; // the book already holds one

        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            MobCard held = MobCardItem.cardOf(s);
            if (held == null || !held.id().equals(mobId)) continue;
            if (MobCardItem.isFoilCard(s) != foil) continue;
            s.shrink(1);
            List<String> next = new ArrayList<>(current);
            next.add(mobId);
            player.setData(attachment, List.copyOf(next));
            CollectionTracker.record(player, mobId, foil);
            sync(player);
            ServerSync.flushNow(player);
            player.sendSystemMessage(Component.literal("Filed " + (foil ? "a foil " : "")
                            + card.displayName() + " into your book.")
                    .withStyle(ChatFormatting.GREEN));
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.9F, 1.1F);
            return true;
        }
        return false; // not carrying one
    }

    /** Take a single stored card back out of the book as an item. */
    public static boolean withdraw(ServerPlayer player, String mobId, boolean foil) {
        MobCard card = com.jrpetty.mobtrumps.game.MobCards.byId(mobId);
        if (card == null) return false;
        var attachment = foil ? ModAttachments.STORED_FOIL.get() : ModAttachments.STORED.get();
        List<String> current = player.getData(attachment);
        if (!current.contains(mobId)) return false;

        List<String> next = new ArrayList<>(current);
        next.remove(mobId);
        player.setData(attachment, List.copyOf(next));
        CardActions.give(player, MobCardItem.stackOf(card, foil));
        sync(player);
        player.sendSystemMessage(Component.literal("Took " + (foil ? "a foil " : "")
                        + card.displayName() + " out of your book.")
                .withStyle(ChatFormatting.YELLOW));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7F, 1.3F);
        return true;
    }

    /** Empty the whole binder back into the inventory. */
    public static int withdrawAll(ServerPlayer player) {
        List<String> stored = player.getData(ModAttachments.STORED.get());
        List<String> storedFoil = player.getData(ModAttachments.STORED_FOIL.get());
        int taken = 0;
        for (String id : stored) {
            MobCard card = com.jrpetty.mobtrumps.game.MobCards.byId(id);
            if (card != null) { CardActions.give(player, take(player, id, false, card)); taken++; }
        }
        for (String id : storedFoil) {
            MobCard card = com.jrpetty.mobtrumps.game.MobCards.byId(id);
            if (card != null) { CardActions.give(player, take(player, id, true, card)); taken++; }
        }
        player.setData(ModAttachments.STORED.get(), List.of());
        player.setData(ModAttachments.STORED_FOIL.get(), List.of());
        player.setData(ModAttachments.STORED_CARDS.get(), List.of());
        sync(player);
        if (taken > 0) {
            player.sendSystemMessage(Component.literal("Emptied your book — " + taken
                            + " card" + (taken == 1 ? "" : "s") + " back in your inventory.")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            player.sendSystemMessage(Component.literal("Your book has no cards filed in it.")
                    .withStyle(ChatFormatting.GRAY));
        }
        return taken;
    }

    /** Put a physical card into the book's shelf of real stacks. */
    private static void keep(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        List<ItemStack> shelf = new ArrayList<>(player.getData(ModAttachments.STORED_CARDS.get()));
        shelf.add(stack.copy());
        player.setData(ModAttachments.STORED_CARDS.get(), List.copyOf(shelf));
    }

    /**
     * Pull the filed card for a mob back off the shelf. Falls back to minting a
     * plain one only if the shelf has no record — which can only happen for a
     * book filled before cards had identities.
     */
    private static ItemStack take(ServerPlayer player, String mobId, boolean foil, MobCard card) {
        List<ItemStack> shelf = new ArrayList<>(player.getData(ModAttachments.STORED_CARDS.get()));
        for (int i = 0; i < shelf.size(); i++) {
            ItemStack s = shelf.get(i);
            MobCard held = MobCardItem.cardOf(s);
            if (held != null && held.id().equals(mobId) && MobCardItem.isFoilCard(s) == foil) {
                shelf.remove(i);
                player.setData(ModAttachments.STORED_CARDS.get(), List.copyOf(shelf));
                return s;
            }
        }
        return MobCardItem.stackOf(card, foil);
    }

    /** Ask for a binder push; coalesced by {@link ServerSync}. */
    public static void sync(ServerPlayer player) {
        ServerSync.markStorage(player);
    }

    /** Actually send it. Binder changes are all click-driven, so these flush at once. */
    public static void sendNow(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new StorageSyncPayload(
                player.getData(ModAttachments.STORED.get()),
                player.getData(ModAttachments.STORED_FOIL.get())));
    }
}
