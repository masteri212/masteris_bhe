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
    public static final ModConfigSpec.DoubleValue SPIN_ACCELERATION = B.comment("Spin gained per tick for each degree of offset (rpm/t). There is no top speed.")
            .defineInRange("spinAcceleration", 0.5, 0.0, 1000.0);
    public static final ModConfigSpec.IntValue RF_PER_RPM_SPENT = B.comment("Rotational RF made per 1 rpm of spin used up. Spin is only spent on RF the core can store.")
            .defineInRange("rfPerRpmSpent", 2_000_000, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue SHUTDOWN_BRAKE = B.comment("Spin removed per tick while shutting down (rpm/t).")
            .defineInRange("shutdownBrake", 40, 1, 100_000);
    public static final ModConfigSpec.IntValue SAFE_RPM = B.comment("Above this spin, the containment ring takes stress.")
            .defineInRange("safeRpm", 4_000, 1, 1_000_000);
    public static final ModConfigSpec.DoubleValue RF_PER_RPM_SQUARED = B.comment("Rotational output = this x rpm^2 (RF/t). 0.0125 gives 200k RF/t at 4000 rpm.")
            .defineInRange("rfPerRpmSquared", 0.0125, 0.0, 1000.0);
    public static final ModConfigSpec.IntValue RESONANCE_RPM = B.comment("Center of the resonance band (where spin turns into RF at full efficiency) at the ignition minimum of mass.",
                    "It rises with the square root of the mass, stays under 95% of the safe spin, and drifts on its own.")
            .defineInRange("resonanceRpm", 2500, 100, 1_000_000);
    public static final ModConfigSpec.IntValue RESONANCE_WIDTH = B.comment("Half the width of the resonance band, in rpm. Wider is easier.")
            .defineInRange("resonanceWidth", 300, 10, 100_000);
    public static final ModConfigSpec.DoubleValue RESONANCE_MIN_EFFICIENCY = B.comment("Spin-to-RF efficiency far outside the band.")
            .defineInRange("resonanceMinEfficiency", 0.2, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue RESONANCE_DRIFT = B.comment("How fast the band wanders, roughly in rpm per second (it also jumps about once a minute).")
            .defineInRange("resonanceDrift", 6.0, 0.0, 10_000.0);
    public static final ModConfigSpec.DoubleValue PRECESSION_STRENGTH = B.comment("How hard the disc's precession pushes it to wobble (countered with the axial trim). 0 turns wobble off.")
            .defineInRange("precessionStrength", 1.0, 0.0, 10.0);
    static { B.pop().push("failures"); }
    public static final ModConfigSpec.IntValue STALL_SECONDS = B.defineInRange("discStallSeconds", 60, 5, 3600);
    public static final ModConfigSpec.DoubleValue FAULT_BASE_CHANCE = B.comment("Chance of an ignition or shutdown fault on a perfectly steady reactor.",
                    "Instability raises it (see the manual): uneven or barely-enough mass for ignition; low stability, ring stress and overspin for shutdown.")
            .defineInRange("faultBaseChance", 0.05, 0.0, 1.0);
    public static final ModConfigSpec.IntValue FAULT_SECONDS = B.comment("Time to fix an ignition or shutdown fault before the reactor goes to pre-meltdown.")
            .defineInRange("faultSeconds", 120, 5, 3600);
    public static final ModConfigSpec.DoubleValue VENT_MASS_FRACTION = B.comment("Share of the mass a normal vent through the lance burns.")
            .defineInRange("ventMassFraction", 0.15, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue BREACH_VENT_MASS_FRACTION = B.comment("Share of the mass a vent burns while the ring is breached.")
            .defineInRange("breachVentMassFraction", 0.75, 0.0, 1.0);
    public static final ModConfigSpec.IntValue REBOUND_DRAIN_RF = B.comment("Singularity rebound: drain the core's charge below this to let it close.")
            .defineInRange("reboundDrainRf", 5_000_000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue PRE_MELTDOWN_SECONDS = B.defineInRange("preMeltdownSeconds", 90, 5, 3600);
    public static final ModConfigSpec.IntValue MELTDOWN_1_SECONDS = B.defineInRange("meltdownPhase1Seconds", 120, 5, 3600);
    public static final ModConfigSpec.IntValue MELTDOWN_2_SECONDS = B.defineInRange("meltdownPhase2Seconds", 60, 5, 3600);
    static { B.pop().push("collapse"); }
    public static final ModConfigSpec.BooleanValue PULL_BLOCKS = B.comment("Whether a running reactor's black hole tears loose blocks in its sphere of influence (just inside",
                    "the ring, down into the ground too) and swallows them as mass, 1 per block. In a failure the sphere grows",
                    "past the ring (that part also needs destroyBlocks).")
            .define("pullBlocks", true);
    public static final ModConfigSpec.IntValue MAX_BLOCKS_IN_FLIGHT = B.comment("Most torn-loose blocks flying into the black hole at once (each is an entity; lower it if it lags).")
            .defineInRange("maxBlocksInFlight", 300, 0, 5000);
    public static final ModConfigSpec.BooleanValue DESTROY_BLOCKS = B.comment("Whether a reactor collapse destroys blocks.")
            .define("destroyBlocks", true);
    public static final ModConfigSpec.DoubleValue BASE_RADIUS = B.comment("Collapse blast radius in blocks, before the mass bonus.")
            .defineInRange("baseExplosionRadius", 50.0, 1.0, 200.0);
    public static final ModConfigSpec.IntValue MASS_PER_RADIUS = B.comment("Extra explosion radius: +1 per this much mass.")
            .defineInRange("massPerRadius", 10_000, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue MAX_RADIUS = B.comment("Hard cap on the collapse explosion radius. The crater is carved over a few seconds; very big values still take a while.")
            .defineInRange("maxExplosionRadius", 80.0, 1.0, 200.0);
    static { B.pop().push("zone"); }
    public static final ModConfigSpec.IntValue ZONE_RADIUS = B.comment("Radius of the dark energy zone a collapse leaves behind, in blocks, for a reactor holding",
                    "the ignition minimum of mass. It scales with the square root of the mass: 4x the mass, twice the radius.")
            .defineInRange("zoneRadius", 500, 16, 10_000);
    public static final ModConfigSpec.IntValue ZONE_MIN_RADIUS = B.comment("Smallest zone a collapse can leave, however little mass it held.")
            .defineInRange("zoneMinRadius", 150, 16, 10_000);
    public static final ModConfigSpec.IntValue ZONE_MAX_RADIUS = B.comment("Largest zone a collapse can leave, however much mass it held.")
            .defineInRange("zoneMaxRadius", 2000, 16, 30_000);
    public static final ModConfigSpec.IntValue ZONE_NODES = B.comment("Ore nodes that must be stabilized to remove a new zone. Each one shrinks it; the last one removes it.")
            .defineInRange("stabilizationNodes", 8, 1, 1000);
    public static final ModConfigSpec.DoubleValue ZONE_GRAVITY = B.comment("Gravity multiplier for mobs and players inside a zone.")
            .defineInRange("gravityMultiplier", 0.4, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue ORE_CHANCE = B.comment("Chance per surface column that converted terrain gets an Unstable Dark Ore node (inside the crater it's 5x).")
            .defineInRange("oreChance", 0.004, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DISCHARGE_CHANCE = B.comment("Chance that mining Unstable Dark Ore discharges (a small blast that hurts but breaks no blocks).")
            .defineInRange("dischargeChance", 0.25, 0.0, 1.0);
    public static final ModConfigSpec.IntValue ZONE_CHUNKS_PER_TICK = B.comment("Chunks a dark energy zone loads (or generates) per tick to corrupt its terrain, nearest first,",
                    "so the whole zone is converted, not just where players have been. 0 only converts chunks as players load them.")
            .defineInRange("chunksLoadedPerTick", 1, 0, 64);
    public static final ModConfigSpec.BooleanValue CORRUPTED_MOBS = B.comment("Whether hostile mobs spawning in a zone become corrupted (stronger, drop Void Dust).")
            .define("corruptedMobs", true);
    static { B.pop().push("bomb"); }
    public static final ModConfigSpec.IntValue BOMB_FUSE_SECONDS = B.comment("Black Hole Bomb fuse, from arming to detonation.")
            .defineInRange("fuseSeconds", 30, 5, 600);
    public static final ModConfigSpec.DoubleValue BOMB_BASE_RADIUS = B.comment("Black Hole Bomb blast radius in blocks, before the mass bonus.")
            .defineInRange("baseRadius", 60.0, 1.0, 200.0);
    public static final ModConfigSpec.IntValue BOMB_MASS_PER_RADIUS = B.comment("Extra bomb radius: +1 per this much mass fed into it. 25 gives 100 blocks at 1,000 mass.")
            .defineInRange("massPerRadius", 25, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue BOMB_MAX_RADIUS = B.comment("Hard cap on the bomb's blast radius. The crater is carved over a few seconds; huge values take a while.")
            .defineInRange("maxRadius", 150.0, 1.0, 200.0);
    public static final ModConfigSpec.BooleanValue BOMB_LEAVES_ZONE = B.comment("Whether a detonation leaves a dark energy zone behind, like a reactor collapse.")
            .define("leavesZone", true);
    static { B.pop(); }

    public static final ModConfigSpec SPEC = B.build();

    private ReactorConfig() {}
}
