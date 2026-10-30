package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.reactor.ReactorState;
import com.masteri.bhe.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The running reactor's hum: a loop at each core that isn't offline. It rises in pitch with the disc's spin, sags
 * when the disc stalls, and shudders through a meltdown; it fades in at ignition and out at shutdown.
 */
@EventBusSubscriber(modid = MasteriBHE.MODID, value = Dist.CLIENT)
public final class ReactorHum {
    /** Heard this far from the core, in blocks (it fades out with distance; see Loop.tick). */
    private static final double RANGE = 64;
    private static final Map<ReactorCoreBlockEntity, Loop> PLAYING = new WeakHashMap<>();

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PLAYING.clear();
            return;
        }
        SoundManager sounds = mc.getSoundManager();
        for (ReactorCoreBlockEntity core : ReactorCoreBlockEntity.CLIENT_CORES) {
            if (!hums(core, mc)) continue;
            Loop loop = PLAYING.get(core);
            if (loop == null || loop.isStopped() || !sounds.isActive(loop)) {
                loop = new Loop(core);
                PLAYING.put(core, loop);
                sounds.play(loop);
            }
        }
    }

    private static boolean hums(ReactorCoreBlockEntity core, Minecraft mc) {
        return !core.isRemoved() && core.isFormed() && core.getLevel() == mc.level && core.getState() != ReactorState.OFFLINE;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final ReactorCoreBlockEntity core;
        private float level; // 0..1, eased toward the target so it fades in and out

        Loop(ReactorCoreBlockEntity core) {
            super(ModSounds.REACTOR_HUM.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.core = core;
            this.looping = true;
            this.delay = 0;
            this.x = core.getBlockPos().getX() + 0.5;
            this.y = core.getBlockPos().getY() + 0.5;
            this.z = core.getBlockPos().getZ() + 0.5;
            this.attenuation = SoundInstance.Attenuation.NONE; // faded by hand: the vanilla range is only 16 blocks
            this.volume = 0.001F;
            this.pitch = 0.6F;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            boolean on = hums(core, mc);
            ReactorState state = core.getState();
            float spin = Math.abs(core.getClientSpin());
            float target;
            float wantPitch;
            if (!on) {
                target = 0;
                wantPitch = pitch * 0.985F; // winding down
            } else if (state == ReactorState.DISC_STALL) {
                target = 0.55F;
                wantPitch = 0.5F;
            } else {
                target = 0.7F + 0.3F * spin;
                wantPitch = 0.6F + 0.9F * spin;
                if (state.isMeltdown() || state == ReactorState.PRE_MELTDOWN) {
                    // Shuddering: a fast, uneven warble.
                    float t = core.getClientTicks();
                    wantPitch += 0.06F * Mth.sin(t * 0.9F) + 0.04F * Mth.sin(t * 2.3F);
                    target = 1.0F;
                }
            }
            level += (target - level) * (on ? 0.04F : 0.06F);
            pitch = Mth.clamp(pitch + (wantPitch - pitch) * 0.1F, 0.5F, 2.0F);
            double dist = mc.player == null ? RANGE : Math.sqrt(mc.player.distanceToSqr(x, y, z));
            float near = (float) Math.pow(Mth.clamp(1 - dist / RANGE, 0, 1), 1.6);
            volume = Math.max(0.001F, level * near);
            if (!on && level < 0.01F) stop();
        }
    }

    private ReactorHum() {}
}
