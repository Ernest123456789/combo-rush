package com.derevna230.comborush.client;

import com.derevna230.comborush.campaign.StoryEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Сюжетные персонажи и афиши; светящиеся части (лампочки, фонарь) — из *_glowmask.png */
public class StoryRenderer extends GeoEntityRenderer<StoryEntity> {
    public StoryRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new StoryModel());
        this.shadowRadius = 0.4f;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
