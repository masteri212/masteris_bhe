package com.masteri.bhe.multiblock;

import com.masteri.bhe.block.Assemblable;
import com.masteri.bhe.reactor.VentingLance;
import com.masteri.bhe.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Assembles the reactor's structures separately: the ring, each stabilizer, each axial stabilizer and the lance.
 * As soon as one is complete its blocks switch to their assembled state (not drawn: the core draws the whole
 * structure as one model, see AssembledStructures), and back as soon as any of its blocks is missing. This is only
 * how they look: whether the reactor runs is still decided by ReactorStructure.check.
 *
 * The core re-checks every couple of seconds, and at once when a structure block is placed or an assembled one is
 * removed near it (see {@link #changed}).
 */
public final class ReactorAssembly {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** How far from the core a changed block can be and still belong to it (the top of the highest lance). */
    private static final int REACH = VentingLance.MAX_BOTTOM + VentingLance.HEIGHT + 2;
    private static final int MAX_CHANGES = 256;

    private record Change(Level level, BlockPos pos) {}

    private static final List<Change> CHANGES = new ArrayList<>();

    /** A structure block was placed, or an assembled one removed. */
    public static void changed(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        synchronized (CHANGES) {
            if (CHANGES.size() >= MAX_CHANGES) CHANGES.remove(0); // nobody near to take them: drop the oldest
            CHANGES.add(new Change(level, pos.immutable()));
        }
    }

    /** Whether a structure block changed near this core since it last looked (and forgets those changes). */
    public static boolean takeChanges(Level level, BlockPos core) {
        synchronized (CHANGES) {
            if (CHANGES.isEmpty()) return false;
            boolean near = false;
            Iterator<Change> it = CHANGES.iterator();
            while (it.hasNext()) {
                Change c = it.next();
                if (c.level != level) continue;
                if (Math.abs(c.pos.getX() - core.getX()) <= REACH && Math.abs(c.pos.getY() - core.getY()) <= REACH
                        && Math.abs(c.pos.getZ() - core.getZ()) <= REACH) {
                    near = true;
                    it.remove();
                }
            }
            return near;
        }
    }

    /** Assembles every complete structure around the core and takes apart every incomplete one. */
    public static void update(Level level, BlockPos core) {
        for (ReactorStructure.Section section : ReactorStructure.sections()) {
            set(level, core, section.parts(), complete(level, core, section.parts()));
        }
        updateLance(level, core, true);
    }

    /** The core is gone: nothing around it is assembled any more. */
    public static void disassembleAll(Level level, BlockPos core) {
        for (ReactorStructure.Section section : ReactorStructure.sections()) set(level, core, section.parts(), false);
        updateLance(level, core, false);
    }

    private static boolean complete(Level level, BlockPos core, List<ReactorStructure.Part> parts) {
        for (ReactorStructure.Part part : parts) {
            if (!part.matches(level.getBlockState(core.offset(part.offset())))) return false;
        }
        return true;
    }

    private static void set(Level level, BlockPos core, List<ReactorStructure.Part> parts, boolean assembled) {
        for (ReactorStructure.Part part : parts) {
            BlockPos pos = core.offset(part.offset());
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(Assemblable.ASSEMBLED) && part.matches(state) && state.getValue(Assemblable.ASSEMBLED) != assembled) {
                level.setBlock(pos, state.setValue(Assemblable.ASSEMBLED, assembled), FLAGS);
            }
        }
    }

    /** The lance: assembled where it's complete; any other Lance Emitter in the column over the core isn't. */
    private static void updateLance(Level level, BlockPos core, boolean allow) {
        int bottom = allow ? VentingLance.find(level, core) : -1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = VentingLance.MIN_BOTTOM; dy < VentingLance.MAX_BOTTOM + VentingLance.HEIGHT; dy++) {
            boolean in = bottom >= 0 && dy >= bottom && dy < bottom + VentingLance.HEIGHT;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    pos.set(core.getX() + dx, core.getY() + dy, core.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(ModBlocks.LANCE_EMITTER.get()) && state.getValue(Assemblable.ASSEMBLED) != in) {
                        level.setBlock(pos, state.setValue(Assemblable.ASSEMBLED, in), FLAGS);
                    }
                }
            }
        }
    }

    private ReactorAssembly() {}
}
