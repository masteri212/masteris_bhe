package com.masteri.bhe.registry;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.entity.DrifterEntity;
import com.masteri.bhe.entity.EchoOfTheCoreEntity;
import com.masteri.bhe.entity.LensingStalkerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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

    /** Mod bus. */
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DRIFTER.get(), DrifterEntity.createAttributes().build());
        event.put(LENSING_STALKER.get(), LensingStalkerEntity.createAttributes().build());
        event.put(ECHO_OF_THE_CORE.get(), EchoOfTheCoreEntity.createAttributes().build());
    }

    private ModEntities() {}
}
