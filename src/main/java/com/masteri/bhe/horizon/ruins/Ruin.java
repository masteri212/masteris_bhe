package com.masteri.bhe.horizon.ruins;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.function.Consumer;

/**
 * The Event Horizon's ruins, each built in code (see Ruins). {@code radius} is how far it may reach sideways from
 * its origin, {@code below}/{@code above} how far down and up: the box it's clipped to.
 */
public enum Ruin implements StringRepresentable {
    FROZEN_REACTOR("frozen_reactor", 17, 16, 14, Ruins::frozenReactor),
    SWALLOWED_CHUNK("swallowed_chunk", 9, 15, 10, Ruins::swallowedChunk),
    EXPEDITION_CAMP("expedition_camp", 11, 8, 8, Ruins::expeditionCamp),
    OBSERVATORY("observatory", 10, 10, 12, Ruins::observatory),
    RECORDING_ARCHIVE("recording_archive", 10, 10, 31, Ruins::recordingArchive),
    CITADEL("citadel", 23, 15, 26, Ruins::citadel);

    public static final Codec<Ruin> CODEC = StringRepresentable.fromEnum(Ruin::values);

    private final String name;
    public final int radius, below, above;
    final Consumer<Build> builder;

    Ruin(String name, int radius, int below, int above, Consumer<Build> builder) {
        this.name = name;
        this.radius = radius;
        this.below = below;
        this.above = above;
        this.builder = builder;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
