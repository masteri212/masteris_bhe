package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.entity.SpaghettifierEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Spaghettifier: a head with two whiskers and a chain of six segments thinning to a tail fin, each pivoting
 * at its front end. A wave runs down the chain, so the body swims behind the head; faster while it drags someone.
 * (tools/models/spaghettifier.bbmodel is the same model, for Blockbench.)
 */
public class SpaghettifierModel extends HierarchicalModel<SpaghettifierEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "spaghettifier"), "main");
    private static final int SEGMENTS = 6;
    /** Segment sizes and lengths, matching tools/models/spaghettifier.py. */
    private static final int[][] SIZES = {{4, 5, 0, 10}, {4, 5, 0, 10}, {3, 5, 0, 20}, {3, 5, 0, 20}, {2, 4, 0, 30}, {2, 4, 0, 30}};

    private final ModelPart root, head;
    private final ModelPart[] segments = new ModelPart[SEGMENTS];
    private final ModelPart fin;

    public SpaghettifierModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        ModelPart part = head;
        for (int i = 0; i < SEGMENTS; i++) {
            part = part.getChild("segment" + i);
            segments[i] = part;
        }
        this.fin = part.getChild("fin");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition head = mesh.getRoot().addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.5F, -2.5F, -5, 5, 5, 5)
                .texOffs(32, 0).addBox(-3.5F, 0.5F, -5, 1, 1, 4)
                .texOffs(32, 0).mirror().addBox(2.5F, 0.5F, -5, 1, 1, 4), PartPose.offset(0, 18, -14));
        PartDefinition part = head;
        float offset = 0;
        for (int i = 0; i < SEGMENTS; i++) {
            int size = SIZES[i][0], length = SIZES[i][1];
            part = part.addOrReplaceChild("segment" + i, CubeListBuilder.create()
                    .texOffs(SIZES[i][2], SIZES[i][3]).addBox(-size / 2F, -size / 2F, 0, size, size, length),
                    PartPose.offset(0, 0, offset));
            offset = length;
        }
        part.addOrReplaceChild("fin", CubeListBuilder.create()
                .texOffs(20, 0).addBox(-0.5F, -2.5F, 0, 1, 5, 4), PartPose.offset(0, 0, offset));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(SpaghettifierEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float speed = entity.isDragging() ? 0.7F : 0.3F;
        float sway = entity.isDragging() ? 0.45F : 0.32F;
        head.yRot = 0.15F * Mth.sin(ageInTicks * speed);
        head.xRot = headPitch * Mth.DEG_TO_RAD * 0.5F;
        for (int i = 0; i < SEGMENTS; i++) {
            float phase = ageInTicks * speed - (i + 1) * 0.75F;
            segments[i].yRot = sway * Mth.sin(phase);
            segments[i].xRot = 0.08F * Mth.sin(phase * 0.7F);
        }
        fin.yRot = 0.5F * Mth.sin(ageInTicks * speed - 7 * 0.75F);
    }
}
