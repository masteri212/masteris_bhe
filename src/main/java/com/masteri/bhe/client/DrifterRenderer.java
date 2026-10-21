package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.entity.DrifterEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Draws the Drifter see-through, with its rim, core and tentacle tips glowing at full brightness. */
public class DrifterRenderer extends MobRenderer<DrifterEntity, DrifterModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "textures/entity/drifter.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "textures/entity/drifter_glow.png");

    public DrifterRenderer(EntityRendererProvider.Context context) {
        super(context, new DrifterModel(context.bakeLayer(DrifterModel.LAYER)), 0.3F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(GLOW);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(DrifterEntity entity) {
        return TEXTURE;
    }

    @Nullable
    @Override
    protected RenderType getRenderType(DrifterEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return bodyVisible ? RenderType.entityTranslucent(TEXTURE) : super.getRenderType(entity, false, translucent, glowing);
    }
}
