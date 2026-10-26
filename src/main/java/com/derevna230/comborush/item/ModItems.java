package com.derevna230.comborush.item;

import com.derevna230.comborush.ComboRush;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.*;

/** Предметы магазина эссенции: комплекты брони (GeckoLib), шляпы, оружие, питомцы и кейсы */
public final class ModItems {
    private ModItems() {}

    /** id комплекта, редкость, защита [шлем, нагрудник, поножи, ботинки], прочность брони */
    public record SetInfo(String id, String rarity, int[] defense, float toughness) {}

    public static final List<SetInfo> ARMOR_SETS = List.of(
        new SetInfo("copper_knight", "common",    new int[]{2, 5, 4, 2}, 0f),
        new SetInfo("thief",         "uncommon",  new int[]{1, 4, 3, 1}, 0f),
        new SetInfo("iron_defender", "rare",      new int[]{3, 7, 6, 3}, 2f),
        new SetInfo("templar",       "rare",      new int[]{3, 6, 5, 2}, 1f),
        new SetInfo("raider",        "epic",      new int[]{2, 6, 5, 2}, 1f),
        new SetInfo("reverent",      "legendary", new int[]{3, 8, 6, 3}, 2.5f),
        new SetInfo("heavenly",      "legendary", new int[]{3, 7, 6, 3}, 2f));

    public static final List<SetInfo> HATS = List.of(
        new SetInfo("straw_hat",  "common",   null, 0f),
        new SetInfo("tiki_mask",  "uncommon", null, 0f),
        new SetInfo("pirate_hat", "epic",     null, 0f));

    /**
     * Оружие. kind: sword / pick / bow / shield / gun. damage — итоговый урон (как в подсказке), speed — ударов в секунду.
     * gecko — модель GeckoLib (geo/item/weapon/<id>.geo.json), иначе обычная модель предмета.
     */
    public record WeaponInfo(String id, String rarity, String kind, double damage, double speed, boolean gecko) {}

    public static final List<WeaponInfo> WEAPONS = List.of(
        new WeaponInfo("traveler_sword",   "common",    "sword",  6, 1.6, false),
        new WeaponInfo("traveler_dagger",  "common",    "sword",  4, 2.2, false),
        new WeaponInfo("starter_bow",      "common",    "bow",    0, 0, false),
        new WeaponInfo("traveler_buckler", "common",    "shield", 0, 0, false),
        new WeaponInfo("crystal_sword",    "uncommon",  "sword",  7, 1.6, false),
        new WeaponInfo("diamond_blade",    "uncommon",  "sword",  7, 1.6, false),
        new WeaponInfo("kukri",            "uncommon",  "sword",  6, 2.0, false),
        new WeaponInfo("netherite_blade",  "rare",      "sword",  8, 1.6, false),
        new WeaponInfo("emerald_sword",    "rare",      "sword",  7, 1.7, false),
        new WeaponInfo("rebel_pick",       "rare",      "pick",   6, 1.2, false),
        new WeaponInfo("guardian_shield",  "rare",      "shield", 0, 0, false),
        new WeaponInfo("flame_blade",      "rare",      "sword",  8, 1.6, true),
        new WeaponInfo("dreaded_blade",    "epic",      "sword",  9, 1.5, false),
        new WeaponInfo("crimson_fang",     "epic",      "sword",  9, 1.4, false),
        new WeaponInfo("thorn_bow",        "epic",      "bow",    0, 0, false),
        new WeaponInfo("angler_trident",   "epic",      "sword",  9, 1.2, true),
        new WeaponInfo("bass_guitar",      "epic",      "sword",  8, 1.0, true),
        new WeaponInfo("flintlock",        "epic",      "gun",    4, 1.0, true),
        new WeaponInfo("polar_soul",       "legendary", "sword", 10, 1.4, true),
        new WeaponInfo("void_claymore",    "legendary", "sword", 13, 0.9, true),
        new WeaponInfo("storm_spear",      "legendary", "sword", 10, 1.2, true),
        new WeaponInfo("hunting_shotgun",  "legendary", "gun",    5, 1.0, true));

    /** Питомцы: предмет combo-rush:pet_<id>, способности в data/<modid>/pets/pet_<id>.json */
    public record PetInfo(String id, String rarity) {}

    public static final List<PetInfo> PETS = List.of(
        new PetInfo("pigeon", "common"),
        new PetInfo("squirrel", "uncommon"),
        new PetInfo("sailor_crab", "rare"),
        new PetInfo("creeper_skeleton", "epic"),
        new PetInfo("traveler_skeleton", "legendary"),
        new PetInfo("succubus", "legendary"));

    /** Декоративные растения: ПКМ по блоку — посадить (сущность decor с анимацией), удар — забрать обратно */
    public static final List<PetInfo> DECOR = List.of(
        new PetInfo("fern_pot", "common"),
        new PetInfo("tiny_cactus", "common"),
        new PetInfo("glow_shrooms", "uncommon"),
        new PetInfo("moon_lily", "rare"));

    public static Item WATER_BOTTLE;

    public static final List<String> CASE_IDS = List.of("common", "rare", "epic", "legendary", "mythic");
    public static final List<String> PIECES = List.of("helmet", "chestplate", "leggings", "boots");

    /** комплект → его предметы (у шляп один предмет) */
    public static final Map<String, List<Item>> SET_ITEMS = new LinkedHashMap<>();
    /** предмет → id комплекта */
    public static final Map<Item, String> ITEM_SET = new HashMap<>();
    public static final Map<String, String> SET_RARITY = new HashMap<>();
    public static final Map<String, Item> CASES = new LinkedHashMap<>();
    /** id комплекта/предмета → вид: armor, hat, sword, pick, bow, shield, gun, pet */
    public static final Map<String, String> KIND = new HashMap<>();
    public static final List<Item> SHIELDS = new ArrayList<>();
    private static final List<Item> ALL = new ArrayList<>();

    public static String rarityOf(String setId) { return SET_RARITY.getOrDefault(setId, "common"); }
    public static int piecesIn(String setId) { return SET_ITEMS.getOrDefault(setId, List.of()).size(); }
    public static String kindOf(String setId) { return KIND.getOrDefault(setId, "armor"); }
    /** Оружие, которое работает в основной руке */
    public static boolean isMainHand(String setId) {
        String k = kindOf(setId);
        return k.equals("sword") || k.equals("pick") || k.equals("bow") || k.equals("gun") || k.equals("shield");
    }

    public static void init() {
        ArmorItem.Type[] types = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
        for (SetInfo s : ARMOR_SETS) {
            RegistryEntry<ArmorMaterial> mat = material(s);
            List<Item> list = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Item it = register(s.id() + "_" + PIECES.get(i), new CRArmorItem(s.id(), mat, types[i], settings(s.rarity())));
                list.add(it);
                ITEM_SET.put(it, s.id());
            }
            SET_ITEMS.put(s.id(), list);
            SET_RARITY.put(s.id(), s.rarity());
            KIND.put(s.id(), "armor");
        }
        for (SetInfo s : HATS) {
            Item it = register(s.id(), new HatItem(s.id(), settings(s.rarity())));
            SET_ITEMS.put(s.id(), List.of(it));
            ITEM_SET.put(it, s.id());
            SET_RARITY.put(s.id(), s.rarity());
            KIND.put(s.id(), "hat");
        }
        for (WeaponInfo w : WEAPONS) {
            Item.Settings st = settings(w.rarity()).fireproof();
            Item it = switch (w.kind()) {
                case "pick" -> new CRPickaxeItem(w.id(), st.attributeModifiers(attrs(w.damage(), w.speed())));
                case "bow" -> new CRBowItem(w.id(), st);
                case "shield" -> new CRShieldItem(w.id(), st);
                case "gun" -> w.id().equals("hunting_shotgun")
                    ? new CRGunItem(w.id(), st.attributeModifiers(attrs(w.damage(), w.speed())), 4f, 7, 0.09, 26, 24)
                    : new CRGunItem(w.id(), st.attributeModifiers(attrs(w.damage(), w.speed())), 18f, 1, 0, 50, 48);
                default -> w.gecko()
                    ? new CRGeoSwordItem(w.id(), st.attributeModifiers(attrs(w.damage(), w.speed())))
                    : new CRSwordItem(w.id(), st.attributeModifiers(attrs(w.damage(), w.speed())));
            };
            it = register(w.id(), it);
            if (w.kind().equals("shield")) SHIELDS.add(it);
            SET_ITEMS.put(w.id(), List.of(it));
            ITEM_SET.put(it, w.id());
            SET_RARITY.put(w.id(), w.rarity());
            KIND.put(w.id(), w.kind());
        }
        for (PetInfo pi : PETS) {
            String setId = "pet_" + pi.id();
            Item it = register(setId, new PetItem(pi.id(), settings(pi.rarity()).fireproof()));
            SET_ITEMS.put(setId, List.of(it));
            ITEM_SET.put(it, setId);
            SET_RARITY.put(setId, pi.rarity());
            KIND.put(setId, "pet");
        }
        for (PetInfo di : DECOR) {
            String setId = "decor_" + di.id();
            Item it = register(setId, new DecorItem(di.id(), new Item.Settings().maxCount(16).rarity(mcRarity(di.rarity()))));
            SET_ITEMS.put(setId, List.of(it));
            ITEM_SET.put(it, setId);
            SET_RARITY.put(setId, di.rarity());
            KIND.put(setId, "decor");
        }
        WATER_BOTTLE = register("water_bottle", new WaterBottleItem(new Item.Settings().maxCount(16)));
        for (String c : CASE_IDS) {
            Item it = register("case_" + c, new CaseItem(c, new Item.Settings().maxCount(16).rarity(mcRarity(c))));
            CASES.put(c, it);
        }

        Registry.register(Registries.ITEM_GROUP, Identifier.of(ComboRush.MOD_ID, "main"), FabricItemGroup.builder()
            .icon(() -> new ItemStack(CASES.get("legendary")))
            .displayName(Text.translatable("itemGroup.combo-rush.main"))
            .entries((ctx, entries) -> ALL.forEach(entries::add))
            .build());
    }

    private static Item register(String name, Item item) {
        Item it = Registry.register(Registries.ITEM, Identifier.of(ComboRush.MOD_ID, name), item);
        ALL.add(it);
        return it;
    }

    private static Item.Settings settings(String rarity) {
        return new Item.Settings().maxCount(1).rarity(mcRarity(rarity))
            .component(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));
    }

    /** Урон и скорость атаки как у ванильного оружия (зелёные строки в подсказке) */
    private static AttributeModifiersComponent attrs(double damage, double speed) {
        return AttributeModifiersComponent.builder()
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(Identifier.ofVanilla("base_attack_damage"),
                damage - 1, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND)
            .add(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(Identifier.ofVanilla("base_attack_speed"),
                speed - 4, EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND)
            .build();
    }

    public static Rarity mcRarity(String r) {
        return switch (r) {
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic", "legendary", "mythic" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }

    private static RegistryEntry<ArmorMaterial> material(SetInfo s) {
        EnumMap<ArmorItem.Type, Integer> def = new EnumMap<>(ArmorItem.Type.class);
        def.put(ArmorItem.Type.HELMET, s.defense()[0]);
        def.put(ArmorItem.Type.CHESTPLATE, s.defense()[1]);
        def.put(ArmorItem.Type.LEGGINGS, s.defense()[2]);
        def.put(ArmorItem.Type.BOOTS, s.defense()[3]);
        def.put(ArmorItem.Type.BODY, s.defense()[1]);
        // текстура слоёв пустая: броню рисует GeckoLib по своей модели
        return Registry.registerReference(Registries.ARMOR_MATERIAL, Identifier.of(ComboRush.MOD_ID, s.id()),
            new ArmorMaterial(def, 15, SoundEvents.ITEM_ARMOR_EQUIP_IRON, () -> Ingredient.EMPTY,
                List.of(new ArmorMaterial.Layer(Identifier.of(ComboRush.MOD_ID, "cr_blank"))), s.toughness(), 0f));
    }
}
