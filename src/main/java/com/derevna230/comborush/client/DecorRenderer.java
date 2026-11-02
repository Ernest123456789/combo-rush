package com.derevna230.comborush.client;

import com.derevna230.comborush.pet.DecorEntity;
import com.derevna230.comborush.pet.DecorSizes;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Рисует декоративное растение по модели GeckoLib */
public class DecorRenderer extends GeoEntityRenderer<DecorEntity> {
    public DecorRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new DecorModel());
        this.shadowRadius = 0.3f;
    }

    /** Крупные модели (голем, машины) уменьшены до размера статуэтки */
    @Override
    public void render(DecorEntity entity, float entityYaw, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight) {
        float s = DecorSizes.of(entity.decorId()).scale();
        poseStack.push();
        poseStack.scale(s, s, s);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.pop();
    }
}
