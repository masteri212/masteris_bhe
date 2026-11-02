package com.masteri.bhe.gametest;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.block.entity.HorizonGateBlockEntity;
import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.entity.EchoOfTheCoreEntity;
import com.masteri.bhe.horizon.HorizonRing;
import com.masteri.bhe.reactor.Crater;
import com.masteri.bhe.reactor.DarkZones;
import com.masteri.bhe.registry.ModBlocks;
import com.masteri.bhe.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.masteri.bhe.gametest.ReactorGameTests.ARENA;
import static com.masteri.bhe.gametest.ReactorGameTests.CORE;
import static com.masteri.bhe.gametest.ReactorGameTests.call;
import static com.masteri.bhe.gametest.ReactorGameTests.ignite;

/**
 * What reaches out into the world: a collapse (with and without the destroyBlocks setting), its dark energy zone
 * shrinking node by node until the Echo of the Core closes it, and the Horizon Gate's mass cost. Collapses and zones
 * reach far past one test's arena, so each runs in a batch of its own, after the others.
 */
@GameTestHolder(MasteriBHE.MODID)
@PrefixGameTestTemplate(false)
public class WorldGameTests {
    /** Inside the crater's radius, well clear of the reactor's own blocks (in the ring's open middle). */
    private static final BlockPos MARKER = CORE.offset(6, -1, 0);

    // ------------------------------------------------------------------ collapse

    /** destroyBlocks off: the core collapses and leaves a zone, but the blocks around it stay. */
    @GameTest(template = ARENA, batch = "collapse_keep", timeoutTicks = 200)
    public static void collapseKeepsBlocksWhenDestroyOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean was = ReactorConfig.DESTROY_BLOCKS.get();
        ReactorConfig.DESTROY_BLOCKS.set(false);
        BlockPos marker = helper.absolutePos(MARKER);
        level.setBlock(marker, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        ReactorCoreBlockEntity core = ignite(helper, false);
        BlockPos at = core.getBlockPos();
        call(core, "collapse");
        try {
            helper.assertTrue(level.getBlockState(at).isAir(), "the core should be gone");
            helper.assertFalse(Crater.isCarving(level, at), "no crater should be carved");
        } catch (RuntimeException e) {
            ReactorConfig.DESTROY_BLOCKS.set(was); // a failed test mustn't leave its setting or zone behind
            removeZones(level, at);
            throw e;
        }
        helper.runAfterDelay(60, () -> {
            try {
                helper.assertTrue(level.getBlockState(marker).is(Blocks.STONE), "blocks near the core should be left alone");
                expectZone(helper, level, at);
            } finally {
                ReactorConfig.DESTROY_BLOCKS.set(was);
                removeZones(level, at);
            }
            helper.succeed();
        });
    }

    /** destroyBlocks on: the collapse carves a crater (the marker goes) and leaves a zone. */
    @GameTest(template = ARENA, batch = "collapse_destroy", timeoutTicks = 400)
    public static void collapseCarvesCraterWhenDestroyOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean was = ReactorConfig.DESTROY_BLOCKS.get();
        ReactorConfig.DESTROY_BLOCKS.set(true);
        BlockPos marker = helper.absolutePos(MARKER);
        level.setBlock(marker, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        ReactorCoreBlockEntity core = ignite(helper, false);
        BlockPos at = core.getBlockPos();
        call(core, "collapse");
        ReactorConfig.DESTROY_BLOCKS.set(was); // the crater has started: the setting has done its part
        try {
            helper.assertTrue(Crater.isCarving(level, at), "a crater should be carving");
            expectZone(helper, level, at);
        } catch (RuntimeException e) {
            removeZones(level, at); // a failed test mustn't leave its zone behind
            throw e;
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getBlockState(marker).isAir(), "the crater should take the blocks near the core");
            removeZones(level, at);
        });
    }

    // ------------------------------------------------------------------ zones

    /** Each stabilized node takes an equal share off the radius; the Echo's death takes the last node and the zone. */
    @GameTest(template = ARENA, batch = "zones", timeoutTicks = 100)
    public static void zoneShrinksThenEchoClosesIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(CORE);
        DarkZones zones = DarkZones.get(level);
        removeZones(level, center); // anything an earlier test left here would be found first
        zones.add(level, center, 400);
        try {
            DarkZones.Zone zone = zones.at(center);
            helper.assertTrue(zone != null && zone.nodes() == ReactorConfig.ZONE_NODES.get(), "a new zone should have every node");
            while (zone.nodes() > 1) {
                DarkZones.Stabilized result = zones.stabilize(level, center);
                int expected = Math.max(1, zone.radius() - zone.radius() / zone.nodes());
                helper.assertTrue(result != null && result.zone() != null, "a node short of the last should leave the zone");
                helper.assertTrue(result.zone().radius() == expected && result.zone().nodes() == zone.nodes() - 1,
                        "radius " + zone.radius() + " -> " + result.zone().radius() + " (expected " + expected + ")");
                zone = result.zone();
            }
            // The last node is the Echo's: its death closes the zone.
            EchoOfTheCoreEntity echo = ModEntities.ECHO_OF_THE_CORE.get().create(level);
            helper.assertTrue(echo != null, "the Echo should spawn");
            echo.moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
            level.addFreshEntity(echo);
            echo.die(level.damageSources().genericKill());
            helper.assertTrue(zones.at(center) == null, "the Echo's death should close the zone");
            echo.discard();
        } finally {
            removeZones(level, center);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ the Horizon Gate

    /** A built, clear ring only opens with a trip's worth of mass in its gate. */
    @GameTest(template = ARENA)
    public static void horizonGateNeedsATripsMass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos gate = helper.absolutePos(new BlockPos(32, 1, 32));
        level.setBlock(gate, ModBlocks.HORIZON_GATE.get().defaultBlockState(), Block.UPDATE_ALL);
        HorizonRing.build(level, gate, Direction.Axis.X);
        helper.assertTrue(HorizonRing.find(level, gate) != null, "the ring should be complete");
        if (!(level.getBlockEntity(gate) instanceof HorizonGateBlockEntity be)) throw new IllegalStateException("no gate");
        be.addMass(HorizonGateBlockEntity.tripCost() - 1);
        be.activate(null);
        helper.assertTrue(be.getPhase() == HorizonGateBlockEntity.Phase.IDLE, "one mass short, it shouldn't open");
        be.addMass(1);
        be.activate(null);
        helper.assertTrue(be.getPhase() == HorizonGateBlockEntity.Phase.SPINNING, "with a trip's mass it should spin up");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** A collapse's zone: centered on the crater floor, as big as the reactor's mass makes it. */
    private static void expectZone(GameTestHelper helper, ServerLevel level, BlockPos core) {
        DarkZones.Zone zone = null;
        for (DarkZones.Zone z : DarkZones.get(level).zones()) {
            if (z.center().getX() == core.getX() && z.center().getZ() == core.getZ()) zone = z;
        }
        helper.assertTrue(zone != null, "the collapse should leave a dark energy zone");
        helper.assertTrue(zone.center().getY() < core.getY(), "the zone should be centered on the crater floor, below the core");
        helper.assertTrue(zone.nodes() == ReactorConfig.ZONE_NODES.get(), "it should have every node");
    }

    /** Takes out every zone centered over this column (so they don't spill into later tests). */
    private static void removeZones(ServerLevel level, BlockPos core) {
        DarkZones zones = DarkZones.get(level);
        for (DarkZones.Zone z : java.util.List.copyOf(zones.zones())) {
            if (z.center().getX() == core.getX() && z.center().getZ() == core.getZ()) zones.removeAt(level, z.center());
        }
    }
}
