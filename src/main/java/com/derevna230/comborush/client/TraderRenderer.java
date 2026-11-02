package com.derevna230.comborush.client;

import com.derevna230.comborush.boss.GolemTraderEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Модель торговца уменьшена в 1,0 → рост ≈ жителя */
public class TraderRenderer extends GeoEntityRenderer<GolemTraderEntity> {
    private static final float SCALE = 0.905f;

    public TraderRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new TraderModel());
        this.shadowRadius = 0.4f;
    }

    @Override
    public void render(GolemTraderEntity entity, float entityYaw, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight) {
        poseStack.push();
        poseStack.scale(SCALE, SCALE, SCALE);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.pop();
    }
}
