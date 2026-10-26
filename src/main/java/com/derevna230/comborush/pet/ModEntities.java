package com.derevna230.comborush.pet;

import com.derevna230.comborush.ComboRush;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Сущности мода: питомцы и декоративные растения (какой именно — хранится в самой сущности) */
public final class ModEntities {
    private ModEntities() {}

    public static EntityType<CRPetEntity> PET;
    public static EntityType<DecorEntity> DECOR;

    public static void init() {
        PET = Registry.register(Registries.ENTITY_TYPE, Identifier.of(ComboRush.MOD_ID, "pet"),
            EntityType.Builder.create(CRPetEntity::new, SpawnGroup.MISC)
                .dimensions(0.6f, 0.8f)
                .maxTrackingRange(10)
                .disableSaving()           // питомца заново призывает PetManager, в мире он не хранится
                .build("pet"));
        FabricDefaultAttributeRegistry.register(PET, CRPetEntity.createAttributes());
        DECOR = Registry.register(Registries.ENTITY_TYPE, Identifier.of(ComboRush.MOD_ID, "decor"),
            EntityType.Builder.create(DecorEntity::new, SpawnGroup.MISC)
                .dimensions(0.6f, 0.7f)
                .maxTrackingRange(10)
                .build("decor"));
        FabricDefaultAttributeRegistry.register(DECOR, DecorEntity.createAttributes());
    }
}
