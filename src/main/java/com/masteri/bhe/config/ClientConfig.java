package com.masteri.bhe.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Each player's own settings (config/masteris_bhe-client.toml): how loud the mod's sounds are. */
public final class ClientConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static { B.push("sounds"); }

    public static final ModConfigSpec.DoubleValue HUM_VOLUME = B.comment("Volume of the running reactor's hum (0 mutes it).")
            .defineInRange("humVolume", 1.0, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue ALARM_VOLUME = B.comment("Volume of the reactor's alarms (pre-meltdown and meltdown).")
            .defineInRange("alarmVolume", 1.0, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue CONSOLE_VOLUME = B.comment("Volume of the consoles: beeps, button clicks, servos and breaker panels.")
            .defineInRange("consoleVolume", 1.0, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue EFFECTS_VOLUME = B.comment("Volume of everything else the mod plays: ignition, the ring's hiss, arcs, faults, venting,",
                    "breaches, collapses and the like.")
            .defineInRange("effectsVolume", 1.0, 0.0, 1.0);

    static { B.pop(); }

    public static final ModConfigSpec SPEC = B.build();

    private ClientConfig() {}
}
