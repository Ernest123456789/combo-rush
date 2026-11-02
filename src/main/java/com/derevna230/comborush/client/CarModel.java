package com.derevna230.comborush.client;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.car.CarEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/** Модели машин лежат в папке decor (геометрия и текстура), анимации колёс — в animations/entity/car */
public class CarModel extends GeoModel<CarEntity> {
    private final String id;

    public CarModel(String id) { this.id = id; }

    private static Identifier res(String path) { return Identifier.of(ComboRush.MOD_ID, path); }

    public Identifier getModelResource(CarEntity e) { return res("geo/entity/decor/" + id + ".geo.json"); }
    public Identifier getTextureResource(CarEntity e) { return res("textures/entity/decor/" + id + ".png"); }
    public Identifier getAnimationResource(CarEntity e) { return res("animations/entity/car/" + id + ".animation.json"); }

    public Identifier getModelResource(CarEntity e, GeoRenderer<CarEntity> renderer) { return getModelResource(e); }
    public Identifier getTextureResource(CarEntity e, GeoRenderer<CarEntity> renderer) { return getTextureResource(e); }
}
