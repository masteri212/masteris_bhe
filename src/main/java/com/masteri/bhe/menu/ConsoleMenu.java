package com.masteri.bhe.menu;

import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.reactor.ReactorState;
import com.masteri.bhe.registry.ModBlocks;
import com.masteri.bhe.registry.ModMenus;
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

/** No slots: just synced reactor stats and button clicks (vanilla's container button packet, no custom networking). */
public class ConsoleMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final ContainerLevelAccess access;
    @Nullable private final ReactorCoreBlockEntity core; // server side only

    /** Client constructor. */
    public ConsoleMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), null);
    }

    /** Server constructor. */
    public ConsoleMenu(int id, Inventory inventory, BlockPos consolePos, @Nullable ReactorCoreBlockEntity core) {
        super(ModMenus.CONSOLE.get(), id);
        this.core = core;
        this.data = core != null ? core.getData() : new SimpleContainerData(ReactorCoreBlockEntity.DATA_SIZE);
        this.access = core != null ? ContainerLevelAccess.create(inventory.player.level(), consolePos) : ContainerLevelAccess.NULL;
        addDataSlots(data);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (core == null || core.isRemoved()) return false;
        core.handleButton(player, id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return (core == null || !core.isRemoved()) && stillValid(access, player, ModBlocks.CONTROL_CONSOLE.get());
    }

    // ------------------------------------------------------------------ client-side readers

    private int wide(int lo, int hi) {
        return ((data.get(hi) & 0xFFFF) << 16) | (data.get(lo) & 0xFFFF);
    }

    public ReactorState state() { return ReactorState.byId(data.get(0)); }
    public double stability() { return data.get(1) / 10.0; }
    public int rpm() { return data.get(2) & 0xFFFF; }
    public double stress() { return data.get(3) / 10.0; }
    public int offset() { return data.get(4); }
    public int targetOffset() { return data.get(5); }
    public int laserPower() { return data.get(6); }
    public int timerSeconds() { return data.get(7) & 0xFFFF; }
    public int energy() { return wide(8, 9); }
    public int mass() { return wide(10, 11); }
    public boolean formed() { return data.get(12) == 1; }
    public boolean jumpWindow() { return data.get(13) == 1; }
    public int output() { return wide(14, 15); }
}
