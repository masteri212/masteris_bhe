package com.masteri.bhe.reactor;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.block.ControlConsoleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * What the black hole may tear loose, and what a torn block carries in with it. Shared by the ground and by moving
 * structures (SubLevelPull), so both follow the same rules.
 *
 * Blocks with storage go too: their contents are counted (1 mass per item, on top of the block's own 1) and ride
 * along with the torn block. A torn block that's let go instead (a vent) lands with its contents back in it.
 */
public final class BlockContents {
    /** Persistent-data key on a torn block: how many items it carries (mass on top of its own 1). */
    public static final String CARRIED = MasteriBHE.MODID + ":carried_mass";

    /** What was taken out of a block before it was torn off: its saved data (to restore it) and its item count. */
    public record Taken(@Nullable CompoundTag data, long items) {
        public static final Taken NONE = new Taken(null, 0);
    }

    /**
     * Whether the black hole may tear this block off: not air, liquid or unbreakable, and not one of this mod's own
     * machines (the laser, consoles, research core, bomb...), which hold state the black hole shouldn't scramble.
     */
    public static boolean edible(Level level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0) return false;
        if (state.getBlock() instanceof ControlConsoleBlock) return false; // every part of the wall, not just its origin
        return level.getBlockEntity(pos) == null
                || !MasteriBHE.MODID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
    }

    /**
     * Takes a block's contents out before it's torn off: saves its data (contents included), counts its items and
     * empties it, so removing the block drops nothing. Call it right before removing the block.
     */
    public static Taken take(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return Taken.NONE;
        CompoundTag data = be.saveWithoutMetadata(level.registryAccess());
        long items = 0;
        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) items += container.getItem(i).getCount();
        } else {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    items += stack.getCount();
                    handler.extractItem(i, stack.getCount(), false);
                }
            }
        }
        Clearable.tryClear(be); // the same emptying /setblock does, so nothing spills when the block goes
        return new Taken(data, items);
    }

    /** Puts what was taken onto the torn block flying in. */
    public static void attach(FallingBlockEntity falling, Taken taken) {
        if (taken.data() != null) falling.blockData = taken.data();
        if (taken.items() > 0) falling.getPersistentData().putLong(CARRIED, taken.items());
    }

    /** The mass a swallowed torn block is worth: the block's own (blockMass, or more for Neutronium), plus 1 per item it carried. */
    public static long massOf(FallingBlockEntity falling) {
        long own = Math.max(com.masteri.bhe.config.ReactorConfig.BLOCK_MASS.get(), MassValues.per(falling.getBlockState().getBlock().asItem()));
        return own + Math.max(0, falling.getPersistentData().getLong(CARRIED));
    }

    private BlockContents() {}
}
