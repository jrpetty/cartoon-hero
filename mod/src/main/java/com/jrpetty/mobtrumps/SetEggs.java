package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Category;
import com.jrpetty.mobtrumps.game.MobCategories;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The one spawn egg a completed set is worth. Finishing a category lets the
 * player pick a single mob from it and walk away with that mob's spawn egg —
 * once per set, forever.
 *
 * <p>Not every mob has a vanilla spawn egg (the Ender Dragon, the Wither, the
 * golems), so the choices are resolved against the item registry rather than
 * hard-coded: whatever really exists is offered, and a set with no eggs at all
 * simply doesn't grant this reward.
 */
public final class SetEggs {

    private SetEggs() {
    }

    /** The spawn egg item for a mob id, or null if the game has none. */
    public static Item eggFor(String mobId) {
        if (mobId == null || mobId.isEmpty()) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.withDefaultNamespace(mobId + "_spawn_egg"))
                .orElse(null);
        return item == null || item == Items.AIR ? null : item;
    }

    /** Every mob in a set that actually has a spawn egg, in display order. */
    public static List<String> choices(Category category) {
        List<String> out = new ArrayList<>();
        for (String id : MobCategories.members(category)) {
            if (eggFor(id) != null) {
                out.add(id);
            }
        }
        return out;
    }

    /** Whether finishing this set is worth a spawn egg at all. */
    public static boolean hasAny(Category category) {
        return !choices(category).isEmpty();
    }

    /** Parse a Category from its enum name, or null. */
    public static Category category(String name) {
        if (name == null) {
            return null;
        }
        for (Category c : Category.values()) {
            if (c.name().equals(name)) {
                return c;
            }
        }
        return null;
    }
}
