package com.derevna230.comborush.car;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Машины: масштаб модели (в блоках Minecraft ≈ 4 блока в длину), скорость, число мест и их положение.
 * Места заданы в системе сущности: x > 0 — слева по ходу, z > 0 — вперёд; первое место — водительское.
 */
public final class CarDefs {
    private CarDefs() {}

    /** speed — атрибут скорости (≈ 43 блока/с на единицу), wheelCirc — длина окружности колеса в блоках */
    public record Def(String id, float scale, double speed, double wheelCirc, float width, float height, Vec3d[] seats) {
        public int seatCount() { return seats.length; }
    }

    public static final Map<String, Def> ALL = new LinkedHashMap<>();

    static {
        // американский масл-кар: руль слева
        put(new Def("mustang", 0.70f, 0.32, 1.98, 2.0f, 1.4f, new Vec3d[]{
            new Vec3d(0.36, 0.18, -0.26), new Vec3d(-0.36, 0.18, -0.26)}));
        // гоночная версия: одно ковшеобразное кресло
        put(new Def("mustang_modified", 0.70f, 0.40, 1.98, 2.0f, 1.4f, new Vec3d[]{
            new Vec3d(0.36, 0.18, -0.26)}));
        // японский хэтчбек: руль справа, диван сзади
        put(new Def("trueno", 0.62f, 0.36, 2.57, 2.0f, 1.4f, new Vec3d[]{
            new Vec3d(-0.31, 0.28, -0.10), new Vec3d(0.31, 0.28, -0.10),
            new Vec3d(-0.28, 0.28, -1.05), new Vec3d(0.28, 0.28, -1.05)}));
    }

    private static void put(Def d) { ALL.put(d.id(), d); }

    public static Def get(String id) { return ALL.get(id); }

    /** Определение по типу сущности (id вида combo-rush:car_<машина>) */
    public static Def ofType(EntityType<?> type) {
        String path = Registries.ENTITY_TYPE.getId(type).getPath();
        Def d = ALL.get(path.startsWith("car_") ? path.substring(4) : path);
        return d != null ? d : ALL.values().iterator().next();
    }
}
