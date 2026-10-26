package com.derevna230.comborush.client;

import com.derevna230.comborush.pet.DecorEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Рисует декоративное растение по модели GeckoLib */
public class DecorRenderer extends GeoEntityRenderer<DecorEntity> {
    public DecorRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new DecorModel());
        this.shadowRadius = 0.3f;
    }
}
