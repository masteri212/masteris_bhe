package com.masteri.bhe.registry;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class ModCapabilities {
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.MASS_INPUT_PORT.get(),
                (port, side) -> port.getItemHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.REACTOR_PORT.get(),
                (port, side) -> port.getEnergy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.BENDING_LASER.get(),
                (laser, side) -> laser.getEnergy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.BLACK_HOLE_BOMB.get(),
                (bomb, side) -> bomb.getItemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.HORIZON_GATE.get(),
                (gate, side) -> gate.getItemHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.HAWKING_CELL.get(),
                (cell, side) -> cell.getEnergy());
    }

    private ModCapabilities() {}
}
