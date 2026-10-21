package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.entity.LensingStalkerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Lensing Stalker, seen only out of the corner of your eye: the nearer the middle of your view it is, the more it
 * fades, until looking straight at it there's almost nothing there. Its ring and eyes glow (as faded as the rest).
 */
public class StalkerRenderer extends MobRenderer<LensingStalkerEntity, StalkerModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "textures/entity/lensing_stalker.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "textures/entity/lensing_stalker_glow.png");
    /** Fully faded inside this cone around the view's center (cosine of ~14 degrees), fully visible outside ~40. */
    private static final double GONE_DOT = 0.97, SEEN_DOT = 0.77;
    private static final float MIN_ALPHA = 0.04F, MAX_ALPHA = 0.85F;

    public StalkerRenderer(EntityRendererProvider.Context context) {
        super(context, new StalkerModel(context.bakeLayer(StalkerModel.LAYER)), 0);
        addLayer(new GlowLayer(this));
    }

    @Override
    public void render(LensingStalkerEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        model.alpha = visibility(entity, partialTick);
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /** How solid to draw it for this viewer: faint near the center of their view, clearer toward the edges. */
    private static float visibility(LensingStalkerEntity entity, float partialTick) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 middle = entity.getPosition(partialTick).add(0, entity.getBbHeight() * 0.6, 0);
        Vec3 toIt = middle.subtract(camera.getPosition());
        if (toIt.lengthSqr() < 1e-4) return MIN_ALPHA;
        double dot = new Vec3(camera.getLookVector()).dot(toIt.normalize());
        float seen = (float) Mth.clamp((GONE_DOT - dot) / (GONE_DOT - SEEN_DOT), 0, 1);
        return Mth.lerp(seen * seen, MIN_ALPHA, MAX_ALPHA);
    }

    @Override
    public ResourceLocation getTextureLocation(LensingStalkerEntity entity) {
        return TEXTURE;
    }

    @Nullable
    @Override
    protected RenderType getRenderType(LensingStalkerEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(TEXTURE);
    }

    /** The ring and eyes, full-bright and additive, dimmed by the same fade as the body. */
    private static final class GlowLayer extends RenderLayer<LensingStalkerEntity, StalkerModel> {
        GlowLayer(RenderLayerParent<LensingStalkerEntity, StalkerModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, LensingStalkerEntity entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            StalkerModel model = getParentModel();
            float alpha = model.alpha;
            model.alpha = 1; // additive: fade by darkening the color instead
            int c = (int) (255 * Mth.clamp(alpha / MAX_ALPHA, 0, 1));
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), 0xF00000, OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.color(255, c, c, c));
            model.alpha = alpha;
        }
    }
}
