package com.masteri.bhe.reactor;

import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;

/**
 * Decodes the reactor's synced stat slots (see ReactorCoreBlockEntity's ContainerData). Shared by the console GUI's
 * menu and the console monitor, which each get the raw slots their own way.
 */
@FunctionalInterface
public interface ReactorReadout {
    int get(int slot);

    private int wide(int lo, int hi) {
        return ((get(hi) & 0xFFFF) << 16) | (get(lo) & 0xFFFF);
    }

    default ReactorState state() { return ReactorState.byId(get(0)); }
    default double stability() { return get(1) / 10.0; }
    default int rpm() { return (short) get(2); } // signed: negative is spinning backwards
    default double stress() { return get(3) / 10.0; }
    default int offset() { return (short) get(4); }
    default int targetOffset() { return (short) get(5); }
    default int laserPower() { return get(6); }
    default int timerSeconds() { return get(7) & 0xFFFF; }
    default int energy() { return wide(8, 9); }
    default int mass() { return wide(10, 11); }
    default boolean formed() { return get(12) == 1; }
    /** Overcharged breakers, one bit per side. */
    default int breakerMask() { return get(13); }
    default int output() { return wide(14, 15); }
    default ReactorCoreBlockEntity.Fault fault() {
        ReactorCoreBlockEntity.Fault[] all = ReactorCoreBlockEntity.Fault.values();
        int i = get(16);
        return i >= 0 && i < all.length ? all[i] : ReactorCoreBlockEntity.Fault.NONE;
    }
    default int faultMask() { return get(17); }
    default boolean lanceFormed() { return get(18) == 1; }
    default boolean ventArmed() { return get(19) == 1; }
    default boolean faultFixed() { return get(20) == 1; }
    /** Both axial stabilizers are built, so spin makes power. */
    default boolean axialFormed() { return get(21) == 1; }
    /** Where the resonance band is centered, in rpm. */
    default int resonanceRpm() { return get(22) & 0xFFFF; }
    /** Spin-to-RF efficiency right now, 0..100 (0 without the axial stabilizers). */
    default int efficiency() { return get(23); }
    /** -100..100: the disc's lean, negative toward the north-west. */
    default int wobble() { return (short) get(24); }
    /** -10..10: the axial trim, negative pushing toward the north-west. */
    default int axialTrim() { return (short) get(25); }
    /** How many failures (or a shutdown) are waiting underneath the current one. */
    default int pendingCount() { return get(26); }
    /** The one that comes back next. */
    default ReactorState pendingState() { return ReactorState.byId(get(27)); }
    default ReactorCoreBlockEntity.Fault pendingFault() {
        ReactorCoreBlockEntity.Fault[] all = ReactorCoreBlockEntity.Fault.values();
        int i = get(28);
        return i >= 0 && i < all.length ? all[i] : ReactorCoreBlockEntity.Fault.NONE;
    }
    default int pendingFaultMask() { return get(29); }
}
