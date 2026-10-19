package com.masteri.bhe.research;

import com.masteri.bhe.registry.ModItems;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/** The six reactor failures the Research Chamber can recreate, each recorded as its own crafting component. */
public enum Experiment {
    DISC_STALL(4, ModItems.RECORDING_DISC_STALL),
    OVERSPIN(4, ModItems.RECORDING_OVERSPIN),
    STARVATION(5, ModItems.RECORDING_STARVATION),
    PRE_MELTDOWN(5, ModItems.RECORDING_PRE_MELTDOWN),
    BREACH(6, ModItems.RECORDING_BREACH),
    MELTDOWN(6, ModItems.RECORDING_MELTDOWN);

    /** The longest procedure, for sizing the synced data. */
    public static final int MAX_STEPS = 6;

    public final int steps;
    private final Supplier<? extends Item> recording;

    Experiment(int steps, Supplier<? extends Item> recording) {
        this.steps = steps;
        this.recording = recording;
    }

    public Item recording() {
        return recording.get();
    }

    public String translationKey() {
        return "experiment.masteris_bhe." + name().toLowerCase();
    }

    public static Experiment byId(int id) {
        Experiment[] all = values();
        return id >= 0 && id < all.length ? all[id] : DISC_STALL;
    }
}
