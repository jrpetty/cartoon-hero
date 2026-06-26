package dev.structint.world;

import dev.structint.StructuralIntegrityMod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Registration of the chunk data attachment that tracks player-managed structural blocks. */
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, StructuralIntegrityMod.MODID);

    public static final Supplier<AttachmentType<ManagedBlocks>> MANAGED_BLOCKS =
            ATTACHMENT_TYPES.register("managed_blocks", () ->
                    AttachmentType.builder(ManagedBlocks::new)
                            .serialize(ManagedBlocks.CODEC)
                            .build());

    private ModAttachments() {
    }
}
