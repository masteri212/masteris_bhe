package com.masteri.bhe.client;

import com.masteri.bhe.block.entity.TrapCoreBlockEntity;
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

/** A Light Trap's core: a tiny black hole with a bright ring of trapped light and sparks orbiting it. */
public class TrapCoreRenderer implements BlockEntityRenderer<TrapCoreBlockEntity> {
    private static final float[] PALE = {0.75F, 0.92F, 1.0F}, WHITE = {1.0F, 0.98F, 0.95F};

    public TrapCoreRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TrapCoreBlockEntity core, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (core.getLevel() == null) return;
        float time = core.getLevel().getGameTime() % 24000 + partialTick;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        float r = 0.35F + 0.02F * Mth.sin(time * 0.2F);
        ReactorCoreRenderer.renderSphere(pose, buffers.getBuffer(RenderType.debugQuads()), r);
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.glow());
        pose.pushPose();
        ReactorCoreRenderer.faceCamera(pose);
        ReactorCoreRenderer.renderRing(pose, glow, r * 1.05F, r * 1.6F, WHITE, 0.95F, 0);
        ReactorCoreRenderer.renderRing(pose, glow, r * 1.6F, r * 4F, PALE, 0.35F, 0);
        pose.popPose();
        // Light caught in orbit: sparks circling on tilted paths.
        for (int k = 0; k < 10; k++) {
            float a = time * (0.12F + k * 0.01F) + k * 0.63F;
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(k * 37));
            pose.mulPose(Axis.ZP.rotationDegrees(k * 53));
            float d = 1.2F + 0.15F * k;
            ReactorCoreRenderer.renderFlare(pose, glow, new Vector3f(Mth.cos(a) * d, 0, Mth.sin(a) * d), 0.18F, PALE, 0.8F);
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(TrapCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).inflate(4);
    }
}
