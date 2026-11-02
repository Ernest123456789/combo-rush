package com.derevna230.comborush.client;

import com.derevna230.comborush.car.CarEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Рисует машину в натуральную величину (модель в файле — около 6 блоков, уменьшена до ~4) */
public class CarRenderer extends GeoEntityRenderer<CarEntity> {
    public CarRenderer(EntityRendererFactory.Context ctx, String id) {
        super(ctx, new CarModel(id));
        this.shadowRadius = 1.1f;
    }

    @Override
    public void render(CarEntity entity, float entityYaw, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight) {
        float s = entity.def().scale();
        poseStack.push();
        poseStack.scale(s, s, s);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.pop();
    }
}
