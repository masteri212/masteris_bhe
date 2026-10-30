package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.block.Assemblable;
import com.masteri.bhe.block.entity.ReactorCoreBlockEntity;
import com.masteri.bhe.multiblock.ReactorStructure;
import com.masteri.bhe.reactor.VentingLance;
import com.masteri.bhe.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

import java.util.List;
import java.util.Set;

/**
 * Draws the reactor's assembled structures (see ReactorAssembly) as one model each, in place of their blocks: the
 * ring in 16 slices (5 of them with a beam port housing), each stabilizer, each axial stabilizer and the lance.
 * The models are OBJs in models/formed/. Each piece is lit by the light around it, and shaded by its surfaces'
 * facing the way blocks are (drawn like this, models otherwise come out flat).
 */
public final class AssembledStructures {
    public static final ModelResourceLocation RING = model("ring_segment"), RING_PORT = model("ring_port"),
            STABILIZER = model("stabilizer"), AXIAL = model("axial_stabilizer"), LANCE = model("lance");
    private static final List<ModelResourceLocation> ALL = List.of(RING, RING_PORT, STABILIZER, AXIAL, LANCE);
    private static final int RING_SLICES = 16;
    /** Slices with a beam port: east, south, west, north and north-east (slice k is at k * 22.5 degrees from east). */
    private static final Set<Integer> PORT_SLICES = Set.of(0, 4, 8, 12, 14);
    private static final RandomSource RANDOM = RandomSource.create();
    private static final float[] SHADES = new float[4];
    private static final int[] LIGHTS = new int[4];

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "formed/" + name));
    }

    public static void register(ModelEvent.RegisterAdditional event) {
        ALL.forEach(event::register);
    }

    public static void render(ReactorCoreBlockEntity core, PoseStack pose, MultiBufferSource buffers) {
        Level level = core.getLevel();
        if (level == null) return;
        BlockPos c = core.getBlockPos();
        VertexConsumer out = buffers.getBuffer(RenderType.cutout());
        ModelManager models = Minecraft.getInstance().getModelManager();
        for (ReactorStructure.Section section : ReactorStructure.sections()) {
            if (!Assemblable.isAssembled(level.getBlockState(c.offset(section.anchor())))) continue;
            switch (section.kind()) {
                case RING -> {
                    for (int k = 0; k < RING_SLICES; k++) {
                        double phi = Math.toRadians(k * 360.0 / RING_SLICES);
                        double cos = Math.cos(phi), sin = Math.sin(phi);
                        int r = ReactorStructure.RING_MAJOR_RADIUS;
                        int light = light(level, c.offset((int) Math.round(cos * r), 3, (int) Math.round(sin * r)),
                                c.offset((int) Math.round(cos * (r - 4)), 0, (int) Math.round(sin * (r - 4))),
                                c.offset((int) Math.round(cos * (r + 4)), 0, (int) Math.round(sin * (r + 4))));
                        draw(pose, out, models.getModel(PORT_SLICES.contains(k) ? RING_PORT : RING), 0.5, 0, 0.5, k * 360F / RING_SLICES, light);
                    }
                }
                case STABILIZER -> {
                    Direction dir = section.facing();
                    BlockPos mid = c.relative(dir, ReactorStructure.STABILIZER_DISTANCE);
                    int light = light(level, mid.above(3), mid.relative(dir.getClockWise(), 3), mid.relative(dir.getCounterClockWise(), 3));
                    draw(pose, out, models.getModel(STABILIZER), 0.5, 0, 0.5, angle(dir), light);
                }
                case AXIAL -> {
                    BlockPos center = c.offset(section.anchor()).above();
                    int light = light(level, center.above(2), center.below(2), center.east(2), center.west(2));
                    BlockPos o = section.anchor().above();
                    draw(pose, out, models.getModel(AXIAL), o.getX() + 0.5, o.getY() + 0.5, o.getZ() + 0.5, 0, light);
                }
            }
        }
        int bottom = assembledLanceBottom(level, c);
        if (bottom >= 0) {
            BlockPos mid = c.above(bottom + VentingLance.HEIGHT / 2);
            int light = light(level, mid.east(2), mid.west(2), mid.north(2), mid.south(2), c.above(bottom + VentingLance.HEIGHT));
            draw(pose, out, models.getModel(LANCE), 0.5, bottom, 0.5, 0, light);
        }
    }

    /** Height of the assembled lance's bottom over the core, or -1. */
    private static int assembledLanceBottom(Level level, BlockPos core) {
        for (int dy = VentingLance.MIN_BOTTOM; dy <= VentingLance.MAX_BOTTOM; dy++) {
            BlockState state = level.getBlockState(core.above(dy));
            if (state.is(ModBlocks.LANCE_EMITTER.get())) return Assemblable.isAssembled(state) ? dy : -1;
        }
        return -1;
    }

    /** Rotation that turns a model built facing east to face {@code dir}. */
    private static float angle(Direction dir) {
        return switch (dir) {
            case SOUTH -> 90;
            case WEST -> 180;
            case NORTH -> 270;
            default -> 0;
        };
    }

    /** The brightest block and sky light among the sample spots (some may be inside walls or the ground). */
    private static int light(Level level, BlockPos... at) {
        int block = 0, sky = 0;
        for (BlockPos pos : at) {
            int l = LevelRenderer.getLightColor(level, pos);
            block = Math.max(block, LightTexture.block(l));
            sky = Math.max(sky, LightTexture.sky(l));
        }
        return LightTexture.pack(block, sky);
    }

    /**
     * Draws a model at (x, y, z) from the core block's corner, turned {@code degrees} clockwise seen from above
     * (east to south). Quads that glow carry their own light (the OBJ material's ambient), kept by putBulkData.
     */
    private static void draw(PoseStack pose, VertexConsumer out, BakedModel model, double x, double y, double z, float degrees, int light) {
        if (model == Minecraft.getInstance().getModelManager().getMissingModel()) return;
        pose.pushPose();
        pose.translate(x, y, z);
        if (degrees != 0) pose.mulPose(Axis.YP.rotationDegrees(-degrees));
        PoseStack.Pose last = pose.last();
        Vector3f n = new Vector3f();
        for (BakedQuad quad : model.getQuads(null, null, RANDOM, ModelData.EMPTY, null)) {
            int[] v = quad.getVertices();
            int stride = v.length / 4;
            for (int i = 0; i < 4; i++) {
                if (quad.isShade()) {
                    int packed = v[i * stride + 7];
                    n.set((byte) packed / 127F, (byte) (packed >> 8) / 127F, (byte) (packed >> 16) / 127F);
                    last.normal().transform(n);
                    SHADES[i] = shade(n);
                } else {
                    SHADES[i] = 1F;
                }
                LIGHTS[i] = light;
            }
            out.putBulkData(last, quad, SHADES, 1F, 1F, 1F, 1F, LIGHTS, OverlayTexture.NO_OVERLAY, true);
        }
        pose.popPose();
    }

    /** Block-style shading for any facing: top 1.0, bottom 0.5, north/south 0.8, east/west 0.6, blended. */
    private static float shade(Vector3f n) {
        float x = n.x() * n.x(), y = n.y() * n.y(), z = n.z() * n.z();
        float sum = x + y + z;
        if (sum < 1e-4F) return 1F;
        return (x * 0.6F + z * 0.8F + y * (n.y() > 0 ? 1.0F : 0.5F)) / sum;
    }

    private AssembledStructures() {}
}
