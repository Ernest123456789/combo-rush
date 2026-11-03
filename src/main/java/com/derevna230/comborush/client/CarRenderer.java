package com.derevna230.comborush.client;

import com.derevna230.comborush.car.CarEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Рисует машину в натуральную величину и качает кузов на «пружинах»: клёвок при торможении, крен в повороте,
 * отскок при приземлении и плавный заезд на уступ. Наклоны считаются в системе самой машины, поэтому
 * сначала поворот в курс модели, потом наклон, потом обратный поворот — GeckoLib довернёт модель сам.
 */
public class CarRenderer extends GeoEntityRenderer<CarEntity> {
    /** Высота оси, вокруг которой качается кузов (в блоках от земли) */
    private static final float PIVOT_Y = 0.35f;

    public CarRenderer(EntityRendererFactory.Context ctx, String id) {
        super(ctx, new CarModel(id));
        this.shadowRadius = 1.6f;
    }

    @Override
    public void render(CarEntity entity, float entityYaw, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight) {
        float s = entity.def().scale();
        float bodyYaw = MathHelper.lerpAngleDegrees(partialTick, entity.prevBodyYaw, entity.bodyYaw);
        float theta = 180f - bodyYaw;                               // ровно тот поворот, что делает GeckoLib
        poseStack.push();
        poseStack.translate(0f, entity.suspY(partialTick), 0f);
        poseStack.translate(0f, PIVOT_Y, 0f);
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(theta));
        poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.pitchDeg(partialTick)));
        poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(entity.rollDeg(partialTick)));
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-theta));
        poseStack.translate(0f, -PIVOT_Y, 0f);
        poseStack.scale(s, s, s);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.pop();
    }
}
