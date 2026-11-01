package com.masteri.bhe.gametest;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.block.entity.MassInputPortBlockEntity;
import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.multiblock.ReactorStructure;
import com.masteri.bhe.reactor.ReactorReadout;
import com.masteri.bhe.reactor.ReactorState;
import com.masteri.bhe.registry.ModBlocks;
import com.masteri.bhe.util.Advancements;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import com.mojang.authlib.GameProfile;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Server-side checks of the reactor, run headless with {@code gradlew runGameTestServer}. Each test builds a reactor
 * in an empty arena (structure/gametest/reactor_arena), with the core in its middle. Not shipped in the release jar.
 */
@GameTestHolder(MasteriBHE.MODID)
@PrefixGameTestTemplate(false)
public class ReactorGameTests {
    private static final String ARENA = "gametest/reactor_arena";
    private static final BlockPos CORE = new BlockPos(32, 4, 32);
    /** Slots of the reactor's synced stats (see ReactorCoreBlockEntity's ContainerData). */
    private static final int SLOT_UNPOWERED = 32;

    // ------------------------------------------------------------------ stabilizer power

    /** Right after ignition the stabilizers run on the ignition charge: none unpowered, stability untouched. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void graceKeepsStabilizersPowered(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignite(helper, false);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(core.getState() == ReactorState.STABLE, "reactor should be running, is " + core.getState());
            helper.assertTrue(core.getData().get(SLOT_UNPOWERED) == 0, "no stabilizer should be unpowered during the grace period");
            helper.assertTrue(core.getStability() >= 99, "stability should hold during the grace period, is " + core.getStability());
            helper.succeed();
        });
    }

    /** No axial stabilizers (no spin power), no stored RF: once the grace is over, stability drains into pre-meltdown. */
    @GameTest(template = ARENA, timeoutTicks = 900)
    public static void unpoweredStabilizersDrainStabilityToZero(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignite(helper, false);
        set(core, "stabilizerGrace", 0);
        helper.runAfterDelay(20, () -> helper.assertTrue(core.getData().get(SLOT_UNPOWERED) == 4,
                "all 4 stabilizers should be unpowered, " + core.getData().get(SLOT_UNPOWERED) + " are"));
        long start = helper.getTick();
        helper.succeedWhen(() -> {
            helper.assertTrue(core.getState() == ReactorState.PRE_MELTDOWN,
                    "stability should drain into pre-meltdown; it's at " + core.getStability() + " (" + core.getState() + ")");
            // At 0.2 a tick, 100% down to the pre-meltdown line takes about 400 ticks: a gradual drain, not a drop.
            helper.assertTrue(helper.getTick() - start > 300, "the drain should be gradual, took " + (helper.getTick() - start) + " ticks");
        });
    }

    /** All six stabilizers powered from a charged buffer: none unpowered, stability stays up. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void poweredStabilizersStayStable(GameTestHelper helper) {
        ReactorCoreBlockEntity core = ignite(helper, true);
        set(core, "stabilizerGrace", 0);
        core.getEnergy().generate(500_000_000L);
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(core.getData().get(SLOT_UNPOWERED) == 0, "no stabilizer should be unpowered with a charged buffer");
            helper.assertTrue(core.getStability() >= 95, "stability should stay up, is " + core.getStability());
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ synced stats

    /** Spin past 16 bits (either way) survives the 16-bit container sync, and so does the resonance band. */
    @GameTest(template = ARENA)
    public static void spinSyncsPast16Bits(GameTestHelper helper) {
        ReactorCoreBlockEntity core = build(helper, false);
        ContainerData data = core.getData();
        // What a client sees: every slot goes over the wire as a signed 16-bit number.
        ReactorReadout client = slot -> (short) data.get(slot);
        for (double spin : new double[] {40_000, -40_000, 123_456, -5}) {
            set(core, "rpm", spin);
            helper.assertTrue(client.rpm() == (int) spin, "spin " + spin + " read back as " + client.rpm());
        }
        int band = (int) Math.round(core.resonanceRpm());
        helper.assertTrue(client.resonanceRpm() == band, "band " + band + " read back as " + client.resonanceRpm());
        helper.succeed();
    }

    // ------------------------------------------------------------------ advancements

    /** The advancements awarded from code exist, and awarding one completes it. */
    @GameTest(template = ARENA)
    public static void codeAdvancementsCanBeAwarded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A fake player: no connection, so nothing tries to send it packets.
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "bhe-test"));
        // Fake players ignore advancements, so track this one's in a real tracker of its own (saved to a temp file).
        PlayerAdvancements tracker;
        try {
            tracker = new PlayerAdvancements(level.getServer().getFixerUpper(), level.getServer().getPlayerList(),
                    level.getServer().getAdvancements(), Files.createTempFile("bhe-advancements", ".json"), player);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        for (String name : new String[] {Advancements.IGNITION, Advancements.CLOSE_CALL, Advancements.GROUND_ZERO, Advancements.LAID_TO_REST}) {
            AdvancementHolder advancement = level.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, name));
            helper.assertTrue(advancement != null, "advancement " + name + " should exist");
            helper.assertTrue(Advancements.award(player, name), name + " should be found by Advancements.award");
            tracker.award(advancement, "done"); // the criterion Advancements.award grants
            helper.assertTrue(tracker.getOrStartProgress(advancement).isDone(), name + " should be done once its \"done\" criterion is granted");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** A complete reactor (axial stabilizers if asked) with plenty of mass on every stabilizer, not yet running. */
    private static ReactorCoreBlockEntity build(GameTestHelper helper, boolean axial) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CORE);
        level.setBlock(pos, ModBlocks.REACTOR_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        ReactorStructure.build(level, pos);
        if (axial) ReactorStructure.buildAxial(level, pos);
        if (!(level.getBlockEntity(pos) instanceof ReactorCoreBlockEntity core)) throw new IllegalStateException("no reactor core");
        helper.assertTrue(core.checkStructure().complete(), "the reactor should be complete");
        // Twice the ignition minimum on each, so ignition isn't thin on mass (a thin reactor faults far more often).
        for (MassInputPortBlockEntity port : core.massPorts()) port.addMass(2L * ReactorConfig.MIN_MASS_PER_STABILIZER.get());
        return core;
    }

    /** Builds a reactor and ignites it. Ignition can still fault at random: then it's put straight into running. */
    private static ReactorCoreBlockEntity ignite(GameTestHelper helper, boolean axial) {
        ReactorCoreBlockEntity core = build(helper, axial);
        core.fillForTesting(); // the ignition charge
        core.handleButton(null, ReactorCoreBlockEntity.BUTTON_IGNITE);
        if (core.getState() != ReactorState.STABLE) {
            set(core, "state", ReactorState.STABLE);
            set(core, "fault", ReactorCoreBlockEntity.Fault.NONE);
            set(core, "faultMask", 0);
            set(core, "stability", 100.0);
            set(core, "stabilizerGrace", ReactorConfig.STABILIZER_GRACE_SECONDS.get() * 20);
        }
        return core;
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("can't set " + field, e);
        }
    }
}
