package com.gadgets;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * A Command Hub's board, in your pocket.
 *
 * <p>Read a hub's code off its screen, type it into the tablet, and from then
 * on it answers anywhere — down a mineshaft, in the Nether, a thousand blocks
 * out. What it shows is the last board that hub published, with how long ago it
 * was heard from, because a base whose chunks are unloaded is genuinely not
 * doing anything and pretending otherwise would be a lie rather than a feature.
 */
public class BaseTabletItem extends Item {
    private static final String CODE_KEY = "HubCode";
    private static final String LOCK_KEY = "LockHash";
    /** A passcode is four digits — a PIN, deliberately shorter than a hub code. */
    public static final int LOCK_LENGTH = 4;

    public BaseTabletItem(Properties properties) {
        super(properties);
    }

    public static String codeOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(CODE_KEY);
    }

    public static void setCode(ItemStack stack, String code) {
        put(stack, CODE_KEY, code == null ? "" : code);
    }

    /** The SHA-256 of the tablet's passcode, or empty for a factory-fresh one. */
    public static String lockOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(LOCK_KEY);
    }

    public static void setLock(ItemStack stack, String hash) {
        put(stack, LOCK_KEY, hash == null ? "" : hash);
    }

    /** Merge one key into the stack's data — a plain replace here would mean
     *  relinking a tablet quietly wiped its lock, or locking wiped its hub. */
    private static void put(ItemStack stack, String key, String value) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        tag.putString(key, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** True when the text is exactly four digits — the only shape a passcode has. */
    public static boolean isPasscode(String text) {
        if (text == null || text.length() != LOCK_LENGTH) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * What is actually stored on the tablet: a hash, never the passcode.
     *
     * <p>Item data is visible to the holding client, so a plaintext passcode
     * would be readable off the dropped tablet with an NBT viewer — the exact
     * theft the lock exists to stop. Hashing keeps the honest-client promise
     * honest; it does not, and cannot, defeat a modified client that reads the
     * hub code itself out of the item.
     */
    public static String hash(String passcode) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(passcode.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM without SHA-256", impossible);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide()) {
            ScreenOpener.TABLET.run();
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String lock = lockOf(stack);
        if (!lock.isEmpty()) {
            // Deliberately silent about which hub: the tooltip on a dropped
            // tablet is exactly the leak the passcode exists to plug.
            lines.add(Component.literal("Locked — passcode to open").withStyle(ChatFormatting.GOLD));
        } else {
            lines.add(Component.literal("New — right-click to set its passcode")
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.literal("Reads your base from anywhere")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
