package com.masteri.bhe.registry;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.horizon.FrozenMobFeature;
import com.masteri.bhe.horizon.MassShardsFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Worldgen features in code; where and how often they're placed is data (worldgen/placed_feature). */
public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MasteriBHE.MODID);

    public static final DeferredHolder<Feature<?>, MassShardsFeature> MASS_SHARDS = FEATURES.register("mass_shards",
            () -> new MassShardsFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, FrozenMobFeature> FROZEN_MOB = FEATURES.register("frozen_mob",
            () -> new FrozenMobFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {}
}
