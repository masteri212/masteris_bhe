package com.masteri.bhe.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config: config/masteris_bhe-server.toml inside each world's serverconfig folder. */
public final class ReactorConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static { B.push("startup"); }
    public static final ModConfigSpec.IntValue IGNITION_ENERGY = B.comment("RF consumed by ignition.")
            .defineInRange("ignitionEnergy", 50_000_000, 0, 1_000_000_000);
    public static final ModConfigSpec.IntValue MIN_MASS_PER_STABILIZER = B.comment("Mass each stabilizer needs before ignition.")
            .defineInRange("minMassPerStabilizer", 10_000, 0, Integer.MAX_VALUE);
    static { B.pop().push("mass"); }
    public static final ModConfigSpec.IntValue WARNING_MASS = B.comment("Below this total mass, stability starts dropping.")
            .defineInRange("warningMass", 10_000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue CRITICAL_MASS = B.comment("Below this total mass, the core can't be held (pre-meltdown).")
            .defineInRange("criticalMass", 4_000, 0, Integer.MAX_VALUE);
    static { B.pop().push("laser"); }
    public static final ModConfigSpec.IntValue LASER_MAX_RATE = B.comment("RF/t the bending laser fires at 100% power.")
            .defineInRange("laserMaxRate", 200_000, 0, 10_000_000);
    public static final ModConfigSpec.DoubleValue LASER_GAIN_MIN = B.comment("Output multiplier at low laser power.")
            .defineInRange("laserGainMin", 1.15, 1.0, 10.0);
    public static final ModConfigSpec.DoubleValue LASER_GAIN_MAX = B.comment("Output multiplier at 100% laser power.")
            .defineInRange("laserGainMax", 1.30, 1.0, 10.0);
    public static final ModConfigSpec.IntValue LASER_RF_PER_MASS = B.comment("RF fired by the laser per 1 mass drained.")
            .defineInRange("laserRfPerMass", 100_000, 1, Integer.MAX_VALUE);
    static { B.pop().push("rotation"); }
    public static final ModConfigSpec.IntValue MAX_OFFSET = B.comment("Maximum stabilizer offset in degrees.")
            .defineInRange("maxOffset", 15, 1, 90);
    public static final ModConfigSpec.IntValue RPM_PER_OFFSET = B.comment("Target spin per degree of offset.")
            .defineInRange("rpmPerOffset", 400, 1, 100_000);
    public static final ModConfigSpec.IntValue SAFE_RPM = B.comment("Above this spin, the containment ring takes stress.")
            .defineInRange("safeRpm", 4_000, 1, 1_000_000);
    public static final ModConfigSpec.DoubleValue RF_PER_RPM_SQUARED = B.comment("Rotational output = this x rpm^2 (RF/t). 0.0125 gives 200k RF/t at 4000 rpm.")
            .defineInRange("rfPerRpmSquared", 0.0125, 0.0, 1000.0);
    static { B.pop().push("failures"); }
    public static final ModConfigSpec.IntValue STALL_SECONDS = B.defineInRange("discStallSeconds", 60, 5, 3600);
    public static final ModConfigSpec.IntValue PRE_MELTDOWN_SECONDS = B.defineInRange("preMeltdownSeconds", 90, 5, 3600);
    public static final ModConfigSpec.IntValue MELTDOWN_1_SECONDS = B.defineInRange("meltdownPhase1Seconds", 120, 5, 3600);
    public static final ModConfigSpec.IntValue MELTDOWN_2_SECONDS = B.defineInRange("meltdownPhase2Seconds", 60, 5, 3600);
    static { B.pop().push("collapse"); }
    public static final ModConfigSpec.BooleanValue DESTROY_BLOCKS = B.comment("Whether a reactor collapse destroys blocks.")
            .define("destroyBlocks", true);
    public static final ModConfigSpec.DoubleValue BASE_RADIUS = B.defineInRange("baseExplosionRadius", 12.0, 1.0, 200.0);
    public static final ModConfigSpec.IntValue MASS_PER_RADIUS = B.comment("Extra explosion radius: +1 per this much mass.")
            .defineInRange("massPerRadius", 5_000, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue MAX_RADIUS = B.comment("Hard cap on the collapse explosion radius (big values lag).")
            .defineInRange("maxExplosionRadius", 40.0, 1.0, 200.0);
    static { B.pop(); }

    public static final ModConfigSpec SPEC = B.build();

    private ReactorConfig() {}
}
