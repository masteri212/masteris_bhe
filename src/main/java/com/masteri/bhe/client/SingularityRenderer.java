package com.masteri.bhe.client;

import com.masteri.bhe.entity.SingularityEntity;
import com.masteri.bhe.entity.SingularityEntity.Attack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * The Singularity: a black sphere with a white-hot photon ring and a wide, tilted accretion disc turning around it.
 * Fed, it swells and its light turns from gold to an unstable flickering white-red. A Hawking burst gathers as a
 * growing flare before it fires; the tide spins the disc up and throws a red halo round it.
 */
public class SingularityRenderer extends EntityRenderer<SingularityEntity> {
    private static final float[] WHITE = {1.0F, 0.95F, 0.85F}, GOLD = {1.0F, 0.65F, 0.2F}, RED = {1.0F, 0.2F, 0.1F},
            EMBER = {0.9F, 0.3F, 0.1F}, PALE = {0.75F, 0.95F, 1.0F};

    public SingularityRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public boolean shouldRender(SingularityEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBoxForCulling());
    }

    @Override
    public void render(SingularityEntity s, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float time = s.tickCount + partialTick;
        float fed = s.fedFraction();
        float r = s.horizonRadius();
        Attack attack = s.getAttack();
        float tick = s.getAttackTick() + (attack == Attack.NONE ? 0 : partialTick);
        float flicker = fed > 0.5F ? 0.8F + 0.2F * Mth.sin(time * (0.6F + fed)) : 1;
        float[] disc = lerp(GOLD, RED, Math.max(0, fed * 1.4F - 0.4F));

        pose.pushPose();
        pose.translate(0, s.getBbHeight() / 2, 0);
        ReactorCoreRenderer.renderSphere(pose, buffers.getBuffer(RenderType.debugQuads()), r);
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.glow());
        // Photon ring and halo, facing the camera.
        pose.pushPose();
        ReactorCoreRenderer.faceCamera(pose);
        ReactorCoreRenderer.renderRing(pose, glow, r * 1.02F, r * 1.35F, WHITE, 0.95F * flicker, 0);
        ReactorCoreRenderer.renderRing(pose, glow, r * 1.35F, r * 2.4F, attack == Attack.TIDE ? RED : disc, 0.35F * flicker, 0);
        pose.popPose();
        // The disc: wide, tilted, turning; faster in a tide.
        float spin = attack == Attack.TIDE ? 14 : 3 + 6 * fed;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(time * spin));
        pose.mulPose(Axis.XP.rotationDegrees(78 + 6 * Mth.sin(time * 0.02F)));
        ReactorCoreRenderer.renderRing(pose, glow, r * 1.25F, r * 2.2F, WHITE, 0.7F * flicker, 0);
        ReactorCoreRenderer.renderRing(pose, glow, r * 2.2F, r * 4.6F, disc, 0.55F * flicker, 0);
        ReactorCoreRenderer.renderRing(pose, glow, r * 4.6F, r * 6.5F, EMBER, 0.25F, 0);
        pose.popPose();
        // A Hawking burst gathering, then going off.
        if (attack == Attack.BURST) {
            float charge = Mth.clamp(tick / SingularityEntity.BURST_CHARGE, 0, 1);
            float after = Math.max(0, tick - SingularityEntity.BURST_CHARGE) / 10F;
            float alpha = tick < SingularityEntity.BURST_CHARGE ? 0.3F + 0.6F * charge : Math.max(0, 1 - after);
            ReactorCoreRenderer.renderFlare(pose, glow, new Vector3f(), r * (1.5F + 2.5F * charge + 6 * after), PALE, alpha);
        }
        // Sparks spiraling in.
        for (int k = 0; k < 16; k++) {
            float f = (time * 0.02F + k / 16F) % 1;
            float radius = Mth.lerp(f * f, r * 7, r);
            float angle = k * 2.39996F + f * Mth.TWO_PI * 2;
            Vector3f at = new Vector3f(Mth.cos(angle) * radius, (1 - f) * Mth.sin(k * 1.7F) * r, Mth.sin(angle) * radius);
            ReactorCoreRenderer.renderFlare(pose, glow, at, 0.3F + 0.3F * f, disc, Mth.sin(Mth.PI * f) * 0.8F);
        }
        pose.popPose();
        super.render(s, yaw, partialTick, pose, buffers, light);
    }

    private static float[] lerp(float[] a, float[] c, float t) {
        t = Mth.clamp(t, 0, 1);
        return new float[] {Mth.lerp(t, a[0], c[0]), Mth.lerp(t, a[1], c[1]), Mth.lerp(t, a[2], c[2])};
    }

    @Override
    public ResourceLocation getTextureLocation(SingularityEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
