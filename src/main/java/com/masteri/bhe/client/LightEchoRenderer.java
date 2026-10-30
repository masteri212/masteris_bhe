package com.masteri.bhe.client;

import com.masteri.bhe.entity.LightEchoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.UUID;

/**
 * A Light Trap's echo: its player's own skin, see-through and washed pale blue, walking where they walked five
 * seconds ago. Hidden until it has caught up.
 */
public class LightEchoRenderer extends EntityRenderer<LightEchoEntity> {
    private static final int TINT = 0x90A8E8FF; // ARGB: see-through, pale blue
    private final PlayerModel<AbstractClientPlayer> wide, slim;

    public LightEchoRenderer(EntityRendererProvider.Context context) {
        super(context);
        wide = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        shadowRadius = 0;
    }

    private static PlayerSkin skin(LightEchoEntity echo) {
        UUID owner = echo.getOwner();
        if (owner == null) return DefaultPlayerSkin.get(new UUID(0, 0));
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner);
    }

    @Override
    public void render(LightEchoEntity echo, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!echo.isShown()) return;
        PlayerSkin skin = skin(echo);
        PlayerModel<AbstractClientPlayer> model = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
        float bodyYaw = Mth.rotLerp(partialTick, echo.yRotO, echo.getYRot());
        float pitch = Mth.lerp(partialTick, echo.xRotO, echo.getXRot());
        float walked = Mth.lerp(partialTick, echo.walkedBefore, echo.walked);
        float swing = Mth.clamp((echo.walked - echo.walkedBefore) * 4, 0, 1);
        // Pose it by hand: it isn't a living entity, so the model's own animation can't be used.
        float legs = Mth.cos(walked * 2.2F) * 1.2F * swing;
        model.head.xRot = pitch * Mth.DEG_TO_RAD;
        model.head.yRot = 0;
        model.rightLeg.xRot = legs;
        model.leftLeg.xRot = -legs;
        model.rightArm.xRot = -legs * 0.8F;
        model.leftArm.xRot = legs * 0.8F;
        model.hat.copyFrom(model.head);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.jacket.copyFrom(model.body);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - bodyYaw));
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501F, 0);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skin.texture())), 0xF000F0, OverlayTexture.NO_OVERLAY, TINT);
        pose.popPose();
        super.render(echo, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(LightEchoEntity echo) {
        return skin(echo).texture();
    }
}
