package com.masteri.bhe.block.entity;

import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.reactor.Crater;
import com.masteri.bhe.reactor.DarkZones;
import com.masteri.bhe.reactor.ZoneTerrain;
import com.masteri.bhe.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The Black Hole Bomb: a captured black hole fed with mass (any block, 1 mass each, by hand or hopper) and set
 * off with a fuse. Its blast is bigger than a reactor collapse holding the same mass: the same carved crater, the
 * crater's loot and a dark energy zone. In the fuse's last 5 seconds the black hole breaks out and starts pulling.
 */
public class BlackHoleBombBlockEntity extends BlockEntity {
    /** The black hole breaks out of the casing for the fuse's last stretch and pulls everything near. */
    public static final int BREAKOUT_TICKS = 100;
    private static final double PULL_RANGE = 24;

    private long mass;
    private boolean armed;
    private int fuse, fuseTotal;

    private final IItemHandler itemHandler = new IItemHandler() {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return MassInputPortBlockEntity.isMass(stack); }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!MassInputPortBlockEntity.isMass(stack)) return stack;
            if (!simulate) addMass(com.masteri.bhe.reactor.MassValues.of(stack));
            return ItemStack.EMPTY;
        }
    };

    public BlackHoleBombBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BLACK_HOLE_BOMB.get(), pos, state);
    }

    public IItemHandler getItemHandler() { return itemHandler; }
    public long getMass() { return mass; }
    public boolean isArmed() { return armed; }
    public int getFuse() { return fuse; }
    public int getFuseTotal() { return fuseTotal; }

    /** 0..1 through the fuse (0 when not armed). */
    public float fuseProgress(float partialTick) {
        return !armed || fuseTotal <= 0 ? 0 : Math.min(1, 1 - (fuse - partialTick) / fuseTotal);
    }

    /** Blast radius: bigger than a reactor collapse with the same mass. */
    public int radius() {
        double r = ReactorConfig.BOMB_BASE_RADIUS.get() + mass / (double) ReactorConfig.BOMB_MASS_PER_RADIUS.get();
        return (int) Math.round(Math.min(ReactorConfig.BOMB_MAX_RADIUS.get(), r));
    }

    public void addMass(long amount) {
        if (amount <= 0) return;
        mass += amount;
        setChanged();
        sync();
    }

    // ------------------------------------------------------------------ fuse

    public void arm() {
        if (armed || level == null) return;
        if (!ReactorConfig.BOMB_ENABLED.get()) {
            broadcast(Component.translatable("message.masteris_bhe.bomb.disabled").withStyle(ChatFormatting.GRAY), 16);
            sound(SoundEvents.FIRE_EXTINGUISH, 0.6F, 1F);
            return;
        }
        armed = true;
        fuseTotal = fuse = ReactorConfig.BOMB_FUSE_SECONDS.get() * 20;
        sound(SoundEvents.TNT_PRIMED, 0.5F, 1.5F);
        sound(SoundEvents.BEACON_ACTIVATE, 0.6F, 2F);
        broadcast(Component.translatable("message.masteris_bhe.bomb.armed", fuseTotal / 20, radius()).withStyle(ChatFormatting.RED), 96);
        setChanged();
        sync();
    }

    public void disarm(Player player) {
        if (!armed) return;
        armed = false;
        fuse = 0;
        sound(SoundEvents.BEACON_DEACTIVATE, 1F, 1.4F);
        broadcast(Component.translatable("message.masteris_bhe.bomb.disarmed", player.getDisplayName()).withStyle(ChatFormatting.GREEN), 96);
        setChanged();
        sync();
    }

    public void serverTick() {
        if (!armed || !(level instanceof ServerLevel server)) return;
        fuse--;
        Vec3 c = Vec3.atCenterOf(worldPosition);
        // Beeps, faster as it runs down.
        int interval = fuse > 200 ? 20 : fuse > BREAKOUT_TICKS ? 10 : 4;
        if (fuse % interval == 0) {
            float urgency = 1 - fuse / (float) fuseTotal;
            sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1F, 0.8F + urgency);
        }
        if (fuse % 20 == 0 && fuse > 0) {
            broadcastBar(Component.translatable("message.masteris_bhe.bomb.countdown", fuse / 20).withStyle(ChatFormatting.DARK_RED));
        }
        if (fuse <= BREAKOUT_TICKS) breakout(server, c);
        if (fuse <= 0) detonate();
    }

    /** The last few seconds: the black hole tears out of the casing and pulls everything close in. */
    private void breakout(ServerLevel server, Vec3 c) {
        if (fuse == BREAKOUT_TICKS) {
            sound(SoundEvents.WARDEN_SONIC_BOOM, 2F, 0.6F);
            sound(SoundEvents.END_PORTAL_SPAWN, 2F, 1.2F);
        }
        if (fuse % 10 == 0) sound(SoundEvents.WARDEN_HEARTBEAT, 2F, 0.6F);
        float strength = 1 - fuse / (float) BREAKOUT_TICKS;
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 6 + (int) (20 * strength), 3, 3, 3, 0.3);
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(worldPosition).inflate(PULL_RANGE),
                e -> e.isAlive() && (e instanceof LivingEntity || e instanceof ItemEntity))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            Vec3 toBomb = c.subtract(e.position());
            double dist = toBomb.length();
            if (dist > PULL_RANGE || dist < 0.5) continue;
            double pull = (0.03 + 0.12 * (1 - dist / PULL_RANGE)) * (0.4 + strength);
            e.setDeltaMovement(e.getDeltaMovement().scale(0.9).add(toBomb.scale(pull / dist)).add(0, 0.04, 0));
            e.hurtMarked = true;
        }
    }

    /** Goes off now: the collapse blast, the crater's loot and (by default) a dark energy zone. */
    public void detonate() {
        if (!(level instanceof ServerLevel server)) return;
        BlockPos pos = worldPosition;
        int radius = radius();
        long heldMass = mass;
        armed = false;
        Vec3 c = Vec3.atCenterOf(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.explode(null, c.x, c.y, c.z, 8F, Level.ExplosionInteraction.NONE); // the bang; the crater is carved below
        Crater.blast(server, pos, radius, ReactorConfig.DESTROY_BLOCKS.get());
        if (ReactorConfig.BOMB_LEAVES_ZONE.get()) {
            DarkZones.get(server).add(server, Crater.floor(server, pos, radius), DarkZones.radiusForMass(heldMass * 2));
            ZoneTerrain.collapse(server, pos, radius);
        }
        broadcast(Component.translatable("message.masteris_bhe.bomb.detonated", pos.getX(), pos.getY(), pos.getZ(), radius)
                .withStyle(ChatFormatting.DARK_PURPLE), 1000);
    }

    // ------------------------------------------------------------------ helpers

    private void sound(SoundEvent sound, float volume, float pitch) {
        if (level != null) level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume * 4, pitch);
    }

    private void broadcast(Component message, double range) {
        if (level == null) return;
        Vec3 c = Vec3.atCenterOf(worldPosition);
        for (Player player : level.players()) {
            if (player.distanceToSqr(c) <= range * range) player.displayClientMessage(message, false);
        }
    }

    private void broadcastBar(Component message) {
        if (level == null) return;
        Vec3 c = Vec3.atCenterOf(worldPosition);
        for (Player player : level.players()) {
            if (player.distanceToSqr(c) <= 64 * 64) player.displayClientMessage(message, true);
        }
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Client side: the fuse runs down locally between syncs, for the renderer. */
    public void clientTick() {
        if (armed && fuse > 0) fuse--;
    }

    // ------------------------------------------------------------------ syncing and saving

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
        tag.putBoolean("Armed", armed);
        tag.putInt("Fuse", fuse);
        tag.putInt("FuseTotal", fuseTotal);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mass = tag.getLong("Mass");
        armed = tag.getBoolean("Armed");
        fuse = tag.getInt("Fuse");
        fuseTotal = tag.getInt("FuseTotal");
    }
}
