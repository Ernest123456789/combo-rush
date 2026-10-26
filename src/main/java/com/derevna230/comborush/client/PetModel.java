package com.derevna230.comborush.client;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.pet.CRPetEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/** Модель питомца выбирается по его id: geo/entity/pet/<id>.geo.json и т.д. */
public class PetModel extends GeoModel<CRPetEntity> {
    private static Identifier res(String path) { return Identifier.of(ComboRush.MOD_ID, path); }

    public Identifier getModelResource(CRPetEntity pet) { return res("geo/entity/pet/" + pet.petId() + ".geo.json"); }
    public Identifier getTextureResource(CRPetEntity pet) { return res("textures/entity/pet/" + pet.petId() + ".png"); }
    public Identifier getAnimationResource(CRPetEntity pet) { return res("animations/entity/pet/" + pet.petId() + ".animation.json"); }

    // в новых версиях GeckoLib методы принимают ещё и рендерер
    public Identifier getModelResource(CRPetEntity pet, GeoRenderer<CRPetEntity> renderer) { return getModelResource(pet); }
    public Identifier getTextureResource(CRPetEntity pet, GeoRenderer<CRPetEntity> renderer) { return getTextureResource(pet); }
}
