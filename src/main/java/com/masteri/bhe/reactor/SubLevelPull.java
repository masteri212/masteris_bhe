package com.masteri.bhe.reactor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Lets the black hole reach moving block structures from physics mods (Sable's sub-levels: see compat/sable). The
 * reactor doesn't know about those mods; when one is installed it sets {@link #hook}.
 */
public final class SubLevelPull {
    /**
     * A block torn off a structure: where it was in the world, what it was, and what it held (see BlockContents).
     * Already removed from the structure.
     */
    public record Torn(Vec3 at, BlockState state, BlockContents.Taken taken) {}

    public interface Hook {
        /** This tick, pull structures within {@code reach} of {@code center} toward it, harder with {@code strength}. */
        void pull(ServerLevel level, Vec3 center, double reach, double strength);

        /**
         * Tears up to {@code budget} exposed blocks off structures within {@code reach} of {@code center}, nearest it
         * first, by the same rules as the ground ({@link BlockContents#edible}), taking their contents along.
         */
        List<Torn> tear(ServerLevel level, Vec3 center, double reach, int budget);
    }

    @Nullable public static Hook hook;

    private SubLevelPull() {}
}
