package com.jrpetty.aztecabyss.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Marker Block: an instruction nailed to the map, that the map does not show.
 *
 * <p>Everything the engine reads from a sign, it reads from one of these, with
 * eight long lines instead of four short ones. The difference that matters to a
 * player is that it is <em>not there</em>: invisible, walk-through, and silent. A
 * finished map stops being covered in signs explaining itself to the engine.
 *
 * <p>That solved a problem the sign version could only trade around. Half the
 * marker kinds are notes to the engine and half are furniture a player reads, and
 * signs forced one decision for both: leave them up and every horde gate has a
 * label on it, or consume them on load and the author cannot see their own map
 * any more. An invisible block is honest about which it is - authors see markers
 * in creative, players never see them at all, and nothing has to be deleted for
 * the map to look right.
 *
 * <p>No collision, on purpose. It is an annotation, not geometry; an invisible
 * solid block is a wall people walk into and cannot explain.
 */
public class MarkerBlock extends BaseEntityBlock {

    public static final MapCodec<MarkerBlock> CODEC = simpleCodec(MarkerBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public MarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Faces the way the author was looking.
     *
     * <p>Several kinds read their direction from the block - a horde marker's
     * facing is which way the mobs walk in - so the placement has to capture it.
     * Looking at where you want them to go and placing the marker is the whole
     * interaction, and it is the same one signs had.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarkerBlockEntity(pos, state);
    }

    /**
     * Shows itself to whoever is building, and to nobody else.
     *
     * <p>An invisible block an author cannot find is not a better sign, it is a
     * lost marker - so a Marker Block draws a small ember for anyone in creative
     * within a room's distance. Particles rather than a block-entity renderer
     * because a renderer is a client class, a registration and a model to get
     * wrong, and this needs to be seen from across a room rather than read.
     *
     * <p>Once a second, and only when someone in creative is actually near, so a
     * finished map full of markers costs a survival server nothing at all.
     */
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if ((lvl.getGameTime() + pos.hashCode()) % 20L != 0L
                    || !(lvl instanceof net.minecraft.server.level.ServerLevel server)) {
                return;
            }
            for (net.minecraft.server.level.ServerPlayer p : server.players()) {
                if (!p.isCreative() || p.blockPosition().distSqr(pos) > 32 * 32) {
                    continue;
                }
                server.sendParticles(p, net.minecraft.core.particles.ParticleTypes.END_ROD,
                        true, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        2, 0.12, 0.12, 0.12, 0.0);
            }
        };
    }

    /**
     * Invisible, and drawn by the client only for someone in creative.
     *
     * <p>{@code INVISIBLE} rather than a transparent texture, so it costs nothing
     * to render and cannot be spotted by a player looking for a seam in a wall.
     */
    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    /**
     * Kinds a player has to be able to click during a run.
     *
     * <p>This is the line between an instruction and a machine. A {@code [Horde]}
     * marker is a note to the engine and should be as absent to a player as the
     * air it looks like; a {@code [Dealer]} is a shop they have to be able to buy
     * from. Both are invisible, and only one of them is there.
     *
     * <p>Without this split an invisible dealer was unusable - the outline existed
     * only in creative, so in survival the click went through it into the wall
     * behind. Giving <em>every</em> marker an outline instead would fill a map
     * with invisible things swallowing clicks meant for the scenery, which is
     * worse than the signs this replaced.
     */
    private static final java.util.Set<String> INTERACTIVE = java.util.Set.of(
            "dealer", "box", "perk", "upgrade", "loot", "door", "trap", "objective");

    public static boolean isInteractive(String kind) {
        return INTERACTIVE.contains(kind.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Solid to an author; solid to a player only if there is something to buy.
     *
     * <p>A marker must be selectable in creative or it cannot be edited once
     * placed - {@code /arena look} and {@code /arena set} both work on whatever you
     * are pointing at.
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        if (context instanceof net.minecraft.world.phys.shapes.EntityCollisionContext ec
                && ec.getEntity() instanceof net.minecraft.world.entity.player.Player p
                && p.isCreative()) {
            return Shapes.block();
        }
        if (level.getBlockEntity(pos) instanceof MarkerBlockEntity be
                && isInteractive(be.kind())) {
            return Shapes.block();
        }
        return Shapes.empty();
    }
}
