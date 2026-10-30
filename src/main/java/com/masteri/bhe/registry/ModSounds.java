package com.masteri.bhe.registry;

import com.masteri.bhe.MasteriBHE;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The reactor's own sounds (assets/masteris_bhe/sounds/reactor, made by tools/sounds/reactor.py). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MasteriBHE.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_HUM = reactor("hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_IGNITE = reactor("ignite");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_POWER_DOWN = reactor("power_down");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_ALARM = reactor("alarm");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_ALARM_MELTDOWN = reactor("alarm_meltdown");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_FAULT = reactor("fault");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_STALL = reactor("stall");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_RING_STRAIN = reactor("ring_strain");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_ARC = reactor("arc");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_BEEP_CONFIRM = reactor("beep_confirm");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_BEEP_DENY = reactor("beep_deny");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_BEEP_READY = reactor("beep_ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_CONSOLE = reactor("console");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_BREAKER_CHARGE = reactor("breaker_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_SERVO = reactor("servo");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_EMITTER_CRACK = reactor("emitter_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_BREACH = reactor("breach");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_IMPLODE = reactor("implode");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_ABSORB = reactor("absorb");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_VENT_STRAIN = reactor("vent_strain");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_VENT_RELEASE = reactor("vent_release");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_REBOUND = reactor("rebound");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_COLLAPSE = reactor("collapse");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_CAPTURE = reactor("capture");

    private static DeferredHolder<SoundEvent, SoundEvent> reactor(String name) {
        String id = "reactor." + name;
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, id)));
    }

    private ModSounds() {}
}
