package com.masteri.bhe.block.entity;

import com.masteri.bhe.menu.ConsoleMenu;
import com.masteri.bhe.reactor.ReactorLocator;
import com.masteri.bhe.reactor.ReactorReadout;
import com.masteri.bhe.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * The console wall's origin block. Links to the nearest Reactor Core and, a few times a second, copies its stats
 * here and sends them to the clients so the monitor can draw them (no menu needs to be open).
 */
public class ControlConsoleBlockEntity extends BlockEntity {
    public static final int LINK_RADIUS = 32;
    private static final int SYNC_INTERVAL = 4, RELINK_INTERVAL = 40;

    @Nullable private BlockPos corePos;
    private int[] stats = new int[ReactorCoreBlockEntity.DATA_SIZE];
    private boolean linked;
    private int ticks;

    public ControlConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTROL_CONSOLE.get(), pos, state);
    }

    /** The linked core, looking for the nearest one if there's none yet (or it's gone). */
    @Nullable
    private ReactorCoreBlockEntity core(boolean search) {
        ReactorCoreBlockEntity core = ReactorLocator.coreAt(level, corePos);
        if (core == null && search && level != null) {
            core = ReactorLocator.findCore(level, worldPosition, LINK_RADIUS);
            corePos = core == null ? null : core.getBlockPos();
            setChanged();
        }
        return core;
    }

    /** The core this console controls (finding the nearest one if needed), or null. */
    @Nullable
    public ReactorCoreBlockEntity linkedCore() {
        return core(true);
    }

    /** Links this console to a specific core (for the debug assembler). */
    public void link(BlockPos core) {
        corePos = core.immutable();
        setChanged();
    }

    /** The stats as last synced (on the client) or copied (on the server). */
    public ReactorReadout readout() {
        return slot -> slot >= 0 && slot < stats.length ? stats[slot] : 0;
    }

    public boolean isLinked() {
        return linked;
    }

    public void serverTick() {
        if (level == null || ++ticks % SYNC_INTERVAL != 0) return;
        ReactorCoreBlockEntity core = core(ticks % RELINK_INTERVAL == 0);
        int[] next = new int[ReactorCoreBlockEntity.DATA_SIZE];
        if (core != null) {
            for (int i = 0; i < next.length; i++) next[i] = core.getData().get(i);
        }
        if ((core != null) != linked || !Arrays.equals(next, stats)) {
            stats = next;
            linked = core != null;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** A button on the screen was pressed. */
    public void press(Player player, int button) {
        ReactorCoreBlockEntity core = core(true);
        if (core == null) {
            player.displayClientMessage(Component.translatable("message.masteris_bhe.console.no_core", LINK_RADIUS)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        core.handleButton(player, button);
        ticks = SYNC_INTERVAL - 1; // show the result on the next tick
    }

    /** The older GUI, still there on sneak-use. */
    public void open(Player player) {
        ReactorCoreBlockEntity core = core(true);
        if (core == null) {
            player.displayClientMessage(Component.translatable("message.masteris_bhe.console.no_core", LINK_RADIUS)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ReactorCoreBlockEntity linkedCore = core;
        BlockPos consolePos = worldPosition;
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new ConsoleMenu(id, inventory, consolePos, linkedCore),
                Component.translatable("container.masteris_bhe.control_console")), buf -> buf.writeBlockPos(consolePos));
    }

    // ------------------------------------------------------------------ syncing and saving

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("Stats", stats);
        tag.putBoolean("Linked", linked);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (corePos != null) tag.put("Core", NbtUtils.writeBlockPos(corePos));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Stats")) { // update packets only
            int[] synced = tag.getIntArray("Stats");
            stats = synced.length == ReactorCoreBlockEntity.DATA_SIZE ? synced : new int[ReactorCoreBlockEntity.DATA_SIZE];
            linked = tag.getBoolean("Linked");
        } else {
            corePos = NbtUtils.readBlockPos(tag, "Core").orElse(null);
        }
    }
}
