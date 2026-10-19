package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.reactor.DarkZones;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * Inside a dark energy zone the world looks like it's seen from inside a black hole: the view swirls and bends
 * toward the edges, colors split, light gathers into a faint ring and the edges of the screen fall into darkness.
 * Strength fades in over the outer 48 blocks of the zone and grows toward the crater.
 *
 * Shader files: assets/masteris_bhe/shaders/post/dark_zone.json and shaders/program/dark_zone.{json,fsh}.
 */
@EventBusSubscriber(modid = MasteriBHE.MODID, value = Dist.CLIENT)
public final class DarkZoneShader {
    private static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "shaders/post/dark_zone.json");
    private static final double EDGE_FADE = 48;

    private static PostChain chain;
    private static boolean failed;
    private static int width, height;

    /** 0..1: how deep the camera is in a zone. */
    private static float intensity(Vec3 camera) {
        float best = 0;
        List<DarkZones.ClientZones.View> zones = DarkZones.ClientZones.views();
        for (DarkZones.ClientZones.View zone : zones) {
            double dist = Math.sqrt(zone.center().distToCenterSqr(camera.x, camera.y, camera.z));
            if (dist >= zone.radius()) continue;
            double edge = Mth.clamp((zone.radius() - dist) / Math.min(EDGE_FADE, zone.radius() * 0.5), 0, 1);
            double depth = 1 - dist / zone.radius();
            best = Math.max(best, (float) (edge * (0.55 + 0.45 * depth)));
        }
        return best;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float strength = intensity(event.getCamera().getPosition());
        if (strength <= 0.001F) return;
        PostChain effect = chain();
        if (effect == null) return;

        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        if (w != width || h != height) {
            effect.resize(w, h);
            width = w;
            height = h;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        effect.setUniform("Intensity", strength);
        effect.setUniform("Seconds", (mc.level.getGameTime() % 24000 + partial) / 20F);
        effect.process(event.getPartialTick().getGameTimeDeltaTicks());
        mc.getMainRenderTarget().bindWrite(false);
    }

    private static PostChain chain() {
        if (chain == null && !failed) {
            Minecraft mc = Minecraft.getInstance();
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), EFFECT);
                width = height = -1; // resize on first use
            } catch (Exception e) {
                failed = true; // don't retry every frame; a resource reload resets this
                MasteriBHE.LOGGER.error("Could not load the dark zone shader", e);
            }
        }
        return chain;
    }

    /** Called on resource reload (see ClientEvents) so edited shader files are picked up. */
    static void reset() {
        if (chain != null) chain.close();
        chain = null;
        failed = false;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        DarkZones.ClientZones.clear();
    }

    private DarkZoneShader() {}
}
