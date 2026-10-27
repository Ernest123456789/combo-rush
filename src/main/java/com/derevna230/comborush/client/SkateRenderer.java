package com.derevna230.comborush.client;

import com.derevna230.comborush.skate.SkateboardEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Рисует скейтборд под ногами игрока */
public class SkateRenderer extends GeoEntityRenderer<SkateboardEntity> {
    public SkateRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new SkateModel());
        this.shadowRadius = 0.35f;
    }
}
