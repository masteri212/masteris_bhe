package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.entity.EchoOfTheCoreEntity;
import com.masteri.bhe.entity.EchoOfTheCoreEntity.Attack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The Echo of the Core: its spectral cage and pods (EchoModel), the black hole inside (swelling while it pulls), and
 * its attacks, drawn where the server applies them: the shockwave ring rolling along the ground, and the beam's
 * warning line, then the beam.
 */
public class EchoRenderer extends MobRenderer<EchoOfTheCoreEntity, EchoModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "textures/entity/echo_of_the_core.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "textures/entity/echo_of_the_core_glow.png");
    private static final float[] WHITE_VIOLET = {0.95F, 0.85F, 1.0F}, VIOLET = {0.6F, 0.3F, 1.0F}, RED = {1.0F, 0.25F, 0.2F},
            GOLD = {1.0F, 0.75F, 0.3F};

    public EchoRenderer(EntityRendererProvider.Context context) {
        super(context, new EchoModel(context.bakeLayer(EchoModel.LAYER)), 0);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(GLOW);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(EchoOfTheCoreEntity entity) {
        return TEXTURE;
    }

    @Nullable
    @Override
    protected RenderType getRenderType(EchoOfTheCoreEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(TEXTURE);
    }

    @Override
    public void render(EchoOfTheCoreEntity echo, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(echo, yaw, partialTick, pose, buffers, light);
        float time = echo.tickCount + partialTick;
        Attack attack = echo.getAttack();
        float tick = echo.getAttackTick() + (attack == Attack.NONE ? 0 : partialTick);
        float mid = echo.getBbHeight() / 2;

        // The black hole: bigger while it pulls, with its photon ring and a spinning disc.
        float swell = attack == Attack.PULL
                ? Mth.sin(Mth.PI * Mth.clamp(tick / EchoOfTheCoreEntity.PULL_TICKS, 0, 1)) : 0;
        float horizon = 0.3F + 0.6F * swell + 0.03F * Mth.sin(time * 0.3F);
        pose.pushPose();
        pose.translate(0, mid, 0);
        ReactorCoreRenderer.renderSphere(pose, buffers.getBuffer(RenderType.debugQuads()), horizon);
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.glow());
        pose.pushPose();
        ReactorCoreRenderer.faceCamera(pose);
        ReactorCoreRenderer.renderRing(pose, glow, horizon * 1.04F, horizon * 1.5F, WHITE_VIOLET, 0.9F, 0);
        ReactorCoreRenderer.renderRing(pose, glow, horizon * 1.5F, horizon * 2.6F, attack == Attack.PULL ? RED : VIOLET, 0.35F + 0.4F * swell, 0);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(time * (attack == Attack.PULL ? 18 : 4)));
        pose.mulPose(Axis.XP.rotationDegrees(90 + 15 * Mth.sin(time * 0.05F)));
        ReactorCoreRenderer.renderRing(pose, glow, horizon * 1.3F, horizon * 3.4F, GOLD, 0.45F + 0.3F * swell, 0);
        pose.popPose();
        pose.popPose();

        if (attack == Attack.SHOCKWAVE) renderShockwave(echo, tick, pose, buffers);
        if (attack == Attack.BEAM) renderBeam(echo, tick, partialTick, mid, time, pose, buffers);
    }

    /** The disc-stall wave: a ring on the ground below, at the radius the server hits at. */
    private static void renderShockwave(EchoOfTheCoreEntity echo, float tick, PoseStack pose, MultiBufferSource buffers) {
        float radius = tick * EchoOfTheCoreEntity.SHOCKWAVE_SPEED;
        float fade = 1 - Mth.clamp(tick / EchoOfTheCoreEntity.SHOCKWAVE_TICKS, 0, 1);
        int ground = echo.level().getHeight(Heightmap.Types.MOTION_BLOCKING, echo.getBlockX(), echo.getBlockZ());
        pose.pushPose();
        pose.translate(0, ground - echo.getY() + 0.15, 0);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.glow());
        ReactorCoreRenderer.renderRing(pose, glow, Math.max(0, radius - 0.6F), radius + 0.9F, GOLD, 0.9F * fade + 0.1F, 0);
        ReactorCoreRenderer.renderRing(pose, glow, radius + 0.9F, radius + 2.2F, WHITE_VIOLET, 0.35F * fade, 0);
        pose.popPose();
    }

    /** The stabilizer beam: a thin flickering warning line while it charges, then the beam itself. */
    private static void renderBeam(EchoOfTheCoreEntity echo, float tick, float partialTick, float mid, float time,
                                   PoseStack pose, MultiBufferSource buffers) {
        Vec3 origin = echo.getPosition(partialTick);
        Vec3 from = new Vec3(0, mid, 0);
        Vec3 dir = echo.getBeamTarget().subtract(origin.add(from));
        double length = dir.length();
        if (length < 0.1) return;
        dir = dir.scale(1 / length);
        Vec3 to = from.add(dir.scale(Math.max(length + 12, 24)));
        Vector3f a = new Vector3f((float) from.x, (float) from.y, (float) from.z), b = new Vector3f((float) to.x, (float) to.y, (float) to.z);
        Vector3f side = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).cross(0, 1, 0);
        if (side.lengthSquared() < 1e-4) side.set(1, 0, 0);
        side.normalize();
        Vector3f up = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).cross(side).normalize();
        VertexConsumer glow = buffers.getBuffer(ModRenderTypes.glow());
        PoseStack.Pose p = pose.last();
        if (tick < EchoOfTheCoreEntity.BEAM_CHARGE) {
            float flicker = 0.5F + 0.5F * Mth.sin(time * (1.5F + tick * 0.1F));
            int color = ReactorCoreRenderer.argb(0.4F + 0.5F * flicker, RED[0], RED[1], RED[2]);
            for (Vector3f axis : new Vector3f[] {side, up}) ReactorCoreRenderer.beamPlane(p, glow, a, b, axis, 0.08F, color);
        } else {
            float fire = 1 - Mth.clamp((tick - EchoOfTheCoreEntity.BEAM_CHARGE) / EchoOfTheCoreEntity.BEAM_FIRE, 0, 1);
            int core = ReactorCoreRenderer.argb(fire, 0.95F, 0.85F, 1.0F);
            int outer = ReactorCoreRenderer.argb(fire * 0.35F, VIOLET[0], VIOLET[1], VIOLET[2]);
            for (Vector3f axis : new Vector3f[] {side, up}) {
                ReactorCoreRenderer.beamPlane(p, glow, a, b, axis, 0.9F, core);
                ReactorCoreRenderer.beamPlane(p, glow, a, b, axis, 2.8F, outer);
            }
        }
    }
}
