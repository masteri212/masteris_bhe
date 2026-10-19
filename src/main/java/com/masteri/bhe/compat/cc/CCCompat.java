package com.masteri.bhe.compat.cc;

import com.masteri.bhe.registry.ModBlockEntities;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * CC: Tweaked support. Only loaded when CC: Tweaked is installed (see MasteriBHE): the reactor peripheral on the
 * Reactor Core, every Reactor Port and the control console (put the modem on its bottom-left block).
 */
public final class CCCompat {
    public static final String MOD_ID = "computercraft";

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(), ModBlockEntities.REACTOR_CORE.get(),
                (core, side) -> new ReactorPeripheral(core, () -> core));
        event.registerBlockEntity(PeripheralCapability.get(), ModBlockEntities.REACTOR_PORT.get(),
                (port, side) -> new ReactorPeripheral(port, port::core));
        event.registerBlockEntity(PeripheralCapability.get(), ModBlockEntities.CONTROL_CONSOLE.get(),
                (console, side) -> new ReactorPeripheral(console, console::linkedCore));
    }

    private CCCompat() {}
}
