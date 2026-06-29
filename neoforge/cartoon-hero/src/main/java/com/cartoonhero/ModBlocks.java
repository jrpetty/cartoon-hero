package com.cartoonhero;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CartoonHero.MODID);

    public static final DeferredBlock<Block> CARTOON_BRICKS = BLOCKS.register("cartoon_bricks",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(1.5F, 6.0F)
                    .lightLevel(state -> 7)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.GLASS)));

    // Matching block item, registered into the item registry.
    public static final DeferredItem<BlockItem> CARTOON_BRICKS_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("cartoon_bricks", CARTOON_BRICKS);
}
