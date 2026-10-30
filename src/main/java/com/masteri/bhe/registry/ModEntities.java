package com.masteri.bhe.registry;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.entity.DrifterEntity;
import com.masteri.bhe.entity.EchoOfTheCoreEntity;
import com.masteri.bhe.entity.HawkingWispEntity;
import com.masteri.bhe.entity.SingularityEntity;
import com.masteri.bhe.entity.SpaghettifierEntity;
import com.masteri.bhe.entity.LensingStalkerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MasteriBHE.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<DrifterEntity>> DRIFTER = ENTITY_TYPES.register("drifter",
            () -> EntityType.Builder.of(DrifterEntity::new, MobCategory.AMBIENT)
                    .sized(0.9F, 1.3F)
                    .clientTrackingRange(8)
                    .build("drifter"));

    public static final DeferredHolder<EntityType<?>, EntityType<LensingStalkerEntity>> LENSING_STALKER = ENTITY_TYPES.register("lensing_stalker",
            () -> EntityType.Builder.of(LensingStalkerEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 3.0F)
                    .eyeHeight(2.7F)
                    .clientTrackingRange(10)
                    .build("lensing_stalker"));

    public static final DeferredHolder<EntityType<?>, EntityType<EchoOfTheCoreEntity>> ECHO_OF_THE_CORE = ENTITY_TYPES.register("echo_of_the_core",
            () -> EntityType.Builder.of(EchoOfTheCoreEntity::new, MobCategory.MONSTER)
                    .sized(2.2F, 2.0F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .build("echo_of_the_core"));

    public static final DeferredHolder<EntityType<?>, EntityType<SpaghettifierEntity>> SPAGHETTIFIER = ENTITY_TYPES.register("spaghettifier",
            () -> EntityType.Builder.of(SpaghettifierEntity::new, MobCategory.MONSTER)
                    .sized(1.0F, 0.8F)
                    .clientTrackingRange(10)
                    .build("spaghettifier"));

    public static final DeferredHolder<EntityType<?>, EntityType<HawkingWispEntity>> HAWKING_WISP = ENTITY_TYPES.register("hawking_wisp",
            () -> EntityType.Builder.of(HawkingWispEntity::new, MobCategory.AMBIENT)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .build("hawking_wisp"));

    public static final DeferredHolder<EntityType<?>, EntityType<SingularityEntity>> SINGULARITY = ENTITY_TYPES.register("singularity",
            () -> EntityType.Builder.of(SingularityEntity::new, MobCategory.MONSTER)
                    .sized(4.0F, 4.0F)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("singularity"));

    /** A Light Trap's delayed copy of a player (not saved: the trap makes a new one). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.masteri.bhe.entity.LightEchoEntity>> LIGHT_ECHO = ENTITY_TYPES.register("light_echo",
            () -> EntityType.Builder.<com.masteri.bhe.entity.LightEchoEntity>of(com.masteri.bhe.entity.LightEchoEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSave()
                    .build("light_echo"));

    /** A chunk of rubble orbiting a Debris Ring: solid, and carries what stands on it. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.masteri.bhe.entity.DebrisPlatformEntity>> DEBRIS_PLATFORM = ENTITY_TYPES.register("debris_platform",
            () -> EntityType.Builder.<com.masteri.bhe.entity.DebrisPlatformEntity>of(com.masteri.bhe.entity.DebrisPlatformEntity::new, MobCategory.MISC)
                    .sized(3.0F, 1.0F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("debris_platform"));

    /** Mod bus. The Event Horizon's mobs float, so they spawn in the air (natural spawning, from the biomes). */
    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(SPAGHETTIFIER.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SpaghettifierEntity::checkSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(HAWKING_WISP.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                HawkingWispEntity::checkSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        // Drifters drift through the Event Horizon's upper layers too (in the zones ZoneMobSpawner places them).
        event.register(DRIFTER.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above()),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /** Mod bus. */
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DRIFTER.get(), DrifterEntity.createAttributes().build());
        event.put(LENSING_STALKER.get(), LensingStalkerEntity.createAttributes().build());
        event.put(ECHO_OF_THE_CORE.get(), EchoOfTheCoreEntity.createAttributes().build());
        event.put(SPAGHETTIFIER.get(), SpaghettifierEntity.createAttributes().build());
        event.put(HAWKING_WISP.get(), HawkingWispEntity.createAttributes().build());
        event.put(SINGULARITY.get(), SingularityEntity.createAttributes().build());
    }

    private ModEntities() {}
}
