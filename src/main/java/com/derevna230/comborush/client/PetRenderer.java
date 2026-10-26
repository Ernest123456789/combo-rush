package com.derevna230.comborush.client;

import com.derevna230.comborush.pet.CRPetEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Рисует питомца по его модели GeckoLib; крупные модели уменьшены до размера спутника */
public class PetRenderer extends GeoEntityRenderer<CRPetEntity> {
    public PetRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PetModel());
        this.shadowRadius = 0.35f;
    }

    private static float scaleOf(String id) {
        return switch (id) {
            case "sailor_crab" -> 0.55f;
            case "creeper_skeleton" -> 0.75f;
            case "traveler_skeleton" -> 0.8f;
            case "succubus" -> 0.6f;
            case "squirrel" -> 0.9f;
            default -> 1.0f;
        };
    }

    @Override
    public void render(CRPetEntity entity, float entityYaw, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight) {
        float s = scaleOf(entity.petId());
        poseStack.push();
        poseStack.scale(s, s, s);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.pop();
    }
}
