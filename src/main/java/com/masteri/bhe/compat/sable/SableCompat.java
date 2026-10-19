package com.masteri.bhe.compat.sable;

import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.reactor.BlockContents;
import com.masteri.bhe.reactor.DarkZones;
import com.masteri.bhe.reactor.SubLevelPull;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Sable support (only loaded when Sable is installed, see MasteriBHE). A running reactor's black hole pulls Sable's
 * sub-levels (moving block structures, like Create: Aeronautics ships) toward it, and tears their blocks off one by
 * one, nearest first, just like the ground; the torn blocks fly in and become mass. Inside a dark energy zone they
 * get the zone's low gravity too.
 *
 * The pull is a force, so it's applied on Sable's physics tick (several per game tick). A sub-level's blocks live in
 * its plot, a region far out in the same level, so tearing one off is an ordinary setBlock there.
 */
public final class SableCompat {
    public static final String MOD_ID = "sable";
    /** Pulled no faster than this toward the black hole [m/s]: it reels them in rather than flinging them. */
    private static final double MAX_SPEED = 20;
    /** Positions looked at per tear, at most (a big ship is only scanned around the part within reach). */
    private static final int MAX_SCAN = 65_536;
    private static final ResourceLocation FORCE_GROUP = ResourceLocation.fromNamespaceAndPath("sable", "magnetic_force");
    private static final ResourceLocation LEVITATION = ResourceLocation.fromNamespaceAndPath("sable", "levitation");

    private record Source(ServerLevel level, Vec3 center, double reach, double strength, long tick) {}

    /** The black holes pulling this game tick (refreshed every tick by the reactors that are running). */
    private static final List<Source> SOURCES = new ArrayList<>();

    public static void init() {
        SubLevelPull.hook = new Hook();
        NeoForge.EVENT_BUS.addListener(SableCompat::onPhysicsTick);
    }

    // ------------------------------------------------------------------ pulling

    private static void onPhysicsTick(ForgeSablePrePhysicsTickEvent event) {
        ServerLevel level = event.getPhysicsSystem().getLevel();
        long now = level.getGameTime();
        SOURCES.removeIf(s -> now - s.tick > 2);
        DarkZones zones = DarkZones.get(level);
        if (SOURCES.isEmpty() && zones.zones().isEmpty()) return;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        ForceGroup group = ForceGroups.REGISTRY.get(FORCE_GROUP);
        ForceGroup levitation = ForceGroups.REGISTRY.get(LEVITATION);
        if (group == null || levitation == null) return;
        double timeStep = event.getTimeStep();
        for (ServerSubLevel sub : container.getAllSubLevels()) {
            if (sub.isRemoved()) continue;
            MassData mass = sub.getMassTracker();
            if (mass == null || mass.isInvalid()) continue;
            Pose3d pose = sub.logicalPose();
            Vector3d com = pose.transformPosition(mass.getCenterOfMass(), new Vector3d());
            if (zones.at(com.x, com.y, com.z) != null) lighten(level, sub, pose, mass, com, levitation, timeStep);
            for (Source source : SOURCES) {
                if (source.level != level) continue;
                Vector3d toHole = new Vector3d(source.center.x - com.x, source.center.y - com.y, source.center.z - com.z);
                double dist = toHole.length();
                if (dist > source.reach || dist < 1e-3) continue;
                toHole.div(dist);
                if (sub.latestLinearVelocity.dot(toHole) > MAX_SPEED) continue; // already rushing in
                // Like everything else it pulls: gentle at the edge of its reach, overwhelming near the horizon [m/s^2].
                double closeness = 1 - dist / source.reach;
                double acceleration = (4 + 36 * closeness * closeness) * source.strength;
                Vector3d impulse = toHole.mul(acceleration * mass.getMass() * timeStep);
                Vector3d local = pose.transformNormalInverse(impulse, new Vector3d());
                sub.getOrCreateQueuedForceGroup(group).applyAndRecordPointForce(new Vector3d(mass.getCenterOfMass()), local);
            }
        }
    }

    /**
     * Inside a dark energy zone a sub-level is as light as everything else there: an upward push at its center of
     * mass cancels part of Sable's gravity, so it ends up at the zone's gravity multiplier (0.4 by default).
     */
    private static void lighten(ServerLevel level, ServerSubLevel sub, Pose3d pose, MassData mass, Vector3d com,
                                ForceGroup levitation, double timeStep) {
        double cancel = 1 - ReactorConfig.ZONE_GRAVITY.get();
        if (cancel <= 0) return;
        Vector3d gravity = DimensionPhysicsData.getGravity(level, com, new Vector3d());
        Vector3d impulse = gravity.mul(-cancel * mass.getMass() * timeStep);
        Vector3d local = pose.transformNormalInverse(impulse, new Vector3d());
        sub.getOrCreateQueuedForceGroup(levitation).applyAndRecordPointForce(new Vector3d(mass.getCenterOfMass()), local);
    }

    private static final class Hook implements SubLevelPull.Hook {
        @Override
        public void pull(ServerLevel level, Vec3 center, double reach, double strength) {
            long now = level.getGameTime();
            SOURCES.removeIf(s -> s.level == level && s.center.equals(center));
            SOURCES.add(new Source(level, center, reach, strength, now));
        }

        // ------------------------------------------------------------------ tearing

        private record Candidate(double dist2, BlockPos pos, BlockState state, Vec3 at) {}

        @Override
        public List<SubLevelPull.Torn> tear(ServerLevel level, Vec3 center, double reach, int budget) {
            List<SubLevelPull.Torn> torn = new ArrayList<>();
            if (budget <= 0) return torn;
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) return torn;
            // The nearest {@code budget} candidates across every structure in reach (a max-heap, farthest on top).
            PriorityQueue<Candidate> nearest = new PriorityQueue<>(Comparator.comparingDouble(Candidate::dist2).reversed());
            double reach2 = reach * reach;
            int scanned = 0;
            for (ServerSubLevel sub : container.getAllSubLevels()) {
                if (sub.isRemoved() || !withinReach(sub.boundingBox(), center, reach)) continue;
                Pose3d pose = sub.logicalPose();
                BoundingBox3ic plot = sub.getPlot().getBoundingBox();
                if (plot.minX() > plot.maxX()) continue; // empty
                // Only the part of its plot that can be within reach: a box around the black hole in its own frame.
                Vector3d local = pose.transformPositionInverse(new Vector3d(center.x, center.y, center.z), new Vector3d());
                int r = (int) Math.ceil(reach) + 1;
                int x0 = Math.max(plot.minX(), (int) Math.floor(local.x) - r), x1 = Math.min(plot.maxX(), (int) Math.floor(local.x) + r);
                int y0 = Math.max(plot.minY(), (int) Math.floor(local.y) - r), y1 = Math.min(plot.maxY(), (int) Math.floor(local.y) + r);
                int z0 = Math.max(plot.minZ(), (int) Math.floor(local.z) - r), z1 = Math.min(plot.maxZ(), (int) Math.floor(local.z) + r);
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                Vector3d global = new Vector3d();
                for (int x = x0; x <= x1; x++) {
                    for (int y = y0; y <= y1; y++) {
                        for (int z = z0; z <= z1; z++) {
                            if (scanned++ >= MAX_SCAN) break;
                            pos.set(x, y, z);
                            BlockState state = level.getBlockState(pos);
                            if (!BlockContents.edible(level, pos, state)) continue;
                            pose.transformPosition(new Vector3d(x + 0.5, y + 0.5, z + 0.5), global);
                            double d2 = global.distanceSquared(center.x, center.y, center.z);
                            if (d2 > reach2 || !exposed(level, pos)) continue;
                            if (nearest.size() >= budget && d2 >= nearest.peek().dist2()) continue;
                            nearest.add(new Candidate(d2, pos.immutable(), state, new Vec3(global.x, global.y, global.z)));
                            if (nearest.size() > budget) nearest.poll();
                        }
                    }
                }
            }
            for (Candidate c : nearest) {
                BlockContents.Taken taken = BlockContents.take(level, c.pos()); // emptied first, so nothing spills
                level.setBlock(c.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                torn.add(new SubLevelPull.Torn(c.at(), c.state(), taken));
            }
            return torn;
        }

        private static boolean withinReach(BoundingBox3dc box, Vec3 center, double reach) {
            double dx = Math.max(0, Math.max(box.minX() - center.x, center.x - box.maxX()));
            double dy = Math.max(0, Math.max(box.minY() - center.y, center.y - box.maxY()));
            double dz = Math.max(0, Math.max(box.minZ() - center.z, center.z - box.maxZ()));
            return dx * dx + dy * dy + dz * dz <= reach * reach;
        }

        /** Only blocks on the structure's surface come off, like the ground. */
        private static boolean exposed(ServerLevel level, BlockPos pos) {
            for (Direction dir : Direction.values()) {
                if (level.isEmptyBlock(pos.relative(dir))) return true;
            }
            return false;
        }
    }

    private SableCompat() {}
}
