package com.jrpetty.mobtrumps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * A card display doesn't hold a physical card — it PROJECTS one straight from
 * its owner's collection. The card item never leaves the owner's inventory, so
 * there is nothing to steal: breaking the block or clicking it as another
 * player can't take anything. Only the owner can swap or clear the projection.
 */
public class CardDisplayBlockEntity extends BlockEntity {

    private String mobId = "";
    private boolean foil = false;
    private UUID owner = null;
    private String ownerName = "";

    public CardDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CARD_DISPLAY_BE.get(), pos, state);
    }

    protected CardDisplayBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                     BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean hasCard() {
        return !mobId.isEmpty();
    }

    public String getMobId() {
        return mobId;
    }

    public boolean isFoil() {
        return foil;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public boolean isOwner(UUID player) {
        return owner != null && owner.equals(player);
    }

    /** Anyone may claim an empty or ownerless display; otherwise owner only. */
    public boolean canEdit(UUID player) {
        return owner == null || owner.equals(player);
    }

    public void setProjection(String mobId, boolean foil, UUID owner, String ownerName) {
        this.mobId = mobId == null ? "" : mobId;
        this.foil = foil;
        this.owner = owner;
        this.ownerName = ownerName == null ? "" : ownerName;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public void clearProjection() {
        setProjection("", false, null, "");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("MobId", mobId);
        tag.putBoolean("Foil", foil);
        tag.putString("OwnerName", ownerName);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mobId = tag.getString("MobId");
        foil = tag.getBoolean("Foil");
        ownerName = tag.getString("OwnerName");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        // migrate a legacy display that held a physical card item
        if (mobId.isEmpty() && tag.contains("Card")) {
            ItemStack legacy = ItemStack.parseOptional(registries, tag.getCompound("Card"));
            var card = MobCardItem.cardOf(legacy);
            if (card != null) {
                mobId = card.id();
                foil = MobCardItem.isFoilCard(legacy);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
