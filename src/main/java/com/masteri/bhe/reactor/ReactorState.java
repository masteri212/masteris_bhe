package com.masteri.bhe.reactor;

import net.minecraft.ChatFormatting;

public enum ReactorState {
    OFFLINE(ChatFormatting.GRAY),
    STABLE(ChatFormatting.GREEN),
    WARNING(ChatFormatting.YELLOW),
    SHUTTING_DOWN(ChatFormatting.AQUA),
    DISC_STALL(ChatFormatting.GOLD),
    PRE_MELTDOWN(ChatFormatting.RED),
    MELTDOWN_1(ChatFormatting.DARK_RED),
    MELTDOWN_2(ChatFormatting.DARK_PURPLE),
    /** Ignition went wrong partway: the black hole is open but not held. Fix the fault or it goes critical. */
    IGNITION_FAULT(ChatFormatting.GOLD),
    /** Shutdown went wrong partway: the reactor can't finish going offline until the fault is fixed. */
    SHUTDOWN_FAULT(ChatFormatting.GOLD);

    public final ChatFormatting color;

    ReactorState(ChatFormatting color) {
        this.color = color;
    }

    /** States where the disc spins and the core produces power. */
    public boolean generates() {
        return this == STABLE || this == WARNING || this == SHUTTING_DOWN || this == PRE_MELTDOWN;
    }

    public boolean isMeltdown() {
        return this == MELTDOWN_1 || this == MELTDOWN_2;
    }

    public boolean isFault() {
        return this == IGNITION_FAULT || this == SHUTDOWN_FAULT;
    }

    public boolean isAlarm() {
        return this == DISC_STALL || this == PRE_MELTDOWN || isMeltdown() || isFault();
    }

    public String translationKey() {
        return "state.masteris_bhe." + name().toLowerCase();
    }

    public static ReactorState byId(int id) {
        ReactorState[] values = values();
        return id >= 0 && id < values.length ? values[id] : OFFLINE;
    }
}
