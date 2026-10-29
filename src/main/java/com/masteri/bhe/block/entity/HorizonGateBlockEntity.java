package com.masteri.bhe.block.entity;

import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.horizon.EventHorizon;
import com.masteri.bhe.horizon.HorizonRing;
import com.masteri.bhe.reactor.MassValues;
import com.masteri.bhe.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A Horizon Gate: the base of a Horizon Ring (see HorizonRing). Fed mass (by hand or hopper, like a bomb), it opens
 * the ring: a spin-up while the frames light one by one, a burst out of both faces, then an open portal for a
 * while. Every player who steps through goes into the Event Horizon, for a trip's worth of mass each. Inside the
 * Event Horizon a ring opens for free and leads back out.
 */
public class HorizonGateBlockEntity extends BlockEntity {
    public enum Phase { IDLE, SPINNING, OPEN }

    public static final int SPIN_TICKS = 80, BURST_TICKS = 16, CLOSE_TICKS = 20;
    private static final float BURST_REACH = 5;

    private long mass;
    private Phase phase = Phase.IDLE;
    /** Ticks since the phase began (both sides count; synced on every change). */
    private int age;
    /** While open: ticks until it closes. */
    private int left;
    @Nullable private Direction.Axis axis;
    private boolean powered;

    private final IItemHandler itemHandler = new IItemHandler() {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return MassValues.isMass(stack); }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!MassValues.isMass(stack)) return stack;
            if (!simulate) addMass(MassValues.of(stack));
            return ItemStack.EMPTY;
        }
    };

    public HorizonGateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HORIZON_GATE.get(), pos, state);
    }

    public IItemHandler getItemHandler() { return itemHandler; }
    public long getMass() { return mass; }
    public Phase getPhase() { return phase; }
    public int getAge() { return age; }
    public int getLeft() { return left; }
    @Nullable public Direction.Axis getAxis() { return axis; }

    public static long tripCost() {
        return ReactorConfig.GATE_MASS_PER_TRIP.get();
    }

    private boolean free() {
        return level != null && EventHorizon.isHorizon(level);
    }

    /** 0..1: how charged it is for one trip (1 inside the Event Horizon). For the renderer. */
    public float charge() {
        if (free()) return 1;
        long cost = tripCost();
        return cost <= 0 ? 1 : Math.min(1, mass / (float) cost);
    }

    public void addMass(long amount) {
        if (amount <= 0) return;
        mass += amount;
        changed();
    }

    // ------------------------------------------------------------------ opening and closing

    /** Starts the ring spinning up, if it's built, clear and charged. Tells the player why not otherwise. */
    public void activate(@Nullable Player player) {
        if (!(level instanceof ServerLevel server)) return;
        if (phase != Phase.IDLE) {
            say(player, Component.translatable(EventHorizon.key("gate_busy")).withStyle(ChatFormatting.GRAY));
            return;
        }
        Direction.Axis found = HorizonRing.find(server, worldPosition);
        if (found == null) {
            int missing = HorizonRing.missing(server, worldPosition, HorizonRing.likely(server, worldPosition));
            say(player, Component.translatable(EventHorizon.key("ring_incomplete"), missing).withStyle(ChatFormatting.RED));
            return;
        }
        if (!HorizonRing.clear(server, worldPosition, found)) {
            say(player, Component.translatable(EventHorizon.key("ring_blocked")).withStyle(ChatFormatting.RED));
            return;
        }
        if (!free() && mass < tripCost()) {
            say(player, Component.translatable(EventHorizon.key("gate_charging"), String.format("%,d", mass),
                    String.format("%,d", tripCost())).withStyle(ChatFormatting.GRAY));
            return;
        }
        axis = found;
        phase = Phase.SPINNING;
        age = 0;
        sound(SoundEvents.BEACON_POWER_SELECT, 2F, 0.5F);
        sound(SoundEvents.CONDUIT_ACTIVATE, 2F, 0.6F);
        changed();
    }

    private void open(ServerLevel server) {
        phase = Phase.OPEN;
        age = 0;
        left = ReactorConfig.GATE_OPEN_SECONDS.get() * 20;
        sound(SoundEvents.WARDEN_SONIC_BOOM, 3F, 0.6F);
        sound(SoundEvents.END_PORTAL_SPAWN, 2F, 1.1F);
        Vec3 c = HorizonRing.center(worldPosition);
        server.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        changed();
    }

    private void close(boolean fizzle) {
        phase = Phase.IDLE;
        age = 0;
        left = 0;
        sound(fizzle ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.BEACON_DEACTIVATE, 2F, fizzle ? 0.6F : 0.8F);
        changed();
    }

    public void neighborSignal(boolean signal) {
        if (signal && !powered) activate(null); // rising edge only
        powered = signal;
    }

    // ------------------------------------------------------------------ ticking

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        age++;
        switch (phase) {
            case SPINNING -> {
                // A click as each frame lights, rising in pitch.
                int frames = HorizonRing.FRAMES.size();
                int lit = age * frames / SPIN_TICKS, before = (age - 1) * frames / SPIN_TICKS;
                if (lit != before) sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2F, 0.5F + 1.5F * lit / frames);
                if (age % 20 == 0) sound(SoundEvents.BEACON_AMBIENT, 2F, 0.6F + age / (float) SPIN_TICKS);
                if (age >= SPIN_TICKS) {
                    if (stillStanding(server)) open(server);
                    else close(true);
                }
            }
            case OPEN -> {
                left--;
                if (age < BURST_TICKS) burst(server);
                if (age % 10 == 0 && !stillStanding(server)) {
                    close(true);
                    return;
                }
                if (age % 40 == 0) sound(SoundEvents.PORTAL_AMBIENT, 1.5F, 0.6F);
                if (age > BURST_TICKS / 2) carryThrough(server);
                if (left <= 0 && phase == Phase.OPEN) close(false);
            }
            default -> {}
        }
    }

    /** Client side: keep counting between syncs, for the animation. */
    public void clientTick() {
        age++;
        if (phase == Phase.OPEN && left > 0) left--;
    }

    private boolean stillStanding(ServerLevel server) {
        return axis != null && HorizonRing.find(server, worldPosition) == axis && HorizonRing.clear(server, worldPosition, axis);
    }

    /** The opening burst: anything standing right in front of (or behind) the ring is thrown back and hurt. */
    private void burst(ServerLevel server) {
        if (axis == null) return;
        Vec3 c = HorizonRing.center(worldPosition);
        Direction n = HorizonRing.normal(axis);
        float reach = BURST_REACH * (float) Math.sin(Math.PI * age / BURST_TICKS);
        AABB box = new AABB(c, c).inflate(HorizonRing.INNER_RADIUS).expandTowards(n.getStepX() * reach, 0, n.getStepZ() * reach)
                .expandTowards(-n.getStepX() * reach, 0, -n.getStepZ() * reach);
        for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, box)) {
            if (living instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            Vec3 rel = living.position().subtract(c);
            double side = rel.x * n.getStepX() + rel.z * n.getStepZ();
            if (Math.abs(side) < 0.3) continue;
            Vec3 push = new Vec3(n.getStepX(), 0, n.getStepZ()).scale(Math.signum(side) * 1.2).add(0, 0.4, 0);
            living.setDeltaMovement(living.getDeltaMovement().add(push));
            living.hurtMarked = true;
            if (age == 1) living.hurt(server.damageSources().magic(), 4);
        }
        server.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 20, n.getStepX() * reach / 2 + 1, 2, n.getStepZ() * reach / 2 + 1, 0.3);
    }

    /** Players stepping into the portal go through (into the Event Horizon for a trip's mass; out of it for free). */
    private void carryThrough(ServerLevel server) {
        if (axis == null) return;
        Vec3 c = HorizonRing.center(worldPosition);
        for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(HorizonRing.INNER_RADIUS, HorizonRing.INNER_RADIUS, HorizonRing.INNER_RADIUS))) {
            if (player.isSpectator() || player.isOnPortalCooldown() || !HorizonRing.inPortal(player, worldPosition, axis)) continue;
            if (free()) {
                EventHorizon.leave(player);
            } else if (mass >= tripCost()) {
                mass -= tripCost();
                changed();
                EventHorizon.enter(player, worldPosition);
            } else {
                player.displayClientMessage(Component.translatable(EventHorizon.key("out_of_mass")).withStyle(ChatFormatting.RED), true);
                close(true);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ helpers, syncing and saving

    private void say(@Nullable Player player, Component text) {
        if (player != null) player.displayClientMessage(text, true);
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        if (level != null) level.playSound(null, worldPosition.above(HorizonRing.CENTER_UP), sound, SoundSource.BLOCKS, volume, pitch);
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("Mass", mass);
        tag.putString("Phase", phase.name());
        tag.putInt("Age", age);
        tag.putInt("Left", left);
        if (axis != null) tag.putString("Axis", axis.getName());
        tag.putBoolean("Powered", powered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mass = tag.getLong("Mass");
        try {
            phase = Phase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException e) {
            phase = Phase.IDLE;
        }
        age = tag.getInt("Age");
        left = tag.getInt("Left");
        axis = tag.contains("Axis") ? Direction.Axis.byName(tag.getString("Axis")) : null;
        powered = tag.getBoolean("Powered");
    }
}
