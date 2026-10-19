package com.masteri.bhe.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

public final class ModRenderTypes {
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

    public static RenderType glow() {
        return GLOW;
    }

    private ModRenderTypes() {}
}
