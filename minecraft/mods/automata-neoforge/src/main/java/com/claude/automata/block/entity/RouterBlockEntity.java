package com.claude.automata.block.entity;

import com.claude.automata.logistics.InventoryTransfer;
import com.claude.automata.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Logistics Router — pulls items from an adjacent inventory and sends them
 * to linked destination inventories anywhere in the world, with an optional
 * item filter and round-robin balancing. Slow by hand, faster with power.
 *
 * <p>Linking is done with the Logistics Wrench (see {@code Automata}'s
 * UseBlockCallback): select a router, then click destination inventories.
 */
public class RouterBlockEntity extends BlockEntity {
	/** Per-player "currently selected router" for the wrench-linking workflow. */
	private static final Map<UUID, BlockPos> SELECTION = new ConcurrentHashMap<>();

	private static final int ENERGY_PER_OP = 10;
	private static final int INTERVAL_POWERED = 4;
	private static final int INTERVAL_UNPOWERED = 20;
	private static final int BATCH_POWERED = 16;
	private static final int BATCH_UNPOWERED = 4;

	private final List<BlockPos> destinations = new ArrayList<>();
	private final Set<Item> filter = new LinkedHashSet<>();
	private int roundRobin = 0;
	private int timer = 0;

	public RouterBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ROUTER.get(), pos, state);
	}

	public static void select(UUID player, BlockPos routerPos) {
		SELECTION.put(player, routerPos.immutable());
	}

	public static BlockPos getSelection(UUID player) {
		return SELECTION.get(player);
	}

	/** Add a destination (deduplicated). Returns the new destination count. */
	public int addDestination(BlockPos pos) {
		BlockPos p = pos.immutable();
		if (!destinations.contains(p)) {
			destinations.add(p);
			setChanged();
		}
		return destinations.size();
	}

	public int destinationCount() {
		return destinations.size();
	}

	/** Toggle an item in the whitelist filter (empty filter = pass everything). */
	public boolean toggleFilter(Item item) {
		boolean added;
		if (filter.contains(item)) {
			filter.remove(item);
			added = false;
		} else {
			filter.add(item);
			added = true;
		}
		setChanged();
		return added;
	}

	public void clearLinks() {
		destinations.clear();
		filter.clear();
		setChanged();
	}

	public int filterCount() {
		return filter.size();
	}

	private boolean accepts(ItemStack stack) {
		return filter.isEmpty() || filter.contains(stack.getItem());
	}

	public void tick(Level world, BlockPos pos, BlockState state) {
		if (destinations.isEmpty()) {
			return;
		}
		boolean powered = MachinePower.draw(world, pos, ENERGY_PER_OP);
		int interval = powered ? INTERVAL_POWERED : INTERVAL_UNPOWERED;
		if (++timer < interval) {
			return;
		}
		timer = 0;
		int batch = powered ? BATCH_POWERED : BATCH_UNPOWERED;

		// Resolve destination inventories (skip any that aren't loaded / present).
		Container[] dests = new Container[destinations.size()];
		for (int i = 0; i < destinations.size(); i++) {
			dests[i] = InventoryTransfer.inventoryAt(world, destinations.get(i));
		}

		// Find an adjacent source inventory that isn't a router or a destination.
		for (Direction dir : Direction.values()) {
			BlockPos sourcePos = pos.relative(dir);
			if (destinations.contains(sourcePos)) {
				continue;
			}
			if (world.getBlockEntity(sourcePos) instanceof RouterBlockEntity) {
				continue;
			}
			Container source = InventoryTransfer.inventoryAt(world, sourcePos);
			if (source == null) {
				continue;
			}
			int moved = InventoryTransfer.pullAndDistribute(
					source, dir.getOpposite(), this::accepts, batch, dests, roundRobin);
			if (moved > 0) {
				roundRobin = (roundRobin + 1) % dests.length;
				setChanged();
				return;
			}
		}
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		long[] dest = new long[destinations.size()];
		for (int i = 0; i < destinations.size(); i++) {
			dest[i] = destinations.get(i).asLong();
		}
		nbt.putLongArray("Destinations", dest);

		ListTag filterList = new ListTag();
		for (Item item : filter) {
			filterList.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(item).toString()));
		}
		nbt.put("Filter", filterList);
		nbt.putInt("RoundRobin", roundRobin);
	}

	@Override
	public void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		destinations.clear();
		for (long packed : nbt.getLongArray("Destinations")) {
			destinations.add(BlockPos.of(packed));
		}
		filter.clear();
		ListTag filterList = nbt.getList("Filter", Tag.TAG_STRING);
		for (int i = 0; i < filterList.size(); i++) {
			ResourceLocation id = ResourceLocation.tryParse(filterList.getString(i));
			if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
				filter.add(BuiltInRegistries.ITEM.get(id));
			}
		}
		roundRobin = nbt.getInt("RoundRobin");
	}
}
