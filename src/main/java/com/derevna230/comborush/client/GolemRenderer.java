package com.derevna230.comborush.client;

import com.derevna230.comborush.boss.RedstoneGolemEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Босс крупнее исходной модели в 1,25 раза; трещины и глаза светятся (*_glowmask.png) */
public class GolemRenderer extends GeoEntityRenderer<RedstoneGolemEntity> {
    private static final float SCALE = 1.25f;

    public GolemRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new GolemModel());
        this.shadowRadius = 2.2f;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(RedstoneGolemEntity entity, float entityYaw, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight) {
        poseStack.push();
        poseStack.scale(SCALE, SCALE, SCALE);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.pop();
    }
}
