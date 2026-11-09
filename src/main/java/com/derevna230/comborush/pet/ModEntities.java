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
    public static EntityType<com.derevna230.comborush.skate.SkateboardEntity> SKATE;
    /** Сюжетные персонажи (Билетёр, адресаты) и предметы сцены (афиши) — один класс, разный размер */
    public static EntityType<com.derevna230.comborush.campaign.StoryEntity> STORY_NPC;
    public static EntityType<com.derevna230.comborush.campaign.StoryEntity> STORY_PROP;
    /** Босс «Редстоуновый голем» и Торговец Редстоуна, продающий Сердце голема */
    public static EntityType<com.derevna230.comborush.boss.RedstoneGolemEntity> GOLEM;
    public static EntityType<com.derevna230.comborush.boss.GolemTraderEntity> TRADER;
    /** Брошенная граната (одна сущность на все четыре вида) */
    public static EntityType<com.derevna230.comborush.item.GrenadeEntity> GRENADE;
    /** Заложенная бомба */
    public static EntityType<com.derevna230.comborush.item.BombEntity> BOMB;
    /** Машины: combo-rush:car_<id>, один класс, разные модели и места */
    public static final java.util.Map<String, EntityType<com.derevna230.comborush.car.CarEntity>> CARS = new java.util.LinkedHashMap<>();

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
        SKATE = Registry.register(Registries.ENTITY_TYPE, Identifier.of(ComboRush.MOD_ID, "skateboard"),
            EntityType.Builder.create(com.derevna230.comborush.skate.SkateboardEntity::new, SpawnGroup.MISC)
                .dimensions(0.8f, 0.25f)
                .maxTrackingRange(10)
                .build("skateboard"));
        FabricDefaultAttributeRegistry.register(SKATE, com.derevna230.comborush.skate.SkateboardEntity.createAttributes());
        STORY_NPC = Registry.register(Registries.ENTITY_TYPE, Identifier.of(ComboRush.MOD_ID, "story_npc"),
            EntityType.Builder.create(com.derevna230.comborush.campaign.StoryEntity::new, SpawnGroup.MISC)
                .dimensions(0.6f, 1.95f)
                .maxTrackingRange(10)
                .build("story_npc"));
        FabricDefaultAttributeRegistry.register(STORY_NPC, com.derevna230.comborush.campaign.StoryEntity.createAttributes());
        STORY_PROP = Registry.register(Registries.ENTITY_TYPE, Identifier.of(ComboRush.MOD_ID, "story_prop"),
            EntityType.Builder.create(com.derevna230.comborush.campaign.StoryEntity::new, SpawnGroup.MISC)
                .dimensions(1.3f, 2.0f)
                .maxTrackingRange(10)
                .build("story_prop"));
        FabricDefaultAttributeRegistry.register(STORY_PROP, com.derevna230.comborush.campaign.StoryEntity.createAttributes());
        GOLEM = Registry.register(Registries.ENTITY_TYPE, Identifier.of(ComboRush.MOD_ID, "redstone_golem"),
            EntityType.Builder.create(com.derevna230.comborush.boss.RedstoneGolemEntity::new, SpawnGroup.MONSTER)
                .dimensions(2.8f, 4.7f)
                .maxTrackingRange(16)
                .makeFireImmune()
                .build("redstone_golem"));
        FabricDefaultAttributeRegistry.register(GOLEM, com.derevna230.comborush.boss.RedstoneGolemEntity.createAttributes());
        TRADER = Registry.register(Registries.ENTITY_TYPE, Identifier.of(ComboRush.MOD_ID, "golem_trader"),
            EntityType.Builder.create(com.derevna230.comborush.boss.GolemTraderEntity::new, SpawnGroup.MISC)
                .dimensions(0.6f, 1.95f)
                .maxTrackingRange(10)
                .build("golem_trader"));
        FabricDefaultAttributeRegistry.register(TRADER, com.derevna230.comborush.boss.GolemTraderEntity.createAttributes());
        GRENADE = Registry.register(Registries.ENTITY_TYPE, Identifier.of(ComboRush.MOD_ID, "grenade"),
            EntityType.Builder.create(com.derevna230.comborush.item.GrenadeEntity::new, SpawnGroup.MISC)
                .dimensions(0.25f, 0.25f)
                .maxTrackingRange(8)
                .trackingTickInterval(1)
                .disableSaving()
                .build("grenade"));
        BOMB = Registry.register(Registries.ENTITY_TYPE, Identifier.of(ComboRush.MOD_ID, "bomb"),
            EntityType.Builder.create(com.derevna230.comborush.item.BombEntity::new, SpawnGroup.MISC)
                .dimensions(0.9f, 0.5f)
                .maxTrackingRange(16)
                .trackingTickInterval(2)
                .disableSaving()
                .build("bomb"));
        for (com.derevna230.comborush.car.CarDefs.Def d : com.derevna230.comborush.car.CarDefs.ALL.values()) {
            EntityType<com.derevna230.comborush.car.CarEntity> t = Registry.register(Registries.ENTITY_TYPE,
                Identifier.of(ComboRush.MOD_ID, "car_" + d.id()),
                EntityType.Builder.create(com.derevna230.comborush.car.CarEntity::new, SpawnGroup.MISC)
                    .dimensions(d.width(), d.height())
                    .passengerAttachments(d.seats())
                    .maxTrackingRange(12)
                    .build("car_" + d.id()));
            FabricDefaultAttributeRegistry.register(t, com.derevna230.comborush.car.CarEntity.createAttributes());
            CARS.put(d.id(), t);
        }
    }
}
