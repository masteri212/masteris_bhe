package com.masteri.bhe.client;

import com.masteri.bhe.entity.HawkingWispEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/** A Hawking Wisp: a bright flickering point with a few motes orbiting it, shrinking and dimming as it evaporates. */
public class HawkingWispRenderer extends EntityRenderer<HawkingWispEntity> {
    private static final float[] PALE = {0.75F, 0.95F, 1.0F}, WHITE = {1.0F, 1.0F, 1.0F};

    public HawkingWispRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public void render(HawkingWispEntity wisp, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float time = wisp.tickCount + partialTick;
        float life = wisp.lifeFraction();
        float size = 0.25F + 0.35F * life;
        float flicker = 0.75F + 0.25F * Mth.sin(time * 0.9F) * Mth.sin(time * 0.37F);
        VertexConsumer glow = ModRenderTypes.glow(buffers);
        pose.pushPose();
        pose.translate(0, wisp.getBbHeight() / 2, 0);
        ReactorCoreRenderer.renderFlare(pose, glow, new Vector3f(), size * 2.2F, PALE, (0.3F + 0.6F * life) * flicker);
        ReactorCoreRenderer.renderFlare(pose, glow, new Vector3f(), size, WHITE, 0.9F * flicker);
        for (int i = 0; i < 4; i++) {
            float a = time * (0.08F + i * 0.02F) + i * Mth.HALF_PI;
            float r = 0.35F + 0.1F * i;
            Vector3f at = new Vector3f(Mth.cos(a) * r, Mth.sin(a * 1.3F + i) * 0.2F, Mth.sin(a) * r);
            ReactorCoreRenderer.renderFlare(pose, glow, at, 0.08F + 0.04F * life, PALE, 0.7F * life);
        }
        pose.popPose();
        super.render(wisp, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HawkingWispEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
