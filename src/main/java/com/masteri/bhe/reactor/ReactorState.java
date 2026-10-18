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
    MELTDOWN_2(ChatFormatting.DARK_PURPLE);

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

    public boolean isAlarm() {
        return this == DISC_STALL || this == PRE_MELTDOWN || isMeltdown();
    }

    public String translationKey() {
        return "state.masteris_bhe." + name().toLowerCase();
    }

    public static ReactorState byId(int id) {
        ReactorState[] values = values();
        return id >= 0 && id < values.length ? values[id] : OFFLINE;
    }
}
