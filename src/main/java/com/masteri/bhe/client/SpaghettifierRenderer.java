package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.entity.SpaghettifierEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** The Spaghettifier, with its eyes, mouth and streaks glowing at full brightness. */
public class SpaghettifierRenderer extends MobRenderer<SpaghettifierEntity, SpaghettifierModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "textures/entity/spaghettifier.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "textures/entity/spaghettifier_glow.png");

    public SpaghettifierRenderer(EntityRendererProvider.Context context) {
        super(context, new SpaghettifierModel(context.bakeLayer(SpaghettifierModel.LAYER)), 0);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(GLOW);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(SpaghettifierEntity entity) {
        return TEXTURE;
    }
}
