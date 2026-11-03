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
        this.shadowRadius = 0.45f;
    }

    private static float scaleOf(String id) {
        return switch (id) {
            case "sailor_crab" -> 0.55f;
            // «взрослые» питомцы — размером с игрока (1,8 блока)
            case "creeper_skeleton" -> 1.1f;
            case "traveler_skeleton" -> 0.9f;
            case "succubus" -> 0.86f;
            case "squirrel" -> 0.9f;
            case "armor_stand" -> 0.9f;
            case "redstone_golem" -> 0.311f;   // как был декор
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
