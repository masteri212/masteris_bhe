package com.masteri.bhe.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Server config: config/masteris_bhe-server.toml (a world can override it with a copy in its serverconfig folder). Synced
 * to clients when they join.
 */
public final class ReactorConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static { B.push("startup"); }
    public static final ModConfigSpec.IntValue IGNITION_ENERGY = B.comment("RF consumed by ignition.")
            .defineInRange("ignitionEnergy", 50_000_000, 0, 1_000_000_000);
    public static final ModConfigSpec.IntValue MIN_MASS_PER_STABILIZER = B.comment("Mass each stabilizer needs before ignition.")
            .defineInRange("minMassPerStabilizer", 10_000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> IGNITION_DIMENSIONS = B.comment(
                    "Dimensions a reactor may be ignited in, by id (e.g. \"minecraft:overworld\"). Empty: any dimension.")
            .defineListAllowEmpty("ignitionDimensions", List.of(), () -> "minecraft:overworld", o -> o instanceof String);
    public static final ModConfigSpec.IntValue CONSOLE_LINK_RADIUS = B.comment("How far from a Reactor Core a Control Console can be and still link to it, in blocks.")
            .defineInRange("consoleLinkRadius", 32, 4, 128);
    public static final ModConfigSpec.IntValue PORT_PUSH_RF = B.comment("Most RF/t a Reactor Port pushes into each neighbouring machine or cable.")
            .defineInRange("portPushPerSide", 2_000_000, 0, Integer.MAX_VALUE);
    static { B.pop().push("mass"); }
    public static final ModConfigSpec.IntValue WARNING_MASS = B.comment("Below this total mass, stability starts dropping.")
            .defineInRange("warningMass", 10_000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue CRITICAL_MASS = B.comment("Below this total mass, the core can't be held (pre-meltdown).")
            .defineInRange("criticalMass", 4_000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue BLOCK_MASS = B.comment("Mass one ordinary block is worth, fed in or swallowed (the Event Horizon's dense materials have their own values).")
            .defineInRange("blockMass", 1, 1, 1_000_000);
    public static final ModConfigSpec.IntValue ITEM_MASS = B.comment("Mass a black hole gets from swallowing one item that isn't a block (these can't be fed in).")
            .defineInRange("swallowedItemMass", 1, 0, 1_000_000);
    public static final ModConfigSpec.DoubleValue CREATURE_MASS_PER_HEART = B.comment("Mass a black hole gets per heart of max health of a creature it swallows.")
            .defineInRange("creatureMassPerHeart", 1.0, 0.0, 1_000.0);
    static { B.pop().push("laser"); }
    public static final ModConfigSpec.IntValue LASER_MAX_RATE = B.comment("RF/t the bending laser fires at 100% power.")
            .defineInRange("laserMaxRate", 200_000, 0, 10_000_000);
    public static final ModConfigSpec.DoubleValue LASER_GAIN_MIN = B.comment("Output multiplier at low laser power.")
            .defineInRange("laserGainMin", 1.15, 1.0, 10.0);
    public static final ModConfigSpec.DoubleValue LASER_GAIN_MAX = B.comment("Output multiplier at 100% laser power.")
            .defineInRange("laserGainMax", 1.30, 1.0, 10.0);
    public static final ModConfigSpec.IntValue LASER_RF_PER_MASS = B.comment("RF fired by the laser per 1 mass drained.")
            .defineInRange("laserRfPerMass", 100_000, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue LASER_RANGE = B.comment("How far from a Reactor Core the bending laser can be and still link to it, in blocks.")
            .defineInRange("laserRange", 24, 4, 128);
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
    public static final ModConfigSpec.IntValue WARNING_STABILITY = B.comment("Below this stability (%) a running reactor is in Warning.")
            .defineInRange("warningStability", 50, 0, 100);
    public static final ModConfigSpec.IntValue PRE_MELTDOWN_STABILITY = B.comment("Below this stability (%) a running reactor goes to pre-meltdown.")
            .defineInRange("preMeltdownStability", 20, 0, 100);
    public static final ModConfigSpec.IntValue RECOVER_STABILITY = B.comment("Pre-meltdown recovers once stability (%) is back to at least this (and stress is under recoverStress).")
            .defineInRange("recoverStability", 35, 0, 100);
    public static final ModConfigSpec.IntValue RECOVER_STRESS = B.comment("Pre-meltdown recovers only while ring stress (%) is under this.")
            .defineInRange("recoverStress", 90, 1, 100);
    public static final ModConfigSpec.DoubleValue SCRAM_STALL_SPIN = B.comment("SCRAM stalls the disc above this share of the safe spin (0.9 = 3,600 rpm with the default safeRpm).")
            .defineInRange("scramStallSpin", 0.9, 0.0, 10.0);
    public static final ModConfigSpec.IntValue JUMP_PENALTY_SECONDS = B.comment("Seconds a disc stall's timer loses each time Jump is pressed before all the breakers are overcharged.")
            .defineInRange("jumpPenaltySeconds", 5, 0, 600);
    public static final ModConfigSpec.IntValue STALL_SECONDS = B.comment("Time to jump-start a stalled disc (overcharge every stabilizer's breakers, then JUMP) before the reactor goes to pre-meltdown.")
            .defineInRange("discStallSeconds", 60, 5, 3600);
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
    public static final ModConfigSpec.IntValue PRE_MELTDOWN_SECONDS = B.comment("Pre-meltdown countdown: time to bring stability, stress and the structure back before meltdown starts.")
            .defineInRange("preMeltdownSeconds", 90, 5, 3600);
    public static final ModConfigSpec.IntValue MELTDOWN_1_SECONDS = B.comment("Meltdown phase 1: the core pulls things in and starts eating the reactor. Only the emergency vent saves it.")
            .defineInRange("meltdownPhase1Seconds", 120, 5, 3600);
    public static final ModConfigSpec.IntValue MELTDOWN_2_SECONDS = B.comment("Meltdown phase 2: a stronger, wider pull, then the core collapses.")
            .defineInRange("meltdownPhase2Seconds", 60, 5, 3600);
    public static final ModConfigSpec.IntValue MELTDOWN_1_PULL = B.comment("How far meltdown phase 1 pulls in mobs, players and items, in blocks.")
            .defineInRange("meltdownPhase1Pull", 20, 0, 128);
    public static final ModConfigSpec.IntValue MELTDOWN_2_PULL = B.comment("How far meltdown phase 2 pulls in mobs, players and items, in blocks.")
            .defineInRange("meltdownPhase2Pull", 32, 0, 128);
    static { B.pop().push("collapse"); }
    public static final ModConfigSpec.BooleanValue PULL_BLOCKS = B.comment("Whether a running reactor's black hole tears loose blocks in its sphere of influence (just inside",
                    "the ring, down into the ground too) and swallows them as mass, 1 per block. In a failure the sphere grows",
                    "past the ring (that part also needs destroyBlocks).")
            .define("pullBlocks", true);
    public static final ModConfigSpec.IntValue MAX_BLOCKS_IN_FLIGHT = B.comment("Most torn-loose blocks flying into the black hole at once (each is an entity; lower it if it lags).")
            .defineInRange("maxBlocksInFlight", 300, 0, 5000);
    public static final ModConfigSpec.BooleanValue DESTROY_BLOCKS = B.comment("Whether a reactor collapse destroys blocks. Off: no crater (from a collapse or a Black Hole Bomb),",
                    "no block eaten past the ring in a failure, and a dark energy zone never corrupts the ground.")
            .define("destroyBlocks", true);
    public static final ModConfigSpec.DoubleValue BASE_RADIUS = B.comment("Collapse blast radius in blocks, before the mass bonus.")
            .defineInRange("baseExplosionRadius", 50.0, 1.0, 200.0);
    public static final ModConfigSpec.IntValue MASS_PER_RADIUS = B.comment("Extra explosion radius: +1 per this much mass.")
            .defineInRange("massPerRadius", 10_000, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue MAX_RADIUS = B.comment("Hard cap on the collapse explosion radius. The crater is carved over a few seconds; very big values still take a while.")
            .defineInRange("maxExplosionRadius", 80.0, 1.0, 200.0);
    static { B.pop().push("research"); }
    public static final ModConfigSpec.IntValue RESEARCH_STEP_SECONDS = B.comment("Time allowed for each step of a Research Chamber experiment.")
            .defineInRange("stepSeconds", 30, 5, 600);
    static { B.pop().push("zone"); }
    public static final ModConfigSpec.IntValue ZONE_RADIUS = B.comment("Radius of the dark energy zone a collapse leaves behind, in blocks, for a reactor holding",
                    "the ignition minimum of mass. It scales with the square root of the mass: 4x the mass, twice the radius.")
            .defineInRange("zoneRadius", 500, 16, 10_000);
    public static final ModConfigSpec.IntValue ZONE_MIN_RADIUS = B.comment("Smallest zone a collapse can leave, however little mass it held.")
            .defineInRange("zoneMinRadius", 150, 16, 10_000);
    public static final ModConfigSpec.IntValue ZONE_MAX_RADIUS = B.comment("Largest zone a collapse can leave, however much mass it held.")
            .defineInRange("zoneMaxRadius", 2000, 16, 30_000);
    public static final ModConfigSpec.IntValue ZONE_FALLOUT_RADIUS = B.comment("How far a zone's fallout reaches from its center: corrupted ground, ore nodes and corrupted mobs.",
                    "Past it the zone is only low gravity (and the view inside a black hole). A zone smaller than this is all fallout.")
            .defineInRange("falloutRadius", 500, 16, 30_000);
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
            .defineInRange("chunksLoadedPerTick", 5, 0, 64);
    public static final ModConfigSpec.IntValue ZONE_DRIFTERS = B.comment("Drifters (floating dark matter jellyfish) spawned around each player inside a zone, at most. 0 turns them off.")
            .defineInRange("driftersPerPlayer", 4, 0, 32);
    public static final ModConfigSpec.IntValue ZONE_STALKERS = B.comment("Lensing Stalkers spawned around each player inside a zone's fallout, at most. 0 turns them off.")
            .defineInRange("stalkersPerPlayer", 2, 0, 16);
    public static final ModConfigSpec.IntValue ECHO_COOLDOWN_MINUTES = B.comment("Minutes before a zone's Echo of the Core can rise again after it last did. 0 turns the Echo off.")
            .defineInRange("echoCooldownMinutes", 20, 0, 100_000);
    public static final ModConfigSpec.BooleanValue CORRUPTED_MOBS = B.comment("Whether hostile mobs spawning in a zone become corrupted (stronger, drop Void Dust).")
            .define("corruptedMobs", true);
    public static final ModConfigSpec.DoubleValue CORRUPTED_HEALTH = B.comment("Extra health a corrupted mob gets, as a share of its normal health (1 = twice the health).")
            .defineInRange("corruptedHealthBonus", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue CORRUPTED_DAMAGE = B.comment("Extra attack damage a corrupted mob gets, as a share of its normal damage.")
            .defineInRange("corruptedDamageBonus", 0.5, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue CORRUPTED_SPEED = B.comment("Extra speed a corrupted mob gets, as a share of its normal speed.")
            .defineInRange("corruptedSpeedBonus", 0.15, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue CORRUPTED_ARMOR = B.comment("Armor points a corrupted mob gets.")
            .defineInRange("corruptedArmor", 4.0, 0.0, 30.0);
    public static final ModConfigSpec.DoubleValue GRAVITON_CHANCE = B.comment("Chance a corrupted mob killed by a player drops a Graviton Core.")
            .defineInRange("gravitonCoreChance", 0.03, 0.0, 1.0);
    static { B.pop().push("bomb"); }
    public static final ModConfigSpec.BooleanValue BOMB_ENABLED = B.comment("Whether Black Hole Bombs can be armed at all.")
            .define("enabled", true);
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
    static { B.pop().push("event_horizon"); }
    public static final ModConfigSpec.BooleanValue HORIZON_ENABLED = B.comment("Whether Horizon Rings can be opened into the Event Horizon. Off: rings outside stay shut",
                    "(rings and Escape Recordings inside still lead out).")
            .define("enabled", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> GATE_DIMENSIONS = B.comment(
                    "Dimensions a Horizon Ring can be opened from, into the Event Horizon (e.g. \"minecraft:overworld\"). Empty: any dimension.")
            .defineListAllowEmpty("gateDimensions", List.of(), () -> "minecraft:overworld", o -> o instanceof String);
    public static final ModConfigSpec.IntValue ARRIVAL_Y = B.comment("Height of the landing platform (and its ring) in the Event Horizon.")
            .defineInRange("arrivalY", 190, 50, 320);
    public static final ModConfigSpec.BooleanValue LURE_ENABLED = B.comment("Whether the Singularity Lure works (the Citadel's altar always does).")
            .define("singularityLureEnabled", true);
    public static final ModConfigSpec.IntValue DILATION_START_Y = B.comment("Time starts running faster below this height in the Event Horizon...")
            .defineInRange("timeDilationStartY", 150, 0, 384);
    public static final ModConfigSpec.IntValue DILATION_FULL_Y = B.comment("...and is at its fastest (timeDilation) from this height down.")
            .defineInRange("timeDilationFullY", 60, 0, 384);
    public static final ModConfigSpec.DoubleValue GRAVITON_WELL_RANGE = B.comment("How far a Graviton Well pulls, in blocks.")
            .defineInRange("gravitonWellRange", 7.0, 0.0, 32.0);
    public static final ModConfigSpec.DoubleValue GRAVITON_WELL_STRENGTH = B.comment("How hard a Graviton Well pulls up close.")
            .defineInRange("gravitonWellStrength", 0.06, 0.0, 1.0);
    public static final ModConfigSpec.IntValue ROCK_FADE_MIN = B.comment("Evaporating Rock is gone between this many seconds...")
            .defineInRange("rockFadeMinSeconds", 2, 1, 600);
    public static final ModConfigSpec.IntValue ROCK_FADE_MAX = B.comment("...and this many after it starts fading.")
            .defineInRange("rockFadeMaxSeconds", 5, 1, 600);
    public static final ModConfigSpec.IntValue ECHO_DELAY_SECONDS = B.comment("How far behind a Light Trap's echo follows its player, in seconds.")
            .defineInRange("lightEchoDelaySeconds", 5, 1, 30);
    public static final ModConfigSpec.IntValue GATE_MASS_PER_TRIP = B.comment("Mass a Horizon Ring burns for each player it sends into the Event Horizon. Coming back out is free.")
            .defineInRange("gateMassPerTrip", 1000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue GATE_OPEN_SECONDS = B.comment("How long a Horizon Ring stays open once it opens (anyone can step through while it is).")
            .defineInRange("gateOpenSeconds", 30, 5, 3600);
    public static final ModConfigSpec.DoubleValue HORIZON_GRAVITY_TOP = B.comment("Gravity multiplier at the top of the Event Horizon (it rises smoothly toward the bottom).")
            .defineInRange("gravityTop", 0.2, 0.0, 4.0);
    public static final ModConfigSpec.DoubleValue HORIZON_GRAVITY_BOTTOM = B.comment("Gravity multiplier just above the singularity, at the bottom of the Event Horizon.")
            .defineInRange("gravityBottom", 1.8, 0.0, 4.0);
    public static final ModConfigSpec.DoubleValue TIME_DILATION = B.comment("How much faster time runs in the deepest part of the Event Horizon: furnaces and random ticks",
                    "(crops, saplings...) near players. 1 turns it off. It starts below y 150 and is strongest at y 60.")
            .defineInRange("timeDilation", 4.0, 1.0, 16.0);
    public static final ModConfigSpec.IntValue SPAGHETTIFICATION_Y = B.comment("Below this height the singularity drags everything down and tears it apart.")
            .defineInRange("spaghettificationY", 40, -64, 320);
    public static final ModConfigSpec.IntValue HAWKING_CELL_RF = B.comment("RF/t a Hawking Cell makes.")
            .defineInRange("hawkingCellRf", 256, 0, 1_000_000);
    public static final ModConfigSpec.IntValue NEUTRONIUM_MASS = B.comment("Mass one Neutronium Ingot is worth when fed to a stabilizer, bomb or gate (a block is 9x, raw Neutronium half).")
            .defineInRange("neutroniumMass", 200, 1, 1_000_000);
    public static final ModConfigSpec.IntValue MASS_SHARD_MASS = B.comment("Mass one Mass Shard is worth.")
            .defineInRange("massShardMass", 16, 1, 1_000_000);
    public static final ModConfigSpec.IntValue SINGULARITY_HEART_MASS = B.comment("Mass a Singularity Heart is worth.")
            .defineInRange("singularityHeartMass", 10_000, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue SINGULARITY_COLLAPSE = B.comment("How much unstable matter the Singularity has to swallow before it collapses (see the manual for what each item is worth).")
            .defineInRange("singularityCollapse", 2500, 1, 1_000_000);
    static { B.pop().push("mobs"); }
    public static final ModConfigSpec.DoubleValue DRIFTER_HEALTH = B.comment("Drifter health.").defineInRange("drifterHealth", 12.0, 1.0, 1024.0);
    public static final ModConfigSpec.DoubleValue STALKER_HEALTH = B.comment("Lensing Stalker health.").defineInRange("lensingStalkerHealth", 30.0, 1.0, 1024.0);
    public static final ModConfigSpec.DoubleValue STALKER_DAMAGE = B.comment("Lensing Stalker attack damage.").defineInRange("lensingStalkerDamage", 7.0, 0.0, 1024.0);
    public static final ModConfigSpec.DoubleValue ECHO_HEALTH = B.comment("Echo of the Core health.").defineInRange("echoHealth", 300.0, 1.0, 10_000.0);
    public static final ModConfigSpec.IntValue ECHO_ARENA = B.comment("Echo of the Core arena radius around where it rose, in blocks: it ignores players outside it.")
            .defineInRange("echoArenaRadius", 64, 16, 512);
    public static final ModConfigSpec.DoubleValue ECHO_DAMAGE = B.comment("Multiplier on all the Echo of the Core's attacks.").defineInRange("echoDamageMultiplier", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue SPAGHETTIFIER_HEALTH = B.comment("Spaghettifier health.").defineInRange("spaghettifierHealth", 24.0, 1.0, 1024.0);
    public static final ModConfigSpec.DoubleValue SPAGHETTIFIER_SPAWN_CHANCE = B.comment("Share of the natural spawn attempts for Spaghettifiers that go through (0 turns natural spawns off).")
            .defineInRange("spaghettifierSpawnChance", 0.25, 0.0, 1.0);
    public static final ModConfigSpec.IntValue SPAGHETTIFIER_MAX_NEARBY = B.comment("A Spaghettifier won't spawn naturally if this many are already within spaghettifierCrowdRadius.")
            .defineInRange("spaghettifierMaxNearby", 2, 1, 64);
    public static final ModConfigSpec.IntValue SPAGHETTIFIER_CROWD_RADIUS = B.comment("How far around a spawn spot to count Spaghettifiers, in blocks.")
            .defineInRange("spaghettifierCrowdRadius", 32, 1, 128);
    public static final ModConfigSpec.DoubleValue SPAGHETTIFIER_DAMAGE = B.comment("Spaghettifier bite damage.").defineInRange("spaghettifierDamage", 5.0, 0.0, 1024.0);
    public static final ModConfigSpec.DoubleValue WISP_HEALTH = B.comment("Hawking Wisp health.").defineInRange("hawkingWispHealth", 4.0, 1.0, 1024.0);
    public static final ModConfigSpec.IntValue WISP_LIFE_MIN = B.comment("A Hawking Wisp evaporates after between this many seconds...")
            .defineInRange("hawkingWispLifeMinSeconds", 120, 5, 36_000);
    public static final ModConfigSpec.IntValue WISP_LIFE_MAX = B.comment("...and this many.")
            .defineInRange("hawkingWispLifeMaxSeconds", 300, 5, 36_000);
    public static final ModConfigSpec.DoubleValue SINGULARITY_DAMAGE = B.comment("Multiplier on all the Singularity's attacks.").defineInRange("singularityDamageMultiplier", 1.0, 0.0, 100.0);
    static { B.pop(); }

    public static final ModConfigSpec SPEC = B.build();

    /** Whether {@code dimension} is on a list of dimension ids (an empty list allows every dimension). */
    public static boolean allows(List<? extends String> ids, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        return ids.isEmpty() || ids.contains(dimension.location().toString());
    }

    private ReactorConfig() {}
}
