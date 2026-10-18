package com.masteri.bhe.block.entity;

import com.masteri.bhe.block.ReactorCoreBlock;
import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.multiblock.ReactorStructure;
import com.masteri.bhe.reactor.ReactorEnergy;
import com.masteri.bhe.reactor.ReactorState;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The reactor's brain. Holds every stat from the design doc (mass, stability, spin, ring stress, charge)
 * and runs the state machine: offline -> ignition -> stable/warning -> faults -> meltdown -> collapse.
 */
public class ReactorCoreBlockEntity extends BlockEntity {
    public static final int BUTTON_IGNITE = 0, BUTTON_SHUTDOWN = 1, BUTTON_SCRAM = 2, BUTTON_OFFSET_DOWN = 3,
            BUTTON_OFFSET_UP = 4, BUTTON_LASER_DOWN = 5, BUTTON_LASER_UP = 6, BUTTON_JUMP_START = 7;
    public static final int DATA_SIZE = 16;

    private static final int RECHECK_INTERVAL = 40;
    private static final int STALL_WINDOW_PERIOD = 80;   // the jump-start window comes around every 4 s ...
    private static final int STALL_WINDOW_LENGTH = 16;   // ... and stays open for 0.8 s

    private final ReactorEnergy energy = new ReactorEnergy();
    private final List<BlockPos> reactorPorts = new ArrayList<>();
    @Nullable private BlockPos laserPos;

    private boolean formed;
    private ReactorState state = ReactorState.OFFLINE;
    private double stability = 100, rpm, stress, massDebt;
    private int offset, targetOffset, laserPower, stateTimer, lastOutput, ticks;
    private long totalMass;

    /** Synced to open consoles. Values are 16-bit on the wire, so big numbers are split in two. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            int mass = (int) Math.min(totalMass, Integer.MAX_VALUE);
            return switch (i) {
                case 0 -> state.ordinal();
                case 1 -> (int) Math.round(stability * 10);
                case 2 -> (int) Math.round(rpm);
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
                case 13 -> inJumpWindow() ? 1 : 0;
                case 14 -> lastOutput & 0xFFFF;
                case 15 -> lastOutput >>> 16;
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

    public void linkLaser(BlockPos pos) {
        if (!pos.equals(laserPos)) {
            laserPos = pos;
            setChanged();
        }
    }

    // ------------------------------------------------------------------ structure

    public ReactorStructure.Result checkStructure() {
        ReactorStructure.Result result = ReactorStructure.check(level, worldPosition);
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

    private void tick() {
        ticks++;
        if (ticks % 20 == 0) {
            long[] masses = stabilizerMasses();
            totalMass = masses[0] + masses[1] + masses[2] + masses[3];
        }

        if (ticks % RECHECK_INTERVAL == 0 && (formed || state != ReactorState.OFFLINE)) {
            boolean intact = ReactorStructure.check(level, worldPosition).complete();
            if (!intact) {
                if (state.generates() || state == ReactorState.DISC_STALL) {
                    broadcast(Component.translatable("message.masteris_bhe.reactor.breach").withStyle(ChatFormatting.RED));
                    enterState(ReactorState.PRE_MELTDOWN, ReactorConfig.PRE_MELTDOWN_SECONDS.get() * 20);
                }
                if (state == ReactorState.OFFLINE) setFormed(false);
            }
        }

        switch (state) {
            case OFFLINE -> {
                rpm = Math.max(0, rpm - 50);
                offset = targetOffset = 0;
                laserPower = 0;
                stress = Math.max(0, stress - 0.1);
                stability = Math.min(100, stability + 0.5);
            }
            case STABLE, WARNING -> {
                runReactor();
                if (state != ReactorState.STABLE && state != ReactorState.WARNING) break; // a fault fired
                if (stability < 20 || stress >= 100 || totalMass < ReactorConfig.CRITICAL_MASS.get()) {
                    enterState(ReactorState.PRE_MELTDOWN, ReactorConfig.PRE_MELTDOWN_SECONDS.get() * 20);
                } else {
                    state = stability < 50 ? ReactorState.WARNING : ReactorState.STABLE;
                }
            }
            case SHUTTING_DOWN -> {
                targetOffset = 0;
                laserPower = 0;
                runReactor();
                if (state == ReactorState.SHUTTING_DOWN && offset == 0 && rpm < 50) {
                    enterState(ReactorState.OFFLINE, 0);
                    playSound(SoundEvents.BEACON_DEACTIVATE, 1.0F);
                }
            }
            case DISC_STALL -> {
                rpm = 0;
                stability = Math.max(0, stability - 0.02);
                if (--stateTimer <= 0) enterState(ReactorState.PRE_MELTDOWN, ReactorConfig.PRE_MELTDOWN_SECONDS.get() * 20);
            }
            case PRE_MELTDOWN -> {
                runReactor();
                if (state != ReactorState.PRE_MELTDOWN) break;
                screenShakeParticles(6);
                boolean recovered = ticks % 20 == 0 && formed && stability >= 35 && stress < 90
                        && totalMass >= ReactorConfig.CRITICAL_MASS.get()
                        && ReactorStructure.check(level, worldPosition).complete();
                if (recovered) {
                    broadcast(Component.translatable("message.masteris_bhe.reactor.recovered").withStyle(ChatFormatting.GREEN));
                    enterState(ReactorState.WARNING, 0);
                } else if (--stateTimer <= 0) {
                    enterState(ReactorState.MELTDOWN_1, ReactorConfig.MELTDOWN_1_SECONDS.get() * 20);
                }
            }
            case MELTDOWN_1 -> {
                meltdownEffects(20, 0.04, 2.0F);
                if (--stateTimer <= 0) enterState(ReactorState.MELTDOWN_2, ReactorConfig.MELTDOWN_2_SECONDS.get() * 20);
            }
            case MELTDOWN_2 -> {
                meltdownEffects(32, 0.10, 4.0F);
                if (--stateTimer <= 0) {
                    collapse();
                    return; // the core is gone
                }
            }
        }

        if (ticks % 40 == 0 && state.isAlarm()) {
            playSound(SoundEvents.NOTE_BLOCK_BIT.value(), state.isMeltdown() ? 0.5F : 0.8F);
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

        double maxRpm = ReactorConfig.MAX_OFFSET.get() * (double) ReactorConfig.RPM_PER_OFFSET.get();
        double targetRpm = offset * (double) ReactorConfig.RPM_PER_OFFSET.get();
        double safeRpm = ReactorConfig.SAFE_RPM.get();

        // Disc stall: the stabilizers snapped back while the disc was spinning hard.
        if (rpm > 0.6 * maxRpm && targetRpm < rpm * 0.4) {
            enterDiscStall();
            return;
        }
        rpm += Mth.clamp(targetRpm - rpm, -20, 20);

        long[] masses = stabilizerMasses();
        totalMass = masses[0] + masses[1] + masses[2] + masses[3];
        boolean starvedStabilizer = masses[0] == 0 || masses[1] == 0 || masses[2] == 0 || masses[3] == 0;
        if (starvedStabilizer && rpm > 1000) {
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

        // Power mode 2: rotation. Output grows with the square of the spin.
        double rotational = ReactorConfig.RF_PER_RPM_SQUARED.get() * rpm * rpm;
        long output = (long) (rotational + laserIn * gain);
        energy.generate(output);
        lastOutput = (int) Math.min(output, Integer.MAX_VALUE);

        // Ring stress builds above the safe spin band and cools off below it.
        if (rpm > safeRpm) stress = Math.min(100, stress + (rpm - safeRpm) / 20_000.0);
        else stress = Math.max(0, stress - 0.05);

        double target = 100
                - offset * 2.0
                - laserPower * 0.2
                - stress * 0.3
                - (rpm > safeRpm ? (rpm - safeRpm) / 100.0 : 0)
                - (totalMass < ReactorConfig.WARNING_MASS.get() ? 20 : 0);
        stability = Mth.clamp(stability + Mth.clamp(target - stability, -0.2, 0.2), 0, 100);
    }

    private void enterDiscStall() {
        // The trapped rotational energy dumps into the core as charge.
        energy.generate((long) (ReactorConfig.RF_PER_RPM_SQUARED.get() * rpm * rpm * 200));
        rpm = 0;
        stability = Math.max(0, stability - 15);
        enterState(ReactorState.DISC_STALL, ReactorConfig.STALL_SECONDS.get() * 20);
        playSound(SoundEvents.ANVIL_LAND, 0.5F);
        broadcast(Component.translatable("message.masteris_bhe.reactor.disc_stall").withStyle(ChatFormatting.GOLD));
    }

    private boolean inJumpWindow() {
        return state == ReactorState.DISC_STALL && stateTimer % STALL_WINDOW_PERIOD < STALL_WINDOW_LENGTH;
    }

    private void enterState(ReactorState next, int timer) {
        state = next;
        stateTimer = timer;
        setChanged();
    }

    // ------------------------------------------------------------------ console buttons

    public void handleButton(Player player, int id) {
        switch (id) {
            case BUTTON_IGNITE -> ignite(player);
            case BUTTON_SHUTDOWN -> {
                if (state == ReactorState.STABLE || state == ReactorState.WARNING) {
                    enterState(ReactorState.SHUTTING_DOWN, 0);
                    tell(player, "shutdown", ChatFormatting.AQUA);
                }
            }
            case BUTTON_SCRAM -> scram(player);
            case BUTTON_OFFSET_DOWN -> { if (canAdjust()) targetOffset = Math.max(0, targetOffset - 1); }
            case BUTTON_OFFSET_UP -> { if (canAdjust()) targetOffset = Math.min(ReactorConfig.MAX_OFFSET.get(), targetOffset + 1); }
            case BUTTON_LASER_DOWN -> { if (canAdjust()) laserPower = Math.max(0, laserPower - 10); }
            case BUTTON_LASER_UP -> { if (canAdjust()) laserPower = Math.min(100, laserPower + 10); }
            case BUTTON_JUMP_START -> jumpStart(player);
            default -> {}
        }
        setChanged();
    }

    private boolean canAdjust() {
        return state == ReactorState.STABLE || state == ReactorState.WARNING || state == ReactorState.PRE_MELTDOWN;
    }

    private void ignite(Player player) {
        if (state != ReactorState.OFFLINE) { tell(player, "ignite.running", ChatFormatting.YELLOW); return; }
        if (!checkStructure().complete()) { tell(player, "ignite.structure", ChatFormatting.RED); return; }
        long min = ReactorConfig.MIN_MASS_PER_STABILIZER.get();
        for (long mass : stabilizerMasses()) {
            if (mass < min) {
                player.displayClientMessage(Component.translatable("message.masteris_bhe.ignite.mass", min)
                        .withStyle(ChatFormatting.RED), false);
                fizzle();
                return;
            }
        }
        if (!energy.consume(ReactorConfig.IGNITION_ENERGY.get())) {
            player.displayClientMessage(Component.translatable("message.masteris_bhe.ignite.energy",
                    ReactorConfig.IGNITION_ENERGY.get()).withStyle(ChatFormatting.RED), false);
            fizzle();
            return;
        }
        stability = 100;
        stress = 0;
        rpm = 0;
        enterState(ReactorState.STABLE, 0);
        playSound(SoundEvents.BEACON_ACTIVATE, 1.0F);
        tell(player, "ignite.success", ChatFormatting.GREEN);
    }

    private void fizzle() {
        playSound(SoundEvents.FIRE_EXTINGUISH, 1.0F);
    }

    private void scram(Player player) {
        if (!state.generates()) return;
        double maxRpm = ReactorConfig.MAX_OFFSET.get() * (double) ReactorConfig.RPM_PER_OFFSET.get();
        offset = targetOffset = 0;
        laserPower = 0;
        if (rpm > 0.6 * maxRpm) {
            enterDiscStall(); // snapping the stabilizers back at high spin stalls the disc
        } else if (state != ReactorState.PRE_MELTDOWN) {
            enterState(ReactorState.SHUTTING_DOWN, 0);
            tell(player, "scram", ChatFormatting.AQUA);
        }
    }

    private void jumpStart(Player player) {
        if (state != ReactorState.DISC_STALL) return;
        if (inJumpWindow()) {
            targetOffset = offset = Math.max(1, targetOffset);
            rpm = 600;
            stability = Math.min(100, stability + 10);
            enterState(ReactorState.WARNING, 0);
            playSound(SoundEvents.BEACON_POWER_SELECT, 1.2F);
            tell(player, "jump.success", ChatFormatting.GREEN);
        } else {
            stateTimer = Math.max(1, stateTimer - 200);
            energy.generate(5_000_000);
            playSound(SoundEvents.FIRE_EXTINGUISH, 0.6F);
            tell(player, "jump.fail", ChatFormatting.RED);
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
        lvl.explode(null, c.x, c.y, c.z, radius, destroy ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        // TODO: crater floor materials and the dark energy zone.
    }

    // ------------------------------------------------------------------ helpers

    private void tell(Player player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable("message.masteris_bhe." + key).withStyle(color), false);
    }

    private void broadcast(Component message) {
        if (level == null) return;
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(64))) {
            p.displayClientMessage(message, false);
        }
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound, float pitch) {
        if (level != null) level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 3.0F, pitch);
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Formed", formed);
        tag.putString("State", state.name());
        tag.putDouble("Stability", stability);
        tag.putDouble("Rpm", rpm);
        tag.putDouble("Stress", stress);
        tag.putDouble("MassDebt", massDebt);
        tag.putInt("Offset", offset);
        tag.putInt("TargetOffset", targetOffset);
        tag.putInt("LaserPower", laserPower);
        tag.putInt("StateTimer", stateTimer);
        tag.putInt("Energy", energy.getEnergyStored());
        if (laserPos != null) tag.put("Laser", NbtUtils.writeBlockPos(laserPos));
        ListTag ports = new ListTag();
        for (BlockPos p : reactorPorts) ports.add(NbtUtils.writeBlockPos(p));
        tag.put("Ports", ports);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        formed = tag.getBoolean("Formed");
        try {
            state = ReactorState.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException e) {
            state = ReactorState.OFFLINE;
        }
        stability = tag.contains("Stability") ? tag.getDouble("Stability") : 100;
        rpm = tag.getDouble("Rpm");
        stress = tag.getDouble("Stress");
        massDebt = tag.getDouble("MassDebt");
        offset = tag.getInt("Offset");
        targetOffset = tag.getInt("TargetOffset");
        laserPower = tag.getInt("LaserPower");
        stateTimer = tag.getInt("StateTimer");
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
