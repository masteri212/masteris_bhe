package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.reactor.DarkZones;
import com.masteri.bhe.reactor.DarkZones.ClientZones;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The edge of each dark energy zone as a nearly solid black sphere (about 10% see-through), so the border is clear
 * from inside and out. Seen from outside, the rim is a little darker than the middle, which gives it a solid
 * silhouette; a faint violet shimmer drifts over the surface. It isn't fogged, so it shows from far away, and it
 * doesn't write depth, so terrain and everything else still show through it.
 *
 * Animations (driven by {@link ClientZones}):
 *  - Birth: a blinding flash at the crater, then the zone bursts outward behind a glowing violet shock front,
 *    the black wall thickening in its wake.
 *  - Resize (a node stabilized): the wall glides to its new size with a pulse of light.
 *  - Closing (the last node stabilized): the surface churns and glows hotter as the zone falls in on itself,
 *    rushing faster and faster, then implodes into the center with a white flash.
 */
@EventBusSubscriber(modid = MasteriBHE.MODID, value = Dist.CLIENT)
public final class ZoneBorderRenderer {
    private static final int STACKS = 48, SLICES = 96;
    private static final float BASE_ALPHA = 0.9F, RIM_ALPHA = 0.95F, GLOW_ALPHA = 0.12F; // about 10% see-through
    private static final float[] SHOCK = {0.75F, 0.55F, 1.0F}, WHITE = {1.0F, 0.95F, 1.0F};

    /** How far from the camera a wall vertex may be drawn (just inside the far clipping plane), this frame. */
    private static float maxDistance = Float.MAX_VALUE;

    /** Which one-off sounds each animation has already played (keyed by its start time). */
    private static final Map<Long, Integer> CUES = new HashMap<>();

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        List<ClientZones.View> views = ClientZones.views();
        if (mc.level == null || views.isEmpty()) {
            CUES.clear();
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        double viewRange = mc.options.getEffectiveRenderDistance() * 16.0 * 2;
        float time = (mc.level.getGameTime() % 24000 + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 20F;

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType wallType = RenderType.debugStructureQuads(); // translucent, no culling, depth-tested, no depth write
        RenderType glowType = ModRenderTypes.glow();            // additive glow, no depth write
        PoseStack pose = event.getPoseStack();
        // A zone is often bigger than the far clipping plane: anything past it would be cut away, leaving a round
        // hole in the wall right where you look. Points that far out are pulled in along their line of sight instead.
        maxDistance = mc.gameRenderer.getDepthFar() * 0.9F;
        boolean drew = false;
        for (ClientZones.View zone : views) {
            Vec3 center = Vec3.atCenterOf(zone.center());
            double dist = center.distanceTo(camera);
            playCues(mc, zone, dist);
            if (dist - zone.radius() > viewRange) continue; // too far away to see
            Look look = Look.of(zone, time);
            pose.pushPose();
            pose.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
            Vec3 cameraLocal = camera.subtract(center);
            // The zone is a dome over a cylinder: below the center its wall goes straight down to the world's bottom.
            float bottom = (float) (mc.level.getMinBuildHeight() - center.y);
            if (zone.radius() > 0.5F && look.wallAlpha > 0) {
                renderShell(pose, buffers.getBuffer(wallType), zone.radius(), bottom, cameraLocal, time, look, false);
            }
            if (look.shell > 0 && zone.radius() > 0.5F) {
                renderShell(pose, buffers.getBuffer(glowType), zone.radius() * 1.004F, bottom, cameraLocal, time, look, true);
            }
            if (look.flash > 0) {
                // A ball of light at the crater: the zone's first moment, or its implosion.
                Look ball = new Look(0, 0, 0, look.flash, look.flashColor, 0, 0, look.flashColor);
                renderShell(pose, buffers.getBuffer(glowType), look.flashRadius, Float.NaN, cameraLocal, time, ball, true);
            }
            pose.popPose();
            drew = true;
        }
        if (drew) {
            buffers.endBatch(wallType);
            buffers.endBatch(glowType);
        }
    }

    /** How a zone looks this frame, from its animation phase. */
    private record Look(float wallAlpha, float churn, float heat, float shell, float[] shellColor,
                        float flash, float flashRadius, float[] flashColor) {
        static Look of(ClientZones.View zone, float time) {
            float p = zone.progress();
            return switch (zone.phase()) {
                case STEADY -> new Look(1, 0, 0, 0, SHOCK, 0, 0, WHITE);
                case BIRTH -> {
                    // The wall thickens behind the shock front; the front fades as it slows.
                    float wall = Mth.clamp(p * 2.5F, 0, 1);
                    float shell = (1 - p) * 0.9F;
                    float flash = Math.max(0, 1 - p / 0.12F);
                    yield new Look(wall, 0.5F * (1 - p), 0, shell, SHOCK, flash, 20 + 120 * p, WHITE);
                }
                case RESIZE -> new Look(1, 0.3F, 0, Mth.sin(Mth.PI * p) * 0.6F, SHOCK, 0, 0, WHITE);
                case CLOSING -> {
                    float shrink = Math.min(1, p / ClientZones.CLOSING_SHRINK);
                    float after = Mth.clamp((p - ClientZones.CLOSING_SHRINK) / (1 - ClientZones.CLOSING_SHRINK), 0, 1);
                    // Churning and glowing hotter as it collapses; at the end, an implosion flash.
                    float shell = 0.2F + 0.7F * shrink;
                    float flash = after > 0 ? 1 - after : 0;
                    yield new Look(1, 0.6F + 1.4F * shrink, shrink, shell, shrink > 0.7F ? WHITE : SHOCK,
                            flash, 4 + 60 * after, WHITE);
                }
            };
        }
    }

    /**
     * The zone's surface: a dome (the upper half of a sphere) over a straight wall down to {@code bottom} (local y),
     * or with {@code bottom} NaN a whole sphere (the flash ball).
     */
    private static void renderShell(PoseStack pose, VertexConsumer vc, float radius, float bottom, Vec3 cameraLocal,
                                    float time, Look look, boolean glow) {
        PoseStack.Pose p = pose.last();
        boolean sphere = Float.isNaN(bottom);
        double horizontal = Math.sqrt(cameraLocal.x * cameraLocal.x + cameraLocal.z * cameraLocal.z);
        boolean inside = sphere || cameraLocal.y >= 0
                ? cameraLocal.lengthSqr() < (double) radius * radius
                : horizontal < radius && cameraLocal.y > bottom;
        // The dome (or the whole sphere): latitude from the equator (or the south pole) up to the north pole.
        int firstStack = sphere ? 0 : STACKS / 2;
        for (int i = firstStack; i < STACKS; i++) {
            float lat0 = Mth.PI * i / STACKS - Mth.HALF_PI, lat1 = Mth.PI * (i + 1) / STACKS - Mth.HALF_PI;
            for (int j = 0; j < SLICES; j++) {
                float lon0 = Mth.TWO_PI * j / SLICES, lon1 = Mth.TWO_PI * (j + 1) / SLICES;
                quad(p, vc, radius, lat0, lat1, lon0, lon1, Float.NaN, Float.NaN, cameraLocal, inside, time, look, glow);
            }
        }
        if (sphere || bottom >= 0) return;
        // The wall below, in bands, so the shimmer runs on down it.
        int bands = Mth.clamp((int) (-bottom / 8), 4, 48);
        for (int i = 0; i < bands; i++) {
            float y0 = bottom * i / bands, y1 = bottom * (i + 1) / bands;
            for (int j = 0; j < SLICES; j++) {
                float lon0 = Mth.TWO_PI * j / SLICES, lon1 = Mth.TWO_PI * (j + 1) / SLICES;
                quad(p, vc, radius, 0, 0, lon0, lon1, y0, y1, cameraLocal, inside, time, look, glow);
            }
        }
    }

    /** One quad of the surface: on the sphere by latitude, or (with {@code y0}, {@code y1} set) on the wall below. */
    private static void quad(PoseStack.Pose p, VertexConsumer vc, float radius, float lat0, float lat1, float lon0, float lon1,
                             float y0, float y1, Vec3 cameraLocal, boolean inside, float time, Look look, boolean glow) {
        vertex(p, vc, radius, lat0, lon0, y0, cameraLocal, inside, time, look, glow);
        vertex(p, vc, radius, lat1, lon0, y1, cameraLocal, inside, time, look, glow);
        vertex(p, vc, radius, lat1, lon1, y1, cameraLocal, inside, time, look, glow);
        vertex(p, vc, radius, lat0, lon1, y0, cameraLocal, inside, time, look, glow);
        if (glow) { // the glow render type culls back faces: draw the other winding too
            vertex(p, vc, radius, lat0, lon1, y0, cameraLocal, inside, time, look, glow);
            vertex(p, vc, radius, lat1, lon1, y1, cameraLocal, inside, time, look, glow);
            vertex(p, vc, radius, lat1, lon0, y1, cameraLocal, inside, time, look, glow);
            vertex(p, vc, radius, lat0, lon0, y0, cameraLocal, inside, time, look, glow);
        }
    }

    /** A point on the sphere at {@code lat}, {@code lon}; or, if {@code wallY} is set, on the wall at that height. */
    private static void vertex(PoseStack.Pose p, VertexConsumer vc, float radius, float lat, float lon, float wallY,
                               Vec3 cameraLocal, boolean inside, float time, Look look, boolean glow) {
        float nx, ny, nz, x, y, z;
        if (Float.isNaN(wallY)) {
            float c = Mth.cos(lat);
            nx = c * Mth.cos(lon);
            ny = Mth.sin(lat);
            nz = c * Mth.sin(lon);
            x = nx * radius;
            y = ny * radius;
            z = nz * radius;
        } else {
            nx = Mth.cos(lon);
            ny = 0;
            nz = Mth.sin(lon);
            x = nx * radius;
            y = wallY;
            z = nz * radius;
            lat = wallY / radius; // keeps the shimmer running on past the equator
        }
        // How squarely we're looking at this bit of surface: 1 face-on, 0 edge-on.
        double vx = cameraLocal.x - x, vy = cameraLocal.y - y, vz = cameraLocal.z - z;
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        float facing = len < 1e-3 ? 1 : (float) Math.abs((nx * vx + ny * vy + nz * vz) / len);
        float rim = 1 - facing;
        // Drifting violet shimmer; while animating, the surface churns with faster, bigger ripples.
        float wave = Mth.sin(lon * 6 + lat * 4 + time * (0.3F + 2.5F * look.churn))
                * (0.5F + 0.5F * Mth.sin(lon * 17 - lat * 11 + time * 1.7F * look.churn));
        float shimmer = (GLOW_ALPHA + 0.3F * look.churn) * (0.5F + 0.5F * wave);
        // Past the far plane: slide it toward the camera along the same ray. It lands on the same spot on screen,
        // and since nothing renders that far out, it still sits behind all the terrain.
        if (len > maxDistance) {
            double k = maxDistance / len;
            x = (float) (cameraLocal.x - vx * k);
            y = (float) (cameraLocal.y - vy * k);
            z = (float) (cameraLocal.z - vz * k);
        }
        if (glow) {
            // Additive: a band of light brightest where you look along the surface, so it reads as a shell.
            float a = look.shell * (0.25F + 0.75F * rim) * (0.7F + 0.3F * wave);
            vc.addVertex(p, x, y, z).setColor(look.shellColor[0], look.shellColor[1], look.shellColor[2], Mth.clamp(a, 0, 1));
            return;
        }
        // Outside: a dark silhouette at the rim. Inside: evenly dark, so the wall reads as a wall in every direction.
        float alpha = (inside ? BASE_ALPHA : BASE_ALPHA + (RIM_ALPHA - BASE_ALPHA) * rim * rim) * look.wallAlpha;
        float r = 0.04F + 0.25F * shimmer + 0.5F * look.heat * shimmer, g = 0.1F * look.heat * shimmer,
                b = 0.07F + 0.45F * shimmer;
        vc.addVertex(p, x, y, z).setColor(Mth.clamp(r, 0, 1), Mth.clamp(g, 0, 1), Mth.clamp(b, 0, 1), Mth.clamp(alpha, 0, 1));
    }

    // ------------------------------------------------------------------ sounds

    /** One-off sounds for each animation, for players near enough to the zone to notice. */
    private static void playCues(Minecraft mc, ClientZones.View zone, double dist) {
        if (zone.phase() == ClientZones.Phase.STEADY || dist > zone.radius() + 300) return;
        int played = CUES.getOrDefault(zone.started(), 0);
        int next = played;
        switch (zone.phase()) {
            case BIRTH -> {
                if (played < 1) {
                    play(mc, SoundEvents.WARDEN_SONIC_BOOM, 0.4F, 1.0F);
                    play(mc, SoundEvents.AMBIENT_CAVE.value(), 0.5F, 1.0F);
                    next = 1;
                }
            }
            case RESIZE -> {
                if (played < 1) {
                    play(mc, SoundEvents.BEACON_POWER_SELECT, 0.5F, 0.6F);
                    next = 1;
                }
            }
            case CLOSING -> {
                if (played < 1) {
                    play(mc, SoundEvents.BEACON_DEACTIVATE, 0.4F, 1.0F);
                    play(mc, SoundEvents.PORTAL_TRIGGER, 0.6F, 0.5F);
                    next = 1;
                }
                if (played < 2 && zone.progress() >= ClientZones.CLOSING_SHRINK) {
                    play(mc, SoundEvents.WARDEN_SONIC_BOOM, 0.7F, 1.0F);
                    play(mc, SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 0.8F);
                    next = 2;
                }
            }
            default -> {}
        }
        if (next != played) CUES.put(zone.started(), next);
    }

    private static void play(Minecraft mc, SoundEvent sound, float pitch, float volume) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private ZoneBorderRenderer() {}
}
