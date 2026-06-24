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
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

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
		super(ModBlockEntities.ROUTER, pos, state);
	}

	public static void select(UUID player, BlockPos routerPos) {
		SELECTION.put(player, routerPos.toImmutable());
	}

	public static BlockPos getSelection(UUID player) {
		return SELECTION.get(player);
	}

	/** Add a destination (deduplicated). Returns the new destination count. */
	public int addDestination(BlockPos pos) {
		BlockPos p = pos.toImmutable();
		if (!destinations.contains(p)) {
			destinations.add(p);
			markDirty();
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
		markDirty();
		return added;
	}

	public void clearLinks() {
		destinations.clear();
		filter.clear();
		markDirty();
	}

	public int filterCount() {
		return filter.size();
	}

	private boolean accepts(ItemStack stack) {
		return filter.isEmpty() || filter.contains(stack.getItem());
	}

	public void tick(World world, BlockPos pos, BlockState state) {
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
		Inventory[] dests = new Inventory[destinations.size()];
		for (int i = 0; i < destinations.size(); i++) {
			dests[i] = InventoryTransfer.inventoryAt(world, destinations.get(i));
		}

		// Find an adjacent source inventory that isn't a router or a destination.
		for (Direction dir : Direction.values()) {
			BlockPos sourcePos = pos.offset(dir);
			if (destinations.contains(sourcePos)) {
				continue;
			}
			if (world.getBlockEntity(sourcePos) instanceof RouterBlockEntity) {
				continue;
			}
			Inventory source = InventoryTransfer.inventoryAt(world, sourcePos);
			if (source == null) {
				continue;
			}
			int moved = InventoryTransfer.pullAndDistribute(
					source, dir.getOpposite(), this::accepts, batch, dests, roundRobin);
			if (moved > 0) {
				roundRobin = (roundRobin + 1) % dests.length;
				markDirty();
				return;
			}
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		long[] dest = new long[destinations.size()];
		for (int i = 0; i < destinations.size(); i++) {
			dest[i] = destinations.get(i).asLong();
		}
		nbt.putLongArray("Destinations", dest);

		NbtList filterList = new NbtList();
		for (Item item : filter) {
			filterList.add(NbtString.of(Registries.ITEM.getId(item).toString()));
		}
		nbt.put("Filter", filterList);
		nbt.putInt("RoundRobin", roundRobin);
	}

	@Override
	public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		destinations.clear();
		for (long packed : nbt.getLongArray("Destinations")) {
			destinations.add(BlockPos.fromLong(packed));
		}
		filter.clear();
		NbtList filterList = nbt.getList("Filter", NbtElement.STRING_TYPE);
		for (int i = 0; i < filterList.size(); i++) {
			Identifier id = Identifier.tryParse(filterList.getString(i));
			if (id != null && Registries.ITEM.containsId(id)) {
				filter.add(Registries.ITEM.get(id));
			}
		}
		roundRobin = nbt.getInt("RoundRobin");
	}
}
