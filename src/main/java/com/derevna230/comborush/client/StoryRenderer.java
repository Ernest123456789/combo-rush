package com.derevna230.comborush.client;

import com.derevna230.comborush.campaign.StoryEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.Set;

/**
 * Сюжетные персонажи и афиши. Светящиеся части (лампочки, фонарь, свеча) — из *_glowmask.png,
 * но только у тех видов, где они есть: GeckoLib падает на пустой маске свечения.
 */
public class StoryRenderer extends GeoEntityRenderer<StoryEntity> {
    private static final Set<String> GLOWING = Set.of("lighter", "prompter", "poster_1", "poster_2", "poster_3", "poster_4");

    public StoryRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new StoryModel());
        this.shadowRadius = 0.4f;
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            public void render(MatrixStack poseStack, StoryEntity animatable, BakedGeoModel bakedModel, RenderLayer renderType,
                               VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
                if (!GLOWING.contains(animatable.kind())) return;
                super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
            }
        });
    }
}
