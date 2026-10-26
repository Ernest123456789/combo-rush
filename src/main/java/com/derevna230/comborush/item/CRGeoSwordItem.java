package com.derevna230.comborush.item;

import com.derevna230.comborush.ComboRush;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Оружие с моделью GeckoLib (у модели повороты, которые обычная модель предмета не умеет).
 * Модель: assets/combo-rush/geo/item/weapon/<id>.geo.json, текстура textures/item/weapon/<id>.png,
 * models/item/<id>.json — builtin/entity с настройками отображения в руке.
 */
public class CRGeoSwordItem extends CRSwordItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CRGeoSwordItem(String setId, Settings settings) {
        super(setId, settings);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        String id = setId();
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<CRGeoSwordItem> renderer;

            @Override
            public BuiltinModelItemRenderer getGeoItemRenderer() {
                if (this.renderer == null)
                    this.renderer = new GeoItemRenderer<>(new DefaultedItemGeoModel<>(Identifier.of(ComboRush.MOD_ID, "weapon/" + id)));
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
