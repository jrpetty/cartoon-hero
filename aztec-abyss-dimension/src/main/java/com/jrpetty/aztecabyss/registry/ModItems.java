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

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
