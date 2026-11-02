package com.masteri.bhe.gametest;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.block.BreakerPanelBlock;
import com.masteri.bhe.block.entity.MassInputPortBlockEntity;
import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.block.entity.ReactorCoreBlockEntity.Fault;
import com.masteri.bhe.block.entity.ReactorCoreBlockEntity.FixResult;
import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.multiblock.ReactorStructure;
import com.masteri.bhe.reactor.ReactorState;
import com.masteri.bhe.reactor.VentingLance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.masteri.bhe.gametest.ReactorGameTests.ARENA;
import static com.masteri.bhe.gametest.ReactorGameTests.build;
import static com.masteri.bhe.gametest.ReactorGameTests.call;
import static com.masteri.bhe.gametest.ReactorGameTests.get;
import static com.masteri.bhe.gametest.ReactorGameTests.ignite;
import static com.masteri.bhe.gametest.ReactorGameTests.set;

/**
 * Every failure, its fixes, and what happens when it isn't fixed: disc stalls, ignition and shutdown faults,
 * pre-meltdown, meltdown, venting, scrams and breaches. Failures are set off directly (the same calls the reactor
 * makes) rather than by playing the reactor into them.
 */
@GameTestHolder(MasteriBHE.MODID)
@PrefixGameTestTemplate(false)
public class FailureGameTests {
    private static final int BUTTON_JUMP = ReactorCoreBlockEntity.BUTTON_JUMP_START;
    /** Synced stat slots: failures waiting underneath, and the state of the next one. */
    private static final int SLOT_PENDING = 26, SLOT_PENDING_STATE = 27;

    // ------------------------------------------------------------------ disc stall

    /** JUMP before every breaker is overcharged costs time; with all four it restarts the disc and resets them. */
    @GameTest(template = ARENA)
    public static void discStallJumpStart(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignite(helper, false);
        call(core, "enterDiscStall");
        expect(helper, core, ReactorState.DISC_STALL);
        int before = get(core, "stateTimer");
        core.handleButton(null, BUTTON_JUMP);
        int after = get(core, "stateTimer");
        expect(helper, core, ReactorState.DISC_STALL);
        helper.assertTrue(before - after >= ReactorConfig.JUMP_PENALTY_SECONDS.get() * 20,
                "an early JUMP should cost time: " + before + " -> " + after);

        setBreakers(helper, core, true);
        helper.assertTrue(core.isJumpReady(), "all four breakers overcharged should make JUMP ready");
        core.handleButton(null, BUTTON_JUMP);
        expect(helper, core, ReactorState.WARNING);
        double rpm = get(core, "rpm");
        helper.assertTrue(Math.abs(rpm) >= 600, "the disc should be kicked back into spin, rpm " + rpm);
        helper.assertTrue(core.breakerMask() == 0, "the breakers should drop back to normal");
        helper.succeed();
    }

    /** A stall that runs out goes to pre-meltdown, waits underneath it, and comes back once pre-meltdown is saved. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void discStallTimesOutThenResumes(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignite(helper, false);
        call(core, "enterDiscStall");
        set(core, "stateTimer", 1);
        set(core, "stability", 10.0); // hold pre-meltdown until we let it recover
        helper.runAfterDelay(3, () -> {
            expect(helper, core, ReactorState.PRE_MELTDOWN);
            helper.assertTrue(core.getData().get(SLOT_PENDING) == 1
                            && core.getData().get(SLOT_PENDING_STATE) == ReactorState.DISC_STALL.ordinal(),
                    "the stall should wait underneath the pre-meltdown");
            set(core, "stability", 100.0);
        });
        helper.runAfterDelay(4, () -> helper.succeedWhen(() -> {
            expect(helper, core, ReactorState.DISC_STALL);
            int timer = get(core, "stateTimer");
            helper.assertTrue(timer >= 15 * 20 - 40, "the stall should come back with time on it, has " + timer);
        }));
    }

    /** Scramming at low spin shuts down; at high spin the stabilizers snap back and stall the disc. */
    @GameTest(template = ARENA)
    public static void scramShutsDownOrStalls(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignite(helper, false);
        core.handleButton(null, ReactorCoreBlockEntity.BUTTON_SCRAM);
        expect(helper, core, ReactorState.SHUTTING_DOWN);
        set(core, "rpm", ReactorConfig.SAFE_RPM.get() * 1.2);
        core.handleButton(null, ReactorCoreBlockEntity.BUTTON_SCRAM);
        expect(helper, core, ReactorState.DISC_STALL);
        helper.assertTrue(core.getData().get(SLOT_PENDING) == 1, "the interrupted shutdown should wait underneath");
        helper.succeed();
    }

    // ------------------------------------------------------------------ ignition faults

    /** Desync: RESET does nothing until every knocked-out emitter is realigned, then the reactor runs. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void ignitionDesyncFix(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignitionFault(helper, Fault.STABILIZER_DESYNC, Direction.NORTH, Direction.EAST);
        core.handleButton(null, BUTTON_JUMP);
        expect(helper, core, ReactorState.IGNITION_FAULT);
        helper.assertTrue(core.realignEmitter(emitter(core, Direction.SOUTH)) == FixResult.NOT_NEEDED,
                "an emitter the fault didn't hit needs no fix");
        helper.assertTrue(core.realignEmitter(emitter(core, Direction.NORTH)) == FixResult.FIXED, "north should realign");
        helper.assertTrue(core.realignEmitter(emitter(core, Direction.EAST)) == FixResult.ALL_FIXED, "east should finish it");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(core.isFaultFixed(), "the fault should read as fixed");
            core.handleButton(null, BUTTON_JUMP);
            expect(helper, core, ReactorState.STABLE);
            helper.succeed();
        });
    }

    /** Mass flash: fixed only once every stabilizer is back up to the ignition minimum. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void ignitionMassFlashFix(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignitionFault(helper, Fault.MASS_FLASH, Direction.NORTH);
        MassInputPortBlockEntity north = port(core, Direction.NORTH);
        long min = ReactorConfig.MIN_MASS_PER_STABILIZER.get();
        north.drain(north.getMass() - min / 2);
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(core.isFaultFixed(), "a stabilizer under the minimum isn't fixed");
            core.handleButton(null, BUTTON_JUMP);
            expect(helper, core, ReactorState.IGNITION_FAULT);
            north.addMass(min);
        });
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(core.isFaultFixed(), "refilled, the fault should read as fixed");
            core.handleButton(null, BUTTON_JUMP);
            expect(helper, core, ReactorState.STABLE);
            helper.succeed();
        });
    }

    /** An unfixed fault runs out into pre-meltdown, and waits underneath it. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void ignitionFaultTimesOut(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignitionFault(helper, Fault.STABILIZER_DESYNC, Direction.WEST);
        set(core, "stateTimer", 1);
        helper.runAfterDelay(3, () -> {
            expect(helper, core, ReactorState.PRE_MELTDOWN);
            helper.assertTrue(core.getData().get(SLOT_PENDING_STATE) == ReactorState.IGNITION_FAULT.ordinal(),
                    "the fault should wait underneath");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ shutdown faults

    /** Cracked lens: recalibrating the cracked stabilizer's lens fixes it, and RESET finishes the shutdown. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void shutdownCrackedLensFix(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignite(helper, false);
        call(core, "startShutdownFault", Fault.CRACKED_LENS);
        expect(helper, core, ReactorState.SHUTDOWN_FAULT);
        Direction cracked = null;
        for (Direction dir : Direction.Plane.HORIZONTAL) if (core.isFaulted(dir)) cracked = dir;
        helper.assertTrue(cracked != null, "one lens should be cracked");
        helper.assertTrue(core.recalibrateLens(cracked) == FixResult.ALL_FIXED, "recalibrating it should fix it");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(core.isFaultFixed(), "the fault should read as fixed");
            core.handleButton(null, BUTTON_JUMP);
            expect(helper, core, ReactorState.SHUTTING_DOWN);
            helper.succeed();
        });
    }

    /** Singularity rebound: fixed once the core's charge is drained under the limit; RESET then goes offline. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void shutdownReboundFix(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignite(helper, false);
        core.getEnergy().generate(ReactorConfig.REBOUND_DRAIN_RF.get() * 4L);
        call(core, "startShutdownFault", Fault.SINGULARITY_REBOUND);
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(core.isFaultFixed(), "with the charge still in, it isn't fixed");
            core.getEnergy().setEnergy(0);
        });
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(core.isFaultFixed(), "drained, the fault should read as fixed");
            core.handleButton(null, BUTTON_JUMP);
            expect(helper, core, ReactorState.OFFLINE);
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ pre-meltdown and meltdown

    /** Low stability sends it into pre-meltdown; with stability back it recovers to warning. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void preMeltdownRecovers(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignite(helper, false);
        set(core, "stability", 10.0);
        helper.runAfterDelay(2, () -> {
            expect(helper, core, ReactorState.PRE_MELTDOWN);
            set(core, "stability", 100.0);
        });
        helper.runAfterDelay(3, () -> helper.succeedWhen(() -> expect(helper, core, ReactorState.WARNING)));
    }

    /** Pre-meltdown that runs out goes to meltdown phase 1, then phase 2. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void preMeltdownTimesOutToMeltdown(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignite(helper, false);
        set(core, "stabilizerGrace", 0); // no power: it can't recover by itself
        set(core, "stability", 0.0);
        helper.runAfterDelay(2, () -> {
            expect(helper, core, ReactorState.PRE_MELTDOWN);
            set(core, "stateTimer", 1);
        });
        helper.runAfterDelay(5, () -> {
            expect(helper, core, ReactorState.MELTDOWN_1);
            set(core, "stateTimer", 1);
        });
        helper.runAfterDelay(8, () -> {
            expect(helper, core, ReactorState.MELTDOWN_2);
            call(core, "enterState", ReactorState.OFFLINE, 0); // stop before it collapses
            helper.succeed();
        });
    }

    /** Taking a ring block out of a running reactor is a breach: straight to pre-meltdown. */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void breachStartsPreMeltdown(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignite(helper, false);
        ServerLevel level = helper.getLevel();
        BlockPos ring = core.getBlockPos().offset(ReactorStructure.layout().get(0).offset());
        level.setBlock(ring, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        helper.succeedWhen(() -> {
            expect(helper, core, ReactorState.PRE_MELTDOWN);
            helper.assertTrue(get(core, "breached"), "it should count as a breach");
        });
    }

    // ------------------------------------------------------------------ venting

    /** A normal vent (armed, then fired) dumps the charge, burns 15% of the mass and leaves the reactor offline. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void ventBurnsSomeMass(GameTestHelper helper) {
        ReactorCoreBlockEntity core = withLance(helper);
        long before = port(core, Direction.NORTH).getMass();
        core.getEnergy().generate(1_000_000);
        core.handleButton(null, ReactorCoreBlockEntity.BUTTON_VENT);
        helper.assertTrue(core.isVentArmed(), "the first press should arm the lance");
        core.handleButton(null, ReactorCoreBlockEntity.BUTTON_VENT);
        expect(helper, core, ReactorState.OFFLINE);
        long after = port(core, Direction.NORTH).getMass();
        double kept = after / (double) before;
        double expected = 1 - ReactorConfig.VENT_MASS_FRACTION.get();
        helper.assertTrue(Math.abs(kept - expected) < 0.01, "should keep " + expected + " of the mass, kept " + kept);
        helper.assertTrue(core.getEnergy().getEnergyStored() == 0, "the charge should be dumped");
        helper.succeed();
    }

    /** In meltdown phase 1 the vent is the emergency one: all the mass goes. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void emergencyVentBurnsAllMass(GameTestHelper helper) {
        ReactorCoreBlockEntity core = withLance(helper);
        call(core, "enterState", ReactorState.MELTDOWN_1, 1200);
        core.handleButton(null, ReactorCoreBlockEntity.BUTTON_VENT);
        core.handleButton(null, ReactorCoreBlockEntity.BUTTON_VENT);
        expect(helper, core, ReactorState.OFFLINE);
        helper.assertTrue(core.isVentEmergency(), "it should be the emergency vent");
        for (MassInputPortBlockEntity port : core.massPorts()) {
            helper.assertTrue(port.getMass() == 0, "every stabilizer's mass should burn, " + port.getMass() + " left");
        }
        helper.succeed();
    }

    /** In meltdown phase 2 it's too late: the vent does nothing. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void ventTooLateInMeltdown2(GameTestHelper helper) {
        ReactorCoreBlockEntity core = withLance(helper);
        call(core, "enterState", ReactorState.MELTDOWN_2, 1200);
        core.handleButton(null, ReactorCoreBlockEntity.BUTTON_VENT);
        core.handleButton(null, ReactorCoreBlockEntity.BUTTON_VENT);
        expect(helper, core, ReactorState.MELTDOWN_2);
        call(core, "enterState", ReactorState.OFFLINE, 0); // stop before it collapses
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    static void expect(GameTestHelper helper, ReactorCoreBlockEntity core, ReactorState state) {
        helper.assertTrue(core.getState() == state, "expected " + state + ", is " + core.getState());
    }

    /** A running reactor that has just gone into an ignition fault of this kind, on these stabilizers. */
    private static ReactorCoreBlockEntity ignitionFault(GameTestHelper helper, Fault fault, Direction... sides) {
        ReactorCoreBlockEntity core = build(helper, false);
        core.fillForTesting();
        call(core, "startIgnitionFault");
        // It picks its own kind and sides at random (and a mass flash drains some mass): make it the one we want.
        for (MassInputPortBlockEntity port : core.massPorts()) {
            long want = 2L * ReactorConfig.MIN_MASS_PER_STABILIZER.get();
            if (port.getMass() < want) port.addMass(want - port.getMass());
        }
        int mask = 0;
        for (Direction dir : sides) mask |= 1 << dir.get2DDataValue();
        set(core, "fault", fault);
        set(core, "faultMask", mask);
        set(core, "faultFixed", false);
        expect(helper, core, ReactorState.IGNITION_FAULT);
        return core;
    }

    /** A running reactor with a Venting Lance over it, already found by the core. */
    private static ReactorCoreBlockEntity withLance(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignite(helper, false);
        VentingLance.build(helper.getLevel(), core.getBlockPos());
        call(core, "findLance");
        helper.assertTrue(core.isLanceFormed(), "the lance should be found");
        return core;
    }

    private static BlockPos emitter(ReactorCoreBlockEntity core, Direction dir) {
        return core.getBlockPos().relative(dir, ReactorStructure.EMITTER_DISTANCE);
    }

    private static MassInputPortBlockEntity port(ReactorCoreBlockEntity core, Direction dir) {
        BlockPos pos = core.getBlockPos().offset(ReactorStructure.massPortOffset(dir));
        if (!(core.getLevel().getBlockEntity(pos) instanceof MassInputPortBlockEntity port)) throw new IllegalStateException("no mass port " + dir);
        return port;
    }

    private static void setBreakers(GameTestHelper helper, ReactorCoreBlockEntity core, boolean overcharged) {
        ServerLevel level = helper.getLevel();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos pos = core.getBlockPos().offset(ReactorStructure.breakerPanelOffset(dir));
            BlockState panel = level.getBlockState(pos);
            helper.assertTrue(panel.hasProperty(BreakerPanelBlock.OVERCHARGED), "no breaker panel " + dir);
            level.setBlock(pos, panel.setValue(BreakerPanelBlock.OVERCHARGED, overcharged), Block.UPDATE_ALL);
        }
    }
}
