package com.jrpetty.aztecabyss.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The authoring kit: a region selector, and markers you place instead of type.
 *
 * <p>Two things were making map-building harder than building. Picking the map's
 * extent with a radius meant guessing a number and hoping it covered the far
 * corner without dragging in half the landscape; and every marker meant typing a
 * bracketed word onto a blank sign correctly, in the dark, forty times.
 *
 * <p>Both tools are vanilla items carrying data components rather than registered
 * ones. That is not laziness - a registered item needs a model, a texture and a
 * translation, and none of those make the tool work any better. A blaze rod that
 * knows it is a wand does the whole job and cannot break a build.
 */
public final class BuildTools {

    private static final String WAND_TAG = "aztecabyss_wand";
    private static final String KIT_TAG = "aztecabyss_marker";

    /** The two corners each player has picked. */
    private static final Map<UUID, BlockPos[]> SELECTION = new HashMap<>();

    private BuildTools() {
    }

    // ------------------------------------------------------------------
    // The wand
    // ------------------------------------------------------------------

    public static ItemStack wand() {
        ItemStack stack = new ItemStack(Items.BLAZE_ROD);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(WAND_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("§6Map Wand"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("§7Left-click a block — first corner"),
                Component.literal("§7Right-click a block — second corner"),
                Component.literal("§8/arena create <name> to build it"))));
        return stack;
    }

    public static boolean isWand(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(WAND_TAG);
    }

    public static void setCorner(ServerPlayer player, BlockPos pos, boolean first) {
        BlockPos[] pair = SELECTION.computeIfAbsent(player.getUUID(), k -> new BlockPos[2]);
        pair[first ? 0 : 1] = pos.immutable();
        BlockPos other = pair[first ? 1 : 0];
        String label = first ? "First" : "Second";
        if (other == null) {
            player.displayClientMessage(Component.literal(
                    "§6" + label + " corner §7at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                            + " §8— now pick the other"), true);
            return;
        }
        BoundingBox box = selectionOf(player);
        player.displayClientMessage(Component.literal(
                "§6" + label + " corner §7set — §f" + spanText(box)), true);
    }

    /** The selected box, or null if both corners are not picked. */
    public static BoundingBox selectionOf(ServerPlayer player) {
        BlockPos[] pair = SELECTION.get(player.getUUID());
        if (pair == null || pair[0] == null || pair[1] == null) {
            return null;
        }
        return BoundingBox.fromCorners(pair[0], pair[1]);
    }

    public static void clearSelection(ServerPlayer player) {
        SELECTION.remove(player.getUUID());
    }

    public static String spanText(BoundingBox box) {
        return (box.maxX() - box.minX() + 1) + " × "
                + (box.maxY() - box.minY() + 1) + " × "
                + (box.maxZ() - box.minZ() + 1);
    }

    /** Volume, used to refuse selections large enough to hang a server. */
    public static long volumeOf(BoundingBox box) {
        return (long) (box.maxX() - box.minX() + 1)
                * (box.maxY() - box.minY() + 1)
                * (box.maxZ() - box.minZ() + 1);
    }

    // ------------------------------------------------------------------
    // The marker kit
    // ------------------------------------------------------------------

    /** Every marker the engine understands, for the palette and for tab-completion. */
    public static final String[] KINDS = {
            "spawn", "extract", "horde", "pen", "boss", "loot", "powerup",
            "dealer", "door", "box", "perk", "upgrade", "zone", "spawner", "trap", "teleport"
    };

    /**
     * A sign that already says what it is.
     *
     * <p>Sign text lives in the block entity, and an item can carry block-entity
     * data forward into the block it places - so this is a sign that arrives in
     * the world pre-written. Place it and the marker exists; no typing, nothing to
     * spell wrong.
     */
    public static ItemStack markerSign(String kind, String secondLine) {
        ItemStack stack = new ItemStack(Items.OAK_SIGN);

        ListTag messages = new ListTag();
        messages.add(StringTag.valueOf(json("[" + kind + "]")));
        messages.add(StringTag.valueOf(json(secondLine == null ? "" : secondLine)));
        messages.add(StringTag.valueOf(json("")));
        messages.add(StringTag.valueOf(json("")));

        CompoundTag front = new CompoundTag();
        front.put("messages", messages);

        CompoundTag be = new CompoundTag();
        be.putString("id", "minecraft:sign");
        be.put("front_text", front);
        stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(be));

        CompoundTag mark = new CompoundTag();
        mark.putString(KIT_TAG, kind);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(mark));

        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("§6[" + kind + "]§r §7marker"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("§7Place it. That is the whole step."),
                Component.literal("§8Edit the sign to add key=value options."))));
        return stack;
    }

    /** Sign messages are stored as JSON text, so even a bare word needs wrapping. */
    private static String json(String text) {
        return "{\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    /** A sensible starting line for kinds that need an argument to be useful. */
    public static String hintFor(String kind) {
        return switch (kind) {
            case "dealer" -> "minecraft:iron_sword";
            case "horde" -> "area=start";
            case "door" -> "area=back cost=1500";
            case "perk" -> "minecraft:health_boost";
            case "zone" -> "effect=minecraft:slowness";
            case "loot" -> "tier=1";
            case "box" -> "price=950";
            case "upgrade" -> "price=5000";
            case "boss" -> "minecraft:warden";
            case "spawner" -> "minecraft:skeleton";
            case "trap" -> "cost=1000 damage=12";
            case "teleport" -> "id=a";
            default -> "";
        };
    }
}
