package com.jrpetty.aztecabyss.registry;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own items: the Marker block's item form, and the two relic
 * weapons - the Temple's Obsidian Edge and the maze's Griever Fang - with the
 * proof materials each is built from. (The Bridge's reward is not an item at all -
 * see {@link com.jrpetty.aztecabyss.item.HeartCore}.)
 *
 * <p>Every relic is crafted, and every recipe needs a material that only
 * comes out of a finished run. That is the gate: not the recipe book, which
 * is a hint rather than a lock, but the stuff itself.
 *
 * <p>The portal frame is still vanilla blocks and the portal surface still has
 * no item form; nothing here is scenery.
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(AztecAbyssConstants.MOD_ID);

    /**
     * The Marker Block's item form.
     *
     * <p>Registered rather than handed out as a data-component-carrying vanilla
     * item, which is how the wand and the old marker signs work. That trick stops
     * being available the moment the thing you are placing is a block of your own:
     * a block needs an item that places it.
     */
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> MARKER =
            ITEMS.registerSimpleBlockItem("marker", ModBlocks.MARKER);

    /**
     * Obsidian off the altar itself, and the only stone the Edge can be built
     * from.
     *
     * <p>This is what makes the weapon exclusive without taking the crafting
     * away: the recipe is the same nine squares it always was, but ordinary
     * obsidian will not do. Seven of these come out of the Temple with anyone
     * who clears its last round, which is exactly one blade's worth.
     */
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.Item> ALTAR_OBSIDIAN =
            ITEMS.register("altar_obsidian", id -> new net.minecraft.world.item.Item(
                    new net.minecraft.world.item.Item.Properties().fireResistant()));

    /**
     * A shard of the portal you came out through.
     *
     * <p>Granted for escaping the maze and nothing else - not for surviving to
     * the deadline, not for dying well. The Fang is bound to it, so the
     * weapon cannot exist in the hands of anybody who did not get out.
     */
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.Item> WAY_OUT =
            ITEMS.register("way_out", id -> new net.minecraft.world.item.Item(
                    new net.minecraft.world.item.Item.Properties()));

    /**
     * The macuahuitl's stone: obsidian teeth in a wooden shaft.
     *
     * <p>Brutal and slow rather than sharp and quick - the swing is a third
     * slower than a sword's, and it hits like netherite. It wears out sooner
     * than steel would, because volcanic glass shatters, and it is mended with
     * more obsidian. Fireproof, since it came out of lava to begin with.
     */
    public static final net.minecraft.world.item.Tier OBSIDIAN_TIER =
            new com.jrpetty.aztecabyss.item.RelicTier(
                    720, 6.0F, 4.0F, 12,
                    () -> net.minecraft.world.item.crafting.Ingredient.of(
                            net.minecraft.world.item.Items.OBSIDIAN));

    /**
     * The Obsidian Edge, carried out of the Temple.
     *
     * <p>The Aztecs fought with a blade of set obsidian teeth that opened
     * armour steel would skate off, and that is exactly what this does - see
     * {@link com.jrpetty.aztecabyss.item.ObsidianEdge}.
     */
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.Item> OBSIDIAN_EDGE =
            ITEMS.register("obsidian_edge", id -> new com.jrpetty.aztecabyss.item.RelicSword(
                    OBSIDIAN_TIER,
                    new net.minecraft.world.item.Item.Properties()
                            .fireResistant()
                            .attributes(net.minecraft.world.item.SwordItem.createAttributes(
                                    OBSIDIAN_TIER, 3, -2.8F)),
                    "§7Ignores §f70%§7 of armour",
                    "§8Teeth of volcanic glass, set in wood.",
                    "§8Carried out of the Temple."));

    /**
     * A Griever's barb, left behind when one is put down.
     *
     * <p>The Fang cannot be built from these - it cannot be built at all, it
     * is handed to people who got out - but it is what the Fang is mended
     * with, so keeping one in the field costs you Grievers.
     */
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.Item> GRIEVER_STINGER =
            ITEMS.register("griever_stinger", id -> new net.minecraft.world.item.Item(
                    new net.minecraft.world.item.Item.Properties()));

    /** Chitin does not hold an edge the way steel does; it makes up for it. */
    public static final net.minecraft.world.item.Tier CHITIN_TIER =
            new com.jrpetty.aztecabyss.item.RelicTier(
                    500, 7.0F, 2.0F, 16,
                    () -> net.minecraft.world.item.crafting.Ingredient.of(
                            GRIEVER_STINGER.get()));

    /**
     * The Griever Fang: the barb, bound to a grip.
     *
     * <p>The Temple's weapon is heavy and slow and opens armour. This is the
     * opposite number in every respect - light, quick, and it poisons - which
     * is what the maze is about: you are not there to win a fight, you are
     * there to make something stop chasing you and get out.
     */
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.Item> GRIEVER_FANG =
            ITEMS.register("griever_fang", id -> new com.jrpetty.aztecabyss.item.RelicSword(
                    CHITIN_TIER,
                    new net.minecraft.world.item.Item.Properties()
                            .attributes(net.minecraft.world.item.SwordItem.createAttributes(
                                    CHITIN_TIER, 3, -1.8F)),
                    "§2Envenoms on every hit",
                    "§8Cut from the thing that hunted you.",
                    "§8Quick where the Edge is heavy.",
                    "§8Carried out of the maze."));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
