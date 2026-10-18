package com.masteri.bhe.block.entity;

import com.masteri.bhe.reactor.ReactorLocator;
import com.masteri.bhe.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public class ReactorPortBlockEntity extends BlockEntity {
    private static final int PUSH_PER_SIDE = 2_000_000;

    @Nullable private BlockPos corePos;

    /** Forwards everything to the linked core's energy buffer. */
    private final IEnergyStorage energy = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) {
            ReactorCoreBlockEntity core = core();
            return core == null ? 0 : core.getEnergy().receiveEnergy(max, simulate);
        }
        @Override public int extractEnergy(int max, boolean simulate) {
            ReactorCoreBlockEntity core = core();
            return core == null ? 0 : core.getEnergy().extractEnergy(max, simulate);
        }
        @Override public int getEnergyStored() {
            ReactorCoreBlockEntity core = core();
            return core == null ? 0 : core.getEnergy().getEnergyStored();
        }
        @Override public int getMaxEnergyStored() {
            ReactorCoreBlockEntity core = core();
            return core == null ? 0 : core.getEnergy().getMaxEnergyStored();
        }
        @Override public boolean canExtract() { return core() != null; }
        @Override public boolean canReceive() { return core() != null; }
    };

    public ReactorPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REACTOR_PORT.get(), pos, state);
    }

    public IEnergyStorage getEnergy() {
        return energy;
    }

    public void link(BlockPos core) {
        if (!core.equals(corePos)) {
            corePos = core;
            setChanged();
        }
    }

    @Nullable
    private ReactorCoreBlockEntity core() {
        ReactorCoreBlockEntity core = ReactorLocator.coreAt(level, corePos);
        return core != null && core.isFormed() ? core : null;
    }

    public int comparatorSignal() {
        ReactorCoreBlockEntity core = core();
        return core == null ? 0 : (int) Math.round(core.getStability() / 100.0 * 15);
    }

    /** While the reactor generates, push its power into neighbouring machines and cables. */
    public void serverTick() {
        ReactorCoreBlockEntity core = core();
        if (core == null || !core.getState().generates() || level == null) return;
        for (Direction dir : Direction.values()) {
            BlockPos target = worldPosition.relative(dir);
            if (level.getBlockEntity(target) instanceof ReactorPortBlockEntity) continue;
            IEnergyStorage sink = level.getCapability(Capabilities.EnergyStorage.BLOCK, target, dir.getOpposite());
            if (sink == null || !sink.canReceive()) continue;
            int available = core.getEnergy().extractEnergy(PUSH_PER_SIDE, true);
            if (available <= 0) return;
            int accepted = sink.receiveEnergy(available, false);
            core.getEnergy().extractEnergy(accepted, false);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (corePos != null) tag.put("Core", NbtUtils.writeBlockPos(corePos));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        corePos = NbtUtils.readBlockPos(tag, "Core").orElse(null);
    }
}
