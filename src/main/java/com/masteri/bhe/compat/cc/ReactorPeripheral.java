package com.masteri.bhe.compat.cc;

import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.reactor.ReactorReadout;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The reactor as a CC: Tweaked peripheral ({@code "bhe_reactor"}), on the Reactor Core, any Reactor Port and the
 * control console. Everything the console shows can be read, and everything it does can be done: the actions
 * return the message a player pressing the button would have seen (nil if none). All calls run on the server
 * thread, so each takes a tick.
 */
public final class ReactorPeripheral implements IPeripheral {
    private final BlockEntity host;
    private final Supplier<ReactorCoreBlockEntity> core;

    public ReactorPeripheral(BlockEntity host, Supplier<ReactorCoreBlockEntity> core) {
        this.host = host;
        this.core = core;
    }

    @Override
    public String getType() {
        return "bhe_reactor";
    }

    @Override
    public Object getTarget() {
        return host;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof ReactorPeripheral peripheral && peripheral.host == host;
    }

    private ReactorCoreBlockEntity core() throws LuaException {
        ReactorCoreBlockEntity linked = core.get();
        if (linked == null || linked.isRemoved()) throw new LuaException("No Reactor Core linked");
        return linked;
    }

    private ReactorReadout read() throws LuaException {
        return core().getData()::get;
    }

    // ------------------------------------------------------------------ reading

    @LuaFunction(mainThread = true)
    public final boolean isLinked() {
        ReactorCoreBlockEntity linked = core.get();
        return linked != null && !linked.isRemoved();
    }

    /** "offline", "stable", "warning", "disc_stall", "pre_meltdown", "meltdown_1", ... */
    @LuaFunction(mainThread = true)
    public final String getState() throws LuaException {
        return read().state().name().toLowerCase();
    }

    @LuaFunction(mainThread = true)
    public final boolean isFormed() throws LuaException {
        return read().formed();
    }

    @LuaFunction(mainThread = true)
    public final double getStability() throws LuaException {
        return read().stability();
    }

    @LuaFunction(mainThread = true)
    public final double getStress() throws LuaException {
        return read().stress();
    }

    /** Signed: negative is spinning backwards. */
    @LuaFunction(mainThread = true)
    public final int getRpm() throws LuaException {
        return read().rpm();
    }

    @LuaFunction(mainThread = true)
    public final int getOffset() throws LuaException {
        return read().offset();
    }

    @LuaFunction(mainThread = true)
    public final int getTargetOffset() throws LuaException {
        return read().targetOffset();
    }

    @LuaFunction(mainThread = true)
    public final int getMaxOffset() {
        return ReactorConfig.MAX_OFFSET.get();
    }

    @LuaFunction(mainThread = true)
    public final int getLaserPower() throws LuaException {
        return read().laserPower();
    }

    @LuaFunction(mainThread = true)
    public final long getEnergy() throws LuaException {
        return core().getEnergy().getEnergyStored();
    }

    @LuaFunction(mainThread = true)
    public final long getEnergyCapacity() throws LuaException {
        return core().energyCapacity();
    }

    /** RF/t made last tick. */
    @LuaFunction(mainThread = true)
    public final long getOutput() throws LuaException {
        return read().output();
    }

    @LuaFunction(mainThread = true)
    public final long getMass() throws LuaException {
        return read().mass();
    }

    /** {north = ..., east = ..., south = ..., west = ...} */
    @LuaFunction(mainThread = true)
    public final Map<String, Long> getStabilizerMasses() throws LuaException {
        ReactorCoreBlockEntity c = core();
        Map<String, Long> masses = new LinkedHashMap<>();
        for (Direction dir : Direction.Plane.HORIZONTAL) masses.put(dir.getName(), c.stabilizerMass(dir));
        return masses;
    }

    /** Seconds left on the current countdown (stall, fault, pre-meltdown, meltdown), 0 if none. */
    @LuaFunction(mainThread = true)
    public final int getTimer() throws LuaException {
        return read().state().isAlarm() ? read().timerSeconds() : 0;
    }

    /** The fault's name ("stabilizer_desync", "mass_flash", "cracked_lens", "singularity_rebound"), or nil. */
    @Nullable
    @LuaFunction(mainThread = true)
    public final String getFault() throws LuaException {
        ReactorCoreBlockEntity.Fault fault = read().fault();
        return fault == ReactorCoreBlockEntity.Fault.NONE ? null : fault.name().toLowerCase();
    }

    /** The stabilizers the fault hit, e.g. {"north", "west"}. */
    @LuaFunction(mainThread = true)
    public final List<String> getFaultSides() throws LuaException {
        return sides(read().faultMask());
    }

    @LuaFunction(mainThread = true)
    public final boolean isFaultFixed() throws LuaException {
        return read().faultFixed();
    }

    /** Which stabilizers' Breaker Panels are overcharged: {north = true, ...}. */
    @LuaFunction(mainThread = true)
    public final Map<String, Boolean> getBreakers() throws LuaException {
        int mask = read().breakerMask();
        Map<String, Boolean> breakers = new LinkedHashMap<>();
        for (Direction dir : Direction.Plane.HORIZONTAL) breakers.put(dir.getName(), (mask & (1 << dir.get2DDataValue())) != 0);
        return breakers;
    }

    /** {center = rpm, width = rpm either side, efficiency = 0..100} */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getResonance() throws LuaException {
        ReactorReadout r = read();
        Map<String, Object> resonance = new LinkedHashMap<>();
        resonance.put("center", r.resonanceRpm());
        resonance.put("width", ReactorConfig.RESONANCE_WIDTH.get());
        resonance.put("efficiency", r.efficiency());
        return resonance;
    }

    /** -100..100: the disc's lean, negative toward the north-west. At +-100 it stalls. */
    @LuaFunction(mainThread = true)
    public final int getWobble() throws LuaException {
        return read().wobble();
    }

    @LuaFunction(mainThread = true)
    public final int getAxialTrim() throws LuaException {
        return read().axialTrim();
    }

    @LuaFunction(mainThread = true)
    public final boolean isAxialFormed() throws LuaException {
        return read().axialFormed();
    }

    @LuaFunction(mainThread = true)
    public final boolean isLanceFormed() throws LuaException {
        return read().lanceFormed();
    }

    @LuaFunction(mainThread = true)
    public final boolean isVentArmed() throws LuaException {
        return read().ventArmed();
    }

    /** Everything above in one table. */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getStatus() throws LuaException {
        ReactorReadout r = read();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("state", r.state().name().toLowerCase());
        status.put("formed", r.formed());
        status.put("stability", r.stability());
        status.put("stress", r.stress());
        status.put("rpm", r.rpm());
        status.put("offset", r.offset());
        status.put("targetOffset", r.targetOffset());
        status.put("laserPower", r.laserPower());
        status.put("energy", getEnergy());
        status.put("energyCapacity", getEnergyCapacity());
        status.put("output", r.output());
        status.put("mass", r.mass());
        status.put("stabilizerMasses", getStabilizerMasses());
        status.put("timer", getTimer());
        status.put("fault", getFault());
        status.put("faultSides", getFaultSides());
        status.put("faultFixed", r.faultFixed());
        status.put("breakers", getBreakers());
        status.put("resonance", getResonance());
        status.put("wobble", r.wobble());
        status.put("axialTrim", r.axialTrim());
        status.put("axialFormed", r.axialFormed());
        status.put("lanceFormed", r.lanceFormed());
        status.put("ventArmed", r.ventArmed());
        return status;
    }

    // ------------------------------------------------------------------ controls

    @Nullable
    @LuaFunction(mainThread = true)
    public final String ignite() throws LuaException {
        return press(ReactorCoreBlockEntity.BUTTON_IGNITE);
    }

    @Nullable
    @LuaFunction(mainThread = true)
    public final String shutdown() throws LuaException {
        return press(ReactorCoreBlockEntity.BUTTON_SHUTDOWN);
    }

    @Nullable
    @LuaFunction(mainThread = true)
    public final String scram() throws LuaException {
        return press(ReactorCoreBlockEntity.BUTTON_SCRAM);
    }

    /** Restarts a stalled disc (once every breaker is overcharged). During a fault it's RESET instead. */
    @Nullable
    @LuaFunction(mainThread = true)
    public final String jumpStart() throws LuaException {
        return press(ReactorCoreBlockEntity.BUTTON_JUMP_START);
    }

    /** RESET after fixing a fault (the same button as jumpStart). */
    @Nullable
    @LuaFunction(mainThread = true)
    public final String reset() throws LuaException {
        return press(ReactorCoreBlockEntity.BUTTON_JUMP_START);
    }

    /** Arms the Venting Lance; call it again within 5 seconds to fire. */
    @Nullable
    @LuaFunction(mainThread = true)
    public final String vent() throws LuaException {
        return press(ReactorCoreBlockEntity.BUTTON_VENT);
    }

    /** False if the reactor can't be adjusted right now (it isn't running steadily). */
    @LuaFunction(mainThread = true)
    public final boolean setTargetOffset(int degrees) throws LuaException {
        return core().remoteSetTargetOffset(degrees);
    }

    @LuaFunction(mainThread = true)
    public final boolean setLaserPower(int percent) throws LuaException {
        return core().remoteSetLaserPower(percent);
    }

    /** -10..10, negative pushes toward the north-west. */
    @LuaFunction(mainThread = true)
    public final boolean setAxialTrim(int trim) throws LuaException {
        return core().remoteSetAxialTrim(trim);
    }

    @Nullable
    private String press(int button) throws LuaException {
        Component reply = core().remoteButton(button);
        return reply == null ? null : reply.getString();
    }

    private static List<String> sides(int mask) {
        List<String> sides = new ArrayList<>();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if ((mask & (1 << dir.get2DDataValue())) != 0) sides.add(dir.getName());
        }
        return sides;
    }
}
