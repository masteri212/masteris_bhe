package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class ModRenderTypes {
    /** A plain white texture, for the shader-pack versions below (packs multiply the vertex color by a texture). */
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "textures/misc/white.png");

    /**
     * Additive glow, like vanilla's lightning but without writing depth. Lightning writes depth as if it were
     * solid, so water (drawn after block entities) vanished behind the reactor's glow, and overlapping glows (a beam
     * crossing the disc or the halo) punched see-through holes in each other. Additive light doesn't care about draw
     * order, so it needs no depth of its own; it's still hidden behind anything solid.
     */
    private static final RenderType GLOW = RenderType.create("masteris_bhe_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setOutputState(RenderStateShard.WEATHER_TARGET)
                    .createCompositeState(false));

    /**
     * The same glow for shader packs (see ShaderCompat): a full entity vertex (texture, light, normal) through the
     * glowing-eyes shader, which Iris treats as full-bright and blends additively.
     */
    private static final RenderType GLOW_LIT = glowLit("masteris_bhe_glow_lit", RenderStateShard.CULL);
    /** The shader-pack glow without back-face culling, for the Event Horizon's sky. */
    private static final RenderType SKY_GLOW = glowLit("masteris_bhe_sky_glow", RenderStateShard.NO_CULL);
    /** Solid black without depth, for the black hole's shadow in the Event Horizon's sky under a shader pack. */
    private static final RenderType SKY_SHADOW = RenderType.create("masteris_bhe_sky_shadow", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_SOLID_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(WHITE, false, false))
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private static RenderType glowLit(String name, RenderStateShard.CullStateShard cull) {
        return RenderType.create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(WHITE, false, false))
                        .setCullState(cull)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                        .createCompositeState(false));
    }

    /** The glow render type without a shader pack. Draw through {@link #glow(MultiBufferSource)}. */
    public static RenderType glow() {
        return GLOW;
    }

    /**
     * Where to draw additive glow quads (position + color): the glow render type, or with a shader pack on, a buffer
     * drawn after the pack's lighting (see ShaderCompat).
     */
    public static VertexConsumer glow(MultiBufferSource buffers) {
        return ShaderCompat.glow(buffers);
    }

    static RenderType glowLit() {
        return GLOW_LIT;
    }

    static RenderType skyGlow() {
        return SKY_GLOW;
    }

    static RenderType skyShadow() {
        return SKY_SHADOW;
    }

    private ModRenderTypes() {}
}
