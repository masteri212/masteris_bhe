package com.masteri.bhe.client;

import com.masteri.bhe.block.entity.BlackHoleBombBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * An armed Black Hole Bomb: the casing pulses violet, then red, faster as the fuse runs down. For the last few
 * seconds the black hole breaks out: it swells out of the block with a photon ring, a spinning disc and sparks
 * spiraling in (the pull itself is the block entity's).
 */
public class BlackHoleBombRenderer implements BlockEntityRenderer<BlackHoleBombBlockEntity> {
    private static final float[] VIOLET = {0.6F, 0.3F, 1.0F}, RED = {1.0F, 0.2F, 0.15F}, WHITE_VIOLET = {0.95F, 0.85F, 1.0F},
            ORANGE = {1.0F, 0.55F, 0.2F};

    public BlackHoleBombRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(BlackHoleBombBlockEntity bomb, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (!bomb.isArmed() || bomb.getLevel() == null) return;
        float time = bomb.getLevel().getGameTime() % 24000 + partialTick;
        float t = bomb.fuseProgress(partialTick);
        float[] color = lerp(VIOLET, RED, t);
        // A heartbeat that quickens as the fuse runs down.
        float period = Mth.lerp(t * t, 20F, 4F);
        float pulse = (float) Math.exp(-(time % period) / period * 5);

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.glow());
        ReactorCoreRenderer.renderFlare(pose, glow, new Vector3f(), 0.9F + 0.6F * pulse + 1.2F * t, color, 0.35F + 0.5F * pulse);

        float fuse = bomb.getFuse() - partialTick;
        if (fuse <= BlackHoleBombBlockEntity.BREAKOUT_TICKS) {
            float b = Mth.clamp(1 - fuse / BlackHoleBombBlockEntity.BREAKOUT_TICKS, 0, 1);
            float horizon = 0.3F + 2.4F * (1 - (1 - b) * (1 - b)) * (1 + 0.08F * pulse);
            // The black hole itself (drawn first, so the glow sits around it)...
            ReactorCoreRenderer.renderSphere(pose, buffers.getBuffer(RenderType.debugQuads()), horizon);
            glow = buffers.getBuffer(ModRenderTypes.glow());
            // ...its photon ring...
            pose.pushPose();
            ReactorCoreRenderer.faceCamera(pose);
            ReactorCoreRenderer.renderRing(pose, glow, horizon * 1.02F, horizon * 1.4F, WHITE_VIOLET, 0.9F, 0);
            ReactorCoreRenderer.renderRing(pose, glow, horizon * 1.4F, horizon * 2.3F, RED, 0.35F + 0.3F * pulse, 0);
            pose.popPose();
            // ...a disc spinning up around it...
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(time * (10 + 40 * b)));
            pose.mulPose(Axis.XP.rotationDegrees(90 + 12 * Mth.sin(time * 0.3F)));
            ReactorCoreRenderer.renderRing(pose, glow, horizon * 1.3F, horizon * 3.6F, lerp(ORANGE, RED, b), 0.55F * b, 0);
            pose.popPose();
            // ...and sparks spiraling in.
            for (int k = 0; k < 20; k++) {
                float f = (time * 0.03F + k / 20F) % 1;
                float radius = Mth.lerp(f * f, horizon * 4 + 4, horizon);
                float angle = k * 2.39996F + f * Mth.TWO_PI * 2;
                Vector3f at = new Vector3f(Mth.cos(angle) * radius, (1 - f) * Mth.sin(k * 1.7F) * 2, Mth.sin(angle) * radius);
                ReactorCoreRenderer.renderFlare(pose, glow, at, 0.25F + 0.2F * f, color, b * Mth.sin(Mth.PI * f));
            }
        }
        pose.popPose();
    }

    private static float[] lerp(float[] a, float[] c, float t) {
        return new float[] {Mth.lerp(t, a[0], c[0]), Mth.lerp(t, a[1], c[1]), Mth.lerp(t, a[2], c[2])};
    }

    @Override
    public AABB getRenderBoundingBox(BlackHoleBombBlockEntity bomb) {
        return new AABB(bomb.getBlockPos()).inflate(16);
    }
}
