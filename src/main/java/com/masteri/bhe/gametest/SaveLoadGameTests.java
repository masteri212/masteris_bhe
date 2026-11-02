package com.masteri.bhe.gametest;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.block.entity.ReactorCoreBlockEntity.Fault;
import com.masteri.bhe.reactor.DarkZones;
import com.masteri.bhe.reactor.ReactorState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

import static com.masteri.bhe.gametest.FailureGameTests.expect;
import static com.masteri.bhe.gametest.ReactorGameTests.ARENA;
import static com.masteri.bhe.gametest.ReactorGameTests.CORE;
import static com.masteri.bhe.gametest.ReactorGameTests.call;
import static com.masteri.bhe.gametest.ReactorGameTests.callStatic;
import static com.masteri.bhe.gametest.ReactorGameTests.get;
import static com.masteri.bhe.gametest.ReactorGameTests.ignite;
import static com.masteri.bhe.gametest.ReactorGameTests.set;

/**
 * A reactor saved in the middle of things (and loaded back, as when a world is reopened or its chunk reloads) comes
 * back exactly as it was: its state and countdown, the failures waiting underneath, its spin and settings. The same
 * for the dark energy zones.
 */
@GameTestHolder(MasteriBHE.MODID)
@PrefixGameTestTemplate(false)
public class SaveLoadGameTests {
    /** Everything the core saves (see ReactorCoreBlockEntity#saveAdditional), by field name. */
    private static final List<String> SAVED = List.of("state", "stability", "rpm", "resonanceDrift", "wobble", "precession",
            "axialTrim", "stress", "massDebt", "offset", "targetOffset", "laserPower", "stateTimer", "stateDuration",
            "fault", "faultMask", "faultFixed", "pendingFault", "breached", "scrammed", "stabilizerGrace");

    /** Pre-meltdown with a disc stall waiting underneath, odd values everywhere, a few seconds into the grace. */
    @GameTest(template = ARENA)
    public static void stackedFailuresSurviveReload(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignite(helper, false);
        call(core, "enterDiscStall");
        call(core, "escalateTimedOut", ReactorState.PRE_MELTDOWN, 1234);
        expect(helper, core, ReactorState.PRE_MELTDOWN);
        set(core, "stability", 27.5);
        set(core, "rpm", -1830.25);
        set(core, "stress", 42.5);
        set(core, "offset", -4);
        set(core, "targetOffset", 7);
        set(core, "laserPower", 30);
        set(core, "axialTrim", 3);
        set(core, "wobble", 12.75);
        set(core, "stabilizerGrace", 345);
        set(core, "breached", true);
        core.getEnergy().setEnergy(7_654_321);
        compareAfterReload(helper, core);
        helper.succeed();
    }

    /** An ignition fault, partly fixed: which stabilizers are still out survives too. */
    @GameTest(template = ARENA)
    public static void faultSurvivesReload(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignite(helper, false);
        call(core, "startIgnitionFault");
        set(core, "fault", Fault.STABILIZER_DESYNC);
        set(core, "faultMask", 1 << Direction.WEST.get2DDataValue());
        set(core, "stateTimer", 777);
        compareAfterReload(helper, core);
        helper.succeed();
    }

    /** A shutdown with a fault on the way: the pending fault survives. */
    @GameTest(template = ARENA)
    public static void pendingShutdownFaultSurvivesReload(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignite(helper, false);
        call(core, "enterState", ReactorState.SHUTTING_DOWN, 0);
        set(core, "pendingFault", Fault.SINGULARITY_REBOUND);
        set(core, "scrammed", true);
        compareAfterReload(helper, core);
        helper.succeed();
    }

    /** A zone two nodes down survives a save and load of the level's zone data. */
    @GameTest(template = ARENA)
    public static void zonesSurviveReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(CORE);
        DarkZones zones = DarkZones.get(level);
        zones.add(level, center, 40);
        zones.stabilize(level, center);
        zones.stabilize(level, center);
        DarkZones.Zone before = zones.at(center);
        try {
            CompoundTag tag = zones.save(new CompoundTag(), level.registryAccess());
            DarkZones loaded = callStatic(DarkZones.class, "load", tag, level.registryAccess());
            DarkZones.Zone after = loaded.at(center);
            helper.assertTrue(before != null && before.equals(after), "zone " + before + " came back as " + after);
        } finally {
            zones.removeAt(level, center); // don't leave a zone over the other tests
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** Saves the core the way the game does, loads that into a fresh core, and compares everything saved. */
    private static void compareAfterReload(GameTestHelper helper, ReactorCoreBlockEntity core) {
        ServerLevel level = helper.getLevel();
        CompoundTag tag = core.saveWithFullMetadata(level.registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(core.getBlockPos(), core.getBlockState(), tag, level.registryAccess());
        helper.assertTrue(loaded instanceof ReactorCoreBlockEntity, "the save should load back as a reactor core");
        ReactorCoreBlockEntity copy = (ReactorCoreBlockEntity) loaded;
        for (String field : SAVED) {
            Object a = get(core, field), b = get(copy, field);
            helper.assertTrue(Objects.equals(a, b), field + ": saved " + a + ", loaded " + b);
        }
        helper.assertTrue(core.getEnergy().getEnergyStored() == copy.getEnergy().getEnergyStored(), "the stored charge should survive");
        ArrayDeque<?> waiting = get(core, "suspended"), waitingAfter = get(copy, "suspended");
        helper.assertTrue(List.copyOf(waiting).equals(List.copyOf(waitingAfter)),
                "the failures waiting underneath should survive: " + waiting + " vs " + waitingAfter);
    }
}
