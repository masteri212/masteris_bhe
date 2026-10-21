package com.masteri.bhe.registry;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.entity.DrifterEntity;
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

    /** Mod bus. */
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DRIFTER.get(), DrifterEntity.createAttributes().build());
    }

    private ModEntities() {}
}
