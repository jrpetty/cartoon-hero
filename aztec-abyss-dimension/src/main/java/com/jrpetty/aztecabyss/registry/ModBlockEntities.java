package com.jrpetty.aztecabyss.registry;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.block.MarkerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block entities. There is one, and it exists because a marker's text has to
 * travel inside the map file.
 *
 * <p>Structure NBT carries block entities and nothing else that is per-position,
 * so anything a marker knows has to live in one. Storing marker text in a level
 * attachment or a side file would be simpler to write and would quietly break the
 * property the whole engine rests on: that the {@code .nbt} someone sends you
 * <em>is</em> the map, shops and instructions included.
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AztecAbyssConstants.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MarkerBlockEntity>> MARKER =
            BLOCK_ENTITIES.register("marker",
                    () -> BlockEntityType.Builder.of(MarkerBlockEntity::new,
                            ModBlocks.MARKER.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
