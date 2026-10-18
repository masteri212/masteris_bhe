package com.masteri.bhe.multiblock;

import com.masteri.bhe.block.FacingBlock;
import com.masteri.bhe.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The reactor layout, relative to the Reactor Core (see the design doc).
 *
 * Containment ring: flat torus around the core, 21 blocks across, tube radius 2 (5 layers, core on the middle layer),
 * 11-block hole. 3x3 Ring Beam Port tunnels through the middle three layers facing N, S, E, W and NE.
 *
 * Stabilizers: 5x5x7 towers centered 16 blocks out on each axis, bottom layer level with the ring's bottom layer.
 * Emitter on layer 3 (core height) in the middle of the core-facing side, a ring of 16 coils on layer 6,
 * and the mass input port in the center of layer 7.
 */
public final class ReactorStructure {
    public static final int RING_MAJOR_RADIUS = 8;
    public static final double RING_TUBE_RADIUS = 2.5; // 2-block tube, voxelized around block centers
    public static final int STABILIZER_DISTANCE = 16;

    /**
     * One block of the structure. {@code facing} is only used by facing blocks such as the emitter.
     * {@code alternative} is another block allowed in the same spot (Reactor Ports can replace Ring Frame).
     */
    public record Part(BlockPos offset, Supplier<? extends Block> block, Direction facing, Supplier<? extends Block> alternative) {
        public Part(BlockPos offset, Supplier<? extends Block> block, Direction facing) {
            this(offset, block, facing, null);
        }

        public boolean matches(BlockState state) {
            return state.is(block.get()) || (alternative != null && state.is(alternative.get()));
        }

        public BlockState placementState() {
            BlockState state = block.get().defaultBlockState();
            if (facing != null && state.hasProperty(FacingBlock.FACING)) {
                state = state.setValue(FacingBlock.FACING, facing);
            }
            return state;
        }
    }

    /** Result of a structure check. Missing positions are absolute world positions. */
    public record Result(int total, int placed, Map<Block, Integer> missingByBlock, List<BlockPos> missing,
                         List<BlockPos> reactorPorts) {
        public boolean complete() {
            return placed == total;
        }
    }

    private static List<Part> layout;

    public static List<Part> layout() {
        if (layout == null) layout = Collections.unmodifiableList(build());
        return layout;
    }

    private static List<Part> build() {
        List<Part> parts = new ArrayList<>();

        // Containment ring
        int reach = RING_MAJOR_RADIUS + (int) Math.ceil(RING_TUBE_RADIUS);
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz) - RING_MAJOR_RADIUS;
                    if (d * d + dy * dy > RING_TUBE_RADIUS * RING_TUBE_RADIUS) continue;
                    boolean port = Math.abs(dy) <= 1 && (Math.abs(dx) <= 1 || Math.abs(dz) <= 1
                            || (dx > 0 && dz < 0 && Math.abs(dx + dz) <= 1)); // NE diagonal for the bending laser
                    parts.add(port
                            ? new Part(new BlockPos(dx, dy, dz), ModBlocks.RING_BEAM_PORT, null)
                            : new Part(new BlockPos(dx, dy, dz), ModBlocks.RING_FRAME, null, ModBlocks.REACTOR_PORT));
                }
            }
        }

        // Four stabilizers
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            Direction side = dir.getClockWise();
            for (int a = -2; a <= 2; a++) {          // along the axis, -2 is the side facing the core
                for (int b = -2; b <= 2; b++) {      // across the axis
                    for (int dy = -2; dy <= 4; dy++) {
                        BlockPos offset = BlockPos.ZERO.relative(dir, STABILIZER_DISTANCE + a).relative(side, b).above(dy);
                        Supplier<? extends Block> block = ModBlocks.STABILIZER_CASING;
                        Direction facing = null;
                        if (dy == 0 && b == 0 && a == -2) {
                            block = ModBlocks.STABILIZER_EMITTER;
                            facing = dir.getOpposite(); // front faces the core
                        } else if (dy == 4 && a == 0 && b == 0) {
                            block = ModBlocks.MASS_INPUT_PORT;
                        } else if (dy == 3 && (Math.abs(a) == 2 || Math.abs(b) == 2)) {
                            block = ModBlocks.STABILIZER_COIL;
                        }
                        parts.add(new Part(offset, block, facing));
                    }
                }
            }
        }
        return parts;
    }

    public static Result check(Level level, BlockPos core) {
        int placed = 0;
        Map<Block, Integer> missingByBlock = new LinkedHashMap<>();
        List<BlockPos> missing = new ArrayList<>();
        List<BlockPos> ports = new ArrayList<>();
        for (Part part : layout()) {
            BlockPos pos = core.offset(part.offset());
            Block expected = part.block().get();
            BlockState state = level.getBlockState(pos);
            if (part.matches(state)) {
                placed++;
                if (state.is(ModBlocks.REACTOR_PORT.get())) ports.add(pos);
            } else {
                missingByBlock.merge(expected, 1, Integer::sum);
                missing.add(pos);
            }
        }
        return new Result(layout().size(), placed, missingByBlock, missing, ports);
    }

    /** Creative/testing helper: places every block of the structure around the core. */
    public static int build(Level level, BlockPos core) {
        int count = 0;
        for (Part part : layout()) {
            BlockPos pos = core.offset(part.offset());
            BlockState target = part.placementState();
            if (!part.matches(level.getBlockState(pos))) {
                level.setBlock(pos, target, Block.UPDATE_CLIENTS);
                count++;
            }
        }
        return count;
    }

    /** Mass Input Port position of the stabilizer on the {@code dir} side of the core. */
    public static BlockPos massPortOffset(Direction dir) {
        return BlockPos.ZERO.relative(dir, STABILIZER_DISTANCE).above(4);
    }

    private ReactorStructure() {}
}
