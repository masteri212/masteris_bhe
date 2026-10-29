package com.masteri.bhe.block.entity;

import com.masteri.bhe.reactor.MassValues;
import com.masteri.bhe.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

public class MassInputPortBlockEntity extends BlockEntity {
    private long mass;

    private final IItemHandler itemHandler = new IItemHandler() {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return isMass(stack); }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isMass(stack)) return stack;
            if (!simulate) addMass(MassValues.of(stack));
            return ItemStack.EMPTY;
        }
    };

    public MassInputPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MASS_INPUT_PORT.get(), pos, state);
    }

    /** Every block counts as 1 mass, whatever it is; the Event Horizon's dense materials far more (MassValues). */
    public static boolean isMass(ItemStack stack) {
        return MassValues.isMass(stack);
    }

    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    public long getMass() {
        return mass;
    }

    public void addMass(long amount) {
        mass += Math.max(0, amount);
        changed();
    }

    /** Removes up to {@code amount} mass and returns how much was actually removed. */
    public long drain(long amount) {
        long drained = Math.min(mass, Math.max(0, amount));
        mass -= drained;
        if (drained > 0) changed();
        return drained;
    }

    /** Loses a fraction of the stored mass (used by venting). */
    public void burnFraction(double fraction) {
        drain((long) Math.ceil(mass * fraction));
    }

    private void changed() {
        setChanged();
        if (level != null) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("Mass", mass);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mass = tag.getLong("Mass");
    }
}
