package com.jrpetty.aztecabyss.registry;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * No custom items - the portal frame is vanilla diamond/iron blocks and the
 * portal surface has no item form. Kept as an (empty) registry so the wiring
 * stays consistent if items are added later.
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
     * The macuahuitl's stone: obsidian teeth in a wooden shaft.
     *
     * <p>Brutal and slow rather than sharp and quick - the swing is a third
     * slower than a sword's, and it hits like netherite. It wears out sooner
     * than steel would, because volcanic glass shatters, and it is mended with
     * more obsidian. Fireproof, since it came out of lava to begin with.
     */
    public static final net.minecraft.world.item.Tier OBSIDIAN_TIER =
            new net.minecraft.world.item.SimpleTier(
                    net.minecraft.tags.BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
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
            ITEMS.register("obsidian_edge", id -> new net.minecraft.world.item.SwordItem(
                    OBSIDIAN_TIER,
                    new net.minecraft.world.item.Item.Properties()
                            .fireResistant()
                            .attributes(net.minecraft.world.item.SwordItem.createAttributes(
                                    OBSIDIAN_TIER, 3, -2.8F))));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
