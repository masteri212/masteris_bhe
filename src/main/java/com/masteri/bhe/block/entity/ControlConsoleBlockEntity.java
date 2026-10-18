package com.masteri.bhe.block.entity;

import com.masteri.bhe.menu.ConsoleMenu;
import com.masteri.bhe.reactor.ReactorLocator;
import com.masteri.bhe.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ControlConsoleBlockEntity extends BlockEntity {
    public static final int LINK_RADIUS = 32;

    @Nullable private BlockPos corePos;

    public ControlConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTROL_CONSOLE.get(), pos, state);
    }

    public void open(Player player) {
        ReactorCoreBlockEntity core = ReactorLocator.coreAt(level, corePos);
        if (core == null && level != null) {
            core = ReactorLocator.findCore(level, worldPosition, LINK_RADIUS);
            corePos = core == null ? null : core.getBlockPos();
            setChanged();
        }
        if (core == null) {
            player.displayClientMessage(Component.translatable("message.masteris_bhe.console.no_core", LINK_RADIUS)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ReactorCoreBlockEntity linked = core;
        BlockPos consolePos = worldPosition;
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new ConsoleMenu(id, inventory, consolePos, linked),
                Component.translatable("container.masteris_bhe.control_console")), buf -> buf.writeBlockPos(consolePos));
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
