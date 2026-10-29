package com.masteri.bhe.horizon;

import com.masteri.bhe.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Horizon Gate's ring: 23 Horizon Frames standing in a 9x9 circle on its edge, with the gate as the middle of
 * the bottom. It can face either way (spread along x or along z). The inside must be open: that's where the
 * portal forms.
 *
 * <pre>
 *   ..FFFFF..
 *   .F.....F.
 *   F.......F
 *   F.......F
 *   F.......F
 *   F.......F
 *   F.......F
 *   .F.....F.
 *   ..FFGFF..
 * </pre>
 */
public final class HorizonRing {
    private static final String[] SHAPE = {
            "..FFFFF..",
            ".F.....F.",
            "F.......F",
            "F.......F",
            "F.......F",
            "F.......F",
            "F.......F",
            ".F.....F.",
            "..FFGFF..",
    };
    /** The ring's middle, above the gate. */
    public static final int CENTER_UP = 4;
    /** Radius of the open inside, from the middle (the frames start just past it). */
    public static final float INNER_RADIUS = 3.5F;

    /** Offsets (along the ring, up from the gate) of every frame, ordered round the ring from the gate clockwise. */
    public static final List<int[]> FRAMES = new ArrayList<>();
    /** Offsets of every open block inside the ring. */
    public static final List<int[]> INSIDE = new ArrayList<>();

    static {
        for (int row = 0; row < SHAPE.length; row++) {
            int up = SHAPE.length - 1 - row;
            String line = SHAPE[row];
            int first = line.indexOf('F'), last = line.lastIndexOf('F');
            for (int col = 0; col < line.length(); col++) {
                int along = col - 4;
                char c = line.charAt(col);
                if (c == 'F') FRAMES.add(new int[] {along, up});
                else if (c == '.' && col > first && col < last && up > 0 && up < SHAPE.length - 1) INSIDE.add(new int[] {along, up});
            }
        }
        // Round the ring, starting at the bottom: by angle around the middle.
        FRAMES.sort(Comparator.comparingDouble(f -> {
            double a = Math.atan2(f[0], -(f[1] - CENTER_UP)); // 0 straight down, increasing one way round
            return a < 0 ? a + Math.PI * 2 : a;
        }));
    }

    public static BlockPos at(BlockPos gate, Direction.Axis axis, int along, int up) {
        return axis == Direction.Axis.X ? gate.offset(along, up, 0) : gate.offset(0, up, along);
    }

    /** Which way a complete ring stands on this gate, or null if neither way is complete. */
    @Nullable
    public static Direction.Axis find(Level level, BlockPos gate) {
        for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
            if (missing(level, gate, axis) == 0) return axis;
        }
        return null;
    }

    /** The way the ring is most nearly built (for telling the player what's missing). */
    public static Direction.Axis likely(Level level, BlockPos gate) {
        return missing(level, gate, Direction.Axis.X) <= missing(level, gate, Direction.Axis.Z) ? Direction.Axis.X : Direction.Axis.Z;
    }

    public static int missing(Level level, BlockPos gate, Direction.Axis axis) {
        int missing = 0;
        for (int[] f : FRAMES) {
            if (!level.getBlockState(at(gate, axis, f[0], f[1])).is(ModBlocks.HORIZON_FRAME.get())) missing++;
        }
        return missing;
    }

    /** Whether the inside is open (nothing solid, no liquid) for the portal to form. */
    public static boolean clear(Level level, BlockPos gate, Direction.Axis axis) {
        for (int[] in : INSIDE) {
            BlockPos p = at(gate, axis, in[0], in[1]);
            var state = level.getBlockState(p);
            if (!state.getCollisionShape(level, p).isEmpty() || !state.getFluidState().isEmpty()) return false;
        }
        return true;
    }

    public static Vec3 center(BlockPos gate) {
        return Vec3.atCenterOf(gate).add(0, CENTER_UP, 0);
    }

    /** The direction straight through the ring. */
    public static Direction normal(Direction.Axis axis) {
        return axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
    }

    /** Whether the entity is stepping through the open portal (its middle is in the ring's plane, inside the frames). */
    public static boolean inPortal(Entity entity, BlockPos gate, Direction.Axis axis) {
        Vec3 c = center(gate);
        Vec3 p = entity.position().add(0, entity.getBbHeight() / 2, 0);
        double along = axis == Direction.Axis.X ? p.x - c.x : p.z - c.z;
        double through = axis == Direction.Axis.X ? p.z - c.z : p.x - c.x;
        double up = p.y - c.y;
        return Math.abs(through) < 0.6 && along * along + up * up < Mth.square(INNER_RADIUS - 0.3);
    }

    /** Builds the whole ring (frames and gate) standing on {@code gate}. */
    public static void build(Level level, BlockPos gate, Direction.Axis axis) {
        for (int[] f : FRAMES) level.setBlock(at(gate, axis, f[0], f[1]), ModBlocks.HORIZON_FRAME.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(gate, ModBlocks.HORIZON_GATE.get().defaultBlockState(), Block.UPDATE_ALL);
    }

    private HorizonRing() {}
}
