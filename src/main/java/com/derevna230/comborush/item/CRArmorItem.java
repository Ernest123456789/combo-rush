package com.derevna230.comborush.item;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.armor.ArmorTooltips;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * Часть комплекта брони с 3D-моделью GeckoLib.
 * Модель: assets/combo-rush/geo/item/armor/<комплект>.geo.json, текстура: textures/item/armor/<комплект>.png.
 * Какие кости показывать для каждого слота, GeckoLib решает сам (armorHead, armorBody, armorLeftArm …).
 */
public class CRArmorItem extends ArmorItem implements GeoItem {
    private final String setId;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CRArmorItem(String setId, RegistryEntry<ArmorMaterial> material, Type type, Settings settings) {
        super(material, type, settings);
        this.setId = setId;
    }

    public String setId() { return setId; }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<CRArmorItem> renderer;

            @Override
            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(@Nullable T livingEntity, ItemStack itemStack,
                                                                                 @Nullable EquipmentSlot equipmentSlot,
                                                                                 @Nullable BipedEntityModel<T> original) {
                if (this.renderer == null)
                    this.renderer = new GeoArmorRenderer<>(new DefaultedItemGeoModel<>(Identifier.of(ComboRush.MOD_ID, "armor/" + setId)));
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        ArmorTooltips.append(setId, tooltip);
    }
}
