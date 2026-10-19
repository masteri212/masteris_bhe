package com.masteri.bhe.client;

import com.masteri.bhe.reactor.EmergencyVent;
import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.reactor.DarkZones;
import com.masteri.bhe.reactor.ReactorState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;

/**
 * What a failing reactor does to your screen:
 * camera shake that grows with the danger, red pulsing fog in pre-meltdown and phase 1,
 * and in phase 2 the sky goes dark as black-violet fog closes in.
 */
@EventBusSubscriber(modid = MasteriBHE.MODID, value = Dist.CLIENT)
public final class ReactorScreenEffects {
    private static final double SHAKE_RANGE = 64, FOG_RANGE = 96;

    /** The core that affects the camera most right now, and how close it is (1 = on top of it, 0 = out of range). */
    private record Nearest(ReactorCoreBlockEntity core, double closeness) {}

    @Nullable
    private static Nearest nearest(Vec3 camera, double range) {
        Nearest best = null;
        for (ReactorCoreBlockEntity core : ReactorCoreBlockEntity.CLIENT_CORES) {
            if (core.isRemoved() || !core.isFormed()) continue;
            double dist = Vec3.atCenterOf(core.getBlockPos()).distanceTo(camera);
            if (dist > range) continue;
            double closeness = 1 - dist / range;
            if (best == null || closeness > best.closeness) best = new Nearest(core, closeness);
        }
        return best;
    }

    private static float time(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + partialTick;
    }

    // ------------------------------------------------------------------ camera shake

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Nearest near = nearest(event.getCamera().getPosition(), SHAKE_RANGE);
        if (near == null) return;
        ReactorCoreBlockEntity core = near.core;
        float pt = (float) event.getPartialTick();
        float danger = core.getDangerProgress(pt);
        float shake = switch (core.getState()) {
            case DISC_STALL -> 0.25F;
            case PRE_MELTDOWN -> 0.3F + 0.6F * danger;
            case MELTDOWN_1 -> 1.0F + 0.5F * danger;
            case MELTDOWN_2 -> danger > 0.85F ? 3.0F : 1.6F + danger;
            default -> 0;
        };
        float p = core.getAnimProgress(pt);
        shake += switch (core.getAnim()) {
            case BREACH -> 2.5F * (1 - p);
            case MELTDOWN_1, MELTDOWN_2 -> 2.0F * (1 - p);
            case STALL, ALARM -> 0.8F * (1 - p);
            case STARTUP -> 1.2F * Math.max(0, 1 - Math.abs(p - 0.45F) / 0.1F); // the ignition thump
            case SCRAM -> 1.0F * (1 - p);
            case POWER_DOWN, SCRAM_POWER_DOWN -> 0.8F * Math.max(0, 1 - Math.abs(p - 0.88F) / 0.08F);
            case VENT -> 1.0F * Math.max(0, 1 - Math.abs(p - 0.9F) / 0.06F); // the final pop
            // Emergency vent: a jolt with every rebound, then the implosion.
            case VENT_EMERGENCY -> (p < EmergencyVent.SURGE ? 0.6F : 0)                          // the standoff rumble
                    + 2.0F * Math.max(0, 1 - Math.abs(p - EmergencyVent.SURGE) / 0.04F)              // the surge
                    + 1.2F * EmergencyVent.beat(p)                                                     // each rebound
                    + 5.0F * Math.max(0, 1 - Math.abs(p - EmergencyVent.IMPLODE) / 0.06F);            // the implosion
            default -> 0;
        };
        if (core.getVentTicks() > 0) shake += core.isVentEmergency() ? 2.5F : 1.2F; // the lance firing
        if (shake <= 0) return;
        float amount = (float) (shake * near.closeness);
        float t = time(pt);
        event.setYaw(event.getYaw() + amount * (Mth.sin(t * 1.9F) + 0.5F * Mth.sin(t * 4.3F + 1)) * 0.6F);
        event.setPitch(event.getPitch() + amount * (Mth.sin(t * 2.3F + 2) + 0.5F * Mth.sin(t * 5.1F)) * 0.6F);
        event.setRoll(event.getRoll() + amount * Mth.sin(t * 1.3F + 4) * 0.5F);
    }

    /** A dark energy zone being born (the first moments) or imploding shakes everyone near it. */
    @SubscribeEvent
    public static void onCameraAnglesZone(ViewportEvent.ComputeCameraAngles event) {
        Vec3 camera = event.getCamera().getPosition();
        float shake = 0;
        for (DarkZones.ClientZones.View zone : DarkZones.ClientZones.views()) {
            double dist = DarkZones.distance(zone.center(), camera.x, camera.y, camera.z);
            double reach = Math.max(zone.radius(), 200) + 200;
            if (dist > reach) continue;
            float near = (float) (1 - dist / reach);
            float p = zone.progress();
            float s = switch (zone.phase()) {
                case BIRTH -> 2.5F * Math.max(0, 1 - p / 0.2F);
                case CLOSING -> p < DarkZones.ClientZones.CLOSING_SHRINK
                        ? 0.6F * p / DarkZones.ClientZones.CLOSING_SHRINK   // rumbling harder as it falls in
                        : 3.0F * (1 - (p - DarkZones.ClientZones.CLOSING_SHRINK) / (1 - DarkZones.ClientZones.CLOSING_SHRINK));
                default -> 0;
            };
            shake = Math.max(shake, s * near);
        }
        if (shake <= 0) return;
        float t = time((float) event.getPartialTick());
        event.setYaw(event.getYaw() + shake * (Mth.sin(t * 2.1F + 3) + 0.5F * Mth.sin(t * 4.7F)) * 0.6F);
        event.setPitch(event.getPitch() + shake * (Mth.sin(t * 2.7F) + 0.5F * Mth.sin(t * 5.3F + 2)) * 0.6F);
        event.setRoll(event.getRoll() + shake * Mth.sin(t * 1.1F + 1) * 0.5F);
    }

    // ------------------------------------------------------------------ fog: red lighting, dark sky

    /** 0..1: how strongly the fog turns red (pulsing) or dark at the camera. */
    private static float redness(Nearest near, float t) {
        float pulse = 0.5F + 0.5F * Mth.sin(t * 0.3F);
        return (float) near.closeness * switch (near.core.getState()) {
            case PRE_MELTDOWN -> 0.25F + 0.2F * pulse;
            case MELTDOWN_1 -> 0.4F + 0.25F * pulse;
            default -> 0F;
        };
    }

    private static float darkness(Nearest near, float partialTick) {
        if (near.core.getState() != ReactorState.MELTDOWN_2) return 0;
        return (float) Math.min(1, near.closeness * 1.6) * (0.6F + 0.4F * near.core.getDangerProgress(partialTick));
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Nearest near = nearest(event.getCamera().getPosition(), FOG_RANGE);
        if (near == null) return;
        float pt = (float) event.getPartialTick();
        float red = redness(near, time(pt));
        float dark = darkness(near, pt);
        float r = event.getRed(), g = event.getGreen(), b = event.getBlue();
        if (red > 0) {
            r = Mth.lerp(red, r, 0.75F);
            g = Mth.lerp(red, g, 0.05F);
            b = Mth.lerp(red, b, 0.1F);
        }
        if (dark > 0) {
            r = Mth.lerp(dark, r, 0.06F);
            g = Mth.lerp(dark, g, 0.0F);
            b = Mth.lerp(dark, b, 0.09F);
        }
        event.setRed(r);
        event.setGreen(g);
        event.setBlue(b);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Nearest near = nearest(event.getCamera().getPosition(), FOG_RANGE);
        if (near == null) return;
        float pt = (float) event.getPartialTick();
        float dark = darkness(near, pt);
        float red = redness(near, time(pt));
        float closeIn = Math.max(dark, red * 0.4F);
        if (closeIn <= 0) return;
        float far = Mth.lerp(closeIn, event.getFarPlaneDistance(), 40F);
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.1F));
        event.setCanceled(true); // required for the new distances to apply
    }

    private ReactorScreenEffects() {}
}
