package com.jrpetty.zombierealm;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Blocks for the Zombie Dimension. */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ZombieRealm.MODID);

    /** A full-bright green lamp — the light you carry into the pitch-dark realm. */
    public static final DeferredBlock<Block> CURSED_LAMP = BLOCKS.register("cursed_lamp",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(0.6F)
                    .lightLevel(state -> 15)
                    .sound(SoundType.GLASS)));

    public static final DeferredItem<BlockItem> CURSED_LAMP_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("cursed_lamp", CURSED_LAMP);

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }

    private ModBlocks() {
    }
}
