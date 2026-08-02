package com.jrpetty.aztecabyss.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A weighted list of things a machine can hand out.
 *
 * <p>The Box shipped with thirteen items hard-coded into a Java array, and that
 * array was the ceiling on what a Box could ever be. A map set in a flooded dock
 * wants tridents and nothing else; a map about running out of ammunition wants
 * arrows and bandages; a low-tech map does not want a netherite sword to exist.
 * None of that was expressible, and all of it is the sort of thing that decides
 * what a map <em>feels</em> like far more than the round curve does.
 *
 * <p>This is deliberately one mechanism rather than a Box feature. The same shape
 * answers the Box, supply caches, what you spawn holding, and which power-ups can
 * drop - all of which were separate hard-coded lists solving the same problem
 * badly. An author who learns the syntax once can retune every one of them.
 *
 * <pre>
 * "pools": {
 *   "box": [
 *     { "id": "minecraft:trident", "weight": 1 },
 *     { "id": "minecraft:crossbow", "weight": 4, "enchant": 2 },
 *     { "id": "minecraft:arrow", "weight": 6, "count": "16-32" }
 *   ]
 * }
 * </pre>
 *
 * <p>Weights are relative and need not add to anything. A count may be a single
 * number or a {@code low-high} range. {@code enchant} is a vanilla enchanting
 * level applied on the way out, which is the cheapest way to make a Box that gets
 * better later without writing an item table per round.
 */
public final class ItemPool {

    /** One possible result. */
    public record Slot(Item item, int weight, int minCount, int maxCount, int enchant) {
    }

    private final String id;
    private final List<Slot> slots;

    public ItemPool(String id, List<Slot> slots) {
        this.id = id;
        this.slots = List.copyOf(slots);
    }

    public String id() {
        return id;
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    public int size() {
        return slots.size();
    }

    /**
     * Draws one stack.
     *
     * <p>Never returns null and never returns nothing. A pool that has somehow
     * ended up empty gives a stone sword rather than a silent no-op, because a Box
     * that takes your money and produces nothing reads as theft, and an author
     * watching it happen has no way to tell a bad pool from a bug.
     */
    public ItemStack roll(RandomSource rng, net.minecraft.world.level.Level level) {
        if (slots.isEmpty()) {
            return new ItemStack(Items.STONE_SWORD);
        }
        int total = 0;
        for (Slot s : slots) {
            total += Math.max(1, s.weight());
        }
        int pick = rng.nextInt(total);
        Slot chosen = slots.get(0);
        for (Slot s : slots) {
            pick -= Math.max(1, s.weight());
            if (pick < 0) {
                chosen = s;
                break;
            }
        }
        int count = chosen.minCount() >= chosen.maxCount()
                ? chosen.minCount()
                : chosen.minCount() + rng.nextInt(chosen.maxCount() - chosen.minCount() + 1);
        ItemStack stack = new ItemStack(chosen.item(), Math.max(1, Math.min(64, count)));
        if (chosen.enchant() > 0 && level != null) {
            try {
                stack = net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(
                        rng, stack, chosen.enchant(),
                        level.registryAccess(),
                        java.util.Optional.empty());
            } catch (RuntimeException ignored) {
                // An item nothing can enchant comes out plain rather than failing.
            }
        }
        return stack;
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /** Reads a pool from a ruleset's {@code pools} block. */
    public static ItemPool fromJson(String id, JsonArray array) {
        List<Slot> slots = new ArrayList<>();
        for (JsonElement el : array) {
            Slot slot = slotOf(el);
            if (slot != null) {
                slots.add(slot);
            }
        }
        return new ItemPool(id, slots);
    }

    private static Slot slotOf(JsonElement el) {
        // A bare string is the common case and should not need an object around it.
        if (el.isJsonPrimitive()) {
            Item item = itemOf(el.getAsString());
            return item == null ? null : new Slot(item, 1, 1, 1, 0);
        }
        if (!el.isJsonObject()) {
            return null;
        }
        JsonObject o = el.getAsJsonObject();
        Item item = itemOf(str(o, "id", ""));
        if (item == null) {
            return null;
        }
        int weight = Math.max(1, num(o, "weight", 1));
        int enchant = Math.max(0, num(o, "enchant", 0));
        int min = 1;
        int max = 1;
        if (o.has("count")) {
            try {
                String raw = o.get("count").getAsString().trim();
                int dash = raw.indexOf('-');
                if (dash > 0) {
                    min = Integer.parseInt(raw.substring(0, dash).trim());
                    max = Integer.parseInt(raw.substring(dash + 1).trim());
                } else {
                    min = max = Integer.parseInt(raw);
                }
            } catch (RuntimeException ignored) {
                min = max = 1;
            }
        }
        return new Slot(item, weight, Math.max(1, min), Math.max(min, max), enchant);
    }

    /**
     * Reads a pool written on a marker: {@code items=trident,crossbow,arrow}.
     *
     * <p>The inline form exists because most Boxes are one Box in one map and
     * making an author open a datapack to change what one machine gives out is a
     * tax on the quick change. Comma-separated ids, optionally {@code id*weight}.
     */
    public static ItemPool inline(String raw) {
        List<Slot> slots = new ArrayList<>();
        for (String token : raw.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            int weight = 1;
            int star = t.indexOf('*');
            if (star > 0) {
                try {
                    weight = Math.max(1, Integer.parseInt(t.substring(star + 1).trim()));
                } catch (NumberFormatException ignored) {
                    weight = 1;
                }
                t = t.substring(0, star).trim();
            }
            Item item = itemOf(t);
            if (item != null) {
                slots.add(new Slot(item, weight, 1, 1, 0));
            }
        }
        return new ItemPool("inline", slots);
    }

    /** Resolves an item id, tolerating a bare name with no namespace. */
    public static Item itemOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(
                raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_'));
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? null : item;
    }

    private static String str(JsonObject o, String key, String fallback) {
        try {
            return o.has(key) ? o.get(key).getAsString() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int num(JsonObject o, String key, int fallback) {
        try {
            return o.has(key) ? o.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** The Box's contents when nobody has said otherwise. */
    public static ItemPool defaultBox() {
        return new ItemPool("box", List.of(
                new Slot(Items.IRON_SWORD, 6, 1, 1, 0),
                new Slot(Items.IRON_AXE, 4, 1, 1, 0),
                new Slot(Items.DIAMOND_SWORD, 3, 1, 1, 0),
                new Slot(Items.DIAMOND_AXE, 2, 1, 1, 0),
                new Slot(Items.BOW, 5, 1, 1, 1),
                new Slot(Items.CROSSBOW, 4, 1, 1, 1),
                new Slot(Items.SHIELD, 3, 1, 1, 0),
                new Slot(Items.TRIDENT, 1, 1, 1, 0),
                new Slot(Items.NETHERITE_SWORD, 1, 1, 1, 0),
                new Slot(Items.IRON_CHESTPLATE, 3, 1, 1, 0),
                new Slot(Items.DIAMOND_CHESTPLATE, 2, 1, 1, 0)));
    }

    /** A supply cache at a given tier when nobody has said otherwise. */
    public static ItemPool defaultLoot(int tier) {
        int t = Math.max(1, Math.min(3, tier));
        return new ItemPool("loot_" + t, List.of(
                new Slot(Items.COOKED_BEEF, 4, 4 * t, 6 * t, 0),
                new Slot(Items.ARROW, 3, 16 * t, 24 * t, 0),
                new Slot(Items.IRON_INGOT, 2, 2 * t, 3 * t, 0)));
    }
}
