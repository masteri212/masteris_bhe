package com.masteri.bhe.horizon;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.entity.HawkingWispEntity;
import com.masteri.bhe.entity.SingularityEntity;
import com.masteri.bhe.entity.SpaghettifierEntity;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Event Horizon's rules that change with depth: gravity (and fall damage) for every mob and player,
 * spaghettification below the line, and time dilation in the deep. Also thaws Frozen Mobs when they're hit.
 */
@EventBusSubscriber(modid = MasteriBHE.MODID)
public final class HorizonEffects {
    private static final ResourceLocation GRAVITY_ID = id("horizon_gravity");
    private static final ResourceLocation FALL_DAMAGE_ID = id("horizon_fall_damage");
    private static final DustParticleOptions STRETCH = new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.15F), 1.0F);
    /** Chunks around each player in the deep that time dilation reaches. */
    private static final int DILATION_RADIUS = 4;

    // ------------------------------------------------------------------ gravity and spaghettification

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living) || !(living.level() instanceof ServerLevel level)) return;
        boolean inside = EventHorizon.isHorizon(level);
        if (living.tickCount % 10 == 0) updateGravity(living, inside);
        if (inside && living.getY() < EventHorizon.spaghettificationY() && !native_(living)) stretch(level, living);
    }

    /** Gravity by height while inside; taken off again once the entity is somewhere else. Transient, like the zone's. */
    private static void updateGravity(LivingEntity living, boolean inside) {
        AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
        if (gravity == null) return;
        AttributeInstance fall = living.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER);
        if (inside) {
            double amount = Math.round((EventHorizon.gravityAt(living.getY()) - 1) * 100) / 100.0; // 1% steps: fewer updates
            AttributeModifier current = gravity.getModifier(GRAVITY_ID);
            if (current != null && current.amount() == amount) return;
            gravity.addOrUpdateTransientModifier(new AttributeModifier(GRAVITY_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            if (fall != null) fall.addOrUpdateTransientModifier(new AttributeModifier(FALL_DAMAGE_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (gravity.hasModifier(GRAVITY_ID)) {
            gravity.removeModifier(GRAVITY_ID);
            if (fall != null) fall.removeModifier(FALL_DAMAGE_ID);
        }
    }

    /** The Event Horizon's own creatures live with the singularity. */
    private static boolean native_(LivingEntity living) {
        return living instanceof SpaghettifierEntity || living instanceof HawkingWispEntity || living instanceof SingularityEntity;
    }

    /** Below the line: dragged down harder the deeper it is, and torn apart. */
    private static void stretch(ServerLevel level, LivingEntity living) {
        if (living instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        double depth = EventHorizon.spaghettificationY() - living.getY();
        Vec3 v = living.getDeltaMovement();
        living.setDeltaMovement(v.x * 0.9, v.y - 0.03 - 0.004 * depth, v.z * 0.9);
        if (living instanceof ServerPlayer && living.tickCount % 4 == 0) living.hurtMarked = true;
        if (living.tickCount % 10 == 0) {
            living.hurt(EventHorizon.spaghettified(level, null), 2F + (float) depth * 0.15F);
            level.sendParticles(STRETCH, living.getX(), living.getY() + living.getBbHeight() / 2, living.getZ(), 8,
                    living.getBbWidth() * 0.3, living.getBbHeight(), living.getBbWidth() * 0.3, 0);
        }
    }

    // ------------------------------------------------------------------ time dilation

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !EventHorizon.isHorizon(level)) return;
        if (EventHorizon.dilationAt(EventHorizon.DEEP_FULL) <= 1) return; // turned off
        int randomTicks = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        LongSet done = new LongOpenHashSet();
        for (ServerPlayer player : level.players()) {
            if (player.getY() > EventHorizon.DEEP_START + 48) continue; // too far above the deep to matter
            int cx = player.chunkPosition().x, cz = player.chunkPosition().z;
            for (int dx = -DILATION_RADIUS; dx <= DILATION_RADIUS; dx++) {
                for (int dz = -DILATION_RADIUS; dz <= DILATION_RADIUS; dz++) {
                    if (!done.add(net.minecraft.world.level.ChunkPos.asLong(cx + dx, cz + dz))) continue;
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                    if (chunk != null) dilate(level, chunk, randomTicks);
                }
            }
        }
    }

    /**
     * Gives the deep parts of a chunk the extra time: extra random ticks (crops, saplings, ...) in each section,
     * and extra furnace ticks, both scaled by the dilation at their height. Fractions are rolled.
     */
    private static void dilate(ServerLevel level, LevelChunk chunk, int randomTicks) {
        RandomSource random = level.random;
        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            if (baseY >= EventHorizon.DEEP_START || randomTicks <= 0 || !section.isRandomlyTicking()) continue;
            int extra = roll(random, (EventHorizon.dilationAt(baseY + 8) - 1) * randomTicks);
            for (int n = 0; n < extra; n++) {
                BlockPos pos = level.getBlockRandomPos(minX, baseY, minZ, 15);
                BlockState state = section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
                if (state.isRandomlyTicking()) state.randomTick(level, pos, random);
            }
        }
        List<BlockEntity> furnaces = new ArrayList<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof AbstractFurnaceBlockEntity && be.getBlockPos().getY() < EventHorizon.DEEP_START) furnaces.add(be);
        }
        for (BlockEntity be : furnaces) {
            int extra = roll(random, EventHorizon.dilationAt(be.getBlockPos().getY()) - 1);
            for (int n = 0; n < extra && !be.isRemoved(); n++) {
                AbstractFurnaceBlockEntity.serverTick(level, be.getBlockPos(), level.getBlockState(be.getBlockPos()), (AbstractFurnaceBlockEntity) be);
            }
        }
    }

    /** {@code amount} rounded up or down at random, so the fraction comes out right on average. */
    private static int roll(RandomSource random, double amount) {
        int whole = (int) amount;
        return whole + (random.nextDouble() < amount - whole ? 1 : 0);
    }

    // ------------------------------------------------------------------ frozen mobs

    /** Hitting a Frozen Mob (see FrozenMobFeature) thaws it: it moves, and falls, again. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity living = event.getEntity();
        if (living.level().isClientSide || !FrozenMobFeature.isFrozen(living)) return;
        FrozenMobFeature.thaw(living);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, path);
    }

    private HorizonEffects() {}
}
