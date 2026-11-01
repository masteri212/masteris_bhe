package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.config.ClientConfig;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Applies the client config's volumes to the mod's sounds. The server plays almost all of them, so they're scaled
 * here as they start; the hum is the client's own loop and reads its volume itself (see ReactorHum).
 */
@EventBusSubscriber(modid = MasteriBHE.MODID, value = Dist.CLIENT)
public final class SoundVolumes {
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound instanceof TickableSoundInstance) return; // the hum handles its own volume
        ResourceLocation id = sound.getLocation();
        if (!MasteriBHE.MODID.equals(id.getNamespace())) return;
        float volume = volumeFor(id.getPath());
        if (volume >= 1) return;
        event.setSound(volume <= 0 ? null : new Scaled(sound, volume));
    }

    /** The configured volume for one of the mod's sound events, by its name ("reactor.alarm", ...). */
    static float volumeFor(String path) {
        String name = path.startsWith("reactor.") ? path.substring("reactor.".length()) : path;
        return switch (name) {
            case "hum" -> ClientConfig.HUM_VOLUME.get().floatValue();
            case "alarm", "alarm_meltdown" -> ClientConfig.ALARM_VOLUME.get().floatValue();
            case "beep_confirm", "beep_deny", "beep_ready", "console", "servo", "breaker_charge" ->
                    ClientConfig.CONSOLE_VOLUME.get().floatValue();
            default -> ClientConfig.EFFECTS_VOLUME.get().floatValue();
        };
    }

    /** A sound played as it is, only quieter. */
    private record Scaled(SoundInstance sound, float factor) implements SoundInstance {
        @Override public ResourceLocation getLocation() { return sound.getLocation(); }
        @Nullable @Override public WeighedSoundEvents resolve(SoundManager manager) { return sound.resolve(manager); }
        @Override public Sound getSound() { return sound.getSound(); }
        @Override public SoundSource getSource() { return sound.getSource(); }
        @Override public boolean isLooping() { return sound.isLooping(); }
        @Override public boolean isRelative() { return sound.isRelative(); }
        @Override public int getDelay() { return sound.getDelay(); }
        @Override public float getVolume() { return sound.getVolume() * factor; }
        @Override public float getPitch() { return sound.getPitch(); }
        @Override public double getX() { return sound.getX(); }
        @Override public double getY() { return sound.getY(); }
        @Override public double getZ() { return sound.getZ(); }
        @Override public Attenuation getAttenuation() { return sound.getAttenuation(); }
        @Override public boolean canStartSilent() { return sound.canStartSilent(); }
        @Override public boolean canPlaySound() { return sound.canPlaySound(); }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping) {
            return this.sound.getStream(library, sound, looping);
        }
    }

    private SoundVolumes() {}
}
