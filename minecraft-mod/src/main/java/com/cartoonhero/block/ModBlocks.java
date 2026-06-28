package com.cartoonhero.block;

import com.cartoonhero.CartoonHero;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * Holds and registers every block this mod adds (and the matching item form).
 */
public class ModBlocks {

    public static final Block CARTOON_BRICKS = registerBlock(
            "cartoon_bricks",
            new Block(AbstractBlock.Settings.create()
                    .strength(1.5F, 6.0F)
                    .luminance(state -> 7)            // softly glows
                    .requiresTool()
                    .sounds(BlockSoundGroup.GLASS)));

    private static Block registerBlock(String name, Block block) {
        Identifier id = Identifier.of(CartoonHero.MOD_ID, name);
        Block registered = Registry.register(Registries.BLOCK, id, block);
        // Every placeable block needs a matching item so it shows up in inventories.
        Registry.register(Registries.ITEM, id, new BlockItem(registered, new Item.Settings()));
        return registered;
    }

    public static void registerModBlocks() {
        CartoonHero.LOGGER.info("Registering blocks for {}", CartoonHero.MOD_ID);
    }
}
