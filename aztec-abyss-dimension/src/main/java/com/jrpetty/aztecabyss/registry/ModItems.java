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

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
