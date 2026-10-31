package com.masteri.bhe.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Event Horizon's sky: black, with stars bent into a ring around a huge black hole hanging over the world.
 * Its accretion disc is nearly edge-on and slowly turning: the far half of the disc passes behind the shadow, the
 * near half across its face, and the light from the far side is bent up over the top. No sun, moon, clouds or
 * weather.
 */
public class EventHorizonEffects extends DimensionSpecialEffects {
    private static final float DISTANCE = 100, SHADOW = 15, DISC_INNER = 18, DISC_OUTER = 50;
    /** Where the black hole hangs: degrees round from north, and up from the horizon. */
    private static final float AZIMUTH = 35, ELEVATION = 22, TILT = -80;
    private static final int STARS = 1800, SEGMENTS = 96, BANDS = 10;
    private static final float[] HOT = {1.0F, 0.95F, 0.82F}, WARM = {1.0F, 0.55F, 0.15F}, EMBER = {0.8F, 0.18F, 0.08F};

    /** x, y, z, size, brightness per star, already bent around the black hole. */
    private final float[] stars = new float[STARS * 5];

    public EventHorizonEffects() {
        super(Float.NaN, false, SkyType.NONE, false, false);
        makeStars();
    }

    private void makeStars() {
        RandomSource random = RandomSource.create(4711L);
        Vector3f hole = holeDirection();
        for (int i = 0; i < STARS; i++) {
            Vector3f d = new Vector3f((float) random.nextGaussian(), (float) random.nextGaussian(), (float) random.nextGaussian()).normalize();
            // Gravitational lensing: stars near the hole are pushed outward from it, piling into a ring.
            float angle = (float) Math.acos(Mth.clamp(d.dot(hole), -1, 1));
            float shadowAngle = (float) Math.atan(SHADOW / DISTANCE);
            if (angle < 0.6F) {
                float bent = angle + shadowAngle * shadowAngle * 1.4F / Math.max(angle, 0.02F);
                Vector3f axis = new Vector3f(hole).cross(d);
                if (axis.lengthSquared() > 1e-6F) {
                    axis.normalize();
                    d = new Vector3f(hole).rotateAxis(bent, axis.x, axis.y, axis.z);
                }
            }
            stars[i * 5] = d.x * DISTANCE;
            stars[i * 5 + 1] = d.y * DISTANCE;
            stars[i * 5 + 2] = d.z * DISTANCE;
            stars[i * 5 + 3] = 0.12F + random.nextFloat() * random.nextFloat() * 0.35F;
            stars[i * 5 + 4] = 0.3F + random.nextFloat() * 0.7F;
        }
    }

    private static Vector3f holeDirection() {
        Vector3f d = new Vector3f(0, 0, -1);
        d.rotateX(ELEVATION * Mth.DEG_TO_RAD);
        d.rotateY(-AZIMUTH * Mth.DEG_TO_RAD);
        return d.normalize();
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor;
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Nullable
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY,
                                double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        return true; // none
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX,
                                     double camY, double camZ) {
        return true; // no weather
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return true;
    }

    // ------------------------------------------------------------------ the sky

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        // A shader pack paints its own sky over this one: it's drawn later instead (renderUnderShaders).
        if (ShaderCompat.shadersOn()) return true;
        PoseStack pose = new PoseStack();
        pose.mulPose(modelViewMatrix);
        FogRenderer.setupNoFog();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        paint(pose, ticks + partialTick, false);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        return true;
    }

    /**
     * The sky under a shader pack. Packs fill every pixel nothing was drawn on with their own sky, after the world
     * is drawn, so this sky is drawn as part of the world instead: after the pack's lighting (after translucent
     * blocks), centered on the camera and far beyond the terrain (half way to the far clipping plane), so terrain
     * still hides it.
     */
    void renderUnderShaders(float time, float depthFar) {
        PoseStack pose = new PoseStack();
        float scale = depthFar * 0.5F / DISTANCE;
        pose.scale(scale, scale, scale);
        paint(pose, time, true);
    }

    /** Draws the whole sky; {@code shaders}: through render types a shader pack understands. */
    private void paint(PoseStack pose, float time, boolean shaders) {
        additive(shaders);
        Matrix4f sky = pose.last().pose();
        draw(b -> stars(b, sky, time), shaders ? ModRenderTypes.skyGlow() : null);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-AZIMUTH));
        pose.mulPose(Axis.XP.rotationDegrees(ELEVATION));
        pose.translate(0, 0, -DISTANCE);
        Matrix4f m = pose.last().pose();
        RenderType glow = shaders ? ModRenderTypes.skyGlow() : null;
        // Behind the shadow: a soft glow, the far half of the disc, and the far side's light bent over the top.
        draw(b -> ring(b, m, SHADOW, DISC_OUTER * 1.25F, WARM, 0.12F, 0F, null), glow);
        draw(b -> disc(b, m, time, false), glow);
        draw(b -> lensedArc(b, m, time), glow);
        // The shadow itself: true black, hiding whatever is behind it.
        if (!shaders) RenderSystem.defaultBlendFunc();
        draw(b -> shadow(b, m), shaders ? ModRenderTypes.skyShadow() : null);
        // In front: the near half of the disc and the thin photon ring.
        additive(shaders);
        draw(b -> disc(b, m, time, true), glow);
        draw(b -> ring(b, m, SHADOW * 0.99F, SHADOW * 1.07F, HOT, 0.9F, 0.15F, null), glow);
        pose.popPose();
    }

    private static void additive(boolean shaders) {
        if (!shaders) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    private interface Builder {
        void build(VertexConsumer b);
    }

    /**
     * Draws position + color quads: with the shader already set ({@code type} null), or through {@code type} as
     * full-bright entity vertices.
     */
    private static void draw(Builder builder, @Nullable RenderType type) {
        if (type == null) {
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            builder.build(b);
            var mesh = b.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        } else {
            BufferBuilder b = Tesselator.getInstance().begin(type.mode(), type.format());
            builder.build(new ShaderCompat.FullBright(b));
            var mesh = b.build();
            if (mesh != null) type.draw(mesh);
        }
    }

    private void stars(VertexConsumer b, Matrix4f m, float time) {
        Vector3f up = new Vector3f(), side = new Vector3f();
        for (int i = 0; i < STARS; i++) {
            float x = stars[i * 5], y = stars[i * 5 + 1], z = stars[i * 5 + 2], size = stars[i * 5 + 3];
            float twinkle = 0.75F + 0.25F * Mth.sin(time * 0.05F + i * 1.7F);
            int color = ReactorCoreRenderer.argb(stars[i * 5 + 4] * twinkle, 0.9F, 0.9F, 1.0F);
            // A small square facing the center.
            Vector3f n = new Vector3f(x, y, z).normalize();
            (Math.abs(n.y) > 0.9F ? side.set(1, 0, 0) : side.set(0, 1, 0)).cross(n).normalize().mul(size);
            up.set(n).cross(side).normalize().mul(size);
            b.addVertex(m, x - side.x - up.x, y - side.y - up.y, z - side.z - up.z).setColor(color);
            b.addVertex(m, x + side.x - up.x, y + side.y - up.y, z + side.z - up.z).setColor(color);
            b.addVertex(m, x + side.x + up.x, y + side.y + up.y, z + side.z + up.z).setColor(color);
            b.addVertex(m, x - side.x + up.x, y - side.y + up.y, z - side.z + up.z).setColor(color);
        }
    }

    /**
     * The accretion disc, seen nearly edge-on: one half at a time (the near half has to go over the shadow, the far
     * half under it). Hot and white inside, ember red outside, with spiral streaks turning slowly, and the side
     * coming toward you brighter (Doppler beaming).
     */
    private static void disc(VertexConsumer b, Matrix4f m, float time, boolean near) {
        float cos = Mth.cos(TILT * Mth.DEG_TO_RAD), sin = Mth.sin(TILT * Mth.DEG_TO_RAD);
        for (int band = 0; band < BANDS; band++) {
            float t0 = band / (float) BANDS, t1 = (band + 1) / (float) BANDS;
            float r0 = Mth.lerp(t0, DISC_INNER, DISC_OUTER), r1 = Mth.lerp(t1, DISC_INNER, DISC_OUTER);
            for (int s = 0; s < SEGMENTS; s++) {
                float a0 = Mth.TWO_PI * s / SEGMENTS, a1 = Mth.TWO_PI * (s + 1) / SEGMENTS;
                // In the disc's own plane y is "across"; tilted, -y comes toward the camera.
                boolean segmentNear = Mth.sin((a0 + a1) / 2) < 0;
                if (segmentNear != near) continue;
                discVertex(b, m, r0, a0, t0, time, cos, sin);
                discVertex(b, m, r1, a0, t1, time, cos, sin);
                discVertex(b, m, r1, a1, t1, time, cos, sin);
                discVertex(b, m, r0, a1, t0, time, cos, sin);
            }
        }
    }

    private static void discVertex(VertexConsumer b, Matrix4f m, float r, float a, float t, float time, float cos, float sin) {
        float x = r * Mth.cos(a), y = r * Mth.sin(a);
        float streak = 0.7F + 0.3F * Mth.sin(5 * a - time * 0.02F + t * 9);
        float beaming = 1 + 0.6F * Mth.cos(a);
        float fade = (1 - t) * (1 - t * 0.5F);
        float[] c = t < 0.4F ? lerp(HOT, WARM, t / 0.4F) : lerp(WARM, EMBER, (t - 0.4F) / 0.6F);
        b.addVertex(m, x, y * cos, y * sin).setColor(ReactorCoreRenderer.argb(0.85F * fade * streak * beaming, c[0], c[1], c[2]));
    }

    /** The far side of the disc, its light bent up over the top of the shadow (and a fainter arc under it). */
    private static void lensedArc(VertexConsumer b, Matrix4f m, float time) {
        ring(b, m, SHADOW * 1.08F, SHADOW * 1.9F, WARM, 0.75F, 0.1F, a -> {
            float top = Math.max(0, Mth.sin(a)), bottom = Math.max(0, -Mth.sin(a));
            return (0.9F * top * top + 0.35F * bottom * bottom) * (0.85F + 0.15F * Mth.sin(6 * a + time * 0.03F));
        });
    }

    private interface Shape {
        float at(float angle);
    }

    /** A flat ring facing the camera: {@code alpha} at its inner edge, {@code outKeep} times that at its outer edge. */
    private static void ring(VertexConsumer b, Matrix4f m, float r0, float r1, float[] color, float alpha, float outKeep,
                             @Nullable Shape shape) {
        for (int s = 0; s < SEGMENTS; s++) {
            float a0 = Mth.TWO_PI * s / SEGMENTS, a1 = Mth.TWO_PI * (s + 1) / SEGMENTS;
            float k0 = shape == null ? 1 : shape.at(a0), k1 = shape == null ? 1 : shape.at(a1);
            int in0 = ReactorCoreRenderer.argb(alpha * k0, color[0], color[1], color[2]);
            int in1 = ReactorCoreRenderer.argb(alpha * k1, color[0], color[1], color[2]);
            int out0 = ReactorCoreRenderer.argb(alpha * k0 * outKeep, color[0], color[1], color[2]);
            int out1 = ReactorCoreRenderer.argb(alpha * k1 * outKeep, color[0], color[1], color[2]);
            b.addVertex(m, r0 * Mth.cos(a0), r0 * Mth.sin(a0), 0).setColor(in0);
            b.addVertex(m, r1 * Mth.cos(a0), r1 * Mth.sin(a0), 0).setColor(out0);
            b.addVertex(m, r1 * Mth.cos(a1), r1 * Mth.sin(a1), 0).setColor(out1);
            b.addVertex(m, r0 * Mth.cos(a1), r0 * Mth.sin(a1), 0).setColor(in1);
        }
    }

    private static void shadow(VertexConsumer b, Matrix4f m) {
        int black = 0xFF000000;
        for (int s = 0; s < SEGMENTS; s++) {
            float a0 = Mth.TWO_PI * s / SEGMENTS, a1 = Mth.TWO_PI * (s + 1) / SEGMENTS;
            b.addVertex(m, 0, 0, 0.01F).setColor(black);
            b.addVertex(m, SHADOW * Mth.cos(a0), SHADOW * Mth.sin(a0), 0.01F).setColor(black);
            b.addVertex(m, SHADOW * Mth.cos(a1), SHADOW * Mth.sin(a1), 0.01F).setColor(black);
            b.addVertex(m, 0, 0, 0.01F).setColor(black);
        }
    }

    private static float[] lerp(float[] a, float[] c, float t) {
        t = Mth.clamp(t, 0, 1);
        return new float[] {Mth.lerp(t, a[0], c[0]), Mth.lerp(t, a[1], c[1]), Mth.lerp(t, a[2], c[2])};
    }
}
