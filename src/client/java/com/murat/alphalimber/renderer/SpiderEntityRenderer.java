package com.murat.alphalimber.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.murat.alphalimber.entity.SpiderEntity;
import com.murat.alphalimber.model.ArmSegmentModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public class SpiderEntityRenderer extends EntityRenderer<SpiderEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("limber", "textures/entity/arm.png");
    private static final float MODEL_HEIGHT = 11.0f / 16.0f;
    private static final float TAPER_FACTOR = 0.3f;

    private final ArmSegmentModel segmentModel;

    public SpiderEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.segmentModel = new ArmSegmentModel(context.bakeLayer(ArmEntityRenderer.ARM_SEGMENT_LAYER));
    }

    @Override
    public boolean shouldRender(SpiderEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(SpiderEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(
            SpiderEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        Vec3[][] legs = entity.getLegs();
        Vec3[][] prevLegs = entity.getPreviousLegs();
        if (legs == null || prevLegs == null) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        for (int leg = 0; leg < legs.length; leg++) {
            Vec3[] joints = legs[leg];
            Vec3[] prevJoints = prevLegs[leg];
            if (joints == null || prevJoints == null || joints.length < 2) continue;

            for (int i = 0; i < joints.length - 1; i++) {
                Vec3 start = prevJoints[i].lerp(joints[i], partialTick);
                Vec3 end = prevJoints[i + 1].lerp(joints[i + 1], partialTick);

                double dx = end.x - start.x;
                double dy = end.y - start.y;
                double dz = end.z - start.z;

                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length < 1e-6) continue;

                double horizontalDist = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) Math.atan2(dx, dz);
                float pitch = (float) Math.atan2(dy, horizontalDist);

                poseStack.pushPose();

                poseStack.translate(
                        start.x - entity.getX(),
                        start.y - entity.getY(),
                        start.z - entity.getZ()
                );

                poseStack.mulPose(Axis.YP.rotation(yaw + (float) Math.PI));
                poseStack.mulPose(Axis.XP.rotation(pitch));
                poseStack.mulPose(Axis.XP.rotationDegrees(90));

                float taper = 1.0f - (i * TAPER_FACTOR);
                poseStack.scale(taper, (float) (length / MODEL_HEIGHT), taper);

                segmentModel.renderToBuffer(
                        poseStack,
                        buffer,
                        packedLight,
                        OverlayTexture.NO_OVERLAY,
                        1f, 1f, 1f, 1f
                );

                poseStack.popPose();
            }
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
