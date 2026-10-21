package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.entity.LensingStalkerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * The Lensing Stalker: a 3-block-tall, thin figure (long limbs, a blank head) with a square ring of bent light
 * turning around its head. {@link #alpha} fades the whole figure: the renderer sets it per frame from how directly
 * the viewer is looking at it. (tools/models/stalker.bbmodel is the same model, for Blockbench.)
 */
public class StalkerModel extends HierarchicalModel<LensingStalkerEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "lensing_stalker"), "main");

    private final ModelPart root, head, halo, rightArm, leftArm, rightLeg, leftLeg;
    /** 0..1, how solid it's drawn this frame. */
    float alpha = 1;

    public StalkerModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.halo = head.getChild("halo");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8), PartPose.offset(0, -18, 0));
        // The lensing ring: four thin bars in a square around the head.
        head.addOrReplaceChild("halo", CubeListBuilder.create()
                .texOffs(16, 16).addBox(-6, -0.5F, -6, 12, 1, 1)
                .texOffs(16, 16).addBox(-6, -0.5F, 5, 12, 1, 1)
                .texOffs(16, 18).addBox(-6, -0.5F, -5, 1, 1, 10)
                .texOffs(16, 18).addBox(5, -0.5F, -5, 1, 1, 10), PartPose.offset(0, -6, 0)); // around the crown, above the eyes
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-4, 0, -2, 8, 12, 4), PartPose.offset(0, -18, 0));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-1, -2, -1, 2, 28, 2), PartPose.offset(-5, -16, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(0, 16).mirror().addBox(-1, -2, -1, 2, 28, 2), PartPose.offset(5, -16, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(8, 16).addBox(-1, 0, -1, 2, 30, 2), PartPose.offset(-2, -6, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(8, 16).mirror().addBox(-1, 0, -1, 2, 30, 2), PartPose.offset(2, -6, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(LensingStalkerEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        // The ring turns slowly, tilted, as if the light around it were orbiting.
        halo.yRot = ageInTicks * 0.04F;
        halo.xRot = 0.25F + 0.05F * Mth.sin(ageInTicks * 0.05F);
        // A long-limbed walk; frozen mid-step while it's watched.
        float swing = entity.isWatched() ? 0 : Mth.cos(limbSwing * 0.5F) * 0.7F * limbSwingAmount;
        rightLeg.xRot = swing;
        leftLeg.xRot = -swing;
        rightArm.xRot = -swing * 0.8F;
        leftArm.xRot = swing * 0.8F;
        rightArm.zRot = 0.05F;
        leftArm.zRot = -0.05F;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        int a = (int) (FastColor.ARGB32.alpha(color) * Mth.clamp(alpha, 0, 1));
        super.renderToBuffer(pose, buffer, packedLight, packedOverlay, FastColor.ARGB32.color(a, color));
    }
}
