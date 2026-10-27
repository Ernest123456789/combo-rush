package com.derevna230.comborush.client;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.skate.SkateboardEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/** Модель скейтборда: geo/entity/skateboard.geo.json, колёса крутятся анимацией animation.cr_skate.roll */
public class SkateModel extends GeoModel<SkateboardEntity> {
    private static final Identifier MODEL = Identifier.of(ComboRush.MOD_ID, "geo/entity/skateboard.geo.json");
    private static final Identifier TEXTURE = Identifier.of(ComboRush.MOD_ID, "textures/entity/skateboard.png");
    private static final Identifier ANIM = Identifier.of(ComboRush.MOD_ID, "animations/entity/skateboard.animation.json");

    public Identifier getModelResource(SkateboardEntity e) { return MODEL; }
    public Identifier getTextureResource(SkateboardEntity e) { return TEXTURE; }
    public Identifier getAnimationResource(SkateboardEntity e) { return ANIM; }

    public Identifier getModelResource(SkateboardEntity e, GeoRenderer<SkateboardEntity> r) { return MODEL; }
    public Identifier getTextureResource(SkateboardEntity e, GeoRenderer<SkateboardEntity> r) { return TEXTURE; }
}
