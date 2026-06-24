package com.claude.automata.block.entity;

import com.claude.automata.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Power Pylon — a "post" that beams power to other pylons it's linked to,
 * across any distance. A pylon next to your generators gathers their energy and
 * sends it to linked pylons elsewhere, which then power their adjacent machines.
 *
 * <p>Link with the Logistics Wrench: click one pylon to select it, then click
 * another to send power from the first to the second.
 */
public class PylonBlockEntity extends EnergyBlockEntity {
	private static final Map<UUID, BlockPos> SELECTION = new ConcurrentHashMap<>();

	private static final int MAX_ENERGY = 5000;
	private static final int GATHER_RATE = 200;
	private static final int SEND_RATE = 200;

	private final List<BlockPos> links = new ArrayList<>();

	public PylonBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.PYLON.get(), pos, state);
	}

	@Override
	protected int maxEnergy() {
		return MAX_ENERGY;
	}

	public static void select(UUID player, BlockPos pos) {
		SELECTION.put(player, pos.immutable());
	}

	public static BlockPos getSelection(UUID player) {
		return SELECTION.get(player);
	}

	public static void deselect(UUID player) {
		SELECTION.remove(player);
	}

	public int addLink(BlockPos pos) {
		BlockPos p = pos.immutable();
		if (!p.equals(this.worldPosition) && !links.contains(p)) {
			links.add(p);
			setChanged();
		}
		return links.size();
	}

	public void clearLinks() {
		links.clear();
		setChanged();
	}

	public int linkCount() {
		return links.size();
	}

	/** Accept up to {@code amount} energy; returns how much was taken. */
	public int receiveEnergy(int amount) {
		int room = maxEnergy() - energy;
		int accepted = Math.min(amount, room);
		if (accepted > 0) {
			energy += accepted;
			setChanged();
		}
		return accepted;
	}

	@Override
	public void tick(Level world, BlockPos pos, BlockState state) {
		// Gather from adjacent sources.
		if (energy <= MAX_ENERGY - GATHER_RATE && MachinePower.draw(world, pos, GATHER_RATE)) {
			addEnergy(GATHER_RATE);
		}

		// Beam to linked pylons.
		if (energy > 0 && !links.isEmpty()) {
			for (BlockPos link : links) {
				if (energy <= 0) {
					break;
				}
				if (world.getBlockEntity(link) instanceof PylonBlockEntity target) {
					int sent = target.receiveEnergy(Math.min(SEND_RATE, energy));
					if (sent > 0) {
						energy -= sent;
						setChanged();
					}
				}
			}
		}
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		long[] packed = new long[links.size()];
		for (int i = 0; i < links.size(); i++) {
			packed[i] = links.get(i).asLong();
		}
		nbt.putLongArray("Links", packed);
	}

	@Override
	public void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		links.clear();
		for (long packed : nbt.getLongArray("Links")) {
			links.add(BlockPos.of(packed));
		}
	}
}
