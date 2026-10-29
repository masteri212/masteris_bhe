package com.masteri.bhe.client;

import com.masteri.bhe.block.entity.HorizonGateBlockEntity;
import com.masteri.bhe.block.entity.HorizonGateBlockEntity.Phase;
import com.masteri.bhe.horizon.HorizonRing;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.util.List;

/**
 * A Horizon Ring, drawn from its gate.
 * <ul>
 *     <li>Idle: the frames glow faintly with the gate's charge, and a spark hangs in the middle.</li>
 *     <li>Spinning up: the frames light one by one round the ring while a swirl gathers in the middle, faster and
 *     faster.</li>
 *     <li>Opening: a burst of light bulges out of both faces of the ring and falls back.</li>
 *     <li>Open: a black event horizon filling the ring, an accretion swirl turning round its rim, a white-hot
 *     photon ring, running lights on the frames, and arcs of lightning jumping from the frames to the rim.</li>
 * </ul>
 */
public class HorizonGateRenderer implements BlockEntityRenderer<HorizonGateBlockEntity> {
    private static final float[] VIOLET = {0.6F, 0.3F, 1.0F}, WHITE_VIOLET = {0.95F, 0.85F, 1.0F}, GOLD = {1.0F, 0.7F, 0.3F},
            HOT = {1.0F, 0.95F, 0.85F}, CYAN = {0.5F, 0.9F, 1.0F};
    private static final float HORIZON = 2.7F, RIM = HorizonRing.INNER_RADIUS + 0.1F;
    private static final int SEGMENTS = 64, BANDS = 6;

    public HorizonGateRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(HorizonGateBlockEntity gate, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (gate.getLevel() == null) return;
        float time = gate.getLevel().getGameTime() % 24000 + partialTick;
        Phase phase = gate.getPhase();
        Direction.Axis axis = gate.getAxis();
        if (phase == Phase.IDLE) axis = HorizonRing.find(gate.getLevel(), gate.getBlockPos());
        pose.pushPose();
        if (axis == null) {
            // No ring: just a spark over the gate, brighter as it charges.
            pose.translate(0.5, 1.6, 0.5);
            float charge = gate.charge();
            ReactorCoreRenderer.renderFlare(pose, buffers.getBuffer(ModRenderTypes.glow()), new Vector3f(), 0.2F + 0.5F * charge, VIOLET, 0.25F + 0.5F * charge);
            pose.popPose();
            return;
        }
        // Into the ring's own frame: x along the ring, y up, z straight through it; origin in the middle.
        pose.translate(0.5, 0.5 + HorizonRing.CENTER_UP, 0.5);
        if (axis == Direction.Axis.Z) pose.mulPose(Axis.YP.rotationDegrees(90));
        float age = gate.getAge() + partialTick;
        switch (phase) {
            case IDLE -> idle(gate, pose, buffers, time);
            case SPINNING -> spinning(pose, buffers, time, age);
            case OPEN -> open(gate, pose, buffers, time, age, partialTick);
        }
        pose.popPose();
    }

    private static void idle(HorizonGateBlockEntity gate, PoseStack pose, MultiBufferSource buffers, float time) {
        float charge = gate.charge();
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.glow());
        List<int[]> frames = HorizonRing.FRAMES;
        for (int i = 0; i < frames.size(); i++) {
            float pulse = 0.5F + 0.5F * Mth.sin(time * 0.08F - i * 0.4F);
            frameFlare(pose, glow, frames.get(i), 0.7F, VIOLET, (0.05F + 0.2F * charge) * pulse);
        }
        ReactorCoreRenderer.renderFlare(pose, glow, new Vector3f(), 0.3F + 0.8F * charge, VIOLET, 0.2F + 0.5F * charge);
    }

    private static void spinning(PoseStack pose, MultiBufferSource buffers, float time, float age) {
        float p = Mth.clamp(age / HorizonGateBlockEntity.SPIN_TICKS, 0, 1);
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.glow());
        List<int[]> frames = HorizonRing.FRAMES;
        float lit = p * frames.size();
        for (int i = 0; i < frames.size(); i++) {
            if (i > lit) {
                frameFlare(pose, glow, frames.get(i), 0.6F, VIOLET, 0.08F);
                continue;
            }
            boolean newest = lit - i < 1;
            frameFlare(pose, glow, frames.get(i), newest ? 1.6F : 1.0F, newest ? HOT : VIOLET, newest ? 1.0F : 0.75F);
        }
        // The swirl gathering in the middle, spinning up.
        float spin = age * age * 0.012F;
        swirl(pose, glow, 0.2F, RIM * p, spin, VIOLET, 0.25F + 0.6F * p);
        ReactorCoreRenderer.renderFlare(pose, glow, new Vector3f(), 0.4F + 1.6F * p * p, WHITE_VIOLET, 0.3F + 0.6F * p);
        // A dashed ring turning just inside the frames.
        dashes(pose, glow, RIM - 0.25F, RIM + 0.05F, spin * 0.5F, CYAN, 0.6F * p);
    }

    private static void open(HorizonGateBlockEntity gate, PoseStack pose, MultiBufferSource buffers, float time, float age, float partialTick) {
        float left = Math.max(0, gate.getLeft() - partialTick);
        float size = Mth.clamp(left / HorizonGateBlockEntity.CLOSE_TICKS, 0, 1) * Mth.clamp(age / 6, 0, 1);
        size = 1 - (1 - size) * (1 - size);
        float horizon = HORIZON * size;
        // The event horizon itself: black, both faces.
        blackDisc(pose, buffers.getBuffer(RenderType.debugQuads()), horizon);
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.glow());
        // Accretion swirl from the horizon out to the frames, and the photon ring at its edge.
        swirl(pose, glow, horizon, RIM, time * 0.12F, GOLD, 0.85F * size);
        swirl(pose, glow, horizon, RIM, -time * 0.05F + 1.3F, VIOLET, 0.4F * size);
        ReactorCoreRenderer.renderRing(pose, glow, horizon, horizon * 1.08F + 0.05F, HOT, 0.95F * size, 0);
        // A wide soft halo past the frames.
        ReactorCoreRenderer.renderRing(pose, glow, RIM + 0.4F, RIM + 3.5F, VIOLET, 0.18F * size, 0);
        // Running lights round the frames.
        List<int[]> frames = HorizonRing.FRAMES;
        for (int i = 0; i < frames.size(); i++) {
            float run = 0.5F + 0.5F * Mth.sin(time * 0.35F - i * Mth.TWO_PI / frames.size() * 2);
            frameFlare(pose, glow, frames.get(i), 0.9F + 0.5F * run, run > 0.85F ? HOT : VIOLET, (0.45F + 0.5F * run) * size);
        }
        // Arcs from the frames to the rim, re-rolled every few ticks.
        int seed = (int) (time / 3);
        for (int k = 0; k < 3; k++) {
            int[] from = frames.get(Math.floorMod(hash(seed, k), frames.size()));
            float a = (hash(seed, k + 7) & 1023) / 1024F * Mth.TWO_PI;
            arc(pose, glow, new Vector3f(from[0], from[1] - HorizonRing.CENTER_UP, 0),
                    new Vector3f(Mth.cos(a) * horizon * 1.1F, Mth.sin(a) * horizon * 1.1F, 0), seed * 31 + k, size);
        }
        // The opening burst: a bulge of light out of both faces, falling back.
        if (age < HorizonGateBlockEntity.BURST_TICKS) {
            float b = age / HorizonGateBlockEntity.BURST_TICKS;
            float reach = 5 * Mth.sin(Mth.PI * b);
            for (int side = -1; side <= 1; side += 2) {
                for (int j = 1; j <= 10; j++) {
                    float t = j / 10F;
                    pose.pushPose();
                    pose.translate(0, 0, side * reach * t);
                    float r = RIM * (1 - 0.85F * t * t);
                    ReactorCoreRenderer.renderRing(pose, glow, r * 0.3F, r, j < 4 ? HOT : WHITE_VIOLET, (1 - b) * (0.9F - 0.6F * t), 0);
                    pose.popPose();
                }
            }
        }
    }

    // ------------------------------------------------------------------ pieces

    private static void frameFlare(PoseStack pose, VertexConsumer glow, int[] frame, float size, float[] color, float alpha) {
        ReactorCoreRenderer.renderFlare(pose, glow, new Vector3f(frame[0], frame[1] - HorizonRing.CENTER_UP, 0), size, color, alpha);
    }

    /** A flat black disc in the ring's plane, seen from either side. */
    private static void blackDisc(PoseStack pose, VertexConsumer vc, float r) {
        if (r <= 0.01F) return;
        PoseStack.Pose p = pose.last();
        for (int s = 0; s < SEGMENTS; s++) {
            float a0 = Mth.TWO_PI * s / SEGMENTS, a1 = Mth.TWO_PI * (s + 1) / SEGMENTS;
            float x0 = r * Mth.cos(a0), y0 = r * Mth.sin(a0), x1 = r * Mth.cos(a1), y1 = r * Mth.sin(a1);
            vc.addVertex(p, 0, 0, 0).setColor(0, 0, 0, 255);
            vc.addVertex(p, x0, y0, 0).setColor(0, 0, 0, 255);
            vc.addVertex(p, x1, y1, 0).setColor(0, 0, 0, 255);
            vc.addVertex(p, 0, 0, 0).setColor(0, 0, 0, 255);
            vc.addVertex(p, 0, 0, 0).setColor(0, 0, 0, 255);
            vc.addVertex(p, x1, y1, 0).setColor(0, 0, 0, 255);
            vc.addVertex(p, x0, y0, 0).setColor(0, 0, 0, 255);
            vc.addVertex(p, 0, 0, 0).setColor(0, 0, 0, 255);
        }
    }

    /** An annulus of spiral streaks turning by {@code turn}: bright at its inner edge, fading outward. */
    private static void swirl(PoseStack pose, VertexConsumer vc, float r0, float r1, float turn, float[] color, float alpha) {
        if (r1 - r0 < 0.05F || alpha <= 0) return;
        PoseStack.Pose p = pose.last();
        for (int band = 0; band < BANDS; band++) {
            float t0 = band / (float) BANDS, t1 = (band + 1) / (float) BANDS;
            float ra = Mth.lerp(t0, r0, r1), rb = Mth.lerp(t1, r0, r1);
            for (int s = 0; s < SEGMENTS; s++) {
                float a0 = Mth.TWO_PI * s / SEGMENTS, a1 = Mth.TWO_PI * (s + 1) / SEGMENTS;
                int c00 = swirlColor(color, alpha, t0, a0, turn), c01 = swirlColor(color, alpha, t0, a1, turn);
                int c10 = swirlColor(color, alpha, t1, a0, turn), c11 = swirlColor(color, alpha, t1, a1, turn);
                quad(p, vc, ra, rb, a0, a1, c00, c10, c11, c01);
            }
        }
    }

    private static int swirlColor(float[] color, float alpha, float t, float a, float turn) {
        // Four spiral arms; the spiral leans with the radius so it reads as falling in.
        float streak = 0.35F + 0.65F * Math.max(0, Mth.sin(4 * a + t * 7 - turn));
        float fade = (1 - t) * (1 - t);
        float hot = 1 - t;
        return ReactorCoreRenderer.argb(alpha * streak * fade, Mth.lerp(hot * 0.5F, color[0], 1), Mth.lerp(hot * 0.5F, color[1], 1),
                Mth.lerp(hot * 0.5F, color[2], 1));
    }

    /** A ring of short dashes, turned by {@code turn}. */
    private static void dashes(PoseStack pose, VertexConsumer vc, float r0, float r1, float turn, float[] color, float alpha) {
        PoseStack.Pose p = pose.last();
        int c = ReactorCoreRenderer.argb(alpha, color[0], color[1], color[2]);
        for (int d = 0; d < 18; d++) {
            float a0 = turn + Mth.TWO_PI * d / 18, a1 = a0 + Mth.TWO_PI / 18 * 0.55F;
            quad(p, vc, r0, r1, a0, a1, c, c, c, c);
        }
    }

    /** A quad between two radii and two angles, both windings. Corners: (r0,a0) (r1,a0) (r1,a1) (r0,a1). */
    private static void quad(PoseStack.Pose p, VertexConsumer vc, float r0, float r1, float a0, float a1, int c00, int c10, int c11, int c01) {
        float x00 = r0 * Mth.cos(a0), y00 = r0 * Mth.sin(a0), x10 = r1 * Mth.cos(a0), y10 = r1 * Mth.sin(a0);
        float x11 = r1 * Mth.cos(a1), y11 = r1 * Mth.sin(a1), x01 = r0 * Mth.cos(a1), y01 = r0 * Mth.sin(a1);
        vc.addVertex(p, x00, y00, 0).setColor(c00);
        vc.addVertex(p, x10, y10, 0).setColor(c10);
        vc.addVertex(p, x11, y11, 0).setColor(c11);
        vc.addVertex(p, x01, y01, 0).setColor(c01);
        vc.addVertex(p, x01, y01, 0).setColor(c01);
        vc.addVertex(p, x11, y11, 0).setColor(c11);
        vc.addVertex(p, x10, y10, 0).setColor(c10);
        vc.addVertex(p, x00, y00, 0).setColor(c00);
    }

    /** A jagged bolt from {@code a} to {@code b}, a few kinks, flickering. */
    private static void arc(PoseStack pose, VertexConsumer vc, Vector3f a, Vector3f b, int seed, float alpha) {
        PoseStack.Pose p = pose.last();
        int kinks = 5;
        Vector3f prev = new Vector3f(a);
        Vector3f across = new Vector3f(b).sub(a).cross(0, 0, 1);
        if (across.lengthSquared() < 1e-4F) return;
        across.normalize();
        for (int i = 1; i <= kinks; i++) {
            float t = i / (float) kinks;
            Vector3f next = new Vector3f(a).lerp(b, t);
            if (i < kinks) next.add(new Vector3f(across).mul(((hash(seed, i) & 255) / 255F - 0.5F) * 0.9F))
                    .add(0, 0, ((hash(seed, i + 50) & 255) / 255F - 0.5F) * 0.4F);
            int core = ReactorCoreRenderer.argb(alpha * 0.9F, CYAN[0], CYAN[1], CYAN[2]);
            ReactorCoreRenderer.beamPlane(p, vc, prev, next, new Vector3f(0, 0, 1), 0.12F, core);
            ReactorCoreRenderer.beamPlane(p, vc, prev, next, across, 0.12F, core);
            prev = next;
        }
    }

    private static int hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    @Override
    public AABB getRenderBoundingBox(HorizonGateBlockEntity gate) {
        return new AABB(gate.getBlockPos()).inflate(9).move(0, HorizonRing.CENTER_UP, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(HorizonGateBlockEntity gate) {
        return true;
    }
}
