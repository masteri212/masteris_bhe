package com.masteri.bhe.block.entity;

import com.masteri.bhe.block.BreakerPanelBlock;
import com.masteri.bhe.block.ReactorCoreBlock;
import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.multiblock.ReactorStructure;
import com.masteri.bhe.reactor.BlockContents;
import com.masteri.bhe.reactor.MassValues;
import com.masteri.bhe.reactor.Crater;
import com.masteri.bhe.reactor.DarkZones;
import com.masteri.bhe.reactor.EmergencyVent;
import com.masteri.bhe.reactor.ZoneTerrain;
import net.minecraft.world.entity.EntityType;
import com.masteri.bhe.reactor.SubLevelPull;
import net.minecraft.world.item.ItemStack;
import com.masteri.bhe.registry.ModBlocks;
import com.masteri.bhe.registry.ModItems;
import com.masteri.bhe.registry.ModSounds;
import com.masteri.bhe.reactor.ReactorEnergy;
import com.masteri.bhe.reactor.ReactorState;
import com.masteri.bhe.reactor.VentingLance;
import com.masteri.bhe.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The reactor's brain. Holds every stat from the design doc (mass, stability, spin, ring stress, charge)
 * and runs the state machine: offline -> ignition -> stable/warning -> faults -> meltdown -> collapse.
 */
public class ReactorCoreBlockEntity extends BlockEntity {
    public static final int BUTTON_IGNITE = 0, BUTTON_SHUTDOWN = 1, BUTTON_SCRAM = 2, BUTTON_OFFSET_DOWN = 3,
            BUTTON_OFFSET_UP = 4, BUTTON_LASER_DOWN = 5, BUTTON_LASER_UP = 6, BUTTON_JUMP_START = 7, BUTTON_VENT = 8,
            BUTTON_TRIM_NW = 9, BUTTON_TRIM_SE = 10;
    public static final int DATA_SIZE = 30;

    private static final int RECHECK_INTERVAL = 40;
    private static final double MIN_SPIN_SHAKE = 0.2, MAX_SPIN_SHAKE = 2.0;
    private static final double MIN_PULL_SCALE = 0.5, MAX_PULL_SCALE = 3.0;

    private final ReactorEnergy energy = new ReactorEnergy();
    private final List<BlockPos> reactorPorts = new ArrayList<>();
    @Nullable private BlockPos laserPos;

    private boolean formed;
    /** Both axial stabilizers are built: only then does spin make power (see ReactorStructure#axialLayout). */
    private boolean axialFormed;
    private ReactorState state = ReactorState.OFFLINE;
    private double stability = 100, rpm, stress, massDebt;
    /**
     * Spin power (see runReactor). Resonance: the disc only turns spin into RF efficiently near a target rpm, which
     * rises with mass and wanders on its own ({@code resonanceDrift}). Precession: with the axial stabilizers up the
     * disc wobbles toward north-west or south-east ({@code wobble}, -100..100), pushed by {@code precession}
     * (-1..1, wandering) and countered by the axial trim the crew sets on the console.
     */
    private double resonanceDrift, wobble, precession;
    private int axialTrim;
    private double lastEfficiency = 1;
    public static final int MAX_TRIM = 10;
    private int offset, targetOffset, laserPower, stateTimer, stateDuration, lastOutput, ticks;
    private long totalMass;

    // Visual state, synced to clients for the renderer (see getUpdateTag).
    private static final int SYNC_INTERVAL = 5;
    private int lastSyncHash;
    private float clientSpin;          // -1..1 of the visual top speed; negative spins backwards
    private float clientHoleRadius = (float) ReactorStructure.BLACK_HOLE_RADIUS;
    private float discAngle, prevDiscAngle;
    private int clientTicks;
    private float ventHoleRadius = (float) ReactorStructure.BLACK_HOLE_RADIUS;

    // Startup / shutdown / SCRAM animations. The server only says what happened (state, scrammed, scramSeq);
    // the client notices the change and plays the matching animation.
    public enum Anim {
        NONE(0), STARTUP(120), SCRAM(40), POWER_DOWN(70), SCRAM_POWER_DOWN(40),
        STALL(30), ALARM(40), BREACH(50), MELTDOWN_1(60), MELTDOWN_2(80), FAULT(40),
        /** Venting: the black hole drains up the lance, shrinking all the way (as long as the beam, a little more). */
        VENT(110),
        /** Venting out of meltdown phase 1: the black hole fights it, rebounding again and again, then implodes. */
        VENT_EMERGENCY(EmergencyVent.TICKS);
        public final int duration;
        Anim(int duration) { this.duration = duration; }
    }
    private boolean scrammed;          // the current shutdown came from SCRAM: red, no beams
    private int scramSeq;              // bumps on every SCRAM so the client can replay the flash
    private int breachSeq;             // bumps on every containment breach
    private boolean clientStarving;    // stabilizers low on mass: beams thin out and flicker

    /** Cores the client has loaded, for the screen effects (camera shake, red and dark fog). Client side only. */
    public static final Set<ReactorCoreBlockEntity> CLIENT_CORES = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Debug preview of each failure's visuals. While previewing, the reactor is frozen in that state: its timer loops
     * instead of advancing, nothing is simulated, and nothing is destroyed or damaged (a phase 2 preview never collapses).
     */
    public enum Preview {
        DISC_STALL, OVERSPIN, STARVATION, PRE_MELTDOWN, BREACH, MELTDOWN_1, MELTDOWN_2,
        STABILIZER_DESYNC, MASS_FLASH, CRACKED_LENS, SINGULARITY_REBOUND
    }

    /**
     * Ignition and shutdown faults. Each one has its own fix:
     *  - Stabilizer desync (ignition): the marked emitters are knocked out of line. Realign each with the Configurator.
     *  - Mass flash (ignition): the ignition flash burned away most of the marked stabilizers' mass. Refill them to the minimum.
     *  - Cracked lens (shutdown): a marked emitter's lens cracked mid spin-down. Type "lens recalibrate" into that stabilizer's console.
     *  - Singularity rebound (shutdown): the black hole won't close while the core holds charge. Drain the core below the limit.
     * {@code faultMask} marks the stabilizers involved, one bit per side ({@link Direction#get2DDataValue()}).
     */
    public enum Fault { NONE, STABILIZER_DESYNC, MASS_FLASH, CRACKED_LENS, SINGULARITY_REBOUND }
    private Fault fault = Fault.NONE, pendingFault = Fault.NONE;

    // Venting lance (see VentingLance): whether one stands over the core, the two-step arming, and the beam.
    private static final int VENT_ARM_TICKS = 100;
    private boolean lanceFormed, ventEmergency;
    private int ventArmedUntil, ventTicks;
    private int lanceTop = VentingLance.BOTTOM + VentingLance.HEIGHT - 1; // height of the lance's top above the core
    private int faultMask, shutdownTicks;
    /** The fault's cause is fixed; the console's RESET finishes the startup or shutdown. */
    private boolean faultFixed;
    private static final int PREVIEW_TICKS = 300; // 15 s per timed preview, so the end of each phase comes round quickly
    @Nullable private Preview preview;

    // Dark-energy arcs (client side): short-lived bolts from the black hole to whatever they hit.
    public static final int MAX_ARCS = 8, ARC_LIFE = 5;
    private final Vec3[] arcFrom = new Vec3[MAX_ARCS], arcTo = new Vec3[MAX_ARCS];
    private final int[] arcBorn = new int[MAX_ARCS];
    private Anim anim = Anim.NONE;
    private int animStart;
    private boolean clientSynced;

    // Absorption: while the reactor runs, items and mobs inside the ring's hole fall into the black hole and become mass.
    private int massCursor, lastAbsorbSound;
    // Block pull: a cursor sweeping the sphere of influence, nearest the core first, for blocks to tear loose.
    private static final int BLOCK_SCAN_PER_TICK = 400, MAX_TORN_PER_TICK = 6;
    // Lag guards: at most ReactorConfig.MAX_BLOCKS_IN_FLIGHT torn blocks in the air, a capped number of
    // line-of-sight checks per tick, and a block still flying after this long is swallowed where it is.
    private static final int SIGHT_CHECKS_PER_TICK = 64, MAX_FLIGHT_TICKS = 200;
    private int blocksInFlight;
    /** Index into {@link #sphereOffsets}, where this sweep starts (see tearBlocks), and how far out this sweep may tear. */
    private int scanIndex, sweepFrom, sweepLimit = -1;
    /** When the black hole last tore off one of the reactor's own blocks (they go one a second in meltdown phase 1). */
    private int lastStructureTear;
    private static final int STRUCTURE_TEAR_INTERVAL = 20;
    /** The ring broke during this emergency (cleared once the reactor is out of pre-meltdown and the meltdowns). */
    private boolean breached;

    /** Synced to open consoles. Values are 16-bit on the wire, so big numbers are split in two. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            int mass = (int) Math.min(totalMass, Integer.MAX_VALUE);
            return switch (i) {
                case 0 -> state.ordinal();
                case 1 -> (int) Math.round(stability * 10);
                case 2 -> (int) Mth.clamp(Math.round(rpm), Short.MIN_VALUE, Short.MAX_VALUE); // signed 16-bit on the wire
                case 3 -> (int) Math.round(stress * 10);
                case 4 -> offset;
                case 5 -> targetOffset;
                case 6 -> laserPower;
                case 7 -> Math.max(0, stateTimer / 20);
                case 8 -> energy.getEnergyStored() & 0xFFFF;
                case 9 -> energy.getEnergyStored() >>> 16;
                case 10 -> mass & 0xFFFF;
                case 11 -> mass >>> 16;
                case 12 -> formed ? 1 : 0;
                case 13 -> breakerMask();
                case 14 -> lastOutput & 0xFFFF;
                case 15 -> lastOutput >>> 16;
                case 16 -> fault.ordinal();
                case 17 -> faultMask;
                case 18 -> lanceFormed ? 1 : 0;
                case 19 -> ventArmed() ? 1 : 0;
                case 20 -> faultFixed ? 1 : 0;
                case 21 -> axialFormed ? 1 : 0;
                case 22 -> (int) Math.round(resonanceRpm());
                case 23 -> (int) Math.round(lastEfficiency * 100);
                case 24 -> (int) Math.round(wobble);
                case 25 -> axialTrim;
                case 26 -> suspended.size();
                case 27 -> suspended.isEmpty() ? 0 : suspended.peek().state().ordinal();
                case 28 -> suspended.isEmpty() ? 0 : suspended.peek().fault().ordinal();
                case 29 -> suspended.isEmpty() ? 0 : suspended.peek().faultMask();
                default -> 0;
            };
        }

        @Override public void set(int i, int value) {}
        @Override public int getCount() { return DATA_SIZE; }
    };

    public ReactorCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REACTOR_CORE.get(), pos, state);
    }

    // ------------------------------------------------------------------ accessors

    public ReactorEnergy getEnergy() { return energy; }
    public ContainerData getData() { return data; }
    public ReactorState getState() { return state; }
    public double getStability() { return stability; }
    public boolean isFormed() { return formed; }
    public int getOffset() { return offset; }
    public float getClientSpin() { return clientSpin; }
    public float getClientHoleRadius() { return clientHoleRadius; }
    /** The black hole's size just before the vent (the vent burns mass, which would shrink it at once). */
    public float getVentHoleRadius() { return ventHoleRadius; }
    public int getClientTicks() { return clientTicks; }
    public float getDiscAngle(float partialTick) { return Mth.lerp(partialTick, prevDiscAngle, discAngle); }
    public boolean isScrammed() { return scrammed; }
    public double getStress() { return stress; }
    public boolean isClientStarving() { return clientStarving; }
    public Fault getFault() { return fault; }
    public boolean isFaultFixed() { return faultFixed; }
    public boolean isLanceFormed() { return lanceFormed; }
    public boolean isAxialFormed() { return axialFormed; }

    /** Whether the {@code dir} stabilizer's emitter is still standing. Its beam only fires if it is (both sides). */
    public boolean hasEmitter(Direction dir) {
        return level != null && level.getBlockState(worldPosition.relative(dir, ReactorStructure.EMITTER_DISTANCE))
                .is(ModBlocks.STABILIZER_EMITTER.get());
    }

    /** The same for an axial emitter, by its offset from the core. */
    public boolean hasAxialEmitter(BlockPos offset) {
        return level != null && level.getBlockState(worldPosition.offset(offset)).is(ModBlocks.AXIAL_EMITTER.get());
    }
    /** -100..100: how far the disc leans toward the south-east (negative: north-west). Synced for the renderer. */
    public double getWobble() { return wobble; }
    public int getLanceTop() { return lanceTop; }
    public int getVentTicks() { return ventTicks; }
    public boolean isVentEmergency() { return ventEmergency; }
    public boolean isVentArmed() { return ventArmed(); }
    public boolean isFaulted(Direction dir) { return (faultMask & (1 << dir.get2DDataValue())) != 0; }
    /** Disc stall with every stabilizer's breakers overcharged: the jump-start will catch. */
    public boolean isJumpReady() { return state == ReactorState.DISC_STALL && breakerMask() == ALL_SIDES; }
    /** 0..1 through the current timed state (pre-meltdown, meltdown phases, disc stall); 0 if untimed. */
    public float getDangerProgress(float partialTick) {
        return stateDuration <= 0 ? 0 : Mth.clamp(1 - (stateTimer - partialTick) / stateDuration, 0, 1);
    }
    /** The arc in slot {@code k}: start, end (world positions) and the tick it was born; null when the slot is empty. */
    @Nullable public Vec3 getArcFrom(int k) { return arcFrom[k]; }
    @Nullable public Vec3 getArcTo(int k) { return arcTo[k]; }
    public int getArcBorn(int k) { return arcBorn[k]; }
    public Anim getAnim() { return anim; }
    /** 0..1 through the current animation. */
    public float getAnimProgress(float partialTick) {
        return anim == Anim.NONE ? 1 : Mth.clamp((clientTicks + partialTick - animStart) / anim.duration, 0, 1);
    }

    public void linkLaser(BlockPos pos) {
        if (!pos.equals(laserPos)) {
            laserPos = pos;
            setChanged();
        }
    }

    // ------------------------------------------------------------------ structure

    public ReactorStructure.Result checkStructure() {
        ReactorStructure.Result result = ReactorStructure.check(level, worldPosition);
        findLance();
        setFormed(result.complete());
        if (result.complete()) {
            reactorPorts.clear();
            reactorPorts.addAll(result.reactorPorts());
            for (BlockPos port : reactorPorts) {
                if (level.getBlockEntity(port) instanceof ReactorPortBlockEntity p) p.link(worldPosition);
            }
        }
        return result;
    }

    private void setFormed(boolean value) {
        if (formed == value || level == null) return;
        formed = value;
        setChanged();
        BlockState blockState = getBlockState();
        if (blockState.hasProperty(ReactorCoreBlock.FORMED)) {
            level.setBlock(worldPosition, blockState.setValue(ReactorCoreBlock.FORMED, value), 3);
        }
    }

    /** Testing helper: fills every stabilizer to the ignition minimum and charges the ignition energy. */
    public void fillForTesting() {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            MassInputPortBlockEntity port = massPort(dir);
            if (port != null && port.getMass() < ReactorConfig.MIN_MASS_PER_STABILIZER.get()) {
                port.addMass(ReactorConfig.MIN_MASS_PER_STABILIZER.get() - port.getMass());
            }
        }
        energy.generate(ReactorConfig.IGNITION_ENERGY.get());
        setChanged();
    }

    @Nullable
    private MassInputPortBlockEntity massPort(Direction dir) {
        if (level == null) return null;
        BlockPos pos = worldPosition.offset(ReactorStructure.massPortOffset(dir));
        return level.getBlockEntity(pos) instanceof MassInputPortBlockEntity port ? port : null;
    }

    /** The stabilizers' mass input ports that are in place, N/E/S/W order (for debug tools). */
    public List<MassInputPortBlockEntity> massPorts() {
        List<MassInputPortBlockEntity> ports = new ArrayList<>();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            MassInputPortBlockEntity port = massPort(dir);
            if (port != null) ports.add(port);
        }
        return ports;
    }

    private long[] stabilizerMasses() {
        long[] masses = new long[4];
        int i = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            MassInputPortBlockEntity port = massPort(dir);
            masses[i++] = port == null ? 0 : port.getMass();
        }
        return masses;
    }

    private void drainMass(long amount) {
        long perPort = Math.max(1, amount / 4);
        long remaining = amount;
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                MassInputPortBlockEntity port = massPort(dir);
                if (port != null && remaining > 0) remaining -= port.drain(Math.min(perPort, remaining));
            }
            perPort = remaining; // second pass: take what's left from whoever still has mass
        }
    }

    // ------------------------------------------------------------------ ticking

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, ReactorCoreBlockEntity core) {
        core.tick();
    }

    /** Client side: spins the disc so the renderer can interpolate smoothly between ticks. */
    public static void clientTick(Level level, BlockPos pos, BlockState blockState, ReactorCoreBlockEntity core) {
        core.clientTicks++;
        if (core.anim != Anim.NONE && core.clientTicks - core.animStart >= core.anim.duration) core.anim = Anim.NONE;
        if (core.formed) CLIENT_CORES.add(core);
        if (core.stateTimer > 0) core.stateTimer--; // counted down locally between syncs
        if (core.ventTicks > 0) core.ventTicks--;
        if (core.ventArmedUntil > 0) core.ventArmedUntil--;
        core.tickArcs();
        core.tickAnimParticles();
        core.prevDiscAngle = core.discAngle;
        float degreesPerTick = core.formed ? Math.copySign(0.4F, core.clientSpin) + core.clientSpin * 24F : 0;
        if (core.state == ReactorState.DISC_STALL) degreesPerTick = 0;
        core.discAngle += degreesPerTick;
        if (core.discAngle >= 360) {
            core.discAngle -= 360;
            core.prevDiscAngle -= 360;
        }
    }

    /** How much bigger than its mass-based size the black hole draws in this state (matches the renderer). */
    public float stateHoleScale() {
        return switch (state) {
            case PRE_MELTDOWN -> 1.15F;
            case MELTDOWN_1 -> 1.5F;
            case MELTDOWN_2 -> 3.0F;
            default -> 1F;
        };
    }

    /** Client side: fires and retires dark-energy arcs. Each one ray-traces into the world so it strikes real blocks. */
    private void tickArcs() {
        if (level == null) return;
        int count = 0;
        double reach = 0;
        boolean loud = false;
        float danger = getDangerProgress(0);
        switch (state) {
            case DISC_STALL -> { count = 1; reach = 6; }
            case IGNITION_FAULT, SHUTDOWN_FAULT -> { count = 2; reach = 7; }
            case PRE_MELTDOWN -> { count = 2 + Math.round(2 * danger); reach = 10; }
            case MELTDOWN_1 -> { count = 5; reach = 22; loud = true; }
            case MELTDOWN_2 -> { count = 7; reach = 30; loud = true; }
            default -> {}
        }
        float p = getAnimProgress(0);
        if (anim == Anim.BREACH) {
            count = Math.max(count, 6);
            reach = Math.max(reach, 16);
            loud = true;
        } else if (anim == Anim.STARTUP && p > 0.44F && p < 0.62F) {
            count = Math.max(count, 4); // the new black hole crackles as it opens
            reach = Math.max(reach, 8);
        } else if (anim == Anim.SCRAM && p < 0.5F) {
            count = Math.max(count, 6); // the cut beams' energy lashes out
            reach = Math.max(reach, 12);
            loud = true;
        } else if (anim == Anim.VENT_EMERGENCY && p < EmergencyVent.IMPLODE) {
            count = Math.max(count, 7); // a storm of arcs as it fights the drain
            reach = Math.max(reach, 24);
            loud = true;
        } else if (anim == Anim.VENT && p < 0.85F) {
            count = Math.max(count, 3); // the draining black hole spits arcs as it shrinks
            reach = Math.max(reach, 9);
        } else if ((anim == Anim.POWER_DOWN || anim == Anim.SCRAM_POWER_DOWN) && p > 0.85F) {
            count = Math.max(count, 3);
            reach = Math.max(reach, 6);
        }
        RandomSource rand = level.random;
        Vec3 center = Vec3.atCenterOf(worldPosition);
        double hole = clientHoleRadius * stateHoleScale();
        for (int k = 0; k < MAX_ARCS; k++) {
            if (k >= count) {
                arcTo[k] = null;
                continue;
            }
            if (arcTo[k] != null && clientTicks - arcBorn[k] < ARC_LIFE + k % 3) continue;
            // New bolt: mostly outward and downward, so most of them hit the ring and the ground.
            double yaw = rand.nextDouble() * Math.PI * 2;
            double y = -0.9 + rand.nextDouble() * 1.2;
            double flat = Math.sqrt(1 - y * y);
            Vec3 dir = new Vec3(Math.cos(yaw) * flat, y, Math.sin(yaw) * flat);
            Vec3 from = center.add(dir.scale(hole));
            Vec3 to = center.add(dir.scale(hole + reach * (0.6 + 0.4 * rand.nextDouble())));
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            boolean struck = hit.getType() == HitResult.Type.BLOCK;
            Vec3 end = struck ? hit.getLocation() : to;
            arcFrom[k] = from;
            arcTo[k] = end;
            arcBorn[k] = clientTicks;
            if (struck) {
                for (int i = 0; i < 8; i++) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z,
                            rand.nextGaussian() * 0.15, rand.nextDouble() * 0.2, rand.nextGaussian() * 0.15);
                }
                level.addParticle(ParticleTypes.SMOKE, end.x, end.y, end.z, 0, 0.03, 0);
                if (loud && rand.nextInt(3) == 0) {
                    level.playLocalSound(end.x, end.y, end.z, ModSounds.REACTOR_ARC.get(), SoundSource.BLOCKS,
                            0.6F, 0.85F + rand.nextFloat() * 0.3F, false);
                }
            }
        }
    }

    /** Client side: sparks that stream from the emitters into the core while it charges up for ignition. */
    private void tickAnimParticles() {
        if (level == null || anim != Anim.STARTUP) return;
        float p = getAnimProgress(0);
        if (p > 0.38F) return;
        RandomSource rand = level.random;
        Vec3 center = Vec3.atCenterOf(worldPosition);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            Vec3 from = emitterFace(dir);
            for (int i = 0; i < 2; i++) {
                Vec3 at = from.lerp(center, rand.nextDouble() * 0.9);
                Vec3 v = center.subtract(at).normalize().scale(0.6 + rand.nextDouble() * 0.4);
                level.addParticle(ParticleTypes.END_ROD, at.x, at.y + rand.nextGaussian() * 0.3, at.z, v.x, v.y, v.z);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) CLIENT_CORES.remove(this);
    }

    private void tick() {
        ticks++;
        if (ticks % SYNC_INTERVAL == 0) syncIfChanged();
        if (formed && state != ReactorState.OFFLINE) absorb(); // only while the black hole is there
        if (ticks % 20 == 0) {
            long[] masses = stabilizerMasses();
            totalMass = masses[0] + masses[1] + masses[2] + masses[3];
        }
        if (ventTicks > 0) {
            ventTicks--;
            if (ventEmergency) emergencyVentEffects();
        }
        if (ventArmedUntil > 0 && --ventArmedUntil == 0) syncIfChanged();
        if (ticks % RECHECK_INTERVAL == 0 && formed && ventTicks <= 0) findLance();
        if (ticks % RECHECK_INTERVAL == 0) axialFormed = formed && ReactorStructure.checkAxial(level, worldPosition);
        // Offline, any torn block still hanging around (say, from before a reload) is let go too.
        if (ticks % RECHECK_INTERVAL == 0 && state == ReactorState.OFFLINE) releaseTornBlocks();
        if (tickPreview()) return;

        if (ticks % RECHECK_INTERVAL == 0 && (formed || state != ReactorState.OFFLINE)) {
            boolean intact = ReactorStructure.check(level, worldPosition).complete();
            if (!intact) {
                // A breach starts the pre-meltdown countdown once; re-checks during it must not restart the timer.
                if ((state.generates() || state == ReactorState.DISC_STALL || state.isFault()) && state != ReactorState.PRE_MELTDOWN) {
                    broadcast(Component.translatable("message.masteris_bhe.reactor.breach").withStyle(ChatFormatting.RED));
                    breachSeq++;
                    breached = true;
                    breachEffects();
                    escalate(ReactorState.PRE_MELTDOWN, ReactorConfig.PRE_MELTDOWN_SECONDS.get() * 20);
                }
                if (state == ReactorState.OFFLINE) setFormed(false);
            }
        }

        switch (state) {
            case OFFLINE -> {
                rpm = towardZero(rpm, 50);
                offset = targetOffset = 0;
                laserPower = 0;
                stress = Math.max(0, stress - 0.1);
                stability = Math.min(100, stability + 0.5);
            }
            case STABLE, WARNING -> {
                runReactor();
                if (state != ReactorState.STABLE && state != ReactorState.WARNING) break; // a fault fired
                if (stability < ReactorConfig.PRE_MELTDOWN_STABILITY.get() || stress >= 100 || totalMass < ReactorConfig.CRITICAL_MASS.get()) {
                    enterState(ReactorState.PRE_MELTDOWN, ReactorConfig.PRE_MELTDOWN_SECONDS.get() * 20);
                } else {
                    state = stability < ReactorConfig.WARNING_STABILITY.get() ? ReactorState.WARNING : ReactorState.STABLE;
                }
            }
            case SHUTTING_DOWN -> {
                targetOffset = 0;
                laserPower = 0;
                rpm = towardZero(rpm, ReactorConfig.SHUTDOWN_BRAKE.get());
                runReactor();
                shutdownTicks++;
                if (state == ReactorState.SHUTTING_DOWN && pendingFault == Fault.CRACKED_LENS && shutdownTicks >= 60) {
                    startShutdownFault(Fault.CRACKED_LENS);
                } else if (state == ReactorState.SHUTTING_DOWN && offset == 0 && Math.abs(rpm) < 50) {
                    if (pendingFault == Fault.SINGULARITY_REBOUND) {
                        startShutdownFault(Fault.SINGULARITY_REBOUND);
                    } else {
                        enterState(ReactorState.OFFLINE, 0);
                        powerDownEffects();
                    }
                }
            }
            case IGNITION_FAULT -> tickIgnitionFault();
            case SHUTDOWN_FAULT -> tickShutdownFault();
            case DISC_STALL -> {
                rpm = 0;
                stability = Math.max(0, stability - 0.02);
                if (--stateTimer <= 0) escalateTimedOut(ReactorState.PRE_MELTDOWN, ReactorConfig.PRE_MELTDOWN_SECONDS.get() * 20);
            }
            case PRE_MELTDOWN -> {
                runReactor();
                if (state != ReactorState.PRE_MELTDOWN) break;
                screenShakeParticles(6);
                boolean recovered = ticks % 20 == 0 && formed && stability >= ReactorConfig.RECOVER_STABILITY.get()
                        && stress < ReactorConfig.RECOVER_STRESS.get()
                        && totalMass >= ReactorConfig.CRITICAL_MASS.get()
                        && ReactorStructure.check(level, worldPosition).complete();
                if (recovered) {
                    broadcast(Component.translatable("message.masteris_bhe.reactor.recovered").withStyle(ChatFormatting.GREEN));
                    if (!resumeSuspended()) enterState(ReactorState.WARNING, 0);
                } else if (--stateTimer <= 0) {
                    enterState(ReactorState.MELTDOWN_1, ReactorConfig.MELTDOWN_1_SECONDS.get() * 20);
                }
            }
            case MELTDOWN_1 -> {
                meltdownEffects(ReactorConfig.MELTDOWN_1_PULL.get(), 0.04, 2.0F);
                if (--stateTimer <= 0) enterState(ReactorState.MELTDOWN_2, ReactorConfig.MELTDOWN_2_SECONDS.get() * 20);
            }
            case MELTDOWN_2 -> {
                meltdownEffects(ReactorConfig.MELTDOWN_2_PULL.get(), 0.10, 4.0F);
                if (--stateTimer <= 0) {
                    collapse();
                    return; // the core is gone
                }
            }
        }

        failureEffects();

        if (ticks % 40 == 0 && state.isAlarm()) {
            playSound(state.isMeltdown() ? ModSounds.REACTOR_ALARM_MELTDOWN.get() : ModSounds.REACTOR_ALARM.get(), 1.0F);
        }
        if (ticks % 20 == 0) {
            for (BlockPos port : reactorPorts) {
                level.updateNeighbourForOutputSignal(port, level.getBlockState(port).getBlock());
            }
            setChanged();
        }
    }

    /** One tick of a running reactor: offset, spin, laser, mass, output, stress and stability. */
    private void runReactor() {
        // Stabilizers realign 1 degree per second.
        if (ticks % 20 == 0 && offset != targetOffset) offset += Integer.signum(targetOffset - offset);

        double safeRpm = ReactorConfig.SAFE_RPM.get();

        // Offset keeps pushing the disc: spin climbs for as long as the stabilizers are angled, with no top speed.
        // A negative offset pushes the other way: it brakes a forward spin, then spins the disc backwards.
        rpm += offset * ReactorConfig.SPIN_ACCELERATION.get();
        double speed = Math.abs(rpm); // backwards spin behaves exactly like forwards spin

        long[] masses = stabilizerMasses();
        totalMass = masses[0] + masses[1] + masses[2] + masses[3];
        boolean starvedStabilizer = masses[0] == 0 || masses[1] == 0 || masses[2] == 0 || masses[3] == 0;
        if (starvedStabilizer && speed > 1000) {
            enterDiscStall();
            return;
        }

        // Power mode 1: laser bending.
        int laserIn = 0;
        if (laserPower > 0) {
            BendingLaserBlockEntity laser = laser();
            if (laser != null) laserIn = laser.fire(ReactorConfig.LASER_MAX_RATE.get() * laserPower / 100);
        }
        double gain = ReactorConfig.LASER_GAIN_MIN.get()
                + (ReactorConfig.LASER_GAIN_MAX.get() - ReactorConfig.LASER_GAIN_MIN.get()) * laserPower / 100.0;
        massDebt += laserIn / (double) ReactorConfig.LASER_RF_PER_MASS.get();
        if (massDebt >= 1) {
            long whole = (long) massDebt;
            drainMass(whole);
            massDebt -= whole;
        }

        // Power mode 2: rotation. Output grows with the square of the spin, and every RF of it is paid for with spin.
        // Spin is only spent on RF the core has room for, so a full buffer leaves the disc spinning.
        long laserOut = (long) (laserIn * gain);
        long room = Math.max(0, energy.getMaxEnergyStored() - energy.getEnergyStored() - laserOut);
        // Spin only turns into power with both axial stabilizers up. Without them it isn't spent, so it keeps climbing.
        // How much of it turns into power depends on resonance and wobble; spin that isn't converted isn't spent.
        updateResonance();
        updatePrecession(speed, masses);
        if (Math.abs(wobble) >= 100) {
            enterDiscStall(); // the disc tipped over: it stalls
            return;
        }
        lastEfficiency = axialFormed ? resonanceEfficiency(speed) * wobbleEfficiency() : 0;
        long rotational = axialFormed
                ? (long) Math.min(ReactorConfig.RF_PER_RPM_SQUARED.get() * rpm * rpm * lastEfficiency, room) : 0;
        rpm = towardZero(rpm, rotational / (double) ReactorConfig.RF_PER_RPM_SPENT.get());
        speed = Math.abs(rpm);
        long output = rotational + laserOut;
        energy.generate(output);
        lastOutput = (int) Math.min(output, Integer.MAX_VALUE);

        // More mass holds the core steadier, so spin costs less stability and builds ring stress slower.
        double spinShake = spinShake();

        // Ring stress builds above the safe spin band and cools off below it.
        if (speed > safeRpm) stress = Math.min(100, stress + (speed - safeRpm) / 20_000.0 * spinShake);
        else stress = Math.max(0, stress - 0.05);

        double target = 100
                - Math.abs(offset) * 2.0 * spinShake
                - laserPower * 0.2
                - stress * 0.3
                - (speed > safeRpm ? (speed - safeRpm) / 100.0 * spinShake : 0)
                - (totalMass < ReactorConfig.WARNING_MASS.get() ? 20 : 0)
                - Math.max(0, Math.abs(wobble) - WOBBLE_SAFE) * 0.3;
        stability = Mth.clamp(stability + Mth.clamp(target - stability, -0.2, 0.2), 0, 100);
    }

    // ------------------------------------------------------------------ resonance and precession

    private static final double WOBBLE_SAFE = 25, WOBBLE_DAMPING = 0.99, TRIM_STEP = 0.1;

    /**
     * Where the resonance band is centered: the configured rpm at the ignition minimum of mass, rising with the
     * square root of the mass, kept under the safe spin, plus the drift.
     */
    public double resonanceRpm() {
        double reference = 4.0 * ReactorConfig.MIN_MASS_PER_STABILIZER.get();
        double scale = reference <= 0 ? 1 : Math.sqrt(Math.max(1, totalMass) / reference);
        double base = Math.min(ReactorConfig.RESONANCE_RPM.get() * scale, ReactorConfig.SAFE_RPM.get() * 0.95);
        return Math.max(ReactorConfig.RESONANCE_WIDTH.get() * 2.0, base + resonanceDrift);
    }

    /** The target wanders a little every tick and now and then jumps (about once a minute). */
    private void updateResonance() {
        if (level == null) return;
        double width = ReactorConfig.RESONANCE_WIDTH.get();
        double step = ReactorConfig.RESONANCE_DRIFT.get() / 20.0;
        resonanceDrift += (level.random.nextDouble() * 2 - 1) * step * 2;
        if (level.random.nextInt(1200) == 0) {
            resonanceDrift += (level.random.nextBoolean() ? 1 : -1) * width * (1 + level.random.nextDouble());
        }
        resonanceDrift = Mth.clamp(resonanceDrift, -width * 3, width * 3);
    }

    /** 1 inside the band, falling off smoothly outside it to the configured floor. */
    private double resonanceEfficiency(double speed) {
        double half = ReactorConfig.RESONANCE_WIDTH.get();
        double off = Math.abs(speed - resonanceRpm()) - half;
        if (off <= 0) return 1;
        double floor = ReactorConfig.RESONANCE_MIN_EFFICIENCY.get();
        double x = off / (half * 1.5);
        return floor + (1 - floor) * Math.exp(-x * x);
    }

    /**
     * The disc precesses: the faster it spins, and the more uneven the stabilizers' mass, the harder it's pushed
     * to lean one way or the other (the way wanders). The axial trim pushes back; the wobble settles where the two
     * cancel. Only with the axial stabilizers up: without them there's nothing to lean against.
     */
    private void updatePrecession(double speed, long[] masses) {
        if (level == null) return;
        if (!axialFormed) {
            wobble *= 0.95;
            return;
        }
        precession = Mth.clamp(precession + (level.random.nextDouble() * 2 - 1) * 0.01, -1, 1);
        long max = Math.max(Math.max(masses[0], masses[1]), Math.max(masses[2], masses[3]));
        long min = Math.min(Math.min(masses[0], masses[1]), Math.min(masses[2], masses[3]));
        double uneven = max <= 0 ? 0 : (max - min) / (double) max;
        double drive = speed / ReactorConfig.SAFE_RPM.get() * 0.5 * precession * (1 + uneven * 2)
                * ReactorConfig.PRECESSION_STRENGTH.get();
        wobble = (wobble + drive + axialTrim * TRIM_STEP) * WOBBLE_DAMPING;
    }

    /** Past a gentle lean, wobble costs efficiency too: down to half at the tipping point. */
    private double wobbleEfficiency() {
        double excess = Math.max(0, Math.abs(wobble) - WOBBLE_SAFE);
        return 1 - excess / (100 - WOBBLE_SAFE) * 0.5;
    }

    /**
     * How much the spin shakes the core: 1 at the ignition minimum (all four stabilizers at their minimum),
     * smaller with more mass, bigger with less. 80,000 mass halves it; it never drops below 0.2 or goes above 2.
     */
    private double spinShake() {
        double reference = 4.0 * ReactorConfig.MIN_MASS_PER_STABILIZER.get();
        if (reference <= 0) return 1;
        return Mth.clamp(reference / Math.max(1, totalMass), MIN_SPIN_SHAKE, MAX_SPIN_SHAKE);
    }

    /** Moves {@code value} toward 0 by {@code amount} without crossing it. */
    private static double towardZero(double value, double amount) {
        return value > 0 ? Math.max(0, value - amount) : Math.min(0, value + amount);
    }

    private void enterDiscStall() {
        wobble = 0;
        if (level != null) resonanceDrift += (level.random.nextBoolean() ? 1 : -1) * ReactorConfig.RESONANCE_WIDTH.get() * 2;
        // The trapped rotational energy dumps into the core as charge.
        energy.generate((long) (ReactorConfig.RF_PER_RPM_SQUARED.get() * rpm * rpm * 200));
        rpm = 0;
        stability = Math.max(0, stability - 15);
        escalate(ReactorState.DISC_STALL, ReactorConfig.STALL_SECONDS.get() * 20);
        playSound(ModSounds.REACTOR_STALL.get(), 1.0F);
        broadcast(Component.translatable("message.masteris_bhe.reactor.disc_stall").withStyle(ChatFormatting.GOLD));
    }

    // ------------------------------------------------------------------ breaker panels

    private static final int ALL_SIDES = 0b1111;

    /** One bit per side ({@link Direction#get2DDataValue()}) whose Breaker Panel is thrown to OVERCHARGED. */
    public int breakerMask() {
        if (level == null) return 0;
        int mask = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (BreakerPanelBlock.isOvercharged(level.getBlockState(worldPosition.offset(ReactorStructure.breakerPanelOffset(dir))))) {
                mask |= 1 << dir.get2DDataValue();
            }
        }
        return mask;
    }

    /** Throws every overcharged breaker back to normal. */
    private void resetBreakers() {
        if (level == null) return;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos pos = worldPosition.offset(ReactorStructure.breakerPanelOffset(dir));
            BlockState panel = level.getBlockState(pos);
            if (BreakerPanelBlock.isOvercharged(panel)) {
                level.setBlock(pos, panel.setValue(BreakerPanelBlock.OVERCHARGED, false), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1F, 0.5F);
            }
        }
    }

    // ------------------------------------------------------------------ stacked failures

    /**
     * A failure (or a shutdown) that a newer failure interrupted. It waits underneath, paused, and comes back as it
     * was, same stabilizers, same fix, same time left, once the newer one is dealt with. A meltdown, a vent or going
     * offline clears them all.
     */
    private record Suspended(ReactorState state, int timer, int duration, Fault fault, int faultMask, boolean faultFixed,
                             Fault pendingFault, int shutdownTicks) {}

    private final java.util.ArrayDeque<Suspended> suspended = new java.util.ArrayDeque<>();

    /** Whether this state, interrupted, should come back afterwards: a failure in progress, or a shutdown. */
    private static boolean resumable(ReactorState state) {
        return state == ReactorState.DISC_STALL || state.isFault() || state == ReactorState.SHUTTING_DOWN
                || state == ReactorState.PRE_MELTDOWN;
    }

    private void suspendCurrent(int timer) {
        suspended.push(new Suspended(state, timer, stateDuration, fault, faultMask, faultFixed, pendingFault, shutdownTicks));
    }

    /** A new failure on top of whatever is going on: anything in progress is put on hold underneath, not forgotten. */
    private void escalate(ReactorState next, int timer) {
        if (resumable(state) && state != next) suspendCurrent(stateTimer);
        enterState(next, timer);
    }

    /**
     * The current failure ran out of time and gets worse. It isn't cleared by that: it waits underneath with half
     * its time back. And if a pre-meltdown was already waiting underneath, that same countdown picks up again
     * rather than a fresh one starting.
     */
    private void escalateTimedOut(ReactorState next, int timer) {
        Suspended earlier = null;
        if (next == ReactorState.PRE_MELTDOWN) {
            for (Suspended s : suspended) {
                if (s.state() == ReactorState.PRE_MELTDOWN) {
                    earlier = s;
                    break;
                }
            }
            if (earlier != null) suspended.remove(earlier);
        }
        if (state == ReactorState.DISC_STALL || state.isFault()) suspendCurrent(Math.max(15 * 20, stateDuration / 2));
        if (earlier != null) restore(earlier);
        else enterState(next, timer);
    }

    /** The current failure is dealt with: the one it interrupted, if any, comes back. Returns false if there was none. */
    private boolean resumeSuspended() {
        Suspended s = suspended.poll();
        if (s == null) return false;
        restore(s);
        Component what = s.fault() != Fault.NONE
                ? Component.translatable("gui.masteris_bhe.fault." + s.fault().name().toLowerCase(),
                        s.fault() == Fault.SINGULARITY_REBOUND ? "" : sideNames(s.faultMask()))
                : Component.translatable(s.state().translationKey());
        broadcast(Component.translatable("message.masteris_bhe.reactor.resumed", what).withStyle(ChatFormatting.GOLD));
        playSound(ModSounds.REACTOR_BEEP_CONFIRM.get(), 0.9F);
        return true;
    }

    private void restore(Suspended s) {
        java.util.List<Suspended> keep = new java.util.ArrayList<>(suspended); // enterState must not clear the rest
        enterState(s.state(), s.timer());
        suspended.clear();
        suspended.addAll(keep);
        stateDuration = s.duration();
        fault = s.fault();
        faultMask = s.faultMask();
        faultFixed = s.faultFixed();
        pendingFault = s.pendingFault();
        shutdownTicks = s.shutdownTicks();
        setChanged();
    }

    private void enterState(ReactorState next, int timer) {
        state = next;
        if (next.isMeltdown() || next == ReactorState.OFFLINE) suspended.clear(); // past all that, or all over
        if (next == ReactorState.OFFLINE) {
            wobble = 0; // the disc stops: it settles level
            releaseTornBlocks(); // vented or shut down: nothing holds them up any more
        }
        stateTimer = timer;
        stateDuration = timer;
        if (!next.isFault()) {
            fault = Fault.NONE;
            faultMask = 0;
        }
        faultFixed = false;
        if (next != ReactorState.PRE_MELTDOWN && !next.isMeltdown()) breached = false;
        if (next != ReactorState.SHUTTING_DOWN) shutdownTicks = 0;
        if (next != ReactorState.SHUTTING_DOWN && next != ReactorState.SHUTDOWN_FAULT) pendingFault = Fault.NONE;
        setChanged();
    }

    // ------------------------------------------------------------------ console buttons

    /** A console button. {@code player} is null when a computer pressed it (see {@link #remoteButton}). */
    public void handleButton(@Nullable Player player, int id) {
        switch (id) {
            case BUTTON_IGNITE -> ignite(player);
            case BUTTON_SHUTDOWN -> {
                if (state == ReactorState.STABLE || state == ReactorState.WARNING) {
                    armShutdownFault();
                    enterState(ReactorState.SHUTTING_DOWN, 0);
                    tell(player, "shutdown", ChatFormatting.AQUA);
                    playSound(ModSounds.REACTOR_BEEP_CONFIRM.get(), 0.8F);
                }
            }
            case BUTTON_SCRAM -> scram(player);
            case BUTTON_OFFSET_DOWN -> { if (canAdjust()) targetOffset = Math.max(-ReactorConfig.MAX_OFFSET.get(), targetOffset - 1); }
            case BUTTON_OFFSET_UP -> { if (canAdjust()) targetOffset = Math.min(ReactorConfig.MAX_OFFSET.get(), targetOffset + 1); }
            case BUTTON_LASER_DOWN -> { if (canAdjust()) laserPower = Math.max(0, laserPower - 10); }
            case BUTTON_LASER_UP -> { if (canAdjust()) laserPower = Math.min(100, laserPower + 10); }
            case BUTTON_JUMP_START -> {
                if (state.isFault()) resetFault(player); // the same console button is RESET during a fault
                else jumpStart(player);
            }
            case BUTTON_VENT -> vent(player);
            case BUTTON_TRIM_NW -> { if (canAdjust()) axialTrim = Math.max(-MAX_TRIM, axialTrim - 1); }
            case BUTTON_TRIM_SE -> { if (canAdjust()) axialTrim = Math.min(MAX_TRIM, axialTrim + 1); }
            default -> {}
        }
        setChanged();
        syncIfChanged(); // so the animations start right away
    }

    private boolean canAdjust() {
        return state == ReactorState.STABLE || state == ReactorState.WARNING || state == ReactorState.PRE_MELTDOWN;
    }

    private void ignite(@Nullable Player player) {
        if (state != ReactorState.OFFLINE) { tell(player, "ignite.running", ChatFormatting.YELLOW); return; }
        if (!checkStructure().complete()) { tell(player, "ignite.structure", ChatFormatting.RED); return; }
        if (level != null && !ReactorConfig.allows(ReactorConfig.IGNITION_DIMENSIONS.get(), level.dimension())) {
            tell(player, "ignite.dimension", ChatFormatting.RED);
            fizzle();
            return;
        }
        long min = ReactorConfig.MIN_MASS_PER_STABILIZER.get();
        for (long mass : stabilizerMasses()) {
            if (mass < min) {
                say(player, Component.translatable("message.masteris_bhe.ignite.mass", min)
                        .withStyle(ChatFormatting.RED), false);
                fizzle();
                return;
            }
        }
        if (!energy.consume(ReactorConfig.IGNITION_ENERGY.get())) {
            say(player, Component.translatable("message.masteris_bhe.ignite.energy",
                    ReactorConfig.IGNITION_ENERGY.get()).withStyle(ChatFormatting.RED), false);
            fizzle();
            return;
        }
        double risk = ignitionRisk();
        if (level != null && level.random.nextDouble() < risk) {
            stress = 0;
            rpm = 0;
            scrammed = false;
            startIgnitionFault();
            return;
        }
        stability = 100;
        stress = 0;
        rpm = 0;
        scrammed = false;
        enterState(ReactorState.STABLE, 0);
        startupEffects();
        tell(player, "ignite.success", ChatFormatting.GREEN);
    }

    private void fizzle() {
        playSound(ModSounds.REACTOR_BEEP_DENY.get(), 0.8F);
    }

    private void scram(@Nullable Player player) {
        if (!state.generates()) return;
        offset = targetOffset = 0;
        laserPower = 0;
        scrammed = true;
        scramSeq++;
        scramEffects();
        if (Math.abs(rpm) > ReactorConfig.SCRAM_STALL_SPIN.get() * ReactorConfig.SAFE_RPM.get()) {
            enterDiscStall(); // snapping the stabilizers back at high spin stalls the disc
        } else if (state != ReactorState.PRE_MELTDOWN) {
            armShutdownFault();
            enterState(ReactorState.SHUTTING_DOWN, 0);
            tell(player, "scram", ChatFormatting.AQUA);
        }
    }

    /**
     * Disc stall restart. Only catches once all four stabilizers' breakers are overcharged; then the stabilizers
     * kick the disc back into spin and the breakers drop back to normal. Pressing it early costs time.
     */
    private void jumpStart(@Nullable Player player) {
        if (state != ReactorState.DISC_STALL) return;
        int missing = ~breakerMask() & ALL_SIDES;
        if (missing == 0) {
            if (targetOffset == 0) targetOffset = 1;
            offset = targetOffset;
            rpm = 600 * Integer.signum(offset); // restarts in whichever direction the stabilizers point
            stability = Math.min(100, stability + 10);
            if (!resumeSuspended()) enterState(ReactorState.WARNING, 0);
            resetBreakers();
            playSound(ModSounds.REACTOR_IGNITE.get(), 1.25F);
            tell(player, "jump.success", ChatFormatting.GREEN);
        } else {
            stateTimer = Math.max(1, stateTimer - ReactorConfig.JUMP_PENALTY_SECONDS.get() * 20);
            playSound(ModSounds.REACTOR_BEEP_DENY.get(), 0.9F);
            say(player, Component.translatable("message.masteris_bhe.jump.fail", sideNames(missing))
                    .withStyle(ChatFormatting.RED), false);
        }
    }

    // ------------------------------------------------------------------ meltdown

    @Nullable
    private BendingLaserBlockEntity laser() {
        if (laserPos == null || level == null || !level.isLoaded(laserPos)) return null;
        if (level.getBlockEntity(laserPos) instanceof BendingLaserBlockEntity laser) return laser;
        laserPos = null;
        return null;
    }

    private void meltdownEffects(int radius, double pull, float damage) {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 center = Vec3.atCenterOf(worldPosition);
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(worldPosition).inflate(radius),
                e -> e instanceof LivingEntity || e instanceof ItemEntity)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            Vec3 toCore = center.subtract(e.position());
            double dist = toCore.length();
            if (dist < 1.0) continue;
            Vec3 push = toCore.normalize().scale(pull);
            e.push(push.x, push.y, push.z);
            e.hurtMarked = true;
            if (ticks % 20 == 0 && dist < radius / 2.0 && e instanceof LivingEntity living) {
                living.hurt(level.damageSources().magic(), damage);
            }
        }
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 30, 2, 2, 2, 0.2);
        if (ticks % 10 == 0) server.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 1, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------------ failure effects (server side)


    /** Particles and sounds for whatever is going wrong. The bigger visuals are drawn by the client renderer. */
    private void failureEffects() {
        if (!(level instanceof ServerLevel server) || !formed) return;
        RandomSource rand = level.random;
        Vec3 center = Vec3.atCenterOf(worldPosition);

        // Overspin: the ring glows (client) and sheds hot debris and creaks.
        double load = stress / 100.0;
        if (state != ReactorState.OFFLINE && load > 0.3 && ticks % 3 == 0) {
            List<BlockPos> ring = ReactorStructure.ringOffsets();
            BlockPos at = worldPosition.offset(ring.get(rand.nextInt(ring.size())));
            double x = at.getX() + 0.5, y = at.getY() + 0.5, z = at.getZ() + 0.5;
            server.sendParticles(ParticleTypes.LAVA, x, y, z, 1 + (int) (load * 3), 0.4, 0.4, 0.4, 0);
            server.sendParticles(ParticleTypes.SMOKE, x, y, z, 3, 0.3, 0.3, 0.3, 0.02);
            server.sendParticles(ParticleTypes.CRIT, x, y, z, (int) (load * 8), 0.3, 0.3, 0.3, 0.3);
            if (rand.nextFloat() < load * 0.15F) {
                level.playSound(null, at, ModSounds.REACTOR_RING_STRAIN.get(), SoundSource.BLOCKS, 1.5F, 0.85F + rand.nextFloat() * 0.3F);
            }
        }

        if (state.isFault()) faultTickEffects(server, rand);

        switch (state) {
            case DISC_STALL -> {
                // The disc shudders and throws sparks off its rim.
                if (ticks % 4 == 0) {
                    double a = rand.nextDouble() * Math.PI * 2, r = holeRadius() + 2 + rand.nextDouble() * 6;
                    server.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x + Math.cos(a) * r, center.y,
                            center.z + Math.sin(a) * r, 6, 0.2, 0.2, 0.2, 0.3);
                }
            }
            case MELTDOWN_1 -> {
                // The stabilizer beams are breaking up: their emitters spit sparks and smoke.
                if (ticks % 12 == 0) {
                    Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(rand);
                    Vec3 e = emitterFace(dir);
                    server.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.x, e.y, e.z, 20, 0.3, 0.3, 0.3, 0.4);
                    server.sendParticles(ParticleTypes.LARGE_SMOKE, e.x, e.y, e.z, 4, 0.2, 0.2, 0.2, 0.03);
                    level.playSound(null, BlockPos.containing(e), ModSounds.REACTOR_EMITTER_CRACK.get(), SoundSource.BLOCKS, 1.0F, 0.8F + rand.nextFloat() * 0.3F);
                }
            }
            case MELTDOWN_2 -> {
                // (phase 2's block pull is part of the black hole's reach now: see tearBlocks)
                // The last few seconds: the core implodes before it goes.
                if (stateTimer == 60) {
                    playSound(ModSounds.REACTOR_IMPLODE.get(), 1.0F);
                }
            }
            default -> {}
        }
    }

    /** Containment breach: dark energy bursts out of the ring. */
    private void breachEffects() {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 c = Vec3.atCenterOf(worldPosition);
        server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 200, 8, 1, 8, 0.6);
        server.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 6, 6, 1, 6, 0);
        loudSound(ModSounds.REACTOR_BREACH.get(), 8F, 1.0F);
    }

    // ------------------------------------------------------------------ venting lance

    private void findLance() {
        int bottom = VentingLance.find(level, worldPosition);
        lanceFormed = bottom >= 0;
        if (lanceFormed) lanceTop = bottom + VentingLance.HEIGHT - 1;
    }

    private boolean ventArmed() {
        return ventArmedUntil > 0;
    }

    /**
     * The console's Vent button. The first press arms the lance; a second press within 5 seconds fires it.
     * A vent dumps all the core's charge into the sky and drops the reactor to offline, burning part of the mass
     * (most of it during a breach). In meltdown phase 1 it's the emergency vent: all the mass, and the lance with it.
     */
    private void vent(@Nullable Player player) {
        if (!lanceFormed) { tell(player, "vent.no_lance", ChatFormatting.RED); return; }
        if (state == ReactorState.OFFLINE) { tell(player, "vent.offline", ChatFormatting.GRAY); return; }
        if (state == ReactorState.MELTDOWN_2) { tell(player, "vent.too_late", ChatFormatting.DARK_RED); return; }
        if (ventTicks > 0) return;
        if (!ventArmed()) {
            ventArmedUntil = VENT_ARM_TICKS;
            playSound(ModSounds.REACTOR_BEEP_CONFIRM.get(), 1.4F);
            playSound(ModSounds.REACTOR_ALARM.get(), 1.3F);
            tell(player, state == ReactorState.MELTDOWN_1 ? "vent.armed_emergency" : "vent.armed", ChatFormatting.GOLD);
            syncIfChanged();
            return;
        }
        ventArmedUntil = 0;
        preview = null; // a vent ends any debug preview too
        boolean emergency = state == ReactorState.MELTDOWN_1;
        boolean breached = !ReactorStructure.check(level, worldPosition).complete();
        double burn = emergency ? 1.0 : breached ? ReactorConfig.BREACH_VENT_MASS_FRACTION.get() : ReactorConfig.VENT_MASS_FRACTION.get();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            MassInputPortBlockEntity port = massPort(dir);
            if (port != null) port.burnFraction(burn);
        }
        long[] masses = stabilizerMasses();
        totalMass = masses[0] + masses[1] + masses[2] + masses[3];
        energy.setEnergy(0);
        rpm = 0;
        stress = 0;
        offset = targetOffset = 0;
        laserPower = 0;
        stability = Math.max(stability, 60);
        scrammed = false;
        ventTicks = VentingLance.beamTicks(emergency);
        ventEmergency = emergency;
        enterState(ReactorState.OFFLINE, 0);
        if (level instanceof ServerLevel server) {
            VentingLance.fire(server, worldPosition, lanceTop, emergency);
            long time = level.getDayTime() % 24000;
            if (time > 5000 && time < 7000 && level.canSeeSky(worldPosition.above(lanceTop + 1))) {
                broadcast(Component.translatable("message.masteris_bhe.vent.sun").withStyle(ChatFormatting.YELLOW));
            }
        }
        broadcast(Component.translatable(emergency ? "message.masteris_bhe.vent.fired_emergency" : "message.masteris_bhe.vent.fired",
                Math.round(burn * 100)).withStyle(emergency ? ChatFormatting.DARK_RED : ChatFormatting.GOLD));
        syncIfChanged();
    }

    // ------------------------------------------------------------------ ignition and shutdown faults

    /**
     * Ignition fault chance: the base chance, plus up to 50% for uneven mass across the stabilizers, up to 30% for
     * barely having the minimum (less than 1.5x it in the emptiest one) and up to 30% for low stability.
     */
    private double ignitionRisk() {
        long[] m = stabilizerMasses();
        long low = Long.MAX_VALUE, high = 0;
        for (long v : m) {
            low = Math.min(low, v);
            high = Math.max(high, v);
        }
        double imbalance = high <= 0 ? 0 : (high - low) / (double) high;
        double min = ReactorConfig.MIN_MASS_PER_STABILIZER.get();
        double thin = min <= 0 ? 0 : Mth.clamp(1 - (low / min - 1) / 0.5, 0, 1);
        return Mth.clamp(ReactorConfig.FAULT_BASE_CHANCE.get() + 0.5 * imbalance + 0.3 * thin + 0.3 * (1 - stability / 100), 0, 0.9);
    }

    /** Shutdown fault chance: the base chance, plus up to 50% for low stability, 25% for ring stress, 15% for overspin. */
    private double shutdownRisk() {
        boolean overspin = Math.abs(rpm) > ReactorConfig.SAFE_RPM.get();
        return Mth.clamp(ReactorConfig.FAULT_BASE_CHANCE.get() + 0.5 * (1 - stability / 100) + 0.25 * stress / 100
                + (overspin ? 0.15 : 0), 0, 0.9);
    }

    /** Rolls for a shutdown fault when a shutdown starts; it strikes partway through (cracked lens) or at the end (rebound). */
    private void armShutdownFault() {
        shutdownTicks = 0;
        pendingFault = Fault.NONE;
        if (preview != null || level == null || level.random.nextDouble() >= shutdownRisk()) return;
        pendingFault = level.random.nextBoolean() ? Fault.CRACKED_LENS : Fault.SINGULARITY_REBOUND;
    }

    private int randomSides(int count) {
        List<Direction> sides = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        Collections.shuffle(sides, new java.util.Random(level == null ? 0 : level.random.nextLong()));
        int mask = 0;
        for (int i = 0; i < count; i++) mask |= 1 << sides.get(i).get2DDataValue();
        return mask;
    }

    private void startIgnitionFault() {
        RandomSource rand = level.random;
        long[] m = stabilizerMasses();
        long low = Long.MAX_VALUE, high = 0;
        for (long v : m) {
            low = Math.min(low, v);
            high = Math.max(high, v);
        }
        boolean uneven = high > 0 && (high - low) / (double) high > 0.2;
        // Uneven mass makes a mass flash likelier; otherwise it's usually the stabilizers losing sync.
        fault = rand.nextFloat() < (uneven ? 0.7F : 0.3F) ? Fault.MASS_FLASH : Fault.STABILIZER_DESYNC;
        faultMask = randomSides(1 + rand.nextInt(2));
        stability = 80;
        if (fault == Fault.MASS_FLASH) {
            long min = ReactorConfig.MIN_MASS_PER_STABILIZER.get();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                MassInputPortBlockEntity port = massPort(dir);
                if (port == null || !isFaulted(dir)) continue;
                long left = Math.min((long) (port.getMass() * 0.4), min / 2); // always well under the minimum
                port.drain(port.getMass() - left);
            }
        }
        enterState(ReactorState.IGNITION_FAULT, ReactorConfig.FAULT_SECONDS.get() * 20);
        faultEffects();
        broadcast(Component.translatable("message.masteris_bhe.fault." + fault.name().toLowerCase(), sidesText())
                .withStyle(ChatFormatting.GOLD));
    }

    private void startShutdownFault(Fault which) {
        fault = which;
        pendingFault = Fault.NONE;
        faultMask = which == Fault.CRACKED_LENS ? randomSides(1) : 0;
        enterState(ReactorState.SHUTDOWN_FAULT, ReactorConfig.FAULT_SECONDS.get() * 20);
        faultEffects();
        Object arg = which == Fault.SINGULARITY_REBOUND ? String.format("%,d", ReactorConfig.REBOUND_DRAIN_RF.get()) : sidesText();
        broadcast(Component.translatable("message.masteris_bhe.fault." + which.name().toLowerCase(), arg)
                .withStyle(ChatFormatting.GOLD));
    }

    private void tickIgnitionFault() {
        rpm = 0;
        stability = Math.max(0, stability - 0.02);
        boolean fixed = switch (fault) {
            case STABILIZER_DESYNC -> faultMask == 0;
            case MASS_FLASH -> {
                boolean full = true;
                long min = ReactorConfig.MIN_MASS_PER_STABILIZER.get();
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    MassInputPortBlockEntity port = massPort(dir);
                    if (port == null || port.getMass() < min) full = false;
                }
                yield full;
            }
            default -> true;
        };
        markFixed(fixed);
        if (--stateTimer <= 0) escalateTimedOut(ReactorState.PRE_MELTDOWN, ReactorConfig.PRE_MELTDOWN_SECONDS.get() * 20);
    }

    private void tickShutdownFault() {
        stability = Math.max(0, stability - 0.03);
        // With a lens out the stabilizers can't brake: the disc coasts. A rebound holds the disc still.
        if (fault != Fault.CRACKED_LENS) rpm = 0;
        markFixed(fault == Fault.CRACKED_LENS ? faultMask == 0 : energy.getEnergyStored() <= ReactorConfig.REBOUND_DRAIN_RF.get());
        if (--stateTimer <= 0) escalateTimedOut(ReactorState.PRE_MELTDOWN, ReactorConfig.PRE_MELTDOWN_SECONDS.get() * 20);
    }

    /** The fix is in (or, for mass and charge, slipped back out): tell the crew to press RESET. */
    private void markFixed(boolean fixed) {
        if (fixed == faultFixed) return;
        faultFixed = fixed;
        if (fixed) {
            playSound(ModSounds.REACTOR_BEEP_READY.get(), 1.0F);
            broadcast(Component.translatable("message.masteris_bhe.fault.ready").withStyle(ChatFormatting.GREEN));
        }
        setChanged();
        syncIfChanged();
    }

    /** The console's RESET during a fault: finishes the startup or shutdown, but only once the cause is fixed. */
    private void resetFault(@Nullable Player player) {
        if (!faultFixed) {
            tell(player, "fault.not_fixed", ChatFormatting.RED);
            playSound(ModSounds.REACTOR_BEEP_DENY.get(), 1.0F);
            return;
        }
        switch (fault) {
            case STABILIZER_DESYNC, MASS_FLASH -> {
                stability = Math.max(stability, 70);
                enterState(ReactorState.STABLE, 0);
                startupEffects();
                broadcast(Component.translatable("message.masteris_bhe.fault.fixed.ignition").withStyle(ChatFormatting.GREEN));
            }
            case CRACKED_LENS -> {
                enterState(ReactorState.SHUTTING_DOWN, 0);
                playSound(ModSounds.REACTOR_BEEP_CONFIRM.get(), 0.8F);
                broadcast(Component.translatable("message.masteris_bhe.fault.fixed.lens").withStyle(ChatFormatting.GREEN));
            }
            case SINGULARITY_REBOUND -> {
                enterState(ReactorState.OFFLINE, 0);
                powerDownEffects();
                broadcast(Component.translatable("message.masteris_bhe.fault.fixed.rebound").withStyle(ChatFormatting.GREEN));
            }
            default -> {}
        }
    }

    /** "north, east" for the stabilizers in the fault mask. */
    private Component sidesText() {
        return sideNames(faultMask);
    }

    /** "north, east" for the sides set in {@code mask} (one bit per {@link Direction#get2DDataValue()}). */
    public static Component sideNames(int mask) {
        net.minecraft.network.chat.MutableComponent text = Component.empty();
        boolean first = true;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if ((mask & (1 << dir.get2DDataValue())) == 0) continue;
            if (!first) text.append(", ");
            text.append(Component.translatable("direction.masteris_bhe." + dir.getName()));
            first = false;
        }
        return text;
    }

    @Nullable
    private Direction emitterSide(BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (worldPosition.relative(dir, ReactorStructure.EMITTER_DISTANCE).equals(pos)) return dir;
        }
        return null;
    }

    /** What a fix attempt on a stabilizer emitter did. */
    public enum FixResult { FIXED, ALL_FIXED, NOT_NEEDED }

    /** Configurator on a Stabilizer Emitter: realigns it if a desync knocked it out of line. */
    public FixResult realignEmitter(BlockPos pos) {
        return fixEmitter(pos, Fault.STABILIZER_DESYNC, ModSounds.REACTOR_SERVO.get());
    }

    /** The lens recalibration command on a Stabilizer Console: fixes that stabilizer's lens if it cracked. */
    public FixResult recalibrateLens(Direction dir) {
        return fixEmitter(worldPosition.relative(dir, ReactorStructure.EMITTER_DISTANCE), Fault.CRACKED_LENS,
                SoundEvents.AMETHYST_BLOCK_PLACE);
    }

    /** Which stabilizer's console is at {@code pos}, or null if it isn't one of this reactor's. */
    @Nullable
    public Direction stabilizerConsoleSide(BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (worldPosition.offset(ReactorStructure.stabilizerConsoleOffset(dir)).equals(pos)) return dir;
        }
        return null;
    }

    /** The {@code dir} stabilizer's stored mass, for its console. */
    public long stabilizerMass(Direction dir) {
        MassInputPortBlockEntity port = massPort(dir);
        return port == null ? 0 : port.getMass();
    }

    private FixResult fixEmitter(BlockPos pos, Fault needed, net.minecraft.sounds.SoundEvent sound) {
        Direction dir = emitterSide(pos);
        if (fault != needed || dir == null || !isFaulted(dir)) return FixResult.NOT_NEEDED;
        faultMask &= ~(1 << dir.get2DDataValue());
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.05);
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
        setChanged();
        syncIfChanged();
        return faultMask == 0 ? FixResult.ALL_FIXED : FixResult.FIXED;
    }

    /** The moment a fault strikes. */
    private void faultEffects() {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 c = Vec3.atCenterOf(worldPosition);
        server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        playSound(ModSounds.REACTOR_FAULT.get(), 1.0F);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (!isFaulted(dir)) continue;
            Vec3 e = emitterFace(dir);
            switch (fault) {
                case MASS_FLASH -> {
                    BlockPos port = worldPosition.offset(ReactorStructure.massPortOffset(dir));
                    server.sendParticles(ParticleTypes.FLASH, port.getX() + 0.5, port.getY() + 1, port.getZ() + 0.5, 1, 0, 0, 0, 0);
                    server.sendParticles(ParticleTypes.END_ROD, port.getX() + 0.5, port.getY() + 1, port.getZ() + 0.5, 60, 1, 1, 1, 0.3);
                }
                case CRACKED_LENS -> level.playSound(null, BlockPos.containing(e), ModSounds.REACTOR_EMITTER_CRACK.get(), SoundSource.BLOCKS, 1.5F, 0.8F);
                default -> server.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.x, e.y, e.z, 40, 0.4, 0.4, 0.4, 0.4);
            }
        }
    }

    /** Ongoing particles while a fault is waiting to be fixed. */
    private void faultTickEffects(ServerLevel server, RandomSource rand) {
        if (ticks % 5 != 0) return;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (!isFaulted(dir)) continue;
            Vec3 e = emitterFace(dir);
            switch (fault) {
                case STABILIZER_DESYNC -> server.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.x, e.y, e.z, 6, 0.3, 0.3, 0.3, 0.2);
                case CRACKED_LENS -> {
                    server.sendParticles(ParticleTypes.CRIT, e.x, e.y, e.z, 4, 0.3, 0.3, 0.3, 0.2);
                    server.sendParticles(ParticleTypes.SMOKE, e.x, e.y, e.z, 2, 0.2, 0.2, 0.2, 0.01);
                }
                case MASS_FLASH -> {
                    BlockPos port = worldPosition.offset(ReactorStructure.massPortOffset(dir));
                    server.sendParticles(ParticleTypes.REVERSE_PORTAL, port.getX() + 0.5, port.getY() + 1.2, port.getZ() + 0.5, 6, 0.4, 0.3, 0.4, 0.02);
                }
                default -> {}
            }
        }
        if (fault == Fault.SINGULARITY_REBOUND) {
            Vec3 c = Vec3.atCenterOf(worldPosition);
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 20, 1.5, 1.5, 1.5, 0.2);
        }
    }

    // ------------------------------------------------------------------ debug failure preview

    @Nullable public Preview getPreview() { return preview; }

    /** Debug: moves the preview on to the next failure (or starts at the first one). Returns the one now showing. */
    public Preview previewNext() {
        Preview[] all = Preview.values();
        Preview next = preview == null ? all[0] : all[(preview.ordinal() + 1) % all.length];
        startPreview(next);
        return next;
    }

    /** Debug: ends the preview and leaves the reactor running normally. */
    public void stopPreview() {
        if (preview == null) return;
        preview = null;
        stress = 0;
        stability = 100;
        rpm = 0;
        enterState(ReactorState.STABLE, 0);
        syncIfChanged();
    }

    private void startPreview(Preview next) {
        preview = next;
        switch (next) {
            case DISC_STALL -> {
                playSound(ModSounds.REACTOR_STALL.get(), 1.0F);
                enterState(ReactorState.DISC_STALL, PREVIEW_TICKS);
            }
            case OVERSPIN, STARVATION -> enterState(ReactorState.WARNING, 0);
            case PRE_MELTDOWN -> enterState(ReactorState.PRE_MELTDOWN, PREVIEW_TICKS);
            case BREACH -> {
                breachSeq++;
                breached = true;
                breachEffects();
                enterState(ReactorState.PRE_MELTDOWN, PREVIEW_TICKS);
            }
            case MELTDOWN_1 -> enterState(ReactorState.MELTDOWN_1, PREVIEW_TICKS);
            case MELTDOWN_2 -> enterState(ReactorState.MELTDOWN_2, PREVIEW_TICKS);
            case STABILIZER_DESYNC, MASS_FLASH -> {
                enterState(ReactorState.IGNITION_FAULT, PREVIEW_TICKS);
                fault = Fault.valueOf(next.name());
                faultMask = randomSides(2);
                faultEffects();
            }
            case CRACKED_LENS, SINGULARITY_REBOUND -> {
                enterState(ReactorState.SHUTDOWN_FAULT, PREVIEW_TICKS);
                fault = Fault.valueOf(next.name());
                faultMask = next == Preview.CRACKED_LENS ? randomSides(1) : 0;
                faultEffects();
            }
        }
        syncIfChanged();
    }

    /** Runs instead of the normal tick while previewing. Returns false when not previewing. */
    private boolean tickPreview() {
        if (preview == null) return false;
        double safe = ReactorConfig.SAFE_RPM.get();
        stress = preview == Preview.OVERSPIN ? 85 : 0;
        rpm = switch (preview) {
            case DISC_STALL, STABILIZER_DESYNC, MASS_FLASH, SINGULARITY_REBOUND -> 0;
            case OVERSPIN -> safe * 2.2;
            default -> safe * 0.6;
        };
        // Loop the timer so the whole phase (and the phase 2 implosion) plays again and again.
        if (stateDuration > 0 && --stateTimer <= 0) enterState(state, stateDuration);
        failureEffects();
        if (ticks % 40 == 0 && state.isAlarm()) {
            playSound(state.isMeltdown() ? ModSounds.REACTOR_ALARM_MELTDOWN.get() : ModSounds.REACTOR_ALARM.get(), 1.0F);
        }
        return true;
    }

    private void screenShakeParticles(int count) {
        if (level instanceof ServerLevel server && ticks % 5 == 0) {
            Vec3 c = Vec3.atCenterOf(worldPosition);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, count, 1.5, 1.5, 1.5, 0.3);
        }
    }

    private void collapse() {
        if (level == null) return;
        long[] masses = stabilizerMasses();
        long mass = masses[0] + masses[1] + masses[2] + masses[3];
        float radius = (float) Math.min(ReactorConfig.MAX_RADIUS.get(),
                ReactorConfig.BASE_RADIUS.get() + mass / (double) ReactorConfig.MASS_PER_RADIUS.get());
        boolean destroy = ReactorConfig.DESTROY_BLOCKS.get();
        Vec3 c = Vec3.atCenterOf(worldPosition);
        Level lvl = level;
        broadcast(Component.translatable("message.masteris_bhe.reactor.collapse").withStyle(ChatFormatting.DARK_PURPLE));
        lvl.removeBlock(worldPosition, false);
        // A small vanilla blast for the boom and the particles; the crater itself is carved as a true sphere.
        lvl.explode(null, c.x, c.y, c.z, 8F, Level.ExplosionInteraction.NONE);
        if (lvl instanceof ServerLevel server) {
            Crater.blast(server, worldPosition, Math.round(radius), destroy);
            // The zone is centered on the crater's floor, so it takes in the ground (and ore) all round the crater.
            DarkZones.get(server).add(server, Crater.floor(server, worldPosition, Math.round(radius)), DarkZones.radiusForMass(mass));
            ZoneTerrain.collapse(server, worldPosition, Math.round(radius));
        }
    }

    // ------------------------------------------------------------------ helpers

    private void tell(@Nullable Player player, String key, ChatFormatting color) {
        say(player, Component.translatable("message.masteris_bhe." + key).withStyle(color), false);
    }

    /** A reply to whoever pressed a button: chat for a player, kept for the computer otherwise. */
    private void say(@Nullable Player player, Component message, boolean actionBar) {
        if (player != null) player.displayClientMessage(message, actionBar);
        else remoteReply = message;
    }

    // ------------------------------------------------------------------ remote control (computers, see compat/cc)

    @Nullable private Component remoteReply;

    /** Presses a console button for a computer. Returns what a player would have been told, or null. */
    @Nullable
    public Component remoteButton(int id) {
        remoteReply = null;
        handleButton(null, id);
        return remoteReply;
    }

    /** Sets the stabilizers' target offset directly. False if it can't be adjusted right now. */
    public boolean remoteSetTargetOffset(int value) {
        if (!canAdjust()) return false;
        targetOffset = Mth.clamp(value, -ReactorConfig.MAX_OFFSET.get(), ReactorConfig.MAX_OFFSET.get());
        setChanged();
        return true;
    }

    /** Sets the laser power (0..100) directly. False if it can't be adjusted right now. */
    public boolean remoteSetLaserPower(int value) {
        if (!canAdjust()) return false;
        laserPower = Mth.clamp(value, 0, 100);
        setChanged();
        return true;
    }

    /** Sets the axial trim directly. False if it can't be adjusted right now. */
    public boolean remoteSetAxialTrim(int value) {
        if (!canAdjust()) return false;
        axialTrim = Mth.clamp(value, -MAX_TRIM, MAX_TRIM);
        setChanged();
        return true;
    }

    public long energyCapacity() {
        return energy.getMaxEnergyStored();
    }

    // ------------------------------------------------------------------ animation effects (server side)

    private Vec3 emitterFace(Direction dir) {
        double d = ReactorStructure.EMITTER_DISTANCE - 0.5;
        return Vec3.atCenterOf(worldPosition).add(dir.getStepX() * d, 0, dir.getStepZ() * d);
    }

    /** Ignition: sparks run down each beam path toward the core, then a flash. */
    private void startupEffects() {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 center = Vec3.atCenterOf(worldPosition);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            Vec3 from = emitterFace(dir);
            for (int i = 0; i <= 20; i++) {
                Vec3 p = from.lerp(center, i / 20.0);
                server.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.01);
            }
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 15, 0.3, 0.3, 0.3, 0.2);
        }
        server.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 80, 1.5, 0.5, 1.5, 0.3);
        loudSound(ModSounds.REACTOR_IGNITE.get(), 5F, 1.0F);
    }

    /** SCRAM: the emitters spark as the beams are cut, and the core flashes. */
    private void scramEffects() {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 center = Vec3.atCenterOf(worldPosition);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            Vec3 from = emitterFace(dir);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 30, 0.4, 0.4, 0.4, 0.4);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 6, 0.2, 0.2, 0.2, 0.02);
        }
        server.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        playSound(ModSounds.REACTOR_POWER_DOWN.get(), 1.0F);
    }

    /** Back to offline: the black hole collapses in on itself. */
    private void powerDownEffects() {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 center = Vec3.atCenterOf(worldPosition);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 120, 3, 0.3, 3, 0.05);
        server.sendParticles(ParticleTypes.SMOKE, center.x, center.y, center.z, 20, 0.5, 0.5, 0.5, 0.02);
        playSound(ModSounds.REACTOR_POWER_DOWN.get(), 1.0F);
    }

    private void broadcast(Component message) {
        if (level == null) return;
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(64))) {
            p.displayClientMessage(message, false);
        }
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound, float pitch) {
        loudSound(sound, 3.0F, pitch);
    }

    /** A sound from the core that carries further: volume above 1 widens its range (16 blocks per 1). */
    private void loudSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (level != null) level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
    }

    // ------------------------------------------------------------------ absorption

    /**
     * Pulls items and living things inside the ring's hole toward the black hole, harder the closer they get.
     * Whatever reaches the horizon is swallowed: 1 mass per item, 1 mass per heart of max health.
     */
    private void absorb() {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 center = Vec3.atCenterOf(worldPosition);
        double radius = pullReach();
        double holeRadius = holeRadius() * stateHoleScale();
        double pullScale = massScale(MIN_PULL_SCALE, MAX_PULL_SCALE);
        AABB zone = new AABB(worldPosition).inflate(radius);
        if (preview == null && ReactorConfig.PULL_BLOCKS.get()) tearBlocks(pullScale);
        if (preview == null && SubLevelPull.hook != null) pullSubLevels(server, center, radius, pullScale);
        int inFlight = 0;
        for (Entity e : level.getEntitiesOfClass(Entity.class, zone,
                e -> e.isAlive() && (e instanceof ItemEntity || e instanceof LivingEntity || e instanceof FallingBlockEntity))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            if (e instanceof ItemEntity item && item.getItem().is(ModBlocks.CONTAINMENT_BOX.get().asItem())) continue; // contained: immune
            if (e instanceof FallingBlockEntity falling) {
                // Torn blocks fly straight in and pass through anything in the way (the ring, terrain), so they can
                // never get stuck and pile up. One that's somehow still out there after a while is swallowed anyway.
                Vec3 toCore = center.subtract(falling.position());
                double dist = toCore.length();
                if (dist <= holeRadius + 0.5 || falling.tickCount > MAX_FLIGHT_TICKS) {
                    swallow(server, falling);
                    continue;
                }
                inFlight++;
                falling.setNoGravity(true);
                falling.noPhysics = true;
                falling.time = 1; // never times out into an item on the way in
                double speed = Math.min(1.2, (0.25 + 0.6 * (1 - Math.min(1, dist / radius))) * pullScale);
                falling.setDeltaMovement(toCore.scale(speed / dist));
                falling.hurtMarked = true;
                continue;
            }
            Vec3 pos = e.getBoundingBox().getCenter();
            if (pos.distanceToSqr(center) > radius * radius) continue; // a sphere, not the corners of the box

            Vec3 toCore = center.subtract(pos);
            double dist = toCore.length();
            if (dist <= holeRadius + e.getBbWidth() / 2) {
                swallow(server, e);
                continue;
            }
            double closeness = 1 - Math.min(1, dist / radius);
            double pull = (0.02 + 0.12 * closeness * closeness) * pullScale;
            double antiGravity = e instanceof LivingEntity ? 0.08 : 0.04; // they float once caught
            Vec3 motion = e.getDeltaMovement().scale(0.9).add(toCore.normalize().scale(pull))
                    .add(0, antiGravity * Math.min(1, closeness * pullScale), 0);
            if (motion.lengthSqr() > 1) motion = motion.normalize();
            e.setDeltaMovement(motion);
            e.hurtMarked = true;
        }
        blocksInFlight = inFlight;
    }

    /**
     * Radius of the black hole's sphere of influence around the core, in every direction (down into the ground
     * too). Normally it just fits inside the ring; a failure
     * widens it past the ring, more the worse it gets: a stall or fault reaches a few blocks past the ring,
     * pre-meltdown spreads further as its countdown runs (more again after a breach), and the meltdowns further still.
     */
    public double pullReach() {
        double inside = ReactorStructure.RING_INNER_RADIUS;
        double pastRing = ReactorStructure.RING_MAJOR_RADIUS + ReactorStructure.RING_TUBE_RADIUS + 4;
        float danger = getDangerProgress(0);
        return switch (state) {
            case DISC_STALL, IGNITION_FAULT, SHUTDOWN_FAULT -> pastRing;
            case PRE_MELTDOWN -> pastRing + 10 * danger + (breached ? 6 : 0);
            case MELTDOWN_1 -> 40;
            case MELTDOWN_2 -> 48;
            default -> inside;
        };
    }

    /** Whether the black hole has broken out of the ring (see {@link #pullReach}). */
    private boolean pullEscaped() {
        return pullReach() > ReactorStructure.RING_INNER_RADIUS + 0.01;
    }

    private static Set<BlockPos> structureOffsets;

    /** Every reactor and lance block position (relative to the core), spared until a meltdown. */
    private static Set<BlockPos> structureOffsets() {
        if (structureOffsets == null) {
            Set<BlockPos> set = new java.util.HashSet<>();
            for (ReactorStructure.Part part : ReactorStructure.layout()) set.add(part.offset());
            for (ReactorStructure.Part part : ReactorStructure.axialLayout()) set.add(part.offset());
            structureOffsets = set;
        }
        return structureOffsets;
    }

    /**
     * Sweeps the sphere of influence (see {@link #pullReach}) a slice per tick and tears loose a few exposed blocks
     * it finds, more with more mass and more in a failure. They fly in as falling blocks, so the ground under the core
     * is slowly eaten into a bowl. Never liquids, unbreakable blocks or this mod's machines (see BlockContents); a block
     * with storage takes its contents in with it. Until a meltdown the reactor's own
     * blocks are spared, so a breach can still be repaired, and the lance until phase 2 (phase 1's emergency vent needs it); outside the ring it also needs the
     * config's block destruction to be on.
     */
    private void tearBlocks(double pullScale) {
        double reach = pullReach();
        boolean escaped = pullEscaped();
        if (escaped && !ReactorConfig.DESTROY_BLOCKS.get()) reach = ReactorStructure.RING_INNER_RADIUS;
        int r = (int) Math.floor(reach);
        double r2 = reach * reach;
        boolean spareStructure = !state.isMeltdown();
        // The lance lasts one phase longer: meltdown phase 1's emergency vent needs it.
        boolean spareLance = state != ReactorState.MELTDOWN_2;
        // Meltdown phase 1 takes the reactor apart slowly, about one of its blocks a second (it still shields what's
        // behind it); phase 2 eats it as fast as anything else.
        boolean slowStructure = state == ReactorState.MELTDOWN_1;
        boolean structureDue = ticks - lastStructureTear >= STRUCTURE_TEAR_INTERVAL;
        int budget = (int) Math.max(1, Math.round(MAX_TORN_PER_TICK * pullScale / 2 * (escaped ? 2 : 1)));
        budget = Math.min(budget, ReactorConfig.MAX_BLOCKS_IN_FLIGHT.get() - blocksInFlight);
        if (budget <= 0) return;
        int sightChecks = SIGHT_CHECKS_PER_TICK;
        double hole = holeRadius() * stateHoleScale();
        int scan = escaped ? BLOCK_SCAN_PER_TICK * 5 : BLOCK_SCAN_PER_TICK;
        Vec3 center = Vec3.atCenterOf(worldPosition);
        int[] offsets = sphereOffsets(r);
        int end = sphereEnd(r2);
        for (int i = 0; i < scan && budget > 0; i++) {
            // Nearest the core first, one shell at a time: a sweep runs outward until it has torn something, finishes
            // that shell (about a block thick) and starts again from the inside, so the hole grows as a sphere.
            if (scanIndex >= end) {
                if (sweepLimit < 0) sweepFrom = 0; // a sweep that found nothing: next time check from the very middle
                scanIndex = Math.min(sweepFrom, end);
                sweepLimit = -1;
                if (scanIndex >= end) break;
            }
            int d2 = sphereDist2[scanIndex];
            if (sweepLimit >= 0 && d2 > sweepLimit) {
                scanIndex = end; // this shell is done
                continue;
            }
            int packed = offsets[scanIndex++];
            BlockPos offset = new BlockPos((packed >> 16 & 0xFF) - 128, (packed >> 8 & 0xFF) - 128, (packed & 0xFF) - 128);
            boolean ownBlock = structureOffsets().contains(offset);
            if ((spareStructure && ownBlock) || (spareLance && isLanceOffset(offset))) continue;
            if (slowStructure && ownBlock && !structureDue) continue;
            BlockPos pos = worldPosition.offset(offset);
            if (!level.isLoaded(pos)) continue;
            BlockState block = level.getBlockState(pos);
            if (!BlockContents.edible(level, pos, block) || !exposed(pos)) continue;
            if (sightChecks-- <= 0) {
                scanIndex--; // out of checks for this tick: look at this one again next tick
                break;
            }
            if (shielded(pos, center, hole, spareStructure || slowStructure, spareLance)) continue; // behind something the black hole can't take
            BlockContents.Taken taken = level instanceof ServerLevel server ? BlockContents.take(server, pos) : BlockContents.Taken.NONE;
            FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, block);
            BlockContents.attach(falling, taken);
            falling.getPersistentData().putBoolean(TORN, true);
            falling.noPhysics = true;
            falling.setNoGravity(true);
            falling.dropItem = false;
            falling.setDeltaMovement(center.subtract(falling.position()).normalize().scale(0.15));
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
            }
            budget--;
            if (ownBlock) {
                lastStructureTear = ticks;
                structureDue = !slowStructure;
            }
            if (sweepLimit < 0) {
                // The first block this sweep: finish its shell, and start the next sweep just inside it.
                double d = Math.sqrt(d2);
                sweepLimit = (int) Math.ceil((d + 1) * (d + 1));
                sweepFrom = sphereStart((int) Math.floor(Math.max(0, d - 1.5) * Math.max(0, d - 1.5)));
            }
        }
    }

    // Every offset within a sphere around the core, sorted nearest first (packed x, y, z, 8 bits each, +128),
    // and each one's squared distance. Shared by every core and grown when a bigger reach needs it.
    private static int[] sphereOffsets = new int[0], sphereDist2 = new int[0];
    private static int sphereRadius = -1;

    private static int[] sphereOffsets(int radius) {
        if (radius <= sphereRadius) return sphereOffsets;
        int r = Math.min(radius, 127);
        long[] keys = new long[(2 * r + 1) * (2 * r + 1) * (2 * r + 1)];
        int n = 0;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    int d2 = x * x + y * y + z * z;
                    if (d2 == 0 || d2 > r * r + r) continue;
                    // Ties within a shell go top-down, so things peel off from above.
                    int packed = (x + 128) << 16 | (y + 128) << 8 | (z + 128);
                    keys[n++] = (long) d2 << 32 | (long) (255 - (y + 128)) << 24 | packed;
                }
            }
        }
        long[] sorted = java.util.Arrays.copyOf(keys, n);
        java.util.Arrays.sort(sorted);
        int[] offsets = new int[n], dist2 = new int[n];
        for (int i = 0; i < n; i++) {
            offsets[i] = (int) (sorted[i] & 0xFFFFFF);
            dist2[i] = (int) (sorted[i] >>> 32);
        }
        sphereDist2 = dist2;
        sphereOffsets = offsets;
        sphereRadius = r;
        return offsets;
    }

    /** How many sphere offsets are within the reach ({@code r2} = reach squared). */
    private static int sphereEnd(double r2) {
        return sphereStart((int) Math.floor(r2) + 1);
    }

    /** The first sphere offset at squared distance {@code d2} or more. */
    private static int sphereStart(int d2) {
        int lo = 0, hi = sphereDist2.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sphereDist2[mid] < d2) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /**
     * Whether something the black hole can't take (the reactor's own blocks while they're spared, block entities such
     * as the console, unbreakable blocks, liquids) sits on the straight line from the block to the black hole.
     * Ordinary terrain in the way doesn't count: blocks go nearest first, so it's torn away before this one anyway.
     * (A strict line-of-sight test hid most of a flat floor behind its own neighbors, leaving only an X of diagonals.)
     */
    private boolean shielded(BlockPos pos, Vec3 center, double hole, boolean spareStructure, boolean spareLance) {
        Vec3 from = Vec3.atCenterOf(pos);
        Vec3 toCore = center.subtract(from);
        double length = toCore.length() - hole;
        if (length <= 1) return false;
        Vec3 dir = toCore.normalize();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        BlockPos last = pos;
        for (double t = 0.5; t < length; t += 0.5) {
            at.set(from.x + dir.x * t, from.y + dir.y * t, from.z + dir.z * t); // every half block along the line
            if (at.equals(last) || at.equals(worldPosition)) continue;
            last = at.immutable();
            BlockState block = level.getBlockState(at);
            if (block.isAir()) continue;
            if (!BlockContents.edible(level, at, block)) return true;
            BlockPos offset = at.subtract(worldPosition);
            if ((spareStructure && structureOffsets().contains(offset)) || (spareLance && isLanceOffset(offset))) return true;
        }
        return false;
    }

    /**
     * The emergency vent's sound and fury (the visuals are the renderer's VENT_EMERGENCY animation): thunder each
     * time the black hole rebounds against the drain, and when it gives way, an implosion: a roar, a boom and a
     * flash, as the last of it is pulled up the lance.
     */
    private void emergencyVentEffects() {
        if (!(level instanceof ServerLevel server)) return;
        int age = EmergencyVent.BEAM_TICKS - ventTicks;
        float p = age / (float) EmergencyVent.TICKS, before = (age - 1) / (float) EmergencyVent.TICKS;
        Vec3 c = Vec3.atCenterOf(worldPosition);
        if (age == 20) {
            // The standoff: a low groan as the black hole strains against the lance.
            loudSound(ModSounds.REACTOR_VENT_STRAIN.get(), 8F, 1.0F);
        }
        if (p >= EmergencyVent.SURGE && before < EmergencyVent.SURGE) {
            // The surge: the column bursts open.
            loudSound(ModSounds.REACTOR_VENT_RELEASE.get(), 8F, 1.0F);
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        }
        int beat = EmergencyVent.beatIndex(p);
        if (beat >= 0 && beat != EmergencyVent.beatIndex(before)) { // a rebound
            loudSound(ModSounds.REACTOR_REBOUND.get(), 6F, 0.85F + level.random.nextFloat() * 0.3F);
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 80, 2, 2, 2, 0.6);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 40, 3, 3, 3, 0.8);
        }
        if (p >= EmergencyVent.IMPLODE && before < EmergencyVent.IMPLODE) { // it gives way
            loudSound(ModSounds.REACTOR_BREACH.get(), 10F, 0.8F);
            server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 3, 1, 1, 1, 0);
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 2, 1, 1, 1, 0);
            server.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 150, 1, 1, 1, 1.2);
        }
    }

    /**
     * Moving block structures from physics mods (Sable's sub-levels) are pulled in too, and torn apart a few blocks
     * at a time, nearest first, by the same rules as the ground. The torn blocks fly in like any other.
     */
    private void pullSubLevels(ServerLevel server, Vec3 center, double reach, double pullScale) {
        SubLevelPull.Hook hook = SubLevelPull.hook;
        if (hook == null) return;
        hook.pull(server, center, reach, pullScale);
        if (!ReactorConfig.PULL_BLOCKS.get() || ticks % 4 != 0) return;
        // Beyond the ring, only with block destruction on (as for the ground).
        double tearReach = pullEscaped() && !ReactorConfig.DESTROY_BLOCKS.get() ? ReactorStructure.RING_INNER_RADIUS : reach;
        int budget = (int) Math.max(1, Math.round(MAX_TORN_PER_TICK * pullScale / 2)) * 4;
        budget = Math.min(budget, ReactorConfig.MAX_BLOCKS_IN_FLIGHT.get() - blocksInFlight);
        for (SubLevelPull.Torn torn : hook.tear(server, center, tearReach, budget)) {
            spawnTornBlock(server, torn.at(), torn.state(), torn.taken(), center);
        }
    }

    /** A torn block in flight toward the black hole, where there's no block to make it from (it came off a structure). */
    private void spawnTornBlock(ServerLevel server, Vec3 at, BlockState state, BlockContents.Taken taken, Vec3 center) {
        FallingBlockEntity falling = EntityType.FALLING_BLOCK.create(server);
        if (falling == null) return;
        // FallingBlockEntity has no public way to set its block other than loading it.
        CompoundTag tag = falling.saveWithoutId(new CompoundTag());
        tag.put("BlockState", NbtUtils.writeBlockState(state));
        falling.load(tag);
        falling.setPos(at.x, at.y - 0.5, at.z);
        BlockContents.attach(falling, taken);
        falling.getPersistentData().putBoolean(TORN, true);
        falling.noPhysics = true;
        falling.setNoGravity(true);
        falling.dropItem = false;
        falling.time = 1;
        falling.setDeltaMovement(center.subtract(at).normalize().scale(0.15));
        server.addFreshEntity(falling);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.02);
    }

    /** Persistent-data tag on the blocks the black hole tore loose, so they can be let go again. */
    private static final String TORN = "masteris_bhe:torn";

    /**
     * Lets go of every block the black hole tore loose that's still in the air: gravity back on, no more passing
     * through things. They fall and land as ordinary blocks again (or drop as items where they can't be placed).
     */
    private void releaseTornBlocks() {
        if (level == null || level.isClientSide) return;
        double reach = Math.max(pullReach(), 64);
        for (FallingBlockEntity falling : level.getEntitiesOfClass(FallingBlockEntity.class,
                new AABB(worldPosition).inflate(reach), f -> f.isAlive() && f.getPersistentData().getBoolean(TORN))) {
            falling.getPersistentData().remove(TORN);
            falling.setNoGravity(false);
            falling.noPhysics = false;
            falling.dropItem = true;
            falling.time = 1; // a fresh 30 seconds to land
            falling.setDeltaMovement(falling.getDeltaMovement().scale(0.2));
            falling.hurtMarked = true;
        }
    }

    /** Blocks are only torn off a surface: something solid deep in the ground stays put until it's uncovered. */
    private boolean exposed(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (level.isEmptyBlock(pos.relative(dir))) return true;
        }
        return false;
    }

    private boolean isLanceOffset(BlockPos offset) {
        return Math.abs(offset.getX()) <= 1 && Math.abs(offset.getZ()) <= 1
                && offset.getY() >= VentingLance.MIN_BOTTOM && offset.getY() <= VentingLance.MAX_BOTTOM + VentingLance.HEIGHT;
    }

    /** sqrt(mass / ignition minimum), clamped: 1 at the ignition minimum, 2 at four times that. */
    private double massScale(double min, double max) {
        double reference = 4.0 * ReactorConfig.MIN_MASS_PER_STABILIZER.get();
        if (reference <= 0) return 1;
        return Mth.clamp(Math.sqrt(totalMass / reference), min, max);
    }

    /** Black hole radius from the current mass (see ReactorStructure.BLACK_HOLE_RADIUS). */
    private double holeRadius() {
        return ReactorStructure.BLACK_HOLE_RADIUS * massScale(
                ReactorStructure.BLACK_HOLE_MIN_RADIUS / ReactorStructure.BLACK_HOLE_RADIUS,
                ReactorStructure.BLACK_HOLE_MAX_RADIUS / ReactorStructure.BLACK_HOLE_RADIUS);
    }

    private void swallow(ServerLevel server, Entity e) {
        if (e instanceof ItemEntity item && item.getItem().is(ModItems.SINGULARITY_CONTAINER.get())) {
            captureSingularity(server, item);
            return;
        }
        long mass;
        if (e instanceof ItemEntity item) {
            // Blocks and dense materials by MassValues; anything else at swallowedItemMass.
            long per = MassValues.per(item.getItem().getItem());
            mass = (per > 0 ? per : ReactorConfig.ITEM_MASS.get()) * item.getItem().getCount();
            item.discard();
        } else if (e instanceof FallingBlockEntity falling) {
            mass = BlockContents.massOf(falling); // blockMass (Neutronium more), plus 1 per item it carried in
            falling.discard();
        } else if (e instanceof LivingEntity living) {
            mass = (long) Math.ceil(living.getMaxHealth() / 2 * ReactorConfig.CREATURE_MASS_PER_HEART.get()); // per heart
            living.hurt(level.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            if (living.isAlive()) return; // totem or similar: it survives, no mass
            if (!(living instanceof Player)) living.discard(); // no drops, no corpse: it's gone
        } else {
            return;
        }
        addAbsorbedMass(mass);
        Vec3 p = e.position();
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y + e.getBbHeight() / 2, p.z, 12, 0.2, 0.2, 0.2, 0.05);
        if (ticks - lastAbsorbSound >= 4) {
            lastAbsorbSound = ticks;
            level.playSound(null, worldPosition, ModSounds.REACTOR_ABSORB.get(), SoundSource.BLOCKS, 1.5F, 0.8F + level.random.nextFloat() * 0.4F);
        }
    }

    /**
     * A Singularity Container thrown into the black hole pinches off a tiny piece of it: it's flung back out as a
     * Containment Box with a black hole inside (one per container), for the Research Chamber. Never despawns.
     */
    private void captureSingularity(ServerLevel server, ItemEntity container) {
        int count = container.getItem().getCount();
        container.discard();
        Vec3 c = Vec3.atCenterOf(worldPosition);
        double out = holeRadius() * stateHoleScale() + 1.5;
        for (int i = 0; i < count; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            ItemEntity box = new ItemEntity(level, c.x, c.y + out, c.z, new ItemStack(ModBlocks.CONTAINMENT_BOX.get()),
                    Math.cos(angle) * 0.25, 0.6, Math.sin(angle) * 0.25);
            box.setUnlimitedLifetime();
            box.setGlowingTag(true);
            box.setPickUpDelay(20);
            level.addFreshEntity(box);
        }
        server.sendParticles(ParticleTypes.FLASH, c.x, c.y + out, c.z, 1, 0, 0, 0, 0);
        server.sendParticles(ParticleTypes.END_ROD, c.x, c.y + out, c.z, 30, 0.4, 0.4, 0.4, 0.2);
        loudSound(ModSounds.REACTOR_CAPTURE.get(), 3F, 1.0F);
    }

    /** Splits mass evenly across the stabilizers; the remainder goes round-robin so nothing is lost. */
    private void addAbsorbedMass(long mass) {
        List<MassInputPortBlockEntity> ports = new ArrayList<>(4);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            MassInputPortBlockEntity port = massPort(dir);
            if (port != null) ports.add(port);
        }
        if (ports.isEmpty()) return;
        long share = mass / ports.size();
        long remainder = mass % ports.size();
        for (MassInputPortBlockEntity port : ports) {
            if (share > 0) port.addMass(share);
        }
        for (long i = 0; i < remainder; i++) {
            ports.get(massCursor++ % ports.size()).addMass(1);
        }
        massCursor %= ports.size();
        totalMass += mass;
    }

    // ------------------------------------------------------------------ client animations

    private void startAnimation(ReactorState previous, int previousScramSeq, int previousBreachSeq) {
        Anim next = Anim.NONE;
        if (previous == ReactorState.OFFLINE && state != ReactorState.OFFLINE) {
            next = Anim.STARTUP;
        } else if (previous != ReactorState.OFFLINE && state == ReactorState.OFFLINE) {
            next = ventTicks > 0 ? (ventEmergency ? Anim.VENT_EMERGENCY : Anim.VENT)
                    : scrammed ? Anim.SCRAM_POWER_DOWN : Anim.POWER_DOWN;
        } else if (breachSeq != previousBreachSeq) {
            next = Anim.BREACH;
        } else if (scramSeq != previousScramSeq) {
            next = Anim.SCRAM;
        } else if (state != previous) {
            next = switch (state) {
                case DISC_STALL -> Anim.STALL;
                case PRE_MELTDOWN -> Anim.ALARM;
                case MELTDOWN_1 -> Anim.MELTDOWN_1;
                case MELTDOWN_2 -> Anim.MELTDOWN_2;
                case IGNITION_FAULT, SHUTDOWN_FAULT -> Anim.FAULT;
                default -> Anim.NONE;
            };
        }
        if (next != Anim.NONE) {
            anim = next;
            animStart = clientTicks;
        }
    }

    // ------------------------------------------------------------------ client sync

    private float spinFraction() {
        // Spin has no top speed, so the visuals reach full speed at twice the safe band.
        return (float) Mth.clamp(rpm / (2.0 * ReactorConfig.SAFE_RPM.get()), -1, 1); // signed: negative spins backwards
    }

    private void syncIfChanged() {
        if (level == null) return;
        int hash = java.util.Objects.hash(formed, state, offset, Math.round(spinFraction() * 50), scrammed, scramSeq,
                Math.round(holeRadius() * 20), Math.round(stress / 5), stateTimer / 100, starving(), breachSeq, fault, faultMask,
                lanceFormed, lanceTop, ventTicks > 0, ventArmed(), faultFixed, axialFormed, Math.round(wobble / 4));
        if (hash == lastSyncHash) return;
        lastSyncHash = hash;
        BlockState blockState = getBlockState();
        level.sendBlockUpdated(worldPosition, blockState, blockState, Block.UPDATE_CLIENTS);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Formed", formed);
        tag.putBoolean("Axial", axialFormed);
        tag.putFloat("Wobble", (float) wobble);
        tag.putString("State", state.name());
        tag.putInt("Offset", offset);
        tag.putFloat("Spin", spinFraction());
        tag.putFloat("HoleRadius", (float) holeRadius());
        tag.putBoolean("Scrammed", scrammed);
        tag.putInt("ScramSeq", scramSeq);
        tag.putInt("BreachSeq", breachSeq);
        tag.putDouble("Stress", stress);
        tag.putInt("StateTimer", stateTimer);
        tag.putInt("StateDuration", stateDuration);
        tag.putBoolean("Starving", starving());
        tag.putString("Fault", fault.name());
        tag.putInt("FaultMask", faultMask);
        tag.putBoolean("FaultFixed", faultFixed);
        tag.putBoolean("LanceFormed", lanceFormed);
        tag.putInt("LanceTop", lanceTop);
        tag.putInt("VentTicks", ventTicks);
        tag.putBoolean("VentEmergency", ventEmergency);
        tag.putInt("VentArmed", ventArmedUntil);
        return tag;
    }

    private static Fault faultOf(String name) {
        try {
            return name.isEmpty() ? Fault.NONE : Fault.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Fault.NONE;
        }
    }

    private boolean starving() {
        if (preview != null) return preview == Preview.STARVATION;
        return state != ReactorState.OFFLINE && totalMass < ReactorConfig.WARNING_MASS.get();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Formed", formed);
        tag.putBoolean("Axial", axialFormed);
        tag.putString("State", state.name());
        tag.putDouble("Stability", stability);
        tag.putDouble("Rpm", rpm);
        tag.putDouble("ResonanceDrift", resonanceDrift);
        tag.putDouble("Wobble", wobble);
        tag.putDouble("Precession", precession);
        tag.putInt("AxialTrim", axialTrim);
        net.minecraft.nbt.ListTag stack = new net.minecraft.nbt.ListTag();
        for (Suspended s : suspended) {
            CompoundTag t = new CompoundTag();
            t.putString("State", s.state().name());
            t.putInt("Timer", s.timer());
            t.putInt("Duration", s.duration());
            t.putString("Fault", s.fault().name());
            t.putInt("FaultMask", s.faultMask());
            t.putBoolean("FaultFixed", s.faultFixed());
            t.putString("PendingFault", s.pendingFault().name());
            t.putInt("ShutdownTicks", s.shutdownTicks());
            stack.add(t);
        }
        tag.put("Suspended", stack);
        tag.putDouble("Stress", stress);
        tag.putDouble("MassDebt", massDebt);
        tag.putInt("Offset", offset);
        tag.putInt("TargetOffset", targetOffset);
        tag.putInt("LaserPower", laserPower);
        tag.putInt("StateTimer", stateTimer);
        tag.putInt("StateDuration", stateDuration);
        tag.putBoolean("Scrammed", scrammed);
        tag.putBoolean("Breached", breached);
        tag.putString("Fault", fault.name());
        tag.putString("PendingFault", pendingFault.name());
        tag.putInt("FaultMask", faultMask);
        tag.putBoolean("FaultFixed", faultFixed);
        if (preview != null) tag.putString("Preview", preview.name());
        tag.putInt("Energy", energy.getEnergyStored());
        if (laserPos != null) tag.put("Laser", NbtUtils.writeBlockPos(laserPos));
        ListTag ports = new ListTag();
        for (BlockPos p : reactorPorts) ports.add(NbtUtils.writeBlockPos(p));
        tag.put("Ports", ports);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ReactorState previousState = state;
        int previousScramSeq = scramSeq, previousBreachSeq = breachSeq;
        float previousHoleRadius = clientHoleRadius;
        formed = tag.getBoolean("Formed");
        axialFormed = tag.getBoolean("Axial");
        try {
            state = ReactorState.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException e) {
            state = ReactorState.OFFLINE;
        }
        stability = tag.contains("Stability") ? tag.getDouble("Stability") : 100;
        rpm = tag.getDouble("Rpm");
        resonanceDrift = tag.getDouble("ResonanceDrift");
        wobble = tag.getDouble("Wobble");
        precession = tag.getDouble("Precession");
        axialTrim = Mth.clamp(tag.getInt("AxialTrim"), -MAX_TRIM, MAX_TRIM);
        if (tag.contains("Suspended")) {
            suspended.clear();
            for (net.minecraft.nbt.Tag raw : tag.getList("Suspended", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                CompoundTag t = (CompoundTag) raw;
                ReactorState st;
                try {
                    st = ReactorState.valueOf(t.getString("State"));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                suspended.add(new Suspended(st, t.getInt("Timer"), t.getInt("Duration"), faultOf(t.getString("Fault")),
                        t.getInt("FaultMask"), t.getBoolean("FaultFixed"), faultOf(t.getString("PendingFault")), t.getInt("ShutdownTicks")));
            }
        }
        stress = tag.getDouble("Stress");
        massDebt = tag.getDouble("MassDebt");
        offset = tag.getInt("Offset");
        targetOffset = tag.getInt("TargetOffset");
        laserPower = tag.getInt("LaserPower");
        stateTimer = tag.getInt("StateTimer");
        stateDuration = tag.contains("StateDuration") ? tag.getInt("StateDuration") : stateTimer;
        breachSeq = tag.getInt("BreachSeq");
        fault = faultOf(tag.getString("Fault"));
        breached = tag.getBoolean("Breached");
        pendingFault = faultOf(tag.getString("PendingFault"));
        faultMask = tag.getInt("FaultMask");
        faultFixed = tag.getBoolean("FaultFixed");
        lanceFormed = tag.getBoolean("LanceFormed");
        if (tag.contains("LanceTop")) lanceTop = tag.getInt("LanceTop");
        ventTicks = tag.getInt("VentTicks");
        ventEmergency = tag.getBoolean("VentEmergency");
        ventArmedUntil = tag.getInt("VentArmed");
        try {
            preview = tag.contains("Preview") ? Preview.valueOf(tag.getString("Preview")) : null;
        } catch (IllegalArgumentException e) {
            preview = null;
        }
        clientStarving = tag.getBoolean("Starving");
        clientSpin = tag.getFloat("Spin");
        if (tag.contains("HoleRadius")) clientHoleRadius = tag.getFloat("HoleRadius");
        scrammed = tag.getBoolean("Scrammed");
        scramSeq = tag.getInt("ScramSeq");
        if (level != null && level.isClientSide) {
            // The first sync just tells us where things are; only later changes animate.
            if (clientSynced) startAnimation(previousState, previousScramSeq, previousBreachSeq);
            if ((anim == Anim.VENT || anim == Anim.VENT_EMERGENCY) && animStart == clientTicks) ventHoleRadius = previousHoleRadius;
            clientSynced = true;
        }
        energy.setEnergy(tag.getInt("Energy"));
        laserPos = NbtUtils.readBlockPos(tag, "Laser").orElse(null);
        reactorPorts.clear();
        for (Tag t : tag.getList("Ports", Tag.TAG_INT_ARRAY)) {
            if (t instanceof net.minecraft.nbt.IntArrayTag arr && arr.size() == 3) {
                reactorPorts.add(new BlockPos(arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt()));
            }
        }
    }
}
