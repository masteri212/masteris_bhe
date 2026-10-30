package com.masteri.bhe.client;

import com.masteri.bhe.entity.DebrisPlatformEntity;
import com.masteri.bhe.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A Debris Ring platform, drawn as real blocks: a flat top (what you stand on) with ragged rubble hanging under it,
 * the same shape every time for the same platform. Positioned from its orbit at the exact frame time, so it
 * glides.
 */
public class DebrisPlatformRenderer extends EntityRenderer<DebrisPlatformEntity> {
    private final BlockRenderDispatcher blocks;

    public DebrisPlatformRenderer(EntityRendererProvider.Context context) {
        super(context);
        blocks = context.getBlockRenderDispatcher();
        shadowRadius = 0;
    }

    @Override
    public void render(DebrisPlatformEntity platform, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        // Draw it where its orbit says, not where the last tick left it.
        Vec3 smooth = platform.orbitAt(platform.level().getGameTime() - 1 + partialTick);
        Vec3 drawn = platform.getPosition(partialTick);
        int w = platform.getWidth(), style = platform.getStyle();
        BlockState top = switch (Math.floorMod(style, 3)) {
            case 0 -> ModBlocks.CORRUPTED_STONE.get().defaultBlockState();
            case 1 -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            default -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        };
        BlockState under = Blocks.DEEPSLATE.defaultBlockState();
        pose.pushPose();
        pose.translate(smooth.x - drawn.x - w / 2.0, smooth.y - drawn.y, smooth.z - drawn.z - w / 2.0);
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < w; z++) {
                block(pose, buffers, light, top, x, 0, z);
                int hang = hang(style, x, z, w);
                for (int y = 1; y <= hang; y++) block(pose, buffers, light, y == hang && hash(style, x, z) % 3 == 0 ? Blocks.TUFF.defaultBlockState() : under, x, -y, z);
            }
        }
        pose.popPose();
        super.render(platform, yaw, partialTick, pose, buffers, light);
    }

    /** How many blocks of rubble hang under each column: most under the middle, ragged. */
    private static int hang(int style, int x, int z, int w) {
        double cx = x - (w - 1) / 2.0, cz = z - (w - 1) / 2.0;
        double edge = Math.max(Math.abs(cx), Math.abs(cz)) / Math.max(1, (w - 1) / 2.0);
        return (int) Math.max(0, Math.round((1 - edge) * 2.5 + (hash(style, x, z) % 3) - 1));
    }

    private static int hash(int style, int x, int z) {
        int h = style * 73856093 ^ x * 19349663 ^ z * 83492791;
        return Math.abs(h ^ (h >>> 13));
    }

    private void block(PoseStack pose, MultiBufferSource buffers, int light, BlockState state, int x, int y, int z) {
        pose.pushPose();
        pose.translate(x, y, z);
        blocks.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(DebrisPlatformEntity platform) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
