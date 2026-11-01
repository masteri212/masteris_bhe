package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.lang.reflect.Method;

/**
 * Keeps the mod's glowing effects visible under Iris shader packs.
 *
 * Shader packs light everything that's drawn before their lighting pass from its lightmap and normals, and Iris
 * draws all entity and block entity rendering in that stage. The glows have neither (they're plain position + color
 * quads), so packs lit them as unlit black surfaces. With a shader pack on, the glows go into a buffer of their own
 * instead, with a full-bright lightmap, and are drawn after the lighting pass (after particles), where the pack adds
 * them to the finished image as they are. Without a shader pack, nothing changes.
 *
 * The Event Horizon's sky and the dark energy zones' walls are drawn here too under a shader pack (see
 * EventHorizonEffects#renderUnderShaders and ZoneBorderRenderer), for the same reason.
 *
 * Iris is an optional mod: it's only reached through its public API, by reflection.
 */
@EventBusSubscriber(modid = MasteriBHE.MODID, value = Dist.CLIENT)
public final class ShaderCompat {
    private static final MultiBufferSource.BufferSource LATE = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 18));
    private static boolean looked;
    private static Object api;
    private static Method inUse, shadowPass;
    /**
     * True from the start of each frame until the late buffer is drawn (after particles): the world is being drawn.
     * Glows drawn outside it (in GUIs, Ponder scenes and the like) take the plain path, so none wait in the late
     * buffer for a world render that has already passed.
     */
    private static boolean inWorld;

    /** True while an Iris shader pack is in use. */
    public static boolean shadersOn() {
        Object iris = iris();
        if (iris == null) return false;
        try {
            return (boolean) inUse.invoke(iris);
        } catch (ReflectiveOperationException | RuntimeException e) {
            api = null;
            return false;
        }
    }

    /** True while Iris renders the world again into its shadow map (glows cast no shadows). */
    private static boolean shadowPass() {
        Object iris = iris();
        if (iris == null) return false;
        try {
            return (boolean) shadowPass.invoke(iris);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static Object iris() {
        if (!looked) {
            looked = true;
            try {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = c.getMethod("getInstance").invoke(null);
                inUse = c.getMethod("isShaderPackInUse");
                shadowPass = c.getMethod("isRenderingShadowPass");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                api = null; // no Iris
            }
        }
        return api;
    }

    /** Where to draw glow quads this frame (see {@link ModRenderTypes#glow(MultiBufferSource)}). */
    static VertexConsumer glow(MultiBufferSource buffers) {
        if (!inWorld || !shadersOn()) return buffers.getBuffer(ModRenderTypes.glow());
        if (shadowPass()) return NOWHERE;
        return new FullBright(LATE.getBuffer(ModRenderTypes.glowLit()));
    }

    /**
     * Where to draw the dark energy zone's wall (translucent position + color quads, drawn after particles): the
     * plain translucent type, or with a shader pack on, a full-bright entity version packs draw as it is.
     */
    static VertexConsumer zoneWall(MultiBufferSource buffers, net.minecraft.client.renderer.RenderType plain) {
        if (!inWorld || !shadersOn()) return buffers.getBuffer(plain);
        if (shadowPass()) return NOWHERE;
        return new FullBright(LATE.getBuffer(ModRenderTypes.zoneWallLit()));
    }

    /** Draws the glows collected this frame, after the shader pack's lighting pass. Last, after everyone else's. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.effects() instanceof EventHorizonEffects sky && shadersOn() && !shadowPass()) {
                float time = event.getRenderTick() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
                sky.renderUnderShaders(time, mc.gameRenderer.getDepthFar());
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            LATE.endBatch();
            inWorld = false;
        }
    }

    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre event) {
        LATE.endBatch(); // anything left over from a world render that never reached its particles: draw and clear it
        inWorld = true;
    }

    /**
     * Takes position + color quads (what all the glow code writes) and fills in the rest of an entity vertex: the
     * middle of a white texture, no overlay, full-bright light and an up normal.
     */
    static final class FullBright implements VertexConsumer {
        private final VertexConsumer out;

        FullBright(VertexConsumer out) {
            this.out = out;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            out.addVertex(x, y, z);
            out.setUv(0.5F, 0.5F);
            out.setOverlay(OverlayTexture.NO_OVERLAY);
            out.setLight(LightTexture.FULL_BRIGHT);
            out.setNormal(0, 1, 0);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            out.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    /** Throws everything away (glows during the shadow pass). */
    private static final VertexConsumer NOWHERE = new VertexConsumer() {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    };

    private ShaderCompat() {}
}
