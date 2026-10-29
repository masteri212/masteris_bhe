package com.masteri.bhe.block.entity;

import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * A Hawking Cell: a speck of black hole slowly evaporating into Hawking radiation. It makes RF on its own, forever,
 * and pushes it into neighbouring machines and cables (or holds it until they take it).
 */
public class HawkingCellBlockEntity extends BlockEntity {
    private static final int CAPACITY = 200_000, PUSH = 8192;

    private final Buffer energy = new Buffer();

    public HawkingCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAWKING_CELL.get(), pos, state);
    }

    public IEnergyStorage getEnergy() {
        return energy;
    }

    public void serverTick() {
        if (level == null) return;
        energy.generate(ReactorConfig.HAWKING_CELL_RF.get());
        for (Direction dir : Direction.values()) {
            if (energy.getEnergyStored() <= 0) break;
            IEnergyStorage sink = level.getCapability(Capabilities.EnergyStorage.BLOCK, worldPosition.relative(dir), dir.getOpposite());
            if (sink == null || !sink.canReceive()) continue;
            int accepted = sink.receiveEnergy(Math.min(PUSH, energy.getEnergyStored()), false);
            energy.extractEnergy(accepted, false);
        }
        if (level.getGameTime() % 40 == 0) setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.set(tag.getInt("Energy"));
    }

    /** Output only: nothing can charge it. */
    private static final class Buffer extends EnergyStorage {
        Buffer() {
            super(CAPACITY, 0, PUSH);
        }

        void generate(int amount) {
            energy = Math.min(capacity, energy + Math.max(0, amount));
        }

        void set(int value) {
            energy = Math.max(0, Math.min(capacity, value));
        }
    }
}
