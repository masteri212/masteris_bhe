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
 * Containment ring: flat torus around the core, 35 blocks across, tube radius 2 (5 layers, core on the middle layer),
 * 25-block hole. 3x3 Ring Beam Port tunnels through the middle three layers facing N, S, E, W and NE.
 *
 * Stabilizers: 7-long, 5x5 barrels lying on their side, pointing at the core, centered 24 blocks out on each axis
 * with the bottom layer level with the ring's bottom layer. Emitter in the middle of the core-facing end (core
 * height), a band of 16 coils around the barrel one block behind the middle, the mass input port on top in the
 * middle, and on the far end, at eye level, the Stabilizer Console (left) and the Breaker Panel (right).
 */
public final class ReactorStructure {
    public static final int RING_MAJOR_RADIUS = 15;
    public static final double RING_TUBE_RADIUS = 2.5; // 2-block tube, voxelized around block centers
    public static final int STABILIZER_DISTANCE = 24;
    /** Half the stabilizer's length: its blocks run from {@code STABILIZER_DISTANCE - 3} to {@code + 3} out. */
    public static final int STABILIZER_HALF_LENGTH = 3;
    /** How far out each stabilizer's emitter (the end facing the core) sits. */
    public static final int EMITTER_DISTANCE = STABILIZER_DISTANCE - STABILIZER_HALF_LENGTH;
    /** Radius of the open hole inside the ring, where the disc spins and anything that falls in becomes mass. */
    public static final double RING_INNER_RADIUS = RING_MAJOR_RADIUS - RING_TUBE_RADIUS;
    /**
     * The black hole over the core. It is about 3 blocks across at the ignition minimum and grows with the
     * square root of the mass (4x the mass, twice as wide), between these limits. Things that reach it are swallowed.
     */
    public static final double BLACK_HOLE_RADIUS = 1.5;
    public static final double BLACK_HOLE_MIN_RADIUS = 0.75, BLACK_HOLE_MAX_RADIUS = 5.0;

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

        // Four stabilizers, lying down
        int len = STABILIZER_HALF_LENGTH;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            Direction side = dir.getClockWise();
            for (int a = -len; a <= len; a++) {      // along the axis, -len is the end facing the core
                for (int b = -2; b <= 2; b++) {      // across the axis
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos offset = BlockPos.ZERO.relative(dir, STABILIZER_DISTANCE + a).relative(side, b).above(dy);
                        Supplier<? extends Block> block = ModBlocks.STABILIZER_CASING;
                        Direction facing = null;
                        if (a == -len && b == 0 && dy == 0) {
                            block = ModBlocks.STABILIZER_EMITTER;
                            facing = dir.getOpposite(); // front faces the core
                        } else if (a == 0 && b == 0 && dy == 2) {
                            block = ModBlocks.MASS_INPUT_PORT;
                        } else if (offset.equals(stabilizerConsoleOffset(dir))) {
                            block = ModBlocks.STABILIZER_CONSOLE;
                            facing = dir; // faces away from the core
                        } else if (offset.equals(breakerPanelOffset(dir))) {
                            block = ModBlocks.BREAKER_PANEL;
                            facing = dir;
                        } else if (a == 1 && (Math.abs(b) == 2 || Math.abs(dy) == 2)) {
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

    private static List<BlockPos> ringOffsets;

    /** Offsets of every containment ring block (frame and beam port spots), for effects. */
    public static List<BlockPos> ringOffsets() {
        if (ringOffsets == null) {
            List<BlockPos> list = new ArrayList<>();
            for (Part part : layout()) {
                BlockPos o = part.offset();
                if (o.getX() * o.getX() + o.getZ() * o.getZ() <= (RING_MAJOR_RADIUS + 3) * (RING_MAJOR_RADIUS + 3)) list.add(o);
            }
            ringOffsets = Collections.unmodifiableList(list);
        }
        return ringOffsets;
    }

    /** Mass Input Port position of the stabilizer on the {@code dir} side of the core. */
    public static BlockPos massPortOffset(Direction dir) {
        return BlockPos.ZERO.relative(dir, STABILIZER_DISTANCE).above(2);
    }

    /** Stabilizer Console of the {@code dir} stabilizer: far end, eye level, on the left as you face it. */
    public static BlockPos stabilizerConsoleOffset(Direction dir) {
        return BlockPos.ZERO.relative(dir, STABILIZER_DISTANCE + STABILIZER_HALF_LENGTH).relative(dir.getClockWise(), 1).below();
    }

    /** Breaker Panel of the {@code dir} stabilizer: next to the console, on the right as you face it. */
    public static BlockPos breakerPanelOffset(Direction dir) {
        return BlockPos.ZERO.relative(dir, STABILIZER_DISTANCE + STABILIZER_HALF_LENGTH).relative(dir.getCounterClockWise(), 1).below();
    }

    // ------------------------------------------------------------------ axial stabilizers

    /**
     * The two axial stabilizers, raised over the ring on opposite diagonals (north-west and south-east, clear of the
     * laser in the north-east) with their emitters firing down at the core. They aren't needed to run the reactor,
     * but without both of them the disc's spin can't be turned into power. Each is a 3x3x3 block of Stabilizer
     * Casing: a ring of 8 coils around the top, and the Axial Emitter in the middle of the bottom.
     */
    public static final int AXIAL_OFFSET = 11, AXIAL_HEIGHT = 10;

    private static List<Part> axialLayout;

    public static List<Part> axialLayout() {
        if (axialLayout == null) {
            List<Part> parts = new ArrayList<>();
            for (BlockPos center : axialCenters()) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            Supplier<? extends Block> block = ModBlocks.STABILIZER_CASING;
                            if (dy == -1 && dx == 0 && dz == 0) block = ModBlocks.AXIAL_EMITTER;
                            else if (dy == 1 && (dx != 0 || dz != 0)) block = ModBlocks.STABILIZER_COIL;
                            parts.add(new Part(center.offset(dx, dy, dz), block, null));
                        }
                    }
                }
            }
            axialLayout = Collections.unmodifiableList(parts);
        }
        return axialLayout;
    }

    /** The middle of each axial stabilizer, relative to the core. */
    public static List<BlockPos> axialCenters() {
        return List.of(new BlockPos(-AXIAL_OFFSET, AXIAL_HEIGHT, -AXIAL_OFFSET), new BlockPos(AXIAL_OFFSET, AXIAL_HEIGHT, AXIAL_OFFSET));
    }

    /** The Axial Emitters, relative to the core. */
    public static List<BlockPos> axialEmitterOffsets() {
        return axialCenters().stream().map(BlockPos::below).toList();
    }

    /** Whether both axial stabilizers are complete. */
    public static boolean checkAxial(Level level, BlockPos core) {
        for (Part part : axialLayout()) {
            if (!part.matches(level.getBlockState(core.offset(part.offset())))) return false;
        }
        return true;
    }

    /** Creative/testing helper: places both axial stabilizers. */
    public static int buildAxial(Level level, BlockPos core) {
        int count = 0;
        for (Part part : axialLayout()) {
            BlockPos pos = core.offset(part.offset());
            if (!part.matches(level.getBlockState(pos))) {
                level.setBlock(pos, part.placementState(), Block.UPDATE_CLIENTS);
                count++;
            }
        }
        return count;
    }

    private ReactorStructure() {}
}
