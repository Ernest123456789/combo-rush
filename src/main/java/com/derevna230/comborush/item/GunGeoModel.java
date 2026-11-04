package com.derevna230.comborush.item;

import com.derevna230.comborush.ComboRush;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * Модель ствола/ножа с учётом скина: геометрия и анимации общие (по id ствола), текстура — своя у каждого скина.
 * geo/item/weapon/&lt;base&gt;.geo.json, animations/item/weapon/&lt;base&gt;.animation.json,
 * textures/item/weapon/&lt;base&gt;.png или textures/item/weapon/skins/&lt;skin&gt;.png
 */
public class GunGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
    private final Identifier model, texture, animation;

    public GunGeoModel(String base, String itemId) {
        this.model = Identifier.of(ComboRush.MOD_ID, "geo/item/weapon/" + base + ".geo.json");
        this.animation = Identifier.of(ComboRush.MOD_ID, "animations/item/weapon/" + base + ".animation.json");
        this.texture = Identifier.of(ComboRush.MOD_ID, itemId.equals(base)
            ? "textures/item/weapon/" + base + ".png"
            : "textures/item/weapon/skins/" + itemId + ".png");
    }

    @Override public Identifier getModelResource(T animatable) { return model; }
    @Override public Identifier getTextureResource(T animatable) { return texture; }
    @Override public Identifier getAnimationResource(T animatable) { return animation; }

    public Identifier getModelResource(T animatable, GeoRenderer<T> renderer) { return model; }
    public Identifier getTextureResource(T animatable, GeoRenderer<T> renderer) { return texture; }
}
