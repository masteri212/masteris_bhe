package com.masteri.bhe.reactor;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.config.ReactorConfig;
import com.masteri.bhe.registry.ModBlocks;
import com.masteri.bhe.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.joml.Vector3f;

/**
 * What it's like inside a dark energy zone: weak gravity (and softer landings) for every mob and player, corrupted
 * hostile mobs, and Unstable Dark Ore that sometimes discharges when mined. The terrain itself is in ZoneTerrain.
 */
@EventBusSubscriber(modid = MasteriBHE.MODID)
public final class ZoneEffects {
    private static final ResourceLocation GRAVITY_ID = id("zone_gravity");
    private static final ResourceLocation FALL_DAMAGE_ID = id("zone_fall_damage");
    private static final ResourceLocation HEALTH_ID = id("corrupted_health");
    private static final ResourceLocation DAMAGE_ID = id("corrupted_damage");
    private static final ResourceLocation SPEED_ID = id("corrupted_speed");
    private static final ResourceLocation ARMOR_ID = id("corrupted_armor");
    private static final String CORRUPTED = MasteriBHE.MODID + ":corrupted";
    private static final DustParticleOptions TRAIL = new DustParticleOptions(new Vector3f(0.55F, 0.1F, 0.85F), 1.2F);

    public static boolean isCorrupted(Entity entity) {
        return entity.getPersistentData().getBoolean(CORRUPTED);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living) || !(living.level() instanceof ServerLevel level)) return;
        if (living.tickCount % 10 == 0) updateGravity(level, living);
        if (living.tickCount % 3 == 0 && isCorrupted(living)) {
            double w = living.getBbWidth() * 0.3, h = living.getBbHeight();
            level.sendParticles(TRAIL, living.getX(), living.getY() + h * 0.4, living.getZ(), 2, w, h * 0.3, w, 0);
        }
    }

    /**
     * Adds the weak-gravity modifiers while the entity is inside a zone and takes them off once it leaves (or the
     * zone goes away). Transient, so they're never saved; gravity is a synced attribute, so players' own jumps
     * float on their client too.
     */
    private static void updateGravity(ServerLevel level, LivingEntity living) {
        AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
        if (gravity == null) return;
        if (DarkZones.inZone(level, living.getX(), living.getY(), living.getZ())) {
            double amount = ReactorConfig.ZONE_GRAVITY.get() - 1; // x0.4 gravity -> -0.6 of the total
            AttributeModifier current = gravity.getModifier(GRAVITY_ID);
            if (current != null && current.amount() == amount) return;
            gravity.addOrUpdateTransientModifier(new AttributeModifier(GRAVITY_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            AttributeInstance fall = living.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER);
            if (fall != null) {
                fall.addOrUpdateTransientModifier(new AttributeModifier(FALL_DAMAGE_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        } else if (gravity.hasModifier(GRAVITY_ID)) {
            gravity.removeModifier(GRAVITY_ID);
            AttributeInstance fall = living.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER);
            if (fall != null) fall.removeModifier(FALL_DAMAGE_ID);
        }
    }

    // ------------------------------------------------------------------ corrupted mobs

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        // getLevel() is a WorldGenRegion during chunk generation; zones are only read on the real level.
        if (!(mob instanceof Enemy) || !(event.getLevel() instanceof ServerLevel level) || !ReactorConfig.CORRUPTED_MOBS.get()) return;
        if (DarkZones.inFallout(level, event.getX(), event.getY(), event.getZ())) corrupt(mob); // only near the crater
    }

    /** Marks the mob as corrupted (saved with it) and makes it tougher: 2x health, +50% damage, faster, some armor. */
    public static void corrupt(Mob mob) {
        mob.getPersistentData().putBoolean(CORRUPTED, true);
        modify(mob, Attributes.MAX_HEALTH, HEALTH_ID, ReactorConfig.CORRUPTED_HEALTH.get(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, ReactorConfig.CORRUPTED_DAMAGE.get(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, ReactorConfig.CORRUPTED_SPEED.get(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(mob, Attributes.ARMOR, ARMOR_ID, ReactorConfig.CORRUPTED_ARMOR.get(), AttributeModifier.Operation.ADD_VALUE);
        mob.setHealth(mob.getMaxHealth());
    }

    private static void modify(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id, double amount,
                               AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!isCorrupted(entity) || entity.level().isClientSide) return;
        RandomSource random = entity.getRandom();
        event.getDrops().add(drop(entity, new ItemStack(ModItems.VOID_DUST.get(), 1 + random.nextInt(2))));
        if (event.isRecentlyHit() && random.nextDouble() < ReactorConfig.GRAVITON_CHANCE.get()) {
            event.getDrops().add(drop(entity, new ItemStack(ModItems.GRAVITON_CORE.get())));
        }
    }

    private static ItemEntity drop(LivingEntity entity, ItemStack stack) {
        ItemEntity item = new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack);
        item.setDefaultPickUpDelay();
        return item;
    }

    // ------------------------------------------------------------------ unstable dark ore

    /** Mining Unstable Dark Ore sometimes discharges: a small blast that hurts but leaves the blocks alone. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.getState().is(ModBlocks.UNSTABLE_DARK_ORE.get())) return;
        Player player = event.getPlayer();
        if (player.isCreative() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (level.random.nextDouble() >= ReactorConfig.DISCHARGE_CHANCE.get()) return;
        Vec3 c = Vec3.atCenterOf(event.getPos());
        level.explode(null, c.x, c.y, c.z, 1.5F, Level.ExplosionInteraction.NONE);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 40, 0.4, 0.4, 0.4, 0.3);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 20, 0.5, 0.5, 0.5, 0.4);
        level.playSound(null, event.getPos(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.BLOCKS, 0.6F, 1.6F);
        player.hurt(level.damageSources().magic(), 2F);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, path);
    }

    private ZoneEffects() {}
}
