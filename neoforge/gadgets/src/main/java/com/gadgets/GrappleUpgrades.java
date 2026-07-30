package com.gadgets;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * The Grappling Hook's enhancement system: which upgrades a hook carries, how
 * worn it is, and the bench recipes that apply them.
 *
 * <p>Everything lives in the stack's custom data, so an enhanced hook keeps its
 * upgrades when dropped, traded, or stashed in a chest. Each upgrade is applied
 * once — a fully kitted hook has all five.
 */
public final class GrappleUpgrades {
    /** How many uses a fresh hook has before it must be repaired. */
    public static final int MAX_USES = 100;

    /** One bench recipe: eight identical items ringing the hook. */
    public record Upgrade(String key, String name, String effect, Item material, int perSlot) {
        /** Total material a full application costs. */
        public int totalCost() {
            return perSlot * 8;
        }
    }

    public static final Upgrade RANGE = new Upgrade("range", "Long Line",
            "Doubles your grappling range", Items.STRING, 20);
    public static final Upgrade COOLDOWN = new Upgrade("cool", "Quick Winch",
            "Halves the cooldown between shots", Items.IRON_INGOT, 20);
    public static final Upgrade VELOCITY = new Upgrade("vel", "Spring Loaded",
            "Pulls you in 50% faster", Items.BLAZE_POWDER, 20);
    public static final Upgrade IMPACT = new Upgrade("aoe", "Impact Charge",
            "Shockwave throws mobs clear · up to 5 hearts", Items.GUNPOWDER, 20);
    public static final Upgrade LANDING = new Upgrade("land", "Soft Landing",
            "Drift down safely after a grapple", Items.PHANTOM_MEMBRANE, 20);

    public static final List<Upgrade> ALL = List.of(RANGE, COOLDOWN, VELOCITY, IMPACT, LANDING);

    /** Repair accepts either material, mixed freely, so scraps get used up. */
    public static final int REPAIR_PER_SLOT = 4;

    private GrappleUpgrades() {
    }

    // --- stack data ---

    private static CompoundTag read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    private static void write(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean has(ItemStack stack, Upgrade upgrade) {
        return read(stack).getBoolean(upgrade.key());
    }

    public static void apply(ItemStack stack, Upgrade upgrade) {
        CompoundTag tag = read(stack);
        tag.putBoolean(upgrade.key(), true);
        write(stack, tag);
    }

    public static List<Upgrade> installed(ItemStack stack) {
        List<Upgrade> out = new ArrayList<>();
        for (Upgrade u : ALL) {
            if (has(stack, u)) {
                out.add(u);
            }
        }
        return out;
    }

    // --- wear ---

    public static int wear(ItemStack stack) {
        return Math.max(0, Math.min(MAX_USES, read(stack).getInt("wear")));
    }

    public static int usesLeft(ItemStack stack) {
        return MAX_USES - wear(stack);
    }

    public static boolean isWornOut(ItemStack stack) {
        return wear(stack) >= MAX_USES;
    }

    /** Charge one use of wear; returns false when the hook is already spent. */
    public static boolean spendUse(ItemStack stack) {
        int w = wear(stack);
        if (w >= MAX_USES) {
            return false;
        }
        CompoundTag tag = read(stack);
        tag.putInt("wear", w + 1);
        write(stack, tag);
        return true;
    }

    public static void repair(ItemStack stack) {
        CompoundTag tag = read(stack);
        tag.putInt("wear", 0);
        write(stack, tag);
    }

    // --- effective stats, read by the hook itself ---

    public static double rangeMultiplier(ItemStack stack) {
        return has(stack, RANGE) ? 2.0 : 1.0;
    }

    public static double cooldownMultiplier(ItemStack stack) {
        return has(stack, COOLDOWN) ? 0.5 : 1.0;
    }

    public static double launchMultiplier(ItemStack stack) {
        return has(stack, VELOCITY) ? 1.5 : 1.0;
    }
}
