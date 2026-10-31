package com.masteri.bhe.block.entity;

import com.masteri.bhe.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Stores RF from cables; the core pulls it as laser input according to the console's laser power. Only a laser in
 * its spot by the ring's north-east beam port is used (see ReactorStructure.LASER_OFFSET).
 */
public class BendingLaserBlockEntity extends BlockEntity {
    private static final int CAPACITY = 4_000_000;

    private final Buffer buffer = new Buffer();

    public BendingLaserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BENDING_LASER.get(), pos, state);
    }

    public IEnergyStorage getEnergy() {
        return buffer;
    }

    /** Called by the core: takes up to {@code max} RF out of the laser's buffer. */
    public int fire(int max) {
        int fired = Math.min(buffer.getEnergyStored(), Math.max(0, max));
        buffer.take(fired);
        if (fired > 0) setChanged();
        return fired;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", buffer.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        buffer.set(tag.getInt("Energy"));
    }

    private static class Buffer extends EnergyStorage {
        Buffer() { super(CAPACITY, CAPACITY, 0); }   // input only from outside
        void take(int amount) { energy -= amount; }
        void set(int amount) { energy = Math.max(0, Math.min(capacity, amount)); }
    }
}
