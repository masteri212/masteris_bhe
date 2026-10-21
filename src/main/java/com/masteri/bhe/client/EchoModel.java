package com.masteri.bhe.client;

import com.masteri.bhe.MasteriBHE;
import com.masteri.bhe.entity.EchoOfTheCoreEntity;
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
 * The Echo of the Core: a spectral 12-bar cage (the reactor core's frame) with four stabilizer pods orbiting it, each
 * with a lens turned toward the middle. The black hole inside is drawn by EchoRenderer.
 * (tools/models/echo_of_the_core.bbmodel is the same model, for Blockbench.)
 */
public class EchoModel extends HierarchicalModel<EchoOfTheCoreEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MasteriBHE.MODID, "echo_of_the_core"), "main");
    public static final int PODS = 4;
    private static final float POD_RING = 14;

    private final ModelPart root, cage, pods;
    private final ModelPart[] pod = new ModelPart[PODS];

    public EchoModel(ModelPart root) {
        this.root = root;
        this.cage = root.getChild("cage");
        this.pods = root.getChild("pods");
        for (int i = 0; i < PODS; i++) pod[i] = pods.getChild("pod" + i);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // The cage: 4 posts, then 4 bars along x and 4 along z, top and bottom.
        CubeListBuilder cage = CubeListBuilder.create();
        for (int x : new int[] {-6, 5}) {
            for (int z : new int[] {-6, 5}) cage.texOffs(0, 0).addBox(x, -6, z, 1, 12, 1);
        }
        for (int y : new int[] {-6, 5}) {
            for (int z : new int[] {-6, 5}) cage.texOffs(4, 0).addBox(-5, y, z, 10, 1, 1);
            for (int x : new int[] {-6, 5}) cage.texOffs(4, 2).addBox(x, y, -5, 1, 1, 10);
        }
        root.addOrReplaceChild("cage", cage, PartPose.offset(0, 8, 0));
        PartDefinition pods = root.addOrReplaceChild("pods", CubeListBuilder.create(), PartPose.offset(0, 8, 0));
        for (int i = 0; i < PODS; i++) {
            float angle = Mth.TWO_PI * i / PODS;
            // Turned so its lens (on the -z face) points at the middle.
            pods.addOrReplaceChild("pod" + i, CubeListBuilder.create()
                    .texOffs(0, 16).addBox(-2, -2, -3, 4, 4, 6)
                    .texOffs(20, 16).addBox(-1, -1, -4, 2, 2, 1),
                    PartPose.offsetAndRotation(Mth.cos(angle) * POD_RING, 0, Mth.sin(angle) * POD_RING, 0, Mth.HALF_PI - angle, 0));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(EchoOfTheCoreEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        EchoOfTheCoreEntity.Attack attack = entity.getAttack();
        // The pods orbit, much faster while it's pulling; the cage turns slowly the other way.
        float spin = attack == EchoOfTheCoreEntity.Attack.PULL ? 0.25F : 0.04F;
        pods.yRot = ageInTicks * spin;
        cage.yRot = -ageInTicks * 0.01F;
        cage.xRot = 0.1F * Mth.sin(ageInTicks * 0.03F);
        for (int i = 0; i < PODS; i++) {
            pod[i].y = 2.5F * Mth.sin(ageInTicks * 0.08F + i * Mth.HALF_PI);
        }
    }
}
