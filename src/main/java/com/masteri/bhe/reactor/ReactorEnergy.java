package com.masteri.bhe.reactor;

import net.neoforged.neoforge.energy.EnergyStorage;

/** The core's RF buffer. Accepts charge for ignition and holds generated power until ports push it out. */
public class ReactorEnergy extends EnergyStorage {
    public static final int CAPACITY = 1_000_000_000;

    public ReactorEnergy() {
        super(CAPACITY, CAPACITY, CAPACITY);
    }

    public void generate(long amount) {
        energy = (int) Math.min(capacity, energy + Math.max(0, amount));
    }

    public boolean consume(int amount) {
        if (energy < amount) return false;
        energy -= amount;
        return true;
    }

    public void setEnergy(int value) {
        energy = Math.max(0, Math.min(capacity, value));
    }
}
