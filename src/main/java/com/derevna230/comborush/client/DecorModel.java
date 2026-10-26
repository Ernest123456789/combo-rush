package com.derevna230.comborush.client;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.pet.DecorEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/** Модель декоративного растения: geo/entity/decor/<id>.geo.json и т.д. */
public class DecorModel extends GeoModel<DecorEntity> {
    private static Identifier res(String path) { return Identifier.of(ComboRush.MOD_ID, path); }

    public Identifier getModelResource(DecorEntity e) { return res("geo/entity/decor/" + e.decorId() + ".geo.json"); }
    public Identifier getTextureResource(DecorEntity e) { return res("textures/entity/decor/" + e.decorId() + ".png"); }
    public Identifier getAnimationResource(DecorEntity e) { return res("animations/entity/decor/" + e.decorId() + ".animation.json"); }

    public Identifier getModelResource(DecorEntity e, GeoRenderer<DecorEntity> renderer) { return getModelResource(e); }
    public Identifier getTextureResource(DecorEntity e, GeoRenderer<DecorEntity> renderer) { return getTextureResource(e); }
}
