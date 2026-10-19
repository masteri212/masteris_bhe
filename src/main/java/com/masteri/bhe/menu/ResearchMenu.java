package com.masteri.bhe.menu;

import com.masteri.bhe.block.entity.ResearchCoreBlockEntity;
import com.masteri.bhe.registry.ModBlocks;
import com.masteri.bhe.registry.ModMenus;
import com.masteri.bhe.research.Experiment;
import com.masteri.bhe.research.ResearchChamber;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The Research Core's panel: no slots, just the synced experiment state and button clicks. */
public class ResearchMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final ContainerLevelAccess access;
    @Nullable private final ResearchCoreBlockEntity core; // server side only

    /** Client constructor. */
    public ResearchMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), null);
    }

    /** Server constructor. */
    public ResearchMenu(int id, Inventory inventory, BlockPos pos, @Nullable ResearchCoreBlockEntity core) {
        super(ModMenus.RESEARCH.get(), id);
        this.core = core;
        this.data = core != null ? core.getData() : new SimpleContainerData(ResearchCoreBlockEntity.DATA_SIZE);
        this.access = core != null ? ContainerLevelAccess.create(inventory.player.level(), pos) : ContainerLevelAccess.NULL;
        addDataSlots(data);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (core == null || core.isRemoved()) return false;
        core.button(player, id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return (core == null || !core.isRemoved()) && stillValid(access, player, ModBlocks.RESEARCH_CORE.get());
    }

    // ------------------------------------------------------------------ client-side readers

    @Nullable
    public Experiment experiment() {
        int id = data.get(0);
        return id == 0 ? null : Experiment.byId(id - 1);
    }

    public int stepIndex() { return data.get(1); }
    public int stepCount() { return Math.min(data.get(2), Experiment.MAX_STEPS); }
    public int seconds() { return data.get(3); }
    public int field() { return data.get(4); }
    public int fed() { return data.get(5); }
    public boolean pulseOpen() { return data.get(6) == 1; }
    public ResearchChamber.Status status() {
        ResearchChamber.Status[] all = ResearchChamber.Status.values();
        int i = data.get(7);
        return i >= 0 && i < all.length ? all[i] : ResearchChamber.Status.INCOMPLETE;
    }
    public boolean powered() { return data.get(8) == 1; }
    /** The procedure's step {@code i}, as a Step code. */
    public int step(int i) { return data.get(9 + i); }
}
