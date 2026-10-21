package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.entity.DrifterEntity;
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
 * The Drifter: a translucent bell on a glowing rim, a dark core inside it, and eight two-segment tentacles hanging
 * in a ring under the rim. The bell pulses; the tentacles sway, each a little behind the one before.
 * (tools/models/drifter.bbmodel is the same model, for Blockbench: keep the two in step.)
 */
public class DrifterModel extends HierarchicalModel<DrifterEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "drifter"), "main");
    public static final int TENTACLES = 8;
    private static final float TENTACLE_RING = 4.5F;

    private final ModelPart root, bell;
    private final ModelPart[] upper = new ModelPart[TENTACLES], lower = new ModelPart[TENTACLES];

    public DrifterModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("body");
        this.bell = body.getChild("bell");
        for (int i = 0; i < TENTACLES; i++) {
            upper[i] = body.getChild("tentacle" + i);
            lower[i] = upper[i].getChild("tip");
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0, 14, 0));
        // The bell: a capped dome on a wider rim, with the core floating inside (all scaled together by the pulse).
        body.addOrReplaceChild("bell", CubeListBuilder.create()
                .texOffs(40, 0).addBox(-3, -10, -3, 6, 2, 6)
                .texOffs(0, 0).addBox(-5, -8, -5, 10, 5, 10)
                .texOffs(0, 15).addBox(-6, -3, -6, 12, 3, 12)
                .texOffs(0, 30).addBox(-2, -6, -2, 4, 4, 4), PartPose.ZERO);
        for (int i = 0; i < TENTACLES; i++) {
            float angle = Mth.TWO_PI * i / TENTACLES;
            PartDefinition tentacle = body.addOrReplaceChild("tentacle" + i, CubeListBuilder.create()
                    .texOffs(16, 30).addBox(-0.5F, 0, -0.5F, 1, 5, 1),
                    PartPose.offset(Mth.cos(angle) * TENTACLE_RING, 0, Mth.sin(angle) * TENTACLE_RING));
            tentacle.addOrReplaceChild("tip", CubeListBuilder.create()
                    .texOffs(20, 30).addBox(-0.5F, 0, -0.5F, 1, 4, 1), PartPose.offset(0, 5, 0));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(DrifterEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // The bell squeezes and relaxes, like a jellyfish swimming.
        float pulse = Mth.sin(ageInTicks * 0.15F);
        bell.xScale = bell.zScale = 1 + 0.06F * pulse;
        bell.yScale = 1 - 0.05F * pulse;
        // Each tentacle sways, a little out of step with its neighbors; the tips trail behind.
        for (int i = 0; i < TENTACLES; i++) {
            float phase = ageInTicks * 0.12F + i * 0.8F;
            upper[i].xRot = 0.25F * Mth.sin(phase);
            upper[i].zRot = 0.25F * Mth.cos(phase * 0.9F);
            lower[i].xRot = 0.35F * Mth.sin(phase - 0.8F);
            lower[i].zRot = 0.3F * Mth.cos(phase * 0.9F - 0.8F);
        }
    }
}
